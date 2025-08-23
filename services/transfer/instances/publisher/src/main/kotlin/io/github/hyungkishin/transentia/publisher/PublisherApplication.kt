package io.github.hyungkishin.transentia.publisher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["io.github.hyungkishin.transentia"])
@EnableScheduling
@EnableConfigurationProperties(PublisherProperties::class)
class PublisherApplication

fun main(args: Array<String>) {
    runApplication<PublisherApplication>(*args)
}