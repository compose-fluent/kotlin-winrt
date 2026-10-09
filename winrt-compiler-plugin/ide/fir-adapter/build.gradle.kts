import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform.base")
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    implementation("io.github.compose-fluent:winrt-metadata:0.1.0-SNAPSHOT")
    intellijPlatform {
        val localIde = providers.gradleProperty("kotlinWinRT.ide.path")
        val sdkVersion = providers.gradleProperty("kotlinWinRT.ide.sdkVersion")
        when {
            localIde.isPresent -> local(localIde.get())
            providers.gradleProperty("kotlinWinRT.ide.variant").orNull?.startsWith("as-") == true ->
                androidStudio(sdkVersion.get())
            else -> intellijIdea(sdkVersion.orElse("2026.2.2").get())
        }
        bundledPlugin("org.jetbrains.kotlin")
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
    sourceSets.main {
        // Recompile the compiler-owned rules against the IDE's embedded FIR API.
        // No copied rules, IR lowering or semantic-output writes enter IDE analysis.
        kotlin.srcDir("../../src/main/kotlin")
        kotlin.include("io/github/composefluent/winrt/compiler/xaml/XamlFirRegistrar.kt")
        kotlin.include("io/github/composefluent/winrt/compiler/xaml/XamlNameCheckers.kt")
        kotlin.include("io/github/composefluent/winrt/compiler/xaml/XamlPropertyTypes.kt")
        kotlin.include("io/github/composefluent/winrt/ide/fir/**")
    }
}

tasks.named<Jar>("jar") {
    archiveFileName.set("kotlin-winrt-ide-fir.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Kotlin loads a replacement compiler plugin in an isolated class loader.
    // Include its model dependencies, but use the IDE's Kotlin and compiler classes.
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.filterNot { it.name.startsWith("kotlin-stdlib") || it.name.startsWith("annotations-") ||
            it.name.startsWith("kotlinx-coroutines-") }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
