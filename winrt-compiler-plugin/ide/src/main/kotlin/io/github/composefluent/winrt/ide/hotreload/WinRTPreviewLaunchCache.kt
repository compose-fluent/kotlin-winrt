package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** IDE launch optimization only; Gradle remains the owner of compilation and deployment.
 * Reuse is limited to the desktop Native package path that already supports CreateProcess
 * in NativeDebugLauncher, plus the SDK designer with its staged JVM launcher.
 * UWP activation and ordinary JVM application environment setup still use Gradle.
 */
internal class WinRTPreviewLaunchCache(
    private val buildRoot: Path,
    private val modules: List<WinRTModuleData>,
    private val module: WinRTModuleData,
    private val launch: WinUIXamlHotReloadLaunchData,
) {
    private val sdk = launch.taskName == "runWinRTXamlSdkPreview" && module.staticPreview?.launch() == launch
    private val executable = Path.of(launch.previewExecutable).toAbsolutePath().normalize()
    val deployment: Path = executable.parent
    private val receipt = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-preview-launch/" +
        digest(launch.taskName.toByteArray()) + ".txt")

    fun supported(): Boolean {
        if (!executable.startsWith(Path.of(module.buildDirectory).toAbsolutePath().normalize()) || !Files.isRegularFile(executable)) return false
        if (sdk) return module.staticPreview?.metadataReferencesFile?.let { Files.isRegularFile(Path.of(it)) } == true
        return launch.nativeCompilerHome.isNotBlank() && launch.taskName.startsWith("runWinAppPackage") &&
            runCatching { Files.readString(deployment.resolve("AppxManifest.xml")).contains("Windows.FullTrustApplication") }.getOrDefault(false)
    }

    /** Content hashes catch edits with preserved timestamps and additions/deletions in dependencies.
     * Build outputs are excluded here and separately checked in the deployment fingerprint.
     */
    fun inputs(): String = fingerprint(buildList {
        add(buildRoot)
        modules.forEach { m ->
            add(Path.of(m.projectDirectory))
            (m.sourceSets.flatMap { it.kotlinRoots + it.appxResourceRoots } +
                m.xamlCompilations.flatMap { it.sourceRoots } + m.manifestFiles + m.restoreLockFiles)
                .filter(String::isNotBlank).forEach { add(Path.of(it)) }
        }
    }, content = true, excludeBuild = true, configurationOnly = sdk)

    private fun outputs() = fingerprint(listOf(deployment), content = false, excludeBuild = false)
    fun reusable(inputs: String): Boolean = supported() && runCatching {
        val stored = Files.readAllLines(receipt)
        val current = record(inputs).lines()
        if (stored != current) {
            com.intellij.openapi.diagnostic.Logger.getInstance(WinRTPreviewLaunchCache::class.java)
                .info("Preview build cache changed: " + current.filter { it !in stored }.map { it.substringBefore('=') })
        }
        stored == current
    }.getOrDefault(false)

    fun remember(inputs: String) {
        if (!supported()) return
        Files.createDirectories(receipt.parent)
        Files.writeString(receipt, record(inputs))
    }

    fun invalidate() { Files.deleteIfExists(receipt) }

    private fun record(inputs: String): String = linkedMapOf(
        "format" to if (sdk) "sdk-desktop-preview-v1" else "native-desktop-preview-v2",
        "launch" to launch.toString(),
        "model" to modules.toString(),
        "inputs" to inputs,
        "outputs" to outputs(),
        "plugin" to javaClass.protectionDomain.codeSource?.location?.let { url ->
            runCatching { Files.getLastModifiedTime(Path.of(url.toURI())).toString() }.getOrDefault(url.toString())
        }.orEmpty(),
        // Launcher/session variables change every IDE process. Only toolchain
        // locations affect whether the existing build can be reused; application
        // environment is inherited afresh when starting the native process.
        "toolchain" to listOf("JAVA_HOME", "GRADLE_USER_HOME", "KONAN_DATA_DIR", "WindowsSdkDir", "VCToolsInstallDir")
            .associateWith { System.getenv(it).orEmpty() }.toString(),
    ).entries.joinToString("\n") { (name, value) -> "$name=${digest(value.toByteArray())}" }

    private fun fingerprint(roots: List<Path>, content: Boolean, excludeBuild: Boolean, configurationOnly: Boolean = false): String {
        val files = sortedSetOf<Path>()
        val missing = sortedSetOf<String>()
        val excluded = setOf("build", ".gradle", ".git", ".idea", ".kotlin", ".konan", "node_modules")
        roots.map { it.toAbsolutePath().normalize() }.distinct().forEach { root ->
            if (!Files.exists(root)) { missing += root.toString(); return@forEach }
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (excludeBuild && dir != root && dir.fileName.toString() in excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    check(!attrs.isSymbolicLink) { "Preview input links require Gradle validation" }
                    // SDK host inputs exclude app XAML/Kotlin edits. Build logic,
                    // version catalogs, restore locks and model/toolchain identity
                    // still invalidate it; deployed runtime changes are checked below.
                    val name = file.fileName.toString()
                    val configuration = name.endsWith(".gradle.kts") || name.endsWith(".gradle") ||
                        name.endsWith(".toml") || name.endsWith(".properties") || name.endsWith(".json") ||
                        file.any { it.toString() in setOf("buildSrc", "build-logic", "gradle") }
                    if (attrs.isRegularFile && (!configurationOnly || configuration)) files.add(file)
                    check(files.size <= 30_000) { "Preview inputs require Gradle validation" }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        val hash = MessageDigest.getInstance("SHA-256")
        fun add(text: String) { hash.update(text.toByteArray()); hash.update(0.toByte()) }
        missing.forEach { add("missing:$it") }
        files.forEach { file ->
            add(file.toString())
            if (content) Files.newInputStream(file).use { stream ->
                val buffer = ByteArray(65536)
                while (true) { val count = stream.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
            } else { add(Files.size(file).toString()); add(Files.getLastModifiedTime(file).toString()) }
            hash.update(0.toByte())
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
