# 이상 거래 실시간 탐지 시스템 (FDS)

Kafka 기반 이벤트 스트리밍과 Redis 캐시, Rule/AI 기반 이상 거래 탐지, DLQ 복구, WebSocket 실시간 알림까지 통합한 실전형 FDS 아키텍처입니다.

## 프로젝트 개요

사용자 거래에서 이상 징후를 실시간으로 감지하고, 관리자에게 알림과 리포트를 제공하는 이상 거래 탐지 시스템입니다.  
**실시간성과 장애 복구, 확장성, 운영자 관측성까지 모두 고려한 아키텍처 설계에 중점을 두었습니다.**

## 요약
- [SequenceDiagram](docs/SequenceDiagram.md)
- [ERD](docs/ERD.puml)
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

## Kafka 토픽 구성 요약

| 토픽 이름                | 발행자                | 소비자                     | 설명                |
| -------------------- | ------------------ | ----------------------- | ----------------- |
| `transfer.initiated` | `transfer-service` | `tx-worker`             | 송금 요청 이벤트         |
| `transfer.created`   | `tx-worker`        | `risk-eval`             | 트랜잭션 성공 생성        |
| `transfer.approved`  | `risk-eval`        | TxStatusUpdater, Admin UI | 이상 없음 / 거래 완료 처리 |
| `transfer.flagged`   | `risk-eval`        | WebSocket, Slack, Admin | 이상 탐지됨            |
| `tx.sync_required`   | `tx-worker`        | `sync-worker`           | RDB 삽입 실패 시 보정 필요 |
