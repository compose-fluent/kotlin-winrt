package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlinx.serialization.json.*
import java.io.File
import java.util.zip.ZipFile

@RunWith(Parameterized::class)
class NativeReferencePublicationTest(private val kotlinVersion: String, private val withOverlays: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "Kotlin {0}, overlays={1}")
        fun versions(): List<Array<Any>> = projectionTestCompilerVersions().flatMap { version ->
            listOf(false, true).map { arrayOf<Any>(version, it) }
        }
    }
    @get:Rule val temporary = TemporaryFolder()

    @Test fun published_native_diamond_uses_contracts_without_building_sdk_implementation() {
        val root = temporary.newFolder()
        fun write(path: String, content: String) = root.resolve(path).apply {
            parentFile.mkdirs(); writeText(content.trimIndent())
        }
        for (directory in listOf("producer", "consumer")) {
            write("$directory/gradle.properties", "org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC\norg.gradle.workers.max=1\n")
        }
        write("producer/settings.gradle", """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = 'native-publisher'
            include 'sdk', 'a', 'b'
        """)
        write("producer/build.gradle", """
            import org.gradle.api.attributes.Attribute
            import io.github.composefluent.windows.toolkit.gradle.NativeProjectionReferenceVariantsKt
            import io.github.composefluent.windows.toolkit.gradle.NativeProjectionBinaryContractKt
            plugins {
                id 'org.jetbrains.kotlin.multiplatform' version '$kotlinVersion' apply false
                id 'io.github.compose-fluent.windows-toolkit' apply false
            }
            subprojects {
                apply plugin: 'org.jetbrains.kotlin.multiplatform'
                apply plugin: 'maven-publish'
                group = 'sample'; version = '1.0'
                kotlin { mingwX64() }
                kotlin.targets.getByName('mingwX64').compilations.getByName('main').compileTaskProvider.configure {
                    produceUnpackagedKlib.set(true)
                }
                publishing { repositories { maven { name = 'fixture'; url = rootProject.file('../repository') } } }
            }
            project(':sdk') {
                def target = kotlin.targets.getByName('mingwX64')
                def reference = target.compilations.create('reference')
                def implementationCompilation = target.compilations.create('implementation')
                [reference, implementationCompilation].each { compilation ->
                    compilation.compileTaskProvider.configure {
                        compilerOptions.moduleName.set('canonical-sdk')
                        produceUnpackagedKlib.set(false)
                    }
                }
                def main = target.compilations.getByName('main')
                implementationCompilation.compileTaskProvider.configure {
                    doFirst { throw new GradleException('Publication must not compile the full SDK implementation') }
                }
                def referenceFile = NativeProjectionBinaryContractKt.nativeProjectionReferenceArtifact(project, reference)
                dependencies.add(main.compileDependencyConfigurationName, files(referenceFile).builtBy(reference.compileTaskProvider))
                afterEvaluate {
                    main.compileTaskProvider.configure { libraries.from(files(referenceFile).builtBy(reference.compileTaskProvider)) }
                    if (!$withOverlays) configurations.getByName(target.apiElementsConfigurationName).outgoing.artifacts.clear()
                    configurations.getByName(target.apiElementsConfigurationName).outgoing.artifact(
                        referenceFile) {
                        classifier = '${if (withOverlays) "winrt-projection-reference" else ""}'; extension = 'klib'; type = 'klib'
                        builtBy reference.compileTaskProvider
                    }
                    NativeProjectionReferenceVariantsKt.configureNativeProjectionReferenceVariants(project, target, reference)
                }
            }
            configure([project(':a'), project(':b')]) {
                def main = kotlin.targets.getByName('mingwX64').compilations.getByName('main')
                main.defaultSourceSet.dependencies { api project(':sdk') }
                configurations.getByName(main.compileDependencyConfigurationName).attributes.attribute(
                    Attribute.of('io.github.composefluent.winrt.native-projection-artifact', String), 'reference')
            }
        """)
        val referenceSource = write("producer/sdk/src/mingwX64Reference/kotlin/Projection.kt", """
            package sample
            class Box(val value: Int = 41) { fun increment(): Int = throw UnsupportedOperationException("reference") }
        """)
        write("producer/sdk/src/mingwX64Implementation/kotlin/Projection.kt", """
            package sample
            class Box(val value: Int = 41) { fun increment(): Int = value + 1 }
        """)
        val overlaySource = if (withOverlays) write("producer/sdk/src/mingwX64Main/kotlin/Overlay.kt",
            """
                package sdk
                fun create(seed: Int = 41): sample.Box = sample.Box(seed)
                @PublishedApi internal fun bridge(value: Int) = value + 1
                inline fun forwarded(value: Int) = bridge(value)
                inline fun <reified T> name() = T::class.simpleName
            """) else null
        write("producer/a/src/mingwX64Main/kotlin/A.kt", "package a; fun value() = ${if (withOverlays) "sdk.create()" else "sample.Box()"}")
        write("producer/b/src/mingwX64Main/kotlin/B.kt", "package b; fun read(value: sample.Box) = value.increment()")
        fun producer(vararg tasks: String) = GradleRunner.create().withProjectDir(root.resolve("producer")).withProjectionTestEnvironment()
            .withArguments(*tasks, "--offline", "--stacktrace").build()
        val compile = producer(":a:compileKotlinMingwX64", ":b:compileKotlinMingwX64")
        assertNull(compile.task(":sdk:compileImplementationKotlinMingwX64"))
        producer("publishAllPublicationsToFixtureRepository")
        // CsWinRT's reference assembly does not ship ordinary stub bodies.
        // Check the published KLIB is exactly the compiler's ABI header,
        // including its inline/default IR, rather than the local full output.
        fun entries(file: File): Map<String, ByteArray> = ZipFile(file).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.associate {
                it.name to zip.getInputStream(it).use { input -> input.readBytes() }
            }
        }
        val publishedHeader = entries(root.resolve("repository/sample/sdk-mingwx64/1.0/sdk-mingwx64-1.0-winrt-projection-reference.klib"))
        val compilerHeader = entries(root.resolve("producer/sdk/build/classes/kotlin-winrt/native-header/compileReferenceKotlinMingwX64.klib"))
        assertEquals(compilerHeader.keys, publishedHeader.keys)
        compilerHeader.forEach { (name, bytes) -> assertArrayEquals(name, bytes, publishedHeader.getValue(name)) }
        val localReference = entries(root.resolve("producer/sdk/build/classes/kotlin/mingwX64/reference/klib")
            .walkTopDown().single { it.isFile && it.extension == "klib" })
        assertTrue("Ordinary reference bodies must stay out of the published ABI header",
            localReference.keys != publishedHeader.keys || localReference.any { (name, bytes) ->
                publishedHeader[name]?.contentEquals(bytes) != true
            })
        val contract = root.resolve("producer/sdk/build/kotlin-winrt/contracts/mingwX64.json")
        fun fingerprint() = Json.parseToJsonElement(contract.readText()).jsonObject.getValue("fingerprint")
        val originalFingerprint = fingerprint()
        assertEquals(kotlinVersion, Json.parseToJsonElement(contract.readText()).jsonObject
            .getValue("contract").jsonObject.getValue("kotlinCompilerVersion").jsonPrimitive.content)
        assertEquals(if (withOverlays) 2 else 1, Json.parseToJsonElement(contract.readText()).jsonObject
            .getValue("contract").jsonObject.getValue("declarations").jsonObject.size)
        referenceSource.writeText(referenceSource.readText().replace("\"reference\"", "\"a longer ordinary implementation body\""))
        overlaySource?.let { it.writeText(it.readText().replace("sample.Box(seed)", "sample.Box(seed).also { }")
            .replace("value + 1", "value + 200")) }
        producer(":sdk:mingwX64WinRTBinaryContract")
        assertEquals(originalFingerprint, fingerprint())
        referenceSource.writeText(referenceSource.readText().replace("value: Int = 41", "value: Int = 40"))
        producer(":sdk:mingwX64WinRTBinaryContract")
        assertNotEquals(originalFingerprint, fingerprint())
        val repository = root.resolve("repository").toURI()
        write("consumer/settings.gradle", """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { maven { url = uri('$repository') }; mavenCentral() } }
            rootProject.name = 'native-consumer'
        """)
        write("consumer/build.gradle", """
            import org.gradle.api.attributes.Attribute
            import io.github.composefluent.windows.toolkit.gradle.NativeProjectionDemandKt
            plugins {
                id 'org.jetbrains.kotlin.multiplatform' version '$kotlinVersion'
                id 'io.github.compose-fluent.windows-toolkit' apply false
            }
            kotlin {
                mingwX64 {
                    def main = compilations.getByName('main')
                    // A small consumer-side implementation substitutes the canonical owner,
                    // as the recipe compiler does after merging final binary demand.
                    def implementationCompilation = compilations.create('implementation')
                    implementationCompilation.compileTaskProvider.configure {
                        compilerOptions.moduleName.set('canonical-sdk')
                        produceUnpackagedKlib.set(false)
                    }
                    def implementationFile = implementationCompilation.compileTaskProvider.flatMap { it.outputFile }
                    main.defaultSourceSet.dependencies { implementation 'sample:a:1.0'; implementation 'sample:b:1.0' }
                    configurations.getByName(main.compileDependencyConfigurationName).attributes.attribute(
                        Attribute.of('io.github.composefluent.winrt.native-projection-artifact', String), 'reference')
                    binaries.executable {
                        entryPoint = 'main'
                        project.afterEvaluate {
                            def runtime = NativeProjectionDemandKt.nativeDemandConfiguration(project, main)
                            linkTaskProvider.configure {
                                dependsOn implementationCompilation.compileTaskProvider
                                libraries.setFrom(runtime.filter {
                                    !it.name.endsWith('-winrt-projection-reference.klib')
                                }, main.output.classesDirs, implementationFile)
                                doFirst {
                                    assert libraries.files.findAll { it.name.endsWith('-winrt-projection-reference.klib') }.empty
                                    assert libraries.files.findAll { it.name.endsWith('-winrt-main-reference.klib') }.empty
                                    assert libraries.files.findAll { it == implementationFile.get() }.size() == 1
                                }
                            }
                            main.compileTaskProvider.configure { doFirst {
                                assert libraries.files.findAll { it.name.endsWith('-winrt-projection-reference.klib') }.size() == 1
                                assert libraries.files.findAll { it.name.endsWith('-winrt-main-reference.klib') }.size() == ${if (withOverlays) 1 else 0}
                            } }
                        }
                    }
                }
            }
        """)
        write("consumer/src/mingwX64Implementation/kotlin/Projection.kt", """
            package sample
            class Box(val value: Int = 41) { fun increment(): Int = value + 1 }
        """)
        write("consumer/src/mingwX64Main/kotlin/Main.kt", """
            fun main() {
                check(b.read(a.value()) == 42)
                ${if (withOverlays) "check(sdk.forwarded(41) == 42); check(sdk.name<String>() == \"String\")" else ""}
                println("NATIVE_PUBLISHED_DIAMOND_OK")
            }
        """)
        val consumer = GradleRunner.create().withProjectDir(root.resolve("consumer")).withProjectionTestEnvironment()
            .withArguments("runDebugExecutableMingwX64", "--offline", "--stacktrace").build()
        assertTrue(consumer.output, consumer.output.contains("NATIVE_PUBLISHED_DIAMOND_OK"))
    }
}

