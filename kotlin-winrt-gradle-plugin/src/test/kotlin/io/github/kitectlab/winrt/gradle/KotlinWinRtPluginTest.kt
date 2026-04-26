package io.github.kitectlab.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinWinRtPluginTest {
    @Test
    fun plugin_wires_extension_inputs_to_generation_task() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val extension = project.extensions.getByType(KotlinWinRtExtension::class.java)
        extension.namespace("Windows.Foundation")
        extension.type("Windows.Foundation.IStringable")
        extension.winmd("sdk+")
        extension.windowsSdk("10.0.26100.0", includeExtensions = true)
        extension.nugetExecutable.set("nuget.exe")
        extension.restoreNuGetPackages.set(false)
        extension.useNuGetCliGlobalPackages.set(false)
        extension.nugetGlobalPackagesRoots.add(project.layout.projectDirectory.dir("nuget-cache").asFile.absolutePath)
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260317003")

        val task = project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).get()

        assertEquals(listOf("Windows.Foundation"), task.includeNamespaces.get())
        assertEquals(listOf("Windows.Foundation.IStringable"), task.includeTypes.get())
        assertEquals(listOf("sdk+"), task.metadataInputs.get())
        assertEquals("10.0.26100.0", task.windowsSdkVersion.get())
        assertTrue(task.includeWindowsSdkExtensions.get())
        assertEquals("nuget.exe", task.nugetExecutable.get())
        assertEquals(false, task.restoreNuGetPackages.get())
        assertEquals(false, task.useNuGetCliGlobalPackages.get())
        assertEquals(listOf("Microsoft.WindowsAppSDK@1.8.260317003"), task.nugetPackages.get())
    }

    @Test
    fun role_plugins_mark_library_and_application_models() {
        val libraryProject = ProjectBuilder.builder().build()
        libraryProject.pluginManager.apply(KotlinWinRtLibraryPlugin::class.java)
        assertEquals("library", libraryProject.extensions.extraProperties["kotlinWinRtModel"])

        val applicationProject = ProjectBuilder.builder().build()
        applicationProject.pluginManager.apply(KotlinWinRtApplicationPlugin::class.java)
        assertEquals("application", applicationProject.extensions.extraProperties["kotlinWinRtModel"])
    }
}
