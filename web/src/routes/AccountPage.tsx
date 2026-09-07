import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { Field, TextInput } from '../components/Field'
import { InlineError, Skeleton, SkeletonStack } from '../components/States'
import { usersApi } from '../lib/api/endpoints'
import { ApiError } from '../lib/api/client'
import { queryKeys } from '../lib/queryKeys'
import { useSession } from '../lib/auth/SessionProvider'
import type { Bank } from '../lib/api/types'
import styles from './AccountPage.module.css'

/**
 * 은행은 자유 문자열이 아니라 열거형이다.
 * "국민"과 "KB국민"이 섞이면 붙여넣을 때마다 확인해야 하기 때문이다.
 */
const BANKS: { value: Bank; label: string }[] = [
  { value: 'KB', label: '국민' },
  { value: 'SHINHAN', label: '신한' },
  { value: 'WOORI', label: '우리' },
  { value: 'HANA', label: '하나' },
  { value: 'NH', label: '농협' },
  { value: 'IBK', label: '기업' },
  { value: 'KAKAOBANK', label: '카카오뱅크' },
  { value: 'TOSSBANK', label: '토스뱅크' },
  { value: 'KBANK', label: '케이뱅크' },
  { value: 'SC', label: 'SC제일' },
  { value: 'CITI', label: '씨티' },
  { value: 'KDB', label: '산업' },
  { value: 'SUHYUP', label: '수협' },
  { value: 'DGB', label: '대구' },
  { value: 'BUSAN', label: '부산' },
  { value: 'KYONGNAM', label: '경남' },
  { value: 'KWANGJU', label: '광주' },
  { value: 'JEONBUK', label: '전북' },
  { value: 'JEJU', label: '제주' },
  { value: 'POST', label: '우체국' },
  { value: 'SAEMAUL', label: '새마을금고' },
  { value: 'SHINHYUP', label: '신협' },
]

export function AccountPage() {
  const { me } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const [bank, setBank] = useState<Bank>('KB')
  const [accountNo, setAccountNo] = useState('')
  const [holderName, setHolderName] = useState('')

  const account = useQuery({
    queryKey: queryKeys.account,
    queryFn: usersApi.account,
    // 아직 등록하지 않았으면 404 다. 없는 게 정상 상태라 재시도하지 않는다.
    retry: false,
  })

  const registered = account.data
  useEffect(() => {
    if (!registered) return
    setBank(registered.bank)
    setAccountNo(registered.accountNo)
    setHolderName(registered.holderName)
  }, [registered])

  // 예금주는 대개 본인 이름이다. 처음 열었을 때 채워 두면 입력이 하나 줄어든다.
  useEffect(() => {
    if (!registered && me?.nickname) setHolderName((current) => current || me.nickname)
  }, [registered, me?.nickname])

  const save = useMutation({
    mutationFn: () => usersApi.saveAccount({ bank, accountNo: accountNo.trim(), holderName: holderName.trim() }),
    onSuccess: async (saved) => {
      queryClient.setQueryData(queryKeys.account, saved)
      // 채권자의 계좌가 생기면 송금 안내가 함께 내려온다.
      await queryClient.invalidateQueries({ queryKey: ['settlement'] })
      navigate(-1)
    },
  })

  const remove = useMutation({
    mutationFn: usersApi.deleteAccount,
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: queryKeys.account })
      await queryClient.invalidateQueries({ queryKey: ['settlement'] })
      setAccountNo('')
    },
  })

  const error = save.error instanceof ApiError ? save.error : null
  const notFound = account.error instanceof ApiError && account.error.code === 'ACCOUNT_NOT_REGISTERED'
  const canSubmit = accountNo.trim().length >= 8 && holderName.trim().length > 0 && !save.isPending

  return (
    <AppScreen
      title="내 입금 계좌"
      back
      footer={
        <Button size="lg" block disabled={!canSubmit} loading={save.isPending} onClick={() => save.mutate()}>
          {registered ? '수정하기' : '등록하기'}
        </Button>
      }
    >
      <p className={styles.lead}>
        정산할 때 상대에게 보여 줄 계좌예요. 상대는 복사 버튼 한 번으로 계좌와 금액을 가져갑니다.
        <br />
        계좌번호는 암호화해서 저장해요.
      </p>

      {account.isPending && (
        <SkeletonStack>
          <Skeleton height={52} />
          <Skeleton height={52} />
          <Skeleton height={52} />
        </SkeletonStack>
      )}

      {account.error && !notFound && <InlineError error={account.error} />}

      {!account.isPending && (
        <>
          <Field label="은행" error={error?.fieldReason('bank')}>
            {(id) => (
              <select
                id={id}
                className={styles.select}
                value={bank}
                onChange={(event) => setBank(event.target.value as Bank)}
              >
                {BANKS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            )}
          </Field>

          <Field
            label="계좌번호"
            hint="숫자와 하이픈만 쓸 수 있어요. 하이픈은 저장할 때 지워집니다."
            error={error?.fieldReason('accountNo')}
          >
            {(id) => (
              <TextInput
                id={id}
                inputMode="numeric"
                value={accountNo}
                placeholder="3333011234567"
                invalid={Boolean(error?.fieldReason('accountNo'))}
                onChange={(event) => setAccountNo(event.target.value.replace(/[^\d-]/g, ''))}
              />
            )}
          </Field>

          <Field label="예금주" error={error?.fieldReason('holderName')}>
            {(id) => (
              <TextInput
                id={id}
                value={holderName}
                maxLength={50}
                placeholder="박성준"
                invalid={Boolean(error?.fieldReason('holderName'))}
                onChange={(event) => setHolderName(event.target.value)}
              />
            )}
          </Field>

          {save.error && <InlineError error={save.error} />}

          {registered && (
            <div className={styles.danger}>
              <Button
                variant="danger"
                block
                loading={remove.isPending}
                onClick={() => remove.mutate()}
              >
                계좌 삭제
              </Button>
            </div>
          )}

          {remove.error && <InlineError error={remove.error} />}
        </>
      )}
    </AppScreen>
  )
}
