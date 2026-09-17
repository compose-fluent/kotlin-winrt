package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeApplicationEntryPointTest {
    @Test
    fun runtime_assets_without_main_class_preserve_the_executable_entry() {
        assertEquals("sample.benchmarkMain", configuredEntryPoint(null))
    }

    @Test
    fun explicit_main_class_selects_the_generated_application_host() {
        assertEquals(
            "io.github.composefluent.winrt.application.mainMingwX64MainReleaseExecutable",
            configuredEntryPoint("sample.MainKt"),
        )
    }

    private fun configuredEntryPoint(mainClass: String?): String? {
        val project = ProjectBuilder.builder().withName("native-entry").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val target = project.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64()
        target.binaries.executable { entryPoint = "sample.benchmarkMain" }
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { application ->
            application.runtimeAsset(project.file("Component.dll").absolutePath)
            if (mainClass != null) application.mainClass.set(mainClass)
        }
        (project as ProjectInternal).evaluate()
        return target.binaries.withType(Executable::class.java)
            .single { it.name == "releaseExecutable" }.entryPoint
    }
}
