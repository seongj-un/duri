import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { TabBar } from './TabBar'
import styles from './AppScreen.module.css'

interface AppScreenProps {
  title?: string
  /** 뒤로가기 버튼. 어디로 갈지 지정하지 않으면 히스토리를 한 칸 되돌린다. */
  back?: boolean | string
  action?: ReactNode
  footer?: ReactNode
  /** 홈·내역·정산처럼 탭으로 오가는 화면. */
  tabBar?: boolean
  children: ReactNode
}

export function AppScreen({ title, back, action, footer, tabBar = false, children }: AppScreenProps) {
  const navigate = useNavigate()
  const hasBar = Boolean(title || back || action)

  return (
    <div className={styles.screen}>
      {hasBar && (
        <header className={`${styles.bar} ${title ? styles.barBordered : ''}`}>
          {back && (
            <button
              type="button"
              className={styles.back}
              aria-label="뒤로"
              onClick={() => (typeof back === 'string' ? navigate(back) : navigate(-1))}
            >
              <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                <path
                  d="M12.5 4.5 7 10l5.5 5.5"
                  stroke="currentColor"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </button>
          )}
          {title && <h1 className={styles.title}>{title}</h1>}
          <div className={styles.barSpacer} />
          {action}
        </header>
      )}

      <main
        className={[
          styles.body,
          footer ? styles.bodyWithFooter : '',
          tabBar ? styles.bodyWithTabBar : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {children}
      </main>

      {footer && <div className={styles.footer}>{footer}</div>}
      {tabBar && <TabBar />}
    </div>
  )
}
