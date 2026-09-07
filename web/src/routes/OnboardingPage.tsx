import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { Field, TextInput } from '../components/Field'
import { InlineError } from '../components/States'
import { couplesApi } from '../lib/api/endpoints'
import { ApiError } from '../lib/api/client'
import { queryKeys } from '../lib/queryKeys'
import { useSession } from '../lib/auth/SessionProvider'
import styles from './OnboardingPage.module.css'

/** 로그인은 했지만 아직 커플 space 가 없는 사람이 오는 곳. */
export function OnboardingPage() {
  const { me, signOut } = useSession()
  const [name, setName] = useState('')
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const create = useMutation({
    mutationFn: () => couplesApi.create(name.trim()),
    onSuccess: async () => {
      // coupleId 가 생겼으니 부트스트랩 응답부터 다시 받아야 라우팅이 맞는다.
      await queryClient.invalidateQueries({ queryKey: queryKeys.me })
      await queryClient.invalidateQueries({ queryKey: queryKeys.couple })
      navigate('/link', { replace: true })
    },
  })

  const nameError = create.error instanceof ApiError ? create.error.fieldReason('name') : undefined
  const canSubmit = name.trim().length > 0 && !create.isPending

  return (
    <AppScreen
      action={
        <Button variant="quiet" onClick={() => void signOut()}>
          로그아웃
        </Button>
      }
      footer={
        <Button
          size="lg"
          block
          disabled={!canSubmit}
          loading={create.isPending}
          onClick={() => create.mutate()}
        >
          만들기
        </Button>
      }
    >
      <div className={styles.lead}>
        <h1 className={styles.headline}>
          {me?.nickname}님,
          <br />
          우리 둘의 space를 만들어요
        </h1>
        <p className={styles.sub}>
          만든 뒤에 초대 링크를 보내면 둘이 같은 가계부를 씁니다.
          <br />
          이름은 나중에 언제든 바꿀 수 있어요.
        </p>
      </div>

      <Field label="space 이름" hint="예: 성준 ♥ 지현, 우리집 금고" error={nameError}>
        {(id) => (
          <TextInput
            id={id}
            value={name}
            maxLength={50}
            placeholder="우리 둘의 이름을 붙여 주세요"
            invalid={Boolean(nameError)}
            onChange={(event) => setName(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && canSubmit) create.mutate()
            }}
          />
        )}
      </Field>

      {create.error && !nameError && <InlineError error={create.error} />}
    </AppScreen>
  )
}
