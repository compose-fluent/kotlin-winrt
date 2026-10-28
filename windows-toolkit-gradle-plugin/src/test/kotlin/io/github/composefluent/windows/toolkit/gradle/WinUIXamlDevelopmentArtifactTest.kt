package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.*
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test

class WinUIXamlDevelopmentArtifactTest {
    @Test fun development_run_stamps_only_the_isolated_copy_and_packaging_removes_permission() {
        val project = ProjectBuilder.builder().build()
        val input = project.projectDir.toPath().resolve("production")
        Files.createDirectories(input.resolve("lib"))
        val runtime = input.resolve("lib/runtime.jar")
        JarOutputStream(Files.newOutputStream(runtime), Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        }).use { jar ->
            jar.putNextEntry(JarEntry("io/github/composefluent/winrt/runtime/WinUIXamlDevelopmentBuildPolicy.class"))
            jar.write(byteArrayOf(1, 2, 3)); jar.closeEntry()
        }
        Files.write(input.resolve("app.exe"), byteArrayOf())
        val original = Files.readAllBytes(runtime)
        val run = project.tasks.create("runDevelopment", RunWinAppHostTask::class.java)
        run.workingDirectory.set(input.toFile())
        run.hostExecutable.set(input.resolve("app.exe").toFile())
        run.deploymentDirectory.set(project.layout.buildDirectory.dir("development"))
        run.supportsXamlHotReload.set(true)
        run.noLaunch.set(true)
        run.run()
        val development = run.deploymentDirectory.get().asFile.toPath().resolve("lib/runtime.jar")
        assertEquals("true", permission(development))
        assertNull(permission(runtime))
        assertArrayEquals(original, Files.readAllBytes(runtime))
        val packaged = project.projectDir.toPath().resolve("packaged")
        stageWinAppJvmRuntimeClasspath(listOf(development.toFile()), packaged)
        assertNull(permission(packaged.resolve("lib/runtime.jar")))
        assertEquals("true", permission(development))
    }

    @Test fun native_release_and_unknown_build_types_are_not_development_variants() {
        val native = WinAppVariant("native", WinAppVariantKind.MingwX64, "mingwX64", "main", "mingwX64Main", "RELEASE", "releaseExecutable", "win-x64")
        assertFalse(native.supportsXamlDevelopment)
        assertFalse(native.copy(buildType = null).supportsXamlDevelopment)
        assertTrue(native.copy(buildType = "DEBUG").supportsXamlDevelopment)
        assertTrue(native.copy(kind = WinAppVariantKind.Jvm, buildType = null).supportsXamlDevelopment)
    }

    private fun permission(path: Path) = JarFile(path.toFile()).use { it.manifest.mainAttributes.getValue("Kotlin-WinRT-Development") }
}
