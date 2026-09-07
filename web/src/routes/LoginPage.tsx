import { useSearchParams } from 'react-router-dom'
import { API_BASE } from '../lib/api/client'
import { InlineError } from '../components/States'
import styles from './LoginPage.module.css'

/**
 * 첫 진입 화면. 로그인 방법 외에는 아무것도 묻지 않는다.
 *
 * 소셜 로그인은 SPA 라우팅이 아니라 실제 페이지 이동이다.
 * 백엔드가 세션에 state 를 보관해야 하고, 돌아올 때 HttpOnly 쿠키를 심어야 하기 때문이다.
 */
export function LoginPage() {
  const [params] = useSearchParams()
  const failed = params.get('status') === 'failure'

  const startLogin = (provider: 'kakao' | 'google') => {
    window.location.href = `${API_BASE}/oauth2/authorization/${provider}`
  }

  return (
    <div className={styles.page}>
      <div className={styles.hero}>
        <div className={styles.logo}>
          <span className={styles.mark} aria-hidden="true">
            ₩
          </span>
          PairPay
        </div>
        <p className={styles.tagline}>
          둘이 쓴 돈, 깔끔하게 반반.
          <br />
          누가 얼마 냈는지 더 세지 않아도 돼요.
        </p>
      </div>

      {failed && (
        <div className={styles.failure}>
          <InlineError error={new Error('로그인이 완료되지 않았어요. 다시 시도해 주세요.')} />
        </div>
      )}

      <div className={styles.buttons}>
        <button
          type="button"
          className={`${styles.social} ${styles.kakao}`}
          onClick={() => startLogin('kakao')}
        >
          <KakaoMark />
          카카오로 시작하기
        </button>
        <button
          type="button"
          className={`${styles.social} ${styles.google}`}
          onClick={() => startLogin('google')}
        >
          <GoogleMark />
          구글로 시작하기
        </button>
      </div>

      <p className={styles.trust}>기록은 우리 둘만 볼 수 있어요.</p>
    </div>
  )
}

function KakaoMark() {
  return (
    <svg width="19" height="19" viewBox="0 0 18 18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M9 1.8C4.98 1.8 1.8 4.36 1.8 7.5c0 2.03 1.34 3.8 3.35 4.8l-.85 3.1c-.07.26.22.47.45.32l3.7-2.44c.18.01.37.02.55.02 4.02 0 7.2-2.56 7.2-5.8S13.02 1.8 9 1.8Z"
      />
    </svg>
  )
}

function GoogleMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62Z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z"
      />
      <path fill="#FBBC05" d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3-2.33Z" />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z"
      />
    </svg>
  )
}
