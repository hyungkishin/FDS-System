import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

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

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.jetbrains.kotlin.plugin.jpa")
    apply(plugin = "org.jetbrains.kotlin.plugin.allopen")
    apply(plugin = "org.jetbrains.kotlin.kapt")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    // 실행 가능한 모듈만 boot 적용
    if (name in listOf("api-server")) {
        apply(plugin = "org.springframework.boot")
    }

    // BOM
    configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.2")
        }
    }

    // Java toolchain 설정
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Kotlin 컴파일러 설정
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs += "-Xjsr305=strict"
            jvmTarget = "21"
        }
    }

    // 테스트 설정
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // 공통 의존성
    dependencies {
        add("implementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
        add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
    }

}
