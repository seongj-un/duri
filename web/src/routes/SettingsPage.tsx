import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { Card, SectionTitle } from '../components/Card'
import { Field, TextInput } from '../components/Field'
import { InlineError } from '../components/States'
import { couplesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { useCoupleContext, useSession } from '../lib/auth/SessionProvider'
import styles from './SettingsPage.module.css'

const MENU = [
  { to: '/account', label: '내 계좌', detail: '정산할 때 상대에게 보여줄 입금 계좌' },
  { to: '/recurring', label: '반복지출', detail: '월세·공과금·구독처럼 매달 나가는 지출' },
  { to: '/burden-presets', label: '부담 비율', detail: '카테고리마다 기본으로 나눌 비율' },
]

export function SettingsPage() {
  const { couple, me, partner } = useCoupleContext()
  const { signOut } = useSession()
  const queryClient = useQueryClient()

  const [name, setName] = useState(couple.name)
  const [settlementDay, setSettlementDay] = useState(String(couple.settlementDay))

  const changed = name.trim() !== couple.name || Number(settlementDay) !== couple.settlementDay

  const save = useMutation({
    mutationFn: () =>
      couplesApi.update({ name: name.trim(), settlementDay: Number(settlementDay) }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.couple }),
  })

  return (
    <AppScreen tabBar title="설정">
      <Card className={styles.pair}>
        <span className={styles.pairLabel}>함께 쓰는 사람</span>
        <span className={styles.pairNames}>
          {me.nickname}
          {partner && ` · ${partner.nickname}`}
        </span>
      </Card>

      <SectionTitle>커플 space</SectionTitle>

      <Field label="이름">
        {(id) => (
          <TextInput
            id={id}
            value={name}
            maxLength={50}
            onChange={(event) => setName(event.target.value)}
          />
        )}
      </Field>

      <Field
        label="정산 기준일"
        hint="이 날 지난달 정산을 하자고 알려 드려요. 없는 달이 생기지 않도록 1~28일 중에서 고릅니다."
      >
        {(id) => (
          <div className={styles.dayRow}>
            <TextInput
              id={id}
              type="number"
              min={1}
              max={28}
              inputMode="numeric"
              value={settlementDay}
              className={styles.dayInput}
              onChange={(event) => setSettlementDay(event.target.value)}
            />
            <span className={styles.dayUnit}>일</span>
          </div>
        )}
      </Field>

      {save.error && <InlineError error={save.error} />}

      <Button
        block
        variant="ghost"
        disabled={!changed || !name.trim() || save.isPending}
        loading={save.isPending}
        onClick={() => save.mutate()}
      >
        {/* 아직 아무것도 저장하지 않았으므로 "저장됨" 이라고 말하지 않는다. */}
        {changed ? '저장' : '변경사항 없음'}
      </Button>

      <SectionTitle>관리</SectionTitle>

      <nav className={styles.menu}>
        {MENU.map((item) => (
          <Link key={item.to} to={item.to} className={styles.menuItem}>
            <span className={styles.menuText}>
              <span className={styles.menuLabel}>{item.label}</span>
              <span className={styles.menuDetail}>{item.detail}</span>
            </span>
            <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <path
                d="m8 5 5 5-5 5"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </Link>
        ))}
      </nav>

      <div className={styles.signOut}>
        <Button variant="quiet" block onClick={() => void signOut()}>
          로그아웃
        </Button>
      </div>
    </AppScreen>
  )
}
