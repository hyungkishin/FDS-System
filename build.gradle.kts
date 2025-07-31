import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.2" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    kotlin("plugin.allopen") version "1.9.25" apply false
    kotlin("kapt") version "1.9.25" apply false
}

group = "io.github.hyungkishin"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
    }
}

// coverage 정책 정의
object CoveragePolicy {
    val line = 0.80.toBigDecimal()
    val branch = 0.70.toBigDecimal()
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
    apply(plugin = "org.jetbrains.kotlin.kapt")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    if (name in listOf("api-server")) {
        apply(plugin = "org.springframework.boot")
    }

    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
            mavenBom("io.kotest:kotest-bom:5.8.0")
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            jvmTarget = "21"
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = true
        }
    }

    dependencies {
        add("implementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
        add("testImplementation", "io.kotest:kotest-runner-junit5")
        add("testImplementation", "io.kotest:kotest-assertions-core")
    }

    if (name in listOf("core-transfer", "infra-rdb")) {
        tasks.named<JacocoReport>("jacocoTestReport") {
            dependsOn(tasks.named<Test>("test"))
            finalizedBy("jacocoTestCoverageVerification") // 리포트 생성 후 검증 실행

            classDirectories.setFrom(files(jacocoClassDirs()))
            sourceDirectories.setFrom(project.layout.projectDirectory.dir("src/main/kotlin"))
            executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/test.exec"))

            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            classDirectories.setFrom(files(jacocoClassDirs()))
            sourceDirectories.setFrom(project.layout.projectDirectory.dir("src/main/kotlin"))
            executionData.setFrom(fileTree(layout.buildDirectory).include("/jacoco/test.exec"))

            violationRules {
                rule {
                    enabled = true
                    element = "BUNDLE"
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = CoveragePolicy.line
                    }
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        minimum = CoveragePolicy.branch
                    }
                }
            }
        }

        // test + report + verify를 순차 실행하는 task
        tasks.register("testCoverage") {
            group = "verification"
            description = "Run tests, generate report, and verify coverage"

            dependsOn("test", "jacocoTestReport", "jacocoTestCoverageVerification")
            tasks["jacocoTestReport"].mustRunAfter("test")
            tasks["jacocoTestCoverageVerification"].mustRunAfter("jacocoTestReport")
        }
    }

    // check 시 coverage 실행 옵션 ( CI에서만 실행되도록 )
    if (System.getenv("CI") == "true" || project.hasProperty("enableCoverage")) {
        tasks.named("check") {
            dependsOn("testCoverage")
        }
    }
}