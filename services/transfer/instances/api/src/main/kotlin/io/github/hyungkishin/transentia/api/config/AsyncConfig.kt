package io.github.hyungkishin.transentia.api.config

import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("outboxEventExecutor")
    fun outboxEventExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 3
        executor.maxPoolSize = 10
        executor.queueCapacity = 50
        executor.threadNamePrefix = "outbox-event-"
        executor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(30)
        executor.initialize()
        return executor
    }

    // MDC 정보 전파를 위한 TaskDecorator
    @Bean
    fun mdcTaskDecorator(): TaskDecorator {
        return TaskDecorator { runnable ->
            val contextMap = MDC.getCopyOfContextMap()
            Runnable {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap)
                    }
                    runnable.run()
                } finally {
                    MDC.clear()
                }
            }
        }
    }
}