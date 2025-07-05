# 이상 거래 실시간 탐지 시스템 (FDS)

## 프로젝트 개요
사용자 거래에서 이상 징후를 실시간으로 감지하고, 관리자에게 알림과 리포트를 제공하는 FDS 시스템

> Kafka 기반 이벤트 스트리밍과 Redis 캐시, 룰/AI 기반 이상 거래 탐지 및 실시간 WebSocket 대시보드를 통해 운영성과 확장성을 갖춘 FDS 아키텍처입니다.

## 목표

- Kafka 기반 이벤트 스트리밍 구조 설계
- Redis 기반 실시간 탐지 구조 구현
- 이상 거래 룰 기반 탐지 및 관리자 대시보드 구축
- 추후 AI 기반 탐지 확장이 가능한 형태로 설계
- 실시간성과 확장성을 고려한 아키텍처 실습

## 해결하고자 하는 문제

| 사례 | 설명 |
|------|------|
| 토스 | 3초 간격 5회 송금 시도 → 자동 차단 |
| 신한은행 | 새벽 2시 해외 결제 → 탐지 후 보류 |
| 카카오페이 | 낯선 기기에서 고액 결제 → 알림 후 차단 |

---

## 로그인 
```mermaid
sequenceDiagram
    participant Client as 사용자(Client)
    participant Auth as 인증 서버
    participant API as API Gateway
    participant TokenVerifier as JWT 검증 미들웨어

    Client->>Auth: POST /login (ID/PW)
    Auth-->>Client: JWT Access Token

    Client->>API: 요청 Authorization: Bearer <JWT>
    API->>TokenVerifier: verify(JWT)
    TokenVerifier-->>API: 유효 / 무효 응답
    API-->>Client: 요청 처리 결과
```

## 송금 요청 및 트랜잭션
```mermaid
sequenceDiagram
    participant Client as 사용자
    participant API as API Gateway
    participant TransferService as 송금 도메인 서비스
    participant Redis as 잔액 캐시
    participant Kafka as Kafka Broker

    Client->>API: POST /transfer (금액, 수취인, device)
    API->>TransferService: tryTransfer(userId, amount)

    TransferService->>Redis: EVAL Lua 선차감
    alt 차감 성공
        TransferService->>Kafka: emit transfer.initiated
        TransferService-->>API: { success: true, txId }
    else
        TransferService-->>API: { success: false, error: "잔액 부족" }
    end

    API-->>Client: 송금 결과 응답
```

## TxWorker 처리 + 실패 시 DLQ emit
```mermaid
sequenceDiagram
    participant Kafka as Kafka Broker
    participant TxWorker as 트랜잭션 처리기
    participant RDB as Ledger DB
    participant Redis as 잔액 캐시
    participant Slack as Slack Alert
    participant DLQ as DLQ

    Kafka->>TxWorker: consume transfer.initiated
    TxWorker->>RDB: INSERT INTO tx (status=PENDING)
    alt 성공
        TxWorker->>Kafka: emit transfer.created
    else 실패
        TxWorker->>Redis: INCRBY 복원
        TxWorker->>Slack: INSERT 실패 알림
        TxWorker->>DLQ: emit tx.sync_required (txId, amount, reason="rdb_insert_failed")
    end
```

## 룰 수정 및 반영 흐름 (Admin Console에서 룰 수정 → RiskEval 동기화 흐름 추가)
```mermaid
sequenceDiagram
    participant AdminUI as Admin Console
    participant RuleAPI as 룰 저장 API
    participant RuleStore as Redis/Mongo
    participant RiskEval as RiskEvaluator

    AdminUI->>RuleAPI: POST /rules (신규 룰, threshold 수정 등)
    RuleAPI->>RuleStore: 룰 저장
    RuleAPI-->>AdminUI: 저장 완료 응답

    Note over RiskEval: 주기적으로 룰 fetch or 구독
    RiskEval->>RuleStore: GET /rules
    RuleStore-->>RiskEval: 최신 룰 리스트
```
##  Tx 상태 변경시 tx_history 기록 흐름 추가
```mermaid
sequenceDiagram
    participant TxStatusUpdater
    participant RDB as Ledger DB
    participant History as tx_history

    TxStatusUpdater->>RDB: UPDATE tx.status = COMPLETED or HOLD
    TxStatusUpdater->>History: INSERT INTO tx_history (txId, status, timestamp)
```


## RiskEval 판단 기준 명확화 + 상태 emit
```mermaid
sequenceDiagram
    participant RiskEval as RiskEvaluator
    participant Kafka as Kafka Broker
    participant Admin as Admin Console
    participant WebSocket as WS Gateway
    participant Slack as Slack

    RiskEval->>RiskEval: 룰 HIT 여부 + AI 스코어 기반 판단
    alt ruleHit == true or aiScore > 0.9
        RiskEval->>Kafka: emit transfer.flagged
        RiskEval->>WebSocket: emit real-time flagged alert
        WebSocket-->>Admin: 대시보드 실시간 표시
        RiskEval->>Slack: 이상 거래 탐지 알림
    else
        RiskEval->>Kafka: emit transfer.approved
    end
```

## Fallback 처리 흐름 강화: TTL 키 + 중복 체크 포함
```mermaid
sequenceDiagram
    participant DLQ as DLQ
    participant SyncWorker as 복구 워커
    participant RDB as Ledger DB
    participant Redis as 잔액 캐시
    participant Slack as Slack

    DLQ->>SyncWorker: consume tx.sync_required
    SyncWorker->>RDB: txId 존재 확인
    alt tx 미존재
        SyncWorker->>RDB: INSERT 복구 트랜잭션
        SyncWorker->>Redis: SET tx:{txId}:status = COMPLETED (TTL 10m)
        SyncWorker->>RDB: INSERT INTO correction_log
        SyncWorker->>Slack: 복구 성공 알림
    else 중복
        SyncWorker->>Slack: 중복 무시 알림
    end
```

| Task        | 설명                                                     |   |                        |
| ----------- | ------------------------------------------------------ | - | ---------------------- |
| 로그인        | JWT 발급, 이후 인증 토큰 사용                                    |   |                        |
| 송금 요청      | TransferService가 Redis 차감 + Kafka 발행                   |   |                        |
| TxWorker   | DB에 트랜잭션 생성 + 실패 시 rollback                            |   |                        |
| FDS 탐지     | Rule + AI 기반 위험 평가 수행                                  |   |                        |
| RiskEval   | 판단 기준(ruleHit                                          |   | aiScore > 0.9)으로 상태 분기 |
| TxStatusUpdater | transfer.approved → status = COMPLETED, flagged → HOLD |   |                        |
| Fallback   | DLQ로 들어온 tx에 대해 복구 수행                                  |   |                        |
| Admin Console | 운영자가 rollback emit → 상태를 ROLLED\_BACK으로 바꿈             |   |                        |
