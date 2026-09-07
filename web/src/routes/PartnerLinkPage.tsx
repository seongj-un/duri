import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { Avatar, EmptyAvatar } from '../components/Avatar'
import { InlineError, Skeleton, SkeletonStack } from '../components/States'
import { couplesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { useSession } from '../lib/auth/SessionProvider'
import { useCopy } from '../lib/clipboard'
import styles from './PartnerLinkPage.module.css'

/** 상대가 링크를 눌렀는지 SSE 로는 알 수 없다 — 스트림은 커플이 ACTIVE 여야 열린다. */
const POLL_INTERVAL_MS = 5000

export function PartnerLinkPage() {
  const { me, couple, signOut } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { copied, copy } = useCopy()

  // 링크는 화면에 들어올 때 한 번만 발급한다. 다시 부르면 이전 링크가 무효가 되기 때문이다.
  const invite = useQuery({
    queryKey: ['invite', 'mine'],
    queryFn: couplesApi.issueInvite,
    staleTime: Infinity,
    gcTime: Infinity,
    retry: false,
  })

  // 상대가 수락하면 커플이 ACTIVE 가 된다. 그때 홈으로 넘긴다.
  const coupleQuery = useQuery({
    queryKey: queryKeys.couple,
    queryFn: couplesApi.mine,
    refetchInterval: POLL_INTERVAL_MS,
  })

  useEffect(() => {
    if (coupleQuery.data?.status === 'ACTIVE') navigate('/', { replace: true })
  }, [coupleQuery.data?.status, navigate])

  const reissue = useMutation({
    mutationFn: couplesApi.issueInvite,
    onSuccess: (issued) => queryClient.setQueryData(['invite', 'mine'], issued),
  })

  const inviteUrl = invite.data?.inviteUrl
  const expiresAt = invite.data?.expiresAt

  const share = async () => {
    if (!inviteUrl) return
    const text = `${couple?.name ?? '우리 가계부'}에 초대할게요. 링크로 들어와 주세요.`

    if (navigator.share) {
      try {
        await navigator.share({ title: 'PairPay 초대', text, url: inviteUrl })
        return
      } catch {
        // 사용자가 공유 시트를 닫은 경우다. 복사로 떨어진다.
      }
    }
    await copy(inviteUrl)
  }

  return (
    <AppScreen
      action={
        <Button variant="quiet" onClick={() => void signOut()}>
          로그아웃
        </Button>
      }
    >
      <div className={styles.pair}>
        <div className={styles.person}>
          <Avatar nickname={me?.nickname ?? '나'} imageUrl={me?.profileImageUrl} size="lg" mine />
          <span>{me?.nickname}</span>
        </div>
        <div className={styles.link} aria-hidden="true" />
        <div className={styles.person}>
          <EmptyAvatar size="lg" />
          <span>기다리는 중</span>
        </div>
      </div>

      <h1 className={styles.headline}>파트너를 초대해 주세요</h1>
      <p className={styles.sub}>
        링크를 받은 사람이 로그인하면 바로 연결돼요.
        <br />
        들어올 수 있는 사람은 한 명뿐입니다.
      </p>

      {invite.isPending && (
        <SkeletonStack>
          <Skeleton height={56} />
          <Skeleton height={54} />
        </SkeletonStack>
      )}

      {invite.error && <InlineError error={invite.error} />}

      {inviteUrl && (
        <>
          <div className={styles.linkBox}>
            <span className={styles.url}>{inviteUrl}</span>
            <Button variant="ghost" onClick={() => void copy(inviteUrl)}>
              {copied ? '복사됨' : '복사'}
            </Button>
          </div>

          <div className={styles.actions}>
            <Button size="lg" block onClick={() => void share()}>
              초대 링크 공유하기
            </Button>
            <Button
              variant="quiet"
              block
              loading={reissue.isPending}
              onClick={() => reissue.mutate()}
            >
              링크 새로 만들기
            </Button>
          </div>

          {expiresAt && (
            <p className={styles.expiry}>
              {new Date(expiresAt).toLocaleDateString('ko-KR', {
                month: 'long',
                day: 'numeric',
              })}
              까지 쓸 수 있어요. 새로 만들면 이전 링크는 바로 막힙니다.
            </p>
          )}

          <p className={styles.waiting}>
            <span className={styles.pulse} aria-hidden="true" />
            상대가 들어오면 자동으로 넘어가요
          </p>
        </>
      )}

      {reissue.error && <InlineError error={reissue.error} />}
    </AppScreen>
  )
}
