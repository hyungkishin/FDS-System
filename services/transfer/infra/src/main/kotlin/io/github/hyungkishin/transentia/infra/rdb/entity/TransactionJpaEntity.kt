package io.github.hyungkishin.transentia.infra.rdb.entity

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.consumer.enums.TransactionStatus
import io.github.hyungkishin.transentia.consumer.model.Money
import io.github.hyungkishin.transentia.consumer.model.Transaction
import io.github.hyungkishin.transentia.infra.config.BaseEntity
import jakarta.persistence.*
import org.hibernate.Hibernate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Persistable
import java.time.Instant

@Entity
@Table(
    name = "transactions",
    indexes = [
        Index(name = "idx_tx_sender_created", columnList = "sender_user_id, created_at DESC"),
        Index(name = "idx_tx_receiver_created", columnList = "receiver_user_id, created_at DESC"),
        Index(name = "idx_tx_status_updated", columnList = "status, status_updated_at DESC")
    ]
)
class TransactionJpaEntity(
    @Id
    @Column(nullable = false)
    val id: Long,

    @Column(name = "sender_user_id", nullable = false)
    val senderUserId: Long,

    @Column(name = "receiver_user_id", nullable = false)
    val receiverUserId: Long,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    var status: TransactionStatus,

    @Column(name = "received_at")
    val receivedAt: Instant? = null,

    @Column(name = "status_updated_at", insertable = false, updatable = false)
    val statusUpdatedAt: Instant? = null,

    @Version
    var version: Long? = null

) : BaseEntity(), Persistable<Long> {

    override fun getId(): Long = id
    override fun isNew(): Boolean = (version == null)

    fun toDomain(): Transaction =
        Transaction.of(
            id = SnowFlakeId(id),
            senderSnowFlakeId = SnowFlakeId(senderUserId),
            receiverSnowFlakeId = SnowFlakeId(receiverUserId),
            amount = Money.fromRawValue(amount)
        ).apply {
            when (status) {
                TransactionStatus.COMPLETED -> complete()
                TransactionStatus.FAILED -> fail("송금 실패")
                TransactionStatus.PENDING -> {}
            }
        }

    companion object {
        fun from(domain: Transaction): TransactionJpaEntity =
            TransactionJpaEntity(
                id = domain.id.value,
                senderUserId = domain.senderSnowFlakeId.value,
                receiverUserId = domain.receiverSnowFlakeId.value,
                amount = domain.amount.rawValue,
                status = domain.status,
                receivedAt = domain.createdAt // 의미가 다르면 적절히 매핑
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as TransactionJpaEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}