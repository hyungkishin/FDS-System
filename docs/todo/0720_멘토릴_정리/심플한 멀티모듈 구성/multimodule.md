## common 
- 공통 타입, 유틸, 예외 정의

> 어떤 모듈에서도 의존할 수 있도록 Spring 없이, 순수 Kotlin으로 유지  
> Ex: Money, UserId, ErrorResponse, DomainException

## core-transfer
- 도메인 로직 (송금 규칙, 정책, 서비스) 담당
- Spring에 의존하지 않는 순수한 비즈니스 계층
- Presentation (api-server)에서만 이걸 사용

> TransferService, TransferCommand, TransferResult

## infra-rdb
- DB 연동을 위한 Spring + JPA 구성
- Entity, Repository, Datasource config 포함
- 도메인에서는 이 모듈을 모르고, 상위 모듈( api-server )만 사용

## 

```text
fds-system/
├── api-server
│   └── Presentation Layer (HTTP Controller)
│   └── Depends on: core-transfer, event-handler, common
│
├── core-transfer
│   └── Business Logic for transfer
│   └── Define TransferEventPublisher interface
│   └── Depends on: common
│
├── event-handler    // Kafka 없이 이벤트 처리
│   └── Implements TransferEventPublisher
│   └── Event 후속 처리 로직 (e.g audit log)
│   └── Depends on: common
│
├── common
│   └── DTO, Event 객체 정의

```

```text
[api-server]
↓
[core-transfer]  ←─ TransferEventPublisher Interface 정의
↓                     ↑
[TransferCompletedEvent]  │
↓                     │
[event-handler] ──────────┘ 구현체에서 후속 처리
```

> Kafka 도입 시 event-handler 내부 구현만 교체   
> Publish -> KafkaProducer, Consume -> KafkaListener
