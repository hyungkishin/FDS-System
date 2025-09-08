-- PostgreSQL DDL generated from FDS ERD
SET client_encoding = 'UTF8';

-- ENUM 타입 정의
CREATE TYPE user_status AS ENUM ('ACTIVE', 'SUSPENDED', 'DEACTIVATED'); -- 유저 상태
CREATE TYPE user_role AS ENUM ('USER', 'ADMIN', 'AUDITOR'); -- 유저 권한
CREATE TYPE currency_code AS ENUM ('KRW', 'USD', 'JPY', 'BTC', 'ETH'); -- 통화 코드
CREATE TYPE transaction_status AS ENUM ('PENDING', 'COMPLETED', 'FAILED', 'CORRECTED'); -- 트랜잭션 상태
CREATE TYPE final_decision_type AS ENUM ('PASS', 'REVIEW', 'BLOCK'); -- 이상 탐지 최종 판정 결과: PASS(정상), REVIEW(검토), BLOCK(차단)
CREATE TYPE changer_role_type AS ENUM ('SYSTEM', 'ADMIN', 'USER'); -- 송금 주체 변경 유형: SYSTEM(시스템 자동 처리), ADMIN(관리자 조작), USER(사용자 요청)
CREATE TYPE transfer_outbox_status AS ENUM ('PENDING','SENDING','PUBLISHED','FAILED');
CREATE TYPE transaction_history_status AS ENUM ('SUCCESS','FAILED');


-- ============================================
-- USER DOMAIN
-- ============================================

-- 사용자 테이블
CREATE TABLE users (
                       id BIGINT PRIMARY KEY,
                       name TEXT NOT NULL,
                       email TEXT UNIQUE NOT NULL,
                       status user_status NOT NULL DEFAULT 'ACTIVE',
                       is_transfer_locked BOOLEAN NOT NULL DEFAULT false,
                       daily_transfer_limit BIGINT NOT NULL DEFAULT 1000000,
                       role user_role NOT NULL DEFAULT 'USER',
                       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ NULL
);

-- 인덱스
CREATE INDEX idx_users_email_created_at ON users(email, created_at);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_role ON users(role);

COMMENT ON TABLE users IS '송금 시스템 사용자 정보';
COMMENT ON COLUMN users.id IS '사용자 고유 ID (Snowflake)';
COMMENT ON COLUMN users.name IS '사용자 이름';
COMMENT ON COLUMN users.email IS '사용자 이메일 (UNIQUE)';
COMMENT ON COLUMN users.status IS '계정 상태: ACTIVE, SUSPENDED, DEACTIVATED';
COMMENT ON COLUMN users.is_transfer_locked IS '송금 잠금 여부 (이상탐지, 관리자 제재 등)';
COMMENT ON COLUMN users.daily_transfer_limit IS '1일 최대 송금 가능 금액';
COMMENT ON COLUMN users.role IS '사용자 역할: USER, ADMIN, AUDITOR';
COMMENT ON COLUMN users.created_at IS '계정 생성 일시';
COMMENT ON COLUMN users.updated_at IS '마지막 정보 갱신 일시';


CREATE TABLE account_balances (
                                  id         BIGINT PRIMARY KEY, -- ID (Snowflake)
                                  user_id      BIGINT PRIMARY KEY REFERENCES users(id),
                                  balance      NUMERIC(20, 8) NOT NULL DEFAULT 0
                                      CHECK (balance >= 0),             -- 음수 잔액 방지
                                  currency     currency_code NOT NULL DEFAULT 'KRW',
                                  version      BIGINT NOT NULL DEFAULT 0,        -- 낙관적 락 버전
                                  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                                  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE account_balances IS '사용자의 계좌 잔액 정보';
COMMENT ON COLUMN account_balances.version IS '낙관적 락(Optimistic Lock) 버전 관리';

-- 인덱스
CREATE UNIQUE INDEX account_balances_user_version
    ON account_balances(user_id, version);  -- 동시성 처리 최적화
CREATE INDEX idx_account_balances_updated_at ON account_balances(updated_at);
CREATE INDEX idx_account_balances_currency   ON account_balances(currency);

-- Phase-3
CREATE TABLE wallet_addresses (
                                  id BIGINT PRIMARY KEY,
                                  user_id BIGINT NOT NULL,
                                  address TEXT UNIQUE NOT NULL,
                                  chain_code TEXT NOT NULL,
                                  registered_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

COMMENT ON TABLE wallet_addresses IS '사용자 지갑 주소';
COMMENT ON COLUMN wallet_addresses.id IS '지갑 주소 ID (Snowflake)';
COMMENT ON COLUMN wallet_addresses.user_id IS '사용자 ID';
COMMENT ON COLUMN wallet_addresses.address IS '온체인 지갑 주소 (UNIQUE)';
COMMENT ON COLUMN wallet_addresses.chain_code IS '체인 코드 (예: ETH)';
COMMENT ON COLUMN wallet_addresses.registered_at IS '등록 시각';

CREATE INDEX idx_wallet_addresses_user_chain ON wallet_addresses(user_id, chain_code);

-- ============================================
-- ADMIN DOMAIN
-- ============================================

CREATE TABLE admin_users (
                             id BIGINT PRIMARY KEY,
                             username TEXT UNIQUE NOT NULL,
                             email TEXT UNIQUE NOT NULL,
                             role TEXT NOT NULL CHECK (role IN ('ADMIN', 'AUDITOR')),
                             created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

COMMENT ON TABLE admin_users IS '관리자 계정';
COMMENT ON COLUMN admin_users.id IS '관리자 ID';
COMMENT ON COLUMN admin_users.username IS '관리자 계정명';
COMMENT ON COLUMN admin_users.email IS '이메일';
COMMENT ON COLUMN admin_users.role IS '관리자 권한 (ADMIN / AUDITOR)';
COMMENT ON COLUMN admin_users.created_at IS '생성 시각';

CREATE UNIQUE INDEX unq_admin_users_email ON admin_users(email);
CREATE INDEX idx_admin_user_id_role ON admin_users(id, role);
CREATE INDEX idx_admin_user_created_at ON admin_users(created_at);

-- ============================================
-- TRANSACTION DOMAIN
-- ============================================

CREATE TABLE transactions (
                              id BIGINT PRIMARY KEY,
                              sender_user_id BIGINT NOT NULL REFERENCES users(id),
                              receiver_user_id BIGINT NOT NULL REFERENCES users(id),
                              amount NUMERIC(20, 8) NOT NULL CHECK (amount > 0),
                              currency currency_code NOT NULL DEFAULT 'KRW',
                              status transaction_status NOT NULL,
--                               exchange_rate_id BIGINT,
                              received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                              created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                              status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE transactions IS '송금 트랜잭션';
COMMENT ON COLUMN transactions.id IS '트랜잭션 ID';
COMMENT ON COLUMN transactions.sender_user_id IS '보낸 사용자 ID';
COMMENT ON COLUMN transactions.receiver_user_id IS '받는 사용자 ID';
COMMENT ON COLUMN transactions.amount IS '송금 금액';
COMMENT ON COLUMN transactions.currency IS '송금 트랜잭션 통화 코드 (예: KRW, USD)';
COMMENT ON COLUMN transactions.status IS '트랜잭션 상태: PENDING, COMPLETED, FAILED, CORRECTED';
-- COMMENT ON COLUMN transactions.exchange_rate_id IS '참조 환율 ID';
COMMENT ON COLUMN transactions.received_at IS '수신 확인 시각';
COMMENT ON COLUMN transactions.created_at IS '트랜잭션 생성 시각';
COMMENT ON COLUMN transactions.status_updated_at IS '트랜잭션 상태가 마지막으로 변경된 시각';

CREATE INDEX idx_transactions_sender_created ON transactions(sender_user_id, created_at);
CREATE INDEX idx_transactions_receiver_created ON transactions(receiver_user_id, created_at);

CREATE TABLE transaction_histories (
                                       id            BIGSERIAL PRIMARY KEY,             -- 이력 자체 PK
                                       transaction_id BIGINT NOT NULL REFERENCES transactions(id),
                                       status        transaction_status NOT NULL,
                                       created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE transaction_histories IS '트랜잭션 상태 변경 이력';
COMMENT ON COLUMN transaction_histories.status IS '최종 송금 상태';
COMMENT ON COLUMN transaction_histories.transaction_id IS '참조 트랜잭션 ID';
COMMENT ON COLUMN transaction_histories.created_at IS '생성 시각';

-- 인덱스
CREATE INDEX idx_tx_histories_txid_created
    ON transaction_histories(transaction_id, created_at);

-- =============================================
-- transfer_events & Idempotency tables (Snowflake Long)
-- =============================================
CREATE TABLE IF NOT EXISTS transfer_events (
                                               id               BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,  -- 내부 PK (로컬 식별)
                                               event_id         BIGINT       NOT NULL,                                -- Snowflake(Long) - 멱등 키
                                               event_version    INT          NOT NULL DEFAULT 1,                      -- 이벤트 스키마 버전
                                               aggregate_type   VARCHAR(100) NOT NULL,                                -- 예: 'Transfer'
                                               aggregate_id     VARCHAR(100) NOT NULL,                                -- 예: '192834...'
                                               event_type       VARCHAR(100) NOT NULL,                                -- 예: 'TransferCompleted'
                                               payload          JSONB        NOT NULL,                                -- 직렬화된 이벤트 본문
                                               headers          JSONB        NOT NULL DEFAULT '{}'::jsonb,            -- traceId 등
                                               created_at       TIMESTAMPTZ(6) NOT NULL DEFAULT now(),
                                               published_at     TIMESTAMPTZ(6),
                                               status           transfer_outbox_status NOT NULL DEFAULT 'PENDING',
                                               attempt_count    INT           NOT NULL DEFAULT 0,
                                               next_retry_at    TIMESTAMPTZ(6),                                       -- ← 백오프 기준 시각
                                               last_error       TEXT,

                                               CONSTRAINT ck_transfer_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
                                               CONSTRAINT ck_transfer_events_headers_object CHECK (jsonb_typeof(headers) = 'object'),
                                               CONSTRAINT ck_transfer_events_nonempty CHECK (
                                                   length(btrim(aggregate_type)) > 0 AND
                                                   length(btrim(aggregate_id))   > 0 AND
                                                   length(btrim(event_type))     > 0
                                                   ),
                                               CONSTRAINT uq_outbox_event_id UNIQUE (event_id),

    -- 상태와 칼럼의 일관성을 위해, PUBLISHED 상태 면 published_at 이 있어야 한다.
                                               CONSTRAINT ck_published_requires_timestamp CHECK (
                                                   status <> 'PUBLISHED' OR published_at IS NOT NULL
                                                   )
);

COMMENT ON TABLE  transfer_events IS 'Outbox(transfer_events): DB 커밋과 함께 기록되는 발행 보장 버퍼';
COMMENT ON COLUMN transfer_events.event_id       IS 'Snowflake(Long) 이벤트 고유 ID (idempotency/재생 기준)';
COMMENT ON COLUMN transfer_events.event_version  IS '이벤트 스키마 버전';
COMMENT ON COLUMN transfer_events.aggregate_type IS '애그리거트 종류 (예: Transfer)';
COMMENT ON COLUMN transfer_events.aggregate_id   IS '애그리거트 식별자';
COMMENT ON COLUMN transfer_events.event_type     IS '이벤트 타입명 (예: TransferCompleted)';
COMMENT ON COLUMN transfer_events.payload        IS '이벤트 페이로드(JSONB)';
COMMENT ON COLUMN transfer_events.headers        IS '추적/전파 헤더(traceId, correlationId 등)';
COMMENT ON COLUMN transfer_events.created_at     IS 'Outbox 레코드 생성(커밋) 시각';
COMMENT ON COLUMN transfer_events.published_at   IS '브로커 발행 성공 시각(null=미발행)';
COMMENT ON COLUMN transfer_events.status         IS '상태(PENDING|SENDING|PUBLISHED|FAILED)';
COMMENT ON COLUMN transfer_events.attempt_count  IS '발행 재시도 누적 횟수';
COMMENT ON COLUMN transfer_events.next_retry_at  IS 'FAILED 일 경우, 재시도 가능 시각(백오프)';
COMMENT ON COLUMN transfer_events.last_error     IS '최근 실패 에러 메시지 요약';

-- 인덱스
-- 워커 픽업: PENDING 만 빠르게 스캔하기 위함 (created_at 순)
CREATE INDEX IF NOT EXISTS ix_transfer_events_pick_pending
    ON transfer_events (created_at)
    WHERE status = 'PENDING';

-- 재시도 픽업: FAILED + next_retry_at 도래한 것 빠르게 확인하기 위함
CREATE INDEX IF NOT EXISTS ix_transfer_events_retry_failed
    ON transfer_events (next_retry_at, created_at)
    WHERE status = 'FAILED';

-- 조회/리포트: 애그리거트별 타임라인
CREATE INDEX IF NOT EXISTS ix_transfer_events_aggregate
    ON transfer_events (aggregate_type, aggregate_id, created_at);

--  FDS 스키마

-- 확장 모듈 활성화 (JSON 기능 강화)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- 1. 룰 카테고리 테이블
CREATE TABLE fraud_rule_categories (
                                       id BIGSERIAL PRIMARY KEY,
                                       name VARCHAR(50) NOT NULL UNIQUE,
                                       description TEXT,
                                       is_active BOOLEAN DEFAULT true,
                                       created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                                       updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. 메인 룰 테이블 (JSONB 활용)
CREATE TYPE rule_type_enum AS ENUM ('BLACKLIST', 'WHITELIST', 'THRESHOLD', 'PATTERN', 'TIME_BASED', 'VELOCITY', 'AMOUNT_LIMIT');
CREATE TYPE action_type_enum AS ENUM ('BLOCK', 'ALERT', 'LOG_ONLY', 'MANUAL_REVIEW');

CREATE TABLE fraud_rules (
                             id BIGSERIAL PRIMARY KEY,
                             category_id BIGINT NOT NULL REFERENCES fraud_rule_categories(id),
                             rule_name VARCHAR(100) NOT NULL,
                             rule_type rule_type_enum NOT NULL,
                             priority INTEGER DEFAULT 0,
                             is_active BOOLEAN DEFAULT true,
                             risk_score DECIMAL(3,2) DEFAULT 0.00 CHECK (risk_score >= 0.00 AND risk_score <= 1.00),
                             action_type action_type_enum DEFAULT 'LOG_ONLY',

    -- JSONB로 유연한 룰 조건 저장
                             conditions JSONB NOT NULL,

    -- 메타데이터
                             description TEXT,
                             created_by VARCHAR(100),
                             updated_by VARCHAR(100),
                             created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- JSONB 인덱스 (조건 검색 최적화)
CREATE INDEX idx_fraud_rules_conditions_gin ON fraud_rules USING GIN (conditions);
CREATE INDEX idx_fraud_rules_category_priority ON fraud_rules (category_id, priority DESC);
CREATE INDEX idx_fraud_rules_type_active ON fraud_rules (rule_type, is_active);
CREATE INDEX idx_fraud_rules_updated_at ON fraud_rules (updated_at);

-- 3. 블랙리스트 테이블
CREATE TYPE list_type_enum AS ENUM ('USER', 'ACCOUNT', 'DEVICE', 'IP', 'PHONE', 'EMAIL');
CREATE TYPE severity_enum AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');

CREATE TABLE fraud_blacklists (
                                  id BIGSERIAL PRIMARY KEY,
                                  list_type list_type_enum NOT NULL,
                                  value VARCHAR(255) NOT NULL,
                                  reason TEXT,
                                  severity severity_enum DEFAULT 'MEDIUM',
                                  expires_at TIMESTAMPTZ NULL,
                                  is_active BOOLEAN DEFAULT true,
                                  metadata JSONB DEFAULT '{}', -- 추가 정보 (IP 위치, 디바이스 정보 등)
                                  created_by VARCHAR(100),
                                  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT uk_blacklist_type_value UNIQUE (list_type, value)
);

CREATE INDEX idx_blacklist_type_active ON fraud_blacklists (list_type, is_active);
CREATE INDEX idx_blacklist_expires_at ON fraud_blacklists (expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_blacklist_value_hash ON fraud_blacklists USING HASH (value); -- 빠른 정확 매치

-- 4. 화이트리스트 테이블
CREATE TABLE fraud_whitelists (
                                  id BIGSERIAL PRIMARY KEY,
                                  list_type list_type_enum NOT NULL,
                                  value VARCHAR(255) NOT NULL,
                                  reason TEXT,
                                  expires_at TIMESTAMPTZ NULL,
                                  is_active BOOLEAN DEFAULT true,
                                  metadata JSONB DEFAULT '{}',
                                  created_by VARCHAR(100),
                                  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

                                  CONSTRAINT uk_whitelist_type_value UNIQUE (list_type, value)
);

CREATE INDEX idx_whitelist_type_active ON fraud_whitelists (list_type, is_active);
CREATE INDEX idx_whitelist_value_hash ON fraud_whitelists USING HASH (value);

-- 5. 임계값 룰 테이블
CREATE TYPE threshold_type_enum AS ENUM ('DAILY_AMOUNT', 'MONTHLY_AMOUNT', 'TRANSACTION_COUNT', 'VELOCITY', 'HOURLY_AMOUNT');
CREATE TYPE user_type_enum AS ENUM ('ALL', 'NEW_USER', 'VERIFIED_USER', 'VIP_USER', 'SUSPICIOUS_USER');

CREATE TABLE fraud_thresholds (
                                  id BIGSERIAL PRIMARY KEY,
                                  threshold_type threshold_type_enum NOT NULL,
                                  user_type user_type_enum DEFAULT 'ALL',
                                  threshold_value DECIMAL(15,2) NOT NULL,
                                  time_window_minutes INTEGER,
                                  risk_score DECIMAL(3,2) DEFAULT 0.50,
                                  action_type action_type_enum DEFAULT 'ALERT',
                                  conditions JSONB DEFAULT '{}', -- 추가 조건 (시간대, 지역 등)
                                  is_active BOOLEAN DEFAULT true,
                                  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_thresholds_type_active ON fraud_thresholds (threshold_type, is_active);

-- 6. 룰 실행 히스토리 (파티셔닝 적용)
CREATE TYPE rule_result_enum AS ENUM ('PASS', 'FAIL', 'ERROR');
CREATE TYPE action_taken_enum AS ENUM ('NONE', 'BLOCKED', 'ALERTED', 'LOGGED', 'MANUAL_REVIEW');

CREATE TABLE fraud_rule_executions (
                                       id BIGSERIAL,
                                       rule_id BIGINT NOT NULL REFERENCES fraud_rules(id),
                                       transaction_id BIGINT,
                                       user_id BIGINT,
                                       rule_result rule_result_enum NOT NULL,
                                       execution_time_ms INTEGER,
                                       risk_score_calculated DECIMAL(3,2),
                                       action_taken action_taken_enum NOT NULL,
                                       error_message TEXT,
                                       execution_context JSONB DEFAULT '{}', -- 실행 시점의 컨텍스트 정보
                                       executed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

                                       PRIMARY KEY (id, executed_at)
) PARTITION BY RANGE (executed_at);

-- 월별 파티션 생성 (PostgreSQL 10+)
CREATE TABLE fraud_rule_executions_202501 PARTITION OF fraud_rule_executions
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE fraud_rule_executions_202502 PARTITION OF fraud_rule_executions
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE fraud_rule_executions_202503 PARTITION OF fraud_rule_executions
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');

-- 파티션별 인덱스
CREATE INDEX idx_executions_202501_rule_executed ON fraud_rule_executions_202501 (rule_id, executed_at);
CREATE INDEX idx_executions_202501_transaction ON fraud_rule_executions_202501 (transaction_id);

-- 7. 룰 변경 히스토리 (Audit)
CREATE TYPE audit_action_enum AS ENUM ('CREATE', 'UPDATE', 'DELETE', 'ACTIVATE', 'DEACTIVATE');

CREATE TABLE fraud_rule_audit_logs (
                                       id BIGSERIAL PRIMARY KEY,
                                       rule_id BIGINT NOT NULL REFERENCES fraud_rules(id),
                                       action_type audit_action_enum NOT NULL,
                                       old_values JSONB,
                                       new_values JSONB,
                                       changed_by VARCHAR(100),
                                       change_reason TEXT,
                                       changed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_rule_changed_at ON fraud_rule_audit_logs (rule_id, changed_at);

-- 8. 사용자 행동 패턴 캐시 테이블 (PostgreSQL 특화)
CREATE TABLE user_behavior_cache (
                                     user_id BIGINT PRIMARY KEY,
                                     behavior_data JSONB NOT NULL,
                                     risk_score DECIMAL(3,2),
                                     last_transaction_at TIMESTAMPTZ,
                                     daily_amount DECIMAL(15,2) DEFAULT 0,
                                     daily_count INTEGER DEFAULT 0,
                                     updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_behavior_cache_updated ON user_behavior_cache (updated_at);
CREATE INDEX idx_behavior_cache_risk ON user_behavior_cache (risk_score DESC);

-- 9. 실시간 거래 속도 추적 테이블
CREATE TABLE user_velocity_tracking (
                                        user_id BIGINT,
                                        transaction_id BIGINT,
                                        amount DECIMAL(15,2),
                                        timestamp_ms BIGINT, -- Unix timestamp in milliseconds
                                        created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,

                                        PRIMARY KEY (user_id, timestamp_ms)
);

-- 시계열 데이터를 위한 BRIN 인덱스 (PostgreSQL 특화)
CREATE INDEX idx_velocity_timestamp_brin ON user_velocity_tracking USING BRIN (timestamp_ms);

-- 10. 초기 데이터 입력
INSERT INTO fraud_rule_categories (name, description) VALUES
                                                          ('기본보안', '기본적인 보안 룰 (블랙리스트, 화이트리스트)'),
                                                          ('거래한도', '거래 금액 및 빈도 관련 룰'),
                                                          ('행동패턴', '사용자 행동 패턴 기반 룰'),
                                                          ('시간기반', '시간대별 제한 룰'),
                                                          ('디바이스', '디바이스 및 위치 기반 룰'),
                                                          ('속도제한', '거래 속도 기반 룰');

-- 기본 임계값 설정
INSERT INTO fraud_thresholds (threshold_type, user_type, threshold_value, action_type, conditions) VALUES
                                                                                                       ('DAILY_AMOUNT', 'ALL', 5000000.00, 'ALERT', '{"currency": "KRW"}'),
                                                                                                       ('DAILY_AMOUNT', 'NEW_USER', 1000000.00, 'BLOCK', '{"currency": "KRW", "account_age_days": 30}'),
                                                                                                       ('TRANSACTION_COUNT', 'ALL', 100, 'ALERT', '{"period": "daily"}'),
                                                                                                       ('VELOCITY', 'ALL', 10, 'ALERT', '{"window_minutes": 10}'),
                                                                                                       ('HOURLY_AMOUNT', 'ALL', 2000000.00, 'ALERT', '{"currency": "KRW"}');

-- 기본 룰 예시 (JSONB 활용)
INSERT INTO fraud_rules (category_id, rule_name, rule_type, priority, risk_score, action_type, conditions, description) VALUES
                                                                                                                            (1, '블랙리스트 사용자 차단', 'BLACKLIST', 100, 1.00, 'BLOCK',
                                                                                                                             '{"check_type": "user_blacklist", "lists": ["USER"], "strict_mode": true}',
                                                                                                                             '블랙리스트에 등록된 사용자의 모든 거래 차단'),

                                                                                                                            (2, '고액 거래 알림', 'THRESHOLD', 80, 0.70, 'ALERT',
                                                                                                                             '{"amount_threshold": 1000000, "currency": "KRW", "check_daily_limit": true}',
                                                                                                                             '100만원 이상 거래 시 알림'),

                                                                                                                            (4, '심야 시간 거래 제한', 'TIME_BASED', 60, 0.50, 'ALERT',
                                                                                                                             '{"time_start": "23:00", "time_end": "06:00", "timezone": "Asia/Seoul", "weekend_only": false}',
                                                                                                                             '심야 시간 (23:00-06:00) 거래 모니터링'),

                                                                                                                            (6, '연속 거래 속도 제한', 'VELOCITY', 70, 0.60, 'ALERT',
                                                                                                                             '{"max_count": 5, "window_minutes": 10, "min_interval_seconds": 30}',
                                                                                                                             '10분간 5건 이상 또는 30초 이내 연속 거래 시 알림');

-- PostgreSQL 특화 기능들

-- 1. 자동 업데이트 트리거 함수
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 2. 업데이트 트리거 적용
CREATE TRIGGER update_fraud_rules_updated_at BEFORE UPDATE ON fraud_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_fraud_blacklists_updated_at BEFORE UPDATE ON fraud_blacklists
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_fraud_whitelists_updated_at BEFORE UPDATE ON fraud_whitelists
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 3. 룰 조건 검증 함수 (PostgreSQL JSON 함수 활용)
CREATE OR REPLACE FUNCTION validate_rule_conditions(rule_type rule_type_enum, conditions JSONB)
    RETURNS BOOLEAN AS $$
BEGIN
    CASE rule_type
        WHEN 'THRESHOLD' THEN
            RETURN (conditions ? 'amount_threshold') OR (conditions ? 'count_threshold');
        WHEN 'TIME_BASED' THEN
            RETURN (conditions ? 'time_start') AND (conditions ? 'time_end');
        WHEN 'VELOCITY' THEN
            RETURN (conditions ? 'max_count') AND (conditions ? 'window_minutes');
        ELSE
            RETURN true;
        END CASE;
END;
$$ LANGUAGE plpgsql;

-- 4. 룰 조건 검증 제약조건 추가
ALTER TABLE fraud_rules ADD CONSTRAINT check_rule_conditions
    CHECK (validate_rule_conditions(rule_type, conditions));

-- 5. 만료된 블랙리스트 자동 정리 함수
CREATE OR REPLACE FUNCTION cleanup_expired_blacklists()
    RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    UPDATE fraud_blacklists
    SET is_active = false
    WHERE expires_at IS NOT NULL
      AND expires_at < CURRENT_TIMESTAMP
      AND is_active = true;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- 6. 주기적 정리 작업을 위한 pg_cron 설정 (확장 설치 필요)
-- SELECT cron.schedule('cleanup-expired-blacklists', '0 */6 * * *', 'SELECT cleanup_expired_blacklists();');

-- 7. 성능 모니터링을 위한 뷰
CREATE VIEW fraud_system_stats AS
SELECT
    'active_rules' as metric,
    COUNT(*)::text as value,
    CURRENT_TIMESTAMP as collected_at
FROM fraud_rules WHERE is_active = true
UNION ALL
SELECT
    'active_blacklists' as metric,
    COUNT(*)::text as value,
    CURRENT_TIMESTAMP as collected_at
FROM fraud_blacklists WHERE is_active = true
UNION ALL
SELECT
    'executions_today' as metric,
    COUNT(*)::text as value,
    CURRENT_TIMESTAMP as collected_at
FROM fraud_rule_executions
WHERE executed_at >= CURRENT_DATE;

-- 8. 실시간 통계를 위한 Materialized View
CREATE MATERIALIZED VIEW fraud_daily_stats AS
SELECT
    DATE(executed_at) as date,
    rule_result,
    action_taken,
    COUNT(*) as count,
    AVG(execution_time_ms) as avg_execution_time,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY execution_time_ms) as p95_execution_time
FROM fraud_rule_executions
WHERE executed_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE(executed_at), rule_result, action_taken
ORDER BY date DESC;

-- Materialized View 자동 갱신 (pg_cron 사용)
-- SELECT cron.schedule('refresh-fraud-stats', '*/15 * * * *', 'REFRESH MATERIALIZED VIEW fraud_daily_stats;');

-- 9. 파티션 자동 생성 함수
CREATE OR REPLACE FUNCTION create_monthly_partition(target_date DATE)
    RETURNS TEXT AS $$
DECLARE
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    start_date := DATE_TRUNC('month', target_date);
    end_date := start_date + INTERVAL '1 month';
    partition_name := 'fraud_rule_executions_' || TO_CHAR(start_date, 'YYYYMM');

    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF fraud_rule_executions
                   FOR VALUES FROM (%L) TO (%L)',
                   partition_name, start_date, end_date);

    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_rule_executed
                   ON %I (rule_id, executed_at)',
                   partition_name, partition_name);

    RETURN partition_name;
END;
$$ LANGUAGE plpgsql;

-- 향후 3개월 파티션 미리 생성
SELECT create_monthly_partition(CURRENT_DATE + (n || ' months')::INTERVAL)
FROM generate_series(1, 3) n;