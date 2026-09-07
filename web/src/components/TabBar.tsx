import { NavLink } from 'react-router-dom'
import type { ReactNode } from 'react'
import styles from './TabBar.module.css'

const TABS: { to: string; label: string; icon: ReactNode }[] = [
  {
    to: '/',
    label: '홈',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path
          d="M3.5 8.2 10 3l6.5 5.2V16a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V8.2Z"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
  {
    to: '/monthly',
    label: '내역',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path
          d="M4.5 15V9m5.5 6V5m5.5 10v-4"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </svg>
    ),
  },
  {
    to: '/settlement',
    label: '정산',
    icon: (
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path
          d="M3.5 6.5h11m0 0-2.5-2.5M14.5 6.5 12 9M16.5 13h-11m0 0L8 10.5M5.5 13 8 15.5"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    ),
  },
]

export function TabBar() {
  return (
    <nav className={styles.bar} aria-label="주요 화면">
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.to === '/'}
          className={({ isActive }) => `${styles.tab} ${isActive ? styles.active : ''}`}
        >
          {tab.icon}
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}
