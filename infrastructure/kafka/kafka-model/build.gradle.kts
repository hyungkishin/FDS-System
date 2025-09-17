plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

dependencies {
    implementation("org.apache.avro:avro:1.11.4")
    implementation("io.confluent:kafka-avro-serializer:7.9.2")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}

avro {
    stringType.set("String")
}

// Avro 파일 위치 지정
sourceSets {
    main {
        java {
            srcDir("/kotlin")
        }
    }
}

tasks.withType<com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask>().configureEach {
    source("src/main/resources/avro")
}

tasks.compileKotlin {
    dependsOn(tasks.generateAvroJava)
}