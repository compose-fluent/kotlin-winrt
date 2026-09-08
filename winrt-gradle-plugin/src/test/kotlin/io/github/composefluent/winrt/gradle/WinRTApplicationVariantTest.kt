package io.github.composefluent.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class WinRTApplicationVariantTest {
    @Test
    fun default_application_expands_every_matching_target_variant_into_tasks() {
        val project = ProjectBuilder.builder().withName("variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("customJvm")
            mingwX64("customMingw") {
                binaries { executable() }
            }
        }
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { }

        val concreteTasks = setOf(
            "packageWinRTApplicationCustomJvmMain",
            "packageWinRTApplicationCustomMingwMainDebugExecutable",
            "packageWinRTApplicationCustomMingwMainReleaseExecutable",
        )
        concreteTasks.forEach { taskName ->
            assertTrue("Missing $taskName", taskName in project.tasks.names)
        }
        val aggregate = project.tasks.getByName("packageWinRTApplication")
        assertEquals(
            concreteTasks,
            aggregate.taskDependencies.getDependencies(aggregate).map { task -> task.name }.toSet(),
        )

        val variants = concreteTasks.map { taskName ->
            project.tasks.named(taskName, PackageWinRTApplicationTask::class.java).get().applicationVariant.get()
        }
        assertEquals(3, variants.toSet().size)
        val outputs = concreteTasks.map { taskName ->
            project.tasks.named(taskName, PackageWinRTApplicationTask::class.java).get().outputFile.get().asFile
        }
        assertEquals(3, outputs.toSet().size)
        assertNotEquals(outputs.first(), outputs.last())
    }

    @Test
    fun default_application_registers_variants_declared_after_the_application_block() {
        val project = ProjectBuilder.builder().withName("late-variant-test").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { }

        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("lateJvm")
            mingwX64("lateMingw") {
                binaries { executable() }
            }
        }

        val concreteTasks = setOf(
            "packageWinRTApplicationLateJvmMain",
            "packageWinRTApplicationLateMingwMainDebugExecutable",
            "packageWinRTApplicationLateMingwMainReleaseExecutable",
        )
        val aggregate = project.tasks.getByName("packageWinRTApplication")
        assertEquals(
            concreteTasks,
            aggregate.taskDependencies.getDependencies(aggregate).map { task -> task.name }.toSet(),
        )
    }

    @Test
    fun selects_a_custom_jvm_target_without_linking_native_candidates() {
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
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.jvmTarget("customJvm")
        }

        val variant = resolveWinRTApplicationVariant(project, options)

        assertEquals(WinRTApplicationVariantKind.Jvm, variant.kind)
        assertEquals("customJvm", variant.targetName)
        assertEquals("customJvm:main", variant.id)
        assertTrue("packageWinRTApplicationCustomJvmMain" in project.tasks.names)
        assertTrue(project.tasks.names.none { taskName -> taskName.startsWith("packageWinRTApplicationCustomMingw") })
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
        assertEquals(
            setOf("DEBUG", "RELEASE"),
            matchingWinRTApplicationVariants(project, options).mapNotNull { it.buildType }.toSet(),
        )
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
            .named("stageWinRTApplicationPackageCustomJvmMain", StageWinRTApplicationPackageTask::class.java)
            .get()
        val error = runCatching { stageTask.defaultAppxResourceRoots.get() }.exceptionOrNull()

        val causeChain = generateSequence(error) { it.cause }
            .joinToString(" <- ") { "${it.javaClass.name}: ${it.message}" }
        assertTrue("Expected conflict diagnostic, got $causeChain", causeChain.contains("unrelated source sets"))
        assertTrue(causeChain.contains("first"))
        assertTrue(causeChain.contains("second"))
    }
}
