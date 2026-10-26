package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.internal.project.ProjectInternal
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Validates the IDE boundary, not WinMD/projection policy owned by .cswinrt/src/cswinrt. */
class WinRTIdeModelBuilderTest {
    @Test fun generated_roots_come_from_task_outputs_and_preserve_custom_sources() {
        val project = ProjectBuilder.builder().withName("generated-roots").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.mingwX64 { binaries { executable() } }
        (project as ProjectInternal).evaluate()
        val generator = project.tasks.register("customPreviewEntry", GenerateWinAppMingwEntryTask::class.java).get()
        generator.outputDirectory.set(project.layout.buildDirectory.dir("relocated-owned-output"))
        val sourceSet = kotlin.sourceSets.getByName("winuiMain")
        sourceSet.kotlin.srcDir(generator.outputDirectory)
        val userRoot = project.layout.buildDirectory.dir("generated/user-maintained").get().asFile
        sourceSet.kotlin.srcDir(userRoot)
        project.configurations.configureEach { configuration ->
            configuration.incoming.beforeResolve { error("IDE import resolved ${configuration.name}") }
        }
        val model = WinRTIdeModelBuilder().buildAll(WinRTIdeModel::class.java.name, project)
        val imported = model.sourceSets.single { it.name == "winuiMain" }
        assertTrue(imported.generatedKotlinRoots.contains(generator.outputDirectory.get().asFile.absolutePath))
        assertTrue(imported.kotlinRoots.contains(userRoot.absolutePath))
        assertFalse(imported.generatedKotlinRoots.contains(userRoot.absolutePath))
    }

    @Test fun uwp_sdk_designer_is_independent_of_native_application_compilation() {
        val project = ProjectBuilder.builder().withName("uwp-sdk-designer").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64 { binaries { executable() } }
        val windows = project.extensions.getByType(WindowsExtension::class.java)
        windows.application.applicationModel.set(WindowsApplicationModel.UWP)
        windows.application.mainClass.set("sample.MainKt")
        (project as ProjectInternal).evaluate()
        project.configurations.configureEach { configuration ->
            configuration.incoming.beforeResolve { error("IDE import resolved ${configuration.name}") }
        }
        val model = WinRTIdeModelBuilder().buildAll(WinRTIdeModel::class.java.name, project)
        val preview = requireNotNull(model.staticPreview)
        assertEquals("runWinRTXamlSdkPreviewUwp", preview.taskName)
        assertTrue(preview.executable.replace('\\', '/').endsWith("xaml-sdk-preview/AppX/KotlinWinUIXamlPreview.exe"))
        assertFalse(model.hotReloadLaunches.any { it.taskName == preview.taskName })
        assertFalse(model.runTasks.contains(preview.taskName))
        assertFalse(model.hotReloadLaunches.any { it.isDirectPreview })
        val sdk = project.tasks.named("generateWinRTXamlSdkPreviewProjections", GenerateWinRTProjectionsTask::class.java).get()
        assertEquals(io.github.composefluent.winrt.runtime.XamlFramework.UWP, sdk.xamlFramework.get())
        assertTrue(sdk.includeNamespaces.get().contains("Windows.UI.Xaml"))
        assertTrue(sdk.sourceRoots.isEmpty)
        val host = project.tasks.named("buildWinRTXamlSdkPreviewNative", BuildWinUIXamlNativeSdkPreviewTask::class.java).get()
        assertTrue(host.executable.get().asFile.name == "KotlinWinUIXamlPreview.exe")
        assertEquals("KotlinWinUIXamlPreview.exe", sdk.authoringTargetArtifactName.get())
        assertFalse(sdk.emitJvmAuthoringHostExports.get())
        val assets = project.tasks.named("prepareWinRTXamlSdkPreviewPackageAssets", PrepareUwpSdkPreviewAssetsTask::class.java).get()
        assets.prepare()
        val manifest = assets.outputDirectory.file("AppxManifest.xml").get().asFile.toPath()
        assertTrue(java.nio.file.Files.readString(manifest).contains("Capability Name=\"codeGeneration\""))
        assertTrue(ProjectPriManifestSupport.validatePackageManifest(manifest).isEmpty())
        assertTrue(ProjectPriManifestSupport.validatePackageManifestPayload(manifest, manifest.parent, setOf(java.nio.file.Path.of("KotlinWinUIXamlPreview.exe"))).isEmpty())
    }

    @Test
    fun hot_reload_exports_packaged_and_unpacked_jvm_and_native_launches() {
        val project = ProjectBuilder.builder().withName("packaged-app").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("desktop")
            mingwX64 { binaries { executable() } }
        }
        val application = project.extensions.getByType(WindowsExtension::class.java).application
        project.extensions.getByType(WindowsExtension::class.java).application {
            it.mainClass.set("sample.MainKt")
            it.packageType.set(WindowsPackageType.Packaged)
        }
        project.file("src/winuiMain/kotlin/Preview.xaml").apply {
            parentFile.mkdirs()
            writeText("<Page xmlns=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\"/>")
        }
        (project as ProjectInternal).evaluate()
        project.configurations.configureEach { configuration ->
            configuration.incoming.beforeResolve { error("IDE import resolved ${configuration.name}") }
        }
        val builder = WinRTIdeModelBuilder()
        val run = project.tasks.named("runWinAppPackageDesktopMain", RunWinAppPackageTask::class.java).get()
        assertTrue("Configured packaged JVM run must support Hot Reload", run.supportsXamlHotReload.get())
        val launches = builder.buildAll(WinRTIdeModel::class.java.name, project).hotReloadLaunches
        assertTrue(launches.any { it.taskName.startsWith("runWinAppPackageMingwX64") })
        assertTrue(launches.filter { it.taskName.startsWith("runWinAppPackageMingwX64") }.all {
            it.previewExecutable.isNotBlank() && it.previewExecutable != it.executable
        })
        val launch = launches.single { it.taskName == run.name }
        assertEquals("JVM launches must not offer Native debugging", "", launch.nativeCompilerHome)
        assertFalse(launch.isDirectPreview)
        assertTrue(launches.filter { it.taskName.startsWith("runWinAppPackageMingwX64") }.all { it.isDirectPreview })
        assertTrue(launches.filter { it.taskName.startsWith("runWinAppPackageMingwX64") }.all { it.nativeCompilerHome.isNotBlank() })
        launches.filter { it.taskName.startsWith("runWinAppPackage") }.forEach { launch ->
            val prepare = project.tasks.named(launch.developmentBuildTask, StageWinAppPackageTask::class.java).get()
            assertFalse(prepare.outputDirectory.get().asFile.absolutePath == launch.workingDirectory)
        }
        val runTasks = builder.buildAll(WinRTIdeModel::class.java.name, project).runTasks
        assertTrue(runTasks.toString(), runTasks.contains("runWinAppPackageDesktopMain"))
        assertTrue(runTasks.toString(), runTasks.any { it.startsWith("runWinAppPackageMingwX64") })
        assertFalse(runTasks.any { it.startsWith("runWinAppHost") || it.startsWith("runDebugExecutable") || "Preview" in it })
        assertEquals(run.name, launch.taskName)
        assertEquals(run.deploymentDirectory.file("packaged-app.exe").get().asFile.absolutePath, launch.executable)
        assertEquals(run.deploymentDirectory.get().asFile.absolutePath, launch.workingDirectory)
        assertNotEquals("A design session must discover its independent package process", launch.executable, launch.previewExecutable)
        assertEquals(project.layout.buildDirectory.file("kotlin-winrt/application-preview/desktop_main/AppX/packaged-app.exe").get().asFile.absolutePath,
            launch.previewExecutable)
        application.selfContained()
        assertTrue(builder.buildAll(WinRTIdeModel::class.java.name, project).hotReloadLaunches.isEmpty())
        assertTrue("WinApp CLI cannot launch self-contained packages, so IDE import must not offer them",
            builder.buildAll(WinRTIdeModel::class.java.name, project).runTasks.isEmpty())
        application.packageType.set(WindowsPackageType.None)
        assertTrue(
            builder.buildAll(WinRTIdeModel::class.java.name, project).hotReloadLaunches.any { it.taskName.startsWith("runWinAppNativeMingwX64") })
        val unpackaged = builder.buildAll(WinRTIdeModel::class.java.name, project).hotReloadLaunches.single { it.taskName == "runWinAppHostDesktopMain" }
        assertEquals(unpackaged.executable, unpackaged.previewExecutable)
        val unpackagedTasks = builder.buildAll(WinRTIdeModel::class.java.name, project).runTasks
        assertTrue(unpackagedTasks.contains("runWinAppHostDesktopMain"))
        assertTrue(unpackagedTasks.toString(), unpackagedTasks.any { it.startsWith("runDebugExecutable") })
        assertFalse(unpackagedTasks.any { it.startsWith("runWinAppPackage") })
        val nativeLaunches = builder.buildAll(WinRTIdeModel::class.java.name, project).hotReloadLaunches
            .filter { it.taskName.startsWith("runWinAppNative") }
        assertTrue(nativeLaunches.isNotEmpty())
        assertTrue(nativeLaunches.all { it.previewExecutable == it.executable })
        assertTrue(nativeLaunches.all { it.nativeCompilerHome.isNotBlank() })
        val nativeRuntimeStages = project.tasks.withType(StageWindowsPackageRuntimeAssetsTask::class.java)
            .filter { it.name.startsWith("stageWindowsPackageRuntimeAssetsMingwX64") }
        assertTrue(nativeRuntimeStages.isNotEmpty())
        assertTrue(nativeRuntimeStages.all { it.includeFrameworkRuntimeAssets.get() })
        application.frameworkDependent()
        assertTrue(nativeRuntimeStages.none { it.includeFrameworkRuntimeAssets.get() })
        application.mainClass.set("")
        assertTrue(builder.buildAll(WinRTIdeModel::class.java.name, project).hotReloadLaunches
            .filter { it.taskName.startsWith("runWinAppNative") }.all { it.previewExecutable.isEmpty() })
    }

    @Test
    fun exports_multiplatform_configuration_without_resolving_dependencies() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("io.github.compose-fluent.windows-toolkit")
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.jvm("desktop")
        kotlin.mingwX64("windowsNative")
        val shared = kotlin.sourceSets.getByName("winuiMain")
        shared.dependsOn(kotlin.sourceSets.getByName("commonMain"))
        kotlin.sourceSets.getByName("desktopMain").dependsOn(shared)
        val windows = project.extensions.getByType(WindowsExtension::class.java)
        windows.packageReferences.windowsSdk("10.0.26100.0")
        windows.packageReferences.nugetPackage("Microsoft.WindowsAppSDK", "2.2.0")
        windows.packageReferences.nugetPackage("Sample.ProjectDependency", "1.0.0")
        windows.application.appxManifest("src/desktopMain/appxResources/AppxManifest.xml")
        project.tasks.register("analyzeWinRTXamlFixture", CompileWinUIXamlTask::class.java) { task ->
            task.sourceRoots.from(project.file("src/desktopMain/kotlin"))
            task.outputDirectory.set(project.layout.buildDirectory.dir("ide-fixture/declarations"))
            task.compilerDirectory.set(project.layout.projectDirectory.dir("tools/xamlc"))
        }
        project.configurations.configureEach { configuration ->
            configuration.incoming.beforeResolve { error("IDE import resolved ${configuration.name}") }
        }

        val model = WinRTIdeModelBuilder().buildAll(WinRTIdeModel::class.java.name, project)
        assertTrue(model.isEnabled)
        assertEquals(WinRTIdeModel.SCHEMA_VERSION, model.schemaVersion)
        assertEquals("10.0.26100.0", model.windowsSdkVersion)
        assertEquals(listOf("desktop", "metadata", "windowsNative"), model.targets.map { it.name })
        assertEquals("jvm", model.targets.single { it.name == "desktop" }.platform)
        assertEquals("native", model.targets.single { it.name == "windowsNative" }.platform)
        assertEquals(listOf("Microsoft.WindowsAppSDK", "Sample.ProjectDependency"), model.nuGetPackages.map { it.id })
        assertEquals("2.2.0", model.nuGetPackages.first().version)
        assertEquals("runWinRTXamlSdkPreview", model.staticPreview!!.taskName)
        assertTrue(model.staticPreview!!.executable.endsWith("KotlinWinUIXamlPreview.exe"))
        assertFalse("The SDK designer must not become a user application run configuration",
            model.hotReloadLaunches.any { it.taskName == model.staticPreview!!.taskName })
        val sdk = project.tasks.named("generateWinRTXamlSdkPreviewProjections", GenerateWinRTProjectionsTask::class.java).get()
        assertTrue("The designer projection must never scan application source", sdk.sourceRoots.files.isEmpty())
        assertFalse(sdk.preparedMetadataManifest.isPresent)
        assertFalse(sdk.authoringCandidatesFile.isPresent)
        assertFalse(sdk.nugetPackages.get().any { it.startsWith("Sample.ProjectDependency") })
        assertEquals(setOf("buildWinRTXamlSdkPreview"), project.tasks.named("stageWinRTXamlSdkPreviewRuntime",
            StageWindowsPackageRuntimeAssetsTask::class.java).get().applicationCompilationTasks.get())
        val sdkAssets = project.tasks.named("stageWinRTXamlSdkPreviewRuntime", StageWindowsPackageRuntimeAssetsTask::class.java).get()
        assertTrue("WinUI's own theme PRIs must be merged for the isolated self-contained host", sdkAssets.generateProjectPri.get())
        assertFalse("Application resources must be read from source, not staged by an application build", sdkAssets.enableDefaultProjectPriResources.get())
        val desktop = model.sourceSets.single { it.name == "desktopMain" }
        assertEquals(
            listOf("commonMain", "winuiMain", "desktopMain"),
            desktop.appxResourceRoots.map { java.io.File(it).parentFile.name },
        )
        assertEquals(project.file("src/desktopMain/appxResources/AppxManifest.xml").path, model.manifestFiles.single())
        val xaml = model.xamlCompilations.single()
        assertEquals("analyzeWinRTXamlFixture", xaml.taskName)
        assertEquals(project.file("src/desktopMain/kotlin").path, xaml.sourceRoots.single())
        assertEquals(project.layout.buildDirectory.file("ide-fixture/declarations/declarations.json").get().asFile.path,
            xaml.declarationsFile)
        assertFalse(java.io.File(xaml.declarationsFile).exists())

        val bytes = ByteArrayOutputStream().also { output -> ObjectOutputStream(output).use { it.writeObject(model) } }
        val restored = ObjectInputStream(ByteArrayInputStream(bytes.toByteArray())).use { it.readObject() as WinRTIdeModel }
        assertEquals(model.projectDirectory, restored.projectDirectory)
        assertEquals(desktop.appxResourceRoots, restored.sourceSets.single { it.name == "desktopMain" }.appxResourceRoots)
        assertEquals(xaml.compilerDirectory, restored.xamlCompilations.single().compilerDirectory)
        assertEquals(model.staticPreview, restored.staticPreview)
        assertTrue("Library modules must not produce run configurations", model.runTasks.isEmpty())
    }

    @Test
    fun non_windows_projects_have_an_explicit_disabled_model() {
        val project = ProjectBuilder.builder().build()
        val builder = WinRTIdeModelBuilder()
        assertFalse(builder.canBuild("unrelated.Model"))
        val model = builder.buildAll(WinRTIdeModel::class.java.name, project)
        assertFalse(model.isEnabled)
        assertTrue(model.sourceSets.isEmpty())
        assertTrue(model.targets.isEmpty())
        assertTrue(model.nuGetPackages.isEmpty())
        assertEquals(null, model.staticPreview)
        assertTrue(model.runTasks.isEmpty())
    }
}
