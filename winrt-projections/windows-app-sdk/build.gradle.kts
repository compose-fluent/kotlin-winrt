import org.gradle.api.publish.maven.MavenPublication
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
    id("winrt.prebuilt-projection") apply false
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows App SDK and WinUI metadata surface."

base {
    archivesName.set("winrt-projections-windows-app-sdk")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsSdkVersion.get(), rootProject.version.toString()) })
val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkVersion")
    .orElse("2.1.3")
val projectionWindowsAppSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsAppSdkVersion.get(), rootProject.version.toString()) })

version = projectionWindowsAppSdkArtifactVersion.get()
project(":winrt-projections:windows-sdk").version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-app-sdk", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

evaluationDependsOn(":winrt-projections:windows-sdk")
evaluationDependsOn(":winrt-projections:windows-webview2")

dependencies {
    commonMainApi(project(":winrt-projections:windows-sdk"))
    commonMainApi(project(":winrt-projections:windows-webview2"))
    add("kotlinWinRTLibraryDependencyIdentity", project(":winrt-projections:windows-sdk"))
    add("kotlinWinRTLibraryDependencyIdentity", project(":winrt-projections:windows-webview2"))
}

publishing {
    publications.withType(MavenPublication::class.java)
        .matching { publication -> publication.name == "kotlinMultiplatform" }
        .configureEach {
            pom.withXml {
                val dependencies = asElement().getElementsByTagName("dependency")
                (0 until dependencies.length)
                    .map { index -> dependencies.item(index) as Element }
                    .filter { dependency ->
                        dependency.getElementsByTagName("artifactId").item(0)?.textContent in setOf(
                            "winrt-projections-windows-sdk",
                            "winrt-projections-windows-webview2",
                        )
                    }
                    .forEach { dependency ->
                        dependency.getElementsByTagName("scope").item(0)?.textContent = "compile"
                    }
            }
        }
}

val windowsSdkProjection = project(":winrt-projections:windows-sdk")
val windowsWebView2Projection = project(":winrt-projections:windows-webview2")
val windowsUiXamlProjection = project(":winrt-projections:windows-ui-xaml")
val generatedWinRTProjectionSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
val auditGeneratedWinRTProjectionOutput by tasks.registering(
    io.github.composefluent.winrt.build.ValidatePrebuiltProjectionOutputTask::class,
) {
    group = "verification"
    description = "Audits Windows App SDK projection output and cross-artifact class ownership."
    dependsOn("generateWinRTProjections", "compileKotlinJvm")
    dependsOn(windowsSdkProjection.tasks.named("compileKotlinJvm"))
    dependsOn(windowsWebView2Projection.tasks.named("compileKotlinJvm"))
    dependsOn(":winrt-projections:windows-ui-xaml:compileKotlinJvm")
    generatedSourcesDirectory.set(generatedWinRTProjectionSources)
    compiledClassesDirectories.from(layout.buildDirectory.dir("classes/kotlin/jvm/main"))
    crossArtifactClassOwners.set(
        listOf(project.name, windowsSdkProjection.name, windowsWebView2Projection.name, windowsUiXamlProjection.name),
    )
    crossArtifactClassDirectories.from(
        layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        windowsSdkProjection.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        windowsWebView2Projection.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
        windowsUiXamlProjection.layout.buildDirectory.dir("classes/kotlin/jvm/main"),
    )
}

tasks.named("check") {
    dependsOn(auditGeneratedWinRTProjectionOutput)
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    nugetPackage("Microsoft.WindowsAppSDK", projectionWindowsAppSdkVersion.get()) {
        generateProjection = true
    }
    excludeNamespace("Windows")
    excludeNamespace("Microsoft.Windows.Internal")
    excludeNamespace("Microsoft.Windows.AI.GenerativeInternal")
    excludeNamespace("Microsoft.Windows.Management.Deployment")
    excludeNamespace("Microsoft.Graphics.ImagingInternal")
    excludeNamespace("Microsoft.Graphics.Internal.Imaging")
    excludeNamespace("Microsoft.Web.WebView2")
    excludeNamespace("Microsoft.UI.Composition.SystemBackdrops")
    namespace("Microsoft")
    type("Windows.UI.Xaml.Interop.Type")
    type("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction")
    type("Windows.UI.Xaml.Markup.ContentPropertyAttribute")
    type("Windows.UI.Xaml.StyleTypedPropertyAttribute")
    type("Windows.UI.Xaml.TemplatePartAttribute")
    type("Windows.UI.Xaml.TemplateVisualStateAttribute")
    type("Windows.UI.Xaml.Data.BindableAttribute")
    type("Windows.UI.Xaml.Markup.FullXamlMetadataProviderAttribute")
    type("Windows.UI.Xaml.Markup.MarkupExtensionReturnTypeAttribute")
    type("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
    type("Windows.UI.Xaml.Media.Animation.IndependentlyAnimatableAttribute")
    // CsWinRT's standalone test projection consumes the official Microsoft.WinUI assembly for this
    // control. Kotlin owns its WinUI projection, so emit the control while keeping WebView2 Core in
    // the separately published windows-webview2 artifact.
    excludeType("Microsoft.UI.Xaml.Controls.IWebView")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.IWebView")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.WebView")
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
