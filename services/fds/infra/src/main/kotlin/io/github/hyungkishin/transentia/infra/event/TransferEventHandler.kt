package io.github.hyungkishin.transentia.infra.event

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TransferEventHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleCompleted(n: JsonNode) {
        val txId = n["transactionId"].asLong()
        val amount = n["amount"].asLong()
        val sender = n["senderId"].asLong()
        val receiver = n["receiverId"].asLong()
        // TODO: FDS 룰 엔진 호출/스코어 계산/저장 등
        log.info("FDS completed txId={}, amount={}, sender={}, receiver={}", txId, amount, sender, receiver)
    }

    fun handleFailed(n: JsonNode) {
        val txId = n["transactionId"].asLong()
        val reason = n["reason"]?.asText()
        log.info("FDS failed txId={}, reason={}", txId, reason)
        // TODO: 실패 시나리오 기록/알림 등
    }
}