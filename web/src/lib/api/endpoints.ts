import { request } from './client'
import type {
  Account,
  AccountRequest,
  CategoryTrend,
  Couple,
  Expense,
  ExpenseCreateRequest,
  ExpensePage,
  ExpenseUpdateRequest,
  Invite,
  InvitePreview,
  Me,
  MonthlySummary,
  Notification,
  NotificationPage,
  Settlement,
  SettlementHistory,
} from './types'

export const usersApi = {
  /** 앱을 켤 때 가장 먼저 부르는 부트스트랩. */
  me: () => request<Me>('/api/v1/users/me'),

  account: () => request<Account>('/api/v1/users/me/account'),
  saveAccount: (body: AccountRequest) =>
    request<Account>('/api/v1/users/me/account', { method: 'PUT', body }),
  deleteAccount: () => request<void>('/api/v1/users/me/account', { method: 'DELETE' }),
}

export const couplesApi = {
  create: (name: string) => request<Couple>('/api/v1/couples', { method: 'POST', body: { name } }),
  mine: () => request<Couple>('/api/v1/couples/me'),
  update: (body: { name?: string; settlementDay?: number }) =>
    request<Couple>('/api/v1/couples/me', { method: 'PATCH', body }),

  /** 초대 링크 발급. 다시 부르면 이전 링크는 무효가 된다. */
  issueInvite: () => request<Invite>('/api/v1/couples/me/invites', { method: 'POST' }),
}

export const invitesApi = {
  /** 로그인 전에도 열린다. 토큰을 가진 것 자체가 열람 권한이다. */
  preview: (token: string) =>
    request<InvitePreview>(`/api/v1/invites/${encodeURIComponent(token)}`, { anonymous: true }),

  accept: (token: string) =>
    request<{ coupleId: number }>(`/api/v1/invites/${encodeURIComponent(token)}/accept`, {
      method: 'POST',
    }),
}

export const expensesApi = {
  /** period 를 생략하면 백엔드가 이번 달을 본다. */
  list: (params: { period?: string; category?: string; payerId?: number; page?: number; size?: number }) =>
    request<ExpensePage>('/api/v1/expenses', { query: params }),

  get: (expenseId: number) => request<Expense>(`/api/v1/expenses/${expenseId}`),

  create: (body: ExpenseCreateRequest) =>
    request<Expense>('/api/v1/expenses', { method: 'POST', body }),

  update: (expenseId: number, body: ExpenseUpdateRequest) =>
    request<Expense>(`/api/v1/expenses/${expenseId}`, { method: 'PATCH', body }),

  remove: (expenseId: number) =>
    request<void>(`/api/v1/expenses/${expenseId}`, { method: 'DELETE' }),
}

export const summariesApi = {
  /** 월별 화면 한 장 분량: 합계 · 사람별 부담 · 카테고리 비중 · 순잔액. */
  monthly: (period?: string) =>
    request<MonthlySummary>('/api/v1/summaries/monthly', { query: { period } }),

  /** 최근 months 개월치 추이. until 을 생략하면 백엔드가 이번 달까지 본다. */
  trend: (params: { until?: string; months?: number } = {}) =>
    request<CategoryTrend>('/api/v1/summaries/trend', { query: params }),
}

export const settlementsApi = {
  history: () => request<SettlementHistory[]>('/api/v1/settlements'),

  /** 확정 전이면 실시간 계산 결과가 온다. */
  get: (period: string) => request<Settlement>(`/api/v1/settlements/${period}`),

  /** 멱등하다. 두 사람이 동시에 눌러도 결과가 같다. */
  confirm: (period: string) =>
    request<Settlement>(`/api/v1/settlements/${period}/confirm`, { method: 'POST' }),
}

export const notificationsApi = {
  list: (page = 0, size = 20) =>
    request<NotificationPage>('/api/v1/notifications', { query: { page, size } }),
  markRead: (notificationId: number) =>
    request<Notification>(`/api/v1/notifications/${notificationId}/read`, { method: 'POST' }),
  markAllRead: () => request<{ updated: number }>('/api/v1/notifications/read-all', { method: 'POST' }),
}
