import type { ReactNode } from 'react'
import styles from './Avatar.module.css'

interface AvatarProps {
  nickname: string
  imageUrl?: string | null
  size?: 'sm' | 'md' | 'lg'
  /** 나를 상대와 구분해 보여준다. */
  mine?: boolean
}

export function Avatar({ nickname, imageUrl, size = 'md', mine = false }: AvatarProps) {
  return (
    <div
      className={`${styles.avatar} ${styles[size]} ${mine ? styles.mine : ''}`}
      title={nickname}
      aria-label={nickname}
    >
      {imageUrl ? (
        <img className={styles.image} src={imageUrl} alt="" loading="lazy" />
      ) : (
        initialOf(nickname)
      )}
    </div>
  )
}

/** 아직 아무도 없는 자리. 파트너 연결 대기 화면에서 쓴다. */
export function EmptyAvatar({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  return (
    <div className={`${styles.avatar} ${styles[size]} ${styles.pending}`} aria-label="아직 비어 있음">
      ?
    </div>
  )
}

export function AvatarPair({ children }: { children: ReactNode }) {
  return <div className={styles.pair}>{children}</div>
}

/** 한글은 첫 글자, 그 외는 첫 두 글자를 대문자로. */
function initialOf(nickname: string): string {
  const trimmed = nickname.trim()
  if (!trimmed) return '?'
  if (/[가-힣]/.test(trimmed[0])) return trimmed[0]
  return trimmed.slice(0, 2).toUpperCase()
}
