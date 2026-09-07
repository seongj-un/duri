import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { FullPageSpinner } from './components/FullPageSpinner'
import { useSession } from './lib/auth/SessionProvider'
import { useCoupleEvents } from './lib/realtime/useCoupleEvents'
import { AccountPage } from './routes/AccountPage'
import { ExpenseCreatePage } from './routes/ExpenseCreatePage'
import { HomePage } from './routes/HomePage'
import { InviteAcceptPage } from './routes/InviteAcceptPage'
import { LoginPage } from './routes/LoginPage'
import { MonthlyPage } from './routes/MonthlyPage'
import { OAuthCallbackPage } from './routes/OAuthCallbackPage'
import { OnboardingPage } from './routes/OnboardingPage'
import { PartnerLinkPage } from './routes/PartnerLinkPage'
import { SettlementPage } from './routes/SettlementPage'

export function App() {
  const { state, ready, me, couple } = useSession()

  // 스트림은 두 사람이 연결된 뒤에만 열린다. 그전에는 붙어도 거절당한다.
  useCoupleEvents(couple?.status === 'ACTIVE', me?.userId)

  // 어디로 보낼지는 me/couple 이 도착해야 정해진다. 그전에 그리면 화면이 한 번 튄다.
  if (!ready) return <FullPageSpinner />

  const authenticated = state === 'authenticated'

  return (
    <Routes>
      <Route
        path="/login"
        element={authenticated ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
      {/* 초대 미리보기는 로그인 전에도 열린다. 토큰을 가진 것 자체가 열람 권한이다. */}
      <Route path="/invite/:token" element={<InviteAcceptPage />} />

      <Route element={<RequireAuth authenticated={authenticated} />}>
        <Route
          path="/onboarding"
          element={me?.coupleId == null ? <OnboardingPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="/link"
          element={couple?.status === 'PENDING' ? <PartnerLinkPage /> : <Navigate to="/" replace />}
        />

        <Route element={<RequireCouple coupleId={me?.coupleId ?? null} status={couple?.status} />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/monthly" element={<MonthlyPage />} />
          <Route path="/settlement" element={<SettlementPage />} />
          <Route path="/expenses/new" element={<ExpenseCreatePage />} />
          <Route path="/account" element={<AccountPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function RequireAuth({ authenticated }: { authenticated: boolean }) {
  return authenticated ? <Outlet /> : <Navigate to="/login" replace />
}

/**
 * 지출·정산 화면은 커플 space 가 있어야 의미가 있다.
 * 아직 없으면 만들게 하고, 파트너를 기다리는 중이면 초대 화면으로 보낸다.
 */
function RequireCouple({
  coupleId,
  status,
}: {
  coupleId: number | null
  status: string | undefined
}) {
  if (coupleId == null) return <Navigate to="/onboarding" replace />
  if (status === 'PENDING') return <Navigate to="/link" replace />
  return <Outlet />
}
