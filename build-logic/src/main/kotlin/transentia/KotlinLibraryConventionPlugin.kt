package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.jvm")

        project.tasks.withType(KotlinJvmCompile::class.java).configureEach {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        }
    }
}
