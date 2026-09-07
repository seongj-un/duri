import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * 복사 버튼. 눌렀다는 사실이 잠깐 남아야 사용자가 복사됐는지 알 수 있다.
 *
 * navigator.clipboard 는 보안 컨텍스트(https 또는 localhost)에서만 있다.
 * 없으면 execCommand 로 떨어진다 — 사파리·구형 안드로이드 웹뷰 때문에 필요하다.
 */
export function useCopy(resetAfterMs = 1600) {
  const [copied, setCopied] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const copy = useCallback(
    async (text: string) => {
      const ok = await writeToClipboard(text)
      if (!ok) return false

      setCopied(true)
      window.clearTimeout(timer.current)
      timer.current = window.setTimeout(() => setCopied(false), resetAfterMs)
      return true
    },
    [resetAfterMs],
  )

  return { copied, copy }
}

async function writeToClipboard(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 권한이 거절된 경우다. 아래 폴백으로 넘어간다.
    }
  }

  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()

  try {
    return document.execCommand('copy')
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}
