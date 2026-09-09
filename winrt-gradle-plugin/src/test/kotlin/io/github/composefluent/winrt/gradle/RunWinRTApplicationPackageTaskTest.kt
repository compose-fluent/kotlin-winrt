package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RunWinRTApplicationPackageTaskTest {
    @Test
    fun each_packaged_run_builds_its_own_layout_without_msix_signing_or_installation() {
        val project = ProjectBuilder.builder().withName("packaged-run").build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application {
            it.mainClass.set("sample.MainKt")
            it.packaged()
        }
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("desktop")
            mingwX64("nativeDesktop") { binaries { executable() } }
        }
        (project as ProjectInternal).evaluate()

        val expectedLayouts = mapOf(
            "DesktopMain" to "buildWinRTApplicationHostDesktopMain",
            "NativeDesktopMainDebugExecutable" to "stageWinRTApplicationPackageNativeDesktopMainDebugExecutable",
            "NativeDesktopMainReleaseExecutable" to "stageWinRTApplicationPackageNativeDesktopMainReleaseExecutable",
        )
        val deploymentDirectories = expectedLayouts.map { (suffix, producer) ->
            val run = project.tasks.named("runWinRTApplicationPackage$suffix", RunWinRTApplicationPackageTask::class.java).get()
            val developmentStage = project.tasks.named(
                "stageWinRTApplicationDevelopmentPackage$suffix", StageWinRTApplicationPackageTask::class.java,
            ).get()
            assertEquals(
                setOf("restoreWinAppDependencies", developmentStage.name),
                run.taskDependencies.getDependencies(run).map { it.name }.toSet(),
            )
            val pack = project.tasks.named("packageWinRTApplication$suffix", PackageWinRTApplicationTask::class.java).get()
            assertEquals(pack.packageDirectory.get(), developmentStage.runtimeAssetsDirectory.get())
            assertEquals(developmentStage.outputDirectory.get(), run.packageDirectory.get())
            assertTrue(developmentStage.developmentIdentity.get())
            assertTrue(producer in developmentStage.taskDependencies.getDependencies(developmentStage).map { it.name })
            assertEquals(WinRTApplicationPackageMode.Packaged.name, run.packageMode.get())
            assertFalse(run.selfContained.get())
            val deployment = run.deploymentDirectory.get().asFile.toPath()
            assertFalse(deployment.startsWith(run.packageDirectory.get().asFile.toPath()))
            deployment
        }
        assertEquals(3, deploymentDirectories.toSet().size)
        assertFalse("runWinRTApplicationPackage" in project.tasks.names)
        project.extensions.getByType(WinRTExtension::class.java).application { it.selfContained() }
        expectedLayouts.keys.forEach { suffix ->
            val run = project.tasks.named("runWinRTApplicationPackage$suffix", RunWinRTApplicationPackageTask::class.java).get()
            assertTrue(run.selfContained.get())
        }
    }

    @Test
    fun development_staging_changes_only_the_copied_identity_and_rejects_an_unrebuilt_pri() {
        val root = Files.createTempDirectory("kotlin-winrt-development-identity-")
        val input = root.resolve("input")
        write(input.resolve("AppxManifest.xml"), manifest.replace(
            "<DisplayName>RunTest</DisplayName>",
            "<DisplayName>ms-resource://KotlinWinRT.RunTest/Resources/AppName</DisplayName>",
        ))
        write(input.resolve("App.exe"), "test executable")
        write(input.resolve("Assets/Logo.png"), "test logo")
        val original = Files.readString(input.resolve("AppxManifest.xml"))
        val project = ProjectBuilder.builder().withProjectDir(root.toFile()).build()
        val task = project.tasks.register("developmentStage", StageWinRTApplicationPackageTask::class.java).get()
        task.runtimeAssetsDirectory.set(input.toFile())
        task.outputDirectory.set(root.resolve("output").toFile())
        task.runtimeIdentifier.set("win-x64")
        task.generateProjectPri.set(false)
        task.developmentIdentity.set(true)
        task.minWindowsVersion.set("10.0.19041.0")
        task.windowsSdkVersion.set("10.0.26100.0")
        repeat(2) {
            task.stage()
            val staged = Files.readString(root.resolve("output/AppxManifest.xml"))
            assertTrue(staged, staged.contains("Name=\"KotlinWinRT.RunTest.dev\""))
            assertTrue(staged, staged.contains("ms-resource://KotlinWinRT.RunTest.dev/Resources/AppName"))
            assertTrue(staged, staged.contains("Id=\"App\""))
            assertEquals(original, Files.readString(input.resolve("AppxManifest.xml")))
        }
        write(input.resolve("resources.pri"), "precompiled PRI using the original identity")
        val error = runCatching { task.stage() }.exceptionOrNull()
        assertTrue(error is GradleException)
        assertTrue(error?.message.orEmpty().contains("must regenerate resources.pri"))
    }

    @Test
    fun rejects_unsupported_modes_and_overlapping_deployment_before_invoking_winapp() {
        assumeTrue(isWindowsHost())
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("runFixture", RunWinRTApplicationPackageTask::class.java).get()
        task.packageMode.set(WinRTApplicationPackageMode.Unpackaged.name)
        val modeError = runCatching { task.run() }.exceptionOrNull()
        assertTrue(modeError is GradleException)
        assertTrue(modeError?.message.orEmpty().contains("packaged()"))

        task.packageMode.set(WinRTApplicationPackageMode.Packaged.name)
        task.selfContained.set(true)
        val deploymentModeError = runCatching { task.run() }.exceptionOrNull()
        assertTrue(deploymentModeError is GradleException)
        assertTrue(deploymentModeError?.message.orEmpty().contains("frameworkDependent()"))

        task.selfContained.set(false)
        task.packageDirectory.set(project.layout.projectDirectory)
        task.deploymentDirectory.set(project.layout.projectDirectory.dir("AppX"))
        val layoutError = runCatching { task.run() }.exceptionOrNull()
        assertTrue(layoutError is GradleException)
        assertTrue(layoutError?.message.orEmpty().contains("must be separate"))
    }

    @Test
    fun winapp_run_options_stream_output_repeat_with_configuration_cache_and_propagate_failures() {
        assumeTrue(isWindowsHost())
        val root = Files.createTempDirectory("kotlin-winrt-packaged-run-")
        write(root.resolve("settings.gradle"), "rootProject.name = 'packaged-run'")
        write(root.resolve("gradle.properties"), """
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.workers.max=1
        """.trimIndent())
        write(root.resolve("build.gradle"), """
            plugins { id 'io.github.compose-fluent.winrt' }
            def developmentStage = tasks.register('developmentStage', io.github.composefluent.winrt.gradle.StageWinRTApplicationPackageTask) {
                runtimeAssetsDirectory = layout.projectDirectory.dir('build output')
                outputDirectory = layout.buildDirectory.dir('development-input')
                runtimeIdentifier = 'win-x64'
                generateProjectPri = false
                developmentIdentity = true
                minWindowsVersion = providers.gradleProperty('minimum').orElse('10.0.19041.0')
                windowsSdkVersion = providers.gradleProperty('sdkApi').orElse('10.0.26100.0')
            }
            tasks.register('runFixture', io.github.composefluent.winrt.gradle.RunWinRTApplicationPackageTask) {
                packageDirectory = developmentStage.flatMap { it.outputDirectory }
                deploymentDirectory = layout.buildDirectory.dir('deployment')
                winAppCliExecutable = file('fake-winapp.cmd').absolutePath
                winAppCliCacheDirectory = layout.buildDirectory.dir('winapp-cache')
                winAppWorkspace = layout.projectDirectory
            }
        """.trimIndent())
        write(root.resolve("fake-winapp.cmd"), """
            @echo off
            if /I "%~1"=="--version" (
              echo 0.6.0
              exit /b 0
            )
            >>"%~dp0invocations.log" echo %*
            if not exist "%~6" mkdir "%~6"
            echo packaged-launch-output
            if exist "%~dp0fail.flag" exit /b 23
            exit /b 0
        """.trimIndent())
        write(root.resolve("build output/AppxManifest.xml"), manifest)
        write(root.resolve("build output/App.exe"), "test executable")
        write(root.resolve("build output/Assets/Logo.png"), "test logo")

        fun runner(options: List<String> = listOf("--detach", "--args=hello world")) =
            GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withArguments(listOf("runFixture", "--configuration-cache", "--offline", "--stacktrace") + options)
        val first = runner().build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":runFixture")?.outcome)
        assertTrue(first.output, first.output.contains("packaged-launch-output"))
        val second = runner().build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":developmentStage")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, second.task(":runFixture")?.outcome)
        assertTrue(second.output, second.output.contains("Reusing configuration cache"))
        val invocations = Files.readAllLines(root.resolve("invocations.log"))
        assertEquals(2, invocations.size)
        invocations.forEach { command ->
            assertTrue(command, command.startsWith("run "))
            assertTrue(command, command.contains("development-input"))
            assertTrue(command, command.contains("--manifest"))
            assertTrue(command, command.contains("--output-appx-directory"))
            assertTrue(command, command.contains("--detach"))
            assertTrue(command, command.contains("--args=hello world"))
            assertFalse(command, command.contains("--clean"))
            assertFalse(command, command.contains("--debug-output"))
        }
        runner(listOf("--debug-output", "--args=--detach")).build()
        val debugInvocation = Files.readAllLines(root.resolve("invocations.log")).last()
        assertTrue(debugInvocation, debugInvocation.contains("--debug-output"))
        assertTrue(debugInvocation, debugInvocation.contains("--args=--detach"))
        runner(listOf("--no-launch")).build()
        val registerInvocation = Files.readAllLines(root.resolve("invocations.log")).last()
        assertTrue(registerInvocation, registerInvocation.contains("--no-launch"))
        assertFalse(registerInvocation, registerInvocation.contains("--args"))
        assertFalse(registerInvocation, registerInvocation.contains("--detach"))
        runner(listOf("--no-launch", "-PsdkApi=10.0.28000.0", "-Pminimum=10.0.22000.0")).build()
        val staged = Files.readString(root.resolve("build/development-input/AppxManifest.xml"))
        assertTrue(staged, staged.contains("MinVersion=\"10.0.22000.0\""))
        assertTrue(staged, staged.contains("MaxVersionTested=\"10.0.28000.0\""))
        assertTrue(staged, staged.contains("Name=\"KotlinWinRT.RunTest.dev\""))
        assertEquals(manifest, Files.readString(root.resolve("build output/AppxManifest.xml")))
        write(root.resolve("fail.flag"), "fail")
        val failure = runner().buildAndFail()
        assertTrue(failure.output, failure.output.contains("non-zero exit value 23"))
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private val manifest = """
        <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                 xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
          <Identity Name="KotlinWinRT.RunTest" Publisher="CN=KotlinWinRT" Version="1.0.0.0" ProcessorArchitecture="x64" />
          <Properties><DisplayName>RunTest</DisplayName><PublisherDisplayName>KotlinWinRT</PublisherDisplayName><Logo>Assets/Logo.png</Logo></Properties>
          <Applications><Application Id="App" Executable="App.exe" EntryPoint="Windows.FullTrustApplication">
            <uap:VisualElements DisplayName="RunTest" Description="RunTest" BackgroundColor="transparent"
                               Square150x150Logo="Assets/Logo.png" Square44x44Logo="Assets/Logo.png" />
          </Application></Applications>
        </Package>
    """.trimIndent()
}
