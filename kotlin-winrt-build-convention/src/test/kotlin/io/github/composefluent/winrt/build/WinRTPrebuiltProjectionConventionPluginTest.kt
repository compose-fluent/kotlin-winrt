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
    fun native_publication_uses_projection_as_primary_artifact_when_main_is_empty() {
        verifyNativePublication(withBusinessSources = false, expectedKlibs = 1)
    }

    @Test
    fun native_publication_retains_business_and_projection_artifacts_when_main_has_sources() {
        verifyNativePublication(withBusinessSources = true, expectedKlibs = 2)
    }

    @Test
    fun native_publication_retains_sdk_overlays_before_their_sources_are_generated() {
        verifyNativePublication(withBusinessSources = false, expectedKlibs = 2, generatedOverlayProject = "windows-sdk")
    }

    @Test
    fun native_publication_retains_app_sdk_overlays_before_their_sources_are_generated() {
        verifyNativePublication(withBusinessSources = false, expectedKlibs = 2, generatedOverlayProject = "windows-app-sdk")
    }

    private fun verifyNativePublication(
        withBusinessSources: Boolean,
        expectedKlibs: Int,
        generatedOverlayProject: String? = null,
    ) {
        val projectDir = Files.createTempDirectory("kotlin-winrt-native-publication-")
        writeNativeProjectionFixture(projectDir, "lowered native calls")
        val generatedOverlay = generatedOverlayProject != null
        val projectionPath = generatedOverlayProject?.let { ":$it" } ?: ":projection"
        if (generatedOverlay) {
            val settings = projectDir.resolve("settings.gradle.kts")
            Files.writeString(settings, Files.readString(settings).replace(":projection", projectionPath) +
                "\nproject(\"$projectionPath\").projectDir = file(\"projection\")\n")
        }
        val sources = projectDir.resolve("projection/native-projection-sources/Generated.kt")
        write(sources, "class Generated")
        if (withBusinessSources) {
            write(projectDir.resolve("projection/src/mingwX64Main/kotlin/Business.kt"), "class Business")
        }
        // CsWinRT stages compiled assemblies independently from their consumers.
        // Likewise, finalize artifacts before consumers or publication observe
        // the variants (WebView2 is consumed by the App SDK).
        val rootBuildFile = projectDir.resolve("build.gradle.kts")
        Files.writeString(rootBuildFile, Files.readString(rootBuildFile) + "\n" + """
            val nativeConsumer = configurations.create("nativeConsumer") {
                isCanBeResolved = true
                isCanBeConsumed = false
                attributes {
                    attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
                        objects.named(org.gradle.api.attributes.Usage::class.java, "kotlin-api"))
                    attribute(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
                        org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.native)
                    attribute(org.gradle.api.attributes.Attribute.of("org.jetbrains.kotlin.native.target", String::class.java),
                        "mingw_x64")
                }
            }
            dependencies.add(nativeConsumer.name, project("$projectionPath"))
            evaluationDependsOn("$projectionPath")
            afterEvaluate {
                check(nativeConsumer.incoming.artifacts.artifactFiles.files.isNotEmpty())
                val nativePublication = project("$projectionPath").extensions
                    .getByType<org.gradle.api.publish.PublishingExtension>()
                    .publications.getByName("mingwX64") as org.gradle.api.publish.maven.MavenPublication
                check(nativePublication.artifacts.isNotEmpty())
            }
        """.trimIndent())
        val buildFile = projectDir.resolve("projection/build.gradle.kts")
        Files.writeString(buildFile, Files.readString(buildFile) + "\n" + """
            publishing.repositories.maven {
                name = "Test"
                url = uri(layout.buildDirectory.dir("test-repository"))
            }
            tasks.named<org.gradle.jvm.tasks.Jar>("mingwX64SourcesJar") {
                from("native-projection-sources")
            }
            if ($withBusinessSources) {
                val businessKlib = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>(
                    "compileKotlinMingwX64",
                ).get().outputFile.get()
                val linkData = businessKlib.resolve("default/linkdata/module")
                linkData.parentFile.mkdirs()
                linkData.writeText("lowered business calls")
            }
            if ($generatedOverlay) {
                val businessCompile = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>(
                    "compileKotlinMingwX64",
                )
                val businessKlib = businessCompile.get().outputFile.get()
                val overlayDirectory = layout.buildDirectory.dir("generated/sdk-overlay").get().asFile
                val generateOverlay = tasks.register("generateSdkOverlay") {
                    outputs.dir(overlayDirectory)
                    outputs.dir(businessKlib)
                    doLast {
                        overlayDirectory.mkdirs()
                        overlayDirectory.resolve("WindowNative.kt").writeText("object WindowNative")
                        val linkData = businessKlib.resolve("default/linkdata/module")
                        linkData.parentFile.mkdirs()
                        linkData.writeText("lowered SDK overlay calls")
                    }
                }
                kotlin.sourceSets.getByName("mingwX64Main").kotlin.srcDir(generateOverlay.map { overlayDirectory })
                businessCompile.configure { dependsOn(generateOverlay) }
            }
        """.trimIndent())

        if (generatedOverlay) {
            assertFalse(Files.exists(projectDir.resolve("projection/build/generated/sdk-overlay")))
        }
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("$projectionPath:publishMingwX64PublicationToTestRepository", "--configuration-cache", "--max-workers=1")
            .build()
        assertEquals(TaskOutcome.SUCCESS, result.task("$projectionPath:publishMingwX64PublicationToTestRepository")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task("$projectionPath:verifyMingwX64ProjectionCallSiteLowering")?.outcome)
        if (generatedOverlay) {
            assertEquals(TaskOutcome.SUCCESS, result.task("$projectionPath:generateSdkOverlay")?.outcome)
        }
        val repository = projectDir.resolve("projection/build/test-repository/test/winrt/published-projection-mingwx64/1.0")
        assertTrue(Files.isRegularFile(repository.resolve("published-projection-mingwx64-1.0.klib")))
        val metadata = Files.readString(repository.resolve("published-projection-mingwx64-1.0.module"))
        assertEquals(metadata, expectedKlibs, Regex(""""url": "[^"\n]*\.klib"""").findAll(metadata).count())
        assertEquals(metadata, expectedKlibs == 2, metadata.contains("-winrt-projection.klib"))

        val cachedResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("$projectionPath:publishMingwX64PublicationToTestRepository", "--configuration-cache", "--max-workers=1")
            .build()
        assertTrue(cachedResult.output, cachedResult.output.contains("Configuration cache entry reused."))
    }

    @Test
    fun native_verification_reads_projection_output_when_main_has_no_sources() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-native-projection-verification-")
        writeNativeProjectionFixture(projectDir, "lowered native calls")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(":projection:verifyMingwX64ProjectionCallSiteLowering", "--configuration-cache", "--max-workers=1")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":projection:verifyMingwX64ProjectionCallSiteLowering")?.outcome)
        assertEquals(TaskOutcome.SKIPPED, result.task(":projection:compileWinRTProjectionKotlinMingwX64")?.outcome)

        val cachedResult = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(":projection:verifyMingwX64ProjectionCallSiteLowering", "--configuration-cache", "--max-workers=1")
            .build()
        assertTrue(cachedResult.output, cachedResult.output.contains("Configuration cache entry reused."))
    }

    @Test
    fun native_verification_rejects_placeholders_in_projection_output() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-native-projection-placeholder-")
        writeNativeProjectionFixture(projectDir, "Fixed WinRT ABI call")

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(":projection:verifyMingwX64ProjectionCallSiteLowering", "--max-workers=1")
            .buildAndFail()

        assertTrue(result.output, result.output.contains("Forbidden WinRT call-site marker 'Fixed WinRT ABI call'"))
    }

    private fun writeNativeProjectionFixture(projectDir: Path, content: String) {
        writeFixture(projectDir)
        val buildFile = projectDir.resolve("projection/build.gradle.kts")
        // Match the toolkit's separate Native projection compilation, without
        // invoking the compiler: the regression is artifact discovery, not ABI emission.
        Files.writeString(buildFile, "import java.util.zip.ZipOutputStream\nimport java.util.zip.ZipEntry\n" +
            Files.readString(buildFile) + "\n" + """
            kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().all {
                val projection = compilations.create("winRTProjection")
                project.afterEvaluate {
                    configurations.getByName(apiElementsConfigurationName).outgoing.artifact(
                        projection.compileTaskProvider.flatMap { it.outputFile },
                    ) {
                        classifier = "winrt-projection"
                        extension = "klib"
                        type = "klib"
                        builtBy(projection.compileTaskProvider)
                    }
                }
            }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
                enabled = false
            }
            tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>(
                "compileWinRTProjectionKotlinMingwX64",
            ) {
                produceUnpackagedKlib.set(false)
            }
            val projectionKlib = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>(
                "compileWinRTProjectionKotlinMingwX64",
            ).get().outputFile.get()
            projectionKlib.parentFile.mkdirs()
            ZipOutputStream(projectionKlib.outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("default/linkdata/module"))
                archive.write("$content".toByteArray())
                archive.closeEntry()
            }
        """.trimIndent())
    }

    @Test
    fun convention_mirrors_compile_only_projection_references_and_registers_validation_tasks() {
        val projectDir = Files.createTempDirectory("kotlin-winrt-prebuilt-convention-")
        writeFixture(projectDir)
        val buildFile = projectDir.resolve("projection/build.gradle.kts")
        // Like CsWinRT's Windows ProjectReference, the SDK must reach the
        // standalone compilers while remaining absent from the published POM.
        // Create these configurations after the dependency declaration, as the
        // toolkit does when it registers the separate projection compilations.
        Files.writeString(buildFile, Files.readString(buildFile) + "\n" + """
            val projectionJvmClasspath = configurations.create("kotlinWinRTProjectionJvmCompileClasspath") {
                isCanBeResolved = true
                isCanBeConsumed = false
                attributes {
                    attribute(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
                        org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm)
                    attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE,
                        objects.named(org.gradle.api.attributes.Usage::class.java, "java-api"))
                }
            }
            kotlin.targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().all {
                val projection = compilations.create("winRTProjection")
                projection.defaultSourceSet.dependsOn(kotlin.sourceSets.maybeCreate("winRTProjectionMain"))
                compilations.getByName("main").associateWith(projection)
            }
            afterEvaluate {
                val jvmReferences = projectionJvmClasspath.elements.map { files ->
                    files.map { it.asFile.absolutePath }
                }
                val nativeReferences = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>(
                    "compileWinRTProjectionKotlinMingwX64",
                ).get().libraries.elements.map { files ->
                    files.map { it.asFile.absolutePath }
                }
                val sdkBuildDirectory = project(":sdk").layout.buildDirectory.get().asFile.absolutePath
                tasks.register("verifyCompilerReferences") {
                    inputs.property("jvmReferences", jvmReferences)
                    inputs.property("nativeReferences", nativeReferences)
                    inputs.property("sdkBuildDirectory", sdkBuildDirectory)
                    doLast {
                        val sdkDirectory = inputs.properties.getValue("sdkBuildDirectory") as String
                        listOf("jvmReferences", "nativeReferences").forEach { name ->
                            val references = inputs.properties.getValue(name) as List<*>
                            check(references.any { it.toString().startsWith(sdkDirectory) }) {
                                "Expected SDK on ${'$'}name, found ${'$'}references"
                            }
                        }
                    }
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(
                ":projection:generatePomFileForKotlinMultiplatformPublication",
                ":projection:generatePomFileForJvmPublication",
                ":projection:generatePomFileForMingwX64Publication",
                ":projection:verifyCompilerReferences",
                "--configuration-cache",
                "--stacktrace",
                "--max-workers=1",
            )
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":projection:generatePomFileForKotlinMultiplatformPublication")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":projection:verifyCompilerReferences")?.outcome)
        val pom = Files.readString(
            projectDir.resolve("projection/build/publications/kotlinMultiplatform/pom-default.xml"),
        )
        assertFalse(pom, pom.contains("<artifactId>published-sdk</artifactId>"))
        assertTrue(pom, pom.contains("<artifactId>published-api</artifactId>"))
        assertTrue(
            pom,
            Regex("""<artifactId>published-api</artifactId>[\s\S]*?<scope>compile</scope>""").containsMatchIn(pom),
        )
        listOf("jvm", "mingwX64").forEach { target ->
            val targetPom = Files.readString(projectDir.resolve("projection/build/publications/$target/pom-default.xml"))
            assertFalse(targetPom, targetPom.contains("<artifactId>published-sdk"))
        }
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
                        id = "io.github.compose-fluent.windows-toolkit"
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
                id("io.github.compose-fluent.windows-toolkit") apply false
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
                    id("io.github.compose-fluent.windows-toolkit")
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
            import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("winrt.prebuilt-projection")
                id("io.github.compose-fluent.windows-toolkit")
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
                val jvmCompilerArguments = tasks.named<KotlinJvmCompile>("compileKotlinJvm")
                    .get()
                    .compilerOptions
                    .freeCompilerArgs
                    .get()
                check("-Xno-source-debug-extension" in jvmCompilerArguments) {
                    "Expected prebuilt projection JVM compilation to omit SourceDebugExtension, found: ${'$'}jvmCompilerArguments"
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
