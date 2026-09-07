import { useEffect } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useSession } from '../lib/auth/SessionProvider'
import { takePendingInvite } from '../lib/auth/pendingInvite'
import { FullPageSpinner } from '../components/FullPageSpinner'

/**
 * 소셜 로그인이 끝나고 브라우저가 착지하는 곳.
 *
 * 액세스 토큰은 URL 에 실려 오지 않는다. 여기서 하는 일은
 * "리프레시 쿠키로 액세스 토큰을 받아오는" 부팅 절차가 끝나기를 기다리는 것뿐이다.
 * 그 절차는 SessionProvider 가 이미 돌리고 있다.
 */
export function OAuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { state, ready } = useSession()

  const failed = params.get('status') === 'failure'

  useEffect(() => {
    if (failed || !ready || state !== 'authenticated') return

    // 초대 링크를 타고 왔다면 원래 가려던 곳으로 돌려보낸다.
    const pending = takePendingInvite()
    navigate(pending ? `/invite/${pending}` : '/', { replace: true })
  }, [failed, ready, state, navigate])

  if (failed) {
    return <Navigate to={`/login?status=failure&reason=${params.get('reason') ?? 'LOGIN_FAILED'}`} replace />
  }

  if (ready && state === 'anonymous') return <Navigate to="/login" replace />

  return <FullPageSpinner label="로그인하는 중" />
}
