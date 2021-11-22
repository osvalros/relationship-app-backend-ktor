import org.jetbrains.kotlin.konan.properties.loadProperties

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposedVersion: String by project

plugins {
    application
    kotlin("jvm") version "1.6.0"
    id("com.jetbrains.exposed.gradle.plugin") version "0.2.1"
    id("org.flywaydb.flyway") version "8.0.5"
}

group = "cz.osvald.rostislav"
version = "0.0.1"
application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-auth:$ktor_version")
    implementation("io.ktor:ktor-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-gson:$ktor_version")
    implementation("io.ktor:ktor-locations:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("org.postgresql:postgresql:42.3.1")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("com.zaxxer:HikariCP:5.0.0")
    implementation("org.flywaydb:flyway-core:8.0.2")
    implementation("de.mkammerer:argon2-jvm:2.7")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

val dbProperties = loadProperties("${projectDir}/db.properties")
val databaseUser = dbProperties["dataSource.user"].toString()
val databasePassword = dbProperties["dataSource.password"].toString()

exposedCodeGeneratorConfig {
    configFilename = "${projectDir}/exposedConf.yml"
    user = databaseUser
    password = databasePassword
    databaseName = dbProperties["dataSource.database"].toString()
    databaseDriver = dbProperties["dataSource.driver"].toString()
}

flyway {
    url = dbProperties["jdbcUrl"].toString()
    user = databaseUser
    password = databasePassword
}

sourceSets.main {
    java.srcDirs("build/tables")
}

tasks.generateExposedCode {
    dependsOn("clean")
}
