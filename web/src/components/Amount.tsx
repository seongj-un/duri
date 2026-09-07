import { formatWon } from '../lib/format'
import styles from './Amount.module.css'

type Tone = 'neutral' | 'credit' | 'debit' | 'muted'

interface AmountProps {
  value: number
  size?: 'hero' | 'lg' | 'md' | 'sm'
  tone?: Tone
  /** 부호를 붙여 방향을 드러낸다(+120,000원). */
  signed?: boolean
  unit?: boolean
}

export function Amount({ value, size = 'md', tone, signed = false, unit = true }: AmountProps) {
  const resolvedTone: Tone = tone ?? (signed ? signTone(value) : 'neutral')
  const sign = signed && value !== 0 ? (value > 0 ? '+' : '−') : ''

  return (
    <span className={`${styles.amount} ${styles[size]} ${styles[resolvedTone]}`}>
      {sign}
      {formatWon(Math.abs(value))}
      {unit && <span className={styles.unit}>원</span>}
    </span>
  )
}

function signTone(value: number): Tone {
  if (value > 0) return 'credit'
  if (value < 0) return 'debit'
  return 'muted'
}
