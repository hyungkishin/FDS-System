# Transentia

> 트랜잭션을 넘어, 자금의 흐름을 인식하고 판단하는 시스템입니다.

Kafka 기반 이벤트 스트리밍, Redis 캐시, Rule/AI 기반 이상 거래 탐지, DLQ 복구, WebSocket 실시간 알림까지 통합한 **FDS 아키텍처**입니다.

---

## 프로젝트 개요

사용자 거래에서 이상 징후를 **실시간으로 감지**하고,  
운영자에게 **알림 및 리포트**를 제공하는 **이상 거래 탐지 플랫폼**입니다.

설계의 핵심은 다음과 같습니다.

- **실시간성**: Kafka 이벤트 기반 구조
- **신뢰성**: DLQ 복구 & TTL 기반 재처리
- **확장성**: 도메인 분리, 멀티모듈 설계
- **관측성**: 알림, 로그, 룰 기반 추적 가능

---

## 시스템 요약

| 구성 요소       | 기술 스택            | 설명 |
|----------------|---------------------|------|
| **API 서버**        | Spring Boot 3.x (REST) | 인증, 송금, 룰 등록 등 |
| **비동기 처리**      | Apache Kafka         | 송금 이벤트 스트리밍 |
| **Outbox Relay**    | @Scheduled + Partitioning | 이벤트 안정적 발행 |
| **캐시/선차감 처리**  | Redis + Lua Script   | TTL 기반 상태 보존 |
| **트랜잭션 저장**     | PostgreSQL / MySQL   | 정합성 있는 거래 기록 저장 |
| **알림/대시보드**    | WebSocket / Slack    | 실시간 탐지 결과 전달 |
| **복구/보정 처리**   | DLQ Worker + TTL     | 장애 발생 시 재처리 |

---

## Phase 1: Outbox 패턴 + 파티셔닝 ✅

### 아키텍처

```
[Transfer Service]
    ↓ (DB Transaction)
[@Transactional]
  - 송금 처리
  - Outbox 저장 (같은 트랜잭션!)
    ↓
[Outbox Table]
    ↓
[Relay Server 3대]
  - Instance 0: MOD(id,3)=0
  - Instance 1: MOD(id,3)=1
  - Instance 2: MOD(id,3)=2
    ↓
[Kafka Topic: transfers]
    ↓
[FDS Consumer]
```

### 핵심 구현

**1. 트랜잭션 원자성 보장**
```kotlin
@Transactional
fun transfer(command: TransferCommand) {
    transactionRepository.save(transaction)
    outboxRepository.save(event)  // 같은 트랜잭션!
}
```

**2. 파티셔닝으로 병렬 처리**
```kotlin
// 각 인스턴스가 서로 다른 이벤트 처리
SELECT * FROM transfer_events
WHERE MOD(event_id, 3) = instanceId
FOR UPDATE SKIP LOCKED
```

**3. 자동 재시도 (Exponential Backoff)**
```kotlin
1차 실패 → 2초 후 재시도
2차 실패 → 4초 후 재시도
3차 실패 → 8초 후 재시도
...
```

### 성능 결과

- **처리량**: 3대로 3배 향상 (470 TPS → 1,410 TPS)
- **락 경합**: 제거 (각 인스턴스가 다른 파티션 처리)
- **균등 분배**: 33.3% / 33.3% / 33.4%

### 상세 문서

- [Outbox Pattern 설계](docs/etc/outbox-pattern.md)
- [파티셔닝 전략](docs/etc/partitioning-strategy.md)
- [성능 테스트 결과](docs/etc/performance-test.md)
- [Relay 서버 가이드](services/transfer/instances/transfer-relay/README.md)

---

## 테이블

| 테이블명           | 설명 |
|--------------------|------|
| `users`            | 일반 사용자 정보 |
| `account_balance`  | 계좌 잔액 관리 |
| `admin_users`      | 운영자 정보 및 권한 |
| `transactions`     | 송금 트랜잭션 요청/처리 |
| `tx_history`       | 상태 변경 이력 기록 |
| `transfer_events`  | Outbox 테이블 (이벤트 발행 큐) |
| `correction_log`   | 정정(복구) 기록 |
| `rules`            | 룰 정의 (JSON 기반 조건) |
| `rule_history`     | 룰 버전 관리 |
| `risk_logs`        | 단건 탐지 로그 |
| `risk_rule_hits`   | 어떤 룰이 감지에 영향을 줬는지 |
| `dlq_events`       | 실패 트랜잭션 로그 (DLQ 용도) |

---

## 운영자 권한 (Admin Role)

| Role 이름        | 설명 |
|------------------|------|
| `SUPER_ADMIN`    | 전체 권한 (모든 도메인) |
| `RULE_ADMIN`     | 룰 등록/수정/삭제 |
| `AUDITOR`        | 이력 열람 전용 |
| `OPS_AGENT`      | 실시간 장애 처리 담당 |
| `RISK_ANALYST`   | 탐지 로그 기반 분석 |
| `READ_ONLY`      | 전체 조회만 가능 |

---

## 기술 스택

- Language: **Java 21 / Kotlin**
- Framework: **Spring Boot 3.x**
- Messaging: **Apache Kafka**
- DB: **PostgreSQL**
- Cache: **Redis** (TTL + Lua)
- Realtime: **WebSocket** / STOMP
- Infra: **Docker**, Docker Compose
- CI/CD: GitHub Actions (planned)

---

## 실행 방법

### 로컬 개발 환경

```bash
# 1. 인프라 실행 (PostgreSQL, Kafka, Redis)
docker-compose up -d postgres kafka redis

# 2. Transfer API 실행
./gradlew :services:transfer:instances:transfer-api:bootRun

# 3. Relay 서버 실행 (3대)
docker-compose up -d transfer-relay-0 transfer-relay-1 transfer-relay-2

# 4. FDS API 실행
./gradlew :services:fds:instances:fds-api:bootRun
```

### 전체 시스템 실행

```bash
# 모든 서비스 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f transfer-relay-0
```

---

## 향후 확장 (Phase 2-3)

### Phase 2: FDS 사전 탐지
- [ ] 동기 검증 (송금 전 차단)
- [ ] Redis 캐싱 (빠른 룰 체크)
- [ ] 빠른 패턴 탐지

### Phase 3: Kafka Streams
- [ ] 복잡한 패턴 분석 (1분에 5회 송금)
- [ ] 실시간 윈도우 집계
- [ ] 이상 패턴 자동 학습

### 장기 계획
- 지갑 도메인 연동 (OnChain/OffChain)
- Fraud Score 모델 학습 (AI 모델 내장)
- CDC 전환 (Debezium)
- ElasticSearch 연동 (로그 + 탐색용)

---

## 설계 원칙

- 도메인 주도 설계 (DDD)
- 멀티모듈 아키텍처 기반
- 도메인별 서비스 분리 (MSA 확장 고려)
- 트랜잭션 기반 흐름 감지
- 이벤트 소싱 기반 처리
- 단계적 개선 (Phase 1 → 2 → 3)
