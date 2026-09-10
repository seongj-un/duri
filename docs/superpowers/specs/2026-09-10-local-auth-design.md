# 자체 이메일/비밀번호 로그인 전환

- 날짜: 2026-09-10
- 상태: 승인됨

## 배경

카카오 Client Secret 발급이 비즈니스 앱 인증에 막혀 소셜 로그인을 완성할 수 없다.
구글까지 붙여도 외부 콘솔 설정에 계속 묶이므로, 외부 의존을 없애고 자체 로그인으로 전환한다.

JWT 액세스 토큰과 리프레시 토큰 회전 구조(`AccessTokenIssuer`, `RefreshTokenService`,
`AuthCookieFactory`)는 provider 와 무관하게 `userId` 만 다룬다. 따라서 그대로 재사용하고,
"신원을 어떻게 확인하는가" 한 겹만 교체한다.

## 범위

포함:

- 이메일 + 비밀번호 회원가입 / 로그인
- OAuth2 코드·설정·스키마 전면 제거

제외 (YAGNI):

- 이메일 인증 메일
- 비밀번호 재설정 / 찾기
- 소셜 로그인 복귀 대비 컬럼

메일 발송 인프라(SMTP)가 필요한 기능을 모두 뺐다. 커플 두 명이 쓰는 앱이라
비밀번호를 잊으면 재가입하거나 DB 에서 직접 바꾼다. 필요해지면 그때 별도 스펙으로 다룬다.

## 접근 방식

선택: **순수 REST + `PasswordEncoder` 직접 비교**

`AuthController` 에 `signup` / `login` 을 더하고, 서비스가 이메일로 사용자를 찾아
`matches` 로 검증한 뒤 기존 토큰 발급 경로를 호출한다. Spring Security 는
JWT 리소스 서버 역할만 남는다.

검토했으나 택하지 않은 대안:

- `formLogin`: 리다이렉트 기반이라 SPA 와 맞지 않는다.
- `AuthenticationManager` + `UserDetailsService`: 레이어만 늘고 얻는 것이 없다.

부수 효과로 시큐리티 필터체인이 2개에서 1개로 줄어 지금보다 단순해진다.

## 설계

### 스키마 — `V5__local_auth.sql`

```sql
TRUNCATE TABLE users CASCADE;          -- 로그인 불가능한 유령 행을 남기지 않는다

ALTER TABLE users DROP CONSTRAINT uq_users_provider_identity;
ALTER TABLE users DROP CONSTRAINT ck_users_provider;
ALTER TABLE users DROP COLUMN provider, DROP COLUMN provider_id;

ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);
ALTER TABLE users ADD COLUMN password_hash VARCHAR(60) NOT NULL;   -- bcrypt 고정 60자
```

소셜 로그인이 한 번도 동작한 적이 없어 실사용자 데이터는 존재하지 않는다.
기존 행은 `password_hash` 가 없어 어차피 로그인할 수 없으므로 남기지 않는다.

`profile_image_url` 은 유지한다. `Avatar` 가 null 이면 닉네임 첫 글자로 떨어지게
이미 되어 있어, 컬럼을 지우면 프론트 4곳과 DTO 3개를 건드려야 한다.
값이 항상 null 이 될 뿐 동작은 같다.

### API

| 엔드포인트 | 요청 | 응답 |
|---|---|---|
| `POST /api/v1/auth/signup` | `{email, password, nickname}` | 201 `AccessTokenResponse` |
| `POST /api/v1/auth/login` | `{email, password}` | 200 `AccessTokenResponse` |
| `POST /api/v1/auth/token` | 변경 없음 | |
| `POST /api/v1/auth/logout` | 변경 없음 | |

가입은 즉시 로그인 상태로 만든다. 두 엔드포인트 모두 기존 `/auth/token` 과 동일하게
`Set-Cookie` 로 리프레시 토큰을 심고 `AccessTokenResponse` 를 반환하므로,
프론트가 토큰을 다루는 방식은 지금과 같다.

네 엔드포인트(`signup`, `login`, `token`, `logout`) 모두 `SecurityConfig` 에서 `permitAll` 이다.

### 에러 코드

- `EMAIL_ALREADY_EXISTS` (409)
- `INVALID_CREDENTIALS` (401)

로그인 실패는 이메일이 없는 경우와 비밀번호가 틀린 경우를 구분하지 않는다.
가입 여부가 새어나가지 않게 하기 위해서다.

### 비밀번호

- 정책: 8자 이상 64자 이하
- 해싱: `BCryptPasswordEncoder` (기본 strength 10)

상한을 두는 이유는 bcrypt 가 72바이트를 넘는 입력을 조용히 잘라내기 때문이다.
`password_hash` 는 평문 길이와 무관하게 항상 60자다.

### 이메일 정규화

저장과 조회 모두 `trim().lowercase()` 를 거친다. 대소문자만 다른 이메일로 계정이
둘 생기면 사용자는 왜 로그인이 안 되는지 알 수 없다.

### 도메인

`User.register` 의 시그니처가 바뀐다.

```kotlin
fun register(email: String, passwordHash: String, nickname: String): User
```

`syncProfile` 은 소셜 프로필을 매 로그인마다 갱신하기 위한 것이므로 제거한다.

`LocalAuthService` 가 `UserRegistrationService` 를 대체한다. 책임은 둘뿐이다.

- `signup(email, rawPassword, nickname)` — 중복 검사, 해싱, 저장
- `authenticate(email, rawPassword)` — 조회, `matches`, 실패 시 `INVALID_CREDENTIALS`

토큰 발급은 `AuthController` 가 기존 협력자에게 그대로 위임한다.

### 삭제 대상

```
auth/oauth/                       6개 파일 전부
auth/config/OAuth2Properties.kt
user/domain/AuthProvider.kt
user/application/UserRegistrationService.kt
SecurityConfig 의 oauth2LoginFilterChain
build.gradle.kts: spring-boot-starter-oauth2-client
application.yml: spring.security.oauth2 / duri.oauth2 블록
.env.example, compose.deploy.yaml: KAKAO_*, GOOGLE_*, OAUTH2_*
ErrorCode: UNSUPPORTED_OAUTH_PROVIDER, OAUTH_PROFILE_UNAVAILABLE
```

`spring-boot-starter-oauth2-resource-server` 는 유지한다. JWT 검증이 여기 있다.

### 프론트엔드

- `LoginPage` — 소셜 버튼 두 개를 이메일/비밀번호 폼으로 교체. 로그인과 회원가입을 탭으로 전환
- `OAuthCallbackPage.tsx` 삭제, `App.tsx` 의 `/oauth/callback` 라우트 제거
- `authApi.signup` / `authApi.login` 추가
- 초대 흐름은 유지한다. `pendingInvite` 의 sessionStorage 왕복은 이제 SPA 내부 이동이
  되지만 구조를 바꾸지 않는 편이 단순하고, 주석의 보안 근거(남의 기기에서 초대를
  가로채지 못하게 한다)도 그대로 유효하다

## 데이터 흐름

```
회원가입   POST /auth/signup
           → LocalAuthService.signup: 중복 검사 → bcrypt 해싱 → users INSERT
           → RefreshTokenService.issue → Set-Cookie
           → AccessTokenIssuer.issue → AccessTokenResponse

로그인     POST /auth/login
           → LocalAuthService.authenticate: 조회 → matches
           → (이하 동일)

이후 요청  Authorization: Bearer <access token> → 리소스 서버가 검증
```

## 에러 처리

기존 `BusinessException` + `ErrorCode` 체계를 그대로 쓴다. 새 코드 두 개를 더할 뿐
전역 핸들러나 응답 형식은 건드리지 않는다.

## 테스트

- `LocalAuthService` 단위 테스트: 가입 성공, 이메일 중복, 로그인 성공,
  없는 이메일, 틀린 비밀번호
- `AuthApiTest`: signup → 쿠키 발급 확인, login → 액세스 토큰 발급 확인,
  잘못된 자격증명 401
- 기존 `CoupleFixture` 등 `User.register` 호출부 전면 수정
- OAuth 관련 기존 테스트 제거

## 구현 중 드러난 것

- `ErrorResponse.timestamp` 가 주입된 `Clock` 이 아니라 `Instant.now()` 를 쓴다.
  그래서 두 응답의 본문을 통째로 비교할 수 없다. 이번 범위에서는 손대지 않고,
  "가입 여부가 새어나가지 않는다" 테스트는 timestamp 를 뺀 나머지를 비교한다.
- `build/` 에 `* 2.class` 형태의 중복 산출물이 쌓여 테스트 실행이 깨지는 일이 있다.
  프로젝트가 iCloud 동기화되는 Desktop 아래 있어서다. `./gradlew clean` 으로 푼다.

## 미결 사항

없음.
