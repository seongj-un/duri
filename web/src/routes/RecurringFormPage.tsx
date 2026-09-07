import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Amount } from '../components/Amount'
import { Button } from '../components/Button'
import { Chip, ChipRow } from '../components/Chip'
import { AmountInput, Field, TextInput } from '../components/Field'
import { SegmentedToggle } from '../components/SegmentedToggle'
import { ErrorState, InlineError, Skeleton, SkeletonStack } from '../components/States'
import { recurringExpensesApi } from '../lib/api/endpoints'
import { ApiError } from '../lib/api/client'
import { queryKeys } from '../lib/queryKeys'
import { CATEGORIES, CATEGORY_LABELS, todayIso } from '../lib/format'
import { useCoupleContext } from '../lib/auth/SessionProvider'
import { toRatioMode, usePresetPayerRate } from '../lib/burden'
import type { ExpenseCategory } from '../lib/api/types'
import styles from './RecurringFormPage.module.css'

type RatioMode = 'half' | 'sixFour' | 'custom'

/** 프리셋이 없으면 반반이다. 서버 기본값과 같다. */
const DEFAULT_BURDEN_RATE = 50

/** 반복지출 등록과 수정은 입력이 같아 한 화면을 쓴다. */
export function RecurringFormPage() {
  const { recurringExpenseId } = useParams()
  const editing = recurringExpenseId !== undefined
  const targetId = Number(recurringExpenseId)

  const { me, partner } = useCoupleContext()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // 단건 조회 API 가 없다. 목록에서 찾는다 — 주소로 바로 들어와도 목록을 받아 오면 채워진다.
  const list = useQuery({
    queryKey: queryKeys.recurringExpenses,
    queryFn: () => recurringExpensesApi.list(),
    enabled: editing,
  })
  const target = editing
    ? list.data?.find((item) => item.recurringExpenseId === targetId)
    : undefined

  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState<number | ''>('')
  const [category, setCategory] = useState<ExpenseCategory>('RENT')
  const [payerId, setPayerId] = useState(me.userId)
  const [ratioMode, setRatioMode] = useState<RatioMode>('half')
  const [customRate, setCustomRate] = useState(50)
  // 사용자가 비율을 직접 고르면 그 뒤로는 프리셋이 끼어들지 않는다.
  const [ratioTouched, setRatioTouched] = useState(false)
  const [dayOfMonth, setDayOfMonth] = useState('1')
  const [startsOn, setStartsOn] = useState(todayIso)
  const [endsOn, setEndsOn] = useState('')

  // 수정 화면은 서버 값이 도착한 뒤에 폼을 채운다.
  useEffect(() => {
    if (!target) return
    setTitle(target.title)
    setAmount(target.amount)
    setCategory(target.category)
    setPayerId(target.payer.userId)
    setRatioMode(
      target.payerBurdenRate === 50 ? 'half' : target.payerBurdenRate === 60 ? 'sixFour' : 'custom',
    )
    setCustomRate(target.payerBurdenRate)
    setRatioTouched(true)
    setDayOfMonth(String(target.dayOfMonth))
    setStartsOn(target.startsOn)
    setEndsOn(target.endsOn ?? '')
  }, [target])

  // 비율을 직접 고르지 않았다면 카테고리 프리셋을 따라간다.
  // 프리셋이 없는 카테고리로 옮기면 반반으로 돌아온다 — 안 그러면 직전 카테고리의
  // 비율이 그대로 남아, 고른 적 없는 값으로 저장된다.
  const presetRate = usePresetPayerRate(category, payerId, me.userId)
  useEffect(() => {
    if (editing) return
    if (ratioTouched) return
    const rate = presetRate ?? DEFAULT_BURDEN_RATE
    setRatioMode(toRatioMode(rate))
    setCustomRate(rate)
  }, [presetRate, ratioTouched])

  const payerBurdenRate = ratioMode === 'half' ? 50 : ratioMode === 'sixFour' ? 60 : customRate
  const payer = payerId === me.userId ? me : partner
  const other = payerId === me.userId ? partner : me

  // 서버(Expense.partnerShare)와 같은 식. 상대 몫을 먼저 내림해 남는 1원은 결제자가 흡수한다.
  const split = useMemo(() => {
    const total = amount === '' ? 0 : amount
    const partnerShare = Math.floor((total * (100 - payerBurdenRate)) / 100)
    return { payerShare: total - partnerShare, partnerShare }
  }, [amount, payerBurdenRate])

  const invalidate = () => queryClient.invalidateQueries({ queryKey: queryKeys.recurringExpenses })

  const save = useMutation({
    mutationFn: () => {
      const body = {
        payerId,
        title: title.trim(),
        amount: amount === '' ? 0 : amount,
        category,
        payerBurdenRate,
        dayOfMonth: Number(dayOfMonth),
        startsOn,
        endsOn: endsOn || null,
      }
      return editing
        ? recurringExpensesApi.update(targetId, body)
        : recurringExpensesApi.create(body)
    },
    onSuccess: async () => {
      await invalidate()
      navigate('/recurring', { replace: true })
    },
  })

  const remove = useMutation({
    mutationFn: () => recurringExpensesApi.remove(targetId),
    onSuccess: async () => {
      await invalidate()
      navigate('/recurring', { replace: true })
    },
  })

  const error = save.error instanceof ApiError ? save.error : null
  const canSubmit =
    title.trim() !== '' && amount !== '' && amount > 0 && !save.isPending && !remove.isPending

  if (editing && list.isPending) {
    return (
      <AppScreen title="반복지출 수정" back="/recurring">
        <SkeletonStack>
          <Skeleton height={64} />
          <Skeleton height={64} />
        </SkeletonStack>
      </AppScreen>
    )
  }

  if (editing && list.data && !target) {
    return (
      <AppScreen title="반복지출 수정" back="/recurring">
        <ErrorState error={new Error('이 반복지출을 찾을 수 없습니다.')} />
      </AppScreen>
    )
  }

  return (
    <AppScreen
      title={editing ? '반복지출 수정' : '반복지출 추가'}
      back="/recurring"
      footer={
        <Button
          size="lg"
          block
          disabled={!canSubmit}
          loading={save.isPending}
          onClick={() => save.mutate()}
        >
          저장
        </Button>
      }
    >
      <Field label="이름" hint="지출 목록에 이 이름으로 남아요.">
        {(id) => (
          <TextInput
            id={id}
            value={title}
            maxLength={50}
            placeholder="예: 월세"
            autoFocus={!editing}
            onChange={(event) => setTitle(event.target.value)}
          />
        )}
      </Field>

      <Field label="금액" error={error?.fieldReason('amount')}>
        {(id) => <AmountInput id={id} value={amount} onChange={setAmount} />}
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
        <p className={styles.groupLabel}>누가 결제하나요?</p>
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

      <Field
        label="발생일"
        hint="매월 이 날에 지출이 만들어져요. 없는 달이 생기지 않도록 1~28일 중에서 고릅니다."
        error={error?.fieldReason('dayOfMonth')}
      >
        {(id) => (
          <div className={styles.dayRow}>
            <span className={styles.dayPrefix}>매월</span>
            <TextInput
              id={id}
              type="number"
              min={1}
              max={28}
              inputMode="numeric"
              value={dayOfMonth}
              className={styles.dayInput}
              onChange={(event) => setDayOfMonth(event.target.value)}
            />
            <span className={styles.dayUnit}>일</span>
          </div>
        )}
      </Field>

      <Field label="시작일" hint="이 날짜 이후의 발생일부터 만들어져요.">
        {(id) => (
          <TextInput
            id={id}
            type="date"
            value={startsOn}
            onChange={(event) => setStartsOn(event.target.value)}
          />
        )}
      </Field>

      <Field label="종료일" hint="구독 해지일처럼 끝나는 날이 있으면 넣어 주세요. 비우면 계속됩니다.">
        {(id) => (
          <TextInput
            id={id}
            type="date"
            value={endsOn}
            min={startsOn}
            onChange={(event) => setEndsOn(event.target.value)}
          />
        )}
      </Field>

      {save.error && <InlineError error={save.error} />}

      {editing && (
        <div className={styles.danger}>
          {/* 이미 지출을 만든 정의는 서버가 409 로 막고 중지를 안내한다. */}
          <Button
            variant="danger"
            block
            loading={remove.isPending}
            onClick={() => remove.mutate()}
          >
            삭제
          </Button>
          {remove.error && <InlineError error={remove.error} />}
        </div>
      )}
    </AppScreen>
  )
}
