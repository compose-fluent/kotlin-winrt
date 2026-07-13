import io.github.composefluent.winrt.gradle.ValidateSplitProjectionPublicationTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.mavenPublish) apply false
    id("io.github.compose-fluent.winrt") apply false
}

val releaseTagRegex = Regex("v\\d+\\.\\d+\\.\\d+(-.*)?")

/**
 * Resolves the project version:
 * - Release: read git tag name from CI environment (`GITHUB_REF_TYPE=tag`, `GITHUB_REF_NAME=vX.Y.Z`)
 *   or explicit `-Pwinrt.releaseTag=vX.Y.Z`, then strip leading `v`.
 * - Otherwise: `<winrt.baseVersion>-SNAPSHOT`.
 */
fun resolveVersion() = providers
    .gradleProperty("winrt.baseVersion")
    .orElse("0.1.0")
    .zip(
        providers
            .environmentVariable("GITHUB_REF_TYPE")
            .zip(providers.environmentVariable("GITHUB_REF_NAME")) { refType, refName ->
                if (refType == "tag") refName else ""
            }
            .orElse(providers.gradleProperty("winrt.releaseTag").orElse("")),
    ) { baseVersion, releaseTag ->
        if (releaseTag.isNotBlank() && releaseTag.matches(releaseTagRegex)) {
            releaseTag.removePrefix("v")
        } else {
            "$baseVersion-SNAPSHOT"
        }
    }

val winrtVersion = resolveVersion().get()

allprojects {
    group = "io.github.compose-fluent"
    version = winrtVersion

    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        minHeapSize = "64m"
        maxHeapSize = "128m"
        jvmArgs("-XX:+UseSerialGC")
        systemProperty(
            "io.github.composefluent.winrt.enableProbe",
            providers.gradleProperty("io.github.composefluent.winrt.enableProbe").orNull ?: "false",
        )
        systemProperty(
            "io.github.composefluent.winrt.probeTarget",
            providers.gradleProperty("io.github.composefluent.winrt.probeTarget").orNull ?: "",
        )
        systemProperty(
            "io.github.composefluent.winrt.probeMode",
            providers.gradleProperty("io.github.composefluent.winrt.probeMode").orNull ?: "",
        )
        systemProperty(
            "io.github.composefluent.winrt.bootstrapDll",
            providers.systemProperty("io.github.composefluent.winrt.bootstrapDll").orNull ?: "",
        )
        systemProperty(
            "io.github.composefluent.winrt.windowsAppSdkRoot",
            providers.systemProperty("io.github.composefluent.winrt.windowsAppSdkRoot").orNull ?: "",
        )
    }
}

val validateWinRTGenerator by tasks.registering {
    group = "verification"
    description = "Runs the generator regression validation for the current WinRT slice."
    dependsOn(":winrt-generator:test")
}

val validateWinRTPluginGraph by tasks.registering {
    group = "verification"
    description = "Runs Gradle plugin graph validation, including TestKit and identity/resource wiring tests."
    dependsOn(validateWinRTGenerator)
    dependsOn(gradle.includedBuild("winrt-gradle-plugin").task(":test"))
}

val splitProjectionModules = listOf(
    ":winrt-projections:windows-webview2",
    ":winrt-projections:windows-ui-xaml",
    ":winrt-projections:windows-app-sdk",
)

val projectReviewRemediationRepository = layout.buildDirectory.dir("project-review-remediation-repository")
val projectReviewPublicationModules = listOf(
    ":winrt-runtime",
    ":winrt-metadata",
    ":winrt-authoring",
    ":winrt-compiler-plugin",
    ":winrt-projections:windows-sdk",
    *splitProjectionModules.toTypedArray(),
)
val projectReviewPublicationTaskName = "publishAllPublicationsToProjectReviewRemediationRepository"
val projectReviewPublicationTaskPaths = projectReviewPublicationModules.map { projectPath ->
    "$projectPath:$projectReviewPublicationTaskName"
}
val cleanProjectReviewRemediationRepository by tasks.registering(Delete::class) {
    delete(projectReviewRemediationRepository)
}

projectReviewPublicationModules.forEach { projectPath ->
    project(projectPath).plugins.withId("maven-publish") {
        project(projectPath).extensions.getByType(PublishingExtension::class.java).repositories.maven {
            name = "projectReviewRemediation"
            url = projectReviewRemediationRepository.get().asFile.toURI()
        }
        project(projectPath).tasks.matching { task -> task.name == projectReviewPublicationTaskName }.configureEach {
            dependsOn(cleanProjectReviewRemediationRepository)
        }
    }
}

val publishProjectReviewRemediationArtifacts by tasks.registering {
    dependsOn(cleanProjectReviewRemediationRepository)
    dependsOn(projectReviewPublicationTaskPaths)
}

fun registerPublishedProjectionConsumer(
    taskName: String,
    fixtureDirectory: String,
    coordinate: Provider<String>,
) = tasks.register(taskName, Exec::class) {
    group = "verification"
    description = "Compiles an isolated JVM/mingwX64 consumer from the published $coordinate coordinate."
    workingDir = file(fixtureDirectory)
    val gradleExecutable = requireNotNull(gradle.gradleHomeDir).resolve("bin/gradle.bat")
    val nugetRootInitScript = rootProject.file("gradle/nuget-root.init.gradle")
    val nugetPackagesRoot = providers.environmentVariable("NUGET_PACKAGES")
        .orElse(rootProject.layout.projectDirectory.dir(".gradle/nuget-packages").asFile.absolutePath)
    commandLine(
        "cmd",
        "/c",
        gradleExecutable.absolutePath,
        "compileKotlinJvm",
        "compileKotlinMingwX64",
        "--no-daemon",
        "--max-workers=1",
        "--console=plain",
        "-I",
        nugetRootInitScript.absolutePath,
        "-Pkotlin.compiler.execution.strategy=in-process",
        "-PkotlinWinRT.test.repository=${projectReviewRemediationRepository.get().asFile.absolutePath}",
        "-PkotlinWinRT.test.coordinate=${coordinate.get()}",
    )
    environment("GRADLE_USER_HOME", gradle.gradleUserHomeDir.absolutePath)
    environment("KOTLIN_DAEMON_RUN_FILES_PATH", layout.buildDirectory.dir("kotlin-daemon").get().asFile.absolutePath)
    environment("NUGET_PACKAGES", nugetPackagesRoot.get())
    dependsOn(publishProjectReviewRemediationArtifacts)
}

val windowsSdkProjectionVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkArtifactVersion")
    .orElse(
        providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
            .orElse("10.0.26100.0")
            .map { metadataVersion -> projectionArtifactVersion(metadataVersion, winrtVersion) },
    )
val windowsAppSdkProjectionVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkArtifactVersion")
    .orElse(
        providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkVersion")
            .orElse("2.1.3")
            .map { metadataVersion -> projectionArtifactVersion(metadataVersion, winrtVersion) },
    )
val webView2ProjectionVersion = providers.gradleProperty("kotlinWinRT.projections.webView2ArtifactVersion")
    .orElse(
        providers.gradleProperty("kotlinWinRT.projections.webView2Version")
            .orElse("1.0.3719.77")
            .map { metadataVersion -> projectionArtifactVersion(metadataVersion, winrtVersion) },
    )

val validatePublishedWindowsUiXamlConsumer = registerPublishedProjectionConsumer(
    taskName = "validatePublishedWindowsUiXamlConsumer",
    fixtureDirectory = "winrt-projections/windows-ui-xaml-consumer-fixture",
    coordinate = windowsSdkProjectionVersion.map { version ->
        "io.github.compose-fluent:winrt-projections-windows-ui-xaml:$version"
    },
)
val validatePublishedWindowsWebView2Consumer = registerPublishedProjectionConsumer(
    taskName = "validatePublishedWindowsWebView2Consumer",
    fixtureDirectory = "winrt-projections/windows-webview2-consumer-fixture",
    coordinate = webView2ProjectionVersion.map { version ->
        "io.github.compose-fluent:winrt-projections-windows-webview2:$version"
    },
)
val validatePublishedWindowsAppSdkConsumer = registerPublishedProjectionConsumer(
    taskName = "validatePublishedWindowsAppSdkConsumer",
    fixtureDirectory = "winrt-projections/windows-app-sdk-consumer-fixture",
    coordinate = windowsAppSdkProjectionVersion.map { version ->
        "io.github.compose-fluent:winrt-projections-windows-app-sdk:$version"
    },
)

val validateWinRTSplitProjectionPublication by tasks.registering(ValidateSplitProjectionPublicationTask::class) {
    group = "verification"
    description = "Validates split projection API metadata and isolated JVM/mingwX64 consumer compilation."
    requiredApiDependencies.set(
        mapOf(
            "winrt-projections-windows-webview2" to "winrt-projections-windows-sdk",
            "winrt-projections-windows-ui-xaml" to "winrt-projections-windows-sdk",
            "winrt-projections-windows-app-sdk" to
                "winrt-projections-windows-sdk,winrt-projections-windows-webview2",
        ),
    )
    splitProjectionModules.forEach { projectPath ->
        val projectionProject = project(projectPath)
        dependsOn("$projectPath:generatePomFileForJvmPublication")
        dependsOn("$projectPath:generatePomFileForMingwX64Publication")
        dependsOn("$projectPath:generatePomFileForKotlinMultiplatformPublication")
        dependsOn("$projectPath:generateMetadataFileForKotlinMultiplatformPublication")
        dependsOn("$projectPath:auditGeneratedWinRTProjectionOutput")
        pomFiles.from(
            projectionProject.layout.buildDirectory.file("publications/jvm/pom-default.xml"),
            projectionProject.layout.buildDirectory.file("publications/mingwX64/pom-default.xml"),
            projectionProject.layout.buildDirectory.file("publications/kotlinMultiplatform/pom-default.xml"),
        )
        moduleMetadataFiles.from(
            projectionProject.layout.buildDirectory.file("publications/kotlinMultiplatform/module.json"),
        )
    }
    dependsOn(validatePublishedWindowsUiXamlConsumer)
    dependsOn(validatePublishedWindowsWebView2Consumer)
    dependsOn(validatePublishedWindowsAppSdkConsumer)
}

val validateWinRTProjectionCompile by tasks.registering {
    group = "verification"
    description = "Compiles plugin-generated projection output after generator and plugin validation."
    dependsOn(validateWinRTPluginGraph)
    dependsOn(":validateWinRTFullWindowsSdkProjectionGate")
    dependsOn(":winrt-projections:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:compileKotlinJvm")
    dependsOn(":winrt-projections:compileKotlinMingwX64")
    dependsOn(":winrt-projections:windows-sdk:compileKotlinJvm")
    dependsOn(":winrt-projections:windows-sdk:compileKotlinMingwX64")
    dependsOn(":winrt-projections:windows-webview2:compileKotlinJvm")
    dependsOn(":winrt-projections:windows-webview2:compileKotlinMingwX64")
    dependsOn(":winrt-projections:windows-webview2:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:windows-ui-xaml:compileKotlinJvm")
    dependsOn(":winrt-projections:windows-ui-xaml:compileKotlinMingwX64")
    dependsOn(":winrt-projections:windows-ui-xaml:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:windows-webview2:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:windows-app-sdk:compileKotlinJvm")
    dependsOn(":winrt-projections:windows-app-sdk:compileKotlinMingwX64")
    dependsOn(":winrt-projections:windows-app-sdk:auditGeneratedWinRTProjectionOutput")
    dependsOn(validateWinRTSplitProjectionPublication)
}

val validateWinRTMingwProjectionGate by tasks.registering {
    group = "verification"
    description = "Runs the lightweight generated projection gate for mingwX64 without compiling full prebuilt projection artifacts."
    dependsOn(":winrt-projections:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:compileKotlinJvm")
    dependsOn(":winrt-projections:compileKotlinMingwX64")
}

val validateWinRTFullWindowsSdkProjectionGate by tasks.registering {
    group = "verification"
    description = "Runs the root KMP generated projection gate against the full Windows SDK projection surface."
    dependsOn(":winrt-projections:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:compileKotlinJvm")
    dependsOn(":winrt-projections:compileKotlinMingwX64")
}

val validateWinRTMingwParity by tasks.registering {
    group = "verification"
    description = "Runs the current mingwX64 parity gate across runtime contracts, compiler lowering, and full Windows SDK projections."
    dependsOn(":winrt-runtime:compileKotlinMetadata")
    dependsOn(":winrt-runtime:jvmTest")
    dependsOn(":winrt-runtime:compileTestKotlinMingwX64")
    dependsOn(":winrt-runtime:mingwX64Test")
    dependsOn(":winrt-compiler-plugin:test")
    dependsOn(validateWinRTFullWindowsSdkProjectionGate)
}

val validateWinRTNativeAuthoringFixture by tasks.registering {
    group = "verification"
    description = "Builds a real mingwX64 authored component DLL and validates native exports plus dependency staging."
    dependsOn(":winrt-authoring:native-component-fixture:verifyNativeAuthoringComponentFixture")
    dependsOn(":winrt-authoring:native-consumer-fixture:verifyNativeAuthoringConsumerFixture")
}

val validateWinRTSampleSmoke by tasks.registering {
    group = "verification"
    description = "Runs sample smoke checks after projection validation."
    dependsOn(validateWinRTProjectionCompile)
    dependsOn(":winrt-samples:check")
    dependsOn(":winrt-samples:winui-kmp-app:check")
}

val validateWinRTNoWinUISampleMode by tasks.registering(Exec::class) {
    group = "verification"
    description = "Configures and compiles the real JVM/mingw sample with WinUI explicitly disabled."
    workingDir = rootDir
    val gradleExecutable = requireNotNull(gradle.gradleHomeDir).resolve("bin/gradle.bat")
    val nugetRootInitScript = rootProject.file("gradle/nuget-root.init.gradle")
    val nugetPackagesRoot = providers.environmentVariable("NUGET_PACKAGES")
        .orElse(rootProject.layout.projectDirectory.dir(".gradle/nuget-packages").asFile.absolutePath)
    commandLine(
        "cmd",
        "/c",
        gradleExecutable.absolutePath,
        ":winrt-samples:verifyWinRTSampleMode",
        ":winrt-samples:compileKotlinWinuiJvm",
        ":winrt-samples:compileKotlinMingwX64",
        "--no-daemon",
        "--max-workers=1",
        "--console=plain",
        "-I",
        nugetRootInitScript.absolutePath,
        "-Pkotlin.compiler.execution.strategy=in-process",
        "-PkotlinWinRT.samples.enableWinUI=false",
    )
    environment("GRADLE_USER_HOME", gradle.gradleUserHomeDir.absolutePath)
    environment("KOTLIN_DAEMON_RUN_FILES_PATH", layout.buildDirectory.dir("kotlin-daemon").get().asFile.absolutePath)
    environment("NUGET_PACKAGES", nugetPackagesRoot.get())
}

tasks.register("validateProjectReviewRemediation") {
    group = "verification"
    description = "Runs the complete project-review remediation gate without machine-dependent packaging smoke tasks."
    dependsOn(":winrt-runtime:jvmTest")
    dependsOn(":winrt-runtime:mingwX64Test")
    dependsOn(":winrt-metadata:test")
    dependsOn(":winrt-generator:test")
    dependsOn(":winrt-compiler-plugin:test")
    dependsOn(":winrt-authoring:jvmTest")
    dependsOn(":winrt-authoring:mingwX64Test")
    dependsOn(gradle.includedBuild("winrt-gradle-plugin").task(":test"))
    dependsOn(":winrt-projections:windows-ui-xaml:auditGeneratedWinRTProjectionOutput")
    dependsOn(":winrt-projections:windows-app-sdk:auditGeneratedWinRTProjectionOutput")
    dependsOn(validateWinRTSplitProjectionPublication)
    dependsOn(":winrt-samples:verifyWinRTSampleMode")
    dependsOn(":winrt-samples:compileKotlinWinuiJvm")
    dependsOn(":winrt-samples:compileKotlinMingwX64")
    dependsOn(validateWinRTNoWinUISampleMode)
}

fun projectionArtifactVersion(
    metadataVersion: String,
    kotlinWinRTVersion: String,
): String =
    if (kotlinWinRTVersion.endsWith("-SNAPSHOT")) {
        "$metadataVersion-kotlin-winrt-$kotlinWinRTVersion"
    } else {
        metadataVersion
    }

tasks.register("validateWinRTQueue16") {
    group = "verification"
    description = "Runs Queue 16 validation in reference-aligned order: generator, plugin graph, mingw parity, projections, samples."
    dependsOn(validateWinRTMingwParity)
    dependsOn(validateWinRTNativeAuthoringFixture)
    dependsOn(validateWinRTSampleSmoke)
}
