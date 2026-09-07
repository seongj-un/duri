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
  /** 무효화용 접두사. SSE 로 새 알림이 오면 이 아래를 전부 턴다. */
  notifications: ['notifications'] as const,
  /** 뱃지는 개수만 필요해 1건만, 목록은 넉넉히 받는다. 둘 다 위 접두사에 걸린다. */
  notificationList: (size: number) => ['notifications', 'list', size] as const,
  settlementHistory: ['settlements', 'history'] as const,
  recurringExpenses: ['recurring-expenses'] as const,
  burdenPresets: ['burden-presets'] as const,

  summary: (period: string) => ['summary', period] as const,
  trend: (until: string, months: number) => ['trend', until, months] as const,
  expenses: (period: string, filters?: Record<string, unknown>) =>
    ['expenses', period, filters ?? {}] as const,
  /** 무효화용 접두사. 필터가 뭐든 그 달 전체를 턴다. */
  expensesOfPeriod: (period: string) => ['expenses', period] as const,
  settlement: (period: string) => ['settlement', period] as const,

  invitePreview: (token: string) => ['invite', token] as const,
}
