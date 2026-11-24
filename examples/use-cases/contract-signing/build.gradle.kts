plugins {
    java
    id("org.springframework.boot") version "4.2.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.operaton.examples"
version = "1.1.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.2.0")
        mavenBom("org.operaton.bpm:operaton-bom:2.2.2")
        mavenBom("software.amazon.awssdk:bom:2.59.2")
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
    implementation("org.apache.pdfbox:pdfbox:3.0.8")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.awaitility:awaitility")
    testImplementation("io.rest-assured:rest-assured:5.5.7")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
