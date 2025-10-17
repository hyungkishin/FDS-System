package io.github.hyungkishin.transentia.relay.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Outbox Relay 설정
 *
 * Outbox 패턴을 구현하는 Relay 서버의 동작을 제어하는 설정값들을 정의한다.
 * 이 설정들은 application.yml의 app.outbox.relay 하위에 정의되며,
 * 환경변수를 통해 동적으로 변경 가능하다.
 *
 * 중요한 설정값은 다음과 같다:
 * 1. 배치 처리 설정 (batchSize, fixedDelayMs)
 * 2. 멀티 스레드 설정 (threadPoolSize)
 * 3. 재시도 정책 (baseBackoffMs, maxBackoffMs, stuckThresholdSeconds)
 * 4. 성능 모니터링 (slowProcessingThresholdMs)
 */
@ConfigurationProperties(prefix = "app.outbox.relay")
data class OutboxRelayConfig(
    /**
     * 한 번에 처리할 최대 이벤트 수
     *
     * 값이 클수록 다음과 같은 장/단점을 갖는다:
     * - 장점: DB 쿼리 횟수 감소, 처리량 증가
     * - 단점: 메모리 사용량 증가, 처리 시간 증가
     *
     * ## 성능 계산
     * - 평시 200 TPS 기준
     * - 1초당 1회 실행
     * - batchSize 500 = 2.5초분 버퍼
     */
    val batchSize: Int = 500,

    /**
     * 배치 처리 간격 (밀리초)
     *
     * fixedDelay 방식으로 이전 배치 처리 완료 후 대기 시간
     * 처리 시간과 무관하게 일정 간격 유지
     *
     * ## 예시
     * - 배치 처리: 50ms
     * - fixedDelay: 1000ms
     * - 총 주기: 1050ms
     */
    val fixedDelayMs: Long = 1000,

    /**
     * 애플리케이션 시작 후 첫 실행까지 대기 시간 (밀리초)
     *
     * 애플리케이션 초기화 시간 확보 (Kafka 연결, DB 초기화 등)
     *
     * 권장값: 5000ms (5초)
     */
    val initialDelayMs: Long = 5000,

    /**
     * 멀티 스레드 풀 크기
     *
     * EventBatchProcessor에서 병렬 Kafka 전송 시 사용
     *
     * ## 계산식
     * - I/O 바운드 작업: CPU 코어 수 × 2
     * - 4코어: 8 스레드
     * - 8코어: 16 스레드
     *
     * ## 성능 예측 (8 스레드 기준 입니다.)
     * - 배치 크기: 500
     * - 청크 크기: 16 (Runtime.availableProcessors() * 2)
     * - 청크 수: 500 / 16 = 32 (청크)
     */
    val threadPoolSize: Int = 8,

    /**
     * Kafka 전송 타임아웃 (초)
     *
     * 개별 이벤트 전송 시 최대 대기 시간
     * 타임아웃 초과 시 재시도 또는 실패 처리
     */
    val timeoutSeconds: Long = 5,

    /**
     * 재시도 기본 백오프 시간 (밀리초)
     *
     * 실패한 이벤트의 첫 재시도 대기 시간 (지수 백오프 시작점)
     *
     * ## 재시도 패턴 (2배씩 증가)
     * - 1차 실패: 5초 후 재시도
     * - 2차 실패: 10초 후 재시도
     * - 3차 실패: 20초 후 재시도
     * - 4차 실패: 40초 후 재시도
     * - 5차 실패: 80초 후 재시도 (maxBackoffMs로 제한)
     */
    val baseBackoffMs: Long = 5000,

    /**
     * 재시도 최대 백오프 시간 (밀리초)
     *
     * 지수 백오프의 상한선 (무한정 증가 방지)
     */
    val maxBackoffMs: Long = 600000,

    /**
     * Stuck SENDING 감지 임계값 (초)
     *
     * SENDING 상태로 이 시간 이상 경과한 이벤트를 Stuck으로 간주하여 재처리
     *
     * ## 배경
     * - Kafka 전송 후 markAsPublished 실패 시 SENDING 상태로 방치
     * - 서버 다운 시 SENDING 상태로 남을 수 있음
     * - 이 임계값 후 자동 복구
     *
     * ## 트레이드오프
     * - 짧게 설정: 빠른 복구, 중복 발행 위험 증가
     * - 길게 설정: 느린 복구, 중복 발행 위험 감소
     *
     * ## 중복 발행 대응
     * - FDS 컨슈머에서 event_id 기반 멱등성 보장 필수
     *
     * 권장값: 120초 (2분)
     * 이전값: 600초 (10분) - 너무 길어서 단축
     */
    val stuckThresholdSeconds: Long = 120,

    /**
     * 느린 처리 경고 임계값 (밀리초)
     *
     * 배치 처리 시간이 이 값 초과 시 경고 로그 출력
     * 성능 모니터링 및 병목 지점 파악용
     *
     * ## 느린 처리의 원인
     * - DB 성능 저하 (슬로우 쿼리, 락 대기)
     * - Kafka 성능 저하 (브로커 과부하, 네트워크 지연)
     * - 애플리케이션 문제 (GC, 스레드 풀 포화)
     */
    val slowProcessingThresholdMs: Long = 3000
)
