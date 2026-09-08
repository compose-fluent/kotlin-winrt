package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinWinRTKotlinDslTest {
    @Test
    fun kotlin_dsl_configures_winrt_properties_with_assignment_syntax() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-kotlin-dsl-assignment-")
        writeFile(
            projectDir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "kotlin-winrt-kotlin-dsl-assignment"
            """.trimIndent(),
        )
        writeFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.daemon=false
            org.gradle.workers.max=1
            """.trimIndent(),
        )
        writeFile(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                application
                id("io.github.compose-fluent.winrt")
            }

            application {
                mainClass = "sample.Main"
            }

            val configuredMainClass = providers.provider { "sample.Main" }

            winRT {
                appxResourcePackageName = "sample.appx"
                winAppCliExecutable = "custom-winapp"
                restoreNuGetPackages = false
                useNuGetCliGlobalPackages = false
                nugetGlobalPackagesRoots = listOf("cache-a", "cache-b")

                nugetPackage("Sample.Package") {
                    version = "1.2.3"
                    generateProjection = false
                }

                application {
                    mainClass = configuredMainClass
                    targetName = providers.provider { "jvm" }
                    nativeBuildType = "release"
                    console = true
                    generateProjectPri = false
                    projectPriDefaultQualifiers = listOf("scale-100")
                    makeAppxExecutable = "custom-makeappx"
                    verifyPackage = false
                    jvmRuntimeModules = listOf("java.base")
                    jvmToolchainVersion = 21
                }
            }

            tasks.register("verifyWinRTKotlinDslAssignments") {
                doLast {
                    val configured = project.extensions.getByType(
                        io.github.composefluent.winrt.gradle.WinRTExtension::class.java,
                    )
                    check(configured.appxResourcePackageName.get() == "sample.appx")
                    check(configured.winAppCliExecutable.get() == "custom-winapp")
                    check(!configured.restoreNuGetPackages.get())
                    check(!configured.useNuGetCliGlobalPackages.get())
                    check(configured.nugetGlobalPackagesRoots.get() == listOf("cache-a", "cache-b"))

                    val nugetPackage = configured.nugetPackages.getByName("Sample.Package")
                    check(nugetPackage.version.get() == "1.2.3")
                    check(!nugetPackage.generateProjection)

                    check(configured.application.mainClass.get() == "sample.Main")
                    check(configured.application.targetName.get() == "jvm")
                    check(configured.application.nativeBuildType.get() == "release")
                    check(configured.application.console.get())
                    check(!configured.application.generateProjectPri.get())
                    check(configured.application.projectPriDefaultQualifiers.get() == listOf("scale-100"))
                    check(configured.application.makeAppxExecutable.get() == "custom-makeappx")
                    check(!configured.application.verifyPackage.get())
                    check(configured.application.jvmRuntimeModules.get() == listOf("java.base"))
                    check(configured.application.jvmToolchainVersion.get() == 21)
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("verifyWinRTKotlinDslAssignments", "--offline", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyWinRTKotlinDslAssignments")?.outcome)
    }

    private fun writeFile(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
