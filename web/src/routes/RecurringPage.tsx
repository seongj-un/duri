import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { Button } from '../components/Button'
import { EmptyState, ErrorState, InlineError, Skeleton, SkeletonStack } from '../components/States'
import { recurringExpensesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { formatDate } from '../lib/format'
import type { RecurringExpense } from '../lib/api/types'
import styles from './RecurringPage.module.css'

export function RecurringPage() {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const recurring = useQuery({
    queryKey: queryKeys.recurringExpenses,
    queryFn: () => recurringExpensesApi.list(),
  })

  const setActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      recurringExpensesApi.setActive(id, active),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.recurringExpenses }),
  })

  return (
    <AppScreen
      title="반복지출"
      back="/settings"
      action={
        <Button variant="ghost" onClick={() => navigate('/recurring/new')}>
          추가
        </Button>
      }
    >
      <p className={styles.lead}>
        등록해 두면 매달 발생일에 지출이 저절로 만들어져요. 만들어진 지출은 직접 넣은 것과 똑같이
        고치거나 지울 수 있습니다.
      </p>

      {recurring.isPending && (
        <SkeletonStack>
          <Skeleton height={92} />
          <Skeleton height={92} />
        </SkeletonStack>
      )}

      {recurring.error && (
        <ErrorState
          error={recurring.error}
          action={
            <Button variant="ghost" onClick={() => void recurring.refetch()}>
              다시 불러오기
            </Button>
          }
        />
      )}

      {setActive.error && <InlineError error={setActive.error} />}

      {recurring.data &&
        (recurring.data.length === 0 ? (
          <EmptyState
            icon="🔁"
            headline="아직 반복지출이 없어요"
            detail="월세나 구독료처럼 매달 같은 날 나가는 지출을 등록해 보세요."
            action={<Button onClick={() => navigate('/recurring/new')}>반복지출 추가</Button>}
          />
        ) : (
          <ul className={styles.list}>
            {recurring.data.map((item) => (
              <li key={item.recurringExpenseId}>
                <div className={`${styles.item} ${item.active ? '' : styles.paused}`}>
                  <button
                    type="button"
                    className={styles.body}
                    onClick={() => navigate(`/recurring/${item.recurringExpenseId}`)}
                  >
                    <span className={styles.head}>
                      <span className={styles.title}>{item.title}</span>
                      <Amount value={item.amount} size="sm" />
                    </span>
                    <span className={styles.meta}>
                      매월 {item.dayOfMonth}일 · {item.categoryName} · {item.payer.nickname} 결제
                    </span>
                  </button>

                  <div className={styles.footer}>
                    <span className={styles.next}>{describeNext(item)}</span>
                    <Button
                      variant="quiet"
                      onClick={() =>
                        setActive.mutate({ id: item.recurringExpenseId, active: !item.active })
                      }
                      disabled={setActive.isPending}
                    >
                      {item.active ? '중지' : '재개'}
                    </Button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        ))}
    </AppScreen>
  )
}

/** 다음에 언제 만들어지는지가 이 화면에서 가장 궁금한 정보다. */
function describeNext(item: RecurringExpense): string {
  if (!item.active) return '중지됨 · 새로 만들지 않아요'
  if (!item.nextDueDate) return '종료됨 · 더 만들 것이 없어요'
  return `다음 ${formatDate(item.nextDueDate)}`
}
