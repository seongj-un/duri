import { NetworkError } from '../lib/api/client'
import { Button } from './Button'
import { ErrorState } from './States'
import styles from './ConnectionLost.module.css'

/**
 * 부팅 재발급이 네트워크로 실패했을 때의 화면.
 *
 * 로그인 화면을 띄우면 멀쩡한 세션을 사용자가 직접 버리게 되고, 스피너만 돌리면 할 수 있는 게 없다.
 * 그래서 무슨 일인지 말하고 다시 시도할 버튼만 준다. 연결이 돌아오면 이 버튼 없이도 알아서 복구된다.
 */
export function ConnectionLost({ onRetry, retrying }: { onRetry: () => void; retrying: boolean }) {
  return (
    <div className={styles.wrap} role="status" aria-live="polite">
      <ErrorState
        error={new NetworkError()}
        action={
          <Button onClick={onRetry} loading={retrying}>
            다시 시도
          </Button>
        }
      />
    </div>
  )
}
