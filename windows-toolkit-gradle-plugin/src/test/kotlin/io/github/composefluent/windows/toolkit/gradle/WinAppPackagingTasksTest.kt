package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.runtime.WinAppPackageIdentity
import io.github.composefluent.winrt.runtime.WindowsAppSdkDeploymentMode
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.*
import org.junit.Test

class WinAppPackagingTasksTest {
    // The CsWinRT WinUI sample and WindowsAppSDK.SelfContained.targets separate deployment
    // payload selection from projection generation. All formats must use the same Kotlin binary.
    @Test
    fun explicit_formats_override_only_packaging_and_keep_the_dsl_default() {
        val project = ProjectBuilder.builder().withName("formats").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val extension = project.extensions.getByType(WindowsExtension::class.java)
        extension.application { options ->
            options.packageType.set(WindowsPackageType.None)
            options.selfContained()
            options.generatePackage.set(false)
            options.packageOutputFile.set(project.layout.buildDirectory.file("custom/default.msix"))
        }
        val stages = WinAppPackagingFormat.entries.map { format ->
            val stage = stage(project, format, "JvmMain")
            val runtime = project.tasks.getByName("stageWindowsPackageRuntimeAssets${format.name}JvmMain") as StageWindowsPackageRuntimeAssetsTask
            assertEquals(format.deployment == WindowsAppSdkDeployment.SelfContained, runtime.includeFrameworkRuntimeAssets.get())
            assertEquals(format.packageType == WindowsPackageType.Packaged && format.deployment == WindowsAppSdkDeployment.FrameworkDependent,
                stage.includeFrameworkPackageDependencies.get())
            assertEquals(WindowsAppSdkDeploymentMode.valueOf(format.deployment.name), stage.windowsAppSdkDeployment.get())
            assertEquals(if (format.packageType == WindowsPackageType.None) WinAppPackageIdentity.Unpackaged else WinAppPackageIdentity.Packaged,
                stage.packageIdentity.get())
            assertEquals(runtime.outputDirectory.get(), stage.runtimeAssetsDirectory.get())
            assertTrue(stage.outputDirectory.get().asFile.path.contains(format.directoryName))
            val packageTask = project.tasks.getByName("packageWinApp${format.name}JvmMain")
            assertTrue(stage in packageTask.taskDependencies.getDependencies(packageTask))
            if (packageTask is PackageWinAppTask) {
                assertEquals(stage.outputDirectory.get(), packageTask.packageDirectory.get())
                assertTrue(packageTask.generatePackage.get())
                assertTrue(packageTask.outputFile.get().asFile.name.contains(format.directoryName))
                assertEquals(format.deployment == WindowsAppSdkDeployment.SelfContained, packageTask.selfContained.get())
            }
            stage
        }
        assertEquals(4, stages.map { it.outputDirectory.get() }.toSet().size)
        assertEquals(4, stages.map { it.resourceResolutionReport.get() }.toSet().size)
        val default = project.tasks.getByName("packageWinAppJvmMain") as PackageWinAppTask
        assertEquals(WindowsPackageType.None.name, default.packageType.get())
        assertFalse(default.generatePackage.get())
        assertEquals(project.file("build/custom/default.msix"), default.outputFile.get().asFile)
        assertEquals(WindowsPackageType.None, extension.application.packageType.get())
        assertEquals(WindowsAppSdkDeployment.SelfContained, extension.application.windowsAppSdkDeployment.get())
    }

    @Test
    fun all_native_formats_share_link_output_and_one_entry_per_executable() {
        val project = ProjectBuilder.builder().withName("native-formats").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64().binaries.executable()
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val extension = project.extensions.getByType(WindowsExtension::class.java)
        extension.application { it.mainClass.set("sample.MainKt") }
        (project as ProjectInternal).evaluate()
        val link = project.tasks.getByName("linkReleaseExecutableMingwX64")
        val defaultStage = project.tasks.getByName("stageWinAppPackageMingwX64MainReleaseExecutable") as StageWinAppPackageTask
        WinAppPackagingFormat.entries.forEach { format ->
            val stage = stage(project, format, "MingwX64MainReleaseExecutable")
            assertTrue(link in stage.taskDependencies.getDependencies(stage))
            assertEquals(defaultStage.rootPackagePayloadFiles.files, stage.rootPackagePayloadFiles.files)
        }
        assertEquals(2, project.tasks.withType(GenerateWinAppMingwEntryTask::class.java).size)
        val entry = project.tasks.getByName("generateWinAppMingwEntryMingwX64MainReleaseExecutable") as GenerateWinAppMingwEntryTask
        entry.generate()
        fun source() = entry.outputDirectory.get().asFile.walkTopDown().single { it.extension == "kt" }.readText()
        val initialSource = source()
        val inputs = entry.inputs.properties
        extension.application.packageType.set(WindowsPackageType.None)
        extension.application.selfContained()
        entry.generate()
        assertEquals(initialSource, source())
        assertEquals(inputs, entry.inputs.properties)
        assertFalse(initialSource.contains("WinAppPackageIdentity."))
        assertFalse(initialSource.contains("WindowsAppSdkDeploymentMode."))
    }

    @Test
    fun jvm_formats_share_launcher_and_runtime_image_without_the_default_host_layout() {
        val project = ProjectBuilder.builder().withName("jvm-formats").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { it.mainClass.set("sample.Main") }
        val stages = WinAppPackagingFormat.entries.map { stage(project, it, "JvmMain") }
        assertEquals(1, stages.map { it.jvmLauncherExecutable.get() }.toSet().size)
        assertEquals(1, stages.map { it.jvmRuntimeImageDirectory.get() }.toSet().size)
        val defaultHost = project.tasks.getByName("buildWinAppHostJvmMain")
        stages.forEach { assertFalse(defaultHost in it.taskDependencies.getDependencies(it)) }
        val launcher = project.tasks.getByName("compileWinAppLauncherJvmMain") as BuildWinAppHostTask
        val initialInputs = launcher.inputs.properties
        project.extensions.getByType(WindowsExtension::class.java).application.selfContained()
        project.extensions.getByType(WindowsExtension::class.java).application.packageType.set(WindowsPackageType.None)
        assertEquals(initialInputs, launcher.inputs.properties)
    }

    @Test
    fun named_applications_have_format_aggregates_and_isolated_package_outputs() {
        val project = ProjectBuilder.builder().withName("named-formats").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { options ->
            listOf("first", "second").forEach { name -> options.variants.create(name) { it.variant("jvm:main") } }
        }
        WinAppPackagingFormat.entries.forEach { format ->
            val aggregate = project.tasks.getByName("packageWinApp${format.name}")
            assertEquals(setOf("packageWinApp${format.name}First", "packageWinApp${format.name}Second"),
                aggregate.taskDependencies.getDependencies(aggregate).map { it.name }.toSet())
            assertNotEquals(stage(project, format, "First").outputDirectory.get(), stage(project, format, "Second").outputDirectory.get())
        }
    }

    private fun stage(project: Project, format: WinAppPackagingFormat, suffix: String) =
        project.tasks.getByName("stageWinAppPackage${format.name}$suffix") as StageWinAppPackageTask
}
