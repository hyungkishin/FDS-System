package io.github.hyungkishin.transentia.relay.partitioning

import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import io.github.hyungkishin.transentia.relay.TransferOutboxRelay
import io.github.hyungkishin.transentia.relay.component.EventBatchProcessor
import io.github.hyungkishin.transentia.relay.component.RetryPolicyHandler
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import io.github.hyungkishin.transentia.relay.model.ProcessingResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.concurrent.ExecutorService

/**
 * Phase 1: 파티셔닝 로직 단위 테스트
 * 
 * 검증 항목:
 * 1. 각 인스턴스가 환경변수로부터 올바른 파티션 정보를 읽는가?
 * 2. Repository의 claimBatch 메서드에 올바른 파티션 파라미터를 전달하는가?
 * 3. 파티션 설정이 없을 때 기본값(단일 인스턴스 모드)으로 동작하는가?
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("Phase 1: Relay 파티셔닝 로직 테스트")
class TransferOutboxRelayPartitioningTest {

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

    @BeforeEach
    fun setUp() {
        whenever(config.batchSize).thenReturn(100)
        whenever(config.timeoutSeconds).thenReturn(30L)
        whenever(config.slowProcessingThresholdMs).thenReturn(3000L)  // 3초
        
        // EventBatchProcessor mock 기본 설정
        whenever(eventBatchProcessor.processBatch(any(), any(), any(), any()))
            .thenReturn(
                ProcessingResult(
                    successIds = emptyList(),
                    failedEvents = emptyList()
                )
            )
    }

    @Test
    @DisplayName("인스턴스 0: 파티션 0의 이벤트만 조회")
    fun `instance 0 should only query partition 0`() {
        // Given - 인스턴스 0 설정
        whenever(config.instanceId).thenReturn(0)
        whenever(config.totalInstances).thenReturn(3)
        
        val relay = TransferOutboxRelay(
            outboxRepository = outboxRepository,
            eventBatchProcessor = eventBatchProcessor,
            retryPolicyHandler = retryPolicyHandler,
            config = config,
            executorService = executorService,
            topicName = "transfer-transaction-events"
        )

        val expectedBatch = listOf(
            createMockClaimedRow(3L),   // eventId % 3 == 0 (파티션 0)
            createMockClaimedRow(6L)    // eventId % 3 == 0 (파티션 0)
        )
        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any()))
            .thenReturn(expectedBatch)

        // When
        relay.run()

        // Then - Repository가 올바른 파티션 정보로 호출되었는지 검증
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(0),
            totalPartitions = eq(3),
            limit = eq(100),
            now = any()
        )
    }

    @Test
    @DisplayName("인스턴스 1: 파티션 1의 이벤트만 조회")
    fun `instance 1 should only query partition 1`() {
        // Given - 인스턴스 1 설정
        whenever(config.instanceId).thenReturn(1)
        whenever(config.totalInstances).thenReturn(3)
        
        val relay = TransferOutboxRelay(
            outboxRepository = outboxRepository,
            eventBatchProcessor = eventBatchProcessor,
            retryPolicyHandler = retryPolicyHandler,
            config = config,
            executorService = executorService,
            topicName = "transfer-events"
        )

        val expectedBatch = listOf(
            createMockClaimedRow(1L),  // eventId % 3 == 1 (파티션 1)
            createMockClaimedRow(4L)   // eventId % 3 == 1 (파티션 1)
        )
        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any()))
            .thenReturn(expectedBatch)

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(1),
            totalPartitions = eq(3),
            limit = eq(100),
            now = any()
        )
    }

    @Test
    @DisplayName("인스턴스 2: 파티션 2의 이벤트만 조회")
    fun `instance 2 should only query partition 2`() {
        // Given - 인스턴스 2 설정
        whenever(config.instanceId).thenReturn(2)
        whenever(config.totalInstances).thenReturn(3)
        
        val relay = TransferOutboxRelay(
            outboxRepository = outboxRepository,
            eventBatchProcessor = eventBatchProcessor,
            retryPolicyHandler = retryPolicyHandler,
            config = config,
            executorService = executorService,
            topicName = "transfer-events"
        )

        val expectedBatch = listOf(
            createMockClaimedRow(2L),  // eventId % 3 == 2 (파티션 2)
            createMockClaimedRow(5L)   // eventId % 3 == 2 (파티션 2)
        )
        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any()))
            .thenReturn(expectedBatch)

        // When
        relay.run()

        // Then
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(2),
            totalPartitions = eq(3),
            limit = eq(100),
            now = any()
        )
    }

    @Test
    @DisplayName("파티션 설정 없을 때: 단일 인스턴스 모드로 동작")
    fun `should work in single instance mode when partition config is not set`() {
        // Given - 단일 인스턴스 모드 (기본값)
        whenever(config.instanceId).thenReturn(0)
        whenever(config.totalInstances).thenReturn(1)
        
        val relay = TransferOutboxRelay(
            outboxRepository = outboxRepository,
            eventBatchProcessor = eventBatchProcessor,
            retryPolicyHandler = retryPolicyHandler,
            config = config,
            executorService = executorService,
            topicName = "transfer-events"
        )

        val expectedBatch = listOf(
            createMockClaimedRow(1L),
            createMockClaimedRow(2L),
            createMockClaimedRow(3L)
        )
        whenever(outboxRepository.claimBatchByPartition(any(), any(), any(), any()))
            .thenReturn(expectedBatch)

        // When
        relay.run()

        // Then - 파티션 없이 호출
        verify(outboxRepository).claimBatchByPartition(
            partition = eq(0),
            totalPartitions = eq(1),
            limit = eq(100),
            now = any()
        )
    }

    private fun createMockClaimedRow(eventId: Long): ClaimedRow {
        return ClaimedRow(
            eventId = eventId,
            aggregateId = "transaction-$eventId",
            payload = """{"transactionId": $eventId}""",
            headers = "{}"
        )
    }
}
