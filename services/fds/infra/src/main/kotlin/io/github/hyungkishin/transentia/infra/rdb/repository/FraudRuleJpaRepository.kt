package io.github.hyungkishin.transentia.infra.rdb.repository

import io.github.hyungkishin.transentia.infra.rdb.entity.FraudRuleJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FraudRuleJpaRepository : JpaRepository<FraudRuleJpaEntity, Long> {
    fun findByIsActiveTrue(): List<FraudRuleJpaEntity>
}
