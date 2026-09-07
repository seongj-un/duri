import type { ReactNode } from 'react'
import styles from './Card.module.css'

interface CardProps {
  variant?: 'flat' | 'hero'
  tight?: boolean
  className?: string
  children: ReactNode
}

export function Card({ variant = 'flat', tight = false, className, children }: CardProps) {
  return (
    <section
      className={[styles.card, styles[variant], tight ? styles.tight : '', className]
        .filter(Boolean)
        .join(' ')}
    >
      {children}
    </section>
  )
}

export function SectionTitle({ children, aside }: { children: ReactNode; aside?: ReactNode }) {
  return (
    <div className={styles.sectionTitle}>
      <h2>{children}</h2>
      {aside && <span className={styles.sectionAside}>{aside}</span>}
    </div>
  )
}
