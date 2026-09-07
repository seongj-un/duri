import type { ReactNode } from 'react'
import styles from './SegmentedToggle.module.css'

export interface SegmentOption<T extends string | number> {
  value: T
  label: ReactNode
}

interface SegmentedToggleProps<T extends string | number> {
  options: SegmentOption<T>[]
  value: T
  onChange: (value: T) => void
  label: string
}

export function SegmentedToggle<T extends string | number>({
  options,
  value,
  onChange,
  label,
}: SegmentedToggleProps<T>) {
  return (
    <div className={styles.group} role="radiogroup" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={option.value === value}
          className={`${styles.option} ${option.value === value ? styles.selected : ''}`}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
