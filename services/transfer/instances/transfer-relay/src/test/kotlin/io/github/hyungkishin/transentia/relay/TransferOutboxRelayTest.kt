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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.time.Instant
import java.util.concurrent.ExecutorService

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
        // 기본 설정값만 설정 - 필요한 테스트에서 개별적으로 추가 설정
        whenever(config.batchSize).thenReturn(100)
        whenever(config.instanceId).thenReturn(0)
        whenever(config.totalInstances).thenReturn(1)

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
        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(emptyList())

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(0),
            totalPartitions = eq(1),
            limit = eq(100),
            now = any()
        )
        verifyNoInteractions(eventBatchProcessor)
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

        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(batch)
        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(successResult)

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(0),
            totalPartitions = eq(1),
            limit = eq(100),
            now = any()
        )
        verify(eventBatchProcessor).processBatch(any(), any(), any(), any())
        verify(outboxRepository).markAsPublished(eq(listOf(1L, 2L)), any())
        verifyNoInteractions(retryPolicyHandler)
    }

    @Test
    fun `배치 처리 실패시 백오프 적용`() {
        // Given
        whenever(config.timeoutSeconds).thenReturn(30L) // 이 테스트에서만 필요
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

        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(batch)
        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(failedResult)
        whenever(retryPolicyHandler.calculateBackoff(1)).thenReturn(5000L)

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(0),
            totalPartitions = eq(1),
            limit = eq(100),
            now = any()
        )
        verify(eventBatchProcessor).processBatch(any(), any(), any(), any())
        verify(retryPolicyHandler).calculateBackoff(eq(1))
        verify(outboxRepository).markFailedWithBackoff(eq(1L), eq("Kafka connection failed"), eq(5000L), any())
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

        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(batch)
        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(mixedResult)
        whenever(retryPolicyHandler.calculateBackoff(2)).thenReturn(10000L)

        // When
        relay.run()

        // Then
        verify(outboxRepository).markAsPublished(eq(listOf(1L)), any())
        verify(retryPolicyHandler).calculateBackoff(eq(2))
        verify(outboxRepository).markFailedWithBackoff(eq(2L), eq("Serialization failed"), eq(10000L), any())
    }

    @Test
    fun `처리 중 예외 발생시 안전하게 처리`() {
        // Given
        val batch = listOf(createMockClaimedRow(1L))
        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(batch)
        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenThrow(RuntimeException("Unexpected error"))

        // When & Then
        // 예외가 발생해도 relay.run()이 안전하게 처리되어야 함
        relay.run()

        // 배치 조회는 성공했어야 함
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(0),
            totalPartitions = eq(1),
            limit = eq(100),
            now = any()
        )
        verify(eventBatchProcessor).processBatch(any(), any(), any(), any())
    }

    private fun createMockClaimedRow(eventId: Long): ClaimedRow {
        return ClaimedRow(
            eventId = eventId,
            aggregateId = "transaction-$eventId",
            payload = """{"transactionId": $eventId, "status": "COMPLETED"}""",
            headers = "{}"
        )
    }
}