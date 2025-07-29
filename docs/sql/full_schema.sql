-- PostgreSQL DDL generated from FDS ERD
SET client_encoding = 'UTF8';

-- ============================================
-- USER DOMAIN
-- ============================================

CREATE TABLE users (
                       id BIGINT PRIMARY KEY,
                       name TEXT NOT NULL,
                       email TEXT UNIQUE NOT NULL,
                       created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
COMMENT ON TABLE users IS '사용자';
COMMENT ON COLUMN users.id IS '사용자 고유 ID (Snowflake)';
COMMENT ON COLUMN users.name IS '사용자 이름';
COMMENT ON COLUMN users.email IS '사용자 이메일 (UNIQUE)';
COMMENT ON COLUMN users.created_at IS '가입 시각';
CREATE INDEX idx_user_id_email ON users(id, created_at);


CREATE TABLE account_balances (
                                  user_id BIGINT PRIMARY KEY,
                                  balance BIGINT NOT NULL DEFAULT 0,
                                  created_at TIMESTAMPTZ DEFAULT now() NOT NULL,
                                  updated_at TIMESTAMPTZ DEFAULT NULL
);
COMMENT ON TABLE account_balances IS '사용자 계좌 잔액';
COMMENT ON COLUMN account_balances.user_id IS '사용자 ID (users.id)';
COMMENT ON COLUMN account_balances.balance IS '현재 보유 잔액';
COMMENT ON COLUMN account_balances.created_at IS '계좌 생성 시각';
COMMENT ON COLUMN account_balances.updated_at IS '잔액 갱신 시각';

CREATE TABLE wallet_addresses (
                                  id BIGINT PRIMARY KEY,
                                  user_id BIGINT NOT NULL,
                                  address TEXT UNIQUE NOT NULL,
                                  chain_code TEXT NOT NULL,
                                  registered_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_wallet_addresses_user_chain ON wallet_addresses(user_id, chain_code);
COMMENT ON TABLE wallet_addresses IS '사용자 지갑 주소';
COMMENT ON COLUMN wallet_addresses.id IS '지갑 주소 ID (Snowflake)';
COMMENT ON COLUMN wallet_addresses.user_id IS '사용자 ID';
COMMENT ON COLUMN wallet_addresses.address IS '온체인 지갑 주소 (UNIQUE)';
COMMENT ON COLUMN wallet_addresses.chain_code IS '체인 코드 (예: ETH)';
COMMENT ON COLUMN wallet_addresses.registered_at IS '등록 시각';

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
CREATE UNIQUE INDEX uniq_email ON correction_log(email);

CREATE INDEX idx_admin_user_id_role ON admin_users(id, role);
CREATE INDEX idx_admin_user_created_at ON admin_users(created_at);

COMMENT ON TABLE admin_users IS '관리자 계정';
COMMENT ON COLUMN admin_users.id IS '관리자 ID';
COMMENT ON COLUMN admin_users.username IS '관리자 계정명';
COMMENT ON COLUMN admin_users.email IS '이메일';
COMMENT ON COLUMN admin_users.role IS '관리자 권한 (ADMIN / AUDITOR)';
COMMENT ON COLUMN admin_users.created_at IS '생성 시각';

-- ============================================
-- TRANSACTION DOMAIN
-- ============================================

CREATE TABLE transactions (
                              id BIGINT PRIMARY KEY,
                              sender_user_id BIGINT NOT NULL,
                              receiver_user_id BIGINT NOT NULL,
                              amount BIGINT NOT NULL,
                              status TEXT NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CORRECTED')),
                              exchange_rate_id BIGINT,
                              received_at TIMESTAMPTZ,
                              created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);

CREATE INDEX idx_transactions_sender_created ON transactions(sender_user_id, created_at);
CREATE INDEX idx_transactions_receiver_created ON transactions(receiver_user_id, created_at);

COMMENT ON TABLE transactions IS '송금 트랜잭션';
COMMENT ON COLUMN transactions.id IS '트랜잭션 ID';
COMMENT ON COLUMN transactions.sender_user_id IS '보낸 사용자 ID';
COMMENT ON COLUMN transactions.receiver_user_id IS '받는 사용자 ID';
COMMENT ON COLUMN transactions.amount IS '송금 금액';
COMMENT ON COLUMN transactions.status IS '트랜잭션 상태';
COMMENT ON COLUMN transactions.exchange_rate_id IS '참조 환율 ID';
COMMENT ON COLUMN transactions.received_at IS '수신 확인 시각';
COMMENT ON COLUMN transactions.created_at IS '트랜잭션 생성 시각';

CREATE TABLE tx_history (
                            id BIGINT PRIMARY KEY,
                            tx_id BIGINT NOT NULL,
                            prev_status TEXT NOT NULL,
                            next_status TEXT NOT NULL,
                            changed_at TIMESTAMPTZ NOT NULL,
                            changed_by BIGINT NOT NULL
);
CREATE INDEX idx_tx_history_tx_id ON tx_history(tx_id);
COMMENT ON TABLE tx_history IS '트랜잭션 상태 변경 이력';
COMMENT ON COLUMN tx_history.id IS '이력 ID';
COMMENT ON COLUMN tx_history.tx_id IS '트랜잭션 참조 ID';
COMMENT ON COLUMN tx_history.prev_status IS '이전 상태';
COMMENT ON COLUMN tx_history.next_status IS '변경 후 상태';
COMMENT ON COLUMN tx_history.changed_at IS '변경 시각';
COMMENT ON COLUMN tx_history.changed_by IS '변경한 관리자 ID';

CREATE TABLE correction_log (
                                id BIGINT PRIMARY KEY,
                                tx_id BIGINT NOT NULL,
                                new_tx_id BIGINT NOT NULL,
                                amount BIGINT NOT NULL,
                                restored_at TIMESTAMPTZ NOT NULL,
                                restored_by BIGINT NOT NULL,
                                reason TEXT NOT NULL
);
CREATE UNIQUE INDEX uniq_correction_log_tx_id ON correction_log(tx_id);
CREATE INDEX idx_tx_id_restored_by ON correction_log(tx_id, restored_by);
CREATE INDEX idx_new_tx_id_restored_by ON correction_log(new_tx_id, restored_by);

COMMENT ON TABLE correction_log IS '트랜잭션 정정 이력';
COMMENT ON COLUMN correction_log.id IS '정정 ID';
COMMENT ON COLUMN correction_log.tx_id IS '원본 트랜잭션 ID';
COMMENT ON COLUMN correction_log.new_tx_id IS '정정된 신규 트랜잭션 ID';
COMMENT ON COLUMN correction_log.amount IS '정정 금액';
COMMENT ON COLUMN correction_log.restored_at IS '정정 시각';
COMMENT ON COLUMN correction_log.restored_by IS '처리 관리자';
COMMENT ON COLUMN correction_log.reason IS '정정 사유';

-- ============================================
-- RISK DETECTION DOMAIN (Phase-2)
-- ============================================

CREATE TABLE rules (
                       id BIGINT PRIMARY KEY,
                       rule_name TEXT UNIQUE NOT NULL,
                       condition_json JSON NOT NULL,
                       threshold INT NOT NULL,
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMPTZ DEFAULT now()
);
COMMENT ON TABLE rules IS '이상거래 탐지 룰';
COMMENT ON COLUMN rules.id IS '룰 ID';
COMMENT ON COLUMN rules.rule_name IS '룰 이름';
COMMENT ON COLUMN rules.condition_json IS '조건 JSON';
COMMENT ON COLUMN rules.threshold IS '임계값';
COMMENT ON COLUMN rules.enabled IS '활성화 여부';
COMMENT ON COLUMN rules.created_at IS '생성 시각';

CREATE TABLE rule_history (
                              id BIGINT PRIMARY KEY,
                              rule_id BIGINT NOT NULL,
                              version INT NOT NULL,
                              condition_json JSON NOT NULL,
                              threshold INT NOT NULL,
                              created_by BIGINT NOT NULL,
                              created_at TIMESTAMPTZ DEFAULT now()
);
CREATE UNIQUE INDEX uniq_rule_version ON rule_history(rule_id, version);
CREATE INDEX idx_rule_created_at ON rule_history(rule_id, created_at);
COMMENT ON TABLE rule_history IS '룰 변경 이력';
COMMENT ON COLUMN rule_history.id IS '이력 ID';
COMMENT ON COLUMN rule_history.rule_id IS '룰 ID';
COMMENT ON COLUMN rule_history.version IS '버전';
COMMENT ON COLUMN rule_history.condition_json IS '조건';
COMMENT ON COLUMN rule_history.threshold IS '임계값';
COMMENT ON COLUMN rule_history.created_by IS '작성자';
COMMENT ON COLUMN rule_history.created_at IS '작성 시각';

CREATE TABLE risk_logs (
                           id BIGINT PRIMARY KEY,
                           tx_id BIGINT NOT NULL,
                           rule_hit BOOLEAN,
                           ai_score DOUBLE PRECISION,
                           final_decision TEXT NOT NULL CHECK (final_decision IN ('PASS', 'REVIEW', 'BLOCK')),
                           evaluated_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_risk_logs_txid_decision ON risk_logs(tx_id, final_decision);
CREATE INDEX idx_risk_logs_eval ON risk_logs(evaluated_at);
COMMENT ON TABLE risk_logs IS '위험 탐지 로그';
COMMENT ON COLUMN risk_logs.id IS '로그 ID';
COMMENT ON COLUMN risk_logs.tx_id IS '트랜잭션 참조';
COMMENT ON COLUMN risk_logs.rule_hit IS '룰 적중 여부';
COMMENT ON COLUMN risk_logs.ai_score IS 'AI 스코어';
COMMENT ON COLUMN risk_logs.final_decision IS '최종 결정';
COMMENT ON COLUMN risk_logs.evaluated_at IS '평가 시각';

CREATE TABLE risk_rule_hits (
                                id BIGINT PRIMARY KEY,
                                risk_log_id BIGINT NOT NULL,
                                rule_id BIGINT NOT NULL,
                                hit BOOLEAN NOT NULL,
                                score DOUBLE PRECISION NOT NULL
);
CREATE INDEX idx_risk_hit_rule ON risk_rule_hits(rule_id);
CREATE INDEX idx_risk_hit_log ON risk_rule_hits(risk_log_id);
COMMENT ON TABLE risk_rule_hits IS '룰별 적중 내역';
COMMENT ON COLUMN risk_rule_hits.id IS '기록 ID';
COMMENT ON COLUMN risk_rule_hits.risk_log_id IS '로그 ID';
COMMENT ON COLUMN risk_rule_hits.rule_id IS '룰 ID';
COMMENT ON COLUMN risk_rule_hits.hit IS '적중 여부';
COMMENT ON COLUMN risk_rule_hits.score IS '스코어';

-- ============================================
-- EXCHANGE DOMAIN
-- ============================================

CREATE TABLE exchange_rates (
                                id BIGINT PRIMARY KEY,
                                from_currency TEXT NOT NULL,
                                to_currency TEXT NOT NULL,
                                rate NUMERIC(18,8) NOT NULL,
                                fetched_at TIMESTAMPTZ NOT NULL,
                                source TEXT NOT NULL,
                                is_batch BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_exchange_composite ON exchange_rates(from_currency, to_currency, source, fetched_at);
COMMENT ON TABLE exchange_rates IS '환율 정보';
COMMENT ON COLUMN exchange_rates.id IS '환율 ID';
COMMENT ON COLUMN exchange_rates.from_currency IS '기준 통화';
COMMENT ON COLUMN exchange_rates.to_currency IS '대상 통화';
COMMENT ON COLUMN exchange_rates.rate IS '환율';
COMMENT ON COLUMN exchange_rates.fetched_at IS '가져온 시각';
COMMENT ON COLUMN exchange_rates.source IS '출처';
COMMENT ON COLUMN exchange_rates.is_batch IS '배치 여부';

-- ============================================
-- DLQ DOMAIN
-- ============================================

CREATE TABLE dlq_events (
                            id BIGINT PRIMARY KEY,
                            tx_id BIGINT NOT NULL,
                            component TEXT NOT NULL,
                            error_message TEXT NOT NULL,
                            received_at TIMESTAMPTZ NOT NULL,
                            resolved BOOLEAN DEFAULT FALSE,
                            resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_dlq_tx_id ON dlq_events(tx_id);
COMMENT ON TABLE dlq_events IS 'DLQ 장애 이벤트';
COMMENT ON COLUMN dlq_events.id IS 'DLQ 이벤트 ID';
COMMENT ON COLUMN dlq_events.tx_id IS '참조 트랜잭션 ID';
COMMENT ON COLUMN dlq_events.component IS '문제 컴포넌트';
COMMENT ON COLUMN dlq_events.error_message IS '에러 메시지';
COMMENT ON COLUMN dlq_events.received_at IS '발생 시각';
COMMENT ON COLUMN dlq_events.resolved IS '해결 여부';
COMMENT ON COLUMN dlq_events.resolved_at IS '해결 시각';


-- ============================================
-- ONCHAIN DOMAIN (Phase-3)
-- ============================================

CREATE TABLE chains (
                        code TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        rpc_url TEXT,
                        explorer_url TEXT
);
COMMENT ON TABLE chains IS '블록체인 체인 메타정보';
COMMENT ON COLUMN chains.code IS '체인 코드';
COMMENT ON COLUMN chains.name IS '체인 이름';
COMMENT ON COLUMN chains.rpc_url IS 'RPC URL';
COMMENT ON COLUMN chains.explorer_url IS '탐색기 URL';

CREATE TABLE tokens (
                        symbol TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        decimals INT NOT NULL,
                        contract_address TEXT NOT NULL,
                        chain_code TEXT NOT NULL
);
CREATE INDEX idx_tokens_chain_code ON tokens(chain_code);
COMMENT ON TABLE tokens IS '온체인 토큰 정보';
COMMENT ON COLUMN tokens.symbol IS '토큰 심볼';
COMMENT ON COLUMN tokens.name IS '토큰 이름';
COMMENT ON COLUMN tokens.decimals IS '소수점 자리수';
COMMENT ON COLUMN tokens.contract_address IS '컨트랙트 주소';
COMMENT ON COLUMN tokens.chain_code IS '소속 체인 코드';

CREATE TABLE wallet_transfers (
                                  id BIGINT PRIMARY KEY,
                                  tx_id BIGINT NOT NULL,
                                  from_wallet_id BIGINT NOT NULL,
                                  to_wallet_id BIGINT NOT NULL,
                                  to_user_id BIGINT NOT NULL,
                                  exchange_rate_id BIGINT,
                                  token_symbol TEXT NOT NULL,
                                  chain_code TEXT NOT NULL,
                                  amount BIGINT NOT NULL,
                                  status TEXT NOT NULL CHECK (status IN ('REQUESTED', 'PENDING', 'CONFIRMED', 'FAILED')),
                                  onchain_tx_id TEXT NOT NULL,
                                  created_at TIMESTAMPTZ DEFAULT now() NOT NULL
);
CREATE INDEX idx_wallet_transfers_from_wallet ON wallet_transfers(from_wallet_id);
CREATE INDEX idx_wallet_transfers_to_wallet ON wallet_transfers(to_wallet_id);
CREATE INDEX idx_wallet_transfers_to_user ON wallet_transfers(to_user_id);
CREATE INDEX idx_wallet_transfers_status ON wallet_transfers(status);
CREATE INDEX idx_wallet_transfers_tx_hash ON wallet_transfers(onchain_tx_id);
CREATE INDEX idx_wallet_transfers_combo ON wallet_transfers(chain_code, token_symbol, status);
COMMENT ON TABLE wallet_transfers IS '온체인 지갑 송금';
COMMENT ON COLUMN wallet_transfers.id IS '송금 ID';
COMMENT ON COLUMN wallet_transfers.tx_id IS '내부 트랜잭션 참조';
COMMENT ON COLUMN wallet_transfers.from_wallet_id IS '보낸 지갑';
COMMENT ON COLUMN wallet_transfers.to_wallet_id IS '받는 지갑';
COMMENT ON COLUMN wallet_transfers.to_user_id IS '수신 사용자';
COMMENT ON COLUMN wallet_transfers.exchange_rate_id IS '환율 참조';
COMMENT ON COLUMN wallet_transfers.token_symbol IS '토큰 심볼';
COMMENT ON COLUMN wallet_transfers.chain_code IS '체인 코드';
COMMENT ON COLUMN wallet_transfers.amount IS '금액';
COMMENT ON COLUMN wallet_transfers.status IS '송금 상태';
COMMENT ON COLUMN wallet_transfers.onchain_tx_id IS '온체인 트랜잭션 ID';
COMMENT ON COLUMN wallet_transfers.created_at IS '생성 시각';

CREATE TABLE onchain_tx_logs (
                                 id BIGINT PRIMARY KEY,
                                 wallet_transfer_id BIGINT NOT NULL,
                                 tx_hash TEXT NOT NULL,
                                 status TEXT NOT NULL CHECK (status IN ('SENT', 'CONFIRMED', 'REORGED', 'DROPPED')),
                                 confirmed_block INT,
                                 confirmed_at TIMESTAMPTZ
);
CREATE INDEX idx_onchain_tx_hash ON onchain_tx_logs(tx_hash);
CREATE INDEX idx_onchain_log_composite ON onchain_tx_logs(wallet_transfer_id, status);
COMMENT ON TABLE onchain_tx_logs IS '온체인 전송 로그';
COMMENT ON COLUMN onchain_tx_logs.id IS '로그 ID';
COMMENT ON COLUMN onchain_tx_logs.wallet_transfer_id IS '참조 송금 ID';
COMMENT ON COLUMN onchain_tx_logs.tx_hash IS '트랜잭션 해시';
COMMENT ON COLUMN onchain_tx_logs.status IS '상태';
COMMENT ON COLUMN onchain_tx_logs.confirmed_block IS '확정 블록';
COMMENT ON COLUMN onchain_tx_logs.confirmed_at IS '확정 시각';