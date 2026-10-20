package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NativeProjectionReferenceArtifactTest(private val kotlinVersion: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "Kotlin {0}")
        fun versions(): List<Array<String>> = projectionTestCompilerVersions().map { arrayOf(it) }
    }
    @get:Rule val temporary = TemporaryFolder()

    @Test fun empty_selected_projection_keeps_native_artifact_and_recovers_when_types_appear() {
        // Existing owner-scoped support anchors keep KGP from treating a selection with no
        // projected types as NO-SOURCE. No configuration-time WinMD scan is necessary.
        val root = temporary.newFolder()
        root.resolve("producer").mkdirs()
        root.resolve("settings.gradle").writeText("""
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = 'empty-projection'
            include 'producer'
        """.trimIndent())
        root.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC\norg.gradle.workers.max=1\n")
        val metadata = root.resolve("empty.winmd").toPath()
        fun writeMetadata(withType: Boolean) =
            io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
                assemblyName = "Empty",
                interfaces = if (withType) listOf(io.github.composefluent.winrt.metadata.WinRTPortableExecutableInterfaceDescriptor(
                    interfaceName = "Empty.IProbe", iid = "00000000-0000-0000-0000-000000000001")) else emptyList(),
                runtimeClasses = emptyList(), outputFile = metadata,
            )
        writeMetadata(false)
        root.resolve("build.gradle").writeText("""
            plugins {
                id 'org.jetbrains.kotlin.multiplatform' version '$kotlinVersion'
                id 'io.github.compose-fluent.windows-toolkit' apply false
            }
            kotlin {
                mingwX64 {
                    def empty = compilations.create('empty')
                    empty.defaultSourceSet.kotlin.srcDir(project(':producer').layout.buildDirectory.dir('generated/kotlin-winrt/reference/src'))
                    empty.compileTaskProvider.configure {
                        dependsOn ':producer:generateWinRTReferenceProjections'
                        produceUnpackagedKlib.set(false)
                        compilerOptions.moduleName.set('empty-selected-projection')
                    }
                }
            }
            project(':producer') {
                apply plugin: 'java-library'
                apply plugin: 'io.github.compose-fluent.windows-toolkit'
                windows { packageReferences {
                    restoreNuGetPackages.set(false)
                    winmd rootProject.file('empty.winmd').absolutePath
                } }
            }
        """.trimIndent())
        fun runner(vararg tasks: String) = GradleRunner.create().withProjectDir(root).withProjectionTestEnvironment()
            .withArguments(*tasks, "--configuration-cache", "--stacktrace", "--console=plain")
        val result = runner("compileEmptyKotlinMingwX64").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileEmptyKotlinMingwX64")?.outcome)
        val sourceRoot = root.resolve("producer/build/generated/kotlin-winrt/reference/src")
        val emptySources = sourceRoot.walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(emptySources.isNotEmpty())
        assertTrue(emptySources.any { it.readText().contains("Initialize()") })
        assertFalse(emptySources.any { it.readText().contains("interface IProbe") })
        assertTrue(root.resolve("build/classes/kotlin/mingwX64/empty/klib").walkTopDown().any { it.extension == "klib" })
        // Restore creates its lockfile on the cold run. The package-input model currently
        // observes that first creation; compilation must still stay up to date.
        val restored = runner("compileEmptyKotlinMingwX64").build()
        assertEquals(TaskOutcome.UP_TO_DATE, restored.task(":compileEmptyKotlinMingwX64")?.outcome)
        val warm = runner("compileEmptyKotlinMingwX64").build()
        assertTrue(warm.output, warm.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, warm.task(":compileEmptyKotlinMingwX64")?.outcome)
        writeMetadata(true)
        runner(":producer:generateWinRTReferenceProjections").build()
        assertTrue(sourceRoot.walkTopDown().any { it.extension == "kt" && it.readText().contains("interface IProbe") })
    }

    @Test fun compiles_against_reference_links_only_implementation_and_checks_inline_contract() {
        val root = temporary.newFolder()
        fun write(path: String, content: String) = root.resolve(path).apply {
            parentFile.mkdirs(); writeText(content.trimIndent())
        }
        write("settings.gradle.kts", """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "native-reference-test"
        """)
        write("gradle.properties", """
            org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC
            org.gradle.workers.max=1
        """)
        write("build.gradle.kts", """
            import io.github.composefluent.windows.toolkit.gradle.VerifyWinRTNativeReferenceAbiTask
            import org.gradle.jvm.toolchain.JavaToolchainService
            import org.gradle.jvm.toolchain.JavaLanguageVersion
            plugins {
                kotlin("multiplatform") version "$kotlinVersion"
                id("io.github.compose-fluent.windows-toolkit") apply false
            }
            kotlin {
                mingwX64 {
                    val reference = compilations.create("reference")
                    val implementation = compilations.create("implementation")
                    listOf(reference, implementation).forEach { compilation ->
                        compilation.compileTaskProvider.configure {
                            compilerOptions.moduleName.set("canonical-projection")
                            produceUnpackagedKlib.set(false)
                        }
                    }
                    val main = compilations.getByName("main")
                    val referenceKlib = reference.compileTaskProvider.flatMap { it.outputFile }
                    val implementationKlib = implementation.compileTaskProvider.flatMap { it.outputFile }
                    main.defaultSourceSet.dependencies { implementation(files(referenceKlib)) }
                    project.afterEvaluate {
                        main.compileTaskProvider.configure {
                            libraries.from(files(referenceKlib).builtBy(reference.compileTaskProvider))
                            dependsOn(reference.compileTaskProvider)
                        }
                        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
                        val compilerHome = reference.compileTaskProvider.get().konanHome
                        tasks.register<VerifyWinRTNativeReferenceAbiTask>("verifyReference") {
                            dependsOn(reference.compileTaskProvider, implementation.compileTaskProvider)
                            this.referenceKlib.set(layout.file(referenceKlib))
                            this.implementationKlib.set(layout.file(implementationKlib))
                            compilerClasspath.from(compilerHome.map { file(it).resolve("konan/lib/kotlin-native-compiler-embeddable.jar") })
                            javaLauncher.set(project.extensions.getByType<JavaToolchainService>().launcherFor {
                                languageVersion.set(JavaLanguageVersion.of(25))
                            })
                            reportDirectory.set(layout.buildDirectory.dir("reports/abi"))
                        }
                    }
                    binaries.executable {
                        entryPoint = "main"
                        project.afterEvaluate { linkTaskProvider.configure {
                            dependsOn(implementation.compileTaskProvider)
                            libraries.setFrom(main.compileTaskProvider.flatMap { it.outputFile }, implementationKlib,
                                main.compileDependencyFiles.filter { it != referenceKlib.get() })
                        } }
                    }
                }
            }
        """)
        val reference = write("src/mingwX64Reference/kotlin/Projection.kt", """
            package sample
            class Box(val value: Int = 41) { fun increment(): Int = throw UnsupportedOperationException("reference") }
            @PublishedApi internal fun <T> bridge(value: T): T = throw UnsupportedOperationException("reference")
            inline fun <reified T> identity(value: T): T = bridge(value)
            enum class Choice { First, Second }
        """)
        val implementation = write("src/mingwX64Implementation/kotlin/Projection.kt", """
            package sample
            class Box(val value: Int = 41) { fun increment(): Int = value + 1 }
            @PublishedApi internal fun <T> bridge(value: T): T = value
            inline fun <reified T> identity(value: T): T = bridge(value)
            enum class Choice { First, Second }
        """)
        write("src/mingwX64Main/kotlin/Main.kt", """
            import sample.*
            fun main() {
                check(identity("implementation") == "implementation")
                check(Choice.entries.size == 2)
                println("projection-value=" + Box().increment())
            }
        """)
        fun runner(vararg tasks: String) = GradleRunner.create().withProjectDir(root).withProjectionTestEnvironment()
            .withArguments(*tasks, "--stacktrace", "--console=plain")
        val first = runner("verifyReference", "runDebugExecutableMingwX64").build()
        assertTrue(first.output, first.output.contains("projection-value=42"))
        implementation.writeText(implementation.readText().replace("value + 1", "value + 2"))
        val second = runner("verifyReference", "runDebugExecutableMingwX64").build()
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":compileKotlinMingwX64")?.outcome)
        assertTrue(second.output, second.output.contains("projection-value=43"))
        reference.writeText(reference.readText().replace("= bridge(value)", "= bridge(value).also { println(42) }"))
        val incompatible = runner("verifyReference").buildAndFail()
        assertTrue(incompatible.output, incompatible.output.contains("reference inline bodies differ"))
    }
}
