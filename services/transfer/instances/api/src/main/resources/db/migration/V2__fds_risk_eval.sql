-- PostgreSQL 최적화 FDS 스키마
-- 확장 모듈 활성화 (JSON 기능 강화)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- 룰 카테고리 테이블
CREATE TABLE fraud_rule_categories
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    is_active   BOOLEAN     DEFAULT true,
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 2. 메인 룰 테이블 (JSONB 활용)
CREATE TYPE rule_type_enum AS ENUM ('BLACKLIST', 'WHITELIST', 'THRESHOLD', 'PATTERN', 'TIME_BASED', 'VELOCITY', 'AMOUNT_LIMIT');
CREATE TYPE action_type_enum AS ENUM ('BLOCK', 'ALERT', 'LOG_ONLY', 'MANUAL_REVIEW');

CREATE TABLE fraud_rules
(
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT         NOT NULL REFERENCES fraud_rule_categories (id),
    rule_name   VARCHAR(100)   NOT NULL,
    rule_type   rule_type_enum NOT NULL,
    priority    INTEGER          DEFAULT 0,
    is_active   BOOLEAN          DEFAULT true,
    risk_score  DECIMAL(3, 2)    DEFAULT 0.00 CHECK (risk_score >= 0.00 AND risk_score <= 1.00),
    action_type action_type_enum DEFAULT 'LOG_ONLY',

    -- JSONB로 유연한 룰 조건 저장
    conditions  JSONB          NOT NULL,

    -- 메타데이터
    description TEXT,
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100),
    created_at  TIMESTAMPTZ      DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ      DEFAULT CURRENT_TIMESTAMP
);

-- JSONB 인덱스 (조건 검색 최적화)
CREATE INDEX idx_fraud_rules_conditions_gin ON fraud_rules USING GIN (conditions);
CREATE INDEX idx_fraud_rules_category_priority ON fraud_rules (category_id, priority DESC);
CREATE INDEX idx_fraud_rules_type_active ON fraud_rules (rule_type, is_active);
CREATE INDEX idx_fraud_rules_updated_at ON fraud_rules (updated_at);

-- 3. 블랙리스트 테이블
CREATE TYPE list_type_enum AS ENUM ('USER', 'ACCOUNT', 'DEVICE', 'IP', 'PHONE', 'EMAIL');
CREATE TYPE severity_enum AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
