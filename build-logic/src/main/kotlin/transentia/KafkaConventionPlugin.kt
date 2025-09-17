package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KafkaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // 공통 플러그인
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.springframework.boot")
        pluginManager.apply("io.spring.dependency-management")
        pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")
        pluginManager.apply("org.jetbrains.kotlin.plugin.spring")

        // 저장소 추가 - 올바른 Gradle Kotlin DSL 문법 사용
        repositories.apply {
            mavenCentral()
            maven {
                url = uri("https://packages.confluent.io/maven/")
            }
        }

        // 공통 의존성
        dependencies {
            add("implementation", "org.springframework.boot:spring-boot-starter")
            add("implementation", "org.springframework.boot:spring-boot-autoconfigure")
            add("implementation", "org.springframework.kafka:spring-kafka")

            add("implementation", "org.springframework.boot:spring-boot-configuration-processor")
            add("implementation", "org.springframework.boot:spring-boot-starter-validation")

            add("implementation", "com.fasterxml.jackson.core:jackson-core")
            add("implementation", "com.fasterxml.jackson.core:jackson-databind")
            add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin")

            add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
            add("testImplementation", "org.springframework.kafka:spring-kafka-test")
        }

        // Kotlin 컴파일 옵션
        tasks.withType(KotlinCompile::class.java).configureEach {
            kotlinOptions {
                freeCompilerArgs = freeCompilerArgs + "-Xjsr305=strict"
                jvmTarget = "21"
            }
        }

        // JUnit5
        tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}