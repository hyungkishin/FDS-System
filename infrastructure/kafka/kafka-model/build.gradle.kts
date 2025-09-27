plugins {
    id("transentia.kotlin-library")
    id("com.github.davidmc24.gradle.plugin.avro")
}

dependencies {
    implementation("org.apache.avro:avro:1.11.4")
    implementation("io.confluent:kafka-avro-serializer:7.9.2")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
}

avro {
    stringType.set("String")
}

// Kotlin과 생성된 Java 파일 모두 컴파일하도록 설정
sourceSets {
    main {
        java {
            srcDirs("src/main/kotlin", "build/generated-main-avro-java")
        }
    }
}

tasks.withType<com.github.davidmc24.gradle.plugin.avro.GenerateAvroJavaTask>().configureEach {
    source("src/main/resources/avro")
}

// Kotlin 컴파일이 Avro 생성 후에 실행되도록 설정
tasks.compileKotlin {
    dependsOn(tasks.generateAvroJava)
}