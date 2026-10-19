plugins {
    kotlin("jvm") version "2.4.0"
    id("application")
}

kotlin {
    jvmToolchain(25)
}

application {
    // Define the main class for the application.
    mainClass = "no.nav.toi.rekrutteringsbistand.bruker.api.AppKt"
    applicationName = "app"
}

repositories {
    mavenCentral()
}

val jacksonVersion = "3.2.3"
val javalinVersion = "7.2.3"
val micrometerVersion = "1.17.1"
val flywayVersion = "13.10.3"
val testContainerVersion = "2.0.5"

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin:javalin-micrometer:$javalinVersion")
    implementation("org.eclipse.jetty:jetty-util")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    implementation("io.prometheus:simpleclient_common:0.16.0")

    implementation(platform("tools.jackson:jackson-bom:$jacksonVersion"))
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("ch.qos.logback:logback-classic:1.6.3")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    implementation("com.auth0:java-jwt:4.6.2")
    implementation("com.auth0:jwks-rsa:0.23.0")

    implementation("org.postgresql:postgresql:42.7.14")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    implementation("com.zaxxer:HikariCP:7.0.2")

    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:${testContainerVersion}")
    testImplementation("org.testcontainers:testcontainers-postgresql:${testContainerVersion}")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("no.nav.security:mock-oauth2-server:6.1.1")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
