import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { EmptyState, ErrorState, InlineError, Skeleton, SkeletonStack } from '../components/States'
import { notificationsApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { formatPeriod, formatRelativeTime } from '../lib/format'
import type { Notification } from '../lib/api/types'
import styles from './NotificationsPage.module.css'

/** 2인 앱의 알림은 많아야 월 몇 건이다. 한 번에 받아 두고 페이징은 두지 않는다. */
const PAGE_SIZE = 50

export function NotificationsPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const notifications = useQuery({
    queryKey: queryKeys.notificationList(PAGE_SIZE),
    queryFn: () => notificationsApi.list(0, PAGE_SIZE),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.notifications })

  const markRead = useMutation({
    mutationFn: (notificationId: number) => notificationsApi.markRead(notificationId),
    onSuccess: () => void invalidate(),
  })

  const markAllRead = useMutation({
    mutationFn: () => notificationsApi.markAllRead(),
    onSuccess: () => void invalidate(),
  })

  const unread = notifications.data?.unreadCount ?? 0

  /** 알림을 열면 읽음으로 바꾸고, 정산 알림이면 정산 화면으로 보낸다. */
  const open = (notification: Notification) => {
    if (!notification.read) markRead.mutate(notification.notificationId)
    if (notification.type === 'SETTLEMENT_REMINDER') navigate('/settlement')
  }

  return (
    <AppScreen
      title="알림"
      back="/"
      action={
        unread > 0 ? (
          <Button
            variant="ghost"
            onClick={() => markAllRead.mutate()}
            disabled={markAllRead.isPending}
          >
            모두 읽음
          </Button>
        ) : undefined
      }
    >
      {notifications.isPending && (
        <SkeletonStack>
          <Skeleton height={76} />
          <Skeleton height={76} />
        </SkeletonStack>
      )}

      {notifications.error && (
        <ErrorState
          error={notifications.error}
          action={
            <Button variant="ghost" onClick={() => void notifications.refetch()}>
              다시 불러오기
            </Button>
          }
        />
      )}

      {markAllRead.error && <InlineError error={markAllRead.error} />}

      {notifications.data &&
        (notifications.data.content.length === 0 ? (
          <EmptyState
            icon="🔔"
            headline="아직 알림이 없어요"
            detail="정산 기준일이 되면 여기로 알려 드릴게요."
          />
        ) : (
          <ul className={styles.list}>
            {notifications.data.content.map((notification) => (
              <li key={notification.notificationId}>
                <button
                  type="button"
                  className={`${styles.item} ${notification.read ? '' : styles.unread}`}
                  onClick={() => open(notification)}
                >
                  <span className={styles.dot} aria-hidden="true" />
                  <span className={styles.content}>
                    <span className={styles.head}>
                      <span className={styles.title}>{notification.title}</span>
                      <span className={styles.time}>
                        {formatRelativeTime(notification.createdAt)}
                      </span>
                    </span>
                    <span className={styles.body}>{notification.body}</span>
                    {notification.period && (
                      <span className={styles.period}>{formatPeriod(notification.period)}</span>
                    )}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        ))}
    </AppScreen>
  )
}
