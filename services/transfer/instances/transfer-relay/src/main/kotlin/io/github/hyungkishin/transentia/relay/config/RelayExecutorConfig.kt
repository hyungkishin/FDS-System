package io.github.hyungkishin.transentia.relay.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class RelayExecutorConfig {
    @Bean(name = ["relayScheduler"])
    fun relayScheduler(): ThreadPoolTaskScheduler {
        return ThreadPoolTaskScheduler().apply {
            poolSize = 1             // 소형 워커 1개 (필요하면 2~3 설정할것.)
            setThreadNamePrefix("relay-")
            setAwaitTerminationSeconds(10)
            setWaitForTasksToCompleteOnShutdown(true)
            initialize()
        }
    }
}