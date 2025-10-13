package io.github.hyungkishin.transentia.relay.partitioning

import io.github.hyungkishin.transentia.common.event.DomainEvent
import io.github.hyungkishin.transentia.container.event.TransferEvent
import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Instant

/**
 * Phase 1: Repository 파티셔닝 DB 통합 테스트
 *
 * 실제 PostgreSQL DB를 사용하여 파티셔닝 쿼리가 올바르게 동작하는지 검증한다.
 *
 * 검증 항목:
 * 1. MOD(event_id, totalPartitions) = partition 쿼리가 올바른 이벤트만 조회하는가?
 * 2. 각 파티션이 서로 겹치지 않는가?
 * 3. 모든 파티션을 합치면 전체 이벤트가 되는가?
 */
@SpringBootTest(properties = ["spring.task.scheduling.enabled=false"])
@ActiveProfiles("test")
@DisplayName("Phase 1: Repository 파티셔닝 DB 통합 테스트")
class TransferEventsOutboxRepositoryPartitioningIntegrationTest {

    @Autowired
    private lateinit var repository: TransferEventsOutboxJdbcRepository

    @Autowired
    private lateinit var jdbc: NamedParameterJdbcTemplate

    @BeforeEach
    fun setUp() {
        // 테스트 전 outbox 테이블 초기화
        jdbc.update("DELETE FROM transfer_events", emptyMap<String, Any>())
    }

    @Test
    @DisplayName("파티션 0은 MOD 3 = 0인 이벤트만 조회")
    fun `partition 0 should only fetch events with mod 3 equals 0`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..9L, baseTime = fixedTime)

        // When - 파티션 0으로 조회 (같은 시간 사용!)
        val events = repository.claimBatchByPartition(
            partition = 0,
            totalPartitions = 3,
            limit = 100,
            now = fixedTime
        )

        // Then - 3, 6, 9만 조회됨
        val eventIds = events.map { it.eventId }.sorted()
        assertEquals(listOf(3L, 6L, 9L), eventIds)
        
        // 모든 이벤트가 MOD 3 = 0
        eventIds.forEach { id ->
            assertEquals(0L, id % 3, "Event $id should have mod 3 = 0")
        }
    }

    @Test
    @DisplayName("파티션 1은 MOD 3 = 1인 이벤트만 조회")
    fun `partition 1 should only fetch events with mod 3 equals 1`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..9L, baseTime = fixedTime)

        // When - 파티션 1로 조회 (같은 시간 사용!)
        val events = repository.claimBatchByPartition(
            partition = 1,
            totalPartitions = 3,
            limit = 100,
            now = fixedTime
        )

        // Then - 1, 4, 7만 조회됨
        val eventIds = events.map { it.eventId }.sorted()
        assertEquals(listOf(1L, 4L, 7L), eventIds)
        
        eventIds.forEach { id ->
            assertEquals(1L, id % 3, "Event $id should have mod 3 = 1")
        }
    }

    @Test
    @DisplayName("파티션 2는 MOD 3 = 2인 이벤트만 조회")
    fun `partition 2 should only fetch events with mod 3 equals 2`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..9L, baseTime = fixedTime)

        // When - 파티션 2로 조회 (같은 시간 사용!)
        val events = repository.claimBatchByPartition(
            partition = 2,
            totalPartitions = 3,
            limit = 100,
            now = fixedTime
        )

        // Then - 2, 5, 8만 조회됨
        val eventIds = events.map { it.eventId }.sorted()
        assertEquals(listOf(2L, 5L, 8L), eventIds)
        
        eventIds.forEach { id ->
            assertEquals(2L, id % 3, "Event $id should have mod 3 = 2")
        }
    }

    @Test
    @DisplayName("3개 파티션이 중복 없이 모든 이벤트를 나눠서 처리")
    fun `three partitions should cover all events without overlap`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..30L, baseTime = fixedTime)

        // When - 각 파티션으로 조회 (같은 시간 사용!)
        val partition0 = repository.claimBatchByPartition(0, 3, 100, fixedTime).map { it.eventId }
        val partition1 = repository.claimBatchByPartition(1, 3, 100, fixedTime).map { it.eventId }
        val partition2 = repository.claimBatchByPartition(2, 3, 100, fixedTime).map { it.eventId }

        // Then
        // 1. 각 파티션 크기가 동일 (균등 분산)
        assertEquals(10, partition0.size, "Partition 0 should have 10 events")
        assertEquals(10, partition1.size, "Partition 1 should have 10 events")
        assertEquals(10, partition2.size, "Partition 2 should have 10 events")

        // 2. 중복 없음
        val allPartitions = partition0 + partition1 + partition2
        assertEquals(30, allPartitions.toSet().size, "No duplicates across partitions")

        // 3. 모든 이벤트 포함
        val expectedIds = (1L..30L).toSet()
        assertEquals(expectedIds, allPartitions.toSet(), "All events should be covered")
    }

    @Test
    @DisplayName("5개 파티션으로 나눠도 정상 동작")
    fun `should work correctly with 5 partitions`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..50L, baseTime = fixedTime)

        // When - 5개 파티션으로 조회 (같은 시간 사용!)
        val partitions = (0..4).map { partition ->
            repository.claimBatchByPartition(partition, 5, 100, fixedTime).map { it.eventId }
        }

        // Then
        // 1. 각 파티션 크기 확인 (균등 분산)
        partitions.forEach { partition ->
            assertEquals(10, partition.size, "Each partition should have 10 events")
        }

        // 2. 모든 이벤트 커버
        val allEvents = partitions.flatten().toSet()
        assertEquals((1L..50L).toSet(), allEvents, "All events should be covered")
    }

    @Test
    @DisplayName("limit보다 많은 이벤트가 있어도 limit만큼만 조회")
    fun `should respect limit parameter`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..90L, baseTime = fixedTime)

        // When - limit=5로 조회 (같은 시간 사용!)
        val events = repository.claimBatchByPartition(
            partition = 0,
            totalPartitions = 3,
            limit = 5,
            now = fixedTime
        )

        // Then - 5개만 조회됨
        assertEquals(5, events.size, "Should fetch only 5 events")
        
        // 모두 파티션 0에 속함
        events.forEach { event ->
            assertEquals(0L, event.eventId % 3, "All events should belong to partition 0")
        }
    }

    @Test
    @DisplayName("단일 인스턴스 모드 (totalPartitions=1)에서는 모든 이벤트 조회")
    fun `single instance mode should fetch all events`() {
        // Given - 고정 시간 설정
        val fixedTime = Instant.parse("2025-01-01T12:00:00Z")
        saveTestEvents(1L..10L, baseTime = fixedTime)

        // When - 단일 인스턴스 모드 (같은 시간 사용!)
        val events = repository.claimBatchByPartition(
            partition = 0,
            totalPartitions = 1,
            limit = 100,
            now = fixedTime
        )

        // Then - 모든 이벤트 조회됨
        assertEquals(10, events.size, "Should fetch all events")
        val eventIds = events.map { it.eventId }.sorted()
        assertEquals((1L..10L).toList(), eventIds)
    }

    /**
     * 테스트용 이벤트 저장 헬퍼 메서드
     * 
     * @param range 저장할 이벤트 ID 범위
     * @param baseTime 고정할 기준 시간 (디버깅 시에도 안정적인 테스트)
     */
    private fun saveTestEvents(
        range: LongRange,
        baseTime: Instant = Instant.now()
    ) {
        range.forEach { id ->
            val event = TransferEvent(
                eventId = id,
                aggregateType = "TRANSFER",
                aggregateId = "transaction-$id",
                eventType = "TRANSFER_COMPLETED",
                payload = """{"transactionId": $id, "amount": 10000}""",
                headers = """{"eventType": "TRANSFER_COMPLETED"}"""
            )
            repository.save(event, now = baseTime)
        }
    }

}
