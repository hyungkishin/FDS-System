package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class SpringJpaConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.jpa")

        dependencies {
            add("implementation", "org.springframework.boot:spring-boot-starter-data-jpa")
            add("runtimeOnly", "org.postgresql:postgresql")
        }
    }
}
