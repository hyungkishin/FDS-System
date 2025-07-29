## 1. api-server

- Spring Boot 애플리케이션 실행 진입점
- REST Controller 진입점 (e.g. `TransferController`)
- 클라이언트 요청 처리 및 UseCase 호출
- SnowflakeIdGenerator를 주입받아 TransferId 생성
- Config 클래스에서 Bean 조립
    - `TransferServiceConfig`
    - `@Import(JpaRepositoryConfig::class, SnowflakeConfig::class)`
- 의존 모듈:
    - `core-transfer`
    - `infra-rdb`
    - `infra-event`
    - `common`

---

## 2. core-transfer

- 도메인 및 유스케이스 모듈
- 도메인 모델:
    - `Transfer`, `TxStatus`, `TransferId`, `TransferCommand`, `TransferResult`
- 유스케이스:
    - `TransferUseCase` (Port)
    - `TransferService` (Spring 모름)
- 포트 인터페이스:
    - `TransferRepository`
    - `TransferEventPublisher`
- 도메인 이벤트:
    - `TransferCompletedEvent`

---

## 3. infra-rdb

- 영속성(RDB) 어댑터 모듈
- JPA Entity: `Transaction`
- Repository 구현체: `TransferJpaAdapter` (implements `TransferRepository`)
- Spring Data JPA 인터페이스: `TransferJpaRepository`
- 설정 클래스:
    - `JpaRepositoryConfig.kt`  
      → `@EnableJpaRepositories(basePackages = [...])`

---

## 4. infra-event

- 이벤트 발행 어댑터 모듈
- 구현체:
    - `TransferEventLoggingAdapter` (implements `TransferEventPublisher`)
- 현재는 로그 기반, 추후 Kafka 등으로 확장 가능

---

## 5. common

- 공통 유틸리티 모듈
- Snowflake 기반 전역 유일 ID 생성기:
    - `Snowflake`
    - `SnowflakeIdGenerator`
- ID 래퍼 클래스:
    - `TransferId`
- Spring 설정:
    - `SnowflakeProperties` (`@ConfigurationProperties`)
    - `SnowflakeConfig` (`@Configuration`, `@Bean` 등록용)

