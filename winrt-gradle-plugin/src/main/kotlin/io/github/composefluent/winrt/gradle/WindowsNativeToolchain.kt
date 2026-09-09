package io.github.composefluent.winrt.gradle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isRegularFile

/** The Gradle-owned equivalent of the VC/SDK build environment used by CsWinRT's WinRT.Host.vcxproj. */
data class WindowsNativeToolchain(
    val compiler: String,
    val compilerArguments: List<String>,
    val environment: Map<String, String>,
    val sdkRoot: String,
    val sdkVersion: String,
    val toolFingerprint: Map<String, String>,
) : Serializable {
    internal val sdk: WindowsSdkLayout
        get() = Path.of(sdkRoot).let { root ->
            WindowsSdkLayout(root, sdkVersion, root.resolve("Include/$sdkVersion"), root.resolve("Lib/$sdkVersion"), root.resolve("bin/$sdkVersion"))
        }

    internal fun compile(arguments: List<String>, workingDirectory: Path): WindowsNativeProcessResult =
        runWindowsNativeProcess(arguments, System.getenv().mapKeys { it.key.uppercase(Locale.ROOT) } + environment, workingDirectory)
}

internal data class WindowsNativeProcessResult(val exitCode: Int, val output: String)

internal fun runWindowsNativeProcess(
    arguments: List<String>,
    environment: Map<String, String>,
    workingDirectory: Path? = null,
    charset: Charset = Charset.defaultCharset(),
): WindowsNativeProcessResult {
    val process = ProcessBuilder(arguments)
        .directory(workingDirectory?.toFile())
        .redirectErrorStream(true)
        .apply { environment().apply { clear(); putAll(environment) } }
        .start()
    val output = process.inputStream.bufferedReader(charset).use { it.readText() }
    return WindowsNativeProcessResult(process.waitFor(), output)
}

internal class WindowsNativeToolchainDiscovery(
    environment: Map<String, String> = System.getenv(),
    private val commandRunner: (List<String>, Map<String, String>, Charset) -> WindowsNativeProcessResult = { args, env, charset ->
        runWindowsNativeProcess(args, env, charset = charset)
    },
) {
    private val environment = environment.mapKeys { it.key.uppercase(Locale.ROOT) }

    fun resolve(runtimeIdentifier: String, sdk: WindowsSdkLayout): WindowsNativeToolchain {
        val target = windowsSdkArchitecture(runtimeIdentifier)
        val host = hostArchitecture()
        val pathClang = findExecutable("clang-cl.exe", environment)
        val failures = mutableListOf<String>()
        val currentTarget = environment["VSCMD_ARG_TGT_ARCH"]
            ?: findExecutable("cl.exe", environment)?.parent?.fileName?.toString()
        if (currentTarget.equals(target, ignoreCase = true)) {
            fromEnvironment(environment, pathClang, target, sdk)?.let { return it }
        }

        val instances = linkedSetOf<Path>()
        environment["VSINSTALLDIR"]?.takeIf(String::isNotBlank)?.let { instances.add(Path.of(it)) }
        val vswhere = findExecutable("vswhere.exe", environment)
            ?: listOfNotNull(environment["PROGRAMFILES(X86)"], environment["PROGRAMFILES"])
                .map { Path.of(it).resolve("Microsoft Visual Studio/Installer/vswhere.exe") }
                .firstOrNull { it.isRegularFile() }
        if (vswhere != null) {
            val component = if (target == "arm64") "Microsoft.VisualStudio.Component.VC.Tools.ARM64"
                else "Microsoft.VisualStudio.Component.VC.Tools.x86.x64"
            val query = commandRunner(
                listOf(vswhere.toString(), "-products", "*", "-requires", component, "-prerelease", "-sort", "-format", "json", "-utf8"),
                environment,
                Charsets.UTF_8,
            )
            if (query.exitCode == 0) {
                val entries = runCatching { Json.parseToJsonElement(query.output.trim().removePrefix("\uFEFF")).jsonArray }
                    .getOrElse { throw IllegalStateException("Cannot parse Visual Studio installations reported by $vswhere.", it) }
                entries.sortedBy { it.jsonObject["isPrerelease"]?.jsonPrimitive?.booleanOrNull == true }
                    .mapNotNull { it.jsonObject["installationPath"]?.jsonPrimitive?.contentOrNull }
                    .filter(String::isNotBlank)
                    .forEach { instances.add(Path.of(it)) }
            } else {
                failures.add("$vswhere failed with exit code ${query.exitCode}: ${query.output.trim()}")
            }
        }

        for (instance in instances) {
            val script = instance.resolve("Common7/Tools/VsDevCmd.bat")
            if (!script.isRegularFile()) {
                failures.add("$instance: Common7/Tools/VsDevCmd.bat is missing.")
                continue
            }
            // Start from the pre-VS environment so a different target/instance cannot leak CRT libraries.
            val cleanEnvironment = environment.toMutableMap().apply {
                keys.removeAll { (it in TOOLCHAIN_ENVIRONMENT_KEYS && it !in COMPILER_OPTION_KEYS) || it.startsWith("VSCMD_") || it.startsWith("__VSCMD") || it.startsWith("__VCVARS") }
                put("PATH", environment["__VSCMD_PREINIT_PATH"] ?: environment["PATH"].orEmpty())
                put("KOTLIN_WINRT_VSDEVCMD", script.toString())
            }
            val command = "call \"%KOTLIN_WINRT_VSDEVCMD%\" -no_logo -arch=$target -host_arch=$host -winsdk=${sdk.version} -app_platform=Desktop -startdir=none" +
                " && echo $ENVIRONMENT_MARKER && set"
            val initialized = commandRunner(
                listOf(environment["COMSPEC"] ?: "cmd.exe", "/d", "/u", "/c", command),
                cleanEnvironment,
                Charsets.UTF_16LE,
            )
            val marker = initialized.output.lineSequence().indexOfFirst { it.trim() == ENVIRONMENT_MARKER }
            if (initialized.exitCode != 0 || marker < 0) {
                // Never include a successful `set` dump (which can contain credentials) in diagnostics.
                failures.add("$script failed for $host -> $target / SDK ${sdk.version} (exit ${initialized.exitCode}). " +
                    initialized.output.substringBefore(ENVIRONMENT_MARKER).trim())
                continue
            }
            val prepared = initialized.output.lineSequence().drop(marker + 1)
                .filter { it.indexOf('=') > 0 }
                .associate { it.substringBefore('=').uppercase(Locale.ROOT) to it.substringAfter('=') }
            val compiler = pathClang ?: findExecutable("cl.exe", prepared)
                ?: standardClangCandidates().firstOrNull { it.isRegularFile() }
            fromEnvironment(prepared, compiler, target, sdk)?.let { return it }
            failures.add("$instance did not provide a complete $target C/C++ environment for Windows SDK ${sdk.version}.")
        }

        // Keep standalone LLVM/custom toolchain environments usable, without treating clang alone as a CRT.
        if (currentTarget == null || currentTarget.equals(target, ignoreCase = true)) {
            val clang = pathClang ?: standardClangCandidates().firstOrNull { it.isRegularFile() }
            fromEnvironment(environment, clang, target, sdk)?.let { return it }
        }
        throw IllegalStateException(
            "No usable Windows C/C++ toolchain for $runtimeIdentifier (Windows SDK ${sdk.version}). " +
                "Install the MSVC C++ build tools for $target and the selected Windows SDK using Visual Studio Installer " +
                "(Visual Studio or Build Tools). Kotlin/WinRT discovers these installations automatically; a Developer Command Prompt is not required." +
                failures.joinToString(separator = "\n", prefix = if (failures.isEmpty()) "" else "\n"),
        )
    }

    private fun fromEnvironment(
        prepared: Map<String, String>,
        preferredCompiler: Path?,
        target: String,
        sdk: WindowsSdkLayout,
    ): WindowsNativeToolchain? {
        val compiler = preferredCompiler ?: findExecutable("cl.exe", prepared) ?: return null
        val clang = compiler.fileName.toString().equals("clang-cl.exe", ignoreCase = true)
        if (prepared["VSCMD_ARG_TGT_ARCH"]?.equals(target, true) == false) return null
        if (!clang && !prepared["VSCMD_ARG_TGT_ARCH"].orEmpty().ifBlank { compiler.parent.fileName.toString() }.equals(target, true)) return null
        val sdkVersion = prepared["WINDOWSSDKVERSION"]?.trimEnd('\\', '/')
        if (!sdkVersion.isNullOrBlank() && sdkVersion != sdk.version) return null
        if (!containsFile(prepared["INCLUDE"], "vcruntime.h") || !containsFile(prepared["LIB"], "libcmt.lib")) return null
        val linker = if (clang) compiler.parent.resolve("lld-link.exe").takeIf { it.isRegularFile() }
            ?: findExecutable("lld-link.exe", prepared) ?: findExecutable("link.exe", prepared)
        else compiler.parent.resolve("link.exe").takeIf { it.isRegularFile() }
        if (linker == null) return null
        val compileEnvironment = TOOLCHAIN_ENVIRONMENT_KEYS.associateWith { prepared[it].orEmpty() }.toMutableMap()
        compileEnvironment["PATH"] = listOf(compiler.parent.toString(), linker.parent.toString(), prepared["PATH"].orEmpty()).joinToString(";")
        return WindowsNativeToolchain(
            compiler = compiler.toString(),
            compilerArguments = if (clang) buildList {
                val triple = when (target) { "x86" -> "i686"; "arm64" -> "aarch64"; else -> "x86_64" }
                add("--target=$triple-pc-windows-msvc")
                if (linker.fileName.toString().equals("lld-link.exe", true)) add("-fuse-ld=lld")
            } else emptyList(),
            environment = compileEnvironment,
            sdkRoot = sdk.root.toString(),
            sdkVersion = sdk.version,
            toolFingerprint = listOf(compiler, linker).associate { it.toString() to "${Files.size(it)}:${Files.getLastModifiedTime(it).toMillis()}" },
        )
    }

    private fun hostArchitecture(): String =
        when ((environment["PROCESSOR_ARCHITEW6432"] ?: environment["PROCESSOR_ARCHITECTURE"] ?: System.getProperty("os.arch")).lowercase()) {
            "arm64", "aarch64" -> "arm64"
            "x86", "i386", "i686" -> "x86"
            else -> "x64"
        }

    private fun standardClangCandidates(): List<Path> =
        listOfNotNull(environment["PROGRAMFILES"], environment["PROGRAMFILES(X86)"])
            .map { Path.of(it).resolve("LLVM/bin/clang-cl.exe") }

    private fun findExecutable(name: String, env: Map<String, String>): Path? =
        pathEntries(env["PATH"]).map { it.resolve(name) }.firstOrNull { it.isRegularFile() }

    private fun containsFile(paths: String?, name: String): Boolean = pathEntries(paths).any { it.resolve(name).isRegularFile() }

    private fun pathEntries(paths: String?): Sequence<Path> = paths.orEmpty().split(';').asSequence()
        .map { it.trim().trim('"') }.filter(String::isNotBlank).mapNotNull { runCatching { Path.of(it) }.getOrNull() }
}

private const val ENVIRONMENT_MARKER = "__KOTLIN_WINRT_NATIVE_ENVIRONMENT__"
private val COMPILER_OPTION_KEYS = setOf("CL", "_CL_", "LINK", "_LINK_")
private val TOOLCHAIN_ENVIRONMENT_KEYS = setOf(
    "PATH", "INCLUDE", "EXTERNAL_INCLUDE", "LIB", "LIBPATH", "CL", "_CL_", "LINK", "_LINK_",
    "VSINSTALLDIR", "VCINSTALLDIR", "VCTOOLSINSTALLDIR", "VCTOOLSVERSION", "VISUALSTUDIOVERSION",
    "WINDOWSSDKDIR", "WINDOWSSDKVERSION", "WINDOWSSDKLIBVERSION", "UNIVERSALCRTSDKDIR", "UCRTVERSION",
    "VSCMD_ARG_HOST_ARCH", "VSCMD_ARG_TGT_ARCH",
)
