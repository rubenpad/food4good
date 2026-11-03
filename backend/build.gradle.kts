plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.food4good"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Every test class has its own database and app, so classes can run side by side.
    maxParallelForks = (project.findProperty("testForks") as String?)?.toInt()
        ?: (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    // Tests are short-lived, so the JVM's heaviest compiler costs more CPU than it ever pays back.
    jvmArgs("-XX:TieredStopAtLevel=1")
    // e.g. -Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer\$Random
    systemProperties(System.getProperties().filterKeys { it.toString().startsWith("junit.") }.mapKeys { it.key.toString() })
}
