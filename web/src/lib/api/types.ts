/**
 * 백엔드 DTO 를 그대로 옮긴 타입. 원본은 src/main/kotlin 아래 각 도메인의 dto 패키지다.
 * 여기서 필드를 임의로 늘리거나 이름을 바꾸지 않는다 — 바뀌면 백엔드부터 바뀐 것이다.
 */

export type AuthProvider = 'KAKAO' | 'GOOGLE'
export type CoupleStatus = 'PENDING' | 'ACTIVE' | 'DISBANDED'
export type MemberRole = 'OWNER' | 'PARTNER'
export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED'
export type SettlementStatus = 'OPEN' | 'CONFIRMED'
export type NotificationType = 'SETTLEMENT_REMINDER'

export type ExpenseCategory =
  | 'RENT'
  | 'UTILITY'
  | 'GROCERY'
  | 'DINING'
  | 'DATE'
  | 'TRANSPORT'
  | 'SHOPPING'
  | 'TRAVEL'
  | 'SUBSCRIPTION'
  | 'ETC'

export type Bank =
  | 'KB' | 'SHINHAN' | 'WOORI' | 'HANA' | 'NH' | 'IBK' | 'SC' | 'CITI' | 'KDB'
  | 'SUHYUP' | 'DGB' | 'BUSAN' | 'KYONGNAM' | 'KWANGJU' | 'JEONBUK' | 'JEJU'
  | 'POST' | 'SAEMAUL' | 'SHINHYUP' | 'KAKAOBANK' | 'KBANK' | 'TOSSBANK'

/** 어떤 실패든 백엔드는 이 모양으로 답한다. */
export interface ErrorBody {
  code: string
  message: string
  path: string
  timestamp: string
  fieldErrors?: { field: string; reason: string }[]
}

export interface AccessTokenResponse {
  accessToken: string
  tokenType: string
  /** 초 단위. 만료 전에 미리 갱신하는 데 쓴다. */
  expiresIn: number
}

export interface Me {
  userId: number
  nickname: string
  email: string | null
  profileImageUrl: string | null
  provider: AuthProvider
  /** null 이면 아직 커플 space 가 없다 — 온보딩으로 보낸다. */
  coupleId: number | null
}

export interface MemberRef {
  userId: number
  nickname: string
  profileImageUrl?: string | null
}

export interface CoupleMember extends MemberRef {
  role: MemberRole
  memberNo: number
  joinedAt: string
}

export interface Couple {
  coupleId: number
  name: string
  status: CoupleStatus
  settlementDay: number
  members: CoupleMember[]
  createdAt: string
}

export interface Invite {
  token: string
  inviteUrl: string
  expiresAt: string
}

export interface InvitePreview {
  coupleName: string
  inviterNickname: string
  status: InviteStatus
  expiresAt: string
}

export interface Expense {
  expenseId: number
  payer: MemberRef
  amount: number
  category: ExpenseCategory
  categoryName: string
  payerBurdenRate: number
  /** 결제자 본인 몫. */
  payerShare: number
  /** 상대가 갚아야 할 몫. */
  partnerShare: number
  spentAt: string
  memo: string | null
  /** 정산이 확정되어 더는 손댈 수 없는 상태. */
  locked: boolean
  createdAt: string
}

export interface ExpensePage {
  content: Expense[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  /** 페이지가 아니라 필터 전체의 합계. */
  totalAmount: number
}

export interface ExpenseCreateRequest {
  payerId: number
  amount: number
  category: ExpenseCategory
  /** 생략하면 카테고리 프리셋, 그것도 없으면 반반. */
  payerBurdenRate?: number
  spentAt: string
  memo?: string | null
}

export type ExpenseUpdateRequest = Partial<ExpenseCreateRequest>

export interface MemberSpending {
  member: MemberRef
  paidAmount: number
  burdenAmount: number
  /** paidAmount - burdenAmount. 양수면 받을 돈. */
  netAmount: number
}

export interface CategorySpending {
  category: ExpenseCategory
  categoryName: string
  amount: number
  count: number
  ratio: number
}

/** netAmount 는 항상 0 이상이고 방향은 채권자/채무자로 표현한다. */
export interface BalanceSummary {
  netAmount: number
  creditor: MemberRef | null
  debtor: MemberRef | null
}

export interface MonthlySummary {
  period: string
  from: string
  to: string
  totalAmount: number
  expenseCount: number
  members: MemberSpending[]
  categories: CategorySpending[]
  balance: BalanceSummary
  settlementStatus: SettlementStatus | null
}

/**
 * 최근 몇 달의 지출 추이.
 *
 * months 와 같은 순서·길이의 배열로 값이 온다. 지출이 없던 달도 0 으로 채워져 있어
 * 그래프 축이 끊기지 않는다.
 */
export interface CategoryTrend {
  from: string
  to: string
  /** ["2026-04", "2026-05", ...] 오래된 달부터. */
  months: string[]
  totalByMonth: number[]
  totalAmount: number
  /** 지출이 없던 달까지 포함해 나눈 평균. */
  monthlyAverage: number
  categories: CategoryTrendRow[]
}

export interface CategoryTrendRow {
  category: ExpenseCategory
  categoryName: string
  totalAmount: number
  /** months 와 같은 순서·길이. */
  monthlyAmounts: number[]
  ratio: number
  /** 이 카테고리를 가장 많이 쓴 달. 지출이 없으면 null. */
  peakMonth: string | null
}

/** 월세·공과금·구독처럼 매달 반복되는 지출의 정의. 실제 지출은 서버 스케줄러가 만든다. */
export interface RecurringExpense {
  recurringExpenseId: number
  payer: MemberRef
  title: string
  amount: number
  category: ExpenseCategory
  categoryName: string
  payerBurdenRate: number
  /** 매월 며칠. 없는 달이 생기지 않도록 1~28 로 제한된다. */
  dayOfMonth: number
  startsOn: string
  endsOn: string | null
  memo: string | null
  active: boolean
  /** 다음으로 지출이 만들어질 날. 더 만들 것이 없으면 null. */
  nextDueDate: string | null
}

export interface RecurringExpenseCreateRequest {
  payerId: number
  title: string
  amount: number
  category: ExpenseCategory
  /** 생략하면 카테고리 프리셋을 따른다. 등록 시점에 확정되어 저장된다. */
  payerBurdenRate?: number | null
  dayOfMonth: number
  startsOn?: string | null
  endsOn?: string | null
  memo?: string | null
}

/** null 인 필드는 "변경 없음"이다. */
export type RecurringExpenseUpdateRequest = Partial<RecurringExpenseCreateRequest>

/**
 * 카테고리별 기본 부담 비율.
 *
 * 비율은 결제자 기준이 아니라 사람 기준이다. "월세는 성준 30%" 는
 * 이번 달 카드가 누구 것이든 그대로 유지된다.
 */
export interface BurdenPreset {
  category: ExpenseCategory
  categoryName: string
  rates: MemberBurdenRate[]
  /** false 면 저장된 값 없이 기본(반반)을 보여주는 중이다. */
  customized: boolean
}

export interface MemberBurdenRate {
  member: MemberRef
  burdenRate: number
}

export interface BurdenPresetUpdateRequest {
  presets: { category: ExpenseCategory; userId: number; burdenRate: number }[]
}

export interface TransferGuide {
  bank: Bank
  bankName: string
  accountNo: string
  holderName: string
  amount: number
  /** 복사 버튼 한 번으로 메신저에 붙여넣을 한 줄. */
  copyText: string
}

export interface Settlement {
  /** 확정 전이면 null. */
  settlementId: number | null
  period: string
  from: string
  to: string
  status: SettlementStatus
  netAmount: number
  creditor: MemberRef | null
  debtor: MemberRef | null
  expenseCount: number
  totalAmount: number
  /** 채권자가 계좌를 등록했을 때만 채워진다. */
  transfer: TransferGuide | null
  confirmedAt: string | null
  confirmedBy: MemberRef | null
}

export interface SettlementHistory {
  settlementId: number
  period: string
  status: SettlementStatus
  netAmount: number
  creditor: MemberRef | null
  debtor: MemberRef | null
  confirmedAt: string | null
}

export interface Account {
  bank: Bank
  bankName: string
  accountNo: string
  holderName: string
}

export interface AccountRequest {
  bank: Bank
  accountNo: string
  holderName: string
}

export interface Notification {
  notificationId: number
  type: NotificationType
  title: string
  body: string
  period: string | null
  read: boolean
  createdAt: string
}

export interface NotificationPage {
  content: Notification[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
  unreadCount: number
}

export type CoupleEventType =
  | 'EXPENSE_CREATED'
  | 'EXPENSE_UPDATED'
  | 'EXPENSE_DELETED'
  | 'SETTLEMENT_CONFIRMED'
  | 'RECURRING_EXPENSE_GENERATED'
  | 'NOTIFICATION_CREATED'

/**
 * 이벤트에 데이터는 실려 오지 않는다.
 * "무엇이 어느 달에서 바뀌었는지"만 알리므로 받는 쪽이 그 달을 다시 부른다.
 */
export interface CoupleEvent {
  type: CoupleEventType
  coupleId: number
  /** 이 변화를 일으킨 사람. 나면 무시해도 된다. */
  actorId: number | null
  resourceId: number | null
  period: string | null
  occurredAt: string
}
