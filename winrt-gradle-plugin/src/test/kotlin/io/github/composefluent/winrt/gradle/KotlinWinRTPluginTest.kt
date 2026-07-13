package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringTypeDetailsRenderer
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTAuthoredRuntimeClassDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableInterfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.runtime.WinUiRuntimeAssetManifests
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.bundling.Jar
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.jar.JarFile

class KotlinWinRTPluginTest {
    @Test
    fun memoized_provider_computes_its_value_once() {
        val project = ProjectBuilder.builder().build()
        var computations = 0

        val provider = memoizedBooleanProvider(project) {
            computations += 1
            true
        }

        assertTrue(provider.get())
        assertTrue(provider.get())
        assertEquals(1, computations)
    }

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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRTIdentity", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTIdentity")?.outcome)
        assertFalse(
            Files.exists(projectDir.resolve("build/generated/kotlin-winrt/src/jvmMain/kotlin/windows")),
        )
        val identity = projectDir.resolve("build/generated/kotlin-winrt/identity/kotlin-winrt.json").toFile().readText()
        assertTrue(identity.contains("\"includeTypes\": []"))
        assertTrue(identity.contains("\"projectedTypes\": []"))
        assertTrue(identity.contains("\"sourceAdditions\": []"))
        assertTrue(identity.contains("\"projectionShapeVersion\": 1"))
    }

    @Test
    fun unresolved_windows_sdk_references_trigger_sdk_source_fallback() {
        val model = WinRTMetadataModel(
            listOf(
                WinRTNamespace(
                    "Sample",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "Widget",
                            kind = WinRTTypeKind.RuntimeClass,
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "measure",
                                    returnTypeName = "Windows.Foundation.Size",
                                    returnTypeSignature = WinRTTypeRef.unknown(),
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "availableSize",
                                            typeName = "Windows.Foundation.Size",
                                            typeSignature = WinRTTypeRef.unknown(),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(model.unresolvedWindowsSdkTypeReferences().contains("Windows.Foundation.Size"))

        val sources = listOf(WinRTMetadataSource.path(Path.of("sample.winmd")))
            .withWindowsSdkSourceForUnresolvedWindowsReferences(model)

        assertTrue(sources.first() is WinRTMetadataSource.WindowsSdk)
    }

    @Test
    fun authoring_metadata_roots_include_internal_type_details_candidates() {
        val roots = authoringCandidateMetadataRootNames(
            listOf(
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "InternalControl",
                    sourceTypeName = "sample.InternalControl",
                    winRTBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
                    winRTInterfaceNames = listOf(
                        "Microsoft.UI.Xaml.Controls.IContentControlOverrides",
                        "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                    ),
                    overridableInterfaceNames = listOf(
                        "Microsoft.UI.Xaml.Controls.IContentControlOverrides",
                        "Microsoft.UI.Xaml.IFrameworkElementOverrides",
                    ),
                    isPublic = false,
                ),
            ),
        )

        assertTrue("Microsoft.UI.Xaml.Controls.ContentControl" in roots)
        assertTrue("Microsoft.UI.Xaml.IFrameworkElementOverrides" in roots)
    }

    @Test
    fun generation_worker_uses_isolated_kotlinpoet_when_buildscript_classpath_contains_older_kotlinpoet() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-generator-isolation-test-")
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
            rootProject.name = "kotlin-winrt-generator-isolation-test"
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
            projectDir.resolve("build.gradle"),
            """
            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath "com.squareup:kotlinpoet:1.3.0"
                }
            }

            plugins {
                id "io.github.compose-fluent.winrt"
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRTProjections", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertTrue(result.output.contains("kotlin-winrt generator worker runtime: KotlinPoet="))
        assertTrue(result.output.contains("1.18.1"))
        assertFalse(result.output.contains("kotlinpoet/1.3.0"))
    }

    @Test
    fun plugin_wires_extension_inputs_to_generation_task() {
        val project = ProjectBuilder.builder().build()
        project.repositories.mavenCentral()
        val nugetPackageRoot = project.projectDir
            .toPath()
            .resolve("nuget-cache/microsoft.windowsappsdk/1.8.260416003")
        Files.createDirectories(nugetPackageRoot.resolve("lib/net8.0"))
        Files.writeString(nugetPackageRoot.resolve("lib/net8.0/Microsoft.UI.Xaml.winmd"), "metadata")

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.namespace("Windows.Foundation")
        extension.type("Windows.Foundation.IStringable")
        extension.excludeNamespace("Sample.Hidden")
        extension.excludeType("Microsoft.UI.Xaml.Controls.WebView2")
        extension.excludeAdditionNamespace("Microsoft.UI.Xaml.Media.Animation")
        extension.winmd("sdk+")
        extension.windowsSdk("10.0.26100.0", includeExtensions = true, generateProjection = true)
        extension.nugetExecutable.set("nuget.exe")
        extension.nugetCliVersion.set("7.3.1")
        extension.restoreNuGetPackages.set(false)
        extension.useNuGetCliGlobalPackages.set(false)
        extension.nugetGlobalPackagesRoots.add(project.layout.projectDirectory.dir("nuget-cache").asFile.absolutePath)
        extension.runtimeAsset(project.layout.projectDirectory.file("SimpleMathComponent.dll").asFile.absolutePath)
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003") { pkg ->
            pkg.generateProjection = true
        }
        extension.namespace("Microsoft")
        extension.type("Windows.UI.Xaml.Interop.Type")
        extension.excludeNamespace("Windows")

        val task = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()

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
        assertTrue(task.generateWindowsSdkProjection.get())
        assertEquals("nuget.exe", task.nugetExecutable.get())
        assertEquals("7.3.1", task.nugetCliVersion.get())
        assertEquals(false, task.restoreNuGetPackages.get())
        assertEquals(false, task.useNuGetCliGlobalPackages.get())
        assertEquals(
            listOf(project.layout.projectDirectory.file("SimpleMathComponent.dll").asFile.absolutePath),
            project.extensions.getByType(WinRTExtension::class.java).runtimeAssets.get(),
        )
        assertEquals(
            listOf("Microsoft.WindowsAppSDK@1.8.260416003"),
            task.nugetPackages.get(),
        )
        assertFalse("Windows.UI.Composition" in task.excludeTypes.get())
        assertEquals(
            setOf(nugetPackageRoot.toFile()),
            task.nugetPackageContentFiles.files,
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
        val generatorWorkerDependencyNames = generatorWorkerConfiguration.dependencies.mapTo(mutableSetOf()) { it.name }
        assertTrue("kotlinpoet-jvm" in generatorWorkerDependencyNames)
        val publishedWorkerDependencyNames = setOf("winrt-runtime", "winrt-metadata", "winrt-generator")
        if (generatorWorkerDependencyNames.any { it in publishedWorkerDependencyNames }) {
            assertEquals(publishedWorkerDependencyNames + "kotlinpoet-jvm", generatorWorkerDependencyNames)
        } else {
            val generatorCodeSource = KotlinProjectionGenerator::class.java.protectionDomain.codeSource.location
                .toURI()
                .let(Path::of)
                .toAbsolutePath()
                .normalize()
            assertTrue(
                task.generatorWorkerClasspath.files.any { file ->
                    file.toPath().toAbsolutePath().normalize() == generatorCodeSource
                },
            )
        }
        assertEquals(project.name, task.authoringAssemblyName.get())
        assertEquals("${project.name}.jar", task.authoringTargetArtifactName.get())
    }

    private fun runtimeJarPath(): Path {
        val runtimeLibs = Path.of("../winrt-runtime/build/libs").toAbsolutePath().normalize()
        return Files.list(runtimeLibs).use { stream ->
            stream.filter { path ->
                val name = path.fileName.toString()
                name.startsWith("winrt-runtime-jvm-") && name.endsWith(".jar")
            }.findFirst().orElseThrow { NoSuchElementException("Missing winrt-runtime JVM jar under $runtimeLibs") }
        }
    }

    private fun authoringJarPath(): Path {
        val authoringLibs = Path.of("../winrt-authoring/build/libs").toAbsolutePath().normalize()
        return Files.list(authoringLibs).use { stream ->
            stream.filter { path ->
                val name = path.fileName.toString()
                name.startsWith("winrt-authoring-jvm-") && name.endsWith(".jar")
            }.findFirst().orElseThrow { NoSuchElementException("Missing winrt-authoring JVM jar under $authoringLibs") }
        }
    }

    @Test
    fun generator_worker_prefers_local_project_dependencies_when_available() {
        val root = ProjectBuilder.builder().withName("root").build()
        ProjectBuilder.builder().withParent(root).withName("winrt-runtime").build()
        ProjectBuilder.builder().withParent(root).withName("winrt-metadata").build()
        ProjectBuilder.builder().withParent(root).withName("winrt-generator").build()
        val project = ProjectBuilder.builder().withParent(root).withName("app").build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val generatorWorkerConfiguration = project.configurations.getByName(KOTLIN_WINRT_GENERATOR_WORKER_CONFIGURATION)
        val projectDependencyPaths = generatorWorkerConfiguration.dependencies
            .filterIsInstance<ProjectDependency>()
            .mapTo(mutableSetOf()) { dependency -> dependency.path }

        assertEquals(
            setOf(":winrt-runtime", ":winrt-metadata", ":winrt-generator"),
            projectDependencyPaths,
        )
    }

    @Test
    fun microsoft_nuget_packages_default_to_local_projection_generation() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003")
        extension.nugetPackage("Microsoft.Windows.SDK.NET.Ref", "10.0.26100.0")

        val generationTask = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()
        val identityTask = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()

        assertEquals(
            setOf("Microsoft.WindowsAppSDK@1.8.260416003", "Microsoft.Windows.SDK.NET.Ref@10.0.26100.0"),
            generationTask.nugetPackages.get().toSet(),
        )
        assertEquals(
            setOf("Microsoft.WindowsAppSDK@1.8.260416003", "Microsoft.Windows.SDK.NET.Ref@10.0.26100.0"),
            identityTask.nugetPackages.get().toSet(),
        )
    }

    @Test
    fun ordinary_nuget_package_defaults_to_projection_generation() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.nugetPackage("Sample.Package", "1.0.0")

        val generationTask = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()
        val identityTask = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()

        assertEquals(listOf("Sample.Package@1.0.0"), generationTask.nugetPackages.get())
        assertEquals(listOf("Sample.Package@1.0.0"), identityTask.nugetPackages.get())
    }

    @Test
    fun prebuilt_nuget_package_can_opt_out_of_local_projection_generation() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003") { pkg ->
            pkg.generateProjection = false
        }

        val generationTask = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()
        val identityTask = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()

        assertEquals(emptyList<String>(), generationTask.nugetPackages.get())
        assertEquals(listOf("Microsoft.WindowsAppSDK@1.8.260416003"), identityTask.nugetPackages.get())
    }

    @Test
    fun projection_inputs_keep_third_party_winmd_nuget_identity_and_local_generation_opt_in() {
        val root = ProjectBuilder.builder().withName("root").build()
        val dependency = ProjectBuilder.builder().withName("dependency").withParent(root).build()
        val app = ProjectBuilder.builder().withName("app").withParent(root).build()
        Files.createDirectories(app.projectDir.toPath())
        val winmd = app.projectDir.toPath().resolve("Sample.Component.winmd")
        Files.writeString(winmd, "metadata")
        val thirdPartyPackageRoot = app.projectDir.toPath().resolve("nuget-cache/sample.package/1.0.0")
        val preprojectedPackageRoot = app.projectDir.toPath().resolve("nuget-cache/microsoft.windowsappsdk/1.8.260416003")
        Files.createDirectories(thirdPartyPackageRoot.resolve("lib/net8.0"))
        Files.createDirectories(preprojectedPackageRoot.resolve("lib/net8.0"))
        Files.writeString(thirdPartyPackageRoot.resolve("lib/net8.0/Sample.Package.winmd"), "metadata")
        Files.writeString(preprojectedPackageRoot.resolve("lib/net8.0/Microsoft.UI.Xaml.winmd"), "metadata")

        dependency.pluginManager.apply(KotlinWinRTPlugin::class.java)
        app.pluginManager.apply("java")
        app.pluginManager.apply(KotlinWinRTPlugin::class.java)
        app.dependencies.add("implementation", dependency)
        val extension = app.extensions.getByType(WinRTExtension::class.java)
        extension.winmd(winmd)
        extension.nugetGlobalPackagesRoots.add(app.projectDir.toPath().resolve("nuget-cache").toString())
        extension.restoreNuGetPackages.set(false)
        extension.useNuGetCliGlobalPackages.set(false)
        extension.nugetPackage("Sample.Package", "1.0.0")
        extension.nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003") { pkg ->
            pkg.generateProjection = true
        }

        val generationTask = app.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()
        val mergeTask = app.tasks.named("mergeWinRTCompilerSupport", MergeWinRTCompilerSupportTask::class.java).get()

        assertEquals(listOf(winmd.toString()), generationTask.metadataInputs.get())
        assertEquals(
            setOf("Sample.Package@1.0.0", "Microsoft.WindowsAppSDK@1.8.260416003"),
            generationTask.nugetPackages.get().toSet(),
        )
        assertEquals(
            setOf(thirdPartyPackageRoot.toFile(), preprojectedPackageRoot.toFile()),
            generationTask.nugetPackageContentFiles.files,
        )
        assertTrue(generationTask.dependencyIdentityFiles.files.isNotEmpty())
        assertTrue(mergeTask.dependencyIdentityFiles.files.isNotEmpty())
    }

    @Test
    fun windows_sdk_defaults_to_no_projection_generation() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.windowsSdk("10.0.26100.0", includeExtensions = true)

        val generationTask = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()
        val identityTask = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()

        assertEquals("10.0.26100.0", generationTask.windowsSdkVersion.get())
        assertTrue(generationTask.includeWindowsSdkExtensions.get())
        assertFalse(generationTask.generateWindowsSdkProjection.get())
        assertEquals("10.0.26100.0", identityTask.windowsSdkVersion.get())
        assertTrue(identityTask.includeWindowsSdkExtensions.get())
    }

    @Test
    fun plugin_adds_runtime_dependency_to_jvm_projects() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val implementationDependencies = project.configurations.getByName("implementation").dependencies
        assertHasKotlinWinRTRuntimeDependency(implementationDependencies)
        assertHasKotlinWinRTAuthoringDependency(implementationDependencies)
    }

    @Test
    fun plugin_adds_runtime_dependency_to_kmp_winui_main_not_common_main() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        assertDoesNotHaveKotlinWinRTRuntimeDependency(
            project.configurations.getByName("commonMainImplementation").dependencies,
        )
        assertHasKotlinWinRTRuntimeDependency(
            project.configurations.getByName("winuiMainImplementation").dependencies,
        )
        assertHasKotlinWinRTAuthoringDependency(
            project.configurations.getByName("winuiMainImplementation").dependencies,
        )
    }

    @Test
    fun plugin_under_test_runtime_dependencies_use_local_artifacts_for_winui_main() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val implementationDependencies = project.configurations.getByName("winuiMainImplementation").dependencies
        assertHasKotlinWinRTRuntimeDependency(implementationDependencies)
        assertHasKotlinWinRTAuthoringDependency(implementationDependencies)
        assertDoesNotHaveExternalKotlinWinRTModuleDependency(implementationDependencies, "winrt-runtime")
        assertDoesNotHaveExternalKotlinWinRTModuleDependency(implementationDependencies, "winrt-authoring")
    }

    @Test
    fun plugin_adds_authoring_dependency_to_kmp_winui_main() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val implementationDependencies = project.configurations.getByName("winuiMainImplementation").dependencies
        assertHasKotlinWinRTAuthoringDependency(implementationDependencies)
    }

    @Test
    fun plugin_adds_authoring_dependency_to_kmp_jvm_main() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val implementationDependencies = project.configurations.getByName("winuiJvmMainImplementation").dependencies
        assertHasKotlinWinRTAuthoringDependency(implementationDependencies)
    }

    @Test
    fun jvm_compilation_declares_compiler_authored_handoff_outputs() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val compileTask = project.tasks.named("compileKotlin", KotlinJvmCompile::class.java).get()
        val outputPaths = compileTask.outputs.files.files
            .map { file -> file.toPath().toString().replace("\\", "/") }
            .toSet()

        listOf(
            "build/classes/kotlin/main/kotlin-winrt/type-index.tsv",
            "build/classes/kotlin/main/kotlin-winrt/authored-candidates.tsv",
            "build/classes/kotlin/main/kotlin-winrt-authoring/authored-metadata.tsv",
            "build/classes/kotlin/main/kotlin-winrt-authoring/test.winmd",
            "build/classes/kotlin/main/kotlin-winrt-authoring/test.host.json",
        ).forEach { expectedSuffix ->
            assertTrue(
                "compileKotlin must declare JVM compiler-authored handoff output $expectedSuffix in:\n" +
                    outputPaths.sorted().joinToString("\n"),
                outputPaths.any { path -> path.endsWith(expectedSuffix) },
            )
        }
    }

    @Test
    fun runtime_only_multiplatform_native_compilation_keeps_authoring_options_without_projection_support() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).mingwX64("winuiMingw")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val compileTask = project.tasks.named("compileKotlinWinuiMingw").get() as KotlinNativeCompile
        val compilerArgs = compileTask.compilerOptions.freeCompilerArgs.get()
        val joinedArgs = compilerArgs.joinToString(separator = "\n")

        assertTrue(joinedArgs, compilerArgs.any { it.contains(":authoredCandidatesOutput=") })
        assertTrue(joinedArgs, compilerArgs.any { it.contains(":authoredWinmdOutput=") })
        assertFalse(joinedArgs, compilerArgs.any { it.contains(":compilerSupportManifest=") })
        assertFalse(joinedArgs, compilerArgs.any { it.contains(":compilerSupportClassOutputDirectory=") })
    }

    @Test
    fun native_authoring_shared_library_registers_export_validation_and_identity_artifact() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.mingwX64("winuiMingw") {
            binaries {
                sharedLib()
            }
        }
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val sourceSet = kotlin.targets.getByName("winuiMingw").compilations.getByName("main").defaultSourceSet
        assertTrue(
            "Native authoring host generated Kotlin source must not be scoped to a target source set:\n" +
                sourceSet.kotlin.srcDirs.joinToString("\n") { it.invariantSeparatorsPath },
            sourceSet.kotlin.srcDirs.none {
                it.invariantSeparatorsPath.endsWith("build/generated/kotlin-winrt-native-authoring-host/src/main/kotlin")
            },
        )
        val compileTask = project.tasks.named("compileKotlinWinuiMingw").get() as KotlinNativeCompile
        val compilerArgs = compileTask.compilerOptions.freeCompilerArgs.get()
        val joinedCompilerArgs = compilerArgs.joinToString("\n")
        assertTrue(joinedCompilerArgs, compilerArgs.any { it.contains(":authoredCandidatesOutput=") })
        assertFalse(joinedCompilerArgs, compilerArgs.any { it.contains(":compilerSupportManifest=") })
        val validationTaskName = "validateCompileKotlinWinuiMingwWinRTAuthoredCandidates"
        val validationTask = project.tasks.named(validationTaskName, ValidateWinRTAuthoredCandidatesTask::class.java).get()
        assertTrue(validationTask.allowTargetSpecificHostManifest.get())
        kotlin.jvm("winuiJvm")
        val jvmValidationTask = project.tasks.named(
            "validateCompileKotlinWinuiJvmWinRTAuthoredCandidates",
            ValidateWinRTAuthoredCandidatesTask::class.java,
        ).get()
        assertTrue(jvmValidationTask.allowTargetSpecificHostManifest.get())
        val validationDependencies = validationTask.taskDependencies.getDependencies(validationTask).map { it.name }
        assertTrue(
            "$validationTaskName must depend on native compile",
            "compileKotlinWinuiMingw" in validationDependencies,
        )
        assertTrue(
            "$validationTaskName must depend on scanner-generated authored artifacts",
            "generateWinRTProjections" in validationDependencies,
        )
        val exportValidationTaskName = "validateCompileKotlinWinuiMingwWinRTNativeAuthoringExports"
        assertTrue(project.tasks.names.contains("linkReleaseSharedWinuiMingw"))
        val exportValidationTask = project.tasks.named(
            exportValidationTaskName,
            ValidateWinRTNativeAuthoringExportsTask::class.java,
        ).get()
        val checkTask = project.tasks.named("check").get()
        val checkDependencies = checkTask.taskDependencies.getDependencies(checkTask).map { it.name }
        assertTrue(
            "check must depend on native authored candidate validation",
            validationTaskName in checkDependencies,
        )
        assertFalse(
            "check should not force native DLL linking; fixture/staging or the explicit export task owns that gate",
            exportValidationTaskName in checkDependencies,
        )
        val lifecycleTaskNames = listOf(
            "classes",
            "jar",
            "assemble",
            "processResources",
            "stageWinRTRuntimeAssets",
            "stageWinRTApplicationPackage",
        )
        lifecycleTaskNames
            .filter { taskName -> taskName in project.tasks.names }
            .forEach { taskName ->
                val task = project.tasks.named(taskName).get()
                val dependencies = task.taskDependencies.getDependencies(task).map { it.name }
                assertTrue(
                    "$taskName must validate native authored artifacts",
                    validationTaskName in dependencies,
                )
                assertFalse(
                    "$taskName should not force native DLL linking in the unevaluated ProjectBuilder model",
                    exportValidationTaskName in dependencies,
                )
            }
        val identityTask = project.tasks.named("generateWinRTIdentity").get()
        val identityDependencies = identityTask.taskDependencies.getDependencies(identityTask).map { it.name }
        assertFalse(
            "generateWinRTIdentity must not depend on authored validation; identity remains a forwarding surface",
            validationTaskName in identityDependencies,
        )
        val processResources = project.tasks.findByName("processResources")
        if (processResources != null) {
            val dependencies = processResources.taskDependencies.getDependencies(processResources).map { it.name }
            assertFalse(
                "native authoring must not route through JVM host DLL build",
                "buildWinRTAuthoringHost" in dependencies,
            )
        }
    }

    @Test
    fun generation_task_includes_component_xaml_resource_dictionaries_when_resource_dictionary_is_projected() {
        val model = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "ResourceDictionary",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRTNamespace(
                    name = "WinUI3Package",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "WinUI3Package",
                            name = "Shimmer",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "WinUI3Package",
                            name = "Shimmer_Resource",
                            kind = WinRTTypeKind.RuntimeClass,
                            baseTypeName = "Microsoft.UI.Xaml.ResourceDictionary",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("WinUI3Package.Shimmer_Resource"),
            automaticXamlComponentResourceDictionaryTypes(
                model,
                setOf("Microsoft.UI.Xaml.ResourceDictionary", "WinUI3Package.Shimmer"),
            ),
        )
        assertEquals(
            emptyList<String>(),
            automaticXamlComponentResourceDictionaryTypes(model, setOf("WinUI3Package.Shimmer")),
        )
    }

    @Test
    fun generation_task_uses_gradle_jar_archive_name_for_authoring_target_artifact() {
        val project = ProjectBuilder.builder().withName("mapped-component").build()
        project.version = "1.2.3"
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val task = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()

        assertEquals("mapped-component-1.2.3.jar", task.authoringTargetArtifactName.get())
    }

    @Test
    fun winrt_extension_defaults_to_library_and_application_block_selects_application_model() {
        val libraryProject = ProjectBuilder.builder().build()
        libraryProject.pluginManager.apply(KotlinWinRTPlugin::class.java)
        assertFalse(libraryProject.extensions.getByType(WinRTExtension::class.java).applicationEnabled.get())
        assertEquals(
            KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION,
            libraryProject.extensions.extraProperties["kotlinWinRTIdentityElements"],
        )

        val applicationProject = ProjectBuilder.builder().build()
        applicationProject.pluginManager.apply(KotlinWinRTPlugin::class.java)
        applicationProject.extensions.getByType(WinRTExtension::class.java).application {}
        assertTrue(applicationProject.extensions.getByType(WinRTExtension::class.java).applicationEnabled.get())
        assertFalse(
            applicationProject.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION).isCanBeConsumed,
        )
        assertEquals(
            KOTLIN_WINRT_IDENTITY_CONFIGURATION,
            applicationProject.extensions.extraProperties["kotlinWinRTIdentity"],
        )
        assertEquals(
            "generateWinRTApplicationIdentity",
            applicationProject.extensions.extraProperties["kotlinWinRTApplicationIdentityTask"],
        )
        assertEquals(
            "resolveWinRTRuntimeNuGetPackages",
            applicationProject.extensions.extraProperties["kotlinWinRTRuntimeNuGetPackagesTask"],
        )
        assertEquals(
            "stageWinRTRuntimeAssets",
            applicationProject.extensions.extraProperties["kotlinWinRTRuntimeAssetsTask"],
        )
        assertEquals(
            "stageWinRTApplicationPackage",
            applicationProject.extensions.extraProperties["kotlinWinRTApplicationPackageTask"],
        )
        assertEquals(
            "packageWinRTApplication",
            applicationProject.extensions.extraProperties["kotlinWinRTPackageTask"],
        )
        assertEquals(
            "signWinRTApplicationPackage",
            applicationProject.extensions.extraProperties["kotlinWinRTSignPackageTask"],
        )
        assertEquals(
            "installWinRTApplicationPackage",
            applicationProject.extensions.extraProperties["kotlinWinRTInstallPackageTask"],
        )
    }

    @Test
    fun library_plugin_publishes_identity_variant() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val identityElements = project.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)
        assertTrue(identityElements.isCanBeConsumed)
        assertFalse(identityElements.isCanBeResolved)
        assertEquals(
            KOTLIN_WINRT_IDENTITY_USAGE,
            identityElements.attributes.getAttribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE)?.name,
        )
        assertEquals(1, identityElements.outgoing.artifacts.files.files.size)
        project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
    }

    @Test
    fun library_identity_variant_publishes_transitive_winrt_identity_project_dependencies() {
        val root = ProjectBuilder.builder().withName("root").build()
        val libA = ProjectBuilder.builder().withName("liba").withParent(root).build()
        val libB = ProjectBuilder.builder().withName("libb").withParent(root).build()
        val runtime = ProjectBuilder.builder().withName("runtime").withParent(root).build()

        libA.pluginManager.apply(KotlinWinRTPlugin::class.java)
        runtime.pluginManager.apply("java")
        libB.pluginManager.apply("java")
        libB.pluginManager.apply(KotlinWinRTPlugin::class.java)
        libB.dependencies.add("implementation", libB.dependencies.project(mapOf("path" to ":liba")))
        libB.dependencies.add("implementation", libB.dependencies.project(mapOf("path" to ":runtime")))

        val identityElements = libB.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)
        val dependencyProjectPaths = identityElements.dependencies
            .filterIsInstance<ProjectDependency>()
            .map { it.path }

        assertEquals(listOf(":liba"), dependencyProjectPaths)
    }

    @Test
    fun forwarding_library_identity_forwards_dependencies_without_authored_validation_cycle() {
        val root = ProjectBuilder.builder().withName("root").build()
        val producer = ProjectBuilder.builder().withName("producer").withParent(root).build()
        val forwardingLibrary = ProjectBuilder.builder().withName("forwarding").withParent(root).build()

        producer.pluginManager.apply(KotlinWinRTPlugin::class.java)
        forwardingLibrary.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        forwardingLibrary.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        forwardingLibrary.pluginManager.apply(KotlinWinRTPlugin::class.java)
        forwardingLibrary.dependencies.add(
            "winuiMainImplementation",
            forwardingLibrary.dependencies.project(mapOf("path" to ":producer")),
        )

        val identityElements = forwardingLibrary.configurations.getByName(KOTLIN_WINRT_IDENTITY_ELEMENTS_CONFIGURATION)
        val dependencyProjectPaths = identityElements.dependencies
            .filterIsInstance<ProjectDependency>()
            .map { it.path }
        val compileTask = forwardingLibrary.tasks.named("compileKotlinWinuiJvm", KotlinJvmCompile::class.java).get()
        val compileDependencies = declaredTaskDependencyNames(compileTask)
        val identityTask = forwardingLibrary.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        val identityDependencies = declaredTaskDependencyNames(identityTask)

        assertTrue(identityElements.isCanBeConsumed)
        assertEquals(listOf(":producer"), dependencyProjectPaths)
        assertTrue(
            "forwarding library compile must retain authoring preparation: $compileDependencies",
            "generateWinRTProjections" in compileDependencies,
        )
        assertTrue(
            "forwarding library identity must retain authoring metadata preparation: $identityDependencies",
            "generateWinRTProjections" in identityDependencies,
        )
        assertFalse(
            "forwarding library identity dependencies: $identityDependencies",
            identityDependencies.any { it.startsWith("validateCompileKotlin") && it.endsWith("WinRTAuthoredCandidates") },
        )
        assertFalse(
            "forwarding library identity must not build the target jar or it can cycle through validation/compile: $identityDependencies",
            "winuiJvmJar" in identityDependencies,
        )
    }

    @Test
    fun runtime_only_winui_library_keeps_authoring_preparation_without_local_projection_output() {
        val root = ProjectBuilder.builder().withName("root").build()
        val projectionOwner = ProjectBuilder.builder().withName("projectionOwner").withParent(root).build()
        val runtimeConsumer = ProjectBuilder.builder().withName("runtimeConsumer").withParent(root).build()

        projectionOwner.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        projectionOwner.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        projectionOwner.pluginManager.apply(KotlinWinRTPlugin::class.java)
        runtimeConsumer.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        runtimeConsumer.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        runtimeConsumer.pluginManager.apply(KotlinWinRTPlugin::class.java)
        runtimeConsumer.dependencies.add(
            "winuiMainImplementation",
            runtimeConsumer.dependencies.project(mapOf("path" to ":projectionOwner")),
        )

        val implementationDependencies = runtimeConsumer.configurations.getByName("winuiMainImplementation").dependencies
        val compileTask = runtimeConsumer.tasks.named("compileKotlinWinuiJvm", KotlinJvmCompile::class.java).get()
        val compileDependencies = taskDependencyNames(compileTask)
        val compilerArguments = compileTask.compilerOptions.freeCompilerArgs.get()
        val identityTask = runtimeConsumer.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        val identityDependencies = taskDependencyNames(identityTask)
        val generateTask = runtimeConsumer.tasks.named(
            "generateWinRTProjections",
            GenerateWinRTProjectionsTask::class.java,
        ).get()

        assertHasKotlinWinRTRuntimeDependency(implementationDependencies)
        assertHasKotlinWinRTAuthoringDependency(implementationDependencies)
        assertTrue(
            "runtime-only WinUI library compile must retain authoring preparation: $compileDependencies",
            "generateWinRTProjections" in compileDependencies,
        )
        assertFalse(generateTask.emitProjectionSources.get())
        assertFalse(
            "runtime-only WinUI library compile must not merge local compiler support: $compileDependencies",
            "mergeWinRTCompilerSupport" in compileDependencies,
        )
        assertTrue(
            "runtime-only WinUI library identity must retain authoring metadata preparation: $identityDependencies",
            "generateWinRTProjections" in identityDependencies,
        )
        assertTrue(compilerArguments.any { argument -> argument.contains(":authoredCandidatesOutput=") })
        assertFalse(compilerArguments.any { argument -> argument.contains(":compilerSupportManifest=") })
    }

    @Test
    fun prebuilt_projection_consumer_does_not_regenerate_dependency_nuget_surface_without_local_requests() {
        val root = ProjectBuilder.builder().withName("root").build()
        val projectionOwner = ProjectBuilder.builder().withName("projectionOwner").withParent(root).build()
        val consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()

        projectionOwner.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        projectionOwner.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        projectionOwner.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val dependencyIdentity = projectionOwner.layout.buildDirectory
            .file("generated/kotlin-winrt/identity/kotlin-winrt.json")
            .get()
            .asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": ["Microsoft"],
              "includeTypes": [],
              "projectionShapeVersion": 1,
              "projectedTypes": ["Microsoft.UI.Xaml.Controls.IWebView22"],
              "sourceAdditions": [],
              "excludeNamespaces": [],
              "excludeTypes": ["Microsoft.UI.Xaml.Controls.WebView2"],
              "nugetPackages": ["Microsoft.WindowsAppSDK@2.1.3"]
            }
            """.trimIndent(),
        )

        consumer.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        consumer.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        consumer.pluginManager.apply(KotlinWinRTPlugin::class.java)
        consumer.dependencies.add(
            "winuiMainImplementation",
            consumer.dependencies.project(mapOf("path" to ":projectionOwner")),
        )
        consumer.dependencies.add(
            "kotlinWinRTLibraryDependencyIdentity",
            consumer.dependencies.project(mapOf("path" to ":projectionOwner")),
        )

        val localGenerationProvider =
            consumer.extensions.extraProperties.properties["kotlinWinRTLocalGenerationRequired"] as org.gradle.api.provider.Provider<*>
        val compileTask = consumer.tasks.named("compileKotlinWinuiJvm", KotlinJvmCompile::class.java).get()
        val compileDependencies = taskDependencyNames(compileTask)
        val generateTask = consumer.tasks.named(
            "generateWinRTProjections",
            GenerateWinRTProjectionsTask::class.java,
        ).get()

        assertEquals(false, localGenerationProvider.get())
        assertTrue(
            "prebuilt projection consumer compile must retain authoring preparation: $compileDependencies",
            "generateWinRTProjections" in compileDependencies,
        )
        assertFalse(generateTask.emitProjectionSources.get())
        assertFalse(
            "prebuilt projection consumer compile must not merge local compiler support: $compileDependencies",
            "mergeWinRTCompilerSupport" in compileDependencies,
        )
    }

    @Test
    fun kmp_sources_jar_depends_on_local_projection_generation_when_winrt_types_are_requested() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.extraProperties["kotlinWinRTLocalGenerationRequired"] = project.provider { true }
        val sourcesJar = project.tasks.findByName("sourcesJar")
            ?: project.tasks.register("sourcesJar", Jar::class.java).get()

        val dependencies = taskDependencyNames(sourcesJar)

        assertTrue("sourcesJar dependencies: $dependencies", "generateWinRTProjections" in dependencies)
        assertTrue("sourcesJar dependencies: $dependencies", "mergeWinRTCompilerSupport" in dependencies)
    }

    @Test
    fun kmp_target_sources_jars_depend_on_local_projection_generation() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("winuiJvm")
            mingwX64()
        }
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.extraProperties["kotlinWinRTLocalGenerationRequired"] = project.provider { true }

        listOf("jvmSourcesJar", "mingwX64SourcesJar").forEach { taskName ->
            val sourcesJar = project.tasks.findByName(taskName)
                ?: project.tasks.register(taskName, Jar::class.java).get()
            val dependencies = taskDependencyNames(sourcesJar)

            assertTrue("$taskName dependencies: $dependencies", "generateWinRTProjections" in dependencies)
            assertTrue("$taskName dependencies: $dependencies", "mergeWinRTCompilerSupport" in dependencies)
        }
    }

    @Test
    fun kmp_sources_jar_keeps_authoring_preparation_without_local_projection_output() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val sourcesJar = project.tasks.findByName("sourcesJar")
            ?: project.tasks.register("sourcesJar", Jar::class.java).get()

        val dependencies = taskDependencyNames(sourcesJar)
        val generateTask = project.tasks.named(
            "generateWinRTProjections",
            GenerateWinRTProjectionsTask::class.java,
        ).get()

        assertTrue("sourcesJar dependencies: $dependencies", "generateWinRTProjections" in dependencies)
        assertFalse("sourcesJar dependencies: $dependencies", "mergeWinRTCompilerSupport" in dependencies)
        assertFalse(generateTask.emitProjectionSources.get())
    }

    @Test
    fun dependency_owned_projection_request_does_not_wire_local_generation() {
        val root = ProjectBuilder.builder().withName("root").build()
        val dependencyOwner = ProjectBuilder.builder().withName("dependencyOwner").withParent(root).build()
        val consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()
        val dependencyIdentity = dependencyOwner.layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json")
            .get()
            .asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": [],
              "includeTypes": ["Windows.Foundation.IStringable"],
              "projectionShapeVersion": 1,
              "projectedTypes": ["Windows.Foundation.IStringable"],
              "sourceAdditions": ["winrt.interop.InitializeWithWindow", "winrt.interop.WindowNative"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )

        dependencyOwner.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        dependencyOwner.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        dependencyOwner.pluginManager.apply(KotlinWinRTPlugin::class.java)
        consumer.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        consumer.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        consumer.pluginManager.apply(KotlinWinRTPlugin::class.java)
        consumer.extensions.getByType(WinRTExtension::class.java).type("Windows.Foundation.IStringable")
        consumer.dependencies.add(
            "winuiMainImplementation",
            consumer.dependencies.project(mapOf("path" to ":dependencyOwner")),
        )

        val compileTask = consumer.tasks.named("compileKotlinWinuiJvm", KotlinJvmCompile::class.java).get()
        val localGenerationProvider =
            consumer.extensions.extraProperties.properties["kotlinWinRTLocalGenerationRequired"] as org.gradle.api.provider.Provider<*>
        val compileDependencies = taskDependencyNames(compileTask)
        val compilerArguments = compileTask.compilerOptions.freeCompilerArgs.get()
        val identityTask = consumer.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        val identityDependencies = taskDependencyNames(identityTask)
        val generateTask = consumer.tasks.named(
            "generateWinRTProjections",
            GenerateWinRTProjectionsTask::class.java,
        ).get()

        assertEquals(false, localGenerationProvider.get())
        assertTrue(
            "dependency-owned explicit type request compile must retain authoring preparation: $compileDependencies",
            "generateWinRTProjections" in compileDependencies,
        )
        assertFalse(generateTask.emitProjectionSources.get())
        assertFalse(
            "dependency-owned explicit type request must not merge local compiler support: $compileDependencies",
            "mergeWinRTCompilerSupport" in compileDependencies,
        )
        assertTrue(
            "dependency-owned explicit type request identity must retain authoring metadata preparation: $identityDependencies",
            "generateWinRTProjections" in identityDependencies,
        )
        assertTrue(compilerArguments.any { argument -> argument.contains(":authoredCandidatesOutput=") })
        assertFalse(compilerArguments.any { argument -> argument.contains(":compilerSupportManifest=") })
    }

    @Test
    fun application_consumes_external_maven_winrt_identity_variant() {
        val root = Files.createTempDirectory("kotlin-winrt-external-identity-test-")
        val repository = root.resolve("repo")
        val producer = root.resolve("producer")
        val consumer = root.resolve("consumer")

        writeGradleFile(
            producer.resolve("settings.gradle.kts"),
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
            rootProject.name = "producer"
            """.trimIndent(),
        )
        writeGradleFile(
            producer.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        Files.createDirectories(producer)
        Files.writeString(producer.resolve("UpstreamComponent.dll"), "upstream-runtime")
        writeGradleFile(
            producer.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "maven-publish"
                id "io.github.compose-fluent.winrt"
            }

            group = "test.winrt"
            version = "1.0"

            kotlin {
                jvmToolchain(25)
            }

            tasks.named("compileKotlin") {
                enabled = false
            }
            tasks.matching { it.name == "validateCompileKotlinWinRTAuthoredCandidates" }.configureEach {
                enabled = false
            }

            winRT {
                runtimeAsset "UpstreamComponent.dll"
            }

            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
                repositories {
                    maven {
                        url = uri("${repository.toUri()}")
                    }
                }
            }
            """.trimIndent(),
        )

        val publishResult = GradleRunner.create()
            .withProjectDir(producer.toFile())
            .withPluginClasspath()
            .withArguments("publishMavenPublicationToMavenRepository", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, publishResult.task(":generateWinRTIdentity")?.outcome)
        assertTrue(
            Files.isRegularFile(
                repository.resolve("test/winrt/producer/1.0/producer-1.0.module"),
            ),
        )
        val moduleMetadata = Files.readString(repository.resolve("test/winrt/producer/1.0/producer-1.0.module"))
        assertTrue(moduleMetadata.contains(KOTLIN_WINRT_IDENTITY_USAGE))
        val producerIdentity = Files.readString(repository.resolve("test/winrt/producer/1.0/producer-1.0.json"))
        assertTrue(producerIdentity.contains("\"runtimeAssetRecords\""))
        assertTrue(producerIdentity.contains("\"fileName\":\"UpstreamComponent.dll\""))
        assertFalse(producerIdentity.contains("\"runtimeAssets\""))
        val targetModule = repository.resolve("test/winrt/producer-mingw/1.0")
        Files.createDirectories(targetModule)
        Files.writeString(
            targetModule.resolve("producer-mingw-1.0.pom"),
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>test.winrt</groupId>
              <artifactId>producer-mingw</artifactId>
              <version>1.0</version>
              <packaging>klib</packaging>
            </project>
            """.trimIndent(),
        )
        Files.writeString(
            targetModule.resolve("producer-mingw-1.0.module"),
            """
            {
              "formatVersion": "1.1",
              "component": {
                "url": "../../producer/1.0/producer-1.0.module",
                "group": "test.winrt",
                "module": "producer",
                "version": "1.0",
                "attributes": { "org.gradle.status": "release" }
              },
              "variants": []
            }
            """.trimIndent(),
        )

        writeGradleFile(
            consumer.resolve("settings.gradle.kts"),
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
                    maven {
                        url = uri("${repository.toUri()}")
                    }
                    mavenCentral()
                }
            }
            rootProject.name = "consumer"
            """.trimIndent(),
        )
        writeGradleFile(
            consumer.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        writeGradleFile(
            consumer.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvmToolchain(25)
            }

            dependencies {
                implementation "test.winrt:producer-mingw:1.0"
            }

            winRT {
                application { }
            }
            """.trimIndent(),
        )

        val consumeResult = GradleRunner.create()
            .withProjectDir(consumer.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRTApplicationIdentity", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, consumeResult.task(":generateWinRTApplicationIdentity")?.outcome)
        val applicationIdentity = Files.readString(
            consumer.resolve("build/generated/kotlin-winrt/identity/kotlin-winrt-application.json"),
        )
        assertTrue(applicationIdentity.contains("producer-1.0.json"))
        assertTrue(applicationIdentity.contains("dependencyIdentityFiles"))
    }

    @Test
    fun external_kmp_target_module_identity_resolution_also_checks_root_module() {
        assertEquals(
            listOf("skiko-winui-mingw", "skiko-winui"),
            kotlinWinRTIdentityExternalModuleNames("skiko-winui-mingw"),
        )
        assertEquals(
            listOf("projection-owner-mingwx64", "projection-owner"),
            kotlinWinRTIdentityExternalModuleNames("projection-owner-mingwx64"),
        )
        assertEquals(
            listOf("plain-library"),
            kotlinWinRTIdentityExternalModuleNames("plain-library"),
        )
    }

    @Test
    fun identity_task_writes_projection_identity_json() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
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

        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"model\": \"library\""))
        assertTrue(json.contains("\"projectionShapeVersion\": 1"))
        assertTrue(json.contains("\"metadataInputs\": [\"sdk+\"]"))
        assertFalse(json.contains("\"runtimeAssets\""))
        assertTrue(json.contains("\"runtimeAssetRecords\": ["))
        assertTrue(json.contains("\"authoredMetadataRecords\": ["))
        assertTrue(json.contains("\"authoringMetadataIndexRows\": ["))
        assertTrue(json.contains("\"authoredHostManifestRecords\": ["))
        assertTrue(json.contains("\"authoredTargetArtifactRecords\": ["))
        assertTrue(json.contains("\"compilerSupportFileRecords\": ["))
        assertFalse(json.contains("\"authoredMetadata\""))
        assertFalse(json.contains("\"authoringMetadataIndexes\""))
        assertFalse(json.contains("\"authoredTargetArtifacts\""))
        assertFalse(json.contains("\"compilerSupportManifests\""))
        assertFalse(json.contains("\"authoredHostManifests\""))
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
    fun identity_task_publishes_runtime_assets_as_portable_records() {
        val project = ProjectBuilder.builder().build()
        val runtimeDll = project.layout.buildDirectory.file("runtime/SampleRuntime.dll").get().asFile.toPath()
        Files.createDirectories(runtimeDll.parent)
        Files.writeString(runtimeDll, "sample-runtime")

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.runtimeAsset(runtimeDll)
        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"runtimeAssetRecords\": ["))
        assertTrue(json.contains("\"fileName\":\"SampleRuntime.dll\""))
        assertTrue(json.contains("\"contentBase64\":\"${Base64.getEncoder().encodeToString("sample-runtime".toByteArray())}\""))
        assertFalse(json.contains("\"runtimeAssets\""))
        assertFalse(json.contains(runtimeDll.toString().replace("\\", "\\\\")))
    }

    @Test
    fun identity_task_omits_local_metadata_input_paths_from_published_identity() {
        val project = ProjectBuilder.builder().build()
        val localWinmd = project.layout.buildDirectory.file("metadata/ProducerOnly.winmd").get().asFile.toPath()
        Files.createDirectories(localWinmd.parent)
        Files.writeString(localWinmd, "producer-winmd")

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.winmd("sdk+")
        extension.winmd("10.0.26100.0+")
        extension.winmd("nuget:Contoso.Metadata@1.2.3")
        extension.winmd(localWinmd)
        extension.winmd("""nuget:D:\a\producer\metadata""")

        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"metadataInputs\": [\"sdk+\", \"10.0.26100.0+\", \"nuget:Contoso.Metadata@1.2.3\"]"))
        assertFalse(json.contains("ProducerOnly.winmd"))
        assertFalse(json.contains(localWinmd.toString().replace("\\", "\\\\")))
        assertFalse(json.contains("""D:\\a\\producer\\metadata"""))
    }

    @Test
    fun identity_task_publishes_authored_metadata_as_portable_records() {
        val project = ProjectBuilder.builder().build()
        val authoredWinmd = project.layout.buildDirectory.file("authoring/SampleComponent.winmd").get().asFile.toPath()
        Files.createDirectories(authoredWinmd.parent)
        Files.writeString(authoredWinmd, "sample-winmd")

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.authoredMetadataFiles.from(authoredWinmd)
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"authoredMetadataRecords\": ["))
        assertTrue(json.contains("\"fileName\":\"SampleComponent.winmd\""))
        assertTrue(json.contains("\"contentBase64\":\"${Base64.getEncoder().encodeToString("sample-winmd".toByteArray())}\""))
        assertFalse(json.contains("\"authoredMetadata\""))
        assertFalse(json.contains(authoredWinmd.toString().replace("\\", "\\\\")))
    }

    @Test
    fun identity_task_publishes_authored_target_artifacts_as_portable_records() {
        val project = ProjectBuilder.builder().build()
        val authoredJar = project.layout.buildDirectory.file("libs/SampleComponent.jar").get().asFile.toPath()
        Files.createDirectories(authoredJar.parent)
        Files.writeString(authoredJar, "sample-jar")

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val task = project.tasks.named("generateWinRTIdentity", GenerateWinRTIdentityTask::class.java).get()
        task.authoredTargetArtifactFiles.from(authoredJar)
        task.generate()

        val json = Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(json.contains("\"authoredTargetArtifactRecords\": ["))
        assertTrue(json.contains("\"fileName\":\"SampleComponent.jar\""))
        assertTrue(json.contains("\"contentBase64\":\"${Base64.getEncoder().encodeToString("sample-jar".toByteArray())}\""))
        assertFalse(json.contains("\"authoredTargetArtifacts\""))
        assertFalse(json.contains(authoredJar.toString().replace("\\", "\\\\")))
    }

    @Test
    fun identity_task_reads_projection_registrar_projected_type_names() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-identity-registrar-test-")
        val registrar = root.resolve("projection-registrar.tsv")
        val typeShapes = root.resolve("type-shape-descriptors.tsv")
        Files.writeString(
            registrar,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata	
            windows.foundation.IStringable	Windows.Foundation.IStringable	Interface			
            windows.applicationmodel.AppExecutionContext	Windows.ApplicationModel.AppExecutionContext	Enum	System.Enum		
            windows.system.DisplayRequest	Windows.System.DisplayRequest	RuntimeClass	System.Object	windows.system.DisplayRequest.Metadata	
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata	
            """.trimIndent(),
        )
        Files.writeString(
            typeShapes,
            """
            projectedTypeName	key	value
            Microsoft.UI.Dispatching.DispatcherQueueShutdownStartingEventArgs	WRITES_WRAPPER_DECLARATION	true
            Microsoft.UI.Dispatching.DispatcherQueueShutdownStartingEventArgs	WRITES_ABI_DECLARATION	true
            Windows.ApplicationModel.DataTransfer.DataPackagePropertySet	WRITES_WRAPPER_DECLARATION	true
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "generateWinRTIdentityWithRegistrarUnderTest",
            GenerateWinRTIdentityTask::class.java,
        ) { registeredTask ->
            registeredTask.outputFile.set(root.resolve("identity.json").toFile())
            registeredTask.metadataInputs.set(emptyList())
            registeredTask.includeNamespaces.set(emptyList())
            registeredTask.includeTypes.set(emptyList())
            registeredTask.projectionRegistrarFiles.from(registrar)
            registeredTask.typeShapeDescriptorFiles.from(typeShapes)
            registeredTask.excludeNamespaces.set(emptyList())
            registeredTask.excludeTypes.set(emptyList())
            registeredTask.additionExcludeNamespaces.set(emptyList())
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
        }.get()

        task.generate()

        val json = Files.readString(root.resolve("identity.json"))
        assertTrue(json.contains("\"Microsoft.UI.Dispatching.DispatcherQueueShutdownStartingEventArgs\""))
        assertTrue(json.contains("\"Windows.ApplicationModel.AppExecutionContext\""))
        assertTrue(json.contains("\"Windows.ApplicationModel.DataTransfer.DataPackagePropertySet\""))
        assertTrue(json.contains("\"Windows.Foundation.IStringable\""))
        assertTrue(json.contains("\"Windows.Foundation.Uri\""))
        assertTrue(json.contains("\"Windows.System.DisplayRequest\""))
    }

    @Test
    fun identity_task_rejects_malformed_projection_registrar_rows() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-identity-bad-registrar-test-")
        val registrar = root.resolve("projection-registrar.tsv")
        Files.writeString(
            registrar,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            windows.foundation.Uri		RuntimeClass	System.Object	windows.foundation.Uri.Metadata	
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "generateWinRTIdentityWithBadRegistrarUnderTest",
            GenerateWinRTIdentityTask::class.java,
        ) { registeredTask ->
            registeredTask.outputFile.set(root.resolve("identity.json").toFile())
            registeredTask.metadataInputs.set(emptyList())
            registeredTask.includeNamespaces.set(emptyList())
            registeredTask.includeTypes.set(emptyList())
            registeredTask.projectionRegistrarFiles.from(registrar)
            registeredTask.excludeNamespaces.set(emptyList())
            registeredTask.excludeTypes.set(emptyList())
            registeredTask.additionExcludeNamespaces.set(emptyList())
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
        }.get()

        try {
            task.generate()
        } catch (error: GradleException) {
            assertTrue(error.message.orEmpty().contains("malformed row 2"))
            assertFalse(Files.exists(root.resolve("identity.json")))
            return
        }

        throw AssertionError("Expected malformed projection registrar rows to fail closed.")
    }

    @Test
    fun identity_task_rejects_projection_registrar_rows_with_extra_columns() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-identity-extra-registrar-test-")
        val registrar = root.resolve("projection-registrar.tsv")
        Files.writeString(
            registrar,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata		extra
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "generateWinRTIdentityWithExtraRegistrarUnderTest",
            GenerateWinRTIdentityTask::class.java,
        ) { registeredTask ->
            registeredTask.outputFile.set(root.resolve("identity.json").toFile())
            registeredTask.metadataInputs.set(emptyList())
            registeredTask.includeNamespaces.set(emptyList())
            registeredTask.includeTypes.set(emptyList())
            registeredTask.projectionRegistrarFiles.from(registrar)
            registeredTask.excludeNamespaces.set(emptyList())
            registeredTask.excludeTypes.set(emptyList())
            registeredTask.additionExcludeNamespaces.set(emptyList())
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
        }.get()

        try {
            task.generate()
        } catch (error: GradleException) {
            assertTrue(error.message.orEmpty().contains("malformed row 2"))
            assertFalse(Files.exists(root.resolve("identity.json")))
            return
        }

        throw AssertionError("Expected projection registrar rows with extra columns to fail closed.")
    }

    @Test
    fun identity_task_rejects_projection_registrar_header_mismatch() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-identity-bad-registrar-header-test-")
        val registrar = root.resolve("projection-registrar.tsv")
        Files.writeString(
            registrar,
            """
            kotlinClassName	projectedTypeName	kind
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "generateWinRTIdentityWithBadRegistrarHeaderUnderTest",
            GenerateWinRTIdentityTask::class.java,
        ) { registeredTask ->
            registeredTask.outputFile.set(root.resolve("identity.json").toFile())
            registeredTask.metadataInputs.set(emptyList())
            registeredTask.includeNamespaces.set(emptyList())
            registeredTask.includeTypes.set(emptyList())
            registeredTask.projectionRegistrarFiles.from(registrar)
            registeredTask.excludeNamespaces.set(emptyList())
            registeredTask.excludeTypes.set(emptyList())
            registeredTask.additionExcludeNamespaces.set(emptyList())
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
        }.get()

        try {
            task.generate()
        } catch (error: GradleException) {
            assertTrue(error.message.orEmpty().contains("malformed header"))
            assertFalse(Files.exists(root.resolve("identity.json")))
            return
        }

        throw AssertionError("Expected malformed projection registrar headers to fail closed.")
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
            kind	className	sourceFile	entries	owner
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1	local.jar
            generic-type-instantiation	io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations_local_jar	generic-instantiations.tsv	1	local.jar
            future-support	io.github.composefluent.winrt.projections.support.FutureSupport	future-support.tsv	1	local.jar
            xaml-component-resource	io.github.composefluent.winrt.projections.support.WinUiXamlComponentResources	xaml-component-resources.tsv	1	local.jar
            """.trimIndent(),
        )
        Files.writeString(
            localRoot.resolve("projection-registrar.tsv"),
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata	
            """.trimIndent(),
        )
        Files.writeString(
            localRoot.resolve("generic-instantiations.tsv"),
            """
            typeName	typeSignature
            Windows.Foundation.IReference`1<Int>	pinterface({61c17706-2d65-11e0-9ae8-d48564015472};i4)
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
            localRoot.resolve("xaml-component-resources.tsv"),
            """
            runtimeClassName
            WinUI3Package.Shimmer_Resource
            """.trimIndent(),
        )
        Files.writeString(
            dependencyRoot.resolve("compiler-support.tsv"),
            """
            kind	className	sourceFile	entries	owner
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1	dependency.jar
            generic-type-instantiation	io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations_dependency_jar	generic-instantiations.tsv	1	dependency.jar
            xaml-component-resource	io.github.composefluent.winrt.projections.support.WinUiXamlComponentResources	xaml-component-resources.tsv	1	dependency.jar
            """.trimIndent(),
        )
        Files.writeString(
            dependencyRoot.resolve("projection-registrar.tsv"),
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
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
        Files.writeString(
            dependencyRoot.resolve("xaml-component-resources.tsv"),
            """
            runtimeClassName
            WinUI3Package.ModernDialogBoxContent_Resource
            """.trimIndent(),
        )
        val dependencyIdentity = root.resolve("dependency-identity.json")
        val dependencyCompilerSupportRecords = listOf(
            dependencyRoot.resolve("compiler-support.tsv"),
            dependencyRoot.resolve("projection-registrar.tsv"),
            dependencyRoot.resolve("generic-instantiations.tsv"),
            dependencyRoot.resolve("xaml-component-resources.tsv"),
        ).joinToString(prefix = "[", postfix = "]") { file ->
            """{"group":"dependency","fileName":${file.fileName.toString().toJsonString()},"content":${Files.readString(file).toJsonString()}}"""
        }
        Files.writeString(
            dependencyIdentity,
            """
            {
              "compilerSupportFileRecords": $dependencyCompilerSupportRecords
            }
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "mergeWinRTCompilerSupportUnderTest",
            MergeWinRTCompilerSupportTask::class.java,
        ) { registeredTask ->
            registeredTask.localCompilerSupportManifest.set(localRoot.resolve("compiler-support.tsv").toFile())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.outputDirectory.set(outputRoot.toFile())
            registeredTask.emitXamlComponentResourceSources.set(true)
        }.get()

        task.merge()

        val manifest = Files.readString(outputRoot.resolve("compiler-support.tsv"))
        val projectionSupport = Files.readString(outputRoot.resolve("projection-registrar.tsv"))
        val genericSupport = Files.readString(outputRoot.resolve("generic-instantiations.tsv"))
        val futureSupport = Files.readString(outputRoot.resolve("future-support.tsv"))
        assertTrue(manifest.contains("projection-registrar"))
        assertTrue(manifest.contains("kind\tclassName\tsourceFile\tentries\towner"))
        assertTrue(manifest.contains("projection-registrar\tio.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic\tprojection-registrar.tsv\t2\tlocal.jar"))
        assertTrue(manifest.contains("projection-registrar\tio.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic\tprojection-registrar.tsv\t2\tdependency.jar"))
        assertTrue(manifest.contains("future-support\tio.github.composefluent.winrt.projections.support.FutureSupport"))
        assertTrue(
            manifest.contains(
                "generic-type-instantiation\tio.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations_local_jar\tgeneric-instantiations.tsv\t2\tlocal.jar",
            ),
        )
        assertTrue(
            manifest.contains(
                "generic-type-instantiation\tio.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations_dependency_jar\tgeneric-instantiations.tsv\t2\tdependency.jar",
            ),
        )
        assertTrue(manifest.contains("xaml-component-resource\tio.github.composefluent.winrt.projections.support.WinUiXamlComponentResources"))
        assertFalse(manifest.contains("WinRTGenericTypeInstantiationRegistry"))
        assertTrue(projectionSupport.contains("Windows.Foundation.Uri"))
        assertTrue(projectionSupport.contains("Windows.System.Display.DisplayRequest"))
        assertTrue(genericSupport.contains("Windows.Foundation.IReference`1<Int>"))
        assertTrue(genericSupport.contains("Windows.Foundation.IReference`1<String>"))
        assertTrue(futureSupport.contains("alpha\tbeta"))
        val xamlResources = Files.readString(outputRoot.resolve("xaml-component-resources.tsv"))
        assertTrue(xamlResources.contains("WinUI3Package.ModernDialogBoxContent_Resource"))
        assertTrue(xamlResources.contains("WinUI3Package.Shimmer_Resource"))
        val xamlBootstrap = Files.readString(
            outputRoot.resolve("io/github/composefluent/winrt/projections/support/WinUiXamlComponentResources.kt"),
        )
        assertTrue(xamlBootstrap.contains("ActivationFactory.activateInstance(\"WinUI3Package.ModernDialogBoxContent_Resource\")"))
        assertTrue(xamlBootstrap.contains("ActivationFactory.activateInstance(\"WinUI3Package.Shimmer_Resource\")"))
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
            "mergeRetiredWinRTCompilerSupportUnderTest",
            MergeWinRTCompilerSupportTask::class.java,
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
    fun merge_compiler_support_task_rejects_malformed_manifest_rows() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-malformed-compiler-support-test-")
        val localRoot = root.resolve("local")
        val outputRoot = root.resolve("merged")
        Files.createDirectories(localRoot)
        Files.writeString(
            localRoot.resolve("compiler-support.tsv"),
            """
            kind	className	sourceFile	entries
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	projection-registrar.tsv	1	owner	extra
            """.trimIndent(),
        )
        Files.writeString(
            localRoot.resolve("projection-registrar.tsv"),
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata	
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "mergeMalformedWinRTCompilerSupportUnderTest",
            MergeWinRTCompilerSupportTask::class.java,
        ) { registeredTask ->
            registeredTask.localCompilerSupportManifest.set(localRoot.resolve("compiler-support.tsv").toFile())
            registeredTask.outputDirectory.set(outputRoot.toFile())
        }.get()

        try {
            task.merge()
        } catch (error: GradleException) {
            assertTrue(error.message.orEmpty().contains("malformed row 2"))
            assertFalse(Files.exists(outputRoot.resolve("compiler-support.tsv")))
            return
        }

        throw AssertionError("Expected malformed compiler support manifest rows to fail closed.")
    }

    @Test
    fun merge_compiler_support_task_rejects_unexpected_manifest_headers() {
        val project = ProjectBuilder.builder().build()
        val root = Files.createTempDirectory("kotlin-winrt-bad-header-compiler-support-test-")
        val localRoot = root.resolve("local")
        val outputRoot = root.resolve("merged")
        Files.createDirectories(localRoot)
        Files.writeString(
            localRoot.resolve("compiler-support.tsv"),
            """
            kind	className	entries	sourceFile
            projection-registrar	io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic	1	projection-registrar.tsv
            """.trimIndent(),
        )
        Files.writeString(
            localRoot.resolve("projection-registrar.tsv"),
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            windows.foundation.Uri	Windows.Foundation.Uri	RuntimeClass	System.Object	windows.foundation.Uri.Metadata	
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "mergeBadHeaderWinRTCompilerSupportUnderTest",
            MergeWinRTCompilerSupportTask::class.java,
        ) { registeredTask ->
            registeredTask.localCompilerSupportManifest.set(localRoot.resolve("compiler-support.tsv").toFile())
            registeredTask.outputDirectory.set(outputRoot.toFile())
        }.get()

        try {
            task.merge()
        } catch (error: GradleException) {
            assertTrue(error.message.orEmpty().contains("unexpected header"))
            assertFalse(Files.exists(outputRoot.resolve("compiler-support.tsv")))
            return
        }

        throw AssertionError("Expected unexpected compiler support manifest headers to fail closed.")
    }

    @Test
    fun dependency_identity_include_types_suppress_downstream_projection_roots() {
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
        val model = WinRTMetadataModel(
            listOf(
                WinRTNamespace(
                    "Microsoft.UI.Xaml",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "DependencyProperty",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRTNamespace(
                    "Microsoft.UI.Xaml.Automation",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation",
                            name = "AutomationProperties",
                            kind = WinRTTypeKind.RuntimeClass,
                            properties = listOf(
                                WinRTPropertyDefinition(
                                    name = "AccessibilityViewProperty",
                                    typeName = "Microsoft.UI.Xaml.DependencyProperty",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRTNamespace(
                    "Microsoft.UI.Xaml.Controls",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "Button",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRTNamespace(
                    "Windows.ApplicationModel.DataTransfer",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "DataPackageView",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("Windows.ApplicationModel.DataTransfer.DataPackageView"),
            dependencyProjectedTypeNames(model, listOf(dependencyIdentity)).toList(),
        )
    }

    @Test
    fun dependency_identity_authoring_metadata_index_participates_in_downstream_authoring_discovery() {
        val project = ProjectBuilder.builder().build()
        val root = project.layout.buildDirectory.dir("dependency-authoring-index").get().asFile.toPath()
        val dependencyIdentity = root.resolve("kotlin-winrt.json")
        Files.createDirectories(root)
        Files.writeString(
            dependencyIdentity,
            """
            {
              "authoringMetadataIndexRows": ["Microsoft.UI.Xaml.Application\tRuntimeClass\tMicrosoft.UI.Xaml.IApplicationOverrides\t"]
            }
            """.trimIndent(),
        )

        val merged = mergedAuthoringMetadataIndexTypes(
            WinRTMetadataModel(emptyList()),
            listOf(dependencyIdentity.toFile()),
        )

        assertEquals(listOf("Microsoft.UI.Xaml.Application"), merged.map { it.qualifiedName })
        assertEquals("RuntimeClass", merged.single().kind)
        assertEquals(listOf("Microsoft.UI.Xaml.IApplicationOverrides"), merged.single().overridableInterfaces)
    }

    @Test
    fun dependency_identity_authored_host_manifest_suppresses_downstream_authored_runtime_class_projection() {
        val project = ProjectBuilder.builder().build()
        val root = project.layout.buildDirectory.dir("dependency-authored-host").get().asFile.toPath()
        val dependencyIdentity = root.resolve("kotlin-winrt.json")
        Files.createDirectories(root)
        Files.writeString(
            dependencyIdentity,
            """
            {
              "includeTypes": [],
              "projectionShapeVersion": 1,
              "projectedTypes": [],
              "authoredHostManifestRecords": [
                {
                  "assemblyName": "dependency",
                  "hostExportsClass": "sample.DependencyHostExports",
                  "targetArtifact": "dependency.jar",
                  "activatableClasses": ["androidx.compose.ui.window.WinUIXamlApplication"],
                  "activatableClassTargets": {}
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            setOf("androidx.compose.ui.window.WinUIXamlApplication"),
            dependencyProjectedTypeNames(WinRTMetadataModel(emptyList()), listOf(dependencyIdentity.toFile())),
        )
    }

    @Test
    fun dependency_identity_ignores_legacy_projected_types_without_projection_shape_version() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": [],
              "includeTypes": [],
              "projectedTypes": ["SimpleMathComponent.SimpleMath"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )
        val model = WinRTMetadataModel(
            listOf(
                WinRTNamespace(
                    "SimpleMathComponent",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "SimpleMathComponent",
                            name = "SimpleMath",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            emptyList<String>(),
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
              "projectionShapeVersion": 1,
              "projectedTypes": ["SimpleMathComponent.SimpleMath"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )
        val model = WinRTMetadataModel(
            listOf(
                WinRTNamespace(
                    "Windows.Data.Json",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonObject",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRTNamespace(
                    "SimpleMathComponent",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "SimpleMathComponent",
                            name = "SimpleMath",
                            kind = WinRTTypeKind.RuntimeClass,
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
    fun dependency_identity_projection_surface_prevents_downstream_duplicate_generated_fqns() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": ["Sample.Dependency"],
              "includeTypes": [],
              "projectionShapeVersion": 1,
              "projectedTypes": ["Sample.Dependency.SharedWidget", "Sample.Dependency.DerivedWidget"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )
        val model = WinRTMetadataModel(
            listOf(
                WinRTNamespace(
                    "Sample.Dependency",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample.Dependency",
                            name = "SharedWidget",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Sample.Dependency",
                            name = "DerivedWidget",
                            kind = WinRTTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Dependency.SharedWidget",
                        ),
                    ),
                ),
                WinRTNamespace(
                    "Sample.Application",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample.Application",
                            name = "AppWidget",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        val dependencyOwnedTypes = dependencyProjectedTypeNames(model, listOf(dependencyIdentity))
        val filesByPath = KotlinProjectionGenerator(
            emitSupportFiles = true,
            suppressedProjectionTypeNames = dependencyOwnedTypes,
        )
            .generate(model)
            .associateBy { it.relativePath }

        assertEquals(setOf("Sample.Dependency.DerivedWidget", "Sample.Dependency.SharedWidget"), dependencyOwnedTypes)
        assertFalse(filesByPath.containsKey("sample/dependency/DerivedWidget.kt"))
        assertFalse(filesByPath.containsKey("sample/dependency/SharedWidget.kt"))
        assertTrue(filesByPath.containsKey("sample/application/AppWidget.kt"))
        val registrar = filesByPath.getValue("kotlin-winrt-support/projection-registrar.tsv").contents
        assertFalse(registrar.contains("Sample.Dependency.SharedWidget"))
        assertTrue(registrar.contains("Sample.Application.AppWidget"))
    }

    @Test
    fun checked_in_projection_breadth_stays_on_smoke_surface_until_upstream_contracts_close() {
        val projectionsRoot = repositoryRoot().resolve("winrt-projections/src/main")
        val checkedInProjectionFiles = Files.walk(projectionsRoot).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .map { path -> projectionsRoot.relativize(path).toString().replace('\\', '/') }
                .sorted()
                .toList()
        }

        assertEquals(
            listOf(
                "kotlin/io/github/composefluent/winrt/projections/ProjectionModuleMarker.kt",
                "winrt/SimpleMathComponent.dll",
                "winrt/SimpleMathComponent.winmd",
            ),
            checkedInProjectionFiles,
        )
    }

    @Test
    fun application_plugin_resolves_identity_configuration() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).apply {
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
        project.tasks.named("generateWinRTApplicationIdentity", GenerateWinRTApplicationIdentityTask::class.java).get()
        val stageRuntimeAssets = project.tasks.named("stageWinRTRuntimeAssets", StageWinRTRuntimeAssetsTask::class.java).get()
        assertTrue(stageRuntimeAssets.runtimeAssetFiles.files.any { it.name == "SimpleMathComponent.dll" })
        project.tasks.named("buildWinRTAuthoringHost", BuildWinRTAuthoringHostTask::class.java).get()
        project.tasks.named("buildWinRTApplicationHost", BuildWinRTApplicationHostTask::class.java).get()
        project.tasks.named("runWinRTApplicationHost").get()
        assertEquals("buildWinRTApplicationHost", project.extensions.extraProperties["kotlinWinRTApplicationHostTask"])
        assertEquals("runWinRTApplicationHost", project.extensions.extraProperties["kotlinWinRTRunApplicationHostTask"])
    }

    @Test
    fun plugin_configures_authoring_scanner_classpath_without_application_boilerplate() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application {}

        val scannerConfiguration = project.configurations.getByName(KOTLIN_WINRT_COMPILER_PLUGIN_CONFIGURATION)
        val task = project.tasks.named("generateWinRTProjections", GenerateWinRTProjectionsTask::class.java).get()
        val scannerClasspathNames = task.authoringScannerClasspath.files.map { it.name }.toSet()

        assertTrue(scannerConfiguration.dependencies.isNotEmpty())
        assertTrue(
            scannerClasspathNames.joinToString("\n"),
            scannerClasspathNames.any { it.contains("kotlin-compiler", ignoreCase = true) },
        )
    }

    @Test
    fun resolvable_identity_configuration_does_not_mutate_after_resolution() {
        val root = ProjectBuilder.builder().withName("root").build()
        val application = ProjectBuilder.builder().withName("app").withParent(root).build()
        val library = ProjectBuilder.builder().withName("lib").withParent(root).build()

        library.pluginManager.apply(KotlinWinRTPlugin::class.java)
        application.pluginManager.apply("java")
        application.pluginManager.apply(KotlinWinRTPlugin::class.java)
        application.dependencies.add("implementation", application.dependencies.project(mapOf("path" to ":lib")))
        application.extensions.getByType(WinRTExtension::class.java).application {}

        val identityConfiguration = application.configurations.getByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION)
        identityConfiguration.incoming.artifactView { view ->
            view.isLenient = true
            view.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                application.objects.named(Usage::class.java, KOTLIN_WINRT_IDENTITY_USAGE),
            )
        }.files.files

        application.dependencies.add("implementation", application.dependencies.create("sample:late-dependency:1.0"))

        assertEquals(org.gradle.api.artifacts.Configuration.State.RESOLVED, identityConfiguration.state)
    }

    @Test
    fun application_plugin_uses_gradle_application_main_class_for_native_host() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply("application")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(org.gradle.api.plugins.JavaApplication::class.java).mainClass.set("sample.MainKt")
        project.extensions.getByType(WinRTExtension::class.java).application {}

        val task = project.tasks.named("buildWinRTApplicationHost", BuildWinRTApplicationHostTask::class.java).get()

        assertEquals("sample.MainKt", task.mainClass.get())
        assertEquals("sample-app", task.executableBaseName.get())
        assertFalse(task.console.get())
    }

    @Test
    fun application_plugin_can_enable_console_native_host() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.mainClass.set("sample.MainKt")
            application.console.set(true)
        }

        val task = project.tasks.named("buildWinRTApplicationHost", BuildWinRTApplicationHostTask::class.java).get()

        assertTrue(task.console.get())
    }

    @Test
    fun application_host_infers_kmp_jvm_target_runtime_classpath_and_jar() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
            jvm("winuiJvm")
            mingwX64("winuiMingw") {
                binaries {
                    executable()
                }
            }
        }
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.mainClass.set("sample.MainKt")
        }

        val hostTask = project.tasks.named("buildWinRTApplicationHost", BuildWinRTApplicationHostTask::class.java).get()
        val hostDependencies = taskDependencyNames(hostTask)
        val stagePackageTask = project.tasks.named("stageWinRTApplicationPackage", StageWinRTApplicationPackageTask::class.java).get()
        val stagePackageDependencies = taskDependencyNames(stagePackageTask)

        assertTrue(project.configurations.getByName("winuiJvmRuntimeClasspath").isCanBeResolved)
        assertTrue("buildWinRTApplicationHost dependencies: $hostDependencies", "winuiJvmJar" in hostDependencies)
        assertTrue(
            "mingw release executable must remain staged through the native package path: $stagePackageDependencies",
            "linkReleaseExecutableWinuiMingw" in stagePackageDependencies,
        )
        assertFalse("buildWinRTApplicationHost must not depend on mingw native link tasks: $hostDependencies", "linkReleaseExecutableWinuiMingw" in hostDependencies)
    }

    @Test
    fun application_host_task_graph_includes_generation_support_staging_and_host_prerequisites() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("winuiJvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.mainClass.set("sample.MainKt")
        }

        val hostTask = project.tasks.named("buildWinRTApplicationHost", BuildWinRTApplicationHostTask::class.java).get()
        val runTask = project.tasks.named("runWinRTApplicationHost").get()
        val hostDependencies = taskDependencyNames(hostTask)
        val runDependencies = taskDependencyNames(runTask)

        listOf(
            "generateWinRTProjections",
            "mergeWinRTCompilerSupport",
            "stageWinRTApplicationPackage",
            "stageWinRTRuntimeAssets",
            "buildWinRTAuthoringHost",
            "winuiJvmJar",
        ).forEach { expected ->
            assertTrue("buildWinRTApplicationHost dependencies: $hostDependencies", expected in hostDependencies)
        }
        assertTrue("runWinRTApplicationHost dependencies: $runDependencies", "buildWinRTApplicationHost" in runDependencies)
    }

    @Test
    fun run_host_is_first_class_typed_task() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.mainClass.set("sample.MainKt")
        }

        val runTask = project.tasks.named("runWinRTApplicationHost").get()

        val runTaskType = runCatching {
            Class.forName("io.github.composefluent.winrt.gradle.RunWinRTApplicationHostTask")
        }.getOrNull()
        assertTrue("RunWinRTApplicationHostTask must be a public Gradle task type.", runTaskType != null)
        assertTrue(runTaskType!!.isInstance(runTask))
        listOf("getArgs", "getJvmArgs", "getEnvironmentVariables", "getWorkingDirectory", "getOutputLog").forEach { methodName ->
            assertTrue(
                "RunWinRTApplicationHostTask must expose $methodName.",
                runTaskType.methods.any { method -> method.name == methodName },
            )
        }
    }

    @Test
    fun application_dsl_registers_additional_typed_run_host_tasks() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.mainClass.set("sample.MainKt")
            application.runTask("runWinUISmoke") { task ->
                task.args.add("--smoke")
                task.jvmArgs.add("-Xmx256m")
                task.environmentVariables.put("SMOKE_MODE", "skiko")
                task.outputLog.set(project.layout.buildDirectory.file("reports/winui-smoke.log"))
            }
        }
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.runTask("runWinUIReplay") { task ->
                task.args.add("--replay")
            }
        }

        val smokeTask = project.tasks.named("runWinUISmoke", RunWinRTApplicationHostTask::class.java).get()
        val replayTask = project.tasks.named("runWinUIReplay", RunWinRTApplicationHostTask::class.java).get()
        val smokeDependencies = taskDependencyNames(smokeTask)

        assertEquals(listOf("--smoke"), smokeTask.args.get())
        assertEquals(listOf("-Xmx256m"), smokeTask.jvmArgs.get())
        assertEquals(mapOf("SMOKE_MODE" to "skiko"), smokeTask.environmentVariables.get())
        assertEquals("winui-smoke.log", smokeTask.outputLog.get().asFile.name)
        assertTrue("runWinUISmoke dependencies: $smokeDependencies", "buildWinRTApplicationHost" in smokeDependencies)
        assertEquals(listOf("--replay"), replayTask.args.get())
        assertTrue(project.tasks.named("runWinRTApplicationHost").get() is RunWinRTApplicationHostTask)
    }

    @Test
    fun run_host_launch_configuration_participates_in_gradle_task_state() {
        val runTaskType = RunWinRTApplicationHostTask::class.java

        listOf("getArgs", "getJvmArgs", "getEnvironmentVariables").forEach { methodName ->
            val method = runTaskType.methods.single { method -> method.name == methodName }
            assertTrue(
                "$methodName must be a Gradle input so different launch modes are not treated as the same run.",
                method.isAnnotationPresent(org.gradle.api.tasks.Input::class.java),
            )
        }
        assertTrue(
            "getHostExecutable must be a Gradle input file.",
            runTaskType.methods.single { method -> method.name == "getHostExecutable" }
                .isAnnotationPresent(org.gradle.api.tasks.InputFile::class.java),
        )
        val workingDirectory = runTaskType.methods.single { method -> method.name == "getWorkingDirectory" }
        assertTrue(
            "getWorkingDirectory must be a Gradle input directory because the host resolves runtime assets relative to it.",
            workingDirectory.isAnnotationPresent(org.gradle.api.tasks.InputDirectory::class.java),
        )
        assertEquals(
            PathSensitivity.RELATIVE,
            workingDirectory.getAnnotation(org.gradle.api.tasks.PathSensitive::class.java).value,
        )
        assertTrue(
            "getOutputLog must be a Gradle output file so each smoke task can own its validation log.",
            runTaskType.methods.single { method -> method.name == "getOutputLog" }
                .isAnnotationPresent(org.gradle.api.tasks.OutputFile::class.java),
        )
        assertTrue(
            "getOutputLog must remain optional for interactive/manual application runs.",
            runTaskType.methods.single { method -> method.name == "getOutputLog" }
                .isAnnotationPresent(org.gradle.api.tasks.Optional::class.java),
        )
    }

    @Test
    fun run_host_and_java_exec_own_runtime_assets_and_pri_lifecycle() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val javaRunTask = project.tasks.register("runSample", JavaExec::class.java).get()
        project.extensions.getByType(WinRTExtension::class.java).application {}

        val runHostTask = project.tasks.named("runWinRTApplicationHost").get()
        val runHostDependencies = taskDependencyNames(runHostTask)
        val javaRunDependencies = taskDependencyNames(javaRunTask)

        assertTrue("runWinRTApplicationHost dependencies: $runHostDependencies", "buildWinRTApplicationHost" in runHostDependencies)
        assertTrue("runSample dependencies: $javaRunDependencies", "stageWinRTApplicationPackage" in javaRunDependencies)
        assertTrue(
            javaRunTask.jvmArgumentProviders
                .any { provider -> provider.javaClass.name.contains("RuntimeAssetsRootJvmArgumentProvider") },
        )
    }

    @Test
    fun application_plugin_wires_unpacked_java_exec_for_manual_bootstrap() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val runTask = project.tasks.register("runSample", JavaExec::class.java).get()
        project.extensions.getByType(WinRTExtension::class.java).application {}

        val stageTask = project.tasks.named("stageWinRTApplicationPackage").get()

        assertTrue(runTask.taskDependencies.getDependencies(runTask).contains(stageTask))
        assertTrue(
            runTask.jvmArgumentProviders
                .any { provider -> provider.javaClass.name.contains("RuntimeAssetsRootJvmArgumentProvider") },
        )
    }

    @Test
    fun packaged_application_plugin_does_not_wire_java_exec_for_unpackaged_bootstrap() {
        val project = ProjectBuilder.builder().withName("sample-app").build()

        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val runTask = project.tasks.register("runSample", JavaExec::class.java).get()
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.packaged()
        }

        val dependencies = runTask.taskDependencies.getDependencies(runTask).map { it.name }

        assertFalse("stageWinRTApplicationPackage" in dependencies)
        assertFalse(
            runTask.jvmArgumentProviders
                .any { provider -> provider.javaClass.name.contains("RuntimeAssetsRootJvmArgumentProvider") },
        )
    }

    @Test
    fun application_plugin_passes_runtime_assets_root_to_java_exec() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-javaexec-assets-test-")
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
            rootProject.name = "kotlin-winrt-javaexec-assets-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "java"
                id "io.github.compose-fluent.winrt"
            }

            winRT {
                application {
                    generateProjectPri = false
                }
            }

            tasks.register("runSample", JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass.set "sample.Main"
            }
            """.trimIndent(),
        )
        val source = projectDir.resolve("src/main/java/sample/Main.java")
        Files.createDirectories(source.parent)
        Files.writeString(
            source,
            """
            package sample;

            public final class Main {
                public static void main(String[] args) {
                    System.out.println("runtimeAssetsRoot=" + System.getProperty("kotlin.winrt.runtimeAssetsRoot"));
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("runSample", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageWinRTApplicationPackage")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":runSample")?.outcome)
        val actualRuntimeAssetsRoot = result.output
            .lineSequence()
            .firstOrNull { it.startsWith("runtimeAssetsRoot=") }
            ?.substringAfter("runtimeAssetsRoot=")
            ?.let { Path.of(it).toAbsolutePath().normalize() }
        val expectedRuntimeAssetsRoot = projectDir
            .resolve("build/kotlin-winrt/application-layout/mingwX64/release")
            .toAbsolutePath()
            .normalize()
        assertTrue(
            result.output,
            actualRuntimeAssetsRoot != null,
        )
        assertEquals(expectedRuntimeAssetsRoot, actualRuntimeAssetsRoot)
    }

    @Test
    fun application_packaging_only_nuget_does_not_expand_projection_surface() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-packaging-only-test-")
        val nugetRoot = projectDir.resolve("nuget")
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK",
            version = "1.8.260416003",
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
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }
            rootProject.name = "kotlin-winrt-packaging-only-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "io.github.compose-fluent.winrt"
            }

            winRT {
                nugetGlobalPackagesRoots.add("${nugetRoot.toString().replace("\\", "\\\\")}")
                restoreNuGetPackages = false
                application {}
                nugetPackage "Microsoft.WindowsAppSDK", "1.8.260416003"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRTProjections", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        val outputRoot = projectDir.resolve("build/generated/kotlin-winrt/src/jvmMain/kotlin")
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

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).apply {
            application {}
            nugetGlobalPackagesRoots.add(globalPackagesRoot.toString())
            nugetPackage("Sample.Package", "1.0.0")
        }

        val task = project.tasks.named("stageWinRTRuntimeAssets", StageWinRTRuntimeAssetsTask::class.java).get()

        assertTrue(
            task.nugetPackageContentFiles.files.any {
                it.toPath().toAbsolutePath().normalize() == packageRoot.toAbsolutePath().normalize()
            },
        )
    }

    @Test
    fun application_plugin_wires_runtime_nuget_resolution_manifest_into_runtime_staging() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).apply {
            application {}
            nugetPackage("Sample.Package", "1.0.0")
        }

        val resolveTask = project.tasks.named(
            "resolveWinRTRuntimeNuGetPackages",
            ResolveWinRTRuntimeNuGetPackagesTask::class.java,
        ).get()
        val stageTask = project.tasks.named("stageWinRTRuntimeAssets", StageWinRTRuntimeAssetsTask::class.java).get()

        assertTrue(
            stageTask.taskDependencies.getDependencies(stageTask).any { it.name == "resolveWinRTRuntimeNuGetPackages" },
        )
        assertTrue(stageTask.resolvedNuGetPackageManifestFiles.files.contains(resolveTask.outputFile.get().asFile))
    }

    @Test
    fun runtime_nuget_resolution_manifest_rejects_missing_package_roots() {
        val manifest = Files.createTempFile("kotlin-winrt-runtime-nuget-packages-", ".json")
        Files.writeString(
            manifest,
            """
            {
              "model": "winrt-runtime-nuget-packages"
            }
            """.trimIndent(),
        )

        val error = runCatching { readResolvedRuntimeNuGetPackageRoots(manifest.toFile()) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("is missing packageRoots"),
        )
    }

    @Test
    fun plugin_registers_compiler_plugin_dependency_for_late_kmp_compiler_plugin_classpath_configurations() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        val targetConfiguration = project.configurations.create("kotlinCompilerPluginClasspathWinuiJvm")

        assertTrue(targetConfiguration.dependencies.isNotEmpty())
        assertHasKotlinWinRTClasspathDependency(targetConfiguration.dependencies, "winrt-runtime")
        assertHasKotlinWinRTClasspathDependency(targetConfiguration.dependencies, "winrt-authoring")
    }

    @Test
    fun application_plugin_collects_only_dependencies_with_kotlin_winrt_identity_metadata() {
        val root = ProjectBuilder.builder().withName("root").build()
        val library = ProjectBuilder.builder().withName("library").withParent(root).build()
        val runtime = ProjectBuilder.builder().withName("runtime").withParent(root).build()
        val application = ProjectBuilder.builder().withName("application").withParent(root).build()

        library.pluginManager.apply(KotlinWinRTPlugin::class.java)
        runtime.pluginManager.apply("java")
        application.pluginManager.apply("java")
        application.pluginManager.apply(KotlinWinRTPlugin::class.java)
        application.extensions.getByType(WinRTExtension::class.java).application {}
        application.dependencies.add("implementation", application.dependencies.project(mapOf("path" to ":library")))
        application.dependencies.add("implementation", application.dependencies.project(mapOf("path" to ":runtime")))

        val identityConfiguration = application.configurations.getByName(KOTLIN_WINRT_IDENTITY_CONFIGURATION)
        assertEquals(
            listOf(":library"),
            identityConfiguration.dependencies.withType(ProjectDependency::class.java).map { dependency -> dependency.path },
        )
        val applicationIdentityTask = application.tasks.named(
            "generateWinRTApplicationIdentity",
            GenerateWinRTApplicationIdentityTask::class.java,
        ).get()

        assertTrue(applicationIdentityTask.dependencyIdentityFiles.files.isNotEmpty())
    }

    @Test
    fun application_plugin_collects_kmp_source_set_project_dependencies_with_identity_metadata() {
        val root = ProjectBuilder.builder().withName("root").build()
        val library = ProjectBuilder.builder().withName("library").withParent(root).build()
        val runtime = ProjectBuilder.builder().withName("runtime").withParent(root).build()
        val application = ProjectBuilder.builder().withName("application").withParent(root).build()

        library.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        library.pluginManager.apply(KotlinWinRTPlugin::class.java)
        application.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        application.pluginManager.apply(KotlinWinRTPlugin::class.java)
        application.extensions.getByType(WinRTExtension::class.java).application {}
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
        assertEquals(
            listOf(":library"),
            identityConfiguration.dependencies.withType(ProjectDependency::class.java).map { dependency -> dependency.path },
        )
        val applicationIdentityTask = application.tasks.named(
            "generateWinRTApplicationIdentity",
            GenerateWinRTApplicationIdentityTask::class.java,
        ).get()

        assertTrue(applicationIdentityTask.dependencyIdentityFiles.files.isNotEmpty())
    }

    @Test
    fun application_plugin_wires_runtime_assets_into_java_resources() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application {}
        project.pluginManager.apply("java")

        val processResources = project.tasks.named("processResources").get()
        val dependencies = processResources.taskDependencies.getDependencies(processResources).map { it.name }
        assertTrue("stageWinRTApplicationPackage" in dependencies)
    }

    @Test
    fun packaged_application_plugin_does_not_wire_appx_payload_into_java_resources() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.packaged()
        }
        project.pluginManager.apply("java")

        val processResources = project.tasks.named("processResources").get()
        val dependencies = processResources.taskDependencies.getDependencies(processResources).map { it.name }
        assertFalse("stageWinRTApplicationPackage" in dependencies)
    }

    @Test
    fun application_plugin_accepts_gradle_application_distribution_model() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val packageOutput = project.layout.buildDirectory.file("custom/Contoso.msix")
        val signedPackageOutput = project.layout.buildDirectory.file("custom/Contoso-signed.msix")
        val signingCertificate = project.layout.buildDirectory.file("certificates/contoso.pfx")
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
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
            WinRTApplicationPackageMode.Packaged,
            project.extensions.getByType(WinRTExtension::class.java).application.packageMode.get(),
        )
        project.tasks.named("stageWinRTRuntimeAssets", StageWinRTRuntimeAssetsTask::class.java).get()
        project.tasks.named("stageWinRTApplicationPackage", StageWinRTApplicationPackageTask::class.java).get()
        val packageTask = project.tasks.named("packageWinRTApplication", PackageWinRTApplicationTask::class.java).get()
        assertEquals(packageOutput.get().asFile, packageTask.outputFile.get().asFile)
        assertEquals("C:/Windows Kits/10/bin/makeappx.exe", packageTask.makeAppxExecutable.get())
        assertEquals(false, packageTask.generatePackage.get())
        val verifyTask = project.tasks.named("verifyWinRTApplicationPackage", VerifyWinRTApplicationPackageTask::class.java).get()
        assertEquals(packageOutput.get().asFile, verifyTask.packageFile.get().asFile)
        assertEquals("C:/Windows Kits/10/bin/makeappx.exe", verifyTask.makeAppxExecutable.get())
        assertEquals(true, verifyTask.verifyPackage.get())
        val stagePackageTask =
            project.tasks.named("stageWinRTApplicationPackage", StageWinRTApplicationPackageTask::class.java).get()
        assertTrue(stagePackageTask.packagePayloadFiles.files.any { it.path.replace("\\", "/").endsWith("build/libs/app.jar") })
        assertTrue(stagePackageTask.projectPriTargetPaths.get().values.contains("App/app.jar"))
        val signTask = project.tasks.named("signWinRTApplicationPackage", SignWinRTApplicationPackageTask::class.java).get()
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
            project.tasks.named("installWinRTApplicationPackage", InstallWinRTApplicationPackageTask::class.java).get()
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

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val packageOutput = project.layout.buildDirectory.file("custom/UnsignedInstall.msix")
        val signedPackageOutput = project.layout.buildDirectory.file("custom/UnsignedInstall-signed.msix")
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.packaged()
            application.packageOutputFile.set(packageOutput)
            application.signedPackageOutputFile.set(signedPackageOutput)
            application.signPackage.set(false)
            application.installPackage.set(true)
        }
        project.pluginManager.apply("application")

        val signTask = project.tasks.named("signWinRTApplicationPackage", SignWinRTApplicationPackageTask::class.java).get()
        val installTask =
            project.tasks.named("installWinRTApplicationPackage", InstallWinRTApplicationPackageTask::class.java).get()

        assertEquals(false, signTask.signPackage.get())
        assertFalse(signTask.onlyIf.isSatisfiedBy(signTask))
        assertEquals(packageOutput.get().asFile, installTask.packageFile.get().asFile)
        assertEquals(true, installTask.installPackage.get())
        assertTrue(installTask.onlyIf.isSatisfiedBy(installTask))
    }

    @Test
    fun application_install_task_uses_explicit_install_package_file() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val packageOutput = project.layout.buildDirectory.file("custom/Default.msix")
        val signedPackageOutput = project.layout.buildDirectory.file("custom/Default-signed.msix")
        val installPackageFile = project.layout.buildDirectory.file("custom/InstallOverride.msix")
        project.extensions.getByType(WinRTExtension::class.java).application { application ->
            application.packaged()
            application.packageOutputFile.set(packageOutput)
            application.signedPackageOutputFile.set(signedPackageOutput)
            application.installPackageFile.set(installPackageFile)
            application.signPackage.set(true)
            application.installPackage.set(true)
        }
        project.pluginManager.apply("application")

        val installTask =
            project.tasks.named("installWinRTApplicationPackage", InstallWinRTApplicationPackageTask::class.java).get()

        assertEquals(installPackageFile.get().asFile, installTask.packageFile.get().asFile)
        assertEquals(true, installTask.installPackage.get())
        assertTrue(installTask.onlyIf.isSatisfiedBy(installTask))
    }

    @Test
    fun application_packaging_tasks_are_skipped_in_default_unpackaged_mode() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(WinRTExtension::class.java).application {
            it.signPackage.set(true)
            it.installPackage.set(true)
        }

        val packageTask = project.tasks.named("packageWinRTApplication", PackageWinRTApplicationTask::class.java).get()
        val verifyTask = project.tasks.named("verifyWinRTApplicationPackage", VerifyWinRTApplicationPackageTask::class.java).get()
        val signTask = project.tasks.named("signWinRTApplicationPackage", SignWinRTApplicationPackageTask::class.java).get()
        val installTask =
            project.tasks.named("installWinRTApplicationPackage", InstallWinRTApplicationPackageTask::class.java).get()

        assertEquals(WinRTApplicationPackageMode.Unpackaged, project.extensions.getByType(WinRTExtension::class.java).application.packageMode.get())
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
            GenerateWinRTApplicationIdentityTask::class.java,
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
            """{"assemblyName":"AppComponent","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_AppComponent_jar","targetArtifact":"AppComponent.jar","activatableClasses":["sample.AppComponent"],"activatableClassTargets":{"sample.AppComponent":"AppComponent.jar"}}""",
        )
        Files.writeString(appJar, "app-jar")
        Files.writeString(appHostDll, "app-host-dll")
        Files.writeString(dependencyDll, "dependency")
        Files.writeString(dependencyWinmd, "winmd")
        Files.writeString(
            dependencyHostManifest,
            """{"assemblyName":"DependencyComponent","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_DependencyComponent_jar","targetArtifact":"DependencyComponent.jar","activatableClasses":["sample.DependencyComponent"],"activatableClassTargets":{"sample.DependencyComponent":"DependencyComponent.jar"}}""",
        )
        Files.writeString(dependencyJar, "dependency-jar")
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        val dependencyDllContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyDll))
        val dependencyWinmdContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyWinmd))
        val dependencyJarContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyJar))
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"runtimeAssetRecords":[{"fileName":"DependencyComponent.dll","contentBase64":"$dependencyDllContent"}],"authoredMetadataRecords":[{"fileName":"DependencyComponent.winmd","contentBase64":"$dependencyWinmdContent"}],"authoredHostManifestRecords":[{"assemblyName":"DependencyComponent","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_DependencyComponent_jar","targetArtifact":"DependencyComponent.jar","activatableClasses":["sample.DependencyComponent"],"activatableClassTargets":{"sample.DependencyComponent":"DependencyComponent.jar"}}],"authoredTargetArtifactRecords":[{"fileName":"DependencyComponent.jar","contentBase64":"$dependencyJarContent"}]}""",
        )

        val task = project.tasks.register(
            "stageLocalComponentAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(listOf(appDll.toString()))
            registeredTask.runtimeAssetFiles.from(appDll)
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
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
    fun runtime_assets_task_registers_dependency_jvm_authored_classes_in_application_manifest() {
        val project = ProjectBuilder.builder().build()
        val dependencyHostManifest = project.layout.buildDirectory.file("dependency/ui.host.json").get().asFile.toPath()
        val dependencyJar = project.layout.buildDirectory.file("dependency/ui.jar").get().asFile.toPath()
        val dependencyHostDll = project.layout.buildDirectory.file("dependency/ui.dll").get().asFile.toPath()
        Files.createDirectories(dependencyHostManifest.parent)
        Files.writeString(
            dependencyHostManifest,
            """{"assemblyName":"ui","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_ui_jar","targetArtifact":"ui.jar","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{}}""",
        )
        Files.writeString(dependencyJar, "dependency-jar")
        Files.writeString(dependencyHostDll, "dependency-host-dll")
        val dependencyJarContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyJar))
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"authoredHostManifestRecords":[{"assemblyName":"ui","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_ui_jar","targetArtifact":"ui.jar","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{}}],"authoredTargetArtifactRecords":[{"fileName":"ui.jar","contentBase64":"$dependencyJarContent"}]}""",
        )

        val task = project.tasks.register(
            "stageDependencyJvmAuthoredApplicationManifest",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(dependencyHostDll)
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
            registeredTask.executableBaseName.set("sample-app")
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        val manifest = Files.readString(outputRoot.resolve("sample-app.exe.manifest"))
        assertTrue(manifest.contains("<asmv3:file name='ui.dll'"))
        assertTrue(manifest.contains("<winrtv1:activatableClass name='androidx.compose.ui.window.WinUIXamlApplication' threadingModel='both'/>"))
        assertTrue(Files.readString(outputRoot.resolve("ui.runtimeconfig.json")).contains("\"androidx.compose.ui.window.WinUIXamlApplication\": \"ui.jar\""))
    }

    @Test
    fun runtime_assets_task_registers_dependency_native_authored_classes_without_jvm_runtimeconfig() {
        val project = ProjectBuilder.builder().build()
        val dependencyHostManifest = project.layout.buildDirectory.file("dependency/winui-kmp-library.host.json").get().asFile.toPath()
        val dependencyDll = project.layout.buildDirectory.file("dependency/winui_kmp_library.dll").get().asFile.toPath()
        Files.createDirectories(dependencyHostManifest.parent)
        Files.writeString(
            dependencyHostManifest,
            """{"assemblyName":"winui-kmp-library","targetArtifact":"winui_kmp_library.dll","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{"androidx.compose.ui.window.WinUIXamlApplication":"winui_kmp_library.dll"}}""",
        )
        Files.writeString(dependencyDll, "dependency-native-dll")
        val dependencyDllContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyDll))
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"authoredHostManifestRecords":[{"assemblyName":"winui-kmp-library","targetArtifact":"winui_kmp_library.dll","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{"androidx.compose.ui.window.WinUIXamlApplication":"winui_kmp_library.dll"}}],"authoredTargetArtifactRecords":[{"fileName":"winui_kmp_library.dll","contentBase64":"$dependencyDllContent"}]}""",
        )

        val task = project.tasks.register(
            "stageDependencyNativeAuthoredApplicationManifest",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("native-runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
            registeredTask.executableBaseName.set("sample-app")
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        val manifest = Files.readString(outputRoot.resolve("sample-app.exe.manifest"))
        assertTrue(manifest.contains("<asmv3:file name='winui_kmp_library.dll'"))
        assertTrue(manifest.contains("<winrtv1:activatableClass name='androidx.compose.ui.window.WinUIXamlApplication' threadingModel='both'/>"))
        assertFalse(Files.exists(outputRoot.resolve("winui-kmp-library.runtimeconfig.json")))
    }

    @Test
    fun runtime_assets_task_prefers_dependency_jvm_host_registration_over_native_duplicate() {
        val project = ProjectBuilder.builder().build()
        val dependencyJvmManifest = project.layout.buildDirectory.file("dependency/jvm/winui-kmp-library.host.json").get().asFile.toPath()
        val dependencyNativeManifest = project.layout.buildDirectory.file("dependency/native/winui-kmp-library.host.json").get().asFile.toPath()
        val dependencyJar = project.layout.buildDirectory.file("dependency/jvm/winui-kmp-library.jar").get().asFile.toPath()
        val dependencyHostDll = project.layout.buildDirectory.file("dependency/jvm/winui-kmp-library.dll").get().asFile.toPath()
        val dependencyNativeDll = project.layout.buildDirectory.file("dependency/native/winui_kmp_library.dll").get().asFile.toPath()
        Files.createDirectories(dependencyJvmManifest.parent)
        Files.createDirectories(dependencyNativeManifest.parent)
        Files.writeString(
            dependencyJvmManifest,
            """{"assemblyName":"winui-kmp-library","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_winui_kmp_library_jar","targetArtifact":"winui-kmp-library.jar","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{"androidx.compose.ui.window.WinUIXamlApplication":"winui-kmp-library.jar"}}""",
        )
        Files.writeString(
            dependencyNativeManifest,
            """{"assemblyName":"winui-kmp-library","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_winui_kmp_library_dll","targetArtifact":"winui_kmp_library.dll","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{"androidx.compose.ui.window.WinUIXamlApplication":"winui_kmp_library.dll"}}""",
        )
        Files.writeString(dependencyJar, "dependency-jar")
        Files.writeString(dependencyHostDll, "dependency-host-dll")
        Files.writeString(dependencyNativeDll, "dependency-native-dll")
        val dependencyJarContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyJar))
        val dependencyNativeDllContent = Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyNativeDll))
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"authoredHostManifestRecords":[{"assemblyName":"winui-kmp-library","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_winui_kmp_library_jar","targetArtifact":"winui-kmp-library.jar","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{"androidx.compose.ui.window.WinUIXamlApplication":"winui-kmp-library.jar"}},{"assemblyName":"winui-kmp-library","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_winui_kmp_library_dll","targetArtifact":"winui_kmp_library.dll","activatableClasses":["androidx.compose.ui.window.WinUIXamlApplication"],"activatableClassTargets":{"androidx.compose.ui.window.WinUIXamlApplication":"winui_kmp_library.dll"}}],"authoredTargetArtifactRecords":[{"fileName":"winui-kmp-library.jar","contentBase64":"$dependencyJarContent"},{"fileName":"winui_kmp_library.dll","contentBase64":"$dependencyNativeDllContent"}]}""",
        )

        val task = project.tasks.register(
            "stageDependencyMixedAuthoredApplicationManifest",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("mixed-runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(dependencyHostDll)
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
            registeredTask.executableBaseName.set("sample-app")
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        val manifest = Files.readString(outputRoot.resolve("sample-app.exe.manifest"))
        assertTrue(manifest.contains("<asmv3:file name='winui-kmp-library.dll'"))
        assertTrue(manifest.contains("<winrtv1:activatableClass name='androidx.compose.ui.window.WinUIXamlApplication' threadingModel='both'/>"))
        assertTrue(manifest.contains("<asmv3:file name='winui_kmp_library.dll'"))
        assertFalse(Regex("""<asmv3:file name='winui_kmp_library\.dll'[\s\S]*?WinUIXamlApplication[\s\S]*?</asmv3:file>""").containsMatchIn(manifest))
        assertTrue(Files.readString(outputRoot.resolve("winui-kmp-library.runtimeconfig.json")).contains("\"androidx.compose.ui.window.WinUIXamlApplication\": \"winui-kmp-library.jar\""))
    }

    @Test
    fun authoring_host_build_consumes_only_dependency_jvm_host_records() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "authoredHostManifestRecords": [
                {
                  "assemblyName": "winui-kmp-library",
                  "hostExportsClass": "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_winui_kmp_library_jar",
                  "targetArtifact": "winui-kmp-library.jar",
                  "activatableClasses": ["androidx.compose.ui.window.WinUIXamlApplication"],
                  "activatableClassTargets": {"androidx.compose.ui.window.WinUIXamlApplication":"winui-kmp-library.jar"}
                },
                {
                  "assemblyName": "winui-kmp-library",
                  "targetArtifact": "winui_kmp_library.dll",
                  "activatableClasses": ["androidx.compose.ui.window.WinUIXamlApplication"],
                  "activatableClassTargets": {"androidx.compose.ui.window.WinUIXamlApplication":"winui_kmp_library.dll"}
                }
              ]
            }
            """.trimIndent(),
        )
        val task = project.tasks.register(
            "buildDependencyJvmAuthoringHost",
            BuildWinRTAuthoringHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("authoring-host/bin"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("authoring-host/src"))
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.commandWorkingDirectory.set(project.layout.projectDirectory)
        }.get()

        task.build()

        val source = Files.readString(
            task.generatedSourceDirectory.get().asFile.toPath()
                .resolve("winui_kmp_library_kotlin_winrt_authoring_host.c"),
        )
        assertTrue(source.contains("WinRTAuthoringHostExports_winui_kmp_library_jar"))
        assertFalse(source.contains("WinRTAuthoringHostExports_winui_kmp_library_dll"))
    }

    @Test
    fun runtime_assets_task_rejects_malformed_authoring_host_manifests() {
        val project = ProjectBuilder.builder().build()
        val manifest = project.layout.buildDirectory.file("component/AppComponent.host.json").get().asFile.toPath()
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """{"assemblyName":"AppComponent","targetArtifact":"AppComponent.jar","activatableClasses":[],"activatableClassTargets":{}}""",
        )

        val task = project.tasks.register(
            "stageMalformedHostManifestAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(manifest)
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
        }.get()

        val error = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("does not declare any activatable class targets"),
        )
        assertFalse(Files.exists(task.outputDirectory.get().asFile.toPath().resolve("AppComponent.runtimeconfig.json")))
    }

    @Test
    fun runtime_assets_task_rejects_missing_declared_runtime_assets() {
        val project = ProjectBuilder.builder().build()
        val missingDll = project.layout.buildDirectory.file("component/MissingComponent.dll").get().asFile.toPath()

        val task = project.tasks.register(
            "stageMissingRuntimeAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(listOf(missingDll.toString()))
            registeredTask.runtimeAssetFiles.from(missingDll)
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
        }.get()

        val error = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("declared runtime asset file"),
        )
        assertTrue(error.message.orEmpty().contains(missingDll.toString()))
    }

    @Test
    fun runtime_assets_task_stages_dependency_runtime_asset_records_without_producer_paths() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        val dependencyDllContent = Base64.getEncoder().encodeToString("dependency-runtime".toByteArray())
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"runtimeAssets":["D:\\a\\skiko\\skiko\\missing\\DependencyRuntime.dll"],"runtimeAssetRecords":[{"fileName":"DependencyRuntime.dll","contentBase64":"$dependencyDllContent"}]}""",
        )

        val task = project.tasks.register(
            "stageDependencyRuntimeAssetRecords",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        val stagedDll = task.outputDirectory.get().asFile.toPath().resolve("DependencyRuntime.dll")
        assertTrue(Files.isRegularFile(stagedDll))
        assertEquals("dependency-runtime", Files.readString(stagedDll))
    }

    @Test
    fun runtime_assets_task_stages_nuget_packages_from_dependency_identity() {
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("sample.runtime").resolve("1.0.0")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Sample.Runtime.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.Runtime</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(packageRoot.resolve("Sample.Runtime.dll"), "runtime")
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.Runtime@1.0.0"]}""")

        val task = project.tasks.register(
            "stageDependencyWindowsAppSdkAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        assertTrue(Files.isRegularFile(task.outputDirectory.get().asFile.toPath().resolve("Sample.Runtime.dll")))
    }

    @Test
    fun runtime_assets_task_rejects_non_portable_dependency_authored_metadata_record_names() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """{"authoredMetadataRecords":[{"fileName":"dependency/MissingComponent.winmd","contentBase64":"d2lubWQ="}],"authoredHostManifestRecords":[],"authoredTargetArtifactRecords":[]}""",
        )

        val task = project.tasks.register(
            "stageMissingDependencyAuthoredAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.runtimeAssetFiles.from(project.files())
            registeredTask.dependencyRuntimeAssetFiles.from(project.files())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.authoredMetadataFiles.from(project.files())
            registeredTask.authoredHostManifestFiles.from(project.files())
            registeredTask.authoredTargetArtifactFiles.from(project.files())
            registeredTask.authoredHostDllFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity)
            registeredTask.appxManifestFiles.from(project.files())
            registeredTask.projectPriResourceFiles.from(project.files())
            registeredTask.projectPriLayoutFiles.from(project.files())
            registeredTask.projectPriContentFiles.from(project.files())
            registeredTask.projectPriEmbedFiles.from(project.files())
            registeredTask.defaultProjectPriResourceFiles.from(project.files())
            registeredTask.defaultProjectPriLayoutFiles.from(project.files())
            registeredTask.defaultProjectPriContentFiles.from(project.files())
            registeredTask.defaultProjectPriResourceRoot.set(project.layout.buildDirectory.dir("default-pri"))
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
        }.get()

        val error = runCatching { task.stage() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("dependency-authored metadata record"),
        )
        assertTrue(error.message.orEmpty().contains("portable file name"))
    }

    @Test
    fun authoring_host_task_generates_reference_style_native_exports() {
        val project = ProjectBuilder.builder().build()
        val manifest = project.layout.buildDirectory.file("component/SampleComponent.host.json").get().asFile.toPath()
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """{"assemblyName":"SampleComponent","hostExportsClass":"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_SampleComponent_jar","targetArtifact":"SampleComponent.jar","activatableClasses":["sample.Component"],"activatableClassTargets":{"sample.Component":"SampleComponent.jar"}}""",
        )
        val task = project.tasks.register(
            "buildAuthoringHost",
            BuildWinRTAuthoringHostTask::class.java,
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
        val source = Files.readString(sourceRoot.resolve("SampleComponent_kotlin_winrt_authoring_host.c"))
        assertTrue(source.contains("DllGetActivationFactory"))
        assertTrue(source.contains("DllCanUnloadNow"))
        assertTrue(source.contains("JNI_GetCreatedJavaVMs"))
        assertTrue(source.contains("kotlin_winrt_append_classpath_jars(buffer, count, L\"*.jar\", L\"\")"))
        assertTrue(source.contains("kotlin_winrt_append_classpath_jars(buffer, count, L\"lib\\\\*.jar\", L\"lib\\\\\")"))
        assertTrue(source.contains("io/github/composefluent/winrt/projections/support/WinRTAuthoringHostExports_SampleComponent_jar"))
        assertTrue(Files.readString(sourceRoot.resolve("kotlin_winrt_authoring_host.def")).contains("DllGetActivationFactory"))
        if (System.getProperty("os.name").contains("Windows", ignoreCase = true) && commandExists("clang-cl.exe")) {
            assertTrue(Files.isRegularFile(task.outputDirectory.get().asFile.toPath().resolve("SampleComponent.dll")))
        }
    }

    @Test
    fun application_host_task_generates_direct_jni_unpackage_launcher() {
        val project = ProjectBuilder.builder().withName("sample-app").build()
        val jar = project.layout.buildDirectory.file("libs/sample-app.jar").get().asFile.toPath()
        val assets = project.layout.buildDirectory.dir("application-package").get().asFile.toPath()
        Files.createDirectories(jar.parent)
        Files.writeString(jar, "jar")
        Files.createDirectories(assets)
        Files.writeString(assets.resolve("sample-app.exe.manifest"), "manifest")
        val outputRoot = project.layout.buildDirectory.dir("application-layout/jvm").get().asFile.toPath()
        Files.createDirectories(outputRoot.resolve("lib"))
        Files.writeString(outputRoot.resolve("lib/stale-app.jar"), "stale")
        val task = project.tasks.register(
            "buildApplicationHost",
            BuildWinRTApplicationHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("application-layout/jvm"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("application-host/src"))
            registeredTask.mainClass.set("sample.MainKt")
            registeredTask.executableBaseName.set("sample-app")
            registeredTask.runtimeClasspath.from(jar)
            registeredTask.runtimeAssetsDirectory.from(assets)
            registeredTask.packageMode.set(WinRTApplicationPackageMode.Unpackaged.name)
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.commandWorkingDirectory.set(project.layout.projectDirectory)
        }.get()

        withSystemProperty("os.name", "Linux") {
            task.build()
        }

        val source = Files.readString(task.generatedSourceDirectory.get().asFile.toPath().resolve("kotlin_winrt_application_host.c"))
        assertTrue(source.contains("JNI_CreateJavaVM"))
        assertTrue(source.contains("FindClass(env, \"sample/MainKt\")"))
        assertTrue(source.contains("WinRTWindowsAppSdkLauncherSupport"))
        assertTrue(source.contains("initializeApplicationHost\", \"(Z)Ljava/lang/AutoCloseable;\""))
        assertTrue(source.contains("CallStaticObjectMethod(env, support_class, initialize, JNI_TRUE)"))
        assertTrue(source.contains("KOTLIN_WINRT_JVM_OPTIONS"))
        assertTrue(source.contains(System.getProperty("java.home").replace("\\", "\\\\")))
        assertFalse(source.contains("java/lang/reflect"))
        assertTrue(Files.isRegularFile(outputRoot.resolve("lib").resolve(jar.fileName)))
        assertFalse(Files.exists(outputRoot.resolve("lib/stale-app.jar")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("sample-app.exe.manifest")))
    }

    @Test
    fun application_host_task_defaults_to_windows_subsystem_and_can_enable_console_subsystem() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true) || !commandExists("clang-cl.exe")) {
            return
        }
        val project = ProjectBuilder.builder().withName("sample-app").build()
        val jar = project.layout.buildDirectory.file("libs/sample-app.jar").get().asFile.toPath()
        Files.createDirectories(jar.parent)
        Files.writeString(jar, "jar")
        val guiTask = project.tasks.register(
            "buildGuiApplicationHost",
            BuildWinRTApplicationHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("gui-application-host/bin"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("gui-application-host/src"))
            registeredTask.mainClass.set("sample.MainKt")
            registeredTask.executableBaseName.set("sample-gui")
            registeredTask.runtimeClasspath.from(jar)
            registeredTask.packageMode.set(WinRTApplicationPackageMode.Packaged.name)
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.commandWorkingDirectory.set(project.layout.projectDirectory)
        }.get()
        val consoleTask = project.tasks.register(
            "buildConsoleApplicationHost",
            BuildWinRTApplicationHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("console-application-host/bin"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("console-application-host/src"))
            registeredTask.mainClass.set("sample.MainKt")
            registeredTask.executableBaseName.set("sample-console")
            registeredTask.runtimeClasspath.from(jar)
            registeredTask.packageMode.set(WinRTApplicationPackageMode.Packaged.name)
            registeredTask.console.set(true)
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.commandWorkingDirectory.set(project.layout.projectDirectory)
        }.get()

        guiTask.build()
        consoleTask.build()

        assertEquals(
            2,
            readPeSubsystem(guiTask.outputDirectory.get().asFile.toPath().resolve("sample-gui.exe")),
        )
        assertEquals(
            3,
            readPeSubsystem(consoleTask.outputDirectory.get().asFile.toPath().resolve("sample-console.exe")),
        )
    }

    @Test
    fun application_host_task_initializes_runtime_scope_for_packaged_apps_without_unpackaged_deployment() {
        val project = ProjectBuilder.builder().withName("sample-app").build()
        val jar = project.layout.buildDirectory.file("libs/sample-app.jar").get().asFile.toPath()
        Files.createDirectories(jar.parent)
        Files.writeString(jar, "jar")
        val task = project.tasks.register(
            "buildPackagedApplicationHost",
            BuildWinRTApplicationHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("packaged-application-host/bin"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("packaged-application-host/src"))
            registeredTask.mainClass.set("sample.MainKt")
            registeredTask.executableBaseName.set("sample-app")
            registeredTask.runtimeClasspath.from(jar)
            registeredTask.packageMode.set(WinRTApplicationPackageMode.Packaged.name)
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.windowsSdkVersion.set("")
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.commandWorkingDirectory.set(project.layout.projectDirectory)
        }.get()

        withSystemProperty("os.name", "Linux") {
            task.build()
        }

        val source = Files.readString(task.generatedSourceDirectory.get().asFile.toPath().resolve("kotlin_winrt_application_host.c"))
        assertTrue(source.contains("WinRTWindowsAppSdkLauncherSupport"))
        assertTrue(source.contains("initializeApplicationHost\", \"(Z)Ljava/lang/AutoCloseable;\""))
        assertTrue(source.contains("CallStaticObjectMethod(env, support_class, initialize, JNI_FALSE)"))
        assertFalse(source.contains("initializeForUnpackagedApp"))
        assertTrue(source.contains("FindClass(env, \"sample/MainKt\")"))
    }

    @Test
    fun mingw_application_entry_initializes_host_scope_with_package_mode() {
        val project = ProjectBuilder.builder().withName("sample-app").build()
        val unpackagedTask = project.tasks.register(
            "generateUnpackagedMingwEntry",
            GenerateWinRTMingwApplicationEntryTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("generated/unpackaged"))
            registeredTask.legacyOutputDirectories.from(project.files())
            registeredTask.mainClass.set("sample.MainKt")
            registeredTask.packageMode.set(WinRTApplicationPackageMode.Unpackaged.name)
        }.get()
        val packagedTask = project.tasks.register(
            "generatePackagedMingwEntry",
            GenerateWinRTMingwApplicationEntryTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("generated/packaged"))
            registeredTask.legacyOutputDirectories.from(project.files())
            registeredTask.mainClass.set("sample.MainKt")
            registeredTask.packageMode.set(WinRTApplicationPackageMode.Packaged.name)
        }.get()

        unpackagedTask.generate()
        packagedTask.generate()

        val relativeSource = Path.of("io/github/composefluent/winrt/application/WinRTMingwApplicationEntry.kt")
        val unpackagedSource = Files.readString(unpackagedTask.outputDirectory.get().asFile.toPath().resolve(relativeSource))
        val packagedSource = Files.readString(packagedTask.outputDirectory.get().asFile.toPath().resolve(relativeSource))
        assertTrue(unpackagedSource.contains("WinRTWindowsAppSdkBootstrap.initializeApplicationHost(unpackaged = true).use"))
        assertTrue(packagedSource.contains("WinRTWindowsAppSdkBootstrap.initializeApplicationHost(unpackaged = false).use"))
        assertFalse(unpackagedSource.contains("WinRTWindowsAppSdkBootstrap.initialize()"))
        assertFalse(packagedSource.contains("WinRTWindowsAppSdkBootstrap.initialize()"))
    }

    @Test
    fun authoring_host_task_rejects_malformed_host_manifests() {
        val project = ProjectBuilder.builder().build()
        val manifest = project.layout.buildDirectory.file("component/SampleComponent.host.json").get().asFile.toPath()
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """{"assemblyName":"SampleComponent","targetArtifact":"SampleComponent.jar","activatableClasses":[],"activatableClassTargets":{}}""",
        )
        val task = project.tasks.register(
            "buildMalformedAuthoringHost",
            BuildWinRTAuthoringHostTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("authoring-host/bin"))
            registeredTask.generatedSourceDirectory.set(project.layout.buildDirectory.dir("authoring-host/src"))
            registeredTask.authoredHostManifestFiles.from(manifest)
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.javaHome.set(System.getProperty("java.home"))
            registeredTask.runtimeIdentifier.set("win-x64")
        }.get()

        val error = runCatching { task.build() }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(
            error!!.message.orEmpty(),
            error.message.orEmpty().contains("does not declare any activatable classes"),
        )
        assertFalse(Files.exists(task.generatedSourceDirectory.get().asFile.toPath().resolve("SampleComponent_kotlin_winrt_authoring_host.c")))
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
            StageWinRTRuntimeAssetsTask::class.java,
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
        val applicationManifest = Files.readString(outputRoot.resolve("app.exe.manifest"))
        assertTrue(
            applicationManifest.contains(
                "<dpiAware xmlns='http://schemas.microsoft.com/SMI/2005/WindowsSettings'>true/PM</dpiAware>",
            ),
        )
        assertTrue(
            applicationManifest.contains(
                "<dpiAwareness xmlns='http://schemas.microsoft.com/SMI/2016/WindowsSettings'>PerMonitorV2, PerMonitor</dpiAwareness>",
            ),
        )
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
    fun runtime_assets_task_stages_msbuild_reference_copy_local_paths() {
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("nuget/sample.webview/1.0.0").get().asFile.toPath()
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.createDirectories(packageRoot.resolve("runtimes/win-x64/native_uap"))
        Files.writeString(
            packageRoot.resolve("Sample.WebView.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.WebView</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(packageRoot.resolve("runtimes/win-x64/native_uap/Sample.WebView.Core.dll"), "webview")
        Files.writeString(
            packageRoot.resolve("build/native/Sample.WebView.targets"),
            """
            <Project xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <PropertyGroup>
                <EffectivePlatform>$(Platform)</EffectivePlatform>
                <EffectivePlatform Condition="'$(EffectivePlatform)' == 'Win32'">x86</EffectivePlatform>
              </PropertyGroup>
              <ItemGroup>
                <ReferenceCopyLocalPaths Include="$(MSBuildThisFileDirectory)..\..\runtimes\win-$(EffectivePlatform)\native_uap\Sample.WebView.Core.dll" />
              </ItemGroup>
            </Project>
            """.trimIndent(),
        )
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/sample-webview.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.WebView@1.0.0"]}""")

        val task = project.tasks.register(
            "stageMsBuildReferenceCopyLocalPaths",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-webview"))
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
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Sample.WebView.Core.dll")))
    }

    @Test
    fun runtime_assets_task_expands_msbuild_wildcard_imports() {
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("nuget/sample.wildcard.import/1.0.0").get().asFile.toPath()
        Files.createDirectories(packageRoot.resolve("build/native/ImportBefore"))
        Files.createDirectories(packageRoot.resolve("runtimes/win-x64/native_uap"))
        Files.writeString(
            packageRoot.resolve("Sample.Wildcard.Import.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.Wildcard.Import</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(packageRoot.resolve("runtimes/win-x64/native_uap/Sample.Wildcard.Import.dll"), "wildcard")
        Files.writeString(
            packageRoot.resolve("build/native/ImportBefore/00-platform.props"),
            """
            <Project xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <PropertyGroup>
                <EffectivePlatform>$(Platform)</EffectivePlatform>
              </PropertyGroup>
            </Project>
            """.trimIndent(),
        )
        Files.writeString(
            packageRoot.resolve("build/native/Sample.Wildcard.Import.targets"),
            """
            <Project xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <Import Project="ImportBefore\*" />
              <ItemGroup>
                <ReferenceCopyLocalPaths Include="$(MSBuildThisFileDirectory)..\..\runtimes\win-$(EffectivePlatform)\native_uap\Sample.Wildcard.Import.dll" />
              </ItemGroup>
            </Project>
            """.trimIndent(),
        )
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/sample-wildcard-import.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.Wildcard.Import@1.0.0"]}""")

        val task = project.tasks.register(
            "stageMsBuildWildcardImport",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-wildcard-import"))
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
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Sample.Wildcard.Import.dll")))
    }

    @Test
    fun runtime_assets_task_ignores_unresolved_msbuild_functions_for_unhandled_items() {
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("nuget/sample.msbuild.function/1.0.0").get().asFile.toPath()
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.createDirectories(packageRoot.resolve("runtimes/win-x64/native_uap"))
        Files.writeString(
            packageRoot.resolve("Sample.MsBuild.Function.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.MsBuild.Function</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(packageRoot.resolve("runtimes/win-x64/native_uap/Sample.MsBuild.Function.dll"), "function")
        Files.writeString(
            packageRoot.resolve("build/native/Sample.MsBuild.Function.targets"),
            """
            <Project xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <PropertyGroup>
                <WindowsSdkPath>$([Microsoft.Build.Utilities.ToolLocationHelper]::GetPlatformSdkLocation("Windows", "10.0"))</WindowsSdkPath>
                <FacadeWinmdPath>$(WindowsSdkPath)UnionMetadata\facade\Windows.winmd</FacadeWinmdPath>
              </PropertyGroup>
              <ItemGroup>
                <ReferencePath Include="$(FacadeWinmdPath)" Condition="Exists('$(FacadeWinmdPath)')" />
                <ReferenceCopyLocalPaths Include="$(MSBuildThisFileDirectory)..\..\runtimes\win-$(Platform)\native_uap\Sample.MsBuild.Function.dll" />
                <ReferenceCopyLocalPaths Include="$(FacadeWinmdPath)" />
              </ItemGroup>
            </Project>
            """.trimIndent(),
        )
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/sample-msbuild-function.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.MsBuild.Function@1.0.0"]}""")

        val task = project.tasks.register(
            "stageMsBuildFunction",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-msbuild-function"))
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
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Sample.MsBuild.Function.dll")))
        assertFalse(Files.exists(outputRoot.resolve("Windows.winmd")))
    }

    @Test
    fun runtime_assets_task_ignores_unresolved_msbuild_function_imports() {
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("nuget/sample.msbuild.import.function/1.0.0").get().asFile.toPath()
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.createDirectories(packageRoot.resolve("runtimes/win-x64/native_uap"))
        Files.writeString(
            packageRoot.resolve("Sample.MsBuild.Import.Function.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.MsBuild.Import.Function</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(
            packageRoot.resolve("runtimes/win-x64/native_uap/Sample.MsBuild.Import.Function.dll"),
            "import-function",
        )
        Files.writeString(
            packageRoot.resolve("build/native/Sample.MsBuild.Import.Function.targets"),
            """
            <Project xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <Import Project="$([MSBuild]::GetPathOfFileAbove('Directory.Build.targets', '$(MSBuildThisFileDirectory)..\'))" />
              <ItemGroup>
                <ReferenceCopyLocalPaths Include="$(MSBuildThisFileDirectory)..\..\runtimes\win-$(Platform)\native_uap\Sample.MsBuild.Import.Function.dll" />
              </ItemGroup>
            </Project>
            """.trimIndent(),
        )
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/sample-msbuild-import-function.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.MsBuild.Import.Function@1.0.0"]}""")

        val task = project.tasks.register(
            "stageMsBuildImportFunction",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-msbuild-import-function"))
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
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Sample.MsBuild.Import.Function.dll")))
    }

    @Test
    fun runtime_assets_task_preserves_msbuild_copy_local_resource_target_paths() {
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("nuget/sample.resources/1.0.0").get().asFile.toPath()
        Files.createDirectories(packageRoot.resolve("build/native"))
        Files.createDirectories(packageRoot.resolve("runtimes/win-x64/native/Assets/en-US"))
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
        Files.writeString(packageRoot.resolve("runtimes/win-x64/native/Assets/en-US/icon.png"), "png")
        Files.writeString(
            packageRoot.resolve("build/native/Sample.Resources.targets"),
            """
            <Project xmlns="http://schemas.microsoft.com/developer/msbuild/2003">
              <PropertyGroup>
                <_SampleAssetsRoot>$(MSBuildThisFileDirectory)..\..\runtimes\win-$(Platform)\native\Assets\</_SampleAssetsRoot>
              </PropertyGroup>
              <ItemGroup>
                <ReferenceCopyLocalPaths Include="$(_SampleAssetsRoot)**\*.*" DestinationSubDirectory="Microsoft.UI.Xaml\Assets\%(RecursiveDir)" />
              </ItemGroup>
            </Project>
            """.trimIndent(),
        )
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/sample-resources.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.Resources@1.0.0"]}""")

        val task = project.tasks.register(
            "stageMsBuildResourceCopyLocalPaths",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-resources"))
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
            registeredTask.generateProjectPri.set(false)
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("Microsoft.UI.Xaml/Assets/en-US/icon.png")))
    }

    @Test
    fun runtime_assets_task_writes_concrete_application_manifest_processor_architecture() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register(
            "stageArchitectureManifest",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-architecture"))
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(project.files())
            registeredTask.resolvedNuGetPackageManifestFiles.from(project.files())
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.dependencyIdentityFiles.from(project.files())
            registeredTask.generateProjectPri.set(false)
            registeredTask.executableBaseName.set("sample-app")
        }.get()

        task.stage()

        val manifest = Files.readString(task.outputDirectory.get().asFile.toPath().resolve("sample-app.exe.manifest"))
        assertTrue(manifest.contains("processorArchitecture='amd64'"))
        assertFalse(manifest.contains("processorArchitecture='*'"))
    }

    @Test
    fun runtime_assets_task_deduplicates_lifted_activatable_classes_in_application_manifest() {
        val project = ProjectBuilder.builder().build()
        val packageRoot = project.layout.buildDirectory.dir("nuget/sample.duplicate/1.0.0").get().asFile.toPath()
        Files.createDirectories(packageRoot.resolve("build/native/first"))
        Files.createDirectories(packageRoot.resolve("build/native/second"))
        Files.writeString(
            packageRoot.resolve("Sample.Duplicate.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.Duplicate</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        Files.writeString(packageRoot.resolve("First.dll"), "first")
        Files.writeString(packageRoot.resolve("Second.dll"), "second")
        Files.writeString(
            packageRoot.resolve("build/native/first/LiftedWinRTClassRegistrations.xml"),
            """
            <Registrations xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Extension Category="windows.activatableClass.inProcessServer">
                <InProcessServer>
                  <Path>First.dll</Path>
                  <ActivatableClass ActivatableClassId="Sample.Duplicate.Widget" ThreadingModel="both" />
                </InProcessServer>
              </Extension>
            </Registrations>
            """.trimIndent(),
        )
        Files.writeString(
            packageRoot.resolve("build/native/second/LiftedWinRTClassRegistrations.xml"),
            """
            <Registrations xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10">
              <Extension Category="windows.activatableClass.inProcessServer">
                <InProcessServer>
                  <Path>Second.dll</Path>
                  <ActivatableClass ActivatableClassId="Sample.Duplicate.Widget" ThreadingModel="both" />
                </InProcessServer>
              </Extension>
            </Registrations>
            """.trimIndent(),
        )
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/sample-duplicate.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(dependencyIdentity.toPath(), """{"nugetPackages":["Sample.Duplicate@1.0.0"]}""")

        val task = project.tasks.register(
            "stageDuplicateLiftedRegistrations",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("runtime-assets-duplicate"))
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
            registeredTask.generateProjectPri.set(false)
            registeredTask.executableBaseName.set("sample-app")
        }.get()

        task.stage()

        val manifest = Files.readString(task.outputDirectory.get().asFile.toPath().resolve("sample-app.exe.manifest"))
        assertEquals(1, Regex("""<winrtv1:activatableClass name='Sample\.Duplicate\.Widget'""").findAll(manifest).count())
    }

    @Test
    fun runtime_assets_task_stages_lib_native_release_assets_for_cpp_winrt_packages() {
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("sample.cppwinrt").resolve("1.0.0")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Sample.CppWinRT.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.CppWinRT</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        val nativeRoot = packageRoot.resolve("lib/native/Release/x64")
        Files.createDirectories(nativeRoot.resolve("WinUI3Package"))
        Files.writeString(nativeRoot.resolve("WinUI3Package.dll"), "dll")
        Files.writeString(nativeRoot.resolve("WinUI3Package.winmd"), "winmd")
        Files.writeString(nativeRoot.resolve("WinUI3Package.pri"), "pri")
        Files.writeString(nativeRoot.resolve("WinUI3Package/SettingsCard_Resource.xaml"), "xaml")
        Files.writeString(nativeRoot.resolve("WinUI3Package/Shimmer_Resource.xaml"), "xaml")

        val task = project.tasks.register(
            "stageCppWinRTNativeAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("cppwinrt-assets"))
            registeredTask.nugetPackages.set(listOf("Sample.CppWinRT@1.0.0"))
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        val outputRoot = task.outputDirectory.get().asFile.toPath()
        assertTrue(Files.isRegularFile(outputRoot.resolve("WinUI3Package.dll")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("WinUI3Package.winmd")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("WinUI3Package.pri")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("WinUI3Package/SettingsCard_Resource.xaml")))
        assertTrue(Files.isRegularFile(outputRoot.resolve("WinUI3Package/Shimmer_Resource.xaml")))
    }

    @Test
    fun runtime_assets_task_discovers_xaml_metadata_providers_from_cpp_winui_package_winmd() {
        val project = ProjectBuilder.builder().build()
        val globalPackagesRoot = project.layout.buildDirectory.dir("nuget").get().asFile.toPath()
        val packageRoot = globalPackagesRoot.resolve("sample.cppwinui").resolve("1.0.0")
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("Sample.CppWinUI.nuspec"),
            """
            <package>
              <metadata>
                <id>Sample.CppWinUI</id>
                <version>1.0.0</version>
              </metadata>
            </package>
            """.trimIndent(),
        )
        val nativeRoot = packageRoot.resolve("lib/native/Release/x64")
        Files.createDirectories(nativeRoot)
        Files.writeString(nativeRoot.resolve("WinUI3Package.dll"), "dll")
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = "WinUI3Package",
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "WinUI3Package.XamlMetaDataProvider",
                    interfaceNames = listOf("Microsoft.UI.Xaml.Markup.IXamlMetadataProvider"),
                ),
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "WinUI3Package.Shimmer",
                    interfaceNames = listOf("WinUI3Package.IShimmer"),
                ),
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "WinUI3Package.Shimmer_Resource",
                    baseRuntimeClassName = "Microsoft.UI.Xaml.ResourceDictionary",
                    interfaceNames = listOf("Microsoft.UI.Xaml.IResourceDictionary"),
                ),
            ),
            outputFile = nativeRoot.resolve("WinUI3Package.winmd"),
        )

        val task = project.tasks.register(
            "stageCppWinUiNativeAssets",
            StageWinRTRuntimeAssetsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("cppwinui-assets"))
            registeredTask.nugetPackages.set(listOf("Sample.CppWinUI@1.0.0"))
            registeredTask.runtimeAssets.set(emptyList())
            registeredTask.nugetPackageContentFiles.from(packageRoot)
            registeredTask.nugetGlobalPackagesRoots.set(listOf(globalPackagesRoot.toString()))
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.runtimeIdentifier.set("win-x64")
            registeredTask.generateProjectPri.set(false)
            registeredTask.dependencyIdentityFiles.from(project.files())
        }.get()

        task.stage()

        val providers = Files.readAllLines(
            task.outputDirectory.get().asFile.toPath().resolve(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName),
        )
        assertEquals(listOf("WinUI3Package.XamlMetaDataProvider"), providers)
        val registration = Files.readString(
            task.outputDirectory.get().asFile.toPath()
                .resolve("registrations/generated/WinUI3Package/LiftedWinRTClassRegistrations.xml"),
        )
        assertTrue(registration.contains("<Path>WinUI3Package.dll</Path>"))
        assertTrue(registration.contains("""ActivatableClassId="WinUI3Package.XamlMetaDataProvider""""))
        assertTrue(registration.contains("""ActivatableClassId="WinUI3Package.Shimmer""""))
        assertTrue(registration.contains("""ActivatableClassId="WinUI3Package.Shimmer_Resource""""))
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
        val makePriArgs = readFakeToolArguments(makePriLog)
        assertFalse(makePriArgs.contains("createconfig"))
        assertTrue(makePriArgs.contains("new"))
        assertArgumentPair(makePriArgs, "/in", "Contoso.App")
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
            StageWinRTRuntimeAssetsTask::class.java,
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

        val makePriArgs = readFakeToolArguments(makePriLog)
        assertArgumentPair(makePriArgs, "/in", "Contoso.ManifestIdentity")
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
        val makeAppxArgs = readFakeToolArguments(makeAppxLog)
        assertTrue(makeAppxArgs.contains("pack"))
        assertTrue(makeAppxArgs.contains("/d"))
        assertTrue(makeAppxArgs.any { it.endsWith("staged-appx") })
        assertTrue(makeAppxArgs.contains("/p"))
        assertTrue(makeAppxArgs.any { it.endsWith("Contoso.msix") })
        assertTrue(makeAppxArgs.contains("/o"))
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            PackageWinRTApplicationTask::class.java,
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
            VerifyWinRTApplicationPackageTask::class.java,
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
        val makeAppxArgs = readFakeToolArguments(makeAppxLog)
        assertTrue(makeAppxArgs.contains("unpack"))
        assertTrue(makeAppxArgs.contains("/p"))
        assertTrue(makeAppxArgs.any { it.endsWith("Contoso.msix") })
        assertTrue(makeAppxArgs.contains("/d"))
        assertTrue(makeAppxArgs.any { it.endsWith("verify-unpack") })
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
            VerifyWinRTApplicationPackageTask::class.java,
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
            VerifyWinRTApplicationPackageTask::class.java,
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
            VerifyWinRTApplicationPackageTask::class.java,
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
    fun verify_application_package_task_fails_when_package_is_missing() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/MissingVerify.msix").get().asFile.toPath()
        val markerFile = project.layout.buildDirectory.file("packages/MissingVerify.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-missing").get().asFile.toPath()
        val makeAppxLog = project.layout.buildDirectory.file("makeappx-verify-missing.log").get().asFile.toPath()
        val makeAppx = writeFakeMakeAppx(
            project.layout.buildDirectory.file("fake-makeappx-verify-missing.cmd").get().asFile.toPath(),
            makeAppxLog,
        )
        val task = project.tasks.register(
            "verifyMissingApplicationPackage",
            VerifyWinRTApplicationPackageTask::class.java,
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
        assertTrue(failure?.message.orEmpty().contains("package file does not exist"))
        assertFalse(Files.exists(markerFile))
        assertFalse(Files.exists(unpackRoot))
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
            VerifyWinRTApplicationPackageTask::class.java,
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
            VerifyWinRTApplicationPackageTask::class.java,
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
    fun verify_application_package_task_fails_when_makeappx_unpack_returns_error() {
        if (!System.getProperty("os.name").contains("Windows", ignoreCase = true)) {
            return
        }
        val project = ProjectBuilder.builder().build()
        val packageFile = project.layout.buildDirectory.file("packages/Malformed.msix").get().asFile.toPath()
        Files.createDirectories(packageFile.parent)
        Files.writeString(packageFile, "malformed-msix")
        val makeAppx = writeFailingFakeMakeAppx(
            project.layout.buildDirectory.file("fake-failing-makeappx-verify.cmd").get().asFile.toPath(),
        )
        val markerFile = project.layout.buildDirectory.file("packages/Malformed.verify.marker").get().asFile.toPath()
        val unpackRoot = project.layout.buildDirectory.dir("verify-unpack-malformed").get().asFile.toPath()
        val task = project.tasks.register(
            "verifyMalformedApplicationPackage",
            VerifyWinRTApplicationPackageTask::class.java,
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
            VerifyWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
        val signToolArgs = readFakeToolArguments(signToolLog)
        assertTrue(signToolArgs.contains("sign"))
        assertArgumentPair(signToolArgs, "/fd", "SHA256")
        assertArgumentPair(signToolArgs, "/tr", "http://timestamp.example.test")
        assertArgumentPair(signToolArgs, "/td", "SHA256")
        assertArgumentPair(signToolArgs, "/sha1", "ABCDEF123456")
        assertTrue(signToolArgs.any { it.endsWith("Contoso-signed.msix") })
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
            SignWinRTApplicationPackageTask::class.java,
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
        val signToolArgs = readFakeToolArguments(signToolLog)
        assertTrue(signToolArgs.contains("/f"))
        assertTrue(signToolArgs.any { it.endsWith("test-signing.pfx") })
        assertArgumentPair(signToolArgs, "/p", "secret")
        assertFalse(signToolArgs.contains("/sha1"))
        assertFalse(signToolArgs.contains("ABCDEF123456"))
        assertFalse(signToolArgs.contains("/a"))
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            SignWinRTApplicationPackageTask::class.java,
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
            InstallWinRTApplicationPackageTask::class.java,
        ) { registeredTask ->
            registeredTask.packageFile.set(project.layout.file(project.provider { packageFile.toFile() }))
            registeredTask.installPackage.set(true)
            registeredTask.powerShellExecutable.set(powershell.toString())
            registeredTask.forceApplicationShutdown.set(true)
        }.get()

        task.install()

        val powershellArgs = readFakeToolArguments(powershellLog)
        assertTrue(powershellArgs.contains("-NoLogo"))
        assertTrue(powershellArgs.contains("-NoProfile"))
        assertTrue(powershellArgs.contains("-NonInteractive"))
        val command = powershellArgs.getOrNull(powershellArgs.indexOf("-Command") + 1).orEmpty().replace("\\", "/")
        assertTrue(command.contains("Add-AppxPackage"))
        assertTrue(command.contains("Contoso.msix"))
        assertTrue(command.contains("-ForceApplicationShutdown"))
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
            InstallWinRTApplicationPackageTask::class.java,
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
            InstallWinRTApplicationPackageTask::class.java,
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
            InstallWinRTApplicationPackageTask::class.java,
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
            InstallWinRTApplicationPackageTask::class.java,
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
            InstallWinRTApplicationPackageTask::class.java,
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
            StageWinRTApplicationPackageTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
            StageWinRTRuntimeAssetsTask::class.java,
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
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvmToolchain(25)
            }

            dependencies {
                implementation files("$runtimeJar")
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("jar", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateCompileKotlinWinRTAuthoredCandidates")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "build/generated/kotlin-winrt/src/jvmMain/kotlin/windows/foundation/windows_foundation.kt",
                ),
            ),
        )
        val winmd = projectDir.resolve(
            "build/generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring/kotlin-winrt-plugin-test.winmd",
        )
        assertTrue(Files.isRegularFile(winmd))
        assertTrue(WinRTMetadataLoader.load(winmd).namespaces.isEmpty())
        assertTrue(
            Files.isRegularFile(
                projectDir.resolve(
                    "build/generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring/kotlin-winrt-plugin-test.host.json",
                ),
            ),
        )
        JarFile(projectDir.resolve("build/libs/kotlin-winrt-plugin-test.jar").toFile()).use { jar ->
            assertTrue(
                jar.getEntry(
                    "windows/foundation/FoundationContract.class",
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
            .withArguments("generateWinRTProjections", "--stacktrace")
            .forwardOutput()
            .build()

        assertTrue(
            secondResult.task(":generateWinRTProjections")?.outcome in
                setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE),
        )
    }

    @Test
    fun plugin_adds_kmp_generated_sources_to_winui_main_when_non_winui_targets_exist() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-generated-common-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-kmp-generated-common-test"
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
        Files.createDirectories(projectDir.resolve("src/winuiMain/kotlin/sample"))
        writeGradleFile(
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                linuxX64()
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            winRT {
                application {
                    mainClass.set "sample.MainKt"
                }
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }

            tasks.named("generateWinRTProjections") {
                sourceRoots.setFrom project.file("src/winuiMain/kotlin")
            }

            tasks.register("verifyGeneratedSourceSetOwnership") {
                dependsOn("generateWinRTProjections")
                doLast {
                    def normalizedSourcePaths = { sourceSetName ->
                        kotlin.sourceSets.named(sourceSetName).get().kotlin.srcDirs
                            .collect { it.toPath().toAbsolutePath().normalize().toString().replace("\\", "/") }
                            .toSet()
                    }

                    def buildRoot = layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize()
                    def generatedProjection = buildRoot.resolve("generated/kotlin-winrt/src/winuiMain/kotlin")
                        .toString()
                        .replace("\\", "/")
                    def legacyGeneratedProjection = buildRoot.resolve("generated/kotlin-winrt/src/main/kotlin")
                    def generatedCompilerSupport = buildRoot.resolve("generated/kotlin-winrt/compiler-support/merged")
                        .toString()
                        .replace("\\", "/")
                    def generatedAuthoring = buildRoot.resolve("generated/kotlin-winrt-authoring/src/winuiMain/kotlin")
                        .toString()
                        .replace("\\", "/")
                    def legacyGeneratedAuthoring = buildRoot.resolve("generated/kotlin-winrt-authoring/src/main/kotlin")
                    def generatedHostExports = buildRoot.resolve("generated/kotlin-winrt-native-authoring-host")
                    def generatedApplicationEntry = buildRoot.resolve("generated/kotlin-winrt-application-entry/src/mingwX64Main/kotlin")
                        .toString()
                        .replace("\\", "/")
                    def legacyGeneratedApplicationEntry = buildRoot.resolve("generated/kotlin-winrt-application-entry/src/commonMain/kotlin")
                    def commonSources = normalizedSourcePaths("commonMain")
                    def winuiSources = normalizedSourcePaths("winuiMain")
                    def winuiJvmMain = kotlin.sourceSets.named("winuiJvmMain").get()
                    def mingwX64Main = kotlin.sourceSets.named("mingwX64Main").get()
                    def linuxX64Main = kotlin.sourceSets.named("linuxX64Main").get()
                    def mingwSources = normalizedSourcePaths("mingwX64Main")
                    println("COMMON_MAIN_SOURCES=" + commonSources)
                    println("WINUI_MAIN_SOURCES=" + winuiSources)
                    println("MINGW_X64_MAIN_SOURCES=" + mingwSources)
                    if (commonSources.contains(generatedProjection)) {
                        throw new GradleException("Generated projection source must not be owned by commonMain: " + commonSources)
                    }
                    if (legacyGeneratedProjection.toFile().exists()) {
                        throw new GradleException("KMP generated projection source must not remain under src/main/kotlin: " +
                            legacyGeneratedProjection.toString().replace("\\", "/"))
                    }
                    if (commonSources.contains(generatedCompilerSupport)) {
                        throw new GradleException("Generated compiler support source must not be owned by commonMain: " + commonSources)
                    }
                    if (commonSources.contains(generatedAuthoring)) {
                        throw new GradleException("Generated authoring support source must not be owned by commonMain: " + commonSources)
                    }
                    if (legacyGeneratedAuthoring.toFile().exists()) {
                        throw new GradleException("KMP generated authoring support source must not remain under src/main/kotlin: " +
                            legacyGeneratedAuthoring.toString().replace("\\", "/"))
                    }
                    if (commonSources.contains(generatedApplicationEntry)) {
                        throw new GradleException("Generated application entry source must not be owned by commonMain: " + commonSources)
                    }
                    if (legacyGeneratedApplicationEntry.toFile().exists()) {
                        throw new GradleException("KMP generated application entry source must not remain under src/commonMain/kotlin: " +
                            legacyGeneratedApplicationEntry.toString().replace("\\", "/"))
                    }
                    if (!winuiSources.contains(generatedProjection)) {
                        throw new GradleException("Generated projection source must be scoped to winuiMain: " + winuiSources)
                    }
                    if (!winuiSources.contains(generatedCompilerSupport)) {
                        throw new GradleException("Generated compiler support source must be scoped to winuiMain: " + winuiSources)
                    }
                    if (!winuiSources.contains(generatedAuthoring)) {
                        throw new GradleException("Generated authoring support source must be scoped to winuiMain: " + winuiSources)
                    }
                    if (!winuiJvmMain.dependsOn.contains(kotlin.sourceSets.named("winuiMain").get())) {
                        throw new GradleException("winuiJvmMain must depend on winuiMain through the plugin WinUI source-set edge.")
                    }
                    if (!mingwX64Main.dependsOn.contains(kotlin.sourceSets.named("winuiMain").get())) {
                        throw new GradleException("mingwX64Main must depend on winuiMain through the plugin WinUI source-set edge.")
                    }
                    if (linuxX64Main.dependsOn.contains(kotlin.sourceSets.named("winuiMain").get())) {
                        throw new GradleException("linuxX64Main must not depend on winuiMain.")
                    }
                    if (generatedHostExports.toFile().exists()) {
                        throw new GradleException("Native authoring host exports must not be generated as target-local Kotlin source: " +
                            generatedHostExports.toString().replace("\\", "/"))
                    }
                    if (mingwSources.contains(generatedHostExports.resolve("src/main/kotlin").toString().replace("\\", "/"))) {
                        throw new GradleException("Generated native authoring host source must not be scoped to mingwX64Main: " + mingwSources)
                    }
                    if (!mingwSources.contains(generatedApplicationEntry)) {
                        throw new GradleException("Generated application entry source must be scoped to mingwX64Main: " + mingwSources)
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyGeneratedSourceSetOwnership", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyGeneratedSourceSetOwnership")?.outcome)
    }

    @Test
    fun plugin_exposes_winui_main_kotlin_dsl_source_set_accessor() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-winui-source-set-accessor-test-")
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
            rootProject.name = "kotlin-winrt-kmp-winui-source-set-accessor-test"
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
                kotlin("multiplatform") version "2.3.20"
                id("io.github.compose-fluent.winrt")
            }

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                linuxX64()
                sourceSets {
                    winuiMain {
                        kotlin.srcDir("src/winuiMain/kotlin")
                    }
                }
            }

            tasks.register("verifyWinuiMainKotlinDslAccessor") {
                doLast {
                    val sourceSet = kotlin.sourceSets.named("winuiMain").get()
                    check(sourceSet.kotlin.srcDirs.any { it.path.replace("\\", "/").endsWith("src/winuiMain/kotlin") }) {
                        "Expected the Kotlin DSL winuiMain accessor to configure src/winuiMain/kotlin: ${'$'}{sourceSet.kotlin.srcDirs}"
                    }
                    check(kotlin.sourceSets.named("winuiJvmMain").get().dependsOn.contains(sourceSet)) {
                        "winuiJvmMain must depend on winuiMain through the plugin WinUI source-set edge."
                    }
                    check(kotlin.sourceSets.named("mingwX64Main").get().dependsOn.contains(sourceSet)) {
                        "mingwX64Main must depend on winuiMain through the plugin WinUI source-set edge."
                    }
                    check(!kotlin.sourceSets.named("linuxX64Main").get().dependsOn.contains(sourceSet)) {
                        "linuxX64Main must not depend on winuiMain."
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyWinuiMainKotlinDslAccessor", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyWinuiMainKotlinDslAccessor")?.outcome)
    }

    @Test
    fun plugin_exposes_winui_test_kotlin_dsl_source_set_accessor() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-winui-test-source-set-accessor-test-")
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
            rootProject.name = "kotlin-winrt-kmp-winui-test-source-set-accessor-test"
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
                kotlin("multiplatform") version "2.3.20"
                id("io.github.compose-fluent.winrt")
            }

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                linuxX64()
                sourceSets {
                    winuiTest {
                        kotlin.srcDir("src/winuiTest/kotlin")
                    }
                }
            }

            tasks.register("verifyWinuiTestKotlinDslAccessor") {
                doLast {
                    val sourceSet = kotlin.sourceSets.named("winuiTest").get()
                    check(sourceSet.kotlin.srcDirs.any { it.path.replace("\\", "/").endsWith("src/winuiTest/kotlin") }) {
                        "Expected the Kotlin DSL winuiTest accessor to configure src/winuiTest/kotlin: ${'$'}{sourceSet.kotlin.srcDirs}"
                    }
                    check(kotlin.sourceSets.named("winuiJvmTest").get().dependsOn.contains(sourceSet)) {
                        "winuiJvmTest must depend on winuiTest through the plugin WinUI source-set edge."
                    }
                    check(kotlin.sourceSets.named("mingwX64Test").get().dependsOn.contains(sourceSet)) {
                        "mingwX64Test must depend on winuiTest through the plugin WinUI source-set edge."
                    }
                    check(!kotlin.sourceSets.named("linuxX64Test").get().dependsOn.contains(sourceSet)) {
                        "linuxX64Test must not depend on winuiTest."
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyWinuiTestKotlinDslAccessor", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyWinuiTestKotlinDslAccessor")?.outcome)
    }

    @Test
    fun plugin_preserves_user_hierarchy_when_exposing_winui_main_accessor() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-user-hierarchy-test-")
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
            rootProject.name = "kotlin-winrt-kmp-user-hierarchy-test"
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
            @file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

            plugins {
                kotlin("multiplatform") version "2.3.20"
                id("io.github.compose-fluent.winrt")
            }

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                linuxX64()
                applyHierarchyTemplate {
                    common {
                        group("posix") {
                            withLinux()
                        }
                    }
                }
                sourceSets {
                    winuiMain {
                        kotlin.srcDir("src/winuiMain/kotlin")
                    }
                }
            }

            tasks.register("verifyUserHierarchy") {
                doLast {
                    val winuiMain = kotlin.sourceSets.named("winuiMain").get()
                    val posixMain = kotlin.sourceSets.named("posixMain").get()
                    val winuiTest = kotlin.sourceSets.named("winuiTest").get()
                    val posixTest = kotlin.sourceSets.named("posixTest").get()
                    val winuiJvmMain = kotlin.sourceSets.named("winuiJvmMain").get()
                    val mingwX64Main = kotlin.sourceSets.named("mingwX64Main").get()
                    val linuxX64Main = kotlin.sourceSets.named("linuxX64Main").get()
                    val winuiJvmTest = kotlin.sourceSets.named("winuiJvmTest").get()
                    val mingwX64Test = kotlin.sourceSets.named("mingwX64Test").get()
                    val linuxX64Test = kotlin.sourceSets.named("linuxX64Test").get()
                    check(winuiJvmMain.dependsOn.contains(winuiMain)) {
                        "winuiJvmMain must keep the plugin WinUI source-set edge."
                    }
                    check(mingwX64Main.dependsOn.contains(winuiMain)) {
                        "mingwX64Main must keep the plugin WinUI source-set edge."
                    }
                    check(linuxX64Main.dependsOn.contains(posixMain)) {
                        "linuxX64Main must keep the user-defined hierarchy source-set edge."
                    }
                    check(!linuxX64Main.dependsOn.contains(winuiMain)) {
                        "linuxX64Main must not inherit the plugin WinUI source set."
                    }
                    check(winuiJvmTest.dependsOn.contains(winuiTest)) {
                        "winuiJvmTest must keep the plugin WinUI test source-set edge."
                    }
                    check(mingwX64Test.dependsOn.contains(winuiTest)) {
                        "mingwX64Test must keep the plugin WinUI test source-set edge."
                    }
                    check(linuxX64Test.dependsOn.contains(posixTest)) {
                        "linuxX64Test must keep the user-defined test hierarchy source-set edge."
                    }
                    check(!linuxX64Test.dependsOn.contains(winuiTest)) {
                        "linuxX64Test must not inherit the plugin WinUI test source set."
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyUserHierarchy", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyUserHierarchy")?.outcome)
    }

    @Test
    fun winui_hierarchy_template_extension_groups_jvm_and_mingw_targets() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-winui-hierarchy-extension-test-")
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
            rootProject.name = "kotlin-winrt-kmp-winui-hierarchy-extension-test"
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
            @file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

            import io.github.composefluent.winrt.gradle.winui

            plugins {
                kotlin("multiplatform") version "2.3.20"
                id("io.github.compose-fluent.winrt")
            }

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                linuxX64()
                applyHierarchyTemplate {
                    common {
                        winui()
                    }
                }
            }

            tasks.register("verifyWinuiHierarchyExtension") {
                doLast {
                    val winuiMain = kotlin.sourceSets.named("winuiMain").get()
                    val winuiTest = kotlin.sourceSets.named("winuiTest").get()
                    check(kotlin.sourceSets.named("winuiJvmMain").get().dependsOn.contains(winuiMain)) {
                        "winuiJvmMain must inherit the winuiMain hierarchy group."
                    }
                    check(kotlin.sourceSets.named("mingwX64Main").get().dependsOn.contains(winuiMain)) {
                        "mingwX64Main must inherit the winuiMain hierarchy group."
                    }
                    check(!kotlin.sourceSets.named("linuxX64Main").get().dependsOn.contains(winuiMain)) {
                        "linuxX64Main must not inherit the winuiMain hierarchy group."
                    }
                    check(kotlin.sourceSets.named("winuiJvmTest").get().dependsOn.contains(winuiTest)) {
                        "winuiJvmTest must inherit the winuiTest hierarchy group."
                    }
                    check(kotlin.sourceSets.named("mingwX64Test").get().dependsOn.contains(winuiTest)) {
                        "mingwX64Test must inherit the winuiTest hierarchy group."
                    }
                    check(!kotlin.sourceSets.named("linuxX64Test").get().dependsOn.contains(winuiTest)) {
                        "linuxX64Test must not inherit the winuiTest hierarchy group."
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyWinuiHierarchyExtension", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyWinuiHierarchyExtension")?.outcome)
    }

    @Test
    fun mingw_application_entry_can_call_main_from_custom_intermediate_source_set() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-application-entry-source-set-test-")
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
            rootProject.name = "kotlin-winrt-kmp-application-entry-source-set-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                sourceSets {
                    winuiMain {
                        dependsOn commonMain
                    }
                    mingwX64Main {
                        dependsOn winuiMain
                    }
                }
            }

            winRT {
                application {
                    mainClass.set "sample.MainKt"
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/winuiMain/kotlin/sample/Main.kt"),
            """
            package sample

            fun main() = Unit
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/winuiMain/kotlin/io/github/composefluent/winrt/runtime/WinRTWindowsAppSdkBootstrap.kt"),
            """
            package io.github.composefluent.winrt.runtime

            object WinRTWindowsAppSdkBootstrap {
                fun initializeApplicationHost(unpackaged: Boolean = true): AutoCloseable =
                    AutoCloseable {
                    }
            }
            """.trimIndent(),
        )
        val staleProjection = projectDir.resolve("build/generated/kotlin-winrt/src/main/kotlin/sample/StaleProjection.kt")
        Files.createDirectories(staleProjection.parent)
        Files.writeString(staleProjection, "package sample\ninternal class StaleProjection\n")
        val staleAuthoring = projectDir.resolve("build/generated/kotlin-winrt-authoring/src/main/kotlin/sample/StaleAuthoring.kt")
        Files.createDirectories(staleAuthoring.parent)
        Files.writeString(staleAuthoring, "package sample\ninternal class StaleAuthoring\n")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileKotlinWinuiJvm", "compileKotlinMingwX64", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTMingwApplicationEntry")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinWinuiJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinMingwX64")?.outcome)
    }

    @Test
    fun plugin_injects_compiler_plugin_options_into_multiplatform_jvm_compilation() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-plugin-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            tasks.named("winuiJvmJar") {
                archiveFileName = "ui-winuijvm-9999.0.0-SNAPSHOT.jar"
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }

            tasks.register("printWinuiJvmCompilerArgs") {
                dependsOn("generateWinRTProjections")
                doLast {
                    def compileTask = tasks.named("compileKotlinWinuiJvm").get()
                    def compilerOptions = compileTask.class.methods
                        .find { it.name == "getCompilerOptions" && it.parameterCount == 0 }
                        .invoke(compileTask)
                    def freeCompilerArgs = compilerOptions.class.methods
                        .find { it.name == "getFreeCompilerArgs" && it.parameterCount == 0 }
                        .invoke(compilerOptions)
                    def args = freeCompilerArgs.class.methods
                        .find { it.name == "get" && it.parameterCount == 0 }
                        .invoke(freeCompilerArgs)
                    println("WINUI_JVM_ARGS=" + args)
                    println("COMMON_MAIN_SOURCES=" + kotlin.sourceSets.named("commonMain").get().kotlin.srcDirs)
                }
            }

            def writeAuthoredCandidateProbe = tasks.register("writeAuthoredCandidateProbe") {
                dependsOn("generateWinRTProjections")
                def outputFile = layout.projectDirectory.file(
                    "src/commonMain/kotlin/sample/InternalStringableThing.kt",
                )
                outputs.file(outputFile)
                doLast {
                    def target = outputFile.asFile
                    target.parentFile.mkdirs()
                    target.text = ${"'''"}
                    package sample

                    import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                    @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                    internal class InternalStringableThing

                    @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                    class PublicStringableThing
                    ${"'''"}.stripIndent().trim()
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn writeAuthoredCandidateProbe
            }

            tasks.register("verifyWinuiJvmCompilerSupportOutput") {
                dependsOn("compileKotlinWinuiJvm")
                doLast {
                    def supportRoot = layout.buildDirectory.dir(
                        "classes/kotlin/winuiJvm/main/io/github/composefluent/winrt/projections/support",
                    ).get().asFile
                    def hasCompilerManifest = false
                    supportRoot.eachFileRecurse(groovy.io.FileType.FILES) {
                        if (it.name.startsWith("WinRTCompilerSupportManifest_") && it.name.endsWith(".class")) {
                            hasCompilerManifest = true
                        }
                    }
                    if (!hasCompilerManifest) {
                        throw new GradleException("Expected owner-scoped compiler support manifest under: " + supportRoot)
                    }
                    def hasProjectionSupportInitializer = false
                    supportRoot.eachFileRecurse(groovy.io.FileType.FILES) {
                        if (it.name.startsWith("WinRTProjectionSupport_") && it.name.endsWith(".class")) {
                            hasProjectionSupportInitializer = true
                        }
                    }
                    if (!hasProjectionSupportInitializer) {
                        throw new GradleException("Expected compiler-generated projection support initializer under: " + supportRoot)
                    }
                    def generatedCompilerSupportRoot = layout.buildDirectory.dir(
                        "generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-support",
                    ).get().asFile
                    def generatedCompilerSupportManifest = new File(generatedCompilerSupportRoot, "compiler-support.tsv")
                    if (!generatedCompilerSupportManifest.text.contains(
                        "authoring-type-details-registrar\t" +
                            "io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar_kotlin_winrt_kmp_plugin_test\t" +
                            "authoring-type-details-registrars.tsv\t1",
                    )) {
                        throw new GradleException("Expected generated compiler support to publish authoring TypeDetails registrar: " +
                            generatedCompilerSupportManifest.text)
                    }
                    def legacyInterfaceRegistry = new File(supportRoot, "WinRTInterfaceProjectionRegistry.class")
                    if (legacyInterfaceRegistry.exists()) {
                        throw new GradleException("Legacy interface projection registry must not be generated: " + legacyInterfaceRegistry)
                    }
                    def authoredCandidates = layout.buildDirectory.file(
                        "classes/kotlin/winuiJvm/main/kotlin-winrt/authored-candidates.tsv",
                    ).get().asFile
                    if (!authoredCandidates.isFile()) {
                        throw new GradleException("Expected compiler-authored candidates output: " + authoredCandidates)
                    }
                    def authoredCandidateRows = authoredCandidates.readLines()
                    if (!authoredCandidateRows.contains(
                        "sample\tInternalStringableThing\tsample.InternalStringableThing\t\tWindows.Foundation.IStringable\t\tfalse\t\t",
                    )) {
                        throw new GradleException("Expected internal authored candidate with non-public visibility in: " + authoredCandidateRows)
                    }
                    if (!authoredCandidateRows.contains(
                        "sample\tPublicStringableThing\tsample.PublicStringableThing\t\tWindows.Foundation.IStringable\t\ttrue\t\t",
                    )) {
                        throw new GradleException("Expected public authored candidate with public visibility in: " + authoredCandidateRows)
                    }
                    def authoredOutputRoot = layout.buildDirectory.dir(
                        "classes/kotlin/winuiJvm/main/kotlin-winrt-authoring",
                    ).get().asFile
                    def authoredMetadata = new File(authoredOutputRoot, "authored-metadata.tsv")
                    if (!authoredMetadata.isFile()) {
                        throw new GradleException("Expected compiler-authored metadata descriptor output: " + authoredMetadata)
                    }
                    if (!authoredMetadata.text.contains("sample.PublicStringableThing")) {
                        throw new GradleException("Expected public compiler-authored metadata descriptor row in: " + authoredMetadata.text)
                    }
                    if (authoredMetadata.text.contains("sample.InternalStringableThing")) {
                        throw new GradleException("Internal authored types must not be exported in compiler-authored metadata descriptor: " +
                            authoredMetadata.text)
                    }
                    def authoredWinmd = new File(authoredOutputRoot, "kotlin-winrt-kmp-plugin-test.winmd")
                    if (!authoredWinmd.isFile()) {
                        throw new GradleException("Expected compiler-authored WinMD output: " + authoredWinmd)
                    }
                    def authoredHostManifest = new File(authoredOutputRoot, "kotlin-winrt-kmp-plugin-test.host.json")
                    if (!authoredHostManifest.isFile()) {
                        throw new GradleException("Expected compiler-authored host manifest output: " + authoredHostManifest)
                    }
                    def authoredHostManifestText = authoredHostManifest.text
                    if (!authoredHostManifestText.contains("sample.PublicStringableThing")) {
                        throw new GradleException("Expected public compiler-authored host manifest entry in: " + authoredHostManifestText)
                    }
                    if (!authoredHostManifestText.contains("WinRTAuthoringHostExports_ui_winuijvm_9999_0_0_SNAPSHOT_jar")) {
                        throw new GradleException("Expected artifact-scoped host exports class in compiler-authored host manifest: " +
                            authoredHostManifestText)
                    }
                    if (!authoredHostManifestText.contains('"targetArtifact": "ui-winuijvm-9999.0.0-SNAPSHOT.jar"')) {
                        throw new GradleException("Expected compiler-authored host manifest to target the WinUI JVM jar: " +
                            authoredHostManifestText)
                    }
                    if (authoredHostManifestText.contains("sample.InternalStringableThing")) {
                        throw new GradleException("Internal authored types must not be exported in compiler-authored host manifest: " +
                            authoredHostManifestText)
                    }
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/commonMain/kotlin/sample/PlainSource.kt"),
            """
            package sample

            internal object PlainSource
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                "printWinuiJvmCompilerArgs",
                "verifyWinuiJvmCompilerSupportOutput",
                "--stacktrace",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:metadataIndex="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:typeIndexOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredCandidatesOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredMetadataOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredWinmdOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredHostManifestOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoringAssemblyName=kotlin-winrt-kmp-plugin-test"))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoringTargetArtifactName=ui-winuijvm-9999.0.0-SNAPSHOT.jar"))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:compilerSupportManifest="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:compilerSupportClassOutputDirectory="))
        assertTrue(result.output.replace("\\", "/").contains("build/generated/kotlin-winrt/src/winuiMain/kotlin"))
    }

    @Test
    fun authoring_only_jvm_library_keeps_discovery_and_validation_without_local_projection_output() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-authoring-only-plugin-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
        val authoringJar = authoringJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-authoring-only-plugin-test"
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx1024m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            configurations.configureEach {
                exclude group: "io.github.compose-fluent", module: "winrt-runtime"
                exclude group: "io.github.compose-fluent", module: "winrt-authoring"
            }

            dependencies {
                implementation files("$runtimeJar")
                implementation files("$authoringJar")
                add("kotlinWinRTLibraryDependencyIdentity", files("dependency-identity.json"))
            }

            winRT {
                windowsSdk("10.0.26100.0", false, false)
            }

            tasks.register("printAuthoringOnlyCompilerArgs") {
                doLast {
                    def localGenerationRequired = project.extensions.extraProperties
                        .get("kotlinWinRTLocalGenerationRequired")
                        .get()
                    def mainSourceDirs = kotlin.sourceSets.named("main").get().kotlin.srcDirs
                    println("AUTHORING_ONLY_LOCAL_GENERATION=" + localGenerationRequired)
                    println("AUTHORING_ONLY_MAIN_SOURCES=" + mainSourceDirs)
                    if (localGenerationRequired) {
                        throw new GradleException("Authoring-only fixture unexpectedly requires local projection generation")
                    }
                    if (mainSourceDirs.any { it.path.replace('\\', '/').contains('/generated/kotlin-winrt/src/') }) {
                        throw new GradleException("Authoring-only fixture includes generated projection sources: " + mainSourceDirs)
                    }
                    def compileTask = tasks.named("compileKotlin").get()
                    def compilerOptions = compileTask.class.methods
                        .find { it.name == "getCompilerOptions" && it.parameterCount == 0 }
                        .invoke(compileTask)
                    def freeCompilerArgs = compilerOptions.class.methods
                        .find { it.name == "getFreeCompilerArgs" && it.parameterCount == 0 }
                        .invoke(compilerOptions)
                    def args = freeCompilerArgs.class.methods
                        .find { it.name == "get" && it.parameterCount == 0 }
                        .invoke(freeCompilerArgs)
                    println("AUTHORING_ONLY_ARGS=" + args)
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("dependency-identity.json"),
            """
            {
              "projectedTypes": ["Windows.Foundation.IStringable"],
              "authoringMetadataIndexRows": ["Windows.Foundation.IStringable\tInterface\t\t\t"]
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/main/kotlin/sample/PublicAuthoredThing.kt"),
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
            class PublicAuthoredThing
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                "printAuthoringOnlyCompilerArgs",
                "check",
                "jar",
                "--stacktrace",
                "--rerun-tasks",
                "--max-workers=1",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateCompileKotlinWinRTAuthoredCandidates")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":jar")?.outcome)
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:metadataIndex="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredCandidatesOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredWinmdOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoredHostManifestOutput="))
        assertTrue(result.output.contains("plugin:io.github.composefluent.winrt.compiler:authoringAssemblyName=kotlin-winrt-authoring-only-plugin-test"))
        assertFalse(result.output.contains("plugin:io.github.composefluent.winrt.compiler:compilerSupportManifest="))
        assertFalse(result.output.contains("plugin:io.github.composefluent.winrt.compiler:compilerSupportClassOutputDirectory="))
        assertEquals(null, result.task(":mergeWinRTCompilerSupport"))

        val generatedProjectionRoot = projectDir.resolve("build/generated/kotlin-winrt/src/jvmMain/kotlin")
        val generatedProjectionKotlinFiles = Files.walk(generatedProjectionRoot).use { paths ->
            paths.filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
        assertTrue(
            "Authoring-only generation must not emit projection Kotlin files: $generatedProjectionKotlinFiles",
            generatedProjectionKotlinFiles.isEmpty(),
        )
        listOf(
            "projection-registrar.tsv",
            "type-shape-descriptors.tsv",
            "source-additions.tsv",
            "generic-instantiations.tsv",
        ).forEach { projectionSupportFile ->
            assertFalse(
                "Authoring-only generation must not emit $projectionSupportFile",
                Files.exists(generatedProjectionRoot.resolve("kotlin-winrt-support/$projectionSupportFile")),
            )
        }
        val generatedAuthoringRoot = projectDir.resolve("build/generated/kotlin-winrt-authoring/src/jvmMain/kotlin")
        assertTrue(
            "Authoring-only generation must retain generated TypeDetails sources under $generatedAuthoringRoot",
            Files.walk(generatedAuthoringRoot).use { paths ->
                paths.anyMatch { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".kt") }
            },
        )

        val compilerOutputRoot = projectDir.resolve("build/classes/kotlin/main")
        assertTrue(Files.isRegularFile(compilerOutputRoot.resolve("kotlin-winrt/authored-candidates.tsv")))
        assertTrue(
            Files.readString(compilerOutputRoot.resolve("kotlin-winrt/authored-candidates.tsv"))
                .contains("sample.PublicAuthoredThing"),
        )
        assertTrue(
            Files.isRegularFile(
                compilerOutputRoot.resolve("kotlin-winrt-authoring/kotlin-winrt-authoring-only-plugin-test.winmd"),
            ),
        )
        assertTrue(
            Files.isRegularFile(
                compilerOutputRoot.resolve("kotlin-winrt-authoring/kotlin-winrt-authoring-only-plugin-test.host.json"),
            ),
        )
    }

    @Test
    fun sample_mode_defaults_to_winui_and_explicit_disable_selects_no_winui_without_app_sdk_packages() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-sample-mode-plugin-test-")
        writeGradleFile(
            projectDir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "kotlin-winrt-sample-mode-plugin-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            def winuiEnabled = providers.gradleProperty("kotlinWinRT.samples.enableWinUI")
                .map { it.toBoolean() }
                .orElse(true)
            def windowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsAppSdkVersion")
                .orElse("2.1.3")

            kotlin {
                jvm("winuiJvm")
                mingwX64()
                sourceSets {
                    winuiMain.kotlin.srcDir("src/winuiMain/kotlin")
                    if (!winuiEnabled.get()) {
                        winuiMain.kotlin.setSrcDirs([])
                        noWinuiMain {
                            dependsOn(commonMain)
                        }
                        winuiJvmMain.dependsOn(noWinuiMain)
                        mingwX64Main.dependsOn(noWinuiMain)
                    }
                }
            }

            winRT {
                if (winuiEnabled.get()) {
                    nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion.get()) {
                        generateProjection = false
                    }
                    nugetPackage("WinUIEssential.WinUI3", "1.6.7") {
                        generateProjection = false
                    }
                }
            }

            tasks.register("printSampleMode") {
                doLast {
                    def noWinuiMain = kotlin.sourceSets.findByName("noWinuiMain")
                    def winuiMain = kotlin.sourceSets.getByName("winuiMain")
                    def winuiJvmMain = kotlin.sourceSets.getByName("winuiJvmMain")
                    def mingwX64Main = kotlin.sourceSets.getByName("mingwX64Main")
                    def packageIds = winRT.nugetPackages.collect { it.packageId }
                    println("SAMPLE_MODE=" + (winuiEnabled.get() ? "winui" : "no-winui"))
                    println("NO_WINUI_SOURCE_SET=" + (noWinuiMain != null))
                    println("JVM_USES_NO_WINUI=" + (noWinuiMain != null && winuiJvmMain.dependsOn.contains(noWinuiMain)))
                    println("MINGW_USES_NO_WINUI=" + (noWinuiMain != null && mingwX64Main.dependsOn.contains(noWinuiMain)))
                    println("WINUI_USER_SOURCES=" + winuiMain.kotlin.srcDirs.findAll {
                        it.path.replace('\\', '/').endsWith('src/winuiMain/kotlin')
                    })
                    println("SAMPLE_NUGET_PACKAGES=" + packageIds)
                }
            }
            """.trimIndent(),
        )

        val defaultResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("printSampleMode", "--stacktrace")
            .build()
        assertTrue(defaultResult.output.contains("SAMPLE_MODE=winui"))
        assertTrue(defaultResult.output.contains("NO_WINUI_SOURCE_SET=false"))
        assertFalse(defaultResult.output.contains("WINUI_USER_SOURCES=[]"))
        assertTrue(
            defaultResult.output.contains(
                "SAMPLE_NUGET_PACKAGES=[Microsoft.WindowsAppSDK, WinUIEssential.WinUI3]",
            ),
        )

        val disabledResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("printSampleMode", "-PkotlinWinRT.samples.enableWinUI=false", "--stacktrace")
            .build()
        assertTrue(disabledResult.output.contains("SAMPLE_MODE=no-winui"))
        assertTrue(disabledResult.output.contains("NO_WINUI_SOURCE_SET=true"))
        assertTrue(disabledResult.output.contains("JVM_USES_NO_WINUI=true"))
        assertTrue(disabledResult.output.contains("MINGW_USES_NO_WINUI=true"))
        assertTrue(disabledResult.output.contains("WINUI_USER_SOURCES=[]"))
        assertTrue(disabledResult.output.contains("SAMPLE_NUGET_PACKAGES=[]"))
    }

    @Test
    fun multiplatform_jvm_authoring_identity_uses_target_jar_archive_file_name() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-identity-artifact-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
        val authoringJar = authoringJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-kmp-identity-artifact-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            configurations.configureEach {
                exclude group: "io.github.compose-fluent", module: "winrt-runtime"
                exclude group: "io.github.compose-fluent", module: "winrt-authoring"
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    winuiMain {
                        dependencies {
                            implementation files("$runtimeJar")
                            implementation files("$authoringJar")
                        }
                    }
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                            implementation files("$authoringJar")
                        }
                    }
                    winuiJvmMain {
                        dependencies {
                            implementation files("$runtimeJar")
                            implementation files("$authoringJar")
                        }
                    }
                }
            }

            tasks.named("winuiJvmJar") {
                archiveFileName = "ui-winuijvm-9999.0.0-SNAPSHOT.jar"
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/commonMain/kotlin/sample/PublicStringableThing.kt"),
            """
            package sample

            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
            class PublicStringableThing
            """.trimIndent(),
        )
        val targetJar = projectDir.resolve("build/libs/ui-winuijvm-9999.0.0-SNAPSHOT.jar")
        Files.createDirectories(targetJar.parent)
        Files.writeString(targetJar, "jar")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("generateWinRTIdentity", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTIdentity")?.outcome)
        val identity = projectDir.resolve("build/generated/kotlin-winrt/identity/kotlin-winrt.json").toFile().readText()
        assertTrue(identity, identity.contains("\"targetArtifact\":\"ui-winuijvm-9999.0.0-SNAPSHOT.jar\""))
        assertTrue(identity, identity.contains("\"fileName\":\"ui-winuijvm-9999.0.0-SNAPSHOT.jar\""))
    }

    @Test
    fun dependency_identity_uses_include_and_projected_types_for_downstream_suppression() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": [],
              "includeTypes": ["Microsoft.UI.Dispatching.DispatcherQueue"],
              "projectionShapeVersion": 1,
              "projectedTypes": ["Microsoft.UI.Dispatching.IDispatcherQueue"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )
        val model = WinRTMetadataModel(
            listOf(
                WinRTNamespace(
                    "Microsoft.UI.Dispatching",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Dispatching",
                            name = "DispatcherQueue",
                            kind = WinRTTypeKind.RuntimeClass,
                        ),
                        WinRTTypeDefinition(
                            namespace = "Microsoft.UI.Dispatching",
                            name = "IDispatcherQueue",
                            kind = WinRTTypeKind.Interface,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "Microsoft.UI.Dispatching.DispatcherQueue",
                "Microsoft.UI.Dispatching.IDispatcherQueue",
            ),
            dependencyProjectedTypeNames(model, listOf(dependencyIdentity)).toList(),
        )
    }

    @Test
    fun source_addition_identity_follows_owner_namespaces() {
        assertEquals(
            listOf("microsoft.ui.Win32Interop", "winrt.interop.InitializeWithWindow", "winrt.interop.WindowNative"),
            sourceAdditionTypeNames(listOf("Microsoft.UI", "WinRT.Interop"), emptyList()),
        )
        assertEquals(
            listOf("microsoft.ui.Win32Interop"),
            sourceAdditionTypeNames(listOf("Microsoft.UI.Windowing"), emptyList()),
        )
        assertEquals(
            listOf("winrt.interop.InitializeWithWindow", "winrt.interop.WindowNative"),
            sourceAdditionTypeNames(listOf("Windows.Foundation"), emptyList()),
        )
        assertEquals(
            listOf("winrt.interop.InitializeWithWindow", "winrt.interop.WindowNative"),
            sourceAdditionTypeNames(listOf("Microsoft.UI", "WinRT.Interop"), listOf("Microsoft.UI")),
        )
    }

    @Test
    fun source_addition_identity_reads_generated_manifest() {
        val project = ProjectBuilder.builder().build()
        val manifest = project.layout.buildDirectory.file("generated-source-additions/source-additions.tsv").get().asFile
        Files.createDirectories(manifest.toPath().parent)
        Files.writeString(
            manifest.toPath(),
            """
            generatedTypeName
            winrt.interop.WindowNative
            microsoft.ui.Win32Interop
            winrt.interop.InitializeWithWindow
            microsoft.ui.Win32Interop
            """.trimIndent(),
        )

        assertEquals(
            listOf("microsoft.ui.Win32Interop", "winrt.interop.InitializeWithWindow", "winrt.interop.WindowNative"),
            readGeneratedSourceAdditionTypeNames(listOf(manifest)),
        )
    }

    @Test
    fun dependency_identity_source_additions_suppress_downstream_generation() {
        val project = ProjectBuilder.builder().build()
        val dependencyIdentity = project.layout.buildDirectory.file("dependency-source-additions/kotlin-winrt.json").get().asFile
        Files.createDirectories(dependencyIdentity.toPath().parent)
        Files.writeString(
            dependencyIdentity.toPath(),
            """
            {
              "includeNamespaces": ["Microsoft.UI"],
              "includeTypes": [],
              "projectionShapeVersion": 1,
              "projectedTypes": [],
              "sourceAdditions": ["microsoft.ui.Win32Interop"],
              "excludeNamespaces": [],
              "excludeTypes": []
            }
            """.trimIndent(),
        )

        assertEquals(
            setOf("microsoft.ui.Win32Interop"),
            dependencySourceAdditionTypeNames(listOf(dependencyIdentity)),
        )
        assertEquals(
            listOf("microsoft.ui.Win32Interop"),
            readProjectionSurfaceIdentity(dependencyIdentity).sourceAdditions,
        )
    }

    @Test
    fun duplicate_dependency_source_addition_owners_fail_closed() {
        val project = ProjectBuilder.builder().build()
        val first = project.layout.buildDirectory.file("dependency-source-additions-a/kotlin-winrt.json").get().asFile
        val second = project.layout.buildDirectory.file("dependency-source-additions-b/kotlin-winrt.json").get().asFile
        listOf(first, second).forEach { identity ->
            Files.createDirectories(identity.toPath().parent)
            Files.writeString(
                identity.toPath(),
                """
                {
                  "sourceAdditions": ["microsoft.ui.Win32Interop"]
                }
                """.trimIndent(),
            )
        }

        val failure = runCatching {
            dependencySourceAdditionTypeNames(listOf(first, second))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("microsoft.ui.Win32Interop"))
        assertTrue(failure?.message.orEmpty().contains("multiple WinRT projection dependencies"))
    }

    @Test
    fun dependency_projection_identity_rejects_conflicting_projected_type_owners() {
        val project = ProjectBuilder.builder().build()
        val firstIdentity = project.layout.buildDirectory.file("dependency-identities-a/kotlin-winrt.json").get().asFile
        val secondIdentity = project.layout.buildDirectory.file("dependency-identities-b/kotlin-winrt.json").get().asFile
        val disjointIdentity = project.layout.buildDirectory.file("dependency-identities-c/kotlin-winrt.json").get().asFile
        listOf(
            firstIdentity to "Windows.UI.Xaml.Data.BindableAttribute",
            secondIdentity to "Windows.UI.Xaml.Data.BindableAttribute",
            disjointIdentity to "Windows.UI.Xaml.Markup.ContentPropertyAttribute",
        ).forEach { (identityFile, projectedType) ->
            Files.createDirectories(identityFile.toPath().parent)
            Files.writeString(
                identityFile.toPath(),
                """
                {
                  "projectionShapeVersion": 1,
                  "projectedTypes": ["$projectedType"]
                }
                """.trimIndent(),
            )
        }

        val failure = runCatching {
            validateDependencyProjectionIdentityOwnership(listOf(firstIdentity, secondIdentity))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("Windows.UI.Xaml.Data.BindableAttribute"))
        assertTrue(failure?.message.orEmpty().contains(firstIdentity.absolutePath))
        assertTrue(failure?.message.orEmpty().contains(secondIdentity.absolutePath))
        validateDependencyProjectionIdentityOwnership(listOf(firstIdentity, disjointIdentity))
    }

    @Test
    fun validates_scanner_and_compiler_authored_candidates_after_jvm_compile() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-authored-candidate-validation-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-authored-candidate-validation-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            dependencies {
                implementation files("$runtimeJar")
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }

            tasks.register("verifyAuthoredCandidateArtifacts") {
                dependsOn("validateCompileKotlinWinRTAuthoredCandidates")
                doLast {
                    def scannerCandidates = layout.buildDirectory.file(
                        "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring/authored-candidates.tsv",
                    ).get().asFile
                    def compilerCandidates = layout.buildDirectory.file(
                        "classes/kotlin/main/kotlin-winrt/authored-candidates.tsv",
                    ).get().asFile
                    if (scannerCandidates.text != compilerCandidates.text) {
                        throw new GradleException("Expected scanner and compiler authored candidates to match")
                    }
                    if (!scannerCandidates.text.isEmpty()) {
                        throw new GradleException("Expected no authored candidates in scanner artifact: " + scannerCandidates.text)
                    }
                    def scannerAuthoredOutputRoot = layout.buildDirectory.dir(
                        "generated/kotlin-winrt/src/jvmMain/kotlin/kotlin-winrt-authoring",
                    ).get().asFile
                    def compilerAuthoredOutputRoot = layout.buildDirectory.dir(
                        "classes/kotlin/main/kotlin-winrt-authoring",
                    ).get().asFile
                    ["authored-metadata.tsv", "${'$'}{project.name}.winmd", "${'$'}{project.name}.host.json"].each { name ->
                        def scannerArtifact = new File(scannerAuthoredOutputRoot, name)
                        def compilerArtifact = new File(compilerAuthoredOutputRoot, name)
                        if (!scannerArtifact.isFile()) {
                            throw new GradleException("Expected scanner-authored support artifact: " + scannerArtifact)
                        }
                        if (!compilerArtifact.isFile()) {
                            throw new GradleException("Expected compiler-authored support artifact: " + compilerArtifact)
                        }
                        if (scannerArtifact.bytes != compilerArtifact.bytes) {
                            throw new GradleException(
                                "Expected scanner and compiler authored support artifacts to match for " + name,
                            )
                        }
                    }
                }
            }

            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/main/kotlin/sample/PlainSource.kt"),
            """
            package sample

            internal object PlainSource
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyAuthoredCandidateArtifacts", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateCompileKotlinWinRTAuthoredCandidates")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyAuthoredCandidateArtifacts")?.outcome)

        val compilerAuthoredOutputRoot = projectDir.resolve("build/classes/kotlin/main/kotlin-winrt-authoring")
        listOf(
            "authored-metadata.tsv",
            "kotlin-winrt-authored-candidate-validation-test.winmd",
            "kotlin-winrt-authored-candidate-validation-test.host.json",
        ).forEach { name ->
            Files.deleteIfExists(compilerAuthoredOutputRoot.resolve(name))
        }

        val rerunResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("validateCompileKotlinWinRTAuthoredCandidates", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, rerunResult.task(":compileKotlin")?.outcome)
        listOf(
            "authored-metadata.tsv",
            "kotlin-winrt-authored-candidate-validation-test.winmd",
            "kotlin-winrt-authored-candidate-validation-test.host.json",
        ).forEach { name ->
            assertTrue(
                "Expected compiler-authored support artifact to be restored: $name",
                Files.isRegularFile(compilerAuthoredOutputRoot.resolve(name)),
            )
        }
    }

    @Test
    fun authored_candidate_validation_gates_jvm_artifact_lifecycle_tasks_without_identity_cycle() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)

        val validationTaskName = "validateCompileKotlinWinRTAuthoredCandidates"
        listOf(
            "classes",
            "jar",
            "assemble",
            "processResources",
            "check",
        ).forEach { taskName ->
            val task = project.tasks.named(taskName).get()
            val dependencies = task.taskDependencies.getDependencies(task).map { it.name }
            assertTrue(
                "$taskName must depend on $validationTaskName",
                validationTaskName in dependencies,
            )
        }
        val identityTask = project.tasks.named("generateWinRTIdentity").get()
        val identityDependencies = declaredTaskDependencyNames(identityTask)
        assertFalse(
            "generateWinRTIdentity must not depend on authored validation or it can create library forwarding cycles: $identityDependencies",
            validationTaskName in identityDependencies,
        )
    }

    @Test
    fun compiler_authored_type_details_load_dependency_authored_metadata_records() {
        val project = ProjectBuilder.builder().build()
        val root = project.layout.buildDirectory.dir("compiler-authored-details").get().asFile.toPath()
        val dependencyWinmd = root.resolve("DependencyComponent.winmd")
        WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "DependencyComponent",
            interfaces = listOf(
                WinRTPortableExecutableInterfaceDescriptor(
                    interfaceName = "Dependency.Component.IWidget",
                    iid = "22222222-3333-4444-5555-666666666666",
                ),
            ),
            outputFile = dependencyWinmd,
        )
        val dependencyIdentity = root.resolve("dependency/kotlin-winrt.json")
        Files.createDirectories(dependencyIdentity.parent)
        Files.writeString(
            dependencyIdentity,
            """{"authoredMetadataRecords":[{"fileName":"DependencyComponent.winmd","contentBase64":"${Base64.getEncoder().encodeToString(Files.readAllBytes(dependencyWinmd))}"}]}""",
        )
        val candidates = root.resolve("compiler-candidates.tsv")
        KotlinWinRTAuthoringCandidateFile.write(
            candidates,
            listOf(
                KotlinWinRTAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "AuthoredWidget",
                    sourceTypeName = "sample.AuthoredWidget",
                    winRTBaseClassName = null,
                    winRTInterfaceNames = listOf("Dependency.Component.IWidget"),
                    overridableInterfaceNames = emptyList(),
                    isPublic = true,
                ),
            ),
        )

        val task = project.tasks.register(
            "generateCompilerAuthoredTypeDetailsUnderTest",
            GenerateWinRTCompilerAuthoredTypeDetailsTask::class.java,
        ) { registeredTask ->
            registeredTask.outputDirectory.set(project.layout.buildDirectory.dir("compiler-authored-details-output"))
            registeredTask.legacyOutputDirectories.from(project.files())
            registeredTask.compilerCandidates.from(candidates)
            registeredTask.metadataInputs.set(emptyList())
            registeredTask.metadataInputFiles.from(project.files())
            registeredTask.dependencyIdentityFiles.from(dependencyIdentity.toFile())
            registeredTask.includeNamespaces.set(emptyList())
            registeredTask.includeTypes.set(emptyList())
            registeredTask.excludeNamespaces.set(emptyList())
            registeredTask.excludeTypes.set(emptyList())
            registeredTask.includeWindowsSdkExtensions.set(false)
            registeredTask.generateWindowsSdkProjection.set(false)
            registeredTask.nugetExecutable.set("nuget")
            registeredTask.nugetCliVersion.set("7.3.1")
            registeredTask.nugetCliCacheDirectory.set(project.layout.buildDirectory.dir("nuget-cli"))
            registeredTask.restoreNuGetPackages.set(false)
            registeredTask.useNuGetCliGlobalPackages.set(false)
            registeredTask.nugetGlobalPackagesRoots.set(emptyList())
            registeredTask.nugetPackages.set(emptyList())
            registeredTask.authoringAssemblyName.set("Sample")
        }.get()

        task.generate()

        val details = task.outputDirectory.get().asFile.toPath()
            .resolve("sample/WinRT_AuthoredWidget_TypeDetails.kt")
        assertTrue(Files.isRegularFile(details))
        assertTrue(Files.readString(details).contains("22222222-3333-4444-5555-666666666666"))
    }

    @Test
    fun compiler_authored_type_details_uses_projection_registrar_interface_iid() {
        val root = Files.createTempDirectory("kotlin-winrt-compiler-authored-details-registrar-iid-")
        val registrar = root.resolve("projection-registrar.tsv")
        Files.writeString(
            registrar,
            """
            kotlinClassName	projectedTypeName	kind	baseTypeName	metadataClassName	interfaceIid
            sample.IWidget	Sample.IWidget	Interface			33333333-4444-5555-6666-777777777777
            """.trimIndent() + "\n",
        )
        val candidate = KotlinWinRTAuthoredTypeCandidate(
            packageName = "sample",
            className = "AuthoredWidget",
            sourceTypeName = "sample.AuthoredWidget",
            winRTBaseClassName = null,
            winRTInterfaceNames = listOf("Sample.IWidget"),
            overridableInterfaceNames = emptyList(),
            isPublic = true,
        )
        val model = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample",
                            name = "IWidget",
                            kind = WinRTTypeKind.Interface,
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "GetName",
                                    returnTypeName = "String",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ).withProjectionRegistrarInterfaceIids(
            readProjectionRegistrarInterfaceIids(listOf(registrar.toFile())),
        )
        val output = root.resolve("details")

        KotlinWinRTAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = model,
            outputDirectory = output,
            assemblyName = "Sample",
        )

        val details = output.resolve("sample/WinRT_AuthoredWidget_TypeDetails.kt")
        assertTrue(Files.isRegularFile(details))
        assertTrue(Files.readString(details).contains("33333333-4444-5555-6666-777777777777"))
    }

    @Test
    fun authored_candidate_validation_is_registered_only_for_winui_native_targets() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.configure(KotlinMultiplatformExtension::class.java) { kotlin ->
            kotlin.mingwX64("winuiMingw")
            kotlin.linuxX64("linux")
        }

        val taskNames = project.tasks.names
        assertTrue("validateCompileKotlinWinuiMingwWinRTAuthoredCandidates" in taskNames)
        assertTrue("validateCompileKotlinWinuiMingwWinRTNativeAuthoringExports" in taskNames)
        assertFalse("validateCompileKotlinLinuxWinRTAuthoredCandidates" in taskNames)
        assertFalse("validateCompileKotlinLinuxWinRTNativeAuthoringExports" in taskNames)
    }

    @Test
    fun authoring_target_artifact_name_uses_configured_jar_archive_name_consistently() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        project.extensions.getByType(BasePluginExtension::class.java).archivesName.set("custom-projection")

        val generateTask = project.tasks.named(
            "generateWinRTProjections",
            GenerateWinRTProjectionsTask::class.java,
        ).get()
        val compileTask = project.tasks.named("compileKotlin", KotlinJvmCompile::class.java).get()
        val compilerArgs = compileTask.compilerOptions.freeCompilerArgs.get()

        assertEquals("custom-projection.jar", generateTask.authoringTargetArtifactName.get())
        assertTrue(
            compilerArgs.joinToString(separator = "\n"),
            compilerArgs.any { arg ->
                arg == "plugin:io.github.composefluent.winrt.compiler:" +
                    "authoringTargetArtifactName=custom-projection.jar"
            },
        )
    }

    @Test
    fun authored_candidate_validation_allows_generated_authored_candidates_without_interfaces() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-authored-candidate-mismatch-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-authored-candidate-mismatch-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            dependencies {
                implementation files("$runtimeJar")
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }

            def writeGeneratedAuthoredCandidate = tasks.register("writeGeneratedAuthoredCandidate") {
                dependsOn("generateWinRTProjections")
                def outputFile = layout.projectDirectory.file(
                    "src/main/kotlin/sample/LateStringableThing.kt",
                )
                outputs.file(outputFile)
                doLast {
                    def target = outputFile.asFile
                    target.parentFile.mkdirs()
                    target.text = ${"'''"}
                    package sample

                    import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                    @WinRTAuthoredRuntimeClass
                    internal class LateStringableThing
                    ${"'''"}.stripIndent().trim()
                }
            }

            tasks.named("compileKotlin") {
                dependsOn writeGeneratedAuthoredCandidate
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/main/kotlin/sample/PlainSource.kt"),
            """
            package sample

            internal object PlainSource
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("validateCompileKotlinWinRTAuthoredCandidates", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateCompileKotlinWinRTAuthoredCandidates")?.outcome)
    }

    @Test
    fun authored_candidate_validation_allows_no_source_jvm_compile() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-authored-candidate-no-source-test-")
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
            rootProject.name = "kotlin-winrt-authored-candidate-no-source-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.jvm" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("check", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":validateCompileKotlinWinRTAuthoredCandidates")?.outcome)
        val compilerCandidates = projectDir.resolve("build/classes/kotlin/main/kotlin-winrt/authored-candidates.tsv")
        assertTrue(Files.isRegularFile(compilerCandidates))
        assertEquals("", Files.readString(compilerCandidates))
        assertEquals(null, result.task(":validateCompileTestKotlinWinRTAuthoredCandidates"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":check")?.outcome)
    }

    @Test
    fun compiler_plugin_rejects_nested_authored_runtime_classes() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/Container.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                class Container {
                    @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                    class NestedStringableThing
                }
            """.trimIndent(),
            expectedDiagnostic = "nested authored runtime classes are not supported",
        )
    }

    @Test
    fun compiler_plugin_rejects_generic_authored_runtime_classes() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/GenericStringableThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class GenericStringableThing<T>
            """.trimIndent(),
            expectedDiagnostic = "must not be generic",
        )
    }

    @Test
    fun compiler_plugin_rejects_non_class_authored_runtime_classes() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/StringableContract.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                interface StringableContract
            """.trimIndent(),
            expectedDiagnostic = "must be a concrete Kotlin class",
        )
    }

    @Test
    fun compiler_plugin_rejects_authored_annotation_metadata_kind_mismatches() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/StringableThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(
                    baseClassName = "windows.foundation.IStringable",
                    interfaceNames = ["windows.foundation.IStringable"],
                )
                class StringableThing
            """.trimIndent(),
            expectedDiagnostic = "annotation baseClassName must reference a WinRT runtime class",
        )
    }

    @Test
    fun compiler_plugin_rejects_public_authored_runtime_classes_without_default_constructor() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/StringableThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class StringableThing(private val value: String)
            """.trimIndent(),
            expectedDiagnostic = "must declare an accessible zero-argument constructor for default activation",
        )
    }

    @Test
    fun compiler_plugin_rejects_authored_runtime_constructors_with_same_arity() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/AmbiguousConstructorThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class AmbiguousConstructorThing {
                    constructor()
                    constructor(value: String)
                    constructor(value: Int)
                }
            """.trimIndent(),
            expectedDiagnostic = "must not declare multiple public constructors with 1 parameter",
        )
    }

    @Test
    fun compiler_plugin_rejects_unsealed_authored_runtime_classes() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/OpenStringableThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                open class OpenStringableThing
            """.trimIndent(),
            expectedDiagnostic = "must be final",
        )
    }

    @Test
    fun compiler_plugin_rejects_jagged_array_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/JaggedArrayThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class JaggedArrayThing {
                    fun values(): Array<Array<String>> = emptyArray()
                }
            """.trimIndent(),
            expectedDiagnostic = "must not expose jagged arrays",
        )
    }

    @Test
    fun compiler_plugin_rejects_exception_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/ExceptionThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class ExceptionThing {
                    fun lastFailure(): Throwable = RuntimeException("failed")
                }
            """.trimIndent(),
            expectedDiagnostic = "must not expose unsupported type kotlin.Throwable",
        )
    }

    @Test
    fun compiler_plugin_rejects_nothing_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/NothingThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class NothingThing {
                    fun impossible(): Nothing = error("no value")
                }
            """.trimIndent(),
            expectedDiagnostic = "must not expose unsupported type kotlin.Nothing",
        )
    }

    @Test
    fun compiler_plugin_rejects_unit_authored_runtime_parameters() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/UnitParameterThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class UnitParameterThing {
                    fun accept(value: Unit) = Unit
                }
            """.trimIndent(),
            expectedDiagnostic = "Unit is only valid as a void return",
        )
    }

    @Test
    fun compiler_plugin_rejects_suspend_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/SuspendThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class SuspendThing {
                    suspend fun load(): String = "value"
                }
            """.trimIndent(),
            expectedDiagnostic = "must not be suspend",
        )
    }

    @Test
    fun compiler_plugin_rejects_authored_runtime_member_retval_parameters() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/RetvalThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class RetvalThing {
                    fun load(__retval: String): String = __retval
                }
            """.trimIndent(),
            expectedDiagnostic = "must not use the generated return-value parameter name",
        )
    }

    @Test
    fun compiler_plugin_rejects_authored_runtime_operator_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/OperatorThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class OperatorThing {
                    operator fun plus(value: String): String = value
                }
            """.trimIndent(),
            expectedDiagnostic = "must not overload Kotlin operators",
        )
    }

    @Test
    fun compiler_plugin_rejects_authored_runtime_overloaded_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/OverloadedThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class OverloadedThing {
                    fun load(value: String): String = value
                    fun load(value: Int): String = value.toString()
                }
            """.trimIndent(),
            expectedDiagnostic = "must not be overloaded until DefaultOverload metadata is supported",
        )
    }

    @Test
    fun compiler_plugin_rejects_generic_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/GenericMemberThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class GenericMemberThing {
                    fun <T> echo(value: T): T = value
                }
            """.trimIndent(),
            expectedDiagnostic = "sample.GenericMemberThing.echo must not be generic",
        )
    }

    @Test
    fun compiler_plugin_rejects_vararg_authored_runtime_parameters() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/VarargThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class VarargThing {
                    fun join(vararg values: String): String = values.joinToString()
                }
            """.trimIndent(),
            expectedDiagnostic = "parameter 'values' must not be vararg",
        )
    }

    @Test
    fun compiler_plugin_rejects_default_value_authored_runtime_parameters() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/DefaultParameterThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class DefaultParameterThing {
                    fun greet(value: String = "hello"): String = value
                }
            """.trimIndent(),
            expectedDiagnostic = "parameter 'value' must not declare a Kotlin default value",
        )
    }

    @Test
    fun compiler_plugin_rejects_function_type_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/FunctionTypeThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class FunctionTypeThing {
                    fun register(callback: (String) -> Unit) = callback("value")
                }
            """.trimIndent(),
            expectedDiagnostic = "must not expose Kotlin function type kotlin.Function1",
        )
    }

    @Test
    fun compiler_plugin_rejects_unsupported_generic_argument_authored_runtime_members() {
        assertCompilerPluginRejectsGeneratedAuthoredSource(
            sourceFile = "src/commonMain/kotlin/sample/GenericArgumentThing.kt",
            sourceText = """
                package sample

                import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                class GenericArgumentThing {
                    fun failures(): List<Throwable> = emptyList()
                }
            """.trimIndent(),
            expectedDiagnostic = "return type generic argument must not expose unsupported type kotlin.Throwable",
        )
    }

    @Test
    fun compiler_plugin_warns_on_authored_runtime_class_casts() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-runtime-class-cast-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-kmp-runtime-class-cast-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }

            def writeRuntimeClassCastProbe = tasks.register("writeRuntimeClassCastProbe") {
                dependsOn("generateWinRTProjections")
                def outputFile = layout.projectDirectory.file("src/commonMain/kotlin/sample/StringableThing.kt")
                outputs.file(outputFile)
                doLast {
                    def target = outputFile.asFile
                    target.parentFile.mkdirs()
                    target.text = ${"'''"}
                    package sample

                    import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass

                    @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
                    class StringableThing

                    fun castStringableThing(value: Any): StringableThing = value as StringableThing
                    ${"'''"}.stripIndent().trim()
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn writeRuntimeClassCastProbe
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileKotlinWinuiJvm", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinWinuiJvm")?.outcome)
        assertTrue(result.output.contains("WinRT runtime class cast to sample.StringableThing is not projection-safe"))
    }

    private fun assertCompilerPluginRejectsGeneratedAuthoredSource(
        sourceFile: String,
        sourceText: String,
        expectedDiagnostic: String,
    ) {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-authoring-validation-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-kmp-nested-authoring-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
            }

            def writeNestedAuthoredProbe = tasks.register("writeNestedAuthoredProbe") {
                dependsOn "generateWinRTProjections"
                def outputFile = layout.projectDirectory.file(
                    "$sourceFile",
                )
                outputs.file outputFile
                doLast {
                    def target = outputFile.asFile
                    target.parentFile.mkdirs()
                    target.text = ${"'''"}
                    $sourceText
                    ${"'''"}.stripIndent().trim()
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn writeNestedAuthoredProbe
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("compileKotlinWinuiJvm", "--stacktrace")
            .forwardOutput()
            .buildAndFail()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlinWinuiJvm")?.outcome)
        assertTrue(result.output.contains(expectedDiagnostic))
    }

    @Test
    fun plugin_lowers_projection_intrinsics_in_multiplatform_jvm_common_sources() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kmp-intrinsic-lowering-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
            }

            apply plugin: "io.github.compose-fluent.winrt"

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IStringable"
                type "Windows.Foundation.Point"
                type "Windows.Foundation.Rect"
            }

            def writeIntrinsicProbe = tasks.register("writeIntrinsicProbe") {
                dependsOn("generateWinRTProjections")
                def outputFile = layout.buildDirectory.file("generated/kotlin-winrt/src/winuiMain/kotlin/sample/IntrinsicProbe.kt")
                outputs.file(outputFile)
                doLast {
                    def target = outputFile.get().asFile
                    target.parentFile.mkdirs()
                    target.text = ${"'''"}
                    package sample

                    import io.github.composefluent.winrt.runtime.ComObjectReference
                    import io.github.composefluent.winrt.runtime.RawAddress
                    import io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic
                    import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic
                    import windows.foundation.Point
                    import windows.foundation.Rect

                    object IntrinsicProbe {
                        fun call(reference: ComObjectReference, value: RawAddress) {
                            WinRTProjectionIntrinsic.callUnit(reference, 7, "RawAddress", value)
                        }

                        fun scalarWithStruct(reference: ComObjectReference, value: Point): Int =
                            WinRTProjectionIntrinsic.callScalar(reference, 8, "Int32", "Struct8_4", value, Point.Metadata)

                        fun rawAddressScalar(reference: ComObjectReference): RawAddress =
                            WinRTProjectionIntrinsic.callScalar(reference, 11, "RawAddress", "")

                        fun booleanWithStruct(reference: ComObjectReference, value: Point): Boolean =
                            WinRTProjectionIntrinsic.callBoolean(reference, 9, "Struct8_4", value, Point.Metadata)

                        fun unitWithLargeStruct(reference: ComObjectReference, value: Rect) {
                            WinRTProjectionIntrinsic.callUnit(reference, 10, "Struct16_4", value, Rect.Metadata)
                        }

                        fun support() {
                            WinRTProjectionSupportIntrinsic.ensureInitialized()
                        }
                    }
                    ${"'''"}.stripIndent().trim()
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn writeIntrinsicProbe
            }

            tasks.register("verifyLoweredIntrinsic") {
                dependsOn("compileKotlinWinuiJvm")
                doLast {
                    def classRoot = layout.buildDirectory.dir("classes/kotlin/winuiJvm/main").get().asFile
                    def classFile = new File(classRoot, "sample/IntrinsicProbe.class")
                    def contents = new String(classFile.bytes, "ISO-8859-1")
                    if (contents.contains("WinRTProjectionIntrinsic")) {
                        throw new GradleException("KMP JVM class still contains WinRTProjectionIntrinsic fallback")
                    }
                    if (contents.contains("WinRTProjectionSupportIntrinsic")) {
                        throw new GradleException("KMP JVM class still contains WinRTProjectionSupportIntrinsic fallback")
                    }
                    if (!contents.contains("kotlinWinRTProjectionSupportInitialize_")) {
                        throw new GradleException("KMP JVM class did not lower projection support marker to compiler-generated initializer")
                    }
                    if (!contents.contains("WinRTJvmFfmDowncallHandles")) {
                        throw new GradleException("KMP JVM class did not lower projection intrinsic to JVM FFM")
                    }
                    if (!contents.contains("Struct8_4")) {
                        throw new GradleException("KMP JVM class did not preserve small struct ABI shape token")
                    }
                    if (!contents.contains("Struct16_4")) {
                        throw new GradleException("KMP JVM class did not preserve large struct ABI shape token")
                    }
                    if (contents.contains("([Ljava/lang/Object;)Ljava/lang/Object;")) {
                        throw new GradleException("KMP JVM class lowered MethodHandle.invoke as a single Object[] vararg call")
                    }
                    if (!contents.contains(
                        "(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;)I",
                    )) {
                        throw new GradleException("KMP JVM class did not lower MethodHandle.invoke with expanded FFM carrier parameters")
                    }
                    def hasSupportFallback = false
                    classRoot.eachFileRecurse(groovy.io.FileType.FILES) {
                        if (it.name.endsWith(".class") && new String(it.bytes, "ISO-8859-1").contains("WinRTProjectionSupportIntrinsic")) {
                            hasSupportFallback = true
                        }
                    }
                    if (hasSupportFallback) {
                        throw new GradleException("Compiled KMP JVM output still contains WinRTProjectionSupportIntrinsic fallback")
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
        val nugetRoot = projectDir.resolve("nuget")
        writeWindowsAppSdkPackage(
            nugetRoot = nugetRoot,
            packageId = "Microsoft.WindowsAppSDK",
            version = "1.8.260416003",
            includeWinUiWinmd = true,
        )
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
        val generatorWorkerSetup = """
            apply plugin: "io.github.compose-fluent.winrt"
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
            projectDir.resolve("winrt-base-library/build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
            }

            $generatorWorkerSetup

            winRT {
                nugetGlobalPackagesRoots.add("${nugetRoot.toString().replace("\\", "\\\\")}")
                restoreNuGetPackages.set false
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.IClosable"
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("winrt-library/build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
            }

            $generatorWorkerSetup

            winRT {
                nugetGlobalPackagesRoots.add("${nugetRoot.toString().replace("\\", "\\\\")}")
                restoreNuGetPackages.set false
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                            api project(":winrt-base-library")
                        }
                    }
                }
            }

            winRT {
                windowsSdk(null, false, true)
                type "Windows.Foundation.Uri"
            }

            tasks.named("generateWinRTProjections") {
                doLast {
                    def supportRoot = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin/kotlin-winrt-support").get().asFile
                    new File(supportRoot, "xaml-component-resources.tsv").text = "runtimeClassName\nWinUI3Package.Shimmer_Resource\n"
                    new File(supportRoot, "compiler-support.tsv").append(
                        "xaml-component-resource\tio.github.composefluent.winrt.projections.support.WinUiXamlComponentResources\txaml-component-resources.tsv\t1\n"
                    )
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("winrt-app/build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
            }

            $generatorWorkerSetup

            winRT {
                nugetGlobalPackagesRoots.add("${nugetRoot.toString().replace("\\", "\\\\")}")
                restoreNuGetPackages.set false
            }

            kotlin {
                jvm("winuiJvm")
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                            implementation project(":winrt-library")
                        }
                    }
                }
            }

            winRT {
                application {
                    mainClass.set "app.MainKt"
                }
                windowsSdk(null, false, true)
                type "Windows.Foundation.IAsyncAction"
                type "Microsoft.UI.Xaml.ResourceDictionary"
                nugetPackage("Microsoft.WindowsAppSDK", "1.8.260416003") {
                    generateProjection = true
                }
            }

            def writeTransitiveSupportProbe = tasks.register("writeTransitiveSupportProbe") {
                dependsOn("generateWinRTProjections")
                def outputFile = layout.buildDirectory.file("generated/kotlin-winrt/src/winuiMain/kotlin/app/TransitiveSupportProbe.kt")
                outputs.file(outputFile)
                doLast {
                    def target = outputFile.get().asFile
                    target.parentFile.mkdirs()
                    target.text = ${"'''"}
                    package app

                    import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic

                    object TransitiveSupportProbe {
                        fun support() {
                            WinRTProjectionSupportIntrinsic.ensureInitialized()
                        }
                    }
                    ${"'''"}.stripIndent().trim()
                }
            }

            tasks.named("compileKotlinWinuiJvm") {
                dependsOn writeTransitiveSupportProbe
            }

            tasks.register("printApplicationIdentity") {
                dependsOn("generateWinRTApplicationIdentity")
                doLast {
                    println(layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json").get().asFile.text)
                }
            }

            tasks.register("verifyTransitiveCompilerSupport") {
                dependsOn("compileKotlinWinuiJvm")
                doLast {
                    def mergedProjectionRegistrar = layout.buildDirectory.file(
                        "generated/kotlin-winrt/compiler-support/merged/projection-registrar.tsv",
                    ).get().asFile.text
                    def mergedXamlResources = layout.buildDirectory.file(
                        "generated/kotlin-winrt/compiler-support/merged/xaml-component-resources.tsv",
                    ).get().asFile.text
                    def mergedXamlBootstrap = layout.buildDirectory.file(
                        "generated/kotlin-winrt/compiler-support/merged/io/github/composefluent/winrt/projections/support/WinUiXamlComponentResources.kt",
                    ).get().asFile.text
                    [
                        "Windows.Foundation.IClosable",
                        "Windows.Foundation.IUriRuntimeClass",
                        "Windows.Foundation.IAsyncAction",
                    ].each { typeName ->
                        if (!mergedProjectionRegistrar.contains(typeName)) {
                            throw new GradleException("Merged app compiler support is missing " + typeName)
                        }
                    }
                    if (!mergedXamlResources.contains("WinUI3Package.Shimmer_Resource")) {
                        throw new GradleException("Merged app compiler support is missing dependency XAML component resources.")
                    }
                    if (!mergedXamlBootstrap.contains("ActivationFactory.activateInstance(\"WinUI3Package.Shimmer_Resource\")")) {
                        throw new GradleException("Merged app compiler support did not generate dependency XAML component resource bootstrap.")
                    }

                    def classRoot = layout.buildDirectory.dir("classes/kotlin/winuiJvm/main").get().asFile
                    def classContents = [:]
                    classRoot.eachFileRecurse(groovy.io.FileType.FILES) { compiledClass ->
                        if (compiledClass.name.endsWith(".class")) {
                            classContents[compiledClass] = new String(compiledClass.bytes, "ISO-8859-1")
                        }
                    }
                    def probe = new File(classRoot, "app/TransitiveSupportProbe.class")
                    def probeContents = classContents[probe]
                    if (probeContents == null || probeContents.contains("WinRTProjectionSupportIntrinsic")) {
                        throw new GradleException("Transitive app probe still contains WinRTProjectionSupportIntrinsic fallback")
                    }
                    if (!probeContents.contains("kotlinWinRTProjectionSupportInitialize_")) {
                        throw new GradleException("Transitive app probe did not call the compiler-generated support initializer")
                    }
                    if (classContents.values().any { it.contains("WinRTProjectionSupportIntrinsic") }) {
                        throw new GradleException("Transitive app bytecode still contains WinRTProjectionSupportIntrinsic fallback")
                    }

                    def generatedInitializers = classContents.values().findAll { contents ->
                        contents.contains("kotlinWinRTProjectionSupportInitialize_") &&
                            contents.contains("registerGeneratedProjectionTypeIndex")
                    }
                    if (generatedInitializers.size() != 1) {
                        throw new GradleException("Expected one compiler-generated projection support initializer method")
                    }
                    def generatedInitializer = generatedInitializers[0]
                    [
                        "Windows.Foundation.IClosable",
                        "Windows.Foundation.IUriRuntimeClass",
                        "Windows.Foundation.IAsyncAction",
                    ].each { typeName ->
                        if (!generatedInitializer.contains(typeName)) {
                            throw new GradleException("Compiler-generated initializer did not include " + typeName)
                        }
                    }

                    def supportRoot = new File(classRoot, "io/github/composefluent/winrt/projections/support")
                    def projectionSupportArtifacts = []
                    supportRoot.eachFileRecurse(groovy.io.FileType.FILES) {
                        if (it.name.startsWith("WinRTProjectionSupport_") && it.name.endsWith(".class")) {
                            projectionSupportArtifacts.add(it)
                        }
                    }
                    if (projectionSupportArtifacts.isEmpty()) {
                        throw new GradleException("Expected at least one content-addressed projection support artifact, found " +
                            projectionSupportArtifacts.size())
                    }
                    if (new File(supportRoot, "WinRTProjectionSupport.class").exists()) {
                        throw new GradleException("Fixed projection support artifact must not be generated")
                    }
                    if (new File(supportRoot, "WinRTInterfaceProjectionRegistry.class").exists()) {
                        throw new GradleException("Legacy interface projection registry must not be generated")
                    }
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("winrt-app/src/commonMain/kotlin/app/Main.kt"),
            """
            package app

            fun main() {
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

        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-base-library:generateWinRTIdentity")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-base-library:compileKotlinWinuiJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-library:generateWinRTIdentity")?.outcome)
        assertTrue(
            result.task(":winrt-library:compileKotlinWinuiJvm")?.outcome in
                setOf(TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE),
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-app:generateWinRTApplicationIdentity")?.outcome)
        assertTrue(
            result.task(":winrt-app:compileKotlinWinuiJvm")?.outcome in
                setOf(TaskOutcome.SUCCESS, TaskOutcome.NO_SOURCE),
        )
        assertTrue(result.output.contains("winrt-base-library"))
        assertTrue(result.output.contains("winrt-library"))
        val baseWindowsFoundationProjection = Files.readString(
            projectDir.resolve(
                "winrt-base-library/build/generated/kotlin-winrt/src/winuiMain/kotlin/windows/foundation/windows_foundation.kt",
            ),
        )
        val libraryWindowsFoundationProjection = Files.readString(
            projectDir.resolve(
                "winrt-library/build/generated/kotlin-winrt/src/winuiMain/kotlin/windows/foundation/windows_foundation.kt",
            ),
        )
        val appWindowsFoundationProjection = Files.readString(
            projectDir.resolve(
                "winrt-app/build/generated/kotlin-winrt/src/winuiMain/kotlin/windows/foundation/windows_foundation.kt",
            ),
        )
        assertTrue(baseWindowsFoundationProjection.contains("IClosable"))
        assertTrue(libraryWindowsFoundationProjection.contains("IUriRuntimeClass"))
        assertTrue(appWindowsFoundationProjection.contains("IAsyncAction"))
        assertFalse(libraryWindowsFoundationProjection.contains("IClosable"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":winrt-app:verifyTransitiveCompilerSupport")?.outcome)
    }

    @Test
    fun application_distribution_contains_windowsappsdk_framework_runtime_resources() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-app-dist-test-")
        val nugetRoot = projectDir.resolve("nuget")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
        val makePriLog = projectDir.resolve("makepri.log")
        val makePri = writeFakeMakePri(projectDir.resolve("fake-makepri.cmd"), makePriLog)
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "application"
                id "io.github.compose-fluent.winrt"
            }

            application {
                mainClass.set "sample.Main"
            }

            winRT {
                nugetGlobalPackagesRoots.add("${nugetRoot.toString().replace("\\", "\\\\")}")
                restoreNuGetPackages.set false
                nugetPackage "Microsoft.WindowsAppSDK", "1.8.260416003"
                application {
                    makePriExecutable.set "${makePri.toString().replace("\\", "\\\\")}"
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

        assertEquals(TaskOutcome.SUCCESS, result.task(":stageWinRTRuntimeAssets")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":stageWinRTApplicationPackage")?.outcome)
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

    @Test
    fun multiplatform_mingw_application_package_stages_release_executable_at_root() {
        val fixtureRoot = repositoryRoot().resolve("winrt-gradle-plugin/build/test-fixtures")
        Files.createDirectories(fixtureRoot)
        val projectDir = Files.createTempDirectory(fixtureRoot, "kotlin-winrt-mingw-package-test-")
        val runtimeJar = runtimeJarPath().toString().replace("\\", "/")
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
            rootProject.name = "kotlin-winrt-mingw-package-test"
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
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id "org.jetbrains.kotlin.multiplatform" version "2.3.20"
                id "io.github.compose-fluent.winrt"
            }

            kotlin {
                mingwX64 {
                    binaries {
                        executable()
                    }
                }
                sourceSets {
                    commonMain {
                        dependencies {
                            implementation files("$runtimeJar")
                        }
                    }
                }
            }

            winRT {
                application {
                    mainClass.set "sample.MainKt"
                    generateProjectPri.set false
                }
            }

            tasks.register("verifyMingwApplicationPackageLayout") {
                dependsOn("stageWinRTApplicationPackage")
                doLast {
                    def packageRoot = layout.buildDirectory.dir("kotlin-winrt/application-layout/mingwX64/release").get().asFile
                    def executable = new File(packageRoot, "kotlin-winrt-mingw-package-test.exe")
                    if (!executable.isFile()) {
                        throw new GradleException("Expected staged release executable at package root: " + executable)
                    }
                    if (new File(packageRoot, "bin/mingwX64/releaseExecutable/kotlin-winrt-mingw-package-test.exe").exists()) {
                        throw new GradleException("Release executable must not be staged under the raw Kotlin/Native build output path.")
                    }
                }
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/commonMain/kotlin/sample/Main.kt"),
            """
            package sample

            fun main() {
            }
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WinRTWindowsAppSdkBootstrap.kt"),
            """
            package io.github.composefluent.winrt.runtime

            object WinRTWindowsAppSdkBootstrap {
                fun initialize() {
                }

                fun initializeApplicationHost(unpackaged: Boolean = true): AutoCloseable =
                    AutoCloseable {
                    }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyMingwApplicationPackageLayout", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":linkReleaseExecutableMingwX64")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":stageWinRTApplicationPackage")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyMingwApplicationPackageLayout")?.outcome)
        assertEquals(
            2,
            readPeSubsystem(projectDir.resolve("build/kotlin-winrt/application-layout/mingwX64/release/kotlin-winrt-mingw-package-test.exe")),
        )
    }
}

private fun writeGradleFile(path: Path, content: String) {
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
}

private fun repositoryRoot(): Path =
    generateSequence(Path.of("").toAbsolutePath().normalize()) { path -> path.parent }
        .firstOrNull { path ->
            Files.isRegularFile(path.resolve("settings.gradle.kts")) &&
                Files.isDirectory(path.resolve("winrt-projections"))
        }
        ?: error("Unable to locate kotlin-winrt repository root from ${Path.of("").toAbsolutePath().normalize()}.")

private fun taskDependencyNames(task: org.gradle.api.Task): Set<String> =
    task.taskDependencies.getDependencies(task).map { it.name }.toSet()

private fun declaredTaskDependencyNames(task: org.gradle.api.Task): Set<String> =
    task.dependsOn.mapNotNull { dependency ->
        when (dependency) {
            is String -> dependency
            is org.gradle.api.Task -> dependency.name
            is org.gradle.api.tasks.TaskProvider<*> -> dependency.name
            else -> null
        }
    }.toSet()

private fun commandExists(name: String): Boolean =
    runCatching {
        ProcessBuilder("cmd.exe", "/c", "where", name)
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)

private fun readPeSubsystem(executable: Path): Int {
    val bytes = Files.readAllBytes(executable)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val peHeaderOffset = buffer.getInt(0x3c)
    val optionalHeaderOffset = peHeaderOffset + 24
    return buffer.getShort(optionalHeaderOffset + 0x44).toInt() and 0xffff
}

private inline fun <T> withSystemProperty(name: String, value: String, block: () -> T): T {
    val previous = System.getProperty(name)
    System.setProperty(name, value)
    return try {
        block()
    } finally {
        if (previous == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, previous)
        }
    }
}

private fun writeFakeMakePri(path: Path, log: Path, languagePri: String = ""): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        set output=
        :next
        if "%~1"=="" goto done
        >>"${log.toString()}" echo(%~1
        if /I "%~1"=="/of" (
          set output=%~2
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
        set command=%~1
        set output=
        set directory=
        :next
        if "%~1"=="" goto done
        >>"${log.toString()}" echo(%~1
        if /I "%~1"=="/p" (
          set output=%~2
        )
        if /I "%~1"=="/d" (
          set directory=%~2
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

private fun writeFailingFakeMakeAppx(path: Path): Path {
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

private fun writeFakeSignTool(path: Path, log: Path): Path {
    Files.createDirectories(path.parent)
    Files.writeString(
        path,
        """
        @echo off
        :writeArgs
        if "%~1"=="" exit /b 0
        >>"${log.toString()}" echo(%~1
        shift
        goto writeArgs
        exit /b 0
        """.trimIndent(),
    )
    return path
}

private fun readFakeToolArguments(log: Path): List<String> =
    Files.readAllLines(log).let { lines ->
        if (lines.size == 1) {
            splitFakeToolCommandLine(lines.single())
        } else {
            lines
        }
    }
        .map { it.trim().trim('"').replace("\\", "/") }
        .filter { it.isNotBlank() }

private fun splitFakeToolCommandLine(commandLine: String): List<String> {
    val arguments = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    commandLine.forEach { character ->
        when {
            character == '"' -> inQuotes = !inQuotes
            character.isWhitespace() && !inQuotes -> {
                if (current.isNotEmpty()) {
                    arguments += current.toString()
                    current.clear()
                }
            }
            else -> current.append(character)
        }
    }
    if (current.isNotEmpty()) {
        arguments += current.toString()
    }
    return arguments
}

private fun assertArgumentPair(arguments: List<String>, option: String, value: String) {
    val optionIndex = arguments.indexOf(option)
    assertTrue("Expected fake tool arguments to contain $option in $arguments", optionIndex >= 0)
    assertTrue(
        "Expected $option to be followed by $value in $arguments",
        optionIndex + 1 < arguments.size && arguments[optionIndex + 1] == value,
    )
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
        :writeArgs
        if "%~1"=="" exit /b 0
        >>"${log.toString()}" echo(%~1
        shift
        goto writeArgs
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

private fun assertHasKotlinWinRTRuntimeDependency(dependencies: Iterable<Dependency>) {
    assertHasKotlinWinRTClasspathDependency(dependencies, "winrt-runtime")
}

private fun assertDoesNotHaveKotlinWinRTRuntimeDependency(dependencies: Iterable<Dependency>) {
    assertDoesNotHaveKotlinWinRTClasspathDependency(dependencies, "winrt-runtime")
}

private fun assertHasKotlinWinRTAuthoringDependency(dependencies: Iterable<Dependency>) {
    assertHasKotlinWinRTClasspathDependency(dependencies, "winrt-authoring")
}

private fun assertHasKotlinWinRTClasspathDependency(
    dependencies: Iterable<Dependency>,
    moduleName: String,
) {
    assertTrue(
        dependencies.joinToString(separator = "\n") { dependency ->
            val files = (dependency as? FileCollectionDependency)
                ?.files
                ?.files
                ?.joinToString(prefix = "[", postfix = "]") { file -> file.name }
                .orEmpty()
            "${dependency::class.qualifiedName}:${dependency.group}:${dependency.name}:${dependency.version}:$files"
        },
        dependencies.any { dependency ->
            dependency.name == moduleName ||
                dependency is ProjectDependency && dependency.path == ":$moduleName" ||
                dependency is FileCollectionDependency && dependency.files.files.any { file ->
                    file.name.startsWith(moduleName) && file.name.endsWith(".jar")
                }
        },
    )
}

private fun assertHasKotlinWinRTModuleDependency(
    dependencies: Iterable<Dependency>,
    moduleName: String,
) {
    assertTrue(
        dependencies.joinToString(separator = "\n") { dependency ->
            "${dependency::class.qualifiedName}:${dependency.group}:${dependency.name}:${dependency.version}"
        },
        dependencies.any { dependency ->
            dependency.group == "io.github.compose-fluent" &&
                dependency.name == moduleName &&
                dependency.version == "0.1.0-SNAPSHOT"
        },
    )
}

private fun assertDoesNotHaveExternalKotlinWinRTModuleDependency(
    dependencies: Iterable<Dependency>,
    moduleName: String,
) {
    assertFalse(
        dependencies.joinToString(separator = "\n") { dependency ->
            "${dependency::class.qualifiedName}:${dependency.group}:${dependency.name}:${dependency.version}"
        },
        dependencies.any { dependency ->
            dependency is ExternalModuleDependency &&
                dependency.group == "io.github.compose-fluent" &&
                dependency.name == moduleName
        },
    )
}

private fun assertDoesNotHaveKotlinWinRTAuthoringDependency(dependencies: Iterable<Dependency>) {
    assertDoesNotHaveKotlinWinRTClasspathDependency(dependencies, "winrt-authoring")
}

private fun assertDoesNotHaveKotlinWinRTClasspathDependency(
    dependencies: Iterable<Dependency>,
    moduleName: String,
) {
    assertFalse(
        dependencies.joinToString(separator = "\n") { dependency ->
            "${dependency::class.qualifiedName}:${dependency.group}:${dependency.name}:${dependency.version}"
        },
        dependencies.any { dependency ->
            dependency.name == moduleName ||
                dependency is ProjectDependency && dependency.path == ":$moduleName" ||
                dependency is FileCollectionDependency && dependency.files.files.any { file ->
                    file.name.startsWith(moduleName) && file.name.endsWith(".jar")
                }
        },
    )
}

private fun writeWindowsAppSdkPackage(
    nugetRoot: Path,
    packageId: String,
    version: String,
    includeWinUiFrameworkAssets: Boolean = false,
    includeWinUiWinmd: Boolean = false,
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
    if (includeWinUiWinmd) {
        val winmdRoot = packageRoot.resolve("lib/net8.0")
        Files.createDirectories(winmdRoot)
        WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Microsoft.UI.Xaml",
            interfaces = listOf(
                WinRTPortableExecutableInterfaceDescriptor(
                    interfaceName = "Microsoft.UI.Xaml.IResourceDictionary",
                    iid = "11111111-2222-3333-4444-555555555555",
                ),
            ),
            runtimeClasses = listOf(
                WinRTAuthoredRuntimeClassDescriptor(
                    runtimeClassName = "Microsoft.UI.Xaml.ResourceDictionary",
                    interfaceNames = listOf("Microsoft.UI.Xaml.IResourceDictionary"),
                ),
            ),
            outputFile = winmdRoot.resolve("Microsoft.UI.Xaml.winmd"),
        )
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
