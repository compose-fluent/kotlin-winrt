package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RestoreWinAppDependenciesTaskTest {
    @Test
    fun gradle_ignores_package_bookkeeping_and_empty_directories_but_tracks_metadata_and_native_payloads() {
        val directory = Files.createTempDirectory("winrt-restore-inputs-").toFile()
        val packageRoot = directory.resolve("nuget/sample.native/1.0.0").apply { mkdirs() }
        val winmd = packageRoot.resolve("Sample.winmd").apply { writeText("original") }
        val native = packageRoot.resolve("Sample.dll").apply { writeText("native") }
        packageRoot.resolve("Sample.Native.nuspec").writeText(
            "<package><metadata><id>Sample.Native</id><version>1.0.0</version></metadata></package>",
        )
        directory.resolve("fixture-lock.json").writeText("""
            {"schema":3,"nuget_cache_dir":"${directory.resolve("nuget").path.replace('\\', '/')}",
             "packages":[{"name":"Sample.Native","version":"1.0.0","winmds":[]}]}
        """.trimIndent())
        directory.resolve("fake-winapp.cmd").writeText("""
            @echo off
            if /I "%~1"=="--version" (
              echo 0.6.0
              exit /b 0
            )
            if not exist .winapp mkdir .winapp
            copy /Y "%~dp0fixture-lock.json" ".winapp\winmds.lock.json" > nul
            exit /b 0
        """.trimIndent() + System.lineSeparator())
        directory.resolve("settings.gradle.kts").writeText("rootProject.name = \"restore-inputs\"\n")
        directory.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx768m\norg.gradle.workers.max=1\n")
        directory.resolve("build.gradle").writeText("""
            plugins { id 'io.github.compose-fluent.windows-toolkit' }
            repositories { mavenCentral() }
            windows {
                winAppCliExecutable.set(file('fake-winapp.cmd').absolutePath)
                packageReferences { nugetPackage("Sample.Native", "1.0.0") { generateProjection = false } }
            }
            // The local fake CLI only copies this fixture's lock; Gradle dependency resolution stays offline.
            tasks.named('restoreWinAppDependencies') { task -> task.offline.set(false) }
            // Exercise the real plugin input wiring without running a generator on fake WinMD bytes.
            tasks.named('generateWinRTProjections') { task ->
                task.preparedMetadataManifest.unset()
                task.authoringCandidatesFile.unset()
                task.setDependsOn([])
                task.setOnlyIf { true }
                task.actions.clear()
                def target = task.outputDirectory.file('fixture.txt')
                task.doLast {
                    target.get().asFile.parentFile.mkdirs()
                    target.get().asFile.text = 'projection'
                }
            }
        """.trimIndent())
        try {
            fun build() = org.gradle.testkit.runner.GradleRunner.create().withProjectDir(directory)
                .withPluginClasspath().withArguments("restoreWinAppDependencies", "generateWinRTProjections",
                    "--offline", "--configuration-cache", "--no-build-cache", "--max-workers=1").build()
            build()
            build() // The initial restore makes its external package inventory available as inputs.
            val warm = build()
            assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, warm.task(":restoreWinAppDependencies")!!.outcome)
            assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, warm.task(":generateWinRTProjections")!!.outcome)
            packageRoot.resolve(".nupkg.metadata").writeText("restore metadata")
            packageRoot.resolve(".signature.p7s").writeText("signature")
            packageRoot.resolve("sample.native.1.0.0.nupkg.sha512").writeText("archive checksum")
            packageRoot.resolve("_rels").mkdirs()
            packageRoot.resolve("_rels/.rels").writeText("relationships")
            packageRoot.resolve("build/native/empty").mkdirs()
            val bookkeeping = build()
            assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, bookkeeping.task(":restoreWinAppDependencies")!!.outcome)
            assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, bookkeeping.task(":generateWinRTProjections")!!.outcome)
            assertTrue(bookkeeping.output.contains("Reusing configuration cache"))
            native.writeText("changed native payload")
            val payload = build()
            assertEquals(org.gradle.testkit.runner.TaskOutcome.SUCCESS, payload.task(":restoreWinAppDependencies")!!.outcome)
            assertEquals(org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE, payload.task(":generateWinRTProjections")!!.outcome)
            val timestamp = winmd.lastModified()
            winmd.writeText("modified metadata")
            winmd.setLastModified(timestamp)
            val metadata = build()
            assertEquals(org.gradle.testkit.runner.TaskOutcome.SUCCESS, metadata.task(":restoreWinAppDependencies")!!.outcome)
            assertEquals(org.gradle.testkit.runner.TaskOutcome.SUCCESS, metadata.task(":generateWinRTProjections")!!.outcome)
        } finally {
            check(directory.canonicalFile.parentFile == java.io.File(System.getProperty("java.io.tmpdir")).canonicalFile)
            check(directory.name.startsWith("winrt-restore-inputs-"))
            directory.deleteRecursively()
        }
    }

    @Test
    fun verified_restore_detects_native_inventory_changes_and_metadata_changes_with_preserved_timestamps() {
        // .cswinrt/nuget/Microsoft.Windows.CsWinRT.targets consumes metadata;
        // native payload content is fingerprinted by the Gradle staging tasks.
        val project = ProjectBuilder.builder().build()
        val workspace = project.layout.buildDirectory.dir("restore-inventory").get().asFile.toPath()
        val cache = workspace.resolve("nuget")
        val packageRoot = cache.resolve("sample.native/1.0.0")
        Files.createDirectories(packageRoot)
        val metadata = packageRoot.resolve("Sample.winmd")
        val native = packageRoot.resolve("Sample.dll")
        Files.writeString(metadata, "original")
        Files.writeString(native, "native")
        Files.writeString(packageRoot.resolve("Sample.Native.nuspec"),
            "<package><metadata><id>Sample.Native</id><version>1.0.0</version></metadata></package>")
        val originalMetadataTime = Files.getLastModifiedTime(metadata)
        val originalNativeTime = Files.getLastModifiedTime(native)
        val lock = workspace.resolve("fixture-lock.json")
        Files.writeString(lock, """
            {"schema":3,"nuget_cache_dir":"${cache.toString().replace('\\', '/')}",
             "packages":[{"name":"Sample.Native","version":"1.0.0","winmds":[]}]}
        """.trimIndent())
        val config = workspace.resolve("winapp.yaml")
        Files.writeString(config, "packages:\n  - name: Sample.Native\n    version: 1.0.0\n")
        val fakeCli = workspace.resolve("fake-winapp.cmd")
        Files.writeString(fakeCli, """
            @echo off
            if /I "%~1"=="--version" (
              echo 0.6.0
              exit /b 0
            )
            if not exist .winapp mkdir .winapp
            copy /Y "%~dp0fixture-lock.json" ".winapp\winmds.lock.json" > nul
            exit /b 0
        """.trimIndent() + System.lineSeparator())
        val output = workspace.resolve(".winapp")
        val task = project.tasks.register("restoreInventoryFixture", RestoreWinAppDependenciesTask::class.java) {
            it.configurationFile.set(config.toFile())
            it.restoreBaseDirectory.set(workspace.toFile())
            it.winAppDirectory.set(output.toFile())
            it.winmdLockFile.set(output.resolve("winmds.lock.json").toFile())
            it.nugetPackages.set(listOf("Sample.Native@1.0.0"))
            it.winAppCliExecutable.set(fakeCli.toString())
            it.winAppCliCacheDirectory.set(workspace.resolve("cli-cache").toFile())
        }.get()
        task.restore()
        task.offline.set(true)
        task.winAppCliExecutable.set(workspace.resolve("missing-winapp.exe").toString())
        task.restore()
        // NuGet can finish these bookkeeping files after WinApp has returned. They do not
        // change the verified payload and must not force another restore or projection pass.
        Files.writeString(packageRoot.resolve(".nupkg.metadata"), "restore metadata")
        Files.writeString(packageRoot.resolve(".signature.p7s"), "signature")
        Files.writeString(packageRoot.resolve("sample.native.1.0.0.nupkg.sha512"), "archive checksum")
        Files.createDirectories(packageRoot.resolve("_rels"))
        Files.writeString(packageRoot.resolve("_rels/.rels"), "package archive relationships")
        Files.createDirectories(packageRoot.resolve("build/native/empty"))
        task.restore()
        Files.writeString(native, "changed native layout")
        assertTrue(runCatching { task.restore() }.exceptionOrNull() is org.gradle.api.GradleException)
        Files.writeString(native, "native")
        Files.setLastModifiedTime(native, originalNativeTime)
        task.restore()
        Files.writeString(metadata, "modified")
        Files.setLastModifiedTime(metadata, originalMetadataTime)
        assertTrue(runCatching { task.restore() }.exceptionOrNull() is org.gradle.api.GradleException)
    }

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
        verifyDisposableRestore(includeRuntimeAssets = true)
    }

    @Test
    fun metadata_only_restore_discards_copied_runtime_architectures() {
        verifyDisposableRestore(includeRuntimeAssets = false)
    }

    @Test
    fun gradle_restore_retains_the_lock_without_copied_native_build_inputs() {
        verifyDisposableRestore(includeRuntimeAssets = false, includeNativeBuildFiles = false)
    }

    private fun verifyDisposableRestore(includeRuntimeAssets: Boolean, includeNativeBuildFiles: Boolean = true) {
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
              mkdir .winapp\bin\x64
              mkdir .winapp\bin\arm64
              > .winapp\bin\x64\Sample.dll echo x64
              > .winapp\bin\arm64\Sample.dll echo arm64
              mkdir .winapp\include
              mkdir .winapp\lib
              mkdir .winapp\share
              > .winapp\include\Sample.h echo header
              > .winapp\lib\Sample.lib echo library
              > .winapp\share\Sample.txt echo shared
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
            registeredTask.includeRuntimeAssets.set(includeRuntimeAssets)
            registeredTask.includeNativeBuildFiles.set(includeNativeBuildFiles)
            registeredTask.offline.set(false)
        }.get()

        task.restore()

        assertTrue(Files.isRegularFile(lockfile))
        assertEquals(emptyList<WinAppRestoredPackage>(), WinAppRestoreLockfileReader.read(lockfile).packages)
        assertTrue(Files.isRegularFile(projectMarker))
        assertEquals(includeRuntimeAssets, Files.isRegularFile(winAppDirectory.resolve("bin/x64/Sample.dll")))
        assertEquals(includeRuntimeAssets, Files.isRegularFile(winAppDirectory.resolve("bin/arm64/Sample.dll")))
        assertEquals(includeNativeBuildFiles, Files.isRegularFile(winAppDirectory.resolve("include/Sample.h")))
        assertEquals(includeNativeBuildFiles, Files.isRegularFile(winAppDirectory.resolve("lib/Sample.lib")))
        assertEquals(includeNativeBuildFiles, Files.isRegularFile(winAppDirectory.resolve("share/Sample.txt")))
        val restoreWorkingDirectory = Path.of(Files.readString(workspace.resolve("restore.cwd")).trim())
            .toAbsolutePath()
            .normalize()
        assertTrue(restoreWorkingDirectory.startsWith(workspace.toAbsolutePath().normalize()))
        Files.list(workspace).use { children ->
            assertFalse(children.anyMatch { it.fileName.toString().startsWith(".kotlin-winrt-winapp-config-") })
        }

        // Disabling restore must reuse the verified WinApp result, even without a CLI.
        task.restoreEnabled.set(false)
        task.winAppCliExecutable.set(workspace.resolve("missing-winapp.exe").toString())
        task.restore()
        assertTrue(Files.isRegularFile(lockfile))

        // Changed inputs cannot silently reuse unrelated package caches.
        task.nugetPackages.set(listOf("Missing.Package@1.0.0"))
        val failure = runCatching { task.restore() }.exceptionOrNull()
        assertTrue(failure is org.gradle.api.GradleException)
        assertTrue(failure?.message.orEmpty().contains("no verified lock/cache"))
        assertTrue(Files.isRegularFile(lockfile))
    }
}
