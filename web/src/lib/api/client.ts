import { clearAccessToken, getAccessToken, isAccessTokenUsable, setAccessToken } from '../auth/tokenStore'
import type { AccessTokenResponse, ErrorBody, LoginRequest, SignupRequest } from './types'

/**
 * 개발에서는 빈 문자열이다. Vite 가 /api 를 백엔드로 프록시하므로 같은 오리진으로 나간다.
 * 다른 도메인에 올릴 때만 VITE_API_BASE_URL 을 채운다.
 */
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const REFRESH_PATH = '/api/v1/auth/token'

/**
 * 백엔드의 ErrorResponse 를 그대로 들고 다니는 에러.
 * message 는 사용자에게 보여줄 수 있는 문장이라 화면에서 다시 만들지 않는다.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; reason: string }[]

  constructor(status: number, body: Partial<ErrorBody> | null) {
    super(body?.message ?? '일시적인 오류가 발생했습니다.')
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code ?? 'UNKNOWN'
    this.fieldErrors = body?.fieldErrors ?? []
  }

  /** 특정 필드의 검증 실패 사유. 폼에서 인풋 밑에 그대로 띄운다. */
  fieldReason(field: string): string | undefined {
    return this.fieldErrors.find((it) => it.field === field)?.reason
  }
}

/** 로그인이 끊겼을 때 앱이 로그인 화면으로 빠지도록 알리는 통로. */
type SessionEndedListener = () => void
let onSessionEnded: SessionEndedListener = () => {}

export function setSessionEndedListener(listener: SessionEndedListener): void {
  onSessionEnded = listener
}

/**
 * 리프레시 토큰으로 액세스 토큰을 받아온다.
 *
 * 회전 토큰이라 두 번 부르면 뒤엣것이 재사용으로 탐지되어 family 전체가 폐기된다.
 * 그래서 진행 중인 요청이 있으면 그 Promise 를 나눠 쓴다.
 */
let inFlightRefresh: Promise<boolean> | null = null

export function refreshAccessToken(): Promise<boolean> {
  if (inFlightRefresh) return inFlightRefresh

  inFlightRefresh = (async () => {
    try {
      const response = await fetch(`${API_BASE}${REFRESH_PATH}`, {
        method: 'POST',
        credentials: 'include',
      })
      if (!response.ok) {
        clearAccessToken()
        return false
      }
      const issued = (await response.json()) as AccessTokenResponse
      setAccessToken(issued.accessToken, issued.expiresIn)
      return true
    } catch {
      // 네트워크가 끊긴 경우다. 토큰이 죽었다고 단정하지 않는다.
      return false
    } finally {
      inFlightRefresh = null
    }
  })()

  return inFlightRefresh
}

/**
 * 회원가입과 로그인.
 *
 * anonymous 로 보내는 이유: 자격증명이 틀리면 401 이 오는데, 그것은 세션 만료가 아니다.
 * 일반 경로로 보내면 재발급을 시도하고 앱 전체를 로그아웃시켜 버린다.
 */
export async function signUp(body: SignupRequest): Promise<void> {
  await startSession('/api/v1/auth/signup', body)
}

export async function signIn(body: LoginRequest): Promise<void> {
  await startSession('/api/v1/auth/login', body)
}

async function startSession(path: string, body: unknown): Promise<void> {
  const issued = await request<AccessTokenResponse>(path, { method: 'POST', body, anonymous: true })
  setAccessToken(issued.accessToken, issued.expiresIn)
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${API_BASE}/api/v1/auth/logout`, { method: 'POST', credentials: 'include' })
  } finally {
    clearAccessToken()
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  query?: Record<string, string | number | undefined | null>
  /** 로그인 전에도 부를 수 있는 경로(초대 미리보기 등). 401 이어도 재발급을 시도하지 않는다. */
  anonymous?: boolean
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = `${API_BASE}${path}`
  if (!query) return url

  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
  }
  const qs = params.toString()
  return qs ? `${url}?${qs}` : url
}

async function readError(response: Response): Promise<ApiError> {
  const body = await response
    .json()
    .then((it) => it as ErrorBody)
    .catch(() => null)
  return new ApiError(response.status, body)
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, anonymous = false } = options

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = getAccessToken()
    if (!anonymous && token) headers.Authorization = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    return fetch(buildUrl(path, query), {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  }

  // 만료가 눈앞이면 요청을 보내기 전에 갱신한다. 실패한 왕복을 한 번 아낀다.
  if (!anonymous && getAccessToken() && !isAccessTokenUsable()) {
    await refreshAccessToken()
  }

  let response = await send()

  // 401 은 한 번만 되살려 본다. 두 번째도 401 이면 정말 끊긴 것이다.
  if (response.status === 401 && !anonymous) {
    const revived = await refreshAccessToken()
    if (!revived) {
      onSessionEnded()
      throw await readError(response)
    }
    response = await send()
    if (response.status === 401) {
      clearAccessToken()
      onSessionEnded()
      throw await readError(response)
    }
  }

  if (!response.ok) throw await readError(response)
  if (response.status === 204) return undefined as T

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
