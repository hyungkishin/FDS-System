package io.github.hyungkishin.transentia.domain

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication(
    scanBasePackages = ["io.github.hyungkishin.transentia"]
)
@EnableKafka
class FdsConsumerApplication

fun main(args: Array<String>) {
    runApplication<FdsConsumerApplication>(*args)
}