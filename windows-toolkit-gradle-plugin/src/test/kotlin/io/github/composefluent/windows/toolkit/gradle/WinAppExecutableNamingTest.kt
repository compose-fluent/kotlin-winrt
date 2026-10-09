package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Executable
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/** Output naming belongs to the build, as in CsWinRT's Samples/WinUIDesktopSample.csproj. */
class WinAppExecutableNamingTest {
    @Test
    fun single_application_module_uses_root_name_and_ignores_libraries() {
        val root = ProjectBuilder.builder().withName("animeko").build()
        val app = module(root, "app")
        module(root, "library")
        app.extensions.getByType(WindowsExtension::class.java).application { }

        assertJvmName(app, "animeko")
        assertEquals(setOf(":app"), winAppApplicationModules(root).get())
    }

    @Test
    fun multiple_application_modules_update_lazy_names_in_either_registration_order() {
        for (order in listOf(listOf("app", "app2"), listOf("app2", "app"))) {
            val root = ProjectBuilder.builder().withName("animeko").build()
            val first = module(root, order.first())
            val second = module(root, order.last())
            first.extensions.getByType(WindowsExtension::class.java).application { }
            assertJvmName(first, "animeko")
            second.extensions.getByType(WindowsExtension::class.java).application { }
            assertJvmName(first, "animeko-${first.name}")
            assertJvmName(second, "animeko-${second.name}")
            // Repeated application blocks and variants do not register another module.
            first.extensions.getByType(WindowsExtension::class.java).application { }
            assertEquals(2, winAppApplicationModules(root).get().size)
        }
    }

    @Test
    fun explicit_name_updates_all_jvm_inputs_even_after_tasks_are_realized() {
        val root = ProjectBuilder.builder().withName("animeko").build()
        val app = module(root, "app")
        val windows = app.extensions.getByType(WindowsExtension::class.java)
        windows.application { }
        assertJvmName(app, "animeko")
        windows.application.executableBaseName.set("Animeko Desktop")
        assertJvmName(app, "Animeko Desktop")
        val second = module(root, "app2")
        second.extensions.getByType(WindowsExtension::class.java).application { }
        assertJvmName(app, "Animeko Desktop")
        assertJvmName(second, "animeko-app2")
    }

    @Test
    fun named_applications_inherit_and_override_names_without_increasing_module_count() {
        val root = ProjectBuilder.builder().withName("animeko").build()
        val app = module(root, "app")
        val windows = app.extensions.getByType(WindowsExtension::class.java)
        windows.application { application ->
            application.variants.create("desktop") { it.variant("jvm:main") }
            application.variants.create("tools") {
                it.variant("jvm:main")
                it.executableBaseName.set("animeko-tools")
            }
        }
        assertJvmName(app, "animeko", "Desktop")
        assertJvmName(app, "animeko-tools", "Tools")
        windows.application.executableBaseName.set("custom")
        assertJvmName(app, "custom", "Desktop")
        assertJvmName(app, "animeko-tools", "Tools")
        assertEquals(setOf(":app"), winAppApplicationModules(root).get())
    }

    @Test
    fun native_package_renames_launcher_and_manifest_references_but_preserves_linked_binary() {
        val root = ProjectBuilder.builder().withName("animeko").build()
        val app = ProjectBuilder.builder().withName("app").withParent(root).build()
        app.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val target = app.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64()
        target.binaries.executable { baseName = "linked-app" }
        app.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        app.extensions.getByType(WindowsExtension::class.java).apply {
            packageReferences.windowsSdk("10.0.26100.0")
            application { application ->
                application.executableBaseName.set("animeko-desktop")
                application.minWindowsVersion.set("10.0.19041.0")
                application.generateProjectPri.set(false)
            }
        }
        (app as ProjectInternal).evaluate()

        val executable = target.binaries.withType(Executable::class.java).single { it.name == "releaseExecutable" }
        val linkedFile = executable.outputFile.toPath()
        Files.createDirectories(linkedFile.parent)
        Files.writeString(linkedFile, "native executable")
        val runtime = app.projectDir.toPath().resolve("runtime-assets")
        Files.createDirectories(runtime.resolve("Assets"))
        Files.writeString(runtime.resolve("Assets/Logo.png"), "logo")
        val manifest = app.projectDir.toPath().resolve("AppxManifest.xml")
        Files.writeString(manifest, """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                     xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
                     xmlns:com="http://schemas.microsoft.com/appx/manifest/com/windows10">
              <Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" ProcessorArchitecture="x64" />
              <Properties><DisplayName>App</DisplayName><PublisherDisplayName>Contoso</PublisherDisplayName><Logo>Assets/Logo.png</Logo></Properties>
              <Applications><Application Id="App" Executable="linked-app.exe" EntryPoint="Windows.FullTrustApplication">
                <uap:VisualElements DisplayName="App" Description="App" BackgroundColor="transparent"
                                   Square150x150Logo="Assets/Logo.png" Square44x44Logo="Assets/Logo.png" />
                <Extensions><com:Extension Category="windows.comServer"><com:ComServer>
                  <com:ExeServer Executable="LINKED-APP.EXE" DisplayName="Notifications" />
                </com:ComServer></com:Extension></Extensions>
              </Application></Applications>
            </Package>
        """.trimIndent())
        val stage = app.tasks.getByName("stageWinAppPackageMingwX64MainReleaseExecutable") as StageWinAppPackageTask
        stage.runtimeAssetsDirectory.set(runtime.toFile())
        stage.appxManifestFiles.setFrom(manifest)
        stage.resolvedNuGetPackageManifestFiles.setFrom(emptyList<Any>())
        stage.winAppRestoreLockFiles.setFrom(emptyList<Any>())
        stage.appxResourceArchives.setFrom(emptyList<Any>())
        stage.defaultAppxResourceRoots.set(emptyList())
        stage.defaultAppxResourceFiles.setFrom(emptyList<Any>())
        stage.stage()

        val output = stage.outputDirectory.get().asFile.toPath()
        assertEquals("native executable", Files.readString(output.resolve("animeko-desktop.exe")))
        assertFalse(Files.exists(output.resolve("linked-app.exe")))
        assertTrue(Files.isRegularFile(output.resolve("animeko-desktop.exe.manifest")))
        val stagedManifest = Files.readString(output.resolve("AppxManifest.xml"))
        assertFalse(stagedManifest.contains("linked-app.exe", ignoreCase = true))
        assertTrue(stagedManifest.contains("Executable=\"animeko-desktop.exe\""))
        assertTrue(Files.readString(manifest).contains("Executable=\"linked-app.exe\""))
        assertEquals("linked-app", executable.baseName)
        assertEquals("animeko-desktop.exe", Path.of(executable.runTaskProvider!!.get().executable!!).fileName.toString())
        assertEquals("animeko-desktop", (app.tasks.getByName("stageWindowsPackageRuntimeAssetsMingwX64MainReleaseExecutable")
            as StageWindowsPackageRuntimeAssetsTask).executableBaseName.get())

        val conflict = app.projectDir.toPath().resolve("payload.txt")
        Files.writeString(conflict, "conflict")
        stage.packagePayloadFiles.from(conflict)
        stage.projectPriTargetPaths.put(conflict.toString(), "animeko-desktop.exe")
        val error = runCatching { stage.stage() }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("reserved package path"))
    }

    @Test
    fun native_run_uses_final_module_count_even_when_realized_before_the_second_app() {
        val root = ProjectBuilder.builder().withName("animeko").build()
        val app = ProjectBuilder.builder().withName("app").withParent(root).build()
        app.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val target = app.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64()
        target.binaries.executable()
        app.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        app.extensions.getByType(WindowsExtension::class.java).application { }
        (app as ProjectInternal).evaluate()
        val executable = target.binaries.withType(Executable::class.java).single { it.name == "releaseExecutable" }
        val run = executable.runTaskProvider!!.get()
        assertEquals("animeko.exe", Path.of(run.executable!!).fileName.toString())

        module(root, "app2").extensions.getByType(WindowsExtension::class.java).application { }
        run.actions.first().execute(run)
        assertEquals("animeko-app.exe", Path.of(run.executable!!).fileName.toString())
    }

    @Test
    fun default_names_survive_configuration_cache_and_recompute_when_application_modules_change() {
        val root = Files.createTempDirectory("winapp-executable-names-")
        write(root.resolve("settings.gradle"), "rootProject.name = 'animeko'\ninclude 'app', 'app2', 'library'")
        write(root.resolve("gradle.properties"), """
            org.gradle.jvmargs=-Xmx512m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
        """.trimIndent())
        write(root.resolve("build.gradle"), """
            plugins { id 'io.github.compose-fluent.windows-toolkit' apply false }
            abstract class WriteLauncherName extends DefaultTask {
                @Input abstract Property<String> getLauncherName()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void write() { outputFile.get().asFile.text = launcherName.get() + '.exe' }
            }
            subprojects {
                apply plugin: 'java'
                apply plugin: 'io.github.compose-fluent.windows-toolkit'
                tasks.register('writeLauncherName', WriteLauncherName) {
                    launcherName.set(windows.application.executableBaseName)
                    outputFile.set(layout.buildDirectory.file('launcher-name.txt'))
                }
            }
        """.trimIndent())
        write(root.resolve("app/build.gradle"), "windows { application {} }")
        write(root.resolve("app2/build.gradle"), "windows { application {} }")
        write(root.resolve("library/build.gradle"), "")
        fun run() = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments(":app:writeLauncherName", ":app2:writeLauncherName", "--configuration-cache", "--offline", "--stacktrace")
            .build()
        run()
        assertEquals("animeko-app.exe", Files.readString(root.resolve("app/build/launcher-name.txt")))
        assertEquals("animeko-app2.exe", Files.readString(root.resolve("app2/build/launcher-name.txt")))
        Files.delete(root.resolve("app/build/launcher-name.txt"))
        assertTrue(run().output.contains("Reusing configuration cache."))
        assertEquals("animeko-app.exe", Files.readString(root.resolve("app/build/launcher-name.txt")))
        GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments(":app:writeLauncherName", "--configure-on-demand", "--configuration-cache", "--offline", "--stacktrace")
            .build()
        assertEquals("animeko-app.exe", Files.readString(root.resolve("app/build/launcher-name.txt")))
        write(root.resolve("app2/build.gradle"), "")
        run()
        assertEquals("animeko.exe", Files.readString(root.resolve("app/build/launcher-name.txt")))
    }

    @Test
    fun rejects_names_that_cannot_be_used_as_windows_launchers() {
        val project = module(ProjectBuilder.builder().withName("animeko").build(), "app")
        val windows = project.extensions.getByType(WindowsExtension::class.java)
        windows.application { }
        val launcher = project.tasks.getByName("compileWinAppLauncherJvmMain") as BuildWinAppHostTask
        for (name in listOf("", "../app", "dir\\app", "a:b", "app.", "app ", "app.exe", "CON", "nul.txt", "LPT1")) {
            windows.application.executableBaseName.set(name)
            val error = runCatching { launcher.executableBaseName.get() }.exceptionOrNull()
            val messages = generateSequence(error) { it.cause }.joinToString(" ") { it.message.orEmpty() }
            assertTrue("$name: $messages", messages.contains("windows.application.executableBaseName"))
        }
    }

    private fun module(root: Project, name: String): Project = ProjectBuilder.builder().withName(name).withParent(root).build().also {
        it.pluginManager.apply("java")
        it.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
    }

    private fun assertJvmName(project: Project, name: String, suffix: String = "JvmMain") {
        val host = project.tasks.getByName("buildWinAppHost$suffix") as BuildWinAppHostTask
        val launcher = project.tasks.getByName("compileWinAppLauncher$suffix") as BuildWinAppHostTask
        val runtime = project.tasks.getByName("stageWindowsPackageRuntimeAssets$suffix") as StageWindowsPackageRuntimeAssetsTask
        assertEquals(name, host.executableBaseName.get())
        assertEquals(name, launcher.executableBaseName.get())
        assertEquals("$name.exe", host.launcherExecutable.get().asFile.name)
        assertEquals(name, runtime.executableBaseName.get())
        for (taskName in listOf("stageWinAppPackage", "stageWinAppDevelopmentPackage")) {
            val stage = project.tasks.getByName(taskName + suffix) as StageWinAppPackageTask
            assertEquals(name, stage.executableBaseName.get())
            assertEquals(listOf("$name.exe"), stage.deferredManifestPayloadPaths.get())
            assertEquals(listOf("$name.exe"), stage.reservedPackageFiles.get())
            assertTrue(stage.rewriteApplicationExecutable.get())
        }
        val run = project.tasks.getByName("runWinAppHost$suffix") as RunWinAppHostTask
        assertEquals("$name.exe", run.hostExecutable.get().asFile.name)
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
