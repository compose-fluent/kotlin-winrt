package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test

/** Packaging owns development copies; no projection/runtime policy is introduced. */
class RunWinAppHostTaskTest {
    @Test fun launcher_accepts_quoted_application_arguments_from_run_configuration() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.create("runHost", RunWinAppHostTask::class.java)
        task.applicationArgs("--title \"two words\" --value example")
        assertEquals(listOf("--title", "two words", "--value", "example"), task.args.get())
    }

    @Test fun preparation_keeps_running_payload_separate_from_staging() {
        val project = ProjectBuilder.builder().build()
        val source = project.file("build/staging").apply { mkdirs() }
        source.resolve("host.exe").writeText("old executable")
        source.resolve("asset.txt").writeText("old asset")
        val destination = project.file("build/deployed")
        val task = project.tasks.create("prepareHost", RunWinAppHostTask::class.java)
        task.hostExecutable.set(source.resolve("host.exe"))
        task.workingDirectory.set(source)
        task.deploymentDirectory.set(destination)
        task.noLaunch.set(true)
        task.run()
        assertEquals(destination.resolve("host.exe"), task.deployedExecutable.get().asFile)
        source.resolve("host.exe").writeText("new executable")
        source.resolve("asset.txt").delete()
        assertEquals("old executable", destination.resolve("host.exe").readText())
        assertTrue(destination.resolve("asset.txt").isFile)
        task.run()
        assertEquals("new executable", destination.resolve("host.exe").readText())
        assertFalse(destination.resolve("asset.txt").exists())
    }

    @Test fun deployment_cannot_overwrite_or_contain_staging() {
        val project = ProjectBuilder.builder().build()
        val source = project.file("build/staging").apply { mkdirs() }
        source.resolve("host.exe").writeText("preserve")
        val task = project.tasks.create("prepareHost", RunWinAppHostTask::class.java)
        task.hostExecutable.set(source.resolve("host.exe"))
        task.workingDirectory.set(source)
        task.noLaunch.set(true)
        listOf(source, source.parentFile, source.resolve("nested")).forEach { destination ->
            task.deploymentDirectory.set(destination)
            assertThrows(IllegalArgumentException::class.java) { task.run() }
            assertEquals("preserve", source.resolve("host.exe").readText())
        }
    }
}
