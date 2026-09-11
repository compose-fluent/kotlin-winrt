package io.github.composefluent.winrt.gradle

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
                id 'io.github.compose-fluent.winrt'
                id 'maven-publish'
            }
            group = 'test.winrt.w9'
            version = '1.0'
            kotlin { mingwX64() }
            winRT { windowsSdk(null, false, true); type 'Windows.Foundation.IClosable' }
            publishing.repositories.maven { name = 'W9'; url = uri('$fixturePath/repository') }
        """)
        write("producer/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.multiplatform'
                id 'io.github.compose-fluent.winrt'
                id 'maven-publish'
            }
            group = 'test.winrt.w9'
            version = '1.0'
            kotlin {
                mingwX64()
                sourceSets.commonMain.dependencies { api project(':w9Base') }
            }
            winRT {
                windowsSdk(null, false, true)
                type 'Windows.Foundation.Uri'
                type 'Windows.Foundation.IStringable'
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
                id 'io.github.compose-fluent.winrt'
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
        val compileTask = ":w9Producer:compileWinRTProjectionKotlinMingwX64"
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
        assertEquals(TaskOutcome.SUCCESS, build("first").task(compileTask)?.outcome)
        runBinary("debug", "before")
        runBinary("release", "before")
        val artifact = fixture.resolve("producer/build/classes/kotlin/mingwX64/winRTProjection/klib/w9Producer_winRTProjection.klib")
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
}
