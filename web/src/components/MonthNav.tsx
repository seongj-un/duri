import { formatPeriod, isFuturePeriod, shiftPeriod } from '../lib/format'
import styles from './MonthNav.module.css'

interface MonthNavProps {
  period: string
  onChange: (period: string) => void
}

/** 다음 달로는 못 넘어간다. 아직 오지 않은 달에는 보여줄 것이 없다. */
export function MonthNav({ period, onChange }: MonthNavProps) {
  const next = shiftPeriod(period, 1)

  return (
    <div className={styles.nav}>
      <button
        type="button"
        className={styles.arrow}
        aria-label="지난달"
        onClick={() => onChange(shiftPeriod(period, -1))}
      >
        <Arrow direction="left" />
      </button>

      <div className={styles.label}>{formatPeriod(period)}</div>

      <button
        type="button"
        className={styles.arrow}
        aria-label="다음 달"
        disabled={isFuturePeriod(next)}
        onClick={() => onChange(next)}
      >
        <Arrow direction="right" />
      </button>
    </div>
  )
}

function Arrow({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
      <path
        d={direction === 'left' ? 'M11 4 6 9l5 5' : 'M7 4l5 5-5 5'}
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
