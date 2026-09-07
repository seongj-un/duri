import styles from './FullPageSpinner.module.css'

export function FullPageSpinner({ label }: { label?: string }) {
  return (
    <div className={styles.wrap} role="status" aria-live="polite">
      <div className={styles.spinner} aria-hidden="true" />
      {label && <p className={styles.label}>{label}</p>}
    </div>
  )
}
