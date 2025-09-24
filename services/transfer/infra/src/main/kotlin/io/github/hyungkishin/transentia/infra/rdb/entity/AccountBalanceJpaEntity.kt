package io.github.hyungkishin.transentia.infra.rdb.entity

import io.github.hyungkishin.transentia.common.snowflake.SnowFlakeId
import io.github.hyungkishin.transentia.domain.model.account.AccountBalance
import io.github.hyungkishin.transentia.domain.model.account.Money
import io.github.hyungkishin.transentia.infra.config.BaseEntity
import jakarta.persistence.*
import org.hibernate.Hibernate
import org.springframework.data.domain.Persistable

@Entity
@Table(name = "account_balances")
class AccountBalanceJpaEntity(

    @Id
    val id: Long,

    @Column(name = "account_number", nullable = false)
    val accountNumber: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "balance", nullable = false)
    var balance: Long,

    @Version
    var version: Long,

) : BaseEntity() {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)  // 여기서만 읽기 전용
    var user: UserJpaEntity? = null

    fun toDomain(): AccountBalance =
        AccountBalance.of(
            SnowFlakeId(id),
            SnowFlakeId(userId),
            accountNumber,
            Money.fromRawValue(balance),
            version,
        )

    companion object {
        fun from(domain: AccountBalance): AccountBalanceJpaEntity =
            AccountBalanceJpaEntity(
                id = domain.id.value,
                userId = domain.userId.value,
                balance = domain.current().rawValue,
                accountNumber = domain.accountNumber,
                version = domain.version,
            )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as AccountBalanceJpaEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
