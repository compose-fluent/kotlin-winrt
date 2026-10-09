package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.gradle.tooling.GradleConnector
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Path
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator

/** CsWinRT SourceGenerator.props builds shared sources against each supported compiler API. */
class KotlinCompilerCompatibilityTest {
    @Test
    fun published_plugin_imports_the_ide_model_without_a_separate_model_dependency() {
        val fixture = fixture("2.4.0")
        val repository = requireNotNull(System.getProperty("winrt.test.repository"))
        // Test the published payload, not TestKit's injected model JAR. Block the
        // old coordinate even if another local build previously published it.
        write(fixture, "settings.gradle", """
            pluginManagement {
                repositories {
                    maven { url = uri('$repository'); content { excludeModule 'io.github.composefluent.winrt', 'ide-model' } }
                    mavenCentral { content { excludeModule 'io.github.composefluent.winrt', 'ide-model' } }
                    gradlePluginPortal { content { excludeModule 'io.github.composefluent.winrt', 'ide-model' } }
                }
            }
            rootProject.name = 'published-ide-model'
        """)
        Files.writeString(fixture.resolve("build.gradle"), "\n" + """
            tasks.configureEach {
                doFirst { throw new GradleException('IDE import must not execute tasks') }
            }
        """.trimIndent(), java.nio.file.StandardOpenOption.APPEND)
        GradleConnector.newConnector().forProjectDirectory(fixture.toFile())
            .useGradleVersion(GradleVersion.current().version).connect().use { connection ->
                val model = connection.model(WinRTIdeModel::class.java)
                    .setJavaHome(File(System.getProperty("java.home")))
                    .withArguments("--no-configuration-cache", "--max-workers=1", "--stacktrace").get()
                assertTrue(model.isEnabled)
                assertEquals(WinRTIdeModel.SCHEMA_VERSION, model.schemaVersion)
                assertEquals(":", model.projectPath)
                assertEquals("2.4.0", model.kotlinVersion)
                assertTrue(model.sourceSets.any { it.name == "main" })
                assertTrue(model.targets.any { it.platform == "jvm" })
            }
    }

    @Test
    fun inactive_published_plugin_resolves_on_jdk_21_and_reuses_configuration_cache() {
        val fixture = fixture("2.4.20")
        val publicationVersion = requireNotNull(System.getProperty("winrt.test.publicationVersion"))
        write(fixture, "build.gradle", """
            plugins {
                id 'io.github.compose-fluent.windows-toolkit' version '$publicationVersion' apply false
                id 'org.jetbrains.kotlin.jvm' version '2.4.20'
            }
            assert !plugins.hasPlugin('io.github.compose-fluent.windows-toolkit')
            abstract class VerifyInactiveJdk extends DefaultTask {
                @TaskAction void verify() {
                    assert Runtime.version().feature() == 21
                    println 'WINRT_INACTIVE_JDK:21'
                }
            }
            tasks.register('verifyInactiveJdk', VerifyInactiveJdk)
        """)
        val javaHome = "-Dorg.gradle.java.home=${jdk21()}"
        val result = build(fixture, "verifyInactiveJdk", javaHome, "--configuration-cache")
        assertTrue(result.output, result.output.contains("WINRT_INACTIVE_JDK:21"))
        val reused = build(fixture, "verifyInactiveJdk", javaHome, "--configuration-cache")
        assertTrue(reused.output, reused.output.contains("Reusing configuration cache"))
    }

    @Test
    fun applying_published_plugin_on_jdk_21_reports_the_required_jdk() {
        val fixture = fixture("2.4.0")
        val result = build(fixture, "help", "-Dorg.gradle.java.home=${jdk21()}", fail = true)
        assertTrue(result.output, result.output.contains("The Windows toolkit requires JDK 25 or newer when applied."))
        assertFalse(result.output, result.output.contains("UnsupportedClassVersionError"))
        assertFalse(result.output, result.output.contains("No matching variant"))
    }

    @Test fun kotlin_2_4_0_compiles_with_matching_plugins() = compileJvm("2.4.0")
    @Test fun kotlin_2_4_20_compiles_with_matching_plugins() = compileJvm("2.4.20")
    @Test fun kotlin_2_4_0_compiles_native_with_matching_plugins() = compileNative("2.4.0")
    @Test fun kotlin_2_4_20_compiles_native_with_matching_plugins() = compileNative("2.4.20")

    @Test
    fun downstream_projection_compiles_with_an_internal_dependency_interface_on_both_targets() {
        // CsWinRT's exclusive interface belongs to its projection assembly. Kotlin
        // cannot name that internal declaration from a dependent module; keep ABI calls.
        listOf(false, true).forEach { native ->
            val fixture = fixture("2.4.0", native)
            Files.writeString(fixture.resolve("settings.gradle"), "\ninclude ':dependency'\n", java.nio.file.StandardOpenOption.APPEND)
            write(fixture, "dependency/build.gradle", """
                apply plugin: 'org.jetbrains.kotlin.${if (native) "multiplatform" else "jvm"}'
                kotlin { ${if (native) "mingwX64()" else "jvmToolchain(25)"} }
                repositories { mavenCentral() }
            """)
            val sourceSet = if (native) "commonMain" else "main"
            write(fixture, "dependency/src/$sourceSet/kotlin/InternalInterface.kt", """
                package sample.search
                internal interface IQueryOptionsAdditionalSearchSources {
                    val additionalSearchSourcesCount: Int
                }
            """)
            val dependency = if (native) "kotlin.sourceSets.commonMain.dependencies { implementation project(':dependency') }"
                else "dependencies { implementation project(':dependency') }"
            Files.writeString(fixture.resolve("build.gradle"), "\n$dependency\n", java.nio.file.StandardOpenOption.APPEND)
            KotlinProjectionGenerator(
                projectionContext = WinRTMetadataProjectionContext(
                    sources = emptyList(), inaccessibleDependencyTypes = setOf(HIDDEN_QUERY_INTERFACE),
                ),
                suppressedProjectionTypeNames = setOf(HIDDEN_QUERY_INTERFACE),
            ).generate(projectionVisibilityModel()).filter { it.relativePath.endsWith(".kt") }.forEach { file ->
                write(fixture, "src/$sourceSet/kotlin/${file.relativePath}", file.contents)
            }
            val task = if (native) ":compileKotlinMingwX64" else ":compileKotlin"
            assertEquals(TaskOutcome.SUCCESS, build(fixture, task).task(task)?.outcome)
        }
    }

    @Test
    fun unsupported_compiler_fails_before_loading_ir_plugins() {
        val fixture = fixture("2.3.20")
        val result = build(fixture, "verifyCompilerArtifacts", fail = true)
        assertTrue(result.output, result.output.contains("kotlin-winrt does not support Kotlin 2.3.20"))
        assertTrue(result.output, result.output.contains("Supported Kotlin compiler versions: 2.4.0, 2.4.20"))
        assertFalse(result.output, result.output.contains("NoSuchMethodError"))
    }

    private fun compileJvm(version: String) {
        val fixture = fixture(version)
        listOf("First", "Second").forEach { name ->
            write(fixture, "src/main/kotlin/$name.kt", """
                package sample
                import io.github.composefluent.winrt.runtime.*
                @WinRTAbiCallSite
                fun invoke$name(receiver: RawComPtr, slot: Int, value: RawAddress): Int = TODO("ABI")
            """)
        }
        val result = build(fixture, "compileKotlin", "verifyCompilerArtifacts", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlin")?.outcome)
        assertTrue(result.output, result.output.contains("WINRT_COMPILER_MATCH:$version"))
        val abiOwners = fixture.resolve("build/classes/kotlin/main/io/github/composefluent/winrt/generated/abi")
        assertTrue("Shared ABI owners were not generated", Files.isDirectory(abiOwners))
        assertEquals(1L, Files.list(abiOwners).use { entries -> entries.filter { it.toString().endsWith(".class") }.count() })
        // The first build creates generated source roots which the source scanner discovers
        // on the next configuration. Once those roots exist, artifact selection must cache.
        build(fixture, "compileKotlin", "verifyCompilerArtifacts", "--configuration-cache")
        val reused = build(fixture, "compileKotlin", "verifyCompilerArtifacts", "--configuration-cache")
        assertTrue(reused.output, reused.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":compileKotlin")?.outcome)
    }

    private fun compileNative(version: String) {
        val fixture = fixture(version, native = true)
        write(fixture, "src/mingwX64Main/kotlin/Main.kt", """
            package sample
            import io.github.composefluent.winrt.runtime.*
            @WinRTAbiCallSite
            fun invokeNative(receiver: RawComPtr, slot: Int, value: RawAddress): Int = TODO("ABI")
            fun main() {
                RuntimeScope.initializeMultithreaded().use { println("WINRT_NATIVE_MATCH:$version") }
            }
        """)
        val result = build(fixture, "linkDebugExecutableMingwX64", "verifyCompilerArtifacts", "--configuration-cache")
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinMingwX64")?.outcome)
        assertTrue(result.output, result.output.contains("WINRT_COMPILER_MATCH:$version"))
        val binary = fixture.resolve("build/bin/mingwX64/debugExecutable/compiler-compatibility.exe")
        val output = fixture.resolve("native-output.log").toFile()
        val process = ProcessBuilder(binary.toString()).redirectErrorStream(true).redirectOutput(output).start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Native compiler compatibility fixture timed out")
        }
        assertEquals(output.readText(), 0, process.exitValue())
        assertTrue(output.readText(), output.readText().contains("WINRT_NATIVE_MATCH:$version"))
    }

    private fun fixture(version: String, native: Boolean = false): Path {
        // Keep the parent stable for configuration-cache inputs: other tests/processes
        // create files in the system Temp directory while this fixture is being compiled.
        val directory = Files.createDirectory(Files.createTempDirectory("winrt-kotlin-$version-").resolve("project"))
        Files.createDirectory(directory.parent.resolve("logs"))
        val repository = requireNotNull(System.getProperty("winrt.test.repository")) { "Run compilerCompatibilityTest" }
        val publicationVersion = requireNotNull(System.getProperty("winrt.test.publicationVersion"))
        write(directory, "settings.gradle", """
            pluginManagement { repositories { maven { url = uri('$repository') }; mavenCentral(); gradlePluginPortal() } }
            rootProject.name = 'compiler-compatibility'
        """)
        write(directory, "gradle.properties", """
            org.gradle.jvmargs=-Xmx768m -XX:+UseSerialGC -Dfile.encoding=UTF-8
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
            kotlin.mpp.applyDefaultHierarchyTemplate=false
        """)
        write(directory, "build.gradle", """
            plugins {
                id 'io.github.compose-fluent.windows-toolkit' version '$publicationVersion'
                id 'org.jetbrains.kotlin.${if (native) "multiplatform" else "jvm"}' version '$version'
            }
            repositories { maven { url = uri('$repository') }; mavenCentral() }
            kotlin { ${if (native) "mingwX64 { binaries { executable { entryPoint = 'sample.main' } } }" else "jvmToolchain(25)"} }
            abstract class VerifyCompilerArtifacts extends DefaultTask {
                @InputFiles abstract ConfigurableFileCollection getCompilerFiles()
                @InputFiles abstract ConfigurableFileCollection getCompilationCompilerFiles()
                @Input abstract Property<String> getKotlinVersion()
                @TaskAction void verify() {
                    [compilerFiles, compilationCompilerFiles].each { classpath ->
                    def plugins = classpath.files.findAll {
                        it.name.startsWith('winrt-compiler-plugin-') || it.name.startsWith('callsite-lowering-')
                    }
                    assert plugins.size() == 2 : plugins
                    plugins.each { artifact ->
                        new java.util.jar.JarFile(artifact).withCloseable { jar ->
                            assert jar.manifest.mainAttributes.getValue('Kotlin-WinRT-Compiler-Version') == kotlinVersion.get() : artifact
                        }
                    }
                    }
                    println 'WINRT_COMPILER_MATCH:' + kotlinVersion.get()
                }
            }
            tasks.register('verifyCompilerArtifacts', VerifyCompilerArtifacts) {
                compilerFiles.from(configurations.kotlinWinRTCompilerPlugin)
                compilationCompilerFiles.from(configurations.${if (native) "kotlinCompilerPluginClasspathMingwX64Main" else "kotlinCompilerPluginClasspathMain"})
                kotlinVersion.set('$version')
            }
        """)
        return directory
    }

    private var buildNumber = 0

    private fun jdk21(): Path {
        val explicit = System.getenv("WINRT_TEST_JDK_21")?.let(Path::of)
        val gradleHome = System.getenv("GRADLE_USER_HOME")?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".gradle")
        val candidates = listOfNotNull(explicit) + gradleHome.resolve("jdks").toFile()
            .listFiles().orEmpty().filter { it.isDirectory }.map { it.toPath() }
        return requireNotNull(candidates.firstOrNull { candidate ->
            Files.isRegularFile(candidate.resolve("bin/java.exe")) &&
                candidate.resolve("release").toFile().takeIf { it.isFile }?.readLines()
                    ?.any { it.startsWith("JAVA_VERSION=\"21.") } == true
        }) { "Install JDK 21 or set WINRT_TEST_JDK_21 to validate inactive plugin resolution" }
    }

    private fun build(directory: Path, vararg arguments: String, fail: Boolean = false) =
        Files.newBufferedWriter(directory.parent.resolve("logs/build-${buildNumber++}.log")).use { log ->
            val runner = GradleRunner.create()
                .withProjectDir(directory.toFile())
                .withArguments(*arguments, "--console=plain", "--max-workers=1", "--stacktrace")
                .forwardStdOutput(log).forwardStdError(log)
            if (fail) runner.buildAndFail() else runner.build()
        }

    private fun write(directory: Path, relative: String, source: String) {
        val file = directory.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, source.trimIndent())
    }
}
