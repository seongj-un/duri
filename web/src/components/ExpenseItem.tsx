import type { ReactNode } from 'react'
import { Amount } from './Amount'
import { Avatar } from './Avatar'
import { formatDate } from '../lib/format'
import type { Expense } from '../lib/api/types'
import styles from './ExpenseItem.module.css'

export function ExpenseList({ children }: { children: ReactNode }) {
  return <div className={styles.list}>{children}</div>
}

export function DateHeading({ children, aside }: { children: ReactNode; aside?: ReactNode }) {
  return (
    <div className={styles.dateHeading}>
      <span>{children}</span>
      {aside}
    </div>
  )
}

interface ExpenseItemProps {
  expense: Expense
  myUserId: number
  /** 날짜로 묶이지 않은 목록(홈의 최근 지출)에서는 행마다 날짜가 필요하다. */
  showDate?: boolean
  onClick?: () => void
}

export function ExpenseItem({ expense, myUserId, showDate = false, onClick }: ExpenseItemProps) {
  const paidByMe = expense.payer.userId === myUserId

  return (
    <button type="button" className={styles.item} onClick={onClick} disabled={!onClick}>
      <Avatar
        nickname={expense.payer.nickname}
        imageUrl={expense.payer.profileImageUrl}
        size="md"
        mine={paidByMe}
      />

      <div className={styles.main}>
        <div className={styles.title}>{expense.memo || expense.categoryName}</div>
        <div className={styles.meta}>
          <span>{paidByMe ? '내가 결제' : `${expense.payer.nickname} 결제`}</span>
          <span className={styles.dot}>{burdenLabel(expense.payerBurdenRate, paidByMe)}</span>
        </div>
      </div>

      <div className={styles.right}>
        <Amount value={expense.amount} size="md" />
        {showDate && <span className={styles.date}>{formatDate(expense.spentAt)}</span>}
        {expense.locked && (
          <span className={styles.lock} title="정산이 확정되어 수정할 수 없어요">
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
              <path
                d="M3.5 5V3.75a2.5 2.5 0 0 1 5 0V5M2.75 5h6.5v5.25h-6.5z"
                stroke="currentColor"
                strokeWidth="1.1"
                strokeLinejoin="round"
              />
            </svg>
          </span>
        )}
      </div>
    </button>
  )
}

/** 비율은 결제자 기준으로 저장되지만, 읽는 사람 기준으로 뒤집어 보여준다. */
function burdenLabel(payerBurdenRate: number, paidByMe: boolean): string {
  const mine = paidByMe ? payerBurdenRate : 100 - payerBurdenRate
  if (mine === 50) return '반반'
  return `내 부담 ${mine}%`
}
