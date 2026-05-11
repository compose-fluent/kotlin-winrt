package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
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
        project.tasks.named("buildWinRtAuthoringHost", BuildWinRtAuthoringHostTask::class.java).get()
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
    fun authoring_host_task_generates_cswinrt_style_native_exports() {
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
            registeredTask.runtimeAssets.set(emptyList())
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
            registeredTask.runtimeAssets.set(emptyList())
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
        assertTrue(Files.isRegularFile(outputRoot.resolve("resources.pri")))
        assertFalse(Files.exists(outputRoot.resolve("include/WindowsAppSDK-VersionInfo.h")))
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
                jvmToolchain(22)
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
    fun application_distribution_contains_windowsappsdk_runtime_resources() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-app-dist-test-")
        val nugetRoot = projectDir.resolve("nuget")
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK",
            version = "1.8.260416003",
            dependencies = listOf(
                "Microsoft.WindowsAppSDK.Foundation" to "1.8.260415000",
                "Microsoft.WindowsAppSDK.InteractiveExperiences" to "1.8.260415001",
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
            packageId = "Microsoft.WindowsAppSDK.WinUI",
            version = "1.8.260415005",
            includeWinUiFrameworkAssets = true,
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
        assertEquals(TaskOutcome.SUCCESS, result.task(":installDist")?.outcome)
        val assetsRoot = projectDir.resolve("build/install/kotlin-winrt-application-test/$KOTLIN_WINRT_RUNTIME_ASSETS_DIRECTORY")
        assertTrue(Files.isRegularFile(assetsRoot.resolve("resources.pri")))
        assertTrue(Files.isRegularFile(assetsRoot.resolve("Microsoft.UI.Xaml.Controls.pri")))
        assertTrue(Files.isRegularFile(assetsRoot.resolve("Microsoft.UI.Xaml/Controls.pri")))
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

private fun writeWindowsAppSdkPackage(
    nugetRoot: Path,
    packageId: String,
    version: String,
    includeWinUiFrameworkAssets: Boolean = false,
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
