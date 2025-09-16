package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project

class SpringBootAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.springframework.boot")
        pluginManager.apply("io.spring.dependency-management")
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("org.jetbrains.kotlin.plugin.spring")
        pluginManager.apply("org.jetbrains.kotlin.plugin.allopen")
    }
}
