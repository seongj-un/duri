/**
 * 초대 링크를 비로그인 상태로 열었을 때, 로그인 왕복을 건너온 뒤에도
 * 원래 가려던 초대로 돌아가기 위한 자리.
 *
 * sessionStorage 를 쓰는 이유: 탭을 닫으면 사라져야 한다.
 * 남의 기기에서 로그인했다가 다음 사람이 그 초대를 수락하면 안 된다.
 */
const KEY = 'pairpay.pendingInvite'

export function rememberPendingInvite(token: string): void {
  try {
    sessionStorage.setItem(KEY, token)
  } catch {
    // 사파리 프라이빗 모드 등에서 막힐 수 있다. 못 기억해도 로그인 자체는 된다.
  }
}

export function takePendingInvite(): string | null {
  try {
    const token = sessionStorage.getItem(KEY)
    if (token) sessionStorage.removeItem(KEY)
    return token
  } catch {
    return null
  }
}
