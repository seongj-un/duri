import type { ReactNode } from 'react'
import { Amount } from './Amount'
import { Avatar } from './Avatar'
import { Card } from './Card'
import type { BalanceSummary, SettlementStatus } from '../lib/api/types'
import styles from './BalanceHero.module.css'

interface BalanceHeroProps {
  balance: BalanceSummary
  myUserId: number
  status?: SettlementStatus | null
  caption?: ReactNode
  children?: ReactNode
}

/**
 * 순잔액 카드. 방향은 "누가 누구에게" 로 말하고, 색으로 그게 나에게 +인지 −인지 보인다.
 * 금액은 언제나 0 이상이고 방향은 채권자·채무자로 표현되므로 부호를 만들어 붙이지 않는다.
 */
export function BalanceHero({ balance, myUserId, status, caption, children }: BalanceHeroProps) {
  const { netAmount, creditor, debtor } = balance
  const settled = netAmount === 0 || !creditor || !debtor

  const iAmCreditor = creditor?.userId === myUserId

  return (
    <Card variant="hero" className={styles.hero}>
      {status && (
        <span
          className={`${styles.badge} ${
            status === 'CONFIRMED' ? styles.badgeConfirmed : styles.badgeOpen
          }`}
        >
          {status === 'CONFIRMED' ? '정산 완료' : '정산 전'}
        </span>
      )}

      {settled ? (
        <>
          <p className={styles.settled}>주고받을 돈이 없어요</p>
          <p className={styles.caption}>{caption ?? '지금은 두 사람의 부담이 같아요.'}</p>
        </>
      ) : (
        <>
          <div className={styles.people}>
            <Avatar
              nickname={debtor.nickname}
              imageUrl={debtor.profileImageUrl}
              size="md"
              mine={!iAmCreditor}
            />
            <div className={styles.arrow} aria-hidden="true">
              <span className={styles.line} />
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path
                  d="M2 7h10m0 0-3.5-3.5M12 7l-3.5 3.5"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
            </div>
            <Avatar
              nickname={creditor.nickname}
              imageUrl={creditor.profileImageUrl}
              size="md"
              mine={iAmCreditor}
            />
          </div>

          <p className={styles.direction}>
            {iAmCreditor ? (
              <>
                <b>{debtor.nickname}</b>님에게 받을 돈
              </>
            ) : (
              <>
                <b>{creditor.nickname}</b>님에게 보낼 돈
              </>
            )}
          </p>

          <div className={styles.amount}>
            <Amount value={netAmount} size="hero" tone={iAmCreditor ? 'credit' : 'debit'} />
          </div>

          {caption && <p className={styles.caption}>{caption}</p>}
        </>
      )}

      {children}
    </Card>
  )
}
