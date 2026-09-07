# PairPay 💸

커플 2인 전용 공동생활비·데이트비용 정산 웹앱.

월세·공과금·장보기·데이트비를 나눠 낼 때 "누가 얼마 냈지"를 없애는 것이 목표다.
소셜 로그인으로 둘이 같은 space를 쓰고, 지출을 기록하면 실시간 순잔액이 뜨며, 월말에 정산을 확정한다.

**[pairpay-two.vercel.app](https://pairpay-two.vercel.app)** — 프론트엔드만 올라가 있다.
백엔드는 아직 어디에도 떠 있지 않아 로그인은 동작하지 않는다([배포](#배포) 참고).

> 저장소 이름은 `duri`, 제품 이름은 `PairPay`다. 초기 작업 이름이 그대로 남았다.

---

**목차**

[기술 스택](#기술-스택) · [실행](#실행) · [화면](#화면) · [인증 흐름](#인증-흐름) ·
[순잔액 계산](#순잔액-계산) · [정산 사이클](#정산-사이클) · [반복지출](#반복지출) ·
[부담비율 프리셋](#카테고리별-부담비율-프리셋) · [실시간 동기화](#실시간-동기화) · [알림](#알림) ·
[스키마 설계](#스키마-설계에서-신경-쓴-것) · [프론트엔드 설계](#프론트엔드-설계) ·
[패키지 구조](#패키지-구조) · [API](#api) · [배포](#배포) · [진행 상황](#진행-상황)

---

## 기술 스택

| | |
|---|---|
| **백엔드** | Kotlin 2.3.21 / Java 17 · Spring Boot 4.1.1 (Framework 7, Security 7) |
| 영속성 | Spring Data JPA (Hibernate 7) + QueryDSL 5.1.0 · PostgreSQL 17 · Flyway 12 |
| 백엔드 테스트 | JUnit 5 + Testcontainers 2 (실제 PostgreSQL) — 166개 |
| 백엔드 빌드 | Gradle 9.7.1 (Kotlin DSL) |
| **프론트엔드** | React 19 + TypeScript · Vite 6 |
| 상태 · 라우팅 | TanStack Query 5 · React Router 7 |
| 스타일 | CSS 변수 + CSS Modules (Pretendard) |
| 실시간 | `@microsoft/fetch-event-source` |
| **인프라** | Docker (멀티스테이지) · GitHub Actions · Vercel (프론트) |

## 실행

### 백엔드

PostgreSQL은 Docker로 띄운다. 호스트 포트는 **5433**을 쓴다(5432를 쓰는 다른 프로젝트와 겹치지 않도록).

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

`bootRun`은 `spring-boot-docker-compose`가 `compose.yaml`을 알아서 기동하므로 첫 명령은 생략해도 된다.
앱이 뜨면 Flyway가 스키마를 만들고, `hibernate.ddl-auto=validate`가 엔티티 매핑과 스키마 일치를 확인한다.

### 프론트엔드

```bash
cd web && npm install && npm run dev
```

**포트 3000은 고정이다.** 백엔드가 CORS 허용 오리진·OAuth 착지 주소·초대 링크를 모두
`http://localhost:3000`으로 잡아 두었기 때문이다. 개발 서버는 `/api`·`/oauth2`·`/login/oauth2`를
`:8080`으로 프록시한다. 같은 오리진이 되므로 리프레시 쿠키(`path=/api/v1/auth`, `SameSite=Lax`)가
자격증명 설정 없이 그대로 실려 간다.

### 소셜 로그인 설정

기본값은 개발용 더미라 로그인 자체는 동작하지 않는다. 실제로 붙이려면:

```bash
cp .env.example .env
```

`.env`를 채운 뒤 환경변수로 넣고 실행한다. 키 생성:

```bash
openssl rand -base64 48
```

- 카카오: [developers.kakao.com](https://developers.kakao.com) → 내 애플리케이션 → REST API 키 / Client Secret
- 구글: [console.cloud.google.com](https://console.cloud.google.com) → API 및 서비스 → 사용자 인증 정보
- 두 곳 모두 리다이렉트 URI에 `http://localhost:8080/login/oauth2/code/{kakao|google}` 등록

### 테스트

```bash
./gradlew test          # 백엔드 166개
cd web && npm run build # 프론트 타입체크 + 번들
```

백엔드 테스트는 Docker가 떠 있어야 한다. H2가 아니라 **실제 PostgreSQL 컨테이너** 위에서 돈다.
부분 유니크 인덱스·체크 제약·`~` 정규식 제약처럼 H2가 조용히 무시하는 것들이 이 프로젝트 규칙의 핵심이기 때문이다.

## 화면

모바일 우선. 화면 정의·컬러 토큰·타이포는 노션 문서 *PairPay 프론트 디자인*이 원본이다.

| 화면 | 경로 | 하는 일 |
|---|---|---|
| 로그인 | `/login` | 카카오·구글 |
| OAuth 착지 | `/oauth/callback` | 리프레시 쿠키로 액세스 토큰 교환 |
| space 만들기 | `/onboarding` | 커플 없는 사용자의 첫 화면 |
| 파트너 연결 | `/link` | 초대 링크 발급·공유, 연결 대기 |
| 초대 수락 | `/invite/:token` | 로그인 전에도 열린다 |
| **홈** | `/` | 월 선택 → 순잔액 히어로 → 총지출 → 최근 지출 |
| 지출 등록 | `/expenses/new` | 금액 → 내용 → 카테고리 → 결제자 → 부담비율 |
| 월별 내역 | `/monthly` | 합계 + 카테고리 막대(결제자별 색) + 날짜별 목록 |
| 정산 | `/settlement` | 순잔액 · 계좌 복사 · 확정 |
| 지출 추이 | `/trend` | 최근 3·6·12개월 카테고리별 |
| 알림함 | `/notifications` | 정산 리마인드 |
| 설정 | `/settings` | space 이름 · 정산 기준일 · 관리 화면 입구 |
| 내 계좌 | `/account` | 정산 때 상대에게 보여줄 입금 계좌 |
| 반복지출 | `/recurring`, `/recurring/new`, `/recurring/:id` | 월세·공과금·구독 정의 |
| 부담 비율 | `/burden-presets` | 카테고리별 기본 비율 |

디자인 원칙 네 가지가 나머지를 결정한다.

- **히어로는 순잔액.** "누가 누구에게 얼마"를 홈 상단에 크게 둔다. 커플이 가장 궁금한 것에 즉시 답한다.
- **돈 의미 = 색.** 받을 돈 `#0E7C5A`, 줄 돈 `#C43B2E`. 장식이 아니라 정보로서의 색이다.
- **두 사람 전면.** 나·상대 아바타를 곳곳에 둬서 "우리 둘"이 느껴지게 한다.
- **조용한 나머지.** 히어로에만 색과 볼륨을 쓰고, 나머지는 큰 여백과 헤어라인으로 정리한다.

## 인증 흐름

액세스 토큰은 JWT(HS256), 리프레시 토큰은 DB에 해시로만 저장하는 불투명 난수다.

```
브라우저                       서버                        카카오/구글
   │                           │                              │
   │ GET /oauth2/authorization/kakao                          │
   ├──────────────────────────>│───── 302 ───────────────────>│
   │                           │<──── code ───────────────────┤
   │                           │
   │   Set-Cookie: duri_rt (HttpOnly)  +  302 → 프론트엔드
   │<──────────────────────────┤
   │
   │ POST /api/v1/auth/token   (쿠키 자동 전송)
   ├──────────────────────────>│
   │<── accessToken + 새 duri_rt ──┤   ← 호출할 때마다 리프레시 토큰이 회전한다
   │
   │ Authorization: Bearer <accessToken>
   ├──────────────────────────>│
```

설계상 결정한 것들:

- **액세스 토큰을 URL에 싣지 않는다.** 주소창·브라우저 히스토리·리퍼러·프록시 로그에 남기 때문이다.
  로그인 성공 시엔 리프레시 쿠키만 심고, 프론트가 착지 후 `POST /api/v1/auth/token`으로 액세스 토큰을 받아간다.
- **액세스 토큰은 브라우저 메모리에만 둔다.** `localStorage`에 두면 XSS 한 번에 새어 나가고,
  리프레시 쿠키가 HttpOnly라 새로고침 후 되찾을 수 있어 저장할 이유가 없다.
- **리프레시 토큰 회전 + 재사용 탐지.** 이미 쓴 토큰이 다시 들어오면 탈취로 보고 같은 회전 체인(family) 전체를 폐기한다.
  이 폐기는 요청이 거절되더라도 남아야 하므로 `noRollbackFor`로 처리한다.
  프론트도 같은 이유로 **재발급을 한 번에 하나만** 돌린다 — 여러 요청이 동시에 401을 받아도 진행 중인 Promise를 나눠 쓴다.
- **필터체인 2개.** 소셜 로그인 핸드셰이크는 state 보관을 위해 세션을 허용하고, 실제 API는 완전 무상태다.
- **CSRF 비활성화.** 쿠키를 쓰는 곳은 리프레시 경로뿐이고 그 쿠키가 `SameSite=Lax`라 크로스사이트 POST에 실려가지 않는다.
  나머지는 Bearer 헤더 인증이라 CSRF 대상이 아니다.

## 순잔액 계산

지출 한 건에서 상대가 갚아야 할 몫은 `amount * (100 - payerBurdenRate) / 100` 이다.
정수 나눗셈이라 내림이 상대 몫 쪽에서 일어나고, 나누어떨어지지 않고 남는 1원은
`amount`에서 뺀 나머지로 결제자가 흡수한다. 돈을 먼저 낸 쪽이 우수리를 떠안는 셈이고,
덕분에 "반반"인데 상대가 절반을 넘게 갚는 일이 생기지 않는다 (101원 → 결제자 51 / 상대 50).

한 달 순잔액은 이 값을 사람별로 합쳐 뺀 차액이다.

```
순잔액(A) = Σ(A 가 결제한 건의 상대 몫) - Σ(B 가 결제한 건의 상대 몫)
```

이 계산은 **건별로 먼저 내림한 뒤 합친다**. 합계를 먼저 내고 비율을 곱하면 반올림 위치가
달라져 1원씩 어긋나기 때문이다. 집계 쿼리(QueryDSL)도 엔티티와 같은 식을 쓰므로
목록에서 건별로 더한 값과 요약 화면의 값이 항상 일치한다. 지출 등록 화면의 분담 미리보기도 같은 식이다.

순잔액은 지출에서 파생되는 값이라 정산 레코드가 없어도 계산된다.
정산 확정은 이 값을 특정 시점에 고정하는 일이 된다.

지출 수정·삭제는 두 층위로 잠긴다.

- 확정된 정산에 귀속된 지출(`settlement_id`)은 개별적으로 잠긴다.
- 확정된 달에는 지출을 새로 넣거나, 다른 달에서 옮겨 넣을 수도 없다.

## 정산 사이클

```
 마감  달이 지나면 그 달의 순잔액은 더 움직이지 않는다
  ↓
 확정  그 시점의 순잔액을 정산 레코드에 고정한다
  ↓
 잠금  그 달의 지출에 settlement_id 를 채워 수정·삭제를 막는다
```

**확정은 멱등하다.** 두 사람이 동시에 눌러도, 같은 사람이 두 번 눌러도 정산은 하나이고
금액도 확정자도 바뀌지 않는다. 커플 행에 비관적 락을 잡아 같은 커플의 확정을 직렬화하고,
그래도 뚫리면 `UNIQUE(couple_id, period)` 가 막는다. 두 스레드가 동시에 확정하는 테스트로 검증한다.

확정 전 조회는 정산 레코드 없이 지출에서 실시간으로 계산해 보여준다.
지출이 하나도 없는 달도 0원으로 확정할 수 있고, 아직 오지 않은 달은 확정할 수 없다.

### 송금 안내

토스 딥링크는 이번 범위 밖이라, 앱이 할 수 있는 최선은 계좌와 금액을 한 번에 복사하게 해 주는 것이다.
확정된 정산에는 채권자의 계좌로 만든 `copyText` 가 함께 내려간다.

```
카카오뱅크 3333011234567 박성준 710,000원
```

계좌번호는 AES-256-GCM으로 암호화되어 저장되고, 저장 전에 하이픈을 걷어내
복사한 값의 형식이 항상 같도록 맞춘다. 은행은 자유 문자열이 아니라 표준 기관코드를 가진
열거형으로 받는다. "국민"과 "KB국민"이 섞이면 붙여넣을 때마다 확인해야 하기 때문이다.

화면에서는 은행·계좌번호와 예금주·금액을 두 줄로 나눠 보여준다. 보내기 전에 눈으로 대조하는
값이라 말줄임으로 가리지 않는다. 복사 버튼은 위 한 줄을 그대로 클립보드에 넣는다.

## 반복지출

월세·공과금·구독처럼 매달 같은 날 나가는 지출은 정의만 등록해 두면 스케줄러가 매일 00:10(KST)에
그날치를 실제 지출로 만든다. 만들어진 지출은 사람이 넣은 것과 똑같아서 그대로 수정·삭제할 수 있고,
정의를 나중에 바꿔도 지나간 달의 금액이 소급해서 흔들리지 않는다.

- **중복 생성 차단** — `UNIQUE(recurring_expense_id, date_trunc('month', spent_at))` 부분 인덱스.
  스케줄러가 여러 번 돌아도(재시작·다중 인스턴스) 한 달에 한 건이다.
  사용자가 지운 건도 "생성됨"으로 세므로 지운 지출이 되살아나지 않는다.
- **따라잡기** — 발생일에 서버가 꺼져 있었다면 다음 실행에서 메운다. 지출 날짜는 실행일이 아니라 발생일로 남는다.
- **건별 트랜잭션** — 한 커플의 월세 생성이 실패해도 다른 커플의 구독료까지 롤백되지 않는다.
- 확정된 달에는 끼워 넣지 않고, 파트너가 없는 space 에는 만들지 않는다.
- 발생일은 1~28일로 제한한다. 29~31일은 없는 달이 있기 때문이다.

시간대는 한국 고정이다. "오늘 발생하는 반복지출"이 사용자가 보는 달력과 어긋나면 안 되므로
`Clock` 빈 자체를 `Asia/Seoul` 로 둔다.

## 카테고리별 부담비율 프리셋

"월세는 성준 30 / 지현 70" 처럼 카테고리마다 기본 비율을 정해 두면, 지출 등록에서 비율을 생략했을 때 이를 따른다.

비율은 **결제자 기준이 아니라 사람 기준**으로 저장한다. 결제자 기준으로 저장하면 이번 달 카드가
누구 것이냐에 따라 부담이 뒤집힌다. 커플은 자리가 1번·2번 둘뿐이므로 1번의 비율만 저장하고
2번은 그 나머지로 계산하며, 지출을 만들 때 결제자 기준으로 뒤집어 준다.
프론트도 같은 변환을 거쳐(`web/src/lib/burden.ts`) 지출 등록 화면의 기본 비율을 채운다.

## 실시간 동기화

둘이 같은 화면을 보고 있을 때 한 쪽이 지출을 넣으면 상대 화면도 따라 바뀌어야 한다.
커플 단위 SSE 스트림(`GET /api/v1/events`)으로 변경 신호를 밀어 준다.

이벤트에는 **데이터를 싣지 않는다.** "무엇이 어느 달에서 바뀌었는지"만 알리고,
받는 쪽이 그 달만 다시 불러온다. 페이로드를 실으면 발행 지점마다 권한 검사를 다시 해야 하고,
받는 쪽이 이미 들고 있는 화면 상태와 어긋날 수 있다.

```
서비스 → ApplicationEvent → @TransactionalEventListener(AFTER_COMMIT) → SSE
```

커밋된 뒤에만 내보낸다. 롤백된 변경을 알리면 있지도 않은 지출이 상대 화면에 뜬다.
리스너는 `@Async` 라 SSE I/O 가 요청 응답 시간에 섞이지 않는다.

브라우저 기본 `EventSource` 는 헤더를 못 붙이므로 프론트는 **fetch 기반 SSE**를 쓴다
(`@microsoft/fetch-event-source`). 토큰을 쿼리스트링에 실으면 주소창·프록시 로그에 남는다.
받는 쪽은 이벤트의 `period` 로 그 달의 쿼리만 무효화하고, 자기가 일으킨 이벤트(`actorId`)는 무시한다.

연결은 이 인스턴스의 메모리에만 있다. 서버를 여러 대로 늘리면 Redis pub/sub 같은 중계가 필요하다.
2인용 앱에 단일 인스턴스라 지금은 이 단순함이 이득이다.

## 알림

정산 기준일이 되면 **지난달**을 정산하라고 두 사람에게 알린다
(`POST /settlements/confirm` 이 기간을 생략했을 때 지난달을 마감하는 것과 같은 규칙).

```
12월 정산할 시간이에요
지현님이 성준님에게 150,000원 보내면 정산 완료예요.
```

이미 확정한 달은 알리지 않는다. 같은 사람에게 같은 기간의 알림을 두 번 보내지 않는 것은
`UNIQUE(user_id, type, period)` 부분 인덱스가 보장한다.

알림은 DB에 쌓이고 SSE로 실시간 전달되며 알림함 화면에서 읽는다.
**웹푸시·메일 발송은 아직 없다** — VAPID 키·SMTP 자격증명이 필요하고,
서비스워커에도 `push` 핸들러를 붙여야 한다. 그 설정이 준비되면 붙인다.

## 스키마 설계에서 신경 쓴 것

규칙을 애플리케이션 코드에만 두지 않고 가능한 한 DB 제약으로 내렸다.
동시 요청이 코드의 검사 사이를 비집고 들어와도 깨지지 않게 하기 위해서다.

- **2인 고정** — `couple_members.member_no ∈ {1,2}` + `UNIQUE(couple_id, member_no)`.
  세 번째 사람은 들어올 자리 자체가 없다.
- **한 사람은 한 커플** — `UNIQUE(user_id) WHERE left_at IS NULL` 부분 인덱스.
- **살아있는 초대는 커플당 하나** — `UNIQUE(couple_id) WHERE accepted_at IS NULL AND revoked_at IS NULL`.
- **같은 달을 두 번 정산할 수 없다** — `UNIQUE(settlements.couple_id, period)`. 정산 확정 API 멱등성의 근거.
- **정산 방향의 일관성** — `net_amount = 0`이면 채권자·채무자가 없고, 0이 아니면 둘 다 있으면서 서로 달라야 한다는 체크 제약.
- **금액은 전부 `BIGINT`(원 단위 정수).** 부동소수점을 쓰지 않는다.
- **토큰은 원문을 저장하지 않는다.** 초대·리프레시 토큰 모두 SHA-256 해시만 보관한다.
- **계좌번호는 AES-256-GCM으로 암호화**해 저장한다(`EncryptedStringConverter`).

초대 수락 같은 동시성 지점은 세 겹으로 막는다: 초대 행 비관적 락 → 엔티티 상태 검증 → DB 유니크 제약.

## 프론트엔드 설계

구현 결정과 근거는 `docs/superpowers/specs/2026-09-07-frontend-design.md` 에 있다.

- **SPA를 골랐다.** 전 화면이 로그인 필수라 SSR 이득이 거의 없고, 서버 컴포넌트에서는
  Bearer 토큰을 못 써서 결국 대부분이 클라이언트가 된다.
- **에러 문구를 프론트에서 다시 만들지 않는다.** 백엔드 `message`가 이미 사용자에게 보여줄
  문장이라 그대로 띄우고, 분기가 필요한 곳만 `code`를 본다.
- **빈 화면은 다음 행동을 준다.** "첫 지출을 추가해 보세요" 처럼. 에러는 사과 대신
  무엇이 잘못됐고 어떻게 고치는지 말한다.
- **버튼은 하지 않은 일을 말하지 않는다.** 저장 전에는 "변경사항 없음", 저장한 뒤에 "저장됨"이다.
- **서비스워커는 API 응답을 캐시하지 않는다.** `/api/`·`/oauth2/`·`/login/`은 통과시키고
  앱 껍데기와 해시된 정적 자산만 캐시한다. 가계부에서 오래된 금액을 보여주는 건
  아무것도 안 보여주는 것보다 나쁘다 — 오프라인에서 "지난주 순잔액"이 아무 표시 없이 떠 있으면
  그걸로 송금하게 된다.
- **금액은 `tabular-nums`.** 자릿수가 흔들리면 숫자를 비교할 수 없다.

## 패키지 구조

도메인별로 먼저 나누고, 그 안에서 레이어를 나눈다.

```
com.duri
├── common/        공통: 에러, 감사 필드, 암호화, 웹 지원
├── auth/          OAuth2 로그인, JWT 발급, 리프레시 토큰 회전
├── user/          사용자, 정산 계좌
├── couple/        커플 space, 초대 페어링
├── expense/       지출, 반복지출, 부담비율 프리셋, 요약·추이
├── settlement/    정산
├── notification/  알림
└── realtime/      SSE 브로커
```

```
web/src
├── lib/api/       백엔드 DTO 미러 타입, fetch 클라이언트, 엔드포인트
├── lib/auth/      토큰 저장소, 세션 컨텍스트
├── lib/realtime/  SSE 구독과 쿼리 무효화
├── components/    아바타 · 금액 · 카드 · 칩 · 토글 · 탭바 …
└── routes/        화면 16개
```

## API

| 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|
| GET | `/oauth2/authorization/{kakao\|google}` | - | 소셜 로그인 시작 |
| POST | `/api/v1/auth/token` | 쿠키 | 액세스 토큰 발급/재발급 (리프레시 회전) |
| POST | `/api/v1/auth/logout` | 쿠키 | 로그아웃 |
| GET | `/api/v1/users/me` | Bearer | 내 정보 + 소속 커플 id (부트스트랩) |
| POST | `/api/v1/couples` | Bearer | 커플 space 생성 |
| GET | `/api/v1/couples/me` | Bearer | 내 space 조회 |
| PATCH | `/api/v1/couples/me` | Bearer | 이름 / 정산 기준일 변경 |
| POST | `/api/v1/couples/me/invites` | Bearer | 초대 링크 발급 |
| GET | `/api/v1/invites/{token}` | - | 초대 미리보기 |
| POST | `/api/v1/invites/{token}/accept` | Bearer | 초대 수락 (페어링 완료) |
| POST | `/api/v1/expenses` | Bearer | 지출 등록 |
| GET | `/api/v1/expenses` | Bearer | 월별 지출 목록 (기간·카테고리·결제자 필터, 페이징) |
| GET | `/api/v1/expenses/{id}` | Bearer | 지출 단건 |
| PATCH | `/api/v1/expenses/{id}` | Bearer | 지출 수정 |
| DELETE | `/api/v1/expenses/{id}` | Bearer | 지출 삭제 (소프트) |
| GET | `/api/v1/summaries/monthly` | Bearer | 월별 요약: 합계 · 사람별 부담 · 카테고리 비중 · 순잔액 |
| GET | `/api/v1/summaries/trend` | Bearer | 최근 N개월 카테고리별 지출 추이 |
| GET | `/api/v1/settlements` | Bearer | 정산 이력 |
| GET | `/api/v1/settlements/{period}` | Bearer | 특정 달 정산 현황 (확정 전이면 실시간 계산) |
| POST | `/api/v1/settlements/{period}/confirm` | Bearer | 정산 확정 (멱등) |
| POST | `/api/v1/settlements/confirm` | Bearer | 지난달 정산 확정 |
| GET | `/api/v1/users/me/account` | Bearer | 내 입금 계좌 |
| PUT | `/api/v1/users/me/account` | Bearer | 입금 계좌 등록 · 수정 |
| DELETE | `/api/v1/users/me/account` | Bearer | 입금 계좌 삭제 |
| POST | `/api/v1/recurring-expenses` | Bearer | 반복지출 등록 |
| GET | `/api/v1/recurring-expenses` | Bearer | 반복지출 목록 (다음 발생일 포함) |
| PATCH | `/api/v1/recurring-expenses/{id}` | Bearer | 반복지출 수정 |
| POST | `/api/v1/recurring-expenses/{id}/activate` | Bearer | 반복지출 재개 |
| POST | `/api/v1/recurring-expenses/{id}/deactivate` | Bearer | 반복지출 중지 |
| DELETE | `/api/v1/recurring-expenses/{id}` | Bearer | 반복지출 삭제 (생성 이력이 없을 때만) |
| GET | `/api/v1/couples/me/burden-presets` | Bearer | 카테고리별 기본 부담비율 |
| PUT | `/api/v1/couples/me/burden-presets` | Bearer | 카테고리별 기본 부담비율 저장 |
| GET | `/api/v1/events` | Bearer | 실시간 변경 스트림 (SSE) |
| GET | `/api/v1/notifications` | Bearer | 알림함 (안 읽은 개수 포함) |
| POST | `/api/v1/notifications/{id}/read` | Bearer | 알림 읽음 |
| POST | `/api/v1/notifications/read-all` | Bearer | 모두 읽음 |

`period` 는 `2026-09` 형식이며 생략하면 이번 달을 본다(정산 확정만 지난달).

에러 응답은 전부 같은 형태다. `message` 는 사용자에게 그대로 보여줄 수 있는 문장이고,
`code` 는 클라이언트가 분기할 수 있도록 안정적으로 유지한다.

```json
{
  "code": "INVITE_EXPIRED",
  "message": "만료된 초대 링크입니다.",
  "path": "/api/v1/invites/xxx/accept",
  "timestamp": "2026-09-07T01:37:52.371216Z"
}
```

## 배포

**프론트엔드**는 Vercel에 올라가 있다 — [pairpay-two.vercel.app](https://pairpay-two.vercel.app).
SPA라 모든 경로를 `index.html`로 되돌리고, `sw.js`는 캐시하지 않으며(캐시되면 서비스워커가
영영 갱신되지 않는다) `/assets/*`는 파일명에 해시가 박혀 있어 영구 캐시로 둔다.

**백엔드는 Railway로 간다.** Vercel에는 Java 런타임이 없고, 이 앱은 서버리스에 맞지도 않다 —
SSE가 30분짜리 연결을 붙들어야 하고 반복지출·알림 스케줄러가 상주 프로세스를 필요로 한다.

`railway.json`이 Dockerfile 빌더와 `/actuator/health` 헬스체크를 지정한다.
**복제본은 1개로 고정**했다. SSE 연결이 인스턴스 메모리에만 있어 두 대로 늘리면 상대에게 신호가
닿지 않고, 스케줄러도 중복 실행된다(중복 생성 자체는 DB 유니크 제약이 막지만 헛일을 한다).

### 배포 절차

1. Railway 프로젝트에 이 레포를 연결하고 **PostgreSQL** 플러그인을 추가한다.
2. 백엔드 서비스에 환경변수를 넣는다. DB 값은 Railway의 변수 참조를 그대로 쓴다.

```
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}

JWT_SECRET=<openssl rand -base64 48>
ACCOUNT_ENCRYPTION_KEY=<openssl rand -base64 32>

KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

FORWARD_HEADERS_STRATEGY=framework
CORS_ALLOWED_ORIGINS=https://pairpay-two.vercel.app
OAUTH2_REDIRECT_URI=https://pairpay-two.vercel.app/oauth/callback
OAUTH2_ALLOWED_REDIRECT_HOSTS=pairpay-two.vercel.app
INVITE_BASE_URL=https://pairpay-two.vercel.app/invite
COOKIE_SAME_SITE=None
COOKIE_SECURE=true
```

`PORT`는 Railway가 알아서 주입한다.

3. 카카오·구글 콘솔의 리다이렉트 URI에 `https://<백엔드도메인>/login/oauth2/code/kakao`(구글도 동일)를 등록한다.
4. Vercel에 `VITE_API_BASE_URL=https://<백엔드도메인>`을 넣고 다시 배포한다. **빌드 시점에 박히는 값이라 재배포가 필요하다.**

### 왜 이 값들이 필요한가

- **`FORWARD_HEADERS_STRATEGY=framework`** — 프록시 뒤에서는 TLS가 앞단에서 끝난다. 이게 없으면
  OAuth `redirect_uri`가 `http://<내부호스트>:<포트>/...`로 만들어져 카카오·구글이 거부한다.
  기본값이 `none`인 것은 의도한 것이다 — 프록시가 없는데 `X-Forwarded-*`를 믿으면
  헤더를 위조해 리다이렉트 주소를 바꿔치기할 수 있다. 프록시 뒤에서만 켠다.
- **`COOKIE_SAME_SITE=None` + `COOKIE_SECURE=true`** — 프론트와 백엔드가 다른 도메인이라
  `Lax`면 리프레시 쿠키가 아예 실리지 않는다.
- **`OAUTH2_ALLOWED_REDIRECT_HOSTS`** — 여기 없는 호스트를 `OAUTH2_REDIRECT_URI`에 넣으면
  부팅 때 바로 죽는다. 설정 실수로 로그인 결과가 엉뚱한 곳으로 흘러가는 것을 막기 위한 검사다.

### 로컬에서 확인하기

배포 전에 같은 설정으로 컨테이너를 띄워 볼 수 있다.

```bash
docker build -t duri .
```

멀티스테이지 빌드로 JRE만 담고, root가 아닌 전용 사용자로 실행한다. 타임존은 `Asia/Seoul` 고정.

GitHub Actions(`.github/workflows/ci.yml`)가 푸시·PR마다 `./gradlew build` 를 돌린다.
Testcontainers가 러너의 Docker로 실제 PostgreSQL을 띄우므로 별도 서비스 설정이 필요 없다.
실패하면 테스트 리포트를 아티팩트로 올린다.

## 진행 상황

3개월 로드맵 기준 **W1–W12 완료**. 백엔드와 프론트엔드 모두 계획한 범위를 채웠다.

- [x] 프로젝트 셋업 (레포·빌드·로컬 인프라)
- [x] 소셜 로그인 (카카오/구글 OAuth2 + JWT 회전)
- [x] 커플 space 생성 + 초대 링크 페어링
- [x] DB 스키마 전체
- [x] 지출 CRUD + 순잔액 계산 + 월별 뷰 ← **MVP 완료 지점**
- [x] 정산 사이클 (마감 / 확정 / 잠금, 멱등)
- [x] 정산 화면 계좌·금액 복사
- [x] 반복지출 스케줄러 (월세·공과금·구독)
- [x] 카테고리별 부담비율 프리셋
- [x] 실시간 동기화 (동시 편집) — SSE
- [x] 월별 카테고리 통계 + 지출 추이
- [x] 정산일 리마인드 알림 (인앱)
- [x] 프론트엔드 화면 16개
- [x] PWA (홈 화면 설치 · 오프라인 껍데기)
- [x] 프론트엔드 배포 (Vercel)
- [ ] 백엔드 배포 — 호스팅 미정
- [ ] 웹푸시 · 메일 발송 — VAPID 키 · SMTP 필요
- [ ] 정산 확정 취소(unlock) — 실수로 확정하면 그 달 지출을 영영 못 고친다. 실사용 전 필요
