-- FDS (Fraud Detection System) 스키마
-- TODO: Database: fds (transfer DB와 분리 필요.)

-- Rule 정의 테이블
CREATE TABLE IF NOT EXISTS fraud_rules (
                             id BIGINT PRIMARY KEY,  -- Snowflake ID로 직접 삽입
                             rule_name VARCHAR(100) NOT NULL,
                             rule_type VARCHAR(50) NOT NULL,
                             weight BIGINT NOT NULL CHECK (weight >= 0 AND weight <= 10000),  -- 0~10000 (10000 = 1.0)
                             threshold JSONB NOT NULL,
                             is_active BOOLEAN DEFAULT true,
                             priority INTEGER DEFAULT 0,
                             created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE fraud_rules IS '사기 탐지 룰 정의 테이블';
COMMENT ON COLUMN fraud_rules.id IS 'Snowflake ID';
COMMENT ON COLUMN fraud_rules.rule_name IS '룰 이름 (e.g: 금액 한도 검증)';
COMMENT ON COLUMN fraud_rules.rule_type IS '룰 타입 (AMOUNT, FREQUENCY, TIME, DEVICE)';
COMMENT ON COLUMN fraud_rules.weight IS '가중치 (고정소수점, 10000 = 1.0, 4000 = 0.4)';
COMMENT ON COLUMN fraud_rules.threshold IS '룰별 임계값 설정 (JSON)';
COMMENT ON COLUMN fraud_rules.is_active IS '활성화 여부';
COMMENT ON COLUMN fraud_rules.priority IS '우선순위 (낮을수록 먼저 실행)';

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_fraud_rules_type_active ON fraud_rules(rule_type, is_active);
CREATE INDEX IF NOT EXISTS idx_fraud_rules_priority ON fraud_rules(priority) WHERE is_active = true;

-- 검증 결과 저장 테이블
CREATE TABLE IF NOT EXISTS fraud_detections
(
                                  id BIGINT PRIMARY KEY,  -- Snowflake ID
                                  event_id BIGINT NOT NULL,
                                  from_account_id BIGINT NOT NULL,  -- Snowflake ID
                                  to_account_id BIGINT NOT NULL,    -- Snowflake ID
                                  amount BIGINT NOT NULL,
                                  currency VARCHAR(10) NOT NULL,
                                  total_risk_score INTEGER NOT NULL,
                                  action_type VARCHAR(20) NOT NULL,
                                  rule_results JSONB NOT NULL,
                                  detected_at TIMESTAMPTZ NOT NULL,
                                  trace_id VARCHAR(255),
                                  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE fraud_detections IS '사기 탐지 결과 저장 테이블';
COMMENT ON COLUMN fraud_detections.id IS 'Snowflake ID';
COMMENT ON COLUMN fraud_detections.event_id IS 'Transfer 이벤트 ID';
COMMENT ON COLUMN fraud_detections.from_account_id IS '출금 계좌 ID (Snowflake)';
COMMENT ON COLUMN fraud_detections.to_account_id IS '입금 계좌 ID (Snowflake)';
COMMENT ON COLUMN fraud_detections.amount IS '거래 금액';
COMMENT ON COLUMN fraud_detections.currency IS '통화 코드 (KRW, USD)';
COMMENT ON COLUMN fraud_detections.total_risk_score IS '총 위험 점수 (0-100)';
COMMENT ON COLUMN fraud_detections.action_type IS '조치 (ALLOW, REVIEW, BLOCK)';
COMMENT ON COLUMN fraud_detections.rule_results IS '각 룰 실행 결과 (JSON 배열)';
COMMENT ON COLUMN fraud_detections.detected_at IS '탐지 시각';
COMMENT ON COLUMN fraud_detections.trace_id IS '분산 추적 ID';

-- 인덱스
CREATE INDEX IF NOT EXISTS idx_fraud_detections_event_id ON fraud_detections(event_id);
CREATE INDEX IF NOT EXISTS idx_fraud_detections_from_account ON fraud_detections(from_account_id, detected_at);
CREATE INDEX IF NOT EXISTS idx_fraud_detections_action ON fraud_detections(action_type, detected_at);
CREATE INDEX IF NOT EXISTS idx_fraud_detections_trace_id ON fraud_detections(trace_id) WHERE trace_id IS NOT NULL;

-- 설정 테이블 (key-value)
CREATE TABLE IF NOT EXISTS fraud_settings (
                                key VARCHAR(100) PRIMARY KEY,
                                value JSONB NOT NULL,
                                description TEXT,
                                updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE fraud_settings IS 'FDS 전역 설정 테이블';
COMMENT ON COLUMN fraud_settings.key IS '설정 키';
COMMENT ON COLUMN fraud_settings.value IS '설정 값 (JSON)';
COMMENT ON COLUMN fraud_settings.description IS '설정 설명';

-- 자동 업데이트 트리거
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_fraud_rules_updated_at ON fraud_rules;
CREATE TRIGGER trg_fraud_rules_updated_at
    BEFORE UPDATE ON fraud_rules
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS trg_fraud_settings_updated_at ON fraud_settings;
CREATE TRIGGER trg_fraud_settings_updated_at
    BEFORE UPDATE ON fraud_settings
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- 초기 데이터 (Snowflake ID는 애플리케이션에서 생성)

-- 단기간 다중 송금 탐지 룰
INSERT INTO fraud_rules (id, rule_name, rule_type, weight, threshold, is_active, priority) VALUES
    (1000000000000000005, '단기간 다중 송금', 'RAPID_TRANSFER', 4000, '{"count": 5, "minutes": 10}'::jsonb, true, 2)
ON CONFLICT (id) DO NOTHING;

-- 수신자 분산 패턴 탐지
INSERT INTO fraud_rules (id, rule_name, rule_type, weight, threshold, is_active, priority) VALUES
    (1000000000000000006, '수신자 분산 패턴', 'DISPERSED_RECIPIENTS', 6000, '{"recipient_count": 3, "hours": 24}'::jsonb, true, 3)
ON CONFLICT (id) DO NOTHING;

-- 시간대별 이상 거래
INSERT INTO fraud_rules (id, rule_name, rule_type, weight, threshold, is_active, priority) VALUES
    (1000000000000000007, '야간 고액 송금', 'NIGHT_HIGH_AMOUNT', 3000, '{"hours": [0,1,2,3,4,5], "amount": 1000000}'::jsonb, true, 4)
ON CONFLICT (id) DO NOTHING;

-- 첫 거래 고액
INSERT INTO fraud_rules (id, rule_name, rule_type, weight, threshold, is_active, priority) VALUES
    (1000000000000000008, '첫 거래 고액', 'FIRST_HIGH_AMOUNT', 7000, '{"amount": 3000000}'::jsonb, true, 5)
ON CONFLICT (id) DO NOTHING;

-- 금액 패턴 탐지 (정확히 같은 금액 반복)
INSERT INTO fraud_rules (id, rule_name, rule_type, weight, threshold, is_active, priority) VALUES
    (1000000000000000009, '동일 금액 반복', 'SAME_AMOUNT_REPEAT', 4000, '{"count": 3, "hours": 24}'::jsonb, true, 6)
ON CONFLICT (id) DO NOTHING;