package io.github.hyungkishin.transentia.relay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Outbox Relay 설정
 *
 * Outbox 패턴을 구현하는 Relay 서버의 동작을 제어하는 설정값들을 정의한다.
 * 이 설정들은 application.yml의 app.outbox.relay 하위에 정의되며,
 * 환경변수를 통해 동적으로 변경 가능하다.
 *
 * 중요한 설정값은 다음과 같다.
 * 1. 배치 처리 설정 (batchSize, fixedDelayMs)
 * 2. 파티셔닝 설정 (instanceId, totalInstances)
 * 3. 재시도 정책 (baseBackoffMs, maxBackoffMs)
 * 4. 성능 모니터링 (slowProcessingThresholdMs)
 */
@ConfigurationProperties(prefix = "app.outbox.relay")
data class OutboxRelayConfig(
    /**
     * 한 번에 처리할 최대 이벤트 수
     *
     * 값이 클수록 다음과 같은 장/단점을 갖는다.
     * - 장점: DB 쿼리 횟수 감소, 처리량 증가
     * - 단점: 메모리 사용량 증가, 처리 시간 증가
     *
     * batchSize 고려 값: 500 (평시 200 TPS 기준으로 설정하였다.)
     */
    val batchSize: Int = 500,

    /**
     * 배치 처리 간격 (밀리초)
     *
     * 이전 배치 처리가 완료된 후 다음 배치를 시작하기까지의 대기 시간이다.
     * fixedDelay 방식이므로 처리 시간과 무관하게 일정 간격 유지한다.
     *
     * 예를 들면 다음과 같다.
     * - 1000ms: 배치 처리(50ms) → 대기(1000ms) → 다음 배치
     * - 총 1050ms 주기로 실행
     *
     * fixedDelayMs 의 값: 1000ms (초당 1회 실행, 부하 측면에서 적절하다고 판단하였다.)
     */
    val fixedDelayMs: Long = 1000,

    /**
     * 애플리케이션 시작 후 첫 실행까지 대기 시간 (밀리초)
     *
     * 애플리케이션이 완전히 초기화될 시간을 주기 위한 설정
     * Kafka 연결, DB 초기화 등이 완료된 후 Relay 시작
     *
     * initialDelayMs 설정값: 5000ms (5초)
     */
    val initialDelayMs: Long = 5000,

    /**
     * 이벤트 처리를 위한 스레드 풀 크기
     *
     * EventBatchProcessor에서 병렬 처리 시 사용할 스레드 수
     * CPU 코어 수의 2배가 적절 (I/O 대기 시간 고려)
     *
     * 권장값: 8 (4코어 기준)
     */
    val threadPoolSize: Int = 8,

    /**
     * Kafka 전송 타임아웃 (초)
     *
     * 개별 이벤트를 Kafka로 전송할 때 최대 대기 시간
     * 이 시간 초과 시 재시도 또는 실패 처리
     *
     * 권장값: 5초 (네트워크 지연 고려)
     */
    val timeoutSeconds: Long = 5,

    /**
     * 재시도 기본 백오프 시간 (밀리초)
     *
     * 실패한 이벤트의 첫 재시도까지 대기 시간
     * 지수 백오프의 시작점이 되는 값
     *
     * 재시도 패턴 (2배씩 증가)
     * - 1차 실패: 5초 후 재시도
     * - 2차 실패: 10초 후 재시도
     * - 3차 실패: 20초 후 재시도
     * - 4차 실패: 40초 후 재시도
     * - 5차 실패: 80초 후 재시도 (maxBackoffMs로 제한)
     *
     * 권장값: 5000ms (5초)
     */
    val baseBackoffMs: Long = 5000,

    /**
     * 재시도 최대 백오프 시간 (밀리초)
     *
     * 지수 백오프가 무한정 증가하는 것을 방지
     * 이 값을 넘으면 더 이상 증가하지 않음
     *
     * 권장값: 600000ms (10분)
     */
    val maxBackoffMs: Long = 600000,

    /**
     * 느린 처리 경고 임계값 (밀리초)
     *
     * 배치 처리 시간이 이 값을 초과하면 경고 로그 출력
     * 성능 모니터링 및 병목 지점 파악에 사용
     *
     * 권장값: 3000ms (3초)
     */
    val slowProcessingThresholdMs: Long = 3000,
    
    /**
     * 현재 인스턴스의 파티션 ID
     *
     * 여러 Relay 인스턴스가 동시 실행될 때 각 인스턴스를 구분하는 식별자
     * 0부터 시작하며, totalInstances - 1까지의 값을 가진다
     *
     * 파티셔닝 원리:
     * - MOD(event_id, totalInstances) = instanceId
     * - 각 인스턴스는 자신의 파티션에 속한 이벤트만 처리
     *
     * 예시 (3대 구성):
     * - Instance 0: event_id % 3 = 0 → 0, 3, 6, 9, 12...
     * - Instance 1: event_id % 3 = 1 → 1, 4, 7, 10, 13...
     * - Instance 2: event_id % 3 = 2 → 2, 5, 8, 11, 14...
     *
     * Docker 환경변수: ${RELAY_INSTANCE_ID:0}
     * 기본값: 0 (단일 인스턴스)
     */
    val instanceId: Int = 0,

    /**
     * 전체 Relay 인스턴스 수
     *
     * 파티셔닝 계산의 분모로 사용
     * 이 값이 변경되면 기존 PENDING 이벤트들이 재분배됨
     *
     * 확장 방법:
     * 1. 기존 PENDING 이벤트 모두 처리 대기
     * 2. totalInstances 값 변경
     * 3. 새 인스턴스 추가
     *
     * 주의사항 으로는 아래와 같다.
     * - 모든 인스턴스가 동일한 값을 가져야 한다.
     * - 실행 중 변경 시 일시적으로 일부 이벤트 처리 지연이 가능해야 한다.
     *
     * Docker 환경변수: ${RELAY_TOTAL_INSTANCES:1}
     * 기본값: 1 (단일 인스턴스, 파티셔닝 비활성화)
     */
    val totalInstances: Int = 1
)
