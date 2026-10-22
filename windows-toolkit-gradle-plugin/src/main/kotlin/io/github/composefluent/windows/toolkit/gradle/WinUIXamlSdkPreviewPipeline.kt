package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import io.github.composefluent.winrt.runtime.XamlFramework
import io.github.composefluent.winrt.runtime.WindowsAppSdkDeploymentMode
import java.security.MessageDigest

/** No application source, compilation, XAMLC, authoring host or packaging edge. */
internal fun configureWinUIXamlSdkPreview(
    project: Project,
    extension: WindowsExtension,
    projection: TaskProvider<GenerateWinRTProjectionsTask>,
    compilerClasspath: FileCollection,
    compilerPluginClasspath: FileCollection,
    runtimeClasspath: FileCollection,
    nativeLibraries: FileCollection,
    nativeCompilerHome: Provider<String>,
    sdkPackages: Provider<List<String>>,
    sdkRegistryRoots: Provider<List<String>>,
    javaHome: Provider<String>,
    runtimeIdentifier: String,
) {
    val framework = extension.xaml.framework
    val previewRuntimeIdentifier = framework.map { if (it == XamlFramework.UWP) "win-x64" else runtimeIdentifier }
    val applicationRestore = project.tasks.named("restoreWinAppDependencies", RestoreWinAppDependenciesTask::class.java)
    val configuration = project.tasks.register("generateWinRTXamlSdkPreviewConfiguration", GenerateWinAppConfigurationTask::class.java) { task ->
        task.nugetPackages.set(sdkPackages)
        task.outputFile.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/restore/winapp.yaml"))
    }
    val restore = project.tasks.register("restoreWinRTXamlSdkPreviewDependencies", RestoreWinAppDependenciesTask::class.java) { task ->
        val settings = applicationRestore.get()
        task.configurationFile.set(configuration.flatMap { it.outputFile })
        task.winAppDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/restore/.winapp"))
        task.winmdLockFile.set(task.winAppDirectory.file("winmds.lock.json"))
        task.nugetPackages.set(sdkPackages)
        task.restoreBaseDirectory.set(settings.restoreBaseDirectory)
        task.nugetConfigFile.set(settings.nugetConfigFile)
        task.nugetConfigHierarchyFiles.from(settings.nugetConfigHierarchyFiles)
        task.winAppCliExecutable.set(settings.winAppCliExecutable)
        task.winAppCliVersion.set(settings.winAppCliVersion)
        task.winAppCliPackageSha512.set(settings.winAppCliPackageSha512)
        task.winAppCliCacheDirectory.set(settings.winAppCliCacheDirectory)
        task.offline.set(project.gradle.startParameter.isOffline)
        task.includeNativeBuildFiles.set(false)
    }
    projection.configure { it.winAppRestoreLockFiles.setFrom(restore.flatMap { task -> task.winmdLockFile }) }
    val compile = project.tasks.register("buildWinRTXamlSdkPreview", BuildWinUIXamlSdkPreviewTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Prepares a cached SDK-only XAML designer without compiling the application."
        task.projectionSources.set(projection.flatMap { it.outputDirectory })
        task.compilerClasspath.from(compilerClasspath)
        task.compilerPluginClasspath.from(compilerPluginClasspath)
        task.runtimeClasspath.from(runtimeClasspath)
        task.javaHome.set(javaHome)
        task.javaMajor.set(extension.application.jvmToolchainVersion)
        task.cacheDirectory.set(sharedWinRTCacheDirectory(project, "xaml-design-hosts").toFile())
        task.outputJar.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/KotlinWinUIXamlSdkPreview.jar"))
        task.metadataReferencesFile.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/references.json"))
        task.onlyIf { framework.get() == XamlFramework.WinUI }
    }
    val nativeCompile = project.tasks.register("buildWinRTXamlSdkPreviewNative", BuildWinUIXamlNativeSdkPreviewTask::class.java) { task ->
        task.projectionSources.set(projection.flatMap { it.outputDirectory })
        task.compilerHome.set(nativeCompilerHome)
        task.compilerClasspath.from(nativeCompilerHome.map { java.io.File(it, "konan/lib/kotlin-native-compiler-embeddable.jar") })
        task.compilerPlatformFiles.from(nativeCompilerHome.map { home ->
            listOf(java.io.File(home, "konan/konan.properties"), java.io.File(home, "klib/common/stdlib"))
        })
        task.compilerPluginClasspath.from(compilerPluginClasspath)
        task.runtimeLibraries.from(nativeLibraries)
        task.javaHome.set(javaHome)
        task.cacheDirectory.set(sharedWinRTCacheDirectory(project, "xaml-native-design-hosts").toFile())
        task.executable.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/executable/KotlinWinUIXamlPreview.exe"))
        task.metadataReferencesFile.set(project.layout.buildDirectory.file("kotlin-winrt/xaml-sdk-preview/native-references.json"))
        task.onlyIf { framework.get() == XamlFramework.UWP }
    }
    val assets = project.tasks.register("stageWinRTXamlSdkPreviewRuntime", StageWindowsPackageRuntimeAssetsTask::class.java) { task ->
        task.xamlFramework.set(framework)
        // Authoring validation attaches only to a stage that owns that Kotlin
        // compilation. The SDK stage owns this fixed SDK build instead.
        task.applicationCompilationTasks.set(framework.map {
            setOf(if (it == XamlFramework.UWP) nativeCompile.name else compile.name)
        })
        task.xamlPreparedMetadataManifest.set(projection.flatMap { it.outputDirectory.file("kotlin-winrt-metadata/resolved-sources.tsv") })
        task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/native"))
        task.nugetPackages.set(sdkPackages)
        task.runtimeAssets.set(emptyList())
        task.winAppRestoreLockFiles.from(restore.flatMap { it.winmdLockFile })
        task.winAppRuntimeAssetDirectories.from(restore.flatMap { it.winAppDirectory }.map { it.dir("bin") })
        // Self-contained WinUI resolves its own ms-appx theme resources through
        // the host's merged index, just as the desktop sample's SDK PRI inputs.
        // Merge SDK PRIs only; no project resource enumeration or XAMLC output.
        task.generateProjectPri.set(framework.map { it == XamlFramework.WinUI })
        task.projectPriIndexName.set("KotlinWinUIXamlPreview")
        task.enableDefaultProjectPriResources.set(false)
        task.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
        task.runtimeIdentifier.set(previewRuntimeIdentifier)
        task.executableBaseName.set("KotlinWinUIXamlPreview")
        task.windowsSdkVersion.set(extension.windowsSdkVersion)
        task.windowsSdkRegistryRoots.set(sdkRegistryRoots)
    }
    val uwpAssets = project.tasks.register("prepareWinRTXamlSdkPreviewPackageAssets", PrepareUwpSdkPreviewAssetsTask::class.java) { task ->
        val identityHash = MessageDigest.getInstance("SHA-256").digest(project.projectDir.absolutePath.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        task.identity.set("KotlinWinRT.UwpDesigner.$identityHash")
        task.architecture.set("x64")
        task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/package-assets"))
        task.onlyIf { framework.get() == XamlFramework.UWP }
    }
    val host = project.tasks.register("prepareWinRTXamlSdkPreview", BuildWinAppHostTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Prepares the isolated WinUI design process, using only SDK and runtime outputs."
        task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/host"))
        task.generatedSourceDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/launcher"))
        task.mainClass.set("io.github.composefluent.winrt.generated.xaml.KotlinWinUIXamlPreviewHost")
        task.executableBaseName.set("KotlinWinUIXamlPreview")
        task.runtimeClasspath.from(runtimeClasspath, compile.flatMap { it.outputJar })
        task.runtimeAssetsDirectory.from(assets.flatMap { it.outputDirectory })
        task.applicationModel.set(WindowsApplicationModel.Desktop)
        task.xamlFramework.set(XamlFramework.WinUI)
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
        task.onlyIf { framework.get() == XamlFramework.WinUI }
        task.doFirst { currentTask ->
            val currentHost = currentTask as BuildWinAppHostTask
            check(currentHost.runtimeAssetsDirectory.files.any { it.resolve("resources.pri").isFile }) {
                "The WinUI designer requires its merged SDK theme resources. Install the selected Windows SDK " +
                    "${currentHost.windowsSdkVersion.get()} including makepri.exe, then retry preview preparation."
            }
        }
    }
    project.tasks.register("runWinRTXamlSdkPreview", RunWinAppHostTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Opens the SDK-only XAML designer; does not build or run project code."
        task.hostExecutable.set(host.flatMap { it.outputDirectory.file("KotlinWinUIXamlPreview.exe") })
        task.workingDirectory.set(host.flatMap { it.outputDirectory })
        task.supportsXamlHotReload.set(true)
        task.sdkPreview.set(true)
        task.designPreview.set(true)
        task.onlyIf { framework.get() == XamlFramework.WinUI }
    }
    val nativeHost = project.tasks.register("prepareWinRTXamlSdkPreviewNative", org.gradle.api.tasks.Sync::class.java) { task ->
        task.from(assets.flatMap { it.outputDirectory }, uwpAssets.flatMap { it.outputDirectory }, nativeCompile.flatMap { it.executable })
        task.into(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/native-host"))
    }
    val packageStage = project.tasks.register("stageWinRTXamlSdkPreviewPackage", StageWinAppPackageTask::class.java) { task ->
        task.applicationModel.set(WindowsApplicationModel.UWP)
        task.xamlFramework.set(XamlFramework.UWP)
        task.windowsAppSdkDeployment.set(WindowsAppSdkDeploymentMode.None)
        task.runtimeAssetsDirectory.set(project.layout.dir(nativeHost.map { it.destinationDir }))
        task.winAppRestoreLockFiles.from(restore.flatMap { it.winmdLockFile })
        task.outputDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/package"))
        task.executableBaseName.set("KotlinWinUIXamlPreview")
        task.appxManifestFiles.from(uwpAssets.flatMap { it.outputDirectory.file("AppxManifest.xml") })
        task.enableDefaultProjectPriResources.set(false)
        task.minWindowsVersion.set("10.0.19041.0")
        task.runtimeIdentifier.set(previewRuntimeIdentifier)
        task.windowsSdkVersion.set(extension.windowsSdkVersion)
        task.windowsSdkRegistryRoots.set(sdkRegistryRoots)
        task.onlyIf { framework.get() == XamlFramework.UWP }
    }
    project.tasks.register("runWinRTXamlSdkPreviewUwp", RunWinAppPackageTask::class.java) { task ->
        task.group = "kotlin-winrt"
        task.description = "Opens an isolated UWP SDK-only designer without compiling project code."
        task.applicationModel.set(WindowsApplicationModel.UWP)
        task.packageDirectory.set(packageStage.flatMap { it.outputDirectory })
        task.deploymentDirectory.set(project.layout.buildDirectory.dir("kotlin-winrt/xaml-sdk-preview/AppX"))
        task.hostExecutable.set(task.deploymentDirectory.file("KotlinWinUIXamlPreview.exe"))
        task.runtimeIdentifier.set(previewRuntimeIdentifier)
        task.dependencyLockFiles.from(restore.flatMap { it.winmdLockFile })
        task.dependencyPackageSpecs.set(sdkPackages)
        task.supportsXamlHotReload.set(true)
        task.sdkPreview.set(true)
        task.designPreview.set(true)
        task.onlyIf { framework.get() == XamlFramework.UWP }
    }
}
