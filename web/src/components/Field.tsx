import { useId } from 'react'
import type { InputHTMLAttributes, ReactNode } from 'react'
import styles from './Field.module.css'

interface FieldProps {
  label: string
  hint?: ReactNode
  error?: string
  children: (id: string) => ReactNode
}

export function Field({ label, hint, error, children }: FieldProps) {
  const id = useId()

  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>
        {label}
      </label>
      {children(id)}
      {error ? (
        <p className={styles.error}>{error}</p>
      ) : (
        hint && <p className={styles.hint}>{hint}</p>
      )}
    </div>
  )
}

interface TextInputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean
}

export function TextInput({ invalid = false, className, ...rest }: TextInputProps) {
  return (
    <input
      className={[styles.input, invalid ? styles.invalid : '', className].filter(Boolean).join(' ')}
      {...rest}
    />
  )
}

interface AmountInputProps {
  value: number | ''
  onChange: (value: number | '') => void
  id?: string
  autoFocus?: boolean
}

/**
 * 금액 입력. 원 단위 정수만 받는다.
 * type="number" 를 쓰지 않는 이유: 자릿수 구분 표시를 못 하고, 모바일에서 스피너가 붙는다.
 */
export function AmountInput({ value, onChange, id, autoFocus }: AmountInputProps) {
  return (
    <div className={styles.amountRow}>
      <input
        id={id}
        className={styles.amountInput}
        inputMode="numeric"
        autoComplete="off"
        placeholder="0"
        autoFocus={autoFocus}
        value={value === '' ? '' : value.toLocaleString('ko-KR')}
        onChange={(event) => {
          const digits = event.target.value.replace(/[^\d]/g, '')
          if (digits === '') return onChange('')
          // 1조 원을 넘길 일은 없다. 실수로 붙여넣은 값이 화면을 깨뜨리지 않게 막는다.
          onChange(Math.min(Number(digits), 999_999_999_999))
        }}
      />
      <span className={styles.amountUnit}>원</span>
    </div>
  )
}
