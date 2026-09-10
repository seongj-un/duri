import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Button } from '../components/Button'
import { Field, TextInput } from '../components/Field'
import { SegmentedToggle } from '../components/SegmentedToggle'
import { InlineError } from '../components/States'
import { ApiError } from '../lib/api/client'
import { useSession } from '../lib/auth/SessionProvider'
import { takePendingInvite } from '../lib/auth/pendingInvite'
import styles from './LoginPage.module.css'

type Mode = 'login' | 'signup'

/** 첫 진입 화면. 로그인 방법 외에는 아무것도 묻지 않는다. */
export function LoginPage() {
  const { signIn, signUp } = useSession()
  const navigate = useNavigate()

  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')

  const submit = useMutation({
    mutationFn: async () => {
      if (mode === 'login') return signIn({ email: email.trim(), password })
      return signUp({ email: email.trim(), password, nickname: nickname.trim() })
    },
    onSuccess: () => {
      // 초대 링크를 타고 왔다면 원래 가려던 곳으로 돌려보낸다.
      const pending = takePendingInvite()
      navigate(pending ? `/invite/${pending}` : '/', { replace: true })
    },
  })

  const error = submit.error instanceof ApiError ? submit.error : null
  const canSubmit =
    email.trim().length > 0 &&
    password.length > 0 &&
    (mode === 'login' || nickname.trim().length > 0) &&
    !submit.isPending

  const switchMode = (next: Mode) => {
    setMode(next)
    submit.reset()
  }

  return (
    <div className={styles.page}>
      <div className={styles.hero}>
        <div className={styles.logo}>
          <span className={styles.mark} aria-hidden="true">
            ₩
          </span>
          PairPay
        </div>
        <p className={styles.tagline}>
          둘이 쓴 돈, 깔끔하게 반반.
          <br />
          누가 얼마 냈는지 더 세지 않아도 돼요.
        </p>
      </div>

      <form
        className={styles.form}
        onSubmit={(event) => {
          event.preventDefault()
          if (canSubmit) submit.mutate()
        }}
      >
        <SegmentedToggle
          label="로그인 또는 회원가입"
          value={mode}
          onChange={switchMode}
          options={[
            { value: 'login', label: '로그인' },
            { value: 'signup', label: '회원가입' },
          ]}
        />

        <Field label="이메일" error={error?.fieldReason('email')}>
          {(id) => (
            <TextInput
              id={id}
              type="email"
              value={email}
              autoComplete="email"
              placeholder="you@example.com"
              invalid={Boolean(error?.fieldReason('email'))}
              onChange={(event) => setEmail(event.target.value)}
            />
          )}
        </Field>

        <Field
          label="비밀번호"
          hint={mode === 'signup' ? '8자 이상 64자 이하' : undefined}
          error={error?.fieldReason('password')}
        >
          {(id) => (
            <TextInput
              id={id}
              type="password"
              value={password}
              autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
              invalid={Boolean(error?.fieldReason('password'))}
              onChange={(event) => setPassword(event.target.value)}
            />
          )}
        </Field>

        {mode === 'signup' && (
          <Field label="닉네임" hint="상대에게 보이는 이름이에요" error={error?.fieldReason('nickname')}>
            {(id) => (
              <TextInput
                id={id}
                value={nickname}
                maxLength={50}
                placeholder="성준"
                invalid={Boolean(error?.fieldReason('nickname'))}
                onChange={(event) => setNickname(event.target.value)}
              />
            )}
          </Field>
        )}

        {/* 필드에 붙지 않는 오류(자격증명 불일치, 이메일 중복)는 폼 아래에 한 번만 띄운다. */}
        {submit.error && !hasFieldError(error) && <InlineError error={submit.error} />}

        <Button type="submit" size="lg" block disabled={!canSubmit} loading={submit.isPending}>
          {mode === 'login' ? '로그인' : '가입하고 시작하기'}
        </Button>
      </form>

      <p className={styles.trust}>기록은 우리 둘만 볼 수 있어요.</p>
    </div>
  )
}

function hasFieldError(error: ApiError | null): boolean {
  return Boolean(error && error.fieldErrors.length > 0)
}
