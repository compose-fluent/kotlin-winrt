import org.gradle.api.publish.maven.MavenPublication
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
    id("winrt.prebuilt-projection") apply false
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows.UI.Xaml metadata surface."

base {
    archivesName.set("winrt-projections-windows-ui-xaml")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsSdkVersion.get(), rootProject.version.toString()) })

version = projectionWindowsSdkArtifactVersion.get()
project(":winrt-projections:windows-sdk").version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-ui-xaml", version.toString())
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
val windowsAppSdkProjection = project(":winrt-projections:windows-app-sdk")
val generatedWinRTProjectionSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
val auditGeneratedWinRTProjectionOutput by tasks.registering(
    io.github.composefluent.winrt.build.ValidatePrebuiltProjectionOutputTask::class,
) {
    group = "verification"
    description = "Audits Windows.UI.Xaml projection output and cross-artifact class ownership."
    dependsOn("generateWinRTProjections", "compileKotlinJvm")
    dependsOn(windowsSdkProjection.tasks.named("compileKotlinJvm"))
    dependsOn(":winrt-projections:windows-app-sdk:compileKotlinJvm")
    generatedSourcesDirectory.set(generatedWinRTProjectionSources)
    compiledClassesDirectories.from(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
    crossArtifactClassOwners.set(listOf(project.name, windowsSdkProjection.name, windowsAppSdkProjection.name))
    crossArtifactClassDirectories.from(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        windowsSdkProjection.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        windowsAppSdkProjection.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
    )
}

tasks.named("check") {
    dependsOn(auditGeneratedWinRTProjectionOutput)
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    namespace("Windows.UI.Xaml")
    excludeNamespace("Windows.UI.Xaml.Controls.Maps")
    excludeType("Windows.UI.Xaml.Data.BindableAttribute")
    excludeType("Windows.UI.Xaml.Markup.ContentPropertyAttribute")
    excludeType("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
    excludeAdditionNamespace("Windows.UI.Xaml.Media.Animation")
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
