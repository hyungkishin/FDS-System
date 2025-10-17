package io.github.hyungkishin.transentia.application.service

import io.github.hyungkishin.transentia.application.required.FraudRuleRepository
import io.github.hyungkishin.transentia.container.enums.FinalDecisionType
import io.github.hyungkishin.transentia.container.enums.RuleSeverity
import io.github.hyungkishin.transentia.container.event.TransferCompleteEvent
import io.github.hyungkishin.transentia.container.model.FraudeRule
import io.github.hyungkishin.transentia.container.model.RiskLog
import io.github.hyungkishin.transentia.container.model.RiskRuleHit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AnalyzeTransferService(
    private val fraudRuleRepository: FraudRuleRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun analyze(event: TransferCompleteEvent): RiskLog {
        log.info("@@@@@@[FDS] 송금 분석 시작 - transferId={}, amount={}", event.eventId, event.amount)

        // TODO: redis 캐싱 ?
        // TODO: model (그래포 ? 타임라인 ? ) 제공해주는 라이브러리 리서치 -> 백터값을 추출 ! -> ES 에 적재 -> 유사도 -> 검색 ! (ML 영역의 모델을 검색해보는것 이 목적 + 학습 )
        // TODO: 엣지케이스 -> 알림 + log 성 + 학습 + 관리자 !
        // 과연 은행사마다 만들었을까 ? 이상감지를 탐지해주는 패턴이 있을것이다.

        // LAG + LLM

        // 모든 활성화된 룰 조회
        val activeRules = fraudRuleRepository.findAllActive()

        // 각 룰 실행 및 위반 감지
        val ruleHits = activeRules.mapNotNull { rule ->
            evaluateRule(rule, event)
        }

        // 최종 판정
        val decision = determineDecision(ruleHits)
        val reasons = ruleHits.map { it.ruleCode }

        // RiskLog 생성
        val riskLog = RiskLog.of(
            txId = event.eventId,
            decision = decision,
            reasons = reasons,
            aiScore = null, // TODO: AI 모델은 추후 구현
            ruleHits = ruleHits
        )

        log.info(
            "@@@@@@@[FDS] 분석 완료 - transferId={}, decision={}, hitCount={}",
            event.eventId, decision, ruleHits.size
        )

        return riskLog
    }

    private fun evaluateRule(rule: FraudeRule, event: TransferCompleteEvent): RiskRuleHit? {
        return when (rule.ruleType) {
            "HIGH_AMOUNT" -> checkHighAmount(rule, event)
            "SINGLE_HIGH_AMOUNT" -> checkSingleHighAmount(rule, event)
            "RAPID_TRANSFER" -> checkRapidTransfer(rule, event)
            else -> null
        }
    }

    /**
     * 단일 거래 2000만원 이상 탐지
     */
    private fun checkSingleHighAmount(rule: FraudeRule, event: TransferCompleteEvent): RiskRuleHit? {
        val threshold = (rule.threshold["amount"] as? Number)?.toLong() ?: return null

        return if (event.amount >= threshold) {
            RiskRuleHit(
                txId = event.eventId,
                ruleCode = "SINGLE_HIGH_AMOUNT",
                severity = RuleSeverity.CRITICAL, // RuleSeverity 에 CRITICAL 추가 추천
                weight = rule.weight.toInt(),
                reason = "단일 거래 고액 감지: ${event.amount} ≥ $threshold",
                occurredAt = Instant.now()
            )
        } else null
    }

    /**
     * 고액 송금 탐지
     */
    private fun checkHighAmount(rule: FraudeRule, event: TransferCompleteEvent): RiskRuleHit? {
        val threshold = (rule.threshold["amount"] as? Number)?.toLong() ?: return null

        return if (event.amount > threshold) {
            RiskRuleHit(
                txId = event.eventId,
                ruleCode = "HIGH_AMOUNT",
                severity = RuleSeverity.HIGH,
                weight = rule.weight.toInt(),
                reason = "고액 송금 감지: ${event.amount} > $threshold",
                occurredAt = Instant.now()
            )
        } else null
    }

    /**
     * 단기간 다중 송금 탐지 (추후 구현)
     */
    private fun checkRapidTransfer(rule: FraudeRule, event: TransferCompleteEvent): RiskRuleHit? {
        // TODO: 시간 기반 쿼리로 최근 N분 내 송금 횟수 체크
        return null
    }

    /**
     * 최종 판정
     */
    private fun determineDecision(ruleHits: List<RiskRuleHit>): FinalDecisionType {
        val totalWeight = ruleHits.sumOf { it.weight }

        return when {
            ruleHits.isEmpty() -> FinalDecisionType.ALLOWED
            totalWeight >= 100 -> FinalDecisionType.BLOCKED
            totalWeight >= 50 -> FinalDecisionType.REVIEW
            else -> FinalDecisionType.ALLOWED
        }
    }
}
