import org.gradle.api.publish.maven.MavenPublication
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
    id("winrt.prebuilt-projection") apply false
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Microsoft WebView2 Core metadata surface."

base {
    archivesName.set("winrt-projections-windows-webview2")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsSdkVersion.get(), rootProject.version.toString()) })
val projectionWebView2Version = providers.gradleProperty("kotlinWinRT.projections.webView2Version")
    .orElse("1.0.3719.77")
val projectionWebView2ArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.webView2ArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWebView2Version.get(), rootProject.version.toString()) })

version = projectionWebView2ArtifactVersion.get()
project(":winrt-projections:windows-sdk").version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-webview2", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

evaluationDependsOn(":winrt-projections:windows-sdk")

dependencies {
    commonMainApi(project(":winrt-projections:windows-sdk"))
    add("kotlinWinRTLibraryDependencyIdentity", project(":winrt-projections:windows-sdk"))
}

publishing {
    publications.withType(MavenPublication::class.java)
        .matching { publication -> publication.name == "kotlinMultiplatform" }
        .configureEach {
            pom.withXml {
                val dependencies = asElement().getElementsByTagName("dependency")
                (0 until dependencies.length)
                    .map { index -> dependencies.item(index) as Element }
                    .firstOrNull { dependency ->
                        dependency.getElementsByTagName("artifactId").item(0)?.textContent ==
                            "winrt-projections-windows-sdk"
                    }
                    ?.getElementsByTagName("scope")
                    ?.item(0)
                    ?.apply { textContent = "compile" }
            }
        }
}

val windowsSdkProjection = project(":winrt-projections:windows-sdk")
val generatedWinRTProjectionSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
val auditGeneratedWinRTProjectionOutput by tasks.registering(
    io.github.composefluent.winrt.build.ValidatePrebuiltProjectionOutputTask::class,
) {
    group = "verification"
    description = "Audits WebView2 Core projection output and cross-artifact class ownership."
    dependsOn("generateWinRTProjections", "compileKotlinJvm")
    dependsOn(windowsSdkProjection.tasks.named("compileKotlinJvm"))
    generatedSourcesDirectory.set(generatedWinRTProjectionSources)
    compiledClassesDirectories.from(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
    crossArtifactClassOwners.set(listOf(project.name, windowsSdkProjection.name))
    crossArtifactClassDirectories.from(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        windowsSdkProjection.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
    )
}

tasks.named("check") {
    dependsOn(auditGeneratedWinRTProjectionOutput)
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    nugetPackage("Microsoft.Web.WebView2", projectionWebView2Version.get()) {
        generateProjection = true
    }
    excludeNamespace("Windows")
    namespace("Microsoft.Web.WebView2.Core")
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
