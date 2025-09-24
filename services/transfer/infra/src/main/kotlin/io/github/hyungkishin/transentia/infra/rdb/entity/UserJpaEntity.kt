package io.github.hyungkishin.transentia.infra.rdb.entity

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.enums.UserRole
import io.github.hyungkishin.transentia.domain.enums.UserStatus
import io.github.hyungkishin.transentia.domain.model.user.*
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Id
    val id: Long,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(nullable = false, unique = true, length = 255)
    val email: String,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    val status: UserStatus = UserStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    val role: UserRole = UserRole.USER,

    // FDS 관련 필드
    @Column(name = "is_transfer_locked", nullable = false)
    val isTransferLocked: Boolean = false,

    @Column(name = "transfer_lock_reason", columnDefinition = "TEXT")
    val transferLockReason: String? = null,

    @Column(name = "daily_transfer_limit", nullable = false)
    val dailyTransferLimit: Long = 5_000_000L, // 기본 500만원

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var account: AccountBalanceJpaEntity

) {

    /**
     * JPA 엔티티 → 도메인 모델 변환
     */
    fun toDomain(): User {
        return User.of(
            id = SnowFlakeId(id),
            name = UserName(name),
            email = Email(email),
            status = status,
            role = role,
            accountBalance = account.toDomain(),
            isTransferLocked = isTransferLocked,
            transferLockReason = transferLockReason?.let { TransferLockReason(it) },
            dailyTransferLimit = DailyTransferLimit(dailyTransferLimit),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun from(domain: User): UserJpaEntity {
            val accountEntity = AccountBalanceJpaEntity.from(domain.accountBalance)

            val userEntity = UserJpaEntity(
                id = domain.id.value,
                name = domain.name.value,
                email = domain.email.value,
                status = domain.status,
                role = domain.role,
                account = accountEntity,  // account 설정
                isTransferLocked = domain.isTransferLocked,
                transferLockReason = domain.transferLockReason?.value,
                dailyTransferLimit = domain.dailyTransferLimit.value,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )

            // 양방향 연관관계 설정
            accountEntity.user = userEntity

            return userEntity
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserJpaEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "UserJpaEntity(id=$id, name='$name', email='$email', status=$status, isTransferLocked=$isTransferLocked)"
    }

}