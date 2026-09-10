-- ---------------------------------------------------------------------
-- 소셜 로그인(OAuth2) 을 걷어내고 자체 이메일/비밀번호 로그인으로 전환한다.
--
-- 소셜 로그인은 한 번도 동작한 적이 없어 실사용자 데이터가 없다.
-- 남아 있는 행은 password_hash 가 없어 어차피 로그인할 수 없으므로 남기지 않는다.
--
-- 주의: CASCADE 는 users 만 비우지 않는다. FK 로 물린 accounts, couples,
-- couple_invites, expenses, settlements, recurring_expenses, notifications,
-- refresh_tokens 까지 전부 비운다. 되돌릴 수 없다.
-- 아래 password_hash 를 DEFAULT 없이 NOT NULL 로 붙일 수 있는 것도 이 TRUNCATE 덕이다.
-- 실데이터가 있는 DB 에는 이 마이그레이션을 그대로 적용하면 안 된다.
-- ---------------------------------------------------------------------

TRUNCATE TABLE users CASCADE;

ALTER TABLE users
    DROP CONSTRAINT uq_users_provider_identity,
    DROP CONSTRAINT ck_users_provider,
    DROP COLUMN provider,
    DROP COLUMN provider_id;

ALTER TABLE users
    ALTER COLUMN email SET NOT NULL,
    -- bcrypt 해시는 평문 길이와 무관하게 항상 60자다.
    ADD COLUMN password_hash VARCHAR(60) NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uq_users_email UNIQUE (email);

COMMENT ON TABLE users IS '자체 로그인 사용자';
COMMENT ON COLUMN users.email IS '로그인 아이디. 소문자로 정규화해 저장한다.';
COMMENT ON COLUMN users.password_hash IS 'bcrypt 해시';
