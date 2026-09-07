/**
 * 액세스 토큰은 메모리에만 둔다.
 *
 * localStorage 에 두면 XSS 한 번에 새어 나가고, 어차피 리프레시 토큰이 HttpOnly 쿠키라
 * 새로고침 후에는 POST /auth/token 으로 되찾을 수 있다. 저장할 이유가 없다.
 */
let accessToken: string | null = null

/** 이 시각이 지나면 만료된 것으로 본다(epoch ms). */
let expiresAt = 0

/** 만료 직전에 미리 갱신하기 위한 여유. 왕복 지연으로 401 이 나는 걸 줄인다. */
const EXPIRY_MARGIN_MS = 30_000

export function getAccessToken(): string | null {
  return accessToken
}

export function isAccessTokenUsable(): boolean {
  return accessToken !== null && Date.now() < expiresAt - EXPIRY_MARGIN_MS
}

export function setAccessToken(token: string, expiresInSeconds: number): void {
  accessToken = token
  expiresAt = Date.now() + expiresInSeconds * 1000
}

export function clearAccessToken(): void {
  accessToken = null
  expiresAt = 0
}
