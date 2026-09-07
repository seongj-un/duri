import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { Button } from '../components/Button'
import { Card, SectionTitle } from '../components/Card'
import { SegmentedToggle } from '../components/SegmentedToggle'
import { EmptyState, ErrorState, Skeleton, SkeletonStack } from '../components/States'
import { summariesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import {
  CATEGORY_COLORS,
  currentPeriod,
  formatPeriod,
  formatPeriodShort,
  formatWonWithUnit,
} from '../lib/format'
import styles from './TrendPage.module.css'

const RANGES = [
  { value: 3, label: '3개월' },
  { value: 6, label: '6개월' },
  { value: 12, label: '1년' },
]

export function TrendPage() {
  const [months, setMonths] = useState(6)
  const until = currentPeriod()

  const trend = useQuery({
    queryKey: queryKeys.trend(until, months),
    queryFn: () => summariesApi.trend({ until, months }),
  })

  return (
    <AppScreen title="지출 추이" back="/monthly">
      <SegmentedToggle
        label="조회 기간"
        options={RANGES}
        value={months}
        onChange={setMonths}
      />

      {trend.isPending && (
        <SkeletonStack>
          <Skeleton card />
          <Skeleton height={160} />
        </SkeletonStack>
      )}

      {trend.error && (
        <ErrorState
          error={trend.error}
          action={
            <Button variant="ghost" onClick={() => void trend.refetch()}>
              다시 불러오기
            </Button>
          }
        />
      )}

      {trend.data && trend.data.totalAmount === 0 && (
        <EmptyState
          icon="📈"
          headline="아직 그릴 게 없어요"
          detail={`${formatPeriod(trend.data.from)}부터 기록된 지출이 없습니다.`}
        />
      )}

      {trend.data && trend.data.totalAmount > 0 && (
        <>
          <Card className={styles.totals}>
            <span className={styles.label}>
              {formatPeriod(trend.data.from)} ~ {formatPeriod(trend.data.to)}
            </span>
            <Amount value={trend.data.totalAmount} size="hero" />
            <span className={styles.average}>
              월 평균 <Amount value={trend.data.monthlyAverage} size="sm" tone="muted" />
            </span>
          </Card>

          <SectionTitle>월별</SectionTitle>
          <MonthlyBars months={trend.data.months} amounts={trend.data.totalByMonth} />

          <SectionTitle aside={`${trend.data.categories.length}개`}>카테고리별</SectionTitle>
          <div className={styles.categories}>
            {trend.data.categories.map((row) => (
              <div key={row.category} className={styles.category}>
                <div className={styles.categoryHead}>
                  <span>
                    <span className={styles.categoryName}>{row.categoryName}</span>
                    <span className={styles.categoryRatio}>{row.ratio}%</span>
                  </span>
                  <Amount value={row.totalAmount} size="sm" tone="muted" />
                </div>
                <div className={styles.track}>
                  <span
                    className={styles.fill}
                    style={{
                      width: `${row.ratio}%`,
                      background: CATEGORY_COLORS[row.category],
                    }}
                  />
                </div>
                {row.peakMonth && hasDistinctPeak(row.monthlyAmounts) && (
                  <span className={styles.peak}>
                    {formatPeriodShort(row.peakMonth)}에 가장 많이 썼어요
                  </span>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </AppScreen>
  )
}

/**
 * 봉우리가 하나로 도드라질 때만 "가장 많이 쓴 달"을 알린다.
 *
 * 월세처럼 매달 같은 금액이면 1등이 있어도 의미가 없고,
 * 여행처럼 한 달에만 쓴 것도 "그 달에 가장 많이 썼다"는 말이 정보가 되지 않는다.
 */
function hasDistinctPeak(monthlyAmounts: number[]): boolean {
  const spent = monthlyAmounts.filter((amount) => amount > 0)
  if (spent.length < 2) return false

  const [highest, second] = [...spent].sort((a, b) => b - a)
  return highest > second
}

/**
 * 월별 총지출 막대.
 *
 * 가장 많이 쓴 달을 100% 로 두고 나머지를 그 비율로 그린다.
 * 절대 금액을 기준으로 하면 액수가 작은 커플의 그래프가 바닥에 깔려 변화가 안 보인다.
 */
function MonthlyBars({ months, amounts }: { months: string[]; amounts: number[] }) {
  const peak = Math.max(...amounts, 1)

  return (
    <div className={styles.bars}>
      {months.map((month, index) => {
        const amount = amounts[index]
        return (
          <div key={month} className={styles.column}>
            <span className={styles.columnValue}>
              {amount > 0 ? `${Math.round(amount / 10000)}만` : ''}
            </span>
            <div className={styles.columnTrack}>
              <span
                className={styles.columnFill}
                style={{ height: `${(amount / peak) * 100}%` }}
                title={`${formatPeriod(month)} ${formatWonWithUnit(amount)}`}
              />
            </div>
            <span className={styles.columnLabel}>{formatPeriodShort(month)}</span>
          </div>
        )
      })}
    </div>
  )
}
