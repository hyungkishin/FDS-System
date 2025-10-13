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
 * Outbox 패턴 Relay 서버 (파티셔닝 지원)
 *
 * ## 역할
 * Outbox 테이블에 저장된 이벤트를 주기적으로 폴링하여 Kafka로 전송한다.
 * 이를 통해 송금 트랜잭션과 이벤트 발행의 원자성을 보장한다.
 *
 * ## 핵심 설계
 * 1. **파티셔닝**
 *    - 여러 인스턴스가 동시 실행 시 각자 다른 이벤트 처리
 *    - MOD(event_id, totalInstances) = instanceId
 *    - 락 경합 감소, 처리량 증가
 *
 * 2. **백오프 전략**
 *    - 실패 시 지수 백오프로 재시도 간격 증가
 *    - 일시적 장애(Kafka 다운 등)에 효과적 대응
 *    - 무의미한 재시도 방지로 리소스 절약
 *
 * 3. **자원 효율**
 *    - 연속으로 빈 배치 발생 시 대기 시간 증가
 *    - 이벤트 없을 때 DB 부하 감소
 *
 * ## 처리 흐름
 * ```
 * @Scheduled 실행 (1초마다)
 *   ↓
 * claimBatchByPartition (자기 파티션만 조회)
 *   ↓
 * EventBatchProcessor (병렬 처리)
 *   ↓
 * Kafka 전송
 *   ↓
 * 성공: markAsPublished
 * 실패: markFailedWithBackoff (재시도 예약)
 * ```
 *
 * ## 인스턴스 확장
 * ```
 * 평시 (200 TPS):
 *   - 3대 운영
 *   - 각 67 TPS 처리
 *   - 부하: 14%
 *
 * 피크 (2000 TPS):
 *   - 7대로 자동 확장 (Auto Scaling)
 *   - 각 286 TPS 처리
 *   - 부하: 61%
 * ```
 *
 * @see EventBatchProcessor 실제 Kafka 전송 처리
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
     * - fixedDelay: 이전 실행 완료 후 1초 대기 (1000ms)
     * - initialDelay: 애플리케이션 시작 후 5초 대기 (5000ms)
     *
     * ## 파티셔닝 동작
     * ```
     * Instance 0: MOD(event_id, 3) = 0 → 1, 4, 7, 10, 13...
     * Instance 1: MOD(event_id, 3) = 1 → 2, 5, 8, 11, 14...
     * Instance 2: MOD(event_id, 3) = 2 → 3, 6, 9, 12, 15...
     * ```
     *
     * ## 처리 단계
     * 1. 파티션 기반 배치 조회 (500건)
     * 2. 빈 배치면 백오프 처리 후 종료
     * 3. EventBatchProcessor로 병렬 처리
     * 4. 성공/실패 결과 처리
     * 5. 성능 모니터링
     *
     * ## 예외 처리
     * - 모든 예외 catch하여 다음 사이클 정상 실행 보장
     * - 로그만 남기고 애플리케이션 중단 방지
     */
    @Scheduled(
        fixedDelayString = "\${app.outbox.relay.fixedDelayMs:1000}",
        initialDelayString = "\${app.outbox.relay.initialDelayMs:2000}"
    )
    fun run() {
        /**
         * TODO : thread pool 을 만들어서 mod 연산에 필요한 대역을 가져도 될것같다.
         * - 성능치를 끌어올리려 하면, multiThread 를 띄우면 될 것 같다.
         * - 1. producer 성능 up 을 위해 멀티 instance 를 하였는데, ThreadPool 로 풀어 내는 방식 ( Todo-1 )
         * - 2. Spring Batch 로 구현 ( Todo-2 )
         */
        try {
            val startTime = System.currentTimeMillis()
            val now = Instant.now()  // 실행 시점 시간 (일관성 보장)

            // 파티션 기반 배치 조회
            val batch = outboxRepository.claimBatchByPartition(
                partition = config.instanceId,
                totalPartitions = config.totalInstances,
                limit = config.batchSize, // 조회 사이즈
                now = now
            )

            // 빈 배치 처리
            if (batch.isEmpty()) {
                handleEmptyBatch()
                return
            }

            // 카운터 리셋 (이벤트 발견)
            consecutiveEmptyCount = 0

            log.debug(
                "[Instance-{}] Processing {} events (partition {}/{})",
                config.instanceId,
                batch.size,
                config.instanceId,
                config.totalInstances
            )

            // 배치 처리 (병렬 Kafka 전송)
            val result = eventBatchProcessor.processBatch(
                batch = batch,
                topicName = topicName,
                timeoutSeconds = config.timeoutSeconds
            )

            val processingTime = System.currentTimeMillis() - startTime

            // 성공 이벤트 처리
            if (result.successIds.isNotEmpty()) {
                outboxRepository.markAsPublished(result.successIds, now)
                _processedEventCount.addAndGet(result.successIds.size)  // atomic 증가
                log.info(
                    "[Instance-{}] Published {} events ({}% success) in {}ms",
                    config.instanceId,
                    result.successIds.size,
                    "%.1f".format(result.successRate * 100),
                    processingTime
                )
            }

            /**
             * end
             */

            // 실패 이벤트 처리 (백오프 적용)
            handleFailedEvents(result.failedEvents, now)

            // 성능 모니터링
            monitorPerformance(processingTime, result.totalProcessed)

        } catch (e: Exception) {
            log.error("[Instance-{}] Relay batch processing failed", config.instanceId, e)
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
     *   - 초당 3회 DB 조회 (3개 인스턴스)
     *   - 시간당 10,800회 조회
     *
     * After (백오프 적용):
     *   - 초당 1회 DB 조회 (3초마다)
     *   - 시간당 3,600회 조회
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
            log.debug(
                "[Instance-{}] No events for {} cycles, sleeping 3s...",
                config.instanceId,
                consecutiveEmptyCount
            )
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
     *   - 1차: 2초 후 재시도
     *   - 2차: 4초 후 재시도
     *   - 3차: 8초 후 재시도
     *   - 4차: 16초 후 재시도
     *   - 5차: 32초 후 재시도
     *   - 총 5회만 시도
     *   - 효율적!
     * ```
     *
     * ## 재시도 패턴 (지수 백오프)
     * ```
     * attempt_count | backoff | next_retry_at
     * --------------|---------|------------------
     * 1             | 2초     | now + 2초
     * 2             | 4초     | now + 4초
     * 3             | 8초     | now + 8초
     * 4             | 16초    | now + 16초
     * 5             | 32초    | now + 32초
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

        log.warn("[Instance-{}] Failed to publish {} events", config.instanceId, failedEvents.size)

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
                "[Instance-{}] Event {} will retry in {}ms (attempt {})",
                config.instanceId,
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
                "[Instance-{}] Slow batch processing: {}ms for {} events (threshold: {}ms)",
                config.instanceId,
                processingTime,
                totalProcessed,
                config.slowProcessingThresholdMs
            )
        }
    }

    /**
     * 애플리케이션 종료 시 정리 작업
     *
     * ## Graceful Shutdown
     * 1. 새로운 작업 수락 중단 (shutdown)
     * 2. 진행 중인 작업 완료 대기 (30초)
     * 3. 타임아웃 시 강제 종료 (shutdownNow)
     *
     * ## 필요한 이유 ?
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
     * ## 타임아웃 에 대해서
     * - 30초: 정상 종료 대기 시간
     * - 1초: 강제 종료 후 재확인 시간
     */
    @PreDestroy
    fun cleanup() {
        log.info("[Instance-{}] Shutting down outbox relay executor service", config.instanceId)
        executorService.shutdown()

        try {
            // 30초 동안 정상 종료 대기
            if (!executorService.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn(
                    "[Instance-{}] Executor did not terminate gracefully, forcing shutdown",
                    config.instanceId
                )
                executorService.shutdownNow()

                // 강제 종료 후 1초 대기
                if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error(
                        "[Instance-{}] Executor did not terminate after forced shutdown",
                        config.instanceId
                    )
                }
            }
            
            log.info("[Instance-{}] Executor service terminated successfully", config.instanceId)
        } catch (e: InterruptedException) {
            log.warn("[Instance-{}] Interrupted while waiting for executor termination", config.instanceId)
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
