import type { ReactNode } from 'react'
import styles from './Chip.module.css'

export function ChipRow({ children }: { children: ReactNode }) {
  return <div className={styles.row}>{children}</div>
}

interface ChipProps {
  selected: boolean
  onClick: () => void
  children: ReactNode
}

export function Chip({ selected, onClick, children }: ChipProps) {
  return (
    <button
      type="button"
      className={`${styles.chip} ${selected ? styles.selected : ''}`}
      aria-pressed={selected}
      onClick={onClick}
    >
      {children}
    </button>
  )
}
