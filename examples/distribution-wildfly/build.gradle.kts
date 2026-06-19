plugins {
    war
}

group = "org.operaton.examples"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// WAR bytecode must target JDK 17: the operaton/wildfly distribution image runs OpenJDK 17
tasks.compileJava {
    options.release = 17
}

repositories {
    mavenCentral()
}

val operatonVersion = "2.1.1"
val testcontainersVersion = "2.0.5"

dependencies {
    compileOnly(platform("org.operaton.bpm:operaton-bom:$operatonVersion"))
    compileOnly("org.operaton.bpm:operaton-engine")
    compileOnly("jakarta.servlet:jakarta.servlet-api:6.1.0")

    testImplementation(platform("org.operaton.bpm:operaton-bom:$operatonVersion"))
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("io.rest-assured:rest-assured:5.5.7")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.slf4j:slf4j-simple:2.0.18")
}

tasks.test {
    useJUnitPlatform()
}
