import { useQuery } from '@tanstack/react-query'
import { burdenPresetsApi } from './api/endpoints'
import { queryKeys } from './queryKeys'
import type { ExpenseCategory } from './api/types'

/**
 * 카테고리 프리셋을 "결제자가 부담할 비율"로 바꿔 준다.
 *
 * 프리셋은 사람 기준(내가 몇 %)이고 지출에 저장되는 값은 결제자 기준이라,
 * 결제자가 상대일 때는 뒤집어야 한다. 이 변환이 없으면 "월세는 내가 30%"로 정해 두고
 * 상대가 결제한 달에 내 부담이 70% 로 뒤집힌다.
 *
 * 저장된 프리셋이 없으면 undefined 를 준다. 호출하는 쪽이 기본값을 정한다.
 */
export function usePresetPayerRate(
  category: ExpenseCategory,
  payerId: number,
  myUserId: number,
): number | undefined {
  const { data } = useQuery({
    queryKey: queryKeys.burdenPresets,
    queryFn: () => burdenPresetsApi.list(),
  })

  const preset = data?.find((it) => it.category === category)
  if (!preset || !preset.customized) return undefined

  const mine = preset.rates.find((rate) => rate.member.userId === myUserId)?.burdenRate ?? 50
  return payerId === myUserId ? mine : 100 - mine
}

/** 비율 값을 세그먼트 선택지로 되돌린다. */
export function toRatioMode(rate: number): 'half' | 'sixFour' | 'custom' {
  if (rate === 50) return 'half'
  if (rate === 60) return 'sixFour'
  return 'custom'
}
