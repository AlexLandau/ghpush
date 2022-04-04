import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    `java-library`
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.assertj:assertj-core:3.22.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

application {
    mainClass.set("com.github.alexlandau.ghpush.GhpushMainKt")
}

tasks.test.configure {
    useJUnitPlatform()
    testLogging.exceptionFormat = TestExceptionFormat.FULL
}

val writeVersionTask = tasks.register("writeVersion") {
    doLast {
        // Use calendar versioning unless this is a tagged CI build
        val formatter = DateTimeFormatter.ofPattern("uu.MM.dd.HH.mm.ss")
        val version = "ghpush non-release version ${LocalDateTime.now().format(formatter)}"

        val dir = file("src/main/resources/com/github/alexlandau/ghpush")
        dir.mkdirs()
        val file = File(dir, "version")
        file.writeText(version)
    }
}
tasks.named("processResources").configure {
    dependsOn(writeVersionTask)
}
