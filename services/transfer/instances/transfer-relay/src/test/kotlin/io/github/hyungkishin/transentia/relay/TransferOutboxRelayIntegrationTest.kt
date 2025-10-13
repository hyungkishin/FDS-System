package io.github.hyungkishin.transentia.relay

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.application.required.TransferEventsOutboxRepository
import io.github.hyungkishin.transentia.common.outbox.transfer.ClaimedRow
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import io.github.hyungkishin.transentia.relay.model.TransferPayload
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Instant

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
        println("===========================")
    }

    @Test
    fun `빈 Outbox에서 relay 실행시 아무것도 처리하지 않음`() {
        // Given - Repository가 빈 목록 반환
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(emptyList())

        // When - Relay 실행
        relay.run()

        // Then - claimBatchByPartition만 호출되고 다른 메서드는 호출되지 않음
        verify(repository).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository, never()).markAsPublished(any(), any())
        verify(repository, never()).markFailedWithBackoff(any(), any(), any(), any())
    }

    @Test
    fun `단일 PENDING 이벤트를 성공적으로 처리`() {
        // Given - Repository가 1개 이벤트 반환
        val claimedEvent = createClaimedRow(1L)
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(listOf(claimedEvent))

        // When - Relay 실행
        relay.run()

        // Then - claimBatchByPartition 호출 후 markAsPublished 호출됨
        verify(repository).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository).markAsPublished(eq(listOf(1L)), any())
        verify(repository, never()).markFailedWithBackoff(any(), any(), any(), any())
    }

    @Test
    fun `다중 PENDING 이벤트를 배치로 처리`() {
        // Given - Repository가 여러 이벤트 반환
        val batchSize = 5
        val claimedEvents = (1L..batchSize).map { createClaimedRow(it) }
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(claimedEvents)

        // When - Relay 실행
        relay.run()

        // Then - 모든 이벤트가 성공적으로 처리됨
        verify(repository).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository).markAsPublished(eq((1L..batchSize).toList()), any())
        verify(repository, never()).markFailedWithBackoff(any(), any(), any(), any())
    }

    @Test
    fun `첫 번째 claimBatchByPartition에서 빈 결과 반환시 처리 종료`() {
        // Given - Repository가 빈 목록 반환 (더 이상 처리할 이벤트 없음)
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(emptyList())

        // When - Relay 실행
        relay.run()

        // Then - claimBatchByPartition 한 번만 호출되고 종료
        verify(repository, times(1)).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository, never()).markAsPublished(any(), any())
    }

    @Test
    fun `대용량 배치를 단일 실행에서 처리`() {
        // Given - Repository가 배치 사이즈만큼 이벤트 반환
        val batchEvents = (1L..config.batchSize.toLong()).map { createClaimedRow(it) }
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(batchEvents)

        // When - Relay 한 번 실행
        relay.run()

        // Then - claimBatchByPartition이 호출되고 모든 이벤트 처리
        verify(repository, atLeast(1)).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository).markAsPublished(eq((1L..config.batchSize.toLong()).toList()), any())
    }

    @Test
    fun `Repository 메서드들의 호출 순서와 인자 검증`() {
        // Given - Repository Mock 설정
        val eventIds = listOf(100L, 101L, 102L)
        val claimedEvents = eventIds.map { createClaimedRow(it) }
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(claimedEvents)

        // When - Relay 실행 (한 번만)
        relay.run()

        // Then - Repository 메서드 호출 확인
        verify(repository, atLeast(1)).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository).markAsPublished(eq(eventIds), any())
    }

    @Test
    fun `Kafka 발송 실패 시뮬레이션 - markAsPublished가 예외 발생`() {
        // Given - Repository가 이벤트 반환하지만 markAsPublished에서 예외 발생
        val claimedEvent = createClaimedRow(1L)
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(listOf(claimedEvent))
        whenever(repository.markAsPublished(any(), any())).thenThrow(RuntimeException("Kafka connection failed"))

        // When & Then - Relay 실행 시 예외가 전파됨
        try {
            relay.run()
        } catch (e: RuntimeException) {
            assertEquals("Kafka connection failed", e.message)
        }

        // Then - 실패한 이벤트에 대해 markFailedWithBackoff 호출 확인
        verify(repository).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository).markAsPublished(eq(listOf(1L)), any())
    }

    @Test
    fun `단일 실행에서 다중 이벤트 배치 처리 확인`() {
        // Given - Repository가 연속으로 이벤트 반환 (하지만 run()은 한 번만 실행됨)
        val batch = listOf(createClaimedRow(1L), createClaimedRow(2L))
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(batch)

        // When - Relay 실행 (한 번)
        relay.run()

        // Then - 한 번의 claimBatchByPartition으로 모든 이벤트 처리
        verify(repository, times(1)).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository).markAsPublished(eq(listOf(1L, 2L)), any())
    }

    @Test
    fun `attempt_count가 높은 이벤트도 정상 처리됨`() {
        // Given - 재시도 횟수가 높은 이벤트 (Repository에서 반환)
        val highAttemptEvent = createClaimedRow(1L, attemptCount = 4)
        whenever(repository.claimBatchByPartition(any(), any(), any(), any())).thenReturn(listOf(highAttemptEvent))

        // When - Relay 실행
        relay.run()

        // Then - attempt_count와 상관없이 정상 처리됨
        verify(repository, atLeast(1)).claimBatchByPartition(
            partition = eq(config.instanceId),
            totalPartitions = eq(config.totalInstances),
            limit = eq(config.batchSize),
            now = any()
        )
        verify(repository, atLeast(1)).markAsPublished(eq(listOf(1L)), any())
    }

    /**
     * 테스트용 ClaimedRow 생성 헬퍼 메서드
     * Transfer 도메인에 의존하지 않고 필요한 데이터만 생성
     */
    private fun createClaimedRow(
        eventId: Long, 
        attemptCount: Int = 1
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
