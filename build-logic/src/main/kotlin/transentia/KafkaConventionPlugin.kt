package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class KafkaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("org.jetbrains.kotlin.jvm")
        target.pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
        target.pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")
        target.pluginManager.apply("io.spring.dependency-management")

        target.afterEvaluate {
            dependencies {
                add("implementation", "org.springframework.kafka:spring-kafka")
                add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin")
                add("testImplementation", "org.springframework.kafka:spring-kafka-test")
            }
        }
    }
}