import org.gradle.api.Project
import org.gradle.api.file.FileTree

/**
 * 공통 exclude 규칙 (커버리지 테스트 대상에서 제외할 클래스 패턴)
 */
val jacocoExcludePatterns = listOf(
    "**/*ApplicationKt.class",    // main 함수 top-level 클래스 (Kotlin)
    "**/*Application.class",      // SpringBootApplication 클래스
    "**/*Dto.class",              // DTO 클래스
    "**/*DtoKt.class",            // companion object 등 포함된 Kotlin Dto 관련
    "**/*Request.class",          // 요청용 객체
    "**/*Response.class",         // 응답용 객체
    "**/*Config.class",            // 설정 클래스
    "**/*Exception.class",        // 예외 클래스
    "**/*Constants.class",        // 상수 클래스
    "**/*Interceptor.class",      // 인터셉터
    "**/*Mapper.class",           // Mapper 클래스
    "**/*Extensions.class",       // Kotlin 확장 함수 모음
    "**/Q*.class"                 // QueryDSL Q 클래스
)

/**
 * Jacoco 에서 사용할 분석 대상 FileTree 반환 함수
 */
fun Project.jacocoClassDirs(): FileTree =
    fileTree(layout.buildDirectory.dir("classes/kotlin/main").get().asFile) {
        exclude(jacocoExcludePatterns)
    }

/**
 * SonarQube에서 사용할 exclude 소스 경로 목록
 * (".class" -> ".kt" 변환, 구조 경로로 변경)
 */
val sonarExcludePatterns: List<String> = jacocoExcludePatterns
    .mapNotNull { pattern ->
        val ktPattern = when {
            pattern.endsWith(".class") -> pattern.replace(".class", ".kt")
            pattern.contains("/Q*")     -> pattern.replace("/Q*", "/Q*.kt")
            else                        -> null
        }
        ktPattern?.let { "src/main/kotlin/$it" }
    }
    .distinct()

const val SONAR_EXCLUSION_DELIMITER = ", "