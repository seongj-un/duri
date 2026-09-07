import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { notificationsApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import styles from './NotificationBell.module.css'

/** 뱃지 숫자만 필요하므로 1건만 받는다. 응답의 unreadCount 는 전체 기준이다. */
const BADGE_PAGE_SIZE = 1

export function NotificationBell() {
  const { data } = useQuery({
    queryKey: queryKeys.notificationList(BADGE_PAGE_SIZE),
    queryFn: () => notificationsApi.list(0, BADGE_PAGE_SIZE),
  })

  const unread = data?.unreadCount ?? 0

  return (
    <Link
      to="/notifications"
      className={styles.bell}
      aria-label={unread > 0 ? `알림 ${unread}건` : '알림'}
    >
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path
          d="M10 3a4.5 4.5 0 0 0-4.5 4.5c0 3-1.2 4.2-1.2 4.2h11.4s-1.2-1.2-1.2-4.2A4.5 4.5 0 0 0 10 3Z"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinejoin="round"
        />
        <path d="M8.5 14.5a1.6 1.6 0 0 0 3 0" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
      {unread > 0 && (
        <span className={styles.badge}>{unread > 9 ? '9+' : unread}</span>
      )}
    </Link>
  )
}
