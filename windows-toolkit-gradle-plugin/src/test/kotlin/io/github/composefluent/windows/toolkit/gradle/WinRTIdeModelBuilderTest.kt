package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/** Validates the IDE boundary, not WinMD/projection policy owned by .cswinrt/src/cswinrt. */
class WinRTIdeModelBuilderTest {
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
        windows.application.appxManifest("src/desktopMain/appxResources/AppxManifest.xml")
        project.tasks.register("analyzeWinRTXamlFixture", CompileWinRTXamlTask::class.java) { task ->
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
        assertEquals(listOf("Microsoft.WindowsAppSDK"), model.nuGetPackages.map { it.id })
        assertEquals("2.2.0", model.nuGetPackages.single().version)
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
    }
}
