package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

// The Android Gradle plugin rejects every configuration that is resolved before the projects are
// evaluated when android.dependencyResolutionAtConfigurationTime.disallow is set. The fixtures
// apply the same rule without the Android plugin.
class ConfigurationTimeResolutionTest {
    @Test
    fun projections_are_generated_where_configurations_must_not_be_resolved_during_configuration() {
        val result = generateProjections("kotlin { jvm('desktop') }")

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
    }

    // With a Native target and a local projection, the toolkit also asks after evaluation whether
    // the dependencies leave anything to generate, which reads the same identities.
    @Test
    fun native_projection_is_configured_where_configurations_must_not_be_resolved_during_configuration() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))

        val result = generateProjections("""
            kotlin {
                jvm('desktop')
                mingwX64()
            }
            windows { packageReferences { windowsSdk(null, false, true); type 'Windows.Foundation.IStringable' } }
        """)

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateWinRTProjections")?.outcome)
    }

    // A resolution makes Gradle look at the variants of the projects in its graph as they are, and
    // keep what it found. While a project is being configured the Kotlin plugin has not completed
    // its variants: the C interop elements of two Native targets are still identical, which Gradle
    // rejects, and then rejects for every consumer of the project. So the toolkit resolves nothing
    // of a project in its afterEvaluate callbacks, and still prepares the imported projection of a
    // project that is not the root one through the generation task.
    @Test
    fun a_library_is_resolved_and_prepared_only_once_it_is_configured() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))

        val root = Files.createTempDirectory("kotlin-winrt-configuration-time-observation-")
        write(root.resolve("settings.gradle"), """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = 'configuration-time-observation'
            include 'library', 'consumer'
        """)
        write(root.resolve("gradle.properties"), """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            kotlin.mpp.applyDefaultHierarchyTemplate=false
        """)
        write(root.resolve("library/build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { mavenCentral() }
            kotlin {
                jvm('desktop')
                linuxX64()
                linuxArm64()
            }
            windows { packageReferences { windowsSdk(null, false, true); type 'Windows.Foundation.IStringable' } }

            def resolvedWhileConfiguring = []
            configurations.configureEach { configuration ->
                configuration.incoming.beforeResolve {
                    // 'EXECUTING' covers the build script and the afterEvaluate callbacks.
                    if (project.state.toString().contains('EXECUTING')) {
                        resolvedWhileConfiguring.add(configuration.name)
                    }
                }
            }
            gradle.taskGraph.whenReady {
                if (!resolvedWhileConfiguring.isEmpty()) {
                    throw new GradleException(
                        "Resolved while the project was being configured: " + resolvedWhileConfiguring)
                }
            }
        """)
        write(root.resolve("consumer/build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
            }
            repositories { mavenCentral() }
            kotlin {
                jvm('desktop')
                sourceSets {
                    commonMain.dependencies {
                        implementation(project(':library'))
                    }
                }
            }
        """)

        val result = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments(":consumer:compileKotlinDesktop", "--dry-run", "--offline", "--stacktrace").build()

        assertTrue(result.output, ":consumer:compileKotlinDesktop SKIPPED" in result.output)
        val generated = root.resolve("library/build/generated/kotlin-winrt")
        org.junit.Assert.assertFalse("Dry-run must not generate library projections", Files.exists(generated))

    }

    private fun generateProjections(configuration: String): BuildResult {
        val root = Files.createTempDirectory("kotlin-winrt-configuration-time-resolution-")
        write(root.resolve("settings.gradle"), """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = 'configuration-time-resolution'
        """)
        write(root.resolve("gradle.properties"), """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            kotlin.mpp.applyDefaultHierarchyTemplate=false
        """)
        write(root.resolve("build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { mavenCentral() }
        """.trimIndent() + "\n" + configuration.trimIndent() + "\n" + """
            def configurationTime = true
            configurations.configureEach { configuration ->
                configuration.incoming.beforeResolve {
                    if (configurationTime) {
                        throw new IllegalStateException(
                            "Configuration '" + configuration.name + "' was resolved during configuration time.")
                    }
                }
            }
            gradle.projectsEvaluated { configurationTime = false }
        """.trimIndent())
        write(root.resolve("src/commonMain/kotlin/sample/Model.kt"), """
            package sample

            class Model { var title: String = "" }
        """)

        return GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments("generateWinRTProjections", "--offline", "--stacktrace").build()
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content.trimIndent() + System.lineSeparator())
    }
}
