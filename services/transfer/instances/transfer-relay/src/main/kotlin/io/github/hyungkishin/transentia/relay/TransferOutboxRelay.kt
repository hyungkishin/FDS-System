package io.github.hyungkishin.transentia.relay

import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.relay.component.EventBatchProcessor
import io.github.hyungkishin.transentia.relay.component.RetryPolicyHandler
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import io.github.hyungkishin.transentia.relay.model.ProcessingResult
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

/**
 * Outbox 패턴 Relay 서버 (멀티 스레드 기반)
 *
 * ## 역할
 * Outbox 테이블에 저장된 이벤트를 주기적으로 폴링하여 Kafka로 전송한다.
 * 이를 통해 송금 트랜잭션과 이벤트 발행의 원자성을 보장한다.
 *
 * ### 1. **단일 인스턴스 + 멀티 스레드**
 * - MOD 파티셔닝 제거 (인스턴스 확장 시 복잡도 제거)
 * - 멀티 스레드로 처리량 확보
 * - 단순하고 안정적인 아키텍처
 *
 * ### 2. **동시성 제어**
 * - **DB 레벨**: SKIP LOCKED (행 단위 락)
 * - **애플리케이션 레벨**: @Scheduled fixedDelay (순차 실행)
 * - **처리 레벨**: ExecutorService (병렬 Kafka 전송)
 *
 * ### 3. **장애 복구**
 * - **Stuck SENDING**: 2분 후 자동 재시도
 * - **백오프 전략**: 지수 백오프로 일시적 장애 대응
 * - **재시도 로직**: markAsPublished 실패 시 3회 재시도
 *
 * ### 4. **성능 목표**
 * ```
 * 평시 (200 TPS):
 *   - 단일 인스턴스
 *   - 멀티 스레드 (8개)
 *   - 배치 크기: 500
 *   - 처리 시간: ~50ms
 *   - 여유도: 충분
 *
 * 피크 (2000 TPS):
 *   - threadPoolSize 증가 (8 -> 16)
 *   - 또는 batchSize 증가 (500 -> 1000)
 * ```
 *
 * ## 엣지케이스 대응
 * 1. **Kafka 성공 + DB 실패**: Stuck SENDING 복구 (2분 후)
 * 2. **서버 다운**: Stuck SENDING 복구 (2분 후)
 * 3. TODO: **중복 발행**: FDS 컨슈머에서 멱등성 보장 (event_id 체크 할것.)
 *
 * @see EventBatchProcessor 멀티 스레드 Kafka 전송
 * @see RetryPolicyHandler 백오프 정책 계산
 */
@Component
class TransferOutboxRelay(
    private val outboxRepository: TransferEventsOutboxRepository,
    private val eventBatchProcessor: EventBatchProcessor,
    private val retryPolicyHandler: RetryPolicyHandler,
    private val config: OutboxRelayConfig,
    @Qualifier("outboxExecutorService") private val executorService: ExecutorService,
    @Value("\${app.kafka.topics.transfer-events}") private val topicName: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 연속으로 빈 배치가 발생한 횟수
     *
     * 이벤트가 없을 때 불필요한 DB 조회를 줄이기 위한 카운터
     * 3회 이상 연속으로 비면 3초 대기 (백오프)
     */
    private var consecutiveEmptyCount = 0

    /**
     * 성능 테스트용: 이 인스턴스가 처리한 총 이벤트 수
     * 
     * 프로덕션에서는 사용하지 않으며, 성능 테스트에서만 사용됩니다.
     * AtomicInteger로 thread-safety 보장
     */
    private val _processedEventCount = AtomicInteger(0)

    /**
     * 처리한 이벤트 수 조회 (테스트용)
     */
    fun getProcessedEventCount(): Int = _processedEventCount.get()

    /**
     * 성능 테스트용: 카운터 리셋
     */
    fun resetCounter() {
        _processedEventCount.set(0)
    }

    /**
     * Outbox 이벤트를 주기적으로 처리하는 메인 루프
     *
     * ## 실행 주기
     * - fixedDelay: 이전 실행 완료 후 1초 대기
     * - initialDelay: 애플리케이션 시작 후 5초 대기
     * - 순차 실행 보장 (오버랩 없음)
     *
     * ## 처리 단계
     * 1. 배치 조회 (SKIP LOCKED, 500건)
     * 2. 빈 배치면 백오프 처리 후 종료
     * 3. EventBatchProcessor로 멀티 스레드 병렬 처리
     * 4. 성공/실패 결과 처리 (재시도 로직 포함)
     * 5. 성능 모니터링
     *
     * ## 예외 처리
     * - 모든 예외 catch하여 다음 사이클 정상 실행 보장
     * - 로그만 남기고 애플리케이션 중단 방지
     */
    @Scheduled(
        fixedDelayString = "\${app.outbox.relay.fixedDelayMs:1000}",
        initialDelayString = "\${app.outbox.relay.initialDelayMs:5000}"
    )
    fun run() {
        try {
            val startTime = System.currentTimeMillis()
            val now = Instant.now()

            // 배치 조회
            val batch = outboxRepository.claimBatch(
                limit = config.batchSize,
                now = now,
                stuckThresholdSeconds = config.stuckThresholdSeconds
            )

            // 빈 배치 처리
            if (batch.isEmpty()) {
                handleEmptyBatch()
                return
            }

            // 카운터 리셋 (이벤트 발견)
            consecutiveEmptyCount = 0

            log.debug("Processing {} events", batch.size)

            // 배치 처리 (멀티 스레드 병렬 Kafka 전송)
            val result = eventBatchProcessor.processBatch(
                batch = batch,
                topicName = topicName,
                timeoutSeconds = config.timeoutSeconds
            )

            val processingTime = System.currentTimeMillis() - startTime

            // 성공 이벤트 처리 (재시도 로직 포함)
            if (result.successIds.isNotEmpty()) {
                retryOperation(maxAttempts = 3, operationName = "markAsPublished") {
                    outboxRepository.markAsPublished(result.successIds, now)
                }
                
                _processedEventCount.addAndGet(result.successIds.size)
                
                log.info(
                    "Published {} events ({}% success) in {}ms",
                    result.successIds.size,
                    "%.1f".format(result.successRate * 100),
                    processingTime
                )
            }

            // 실패 이벤트 처리 (백오프 적용, 재시도 로직 포함)
            if (result.failedEvents.isNotEmpty()) {
                retryOperation(maxAttempts = 3, operationName = "handleFailedEvents") {
                    handleFailedEvents(result.failedEvents, now)
                }
            }

            // 성능 모니터링
            monitorPerformance(processingTime, result.totalProcessed)

        } catch (e: Exception) {
            log.error("Relay batch processing failed", e)
        }
    }

    /**
     * 빈 배치 처리 (자원 절약 전략)
     *
     * ## 문제
     * 이벤트가 없을 때도 매초 DB 조회하면:
     * - 불필요한 DB 부하
     * - CPU 낭비
     * - 로그 증가
     *
     * ## 해결
     * 연속으로 3회 이상 빈 배치 발생 시 3초 대기
     *
     * ## 효과
     * ```
     * Before (이벤트 없을 때):
     *   - 초당 1회 DB 조회
     *   - 시간당 3,600회 조회
     *
     * After (백오프 적용):
     *   - 3초마다 1회 DB 조회
     *   - 시간당 1,200회 조회
     *   - 67% 감소!
     * ```
     *
     * ## 트레이드오프
     * - 장점: DB 부하 감소, 리소스 절약
     * - 단점: 최초 이벤트 처리 3초 지연 가능 (허용 가능)
     */
    private fun handleEmptyBatch() {
        consecutiveEmptyCount++
        
        if (consecutiveEmptyCount > 3) {
            log.debug("No events for {} cycles, sleeping 3s...", consecutiveEmptyCount)
            Thread.sleep(3000)
        }
    }

    /**
     * 실패한 이벤트들에 백오프 전략 적용
     *
     * ## 백오프(Backoff)란?
     * 실패한 작업을 점점 더 긴 간격으로 재시도하는 전략
     *
     * ## 왜 필요한가?
     * ```
     * Kafka가 5분간 다운된 상황:
     *
     * 백오프 없이:
     *   - 1초마다 재시도 (300회)
     *   - 모두 실패
     *   - 리소스 낭비
     *
     * 백오프 적용:
     *   - 1차: 5초 후 재시도
     *   - 2차: 10초 후 재시도
     *   - 3차: 20초 후 재시도
     *   - 4차: 40초 후 재시도
     *   - 5차: 80초 후 재시도
     *   - 총 5회만 시도
     *   - 효율적!
     * ```
     *
     * ## 재시도 패턴 (지수 백오프)
     * ```
     * attempt_count | backoff | next_retry_at
     * --------------|---------|------------------
     * 1             | 5초     | now + 5초
     * 2             | 10초    | now + 10초
     * 3             | 20초    | now + 20초
     * 4             | 40초    | now + 40초
     * 5             | 80초    | now + 80초
     * 6+            | 포기    | DEAD_LETTER 상태
     * ```
     *
     * ## DEAD_LETTER 상태
     * - 5회 재시도 후에도 실패하면 수동 개입 필요
     * - 자동 재시도 중단
     * - 관리자 알림 (추후 구현 예정)
     * - 수동 재처리 or 삭제
     *
     * @param failedEvents 실패한 이벤트 목록
     * @param now 현재 시간 (백오프 계산 기준)
     */
    private fun handleFailedEvents(failedEvents: List<ProcessingResult.FailedEvent>, now: Instant) {
        if (failedEvents.isEmpty()) return

        log.warn("Failed to publish {} events", failedEvents.size)

        failedEvents.forEach { failed ->
            // 백오프 시간 계산 (지수 + Jitter)
            val backoffMillis = retryPolicyHandler.calculateBackoff(failed.attemptCount)
            
            // DB에 실패 기록 + 재시도 시간 설정
            outboxRepository.markFailedWithBackoff(
                id = failed.eventId,
                cause = failed.error,
                backoffMillis = backoffMillis,
                now = now
            )
            
            log.debug(
                "Event {} will retry in {}ms (attempt {})",
                failed.eventId,
                backoffMillis,
                failed.attemptCount + 1
            )
        }
    }

    /**
     * 성능 모니터링 및 경고
     *
     * ## 목적
     * 배치 처리가 비정상적으로 느릴 때 감지하여 병목 지점 파악
     *
     * ## 느린 처리의 원인
     * 1. DB 성능 저하
     *    - 커넥션 풀 부족
     *    - 슬로우 쿼리
     *    - 락 대기
     *
     * 2. Kafka 성능 저하
     *    - 브로커 과부하
     *    - 네트워크 지연
     *    - 파티션 불균형
     *
     * 3. 애플리케이션 문제
     *    - GC 발생
     *    - 스레드 풀 포화
     *    - 메모리 부족
     *
     * ## 대응
     * - 경고 로그 확인
     * - 메트릭 분석 (Grafana 등)
     * - 원인 파악 후 조치
     *
     * @param processingTime 배치 처리에 소요된 시간 (ms)
     * @param totalProcessed 처리한 이벤트 수
     */
    private fun monitorPerformance(processingTime: Long, totalProcessed: Int) {
        if (processingTime > config.slowProcessingThresholdMs) {
            log.warn(
                "Slow batch processing: {}ms for {} events (threshold: {}ms)",
                processingTime,
                totalProcessed,
                config.slowProcessingThresholdMs
            )
        }
    }

    /**
     * DB 작업에 대한 재시도 로직
     *
     * ## 배경
     * markAsPublished나 handleFailedEvents 실패 시:
     * - Kafka는 이미 전송됨
     * - DB만 업데이트 실패
     * - Stuck SENDING 상태로 방치
     * - 2분 후 중복 발행
     *
     * ## 대응
     * DB 작업 실패 시 즉시 재시도 (3회)
     * - 1차 실패: 100ms 후 재시도
     * - 2차 실패: 200ms 후 재시도
     * - 3차 실패: 예외 발생 (Stuck SENDING 복구로 처리)
     *
     * ## 결과
     * 대부분의 일시적 DB 장애 자동 복구 이후, 중복 발행 감소
     *
     * @param maxAttempts 최대 재시도 횟수
     * @param operationName 작업 이름 (로그용)
     * @param operation 실행할 작업
     * @throws Exception 모든 재시도 실패 시
     */
    private fun <T> retryOperation(
        maxAttempts: Int = 3,
        operationName: String,
        operation: () -> T
    ): T {
        var lastException: Exception? = null
        
        repeat(maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                if (attempt < maxAttempts - 1) {
                    val delayMs = 100L * (attempt + 1)  // 100ms, 200ms
                    log.warn(
                        "{} failed (attempt {}/{}): {}. Retrying in {}ms...",
                        operationName,
                        attempt + 1,
                        maxAttempts,
                        e.message,
                        delayMs
                    )
                    Thread.sleep(delayMs)
                } else {
                    log.error(
                        "{} failed after {} attempts. Will be recovered by Stuck SENDING mechanism.",
                        operationName,
                        maxAttempts,
                        e
                    )
                }
            }
        }
        
        throw lastException!!
    }

    /**
     * 애플리케이션 종료 시 정리 작업
     *
     * ## Graceful Shutdown
     * 1. 새로운 작업 수락 중단 (shutdown)
     * 2. 진행 중인 작업 완료 대기 (30초)
     * 3. 타임아웃 시 강제 종료 (shutdownNow)
     *
     * ## 필요한 이유
     * ```
     * Graceful Shutdown 없이:
     *   - 이벤트 처리 중 종료
     *   - Kafka 전송은 했지만 DB 업데이트 안함
     *   - 재시작 시 중복 발행
     *
     * Graceful Shutdown 적용:
     *   - 진행 중인 이벤트 처리 완료
     *   - DB 업데이트 완료
     *   - 안전한 종료
     * ```
     *
     * ## 타임아웃
     * - 30초: 정상 종료 대기 시간
     * - 1초: 강제 종료 후 재확인 시간
     */
    @PreDestroy
    fun cleanup() {
        log.info("Shutting down outbox relay executor service")
        executorService.shutdown()

        try {
            // 30초 동안 정상 종료 대기
            if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown")
                executorService.shutdownNow()

                // 강제 종료 후 1초 대기
                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Executor did not terminate after forced shutdown")
                }
            }
            
            log.info("Executor service terminated successfully")
        } catch (e: InterruptedException) {
            log.warn("Interrupted while waiting for executor termination")
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
