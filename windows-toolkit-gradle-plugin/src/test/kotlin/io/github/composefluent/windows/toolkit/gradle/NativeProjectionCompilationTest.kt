package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class NativeProjectionCompilationTest {
    @Test
    fun native_projection_is_reused_and_published_for_a_separate_consumer() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) && Files.isDirectory(it.resolve("winrt-runtime")) }
        val fixture = Files.createTempDirectory(Files.createDirectories(root.resolve(".agent_tmp")), "w9-native-")
        fun write(relative: String, content: String) {
            val path = fixture.resolve(relative)
            Files.createDirectories(path.parent)
            Files.writeString(path, content.trimIndent())
        }
        val fixturePath = fixture.toString().replace('\\', '/')
        write("fixture.init.gradle", """
            gradle.settingsEvaluated { settings ->
                if (settings.rootProject.name == 'kotlin-winrt') {
                    settings.include ':w9Base', ':w9Producer', ':w9Consumer'
                    settings.project(':w9Base').projectDir = new File('$fixturePath/base')
                    settings.project(':w9Producer').projectDir = new File('$fixturePath/producer')
                    settings.project(':w9Consumer').projectDir = new File('$fixturePath/consumer')
                }
            }
        """)
        write("base/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
                id 'maven-publish'
            }
            group = 'test.winrt.w9'
            version = '1.0'
            kotlin { mingwX64() }
            windows { packageReferences { windowsSdk(null, false, true); type 'Windows.Foundation.IClosable' } }
            publishing.repositories.maven { name = 'W9'; url = uri('$fixturePath/repository') }
        """)
        write("producer/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
                id 'maven-publish'
            }
            group = 'test.winrt.w9'
            version = '1.0'
            kotlin {
                mingwX64()
                sourceSets.commonMain.dependencies { api project(':w9Base') }
            }
            windows {
                packageReferences {
                    windowsSdk(null, false, true)
                    type 'Windows.Foundation.Uri'
                    type 'Windows.Foundation.IStringable'
                }
            }
            publishing.repositories.maven { name = 'W9'; url = uri('$fixturePath/repository') }
        """)
        val businessSource = """
            package sample
            import windows.foundation.IStringable
            import windows.foundation.Uri
            import io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic
            import io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass
            @WinRTAuthoredRuntimeClass(interfaceNames = ["windows.foundation.IStringable"])
            class StringableThing : IStringable { override fun toString(): String = "authored" }
            fun bodyValue(): String = "before"
            fun projectedUri(): String = Uri("https://example.invalid/w9").absoluteUri
            fun initializeProjection() { WinRTProjectionSupportIntrinsic.ensureInitialized() }
        """.trimIndent()
        write("producer/src/winuiMain/kotlin/sample/Library.kt", businessSource)
        write("consumer/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { maven { url = uri('$fixturePath/repository') }; mavenCentral() }
            configurations.configureEach {
                resolutionStrategy.dependencySubstitution {
                    substitute module('io.github.compose-fluent:winrt-runtime') using project(':winrt-runtime')
                    substitute module('io.github.compose-fluent:winrt-authoring') using project(':winrt-authoring')
                }
            }
            kotlin {
                mingwX64 { binaries { executable { entryPoint = 'sample.main' } } }
                sourceSets.mingwX64Main.dependencies {
                    implementation(providers.gradleProperty('w9.published').isPresent()
                        ? 'test.winrt.w9:w9Producer:1.0' : project(':w9Producer'))
                }
            }
        """)
        write("consumer/src/mingwX64Main/kotlin/sample/Main.kt", """
            package sample
            import io.github.composefluent.winrt.runtime.RuntimeScope
            import io.github.composefluent.winrt.runtime.ComWrappersSupport
            import io.github.composefluent.winrt.runtime.IID
            import windows.foundation.Uri
            fun main() {
                RuntimeScope.initializeMultithreaded().use {
                    repeat(2) { initializeProjection() }
                    check(projectedUri() == "https://example.invalid/w9")
                    check(Uri("https://example.invalid/consumer").absoluteUri == "https://example.invalid/consumer")
                    check(StringableThing().toString() == "authored")
                    ComWrappersSupport.createCCWForObject(StringableThing(), IID.IStringable).use {
                        check(it.interfaceId == IID.IStringable)
                    }
                    println("W9_NATIVE_OK:" + bodyValue())
                }
            }
        """)
        val compileTask = ":w9Producer:compileWinRTProjectionReferenceKotlinMingwX64"
        val arguments = listOf(
            ":w9Consumer:linkDebugExecutableMingwX64", ":w9Consumer:linkReleaseExecutableMingwX64",
            "--init-script", fixture.resolve("fixture.init.gradle").toString(),
            "--console=plain", "--max-workers=1", "--configuration-cache", "--configure-on-demand",
        )
        fun build(label: String, extra: List<String> = emptyList()) =
            Files.newBufferedWriter(fixture.resolve("$label.log")).use { output ->
                GradleRunner.create().withProjectDir(root.toFile()).withArguments(arguments + extra)
                    .forwardStdOutput(output).forwardStdError(output).build()
            }
        fun runBinary(mode: String, expected: String) {
            val binary = fixture.resolve("consumer/build/bin/mingwX64/${mode}Executable/w9Consumer.exe")
            val output = fixture.resolve("$mode-runtime.log").toFile()
            val process = ProcessBuilder(binary.toString()).redirectErrorStream(true).redirectOutput(output).start()
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("Native fixture timed out: $binary")
            }
            assertEquals(output.readText(), 0, process.exitValue())
            assertTrue(output.readText(), output.readText().contains("W9_NATIVE_OK:$expected"))
        }
        // CsWinRT owns registration in the projection assembly. Both Native links must retain
        // the same initializer through KLIB serialization and release dead stripping.
        val first = build("first")
        assertEquals(TaskOutcome.SUCCESS, first.task(compileTask)?.outcome)
        assertEquals(TaskOutcome.SUCCESS, first.task(":w9Consumer:generateWinRTNativeAuthoring")?.outcome)
        assertTrue("Native business compilation must not render the JVM SDK implementation",
            first.tasks.none { it.path == ":w9Consumer:generateWinRTProjections" })
        runBinary("debug", "before")
        runBinary("release", "before")
        val artifact = fixture.resolve("producer/build/classes/kotlin/mingwX64/winRTProjectionReference/klib/w9Producer_winRTProjectionReference.klib")
        fun digest() = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)).toList()
        val originalDigest = digest()
        assertEquals(TaskOutcome.UP_TO_DATE, build("noop").task(compileTask)?.outcome)
        write("producer/src/winuiMain/kotlin/sample/Library.kt", businessSource.replace("\"before\"", "\"after\""))
        val edited = build("body-edit")
        assertEquals(TaskOutcome.UP_TO_DATE, edited.task(compileTask)?.outcome)
        assertEquals(TaskOutcome.SUCCESS, edited.task(":w9Producer:compileKotlinMingwX64")?.outcome)
        assertEquals(originalDigest, digest())
        runBinary("debug", "after")
        runBinary("release", "after")
        build("publish", listOf(
            ":w9Base:publishAllPublicationsToW9Repository",
            ":w9Producer:publishAllPublicationsToW9Repository",
        ))
        val published = build("published-consumer", listOf("-Pw9.published=true"))
        assertTrue(published.tasks.none { it.path.startsWith(":w9Producer:") || it.path.startsWith(":w9Base:") })
        runBinary("debug", "after")
        runBinary("release", "after")
    }

    @Test
    fun consumer_projection_compiles_against_types_owned_by_a_published_library() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) && Files.isDirectory(it.resolve("winrt-runtime")) }
        val fixture = Files.createTempDirectory(Files.createDirectories(root.resolve(".agent_tmp")), "external-library-")
        fun write(relative: String, content: String) {
            val path = fixture.resolve(relative)
            Files.createDirectories(path.parent)
            Files.writeString(path, content.trimIndent())
        }
        val fixturePath = fixture.toString().replace('\\', '/')
        write("fixture.init.gradle", """
            gradle.settingsEvaluated { settings ->
                if (settings.rootProject.name == 'kotlin-winrt') {
                    settings.include ':externalLibrary', ':externalConsumer'
                    settings.project(':externalLibrary').projectDir = new File('$fixturePath/library')
                    settings.project(':externalConsumer').projectDir = new File('$fixturePath/consumer')
                }
            }
        """)
        write("library/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
                id 'maven-publish'
            }
            group = 'test.winrt.external'
            version = '1.0'
            kotlin { jvm(); mingwX64() }
            windows { packageReferences { windowsSdk(null, false, true); type 'Windows.Foundation.Uri' } }
            publishing.repositories.maven { name = 'External'; url = uri('$fixturePath/repository') }
        """)
        write("consumer/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { maven { url = uri('$fixturePath/repository') }; mavenCentral() }
            configurations.configureEach {
                resolutionStrategy.dependencySubstitution {
                    substitute module('io.github.compose-fluent:winrt-runtime') using project(':winrt-runtime')
                    substitute module('io.github.compose-fluent:winrt-authoring') using project(':winrt-authoring')
                }
            }
            kotlin {
                jvm()
                mingwX64 { binaries { executable { entryPoint = 'sample.main' } } }
                sourceSets.commonMain.dependencies { implementation 'test.winrt.external:externalLibrary:1.0' }
            }
            windows {
                packageReferences {
                    windowsSdk(null, false, true)
                    // ContactWebsite.Uri is a Windows.Foundation.Uri, which the library owns.
                    type 'Windows.ApplicationModel.Contacts.ContactWebsite'
                }
            }
        """)
        write("consumer/src/winuiMain/kotlin/sample/Website.kt", """
            package sample
            import windows.applicationmodel.contacts.ContactWebsite
            import windows.foundation.Uri
            fun websiteUri(): String {
                val website = ContactWebsite()
                website.uri = Uri("https://example.invalid/external")
                val uri: Uri? = website.uri
                return uri?.absoluteUri.orEmpty()
            }
        """)
        write("consumer/src/mingwX64Main/kotlin/sample/Main.kt", """
            package sample
            import io.github.composefluent.winrt.runtime.RuntimeScope
            fun main() {
                RuntimeScope.initializeMultithreaded().use {
                    check(websiteUri() == "https://example.invalid/external")
                    println("EXTERNAL_LIBRARY_OK")
                }
            }
        """)
        // A cached metadata manifest keeps absolute paths into the fixture directory that produced
        // it, and every run of this test uses a new directory.
        fun build(label: String, vararg tasks: String) =
            Files.newBufferedWriter(fixture.resolve("$label.log")).use { output ->
                GradleRunner.create().withProjectDir(root.toFile()).withArguments(
                    *tasks,
                    "--init-script", fixture.resolve("fixture.init.gradle").toString(),
                    "--console=plain", "--max-workers=1", "--configuration-cache", "--configure-on-demand",
                    "--no-build-cache",
                ).forwardStdOutput(output).forwardStdError(output).build()
            }
        build("publish", ":externalLibrary:publishAllPublicationsToExternalRepository")

        val consumer = build(
            "consumer",
            ":externalConsumer:compileKotlinJvm",
            ":externalConsumer:linkDebugExecutableMingwX64",
        )

        assertTrue(consumer.tasks.none { it.path.startsWith(":externalLibrary:") })
        // The generator leaves Uri to the library that owns it, so both standalone projection
        // contract compilations must see the published module, not only the business compilations.
        val generated = fixture.resolve("consumer/build/generated/kotlin-winrt/src/winuiMain/kotlin/windows")
        assertTrue(Files.isDirectory(generated.resolve("applicationmodel/contacts")))
        assertTrue(Files.notExists(generated.resolve("foundation")))
        assertEquals(
            TaskOutcome.SUCCESS,
            consumer.task(":externalConsumer:compileKotlinWinRTProjectionJvmReference")?.outcome,
        )
        assertEquals(
            TaskOutcome.SUCCESS,
            consumer.task(":externalConsumer:compileWinRTProjectionReferenceKotlinMingwX64")?.outcome,
        )
        val runtimeLog = fixture.resolve("runtime.log").toFile()
        val process = ProcessBuilder(
            fixture.resolve("consumer/build/bin/mingwX64/debugExecutable/externalConsumer.exe").toString(),
        ).redirectErrorStream(true).redirectOutput(runtimeLog).start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Native fixture timed out")
        }
        assertEquals(runtimeLog.readText(), 0, process.exitValue())
        assertTrue(runtimeLog.readText(), runtimeLog.readText().contains("EXTERNAL_LIBRARY_OK"))
    }

    @Test
    fun native_projection_declares_klib_modules_outside_the_source_build() {
        val root = Files.createTempDirectory("kotlin-winrt-native-projection-dependencies-")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'native-projection-dependencies'\n")
        Files.writeString(root.resolve("build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { mavenCentral() }
            kotlin { mingwX64() }
            tasks.register('inspectProjectionDependencies') {
                def declared = configurations.mingwX64WinRTProjectionImplementation.dependencies.collect { dependency ->
                    dependency instanceof ExternalModuleDependency
                        ? "module:${'$'}{dependency.group}:${'$'}{dependency.name}" : 'files'
                }.sort().join(',')
                doLast { println 'projectionDependencies=' + declared }
            }
        """.trimIndent())

        val result = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments("inspectProjectionDependencies", "--offline", "--stacktrace").build()

        // The plugin's own classpath carries only the JVM JARs of the runtime modules. A Native
        // compilation must resolve their multiplatform publications to receive KLIBs.
        assertTrue(result.output, result.output.contains(
            "projectionDependencies=module:io.github.compose-fluent:winrt-authoring," +
                "module:io.github.compose-fluent:winrt-runtime",
        ))
    }

    @Test
    fun cinterop_is_ordered_after_the_native_projection_compilation() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val root = Files.createTempDirectory("kotlin-winrt-native-projection-cinterop-")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'native-projection-cinterop'\n")
        Files.writeString(root.resolve("sample.def"), "package = sample.cinterop\n")
        Files.writeString(root.resolve("build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            repositories { mavenCentral() }
            kotlin { mingwX64 { compilations.main.cinterops { sample { defFile project.file('sample.def') } } } }
            windows { packageReferences { windowsSdk(null, false, true); type 'Windows.Foundation.IClosable' } }
            tasks.register('inspectCinteropOrdering') {
                def cinterop = tasks.named('cinteropSampleMingwX64').get()
                def predecessors = cinterop.mustRunAfter.getDependencies(cinterop).collect { it.name }.sort().join(',')
                doLast { println 'cinteropMustRunAfter=' + predecessors }
            }
        """.trimIndent())

        val result = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments("inspectCinteropOrdering", "--offline", "--stacktrace").build()

        // The business compilation's dependency files include the projection KLIB, and cinterop
        // reads them as libraries. Gradle fails the build when that read has no declared order.
        val predecessors = Regex("cinteropMustRunAfter=([^\\r\\n]*)").find(result.output)
            ?.groupValues?.get(1).orEmpty().split(',')
        assertTrue(result.output, "compileWinRTProjectionReferenceKotlinMingwX64" in predecessors)
    }
}
