plugins {
    java
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "org.operaton.examples"
version = "0.1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.3")
        mavenBom("org.operaton.bpm:operaton-bom:2.1.0")
        mavenBom("software.amazon.awssdk:bom:2.29.0")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Operaton
    implementation("org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter-webapp")
    implementation("org.operaton.bpm.springboot:operaton-bpm-spring-boot-starter-rest")

    // Database
    runtimeOnly("org.postgresql:postgresql")

    // AWS SDK
    implementation("software.amazon.awssdk:s3")

    // PDF Processing
    implementation("org.apache.pdfbox:pdfbox:3.0.7")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.awaitility:awaitility")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
