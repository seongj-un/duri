import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { Button } from '../components/Button'
import { Card, SectionTitle } from '../components/Card'
import { DateHeading, ExpenseItem, ExpenseList } from '../components/ExpenseItem'
import { MonthNav } from '../components/MonthNav'
import { EmptyState, ErrorState, Skeleton, SkeletonStack } from '../components/States'
import { expensesApi, summariesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { currentPeriod, formatDate, formatPeriod } from '../lib/format'
import { useCoupleContext } from '../lib/auth/SessionProvider'
import type { Expense } from '../lib/api/types'
import styles from './MonthlyPage.module.css'

/** 2인 가계부의 한 달치는 이 안에 다 들어온다. 넘치면 아래 버튼으로 늘린다. */
const PAGE_SIZE = 200

export function MonthlyPage() {
  const { me, partner } = useCoupleContext()
  const navigate = useNavigate()
  const [period, setPeriod] = useState(currentPeriod)
  const [size, setSize] = useState(PAGE_SIZE)

  const summary = useQuery({
    queryKey: queryKeys.summary(period),
    queryFn: () => summariesApi.monthly(period),
  })

  const expenses = useQuery({
    queryKey: queryKeys.expenses(period, { size }),
    queryFn: () => expensesApi.list({ period, size }),
  })

  const byDate = useMemo(() => groupByDate(expenses.data?.content ?? []), [expenses.data])

  // 카테고리 막대를 결제자별로 나눠 칠하려면 그 달의 지출이 전부 있어야 한다.
  const complete = expenses.data ? !expenses.data.hasNext : false
  const paidByPayer = useMemo(
    () => (complete ? sumByCategoryAndPayer(expenses.data?.content ?? []) : null),
    [complete, expenses.data],
  )

  return (
    <AppScreen
      tabBar
      title="월별 내역"
      action={
        <Button variant="ghost" onClick={() => navigate('/trend')}>
          추이
        </Button>
      }
    >
      <MonthNav period={period} onChange={setPeriod} />

      {summary.isPending && <Skeleton card />}
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
          <Card className={styles.summary}>
            <span className={styles.summaryLabel}>{formatPeriod(period)} 총지출</span>
            <Amount value={summary.data.totalAmount} size="hero" />
            <span className={styles.count}>{summary.data.expenseCount}건</span>
          </Card>

          <SectionTitle>카테고리별</SectionTitle>

          {summary.data.categories.length === 0 ? (
            <EmptyState headline="이 달엔 지출이 없어요" detail="지출을 추가하면 여기에 쌓입니다." />
          ) : (
            <>
              {paidByPayer && partner && (
                <div className={styles.legend}>
                  <span className={styles.legendItem}>
                    <span className={styles.swatch} style={{ background: 'var(--brand)' }} />
                    내가 결제
                  </span>
                  <span className={styles.legendItem}>
                    <span className={styles.swatch} style={{ background: '#B9C2D4' }} />
                    {partner.nickname} 결제
                  </span>
                </div>
              )}

              <div className={styles.bars}>
                {summary.data.categories.map((category) => {
                  const mine = paidByPayer?.get(category.category)?.get(me.userId) ?? 0
                  const minePercent = category.amount > 0 ? (mine / category.amount) * 100 : 0
                  const widthPercent = (category.amount / summary.data.totalAmount) * 100

                  return (
                    <div key={category.category} className={styles.bar}>
                      <div className={styles.barHead}>
                        <span>
                          <span className={styles.barName}>{category.categoryName}</span>
                          <span className={styles.barRatio}>{category.ratio}%</span>
                        </span>
                        <Amount value={category.amount} size="sm" tone="muted" />
                      </div>
                      <div className={styles.track}>
                        {paidByPayer ? (
                          <>
                            <span
                              className={styles.segment}
                              style={{
                                width: `${(widthPercent * minePercent) / 100}%`,
                                background: 'var(--brand)',
                              }}
                            />
                            <span
                              className={styles.segment}
                              style={{
                                width: `${(widthPercent * (100 - minePercent)) / 100}%`,
                                background: '#B9C2D4',
                              }}
                            />
                          </>
                        ) : (
                          <span
                            className={styles.segment}
                            style={{ width: `${widthPercent}%`, background: 'var(--brand)' }}
                          />
                        )}
                      </div>
                    </div>
                  )
                })}
              </div>
            </>
          )}
        </>
      )}

      <SectionTitle aside={expenses.data ? `${expenses.data.totalElements}건` : undefined}>
        날짜별
      </SectionTitle>

      {expenses.isPending && (
        <SkeletonStack>
          <Skeleton height={68} />
          <Skeleton height={68} />
          <Skeleton height={68} />
        </SkeletonStack>
      )}

      {expenses.error && <ErrorState error={expenses.error} />}

      {expenses.data &&
        (byDate.length === 0 ? (
          <EmptyState
            headline="아직 이 달의 지출이 없어요"
            detail="첫 지출을 추가하면 순잔액이 바로 계산됩니다."
            action={<Button onClick={() => navigate('/expenses/new')}>지출 추가하기</Button>}
          />
        ) : (
          <>
            {byDate.map(([date, items]) => (
              <div key={date}>
                <DateHeading
                  aside={
                    <Amount
                      value={items.reduce((sum, item) => sum + item.amount, 0)}
                      size="sm"
                      tone="muted"
                    />
                  }
                >
                  {formatDate(date)}
                </DateHeading>
                <ExpenseList>
                  {items.map((expense) => (
                    <ExpenseItem key={expense.expenseId} expense={expense} myUserId={me.userId} />
                  ))}
                </ExpenseList>
              </div>
            ))}

            {expenses.data.hasNext && (
              <div className={styles.loadMore}>
                <Button variant="ghost" block onClick={() => setSize((it) => it + PAGE_SIZE)}>
                  더 보기
                </Button>
              </div>
            )}
          </>
        ))}
    </AppScreen>
  )
}

/** 최신 날짜가 위로 오게 묶는다. */
function groupByDate(expenses: Expense[]): [string, Expense[]][] {
  const groups = new Map<string, Expense[]>()
  for (const expense of expenses) {
    const bucket = groups.get(expense.spentAt)
    if (bucket) bucket.push(expense)
    else groups.set(expense.spentAt, [expense])
  }
  return [...groups.entries()].sort((a, b) => b[0].localeCompare(a[0]))
}

/** 카테고리 → 결제자 → 결제 금액. 막대를 누가 냈는지로 갈라 칠하는 데 쓴다. */
function sumByCategoryAndPayer(expenses: Expense[]): Map<string, Map<number, number>> {
  const result = new Map<string, Map<number, number>>()
  for (const expense of expenses) {
    const byPayer = result.get(expense.category) ?? new Map<number, number>()
    byPayer.set(expense.payer.userId, (byPayer.get(expense.payer.userId) ?? 0) + expense.amount)
    result.set(expense.category, byPayer)
  }
  return result
}
