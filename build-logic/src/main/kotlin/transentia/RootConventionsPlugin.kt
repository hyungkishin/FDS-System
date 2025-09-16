package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project

class RootConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.subprojects {
            val bootApps = setOf(
                Modules.TRANSFER_API,
                Modules.TRANSFER_RELAY,
                Modules.TRANSFER_PUBLISHER,
                Modules.TRANSFER_CONSUMER,
                Modules.FDS_CONSUMER
            )
            val springModules = setOf(
                Modules.TRANSFER_APPLICATION,
                Modules.TRANSFER_INFRA,
                Modules.FDS_INFRA,
                Modules.COMMON_APPLICATION,
                Modules.FDS_APPLICATION,
                Modules.KAFKA_CONFIG,
                Modules.KAFKA_PRODUCER,
                Modules.KAFKA_CONSUMER
            )
            val jpaModules = setOf(
                Modules.TRANSFER_INFRA,
                Modules.FDS_INFRA
            )

            val pureKotlinModules = setOf(
                Modules.COMMON_DOMAIN,
                Modules.TRANSFER_DOMAIN,
                Modules.FDS_DOMAIN
            )
            val coverageModules = setOf(
                Modules.TRANSFER_DOMAIN,
                Modules.TRANSFER_INFRA,
                Modules.FDS_INFRA,
            )

            when (name) {
                in bootApps -> pluginManager.apply("transentia.spring-boot-app")
                in springModules -> pluginManager.apply("transentia.spring-library")
                in pureKotlinModules -> pluginManager.apply("transentia.kotlin-library")
            }

            if (name in jpaModules) pluginManager.apply("transentia.spring-jpa")
            if (name in coverageModules) pluginManager.apply("transentia.code-coverage")
        }
    }
}
