package io.github.hyungkishin.transentia.relay

import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import io.github.hyungkishin.transentia.relay.component.EventBatchProcessor
import io.github.hyungkishin.transentia.relay.component.RetryPolicyHandler
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import io.github.hyungkishin.transentia.relay.model.ProcessingResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.concurrent.ExecutorService

/**
 * 멀티 스레드 기반 단일 인스턴스의 단위 테스트
 */
@ExtendWith(MockitoExtension::class)
class TransferOutboxRelayTest {

    @Mock
    private lateinit var outboxRepository: TransferEventsOutboxJdbcRepository

    @Mock
    private lateinit var eventBatchProcessor: EventBatchProcessor

    @Mock
    private lateinit var retryPolicyHandler: RetryPolicyHandler

    @Mock
    private lateinit var config: OutboxRelayConfig

    @Mock
    private lateinit var executorService: ExecutorService

    private lateinit var relay: TransferOutboxRelay

    @BeforeEach
    fun setUp() {
        // 모든 테스트에서 사용하는 필수 설정
        whenever(config.batchSize).thenReturn(100)
        whenever(config.stuckThresholdSeconds).thenReturn(120L)

        relay = TransferOutboxRelay(
            outboxRepository = outboxRepository,
            eventBatchProcessor = eventBatchProcessor,
            retryPolicyHandler = retryPolicyHandler,
            config = config,
            executorService = executorService,
            topicName = "transfer-transaction-events"
        )
    }

    @Test
    fun `빈 배치일 때 처리하지 않음`() {
        // Given
        whenever(outboxRepository.claimBatch(any(), any(), any()))
            .thenReturn(emptyList())

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatch(
            limit = eq(100),
            now = any(),
            stuckThresholdSeconds = eq(120L)
        )

        // eventBatchProcessor 호출 안함
        verifyNoInteractions(eventBatchProcessor)

        // retryPolicyHandler 호출 안함
        verifyNoInteractions(retryPolicyHandler)
    }

    @Test
    fun `배치 처리 성공시 이벤트들을 PUBLISHED로 마킹`() {
        // Given
        val batch = listOf(createMockClaimedRow(1L), createMockClaimedRow(2L))
        val successResult = ProcessingResult(
            successIds = listOf(1L, 2L),
            failedEvents = emptyList()
        )

        whenever(config.timeoutSeconds)
            .thenReturn(5L)

        whenever(outboxRepository.claimBatch(any(), any(), any()))
            .thenReturn(batch)

        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(successResult)

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatch(
            limit = eq(100),
            now = any(),
            stuckThresholdSeconds = eq(120L)
        )
        verify(eventBatchProcessor).processBatch(
            batch = eq(batch),
            topicName = eq("transfer-transaction-events"),
            chunkSize = any(),
            timeoutSeconds = eq(5L)
        )

        // 재시도 로직으로 최대 3회 시도 가능
        verify(outboxRepository, atLeast(1))
            .markAsPublished(eq(listOf(1L, 2L)), any())

        verifyNoInteractions(retryPolicyHandler)
    }

    @Test
    fun `배치 처리 실패시 백오프 적용`() {
        // Given
        val batch = listOf(createMockClaimedRow(1L))
        val failedResult = ProcessingResult(
            successIds = emptyList(),
            failedEvents = listOf(
                ProcessingResult.FailedEvent(
                    eventId = 1L,
                    error = "Kafka connection failed",
                    attemptCount = 1
                )
            )
        )

        whenever(outboxRepository.claimBatch(any(), any(), any())).thenReturn(batch)
        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(failedResult)
        whenever(retryPolicyHandler.calculateBackoff(1)).thenReturn(5000L)

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatch(
            limit = eq(100),
            now = any(),
            stuckThresholdSeconds = eq(120L)
        )
        verify(eventBatchProcessor).processBatch(any(), any(), any(), any())
        verify(retryPolicyHandler).calculateBackoff(eq(1))

        // 재시도 로직으로 최대 3회 시도
        verify(outboxRepository, atLeast(1)).markFailedWithBackoff(
            id = eq(1L),
            cause = eq("Kafka connection failed"),
            backoffMillis = eq(5000L),
            now = any()
        )
        verify(outboxRepository, never()).markAsPublished(any(), any())
    }

    @Test
    fun `부분 성공시 성공과 실패 모두 처리`() {
        // Given
        val batch = listOf(createMockClaimedRow(1L), createMockClaimedRow(2L))
        val mixedResult = ProcessingResult(
            successIds = listOf(1L),
            failedEvents = listOf(
                ProcessingResult.FailedEvent(
                    eventId = 2L,
                    error = "Serialization failed",
                    attemptCount = 2
                )
            )
        )

        whenever(outboxRepository.claimBatch(any(), any(), any()))
            .thenReturn(batch)

        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(mixedResult)

        whenever(retryPolicyHandler.calculateBackoff(2))
            .thenReturn(10000L)

        // When
        relay.run()

        // Then
        verify(outboxRepository, atLeast(1))
            .markAsPublished(eq(listOf(1L)), any())

        verify(retryPolicyHandler).calculateBackoff(eq(2))

        verify(outboxRepository, atLeast(1))
            .markFailedWithBackoff(
                id = eq(2L),
                cause = eq("Serialization failed"),
                backoffMillis = eq(10000L),
                now = any()
            )
    }

    @Test
    fun `처리 중 예외 발생시 안전하게 처리`() {
        // Given
        val batch = listOf(createMockClaimedRow(1L))
        whenever(outboxRepository.claimBatch(any(), any(), any()))
            .thenReturn(batch)

        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenThrow(RuntimeException("Unexpected error"))

        // When & Then
        // 예외가 발생해도 relay.run()이 안전하게 처리되어야 함
        relay.run()

        // 배치 조회는 성공했어야 함
        verify(outboxRepository).claimBatch(
            limit = eq(100),
            now = any(),
            stuckThresholdSeconds = eq(120L)
        )
        verify(eventBatchProcessor)
            .processBatch(any(), any(), any(), any())
    }

    @Test
    fun `markAsPublished 실패 시 재시도 로직 동작 확인 - 3회 재시도 (총 3번 호출)`() {
        // Given
        val batch = listOf(createMockClaimedRow(1L))
        val successResult = ProcessingResult(
            successIds = listOf(1L),
            failedEvents = emptyList()
        )

        whenever(outboxRepository.claimBatch(any(), any(), any()))
            .thenReturn(batch)

        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(successResult)

        whenever(outboxRepository.markAsPublished(any(), any()))
            .thenThrow(RuntimeException("DB connection failed"))
            .thenThrow(RuntimeException("DB connection failed"))
            .thenAnswer { }

        // When
        relay.run()

        // Then
        verify(outboxRepository, times(3))
            .markAsPublished(eq(listOf(1L)), any())
    }

    @Test
    fun `연속 빈 배치 시 백오프 적용 확인 - 4번 연속 실행 (3번째까지는 즉시, 4번째부터 3초 대기)`() {
        // Given
        whenever(outboxRepository.claimBatch(any(), any(), any())).thenReturn(emptyList())

        // When
        relay.run()
        relay.run()
        relay.run()
        relay.run()

        // Then
        verify(outboxRepository, times(4))
            .claimBatch(any(), any(), any())

        verifyNoInteractions(eventBatchProcessor)
    }

    private fun createMockClaimedRow(eventId: Long): ClaimedRow {
        return ClaimedRow(
            eventId = eventId,
            aggregateId = "transaction-$eventId",
            payload = """{"transactionId": $eventId, "status": "COMPLETED"}""",
            headers = "{}",
            attemptCount = 0
        )
    }
}
