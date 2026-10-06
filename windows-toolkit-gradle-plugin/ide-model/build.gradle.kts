plugins {
    `java-library`
    kotlin("jvm") version "2.4.0"
}

group = "io.github.composefluent.winrt"
version = "0.1.0-SNAPSHOT"

repositories { mavenCentral() }
dependencies { compileOnly(kotlin("stdlib")) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

kotlin {
    jvmToolchain(25)
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}
