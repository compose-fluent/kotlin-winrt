package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppxResourcePublicationTest {
    @Test
    fun maven_resource_variant_carries_transitive_appx_resources() {
        val root = Files.createTempDirectory("kotlin-winrt-appx-publication-")
        val repository = root.resolve("repository")
        val base = root.resolve("base")
        val library = root.resolve("library")
        val ordinary = root.resolve("ordinary")
        val consumer = root.resolve("consumer")
        Files.createDirectories(repository)

        writeProducer(base, "base", null, repository, "Base.txt", "base")
        publish(base, repository)

        writeProducer(library, "library", "test.winrt:base:1.0", repository, "Library.txt", "library")
        publish(library, repository)

        writePlainProducer(ordinary, repository)
        publish(ordinary, repository)

        writeSettings(consumer, repository, "consumer")
        writeGradleFile(
            consumer.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'io.github.compose-fluent.winrt'
            }

            winRT {
                application {
                    mainClass = 'sample.Main'
                }
            }

            dependencies {
                implementation 'test.winrt:library:1.0'
                implementation 'test.winrt:ordinary:1.0'
            }

            tasks.register('inspectAppxResources') {
                doLast {
                    def output = file("${'$'}buildDir/appx-resources.txt")
                    output.parentFile.mkdirs()
                    def appxResources = tasks.named('stageWinRTApplicationPackage').get().appxResourceArchives
                    output.text = appxResources.files.collect { it.absolutePath }.sort().join(System.lineSeparator())
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(consumer.toFile())
            .withPluginClasspath()
            .withEnvironment(
                mapOf("GRADLE_USER_HOME" to root.resolve("gradle-user-home-consumer").toString()),
            )
            .withArguments("inspectAppxResources", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":inspectAppxResources")?.outcome)
        val archives = Files.readAllLines(consumer.resolve("build/appx-resources.txt"))
            .filter(String::isNotBlank)
            .map(Path::of)
        assertEquals(2, archives.size)
        val entries = archives.associate { archive ->
            ZipFile(archive.toFile()).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                archive.fileName.toString() to names
            }
        }
        assertTrue(entries.values.any { "Assets/Base.txt" in it })
        assertTrue(entries.values.any { "Assets/Library.txt" in it })
        assertTrue(entries.values.none { names -> names.any { it.startsWith("appxResources/") } })

        // A module that advertises the resource usage but only for another target must not be
        // silently treated like an ordinary dependency. Mutate the published Gradle metadata to
        // model that target-only producer, then force a fresh consumer resolution.
        val libraryModule = repository.resolve("test/winrt/library/1.0/library-1.0.module")
        val resourceUsage = "\"org.gradle.usage\": \"kotlin-winrt-appx\""
        val targetOnlyModule = Files.readString(libraryModule).replace(
            resourceUsage,
            "$resourceUsage,\n        \"io.github.composefluent.winrt.appx-resource-target\": \"mingwX64Main\"",
        )
        Files.writeString(libraryModule, targetOnlyModule)

        val mismatch = GradleRunner.create()
            .withProjectDir(consumer.toFile())
            .withPluginClasspath()
            .withEnvironment(
                mapOf("GRADLE_USER_HOME" to root.resolve("gradle-user-home-consumer").toString()),
            )
            .withArguments(
                "inspectAppxResources",
                "--refresh-dependencies",
                "--rerun-tasks",
                "--no-configuration-cache",
                "--stacktrace",
            )
            .forwardOutput()
            .buildAndFail()
        assertTrue(
            mismatch.output,
            mismatch.output.contains("Failed to resolve Kotlin/WinRT AppX resource variants"),
        )
    }

    @Test
    fun project_resource_variant_mismatch_is_not_silently_ignored() {
        val root = Files.createTempDirectory("kotlin-winrt-appx-project-publication-")
        val producer = root.resolve("producer")
        val consumer = root.resolve("consumer")
        writeMultiProjectSettings(root)

        val artifact = producer.resolve("target-only.zip")
        Files.createDirectories(artifact.parent)
        ZipFileTestSupport.write(artifact, mapOf("Assets/Target.txt" to "target"))
        writeGradleFile(
            producer.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'io.github.compose-fluent.winrt'
            }

            configurations.named('kotlinWinRTAppxResourcesElements') {
                canBeConsumed = false
            }

            def targetOnly = configurations.create('targetOnlyAppxResources') {
                canBeConsumed = true
                canBeResolved = false
                attributes {
                    // Maven publication normalizes this optional usage to kotlin-winrt-appx;
                    // project variants use the same published name.
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'kotlin-winrt-appx'))
                    attribute(
                        Attribute.of('io.github.composefluent.winrt.appx-resource-target', String),
                        'mingwX64Main',
                    )
                }
            }
            artifacts.add(targetOnly.name, file('target-only.zip'))
            """.trimIndent(),
        )
        writeGradleFile(
            consumer.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'io.github.compose-fluent.winrt'
            }

            winRT {
                application {
                    mainClass = 'sample.Main'
                }
            }

            dependencies {
                implementation project(':producer')
            }

            tasks.register('inspectAppxResources') {
                doLast {
                    tasks.named('stageWinRTApplicationPackage').get().appxResourceArchives.files
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath()
            .withArguments(":consumer:inspectAppxResources", "--stacktrace")
            .forwardOutput()
            .buildAndFail()

        assertTrue(
            result.output,
            result.output.contains("Failed to resolve Kotlin/WinRT AppX resource variants"),
        )
    }

    private fun publish(projectDir: Path, repository: Path) {
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withEnvironment(
                mapOf("GRADLE_USER_HOME" to repository.resolve("gradle-user-home-${projectDir.fileName}").toString()),
            )
            .withArguments("publishMavenPublicationToTestRepository", "--stacktrace")
            .forwardOutput()
            .build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToTestRepository")?.outcome)
        assertTrue(Files.exists(repository.resolve("test/winrt/${projectDir.fileName}/1.0")))
    }

    private fun writeProducer(
        projectDir: Path,
        projectName: String,
        dependency: String?,
        repository: Path,
        resourceName: String,
        resourceText: String,
    ) {
        writeSettings(projectDir, repository, projectName)
        val resource = projectDir.resolve("src/main/appxResources/Assets/$resourceName")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, resourceText)
        val dependencyBlock = dependency?.let { "implementation '$it'" }.orEmpty()
        writeGradleFile(
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'maven-publish'
                id 'io.github.compose-fluent.winrt'
            }

            group = 'test.winrt'
            version = '1.0'

            dependencies {
                $dependencyBlock
            }

            publishing {
                repositories {
                    maven {
                        name = 'test'
                        url = uri('${repository.toUri()}')
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun writePlainProducer(projectDir: Path, repository: Path) {
        writeSettings(projectDir, repository, "ordinary")
        writeGradleFile(
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'maven-publish'
            }

            group = 'test.winrt'
            version = '1.0'

            publishing {
                repositories {
                    maven {
                        name = 'test'
                        url = uri('${repository.toUri()}')
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeSettings(projectDir: Path, repository: Path, name: String) {
        writeGradleFile(
            projectDir.resolve("settings.gradle"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri('${repository.toUri()}') }
                    mavenCentral()
                }
            }
            rootProject.name = '$name'
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            """.trimIndent(),
        )
    }

    private fun writeMultiProjectSettings(root: Path) {
        writeGradleFile(
            root.resolve("settings.gradle"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = 'project-resource-publication'
            include 'producer', 'consumer'
            """.trimIndent(),
        )
        writeGradleFile(
            root.resolve("gradle.properties"),
            """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            """.trimIndent(),
        )
    }

    private fun writeGradleFile(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content + System.lineSeparator())
    }
}

private object ZipFileTestSupport {
    fun write(path: Path, entries: Map<String, String>) {
        java.util.zip.ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
