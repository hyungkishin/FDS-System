package io.github.hyungkishin.transentia.relay

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import io.github.hyungkishin.transentia.relay.model.TransferPayload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Instant

/**
 * 멀티 스레드 기반 단일 인스턴스 통합 테스트
 */
@SpringBootTest(properties = ["spring.task.scheduling.enabled=false"])
class TransferOutboxRelayIntegrationTest {

    @Autowired
    private lateinit var relay: TransferOutboxRelay

    @MockBean
    private lateinit var repository: TransferEventsOutboxRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var config: OutboxRelayConfig

    @BeforeEach
    fun setUp() {
        reset(repository)
        println("=== Relay 통합 테스트 설정 ===")
        println("배치 크기: ${config.batchSize}")
        println("스레드 풀: ${config.threadPoolSize}")
        println("Stuck 임계값: ${config.stuckThresholdSeconds}초")
        println("===========================")
    }

    @Test
    fun `빈 Outbox에서 relay 실행시 아무것도 처리하지 않음 - claimBatch만 호출되고 다른 메서드는 호출되지 않아야 한다`() {
        // Given
        whenever(repository.claimBatch(any(), any(), any()))
            .thenReturn(emptyList())

        // When
        relay.run()

        // Then
        verify(repository).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )

        verify(repository, never())
            .markAsPublished(any(), any())

        verify(repository, never())
            .markFailedWithBackoff(any(), any(), any(), any())
    }

    @Test
    fun `단일 PENDING 이벤트를 성공적으로 처리 - 재시도 로직으로 최대 3회 시도되어야 한다`() {
        // Given
        val claimedEvent = createClaimedRow(1L)
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(listOf(claimedEvent))

        // When
        relay.run()

        // Then
        verify(repository).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )

        verify(repository, atLeast(1)).markAsPublished(eq(listOf(1L)), any())
        verify(repository, never()).markFailedWithBackoff(any(), any(), any(), any())
    }

    @Test
    fun `다중 PENDING 이벤트를 배치로 처리 - 모든 이벤트가 성공적으로 처리되어야 한다`() {
        // Given
        val batchSize = 5
        val claimedEvents = (1L..batchSize).map { createClaimedRow(it) }
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(claimedEvents)

        // When
        relay.run()

        // Then
        verify(repository).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )
        verify(repository, atLeast(1)).markAsPublished(eq((1L..batchSize).toList()), any())
        verify(repository, never()).markFailedWithBackoff(any(), any(), any(), any())
    }

    @Test
    fun `첫 번째 claimBatch에서 빈 결과 반환시 처리 종료 - claimBatch 한 번만 호출되고 종료되어야 한다`() {
        // Given
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(emptyList())

        // When
        relay.run()

        // Then
        verify(repository, times(1)).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )
        verify(repository, never()).markAsPublished(any(), any())
    }

    @Test
    fun `대용량 배치를 단일 실행에서 처리 - claimBatch이 호출되고 모든 이벤트 처리되어야 한다`() {
        // Given
        val batchEvents = (1L..config.batchSize.toLong()).map { createClaimedRow(it) }
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(batchEvents)

        // When
        relay.run()

        // Then
        verify(repository, atLeast(1)).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )
        verify(repository, atLeast(1)).markAsPublished(eq((1L..config.batchSize.toLong()).toList()), any())
    }

    @Test
    fun `Repository 메서드들의 호출 순서와 인자 검증`() {
        // Given
        val eventIds = listOf(100L, 101L, 102L)
        val claimedEvents = eventIds.map { createClaimedRow(it) }
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(claimedEvents)

        // When
        relay.run()

        // Then
        verify(repository, atLeast(1)).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )
        verify(repository, atLeast(1)).markAsPublished(eq(eventIds), any())
    }

    @Test
    fun `markAsPublished 실패 시 재시도 로직 - 재시도로 2번 호출 (1번 실패 + 1번 성공) 해야 한다`() {
        // Given
        val claimedEvent = createClaimedRow(1L)
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(listOf(claimedEvent))

        // 첫 번째 실패, 두 번째 성공
        whenever(repository.markAsPublished(any(), any()))
            .thenThrow(RuntimeException("Temporary DB connection failed"))
            .thenAnswer { }

        // When
        relay.run()

        // Then
        verify(repository, times(2)).markAsPublished(eq(listOf(1L)), any())
    }

    @Test
    fun `단일 실행에서 다중 이벤트 배치 처리 확인 - 한 번의 claimBatch으로 모든 이벤트 처리 되어야 한다`() {
        // Given
        val batch = listOf(createClaimedRow(1L), createClaimedRow(2L))
        whenever(repository.claimBatch(any(), any(), any()))
            .thenReturn(batch)

        // When
        relay.run()

        // Then
        verify(repository, times(1)).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )
        verify(repository, atLeast(1))
            .markAsPublished(eq(listOf(1L, 2L)), any())
    }

    @Test
    fun `attempt_count가 높은 이벤트도 정상 처리됨 - attempt_count와 상관없이 정상 처리됨`() {
        // Given
        val highAttemptEvent = createClaimedRow(1L, attemptCount = 4)
        whenever(repository.claimBatch(any(), any(), any())).thenReturn(listOf(highAttemptEvent))

        // When
        relay.run()

        // Then
        verify(repository, atLeast(1)).claimBatch(
            limit = eq(config.batchSize),
            now = any(),
            stuckThresholdSeconds = eq(config.stuckThresholdSeconds)
        )
        verify(repository, atLeast(1))
            .markAsPublished(eq(listOf(1L)), any())
    }

    @Test
    fun `멀티 스레드 처리 확인 - 대량 이벤트 (멀티 스레드로 빠르게 처리되어야 한다)`() {
        // Given
        val largeBatch = (1L..100L).map { createClaimedRow(it) }
        whenever(repository.claimBatch(any(), any(), any()))
            .thenReturn(largeBatch)

        // When
        val startTime = System.currentTimeMillis()
        relay.run()
        val endTime = System.currentTimeMillis()

        // Then
        println("100개 이벤트 처리 시간: ${endTime - startTime}ms")

        verify(repository, atLeast(1))
            .claimBatch(any(), any(), any())

        verify(repository, atLeast(1))
            .markAsPublished(eq(largeBatch.map { it.eventId }), any())
    }

    /**
     * 테스트용 ClaimedRow 생성 헬퍼 메서드 입니다.
     * Transfer 도메인에 의존하지 않고 필요한 데이터만 생성합니다.
     */
    private fun createClaimedRow(
        eventId: Long,
        attemptCount: Int = 0
    ): ClaimedRow {
        return ClaimedRow(
            eventId = eventId,
            aggregateId = "transaction-$eventId",
            payload = createTransferPayload(eventId),
            headers = createEventHeaders(eventId),
            attemptCount = attemptCount
        )
    }

    private fun createTransferPayload(transactionId: Long): String {
        val payload = TransferPayload(
            transactionId = transactionId,
            senderId = 10000 + transactionId,
            receiverUserId = 20000 + transactionId,
            amount = (100000..10000000).random().toLong(),
            status = "COMPLETED",
            occurredAt = Instant.now().toEpochMilli()
        )
        return objectMapper.writeValueAsString(payload)
    }

    private fun createEventHeaders(eventId: Long): String {
        return objectMapper.writeValueAsString(
            mapOf(
                "eventType" to "TRANSFER_COMPLETED",
                "eventVersion" to "v1",
                "traceId" to "test-trace-$eventId-${System.currentTimeMillis()}",
                "producer" to "transfer-api",
                "contentType" to "application/json"
            )
        )
    }

}
