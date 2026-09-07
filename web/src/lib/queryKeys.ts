/**
 * 쿼리 키를 한곳에 모아 둔다.
 *
 * SSE 이벤트가 "어느 달이 바뀌었는지"만 알려 주기 때문에,
 * 그 달의 키를 정확히 집어 무효화할 수 있어야 한다.
 */
export const queryKeys = {
  me: ['me'] as const,
  couple: ['couple'] as const,
  account: ['account'] as const,
  notifications: ['notifications'] as const,
  settlementHistory: ['settlements', 'history'] as const,

  summary: (period: string) => ['summary', period] as const,
  expenses: (period: string, filters?: Record<string, unknown>) =>
    ['expenses', period, filters ?? {}] as const,
  /** 무효화용 접두사. 필터가 뭐든 그 달 전체를 턴다. */
  expensesOfPeriod: (period: string) => ['expenses', period] as const,
  settlement: (period: string) => ['settlement', period] as const,

  invitePreview: (token: string) => ['invite', token] as const,
}
