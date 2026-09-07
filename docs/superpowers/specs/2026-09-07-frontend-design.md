# PairPay 프론트엔드 설계

노션 [🎨 PairPay 프론트 디자인](https://app.notion.com/p/3d4c8fd1afc98171b66feb0a6bd92665) 의 화면 명세를
실제 코드로 옮기기 위한 기술 설계. 화면 정의·컬러·타이포는 노션이 원본이고, 여기서는 그것을 어떻게 구현할지만 정한다.

## 결정

| 항목 | 선택 | 이유 |
|---|---|---|
| 스택 | Vite + React 19 + TypeScript | 액세스 토큰을 메모리에만 두는 인증 모델과 맞는다. SSR 이 있으면 서버에서 Bearer 토큰을 못 써 전 화면이 클라이언트 컴포넌트가 된다. |
| 위치 | 같은 레포 `web/` | API 계약이 바뀌면 한 커밋으로 같이 움직인다. Gradle 소스셋과 겹치지 않는다. |
| 라우팅 | React Router | SPA 라우팅만 필요하다. |
| 서버 상태 | TanStack Query | SSE 신호를 받아 특정 달만 무효화하는 데 쿼리 키가 그대로 쓰인다. |
| 스타일 | CSS 변수 + CSS Modules | 노션 토큰 9개와 역할별 라운드(카드 22 / 입력 14 / 칩 pill)를 1:1 로 옮긴다. |
| 개발 서버 | Vite :3000, `/api`·`/oauth2`·`/login/oauth2` 를 :8080 으로 프록시 | 백엔드가 이미 `localhost:3000` 을 전제로 CORS·리다이렉트·초대 링크를 설정해 두었고, `AuthCookieProperties` 주석이 "같은 오리진 프록시"를 기본 전제로 명시한다. 프록시를 쓰면 리프레시 쿠키가 same-origin 이라 CORS 자격증명 문제가 아예 없다. |

## 인증 흐름

액세스 토큰은 **메모리에만** 둔다. localStorage 에 두면 XSS 한 번에 새어 나가고,
어차피 리프레시 쿠키가 HttpOnly 라 새로고침 후 복구가 된다.

```
앱 부팅
  └─ POST /api/v1/auth/token   (쿠키 자동 전송)
       ├─ 200 → accessToken 메모리 저장 → GET /api/v1/users/me
       └─ 401 → 비로그인 → /login

로그인 버튼 → location.href = /oauth2/authorization/{kakao|google}
  └─ 백엔드가 쿠키 심고 → /oauth/callback?status=success&isNewUser=…
       └─ 위 부팅 절차를 그대로 한 번 더 태운다

API 401 → 토큰 재발급 1회 시도 → 성공하면 원요청 재시도, 실패하면 /login
```

동시에 여러 요청이 401 을 받아도 재발급은 **한 번만** 돈다(진행 중인 Promise 를 공유).
회전 토큰이라 두 번 부르면 뒤엣것이 재사용으로 탐지되어 family 전체가 폐기된다.

## 라우트와 게이트

`GET /users/me` 의 `coupleId` 와 `GET /couples/me` 의 `status` 로 갈 곳이 정해진다.

| 상태 | 보내는 곳 |
|---|---|
| 비로그인 | `/login` |
| 로그인 · `coupleId == null` | `/onboarding` (space 만들기) |
| 커플 `PENDING` | `/link` (초대 링크 공유) |
| 커플 `ACTIVE` | `/` (홈) |

`/invite/:token` 은 로그인 전에도 열린다(미리보기가 permitAll). 비로그인이면 토큰을
sessionStorage 에 넣고 로그인시킨 뒤 돌아와 수락한다.

## 화면 (1차 범위)

노션 6화면 + 흐름상 반드시 필요한 3개.

1. `/login` — 로고·태그라인·카카오/구글 버튼
2. `/onboarding` — space 이름 입력 *(흐름상 추가)*
3. `/link` — 초대 링크 발급·복사·공유, 연결 대기 시각화
4. `/invite/:token` — 초대 미리보기·수락 *(흐름상 추가)*
5. `/` — 월 선택 → 순잔액 히어로 → 총지출 → 최근 지출 → FAB
6. `/expenses/new` — 금액 → 내용 → 카테고리 칩 → 결제자 → 부담비율
7. `/monthly` — 월 합계 + 카테고리 막대 + 날짜별 리스트
8. `/settlement` — 순잔액 결과 + 계좌·금액 복사 + 확정
9. `/oauth/callback` — 토큰 교환 *(흐름상 추가)*

2차: 반복지출, 부담비율 프리셋, 알림함, 지출 추이, 계좌 등록, PWA.
단 계좌가 없으면 정산 화면의 송금 안내가 비므로 `/settlement` 에서 계좌 등록 진입점은 남긴다.

## 실시간

`GET /api/v1/events` 는 Bearer 헤더가 필요해 기본 `EventSource` 로는 못 붙는다.
`@microsoft/fetch-event-source` 로 연결하고, 이벤트에 실린 `period` 로 그 달의 쿼리만 무효화한다.

```
EXPENSE_*              → expenses(period), summary(period), settlement(period)
SETTLEMENT_CONFIRMED   → 위 전부 + settlements 이력
NOTIFICATION_CREATED   → notifications
```

`actorId === 내 userId` 인 이벤트는 무시한다. 내 화면은 이미 갱신됐다.

## 에러

백엔드가 `{code, message, path, timestamp, fieldErrors?}` 로 통일해 두었고 `message` 는
사용자에게 그대로 보여줄 수 있는 문장이다. 프론트는 문구를 다시 만들지 않고 그대로 띄운다.
분기가 필요한 곳만 `code` 를 본다(`EXPENSE_LOCKED`, `INVITE_*`, `ALREADY_IN_COUPLE` 등).

## 돈 표시 규칙

- 금액은 항상 정수 원. 부동소수 연산을 하지 않는다.
- 받을 돈 `#0E7C5A`, 줄 돈 `#C43B2E`. 색이 장식이 아니라 정보다.
- `font-variant-numeric: tabular-nums` 로 자릿수를 정렬한다.
