# duri 💸

커플 2인 전용 공동생활비·데이트비용 정산 웹앱의 백엔드.

월세·공과금·장보기·데이트비를 나눠 낼 때 "누가 얼마 냈지"를 없애는 것이 목표다.
소셜 로그인으로 둘이 같은 space를 쓰고, 지출을 기록하면 실시간 순잔액이 뜨며, 월말에 정산을 확정한다.

## 기술 스택

| | |
|---|---|
| 언어 · 런타임 | Kotlin 2.3.21 / Java 17 |
| 프레임워크 | Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7) |
| 영속성 | Spring Data JPA (Hibernate 7) + QueryDSL 5.1.0 |
| DB | PostgreSQL 17 |
| 마이그레이션 | Flyway 12 |
| 테스트 | JUnit 5 + Testcontainers 2 (실제 PostgreSQL) |
| 빌드 | Gradle 9.7.1 (Kotlin DSL) |

## 실행

PostgreSQL은 Docker로 띄운다. 호스트 포트는 **5433**을 쓴다(5432를 쓰는 다른 프로젝트와 겹치지 않도록).

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

`bootRun`은 `spring-boot-docker-compose`가 `compose.yaml`을 알아서 기동하므로 첫 명령은 생략해도 된다.
앱이 뜨면 Flyway가 스키마를 만들고, `hibernate.ddl-auto=validate`가 엔티티 매핑과 스키마 일치를 확인한다.

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
./gradlew test
```

Docker가 떠 있어야 한다. H2가 아니라 **실제 PostgreSQL 컨테이너** 위에서 돈다.
부분 유니크 인덱스·체크 제약·`~` 정규식 제약처럼 H2가 조용히 무시하는 것들이 이 프로젝트 규칙의 핵심이기 때문이다.

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
- **리프레시 토큰 회전 + 재사용 탐지.** 이미 쓴 토큰이 다시 들어오면 탈취로 보고 같은 회전 체인(family) 전체를 폐기한다.
  이 폐기는 요청이 거절되더라도 남아야 하므로 `noRollbackFor`로 처리한다.
- **필터체인 2개.** 소셜 로그인 핸드셰이크는 state 보관을 위해 세션을 허용하고, 실제 API는 완전 무상태다.
- **CSRF 비활성화.** 쿠키를 쓰는 곳은 리프레시 경로뿐이고 그 쿠키가 `SameSite=Lax`라 크로스사이트 POST에 실려가지 않는다.
  나머지는 Bearer 헤더 인증이라 CSRF 대상이 아니다.

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

`period` 는 `2026-09` 형식이며 생략하면 이번 달을 본다(정산 확정만 지난달).

에러 응답은 전부 같은 형태다.

```json
{
  "code": "INVITE_EXPIRED",
  "message": "만료된 초대 링크입니다.",
  "path": "/api/v1/invites/xxx/accept",
  "timestamp": "2026-09-07T01:37:52.371216Z"
}
```

## 패키지 구조

도메인별로 먼저 나누고, 그 안에서 레이어를 나눈다.

```
com.duri
├── common/        공통: 에러, 감사 필드, 암호화, 웹 지원
├── auth/          OAuth2 로그인, JWT 발급, 리프레시 토큰 회전
├── user/          사용자, 정산 계좌
├── couple/        커플 space, 초대 페어링
├── expense/       지출 (스키마 · 도메인 규칙)
└── settlement/    정산 (스키마 · 도메인 규칙)
```

## 스키마 설계에서 신경 쓴 것

규칙을 애플리케이션 코드에만 두지 않고 가능한 한 DB 제약으로 내렸다. 동시 요청이 코드의 검사 사이를 비집고 들어와도 깨지지 않게 하기 위해서다.

- **2인 고정** — `couple_members.member_no ∈ {1,2}` + `UNIQUE(couple_id, member_no)`.
  세 번째 사람은 들어올 자리 자체가 없다.
- **한 사람은 한 커플** — `UNIQUE(user_id) WHERE left_at IS NULL` 부분 인덱스.
- **살아있는 초대는 커플당 하나** — `UNIQUE(couple_id) WHERE accepted_at IS NULL AND revoked_at IS NULL`.
- **같은 달을 두 번 정산할 수 없다** — `UNIQUE(settlements.couple_id, period)`. 정산 확정 API 멱등성의 근거.
- **정산 방향의 일관성** — `net_amount = 0`이면 채권자·채무자가 없고, 0이 아니면 둘 다 있으면서 서로 달라야 한다는 체크 제약.
- **금액은 전부 `BIGINT`(원 단위 정수).** 부동소수점을 쓰지 않는다. 부담 비율을 곱해 남는 1원은 결제자가 흡수한다.
- **토큰은 원문을 저장하지 않는다.** 초대·리프레시 토큰 모두 SHA-256 해시만 보관한다.
- **계좌번호는 AES-256-GCM으로 암호화**해 저장한다(`EncryptedStringConverter`).

초대 수락 같은 동시성 지점은 세 겹으로 막는다: 초대 행 비관적 락 → 엔티티 상태 검증 → DB 유니크 제약.

## 순잔액 계산

지출 한 건에서 상대가 갚아야 할 몫은 `amount - (amount * payerBurdenRate / 100)` 이다.
정수 나눗셈이라 나누어떨어지지 않고 남는 1원은 결제자가 흡수한다.

한 달 순잔액은 이 값을 사람별로 합쳐 뺀 차액이다.

```
순잔액(A) = Σ(A 가 결제한 건의 상대 몫) - Σ(B 가 결제한 건의 상대 몫)
```

이 계산은 **건별로 먼저 내림한 뒤 합친다**. 합계를 먼저 내고 비율을 곱하면 반올림 위치가
달라져 1원씩 어긋나기 때문이다. 집계 쿼리(QueryDSL)도 엔티티와 같은 식을 쓰므로
목록에서 건별로 더한 값과 요약 화면의 값이 항상 일치한다.

순잔액은 지출에서 파생되는 값이라 정산 레코드가 없어도 계산된다.
정산 확정(W5-6)은 이 값을 특정 시점에 고정하는 일이 된다.

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

계좌번호는 AES-256-GCM 으로 암호화되어 저장되고, 저장 전에 하이픈을 걷어내
복사한 값의 형식이 항상 같도록 맞춘다. 은행은 자유 문자열이 아니라 표준 기관코드를 가진
열거형으로 받는다. "국민"과 "KB국민"이 섞이면 붙여넣을 때마다 확인해야 하기 때문이다.

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

## 진행 상황

3개월 로드맵 기준 **W1–W8 완료**.

- [x] 프로젝트 셋업 (레포·빌드·로컬 인프라)
- [x] 소셜 로그인 (카카오/구글 OAuth2 + JWT 회전)
- [x] 커플 space 생성 + 초대 링크 페어링
- [x] DB 스키마 전체 (지출·정산 포함)
- [x] 지출 CRUD + 순잔액 계산 + 월별 뷰 ← **MVP 완료 지점**
- [x] 정산 사이클 (마감 / 확정 / 잠금, 멱등)
- [x] 정산 화면 계좌·금액 복사
- [x] 반복지출 스케줄러 (월세·공과금·구독)
- [x] 카테고리별 부담비율 프리셋
- [ ] 실시간 동기화 (동시 편집)
- [ ] 월별 카테고리 통계
- [ ] 정산일 리마인드 알림
- [ ] PWA + 배포
