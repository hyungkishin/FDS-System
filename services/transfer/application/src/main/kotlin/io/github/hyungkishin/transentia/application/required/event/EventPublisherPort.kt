package io.github.hyungkishin.transentia.application.required.event

// 퍼블리셔는 구현체 내부에서 afterCommit
interface EventPublisherPort {
    fun publish(event: Any)
}