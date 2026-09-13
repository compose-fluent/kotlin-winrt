package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WinAppVariantTest {
    @Test
    fun default_application_expands_every_target_and_executable_build_type_into_tasks() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("customJvm")
            mingwX64("customMingw") {
                binaries {
                    executable()
                    executable("tools")
                }
            }
        }
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { }

        val concreteTasks = setOf(
            "packageWinAppCustomJvmMain",
            "packageWinAppCustomMingwMainDebugExecutable",
            "packageWinAppCustomMingwMainReleaseExecutable",
            "packageWinAppCustomMingwMainToolsDebugExecutable",
            "packageWinAppCustomMingwMainToolsReleaseExecutable",
        )
        concreteTasks.forEach { taskName ->
            assertTrue("Missing $taskName", taskName in project.tasks.names)
        }
        val aggregate = project.tasks.getByName("packageWinApp")
        assertEquals(
            concreteTasks,
            aggregate.taskDependencies.getDependencies(aggregate).map { task -> task.name }.toSet(),
        )

        val variants = concreteTasks.map { taskName ->
            project.tasks.named(taskName, PackageWinAppTask::class.java).get().applicationVariant.get()
        }
        assertEquals(concreteTasks.size, variants.toSet().size)
        val outputs = concreteTasks.map { taskName ->
            project.tasks.named(taskName, PackageWinAppTask::class.java).get().outputFile.get().asFile
        }
        assertEquals(concreteTasks.size, outputs.toSet().size)
        assertNotEquals(outputs.first(), outputs.last())
    }

    @Test
    fun default_application_registers_variants_declared_after_the_application_block() {
        val project = ProjectBuilder.builder().withName("late-variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { }

        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("lateJvm")
            mingwX64("lateMingw") {
                binaries { executable() }
            }
        }

        val concreteTasks = setOf(
            "packageWinAppLateJvmMain",
            "packageWinAppLateMingwMainDebugExecutable",
            "packageWinAppLateMingwMainReleaseExecutable",
        )
        val aggregate = project.tasks.getByName("packageWinApp")
        assertEquals(
            concreteTasks,
            aggregate.taskDependencies.getDependencies(aggregate).map { task -> task.name }.toSet(),
        )
    }

    @Test
    fun concrete_native_package_tasks_link_only_their_own_executable() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("customJvm")
            mingwX64("customMingw") {
                binaries { executable() }
            }
        }
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { application ->
            application.mainClass.set("sample.MainKt")
        }
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        listOf("Debug", "Release").forEach { buildType ->
            val suffix = "CustomMingwMain${buildType}Executable"
            val packageTask = project.tasks.getByName("packageWinApp$suffix")
            val stageTask = project.tasks.getByName("stageWinAppPackage$suffix")
            assertTrue(stageTask in packageTask.taskDependencies.getDependencies(packageTask))
            val dependencies = stageTask.taskDependencies.getDependencies(stageTask).map { it.name }.toSet()
            assertEquals(setOf("link${buildType}ExecutableCustomMingw"), dependencies.filter { it.startsWith("link") }.toSet())
            assertFalse(dependencies.any { it.startsWith("buildWinAppHost") })
        }
        val jvmStage = project.tasks.getByName("stageWinAppPackageCustomJvmMain")
        assertFalse(jvmStage.taskDependencies.getDependencies(jvmStage).any { it.name.startsWith("link") })
    }

    @Test
    fun named_application_requires_an_exact_variant_id_even_with_one_candidate() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("customJvm")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val options = project.extensions.getByType(WindowsExtension::class.java).application.variants.create("desktop")

        val missing = runCatching { resolveWinAppVariant(project, options) }.exceptionOrNull()
        assertTrue(missing?.message.orEmpty().contains("requires an explicit variantName"))

        options.variant("customJvm")
        val partial = runCatching { resolveWinAppVariant(project, options) }.exceptionOrNull()
        assertTrue(partial?.message.orEmpty().contains("No unique Kotlin/WinRT variant"))
        assertTrue(partial?.message.orEmpty().contains("customJvm:main"))

        options.variant("customJvm:main")
        assertEquals("customJvm:main", resolveWinAppVariant(project, options).id)
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
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        project.extensions.getByType(WindowsExtension::class.java).application { }

        val stageTask = project.tasks
            .named("stageWinAppPackageCustomJvmMain", StageWinAppPackageTask::class.java)
            .get()
        val error = runCatching { stageTask.defaultAppxResourceRoots.get() }.exceptionOrNull()

        val causeChain = generateSequence(error) { it.cause }
            .joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
        assertTrue("Expected conflict diagnostic, got $causeChain", causeChain.contains("unrelated source sets"))
        assertTrue(causeChain.contains("first"))
        assertTrue(causeChain.contains("second"))
    }
}
