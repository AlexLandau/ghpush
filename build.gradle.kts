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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
}

application {
    mainClass.set("com.github.alexlandau.ghss.GhssMainKt")
}

tasks.test {
    useJUnitPlatform()
}
