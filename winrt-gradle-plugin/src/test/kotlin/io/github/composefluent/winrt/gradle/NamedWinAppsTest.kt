package io.github.composefluent.winrt.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

class NamedWinAppsTest {
    @Test
    fun named_application_preserves_explicit_default_deployment_mode() {
        val project = ProjectBuilder.builder().withName("named-deployment").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val extension = project.extensions.getByType(WindowsExtension::class.java)

        extension.application { application ->
            application.selfContained()
            application.variants.create("desktop") {
                it.variant("jvm:main")
                it.mainClass.set("sample.Main")
            }
        }

        val host = project.tasks.getByName("buildWinAppHostDesktop") as BuildWinAppHostTask
        assertEquals(WindowsAppSdkDeployment.SelfContained, host.windowsAppSdkDeployment.get())
    }

    @Test
    fun named_applications_inherit_defaults_and_bind_their_own_run_tasks() {
        val project = ProjectBuilder.builder().withName("named-apps").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val extension = project.extensions.getByType(WindowsExtension::class.java)
        extension.packageReferences.windowsSdk("10.0.26100.0")
        extension.application { application ->
            application.mainClass.set("sample.First")
            application.minWindowsVersion.set("10.0.19041.0")
            application.packagePayloadFiles.from(project.file("shared.txt"))
            application.variants.create("first") {
                it.variant("jvm:main")
                it.runTask("runFirst")
            }
            application.variants.create("second") {
                it.variant("jvm:main")
                it.mainClass.set("sample.Second")
                it.minWindowsVersion.set("10.0.22000.0")
                it.maxVersionTested.set("10.0.28000.0")
                it.packagePayloadFiles.setFrom(project.file("second.txt"))
                it.runTask("runSecond")
            }
            application.console.set(true)
        }
        extension.application { application ->
            application.variants.getByName("second").runTask("runSecondAgain")
        }

        val first = project.tasks.getByName("buildWinAppHostFirst") as BuildWinAppHostTask
        val second = project.tasks.getByName("buildWinAppHostSecond") as BuildWinAppHostTask
        assertEquals("sample.First", first.mainClass.get())
        assertEquals("sample.Second", second.mainClass.get())
        assertTrue(first.console.get())
        assertTrue(second.console.get())
        assertNotEquals(first.outputDirectory.get(), second.outputDirectory.get())
        assertNotEquals(first.generatedSourceDirectory.get(), second.generatedSourceDirectory.get())
        val firstPackagedRun = project.tasks.getByName("runWinAppPackageFirst") as RunWinAppPackageTask
        val secondPackagedRun = project.tasks.getByName("runWinAppPackageSecond") as RunWinAppPackageTask
        val firstDevelopment = project.tasks.getByName("stageWinAppDevelopmentPackageFirst") as StageWinAppPackageTask
        val secondDevelopment = project.tasks.getByName("stageWinAppDevelopmentPackageSecond") as StageWinAppPackageTask
        listOf("First", "Second").forEach { suffix ->
            val stage = project.tasks.getByName("stageWinAppPackage$suffix") as StageWinAppPackageTask
            val dev = project.tasks.getByName("stageWinAppDevelopmentPackage$suffix") as StageWinAppPackageTask
            assertEquals(if (suffix == "First") "10.0.19041.0" else "10.0.22000.0", stage.minWindowsVersion.get())
            assertEquals(if (suffix == "First") "10.0.26100.0" else "10.0.28000.0", stage.maxVersionTested.get())
            assertEquals(stage.minWindowsVersion.get(), dev.minWindowsVersion.get())
            assertEquals(stage.maxVersionTested.get(), dev.maxVersionTested.get())
        }
        extension.packageReferences.windowsSdkVersion.set("10.0.22621.0")
        assertEquals("10.0.22621.0", firstDevelopment.maxVersionTested.get())
        assertEquals("10.0.28000.0", secondDevelopment.maxVersionTested.get())
        assertEquals(first.outputDirectory.get(), firstDevelopment.runtimeAssetsDirectory.get())
        assertEquals(second.outputDirectory.get(), secondDevelopment.runtimeAssetsDirectory.get())
        assertEquals(firstDevelopment.outputDirectory.get(), firstPackagedRun.packageDirectory.get())
        assertEquals(secondDevelopment.outputDirectory.get(), secondPackagedRun.packageDirectory.get())
        assertNotEquals(firstPackagedRun.deploymentDirectory.get(), secondPackagedRun.deploymentDirectory.get())
        assertTrue(first in firstDevelopment.taskDependencies.getDependencies(firstDevelopment))
        assertTrue(second in secondDevelopment.taskDependencies.getDependencies(secondDevelopment))
        assertEquals("shared.txt", extension.application.variants.getByName("first").packagePayloadFiles.singleFile.name)
        assertEquals("second.txt", extension.application.variants.getByName("second").packagePayloadFiles.singleFile.name)
        listOf("runFirst" to first, "runSecond" to second, "runSecondAgain" to second).forEach { (name, host) ->
            val run = project.tasks.getByName(name) as RunWinAppHostTask
            assertTrue(host in run.taskDependencies.getDependencies(run))
        }
        val aggregate = project.tasks.getByName("packageWinApp")
        assertEquals(
            setOf("packageWinAppFirst", "packageWinAppSecond"),
            aggregate.taskDependencies.getDependencies(aggregate).map { it.name }.toSet(),
        )
        val error = runCatching { project.registerWinAppHostRunTask("ambiguousRun") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("select a host"))
    }

    @Test
    fun rejects_variant_task_suffix_collisions() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWindowsToolkitPlugin::class.java)
        val error = runCatching {
            project.extensions.getByType(WindowsExtension::class.java).application { application ->
                application.variants.create("desk-top")
                application.variants.create("desk_top")
            }
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty(), error?.message.orEmpty().contains("collides"))
    }

    @Test
    fun two_jvm_variants_compile_only_their_classpaths_and_collect_only_their_resources() {
        val root = fixture("jvm-variants")
        writeGradleFile(root.resolve("settings.gradle"), "rootProject.name = 'jvm-variants'\ninclude 'firstLib', 'secondLib'")
        listOf("firstLib", "secondLib").forEach { name ->
            writeGradleFile(root.resolve("$name/build.gradle"), """
                plugins { id 'java-library'; id 'io.github.compose-fluent.windows-toolkit' }
                repositories { mavenCentral() }
            """.trimIndent())
            writeGradleFile(root.resolve("$name/src/main/appxResources/Assets/$name.txt"), name)
        }
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'io.github.compose-fluent.windows-toolkit' }
            repositories { mavenCentral() }
            windows { application {
                mainClass = 'sample.FirstKt'
                generateProjectPri = false
                enableDefaultProjectPriResources = false
                variants { create('first') { variantName = 'firstJvm:primary' } }
            } }
            kotlin {
                jvm('firstJvm') {
                    compilations.create('primary')
                    compilations.create('preview')
                }
                mingwX64('unselectedNative') { binaries { executable() } }
                sourceSets {
                    firstJvmPrimary.dependencies { implementation project(':firstLib') }
                    firstJvmPreview.dependencies { implementation project(':secondLib') }
                }
            }
            windows { application { variants { create('second') {
                variantName = 'firstJvm:preview'
                mainClass = 'sample.SecondKt'
            } } } }
            ['First', 'Second'].each { suffix ->
                tasks.register('inspect' + suffix) {
                    def host = tasks.named('buildWinAppHost' + suffix).get()
                    def stage = tasks.named('stageWinAppPackage' + suffix).get()
                    inputs.files(host.runtimeClasspath, stage.appxResourceArchives)
                    dependsOn(stage)
                    doLast {
                        def own = suffix.toLowerCase()
                        def other = own == 'first' ? 'second' : 'first'
                        def paths = host.runtimeClasspath.files.collect { it.name }
                        assert paths.contains(own + 'Lib.jar') : paths
                        assert !paths.contains(other + 'Lib.jar') : paths
                        def compilation = own == 'first' ? 'primary' : 'preview'
                        def otherCompilation = own == 'first' ? 'preview' : 'primary'
                        assert paths.contains('jvm-variants-firstJvm-' + compilation + '.jar') : paths
                        assert !paths.contains('jvm-variants-firstJvm-' + otherCompilation + '.jar') : paths
                        assert stage.appxResourceArchives.files*.name == [own + 'Lib-appx-resources.zip']
                        def resources = stage.outputDirectory.get().asFile.toPath()
                        assert java.nio.file.Files.readString(resources.resolve('Assets/' + own + 'Lib.txt')) == own + 'Lib'
                        assert !java.nio.file.Files.exists(resources.resolve('Assets/' + other + 'Lib.txt'))
                        def identity = configurations.getByName('kotlinWinRTIdentityApplication' + suffix)
                        assert identity.dependencies.findAll { it instanceof ProjectDependency }*.path == [':' + own + 'Lib']
                        assert host.mainClass.get() == 'sample.' + suffix + 'Kt'
                        assert host.outputDirectory.get().asFile.path.contains(own + '--')
                        assert tasks.named('generateWinAppIdentity' + suffix).get().outputFile.get().asFile.path.contains('variant-' + suffix)
                    }
                }
            }
        """.trimIndent())
        writeGradleFile(root.resolve("src/firstJvmPrimary/kotlin/sample/First.kt"), "package sample\nfun main() = println(\"first\")")
        writeGradleFile(root.resolve("src/firstJvmPreview/kotlin/sample/Second.kt"), "package sample\nfun main() = println(\"second\")")

        val result = runner(root, "inspectFirst", "inspectSecond").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compilePrimaryKotlinFirstJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compilePreviewKotlinFirstJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":firstLib:packageAppxResources")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":secondLib:packageAppxResources")?.outcome)
        assertFalse(result.tasks.any { it.path.startsWith(":link") })
        assertTrue(result.task(":compileKotlinUnselectedNative") == null)
        assertTrue(result.task(":compileKotlinFirstJvm") == null)
    }

    @Test
    fun two_native_applications_share_compilation_but_link_and_package_distinct_entries() {
        val makeAppx = findWindowsSdk()?.tool("makeappx.exe", "x64")
        assumeNotNull(makeAppx)
        val root = fixture("native-variants")
        writeGradleFile(root.resolve("build.gradle"), nativeBuildScript + """
            windows { application {
                packageType = io.github.composefluent.winrt.gradle.WindowsPackageType.Packaged
                minWindowsVersion = '10.0.17763.0'
                maxVersionTested = '10.0.26100.0'
                console = true
                generateProjectPri = false
                enableDefaultProjectPriResources = false
                makeAppxExecutable = '${makeAppx.toString().replace("\\", "/")}'
                variants {
                    create('first') {
                        variantName = 'desktop:main:firstReleaseExecutable'
                        mainClass = 'sample.first'
                        appxManifest('payload/first.xml')
                        packagePayload('payload/Logo.png', 'Assets/Logo.png')
                    }
                    create('second') {
                        variantName = 'desktop:main:secondReleaseExecutable'
                        mainClass = 'sample.second'
                        appxManifest('payload/second.xml')
                        packagePayload('payload/Logo.png', 'Assets/Logo.png')
                    }
                }
            } }
        """.trimIndent())
        // The wrapper keeps the CsWinRT application-host scope contract. This fixture isolates
        // KGP compilation/link/packaging ownership from WinUI deployment and projection loading.
        writeGradleFile(root.resolve("src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WindowsAppSdkBootstrap.kt"), """
            package io.github.composefluent.winrt.runtime
            enum class WinAppPackageIdentity { Packaged, Unpackaged }
            enum class WindowsAppSdkDeploymentMode { None, FrameworkDependent, SelfContained, ExternallyInitialized }
            data class WinAppHostConfiguration(
                val packageIdentity: WinAppPackageIdentity,
                val windowsAppSdkDeployment: WindowsAppSdkDeploymentMode,
            ) {
                companion object {
                    fun fromStagedRuntimeAssets(
                        packageIdentity: WinAppPackageIdentity,
                        windowsAppSdkDeployment: WindowsAppSdkDeploymentMode,
                        runtimeAssetsRoot: Any?,
                    ) = WinAppHostConfiguration(packageIdentity, windowsAppSdkDeployment)
                }
            }
            object WindowsAppSdkDeployment {
                fun discoverRuntimeAssetsRoot(): Any? = null
            }
            object WindowsAppSdkBootstrap {
                fun initializeApplicationHost(configuration: WinAppHostConfiguration): AutoCloseable {
                    val unpackaged = configuration.packageIdentity == WinAppPackageIdentity.Unpackaged
                    println("bootstrap=${'$'}unpackaged")
                    return AutoCloseable { }
                }
            }
        """.trimIndent())
        listOf("First", "Second").forEach { name ->
            writeGradleFile(root.resolve("src/commonMain/kotlin/sample/$name.kt"), "package sample\nfun ${name.lowercase()}() = println(\"$name\")")
        }
        listOf("first", "second").forEach { name ->
            writeGradleFile(root.resolve("payload/$name.xml"), manifest(name))
        }
        Files.write(root.resolve("payload/Logo.png"), Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
        ))

        val result = runner(root, "verifyWinAppPackage").build()
        listOf("First", "Second").forEach { name ->
            assertEquals(TaskOutcome.SUCCESS, result.task(":link${name}ReleaseExecutableDesktop")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":packageWinApp$name")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyWinAppPackage$name")?.outcome)
            val id = "${name.lowercase()}--desktop_main_${name.lowercase()}ReleaseExecutable"
            val packageFile = root.resolve("build/kotlin-winrt/packages/native-variants-$id.msix")
            ZipFile(packageFile.toFile()).use { zip ->
                assertTrue(zip.getEntry("${name.lowercase()}.exe") != null)
                assertTrue(zip.getEntry("Assets/Logo.png") != null)
                assertFalse(zip.entries().asSequence().any { it.name.startsWith("appxResources/") })
            }
            val executable = root.resolve("build/kotlin-winrt/application-layout/$id/package/${name.lowercase()}.exe")
            val process = ProcessBuilder(executable.toString()).directory(root.toFile()).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(20, TimeUnit.SECONDS))
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(output, 0, process.exitValue())
                assertTrue(output, output.contains("bootstrap=false"))
                assertTrue(output, output.lineSequence().any { it.trim() == name })
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }
        val repeated = runner(root, "verifyWinAppPackage").build()
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":packageWinAppFirst")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":packageWinAppSecond")?.outcome)
    }

    @Test
    fun runtime_preparation_does_not_capture_application_models_in_configuration_cache() {
        val root = fixture("cached-runtime-preparation")
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.windows-toolkit' }
            windows { application {
                mainClass = 'sample.Main'
                jvmRuntimeMode = io.github.composefluent.winrt.gradle.WinAppJvmRuntimeMode.External
                externalJvmHome = file('${System.getProperty("java.home").replace("\\", "/")}')
                variants {
                    create('first') { variantName = 'jvm:main' }
                    create('second') { variantName = 'jvm:main' }
                }
            } }
        """.trimIndent())
        val first = runner(root, "prepareWinAppJvmRuntimeImage", "--configuration-cache").build()
        assertTrue(first.output, first.output.contains("Configuration cache entry stored"))
        val second = runner(root, "prepareWinAppJvmRuntimeImage", "--configuration-cache").build()
        assertTrue(second.output, second.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.SKIPPED, second.task(":prepareWinAppJvmRuntimeImageFirst")?.outcome)
        assertEquals(TaskOutcome.SKIPPED, second.task(":prepareWinAppJvmRuntimeImageSecond")?.outcome)
    }

    @Test
    fun compatible_jvm_variants_share_runtime_and_authoring_producers_but_keep_materializers() {
        val root = fixture("shared-compatible-jvm-producers")
        val suppliedRuntimeImage = createRuntimeImage(root.resolve("supplied-runtime-image"))
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.windows-toolkit' }
            windows { application {
                mainClass = 'sample.Main'
                jvmRuntimeMode = io.github.composefluent.winrt.gradle.WinAppJvmRuntimeMode.Bundled
                jvmRuntimeImage = file('${suppliedRuntimeImage.toString().replace("\\", "/")}')
                variants {
                    create('first') { variantName = 'jvm:main' }
                    create('second') { variantName = 'jvm:main' }
                }
            } }
            tasks.register('inspectSharedProducers') {
                doLast {
                    def materializers = ['First', 'Second'].collect {
                        tasks.named('prepareWinAppJvmRuntimeImage' + it).get()
                    }
                    def authoringMaterializers = ['First', 'Second'].collect {
                        tasks.named('buildWinRTAuthoringHost' + it).get()
                    }
                    def runtime = materializers.collectMany {
                        it.taskDependencies.getDependencies(it)
                    }.findAll { it.name.startsWith('prepareWinAppJvmRuntimeImageShared') }*.name.unique()
                    def authoring = authoringMaterializers.collectMany {
                        it.taskDependencies.getDependencies(it)
                    }.findAll { it.name.startsWith('buildWinRTAuthoringHostShared') }*.name.unique()
                    assert runtime.size() == 1 : runtime
                    assert authoring.size() == 1 : authoring
                    assert materializers*.outputDirectory*.get()*.asFile*.path.unique().size() == 2
                    assert authoringMaterializers*.outputDirectory*.get()*.asFile*.path.unique().size() == 2
                    ['First', 'Second'].each { suffix ->
                        def materializer = tasks.named('prepareWinAppJvmRuntimeImage' + suffix).get()
                        def authoringMaterializer = tasks.named('buildWinRTAuthoringHost' + suffix).get()
                        assert materializer.taskDependencies.getDependencies(materializer)*.name == runtime*.toString()
                        assert authoringMaterializer.taskDependencies.getDependencies(authoringMaterializer)*.name == authoring*.toString()
                    }
                    println('sharedRuntime=' + runtime)
                    println('sharedAuthoring=' + authoring)
                }
            }
        """.trimIndent())

        val result = runner(root, "inspectSharedProducers").build()

        assertTrue(result.output, result.output.contains("sharedRuntime=[prepareWinAppJvmRuntimeImageShared"))
        assertTrue(result.output, result.output.contains("sharedAuthoring=[buildWinRTAuthoringHostShared"))

        val firstExecution = runner(
            root,
            "prepareWinAppJvmRuntimeImageFirst",
            "prepareWinAppJvmRuntimeImageSecond",
            "buildWinRTAuthoringHostFirst",
            "buildWinRTAuthoringHostSecond",
        ).build()
        val sharedRuntimeTask = firstExecution.tasks.single {
            it.path.startsWith(":prepareWinAppJvmRuntimeImageShared")
        }
        val sharedAuthoringTask = firstExecution.tasks.single {
            it.path.startsWith(":buildWinRTAuthoringHostShared")
        }
        assertEquals(TaskOutcome.SUCCESS, sharedRuntimeTask.outcome)
        assertEquals(TaskOutcome.SUCCESS, sharedAuthoringTask.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstExecution.task(":prepareWinAppJvmRuntimeImageFirst")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstExecution.task(":prepareWinAppJvmRuntimeImageSecond")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstExecution.task(":buildWinRTAuthoringHostFirst")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, firstExecution.task(":buildWinRTAuthoringHostSecond")?.outcome)

        val repeatedExecution = runner(
            root,
            "prepareWinAppJvmRuntimeImageFirst",
            "prepareWinAppJvmRuntimeImageSecond",
            "buildWinRTAuthoringHostFirst",
            "buildWinRTAuthoringHostSecond",
        ).build()
        assertEquals(TaskOutcome.UP_TO_DATE, repeatedExecution.task(sharedRuntimeTask.path)?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeatedExecution.task(sharedAuthoringTask.path)?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeatedExecution.task(":prepareWinAppJvmRuntimeImageFirst")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeatedExecution.task(":prepareWinAppJvmRuntimeImageSecond")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeatedExecution.task(":buildWinRTAuthoringHostFirst")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeatedExecution.task(":buildWinRTAuthoringHostSecond")?.outcome)
    }

    @Test
    fun incompatible_jvm_runtime_modules_keep_separate_runtime_producers() {
        val root = fixture("incompatible-jvm-runtime-producers")
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.windows-toolkit' }
            windows { application {
                mainClass = 'sample.Main'
                variants {
                    create('first') {
                        variantName = 'jvm:main'
                        jvmRuntimeModules = ['java.base']
                    }
                    create('second') {
                        variantName = 'jvm:main'
                        jvmRuntimeModules = ['java.base', 'java.logging']
                    }
                }
            } }
            tasks.register('inspectIncompatibleProducers') {
                doLast {
                    def runtime = ['First', 'Second'].collectMany {
                        tasks.named('prepareWinAppJvmRuntimeImage' + it).get()
                            .taskDependencies.getDependencies(tasks.named('prepareWinAppJvmRuntimeImage' + it).get())
                    }.findAll { it.name.startsWith('prepareWinAppJvmRuntimeImageShared') }*.name.unique()
                    def authoring = ['First', 'Second'].collectMany {
                        tasks.named('buildWinRTAuthoringHost' + it).get()
                            .taskDependencies.getDependencies(tasks.named('buildWinRTAuthoringHost' + it).get())
                    }.findAll { it.name.startsWith('buildWinRTAuthoringHostShared') }*.name.unique()
                    assert runtime.size() == 2 : runtime
                    assert authoring.size() == 1 : authoring
                    println('incompatibleRuntime=' + runtime)
                    println('compatibleAuthoring=' + authoring)
                }
            }
        """.trimIndent())

        val result = runner(root, "inspectIncompatibleProducers").build()

        assertTrue(result.output, result.output.contains("incompatibleRuntime=[prepareWinAppJvmRuntimeImageShared"))
        assertTrue(result.output, result.output.contains("compatibleAuthoring=[buildWinRTAuthoringHostShared"))
    }

    @Test
    fun rejects_two_applications_owning_the_same_native_binary() {
        val root = fixture("duplicate-native")
        writeGradleFile(root.resolve("build.gradle"), nativeBuildScript + """
            windows { application {
                mainClass = 'sample.main'
                variants {
                    create('first') { variantName = 'desktop:main:firstReleaseExecutable' }
                    create('second') { variantName = 'desktop:main:firstReleaseExecutable' }
                }
            } }
        """.trimIndent())
        val result = runner(root, "help").buildAndFail()
        assertTrue(result.output, result.output.contains("select the same Native executable"))
    }

    @Test
    fun rejects_explicit_package_output_colliding_with_another_variants_default() {
        val root = fixture("output-conflict")
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.windows-toolkit' }
            windows { application {
                mainClass = 'sample.Main'
                variants {
                    create('first') { variantName = 'jvm:main' }
                    create('second') {
                        variantName = 'jvm:main'
                        packageOutputFile = layout.buildDirectory.file('kotlin-winrt/packages/output-conflict-first--jvm_main.msix')
                    }
                }
            } }
        """.trimIndent())
        val result = runner(root, "help").buildAndFail()
        assertTrue(result.output, result.output.contains("share package output"))
    }

    private fun fixture(name: String): Path = Files.createTempDirectory("kotlin-winrt-$name-").also { root ->
        writeGradleFile(root.resolve("settings.gradle"), "rootProject.name = '$name'")
        writeGradleFile(root.resolve("gradle.properties"), """
            org.gradle.jvmargs=-Xmx512m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
        """.trimIndent())
    }

    private fun runner(root: Path, vararg tasks: String): GradleRunner = GradleRunner.create()
        .withProjectDir(root.toFile()).withPluginClasspath()
        .withArguments(*tasks, "--offline", "--stacktrace")

    private fun writeGradleFile(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun createRuntimeImage(output: Path): Path {
        val javaHome = Path.of(System.getProperty("java.home"))
        val jlink = javaHome.resolve("bin").resolve(if (isWindowsHost()) "jlink.exe" else "jlink")
        assertTrue("Test JVM must provide jlink: $jlink", Files.isRegularFile(jlink))
        val process = ProcessBuilder(
            jlink.toString(),
            "--add-modules",
            "java.base",
            "--strip-debug",
            "--no-header-files",
            "--no-man-pages",
            "--output",
            output.toString(),
        ).redirectErrorStream(true).start()
        val outputText = process.inputStream.bufferedReader().readText()
        assertEquals("jlink output:\n$outputText", 0, process.waitFor())
        return output
    }

    private val nativeBuildScript = """
        plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'io.github.compose-fluent.windows-toolkit' }
        repositories { mavenCentral() }
        kotlin { mingwX64('desktop') { binaries {
            executable('first', [org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE])
            executable('second', [org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE])
        } } }

    """.trimIndent() + "\n"

    private fun manifest(name: String): String = """
        <Package xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
                 xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
                 xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
                 IgnorableNamespaces="uap rescap">
          <Identity Name="KotlinWinRT.Test.$name" Publisher="CN=KotlinWinRT" Version="1.0.0.0" ProcessorArchitecture="x64" />
          <Properties><DisplayName>$name</DisplayName><PublisherDisplayName>KotlinWinRT</PublisherDisplayName><Logo>Assets/Logo.png</Logo></Properties>
          <Dependencies><TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" /></Dependencies>
          <Resources><Resource Language="en-US" /></Resources>
          <Applications><Application Id="App" Executable="$name.exe" EntryPoint="Windows.FullTrustApplication">
            <uap:VisualElements DisplayName="$name" Description="$name" BackgroundColor="transparent" Square150x150Logo="Assets/Logo.png" Square44x44Logo="Assets/Logo.png" />
          </Application></Applications>
          <Capabilities><rescap:Capability Name="runFullTrust" /></Capabilities>
        </Package>
    """.trimIndent()
}
