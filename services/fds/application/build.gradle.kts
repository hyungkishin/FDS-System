plugins {
    id("transentia.spring-library")
}

dependencies {
    implementation(project(":common-application"))
    implementation(project(":common-domain"))
    implementation(project(":fds-domain"))
}
