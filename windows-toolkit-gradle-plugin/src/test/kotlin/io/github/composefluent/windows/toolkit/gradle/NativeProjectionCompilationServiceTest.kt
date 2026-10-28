package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativeProjectionCompilationServiceTest {
    private fun service(cache: File? = null): NativeProjectionCompilationService {
        val objects = ProjectBuilder.builder().build().objects
        val serviceParameters = object : NativeProjectionCompilationService.Parameters {
            override val cacheDirectory: DirectoryProperty = objects.directoryProperty().apply { cache?.let(::set) }
            override val projectIdentity: Property<String> = objects.property(String::class.java).value("test-project")
        }
        return object : NativeProjectionCompilationService() {
            override fun getParameters(): NativeProjectionCompilationService.Parameters = serviceParameters
        }
    }

    private fun ownerCacheDirectory(cache: File, owner: String): File =
        cache.resolve(projectionContractDigest("test-project\u0000$owner"))

    @Test fun concurrent_consumers_wait_for_one_successful_compilation() {
        val root = Files.createTempDirectory("native-compilation-concurrent").toFile()
        val executor = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            val service = service()
            val first = executor.submit<Boolean> {
                service.copyOrCompile("same", root.resolve("first.klib")) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    root.resolve("first.klib").writeText("complete")
                }
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val second = executor.submit<Boolean> {
                service.copyOrCompile("same", root.resolve("second.klib")) { fail("Duplicate compilation") }
            }
            release.countDown()
            assertFalse(first.get(10, TimeUnit.SECONDS))
            assertTrue(second.get(10, TimeUnit.SECONDS))
            assertEquals("complete", root.resolve("second.klib").readText())
        } finally {
            release.countDown()
            executor.shutdownNow()
            root.deleteRecursively()
        }
    }

    @Test fun identical_inputs_relocate_but_member_dependency_tool_and_option_changes_do_not() {
        val root = Files.createTempDirectory("native-compilation-key").toFile()
        try {
            fun inputs(name: String): List<File> {
                val dir = root.resolve(name).apply { mkdirs() }
                return listOf("binding.kt", "support.tsv", "runtime.klib", "compiler.jar").map {
                    dir.resolve(it).apply { writeText(it) }
                }
            }
            val a = inputs("exe"); val b = inputs("dll")
            fun key(files: List<File>, flag: String = "library") = nativeCompilationKey(
                files[0].parentFile, listOf(files[0]), listOf(files[1]), files[0].parentFile.resolve("out.klib"),
                listOf(files[2]), listOf(a[3]), "JDK25", listOf("-Xmx8192M"),
                listOf("-produce", flag, "-o", files[0].parentFile.resolve("out.klib").absolutePath,
                    "-library", files[2].absolutePath, files[0].absolutePath, "manifest=${files[1]}"))
            assertEquals(key(a), key(b))
            for (index in 0..2) {
                b[index].appendText(" changed")
                assertNotEquals("Input $index must invalidate", key(a), key(b))
                b[index].writeText(a[index].readText())
            }
            val before = key(a)
            a[3].appendText(" new compiler")
            assertNotEquals(before, key(a))
            assertNotEquals(key(a), key(a, "dynamic"))
            assertTrue(a[3].delete())
            val missingClasspathEntry = key(a)
            assertEquals(missingClasspathEntry, key(b))
            a[3].writeText("optional classpath entry installed")
            assertNotEquals(missingClasspathEntry, key(a))
        } finally { root.deleteRecursively() }
    }

    @Test fun reuse_is_build_scoped_and_failed_results_are_not_published() {
        val root = Files.createTempDirectory("native-compilation-reuse").toFile()
        try {
            val service = service()
            val first = root.resolve("first.klib"); val second = root.resolve("second.klib")
            assertFalse(service.copyOrCompile("same", first) { first.writeText("implementation") })
            assertTrue(service.copyOrCompile("same", second) { fail("Must reuse") })
            assertEquals(first.readText(), second.readText())
            assertFalse(service.copyOrCompile("different", second) { second.writeText("other implementation") })
            assertTrue(runCatching { service.copyOrCompile("failed", second) { error("compiler failed") } }.isFailure)
            assertFalse(service.copyOrCompile("failed", second) { second.writeText("recovered") })
            service.close()
            assertFalse(service.copyOrCompile("same", first) { first.writeText("next build") })
        } finally { root.deleteRecursively() }
    }

    @Test fun completed_owner_compilations_survive_builds_and_corrupt_entries_recompile() {
        // CsWinRTGen's per-output Inputs/Outputs: a global usage change must not
        // invalidate another owner's identical compiler inputs.
        val root = Files.createTempDirectory("native-owner-cache").toFile()
        try {
            val cache = root.resolve("cache")
            val owner = "sdk-owner"
            val key = projectionContractDigest("identical compiler inputs")
            val entry = ownerCacheDirectory(cache, owner).resolve(key)
            var compilations = 0
            fun build(): Boolean = service(cache).use { reuse ->
                val output = root.resolve("output.klib").apply { delete() }
                reuse.copyOrCompile(key, output, owner, allowPersistentReuse = true) {
                    compilations++
                    output.writeText("complete implementation")
                }.also { assertEquals("complete implementation", output.readText()) }
            }
            assertFalse(build())
            assertTrue(build())
            assertEquals(1, compilations)
            entry.resolve("artifact.klib").writeText("corrupted artifact")
            assertFalse(build())
            assertEquals(2, compilations)
            assertTrue(build())
            assertTrue(entry.resolve("manifest.tsv").delete())
            assertFalse(build())
            assertEquals(3, compilations)
            val changedKey = projectionContractDigest("different dependency or member inputs")
            service(cache).use { reuse ->
                val output = root.resolve("changed.klib")
                assertFalse(reuse.copyOrCompile(changedKey, output, owner, allowPersistentReuse = true) { output.writeText("changed implementation") })
            }
            assertTrue(build())
            service(cache).use { reuse ->
                val output = root.resolve("disabled.klib")
                assertFalse(reuse.copyOrCompile(key, output, owner, allowPersistentReuse = false) {
                    output.writeText("uncached implementation")
                })
                assertEquals("uncached implementation", output.readText())
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun persistent_cache_is_bounded_per_owner_and_never_publishes_failed_compilations() {
        val root = Files.createTempDirectory("native-owner-cache-retention").toFile()
        try {
            val cache = root.resolve("cache")
            val owner = "sdk-owner"
            fun build(index: Int, selectedOwner: String = owner): Boolean = service(cache).use { reuse ->
                val output = root.resolve("output-$index.klib")
                reuse.copyOrCompile(projectionContractDigest("inputs-$index"), output, selectedOwner, allowPersistentReuse = true) {
                    output.writeText("implementation $index")
                }
            }
            assertFalse(build(0, "other-owner"))
            repeat(4) { assertFalse(build(it)) }
            val entries = ownerCacheDirectory(cache, owner).listFiles().orEmpty().filter(File::isDirectory)
            assertEquals(3, entries.size)
            assertTrue(build(0, "other-owner"))
            val evicted = (0..3).single {
                !ownerCacheDirectory(cache, owner).resolve(projectionContractDigest("inputs-$it")).exists()
            }
            assertFalse(build(evicted))
            val failedKey = projectionContractDigest("failed compiler")
            service(cache).use { reuse ->
                val output = root.resolve("failed.klib")
                assertTrue(runCatching { reuse.copyOrCompile(failedKey, output, owner, allowPersistentReuse = true) {
                    output.writeText("partial implementation")
                    error("compiler failed")
                } }.isFailure)
            }
            assertFalse(ownerCacheDirectory(cache, owner).resolve(failedKey).exists())
            service(cache).use { reuse ->
                val output = root.resolve("recovered.klib")
                assertFalse(reuse.copyOrCompile(failedKey, output, owner, allowPersistentReuse = true) { output.writeText("recovered implementation") })
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun implicit_native_distribution_contents_invalidate_reuse() {
        val root = Files.createTempDirectory("native-distribution-key").toFile()
        try {
            val source = root.resolve("source.kt").apply { writeText("class Binding") }
            val distribution = root.resolve("distribution").apply { mkdirs() }
            val standardLibrary = distribution.resolve("common/stdlib/default/manifest").apply {
                parentFile.mkdirs(); writeText("stdlib v1")
            }
            fun key() = nativeCompilationKey(root, listOf(source), emptyList(), root.resolve("out.klib"),
                emptyList(), listOf(distribution), "JDK25", emptyList(), listOf("-produce", "library"))
            val first = key()
            standardLibrary.writeText("stdlib v2")
            assertNotEquals(first, key())
        } finally { root.deleteRecursively() }
    }

    @Test fun configuration_cache_does_not_freeze_the_compilation_reuse_policy() {
        // CsWinRTGen evaluates input policy per invocation. Gradle can reuse a
        // configured graph across build-cache/rerun flag changes.
        val root = Files.createTempDirectory("native-owner-policy").toFile()
        try {
            root.resolve("settings.gradle").writeText("rootProject.name = 'native-owner-policy'\n")
            root.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx768m -XX:+UseSerialGC\n")
            root.resolve("build.gradle").writeText("""
                import io.github.composefluent.windows.toolkit.gradle.BuildWinRTNativeDemandTask
                import io.github.composefluent.windows.toolkit.gradle.NativeProjectionCompilationService
                plugins {
                    id 'java'
                    id 'io.github.compose-fluent.windows-toolkit' apply false
                }
                def reuse = gradle.sharedServices.registerIfAbsent('nativePolicy', NativeProjectionCompilationService) {
                    parameters.cacheDirectory.set(layout.buildDirectory.dir('owner-cache'))
                    parameters.projectIdentity.set(projectDir.canonicalPath)
                }
                tasks.register('checkPolicy', BuildWinRTNativeDemandTask) {
                    actions.clear()
                    outputs.upToDateWhen { false }
                    outputs.cacheIf { false }
                    compilationReuse.set(reuse)
                    usesService(reuse)
                    compilerJvmArgs.set([])
                    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
                    outputDirectory.set(layout.buildDirectory.dir('policy'))
                    doLast { task -> task.logger.lifecycle('OWNER_POLICY=' + task.persistentCompilationReuseAllowed) }
                }
            """.trimIndent())
            listOf(
                listOf("--build-cache") to true,
                listOf("--no-build-cache") to false,
                listOf("--build-cache", "--rerun-tasks") to false,
                listOf("--build-cache") to true,
            ).forEachIndexed { index, (flags, expected) ->
                val result = org.gradle.testkit.runner.GradleRunner.create().withProjectDir(root)
                    .withPluginClasspath()
                    .withArguments(listOf("checkPolicy", "--configuration-cache", "--max-workers=1") + flags).build()
                assertTrue(result.output, result.output.contains("OWNER_POLICY=$expected"))
                if (index == 1) assertTrue(result.output, result.output.contains("Configuration cache entry reused."))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun sibling_projects_isolate_services_but_binaries_share_the_project_service() {
        // UWP Gallery uses KSP while its background-task library does not. Their plugin
        // classloaders differ; build-wide service instances cannot cross that boundary.
        val root = org.gradle.testfixtures.ProjectBuilder.builder().build()
        fun demandServices(name: String): List<NativeProjectionCompilationService> {
            val project = org.gradle.testfixtures.ProjectBuilder.builder().withParent(root).withName(name).build()
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
                .mingwX64().binaries.executable()
            project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
            (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
            return listOf("debug", "release").map {
                (project.tasks.getByName("${it}ExecutableWinRTDemandImplementation") as BuildWinRTNativeDemandTask)
                    .compilationReuse.get()
            }
        }
        val app = demandServices("app")
        val background = demandServices("background")
        assertTrue(app[0] === app[1])
        assertTrue(background[0] === background[1])
        assertFalse(app[0] === background[0])
        assertEquals(app[0].parameters.projectIdentity.get(), background[0].parameters.projectIdentity.get())
        assertEquals(app[0].parameters.cacheDirectory.get(), background[0].parameters.cacheDirectory.get())
    }
    @Test fun persistent_reuse_obeys_gradle_cache_and_rerun_flags() {
        listOf(false to false, true to false, true to true).forEach { (enabled, rerun) ->
            val project = org.gradle.testfixtures.ProjectBuilder.builder().build()
            project.gradle.startParameter.isBuildCacheEnabled = enabled
            project.gradle.startParameter.isRerunTasks = rerun
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            val kotlin = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            kotlin.mingwX64().binaries.executable()
            project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
            (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
            val demand = project.tasks.getByName("releaseExecutableWinRTDemandImplementation") as BuildWinRTNativeDemandTask
            assertTrue(demand.compilationReuse.get().parameters.cacheDirectory.isPresent)
            assertEquals(enabled && !rerun, demand.persistentCompilationReuseAllowed)
        }
    }

}
