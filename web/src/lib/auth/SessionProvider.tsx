import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  logout as callLogout,
  refreshAccessToken,
  setSessionEndedListener,
  signIn as callSignIn,
  signUp as callSignUp,
} from '../api/client'
import { couplesApi, usersApi } from '../api/endpoints'
import { queryKeys } from '../queryKeys'
import type { RefreshOutcome } from '../api/client'
import type { Couple, CoupleMember, LoginRequest, Me, SignupRequest } from '../api/types'

/**
 * 'offline' 은 "로그인 여부를 아직 모른다"는 뜻이다.
 * 'anonymous' 로 접으면 리프레시 쿠키가 멀쩡한데도 로그인 화면이 떠서, 다시 로그인하면
 * 회전 토큰 family 가 바뀌고 다른 기기의 세션까지 흔들린다. 모를 때는 모른다고 둔다.
 */
type SessionState = 'booting' | 'anonymous' | 'authenticated' | 'offline'

interface SessionValue {
  state: SessionState
  me: Me | null
  couple: Couple | null
  /** 로딩이 끝나 라우팅 결정을 내려도 되는 시점인지. */
  ready: boolean
  /** 부팅 재발급이 네트워크로 실패했을 때 다시 시도하는 중인지. */
  retrying: boolean
  retryBoot: () => Promise<void>
  signUp: (body: SignupRequest) => Promise<void>
  signIn: (body: LoginRequest) => Promise<void>
  signOut: () => Promise<void>
}

function stateFor(outcome: RefreshOutcome): SessionState {
  if (outcome === 'revived') return 'authenticated'
  // 서버가 리프레시 토큰을 거절했을 때만 로그인 화면으로 보낸다.
  if (outcome === 'expired') return 'anonymous'
  return 'offline'
}

const SessionContext = createContext<SessionValue | null>(null)

/**
 * 앱을 켜면 리프레시 쿠키로 액세스 토큰을 되찾는 것부터 한다.
 * 이 한 번의 왕복이 "새로고침해도 로그인이 유지된다"의 전부다.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<SessionState>('booting')
  const [retrying, setRetrying] = useState(false)

  /**
   * 부팅과 재시도가 같은 절차라 한 함수로 둔다.
   * refreshAccessToken 이 진행 중인 요청을 나눠 쓰므로 겹쳐 불러도 재발급은 한 번만 나간다.
   */
  const restoreSession = useCallback(async () => {
    setState(stateFor(await refreshAccessToken()))
  }, [])

  useEffect(() => {
    void restoreSession()
  }, [restoreSession])

  const retryBoot = useCallback(async () => {
    setRetrying(true)
    try {
      await restoreSession()
    } finally {
      setRetrying(false)
    }
  }, [restoreSession])

  // 연결이 돌아오면 사용자가 버튼을 누르기 전에 알아서 복구한다. 지하철에서 나오면 그냥 이어진다.
  useEffect(() => {
    if (state !== 'offline') return
    const onOnline = () => void retryBoot()
    window.addEventListener('online', onOnline)
    return () => window.removeEventListener('online', onOnline)
  }, [state, retryBoot])

  // 어느 요청에서든 세션이 끊기면 앱 전체가 로그인 화면으로 돌아가야 한다.
  useEffect(() => {
    setSessionEndedListener(() => {
      setState('anonymous')
      queryClient.clear()
    })
  }, [queryClient])

  const authenticated = state === 'authenticated'

  const meQuery = useQuery({
    queryKey: queryKeys.me,
    queryFn: usersApi.me,
    enabled: authenticated,
    staleTime: 5 * 60 * 1000,
  })

  const coupleQuery = useQuery({
    queryKey: queryKeys.couple,
    queryFn: couplesApi.mine,
    enabled: authenticated && meQuery.data?.coupleId != null,
    staleTime: 60 * 1000,
  })

  const signUp = useCallback(async (body: SignupRequest) => {
    await callSignUp(body)
    setState('authenticated')
  }, [])

  const signIn = useCallback(async (body: LoginRequest) => {
    await callSignIn(body)
    setState('authenticated')
  }, [])

  const signOut = useCallback(async () => {
    await callLogout()
    setState('anonymous')
    queryClient.clear()
  }, [queryClient])

  const value = useMemo<SessionValue>(() => {
    const waitingForMe = authenticated && meQuery.isPending
    const waitingForCouple = authenticated && meQuery.data?.coupleId != null && coupleQuery.isPending

    return {
      state,
      me: meQuery.data ?? null,
      couple: coupleQuery.data ?? null,
      // 'offline' 에서는 아직 아무 결정도 내릴 수 없다. 라우팅을 열어 주면 로그인 화면으로 튕긴다.
      ready: state !== 'booting' && state !== 'offline' && !waitingForMe && !waitingForCouple,
      retrying,
      retryBoot,
      signUp,
      signIn,
      signOut,
    }
  }, [
    state,
    authenticated,
    retrying,
    retryBoot,
    meQuery.isPending,
    meQuery.data,
    coupleQuery.isPending,
    coupleQuery.data,
    signUp,
    signIn,
    signOut,
  ])

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useSession 은 SessionProvider 안에서만 쓸 수 있습니다.')
  return value
}

interface CoupleContext {
  couple: Couple
  me: CoupleMember
  partner: CoupleMember | null
}

/**
 * "나"와 "상대"를 갈라 준다. 화면 대부분이 이 두 사람을 기준으로 그려진다.
 * 커플이 아직 없는 화면에서 부르면 안 된다 — 라우트 가드가 이미 걸러 준다.
 */
export function useCoupleContext(): CoupleContext {
  const { me, couple } = useSession()
  if (!me || !couple) throw new Error('커플 문맥이 필요한 화면인데 아직 준비되지 않았습니다.')

  const mine = couple.members.find((member) => member.userId === me.userId)
  if (!mine) throw new Error('내가 이 커플 space 의 구성원이 아닙니다.')

  return {
    couple,
    me: mine,
    partner: couple.members.find((member) => member.userId !== me.userId) ?? null,
  }
}
