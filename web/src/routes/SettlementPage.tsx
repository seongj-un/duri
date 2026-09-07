import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { BalanceHero } from '../components/BalanceHero'
import { Button } from '../components/Button'
import { SectionTitle } from '../components/Card'
import { MonthNav } from '../components/MonthNav'
import { EmptyState, ErrorState, InlineError, Skeleton, SkeletonStack } from '../components/States'
import { settlementsApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { currentPeriod, formatPeriod, shiftPeriod } from '../lib/format'
import { useCoupleContext } from '../lib/auth/SessionProvider'
import { useCopy } from '../lib/clipboard'
import styles from './SettlementPage.module.css'

export function SettlementPage() {
  const { me } = useCoupleContext()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { copied, copy } = useCopy()

  // 정산은 보통 지난달을 마감하는 일이라 지난달로 열어 준다.
  const [period, setPeriod] = useState(() => shiftPeriod(currentPeriod(), -1))

  const settlement = useQuery({
    queryKey: queryKeys.settlement(period),
    queryFn: () => settlementsApi.get(period),
  })

  const history = useQuery({
    queryKey: queryKeys.settlementHistory,
    queryFn: settlementsApi.history,
  })

  const confirm = useMutation({
    mutationFn: () => settlementsApi.confirm(period),
    onSuccess: async (confirmed) => {
      queryClient.setQueryData(queryKeys.settlement(period), confirmed)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.summary(period) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.expensesOfPeriod(period) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.settlementHistory }),
      ])
    },
  })

  const data = settlement.data
  const iAmCreditor = data?.creditor?.userId === me.userId
  const confirmed = data?.status === 'CONFIRMED'

  return (
    <AppScreen tabBar title="정산">
      <MonthNav period={period} onChange={setPeriod} />

      {settlement.isPending && (
        <SkeletonStack>
          <Skeleton card />
          <Skeleton height={54} />
        </SkeletonStack>
      )}

      {settlement.error && (
        <ErrorState
          error={settlement.error}
          action={
            <Button variant="ghost" onClick={() => void settlement.refetch()}>
              다시 불러오기
            </Button>
          }
        />
      )}

      {data && (
        <>
          <BalanceHero
            balance={{ netAmount: data.netAmount, creditor: data.creditor, debtor: data.debtor }}
            myUserId={me.userId}
            status={data.status}
            caption={
              confirmed
                ? '이 달의 지출은 잠겨 더 이상 수정할 수 없어요.'
                : `${data.expenseCount}건 · 총 ${data.totalAmount.toLocaleString('ko-KR')}원 기준으로 계산했어요.`
            }
          >
            {data.transfer && (
              <div className={styles.transfer}>
                <div className={styles.copyRow}>
                  {/* 보내기 전에 확인해야 하는 값들이라 한 줄로 줄여 가리지 않는다. */}
                  <span className={styles.account}>
                    <span className={styles.accountNo}>
                      {data.transfer.bankName} {data.transfer.accountNo}
                    </span>
                    <span className={styles.holder}>
                      {data.transfer.holderName} · {data.transfer.amount.toLocaleString('ko-KR')}원
                    </span>
                  </span>
                  <Button variant="ghost" onClick={() => void copy(data.transfer!.copyText)}>
                    {copied ? '복사됨' : '복사'}
                  </Button>
                </div>
                <p className={styles.hint}>
                  송금은 각자 은행 앱에서 해 주세요. 계좌와 금액이 한 줄로 복사돼요.
                </p>
              </div>
            )}

            {!data.transfer && data.netAmount > 0 && iAmCreditor && (
              <div className={styles.notice}>
                <p className={styles.noticeText}>
                  내 계좌를 등록해 두면 상대가 복사해서 바로 보낼 수 있어요.
                </p>
                <Button variant="ghost" onClick={() => navigate('/account')}>
                  계좌 등록하기
                </Button>
              </div>
            )}

            {!data.transfer && data.netAmount > 0 && !iAmCreditor && (
              <div className={styles.notice}>
                <p className={styles.noticeText}>
                  {data.creditor?.nickname}님이 아직 계좌를 등록하지 않았어요. 등록하면 여기에 뜹니다.
                </p>
              </div>
            )}
          </BalanceHero>

          <div className={styles.actions}>
            {confirmed ? (
              <p className={styles.meta}>
                {data.confirmedBy?.nickname}님이{' '}
                {data.confirmedAt &&
                  new Date(data.confirmedAt).toLocaleDateString('ko-KR', {
                    month: 'long',
                    day: 'numeric',
                  })}
                에 확정했어요.
              </p>
            ) : (
              <>
                <Button
                  size="lg"
                  block
                  loading={confirm.isPending}
                  onClick={() => confirm.mutate()}
                >
                  {formatPeriod(period)} 정산 확정
                </Button>
                <p className={styles.meta}>
                  확정하면 이 달의 지출이 잠겨 수정할 수 없어요.
                  <br />
                  두 사람 중 누가 눌러도 결과는 같습니다.
                </p>
              </>
            )}
          </div>

          {confirm.error && <InlineError error={confirm.error} />}
        </>
      )}

      <SectionTitle>지난 정산</SectionTitle>

      {history.isPending && <Skeleton height={68} />}
      {history.error && <ErrorState error={history.error} />}

      {history.data &&
        (history.data.length === 0 ? (
          <EmptyState
            icon="📅"
            headline="아직 확정한 정산이 없어요"
            detail="한 달이 끝나면 여기에 기록이 남습니다."
          />
        ) : (
          <div className={styles.history}>
            {history.data.map((row) => (
              <button
                key={row.settlementId}
                type="button"
                className={styles.historyRow}
                onClick={() => setPeriod(row.period)}
              >
                <span className={styles.historyLabel}>
                  <span className={styles.historyPeriod}>{formatPeriod(row.period)}</span>
                  <span className={styles.historyDirection}>
                    {row.netAmount === 0
                      ? '주고받을 돈 없음'
                      : `${row.debtor?.nickname} → ${row.creditor?.nickname}`}
                  </span>
                </span>
                <Amount
                  value={row.netAmount}
                  size="sm"
                  tone={row.creditor?.userId === me.userId ? 'credit' : 'muted'}
                />
              </button>
            ))}
          </div>
        ))}
    </AppScreen>
  )
}
