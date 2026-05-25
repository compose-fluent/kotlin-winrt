package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

class KotlinWinRtPluginTest {
    @Test
    fun plugin_does_not_project_default_sdk_when_no_projection_inputs_are_declared() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-empty-input-test-")
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
            rootProject.name = "kotlin-winrt-empty-input-test"
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
                id("io.github.composefluent.winrt")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRtIdentity", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRtProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRtIdentity")?.outcome)
        assertFalse(
            Files.exists(projectDir.resolve("build/generated/kotlin-winrt/src/main/kotlin/windows")),
        )
        val identity = projectDir.resolve("build/generated/kotlin-winrt/identity/kotlin-winrt.json").toFile().readText()
        assertTrue(identity.contains("\"includeTypes\": []"))
        assertTrue(identity.contains("\"projectedTypes\": []"))
    }

    @Test
    fun plugin_wires_extension_inputs_to_generation_task() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val extension = project.extensions.getByType(WinRtExtension::class.java)
        extension.namespace("Windows.Foundation")
        extension.type("Windows.Foundation.IStringable")
        extension.excludeNamespace("Sample.Hidden")
        extension.excludeType("Microsoft.UI.Xaml.Controls.WebView2")
        extension.excludeAdditionNamespace("Microsoft.UI.Xaml.Media.Animation")
        extension.winmd("sdk+")
        extension.windowsSdk("10.0.26100.0", includeExtensions = true)
        extension.nugetExecutable.set("nuget.exe")
        extension.nugetCliVersion.set("7.3.1")
        extension.restoreNuGetPackages.set(false)
        extension.useNuGetCliGlobalPackages.set(false)
        extension.nugetGlobalPackagesRoots.add(project.layout.projectDirectory.dir("nuget-cache").asFile.absolutePath)
        extension.runtimeAsset(project.layout.projectDirectory.file("SimpleMathComponent.dll").asFile.absolutePath)
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003")
        extension.namespace("Microsoft")
        extension.type("Windows.UI.Xaml.Interop.Type")
        extension.excludeNamespace("Windows")

        val task = project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).get()

        assertEquals(listOf("Windows.Foundation", "Microsoft"), task.includeNamespaces.get())
        assertTrue("Windows.Foundation.IStringable" in task.includeTypes.get())
        assertTrue("Windows.UI.Xaml.Interop.Type" in task.includeTypes.get())
        assertEquals(
            listOf("Sample.Hidden", "Windows"),
            task.excludeNamespaces.get(),
        )
        assertTrue("Microsoft.UI.Xaml.Controls.WebView2" in task.excludeTypes.get())
        assertEquals(listOf("Microsoft.UI.Xaml.Media.Animation"), task.additionExcludeNamespaces.get())
        assertEquals(listOf("sdk+"), task.metadataInputs.get())
        assertEquals("10.0.26100.0", task.windowsSdkVersion.get())
        assertTrue(task.includeWindowsSdkExtensions.get())
        assertEquals("nuget.exe", task.nugetExecutable.get())
        assertEquals("7.3.1", task.nugetCliVersion.get())
        assertEquals(false, task.restoreNuGetPackages.get())
        assertEquals(false, task.useNuGetCliGlobalPackages.get())
        assertEquals(
            listOf(project.layout.projectDirectory.file("SimpleMathComponent.dll").asFile.absolutePath),
            project.extensions.getByType(WinRtExtension::class.java).runtimeAssets.get(),
        )
        assertEquals(
            listOf("Microsoft.WindowsAppSDK@1.8.260416003"),
            task.nugetPackages.get(),
        )
        assertEquals(
            listOf(
                "-Xmx128m",
                "-Xss512k",
                "-XX:+UseSerialGC",
                "-XX:ReservedCodeCacheSize=32m",
            ),
            task.authoringScannerJvmArgs.get(),
        )
        assertEquals(
            listOf(
                "-Xmx1024m",
                "-XX:+UseSerialGC",
                "-Dfile.encoding=UTF-8",
            ),
            task.generatorWorkerJvmArgs.get(),
        )
        val generatorWorkerConfiguration = project.configurations.getByName(KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION)
        assertTrue(generatorWorkerConfiguration.isCanBeResolved)
        assertFalse(generatorWorkerConfiguration.isCanBeConsumed)
        assertEquals(
            setOf("winrt-runtime", "winrt-metadata", "winrt-generator", "kotlinpoet"),
            generatorWorkerConfiguration.dependencies.mapTo(mutableSetOf()) { it.name },
        )
        assertEquals(project.name, task.authoringAssemblyName.get())
        assertEquals("${project.name}.jar", task.authoringTargetArtifactName.get())
    }

    @Test
    fun generation_task_uses_gradle_jar_archive_name_for_authoring_target_artifact() {
        val project = ProjectBuilder.builder().withName("mapped-component").build()
        project.version = "1.2.3"
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRtPlugin::class.java)

        val task = project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).get()

        assertEquals("mapped-component-1.2.3.jar", task.authoringTargetArtifactName.get())
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
            "resolveWinRtRuntimeNuGetPackages",
            applicationProject.extensions.extraProperties["kotlinWinRtRuntimeNuGetPackagesTask"],
        )
        assertEquals(
            "stageWinRtRuntimeAssets",
            applicationProject.extensions.extraProperties["kotlinWinRtRuntimeAssetsTask"],
        )
        assertEquals(
            "stageWinRtApplicationPackage",
            applicationProject.extensions.extraProperties["kotlinWinRtApplicationPackageTask"],
        )
        assertEquals(
            "packageWinRtApplication",
            applicationProject.extensions.extraProperties["kotlinWinRtPackageTask"],
        )
        assertEquals(
            "signWinRtApplicationPackage",
            applicationProject.extensions.extraProperties["kotlinWinRtSignPackageTask"],
        )
        assertEquals(
            "installWinRtApplicationPackage",
            applicationProject.extensions.extraProperties["kotlinWinRtInstallPackageTask"],
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
    fun library_identity_variant_publishes_transitive_winrt_identity_project_dependencies() {
        val root = ProjectBuilder.builder().withName("root").build()
        val libA = ProjectBuilder.builder().withName("liba").withParent(root).build()
        val libB = ProjectBuilder.builder().withName("libb").withParent(root).build()
        val runtime = ProjectBuilder.builder().withName("runtime").withParent(root).build()

        libA.pluginManager.apply(KotlinWinRtPlugin::class.java)
        runtime.pluginManager.apply("java")
        libB.pluginManager.apply("java")
        libB.pluginManager.apply(KotlinWinRtPlugin::class.java)
        libB.dependencies.add("implementation", libB.dependencies.project(mapOf("path" to ":liba")))
        libB.dependencies.add("implementation", libB.dependencies.project(mapOf("path" to ":runtime")))

        val identityElements = libB.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)
        val dependencyProjectPaths = identityElements.dependencies
            .filterIsInstance<ProjectDependency>()
            .map { it.path }

        assertEquals(listOf(":liba"), dependencyProjectPaths)
    }

    @Test
    fun identity_task_writes_projection_identity_json() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val extension = project.extensions.getByType(WinRtExtension::class.java)
        extension.winmd("sdk+")
        extension.runtimeAsset("SimpleMathComponent.dll")
        extension.namespace("Windows.Foundation")
        extension.type("Windows.Foundation.IStringable")
        extension.excludeAdditionNamespace("Microsoft.UI.Xaml.Media.Animation")
        extension.windowsSdk("10.0.26100.0", includeExtensions = true)
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003")
        extension.namespace("Microsoft")
        extension.type("Windows.UI.Xaml.Interop.Type")
        extension.excludeNamespace("Windows")
        extension.excludeType("Microsoft.UI.Xaml.Controls.WebView2")

        val task = project.tasks.named("generateWinRtIdentity", GenerateWinRtIdentityTask::class.java).get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"model\": \"library\""))
        assertTrue(json.contains("\"metadataInputs\": [\"sdk+\"]"))
        assertTrue(json.contains("\"runtimeAssets\": [\"SimpleMathComponent.dll\"]"))
        assertTrue(json.contains("\"authoredMetadata\": ["))
        assertTrue(json.contains("kotlin-winrt-authoring"))
        assertTrue(json.contains("${project.name}.winmd"))
        assertTrue(json.contains("\"authoredHostManifests\": ["))
        assertTrue(json.contains("${project.name}.host.json"))
        assertTrue(json.contains("\"authoredTargetArtifacts\": ["))
        assertTrue(json.contains("\"includeNamespaces\": [\"Windows.Foundation\", \"Microsoft\"]"))
        assertTrue(json.contains("\"includeTypes\": [\"Windows.Foundation.IStringable\""))
        assertTrue(json.contains("\"projectedTypes\": ["))
        assertTrue(json.contains("Windows.UI.Xaml.Interop.Type"))
        assertTrue(json.contains("\"excludeNamespaces\": [\"Windows\"]"))
        assertTrue(json.contains("\"excludeTypes\": [\"Microsoft.UI.Xaml.Controls.WebView2\""))
        assertTrue(json.contains("\"additionExcludeNamespaces\": [\"Microsoft.UI.Xaml.Media.Animation\"]"))
        assertTrue(json.contains("\"version\": \"10.0.26100.0\""))
        assertTrue(json.contains("\"includeExtensions\": true"))
        assertTrue(
            json.contains(
                "\"nugetPackages\": [\"Microsoft.WindowsAppSDK@1.8.260416003\"]",
            ),
        )
    }

    @Test
    fun merge_compiler_support_task_combines_local_and_dependency_support_tables() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-compiler-support-merge-test-")
        val localRoot = root.resolve("local")
        val dependencyRoot = root.resolve("dependency")
        val outputRoot = root.resolve("merged")
        Files.createDirectories(localRoot)
        Files.createDirectories(dependencyRoot)
        Files.writeString(
            localRoot.resolve("compiler-support.tsv"),
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	1
            future-support	io.github.composefluent.winrt.projections.support.FutureSupport	future-support.tsv	1
            """.trimIndent(),
        )
        Files.writeString(
            localRoot.resolve("projection-registrar.tsv"),
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata
            """.trimIndent(),
        )
        Files.writeString(
            localRoot.resolve("future-support.tsv"),
            """
            key	value
            alpha	beta
            """.trimIndent(),
        )
        Files.writeString(
            dependencyRoot.resolve("compiler-support.tsv"),
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic	projection-registrar.tsv	1
            generic-type-instantiation	io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations	generic-instantiations.tsv	1
            """.trimIndent(),
        )
        Files.writeString(
            dependencyRoot.resolve("projection-registrar.tsv"),
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName
            windows.system.display.DisplayRequest	Windows.System.Display.DisplayRequest	RuntimeClass	System.Object	windows.system.display.DisplayRequest.Metadata
            """.trimIndent(),
        )
        Files.writeString(
            dependencyRoot.resolve("generic-instantiations.tsv"),
            """
            typeName	typeSignature
            Windows.Foundation.IReference`1<String>	pinterface({61c17706-2d65-11e0-9ae8-d48564015472};string)
            """.trimIndent(),
        )
        val dependencyIdentity = root.resolve("dependency-identity.json")
        Files.writeString(
            dependencyIdentity,
            """
            {
              "compilerSupportManifests": [${dependencyRoot.resolve("compiler-support.tsv").toString().toJsonString()}]
            }
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "mergeWinRtCompilerSupportUnderTest",
            MergeWinRtCompilerSupportTask::class.java,
        ) { registeredTask ->
            registeredTask.localCompilerSupportManifest.set(localRoot.resolve("compiler-support.tsv").toFile())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.outputDirectory.set(outputRoot.toFile())
        }.get()

        task.merge()

        val manifest = Files.readString(outputRoot.resolve("compiler-support.tsv"))
        val projectionSupport = Files.readString(outputRoot.resolve("projection-registrar.tsv"))
        val genericSupport = Files.readString(outputRoot.resolve("generic-instantiations.tsv"))
        val futureSupport = Files.readString(outputRoot.resolve("future-support.tsv"))
        assertTrue(manifest.contains("projection-registrar"))
        assertTrue(manifest.contains("future-support\tio.github.composefluent.winrt.projections.support.FutureSupport"))
        assertTrue(manifest.contains("generic-type-instantiation\tio.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations"))
        assertFalse(manifest.contains("WinRTGenericTypeInstantiationRegistry"))
        assertTrue(projectionSupport.contains("Windows.Foundation.Uri"))
        assertTrue(projectionSupport.contains("Windows.System.Display.DisplayRequest"))
        assertTrue(genericSupport.contains("Windows.Foundation.IReference`1<String>"))
        assertTrue(futureSupport.contains("alpha\tbeta"))
    }

    @Test
    fun merge_compiler_support_task_rejects_retired_runtime_discovery_support_rows() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-retired-compiler-support-test-")
        val localRoot = root.resolve("local")
        val outputRoot = root.resolve("merged")
        Files.createDirectories(localRoot)
        Files.writeString(
            localRoot.resolve("compiler-support.tsv"),
            """
            kind	className	sourceFile	entries
            event-source-mapping	io.github.composefluent.winrt.projections.support.WinRTEventProjectionHelpers	EventSourceMappings.kt	1
            """.trimIndent(),
        )
        Files.writeString(localRoot.resolve("EventSourceMappings.kt"), "object EventSourceMappings")
        val task = project.tasks.register(
            "mergeRetiredWinRtCompilerSupportUnderTest",
            MergeWinRtCompilerSupportTask::class.java,
        ) { registeredTask ->
            registeredTask.localCompilerSupportManifest.set(localRoot.resolve("compiler-support.tsv").toFile())
            registeredTask.outputDirectory.set(outputRoot.toFile())
        }.get()

        try {
            task.merge()
        } catch (error: GradleException) {
            assertTrue(error.message.orEmpty().contains("retired runtime-discovery support kind 'event-source-mapping'"))
            assertFalse(Files.exists(outputRoot.resolve("compiler-support.tsv")))
            return
        }

        throw AssertionError("Expected retired runtime-discovery compiler support rows to fail closed.")
    }

    @Test
    fun dependency_identity_projection_surface_suppresses_downstream_projection_types() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": ["Microsoft.UI.Xaml.Automation"],
              "includeTypes": ["Windows.ApplicationModel.DataTransfer.DataPackageView"],
              "excludeNamespaces": ["Microsoft.UI.Xaml.Controls"],
              "excludeTypes": []
            }
            """.trimIndent(),
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    "Microsoft.UI.Xaml",
                    listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "DependencyProperty",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    "Microsoft.UI.Xaml.Automation",
                    listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation",
                            name = "AutomationProperties",
                            kind = WinRtTypeKind.RuntimeClass,
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "AccessibilityViewProperty",
                                    typeName = "Microsoft.UI.Xaml.DependencyProperty",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    "Microsoft.UI.Xaml.Controls",
                    listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "Button",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    "Windows.ApplicationModel.DataTransfer",
                    listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "DataPackageView",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "Microsoft.UI.Xaml.Automation.AutomationProperties",
                "Microsoft.UI.Xaml.DependencyProperty",
                "Windows.ApplicationModel.DataTransfer.DataPackageView",
            ),
            dependencyProjectedTypeNames(model, listOf(dependencyIdentity)).toList(),
        )
    }

    @Test
    fun dependency_identity_projected_types_bound_downstream_suppression_to_actual_generated_types() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": ["Windows.Data.Json"],
              "includeTypes": [],
              "projectedTypes": ["SimpleMathComponent.SimpleMath"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )
        val model = WinRtMetadataModel(
            listOf(
                WinRtNamespace(
                    "Windows.Data.Json",
                    listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonObject",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    "SimpleMathComponent",
                    listOf(
                        WinRtTypeDefinition(
                            namespace = "SimpleMathComponent",
                            name = "SimpleMath",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("SimpleMathComponent.SimpleMath"),
            dependencyProjectedTypeNames(model, listOf(dependencyIdentity)).toList(),
        )
    }

    @Test
    fun application_plugin_resolves_identity_configuration() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).apply {
            application {}
            runtimeAsset(project.layout.projectDirectory.file("SimpleMathComponent.dll").asFile.absolutePath)
        }

        val identityConfiguration = project.configurations.getByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION)
        assertFalse(identityConfiguration.isCanBeConsumed)
        assertTrue(identityConfiguration.isCanBeResolved)
        assertEquals(
            KOTLIN_WINRT_IDENTITY_USAGE,
            identityConfiguration.attributes.getAttribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE)?.name,
        )
        project.tasks.named("generateWinRtApplicationIdentity", GenerateWinRtApplicationIdentityTask::class.java).get()
        val stageRuntimeAssets = project.tasks.named("stageWinRtRuntimeAssets", StageWinRtRuntimeAssetsTask::class.java).get()
        assertTrue(stageRuntimeAssets.runtimeAssetFiles.files.any { it.name == "SimpleMathComponent.dll" })
        project.tasks.named("buildWinRtAuthoringHost", BuildWinRtAuthoringHostTask::class.java).get()
    }

    @Test
    fun application_packaging_only_nuget_does_not_expand_projection_surface() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val extension = project.extensions.getByType(WinRtExtension::class.java)
        extension.application {}
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003")

        val task = project.tasks.named("generateWinRtProjections", GenerateWinRtProjectionsTask::class.java).get()
        task.generate()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(outputRoot.resolve("microsoft")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("kotlin-winrt-authoring/metadata-index.tsv")))
    }

    @Test
    fun application_plugin_snapshots_existing_nuget_package_content_roots_for_runtime_staging() {
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

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).apply {
            application {}
            nugetGlobalPackagesRoots.add(globalPackagesRoot.toString())
            nugetPackage("Sample.Package", "1.0.0")
        }

        val task = project.tasks.named("stageWinRtRuntimeAssets", StageWinRtRuntimeAssetsTask::class.java).get()

        assertTrue(
            task.nugetPackageContentFiles.files.any {
                it.toPath().toAbsolutePath().normalize() == packageRoot.toAbsolutePath().normalize()
            },
        )
    }

    @Test
    fun application_plugin_wires_runtime_nuget_resolution_manifest_into_runtime_staging() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).apply {
            application {}
            nugetPackage("Sample.Package", "1.0.0")
        }

        val resolveTask = project.tasks.named(
            "resolveWinRtRuntimeNuGetPackages",
            ResolveWinRtRuntimeNuGetPackagesTask::class.java,
        ).get()
        val stageTask = project.tasks.named("stageWinRtRuntimeAssets", StageWinRtRuntimeAssetsTask::class.java).get()

        assertTrue(
            stageTask.taskDependencies.getDependencies(stageTask).any { it.name == "resolveWinRtRuntimeNuGetPackages" },
        )
        assertTrue(stageTask.resolvedNuGetPackageManifestFiles.files.contains(resolveTask.outputFile.get().asFile))
    }

    @Test
    fun plugin_registers_compiler_plugin_dependency_for_late_kmp_compiler_plugin_classpath_configurations() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val targetConfiguration = project.configurations.create("kotlinCompilerPluginClasspathWinuiJvm")

        assertTrue(targetConfiguration.dependencies.isNotEmpty())
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
    fun application_plugin_collects_kmp_source_set_project_dependencies_with_identity_metadata() {
        val root = ProjectBuilder.builder().withName("root").build()
        val library = ProjectBuilder.builder().withName("library").withParent(root).build()
        val runtime = ProjectBuilder.builder().withName("runtime").withParent(root).build()
        val application = ProjectBuilder.builder().withName("application").withParent(root).build()

        library.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        library.pluginManager.apply(KotlinWinRtPlugin::class.java)
        application.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        application.pluginManager.apply(KotlinWinRtPlugin::class.java)
        application.extensions.getByType(WinRtExtension::class.java).application {}
        application.extensions.configure(
            org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java,
        ) { kotlin ->
            kotlin.jvm("winuiJvm")
            kotlin.sourceSets.named("commonMain").configure { sourceSet ->
                sourceSet.dependencies {
                    implementation(project(mapOf("path" to ":library")))
                    implementation(project(mapOf("path" to ":runtime")))
                }
            }
        }

        val identityConfiguration = application.configurations.getByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION)
        val dependencyProjectPaths = identityConfiguration.dependencies
            .filterIsInstance<ProjectDependency>()
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
        assertTrue("stageWinRtApplicationPackage" in dependencies)
    }

    @Test
    fun packaged_application_plugin_does_not_wire_appx_payload_into_java_resources() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).application { application ->
            application.packaged()
        }
        project.pluginManager.apply("java")

        val processResources = project.tasks.named("processResources").get()
        val dependencies = processResources.taskDependencies.getDependencies(processResources).map { it.name }
        assertFalse("stageWinRtApplicationPackage" in dependencies)
    }

    @Test
    fun application_plugin_accepts_gradle_application_distribution_model() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val packageOutput = project.layout.buildDirectory.file("custom/Contoso.msix")
        val signedPackageOutput = project.layout.buildDirectory.file("custom/Contoso-signed.msix")
        val signingCertificate = project.layout.buildDirectory.file("certificates/contoso.pfx")
        project.extensions.getByType(WinRtExtension::class.java).application { application ->
            application.packaged()
            application.packageOutputFile.set(packageOutput)
            application.makeAppxExecutable.set("C:/Windows Kits/10/bin/makeappx.exe")
            application.generatePackage.set(false)
            application.signedPackageOutputFile.set(signedPackageOutput)
            application.signPackage.set(true)
            application.signToolExecutable.set("C:/Windows Kits/10/bin/signtool.exe")
            application.signingCertificateThumbprint.set("ABCDEF")
            application.signingCertificateFile.set(signingCertificate)
            application.signingCertificatePassword.set("secret")
            application.signingTimestampUrl.set("http://timestamp.example.test")
            application.signingHashAlgorithm.set("SHA384")
            application.installPackage.set(true)
            application.installPowerShellExecutable.set("C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe")
            application.installForceApplicationShutdown.set(false)
            application.packagePayload("build/libs/app.jar", "App/app.jar")
        }
        project.pluginManager.apply("application")

        assertEquals(
            WinRtApplicationPackageMode.Packaged,
            project.extensions.getByType(WinRtExtension::class.java).application.packageMode.get(),
        )
        project.tasks.named("stageWinRtRuntimeAssets", StageWinRtRuntimeAssetsTask::class.java).get()
        project.tasks.named("stageWinRtApplicationPackage", StageWinRtApplicationPackageTask::class.java).get()
        val packageTask = project.tasks.named("packageWinRtApplication", PackageWinRtApplicationTask::class.java).get()
        assertEquals(packageOutput.get().asFile, packageTask.outputFile.get().asFile)
        assertEquals("C:/Windows Kits/10/bin/makeappx.exe", packageTask.makeAppxExecutable.get())
        assertEquals(false, packageTask.generatePackage.get())
        val verifyTask = project.tasks.named("verifyWinRtApplicationPackage", VerifyWinRtApplicationPackageTask::class.java).get()
        assertEquals(packageOutput.get().asFile, verifyTask.packageFile.get().asFile)
        assertEquals("C:/Windows Kits/10/bin/makeappx.exe", verifyTask.makeAppxExecutable.get())
        assertEquals(true, verifyTask.verifyPackage.get())
        val stagePackageTask =
            project.tasks.named("stageWinRtApplicationPackage", StageWinRtApplicationPackageTask::class.java).get()
        assertTrue(stagePackageTask.packagePayloadFiles.files.any { it.path.replace("\\", "/").endsWith("build/libs/app.jar") })
        assertTrue(stagePackageTask.projectPriTargetPaths.get().values.contains("App/app.jar"))
        val signTask = project.tasks.named("signWinRtApplicationPackage", SignWinRtApplicationPackageTask::class.java).get()
        assertEquals(packageOutput.get().asFile, signTask.inputPackageFile.get().asFile)
        assertEquals(signedPackageOutput.get().asFile, signTask.outputFile.get().asFile)
        assertEquals(true, signTask.signPackage.get())
        assertEquals("C:/Windows Kits/10/bin/signtool.exe", signTask.signToolExecutable.get())
        assertEquals("ABCDEF", signTask.signingCertificateThumbprint.get())
        assertEquals(signingCertificate.get().asFile, signTask.signingCertificateFile.get().asFile)
        assertEquals("secret", signTask.signingCertificatePassword.get())
        assertEquals("http://timestamp.example.test", signTask.signingTimestampUrl.get())
        assertEquals("SHA384", signTask.signingHashAlgorithm.get())
        val installTask =
            project.tasks.named("installWinRtApplicationPackage", InstallWinRtApplicationPackageTask::class.java).get()
        assertEquals(signedPackageOutput.get().asFile, installTask.packageFile.get().asFile)
        assertEquals(true, installTask.installPackage.get())
        assertEquals(
            "C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe",
            installTask.powerShellExecutable.get(),
        )
        assertEquals(false, installTask.forceApplicationShutdown.get())
        project.extensions.getByType(org.gradle.api.distribution.DistributionContainer::class.java).getByName("main")
    }

    @Test
    fun application_install_task_uses_unsigned_package_when_signing_is_disabled() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val packageOutput = project.layout.buildDirectory.file("custom/UnsignedInstall.msix")
        val signedPackageOutput = project.layout.buildDirectory.file("custom/UnsignedInstall-signed.msix")
        project.extensions.getByType(WinRtExtension::class.java).application { application ->
            application.packaged()
            application.packageOutputFile.set(packageOutput)
            application.signedPackageOutputFile.set(signedPackageOutput)
            application.signPackage.set(false)
            application.installPackage.set(true)
        }
        project.pluginManager.apply("application")

        val signTask = project.tasks.named("signWinRtApplicationPackage", SignWinRtApplicationPackageTask::class.java).get()
        val installTask =
            project.tasks.named("installWinRtApplicationPackage", InstallWinRtApplicationPackageTask::class.java).get()

        assertEquals(false, signTask.signPackage.get())
        assertFalse(signTask.onlyIf.isSatisfiedBy(signTask))
        assertEquals(packageOutput.get().asFile, installTask.packageFile.get().asFile)
        assertEquals(true, installTask.installPackage.get())
        assertTrue(installTask.onlyIf.isSatisfiedBy(installTask))
    }

    @Test
    fun application_install_task_uses_explicit_install_package_file() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        val packageOutput = project.layout.buildDirectory.file("custom/Default.msix")
        val signedPackageOutput = project.layout.buildDirectory.file("custom/Default-signed.msix")
        val installPackageFile = project.layout.buildDirectory.file("custom/InstallOverride.msix")
        project.extensions.getByType(WinRtExtension::class.java).application { application ->
            application.packaged()
            application.packageOutputFile.set(packageOutput)
            application.signedPackageOutputFile.set(signedPackageOutput)
            application.installPackageFile.set(installPackageFile)
            application.signPackage.set(true)
            application.installPackage.set(true)
        }
        project.pluginManager.apply("application")

        val installTask =
            project.tasks.named("installWinRtApplicationPackage", InstallWinRtApplicationPackageTask::class.java).get()

        assertEquals(installPackageFile.get().asFile, installTask.packageFile.get().asFile)
        assertEquals(true, installTask.installPackage.get())
        assertTrue(installTask.onlyIf.isSatisfiedBy(installTask))
    }

    @Test
    fun application_packaging_tasks_are_skipped_in_default_unpackaged_mode() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRtPlugin::class.java)
        project.extensions.getByType(WinRtExtension::class.java).application {
            it.signPackage.set(true)
            it.installPackage.set(true)
        }

        val packageTask = project.tasks.named("packageWinRtApplication", PackageWinRtApplicationTask::class.java).get()
        val verifyTask = project.tasks.named("verifyWinRtApplicationPackage", VerifyWinRtApplicationPackageTask::class.java).get()
        val signTask = project.tasks.named("signWinRtApplicationPackage", SignWinRtApplicationPackageTask::class.java).get()
        val installTask =
            project.tasks.named("installWinRtApplicationPackage", InstallWinRtApplicationPackageTask::class.java).get()

        assertEquals(WinRtApplicationPackageMode.Unpackaged, project.extensions.getByType(WinRtExtension::class.java).application.packageMode.get())
        assertFalse(packageTask.onlyIf.isSatisfiedBy(packageTask))
        assertFalse(verifyTask.onlyIf.isSatisfiedBy(verifyTask))
        assertFalse(signTask.onlyIf.isSatisfiedBy(signTask))
        assertFalse(installTask.onlyIf.isSatisfiedBy(installTask))
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
            registeredTask.excludeNamespaces.set(emptyList())
            registeredTask.excludeTypes.set(emptyList())
            registeredTask.additionExcludeNamespaces.set(listOf("Microsoft.UI.Xaml"))
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.nugetPackages.set(listOf("Microsoft.WindowsAppSDK.WinUI@1.8.251105000"))
            registeredTask.runtimeAssets.set(listOf("SimpleMathComponent.dll"))
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"model\": \"application\""))
        assertTrue(json.contains("\"metadataInputs\": [\"sdk+\"]"))
        assertTrue(json.contains("\"nugetPackages\": [\"Microsoft.WindowsAppSDK.WinUI@1.8.251105000\"]"))
        assertTrue(json.contains("\"runtimeAssets\": [\"SimpleMathComponent.dll\"]"))
        assertTrue(json.contains("\"additionExcludeNamespaces\": [\"Microsoft.UI.Xaml\"]"))
        assertTrue(json.contains(dependencyIdentity.absolutePath.replace("\\", "\\\\")))
    }

    @Test
    fun runtime_assets_task_stages_local_component_assets_from_application_and_dependency_identity() {
        val project = ProjectBuilder.builder().build()
        val appDll = project.layout.buildDirectory.file("component/AppComponent.dll").get().asFile.toPath()
        val appWinmd = project.layout.buildDirectory.file("component/AppComponent.winmd").get().asFile.toPath()
        val appHostManifest = project.layout.buildDirectory.file("component/AppComponent.host.json").get().asFile.toPath()
        val appJar = project.layout.buildDirectory.file("component/AppComponent.jar").get().asFile.toPath()
        val appHostDll = project.layout.buildDirectory.file("component/AppComponentHost.dll").get().asFile.toPath()
        val dependencyDll = project.layout.buildDirectory.file("dependency/DependencyComponent.dll").get().asFile.toPath()
        val dependencyWinmd = project.layout.buildDirectory.file("dependency/DependencyComponent.winmd").get().asFile.toPath()
        val dependencyHostManifest = project.layout.buildDirectory.file("dependency/DependencyComponent.host.json").get().asFile.toPath()
        val dependencyJar = project.layout.buildDirectory.file("dependency/DependencyComponent.jar").get().asFile.toPath()
        Files.createDirectories(appDll.parent)
        Files.createDirectories(appWinmd.parent)
        Files.createDirectories(appHostManifest.parent)
        Files.createDirectories(appJar.parent)
        Files.createDirectories(appHostDll.parent)
        Files.createDirectories(dependencyDll.parent)
        Files.createDirectories(dependencyWinmd.parent)
        Files.createDirectories(dependencyHostManifest.parent)
        Files.createDirectories(dependencyJar.parent)
        Files.writeString(appDll, "app")
        Files.writeString(appWinmd, "app-winmd")
        Files.writeString(
            appHostManifest,
            """{"assemblyName":"AppComponent","targetArtifact":"AppComponent.jar","activatableClasses":["sample.AppComponent"],"activatableClassTargets":{"sample.AppComponent":"AppComponent.jar"}}""",
        )
        Files.writeString(appJar, "app-jar")
        Files.writeString(appHostDll, "app-host-dll")
        Files.writeString(dependencyDll, "dependency")
        Files.writeString(dependencyWinmd, "winmd")
        Files.writeString(
            dependencyHostManifest,
            """{"assemblyName":"DependencyComponent","targetArtifact":"DependencyComponent.jar","activatableClasses":["sample.DependencyComponent"],"activatableClassTargets":{"sample.DependencyComponent":"DependencyComponent.jar"}}""",
        )
        Files.writeString(dependencyJar, "dependency-jar")
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"runtimeAssets":["${dependencyDll.toString().replace("\\", "\\\\")}"],"authoredMetadata":["${dependencyWinmd.toString().replace("\\", "\\\\")}"],"authoredHostManifests":["${dependencyHostManifest.toString().replace("\\", "\\\\")}"],"authoredTargetArtifacts":["${dependencyJar.toString().replace("\\", "\\\\")}"]}""",
        )

        val task = project.tasks.register(
            "stageLocalComponentAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(listOf(appDll.toString()))
            registeredTask.runtimeAssetFiles.from(appDll)
            registeredTask.dependencyRuntimeAssetFiles.from(dependencyDll)
            registeredTask.authoredMetadataFiles.from(appWinmd)
            registeredTask.authoredHostManifestFiles.from(appHostManifest)
            registeredTask.authoredTargetArtifactFiles.from(appJar)
            registeredTask.authoredHostDllFiles.from(appHostDll)
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        Files.createDirectories(task.outputDirectory.get().asFile.toPath().resolve("registrations/stale"))
        Files.writeString(task.outputDirectory.get().asFile.toPath().resolve("resources.pri"), "stale")
        Files.writeString(
            task.outputDirectory.get().asFile.toPath().resolve("registrations/stale/LiftedWinRTClassRegistrations.xml"),
            "stale",
        )
        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("AppComponent.dll")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("AppComponent.winmd")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("AppComponent.host.json")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("AppComponent.jar")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("AppComponentHost.dll")))
        assertTrue(Files.readString(outputRoot.resolve("AppComponent.runtimeconfig.json")).contains("\"sample.AppComponent\": \"AppComponent.jar\""))
        assertTrue(Files.isRegularFile(outputRoot.resolve("DependencyComponent.dll")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("DependencyComponent.winmd")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("DependencyComponent.host.json")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("DependencyComponent.jar")))
        assertTrue(Files.readString(outputRoot.resolve("DependencyComponent.runtimeconfig.json")).contains("\"sample.DependencyComponent\": \"DependencyComponent.jar\""))
    }

    @Test
    fun authoring_host_task_generates_reference_style_native_exports() {
        val project = ProjectBuilder.builder().build()
        val manifest = project.layout.buildDirectory.file("component/SampleComponent.host.json").get().asFile.toPath()
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """{"assemblyName":"SampleComponent","targetArtifact":"SampleComponent.jar","activatableClasses":["sample.Component"],"activatableClassTargets":{"sample.Component":"SampleComponent.jar"}}""",
        )
        val task = project.tasks.register(
            "buildAuthoringHost",
            BuildWinRtAuthoringHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("authoring-host/bin"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("authoring-host/src"))
            registeredTask.authoredHostManifestFiles.from(manifest)
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.build()

        val sourceRoot = task.generatedSourceDirectory.get().asFile.toPath()
        val source = Files.readString(sourceRoot.resolve("kotlin_winrt_authoring_host.c"))
        assertTrue(source.contains("DllGetActivationFactory"))
        assertTrue(source.contains("DllCanUnloadNow"))
        assertTrue(source.contains("JNI_GetCreatedJavaVMs"))
        assertTrue(Files.readString(sourceRoot.resolve("kotlin_winrt_authoring_host.def")).contains("DllGetActivationFactory"))
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true) && commandExists("clang-cl.exe")) {
            assertTrue(Files.isRegularFile(task.outputDirectory.get().asFile.toPath().resolve("SampleComponent.dll")))
        }
    }

    @Test
    fun runtime_assets_task_stages_package_declared_framework_layout() {
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
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.writeString(packageRoot.resolve("build/native/LiftedWinRTClassRegistrations.xml"), "<Registrations />")
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
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Sample.Package.dll")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Runtime.Native.dll")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml.Controls.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml/Controls.pri")))
        assertFalse(Files.exists(outputRoot.resolve("resources.pri")))
        assertFalse(Files.exists(outputRoot.resolve("registrations/stale/LiftedWinRTClassRegistrations.xml")))
        assertTrue(
            Files.isRegularFile(
                outputRoot.resolve("registrations/Sample.Package/build/native/LiftedWinRTClassRegistrations.xml"),
            ),
        )
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
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.writeString(packageRoot.resolve("build/native/LiftedWinRTClassRegistrations.xml"), "<Registrations />")
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
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
        }.get()
        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml.Controls.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml/Controls.pri")))
        assertFalse(Files.exists(outputRoot.resolve("resources.pri")))
        assertTrue(
            Files.isRegularFile(
                outputRoot.resolve(
                    "registrations/Microsoft.WindowsAppSDK.WinUI/build/native/LiftedWinRTClassRegistrations.xml",
                ),
            ),
        )
        assertFalse(Files.exists(outputRoot.resolve("include/WindowsAppSDK-VersionInfo.h")))
    }

    @Test
    fun runtime_assets_task_uses_application_pri_manifest_language_and_index_name() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("sample.resources").resolve("1.0.0")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Sample.Resources.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.Resources</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.createDirectories(packageRoot.resolve("runtimes-framework/win-x64/native/Component"))
        Files.writeString(packageRoot.resolve("runtimes-framework/win-x64/native/Component/Controls.pri"), "pri")
        val manifest = project.layout.buildDirectory.file("Package.appxmanifest").get().asFile.toPath()
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Resources>
                <Resource Language="fr-FR" />
              </Resources>
            </Package>
            """.trimIndent(),
        )
        val makePriLog = project.layout.buildDirectory.file("makepri.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(
            project.layout.buildDirectory.file("fake-makepri.cmd").get().asFile.toPath(),
            makePriLog,
            languagePri = "fr-FR",
        )
        val projectResources = project.layout.buildDirectory.dir("project-resources").get().asFile.toPath()
        Files.createDirectories(projectResources.resolve("Strings/en-US"))
        Files.writeString(projectResources.resolve("Strings/en-US/Resources.resw"), "resw")
        val task = project.tasks.register(
            "stagePriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-assets"))
            registeredTask.nugetPackages.set(listOf("Sample.Resources@1.0.0"))
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.projectPriResourceFiles.from(projectResources)
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Component/Controls.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("resources.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("resources.language-fr-FR.pri")))
        assertTrue(Files.isRegularFile(task.temporaryDir.toPath().resolve("project-pri/Appx/Strings/en-US/Resources.resw")))
        val priConfig = Files.readString(task.temporaryDir.toPath().resolve("project-pri-config/priconfig.xml"))
        assertTrue(priConfig.contains("Language"))
        assertTrue(priConfig.contains("fr-FR"))
        assertTrue(priConfig.contains("Scale"))
        assertTrue(priConfig.contains("100"))
        val makePriCalls = Files.readString(makePriLog).replace("\\", "/")
        assertFalse(makePriCalls.contains("createconfig"))
        assertTrue(makePriCalls.contains("new"))
        assertTrue(makePriCalls.contains("/in Contoso.App"))
    }

    @Test
    fun runtime_assets_task_uses_manifest_identity_as_default_project_pri_index_name() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val manifest = project.layout.buildDirectory.file("PackageIdentity.appxmanifest").get().asFile.toPath()
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Contoso.ManifestIdentity" Publisher="CN=Contoso" Version="1.0.0.0" />
              <Resources>
                <Resource Language="en-US" />
              </Resources>
            </Package>
            """.trimIndent(),
        )
        val resource = project.projectDir.toPath().resolve("Strings/en-US/Resources.resw")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "resw")
        val makePriLog = project.layout.buildDirectory.file("makepri-manifest-index.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-manifest-index.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageManifestIndexPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-manifest-index-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("")
            registeredTask.projectPriFallbackIndexName.set("GradleProjectName")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(true)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.defaultProjectPriResourceFiles.from(
                project.fileTree(project.projectDir) { spec -> spec.include("**/*.resw") },
            )
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("/in Contoso.ManifestIdentity"))
    }

    @Test
    fun runtime_assets_task_deduplicates_application_pri_inputs_by_target_path() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("sample.resources").resolve("1.0.0")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Sample.Resources.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.Resources</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.createDirectories(packageRoot.resolve("runtimes-framework/win-x64/native/Component"))
        Files.writeString(packageRoot.resolve("runtimes-framework/win-x64/native/Component/Controls.pri"), "component-pri")
        val makePriLog = project.layout.buildDirectory.file("makepri-dedupe.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-dedupe.cmd").get().asFile.toPath(), makePriLog)
        val projectResources = project.layout.buildDirectory.dir("project-resources-dedupe").get().asFile.toPath()
        Files.createDirectories(projectResources.resolve("Component"))
        Files.writeString(projectResources.resolve("Component/Controls.pri"), "project-pri")
        val task = project.tasks.register(
            "stagePriDedupeAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-dedupe-assets"))
            registeredTask.nugetPackages.set(listOf("Sample.Resources@1.0.0"))
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(projectResources)
        }.get()

        task.stage()

        val projectPriInput = task.temporaryDir.toPath().resolve("project-pri/Component/Controls.pri")
        assertEquals("component-pri", Files.readString(projectPriInput))
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_stages_default_project_resw_resources() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val resource = project.projectDir.toPath().resolve("Strings/en-US/Resources.resw")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "resw")
        val makePriLog = project.layout.buildDirectory.file("makepri-default-resw.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-default-resw.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageDefaultReswPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-default-resw-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(true)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.defaultProjectPriResourceFiles.from(
                project.fileTree(project.projectDir) { spec -> spec.include("**/*.resw") },
            )
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Strings/en-US/Resources.resw"),
            ),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_stages_explicit_project_pri_resource_relative_to_project_root() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val resource = project.projectDir.toPath().resolve("Strings/en-US/AppResources.resw")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "resw")
        val makePriLog = project.layout.buildDirectory.file("makepri-explicit-resw.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-explicit-resw.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageExplicitReswPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-explicit-resw-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(resource)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Strings/en-US/AppResources.resw"),
            ),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_stages_explicit_project_directories_relative_to_project_root() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val strings = project.projectDir.toPath().resolve("Strings/en-US/DirectoryResources.resw")
        val page = project.projectDir.toPath().resolve("Views/DirectoryPage.xaml")
        val image = project.projectDir.toPath().resolve("Assets/DirectoryLogo.png")
        Files.createDirectories(strings.parent)
        Files.createDirectories(page.parent)
        Files.createDirectories(image.parent)
        Files.writeString(strings, "resw")
        Files.writeString(page, "<Page />")
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        val makePriLog = project.layout.buildDirectory.file("makepri-explicit-directories.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-explicit-directories.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageExplicitDirectoryPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-explicit-directory-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(strings.parent.parent)
            registeredTask.projectPriLayoutFiles.from(page.parent)
            registeredTask.projectPriContentFiles.from(image.parent)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        val projectPriRoot = task.temporaryDir.toPath().resolve("project-pri/Appx")
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Strings/en-US/DirectoryResources.resw")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Views/DirectoryPage.xaml")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Assets/DirectoryLogo.png")))
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun application_package_task_applies_explicit_project_pri_target_paths() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val resource = project.projectDir.toPath().resolve("Resources/AppResources.resw")
        val page = project.projectDir.toPath().resolve("Views/AppPage.xaml")
        val image = project.projectDir.toPath().resolve("Images/AppLogo.png")
        Files.createDirectories(resource.parent)
        Files.createDirectories(page.parent)
        Files.createDirectories(image.parent)
        Files.writeString(resource, "resw")
        Files.writeString(page, "<Page />")
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        val makePriLog = project.layout.buildDirectory.file("makepri-target-path.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-target-path.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageTargetPathApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-target-path"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(resource)
            registeredTask.projectPriLayoutFiles.from(page)
            registeredTask.projectPriContentFiles.from(image)
            registeredTask.projectPriTargetPaths.put(resource.toAbsolutePath().normalize().toString(), "Strings/en-US/Resources.resw")
            registeredTask.projectPriTargetPaths.put(page.toAbsolutePath().normalize().toString(), "Xaml/MainPage.xaml")
            registeredTask.projectPriTargetPaths.put(image.toAbsolutePath().normalize().toString(), "Assets/Logo.png")
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val projectPriRoot = task.temporaryDir.toPath().resolve("project-pri/Appx")
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Strings/en-US/Resources.resw")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Xaml/MainPage.xaml")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Assets/Logo.png")))
        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(outputRoot.resolve("Appx/Strings/en-US/Resources.resw")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Appx/Xaml/MainPage.xaml")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Appx/Assets/Logo.png")))
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun application_package_task_applies_explicit_directory_project_pri_target_paths() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val resource = project.projectDir.toPath().resolve("InputResources/en-US/AppResources.resw")
        val page = project.projectDir.toPath().resolve("InputViews/MainPage.xaml")
        val image = project.projectDir.toPath().resolve("InputAssets/Logo.png")
        val embed = project.projectDir.toPath().resolve("InputEmbed/Payload.bin")
        listOf(resource, page, image, embed).forEach { Files.createDirectories(it.parent) }
        Files.writeString(resource, "resw")
        Files.writeString(page, "<Page />")
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        Files.write(embed, byteArrayOf(1, 2, 3))
        val makePriLog = project.layout.buildDirectory.file("makepri-directory-target-path.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(
            project.layout.buildDirectory.file("fake-makepri-directory-target-path.cmd").get().asFile.toPath(),
            makePriLog,
        )
        val task = project.tasks.register(
            "stageDirectoryTargetPathApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-directory-target-path"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(resource.parent.parent)
            registeredTask.projectPriLayoutFiles.from(page.parent)
            registeredTask.projectPriContentFiles.from(image.parent)
            registeredTask.projectPriEmbedFiles.from(embed.parent)
            registeredTask.projectPriTargetPaths.put(resource.parent.parent.toAbsolutePath().normalize().toString(), "Strings")
            registeredTask.projectPriTargetPaths.put(page.parent.toAbsolutePath().normalize().toString(), "Xaml")
            registeredTask.projectPriTargetPaths.put(image.parent.toAbsolutePath().normalize().toString(), "Assets")
            registeredTask.projectPriTargetPaths.put(embed.parent.toAbsolutePath().normalize().toString(), "Embedded")
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val projectPriRoot = task.temporaryDir.toPath().resolve("project-pri")
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Appx/Strings/en-US/AppResources.resw")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Appx/Xaml/MainPage.xaml")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Appx/Assets/Logo.png")))
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("embed/Appx/Embedded/Payload.bin")))
        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(outputRoot.resolve("Appx/Strings/en-US/AppResources.resw")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Appx/Xaml/MainPage.xaml")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Appx/Assets/Logo.png")))
        assertFalse(Files.exists(outputRoot.resolve("Appx/Embedded/Payload.bin")))
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun application_package_task_rejects_absolute_project_pri_initial_path() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-absolute-pri-initial-path").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val resource = project.projectDir.toPath().resolve("Resources/AppResources.resw")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "resw")
        val task = project.tasks.register(
            "stageAbsoluteProjectPriInitialPathApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-absolute-pri-initial-path"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("/Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(resource)
            registeredTask.makePriExecutable.set(project.projectDir.toPath().resolve("fake-makepri.cmd").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("projectPriInitialPath must be a relative path inside the package root: /Appx"))
    }

    @Test
    fun application_package_task_rejects_absolute_project_pri_target_path() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-absolute-pri-target-path").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val resource = project.projectDir.toPath().resolve("Resources/AppResources.resw")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, "resw")
        val task = project.tasks.register(
            "stageAbsoluteProjectPriTargetPathApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-absolute-pri-target-path"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(resource)
            registeredTask.projectPriTargetPaths.put(resource.toAbsolutePath().normalize().toString(), "/Strings/en-US/Resources.resw")
            registeredTask.makePriExecutable.set(project.projectDir.toPath().resolve("fake-makepri.cmd").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("projectPriTargetPaths must be a relative path inside the package root: /Strings/en-US/Resources.resw"))
    }

    @Test
    fun application_package_task_honors_project_pri_excluded_from_build() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val includedResource = project.projectDir.toPath().resolve("Strings/en-US/Included.resw")
        val excludedResource = project.projectDir.toPath().resolve("Strings/en-US/Excluded.resw")
        val excludedPage = project.projectDir.toPath().resolve("Views/ExcludedPage.xaml")
        val excludedImage = project.projectDir.toPath().resolve("Assets/ExcludedLogo.png")
        listOf(includedResource, excludedResource, excludedPage, excludedImage).forEach { Files.createDirectories(it.parent) }
        Files.writeString(includedResource, "included")
        Files.writeString(excludedResource, "excluded")
        Files.writeString(excludedPage, "<Page />")
        Files.write(excludedImage, byteArrayOf(0x50, 0x4e, 0x47))
        val makePriLog = project.layout.buildDirectory.file("makepri-excluded-from-build.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(
            project.layout.buildDirectory.file("fake-makepri-excluded-from-build.cmd").get().asFile.toPath(),
            makePriLog,
        )
        val task = project.tasks.register(
            "stageExcludedFromBuildApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-excluded-from-build"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(true)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.defaultProjectPriResourceFiles.from(includedResource, excludedResource)
            registeredTask.projectPriLayoutFiles.from(excludedPage)
            registeredTask.projectPriContentFiles.from(excludedImage)
            registeredTask.projectPriExcludedFromBuildPaths.add(excludedResource.toAbsolutePath().normalize().toString())
            registeredTask.projectPriExcludedFromBuildPaths.add(excludedPage.toAbsolutePath().normalize().toString())
            registeredTask.projectPriExcludedFromBuildPaths.add(excludedImage.toAbsolutePath().normalize().toString())
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val projectPriRoot = task.temporaryDir.toPath().resolve("project-pri/Appx")
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Strings/en-US/Included.resw")))
        assertFalse(Files.exists(projectPriRoot.resolve("Strings/en-US/Excluded.resw")))
        assertFalse(Files.exists(projectPriRoot.resolve("Views/ExcludedPage.xaml")))
        assertFalse(Files.exists(projectPriRoot.resolve("Assets/ExcludedLogo.png")))
        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(outputRoot.resolve("Appx/Views/ExcludedPage.xaml")))
        assertFalse(Files.exists(outputRoot.resolve("Appx/Assets/ExcludedLogo.png")))
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun application_package_task_stages_project_pri_embed_files() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val embed = project.projectDir.toPath().resolve("Generated/EmbeddedPayload.bin")
        val excludedEmbed = project.projectDir.toPath().resolve("Generated/ExcludedPayload.bin")
        Files.createDirectories(embed.parent)
        Files.write(embed, byteArrayOf(1, 2, 3))
        Files.write(excludedEmbed, byteArrayOf(4, 5, 6))
        val makePriLog = project.layout.buildDirectory.file("makepri-embed-files.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(
            project.layout.buildDirectory.file("fake-makepri-embed-files.cmd").get().asFile.toPath(),
            makePriLog,
        )
        val task = project.tasks.register(
            "stageEmbedFileApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-embed-files"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriEmbedFiles.from(embed, excludedEmbed)
            registeredTask.projectPriTargetPaths.put(embed.toAbsolutePath().normalize().toString(), "Embedded/Payload.bin")
            registeredTask.projectPriExcludedFromBuildPaths.add(excludedEmbed.toAbsolutePath().normalize().toString())
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val projectPriRoot = task.temporaryDir.toPath().resolve("project-pri/embed/Appx")
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("Embedded/Payload.bin")))
        assertFalse(Files.exists(projectPriRoot.resolve("Generated/ExcludedPayload.bin")))
        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertFalse(Files.exists(outputRoot.resolve("Appx/Embedded/Payload.bin")))
        assertFalse(Files.exists(outputRoot.resolve("Appx/Generated/ExcludedPayload.bin")))
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun application_package_task_fails_when_makepri_is_missing_for_project_pri() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val page = project.projectDir.toPath().resolve("Views/MainPage.xaml")
        val image = project.projectDir.toPath().resolve("Assets/Logo.png")
        Files.createDirectories(page.parent)
        Files.createDirectories(image.parent)
        Files.writeString(page, "<Page />")
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val task = project.tasks.register(
            "stagePayloadWithoutMakePriApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-missing-makepri"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.projectPriLayoutFiles.from(page)
            registeredTask.projectPriContentFiles.from(image)
            registeredTask.makePriExecutable.set(project.projectDir.toPath().resolve("missing-makepri.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("makepri.exe was not found"))
        assertFalse(Files.exists(task.outputDirectory.get().asFile.toPath().resolve("resources.pri")))
    }

    @Test
    fun application_package_task_fails_when_makepri_cannot_start_for_project_pri() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-bad-makepri").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val page = project.projectDir.toPath().resolve("Views/MainPage.xaml")
        val image = project.projectDir.toPath().resolve("Assets/Logo.png")
        Files.createDirectories(page.parent)
        Files.createDirectories(image.parent)
        Files.writeString(page, "<Page />")
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val makePri = project.layout.buildDirectory.file("fake-bad-makepri.exe").get().asFile.toPath()
        Files.createDirectories(makePri.parent)
        Files.writeString(makePri, "not an executable")
        val task = project.tasks.register(
            "stagePayloadWithBadMakePriApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-bad-makepri"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.projectPriLayoutFiles.from(page)
            registeredTask.projectPriContentFiles.from(image)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to generate application PRI"))
        assertFalse(Files.exists(task.outputDirectory.get().asFile.toPath().resolve("resources.pri")))
    }

    @Test
    fun application_package_task_rejects_invalid_appx_manifest() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-invalid-manifest").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("InvalidPackage.appxmanifest")
        Files.writeString(
            manifest,
            """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Contoso.App" Version="1.0" />
            </Package>
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "stageInvalidManifestApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-invalid-manifest"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("Invalid AppX manifest"))
        assertTrue(message.contains("Identity must declare Publisher"))
        assertTrue(message.contains("Version must use four numeric components"))
        assertTrue(message.contains("manifest must contain a Properties element"))
        assertTrue(message.contains("manifest must contain an Applications element"))
    }

    @Test
    fun application_package_task_rejects_incomplete_appx_properties_manifest() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-incomplete-properties-manifest").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("IncompletePropertiesPackage.appxmanifest")
        Files.writeString(
            manifest,
            """
            <Package
                xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
              <Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" />
              <Properties>
                <DisplayName>Contoso</DisplayName>
              </Properties>
              <Applications>
                <Application Id="App" Executable="App/Contoso.exe" EntryPoint="Contoso.App">
                  <uap:VisualElements
                      DisplayName="Contoso"
                      Description="Contoso app"
                      BackgroundColor="transparent"
                      Square150x150Logo="Assets/Square150x150Logo.png"
                      Square44x44Logo="Assets/Square44x44Logo.png" />
                </Application>
              </Applications>
            </Package>
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "stageIncompletePropertiesManifestPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-incomplete-properties-manifest"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("Properties must declare PublisherDisplayName"))
        assertTrue(message.contains("Properties must declare Logo"))
    }

    @Test
    fun application_package_task_rejects_incomplete_appx_application_manifest() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-incomplete-application-manifest").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("IncompleteApplicationPackage.appxmanifest")
        Files.writeString(
            manifest,
            """
            <Package
                xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
              <Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" />
              <Properties>
                <DisplayName>Contoso</DisplayName>
                <PublisherDisplayName>Contoso</PublisherDisplayName>
                <Logo>Assets/StoreLogo.png</Logo>
              </Properties>
              <Applications>
                <Application>
                  <uap:VisualElements DisplayName="Contoso" Description="Contoso app" BackgroundColor="transparent" />
                </Application>
              </Applications>
            </Package>
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "stageIncompleteApplicationManifestPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-incomplete-application-manifest"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("Application[0] must declare Id"))
        assertTrue(message.contains("Application[0] must declare Executable"))
        assertTrue(message.contains("Application[0] must declare EntryPoint"))
        assertTrue(message.contains("VisualElements must declare Square150x150Logo"))
        assertTrue(message.contains("VisualElements must declare Square44x44Logo"))
    }

    @Test
    fun application_package_task_rejects_application_manifest_without_visual_elements() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-no-visual-elements-manifest").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("NoVisualElementsPackage.appxmanifest")
        Files.writeString(
            manifest,
            """
            <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" />
              <Properties>
                <DisplayName>Contoso</DisplayName>
                <PublisherDisplayName>Contoso</PublisherDisplayName>
                <Logo>Assets/StoreLogo.png</Logo>
              </Properties>
              <Applications>
                <Application Id="App" Executable="App/Contoso.exe" EntryPoint="Contoso.App" />
              </Applications>
            </Package>
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "stageNoVisualElementsApplicationManifestPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-no-visual-elements-manifest"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Application[0] must contain a VisualElements element"))
    }

    @Test
    fun application_package_task_stages_explicit_package_payloads() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-payload").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        writeManifestPayloadReferences(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val appJar = project.layout.buildDirectory.file("libs/app.jar").get().asFile.toPath()
        val nativePayload = project.projectDir.toPath().resolve("native/x64/component.dll")
        Files.createDirectories(appJar.parent)
        Files.createDirectories(nativePayload.parent)
        Files.writeString(appJar, "jar")
        Files.writeString(nativePayload, "dll")
        val task = project.tasks.register(
            "stageExplicitPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-explicit-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.packagePayloadFiles.from(appJar, nativePayload.parent)
            registeredTask.projectPriTargetPaths.put(appJar.toAbsolutePath().normalize().toString(), "App/app.jar")
            registeredTask.projectPriTargetPaths.put(nativePayload.parent.toAbsolutePath().normalize().toString(), "App/native")
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("AppxManifest.xml")))
        assertEquals("jar", Files.readString(outputRoot.resolve("App/app.jar")))
        assertEquals("dll", Files.readString(outputRoot.resolve("App/native/component.dll")))
    }

    @Test
    fun application_package_task_rejects_missing_manifest_payload_references() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-missing-manifest-payload").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val task = project.tasks.register(
            "stageMissingManifestPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-missing-manifest-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("Invalid AppX manifest payload references"))
        assertTrue(message.contains("Properties Logo references missing package file: Assets/StoreLogo.png"))
        assertTrue(message.contains("Executable references missing package file: App/Contoso.exe"))
        assertTrue(message.contains("VisualElements Square150x150Logo references missing package file: Assets/Square150x150Logo.png"))
        assertTrue(message.contains("VisualElements Square44x44Logo references missing package file: Assets/Square44x44Logo.png"))
    }

    @Test
    fun application_package_task_rejects_manifest_payload_path_escape_with_field_context() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-escaped-manifest-payload").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        writeManifestPayloadReferences(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("EscapedPayloadPackage.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(executable = "../Contoso.exe"),
        )
        val task = project.tasks.register(
            "stageEscapedManifestPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-escaped-manifest-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("Invalid AppX manifest payload references"))
        assertTrue(message.contains("Application[0] Executable must be a relative path inside the package root"))
    }

    @Test
    fun application_package_task_rejects_manifest_payload_absolute_path_with_field_context() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-absolute-manifest-payload").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        writeManifestPayloadReferences(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("AbsolutePayloadPackage.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(propertiesLogo = "/Assets/StoreLogo.png"),
        )
        val task = project.tasks.register(
            "stageAbsoluteManifestPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-absolute-manifest-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("Invalid AppX manifest payload references"))
        assertTrue(message.contains("Properties Logo must be a relative path inside the package root: /Assets/StoreLogo.png"))
    }

    @Test
    fun application_package_task_accepts_qualified_manifest_visual_asset_candidates() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-qualified-visual-assets").get().asFile.toPath()
        Files.createDirectories(runtimeAssets.resolve("App"))
        Files.createDirectories(runtimeAssets.resolve("Assets"))
        Files.writeString(runtimeAssets.resolve("App/Contoso.exe"), "exe")
        Files.write(runtimeAssets.resolve("Assets/StoreLogo.png"), byteArrayOf(0x50, 0x4e, 0x47))
        Files.write(runtimeAssets.resolve("Assets/Square150x150Logo.scale-200.png"), byteArrayOf(0x50, 0x4e, 0x47))
        Files.write(runtimeAssets.resolve("Assets/Square44x44Logo.targetsize-256.png"), byteArrayOf(0x50, 0x4e, 0x47))
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val task = project.tasks.register(
            "stageQualifiedVisualAssetsApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-qualified-visual-assets"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("App/Contoso.exe")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Assets/Square150x150Logo.scale-200.png")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("Assets/Square44x44Logo.targetsize-256.png")))
    }

    @Test
    fun application_package_task_default_payload_targets_depend_on_project_resource_root() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-rooted-payload").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        writeManifestPayloadReferences(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val payload = project.projectDir.toPath().resolve("src/package/App/app.jar")
        Files.createDirectories(payload.parent)
        Files.writeString(payload, "jar")
        val rootedTask = project.tasks.register(
            "stageRootedPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-rooted-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.packagePayloadFiles.from(payload)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()
        val fallbackTask = project.tasks.register(
            "stageFallbackPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-fallback-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.dir(project.provider { project.projectDir.parentFile }))
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.packagePayloadFiles.from(payload)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        rootedTask.stage()
        fallbackTask.stage()

        assertEquals(
            project.projectDir.toPath().toAbsolutePath().normalize().toString(),
            rootedTask.getDefaultProjectPriResourceRootPath(),
        )
        assertTrue(Files.isRegularFile(rootedTask.outputDirectory.get().asFile.toPath().resolve("src/package/App/app.jar")))
        assertTrue(Files.isRegularFile(fallbackTask.outputDirectory.get().asFile.toPath().resolve("${project.projectDir.name}/src/package/App/app.jar")))
    }

    @Test
    fun application_package_task_rejects_missing_explicit_package_payloads() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-missing-payload").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val missingPayload = project.layout.buildDirectory.file("libs/missing-app.jar").get().asFile.toPath()
        val task = project.tasks.register(
            "stageMissingPayloadApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-missing-payload"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.packagePayloadFiles.from(missingPayload)
            registeredTask.projectPriTargetPaths.put(missingPayload.toAbsolutePath().normalize().toString(), "App/missing-app.jar")
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Declared package payload does not exist"))
    }

    @Test
    fun application_package_task_rejects_invalid_explicit_package_payload_target_path() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-invalid-payload-target").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        writeManifestPayloadReferences(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val payload = project.layout.buildDirectory.file("libs/app.jar").get().asFile.toPath()
        Files.createDirectories(payload.parent)
        Files.writeString(payload, "jar")
        val task = project.tasks.register(
            "stageInvalidPackagePayloadTargetApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-invalid-payload-target"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.packagePayloadFiles.from(payload)
            registeredTask.projectPriTargetPaths.put(payload.toAbsolutePath().normalize().toString(), "../escape.jar")
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("packagePayload target path must be a relative path inside the package root"))
        assertFalse(Files.exists(task.outputDirectory.get().asFile.toPath().resolve("escape.jar")))
    }

    @Test
    fun application_package_task_rejects_absolute_explicit_package_payload_target_path() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-absolute-payload-target").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        writeManifestPayloadReferences(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val payload = project.layout.buildDirectory.file("libs/app.jar").get().asFile.toPath()
        Files.createDirectories(payload.parent)
        Files.writeString(payload, "jar")
        val task = project.tasks.register(
            "stageAbsolutePackagePayloadTargetApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-absolute-payload-target"))
            registeredTask.generateProjectPri.set(false)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.packagePayloadFiles.from(payload)
            registeredTask.projectPriTargetPaths.put(payload.toAbsolutePath().normalize().toString(), "/App/app.jar")
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("packagePayload target path must be a relative path inside the package root: /App/app.jar"))
        assertFalse(Files.exists(task.outputDirectory.get().asFile.toPath().resolve("App/app.jar")))
    }

    @Test
    fun application_package_task_rejects_missing_explicit_project_pri_inputs() {
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-missing-pri-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets)
        val manifest = project.projectDir.toPath().resolve("Package.appxmanifest")
        Files.writeString(
            manifest,
            appxManifestXml(),
        )
        val missingResource = project.layout.buildDirectory.file("missing/Strings/en-US/Resources.resw").get().asFile.toPath()
        val task = project.tasks.register(
            "stageMissingProjectPriInputApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-missing-pri-input"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.appxManifestFiles.from(manifest)
            registeredTask.projectPriResourceFiles.from(missingResource)
            registeredTask.makePriExecutable.set("")
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Declared project PRI input does not exist"))
    }

    @Test
    fun package_application_task_invokes_makeappx_for_staged_package_root() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        writeManifestPayloadReferences(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        Files.writeString(packageRoot.resolve("resources.pri"), "pri")
        val makeAppxLog = project.layout.buildDirectory.file("makeappx.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val outputFile = project.layout.buildDirectory.file("packages/Contoso.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplication",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.pack()

        assertTrue(Files.isRegularFile(outputFile))
        val makeAppxCalls = Files.readString(makeAppxLog).replace("\\", "/")
        assertTrue(makeAppxCalls.contains("pack"))
        assertTrue(makeAppxCalls.contains("/d"))
        assertTrue(makeAppxCalls.contains("staged-appx"))
        assertTrue(makeAppxCalls.contains("/p"))
        assertTrue(makeAppxCalls.contains("Contoso.msix"))
        assertTrue(makeAppxCalls.contains("/o"))
    }

    @Test
    fun package_application_task_rejects_output_inside_staged_package_root() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-self-output").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        writeManifestPayloadReferences(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        val makeAppxLog = project.layout.buildDirectory.file("makeappx-self-output.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx-self-output.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val outputFile = packageRoot.resolve("Contoso.msix")
        val task = project.tasks.register(
            "packageApplicationWithSelfIncludedOutput",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package output is inside the staged package root"))
        assertFalse(Files.exists(outputFile))
        assertFalse(Files.exists(makeAppxLog))
    }

    @Test
    fun package_application_task_rejects_non_appx_msix_output_extension() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-invalid-output-extension").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        writeManifestPayloadReferences(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        val makeAppxLog = project.layout.buildDirectory.file("makeappx-invalid-output-extension.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx-invalid-output-extension.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val outputFile = project.layout.buildDirectory.file("packages/Contoso.zip").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplicationWithInvalidOutputExtension",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file must end with .appx or .msix"))
        assertFalse(Files.exists(outputFile))
        assertFalse(Files.exists(makeAppxLog))
    }

    @Test
    fun package_application_task_does_not_delete_output_when_generation_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-generation-disabled").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        val outputFile = project.layout.buildDirectory.file("packages/GenerationDisabled.msix").get().asFile.toPath()
        Files.createDirectories(outputFile.parent)
        Files.writeString(outputFile, "existing-msix")
        val task = project.tasks.register(
            "packageApplicationWithGenerationDisabled",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(false)
            registeredTask.makeAppxExecutable.set(project.projectDir.toPath().resolve("missing-makeappx.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.pack()

        assertEquals("existing-msix", Files.readString(outputFile))
    }

    @Test
    fun package_application_task_skips_missing_inputs_when_generation_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-generation-disabled-missing").get().asFile.toPath()
        val outputFile = project.layout.buildDirectory.file("packages/GenerationDisabledMissing.msix").get().asFile.toPath()
        Files.createDirectories(outputFile.parent)
        Files.writeString(outputFile, "existing-msix")
        val task = project.tasks.register(
            "packageApplicationWithGenerationDisabledMissingInputs",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(false)
            registeredTask.makeAppxExecutable.set(project.projectDir.toPath().resolve("missing-makeappx.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.pack()

        assertFalse(Files.exists(packageRoot))
        assertEquals("existing-msix", Files.readString(outputFile))
    }

    @Test
    fun package_application_task_fails_when_manifest_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-no-manifest").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        val outputFile = project.layout.buildDirectory.file("packages/NoManifest.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplicationWithoutManifest",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(project.projectDir.toPath().resolve("fake-makeappx.cmd").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("AppxManifest.xml was not staged"))
        assertFalse(Files.exists(outputFile))
    }

    @Test
    fun package_application_task_fails_when_manifest_payload_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-missing-payload").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        val makeAppxLog = project.layout.buildDirectory.file("makeappx-missing-payload.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx-missing-payload.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val outputFile = project.layout.buildDirectory.file("packages/MissingPayload.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplicationWithMissingManifestPayload",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("AppxManifest.xml is invalid"))
        assertTrue(message.contains("Properties Logo references missing package file: Assets/StoreLogo.png"))
        assertTrue(message.contains("Executable references missing package file: App/Contoso.exe"))
        assertFalse(Files.exists(outputFile))
        assertFalse(Files.exists(makeAppxLog))
    }

    @Test
    fun package_application_task_fails_when_makeappx_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-missing-makeappx").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        writeManifestPayloadReferences(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        val outputFile = project.layout.buildDirectory.file("packages/MissingMakeAppx.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplicationWithoutMakeAppx",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(project.projectDir.toPath().resolve("missing-makeappx.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("makeappx.exe was not found"))
        assertFalse(Files.exists(outputFile))
    }

    @Test
    fun package_application_task_fails_when_makeappx_does_not_write_output() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-empty-makeappx").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        writeManifestPayloadReferences(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        val makeAppx = writeFakeMakeAppxWithoutOutput(
            project.layout.buildDirectory.file("fake-makeappx-no-output.cmd").get().asFile.toPath(),
        )
        val outputFile = project.layout.buildDirectory.file("packages/NoOutput.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplicationWithoutMakeAppxOutput",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("did not create appx/msix package"))
        assertFalse(Files.exists(outputFile))
    }

    @Test
    fun package_application_task_fails_when_makeappx_cannot_start() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("staged-appx-bad-makeappx").get().asFile.toPath()
        Files.createDirectories(packageRoot)
        writeManifestPayloadReferences(packageRoot)
        Files.writeString(
            packageRoot.resolve("AppxManifest.xml"),
            appxManifestXml(),
        )
        val makeAppx = project.layout.buildDirectory.file("fake-bad-makeappx.exe").get().asFile.toPath()
        Files.createDirectories(makeAppx.parent)
        Files.writeString(makeAppx, "not an executable")
        val outputFile = project.layout.buildDirectory.file("packages/BadMakeAppx.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "packageApplicationWithBadMakeAppx",
            PackageWinRtApplicationTask::class.java,
        ) { registeredTask ->
            registeredTask.packageDirectory.set(project.layout.dir(project.provider { packageRoot.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { outputFile.toFile() }))
            registeredTask.generatePackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.pack() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to create appx/msix package"))
        assertFalse(Files.exists(outputFile))
    }

    @Test
    fun verify_application_package_task_invokes_makeappx_unpack_for_msix() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/Contoso.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val makeAppxLog = project.layout.buildDirectory.file("makeappx-verify.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx-verify.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val markerFile = project.layout.buildDirectory.file("packages/Contoso.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyApplicationPackage",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.verify()

        assertTrue(Files.isRegularFile(markerFile))
        val marker = Files.readString(markerFile)
        assertTrue(marker.contains("verified=true"))
        assertTrue(marker.contains("packageName=Contoso.msix"))
        assertTrue(marker.contains("packageSha256=a1788eec2ed752ba57ac08832710129e67b47ef2704c83466bb6fa12eb855dbe"))
        assertTrue(Files.isRegularFile(unpackRoot.resolve("AppxManifest.xml")))
        val makeAppxCalls = Files.readString(makeAppxLog).replace("\\", "/")
        assertTrue(makeAppxCalls.contains("unpack"))
        assertTrue(makeAppxCalls.contains("/p"))
        assertTrue(makeAppxCalls.contains("Contoso.msix"))
        assertTrue(makeAppxCalls.contains("/d"))
        assertTrue(makeAppxCalls.contains("verify-unpack"))
    }

    @Test
    fun verify_application_package_task_does_not_delete_marker_when_verification_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/VerifyDisabled.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val markerFile = project.layout.buildDirectory.file("packages/VerifyDisabled.verify.marker").get().asFile.toPath()
        Files.writeString(markerFile, "existing-marker")
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-disabled").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyApplicationPackageDisabled",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(false)
            registeredTask.makeAppxExecutable.set(project.projectDir.toPath().resolve("missing-makeappx.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.verify()

        assertEquals("existing-marker", Files.readString(markerFile))
        assertFalse(Files.exists(unpackRoot))
    }

    @Test
    fun verify_application_package_task_skips_missing_input_when_verification_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/MissingVerifyDisabled.msix").get().asFile.toPath()
        val markerFile = project.layout.buildDirectory.file("packages/MissingVerifyDisabled.verify.marker").get().asFile.toPath()
        Files.createDirectories(markerFile.parent)
        Files.writeString(markerFile, "existing-marker")
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-missing-disabled").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyMissingApplicationPackageDisabled",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(false)
            registeredTask.makeAppxExecutable.set(project.projectDir.toPath().resolve("missing-makeappx.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.verify()

        assertFalse(Files.exists(packageFile))
        assertEquals("existing-marker", Files.readString(markerFile))
        assertFalse(Files.exists(unpackRoot))
    }

    @Test
    fun verify_application_package_task_rejects_non_appx_msix_input_extension() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/Contoso.zip").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "not-msix")
        val markerFile = project.layout.buildDirectory.file("packages/Contoso.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-invalid-extension").get().asFile.toPath()
        val makeAppxLog = project.layout.buildDirectory.file("makeappx-verify-invalid-extension.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx-verify-invalid-extension.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val task = project.tasks.register(
            "verifyApplicationPackageWithInvalidExtension",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.verify() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file must end with .appx or .msix"))
        assertFalse(Files.exists(markerFile))
        assertFalse(Files.exists(makeAppxLog))
    }

    @Test
    fun verify_application_package_task_fails_when_unpacked_manifest_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/NoManifest.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val makeAppx = writeFakeMakeAppxUnpackWithoutManifest(
            project.layout.buildDirectory.file("fake-makeappx-unpack-no-manifest.cmd").get().asFile.toPath(),
        )
        val markerFile = project.layout.buildDirectory.file("packages/NoManifest.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-no-manifest").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyApplicationPackageWithoutUnpackedManifest",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.verify() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("did not unpack an AppxManifest.xml"))
        assertFalse(Files.exists(markerFile))
    }

    @Test
    fun verify_application_package_task_fails_when_makeappx_cannot_start() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/BadMakeAppxVerify.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val makeAppx = project.layout.buildDirectory.file("fake-bad-makeappx-verify.exe").get().asFile.toPath()
        Files.createDirectories(makeAppx.parent)
        Files.writeString(makeAppx, "not an executable")
        val markerFile = project.layout.buildDirectory.file("packages/BadMakeAppxVerify.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-bad-makeappx").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyApplicationPackageWithBadMakeAppx",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.verify() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to verify appx/msix package"))
        assertFalse(Files.exists(markerFile))
    }

    @Test
    fun verify_application_package_task_fails_when_unpacked_manifest_payload_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/MissingPayload.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val makeAppx = writeFakeMakeAppxUnpackWithoutPayload(
            project.layout.buildDirectory.file("fake-makeappx-unpack-no-payload.cmd").get().asFile.toPath(),
        )
        val markerFile = project.layout.buildDirectory.file("packages/MissingPayload.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-no-payload").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyApplicationPackageWithoutUnpackedPayload",
            VerifyWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.markerFile.set(project.layout.file(project.provider { markerFile.toFile() }))
            registeredTask.unpackDirectory.set(project.layout.dir(project.provider { unpackRoot.toFile() }))
            registeredTask.verifyPackage.set(true)
            registeredTask.makeAppxExecutable.set(makeAppx.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val failure = runCatching { task.verify() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        val message = failure?.message.orEmpty()
        assertTrue(message.contains("contains an invalid AppxManifest.xml"))
        assertTrue(message.contains("Properties Logo references missing package file: Assets/StoreLogo.png"))
        assertTrue(message.contains("Executable references missing package file: App/Contoso.exe"))
        assertFalse(Files.exists(markerFile))
    }

    @Test
    fun sign_application_package_task_invokes_signtool_for_packaged_msix() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/Contoso.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signToolLog = project.layout.buildDirectory.file("signtool.log").get().asFile.toPath()
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool.cmd").get().asFile.toPath(),
            signToolLog,
        )
        val signedPackage = project.layout.buildDirectory.file("packages/Contoso-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signApplicationPackage",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("http://timestamp.example.test")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        task.sign()

        assertTrue(Files.isRegularFile(signedPackage))
        assertEquals("unsigned-msix", Files.readString(signedPackage))
        val signToolCalls = Files.readString(signToolLog).replace("\\", "/")
        assertTrue(signToolCalls.contains("sign"))
        assertTrue(signToolCalls.contains("/fd SHA256"))
        assertTrue(signToolCalls.contains("/tr http://timestamp.example.test"))
        assertTrue(signToolCalls.contains("/td SHA256"))
        assertTrue(signToolCalls.contains("/sha1 ABCDEF123456"))
        assertTrue(signToolCalls.contains("Contoso-signed.msix"))
    }

    @Test
    fun sign_application_package_task_uses_configured_certificate_file() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/CertificateFile.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val certificate = project.layout.buildDirectory.file("certificates/test-signing.pfx").get().asFile.toPath()
        Files.createDirectories(certificate.parent)
        Files.writeString(certificate, "pfx")
        val signToolLog = project.layout.buildDirectory.file("signtool-certificate-file.log").get().asFile.toPath()
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool-certificate-file.cmd").get().asFile.toPath(),
            signToolLog,
        )
        val signedPackage = project.layout.buildDirectory.file("packages/CertificateFile-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signApplicationPackageWithCertificateFile",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateFile.set(project.layout.file(project.provider { certificate.toFile() }))
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("secret")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        task.sign()

        assertTrue(Files.isRegularFile(signedPackage))
        assertEquals("unsigned-msix", Files.readString(signedPackage))
        val signToolCalls = Files.readString(signToolLog).replace("\\", "/")
        assertTrue(signToolCalls.contains("/f"))
        assertTrue(signToolCalls.contains("test-signing.pfx"))
        assertTrue(signToolCalls.contains("/p secret"))
        assertFalse(signToolCalls.contains("/sha1 ABCDEF123456"))
        assertFalse(signToolCalls.contains("/a"))
    }

    @Test
    fun sign_application_package_task_does_not_delete_output_when_signing_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/UnsignedDisabled.msix").get().asFile.toPath()
        val signedPackage = project.layout.buildDirectory.file("packages/UnsignedDisabled-signed.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        Files.writeString(signedPackage, "existing-signed-msix")
        val task = project.tasks.register(
            "skipDisabledApplicationPackageSigning",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(false)
            registeredTask.signToolExecutable.set(project.projectDir.toPath().resolve("missing-signtool.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        task.sign()

        assertEquals("existing-signed-msix", Files.readString(signedPackage))
    }

    @Test
    fun sign_application_package_task_skips_missing_input_when_signing_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/MissingUnsignedDisabled.msix").get().asFile.toPath()
        val signedPackage = project.layout.buildDirectory.file("packages/MissingUnsignedDisabled-signed.msix").get().asFile.toPath()
        Files.createDirectories(signedPackage.parent)
        Files.writeString(signedPackage, "existing-signed-msix")
        val task = project.tasks.register(
            "skipDisabledMissingInputApplicationPackageSigning",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(false)
            registeredTask.signToolExecutable.set(project.projectDir.toPath().resolve("missing-signtool.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        task.sign()

        assertFalse(Files.exists(inputPackage))
        assertEquals("existing-signed-msix", Files.readString(signedPackage))
    }

    @Test
    fun sign_application_package_task_rejects_non_appx_msix_package_extension() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/Unsigned.zip").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned")
        val signedPackage = project.layout.buildDirectory.file("packages/Unsigned-signed.msix").get().asFile.toPath()
        val signToolLog = project.layout.buildDirectory.file("signtool-invalid-extension.log").get().asFile.toPath()
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool-invalid-extension.cmd").get().asFile.toPath(),
            signToolLog,
        )
        val task = project.tasks.register(
            "signApplicationPackageWithInvalidExtension",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file must end with .appx or .msix"))
        assertFalse(Files.exists(signedPackage))
        assertFalse(Files.exists(signToolLog))
    }

    @Test
    fun sign_application_package_task_rejects_non_appx_msix_output_extension() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/Unsigned.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signedPackage = project.layout.buildDirectory.file("packages/Unsigned-signed.zip").get().asFile.toPath()
        val signToolLog = project.layout.buildDirectory.file("signtool-invalid-output-extension.log").get().asFile.toPath()
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool-invalid-output-extension.cmd").get().asFile.toPath(),
            signToolLog,
        )
        val task = project.tasks.register(
            "signApplicationPackageWithInvalidOutputExtension",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file must end with .appx or .msix"))
        assertFalse(Files.exists(signedPackage))
        assertFalse(Files.exists(signToolLog))
    }

    @Test
    fun sign_application_package_task_rejects_same_input_and_output_package() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/SamePackage.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool-same-package.cmd").get().asFile.toPath(),
            project.layout.buildDirectory.file("signtool-same-package.log").get().asFile.toPath(),
        )
        val task = project.tasks.register(
            "signApplicationPackageSameInputOutput",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("signed output file must be different"))
        assertEquals("unsigned-msix", Files.readString(inputPackage))
    }

    @Test
    fun sign_application_package_task_fails_when_signtool_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/MissingSignTool.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signedPackage = project.layout.buildDirectory.file("packages/MissingSignTool-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signApplicationPackageWithoutSignTool",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(project.projectDir.toPath().resolve("missing-signtool.exe").toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("signtool.exe was not found"))
        assertFalse(Files.exists(signedPackage))
    }

    @Test
    fun sign_application_package_task_fails_when_input_package_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/MissingInput.msix").get().asFile.toPath()
        val signToolLog = project.layout.buildDirectory.file("signtool-missing-input.log").get().asFile.toPath()
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool-missing-input.cmd").get().asFile.toPath(),
            signToolLog,
        )
        val signedPackage = project.layout.buildDirectory.file("packages/MissingInput-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signMissingApplicationPackage",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file does not exist"))
        assertFalse(Files.exists(signedPackage))
        assertFalse(Files.exists(signToolLog))
    }

    @Test
    fun sign_application_package_task_fails_when_certificate_file_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/MissingCertificate.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signToolLog = project.layout.buildDirectory.file("signtool-missing-certificate.log").get().asFile.toPath()
        val signTool = writeFakeSignTool(
            project.layout.buildDirectory.file("fake-signtool-missing-certificate.cmd").get().asFile.toPath(),
            signToolLog,
        )
        val missingCertificate = project.layout.buildDirectory.file("certificates/missing.pfx").get().asFile.toPath()
        val signedPackage = project.layout.buildDirectory.file("packages/MissingCertificate-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signApplicationPackageWithMissingCertificate",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateFile.set(project.layout.file(project.provider { missingCertificate.toFile() }))
            registeredTask.signingCertificateThumbprint.set("")
            registeredTask.signingCertificatePassword.set("secret")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("signing certificate file does not exist"))
        assertFalse(Files.exists(signedPackage))
        assertFalse(Files.exists(signToolLog))
    }

    @Test
    fun sign_application_package_task_fails_when_signtool_cannot_start() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/BadSignTool.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signTool = project.layout.buildDirectory.file("fake-bad-signtool.exe").get().asFile.toPath()
        Files.createDirectories(signTool.parent)
        Files.writeString(signTool, "not an executable")
        val signedPackage = project.layout.buildDirectory.file("packages/BadSignTool-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signApplicationPackageWithBadSignTool",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to sign appx/msix package"))
        assertFalse(Files.exists(signedPackage))
    }

    @Test
    fun sign_application_package_task_fails_when_signtool_returns_error() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val inputPackage = project.layout.buildDirectory.file("packages/FailedSign.msix").get().asFile.toPath()
        Files.createDirectories(inputPackage.parent)
        Files.writeString(inputPackage, "unsigned-msix")
        val signTool = writeFailingFakeSignTool(
            project.layout.buildDirectory.file("fake-failing-signtool.cmd").get().asFile.toPath(),
        )
        val signedPackage = project.layout.buildDirectory.file("packages/FailedSign-signed.msix").get().asFile.toPath()
        val task = project.tasks.register(
            "signApplicationPackageWithFailingSignTool",
            SignWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.inputPackageFile.set(project.layout.file(project.provider { inputPackage.toFile() }))
            registeredTask.outputFile.set(project.layout.file(project.provider { signedPackage.toFile() }))
            registeredTask.signPackage.set(true)
            registeredTask.signToolExecutable.set(signTool.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.signingCertificateThumbprint.set("ABCDEF123456")
            registeredTask.signingCertificatePassword.set("")
            registeredTask.signingTimestampUrl.set("")
            registeredTask.signingHashAlgorithm.set("SHA256")
        }.get()

        val failure = runCatching { task.sign() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to sign appx/msix package"))
        assertFalse(Files.exists(signedPackage))
    }

    @Test
    fun install_application_package_task_invokes_add_appx_package() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/Contoso.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val powershellLog = project.layout.buildDirectory.file("powershell-install.log").get().asFile.toPath()
        val powershell = writeFakePowerShell(
            project.layout.buildDirectory.file("fake-powershell.cmd").get().asFile.toPath(),
            powershellLog,
        )
        val task = project.tasks.register(
            "installApplicationPackage",
            InstallWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(true)
            registeredTask.powerShellExecutable.set(powershell.toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        task.install()

        val powershellCalls = Files.readString(powershellLog).replace("\\", "/")
        assertTrue(powershellCalls.contains("-NoLogo"))
        assertTrue(powershellCalls.contains("-NoProfile"))
        assertTrue(powershellCalls.contains("-NonInteractive"))
        assertTrue(powershellCalls.contains("Add-AppxPackage"))
        assertTrue(powershellCalls.contains("Contoso.msix"))
        assertTrue(powershellCalls.contains("-ForceApplicationShutdown"))
    }

    @Test
    fun install_application_package_task_skips_missing_inputs_when_install_is_disabled() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/InstallDisabled.msix").get().asFile.toPath()
        val powershellLog = project.layout.buildDirectory.file("powershell-install-disabled.log").get().asFile.toPath()
        val powershell = writeFakePowerShell(
            project.layout.buildDirectory.file("fake-powershell-install-disabled.cmd").get().asFile.toPath(),
            powershellLog,
        )
        val task = project.tasks.register(
            "installApplicationPackageDisabled",
            InstallWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(false)
            registeredTask.powerShellExecutable.set(powershell.toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        task.install()

        assertFalse(Files.exists(packageFile))
        assertFalse(Files.exists(powershellLog))
    }

    @Test
    fun install_application_package_task_fails_when_package_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/MissingInstall.msix").get().asFile.toPath()
        val powershellLog = project.layout.buildDirectory.file("powershell-missing-install.log").get().asFile.toPath()
        val powershell = writeFakePowerShell(
            project.layout.buildDirectory.file("fake-powershell-missing-install.cmd").get().asFile.toPath(),
            powershellLog,
        )
        val task = project.tasks.register(
            "installMissingApplicationPackage",
            InstallWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(true)
            registeredTask.powerShellExecutable.set(powershell.toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        val failure = runCatching { task.install() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file does not exist"))
        assertFalse(Files.exists(powershellLog))
    }

    @Test
    fun install_application_package_task_rejects_non_appx_msix_package_extension() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/Contoso.zip").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "not-msix")
        val powershellLog = project.layout.buildDirectory.file("powershell-install-invalid-extension.log").get().asFile.toPath()
        val powershell = writeFakePowerShell(
            project.layout.buildDirectory.file("fake-powershell-invalid-extension.cmd").get().asFile.toPath(),
            powershellLog,
        )
        val task = project.tasks.register(
            "installApplicationPackageWithInvalidExtension",
            InstallWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(true)
            registeredTask.powerShellExecutable.set(powershell.toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        val failure = runCatching { task.install() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("package file must end with .appx or .msix"))
        assertFalse(Files.exists(powershellLog))
    }

    @Test
    fun install_application_package_task_fails_when_powershell_cannot_start() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/MissingPowerShell.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val task = project.tasks.register(
            "installApplicationPackageWithoutPowerShell",
            InstallWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(true)
            registeredTask.powerShellExecutable.set(project.projectDir.toPath().resolve("missing-powershell.exe").toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        val failure = runCatching { task.install() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to install appx/msix package"))
    }

    @Test
    fun install_application_package_task_fails_when_add_appx_package_fails() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/FailedInstall.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "msix")
        val powershell = writeFailingFakePowerShell(
            project.layout.buildDirectory.file("fake-failing-powershell.cmd").get().asFile.toPath(),
        )
        val task = project.tasks.register(
            "installFailingApplicationPackage",
            InstallWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(true)
            registeredTask.powerShellExecutable.set(powershell.toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        val failure = runCatching { task.install() }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("Failed to install appx/msix package"))
    }

    @Test
    fun application_package_task_writes_project_pri_configuration_input_resfiles() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val runtimeAssets = project.layout.buildDirectory.dir("runtime-assets-input").get().asFile.toPath()
        Files.createDirectories(runtimeAssets.resolve("Component"))
        Files.writeString(runtimeAssets.resolve("Component/Controls.pri"), "component-pri")
        val resource = project.projectDir.toPath().resolve("Strings/en-US/Resources.resw")
        val page = project.projectDir.toPath().resolve("Views/MainPage.xaml")
        val compiledXaml = project.projectDir.toPath().resolve("Views/CompiledPage.xaml")
        val compiledXbf = project.projectDir.toPath().resolve("Views/CompiledPage.xbf")
        val image = project.projectDir.toPath().resolve("Assets/Logo.png")
        val embed = project.projectDir.toPath().resolve("Embedded/Payload.bin")
        listOf(resource, page, compiledXaml, compiledXbf, image, embed).forEach { Files.createDirectories(it.parent) }
        Files.writeString(resource, "resw")
        Files.writeString(page, "<Page />")
        Files.writeString(compiledXaml, "<Page />")
        Files.write(compiledXbf, byteArrayOf(0x58, 0x42, 0x46))
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        Files.write(embed, byteArrayOf(1, 2, 3))
        val makePriLog = project.layout.buildDirectory.file("makepri-config-inputs.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(
            project.layout.buildDirectory.file("fake-makepri-config-inputs.cmd").get().asFile.toPath(),
            makePriLog,
        )
        val task = project.tasks.register(
            "stageConfigurationInputApplicationPackage",
            StageWinRtApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.runtimeAssetsDirectory.set(project.layout.dir(project.provider { runtimeAssets.toFile() }))
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-package-config-inputs"))
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriFallbackIndexName.set("ContosoFallback")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriResourceFiles.from(resource)
            registeredTask.projectPriLayoutFiles.from(page, compiledXaml, compiledXbf)
            registeredTask.projectPriContentFiles.from(image)
            registeredTask.projectPriEmbedFiles.from(embed)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        task.stage()

        val configRoot = task.temporaryDir.toPath().resolve("project-pri-config")
        assertEquals(
            listOf(
                "Appx/Assets/Logo.png",
                "Appx/Views/CompiledPage.xaml",
                "Appx/Views/MainPage.xaml",
            ),
            Files.readAllLines(configRoot.resolve("unfiltered.layout.resfiles")),
        )
        assertEquals(
            listOf("Appx/Assets/Logo.png", "Appx/Views/MainPage.xaml"),
            Files.readAllLines(configRoot.resolve("filtered.layout.resfiles")),
        )
        assertEquals(listOf("Appx/Views/CompiledPage.xaml"), Files.readAllLines(configRoot.resolve("excluded.layout.resfiles")))
        assertEquals(listOf("Appx/Strings/en-US/Resources.resw"), Files.readAllLines(configRoot.resolve("resources.resfiles")))
        assertEquals(listOf("Component/Controls.pri"), Files.readAllLines(configRoot.resolve("pri.resfiles")))
        assertEquals(
            listOf("Appx/Embedded/Payload.bin", "Appx/Views/CompiledPage.xbf"),
            Files.readAllLines(configRoot.resolve("embed/embed.resfiles")),
        )
        val priConfig = Files.readString(configRoot.resolve("priconfig.xml"))
        assertTrue(priConfig.contains("startIndexAt=\"") && priConfig.contains("filtered.layout.resfiles"))
        assertTrue(priConfig.contains("resources.resfiles"))
        assertTrue(priConfig.contains("pri.resfiles"))
        assertTrue(priConfig.contains("embed.resfiles"))
        assertTrue(priConfig.contains("type=\"RESFILES\""))
        assertTrue(priConfig.contains("type=\"RESW\""))
        assertTrue(priConfig.contains("type=\"PRI\""))
        assertTrue(priConfig.contains("type=\"EMBEDFILES\""))
        assertTrue(priConfig.contains("name=\"Language\"") && priConfig.contains("value=\"en-US\""))
        assertTrue(priConfig.contains("name=\"Scale\"") && priConfig.contains("value=\"100\""))
        val makePriCalls = Files.readString(makePriLog)
        assertFalse(makePriCalls.contains("createconfig"))
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun project_pri_full_index_config_is_accepted_by_real_makepri() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val makePri = findWindowsSdk()?.tool("makepri.exe", "x64") ?: return
        val root = Files.createTempDirectory("kotlin-winrt-full-index-pri-")
        val projectPriRoot = root.resolve("project-pri")
        val configRoot = root.resolve("project-pri-config")
        Files.createDirectories(projectPriRoot.resolve("Appx/Assets"))
        Files.createDirectories(projectPriRoot.resolve("Appx/Strings/en-US"))
        Files.createDirectories(projectPriRoot.resolve("embed/Appx/Views"))
        Files.createDirectories(configRoot.resolve("embed"))
        Files.write(projectPriRoot.resolve("Appx/Assets/Logo.png"), byteArrayOf(0x50, 0x4e, 0x47))
        Files.writeString(
            projectPriRoot.resolve("Appx/Strings/en-US/Resources.resw"),
            """<root><data name="Hello"><value>World</value></data></root>""",
        )
        Files.write(projectPriRoot.resolve("embed/Appx/Views/CompiledPage.xbf"), byteArrayOf(0x58, 0x42, 0x46))
        Files.write(configRoot.resolve("filtered.layout.resfiles"), listOf("Appx/Assets/Logo.png"))
        Files.write(configRoot.resolve("resources.resfiles"), listOf("Appx/Strings/en-US/Resources.resw"))
        Files.write(configRoot.resolve("pri.resfiles"), emptyList<String>())
        Files.write(configRoot.resolve("embed/embed.resfiles"), listOf("Appx/Views/CompiledPage.xbf"))
        val config = configRoot.resolve("priconfig.xml")
        ProjectPriConfigXmlWriter.write(
            config = config,
            configRoot = configRoot,
            projectPriRoot = projectPriRoot,
            defaultQualifiers = ProjectPriManifestSupport.fullIndexDefaultQualifiers("en-US", listOf("scale-100")),
        )

        val process = ProcessBuilder(
            makePri.toString(),
            "new",
            "/pr",
            projectPriRoot.toString(),
            "/cf",
            config.toString(),
            "/of",
            projectPriRoot.resolve("resources.pri").toString(),
            "/in",
            "Contoso.App",
            "/o",
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        assertEquals("makepri output:\n$output", 0, exitCode)
        assertTrue(Files.isRegularFile(projectPriRoot.resolve("resources.pri")))
    }

    @Test
    fun runtime_assets_task_stages_default_project_image_content_resources() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val image = project.projectDir.toPath().resolve("Assets/Logo.png")
        Files.createDirectories(image.parent)
        Files.write(image, byteArrayOf(0x50, 0x4e, 0x47))
        val makePriLog = project.layout.buildDirectory.file("makepri-default-content.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-default-content.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageDefaultContentPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-default-content-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(true)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.defaultProjectPriContentFiles.from(
                project.fileTree(project.projectDir) { spec -> spec.include("**/*.png") },
            )
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Assets/Logo.png"),
            ),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_stages_explicit_project_content_resources_relative_to_project_root() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val image = project.projectDir.toPath().resolve("Assets/Splash.jpg")
        Files.createDirectories(image.parent)
        Files.write(image, byteArrayOf(0x4a, 0x50, 0x47))
        val makePriLog = project.layout.buildDirectory.file("makepri-explicit-content.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-explicit-content.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageExplicitContentPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-explicit-content-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriContentFiles.from(image)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Assets/Splash.jpg"),
            ),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_stages_default_project_xaml_layout_resources() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val appXaml = project.projectDir.toPath().resolve("App.xaml")
        val pageXaml = project.projectDir.toPath().resolve("Views/MainPage.xaml")
        Files.createDirectories(pageXaml.parent)
        Files.writeString(appXaml, "<Application />")
        Files.writeString(pageXaml, "<Page />")
        val makePriLog = project.layout.buildDirectory.file("makepri-default-xaml.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-default-xaml.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageDefaultXamlPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-default-xaml-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(true)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.defaultProjectPriLayoutFiles.from(
                project.fileTree(project.projectDir) { spec -> spec.include("**/*.xaml") },
            )
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/App.xaml"),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Views/MainPage.xaml"),
            ),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_prefers_xbf_over_matching_xaml_layout_resources() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val pageXaml = project.projectDir.toPath().resolve("Views/CompiledPage.xaml")
        val pageXbf = project.projectDir.toPath().resolve("Views/CompiledPage.xbf")
        Files.createDirectories(pageXaml.parent)
        Files.writeString(pageXaml, "<Page />")
        Files.write(pageXbf, byteArrayOf(0x58, 0x42, 0x46))
        val makePriLog = project.layout.buildDirectory.file("makepri-layout-xbf.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-layout-xbf.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageXbfPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-layout-xbf-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(true)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.defaultProjectPriLayoutFiles.from(
                project.fileTree(project.projectDir) { spec ->
                    spec.include("**/*.xaml")
                    spec.include("**/*.xbf")
                },
            )
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertFalse(
            Files.exists(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Views/CompiledPage.xaml"),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/embed/Appx/Views/CompiledPage.xbf"),
            ),
        )
        assertTrue(
            Files.readString(task.temporaryDir.toPath().resolve("project-pri-config/embed/embed.resfiles"))
                .contains("Appx/Views/CompiledPage.xbf"),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun runtime_assets_task_stages_explicit_project_xaml_layout_resources_relative_to_project_root() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val pageXaml = project.projectDir.toPath().resolve("Views/DetailsPage.xaml")
        Files.createDirectories(pageXaml.parent)
        Files.writeString(pageXaml, "<Page />")
        val makePriLog = project.layout.buildDirectory.file("makepri-explicit-xaml.log").get().asFile.toPath()
        val makePri = writeFakeMakePri(project.layout.buildDirectory.file("fake-makepri-explicit-xaml.cmd").get().asFile.toPath(), makePriLog)
        val task = project.tasks.register(
            "stageExplicitXamlPriAssets",
            StageWinRtRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("pri-explicit-xaml-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(true)
            registeredTask.projectPriIndexName.set("Contoso.App")
            registeredTask.projectPriInitialPath.set("Appx")
            registeredTask.projectPriDefaultLanguage.set("en-US")
            registeredTask.projectPriDefaultQualifiers.set(listOf("scale-100"))
            registeredTask.enableDefaultProjectPriResources.set(false)
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.projectDirectory)
            registeredTask.projectPriLayoutFiles.from(pageXaml)
            registeredTask.makePriExecutable.set(makePri.toString())
            registeredTask.windowsSdkVersion.set("")
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        assertTrue(
            Files.isRegularFile(
                task.temporaryDir.toPath().resolve("project-pri/Appx/Views/DetailsPage.xaml"),
            ),
        )
        val makePriCalls = Files.readString(makePriLog)
        assertTrue(makePriCalls.contains("new"))
    }

    @Test
    fun plugin_generates_sources_into_real_gradle_library_artifact() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-plugin-test-")
        val runtimeJar = Path.of("../winrt-runtime/build/libs/winrt-runtime-jvm.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
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
                id("io.github.composefluent.winrt")
            }

            kotlin {
                jvmToolchain(25)
            }

            dependencies {
                implementation(files("$runtimeJar"))
            }

            winRt {
                type("Windows.Foundation.IStringable")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("jar", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRtProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "build/generated/kotlin-winrt/src/main/kotlin/windows/foundation/IStringable.kt",
                ),
            ),
        )
        val winmd = projectDir.resolve(
            "build/generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/kotlin-winrt-plugin-test.winmd",
        )
        assertTrue(Files.isRegularFile(winmd))
        assertTrue(WinRtMetadataLoader.load(winmd).namespaces.isEmpty())
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "build/generated/kotlin-winrt/src/main/kotlin/kotlin-winrt-authoring/kotlin-winrt-plugin-test.host.json",
                ),
            ),
        )
        JarFile(projectDir.resolve("build/libs/kotlin-winrt-plugin-test.jar").toFile()).use { jar ->
            assertTrue(
                jar.getEntry(
                    "windows/foundation/IStringable.class",
                ) != null,
            )
            assertTrue(
                jar.getEntry(
                    "kotlin-winrt-runtime-assets/kotlin-winrt-plugin-test.winmd",
                ) != null,
            )
            assertTrue(
                jar.getEntry(
                    "kotlin-winrt-runtime-assets/kotlin-winrt-plugin-test.host.json",
                ) != null,
            )
        }

        val secondResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRtProjections", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.UP_TO_DATE, secondResult.task(":generateWinRtProjections")?.outcome)
    }

    @Test
    fun plugin_injects_compiler_plugin_options_into_multiplatform_jvm_compilation() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-plugin-test-")
        val runtimeJar = Path.of("../winrt-runtime/build/libs/winrt-runtime-jvm.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-kmp-plugin-test"
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
                id("org.jetbrains.kotlin.multiplatform") version "2.3.20"
                id("io.github.composefluent.winrt")
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation(files("$runtimeJar"))
                        }
                    }
                }
            }

            winRt {
                type("Windows.Foundation.IStringable")
            }

            tasks.register("printWinuiJvmCompilerArgs") {
                dependsOn("generateWinRtProjections")
                doLast {
                    val compileTask = tasks.named("compileKotlinWinuiJvm").get()
                    val compilerOptions = compileTask.javaClass.methods
                        .first { it.name == "getCompilerOptions" && it.parameterCount == 0 }
                        .invoke(compileTask)
                    val freeCompilerArgs = compilerOptions.javaClass.methods
                        .first { it.name == "getFreeCompilerArgs" && it.parameterCount == 0 }
                        .invoke(compilerOptions)
                    val args = freeCompilerArgs.javaClass.methods
                        .first { it.name == "get" && it.parameterCount == 0 }
                        .invoke(freeCompilerArgs)
                    println("WINUI_JVM_ARGS=" + args)
                    println("COMMON_MAIN_SOURCES=" + kotlin.sourceSets.named("commonMain").get().kotlin.srcDirs)
                }
            }

            tasks.register("verifyWinuiJvmCompilerSupportOutput") {
                dependsOn("compileKotlinWinuiJvm")
                doLast {
                    val supportRoot = layout.buildDirectory.dir(
                        "classes/kotlin/winuiJvm/main/io/github/composefluent/winrt/projections/support",
                    ).get().asFile
                    val compilerManifest = supportRoot.resolve("WinRTCompilerSupportManifest.class")
                    check(compilerManifest.isFile) {
                        "Expected compiler support manifest in WinUI JVM output: " + compilerManifest
                    }
                    check(supportRoot.walkTopDown().any { it.name.startsWith("WinRTProjectionSupport_") && it.extension == "class" }) {
                        "Expected compiler-generated projection support initializer under: " + supportRoot
                    }
                    val legacyInterfaceRegistry = supportRoot.resolve("WinRTInterfaceProjectionRegistry.class")
                    check(!legacyInterfaceRegistry.exists()) {
                        "Legacy interface projection registry must not be generated: " + legacyInterfaceRegistry
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("printWinuiJvmCompilerArgs", "verifyWinuiJvmCompilerSupportOutput", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRtProjections")?.outcome)
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:metadataIndex="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:typeIndexOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:compilerSupportManifest="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:compilerSupportClassOutputDirectory="))
        assertTrue(result.output.replace("\\", "/").contains("build/generated/kotlin-winrt/src/main/kotlin"))
    }

    @Test
    fun plugin_lowers_projection_intrinsics_in_multiplatform_jvm_common_sources() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-intrinsic-lowering-test-")
        val runtimeJar = Path.of("../winrt-runtime/build/libs/winrt-runtime-jvm.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
        val metadataJar = Path.of("../winrt-metadata/build/libs/winrt-metadata.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
        val generatorJar = Path.of("../winrt-generator/build/libs/winrt-generator.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-kmp-intrinsic-lowering-test"
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
                id("org.jetbrains.kotlin.multiplatform") version "2.3.20"
            }

            configurations.create("kotlinWinRtGeneratorWorker") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

            dependencies {
                add("kotlinWinRtGeneratorWorker", files("$runtimeJar", "$metadataJar", "$generatorJar"))
                add("kotlinWinRtGeneratorWorker", "com.squareup:kotlinpoet:1.18.1")
                add("kotlinWinRtGeneratorWorker", "org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
                add("kotlinWinRtGeneratorWorker", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                add("kotlinWinRtGeneratorWorker", "org.jetbrains.kotlinx:kotlinx-io-core:0.9.0")
            }

            apply(plugin = "io.github.composefluent.winrt")

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation(files("$runtimeJar"))
                        }
                    }
                }
            }

            extensions.configure<io.github.composefluent.winrt.gradle.WinRtExtension>("winRt") {
                type("Windows.Foundation.IStringable")
            }

            val writeIntrinsicProbe = tasks.register("writeIntrinsicProbe") {
                dependsOn("generateWinRtProjections")
                val outputFile = layout.buildDirectory.file("generated/kotlin-winrt/src/main/kotlin/sample/IntrinsicProbe.kt")
                outputs.file(outputFile)
                doLast {
                    outputFile.get().asFile.apply {
                        parentFile.mkdirs()
                        writeText(
                            ${"\"\"\""}
                            package sample

                            import io.github.composefluent.winrt.runtime.ComObjectReference
                            import io.github.composefluent.winrt.runtime.Point
                            import io.github.composefluent.winrt.runtime.RawAddress
                            import io.github.composefluent.winrt.runtime.WinRtProjectionIntrinsic
                            import io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic

                            object IntrinsicProbe {
                                fun call(reference: ComObjectReference, value: RawAddress) {
                                    WinRtProjectionIntrinsic.callUnit(reference, 7, "RawAddress", value)
                                }

                                fun scalarWithStruct(reference: ComObjectReference, value: Point): Int =
                                    WinRtProjectionIntrinsic.callScalar(reference, 8, "Int32", "Struct8_4", value, Point.Metadata)

                                fun booleanWithStruct(reference: ComObjectReference, value: Point): Boolean =
                                    WinRtProjectionIntrinsic.callBoolean(reference, 9, "Struct8_4", value, Point.Metadata)

                                fun support() {
                                    WinRtProjectionSupportIntrinsic.ensureInitialized()
                                }
                            }
                            ${"\"\"\""}.trimIndent()
                        )
                    }
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn(writeIntrinsicProbe)
            }

            tasks.register("verifyLoweredIntrinsic") {
                dependsOn("compileKotlinWinuiJvm")
                doLast {
                    val classRoot = layout.buildDirectory.dir("classes/kotlin/winuiJvm/main").get().asFile
                    val classFile = classRoot.resolve("sample/IntrinsicProbe.class")
                    val contents = classFile.readBytes().toString(Charsets.ISO_8859_1)
                    check(!contents.contains("WinRtProjectionIntrinsic")) {
                        "KMP JVM class still contains WinRtProjectionIntrinsic fallback"
                    }
                    check(!contents.contains("WinRtProjectionSupportIntrinsic")) {
                        "KMP JVM class still contains WinRtProjectionSupportIntrinsic fallback"
                    }
                    check(contents.contains("kotlinWinRtProjectionSupportInitialize_")) {
                        "KMP JVM class did not lower projection support marker to compiler-generated initializer"
                    }
                    check(contents.contains("WinRtJvmFfmDowncallHandles")) {
                        "KMP JVM class did not lower projection intrinsic to JVM FFM"
                    }
                    check(!contents.contains("([Ljava/lang/Object;)Ljava/lang/Object;")) {
                        "KMP JVM class lowered MethodHandle.invoke as a single Object[] vararg call"
                    }
                    check(
                        contents.contains(
                            "(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;)I",
                        ),
                    ) {
                        "KMP JVM class did not lower MethodHandle.invoke with expanded FFM carrier parameters"
                    }
                    check(
                        classRoot.walkTopDown()
                            .filter { it.isFile && it.extension == "class" }
                            .none { compiledClass ->
                                compiledClass.readBytes().toString(Charsets.ISO_8859_1)
                                    .contains("WinRtProjectionSupportIntrinsic")
                            },
                    ) {
                        "Compiled KMP JVM output still contains WinRtProjectionSupportIntrinsic fallback"
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyLoweredIntrinsic", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinWinuiJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyLoweredIntrinsic")?.outcome)
    }

    @Test
    fun plugin_validates_multiplatform_winrt_library_consumed_by_multiplatform_winrt_application() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-library-app-test-")
        val runtimeJar = Path.of("../winrt-runtime/build/libs/winrt-runtime-jvm.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
        val metadataJar = Path.of("../winrt-metadata/build/libs/winrt-metadata.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
        val generatorJar = Path.of("../winrt-generator/build/libs/winrt-generator.jar")
            .toAbsolutePath()
            .normalize()
            .toString()
            .replace("\\", "/")
        val generatorWorkerSetup = """
            configurations.create("kotlinWinRtGeneratorWorker") {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

            dependencies {
                add("kotlinWinRtGeneratorWorker", files("$runtimeJar", "$metadataJar", "$generatorJar"))
                add("kotlinWinRtGeneratorWorker", "com.squareup:kotlinpoet:1.18.1")
                add("kotlinWinRtGeneratorWorker", "org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
                add("kotlinWinRtGeneratorWorker", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                add("kotlinWinRtGeneratorWorker", "org.jetbrains.kotlinx:kotlinx-io-core:0.9.0")
            }

            apply(plugin = "io.github.composefluent.winrt")
        """.trimIndent()
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
            rootProject.name = "kotlin-winrt-kmp-library-app-test"
            include(":winrt-base-library")
            include(":winrt-library")
            include(":winrt-app")
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx512m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("winrt-base-library/build.gradle.kts"),
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.3.20"
            }

            $generatorWorkerSetup

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation(files("$runtimeJar"))
                        }
                    }
                }
            }

            extensions.configure<io.github.composefluent.winrt.gradle.WinRtExtension>("winRt") {
                type("Windows.Foundation.IClosable")
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("winrt-library/build.gradle.kts"),
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.3.20"
            }

            $generatorWorkerSetup

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation(files("$runtimeJar"))
                            api(project(":winrt-base-library"))
                        }
                    }
                }
            }

            extensions.configure<io.github.composefluent.winrt.gradle.WinRtExtension>("winRt") {
                type("Windows.Foundation.Uri")
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("winrt-app/build.gradle.kts"),
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "2.3.20"
            }

            $generatorWorkerSetup

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation(files("$runtimeJar"))
                            implementation(project(":winrt-library"))
                        }
                    }
                }
            }

            extensions.configure<io.github.composefluent.winrt.gradle.WinRtExtension>("winRt") {
                application {}
                type("Windows.Foundation.IAsyncAction")
            }

            val writeTransitiveSupportProbe = tasks.register("writeTransitiveSupportProbe") {
                dependsOn("generateWinRtProjections")
                val outputFile = layout.buildDirectory.file("generated/kotlin-winrt/src/main/kotlin/app/TransitiveSupportProbe.kt")
                outputs.file(outputFile)
                doLast {
                    outputFile.get().asFile.apply {
                        parentFile.mkdirs()
                        writeText(
                            ${"\"\"\""}
                            package app

                            import io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic

                            object TransitiveSupportProbe {
                                fun support() {
                                    WinRtProjectionSupportIntrinsic.ensureInitialized()
                                }
                            }
                            ${"\"\"\""}.trimIndent()
                        )
                    }
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn(writeTransitiveSupportProbe)
            }

            tasks.register("printApplicationIdentity") {
                dependsOn("generateWinRtApplicationIdentity")
                doLast {
                    println(layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json").get().asFile.readText())
                }
            }

            tasks.register("verifyTransitiveCompilerSupport") {
                dependsOn("compileKotlinWinuiJvm")
                doLast {
                    val mergedProjectionRegistrar = layout.buildDirectory.file(
                        "generated/kotlin-winrt/compiler-support/merged/projection-registrar.tsv",
                    ).get().asFile.readText()
                    listOf(
                        "Windows.Foundation.IClosable",
                        "Windows.Foundation.IUriRuntimeClass",
                        "Windows.Foundation.IAsyncAction",
                    ).forEach { typeName ->
                        check(mergedProjectionRegistrar.contains(typeName)) {
                            "Merged app compiler support is missing " + typeName
                        }
                    }

                    val classRoot = layout.buildDirectory.dir("classes/kotlin/winuiJvm/main").get().asFile
                    val classContents = classRoot.walkTopDown()
                        .filter { it.isFile && it.extension == "class" }
                        .associateWith { compiledClass ->
                            compiledClass.readBytes().toString(Charsets.ISO_8859_1)
                        }
                    val probe = classRoot.resolve("app/TransitiveSupportProbe.class")
                    val probeContents = classContents.getValue(probe)
                    check(!probeContents.contains("WinRtProjectionSupportIntrinsic")) {
                        "Transitive app probe still contains WinRtProjectionSupportIntrinsic fallback"
                    }
                    check(probeContents.contains("kotlinWinRtProjectionSupportInitialize_")) {
                        "Transitive app probe did not call the compiler-generated support initializer"
                    }
                    check(classContents.values.none { it.contains("WinRtProjectionSupportIntrinsic") }) {
                        "Transitive app bytecode still contains WinRtProjectionSupportIntrinsic fallback"
                    }

                    val generatedInitializer = classContents.values.singleOrNull { contents ->
                        contents.contains("kotlinWinRtProjectionSupportInitialize_") &&
                            contents.contains("registerGeneratedProjectionTypeIndex")
                    } ?: error("Expected one compiler-generated projection support initializer method")
                    listOf(
                        "Windows.Foundation.IClosable",
                        "Windows.Foundation.IUriRuntimeClass",
                        "Windows.Foundation.IAsyncAction",
                    ).forEach { typeName ->
                        check(generatedInitializer.contains(typeName)) {
                            "Compiler-generated initializer did not include " + typeName
                        }
                    }

                    val supportRoot = classRoot.resolve("io/github/composefluent/winrt/projections/support")
                    val projectionSupportArtifacts = supportRoot.walkTopDown()
                        .filter { it.isFile && it.name.startsWith("WinRTProjectionSupport_") && it.extension == "class" }
                        .toList()
                    check(projectionSupportArtifacts.size == 1) {
                        "Expected exactly one content-addressed projection support artifact, found " +
                            projectionSupportArtifacts.size
                    }
                    check(!supportRoot.resolve("WinRTProjectionSupport.class").exists()) {
                        "Fixed projection support artifact must not be generated"
                    }
                    check(!supportRoot.resolve("WinRTInterfaceProjectionRegistry.class").exists()) {
                        "Legacy interface projection registry must not be generated"
                    }
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                ":winrt-base-library:compileKotlinWinuiJvm",
                ":winrt-library:compileKotlinWinuiJvm",
                ":winrt-app:verifyTransitiveCompilerSupport",
                ":winrt-app:printApplicationIdentity",
                "--stacktrace",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-base-library:generateWinRtIdentity")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-base-library:compileKotlinWinuiJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-library:generateWinRtIdentity")?.outcome)
        assertTrue(
            result.task(":winrt-library:compileKotlinWinuiJvm")?.outcome in
                setOf(TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE),
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-app:generateWinRtApplicationIdentity")?.outcome)
        assertTrue(
            result.task(":winrt-app:compileKotlinWinuiJvm")?.outcome in
                setOf(TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE),
        )
        assertTrue(result.output.contains("winrt-base-library"))
        assertTrue(result.output.contains("winrt-library"))
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "winrt-base-library/build/generated/kotlin-winrt/src/main/kotlin/windows/foundation/IClosable.kt",
                ),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "winrt-library/build/generated/kotlin-winrt/src/main/kotlin/windows/foundation/IUriRuntimeClass.kt",
                ),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "winrt-app/build/generated/kotlin-winrt/src/main/kotlin/windows/foundation/IAsyncAction.kt",
                ),
            ),
        )
        assertFalse(
            Files.exists(
                projectDir.resolve(
                    "winrt-library/build/generated/kotlin-winrt/src/main/kotlin/windows/foundation/IClosable.kt",
                ),
            ),
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-app:verifyTransitiveCompilerSupport")?.outcome)
    }

    @Test
    fun application_distribution_contains_windowsappsdk_framework_runtime_resources() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-app-dist-test-")
        val nugetRoot = projectDir.resolve("nuget")
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK",
            version = "1.8.260416003",
            dependencies = listOf(
                "Microsoft.WindowsAppSDK.Foundation" to "1.8.260415000",
                "Microsoft.WindowsAppSDK.InteractiveExperiences" to "1.8.260415001",
                "Microsoft.WindowsAppSDK.Runtime" to "1.8.260416003",
                "Microsoft.WindowsAppSDK.WinUI" to "1.8.260415005",
            ),
        )
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK.Foundation",
            version = "1.8.260415000",
        )
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK.InteractiveExperiences",
            version = "1.8.260415001",
        )
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK.Runtime",
            version = "1.8.260416003",
        )
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK.WinUI",
            version = "1.8.260415005",
            includeWinUiFrameworkAssets = true,
            includeLiftedRegistrations = true,
        )
        writeGradleFile(
            projectDir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "kotlin-winrt-application-test"
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/main/java/sample/Main.java"),
            """
            package sample;

            public final class Main {
                public static void main(String[] args) {
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                application
                id("io.github.composefluent.winrt")
            }

            application {
                mainClass.set("sample.Main")
            }

            winRt {
                nugetGlobalPackagesRoots.add("${nugetRoot.toString().replace("\\", "\\\\")}")
                restoreNuGetPackages.set(false)
                nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003")
                application {
                }
            }
            """.trimIndent(),
        )
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("installDist", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageWinRtRuntimeAssets")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":stageWinRtApplicationPackage")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":installDist")?.outcome)
        val assetsRoot = projectDir.resolve("build/install/kotlin-winrt-application-test/$KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY")
        assertTrue(Files.isRegularFile(assetsRoot.resolve("Microsoft.UI.Xaml.Controls.pri")))
        assertTrue(Files.isRegularFile(assetsRoot.resolve("Microsoft.UI.Xaml/Controls.pri")))
        assertTrue(Files.isRegularFile(assetsRoot.resolve("resources.pri")))
        assertTrue(
            Files.isRegularFile(
                assetsRoot.resolve(
                    "registrations/Microsoft.WindowsAppSDK.WinUI/build/native/LiftedWinRTClassRegistrations.xml",
                ),
            ),
        )
        assertFalse(Files.exists(assetsRoot.resolve("include/WindowsAppSDK-VersionInfo.h")))
    }
}

private fun writeGradleFile(path: Path, content: String) {
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
}

private fun commandExists(name: String): Boolean =
    runCatching {
        ProcessBuilder("cmd.exe", "/c", "where", name)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)

private fun writeFakeMakePri(path: Path, log: Path, languagePri: String = ""): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        echo %*>>"${log.toString()}"
        set output=
        :next
        if "%~1"=="" goto done
        if /I "%~1"=="/of" (
          set output=%~2
          shift
        )
        shift
        goto next
        :done
        if not "%output%"=="" (
          echo fake-pri>"%output%"
          if not "${languagePri}"=="" (
            for %%I in ("%output%") do echo fake-language-pri>"%%~dpIresources.language-${languagePri}.pri"
          )
        )
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFakeMakeAppx(path: Path, log: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        echo %*>>"${log.toString()}"
        set command=%~1
        set output=
        set directory=
        :next
        if "%~1"=="" goto done
        if /I "%~1"=="/p" (
          set output=%~2
          shift
        )
        if /I "%~1"=="/d" (
          set directory=%~2
          shift
        )
        shift
        goto next
        :done
        if /I "%command%"=="pack" if not "%output%"=="" (
          echo fake-msix>"%output%"
        )
        if /I "%command%"=="unpack" if not "%directory%"=="" (
          mkdir "%directory%" 2>nul
          mkdir "%directory%\App" 2>nul
          mkdir "%directory%\Assets" 2>nul
          echo fake-exe>"%directory%\App\Contoso.exe"
          echo fake-logo>"%directory%\Assets\StoreLogo.png"
          echo fake-logo>"%directory%\Assets\Square150x150Logo.png"
          echo fake-logo>"%directory%\Assets\Square44x44Logo.png"
          (
            echo ^<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"^>
            echo   ^<Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" /^>
            echo   ^<Properties^>
            echo     ^<DisplayName^>Contoso^</DisplayName^>
            echo     ^<PublisherDisplayName^>Contoso^</PublisherDisplayName^>
            echo     ^<Logo^>Assets/StoreLogo.png^</Logo^>
            echo   ^</Properties^>
            echo   ^<Applications^>
            echo     ^<Application Id="App" Executable="App/Contoso.exe" EntryPoint="Contoso.App"^>
            echo       ^<uap:VisualElements DisplayName="Contoso" Description="Contoso app" BackgroundColor="transparent" Square150x150Logo="Assets/Square150x150Logo.png" Square44x44Logo="Assets/Square44x44Logo.png" /^>
            echo     ^</Application^>
            echo   ^</Applications^>
            echo ^</Package^>
          )>"%directory%\AppxManifest.xml"
        )
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFakeMakeAppxUnpackWithoutManifest(path: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFakeMakeAppxUnpackWithoutPayload(path: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        set directory=
        :next
        if "%~1"=="" goto done
        if /I "%~1"=="/d" (
          set directory=%~2
          shift
        )
        shift
        goto next
        :done
        if not "%directory%"=="" (
          mkdir "%directory%" 2>nul
          (
            echo ^<Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10" xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"^>
            echo   ^<Identity Name="Contoso.App" Publisher="CN=Contoso" Version="1.0.0.0" /^>
            echo   ^<Properties^>
            echo     ^<DisplayName^>Contoso^</DisplayName^>
            echo     ^<PublisherDisplayName^>Contoso^</PublisherDisplayName^>
            echo     ^<Logo^>Assets/StoreLogo.png^</Logo^>
            echo   ^</Properties^>
            echo   ^<Applications^>
            echo     ^<Application Id="App" Executable="App/Contoso.exe" EntryPoint="Contoso.App"^>
            echo       ^<uap:VisualElements DisplayName="Contoso" Description="Contoso app" BackgroundColor="transparent" Square150x150Logo="Assets/Square150x150Logo.png" Square44x44Logo="Assets/Square44x44Logo.png" /^>
            echo     ^</Application^>
            echo   ^</Applications^>
            echo ^</Package^>
          )>"%directory%\AppxManifest.xml"
        )
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFakeMakeAppxWithoutOutput(path: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFakeSignTool(path: Path, log: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        echo %*>>"${log.toString()}"
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFailingFakeSignTool(path: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        exit /b 1
        """.trimIndent(),
    )
    return path
}

private fun writeFakePowerShell(path: Path, log: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        echo %*>>"${log.toString()}"
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun writeFailingFakePowerShell(path: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        exit /b 1
        """.trimIndent(),
    )
    return path
}

private fun appxManifestXml(
    identityName: String = "Contoso.App",
    executable: String = "App/Contoso.exe",
    entryPoint: String = "Contoso.App",
    propertiesLogo: String = "Assets/StoreLogo.png",
    resources: String = "",
): String =
    """
    <Package
        xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
        xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10">
      <Identity Name="$identityName" Publisher="CN=Contoso" Version="1.0.0.0" />
      <Properties>
        <DisplayName>Contoso</DisplayName>
        <PublisherDisplayName>Contoso</PublisherDisplayName>
        <Logo>$propertiesLogo</Logo>
      </Properties>
      <Applications>
        <Application Id="App" Executable="$executable" EntryPoint="$entryPoint">
          <uap:VisualElements
              DisplayName="Contoso"
              Description="Contoso app"
              BackgroundColor="transparent"
              Square150x150Logo="Assets/Square150x150Logo.png"
              Square44x44Logo="Assets/Square44x44Logo.png" />
        </Application>
      </Applications>
      $resources
    </Package>
    """.trimIndent()

private fun writeManifestPayloadReferences(root: Path) {
    Files.createDirectories(root.resolve("App"))
    Files.createDirectories(root.resolve("Assets"))
    Files.writeString(root.resolve("App/Contoso.exe"), "exe")
    Files.write(root.resolve("Assets/StoreLogo.png"), byteArrayOf(0x50, 0x4e, 0x47))
    Files.write(root.resolve("Assets/Square150x150Logo.png"), byteArrayOf(0x50, 0x4e, 0x47))
    Files.write(root.resolve("Assets/Square44x44Logo.png"), byteArrayOf(0x50, 0x4e, 0x47))
}

private fun writeWindowsAppSdkPackage(
    nugetRoot: Path,
    packageId: String,
    version: String,
    includeWinUiFrameworkAssets: Boolean = false,
    includeLiftedRegistrations: Boolean = false,
    dependencies: List<Pair<String, String>> = emptyList(),
) {
    val packageRoot = nugetRoot.resolve(packageId.lowercase()).resolve(version)
    Files.createDirectories(packageRoot)
    Files.writeString(
        packageRoot.resolve("$packageId.nuspec"),
        """
        <package>
          <metadata>
            <id>$packageId</id>
            <version>$version</version>
            ${dependencies.toNuspecDependencies()}
          </metadata>
        </package>
        """.trimIndent(),
    )
    if (includeWinUiFrameworkAssets) {
        val nativeRoot = packageRoot.resolve("runtimes-framework/win-x64/native")
        Files.createDirectories(nativeRoot.resolve("Microsoft.UI.Xaml"))
        Files.writeString(nativeRoot.resolve("Microsoft.UI.Xaml.Controls.pri"), "pri")
        Files.writeString(nativeRoot.resolve("Microsoft.UI.Xaml/Controls.pri"), "nested")
        Files.createDirectories(packageRoot.resolve("include"))
        Files.writeString(packageRoot.resolve("include/WindowsAppSDK-VersionInfo.h"), "version")
    }
    if (includeLiftedRegistrations) {
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.writeString(packageRoot.resolve("build/native/LiftedWinRTClassRegistrations.xml"), "<Registrations />")
    }
}

private fun List<Pair<String, String>>.toNuspecDependencies(): String {
    if (isEmpty()) {
        return ""
    }
    return joinToString(
        separator = System.lineSeparator(),
        prefix = "<dependencies>${System.lineSeparator()}",
        postfix = "${System.lineSeparator()}</dependencies>",
    ) { (id, version) -> """<dependency id="$id" version="$version" />""" }
}
