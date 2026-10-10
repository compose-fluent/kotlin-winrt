import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.composefluent.winrt"
version = providers.gradleProperty("kotlinWinRT.ide.version").orElse("0.1.0-SNAPSHOT").get()
require(version.toString().matches(Regex("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?"))) {
    "Use a three-part IDE plugin version, optionally followed by a prerelease suffix."
}

// Each distribution recompiles its FIR/UI adapters and owns its test caches.
// Keep the baseline package intact while validating another installed SDK.
val ideVariant = providers.gradleProperty("kotlinWinRT.ide.variant").orNull
val androidStudioSdk = ideVariant?.startsWith("as-") == true
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
    // The isolated FIR jar already owns the metadata/runtime model classes.
    // UI code must consume that same bytecode: recompiling it with Compose
    // adds $stable fields that the non-Compose copy does not have.
    implementation(project(":fir-adapter")) { isTransitive = false }
    intellijPlatform {
        val localIde = providers.gradleProperty("kotlinWinRT.ide.path")
        val sdkVersion = providers.gradleProperty("kotlinWinRT.ide.sdkVersion")
        when {
            localIde.isPresent -> local(localIde.get())
            androidStudioSdk -> androidStudio(sdkVersion.get())
            else -> intellijIdea(sdkVersion.orElse("2026.2.2").get())
        }
        bundledPlugins("com.intellij.java", "org.jetbrains.kotlin", "com.intellij.gradle")
        if (androidStudioSdk) bundledPlugin("org.jetbrains.android")
        composeUI()
        bundledModules("intellij.platform.jewel.markdown.core", "intellij.platform.jewel.markdown.ideLafBridgeStyling")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(25)
    sourceSets.named("main") {
        if (androidStudioSdk) kotlin.srcDir("src/androidStudio/kotlin")
        // Compile the packaging owner's pure Kotlin sources, as with the FIR adapter.
        // Selection, package-path checks and manifest validation have one source of truth.
        kotlin.srcDir("../../windows-toolkit-gradle-plugin/src/main/kotlin")
        kotlin.include("io/github/composefluent/winrt/ide/**",
            "io/github/composefluent/windows/toolkit/gradle/AppxResourceLayout.kt",
            "io/github/composefluent/windows/toolkit/gradle/PackageResourcePaths.kt",
            "io/github/composefluent/windows/toolkit/gradle/ProjectPriManifestSupport.kt",
            "io/github/composefluent/windows/toolkit/gradle/WinAppRestoreLockfileModel.kt",
            "io/github/composefluent/windows/toolkit/gradle/WinRTNuGetMsBuildPayloadResolver.kt")
    }
    if (androidStudioSdk) sourceSets.named("test") { kotlin.srcDir("src/androidStudioTest/kotlin") }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

// Unit-test classpaths can hide a duplicate class by loading the UI output
// first. Verify the installed artifact, whose jar ordering is IDE-owned.
val verifyPluginClassOwnership by tasks.registering {
    val archive = tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }
    dependsOn("buildPlugin")
    inputs.file(archive)
    doLast {
        val owners = mutableMapOf<String, String>()
        val duplicates = mutableListOf<String>()
        ZipFile(archive.get().asFile).use { plugin ->
            plugin.entries().asSequence().filter { it.name.endsWith(".jar") && "/lib/" in it.name }.forEach { jar ->
                ZipInputStream(plugin.getInputStream(jar)).use { classes ->
                    var entry = classes.nextEntry
                    while (entry != null) {
                        if (entry.name.startsWith("io/github/composefluent/") && entry.name.endsWith(".class")) {
                            owners.put(entry.name, jar.name)?.let { previous ->
                                duplicates += "${entry.name}: $previous and ${jar.name}"
                            }
                        }
                        entry = classes.nextEntry
                    }
                }
            }
        }
        check(owners.isNotEmpty()) { "The plugin archive contains no Kotlin WinRT classes." }
        check(duplicates.isEmpty()) { "Classes must have a single owner in the plugin archive:\n${duplicates.joinToString("\n")}" }
    }
}
tasks.check { dependsOn(verifyPluginClassOwnership) }

tasks.named<KotlinCompile>("compileTestKotlin") {
    // The controlled development peer uses the runtime's real internal wire
    // reader. Keep it internal and grant friendship only to this test compiler.
    val models = project(":fir-adapter").tasks.named<Jar>("jar").flatMap { it.archiveFile }
    compilerOptions.freeCompilerArgs.add(models.map {
        "-Xfriend-paths=${layout.buildDirectory.dir("classes/kotlin/main").get().asFile.absolutePath},${it.asFile.absolutePath}"
    })
}

// IntelliJ replaces the source descriptor with patchPluginXml's output during
// processResources. Select distribution-specific dependencies before patching.
val preparePluginDescriptor by tasks.registering(Copy::class) {
    from("src/main/resources/META-INF/plugin.xml")
    into(layout.buildDirectory.dir("generated-plugin-descriptor"))
    inputs.property("androidStudioSdk", androidStudioSdk)
    filter { line -> if (line.contains("<!-- ANDROID_STUDIO_EXTENSION -->")) {
        if (androidStudioSdk) "    <depends optional=\"true\" config-file=\"kotlin-winrt-android-studio.xml\">org.jetbrains.android</depends>" else ""
    } else line }
}

tasks.named<PatchPluginXmlTask>("patchPluginXml") {
    dependsOn(preparePluginDescriptor)
    inputFile.set(layout.buildDirectory.file("generated-plugin-descriptor/plugin.xml"))
}

tasks.processResources {
    if (!androidStudioSdk) exclude("META-INF/kotlin-winrt-android-studio.xml")
}

val toolchainRepository = layout.projectDirectory.dir("../../.gradle/ide-toolchain/repository")
val bundleWinRTToolchain by tasks.registering(Zip::class) {
    val producer = gradle.includedBuild("winrt-toolchain")
    dependsOn(producer.task(":publishPluginMavenPublicationToIdeToolchainRepository"),
        producer.task(":publishKotlinWindowsToolkitPluginMarkerMavenPublicationToIdeToolchainRepository"))
    listOf("winrt-runtime", "winrt-authoring").forEach { module ->
        listOf("Jvm", "MingwX64", "KotlinMultiplatform").forEach { publication ->
            dependsOn(producer.task(":$module:publish${publication}PublicationToIdeToolchainRepository"))
        }
    }
    listOf("winrt-metadata", "winrt-generator", "winrt-compiler-plugin",
        "winrt-compiler-plugin:callsite-contract", "winrt-compiler-plugin:callsite-lowering").forEach {
        dependsOn(producer.task(":$it:publishMavenPublicationToIdeToolchainRepository"))
    }
    archiveFileName.set("toolchain.zip")
    destinationDirectory.set(layout.buildDirectory.dir("bundled-toolchain/templates"))
    from(toolchainRepository) { into("repository") }
    from(layout.projectDirectory.dir("../..")) {
        include("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")
    }
    // Keep just the most recently published timestamp for each SNAPSHOT, so
    // rebuilding distributions does not accumulate old toolchains in the ZIP.
    eachFile {
        if (relativePath.segments.firstOrNull() == "repository" && name.matches(Regex(".*-\\d{8}\\.\\d{6}-\\d+.*"))) {
            val metadata = file.parentFile.resolve("maven-metadata.xml")
            if (metadata.isFile) {
                val current = Regex("<value>([^<]+)</value>").find(metadata.readText())?.groupValues?.get(1)
                if (current != null && current !in name) exclude()
            }
        }
    }
}
tasks.processResources { from(bundleWinRTToolchain.map { it.destinationDirectory.dir("..") }) }

tasks.named<PrepareSandboxTask>("prepareTestSandbox") {
    // The installed unified IDEA also bundles commercial startup services.
    // Platform tests exercise only this plugin and its declared dependencies.
    disabledPlugins.add("com.intellij.modules.ultimate")
}

tasks.withType<Test>().configureEach {
    val pluginJar = tasks.named<ComposedJarTask>("composedJar")
    dependsOn(pluginJar)
    systemProperty("winrt.ide.pluginJar", pluginJar.flatMap { it.archiveFile }.get().asFile.absolutePath)
    // Native import indexes the real SDK and included toolchain alongside the
    // distribution's own plugins; a light fixture's 2 GiB budget is insufficient.
    if (providers.gradleProperty("winrt.ide.importProject").isPresent || providers.gradleProperty("winrt.ide.sdkPreviewProject").isPresent) maxHeapSize = "4g"
    // BasePlatformTestCase is JUnit 3; its runner reports JUnit 4 assumptions
    // as failures. Keep optional real-toolchain fixtures out until configured.
    if (!providers.gradleProperty("winrt.ide.importProject").isPresent) exclude("**/WinRTGradleImportTest.class")
    if (!providers.gradleProperty("winrt.ide.sdkPreviewProject").isPresent) exclude("**/WinRTPreviewLaunchIntegrationTest.class")
    if (!providers.gradleProperty("winrt.ide.templateOutput").isPresent) exclude("**/WinRTTemplateGenerationTest.class")
    if (!providers.gradleProperty("winrt.ide.xamlInput").isPresent || !providers.gradleProperty("winrt.ide.xamlCompiler").isPresent)
        exclude("**/WinRTXamlDocumentCompilerTest.class")
    if (!providers.gradleProperty("winrt.ide.xamlInput").isPresent) exclude("**/WinRTXamlSdkSemanticsTest.class")
    systemProperty("winrt.ide.toolchain", rootProject.projectDir.resolve("../..").canonicalPath)
    providers.gradleProperty("winrt.ide.templateOutput").orNull?.let { systemProperty("winrt.ide.templateOutput", it) }
    providers.gradleProperty("winrt.ide.xamlInput").orNull?.let { systemProperty("winrt.ide.xamlInput", it) }
    providers.gradleProperty("winrt.ide.xamlCompiler").orNull?.let { systemProperty("winrt.ide.xamlCompiler", it) }
    providers.gradleProperty("winrt.ide.hotReloadSession").orNull?.let { systemProperty("winrt.ide.hotReloadSession", it) }
    providers.gradleProperty("winrt.ide.previewSession").orNull?.let { systemProperty("winrt.ide.previewSession", it) }
    providers.gradleProperty("winrt.ide.sdkPreviewSession").orNull?.let { systemProperty("winrt.ide.sdkPreviewSession", it) }
    providers.gradleProperty("winrt.ide.sdkPreviewProject").orNull?.let { systemProperty("winrt.ide.sdkPreviewProject", it) }
    providers.gradleProperty("winrt.ide.previewOutput").orNull?.let { systemProperty("winrt.ide.previewOutput", it) }
    providers.gradleProperty("winrt.ide.uiScreenshotDirectory").orNull?.let { systemProperty("winrt.ide.uiScreenshotDirectory", it) }
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
