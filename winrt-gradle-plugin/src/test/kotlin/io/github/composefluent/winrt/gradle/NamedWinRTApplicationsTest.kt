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

class NamedWinRTApplicationsTest {
    @Test
    fun named_application_preserves_explicit_default_deployment_mode() {
        val project = ProjectBuilder.builder().withName("named-deployment").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)

        extension.application { application ->
            application.selfContained()
            application.variants.create("desktop") {
                it.variant("jvm:main")
                it.mainClass.set("sample.Main")
            }
        }

        val host = project.tasks.getByName("buildWinRTApplicationHostDesktop") as BuildWinRTApplicationHostTask
        assertEquals(WinRTWindowsAppSdkDeployment.SelfContained, host.windowsAppSdkDeployment.get())
    }

    @Test
    fun named_applications_inherit_defaults_and_bind_their_own_run_tasks() {
        val project = ProjectBuilder.builder().withName("named-apps").build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.windowsSdk("10.0.26100.0")
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

        val first = project.tasks.getByName("buildWinRTApplicationHostFirst") as BuildWinRTApplicationHostTask
        val second = project.tasks.getByName("buildWinRTApplicationHostSecond") as BuildWinRTApplicationHostTask
        assertEquals("sample.First", first.mainClass.get())
        assertEquals("sample.Second", second.mainClass.get())
        assertTrue(first.console.get())
        assertTrue(second.console.get())
        assertNotEquals(first.outputDirectory.get(), second.outputDirectory.get())
        assertNotEquals(first.generatedSourceDirectory.get(), second.generatedSourceDirectory.get())
        val firstPackagedRun = project.tasks.getByName("runWinRTApplicationPackageFirst") as RunWinRTApplicationPackageTask
        val secondPackagedRun = project.tasks.getByName("runWinRTApplicationPackageSecond") as RunWinRTApplicationPackageTask
        val firstDevelopment = project.tasks.getByName("stageWinRTApplicationDevelopmentPackageFirst") as StageWinRTApplicationPackageTask
        val secondDevelopment = project.tasks.getByName("stageWinRTApplicationDevelopmentPackageSecond") as StageWinRTApplicationPackageTask
        listOf("First", "Second").forEach { suffix ->
            val stage = project.tasks.getByName("stageWinRTApplicationPackage$suffix") as StageWinRTApplicationPackageTask
            val dev = project.tasks.getByName("stageWinRTApplicationDevelopmentPackage$suffix") as StageWinRTApplicationPackageTask
            assertEquals(if (suffix == "First") "10.0.19041.0" else "10.0.22000.0", stage.minWindowsVersion.get())
            assertEquals(if (suffix == "First") "10.0.26100.0" else "10.0.28000.0", stage.maxVersionTested.get())
            assertEquals(stage.minWindowsVersion.get(), dev.minWindowsVersion.get())
            assertEquals(stage.maxVersionTested.get(), dev.maxVersionTested.get())
        }
        extension.windowsSdkVersion.set("10.0.22621.0")
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
            val run = project.tasks.getByName(name) as RunWinRTApplicationHostTask
            assertTrue(host in run.taskDependencies.getDependencies(run))
        }
        val aggregate = project.tasks.getByName("packageWinRTApplication")
        assertEquals(
            setOf("packageWinRTApplicationFirst", "packageWinRTApplicationSecond"),
            aggregate.taskDependencies.getDependencies(aggregate).map { it.name }.toSet(),
        )
        val error = runCatching { project.registerWinRTApplicationHostRunTask("ambiguousRun") }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("select a host"))
    }

    @Test
    fun rejects_variant_task_suffix_collisions() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("java")
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val error = runCatching {
            project.extensions.getByType(WinRTExtension::class.java).application { application ->
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
                plugins { id 'java-library'; id 'io.github.compose-fluent.winrt' }
                repositories { mavenCentral() }
            """.trimIndent())
            writeGradleFile(root.resolve("$name/src/main/appxResources/Assets/$name.txt"), name)
        }
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'io.github.compose-fluent.winrt' }
            repositories { mavenCentral() }
            winRT { application {
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
            winRT { application { variants { create('second') {
                variantName = 'firstJvm:preview'
                mainClass = 'sample.SecondKt'
            } } } }
            ['First', 'Second'].each { suffix ->
                tasks.register('inspect' + suffix) {
                    def host = tasks.named('buildWinRTApplicationHost' + suffix).get()
                    def stage = tasks.named('stageWinRTApplicationPackage' + suffix).get()
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
                        assert tasks.named('generateWinRTApplicationIdentity' + suffix).get().outputFile.get().asFile.path.contains('variant-' + suffix)
                    }
                }
            }
        """.trimIndent())
        writeGradleFile(root.resolve("src/firstJvmPrimary/kotlin/sample/First.kt"), "package sample\nfun main() = println(\"first\")")
        writeGradleFile(root.resolve("src/firstJvmPreview/kotlin/sample/Second.kt"), "package sample\nfun main() = println(\"second\")")

        val result = runner(root, "inspectFirst", "inspectSecond").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":compilePrimaryKotlinFirstJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compilePreviewKotlinFirstJvm")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":firstLib:packageWinRTAppxResources")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":secondLib:packageWinRTAppxResources")?.outcome)
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
            winRT { application {
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
        writeGradleFile(root.resolve("src/commonMain/kotlin/io/github/composefluent/winrt/runtime/WinRTWindowsAppSdkBootstrap.kt"), """
            package io.github.composefluent.winrt.runtime
            enum class WinRTApplicationPackageIdentity { Packaged, Unpackaged }
            enum class WinRTWindowsAppSdkDeploymentMode { None, FrameworkDependent, SelfContained, ExternallyInitialized }
            data class WinRTApplicationHostConfiguration(
                val packageIdentity: WinRTApplicationPackageIdentity,
                val windowsAppSdkDeployment: WinRTWindowsAppSdkDeploymentMode,
            ) {
                companion object {
                    fun fromStagedRuntimeAssets(
                        packageIdentity: WinRTApplicationPackageIdentity,
                        windowsAppSdkDeployment: WinRTWindowsAppSdkDeploymentMode,
                        runtimeAssetsRoot: Any?,
                    ) = WinRTApplicationHostConfiguration(packageIdentity, windowsAppSdkDeployment)
                }
            }
            object WinRTWindowsAppSdkDeployment {
                fun discoverRuntimeAssetsRoot(): Any? = null
            }
            object WinRTWindowsAppSdkBootstrap {
                fun initializeApplicationHost(configuration: WinRTApplicationHostConfiguration): AutoCloseable {
                    val unpackaged = configuration.packageIdentity == WinRTApplicationPackageIdentity.Unpackaged
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

        val result = runner(root, "verifyWinRTApplicationPackage").build()
        listOf("First", "Second").forEach { name ->
            assertEquals(TaskOutcome.SUCCESS, result.task(":link${name}ReleaseExecutableDesktop")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":packageWinRTApplication$name")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, result.task(":verifyWinRTApplicationPackage$name")?.outcome)
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
        val repeated = runner(root, "verifyWinRTApplicationPackage").build()
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":packageWinRTApplicationFirst")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, repeated.task(":packageWinRTApplicationSecond")?.outcome)
    }

    @Test
    fun runtime_preparation_does_not_capture_application_models_in_configuration_cache() {
        val root = fixture("cached-runtime-preparation")
        writeGradleFile(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.winrt' }
            winRT { application {
                mainClass = 'sample.Main'
                jvmRuntimeMode = io.github.composefluent.winrt.gradle.WinRTJvmRuntimeMode.External
                externalJvmHome = file('${System.getProperty("java.home").replace("\\", "/")}')
                variants {
                    create('first') { variantName = 'jvm:main' }
                    create('second') { variantName = 'jvm:main' }
                }
            } }
        """.trimIndent())
        val first = runner(root, "prepareWinRTJvmRuntimeImage", "--configuration-cache").build()
        assertTrue(first.output, first.output.contains("Configuration cache entry stored"))
        val second = runner(root, "prepareWinRTJvmRuntimeImage", "--configuration-cache").build()
        assertTrue(second.output, second.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.SKIPPED, second.task(":prepareWinRTJvmRuntimeImageFirst")?.outcome)
        assertEquals(TaskOutcome.SKIPPED, second.task(":prepareWinRTJvmRuntimeImageSecond")?.outcome)
    }

    @Test
    fun rejects_two_applications_owning_the_same_native_binary() {
        val root = fixture("duplicate-native")
        writeGradleFile(root.resolve("build.gradle"), nativeBuildScript + """
            winRT { application {
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
            plugins { id 'java'; id 'io.github.compose-fluent.winrt' }
            winRT { application {
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

    private val nativeBuildScript = """
        plugins { id 'org.jetbrains.kotlin.multiplatform'; id 'io.github.compose-fluent.winrt' }
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
