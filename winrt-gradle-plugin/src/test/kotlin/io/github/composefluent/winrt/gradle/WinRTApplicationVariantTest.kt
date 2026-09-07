package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WinRTApplicationVariantTest {
    @Test
    fun refuses_to_guess_between_jvm_and_mingw_candidates() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("customJvm")
            mingwX64("customMingw") {
                binaries { executable() }
            }
        }
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val options = project.extensions.getByType(WinRTExtension::class.java).application

        val error = runCatching { resolveWinRTApplicationVariant(project, options) }.exceptionOrNull()

        assertTrue(error is GradleException)
        assertTrue(error!!.message.orEmpty().contains("ambiguous"))
        assertTrue(error.message.orEmpty().contains("customJvm:main"))
        assertTrue(error.message.orEmpty().contains("customMingw:main"))
    }

    @Test
    fun selects_a_custom_jvm_target_without_linking_native_candidates() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("customJvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val options = project.extensions.getByType(WinRTExtension::class.java).application
        options.jvmTarget("customJvm")

        val variant = resolveWinRTApplicationVariant(project, options)

        assertEquals(WinRTApplicationVariantKind.Jvm, variant.kind)
        assertEquals("customJvm", variant.targetName)
        assertEquals("customJvm:main", variant.id)
    }

    @Test
    fun does_not_assume_a_native_build_type_without_explicit_selection() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64("customMingw") {
            binaries { executable() }
        }
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val options = project.extensions.getByType(WinRTExtension::class.java).application

        assertEquals("", options.nativeBuildType.get())
    }

    @Test
    fun rejects_same_level_appx_resource_conflicts() {
        val projectDir = Files.createTempDirectory("appx-resource-source-set-conflict-")
        Files.createDirectories(projectDir.resolve("src/first/appxResources/Assets"))
        Files.createDirectories(projectDir.resolve("src/second/appxResources/Assets"))
        Files.writeString(projectDir.resolve("src/first/appxResources/Assets/logo.png"), "first")
        Files.writeString(projectDir.resolve("src/second/appxResources/Assets/logo.png"), "second")

        val project = ProjectBuilder.builder()
            .withName("variant-resource-conflict")
            .withProjectDir(projectDir.toFile())
            .build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val jvmTarget = kotlin.jvm("customJvm")
        val first = kotlin.sourceSets.maybeCreate("first")
        val second = kotlin.sourceSets.maybeCreate("second")
        val jvmMain = jvmTarget.compilations.getByName("main").defaultSourceSet
        jvmMain.dependsOn(first)
        jvmMain.dependsOn(second)
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.jvmTarget("customJvm")
        }

        val stageTask = project.tasks
            .named("stageWinRTApplicationPackage", StageWinRTApplicationPackageTask::class.java)
            .get()
        val error = runCatching { stageTask.defaultAppxResourceRoots.get() }.exceptionOrNull()

        val causeChain = generateSequence(error) { it.cause }
            .joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
        assertTrue("Expected conflict diagnostic, got $causeChain", causeChain.contains("unrelated source sets"))
        assertTrue(causeChain.contains("first"))
        assertTrue(causeChain.contains("second"))
    }
}
