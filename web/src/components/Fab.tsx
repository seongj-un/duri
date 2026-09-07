import { Link } from 'react-router-dom'
import styles from './Fab.module.css'

export function Fab({ to, label }: { to: string; label: string }) {
  return (
    <Link className={styles.fab} to={to}>
      <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
        <path d="M9 3.5v11M3.5 9h11" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      </svg>
      {label}
    </Link>
  )
}
