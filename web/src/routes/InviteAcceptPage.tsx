import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import { AppScreen } from '../components/AppScreen'
import { Button } from '../components/Button'
import { ErrorState, InlineError, Skeleton, SkeletonStack } from '../components/States'
import { invitesApi } from '../lib/api/endpoints'
import { queryKeys } from '../lib/queryKeys'
import { useSession } from '../lib/auth/SessionProvider'
import { rememberPendingInvite } from '../lib/auth/pendingInvite'
import styles from './InviteAcceptPage.module.css'

/**
 * 초대 링크를 열었을 때. 로그인 전에도 누가 불렀는지는 보여준다
 * (미리보기는 토큰을 가진 것 자체가 열람 권한이라 permitAll 이다).
 */
export function InviteAcceptPage() {
  const { token = '' } = useParams()
  const { state, me } = useSession()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const preview = useQuery({
    queryKey: queryKeys.invitePreview(token),
    queryFn: () => invitesApi.preview(token),
    retry: false,
  })

  const accept = useMutation({
    mutationFn: () => invitesApi.accept(token),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.me })
      await queryClient.invalidateQueries({ queryKey: queryKeys.couple })
      navigate('/', { replace: true })
    },
  })

  const startLogin = () => {
    // 로그인 화면을 다녀온 뒤 이 초대로 돌아오기 위해 토큰을 남긴다.
    rememberPendingInvite(token)
    navigate('/login')
  }

  if (preview.isPending) {
    return (
      <AppScreen>
        <SkeletonStack>
          <Skeleton height={64} />
          <Skeleton height={28} />
          <Skeleton height={54} />
        </SkeletonStack>
      </AppScreen>
    )
  }

  if (preview.error) {
    return (
      <AppScreen>
        <ErrorState
          error={preview.error}
          action={
            <Button variant="ghost" onClick={() => navigate('/login', { replace: true })}>
              처음으로
            </Button>
          }
        />
      </AppScreen>
    )
  }

  const invite = preview.data
  const alreadyInCouple = state === 'authenticated' && me?.coupleId != null

  return (
    <AppScreen>
      <div className={styles.center}>
        <div className={styles.badge} aria-hidden="true">
          💌
        </div>

        <h1 className={styles.headline}>
          {invite.inviterNickname}님이
          <br />
          함께 쓰자고 초대했어요
        </h1>
        <p className={styles.space}>{invite.coupleName}</p>

        <div className={styles.actions}>
          {invite.status !== 'PENDING' ? (
            <InlineError error={new Error(statusMessage(invite.status))} />
          ) : alreadyInCouple ? (
            <>
              <InlineError
                error={new Error('이미 참여 중인 커플 space가 있어요. 새 초대는 받을 수 없습니다.')}
              />
              <Button variant="ghost" block onClick={() => navigate('/', { replace: true })}>
                내 가계부로 가기
              </Button>
            </>
          ) : state === 'authenticated' ? (
            <>
              <Button
                size="lg"
                block
                loading={accept.isPending}
                onClick={() => accept.mutate()}
              >
                수락하고 시작하기
              </Button>
              {accept.error && <InlineError error={accept.error} />}
            </>
          ) : (
            <Button size="lg" block onClick={startLogin}>
              로그인하고 수락하기
            </Button>
          )}
        </div>

        <p className={styles.note}>
          수락하면 두 사람의 지출이 한곳에 모입니다.
          <br />
          이 space에는 더 이상 다른 사람이 들어올 수 없어요.
        </p>
      </div>
    </AppScreen>
  )
}

function statusMessage(status: string): string {
  switch (status) {
    case 'ACCEPTED':
      return '이미 사용된 초대 링크예요. 초대한 분에게 새 링크를 받아 주세요.'
    case 'EXPIRED':
      return '만료된 초대 링크예요. 초대한 분에게 새 링크를 받아 주세요.'
    case 'REVOKED':
      return '취소된 초대 링크예요. 초대한 분에게 새 링크를 받아 주세요.'
    default:
      return '지금은 쓸 수 없는 초대 링크예요.'
  }
}
