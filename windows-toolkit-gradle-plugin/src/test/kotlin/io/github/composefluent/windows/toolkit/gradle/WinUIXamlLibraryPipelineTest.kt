package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinUIXamlLibraryPipelineTest {
    @Test
    fun generated_xaml_directories_do_not_invalidate_cleanup_configuration() {
        // CsWinRT cleanup runs as a build target; generated output discovery must not
        // become a configuration input in the corresponding Gradle cleanup task.
        val root = writeLibrary("kotlin-winrt-xaml-cleanup-cache-", plainJvm = true)
        run(root, "removeStaleWinRTXamlOutputs", "--configuration-cache")
        val branches = listOf("build/generated/kotlin-winrt/xaml", "build/intermediates/kotlin-winrt/xaml")
        branches.forEach { branch ->
            write(root.resolve("$branch/library/keep.txt"), "library")
            write(root.resolve("$branch/application/keep.txt"), "schema")
            write(root.resolve("$branch/compiler-references.txt"), "shared compiler inputs")
            write(root.resolve("$branch/obsolete/stale.txt"), "obsolete")
        }
        val warm = run(root, "removeStaleWinRTXamlOutputs", "--configuration-cache")
        assertTrue(warm.output, warm.output.contains("Reusing configuration cache"))
        branches.forEach { branch ->
            assertTrue(Files.isRegularFile(root.resolve("$branch/library/keep.txt")))
            assertTrue(Files.isRegularFile(root.resolve("$branch/application/keep.txt")))
            assertTrue(Files.isRegularFile(root.resolve("$branch/compiler-references.txt")))
            assertTrue(!Files.exists(root.resolve("$branch/obsolete")))
        }
        val unchanged = run(root, "removeStaleWinRTXamlOutputs", "--configuration-cache")
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":removeStaleWinRTXamlOutputs")?.outcome)
    }

    @Test
    fun projection_identity_does_not_compile_inferred_library_schema() {
        val root = writeLibrary("kotlin-winrt-xaml-reference-identity-", plainJvm = true)
        val result = run(root, "prepareWinRTIdentity", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, result.task(":prepareWinRTIdentity")?.outcome)
        assertTrue(result.output, result.task(":compileKotlin") == null)
        assertTrue(result.output, result.task(":generateWinRTProjections") == null)
        assertTrue(result.output, result.task(":generateWinRTXamlApplicationHeader") == null)
    }

    @Test
    fun header_reuses_type_selection_when_only_literal_markup_changes() {
        // XAMLC DirectUISchemaContext owns namespace aliases; the Kotlin header only selects
        // application types. Unlike XBF, its schema is independent of literal property values.
        val root = writeLibrary("kotlin-winrt-xaml-header-inputs-", """
            tasks.named('generateWinRTXamlApplicationHeader') { emitSources = false }
        """.trimIndent(), plainJvm = true)
        val markup = root.resolve("src/main/kotlin/Theme.xaml")
        val original = """<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:local="using:sample"><local:Model title="Before" /></ResourceDictionary>"""
        write(markup, original)
        assertEquals(TaskOutcome.SUCCESS, run(root, "generateWinRTXamlApplicationHeader", "--configuration-cache")
            .task(":generateWinRTXamlApplicationHeader")?.outcome)
        // The first restore creates its lock; warm the resulting configuration once.
        run(root, "generateWinRTXamlApplicationHeader", "--configuration-cache")
        write(markup, original.replace("Before", "After"))
        val literal = run(root, "generateWinRTXamlApplicationHeader", "--configuration-cache")
        assertEquals(TaskOutcome.UP_TO_DATE, literal.task(":generateWinRTXamlApplicationHeader")?.outcome)
        assertTrue(literal.output, literal.output.contains("Reusing configuration cache"))
        write(markup, original.replace("local:Model", "local:Other"))
        assertEquals(TaskOutcome.SUCCESS, run(root, "generateWinRTXamlApplicationHeader", "--configuration-cache")
            .task(":generateWinRTXamlApplicationHeader")?.outcome)
        write(root.resolve("src/main/kotlin/sample/Model.kt"), "package sample; class Model { var title: Int = 0 }")
        assertEquals(TaskOutcome.SUCCESS, run(root, "generateWinRTXamlApplicationHeader", "--configuration-cache")
            .task(":generateWinRTXamlApplicationHeader")?.outcome)
    }

    @Test
    fun sdk_preview_preparation_serializes_without_capturing_other_tasks() {
        // The SDK-only designer mirrors CsWinRT's reusable projection/SDK resource ownership.
        // Skip native/compiler execution to exercise serialization of the actual preview task graph.
        val root = writeLibrary("kotlin-winrt-sdk-preview-cache-", """
            windows { application { } }
            tasks.configureEach { enabled = false }
        """.trimIndent(), plainJvm = true)
        val first = run(root, "prepareWinRTXamlSdkPreview", "--configuration-cache")
        assertEquals(TaskOutcome.SKIPPED, first.task(":prepareWinRTXamlSdkPreview")?.outcome)
        assertTrue("The preview must not schedule business compilation", first.task(":compileKotlin") == null)
        assertTrue("The preview must use its own SDK subset", first.task(":generateWinRTXamlSdkPreviewProjections") != null)
        assertTrue("The preview must not generate the application projection", first.task(":generateWinRTProjections") == null)
        assertTrue("The preview must not compile the application projection",
            first.tasks.none { it.path.contains("KotlinWinRTProjection") })
        val reused = run(root, "prepareWinRTXamlSdkPreview", "--configuration-cache")
        assertTrue(reused.output, reused.output.contains("Reusing configuration cache"))
    }

    @Test
    fun sdk_preview_uwp_package_does_not_compile_or_stage_the_application() {
        val root = writeLibrary("kotlin-winrt-uwp-sdk-preview-cache-", """
            windows { application { applicationModel = io.github.composefluent.winrt.runtime.WinAppApplicationModel.UWP } }
            // This graph-only fixture does not publish Native runtime KLIBs.
            tasks.named('buildWinRTXamlSdkPreviewNative') { runtimeLibraries.setFrom([]) }
            tasks.configureEach { enabled = false }
        """.trimIndent(), plainJvm = true)
        val first = run(root, "runWinRTXamlSdkPreviewUwp", "--configuration-cache")
        assertEquals(TaskOutcome.SKIPPED, first.task(":runWinRTXamlSdkPreviewUwp")?.outcome)
        assertTrue(first.task(":buildWinRTXamlSdkPreviewNative") != null)
        assertTrue(first.task(":buildWinRTXamlSdkPreview") == null)
        assertTrue(first.task(":stageWinRTXamlSdkPreviewPackage") != null)
        assertTrue(first.task(":generateWinRTXamlSdkPreviewProjections") != null)
        assertTrue(first.task(":compileKotlin") == null)
        assertTrue(first.task(":generateWinRTProjections") == null)
        assertTrue(first.tasks.none { it.path.contains("stageWinAppPackage") || it.path.contains("compileWinRTXaml") })
        val reused = run(root, "runWinRTXamlSdkPreviewUwp", "--configuration-cache")
        assertTrue(reused.output, reused.output.contains("Reusing configuration cache"))
    }

    @Test
    fun plain_jvm_xaml_main_compilation_has_distinct_tasks_and_reuses_configuration_cache() {
        val root = writeLibrary("kotlin-winrt-xaml-jvm-main-", "windows { application { } }", plainJvm = true)
        write(root.resolve("src/main/kotlin/sample/Model.xaml"), """
            <Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
                  xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.Model" />
        """)

        val result = run(root, "tasks", "--all", "--configuration-cache")
        assertTrue(result.output, result.output.contains("generateWinRTXamlApplicationHeader -"))
        assertTrue(result.output, result.output.contains("generateWinRTXamlApplicationHeaderMain"))
        assertTrue(result.output, result.output.contains("analyzeWinRTXamlMain"))
        assertTrue(result.output, !result.output.contains("compileKotlinWinRTXamlSemanticMain"))
        assertTrue(result.output, result.output.contains("compileWinRTXamlMain"))
        val reused = run(root, "tasks", "--all", "--configuration-cache")
        assertTrue(reused.output, reused.output.contains("Reusing configuration cache."))
    }

    // Only markup selects the declarations that the application header describes. A library
    // without XAML takes the header's references and dependency schemas, never its own sources,
    // so a source root that another task generates is not an undeclared input of the header.
    @Test
    fun library_header_without_xaml_does_not_read_generated_source_roots() {
        val root = writeLibrary("kotlin-winrt-xaml-library-header-")
        // The generated root has to exist before the header resolves its source roots.
        run(root, "generateVersion")

        val result = run(root, "generateVersion", "generateWinRTXamlApplicationHeader")

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":generateVersion")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTXamlApplicationHeader")?.outcome)
        assertTrue(Files.isRegularFile(root.resolve("build/generated/kotlin-winrt/xaml/application/KotlinXaml.winmd")))
    }

    @Test
    fun library_schema_export_is_on_by_default_and_can_be_switched_off() {
        val exported = run(writeLibrary("kotlin-winrt-xaml-library-export-"), "inspectSchemaExport")
        assertTrue(exported.output, exported.output.contains(
            "schemaExport=generateWinRTXamlLibraryRegistrarLibraryDesktop;"))

        val byExtension = run(
            writeLibrary("kotlin-winrt-xaml-library-no-export-", "windows { xaml { exportLibrarySchema = false } }"),
            "generateWinRTXamlApplicationHeader", "inspectSchemaExport",
        )
        assertEquals(TaskOutcome.SKIPPED, byExtension.task(":generateWinRTXamlApplicationHeader")?.outcome)
        assertTrue(byExtension.output, byExtension.output.contains("schemaExport=;"))

        val byProperty = run(writeLibrary("kotlin-winrt-xaml-library-no-export-property-"),
            "generateWinRTXamlApplicationHeader", "inspectSchemaExport", "-PkotlinWinRT.xaml.exportLibrarySchema=false")
        assertEquals(TaskOutcome.SKIPPED, byProperty.task(":generateWinRTXamlApplicationHeader")?.outcome)
        assertTrue(byProperty.output, byProperty.output.contains("schemaExport=;"))
    }

    // Schema export runs inside the business compilation, so only the
    // compilation of the library itself is validated against the source scan.
    @Test
    fun combined_library_compilation_has_one_authored_candidate_validation() {
        val result = run(writeLibrary("kotlin-winrt-xaml-library-validation-"), "inspectSchemaExport")

        assertTrue(result.output, result.output.contains(
            "candidateValidation=validateCompileKotlinLibraryDesktopWinRTAuthoredCandidates;"))
    }

    private fun run(root: Path, vararg arguments: String) =
        GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments(*arguments, "--offline", "--stacktrace").build()

    private fun writeLibrary(prefix: String, configuration: String = "", plainJvm: Boolean = false): Path {
        val root = Files.createTempDirectory(prefix)
        write(root.resolve("settings.gradle"), """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = 'xaml-library'
        """)
        write(root.resolve("gradle.properties"), """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
        """)
        write(root.resolve("build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.${if (plainJvm) "jvm" else "multiplatform"}'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { mavenCentral() }
            def generateVersion = tasks.register('generateVersion') {
                def output = layout.buildDirectory.dir('generated/version')
                outputs.dir(output)
                doLast {
                    def source = output.get().file('sample/Version.kt').asFile
                    source.parentFile.mkdirs()
                    source.text = 'package sample\nobject Version { const val NAME = "1.0" }\n'
                }
            }
            ${if (plainJvm) "" else """
            kotlin {
                jvm('libraryDesktop')
                sourceSets.commonMain.kotlin.srcDir(generateVersion)
            }
            """}
            $configuration
            tasks.register('inspectSchemaExport') {
                doLast {
                    println 'schemaExport=' +
                        tasks.names.findAll { it.startsWith('generateWinRTXamlLibraryRegistrar') }.sort().join(',') + ';'
                    println 'candidateValidation=' +
                        tasks.names.findAll { it.endsWith('WinRTAuthoredCandidates') }.sort().join(',') + ';'
                }
            }
        """)
        write(root.resolve("src/${if (plainJvm) "main" else "commonMain"}/kotlin/sample/Model.kt"), """
            package sample

            class Model { var title: String = "" }
        """)
        return root
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content.trimIndent() + System.lineSeparator())
    }
}
