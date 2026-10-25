package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTPortableExecutableInterfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.serialization.json.*

class ProjectionIdeImportTest {
    @get:Rule val temporary = TemporaryFolder()

    // CsWinRT keeps design-time reference declarations separate from implementation
    // compilation. KGP must give each reference source root one IDE module owner.
    @Test fun native_import_keeps_reference_sources_owned_by_business_source_set() {
        val root = temporary.newFolder()
        root.resolve("build/generated/kotlin-winrt/reference/src/Probe.kt").apply {
            parentFile.mkdirs()
            writeText("package sdk; class Probe")
        }
        root.resolve("settings.gradle").writeText("""
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            rootProject.name = 'native-source-ownership'
        """.trimIndent())
        root.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC\norg.gradle.workers.max=1\n")
        root.resolve("build.gradle").writeText("""
            plugins {
                id 'org.jetbrains.kotlin.multiplatform' version '2.4.0'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            kotlin { mingwX64() }
            windows { packageReferences { type 'Windows.Foundation.Uri' } }
            tasks.register('verifyProjectionSourceOwnership') {
                doLast {
                    def roots = kotlin.sourceSets.collectEntries { sourceSet ->
                        [(sourceSet.name): sourceSet.kotlin.srcDirs.collect { it.canonicalFile }]
                    }
                    def reference = file('build/generated/kotlin-winrt/reference/src').canonicalFile
                    def consumer = tasks.named('compileKotlinMingwX64').get()
                    def probe = new File(reference, 'Probe.kt')
                    def nativeMain = kotlin.targets.getByName('mingwX64').compilations.getByName('main')
                    def artifactAttribute = org.gradle.api.attributes.Attribute.of('io.github.composefluent.winrt.native-projection-artifact', String)
                    def artifactKind = configurations.getByName(nativeMain.compileDependencyConfigurationName).attributes.getAttribute(artifactAttribute)
                    if (System.getProperty('idea.sync.active') == 'true') {
                        assert artifactKind == null
                        assert !kotlin.targets.getByName('mingwX64').compilations.getByName('main').associatedCompilations.any { it.name == 'winRTProjectionReference' }
                        assert consumer.sources.files.contains(probe)
                        assert consumer.multiplatformStructure.fragments.get().find { it.fragmentName == 'winuiMain' }.sources.files.contains(probe)
                        def owners = roots.findAll { name, dirs -> dirs.contains(reference) }.keySet()
                        assert owners == ['winuiMain'] as Set : "Reference source owners: " + owners
                        assert roots.winRTProjectionMain.empty
                        assert roots.winRTProjectionReferenceMain.empty
                    } else {
                        assert artifactKind == 'reference'
                        assert kotlin.targets.getByName('mingwX64').compilations.getByName('main').associatedCompilations.any { it.name == 'winRTProjectionReference' }
                        assert !consumer.sources.files.contains(probe)
                        assert roots.winRTProjectionReferenceMain.contains(reference)
                        assert !roots.winRTProjectionMain.empty
                    }
                }
            }
        """.trimIndent())
        listOf(true, false).forEach { importing ->
            val result = GradleRunner.create().withProjectDir(root).withPluginClasspath()
                .withArguments("verifyProjectionSourceOwnership", "-Didea.sync.active=$importing", "--offline", "--stacktrace").build()
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyProjectionSourceOwnership")?.outcome)
            assertFalse(result.tasks.any { it.path.contains("compile", ignoreCase = true) })
        }
    }
    @Test fun task_graph_does_not_scan_installed_sdk_metadata() {
        val root = temporary.newFolder()
        root.resolve("settings.gradle").writeText("rootProject.name = 'sdk-task-graph'\ndependencyResolutionManagement { repositories { mavenCentral() } }")
        root.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC\n")
        root.resolve("build.gradle").writeText("""
            plugins { id 'io.github.compose-fluent.windows-toolkit' }
            windows { packageReferences {
                windowsSdk('10.0.26100.0', false, true)
                restoreNuGetPackages.set(false)
            } }
        """.trimIndent())
        val sdk = root.resolve("portable-sdk").apply { mkdirs() }
        fun graph() = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withEnvironment(System.getenv() + ("KOTLIN_WINRT_WINDOWS_SDK_ROOT" to sdk.absolutePath))
            .withArguments("prepareWinRTProjectionMetadata", "--dry-run", "--configuration-cache", "--offline").build()
        graph()
        sdk.resolve("References/new-sdk/New.winmd").apply { parentFile.mkdirs(); writeText("not read during configuration") }
        val next = graph()
        assertTrue(next.output, next.output.contains("Reusing configuration cache"))
    }

    @Test fun cold_diamond_import_prepares_dependency_identities_without_implementation_generation() {
        val root = temporary.newFolder()
        root.resolve("settings.gradle").writeText("""
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = 'diamond-import'
            include 'sdk', 'a', 'b', 'app'
        """.trimIndent())
        root.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC\norg.gradle.workers.max=1\n")
        listOf("sdk", "a", "b", "app").forEach { root.resolve(it).mkdirs() }
        WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Sample",
            interfaces = listOf(WinRTPortableExecutableInterfaceDescriptor(
                interfaceName = "Sample.IProbe", iid = "00000000-0000-0000-0000-000000000001")),
            runtimeClasses = emptyList(), outputFile = root.resolve("metadata/Sample.winmd").toPath(),
        )
        WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Local",
            interfaces = listOf(WinRTPortableExecutableInterfaceDescriptor(
                interfaceName = "Local.ILocal", iid = "00000000-0000-0000-0000-000000000002")),
            runtimeClasses = emptyList(), outputFile = root.resolve("metadata/Local.winmd").toPath(),
        )
        root.resolve("build.gradle").writeText("""
            plugins { id 'io.github.compose-fluent.windows-toolkit' apply false }
            subprojects {
                apply plugin: 'java-library'
                apply plugin: 'io.github.compose-fluent.windows-toolkit'
                tasks.register('prepareKotlinIdeaImport')
                windows { packageReferences { restoreNuGetPackages.set(false) } }
            }
            project(':sdk') { windows { packageReferences {
                winmd rootProject.file('metadata/Sample.winmd').absolutePath
                namespace 'Sample'
            } } }
            configure([project(':a'), project(':b')]) { dependencies { api project(':sdk') } }
            project(':a') { windows { packageReferences {
                winmd rootProject.file('metadata/Local.winmd').absolutePath
            } } }
            project(':app') { dependencies { implementation project(':a'); implementation project(':b') } }
        """.trimIndent())
        fun sync(session: String = "first") = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withEnvironment(System.getenv() + ("WINRT_TEST_SESSION" to session))
            .withArguments(":app:prepareKotlinIdeaImport", "--offline", "--configuration-cache", "--stacktrace").build()
        val cold = sync()
        assertFalse(cold.output, cold.tasks.any { it.path.endsWith(":generateWinRTProjections") })
        assertNotNull(cold.task(":sdk:prepareWinRTIdentity"))
        assertTrue(root.resolve("sdk/build/generated/kotlin-winrt/identity/projection.json")
            .readText().contains("Sample.IProbe"))
        // With no type/namespace filter, dependency roots must not hide local WinMD types.
        assertTrue(root.resolve("a/build/generated/kotlin-winrt/reference/src/local/local.kt")
            .readText().contains("interface ILocal"))
        assertFalse(root.resolve("a/build/generated/kotlin-winrt/reference/src/sample/sample.kt").exists())
        val aIdentity = Json.parseToJsonElement(root.resolve("a/build/generated/kotlin-winrt/identity/projection.json").readText()).jsonObject
        val sdkIdentity = root.resolve("sdk/build/generated/kotlin-winrt/identity/projection.json")
        val owner = readProjectionSurfaceIdentity(sdkIdentity).owner!!
        val dependencies = aIdentity.getValue("dependencyOwners").jsonArray
        assertEquals(1, dependencies.size)
        assertEquals(owner.name, dependencies.single().jsonObject.getValue("owner").jsonPrimitive.content)
        assertEquals(owner.fingerprint, dependencies.single().jsonObject.getValue("identityFingerprint").jsonPrimitive.content)
        assertTrue(aIdentity.getValue("generationInputFingerprints").jsonArray.isNotEmpty())
        val warm = sync("different-launcher-session")
        assertTrue(warm.output, warm.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, warm.task(":sdk:generateWinRTReferenceProjections")?.outcome)
    }

    // CsWinRT reference projection is the design-time declaration surface; implementation
    // generation belongs to the build. Exercise cold import without any surviving output.
    @Test fun cold_import_uses_reference_only_and_repairs_outputs_with_configuration_cache() {
        val root = temporary.newFolder()
        fun write(path: String, contents: String) = root.resolve(path).apply {
            parentFile.mkdirs(); writeText(contents.trimIndent())
        }
        write("settings.gradle.kts", """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = "cold-reference-import"
        """)
        write("gradle.properties", """
            org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC
            org.gradle.workers.max=1
        """)
        val metadata = root.resolve("metadata/Sample.winmd")
        fun winmd(name: String) = WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Sample",
            interfaces = listOf(WinRTPortableExecutableInterfaceDescriptor(
                interfaceName = "Sample.$name", iid = "00000000-0000-0000-0000-000000000001")),
            runtimeClasses = emptyList(), outputFile = metadata.toPath(),
        )
        winmd("IFirst")
        // Exercise adding the first file without changing source-directory existence,
        // which is independently tracked by the configured Gradle/Kotlin model.
        root.resolve("src/main/kotlin").mkdirs()
        write("build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '2.4.0'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            windows { packageReferences {
                restoreNuGetPackages.set(false)
                winmd file('metadata/Sample.winmd').absolutePath
                namespace 'Sample'
            } }
            if (tasks.findByName('prepareKotlinIdeaImport') == null) tasks.register('prepareKotlinIdeaImport')
        """)
        fun sync() = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments("prepareKotlinIdeaImport", "-Didea.sync.active=true", "--offline",
                "--configuration-cache", "--stacktrace").build().also { result ->
                assertNull(result.task(":generateWinRTProjections"))
                assertFalse(result.tasks.any { it.path.contains("compileKotlinWinRTProjection") })
            }
        val cold = sync()
        assertEquals(TaskOutcome.SUCCESS, cold.task(":generateWinRTReferenceProjections")?.outcome)
        assertTrue(cold.output, cold.output.contains("Configuration cache entry stored"))
        val source = root.resolve("build/generated/kotlin-winrt/reference/src/sample/sample.kt")
        assertTrue(source.readText().contains("interface IFirst"))
        assertFalse(root.resolve("build/generated/kotlin-winrt/src").exists())
        val warm = sync()
        assertTrue(warm.output, warm.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, warm.task(":generateWinRTReferenceProjections")?.outcome)
        write("src/main/kotlin/First.kt", "class First { fun value() = 1 }")
        val firstSource = sync()
        assertTrue(firstSource.output, firstSource.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.SUCCESS, firstSource.task(":generateWinRTAuthoringCandidates")?.outcome)
        write("src/main/kotlin/First.kt", "class First { fun value() = 2 }")
        val bodyChange = sync()
        assertEquals(TaskOutcome.SUCCESS, bodyChange.task(":generateWinRTAuthoringCandidates")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, bodyChange.task(":generateWinRTReferenceProjections")?.outcome)
        val expected = source.readText()
        assertTrue(source.delete())
        sync()
        assertEquals(expected, source.readText())
        winmd("ISecond")
        sync()
        assertTrue(source.readText().contains("interface ISecond"))
        assertFalse(source.readText().contains("interface IFirst"))
        metadata.writeText("invalid WinMD")
        val failed = GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments("prepareKotlinIdeaImport", "--offline", "--configuration-cache", "--stacktrace")
            .buildAndFail()
        assertTrue(failed.tasks.any { it.outcome == TaskOutcome.FAILED })
        winmd("IRecovered")
        sync()
        assertTrue(source.readText().contains("interface IRecovered"))
        assertFalse(source.readText().contains("interface ISecond"))
    }
}
