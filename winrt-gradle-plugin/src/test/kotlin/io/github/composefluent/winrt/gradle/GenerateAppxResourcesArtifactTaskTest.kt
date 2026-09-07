package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.util.zip.ZipFile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateAppxResourcesArtifactTaskTest {
    @Test
    fun publishes_package_root_relative_resources_without_library_manifest() {
        val root = Files.createTempDirectory("kotlin-winrt-appx-artifact-")
        val resources = root.resolve("src/winuiMain/appxResources")
        Files.createDirectories(resources.resolve("Assets"))
        Files.writeString(resources.resolve("Assets/Icon.png"), "icon")
        Files.writeString(resources.resolve("AppxManifest.xml"), "manifest")
        val output = root.resolve("library-appx-resources.zip")

        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("packageResources", GenerateAppxResourcesArtifactTask::class.java)
        task.resourceRoots.set(listOf(resources.toString()))
        task.outputFile.set(output.toFile())
        task.generate()

        ZipFile(output.toFile()).use { zip ->
            assertTrue(zip.getEntry("Assets/Icon.png") != null)
            assertEquals(null, zip.getEntry("AppxManifest.xml"))
            assertEquals("icon", zip.getInputStream(zip.getEntry("Assets/Icon.png")).bufferedReader().readText())
        }
    }
}
