-- ENUM 없으면 먼저 생성
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_status') THEN
            CREATE TYPE user_status AS ENUM ('ACTIVE','SUSPENDED','DEACTIVATED');
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
            CREATE TYPE user_role AS ENUM ('USER','ADMIN','AUDITOR');
        END IF;
    END
$$;

CREATE TABLE IF NOT EXISTS users
(
    id                   BIGINT PRIMARY KEY,
    name                 TEXT        NOT NULL,
    email                TEXT UNIQUE NOT NULL,
    status               user_status NOT NULL DEFAULT 'ACTIVE',
    is_transfer_locked   BOOLEAN     NOT NULL DEFAULT false,
    daily_transfer_limit BIGINT      NOT NULL DEFAULT 1000000,
    role                 user_role   NOT NULL DEFAULT 'USER',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_users_status ON users (status);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- 코멘트
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

---

CREATE TABLE account_balances
(
    id         BIGINT PRIMARY KEY,   -- ID (Snowflake)
    user_id    BIGINT      NOT NULL, -- TODO : REFERENCES users (id)
    balance    BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
--     currency   currency_code  NOT NULL DEFAULT 'KRW',
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE account_balances IS '사용자의 계좌 잔액 정보';
COMMENT ON COLUMN account_balances.version IS '낙관적 락(Optimistic Lock) 버전 관리';
CREATE INDEX idx_account_balances_updated_at ON account_balances (updated_at);

-- updated_at 자동 갱신 트리거
CREATE OR REPLACE FUNCTION trg_ab_touch_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS ab_touch_updated_at ON account_balances;
CREATE TRIGGER ab_touch_updated_at
    BEFORE UPDATE
    ON account_balances
    FOR EACH ROW
EXECUTE FUNCTION trg_ab_touch_updated_at();

---
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'transaction_status') THEN
            CREATE TYPE transaction_status AS ENUM ('PENDING','COMPLETED','FAILED','CORRECTED');
        END IF;
        --         IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'currency_code') THEN
--             CREATE TYPE currency_code AS ENUM ('KRW','USD','EUR','JPY'); -- 통화
--         END IF;
    END
$$;

CREATE TABLE IF NOT EXISTS transactions
(
    id                BIGINT PRIMARY KEY,                             -- 트랜잭션 ID (Snowflake)
    sender_user_id    BIGINT             NOT NULL,                    -- FK: users(id)
    receiver_user_id  BIGINT             NOT NULL,                    -- FK: users(id)
    amount            NUMERIC(20, 8)     NOT NULL CHECK (amount > 0), -- scale=8
--     currency          currency_code      NOT NULL DEFAULT 'KRW',
    status            transaction_status NOT NULL DEFAULT 'PENDING',
    received_at       TIMESTAMPTZ        NOT NULL DEFAULT now(),      -- 수신/요청 시각
    status_updated_at TIMESTAMPTZ        NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ        NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ        NOT NULL DEFAULT now(),
    version           BIGINT             NOT NULL DEFAULT 0,

--     TODO: users 도메인 작업 이후, 주석 해제
--     CONSTRAINT fk_tx_sender FOREIGN KEY (sender_user_id) REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,
--     CONSTRAINT fk_tx_receiver FOREIGN KEY (receiver_user_id) REFERENCES users (id) ON UPDATE RESTRICT ON DELETE RESTRICT,


    CONSTRAINT ck_tx_sender_ne_receiver CHECK (sender_user_id <> receiver_user_id)
);

COMMENT ON TABLE transactions IS '송금 트랜잭션';
COMMENT ON COLUMN transactions.id IS '트랜잭션 ID';
COMMENT ON COLUMN transactions.sender_user_id IS '보낸 사용자 ID';
COMMENT ON COLUMN transactions.receiver_user_id IS '받는 사용자 ID';
COMMENT ON COLUMN transactions.amount IS '송금 금액';
COMMENT ON COLUMN transactions.status IS '상태: PENDING, COMPLETED, FAILED, CORRECTED';
-- COMMENT ON COLUMN transactions.currency IS '통화 코드 (예: KRW, USD)';
-- COMMENT ON COLUMN transactions.exchange_rate_id IS '참조 환율 ID';
COMMENT ON COLUMN transactions.received_at IS '수신/요청 시각';
COMMENT ON COLUMN transactions.created_at IS '생성 시각';
COMMENT ON COLUMN transactions.status_updated_at IS '상태 최종 갱신 시각';

-- 발신자 타임라인
CREATE INDEX IF NOT EXISTS idx_tx_sender_created ON transactions (sender_user_id, created_at DESC);

-- 수신자 타임라인
CREATE INDEX IF NOT EXISTS idx_tx_receiver_created ON transactions (receiver_user_id, created_at DESC);

-- 상태 기반 조회/모니터링(운영)
CREATE INDEX IF NOT EXISTS idx_tx_status_updated ON transactions (status, status_updated_at DESC);


-- 상태 변경 시 status_updated_at 자동 업데이트 트리거
CREATE OR REPLACE FUNCTION trg_tx_touch_status_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.status_updated_at := now();
    END IF;
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS tx_touch_status_updated_at ON transactions;
CREATE TRIGGER tx_touch_status_updated_at
    BEFORE UPDATE
    ON transactions
    FOR EACH ROW
EXECUTE FUNCTION trg_tx_touch_status_updated_at();

---

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'transaction_history_status') THEN
            CREATE TYPE transaction_history_status AS ENUM ('SUCCESS','FAIL');
        END IF;
    END
$$;

CREATE TABLE transaction_histories
(
    id             BIGINT PRIMARY KEY,                  -- 이력 자체 PK
    transaction_id BIGINT                     NOT NULL, -- TODO: 논리적 FK 로 할지 고민필요. REFERENCES transactions (id),
    status         transaction_history_status NOT NULL,
    created_at     TIMESTAMPTZ                NOT NULL DEFAULT now()
);

COMMENT ON TABLE transaction_histories IS '트랜잭션 상태 변경 이력';
COMMENT ON COLUMN transaction_histories.status IS '최종 송금 상태';
COMMENT ON COLUMN transaction_histories.transaction_id IS '참조 트랜잭션 ID';
COMMENT ON COLUMN transaction_histories.created_at IS '생성 시각';

-- 인덱스
CREATE INDEX idx_tx_histories_txid_created
    ON transaction_histories (transaction_id, created_at);

---

-- ENUM 타입 정의
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'transfer_outbox_status') THEN
            CREATE TYPE transfer_outbox_status AS ENUM ('PENDING','SENDING','PUBLISHED','FAILED');
        END IF;
    END
$$;

-- Outbox 테이블
CREATE TABLE IF NOT EXISTS transfer_events
(
    event_id       BIGINT PRIMARY KEY,                            -- Snowflake or UUID (멱등키)
    event_version  INT                    NOT NULL DEFAULT 1,
    aggregate_type VARCHAR(100)           NOT NULL,
    aggregate_id   VARCHAR(100)           NOT NULL,
    event_type     VARCHAR(100)           NOT NULL,
    payload        JSONB                  NOT NULL,
    headers        JSONB                  NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ(6)         NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ(6)         NOT NULL DEFAULT now(), -- 상태 바뀔 때마다 갱신
    published_at   TIMESTAMPTZ(6),
    status         transfer_outbox_status NOT NULL DEFAULT 'PENDING',
    attempt_count  INT                    NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMPTZ(6)         NOT NULL DEFAULT now(),
    last_error     TEXT,
    CONSTRAINT ck_transfer_events_payload_object CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT ck_transfer_events_headers_object CHECK (jsonb_typeof(headers) = 'object'),
    CONSTRAINT ck_transfer_events_nonempty CHECK (
        char_length(btrim(aggregate_type)) > 0 AND
        char_length(btrim(aggregate_id)) > 0 AND
        char_length(btrim(event_type)) > 0
        ),
    CONSTRAINT ck_published_requires_timestamp CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL
        )
);

COMMENT ON TABLE transfer_events IS 'Outbox: DB 커밋과 함께 기록되는 발행 보장 버퍼';
COMMENT ON COLUMN transfer_events.event_id IS 'Snowflake(Long) 이벤트 고유 ID (idempotency/재생 기준)';
COMMENT ON COLUMN transfer_events.event_version IS '이벤트 스키마 버전';
COMMENT ON COLUMN transfer_events.aggregate_type IS '애그리거트 종류 (예: Transfer)';
COMMENT ON COLUMN transfer_events.aggregate_id IS '애그리거트 식별자';
COMMENT ON COLUMN transfer_events.event_type IS '이벤트 타입명 (예: TransferCompleted)';
COMMENT ON COLUMN transfer_events.payload IS '이벤트 페이로드(JSONB)';
COMMENT ON COLUMN transfer_events.headers IS '추적/전파 헤더(traceId, correlationId 등)';
COMMENT ON COLUMN transfer_events.created_at IS 'Outbox 레코드 생성(커밋) 시각';
COMMENT ON COLUMN transfer_events.updated_at IS '최근 상태 전이 시각';
COMMENT ON COLUMN transfer_events.published_at IS '브로커 발행 성공 시각(null=미발행)';
COMMENT ON COLUMN transfer_events.status IS '상태(PENDING|SENDING|PUBLISHED|FAILED)';
COMMENT ON COLUMN transfer_events.attempt_count IS '발행 재시도 누적 횟수';
COMMENT ON COLUMN transfer_events.next_retry_at IS '재시도 가능 시각(백오프)';
COMMENT ON COLUMN transfer_events.last_error IS '최근 실패 에러 메시지 요약';

-- 인덱스
-- PENDING 픽업 (backoff 고려)
CREATE INDEX IF NOT EXISTS ix_transfer_events_pick_pending
    ON transfer_events (next_retry_at, created_at)
    WHERE status = 'PENDING';

-- FAILED 재시도
CREATE INDEX IF NOT EXISTS ix_transfer_events_retry_failed
    ON transfer_events (next_retry_at, created_at)
    WHERE status = 'FAILED';

-- Aggregate 조회 최적화
CREATE INDEX IF NOT EXISTS ix_transfer_events_aggregate
    ON transfer_events (aggregate_type, aggregate_id, created_at);

-- SENDING stuck 복구용
CREATE INDEX IF NOT EXISTS ix_transfer_events_recover_sending
    ON transfer_events (updated_at)
    WHERE status = 'SENDING';
