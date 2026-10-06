import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.composefluent.winrt"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation(project(":ide-model"))
    implementation(project(":fir-adapter")) { isTransitive = false }
    intellijPlatform {
        val localIde = providers.gradleProperty("kotlinWinRT.ide.path")
        if (localIde.isPresent) local(localIde.get()) else intellijIdea("2026.2.2")
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin", "com.intellij.gradle")
        composeUI()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

tasks.named<PrepareSandboxTask>("prepareTestSandbox") {
    // The installed unified IDEA also bundles commercial startup services.
    // Platform tests exercise only this plugin and its declared dependencies.
    disabledPlugins.add("com.intellij.modules.ultimate")
}

tasks.withType<Test>().configureEach {
    systemProperty("winrt.ide.toolchain", rootProject.projectDir.resolve("../..").canonicalPath)
    providers.gradleProperty("winrt.ide.templateOutput").orNull?.let { systemProperty("winrt.ide.templateOutput", it) }
    providers.gradleProperty("winrt.ide.xamlInput").orNull?.let { systemProperty("winrt.ide.xamlInput", it) }
    providers.gradleProperty("winrt.ide.xamlCompiler").orNull?.let { systemProperty("winrt.ide.xamlCompiler", it) }
}

intellijPlatform {
    // Compose owns the UI; there are no IntelliJ .form files or Java classes.
    instrumentCode = false
    pluginConfiguration {
        id = "io.github.composefluent.winrt.ide"
        name = "Kotlin WinRT"
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}
