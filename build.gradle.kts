import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    kotlin("kapt") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.duri"
version = "0.0.1-SNAPSHOT"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

repositories { mavenCentral() }

val queryDslVersion = "5.1.0"

dependencies {
    // --- web / validation ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Spring Boot 4 의 기본 Jackson 은 3.x(tools.jackson) 다. 2.x 모듈을 넣으면 빈이 안 잡힌다.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // --- security: OAuth2 login(kakao/google) + JWT resource server ---
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // --- persistence ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4 는 자동설정이 기술별 모듈로 쪼개져 있다.
    // flyway-core 만 넣으면 마이그레이션이 조용히 실행되지 않는다.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // --- querydsl ---
    implementation("com.querydsl:querydsl-jpa:$queryDslVersion:jakarta")
    kapt("com.querydsl:querydsl-apt:$queryDslVersion:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // --- ops ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    // --- test ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // Boot 4 에서 MockMvc 슬라이스는 별도 모듈로 빠졌다
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_17
    }
}

// JPA entities: open + no-arg constructor
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

// generated Q-classes are build output, never source
tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("src/main/generated"))
}
