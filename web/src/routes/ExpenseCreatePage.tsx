import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { Button } from '../components/Button'
import { Chip, ChipRow } from '../components/Chip'
import { AmountInput, Field, TextInput } from '../components/Field'
import { SegmentedToggle } from '../components/SegmentedToggle'
import { InlineError } from '../components/States'
import { expensesApi } from '../lib/api/endpoints'
import { ApiError } from '../lib/api/client'
import { queryKeys } from '../lib/queryKeys'
import { CATEGORIES, CATEGORY_LABELS, todayIso, toPeriod } from '../lib/format'
import { useCoupleContext } from '../lib/auth/SessionProvider'
import { toRatioMode, usePresetPayerRate } from '../lib/burden'
import type { ExpenseCategory } from '../lib/api/types'
import styles from './ExpenseCreatePage.module.css'

type RatioMode = 'half' | 'sixFour' | 'custom'

/** 프리셋이 없으면 반반이다. 서버 기본값과 같다. */
const DEFAULT_BURDEN_RATE = 50

export function ExpenseCreatePage() {
  const { me, partner } = useCoupleContext()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [amount, setAmount] = useState<number | ''>('')
  const [memo, setMemo] = useState('')
  const [category, setCategory] = useState<ExpenseCategory>('GROCERY')
  const [payerId, setPayerId] = useState(me.userId)
  const [ratioMode, setRatioMode] = useState<RatioMode>('half')
  const [customRate, setCustomRate] = useState(50)
  // 사용자가 비율을 직접 고르면 그 뒤로는 프리셋이 끼어들지 않는다.
  const [ratioTouched, setRatioTouched] = useState(false)
  const [spentAt, setSpentAt] = useState(todayIso)

  // 저장되는 값은 언제나 "결제자 본인이 부담할 비율" 이다.
  // 비율을 직접 고르지 않았다면 카테고리 프리셋을 따라간다.
  // 프리셋이 없는 카테고리로 옮기면 반반으로 돌아온다 — 안 그러면 직전 카테고리의
  // 비율이 그대로 남아, 고른 적 없는 값으로 저장된다.
  const presetRate = usePresetPayerRate(category, payerId, me.userId)
  useEffect(() => {
    if (ratioTouched) return
    const rate = presetRate ?? DEFAULT_BURDEN_RATE
    setRatioMode(toRatioMode(rate))
    setCustomRate(rate)
  }, [presetRate, ratioTouched])

  const payerBurdenRate = ratioMode === 'half' ? 50 : ratioMode === 'sixFour' ? 60 : customRate

  const payer = payerId === me.userId ? me : partner
  const other = payerId === me.userId ? partner : me

  // Expense.partnerShare / payerShare 와 같은 식으로 계산한다.
  // 상대 몫을 먼저 내림하므로 나누어떨어지지 않는 1원은 결제자가 흡수한다.
  const split = useMemo(() => {
    const total = amount === '' ? 0 : amount
    const partnerShare = Math.floor((total * (100 - payerBurdenRate)) / 100)
    return { payerShare: total - partnerShare, partnerShare }
  }, [amount, payerBurdenRate])

  const create = useMutation({
    mutationFn: () =>
      expensesApi.create({
        payerId,
        amount: amount === '' ? 0 : amount,
        category,
        payerBurdenRate,
        spentAt,
        memo: memo.trim() || null,
      }),
    onSuccess: async (created) => {
      const period = toPeriod(new Date(created.spentAt))
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.expensesOfPeriod(period) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.summary(period) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.settlement(period) }),
      ])
      navigate('/', { replace: true })
    },
  })

  const error = create.error instanceof ApiError ? create.error : null
  const canSubmit = amount !== '' && amount > 0 && !create.isPending

  return (
    <AppScreen
      title="지출 추가"
      back="/"
      footer={
        <Button
          size="lg"
          block
          disabled={!canSubmit}
          loading={create.isPending}
          onClick={() => create.mutate()}
        >
          저장
        </Button>
      }
    >
      <div className={styles.amountBlock}>
        <p className={styles.amountLabel}>얼마를 썼나요?</p>
        <AmountInput value={amount} onChange={setAmount} autoFocus />
        {error?.fieldReason('amount') && <InlineError error={new Error(error.fieldReason('amount'))} />}
      </div>

      <Field label="내용" hint="비워 두면 카테고리 이름으로 보여요.">
        {(id) => (
          <TextInput
            id={id}
            value={memo}
            maxLength={255}
            placeholder="예: 이마트 장보기"
            onChange={(event) => setMemo(event.target.value)}
          />
        )}
      </Field>

      <div className={styles.group}>
        <p className={styles.groupLabel}>카테고리</p>
        <ChipRow>
          {CATEGORIES.map((value) => (
            <Chip key={value} selected={value === category} onClick={() => setCategory(value)}>
              {CATEGORY_LABELS[value]}
            </Chip>
          ))}
        </ChipRow>
      </div>

      <div className={styles.group}>
        <p className={styles.groupLabel}>누가 결제했나요?</p>
        <SegmentedToggle
          label="결제자"
          value={payerId}
          onChange={setPayerId}
          options={[
            { value: me.userId, label: '나' },
            ...(partner ? [{ value: partner.userId, label: partner.nickname }] : []),
          ]}
        />
      </div>

      <div className={styles.group}>
        <p className={styles.groupLabel}>
          부담 비율
          <span>{payer?.nickname ?? '결제자'} 기준</span>
        </p>
        <SegmentedToggle
          label="부담 비율"
          value={ratioMode}
          onChange={(mode) => {
            setRatioTouched(true)
            setRatioMode(mode)
          }}
          options={[
            { value: 'half', label: '5:5' },
            { value: 'sixFour', label: '6:4' },
            { value: 'custom', label: '직접' },
          ]}
        />

        {ratioMode === 'custom' && (
          <div className={styles.custom}>
            <input
              className={styles.slider}
              type="range"
              min={0}
              max={100}
              step={5}
              value={customRate}
              aria-label="결제자 부담 비율"
              onChange={(event) => {
                setRatioTouched(true)
                setCustomRate(Number(event.target.value))
              }}
            />
            <span className={styles.customValue}>{customRate}%</span>
          </div>
        )}

        <div className={styles.split}>
          <div className={styles.splitCell}>
            <span className={styles.splitName}>
              {payer?.nickname ?? '결제자'} ({payerBurdenRate}%)
            </span>
            <Amount value={split.payerShare} size="sm" tone="muted" />
          </div>
          <div className={styles.splitCell}>
            <span className={styles.splitName}>
              {other?.nickname ?? '상대'} ({100 - payerBurdenRate}%)
            </span>
            <Amount value={split.partnerShare} size="sm" tone="muted" />
          </div>
        </div>
      </div>

      <Field label="날짜" error={error?.fieldReason('spentAt')}>
        {(id) => (
          <TextInput
            id={id}
            type="date"
            value={spentAt}
            max={todayIso()}
            onChange={(event) => setSpentAt(event.target.value)}
          />
        )}
      </Field>

      {create.error && <InlineError error={create.error} />}
    </AppScreen>
  )
}
