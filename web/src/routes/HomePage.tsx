import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { BalanceHero } from '../components/BalanceHero'
import { SectionTitle } from '../components/Card'
import { ExpenseItem, ExpenseList } from '../components/ExpenseItem'
import { Fab } from '../components/Fab'
import { MonthNav } from '../components/MonthNav'
import { Button } from '../components/Button'
import { EmptyState, ErrorState, Skeleton, SkeletonStack } from '../components/States'
import { expensesApi, summariesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { currentPeriod } from '../lib/format'
import { useCoupleContext } from '../lib/auth/SessionProvider'
import styles from './HomePage.module.css'

const RECENT_COUNT = 5

export function HomePage() {
  const { couple, me } = useCoupleContext()
  const navigate = useNavigate()
  const [period, setPeriod] = useState(currentPeriod)

  const summary = useQuery({
    queryKey: queryKeys.summary(period),
    queryFn: () => summariesApi.monthly(period),
  })

  const recent = useQuery({
    queryKey: queryKeys.expenses(period, { size: RECENT_COUNT }),
    queryFn: () => expensesApi.list({ period, size: RECENT_COUNT }),
  })

  return (
    <AppScreen
      tabBar
      action={
        <div className={styles.heading}>
          <span className={styles.brand}>PairPay</span>
          <span className={styles.spaceName}>{couple.name}</span>
        </div>
      }
    >
      <MonthNav period={period} onChange={setPeriod} />

      {summary.isPending && (
        <SkeletonStack>
          <Skeleton card />
          <Skeleton height={78} />
        </SkeletonStack>
      )}

      {summary.error && (
        <ErrorState
          error={summary.error}
          action={
            <Button variant="ghost" onClick={() => void summary.refetch()}>
              다시 불러오기
            </Button>
          }
        />
      )}

      {summary.data && (
        <>
          <BalanceHero
            balance={summary.data.balance}
            myUserId={me.userId}
            status={summary.data.settlementStatus}
            caption={
              summary.data.settlementStatus === 'CONFIRMED'
                ? '이 달은 정산이 확정돼 금액이 고정됐어요.'
                : '지출을 넣을 때마다 실시간으로 바뀝니다.'
            }
          />

          <div className={styles.totals}>
            <div className={styles.total}>
              <span className={styles.totalLabel}>이번 달 총지출</span>
              <Amount value={summary.data.totalAmount} size="lg" />
            </div>
            <div className={styles.total}>
              <span className={styles.totalLabel}>내가 낸 돈</span>
              <Amount value={myPaid(summary.data.members, me.userId)} size="lg" tone="muted" />
            </div>
          </div>
        </>
      )}

      <SectionTitle
        aside={
          recent.data && recent.data.totalElements > RECENT_COUNT ? (
            <Link className={styles.more} to="/monthly">
              전체 {recent.data.totalElements}건 보기
            </Link>
          ) : undefined
        }
      >
        최근 지출
      </SectionTitle>

      {recent.isPending && (
        <SkeletonStack>
          <Skeleton height={68} />
          <Skeleton height={68} />
        </SkeletonStack>
      )}

      {recent.error && <ErrorState error={recent.error} />}

      {recent.data &&
        (recent.data.content.length === 0 ? (
          <EmptyState
            headline="아직 이 달의 지출이 없어요"
            detail="첫 지출을 추가하면 순잔액이 바로 계산됩니다."
            action={<Button onClick={() => navigate('/expenses/new')}>첫 지출 추가하기</Button>}
          />
        ) : (
          <ExpenseList>
            {recent.data.content.map((expense) => (
              <ExpenseItem key={expense.expenseId} expense={expense} myUserId={me.userId} showDate />
            ))}
          </ExpenseList>
        ))}

      <Fab to="/expenses/new" label="지출 추가" />
    </AppScreen>
  )
}

function myPaid(members: { member: { userId: number }; paidAmount: number }[], myUserId: number) {
  return members.find((it) => it.member.userId === myUserId)?.paidAmount ?? 0
}
