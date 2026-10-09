package io.github.composefluent.windows.toolkit.gradle

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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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
                listOf("cl.exe", "clang-cl.exe", "vswhere.exe").none { name ->
                    runCatching { Files.isRegularFile(Path.of(directory.trim('"')).resolve(name)) }.getOrDefault(false)
                }
            }.joinToString(";")
        val root = Files.createTempDirectory("winrt-native-build-")
        val deepOutputPath = "nested-checkout-path/".repeat(13)
        val dllOutput = root.resolve("build/${deepOutputPath}dll/Component.dll")
        assertTrue(dllOutput.toString().length > 260)
        writeIcon(root.resolve("launcher.ico"), 0xff4466cc.toInt())
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'native-host-discovery'")
        Files.writeString(root.resolve("component.json"), """
            {"assemblyName":"Component","hostExportsClass":"sample.Exports","activatableClasses":["sample.Component"]}
        """.trimIndent())
        Files.writeString(root.resolve("build.gradle"), """
            plugins { id 'io.github.compose-fluent.windows-toolkit' apply false }
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
            def compiledIcon = tasks.register('compileIcon', io.github.composefluent.windows.toolkit.gradle.CompileWinAppIconTask) {
                launcherIcon.set(layout.projectDirectory.file('launcher.ico'))
                windowsSdkVersion.set('${sdk!!.version}')
                windowsSdkRegistryRoots.set(['${sdk.root.toString().replace('\\', '/')}'])
                outputFile.set(layout.buildDirectory.file('icon/launcher.res'))
            }
            tasks.register('buildExe', io.github.composefluent.windows.toolkit.gradle.BuildWinAppHostTask) {
                mainClass.set('sample.Main')
                executableBaseName.set('sample')
                javaHome.set(System.getProperty('java.home'))
                externalJvmHome.set(System.getProperty('java.home'))
                jvmRuntimeMode.set('External')
                expectedJavaMajor.set(Runtime.version().feature())
                runtimeIdentifier.set(hostRid)
                windowsSdkVersion.set('${sdk.version}')
                windowsSdkRegistryRoots.set(['${sdk.root.toString().replace('\\', '/')}'])
                outputDirectory.set(layout.buildDirectory.dir('exe'))
                generatedSourceDirectory.set(layout.buildDirectory.dir('exe-source'))
                commandWorkingDirectory.set(layout.projectDirectory)
                launcherIconResource.set(compiledIcon.flatMap { it.outputFile })
            }
            tasks.register('buildDll', io.github.composefluent.windows.toolkit.gradle.BuildWinRTAuthoringHostTask) {
                javaHome.set(System.getProperty('java.home'))
                runtimeIdentifier.set(hostRid)
                windowsSdkVersion.set('${sdk.version}')
                windowsSdkRegistryRoots.set(['${sdk.root.toString().replace('\\', '/')}'])
                authoredHostManifestFiles.from(componentManifest.flatMap { it.destinationDirectory }.map { it.file('component.json') })
                outputDirectory.set(layout.buildDirectory.dir('${deepOutputPath}dll'))
                generatedSourceDirectory.set(layout.buildDirectory.dir('${deepOutputPath}dll-source'))
                commandWorkingDirectory.set(layout.projectDirectory)
            }
            tasks.register('emptyDll', io.github.composefluent.windows.toolkit.gradle.BuildWinRTAuthoringHostTask) {
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
        assertTrue(peResourceTypes(root.resolve("build/exe/sample.exe")).containsAll(setOf(3, 14)))
        assertEquals(expectedMachine, peMachine(dllOutput))

        val second = runner(environment).build()
        assertTrue(second.output, second.output.contains("Reusing configuration cache"))
        listOf(":buildExe", ":buildDll").forEach { assertEquals(it, TaskOutcome.UP_TO_DATE, second.task(it)?.outcome) }

        val changed = runner(environment + ("CL" to "/DKOTLIN_WINRT_TOOLCHAIN_INPUT_TEST=1")).build()
        listOf(":buildExe", ":buildDll").forEach { assertEquals(it, TaskOutcome.SUCCESS, changed.task(it)?.outcome) }

        writeIcon(root.resolve("launcher.ico"), 0xffdd8844.toInt())
        val changedIcon = runner(environment + ("CL" to "/DKOTLIN_WINRT_TOOLCHAIN_INPUT_TEST=1")).build()
        assertEquals(TaskOutcome.SUCCESS, changedIcon.task(":compileIcon")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, changedIcon.task(":buildExe")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, changedIcon.task(":buildDll")?.outcome)
        assertTrue(peResourceTypes(root.resolve("build/exe/sample.exe")).containsAll(setOf(3, 14)))

        Files.writeString(root.resolve("build.gradle"), Files.readString(root.resolve("build.gradle"))
            .replace("file('launcher.ico')", "file('launcher.png')"))
        Files.write(root.resolve("launcher.png"), byteArrayOf(1))
        val invalid = runner(environment).buildAndFail()
        assertTrue(invalid.output, invalid.output.contains("requires a Win32 .ico file"))
    }

    private fun peMachine(file: Path): Int {
        val pe = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x4550, pe.getInt(pe.getInt(0x3c)))
        return pe.getShort(pe.getInt(0x3c) + 4).toInt() and 0xffff
    }

    private fun writeIcon(file: Path, color: Int) {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) image.setRGB(x, y, color)
        val png = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val ico = ByteBuffer.allocate(22 + png.size).order(ByteOrder.LITTLE_ENDIAN)
        ico.putShort(0).putShort(1).putShort(1)
        ico.put(16).put(16).put(0).put(0).putShort(1).putShort(32)
        ico.putInt(png.size).putInt(22).put(png)
        Files.write(file, ico.array())
    }

    private fun peResourceTypes(file: Path): Set<Int> {
        val pe = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN)
        val nt = pe.getInt(0x3c)
        val optional = nt + 24
        val directory = optional + if ((pe.getShort(optional).toInt() and 0xffff) == 0x20b) 112 else 96
        val resourceRva = pe.getInt(directory + 16)
        if (resourceRva == 0) return emptySet()
        val sectionCount = pe.getShort(nt + 6).toInt() and 0xffff
        val sections = optional + (pe.getShort(nt + 20).toInt() and 0xffff)
        for (i in 0 until sectionCount) {
            val section = sections + 40 * i
            val start = pe.getInt(section + 12)
            val size = maxOf(pe.getInt(section + 8), pe.getInt(section + 16))
            if (resourceRva in start until start + size) {
                val root = pe.getInt(section + 20) + resourceRva - start
                val entries = (pe.getShort(root + 12).toInt() and 0xffff) + (pe.getShort(root + 14).toInt() and 0xffff)
                return (0 until entries).map { pe.getInt(root + 16 + it * 8) }.toSet()
            }
        }
        return emptySet()
    }
}
