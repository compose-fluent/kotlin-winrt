package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowsToolkitKotlinDslTest {
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
                id("io.github.compose-fluent.windows-toolkit")
            }

            application {
                mainClass = "sample.Main"
            }

            val configuredMainClass = providers.provider { "sample.Main" }

            windows {
                appxResourcePackageName = "sample.appx"
                winAppCliExecutable = "custom-winapp"
                packageReferences {
                    restoreNuGetPackages = false
                    useNuGetCliGlobalPackages = false
                    nugetGlobalPackagesRoots = listOf("cache-a", "cache-b")

                    nugetPackage("Sample.Package") {
                        version = "1.2.3"
                        generateProjection = false
                    }
                }

                application {
                    mainClass = configuredMainClass
                    packageType = io.github.composefluent.winrt.gradle.WindowsPackageType.None
                    console = true
                    generateProjectPri = false
                    projectPriDefaultQualifiers = listOf("scale-100")
                    makeAppxExecutable = "custom-makeappx"
                    verifyPackage = false
                    jvmRuntimeModules = listOf("java.base")
                    jvmToolchainVersion = 21
                    variants.create("desktop") {
                        variantName = providers.provider { "jvm:main" }
                    }
                }
            }

            tasks.register("verifyWinRTKotlinDslAssignments") {
                doLast {
                    val configured = project.extensions.getByType(
                        io.github.composefluent.winrt.gradle.WindowsExtension::class.java,
                    )
                    check(configured.appxResourcePackageName.get() == "sample.appx")
                    check(configured.winAppCliExecutable.get() == "custom-winapp")
                    check(!configured.packageReferences.restoreNuGetPackages.get())
                    check(!configured.packageReferences.useNuGetCliGlobalPackages.get())
                    check(configured.packageReferences.nugetGlobalPackagesRoots.get() == listOf("cache-a", "cache-b"))

                    val nugetPackage = configured.packageReferences.nugetPackages.getByName("Sample.Package")
                    check(nugetPackage.version.get() == "1.2.3")
                    check(!nugetPackage.generateProjection)

                    check(configured.application.mainClass.get() == "sample.Main")
                    check(configured.application.packageType.get() == io.github.composefluent.winrt.gradle.WindowsPackageType.None)
                    check(configured.application.variants.getByName("desktop").variantName.get() == "jvm:main")
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
