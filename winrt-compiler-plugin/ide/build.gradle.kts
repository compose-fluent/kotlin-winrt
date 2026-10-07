import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.composefluent.winrt"
version = "0.1.0-SNAPSHOT"

// Each distribution recompiles its FIR/UI adapters and owns its test caches.
// Keep the baseline package intact while validating another installed SDK.
val ideVariant = providers.gradleProperty("kotlinWinRT.ide.variant").orNull
val ideBuildLine = providers.provider {
    intellijPlatform.productInfo.buildNumber.substringBefore('.').also {
        require(it in setOf("261", "262")) { "This adapter requires a validated 261 or 262 SDK." }
    }
}

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
    sourceSets.named("main") {
        // Compile the packaging owner's pure Kotlin sources, as with the FIR adapter.
        // Selection, package-path checks and manifest validation have one source of truth.
        kotlin.srcDir("../../windows-toolkit-gradle-plugin/src/main/kotlin")
        kotlin.srcDir("../../winrt-runtime/src/commonMain/kotlin")
        kotlin.srcDir("../../winrt-runtime/src/jvmMain/kotlin")
        kotlin.include("io/github/composefluent/winrt/ide/**",
            "io/github/composefluent/winrt/runtime/WinRTXamlHotReloadProtocol.kt",
            "io/github/composefluent/winrt/runtime/WinRTXamlHotReloadWire.kt",
            "io/github/composefluent/windows/toolkit/gradle/AppxResourceLayout.kt",
            "io/github/composefluent/windows/toolkit/gradle/PackageResourcePaths.kt",
            "io/github/composefluent/windows/toolkit/gradle/ProjectPriManifestSupport.kt",
            "io/github/composefluent/windows/toolkit/gradle/WinAppRestoreLockfileModel.kt",
            "io/github/composefluent/windows/toolkit/gradle/WinRTNuGetMsBuildPayloadResolver.kt")
    }
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
    // Native import indexes the real SDK and included toolchain alongside the
    // distribution's own plugins; a light fixture's 2 GiB budget is insufficient.
    if (providers.gradleProperty("winrt.ide.importProject").isPresent) maxHeapSize = "4g"
    // BasePlatformTestCase is JUnit 3; its runner reports JUnit 4 assumptions
    // as failures. Keep optional real-toolchain fixtures out until configured.
    if (!providers.gradleProperty("winrt.ide.importProject").isPresent) exclude("**/WinRTGradleImportTest.class")
    if (!providers.gradleProperty("winrt.ide.templateOutput").isPresent) exclude("**/WinRTTemplateGenerationTest.class")
    if (!providers.gradleProperty("winrt.ide.xamlInput").isPresent || !providers.gradleProperty("winrt.ide.xamlCompiler").isPresent)
        exclude("**/WinRTXamlDocumentCompilerTest.class")
    systemProperty("winrt.ide.toolchain", rootProject.projectDir.resolve("../..").canonicalPath)
    providers.gradleProperty("winrt.ide.templateOutput").orNull?.let { systemProperty("winrt.ide.templateOutput", it) }
    providers.gradleProperty("winrt.ide.xamlInput").orNull?.let { systemProperty("winrt.ide.xamlInput", it) }
    providers.gradleProperty("winrt.ide.xamlCompiler").orNull?.let { systemProperty("winrt.ide.xamlCompiler", it) }
    providers.gradleProperty("winrt.ide.hotReloadSession").orNull?.let { systemProperty("winrt.ide.hotReloadSession", it) }
    providers.gradleProperty("winrt.ide.hotReloadResourcesSession").orNull?.let { systemProperty("winrt.ide.hotReloadResourcesSession", it) }
    providers.gradleProperty("winrt.ide.hotReloadResourcesSource").orNull?.let { systemProperty("winrt.ide.hotReloadResourcesSource", it) }
    providers.gradleProperty("winrt.ide.hotReloadGraphSession").orNull?.let { systemProperty("winrt.ide.hotReloadGraphSession", it) }
    providers.gradleProperty("winrt.ide.hotReloadGraphSource").orNull?.let { systemProperty("winrt.ide.hotReloadGraphSource", it) }
    providers.gradleProperty("winrt.ide.importProject").orNull?.let { systemProperty("winrt.ide.importProject", it) }
    providers.gradleProperty("winrt.ide.importPhase").orNull?.let { systemProperty("winrt.ide.importPhase", it) }
    providers.gradleProperty("winrt.ide.recoveredHotReloadSession").orNull?.let { systemProperty("winrt.ide.recoveredHotReloadSession", it) }
}

intellijPlatform {
    if (ideVariant != null) sandboxContainer.set(layout.projectDirectory.dir(".intellijPlatform/sandbox/$ideVariant"))
    // Compose owns the UI; there are no IntelliJ .form files or Java classes.
    instrumentCode = false
    pluginConfiguration {
        id = "io.github.composefluent.winrt.ide"
        name = "Kotlin WinRT"
        ideaVersion {
            sinceBuild.set(ideBuildLine)
            untilBuild.set(ideBuildLine.map { "$it.*" })
        }
    }
}
