import type { ExpenseCategory } from './api/types'

/** 금액은 언제나 정수 원이다. 부동소수 연산을 끼워 넣지 않는다. */
export function formatWon(amount: number): string {
  return amount.toLocaleString('ko-KR')
}

export function formatWonWithUnit(amount: number): string {
  return `${formatWon(amount)}원`
}

/** "2026-09" */
export function toPeriod(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export function currentPeriod(): string {
  return toPeriod(new Date())
}

export function shiftPeriod(period: string, months: number): string {
  const [year, month] = period.split('-').map(Number)
  const date = new Date(year, month - 1 + months, 1)
  return toPeriod(date)
}

/** "2026-09" → "2026년 9월" */
export function formatPeriod(period: string): string {
  const [year, month] = period.split('-').map(Number)
  return `${year}년 ${month}월`
}

/** "2026-09" → "9월" */
export function formatPeriodShort(period: string): string {
  return `${Number(period.split('-')[1])}월`
}

/** 다음 달은 아직 오지 않았다. 달 이동에서 미래로 못 가게 막는 데 쓴다. */
export function isFuturePeriod(period: string): boolean {
  return period > currentPeriod()
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

/** "2026-09-07" → "9월 7일 (월)" */
export function formatDate(isoDate: string): string {
  const [, month, day] = isoDate.split('-').map(Number)
  const date = new Date(isoDate)
  return `${month}월 ${day}일 (${WEEKDAYS[date.getDay()]})`
}

/** "방금", "12분 전", "3시간 전", "어제", 그보다 오래되면 "9월 5일". */
export function formatRelativeTime(iso: string): string {
  const then = new Date(iso).getTime()
  const minutes = Math.floor((Date.now() - then) / 60_000)

  if (minutes < 1) return '방금'
  if (minutes < 60) return `${minutes}분 전`

  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  if (hours < 48) return '어제'

  const date = new Date(iso)
  return `${date.getMonth() + 1}월 ${date.getDate()}일`
}

export function todayIso(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** 카테고리 표시명은 백엔드도 내려주지만, 선택지를 그릴 때는 프론트에도 필요하다. */
export const CATEGORY_LABELS: Record<ExpenseCategory, string> = {
  RENT: '월세',
  UTILITY: '공과금',
  GROCERY: '장보기',
  DINING: '외식',
  DATE: '데이트',
  TRANSPORT: '교통',
  SHOPPING: '쇼핑',
  TRAVEL: '여행',
  SUBSCRIPTION: '구독',
  ETC: '기타',
}

export const CATEGORIES = Object.keys(CATEGORY_LABELS) as ExpenseCategory[]

/**
 * 카테고리 막대그래프용 색. 채도를 낮춰 히어로 카드의 색을 이기지 않게 한다.
 * 노션 토큰이 아니라 이 화면에서만 쓰는 파생 팔레트다.
 */
export const CATEGORY_COLORS: Record<ExpenseCategory, string> = {
  RENT: '#2A54D6',
  UTILITY: '#4C7DF0',
  GROCERY: '#0E7C5A',
  DINING: '#E08A2E',
  DATE: '#D2588A',
  TRANSPORT: '#3FA3A3',
  SHOPPING: '#8256D0',
  TRAVEL: '#2E9BD6',
  SUBSCRIPTION: '#6B7A99',
  ETC: '#9AA3B2',
}
