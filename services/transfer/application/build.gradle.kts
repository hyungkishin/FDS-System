//plugins {
//    kotlin("jvm")
//    kotlin("plugin.spring")
//}
//
//dependencies {
//    implementation(project(":transfer-domain"))
//    implementation(project(":common-application"))
//    implementation(project(":common-domain"))
//
//    implementation("org.springframework:spring-context")
//    implementation("org.springframework:spring-tx")
//
//    testImplementation("io.kotest:kotest-runner-junit5")
//    testImplementation("io.kotest:kotest-assertions-core")
//}

plugins {
    id("transentia.spring-library")
}

dependencies {
    // 프로젝트 의존성
    implementation(project(":transfer-domain"))
    implementation(project(":common-application"))
    implementation(project(":common-domain"))

    // 특화된 의존성 (있다면 추가)
    // 예: implementation("org.springframework.retry:spring-retry")
}
