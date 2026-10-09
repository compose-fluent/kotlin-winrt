package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

/** No application source, compilation, XAMLC, authoring host or packaging edge. */
internal fun configureWinRTXamlSdkPreview(
    project: Project,
    extension: WindowsExtension,
    projection: TaskProvider<GenerateWinRTProjectionsTask>,
    compilerClasspath: FileCollection,
    compilerPluginClasspath: FileCollection,
    runtimeClasspath: FileCollection,
    sdkPackages: Provider<List<String>>,
    sdkRegistryRoots: Provider<List<String>>,
    javaHome: Provider<String>,
    runtimeIdentifier: String,
) {
    val restore = project.tasks.named("restoreWinAppDependencies", RestoreWinAppDependenciesTask::class.java)
    val compile = project.tasks.register("buildWinRTXamlSdkPreview", BuildWinRTXamlSdkPreviewTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Prepares a cached SDK-only XAML designer without compiling the application."
        task.projectionSources.set(projection.flatMap { it.outputDirectory })
        task.compilerClasspath.from(compilerClasspath)
        task.compilerPluginClasspath.from(compilerPluginClasspath)
        task.runtimeClasspath.from(runtimeClasspath)
        task.javaHome.set(javaHome)
        task.javaMajor.set(extension.application.jvmToolchainVersion)
        task.cacheDirectory.set(sharedWinRTCacheDirectory(project, "xaml-design-hosts").toFile())
        task.outputJar.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/KotlinWinRTXamlSdkPreview.jar"))
        task.metadataReferencesFile.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/references.json"))
    }
    val assets = project.tasks.register("stageWinRTXamlSdkPreviewRuntime", StageWindowsPackageRuntimeAssetsTask::class.java) { task ->
        // Authoring validation attaches only to a stage that owns that Kotlin
        // compilation. The SDK stage owns this fixed SDK build instead.
        task.applicationCompilationTasks.set(setOf(compile.name))
        task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/native"))
        task.nugetPackages.set(sdkPackages)
        task.runtimeAssets.set(emptyList())
        task.winAppRestoreLockFiles.from(restore.flatMap { it.winmdLockFile })
        task.nugetPackageContentFiles.from(project.provider {
            existingWinAppPackageContentRoots(listOf(restore.get().winmdLockFile.get().asFile))
        })
        task.winAppRuntimeAssetDirectories.from(restore.flatMap { it.winAppDirectory }.map { it.dir("bin") })
        // Self-contained WinUI resolves its own ms-appx theme resources through
        // the host's merged index, just as the desktop sample's SDK PRI inputs.
        // Merge SDK PRIs only; no project resource enumeration or XAMLC output.
        task.generateProjectPri.set(true)
        task.projectPriIndexName.set("KotlinWinRTXamlPreview")
        task.enableDefaultProjectPriResources.set(false)
        task.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
        task.runtimeIdentifier.set(runtimeIdentifier)
        task.executableBaseName.set("KotlinWinRTXamlPreview")
        task.windowsSdkVersion.set(extension.windowsSdkVersion)
        task.windowsSdkRegistryRoots.set(sdkRegistryRoots)
    }
    val host = project.tasks.register("prepareWinRTXamlSdkPreview", BuildWinAppHostTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Prepares the isolated WinUI design process, using only SDK and runtime outputs."
        task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/host"))
        task.generatedSourceDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/launcher"))
        task.mainClass.set("io.github.composefluent.winrt.generated.xaml.KotlinWinRTXamlPreviewHost")
        task.executableBaseName.set("KotlinWinRTXamlPreview")
        task.runtimeClasspath.from(runtimeClasspath, compile.flatMap { it.outputJar })
        task.runtimeAssetsDirectory.from(assets.flatMap { it.outputDirectory })
        // A designer's identity is independent of the application's deployment
        // model. The application keeps its packaged/unpackaged configuration.
        task.packageType.set(WindowsPackageType.None.name)
        // This private host owns the restored SDK DLLs. Self-contained activation
        // supplies their manifest base directory and does not depend on a user's
        // application package or a separately installed framework runtime.
        task.windowsAppSdkDeployment.set(WindowsAppSdkDeployment.SelfContained)
        task.jvmRuntimeMode.set(WinAppJvmRuntimeMode.External.name)
        task.externalJvmHome.set(javaHome)
        task.javaHome.set(javaHome)
        task.expectedJavaMajor.set(extension.application.jvmToolchainVersion)
        task.runtimeIdentifier.set(runtimeIdentifier)
        task.windowsSdkVersion.set(extension.windowsSdkVersion)
        task.windowsSdkRegistryRoots.set(sdkRegistryRoots)
        task.doFirst {
            check(assets.get().outputDirectory.file("resources.pri").get().asFile.isFile) {
                "The WinUI designer requires its merged SDK theme resources. Install the selected Windows SDK " +
                    "${task.windowsSdkVersion.get()} including makepri.exe, then retry preview preparation."
            }
        }
    }
    project.tasks.register("runWinRTXamlSdkPreview", RunWinAppHostTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Opens the SDK-only XAML designer; does not build or run project code."
        task.hostExecutable.set(host.flatMap { it.outputDirectory.file("KotlinWinRTXamlPreview.exe") })
        task.workingDirectory.set(host.flatMap { it.outputDirectory })
        task.supportsXamlHotReload.set(true)
        task.sdkPreview.set(true)
        task.designPreview.set(true)
    }
}
