import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { ErrorState, InlineError, Skeleton, SkeletonStack } from '../components/States'
import { burdenPresetsApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { useCoupleContext } from '../lib/auth/SessionProvider'
import type { BurdenPreset, ExpenseCategory } from '../lib/api/types'
import styles from './BurdenPresetPage.module.css'

/** 카테고리 → 내가 부담할 비율(%). */
type Rates = Record<string, number>

export function BurdenPresetPage() {
  const { me, partner } = useCoupleContext()
  const queryClient = useQueryClient()

  const presets = useQuery({
    queryKey: queryKeys.burdenPresets,
    queryFn: () => burdenPresetsApi.list(),
  })

  const [rates, setRates] = useState<Rates>({})

  // 서버 값이 오면 그것을 폼의 출발점으로 삼는다.
  useEffect(() => {
    if (!presets.data) return
    setRates(Object.fromEntries(presets.data.map((it) => [it.category, myRate(it, me.userId)])))
  }, [presets.data, me.userId])

  const changed = presets.data
    ? presets.data.filter((it) => rates[it.category] !== myRate(it, me.userId))
    : []

  const save = useMutation({
    mutationFn: () =>
      burdenPresetsApi.save({
        // 바꾼 것만 보낸다. 서버는 내 비율만 받아 상대 몫을 채운다.
        presets: changed.map((it) => ({
          category: it.category as ExpenseCategory,
          userId: me.userId,
          burdenRate: rates[it.category],
        })),
      }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.burdenPresets }),
  })

  return (
    <AppScreen
      title="부담 비율"
      back="/settings"
      footer={
        <Button
          size="lg"
          block
          disabled={changed.length === 0 || save.isPending}
          loading={save.isPending}
          onClick={() => save.mutate()}
        >
          {/* 아직 아무것도 저장하지 않았으므로 "저장됨" 이라고 말하지 않는다. */}
          {changed.length > 0 ? `${changed.length}개 저장` : '변경사항 없음'}
        </Button>
      }
    >
      <p className={styles.lead}>
        지출을 넣을 때 비율을 고르지 않으면 여기서 정한 값을 따라요. 사람 기준이라 이번 달 카드가
        누구 것이든 부담은 그대로입니다.
      </p>

      {presets.isPending && (
        <SkeletonStack>
          <Skeleton height={72} />
          <Skeleton height={72} />
          <Skeleton height={72} />
        </SkeletonStack>
      )}

      {presets.error && (
        <ErrorState
          error={presets.error}
          action={
            <Button variant="ghost" onClick={() => void presets.refetch()}>
              다시 불러오기
            </Button>
          }
        />
      )}

      {save.error && <InlineError error={save.error} />}

      {presets.data && (
        <ul className={styles.list}>
          {presets.data.map((preset) => {
            const mine = rates[preset.category] ?? myRate(preset, me.userId)
            const dirty = mine !== myRate(preset, me.userId)

            return (
              <li key={preset.category} className={styles.row}>
                <div className={styles.head}>
                  <span className={styles.name}>
                    {preset.categoryName}
                    {dirty && <span className={styles.dirty} aria-label="변경됨" />}
                  </span>
                  <span className={styles.share}>
                    나 <b>{mine}%</b>
                    {partner && (
                      <>
                        {' · '}
                        {partner.nickname} <b>{100 - mine}%</b>
                      </>
                    )}
                  </span>
                </div>
                <input
                  className={styles.slider}
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={mine}
                  aria-label={`${preset.categoryName} 내 부담 비율`}
                  onChange={(event) =>
                    setRates((prev) => ({ ...prev, [preset.category]: Number(event.target.value) }))
                  }
                />
              </li>
            )
          })}
        </ul>
      )}
    </AppScreen>
  )
}

/** 응답은 두 사람의 비율을 함께 주므로 그중 내 몫을 꺼낸다. */
function myRate(preset: BurdenPreset, myUserId: number): number {
  return preset.rates.find((rate) => rate.member.userId === myUserId)?.burdenRate ?? 50
}
