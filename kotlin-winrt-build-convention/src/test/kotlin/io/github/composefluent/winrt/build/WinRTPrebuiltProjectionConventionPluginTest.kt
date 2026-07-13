package io.github.composefluent.winrt.build

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WinRTPrebuiltProjectionConventionPluginTest {
    @Test
    fun convention_mirrors_compile_only_projection_references_and_registers_validation_tasks() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-prebuilt-convention-")
        writeFixture(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                ":projection:generatePomFileForKotlinMultiplatformPublication",
                "--configuration-cache",
                "--stacktrace",
                "--max-workers=1",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":projection:generatePomFileForKotlinMultiplatformPublication")?.outcome)
        val pom = Files.readString(
            projectDir.resolve("projection/build/publications/kotlinMultiplatform/pom-default.xml"),
        )
        assertFalse(pom, pom.contains("<artifactId>published-sdk</artifactId>"))
        assertTrue(pom, pom.contains("<artifactId>published-api</artifactId>"))
        assertTrue(
            pom,
            Regex("""<artifactId>published-api</artifactId>[\s\S]*?<scope>compile</scope>""").containsMatchIn(pom),
        )
    }

    @Test
    fun publication_validation_uses_the_published_artifact_name_as_its_contract_key() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-prebuilt-publication-validation-")
        writeFixture(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                ":projection:validatePrebuiltProjectionPublication",
                "--configuration-cache",
                "--stacktrace",
                "--max-workers=1",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":projection:validatePrebuiltProjectionPublication")?.outcome)

        val cachedResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                ":projection:validatePrebuiltProjectionPublication",
                "--configuration-cache",
                "--stacktrace",
                "--max-workers=1",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, cachedResult.task(":projection:validatePrebuiltProjectionPublication")?.outcome)
        assertTrue(cachedResult.output, cachedResult.output.contains("Configuration cache entry reused."))
    }

    private fun writeFixture(projectDir: Path) {
        write(
            projectDir.resolve("buildSrc/build.gradle.kts"),
            """
            plugins {
                `java-gradle-plugin`
            }

            gradlePlugin {
                plugins {
                    create("fakeWinRT") {
                        id = "io.github.compose-fluent.winrt"
                        implementationClass = "fixture.FakeWinRTPlugin"
                    }
                }
            }
            """.trimIndent(),
        )
        write(
            projectDir.resolve("buildSrc/src/main/java/fixture/FakeWinRTPlugin.java"),
            """
            package fixture;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public final class FakeWinRTPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    project.getConfigurations().maybeCreate("kotlinWinRTLibraryDependencyIdentity");
                    project.getTasks().register("generateWinRTProjections");
                }
            }
            """.trimIndent(),
        )
        write(
            projectDir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
                versionCatalogs {
                    create("libs") {
                        version("jvmTarget", "25")
                    }
                }
            }

            rootProject.name = "prebuilt-convention-fixture"
            include(":sdk", ":api", ":projection")
            """.trimIndent(),
        )
        write(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") apply false
                id("io.github.compose-fluent.winrt") apply false
                id("winrt.prebuilt-projection") apply false
            }
            """.trimIndent(),
        )
        listOf("sdk", "api").forEach { moduleName ->
            write(
                projectDir.resolve("$moduleName/build.gradle.kts"),
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("io.github.compose-fluent.winrt")
                    id("winrt.publish")
                }

                kotlin {
                    jvm()
                    mingwX64()
                }

                base {
                    archivesName.set("published-$moduleName")
                }

                mavenPublishing {
                    coordinates("test.winrt", "published-$moduleName", "1.0")
                }
                """.trimIndent(),
            )
        }
        write(
            projectDir.resolve("projection/build.gradle.kts"),
            """
            import org.gradle.api.artifacts.ProjectDependency
            import io.github.composefluent.winrt.build.ValidatePrebuiltProjectionOutputTask

            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("winrt.prebuilt-projection")
                id("io.github.compose-fluent.winrt")
            }

            kotlin {
                jvm()
                mingwX64()
            }

            base {
                archivesName.set("published-projection")
            }

            mavenPublishing {
                coordinates("test.winrt", "published-projection", "1.0")
            }

            dependencies {
                commonMainCompileOnly(project(":sdk"))
                commonMainApi(project(":api"))
            }

            run {
                val identityPaths = configurations
                    .getByName("kotlinWinRTLibraryDependencyIdentity")
                    .dependencies
                    .withType(ProjectDependency::class.java)
                    .map(ProjectDependency::getPath)
                    .toSet()
                check(":sdk" in identityPaths) { "Expected compile-only SDK identity, found: ${'$'}identityPaths" }
                val auditTask = tasks.named<ValidatePrebuiltProjectionOutputTask>(
                    "auditGeneratedWinRTProjectionOutput",
                ).get()
                check(auditTask.maxTotalClassBytes.get() == 150_000_000L) {
                    "Expected the full prebuilt projection class budget, found: ${'$'}{auditTask.maxTotalClassBytes.get()}"
                }
                check(tasks.findByName("validatePrebuiltProjectionPublication") != null)
                val checkTask = tasks.named("check").get()
                val dependencyNames = checkTask.taskDependencies.getDependencies(checkTask).map { it.name }.toSet()
                check("auditGeneratedWinRTProjectionOutput" in dependencyNames) {
                    "Expected check to depend on output audit, found: ${'$'}dependencyNames"
                }
            }
            """.trimIndent(),
        )
    }

    private fun write(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

}
