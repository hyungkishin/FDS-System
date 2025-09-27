package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("org.jetbrains.kotlin.jvm")
        target.pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
        target.pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")

        target.extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        target.tasks.withType(KotlinCompile::class.java).configureEach {
            kotlinOptions {
                jvmTarget = "21"
                freeCompilerArgs = freeCompilerArgs + "-Xjsr305=strict"
            }
        }

        target.dependencies {
            add("implementation", "org.jetbrains.kotlin:kotlin-reflect")
        }

        target.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}