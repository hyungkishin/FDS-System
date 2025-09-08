package io.github.hyungkishin.transentia.infra.rdb.entity


import io.github.hyungkishin.transentia.domain.enums.TransactionHistoryStatus
import io.github.hyungkishin.transentia.domain.model.transaction.TransactionHistory
import jakarta.persistence.*
import org.hibernate.Hibernate
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "transaction_histories",
    indexes = [
        Index(name = "idx_txh_txid_created_at", columnList = "transaction_id, created_at DESC"),
        Index(name = "idx_txh_status_created_at", columnList = "status, created_at DESC")
    ]
)
class TransactionHistoryJpaEntity(

    @Id
    @Column(nullable = false)
    val id: Long, // Snowflake

    @Column(name = "transaction_id", nullable = false)
    val transactionId: Long, // Snowflake

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    val status: TransactionHistoryStatus,

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    val createdAt: Instant = Instant.now()
) {

    companion object {
        fun from(domain: TransactionHistory): TransactionHistoryJpaEntity =
            TransactionHistoryJpaEntity(
                id = domain.id.value,
                transactionId = domain.transactionId.value,
                status = domain.status,
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as TransactionHistoryJpaEntity
        return this.id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}