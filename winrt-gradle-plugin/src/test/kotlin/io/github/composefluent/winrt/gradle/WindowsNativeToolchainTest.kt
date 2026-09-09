package io.github.composefluent.winrt.gradle

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class WindowsNativeToolchainTest {
    // CsWinRT's Authoring/WinRT.Host/WinRT.Host.vcxproj delegates host/target and CRT selection to VC build tools.
    @Test
    fun discovers_custom_visual_studio_location_without_compiler_on_path() {
        val fixture = Fixture()
        val instance = fixture.instance("custom drive/Build Tools")
        val prepared = fixture.prepared(instance, "x64") + ("SECRET_TOKEN" to "not-a-task-input")
        val toolchain = fixture.discover(listOf(instance), prepared).resolve("win-x64", fixture.sdk)

        assertEquals(Path.of(prepared.getValue("PATH")).resolve("cl.exe").toString(), toolchain.compiler)
        assertEquals(prepared.getValue("INCLUDE"), toolchain.environment["INCLUDE"])
        assertEquals(prepared.getValue("LIB"), toolchain.environment["LIB"])
        assertEquals(fixture.sdk.version, toolchain.sdkVersion)
        assertFalse(toolchain.environment.containsKey("SECRET_TOKEN"))
        assertTrue(fixture.calls[0].arguments.containsAll(listOf("-products", "*", "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64")))
        val setup = fixture.calls[1]
        assertTrue(setup.arguments.last().contains("-arch=x64 -host_arch=x64 -winsdk=${fixture.sdk.version}"))
        assertEquals(instance.resolve("Common7/Tools/VsDevCmd.bat").toString(), setup.environment["KOTLIN_WINRT_VSDEVCMD"])
        assertEquals(Charsets.UTF_16LE, setup.charset)
    }

    @Test
    fun reuses_complete_matching_developer_environment() {
        val fixture = Fixture()
        val prepared = fixture.prepared(fixture.instance("existing VS"), "x64")
        val toolchain = fixture.discover(emptyList(), emptyMap(), fixture.baseEnvironment + prepared).resolve("win-x64", fixture.sdk)

        assertTrue(toolchain.compiler.endsWith("cl.exe"))
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun compiler_on_path_without_crt_environment_still_initializes_visual_studio() {
        val fixture = Fixture()
        val instance = fixture.instance("VS")
        val prepared = fixture.prepared(instance, "x64")
        fixture.discover(listOf(instance), prepared, fixture.baseEnvironment + ("Path" to prepared.getValue("PATH")))
            .resolve("win-x64", fixture.sdk)

        assertEquals(2, fixture.calls.size)
    }

    @Test
    fun reinitializes_wrong_target_and_sdk_without_leaking_previous_developer_environment() {
        val fixture = Fixture()
        val instance = fixture.instance("VS")
        val previous = fixture.prepared(instance, "x86") + mapOf(
            "WINDOWSSDKVERSION" to "10.0.19041.0\\",
            "__VSCMD_PREINIT_PATH" to fixture.baseEnvironment.getValue("PATH"),
            "VSCMD_VER" to "17.0",
            "CL" to "/DUSER_OPTION=1",
        )
        val prepared = fixture.prepared(instance, "arm64")
        val toolchain = fixture.discover(listOf(instance), prepared, fixture.baseEnvironment + previous).resolve("win-arm64", fixture.sdk)

        assertTrue(fixture.calls[0].arguments.contains("Microsoft.VisualStudio.Component.VC.Tools.ARM64"))
        assertTrue(fixture.calls[1].arguments.last().contains("-arch=arm64 -host_arch=x64"))
        assertFalse(fixture.calls[1].environment.containsKey("LIB"))
        assertFalse(fixture.calls[1].environment.containsKey("VSCMD_VER"))
        assertEquals("/DUSER_OPTION=1", fixture.calls[1].environment["CL"])
        assertEquals(prepared.getValue("LIB"), toolchain.environment["LIB"])
    }

    @Test
    fun preserves_path_clang_and_selects_explicit_cross_compilation_target_and_linker() {
        val fixture = Fixture()
        val instance = fixture.instance("VS")
        val clang = fixture.file("custom LLVM/bin/clang-cl.exe")
        fixture.file("custom LLVM/bin/lld-link.exe")
        val prepared = fixture.prepared(instance, "x86")
        val original = fixture.baseEnvironment + ("PATH" to "${clang.parent};${fixture.baseEnvironment.getValue("PATH")}")
        val toolchain = fixture.discover(listOf(instance), prepared, original).resolve("win-x86", fixture.sdk)

        assertEquals(clang.toString(), toolchain.compiler)
        assertEquals(listOf("--target=i686-pc-windows-msvc", "-fuse-ld=lld"), toolchain.compilerArguments)
        assertTrue(toolchain.environment.getValue("PATH").startsWith(clang.parent.toString()))
        assertTrue(toolchain.toolFingerprint.keys.any { it.endsWith("lld-link.exe") })
    }

    @Test
    fun supports_standalone_llvm_with_explicit_crt_and_linker_environment() {
        val fixture = Fixture()
        val clang = fixture.file("Program Files/LLVM/bin/clang-cl.exe")
        fixture.file("Program Files/LLVM/bin/lld-link.exe")
        val prepared = fixture.prepared(fixture.instance("CRT"), "arm64") - "VSCMD_ARG_TGT_ARCH" - "PATH"
        val original = fixture.baseEnvironment + prepared + ("ProgramFiles" to fixture.root.resolve("Program Files").toString())
        val toolchain = fixture.discover(emptyList(), emptyMap(), original).resolve("win-arm64", fixture.sdk)

        assertEquals(clang.toString(), toolchain.compiler)
        assertTrue(toolchain.compilerArguments.contains("--target=aarch64-pc-windows-msvc"))
    }

    @Test
    fun uses_native_host_architecture_in_a_wow64_process() {
        val fixture = Fixture()
        val instance = fixture.instance("ARM VS")
        val original = fixture.baseEnvironment + mapOf("PROCESSOR_ARCHITECTURE" to "x86", "PROCESSOR_ARCHITEW6432" to "ARM64")
        fixture.discover(listOf(instance), fixture.prepared(instance, "arm64"), original).resolve("win-arm64", fixture.sdk)

        assertTrue(fixture.calls[1].arguments.last().contains("-host_arch=arm64"))
    }

    @Test
    fun skips_incomplete_instance_and_uses_next_installation() {
        val fixture = Fixture()
        val first = Files.createDirectories(fixture.root.resolve("incomplete VS"))
        val second = fixture.instance("complete VS")
        val toolchain = fixture.discover(listOf(first, second), fixture.prepared(second, "x64")).resolve("win-x64", fixture.sdk)

        assertTrue(Path.of(toolchain.compiler).startsWith(second))
    }

    @Test
    fun rejects_missing_target_components_with_actionable_diagnostic() {
        val fixture = Fixture()
        val error = assertThrows(IllegalStateException::class.java) {
            fixture.discover(emptyList(), emptyMap()).resolve("win-arm64", fixture.sdk)
        }

        assertTrue(error.message.orEmpty().contains("MSVC C++ build tools for arm64"))
        assertTrue(error.message.orEmpty().contains("Visual Studio Installer"))
        assertTrue(error.message.orEmpty().contains(fixture.sdk.version))
    }

    @Test
    fun reports_failed_environment_initialization_without_environment_dump() {
        val fixture = Fixture()
        val instance = fixture.instance("broken VS")
        val error = assertThrows(IllegalStateException::class.java) {
            fixture.discover(listOf(instance), emptyMap(), setupResult = WindowsNativeProcessResult(1, "SDK is missing"))
                .resolve("win-x64", fixture.sdk)
        }

        assertTrue(error.message.orEmpty().contains("SDK is missing"))
        assertTrue(error.message.orEmpty().contains("exit 1"))
    }

    @Test
    fun rejects_environment_script_that_silently_selects_another_sdk() {
        val fixture = Fixture()
        val instance = fixture.instance("VS")
        val prepared = fixture.prepared(instance, "x64") + ("WINDOWSSDKVERSION" to "10.0.19041.0\\")
        assertThrows(IllegalStateException::class.java) {
            fixture.discover(listOf(instance), prepared).resolve("win-x64", fixture.sdk)
        }
    }

    private class Fixture {
        val root: Path = Files.createTempDirectory("winrt-native-toolchain-")
        val sdk = WindowsSdkLayout(root.resolve("SDK"), "10.0.26100.0", root.resolve("include"), root.resolve("lib"), root.resolve("bin"))
        private val vswhere = file("Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe")
        val baseEnvironment = mapOf(
            "PATH" to root.resolve("system32").toString(),
            "PROGRAMFILES(X86)" to vswhere.parent.parent.parent.toString(),
            "PROCESSOR_ARCHITECTURE" to "AMD64",
        )
        val calls = mutableListOf<Invocation>()

        fun instance(name: String): Path = root.resolve(name).also { file("$name/Common7/Tools/VsDevCmd.bat") }

        fun file(name: String): Path = root.resolve(name).also { Files.createDirectories(it.parent); Files.writeString(it, "fixture") }

        fun prepared(instance: Path, target: String): Map<String, String> {
            val prefix = root.relativize(instance).toString()
            val compiler = file("$prefix/VC/bin/$target/cl.exe")
            file("$prefix/VC/bin/$target/link.exe")
            return mapOf(
                "PATH" to compiler.parent.toString(),
                "INCLUDE" to file("$prefix/VC/include/vcruntime.h").parent.toString(),
                "LIB" to file("$prefix/VC/lib/$target/libcmt.lib").parent.toString(),
                "VSCMD_ARG_TGT_ARCH" to target,
                "WINDOWSSDKVERSION" to "${sdk.version}\\",
            )
        }

        fun discover(
            instances: List<Path>,
            prepared: Map<String, String>,
            original: Map<String, String> = baseEnvironment,
            setupResult: WindowsNativeProcessResult? = null,
        ): WindowsNativeToolchainDiscovery = WindowsNativeToolchainDiscovery(original) { arguments, environment, charset ->
            calls.add(Invocation(arguments, environment, charset))
            if (arguments.first().endsWith("vswhere.exe")) {
                WindowsNativeProcessResult(0, JsonArray(instances.map { JsonObject(mapOf("installationPath" to JsonPrimitive(it.toString()))) }).toString())
            } else {
                setupResult ?: WindowsNativeProcessResult(0,
                    "banner\r\n__KOTLIN_WINRT_NATIVE_ENVIRONMENT__\r\n" + prepared.entries.joinToString("\r\n") { "${it.key}=${it.value}" })
            }
        }
    }

    private data class Invocation(val arguments: List<String>, val environment: Map<String, String>, val charset: Charset)
}
