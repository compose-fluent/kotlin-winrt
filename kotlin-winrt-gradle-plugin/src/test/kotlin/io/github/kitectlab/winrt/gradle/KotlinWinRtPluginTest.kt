package io.github.kitectlab.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class KotlinWinRtPluginTest {
    @Test
    fun plugin_wires_extension_inputs_to_generation_task() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val extension = project.extensions.getByType(WinRtExtension::class.java)
        extension.namespace("Windows.Foundation")
        extension.type("Windows.Foundation.IStringable")
        extension.winmd("sdk+")
        extension.windowsSdk("10.0.26100.0", includeExtensions = true)
        extension.nugetExecutable.set("nuget.exe")
        extension.nugetCliVersion.set("7.3.1")
        extension.restoreNuGetPackages.set(false)
        extension.useNuGetCliGlobalPackages.set(false)
        extension.nugetGlobalPackagesRoots.add(project.layout.projectDirectory.dir("nuget-cache").asFile.absolutePath)
        extension.windowsAppSdk(
            winuiVersion = "1.8.251105000",
            foundationVersion = "1.8.251104000",
            interactiveExperiencesVersion = "1.8.251104001",
        )

        val task = project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).get()

        assertEquals(listOf("Windows.Foundation"), task.includeNamespaces.get())
        assertEquals(listOf("Windows.Foundation.IStringable"), task.includeTypes.get())
        assertEquals(listOf("sdk+"), task.metadataInputs.get())
        assertEquals("10.0.26100.0", task.windowsSdkVersion.get())
        assertTrue(task.includeWindowsSdkExtensions.get())
        assertEquals("nuget.exe", task.nugetExecutable.get())
        assertEquals("7.3.1", task.nugetCliVersion.get())
        assertEquals(false, task.restoreNuGetPackages.get())
        assertEquals(false, task.useNuGetCliGlobalPackages.get())
        assertEquals(
            listOf(
                "Microsoft.WindowsAppSDK.Foundation@1.8.251104000",
                "Microsoft.WindowsAppSDK.InteractiveExperiences@1.8.251104001",
                "Microsoft.WindowsAppSDK.WinUI@1.8.251105000",
            ),
            task.nugetPackages.get(),
        )
    }

    @Test
    fun winrt_extension_defaults_to_library_and_application_block_selects_application_model() {
        val libraryProject = ProjectBuilder.builder().build()
        libraryProject.pluginManager.apply(KotlinWinRtPlugin::class.java)
        assertFalse(libraryProject.extensions.getByType(WinRtExtension::class.java).applicationEnabled.get())
        assertEquals(
            KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION,
            libraryProject.extensions.extraProperties["kotlinWinRtIdentityElements"],
        )

        val applicationProject = ProjectBuilder.builder().build()
        applicationProject.pluginManager.apply(KotlinWinRtPlugin::class.java)
        applicationProject.extensions.getByType(WinRtExtension::class.java).application {}
        assertTrue(applicationProject.extensions.getByType(WinRtExtension::class.java).applicationEnabled.get())
        assertFalse(
            applicationProject.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION).isCanBeConsumed,
        )
        assertEquals(
            KOTLIN_WINRT_IDENTITY_CONFIGURATION,
            applicationProject.extensions.extraProperties["kotlinWinRtIdentity"],
        )
        assertEquals(
            "generateWinRtApplicationIdentity",
            applicationProject.extensions.extraProperties["kotlinWinRtApplicationIdentityTask"],
        )
        assertEquals(
            "stageWinRtRuntimeAssets",
            applicationProject.extensions.extraProperties["kotlinWinRtRuntimeAssetsTask"],
        )
    }

    @Test
    fun library_plugin_publishes_identity_variant() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)

        val identityElements = project.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)
        assertTrue(identityElements.isCanBeConsumed)
        assertFalse(identityElements.isCanBeResolved)
        assertEquals(
            KOTLIN_WINRT_IDENTITY_USAGE,
            identityElements.attributes.getAttribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE)?.name,
        )
        assertEquals(1, identityElements.outgoing.artifacts.files.files.size)
        project.tasks.named("generateWinRtIdentity", GenerateWinRtIdentityTask::class.java).get()
    }

    @Test
    fun identity_task_writes_projection_identity_json() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val extension = project.extensions.getByType(WinRtExtension::class.java)
        extension.winmd("sdk+")
        extension.namespace("Windows.Foundation")
        extension.type("Windows.Foundation.IStringable")
        extension.windowsSdk("10.0.26100.0", includeExtensions = true)
        extension.windowsAppSdk(
            winuiVersion = "1.8.251105000",
            foundationVersion = "1.8.251104000",
            interactiveExperiencesVersion = "1.8.251104001",
        )

        val task = project.tasks.named("generateWinRtIdentity", GenerateWinRtIdentityTask::class.java).get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"model\": \"library\""))
        assertTrue(json.contains("\"metadataInputs\": [\"sdk+\"]"))
        assertTrue(json.contains("\"includeNamespaces\": [\"Windows.Foundation\"]"))
        assertTrue(json.contains("\"includeTypes\": [\"Windows.Foundation.IStringable\"]"))
        assertTrue(json.contains("\"version\": \"10.0.26100.0\""))
        assertTrue(json.contains("\"includeExtensions\": true"))
        assertTrue(
            json.contains(
                "\"nugetPackages\": [\"Microsoft.WindowsAppSDK.Foundation@1.8.251104000\", " +
                    "\"Microsoft.WindowsAppSDK.InteractiveExperiences@1.8.251104001\", " +
                    "\"Microsoft.WindowsAppSDK.WinUI@1.8.251105000\"]",
            ),
        )
    }

    @Test
    fun application_plugin_resolves_identity_configuration() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).application {}

        val identityConfiguration = project.configurations.getByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION)
        assertFalse(identityConfiguration.isCanBeConsumed)
        assertTrue(identityConfiguration.isCanBeResolved)
        assertEquals(
            KOTLIN_WINRT_IDENTITY_USAGE,
            identityConfiguration.attributes.getAttribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE)?.name,
        )
        project.tasks.named("generateWinRtApplicationIdentity", GenerateWinRtApplicationIdentityTask::class.java).get()
        project.tasks.named("stageWinRtRuntimeAssets", StageWinRtRuntimeAssetsTask::class.java).get()
    }

    @Test
    fun application_plugin_collects_only_dependencies_with_kotlin_winrt_identity_metadata() {
        val root = ProjectBuilder.builder().withName("root").build()
        val library = ProjectBuilder.builder().withName("library").withParent(root).build()
        val runtime = ProjectBuilder.builder().withName("runtime").withParent(root).build()
        val application = ProjectBuilder.builder().withName("application").withParent(root).build()

        library.pluginManager.apply(KotlinWinRtPlugin::class.java)
        runtime.pluginManager.apply("java")
        application.pluginManager.apply("java")
        application.pluginManager.apply(KotlinWinRtPlugin::class.java)
        application.extensions.getByType(WinRtExtension::class.java).application {}
        application.dependencies.add("implementation", application.dependencies.project(mapOf("path" to ":library")))
        application.dependencies.add("implementation", application.dependencies.project(mapOf("path" to ":runtime")))

        val identityConfiguration = application.configurations.getByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION)
        val dependencyProjectPaths = identityConfiguration.dependencies
            .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
            .map { it.path }

        assertEquals(listOf(":library"), dependencyProjectPaths)
    }

    @Test
    fun application_plugin_wires_runtime_assets_into_java_resources() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).application {}
        project.pluginManager.apply("java")

        val processResources = project.tasks.named("processResources").get()
        val dependencies = processResources.taskDependencies.getDependencies(processResources).map { it.name }
        assertTrue("stageWinRtRuntimeAssets" in dependencies)
    }

    @Test
    fun application_plugin_accepts_gradle_application_distribution_model() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).application {}
        project.pluginManager.apply("application")

        project.tasks.named("stageWinRtRuntimeAssets", StageWinRtRuntimeAssetsTask::class.java).get()
        project.extensions.getByType(org.gradle.api.distribution.DistributionContainer::class.java).getByName("main")
    }

    @Test
    fun application_identity_task_writes_dependency_identity_paths() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), "{}")

        val task = project.tasks.register(
            "applicationIdentity",
            GenerateWinRtApplicationIdentityTask::class.java,
        ) { registeredTask ->
            registeredTask.outputFile.set(project.layout.buildDirectory.file("application/kotlin-winrt-application.json"))
            registeredTask.metadataInputs.set(listOf("sdk+"))
            registeredTask.includeNamespaces.set(listOf("Windows.Foundation"))
            registeredTask.includeTypes.set(listOf("Windows.Foundation.IStringable"))
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.nugetPackages.set(listOf("Microsoft.WindowsAppSDK.WinUI@1.8.251105000"))
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"model\": \"application\""))
        assertTrue(json.contains("\"metadataInputs\": [\"sdk+\"]"))
        assertTrue(json.contains("\"nugetPackages\": [\"Microsoft.WindowsAppSDK.WinUI@1.8.251105000\"]"))
        assertTrue(json.contains(dependencyIdentity.absolutePath.replace("\\", "\\\\")))
    }

    @Test
    fun runtime_assets_task_stages_generic_runtime_assets_without_windowsappsdk_framework_layout() {
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("sample.package").resolve("1.0.0")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Sample.Package.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.Package</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(packageRoot.resolve("Sample.Package.dll"), "dll")
        Files.createDirectories(packageRoot.resolve("runtimes/win-x64/native"))
        Files.writeString(packageRoot.resolve("runtimes/win-x64/native/Runtime.Native.dll"), "runtime")
        Files.createDirectories(packageRoot.resolve("runtimes-framework/win-x64/native/Microsoft.UI.Xaml"))
        Files.writeString(packageRoot.resolve("runtimes-framework/win-x64/native/Microsoft.UI.Xaml.Controls.pri"), "pri")
        Files.writeString(packageRoot.resolve("runtimes-framework/win-x64/native/Microsoft.UI.Xaml/Controls.pri"), "nested")
        Files.createDirectories(packageRoot.resolve("include"))
        Files.writeString(packageRoot.resolve("include/WindowsAppSDK-VersionInfo.h"), "version")
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.Package@1.0.0"]}""")

        val task = project.tasks.register(
            "stageRuntimeAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Sample.Package.dll")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Runtime.Native.dll")))
        assertFalse(Files.exists(outputRoot.resolve("Microsoft.UI.Xaml.Controls.pri")))
        assertFalse(Files.exists(outputRoot.resolve("Microsoft.UI.Xaml/Controls.pri")))
        assertFalse(Files.exists(outputRoot.resolve("resources.pri")))
        assertFalse(Files.exists(outputRoot.resolve("include/WindowsAppSDK-VersionInfo.h")))
    }

    @Test
    fun runtime_assets_task_stages_windowsappsdk_self_contained_framework_assets() {
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("microsoft.windowsappsdk.winui").resolve("1.8.251105000")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Microsoft.WindowsAppSDK.WinUI.nuspec"),
            """
            <package>
              <metadata>
                <id>Microsoft.WindowsAppSDK.WinUI</id>
                <version>1.8.251105000</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.createDirectories(packageRoot.resolve("runtimes-framework/win-x64/native/Microsoft.UI.Xaml"))
        Files.writeString(packageRoot.resolve("runtimes-framework/win-x64/native/Microsoft.UI.Xaml.Controls.pri"), "pri")
        Files.writeString(packageRoot.resolve("runtimes-framework/win-x64/native/Microsoft.UI.Xaml/Controls.pri"), "nested")
        Files.createDirectories(packageRoot.resolve("include"))
        Files.writeString(packageRoot.resolve("include/WindowsAppSDK-VersionInfo.h"), "version")
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Microsoft.WindowsAppSDK.WinUI@1.8.251105000"]}""")

        val task = project.tasks.register(
            "stageWindowsAppSdkAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("windowsappsdk-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml.Controls.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml/Controls.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("resources.pri")))
        assertFalse(Files.exists(outputRoot.resolve("include/WindowsAppSDK-VersionInfo.h")))
    }

    @Test
    fun plugin_generates_sources_in_real_gradle_library_project() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-plugin-test-")
        writeGradleFile(
            projectDir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }
            rootProject.name = "kotlin-winrt-plugin-test"
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "2.3.20"
                id("io.github.kitectlab.winrt")
            }

            kotlin {
                jvmToolchain(22)
            }

            winRt {
                type("Windows.Foundation.IStringable")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRtProjections", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRtProjections")?.outcome)
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "build/generated/kotlin-winrt/src/main/kotlin/io/github/kitectlab/winrt/projections/windows/foundation/IStringable.kt",
                ),
            ),
        )
    }
}

private fun writeGradleFile(path: Path, content: String) {
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
}
