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

/**
 * 요청이 서버에 닿지도 못한 경우.
 *
 * 인증 실패와 반드시 구분해야 한다. 응답을 못 받은 것은 "토큰이 죽었다"의 근거가 아니라
 * "아직 모른다"일 뿐이다. 이걸 401 과 같이 취급하면 지하철에서 앱을 여는 것만으로 로그아웃된다.
 */
export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super('네트워크에 연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요.', { cause })
    this.name = 'NetworkError'
  }
}

/** 로그인이 끊겼을 때 앱이 로그인 화면으로 빠지도록 알리는 통로. */
type SessionEndedListener = () => void
let onSessionEnded: SessionEndedListener = () => {}

export function setSessionEndedListener(listener: SessionEndedListener): void {
  onSessionEnded = listener
}

/**
 * 재발급 결과.
 *
 * 'expired' 와 'offline' 을 하나로 뭉치면 안 된다. 앞은 서버가 리프레시 토큰을 거절한 것이고,
 * 뒤는 서버에게 물어보지도 못한 것이다. 로그아웃해도 되는 쪽은 앞뿐이다.
 */
export type RefreshOutcome = 'revived' | 'expired' | 'offline'

/**
 * 리프레시 토큰으로 액세스 토큰을 받아온다.
 *
 * 회전 토큰이라 두 번 부르면 뒤엣것이 재사용으로 탐지되어 family 전체가 폐기된다.
 * 그래서 진행 중인 요청이 있으면 그 Promise 를 나눠 쓴다.
 */
let inFlightRefresh: Promise<RefreshOutcome> | null = null

export function refreshAccessToken(): Promise<RefreshOutcome> {
  if (inFlightRefresh) return inFlightRefresh

  inFlightRefresh = (async () => {
    try {
      const response = await fetch(`${API_BASE}${REFRESH_PATH}`, {
        method: 'POST',
        credentials: 'include',
      })
      // 5xx 는 서버가 넘어진 것이지 토큰을 거절한 것이 아니다. 판단을 미룬다.
      if (response.status >= 500) return 'offline'
      if (!response.ok) {
        // 서버가 리프레시 토큰을 거절했다. 액세스 토큰을 버리는 것은 이때뿐이다.
        clearAccessToken()
        return 'expired'
      }
      const issued = (await response.json()) as AccessTokenResponse
      setAccessToken(issued.accessToken, issued.expiresIn)
      return 'revived'
    } catch {
      // fetch 가 던졌다 = 요청이 서버에 닿지 못했다. 토큰이 죽었다고 단정하지 않고 그대로 둔다.
      return 'offline'
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

/**
 * 요청을 보내기도 전에 재발급이 거절된 경우의 에러.
 * 읽어 올 응답이 없으니 화면에 보여줄 문장을 여기서 만든다. 401 인 것은 화면이 인증 실패로 알아보게 하려는 것이다.
 */
function sessionExpired(): ApiError {
  return new ApiError(401, {
    code: 'SESSION_EXPIRED',
    message: '로그인이 만료되었어요. 다시 로그인해 주세요.',
  })
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, anonymous = false } = options

  const send = async (): Promise<Response> => {
    const headers: Record<string, string> = { Accept: 'application/json' }
    const token = getAccessToken()
    if (!anonymous && token) headers.Authorization = `Bearer ${token}`
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    try {
      return await fetch(buildUrl(path, query), {
        method,
        headers,
        credentials: 'include',
        body: body === undefined ? undefined : JSON.stringify(body),
      })
    } catch (cause) {
      // fetch 는 서버에 닿지 못했을 때만 던진다. 4xx·5xx 는 정상적으로 resolve 된다.
      throw new NetworkError(cause)
    }
  }

  // 만료가 눈앞이면 요청을 보내기 전에 갱신한다. 실패한 왕복을 한 번 아낀다.
  if (!anonymous && getAccessToken() && !isAccessTokenUsable()) {
    const outcome = await refreshAccessToken()
    // 재발급이 서버에 닿지 못했으면 이 요청도 닿지 못한다. 세션은 건드리지 않고 요청만 실패시킨다.
    if (outcome === 'offline') throw new NetworkError()
    if (outcome === 'expired') {
      onSessionEnded()
      throw sessionExpired()
    }
  }

  let response = await send()

  // 401 은 한 번만 되살려 본다. 두 번째도 401 이면 정말 끊긴 것이다.
  if (response.status === 401 && !anonymous) {
    const outcome = await refreshAccessToken()
    // 재발급이 서버에 닿지 못한 것은 리프레시 토큰이 죽었다는 근거가 아니다.
    // 여기서 onSessionEnded() 를 부르면 잠깐 끊긴 것만으로 로그아웃되고 캐시까지 날아간다.
    if (outcome === 'offline') throw new NetworkError()
    if (outcome === 'expired') {
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
