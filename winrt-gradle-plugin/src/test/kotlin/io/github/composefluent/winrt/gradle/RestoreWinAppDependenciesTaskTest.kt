package io.github.composefluent.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RestoreWinAppDependenciesTaskTest {
    @Test
    fun empty_package_set_writes_an_empty_lockfile_without_resolving_the_cli() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val workspace = project.layout.buildDirectory.dir("empty-winapp-workspace").get().asFile.toPath()
        Files.createDirectories(workspace)
        val configuration = workspace.resolve("winapp.yaml")
        Files.writeString(configuration, "packages: []${System.lineSeparator()}")
        val winAppDirectory = workspace.resolve(".winapp")
        val lockfile = winAppDirectory.resolve("winmds.lock.json")
        val task = project.tasks.register(
            "restoreEmptyWinAppDependencies",
            RestoreWinAppDependenciesTask::class.java,
        ) { registeredTask ->
            registeredTask.configurationFile.set(project.layout.file(project.provider { configuration.toFile() }))
            registeredTask.winAppDirectory.set(project.layout.dir(project.provider { winAppDirectory.toFile() }))
            registeredTask.winmdLockFile.set(project.layout.file(project.provider { lockfile.toFile() }))
            registeredTask.winAppCliExecutable.set(workspace.resolve("missing-winapp.exe").toString())
            registeredTask.winAppCliCacheDirectory.set(project.layout.buildDirectory.dir("empty-winapp-cli-cache"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.restoreEnabled.set(true)
            registeredTask.includeToolingPackages.set(false)
            registeredTask.offline.set(true)
        }.get()

        task.restore()

        assertTrue(Files.isDirectory(winAppDirectory.resolve("bin")))
        assertEquals(emptyList<WinAppRestoredPackage>(), WinAppRestoreLockfileReader.read(lockfile).packages)
    }

    @Test
    fun failed_restore_preserves_the_previous_winapp_output() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val workspace = project.layout.buildDirectory.dir("preserved-winapp-workspace").get().asFile.toPath()
        Files.createDirectories(workspace)
        val configuration = workspace.resolve("winapp.yaml")
        Files.writeString(
            configuration,
            """
            packages:
              - name: Sample.Package
                version: 1.0.0
            """.trimIndent() + System.lineSeparator(),
        )
        val winAppDirectory = workspace.resolve(".winapp")
        val previousMarker = winAppDirectory.resolve("previous.marker")
        Files.createDirectories(winAppDirectory)
        Files.writeString(previousMarker, "previous")
        val winApp = workspace.resolve("failing-winapp.cmd")
        Files.writeString(
            winApp,
            """
            @echo off
            if /I "%~1"=="--version" (
              echo 0.6.0
              exit /b 0
            )
            echo forced restore failure
            exit /b 7
            """.trimIndent() + System.lineSeparator(),
        )
        val task = project.tasks.register(
            "restoreFailingWinAppDependencies",
            RestoreWinAppDependenciesTask::class.java,
        ) { registeredTask ->
            registeredTask.configurationFile.set(project.layout.file(project.provider { configuration.toFile() }))
            registeredTask.winAppDirectory.set(project.layout.dir(project.provider { winAppDirectory.toFile() }))
            registeredTask.winmdLockFile.set(
                project.layout.file(project.provider { winAppDirectory.resolve("winmds.lock.json").toFile() }),
            )
            registeredTask.winAppCliExecutable.set(winApp.toString())
            registeredTask.winAppCliCacheDirectory.set(project.layout.buildDirectory.dir("failing-winapp-cli-cache"))
            registeredTask.nugetPackages.set(listOf("Sample.Package@1.0.0"))
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.restoreEnabled.set(true)
            registeredTask.includeToolingPackages.set(false)
            registeredTask.offline.set(true)
        }.get()

        val failure = runCatching { task.restore() }.exceptionOrNull()

        assertTrue(failure is org.gradle.api.GradleException)
        assertEquals("previous", Files.readString(previousMarker))
        assertTrue(Files.notExists(task.temporaryDir.toPath().resolve("workspace")))
    }

    @Test
    fun restore_uses_a_disposable_base_without_mutating_the_project_directory() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val workspace = project.layout.buildDirectory.dir("isolated-winapp-workspace").get().asFile.toPath()
        Files.createDirectories(workspace)
        val configuration = workspace.resolve("winapp.yaml")
        Files.writeString(configuration, "packages: []${System.lineSeparator()}")
        val projectMarker = workspace.resolve("project.marker")
        Files.writeString(projectMarker, "keep")
        val winAppDirectory = workspace.resolve(".winapp")
        val lockfile = winAppDirectory.resolve("winmds.lock.json")
        val fakeCli = workspace.resolve("fake-winapp.cmd")
        Files.writeString(
            fakeCli,
            """
            @echo off
            if /I "%~1"=="--version" (
              echo 0.6.0
              exit /b 0
            )
            if /I "%~1"=="restore" (
              > "%~dp0restore.cwd" echo %CD%
              if not exist .winapp mkdir .winapp
              > .winapp\winmds.lock.json echo {"schema": 3, "packages": []}
              exit /b 0
            )
            exit /b 1
            """.trimIndent() + System.lineSeparator(),
        )
        val task = project.tasks.register(
            "restoreIsolatedWinAppDependencies",
            RestoreWinAppDependenciesTask::class.java,
        ) { registeredTask ->
            registeredTask.configurationFile.set(project.layout.file(project.provider { configuration.toFile() }))
            registeredTask.restoreBaseDirectory.set(project.layout.dir(project.provider { workspace.toFile() }))
            registeredTask.winAppDirectory.set(project.layout.dir(project.provider { winAppDirectory.toFile() }))
            registeredTask.winmdLockFile.set(project.layout.file(project.provider { lockfile.toFile() }))
            registeredTask.winAppCliExecutable.set(fakeCli.toString())
            registeredTask.winAppCliCacheDirectory.set(project.layout.buildDirectory.dir("isolated-winapp-cli-cache"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.restoreEnabled.set(true)
            registeredTask.includeToolingPackages.set(true)
            registeredTask.offline.set(false)
        }.get()

        task.restore()

        assertTrue(Files.isRegularFile(lockfile))
        assertEquals(emptyList<WinAppRestoredPackage>(), WinAppRestoreLockfileReader.read(lockfile).packages)
        assertTrue(Files.isRegularFile(projectMarker))
        val restoreWorkingDirectory = Path.of(Files.readString(workspace.resolve("restore.cwd")).trim())
            .toAbsolutePath()
            .normalize()
        assertTrue(restoreWorkingDirectory.startsWith(workspace.toAbsolutePath().normalize()))
        Files.list(workspace).use { children ->
            assertFalse(children.anyMatch { it.fileName.toString().startsWith(".kotlin-winrt-winapp-config-") })
        }
    }
}
