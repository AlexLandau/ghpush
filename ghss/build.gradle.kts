plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
}

tasks.register("run", JavaExec::class.java) {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.github.alexlandau.ghss.GhssMainKt")
}

tasks.test {
    useJUnitPlatform()
}
