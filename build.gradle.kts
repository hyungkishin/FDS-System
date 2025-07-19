import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    kotlin("plugin.jpa") version "1.9.24"
    kotlin("kapt") version "1.9.24"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco") // 코드 커버리지 측정
}

group = "io.github.hyungkishin"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-mustache")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Kotlin 컴파일 설정
tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
    }
}

// 테스트 설정
tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport") // 테스트 후 커버리지 자동 실행
}

// Jacoco 기본 설정
jacoco {
    toolVersion = "0.8.11"
}

// 공통 exclude 규칙 (커버리지 테스트 대상에서 제외할 클래스 패턴)
val excludes = listOf(
    "**/*Application*",
    "**/*Dto*",
    "**/*Request*",
    "**/*Response*",
    "**/*Config*",
    "**/*Exception*",
    "**/*Constants*",
    "**/*Interceptor*",
    "**/Q*.*", // QueryDSL 자동 생성 클래스
    "**/*DtoKt*" // Kotlin Dto companion object
)

// 커버리지 기준 검증 설정 (JacocoViolation) - 커버리지 미달 시 빌드 실패
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)

    violationRules {
        rule {
            enabled = true

            // 라인 커버리지 기준: 80% 이상
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }

            // 분기문 커버리지 기준: 70% 이상
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }

    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(excludes)
            }
        })
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/test.exec"))
}


// testCoverage: 테스트 / 커버리지 리포트 / 검증을 포함한 태스크
tasks.register("testCoverage") {
    group = "verification"
    description = "Run tests, generate report, verify coverage"
    dependsOn("test", "jacocoTestReport", "jacocoTestCoverageVerification")
}

// check 태스크에 통합 (TODO: CI에서 사용 될 기본 태스크)
tasks.named("check") {
    dependsOn("testCoverage")
}