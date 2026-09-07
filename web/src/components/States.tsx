import type { ReactNode } from 'react'
import { ApiError } from '../lib/api/client'
import styles from './States.module.css'

interface EmptyStateProps {
  icon?: ReactNode
  headline: string
  detail?: string
  action?: ReactNode
}

/** 빈 화면은 설명이 아니라 다음 행동을 준다. */
export function EmptyState({ icon = '🧾', headline, detail, action }: EmptyStateProps) {
  return (
    <div className={styles.state}>
      <div className={styles.icon} aria-hidden="true">
        {icon}
      </div>
      <p className={styles.headline}>{headline}</p>
      {detail && <p className={styles.detail}>{detail}</p>}
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}

/**
 * 에러는 사과하지 않는다. 무엇이 잘못됐는지와 무엇을 하면 되는지만 말한다.
 * 백엔드 message 가 이미 그런 문장이라 그대로 쓴다.
 */
export function ErrorState({ error, action }: { error: unknown; action?: ReactNode }) {
  return (
    <div className={styles.state}>
      <div className={styles.icon} aria-hidden="true">
        ⚠️
      </div>
      <p className={styles.headline}>{describe(error)}</p>
      {action && <div className={styles.action}>{action}</div>}
    </div>
  )
}

/** 폼 안에서 저장이 거절됐을 때. 화면을 갈아엎지 않고 그 자리에 알린다. */
export function InlineError({ error }: { error: unknown }) {
  return (
    <p className={styles.inlineError} role="alert">
      {describe(error)}
    </p>
  )
}

export function Skeleton({ height, card = false }: { height?: number; card?: boolean }) {
  return (
    <div
      className={`${styles.skeleton} ${card ? styles.skeletonCard : ''}`}
      style={card ? undefined : { height: height ?? 20 }}
      aria-hidden="true"
    />
  )
}

export function SkeletonStack({ children }: { children: ReactNode }) {
  return <div className={styles.stack}>{children}</div>
}

export function describe(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof TypeError) return '네트워크에 연결할 수 없어요. 연결을 확인하고 다시 시도해 주세요.'
  if (error instanceof Error && error.message) return error.message
  return '일시적인 오류가 발생했습니다.'
}
