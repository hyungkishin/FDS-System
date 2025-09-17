package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class SpringLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // 공통 플러그인 적용
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
        pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")
        pluginManager.apply("io.spring.dependency-management")

        // Kotlin 세팅
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
        tasks.withType(KotlinCompile::class.java).configureEach {
            kotlinOptions {
                jvmTarget = "21"
                freeCompilerArgs = freeCompilerArgs + "-Xjsr305=strict"
            }
        }

        // 공통 의존성
        dependencies {
            add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
            add("testImplementation", platform("io.kotest:kotest-bom:5.9.1"))
            add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
        }
    }
}
