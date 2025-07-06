# 이상 거래 실시간 탐지 시스템 (FDS)

Kafka 기반 이벤트 스트리밍과 Redis 캐시, Rule/AI 기반 이상 거래 탐지, DLQ 복구, WebSocket 실시간 알림까지 통합한 실전형 FDS 아키텍처입니다.

## 프로젝트 개요

사용자 거래에서 이상 징후를 실시간으로 감지하고, 관리자에게 알림과 리포트를 제공하는 이상 거래 탐지 시스템입니다.  
**실시간성과 장애 복구, 확장성, 운영자 관측성까지 모두 고려한 아키텍처 설계에 중점을 두었습니다.**

## 요약
- [SequenceDiagram](docs/SequenceDiagram.md)
- [INFRA](docs/infra.puml)

| 구성 요소       | 기술 스택            | 설명 |
|----------------|---------------------|------|
| API 서버        | RESTful API (Spring Boot) | 인증, 송금, 룰 등록 등 |
| 비동기 처리      | Apache Kafka         | 송금 이벤트 스트리밍 |
| 캐시/선차감 처리  | Redis                | 실시간 잔액 확인, TTL 기반 상태 보존 |
| 트랜잭션 저장     | PostgreSQL/MySQL     | 정합성 있는 거래 기록 저장 |
| 알림/대시보드    | WebSocket + Slack    | 실시간 탐지 결과 전달 |
| 복구/보정 처리   | DLQ + Worker         | 장애 발생 시 TTL + 보정 |

## 기술 스택
- Language: Java
- Framework: Spring Boot 3.x (REST, Kafka, WebSocket)
- Message Queue: Apache Kafka
- Database: PostgreSQL / MySQL
- Cache: Redis (Lua Script, TTL)
- WebSocket: Spring WebSocket / STOMP
- Frontend: React (Admin UI)
- Infra: Docker, Docker Compose, Slack Webhook

---

## ERD
- [hot-link](docs/ERD.puml)

| 테이블명                  | 역할 / 목적              | 주요 컬럼 설명                                                                                                                              |
|-----------------------|----------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| **`users`**           | 일반 사용자 정보 관리         | - `id`: 유저 식별자 (UUID)<br> - `email`: 로그인/계정 기준<br> - `created_at`: 가입 일자                                                              |
| **`account_balance`** | 사용자 계좌               | - `id`: 유저 식별자 (UUID)<br> -  `balance`: 잔액<br/> - `updated_at`: 변경 일자                                                                 |
| **`admin_users`**     | 운영자 계정 및 권한 관리       | - `username`, `email`: 로그인/담당자 구분용<br> - `role`: 권한 (e.g., admin, auditor)                                                            |
| **`transactions`**    | 송금 요청 기록 (기본 트랜잭션)   | - `user_id`: 보낸 사람<br> - `receiver_id`: 받은 사람<br> - `amount`, `status`: 금액 및 처리 상태<br> - `created_at`: 요청 시간                          |
| **`tx_history`**      | 트랜잭션 상태 변경 로그        | - `tx_id`: 어떤 트랜잭션인지<br> - `prev_status → next_status`: 상태 변화 내역 (e.g., `PENDING → FAILED`)<br> - `changed_by`: 운영자 ID                |
| **`correction_log`**  | 트랜잭션 복구/보정 기록        | - `tx_id`: 보정 대상 트랜잭션<br> - `amount`: 보정된 금액<br> - `restored_by`: 복구한 운영자<br> - `reason`: 사유                                          |
| **`rules`**           | 현재 적용 중인 탐지 룰        | - `rule_name`: 고유 이름<br> - `condition_json`: 룰 조건 (JSON 형식)<br> - `threshold`: 임계값<br> - `enabled`: 활성화 여부                            |
| **`rule_history`**    | 룰 버전 이력 관리           | - `rule_id`: 어떤 룰인지<br> - `version`: 버전 번호<br> - `created_by`: 등록자<br> - `condition_json`: 그 당시 룰 조건                                  |
| **`risk_logs`**       | 단건 트랜잭션에 대한 탐지 결과    | - `tx_id`: 어떤 트랜잭션인지<br> - `rule_hit`: 룰 감지 여부<br> - `ai_score`: AI 탐지 점수<br> - `final_decision`: 최종 판단 (e.g., `APPROVED`, `BLOCKED`) |
| **`risk_rule_hits`**  | 어떤 룰이 탐지에 영향을 줬는지    | - `risk_log_id`: 탐지 로그 참조<br> - `rule_id`: 해당 룰<br> - `hit`: 조건 만족 여부<br> - `score`: 룰 기반 평가 점수                                       |
| **`dlq_events`**      | 장애 발생 트랜잭션 기록 (DLQ용) | - `tx_id`: 실패한 트랜잭션<br> - `component`: 실패 위치 (e.g., `TxWorker`, `RiskEval`)<br> - `error_message`: 상세 에러<br> - `resolved`: 복구 여부      |

## Admin Role
| Role 이름        | 설명                  | 권한 예시                 |
| -------------- | ------------------- | --------------------- |
| `SUPER_ADMIN`  | 최상위 관리자 (시스템 전체 제어) | 모든 룰 수정, 복구 승인, 계정 관리 |
| `RULE_ADMIN`   | 룰 편집 권한 담당          | 탐지 룰 등록/수정/삭제 가능      |
| `AUDITOR`      | 감사/이력 열람 전용         | 조회만 가능, 수정 불가         |
| `OPS_AGENT`    | 운영 담당자              | 이상 거래 알림 확인, 복구 승인    |
| `RISK_ANALYST` | 이상 탐지 분석자           | 탐지 결과 열람, 룰 개선 제안 가능  |
| `READ_ONLY`    | 테스트 또는 관찰 계정        | 모든 데이터 열람만 가능         |

## Kafka 토픽 구성 요약

| 토픽 이름                | 발행자                | 소비자                     | 설명                |
| -------------------- | ------------------ | ----------------------- | ----------------- |
| `transfer.initiated` | `transfer-service` | `tx-worker`             | 송금 요청 이벤트         |
| `transfer.created`   | `tx-worker`        | `risk-eval`             | 트랜잭션 성공 생성        |
| `transfer.approved`  | `risk-eval`        | TxStatusUpdater, Admin UI | 이상 없음 / 거래 완료 처리 |
| `transfer.flagged`   | `risk-eval`        | WebSocket, Slack, Admin | 이상 탐지됨            |
| `tx.sync_required`   | `tx-worker`        | `sync-worker`           | RDB 삽입 실패 시 보정 필요 |
