plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone")
}

// -Pbridge=brave switches the tracer bridge
val bridge = (findProperty("bridge") ?: "otel").toString()

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-micrometer-tracing")
    if (bridge == "brave") {
        implementation("org.springframework.boot:spring-boot-micrometer-tracing-brave")
        implementation("io.micrometer:micrometer-tracing-bridge-brave")
    } else {
        implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

sourceSets.test { java.srcDir("src/test-$bridge/java") }

tasks.test {
    useJUnitPlatform()
    systemProperty("bridge", bridge)
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
