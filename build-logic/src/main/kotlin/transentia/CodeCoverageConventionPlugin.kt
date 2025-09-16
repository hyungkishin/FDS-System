package transentia

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

class CodeCoverageConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // jacoco 플러그인 적용
        pluginManager.apply("jacoco")

        // 공통 제외 패턴
        val jacocoExcludePatterns = listOf(
            "**/*ApplicationKt.class",
            "**/*Application.class",
            "**/*Dto.class",
            "**/*DtoKt.class",
            "**/*Request.class",
            "**/*Response.class",
            "**/*Config.class",
            "**/*Exception.class",
            "**/*Constants.class",
            "**/*Interceptor.class",
            "**/*Mapper.class",
            "**/*Extensions.class",
            "**/Q*.class"
        )

        // 분석 대상 class 디렉토리
        fun Project.jacocoClassDirs() =
            fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
                exclude(jacocoExcludePatterns)
            }

        // 테스트는 JUnit Platform 사용(혹시 안 켜져 있는 모듈 대비)
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        // 리포트 설정 (모든 JacocoReport 태스크에 공통 적용)
        tasks.withType<JacocoReport>().configureEach {
            dependsOn(tasks.named("test"))
            classDirectories.setFrom(files(jacocoClassDirs()))
            sourceDirectories.setFrom(project.layout.projectDirectory.dir("src/main/kotlin"))
            executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/test.exec"))

            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        // 커버리지 검증 (모든 JacocoCoverageVerification 태스크에 공통 적용)
        tasks.withType<JacocoCoverageVerification>().configureEach {
            classDirectories.setFrom(files(jacocoClassDirs()))
            sourceDirectories.setFrom(project.layout.projectDirectory.dir("src/main/kotlin"))
            executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/test.exec"))

            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVERED_RATIO"
                        minimum = "0.80".toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        value = "COVERED_RATIO"
                        minimum = "0.70".toBigDecimal()
                    }
                }
            }
        }

        // 통합 태스크
        tasks.register("testCoverage") {
            group = "verification"
            description = "Run tests, generate JaCoCo report, and verify coverage"
            dependsOn("test", "jacocoTestReport", "jacocoTestCoverageVerification")
            tasks["jacocoTestReport"].mustRunAfter("test")
            tasks["jacocoTestCoverageVerification"].mustRunAfter("jacocoTestReport")
        }

        // CI나 -PenableCoverage=1 이면 check에 자동 포함
        if (System.getenv("CI") == "true" || project.hasProperty("enableCoverage")) {
            tasks.named("check") { dependsOn("testCoverage") }
        }
    }
}
