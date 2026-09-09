package io.github.composefluent.winrt.gradle

import java.nio.file.Path
import java.nio.file.Files
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerateAppxResourcesTaskTest {
    @Test
    fun empty_resource_sets_do_not_generate_a_windows_uri_source_file() {
        val root = Files.createTempDirectory("kotlin-winrt-empty-appx-resources-")
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.create("generateAppxResources", GenerateAppxResourcesTask::class.java)
        task.outputDirectory.set(root.resolve("generated").toFile())
        task.packageName.set("sample.appx")
        task.targetSourceSet.set("winuiMain")
        task.resourceRoots.set(emptyList())

        task.generate()

        assertTrue(Files.notExists(root.resolve("generated/sample/appx/AppxRes.kt")))
    }

    @Test
    fun renders_appx_resource_accessors_with_kotlinpoet() {
        val source = renderAppxResourcesSource(
            packageName = "sample.appx",
            inputs = listOf(
                AppxResourceInput(Path.of("logo"), Path.of("Assets/Square44x44Logo.png")),
                AppxResourceInput(Path.of("custom"), Path.of("Assets/Logo one.png")),
            ),
            targetSourceSet = "winuiJvmMain",
        )

        assertTrue(source.contains("public object AppxRes"))
        assertTrue(source.contains("public object Assets"))
        assertTrue(source.contains("Square44x44LogoPng"))
        assertTrue(source.contains("\"Assets/Square44x44Logo.png\""))
        assertTrue(source.contains("LogoOnePng"))
        assertTrue(source.contains("\"Assets/Logo%20one.png\""))
        assertTrue(source.contains("Uri(\"ms-appx:///\" + encodedPath)"))
    }
}
