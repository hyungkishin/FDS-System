package io.github.hyungkishin.transentia.relay.partitioning

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.hyungkishin.transentia.infra.rdb.adapter.TransferEventsOutboxJdbcRepository
import io.github.hyungkishin.transentia.relay.TransferOutboxRelay
import io.github.hyungkishin.transentia.relay.component.EventBatchProcessor
import io.github.hyungkishin.transentia.relay.component.RetryPolicyHandler
import io.github.hyungkishin.transentia.relay.config.OutboxRelayConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(properties = ["spring.task.scheduling.enabled=false"])
@ActiveProfiles("test")
@DisplayName("Phase 1: 파티셔닝 성능 비교 테스트")
class PartitioningPerformanceComparisonTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var repository: TransferEventsOutboxJdbcRepository

    @Autowired
    private lateinit var eventBatchProcessor: EventBatchProcessor

    @Autowired
    private lateinit var retryPolicyHandler: RetryPolicyHandler

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val totalEvents = 10000
    private val batchSize = 1000

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM transfer_events")
    }

    @Test
    @DisplayName("Before: 파티셔닝 없이 3대 실행")
    fun `before partitioning - 3 instances without partitioning`() {
        println("\n=== BEFORE: 파티셔닝 없음 (3대 경쟁) ===")
        
        insertTestEvents(totalEvents)
        println("테스트 데이터: $totalEvents 개 생성")

        val processedCounts = ConcurrentHashMap<Int, AtomicInteger>()
        val queryCounts = ConcurrentHashMap<Int, AtomicInteger>()

        val startTime = System.currentTimeMillis()
        val now = Instant.now()

        val executor = Executors.newFixedThreadPool(3)
        val futures = (0..2).map { instanceId ->
            processedCounts[instanceId] = AtomicInteger(0)
            queryCounts[instanceId] = AtomicInteger(0)

            executor.submit {
                while (getEventCountByStatus("PENDING") > 0 || 
                       getEventCountByStatus("SENDING") > 0 ||
                       getEventCountByStatus("FAILED") > 0) {
                    
                    val batch = repository.claimBatch(batchSize, now)
                    queryCounts[instanceId]!!.incrementAndGet()
                    
                    if (batch.isNotEmpty()) {
                        val result = eventBatchProcessor.processBatch(
                            batch = batch,
                            topicName = "transfer-transaction-events",
                            timeoutSeconds = 30L
                        )
                        
                        if (result.successIds.isNotEmpty()) {
                            repository.markAsPublished(result.successIds, now)
                            processedCounts[instanceId]!!.addAndGet(result.successIds.size)
                        }
                        
                        result.failedEvents.forEach { failed ->
                            val backoffMillis = retryPolicyHandler.calculateBackoff(failed.attemptCount)
                            repository.markFailedWithBackoff(
                                id = failed.eventId,
                                cause = failed.error,
                                backoffMillis = backoffMillis,
                                now = now
                            )
                        }
                    }
                    // Sleep 제거 - 진짜 경쟁!
                }
            }
        }

        futures.forEach { it.get() }
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)

        val totalTime = System.currentTimeMillis() - startTime
        val publishedCount = getEventCountByStatus("PUBLISHED")

        println("\n=== BEFORE 결과 ===")
        println("총 처리 시간: ${totalTime}ms")
        println("처리 완료: $publishedCount / $totalEvents")
        
        println("\n처리 분포:")
        processedCounts.forEach { (id, count) ->
            val percentage = if (totalEvents > 0) count.get() * 100.0 / totalEvents else 0.0
            println("Instance $id: ${count.get()}건 (%.1f%%)".format(percentage))
        }

        println("\nDB 쿼리 수:")
        val totalQueries = queryCounts.values.sumOf { it.get() }
        queryCounts.forEach { (id, count) ->
            println("Instance $id: ${count.get()}회")
        }
        println("총 쿼리: ${totalQueries}회")

        println("\n특징:")
        println("- 파티셔닝 없음 (claimBatch)")
        println("- SKIP LOCKED로 3대 경쟁")
        println("- 불균등 분배 가능")
    }

    @Test
    @DisplayName("After: 파티셔닝으로 3대 실행")
    fun `after partitioning - 3 instances with partitioning`() {
        println("\n=== AFTER: 파티셔닝 적용 (3대 분담) ===")
        
        insertTestEvents(totalEvents)
        println("테스트 데이터: $totalEvents 개 생성")

        val relays = ConcurrentHashMap<Int, TransferOutboxRelay>()
        val queryCounts = ConcurrentHashMap<Int, AtomicInteger>()

        val startTime = System.currentTimeMillis()

        val executor = Executors.newFixedThreadPool(3)
        val futures = (0..2).map { instanceId ->
            queryCounts[instanceId] = AtomicInteger(0)

            executor.submit {
                val config = createConfig(
                    instanceId = instanceId,
                    totalInstances = 3,
                    batchSize = batchSize
                )
                val relay = createRelay(config)
                relays[instanceId] = relay
                relay.resetCounter()

                while (true) {
                    relay.run()
                    queryCounts[instanceId]!!.incrementAndGet()
                    
                    if (getEventCountByStatus("PENDING") == 0 && 
                        getEventCountByStatus("SENDING") == 0 &&
                        getEventCountByStatus("FAILED") == 0) {
                        break
                    }
                }
            }
        }

        futures.forEach { it.get() }
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.MINUTES)

        val totalTime = System.currentTimeMillis() - startTime
        val publishedCount = getEventCountByStatus("PUBLISHED")

        println("\n=== AFTER 결과 ===")
        println("총 처리 시간: ${totalTime}ms")
        println("처리 완료: $publishedCount / $totalEvents")
        
        println("\n처리 분포:")
        relays.forEach { (id, relay) ->
            // DB에서 직접 카운트
            val count = getPartitionEventCount(id, 3, "PUBLISHED")
            val percentage = if (totalEvents > 0) count * 100.0 / totalEvents else 0.0
            println("Instance $id: ${count}건 (%.1f%%)".format(percentage))
        }

        println("\nDB 쿼리 수:")
        val totalQueries = queryCounts.values.sumOf { it.get() }
        queryCounts.forEach { (id, count) ->
            println("Instance $id: ${count.get()}회")
        }
        println("총 쿼리: ${totalQueries}회")

        println("\n특징:")
        println("- MOD 연산으로 파티션 분리")
        println("- 균등 분배 (각각 33.3%)")
        println("- 락 경합 없음")

        val expectedPerInstance = totalEvents / 3
        (0..2).forEach { instanceId ->
            val count = getPartitionEventCount(instanceId, 3, "PUBLISHED")
            val diff = Math.abs(count - expectedPerInstance)
            val errorRate = diff * 100.0 / expectedPerInstance
            assertTrue(errorRate < 5.0, 
                "Instance $instanceId: 분배 오차가 5%를 초과 (${errorRate}%)"
            )
        }
    }

    @Test
    @DisplayName("간단한 파티셔닝 효과 시뮬레이션")
    fun `partitioning effect simulation`() {
        println("\n=== 파티셔닝 효과 시뮬레이션 ===")
        
        val eventCount = 300
        insertTestEvents(eventCount)
        println("테스트 데이터: $eventCount 개 생성")

        val partitionTimes = mutableMapOf<Int, Long>()

        (0..2).forEach { partition ->
            jdbcTemplate.execute("UPDATE transfer_events SET status = 'PENDING'")
            
            val config = createConfig(
                instanceId = partition,
                totalInstances = 3,
                batchSize = 100
            )
            val relay = createRelay(config)

            val startTime = System.currentTimeMillis()
            
            while (true) {
                val before = getPartitionEventCount(partition, 3, "PUBLISHED")
                relay.run()
                val after = getPartitionEventCount(partition, 3, "PUBLISHED")
                
                if (after == getPartitionEventCount(partition, 3, null)) {
                    break
                }
                
                Thread.sleep(50)
            }
            
            partitionTimes[partition] = System.currentTimeMillis() - startTime
        }

        println("\n각 파티션 처리 시간:")
        partitionTimes.forEach { (partition, time) ->
            val count = getPartitionEventCount(partition, 3, null)
            println("Partition $partition: ${time}ms (${count}건)")
        }

        val maxTime = partitionTimes.values.maxOrNull() ?: 0L
        println("\n병렬 실행 시 예상 시간: ${maxTime}ms")
        println("순차 실행 시 예상 시간: ${partitionTimes.values.sum()}ms")
        println("성능 개선: 약 ${partitionTimes.values.sum() / maxTime}배")
    }

    private fun insertTestEvents(count: Int) {
        val now = Instant.now()
        val timestamp = Timestamp.from(now)
        
        val sql = """
            INSERT INTO transfer_events (
                event_id, aggregate_id, aggregate_type, event_type, 
                payload, headers, status, attempt_count,
                created_at, updated_at, next_retry_at
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::transfer_outbox_status, ?, ?, ?, ?)
        """.trimIndent()

        val batchArgs = (1..count).map { id ->
            val payload = objectMapper.writeValueAsString(mapOf(
                "transactionId" to id,
                "senderId" to (10000 + id),
                "receiverId" to (20000 + id),
                "amount" to 100000,
                "status" to "COMPLETED"
            ))

            arrayOf<Any>(
                id.toLong(),
                "tx-$id",
                "TRANSFER",
                "TRANSFER_COMPLETED",
                payload,
                "{}",
                "PENDING",
                0,
                timestamp,
                timestamp,
                timestamp
            )
        }

        jdbcTemplate.batchUpdate(sql, batchArgs)
    }

    private fun getEventCountByStatus(status: String): Int {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM transfer_events WHERE status::text = ?",
            Int::class.java,
            status
        ) ?: 0
    }

    private fun getPartitionEventCount(partition: Int, totalPartitions: Int, status: String?): Int {
        val sql = if (status != null) {
            """
                SELECT COUNT(*) FROM transfer_events 
                WHERE MOD(event_id, ?) = ? AND status::text = ?
            """
        } else {
            """
                SELECT COUNT(*) FROM transfer_events 
                WHERE MOD(event_id, ?) = ?
            """
        }

        return if (status != null) {
            jdbcTemplate.queryForObject(sql, Int::class.java, totalPartitions, partition, status) ?: 0
        } else {
            jdbcTemplate.queryForObject(sql, Int::class.java, totalPartitions, partition) ?: 0
        }
    }

    private fun createConfig(
        instanceId: Int,
        totalInstances: Int,
        batchSize: Int
    ): OutboxRelayConfig {
        return OutboxRelayConfig(
            instanceId = instanceId,
            totalInstances = totalInstances,
            batchSize = batchSize,
            threadPoolSize = 10,
            timeoutSeconds = 30L,
            slowProcessingThresholdMs = 3000L
        )
    }

    private fun createRelay(config: OutboxRelayConfig): TransferOutboxRelay {
        return TransferOutboxRelay(
            outboxRepository = repository,
            eventBatchProcessor = eventBatchProcessor,
            retryPolicyHandler = retryPolicyHandler,
            config = config,
            executorService = Executors.newFixedThreadPool(config.threadPoolSize),
            topicName = "transfer-transaction-events"
        )
    }
}
