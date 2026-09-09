package io.github.composefluent.winrt.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class WindowsNativeHostBuildTest {
    @Test
    fun builds_exe_and_dll_from_ordinary_environment_and_tracks_toolchain_inputs_with_configuration_cache() {
        // Matches CsWinRT's WinRT.Host VC/SDK build prerequisites; no Developer Command Prompt or MSBuild invocation.
        assumeTrue(isWindowsHost())
        val sdk = findWindowsSdk()
        assumeTrue(sdk != null)
        val original = System.getenv().mapKeys { it.key.uppercase(Locale.ROOT) }
        assumeTrue(listOfNotNull(original["PROGRAMFILES(X86)"], original["PROGRAMFILES"])
            .any { Files.isRegularFile(Path.of(it).resolve("Microsoft Visual Studio/Installer/vswhere.exe")) })
        val environment = original.filterKeys {
            it !in setOf("INCLUDE", "LIB", "LIBPATH", "EXTERNAL_INCLUDE", "VSINSTALLDIR", "VCINSTALLDIR", "VCTOOLSINSTALLDIR",
                "WINDOWSSDKDIR", "WINDOWSSDKVERSION", "CL", "_CL_", "LINK", "_LINK_") &&
                !it.startsWith("VSCMD_") && !it.startsWith("__VSCMD") && !it.startsWith("__VCVARS")
        }.toMutableMap()
        environment["PATH"] = (original["__VSCMD_PREINIT_PATH"] ?: original["PATH"].orEmpty()).split(';')
            .filter { directory ->
                listOf("cl.exe", "clang-cl.exe").none { name ->
                    runCatching { Files.isRegularFile(Path.of(directory.trim('"')).resolve(name)) }.getOrDefault(false)
                }
            }.joinToString(";")
        val root = Files.createTempDirectory("winrt-native-build-")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'native-host-discovery'")
        Files.writeString(root.resolve("component.json"), """
            {"assemblyName":"Component","hostExportsClass":"sample.Exports","activatableClasses":["sample.Component"]}
        """.trimIndent())
        Files.writeString(root.resolve("build.gradle"), """
            plugins { id 'io.github.compose-fluent.winrt' apply false }
            def hostRid = System.getProperty('os.arch').toLowerCase() in ['aarch64', 'arm64'] ? 'win-arm64' : 'win-x64'
            abstract class ComponentManifest extends DefaultTask {
                @InputFile abstract RegularFileProperty getSourceFile()
                @OutputDirectory abstract DirectoryProperty getDestinationDirectory()
                @TaskAction void generate() {
                    def directory = destinationDirectory.get().asFile
                    directory.mkdirs()
                    new File(directory, 'component.json').text = sourceFile.get().asFile.text
                }
            }
            def componentManifest = tasks.register('componentManifest', ComponentManifest) {
                sourceFile.set(layout.projectDirectory.file('component.json'))
                destinationDirectory.set(layout.buildDirectory.dir('generated-manifest'))
            }
            tasks.register('buildExe', io.github.composefluent.winrt.gradle.BuildWinRTApplicationHostTask) {
                mainClass.set('sample.Main')
                executableBaseName.set('sample')
                javaHome.set(System.getProperty('java.home'))
                externalJvmHome.set(System.getProperty('java.home'))
                jvmRuntimeMode.set('External')
                expectedJavaMajor.set(Runtime.version().feature())
                runtimeIdentifier.set(hostRid)
                windowsSdkVersion.set('${sdk!!.version}')
                windowsSdkRegistryRoots.set(['${sdk.root.toString().replace('\\', '/')}'])
                outputDirectory.set(layout.buildDirectory.dir('exe'))
                generatedSourceDirectory.set(layout.buildDirectory.dir('exe-source'))
                commandWorkingDirectory.set(layout.projectDirectory)
            }
            tasks.register('buildDll', io.github.composefluent.winrt.gradle.BuildWinRTAuthoringHostTask) {
                javaHome.set(System.getProperty('java.home'))
                runtimeIdentifier.set(hostRid)
                windowsSdkVersion.set('${sdk.version}')
                windowsSdkRegistryRoots.set(['${sdk.root.toString().replace('\\', '/')}'])
                authoredHostManifestFiles.from(componentManifest.flatMap { it.destinationDirectory }.map { it.file('component.json') })
                outputDirectory.set(layout.buildDirectory.dir('dll'))
                generatedSourceDirectory.set(layout.buildDirectory.dir('dll-source'))
                commandWorkingDirectory.set(layout.projectDirectory)
            }
            tasks.register('emptyDll', io.github.composefluent.winrt.gradle.BuildWinRTAuthoringHostTask) {
                javaHome.set(System.getProperty('java.home'))
                runtimeIdentifier.set(hostRid)
                windowsSdkVersion.set('0.0.0.0')
                outputDirectory.set(layout.buildDirectory.dir('empty'))
                generatedSourceDirectory.set(layout.buildDirectory.dir('empty-source'))
            }
        """.trimIndent())
        fun runner(env: Map<String, String>) = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withEnvironment(env)
            .withArguments("buildExe", "buildDll", "emptyDll", "--configuration-cache", "--console=plain", "--max-workers=1", "--info")

        val first = runner(environment).build()
        listOf(":buildExe", ":buildDll", ":emptyDll").forEach { assertEquals(it, TaskOutcome.SUCCESS, first.task(it)?.outcome) }
        assertTrue(first.output, first.output.contains("Kotlin/WinRT JVM application host:") && first.output.contains("cl.exe"))
        val expectedMachine = if (System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64")) 0xAA64 else 0x8664
        assertEquals(expectedMachine, peMachine(root.resolve("build/exe/sample.exe")))
        assertEquals(expectedMachine, peMachine(root.resolve("build/dll/Component.dll")))

        val second = runner(environment).build()
        assertTrue(second.output, second.output.contains("Reusing configuration cache"))
        listOf(":buildExe", ":buildDll").forEach { assertEquals(it, TaskOutcome.UP_TO_DATE, second.task(it)?.outcome) }

        val changed = runner(environment + ("CL" to "/DKOTLIN_WINRT_TOOLCHAIN_INPUT_TEST=1")).build()
        listOf(":buildExe", ":buildDll").forEach { assertEquals(it, TaskOutcome.SUCCESS, changed.task(it)?.outcome) }
    }

    private fun peMachine(file: Path): Int {
        val pe = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x4550, pe.getInt(pe.getInt(0x3c)))
        return pe.getShort(pe.getInt(0x3c) + 4).toInt() and 0xffff
    }
}
