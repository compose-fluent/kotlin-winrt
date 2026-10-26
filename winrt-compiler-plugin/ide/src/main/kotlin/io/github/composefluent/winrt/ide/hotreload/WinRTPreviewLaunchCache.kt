package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** IDE launch optimization only; Gradle remains the owner of compilation and deployment.
 * Reuse is limited to the isolated desktop Native deployment that already supports CreateProcess
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
        return runCatching { io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.read(deployment.parent.resolve(
            io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.FILE_NAME)) }.isSuccess && launch.nativeCompilerHome.isNotBlank() && (
            launch.taskName.startsWith("runWinAppNative") && launch.isDirectPreview ||
            launch.taskName.startsWith("runWinAppPackage") &&
                runCatching { Files.readString(deployment.resolve("AppxManifest.xml")).contains("Windows.FullTrustApplication") }.getOrDefault(false))
    }

    /** Content hashes catch edits with preserved timestamps and additions/deletions in dependencies.
     * Build outputs are excluded here and separately checked in the deployment fingerprint.
     */
    fun inputs(): String = inputs(emptyList())

    private val generatedRoots get() = modules.flatMap { it.sourceSets }.flatMap { it.generatedKotlinRoots.orEmpty() }
        .map { Path.of(it).toAbsolutePath().normalize() }

    fun buildInputs(): String = inputs(generatedRoots)

    private fun inputs(ignored: List<Path>): String = fingerprint(buildList {
        add(buildRoot)
        val gradleHome = System.getenv("GRADLE_USER_HOME")?.takeIf(String::isNotBlank)?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".gradle")
        listOf("gradle.properties", "init.gradle", "init.gradle.kts", "init.d").forEach { add(gradleHome.resolve(it)) }
        modules.forEach { m ->
            add(Path.of(m.projectDirectory))
            (m.sourceSets.flatMap { it.kotlinRoots + it.appxResourceRoots } +
                m.xamlCompilations.flatMap { it.sourceRoots } + m.manifestFiles + m.restoreLockFiles + listOf(m.nuGetConfigFile))
                .filter(String::isNotBlank).forEach { add(Path.of(it)) }
        }
    }, content = true, excludeBuild = true, configurationOnly = sdk, ignored = ignored)

    private fun outputs() = fingerprint(listOf(deployment), content = false, excludeBuild = false)
    /** Routing hint only. start() must still validate all contents before reuse. */
    fun hasCandidate(): Boolean = supported() && Files.isRegularFile(receipt)

    /** Independent file sets are read concurrently, but neither authorizes launch
     * until the full receipt matches. Every file is still content-hashed before reuse. */
    suspend fun checkedInputs(): Pair<String, Boolean> = coroutineScope {
        if (!hasCandidate()) return@coroutineScope inputs() to false
        val environment = async(Dispatchers.IO) { runCatching { environment() } }
        val inputs = inputs()
        val prepared = environment.await()
        inputs to reusable(inputs, { prepared.getOrThrow() })
    }

    /** Identity of the currently connected compiled baseline. It is independent
     * of the cold-launch receipt: generated sources may change during a build,
     * preventing that receipt from being committed. Only its owning live session
     * may retain this identity for a theme-only replacement. */
    fun connectedDeploymentIdentity(): String? = if (!supported()) null
        else runCatching { record("", environment()) }.getOrNull()

    var validatedDeploymentIdentity: String? = null
        private set

    fun reusable(inputs: String): Boolean = reusable(inputs, ::environment)

    private fun reusable(inputs: String, environment: () -> Map<String, String>): Boolean = supported() && runCatching {
        val stored = Files.readAllLines(receipt)
        val prepared = environment()
        val current = record(inputs, prepared).lines()
        validatedDeploymentIdentity = if (stored == current) record("", prepared) else null
        if (stored != current) {
            com.intellij.openapi.diagnostic.Logger.getInstance(WinRTPreviewLaunchCache::class.java)
                .info("Preview build cache changed: " + current.filter { it !in stored }.map { it.substringBefore('=') })
        }
        if (stored == current) com.intellij.openapi.diagnostic.Logger.getInstance(WinRTPreviewLaunchCache::class.java)
            .info("Preview build cache matched: ${launch.taskName}")
        stored == current
    }.onFailure { error ->
        com.intellij.openapi.diagnostic.Logger.getInstance(WinRTPreviewLaunchCache::class.java)
            .info("Preview build cache unavailable for ${launch.taskName}: ${error.javaClass.simpleName}: ${error.message}")
    }.getOrDefault(false)

    fun remember(inputs: String) {
        if (!supported()) return
        Files.createDirectories(receipt.parent)
        Files.writeString(receipt, record(inputs))
    }

    /** Do not bless inputs edited while Gradle compiled an earlier version. */
    fun rememberIfUnchanged(expectedInputs: String): Boolean {
        if (expectedInputs != inputs()) return false
        remember(expectedInputs)
        return true
    }

    /** Generation can legitimately change while building. User inputs must remain stable;
     * the resulting receipt still fingerprints generated contents for every later launch. */
    fun rememberAfterBuild(expectedBuildInputs: String): Boolean {
        if (!supported() || expectedBuildInputs != buildInputs()) return false
        val completedInputs = inputs()
        if (expectedBuildInputs != buildInputs()) return false
        remember(completedInputs)
        return true
    }

    fun invalidate() { Files.deleteIfExists(receipt) }

    private fun environment(): Map<String, String> = linkedMapOf(
        "format" to if (sdk) "sdk-desktop-preview-v2" else "native-desktop-preview-v4",
        "launch" to launch.toString(),
        "model" to modules.toString(),
        "inputs" to "",
        "outputs" to outputs(),
        "dependencies" to if (sdk) "" else fingerprint(io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.read(
            deployment.parent.resolve(io.github.composefluent.winrt.ide.model.WinRTPreviewBuildInputs.FILE_NAME)), content = true, excludeBuild = false),
        "plugin" to if (!sdk) "native-protocol-${io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.VERSION}:$bundledToolchainIdentity" else javaClass.protectionDomain.codeSource?.location?.let { url ->
            runCatching { Files.getLastModifiedTime(Path.of(url.toURI())).toString() }.getOrDefault(url.toString())
        }.orEmpty(),
        // Explicit build-variable allowlist: toolchain locations and compiler/linker
        // flags affect native output. Per-launch transport variables are excluded;
        // application environment is inherited afresh by the launched process.
        "toolchain" to listOf("JAVA_HOME", "GRADLE_USER_HOME", "KONAN_DATA_DIR", "WindowsSdkDir", "WindowsSDKVersion", "VCToolsInstallDir", "VCToolsVersion", "INCLUDE", "LIB", "LIBPATH", "CL", "_CL_", "LINK", "_LINK_")
            .associateWith { System.getenv(it).orEmpty() }.toString(),
    )

    private fun record(inputs: String, environment: Map<String, String> = environment()): String =
        environment.entries.joinToString("\n") { (name, value) -> "$name=${digest((if (name == "inputs") inputs else value).toByteArray())}" }

    private fun fingerprint(roots: List<Path>, content: Boolean, excludeBuild: Boolean, configurationOnly: Boolean = false, ignored: List<Path> = emptyList()): String {
        val started = System.nanoTime()
        val files = sortedSetOf<Path>()
        val outputAttributes = mutableMapOf<Path, BasicFileAttributes>()
        val missing = sortedSetOf<String>()
        val excluded = setOf("build", ".gradle", ".git", ".idea", ".kotlin", ".konan", "node_modules")
        // Project/module/source roots overlap heavily. Scan each reachable subtree
        // once, but retain explicit roots below an excluded build directory.
        // Read each discovered file while the remaining directories are scanned.
        // An unbounded path queue avoids a producer deadlock if a reader fails;
        // the existing 30,000-file limit bounds its size. Digests are still reduced
        // in sorted path order, preserving receipts and full-content validation.
        val pending = java.util.concurrent.LinkedBlockingQueue<Path>()
        val end = Path.of("")
        val results = java.util.concurrent.ConcurrentHashMap<Path, Pair<ByteArray, Long>>()
        val workers = if (!content) emptyList() else List(4) {
            com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService().submit {
                val buffer = ByteArray(65536)
                val fileHash = MessageDigest.getInstance("SHA-256")
                while (true) {
                    val file = pending.take()
                    if (file === end) break
                    var countBytes = 0L
                    Files.newInputStream(file).use { stream ->
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            fileHash.update(buffer, 0, count); countBytes += count
                        }
                    }
                    results[file] = fileHash.digest() to countBytes
                }
            }
        }
        try {
            val scanRoots = mutableListOf<Path>()
            roots.map { it.toAbsolutePath().normalize() }.distinct().sortedBy { it.nameCount }.forEach { root ->
                if (!Files.exists(root) || scanRoots.none { parent -> root.startsWith(parent) &&
                        (!excludeBuild || parent.relativize(root).none { it.toString() in excluded }) }) scanRoots.add(root)
            }
            // Explicit roots below excluded build directories contain generated
            // sources. Read those early while broader project trees are scanned.
            scanRoots.sortedByDescending { it.nameCount }
                .filterNot { root -> ignored.any { root.startsWith(it) } }.forEach { root ->
                if (!Files.exists(root)) { missing += root.toString(); return@forEach }
                Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                        if (ignored.any { dir.startsWith(it) } || excludeBuild && dir != root && dir.fileName.toString() in excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (ignored.any { file.startsWith(it) }) return FileVisitResult.CONTINUE
                        check(!attrs.isSymbolicLink) { "Preview input links require Gradle validation" }
                        // SDK host inputs exclude app XAML/Kotlin edits. Build logic,
                        // version catalogs, restore locks and model/toolchain identity
                        // still invalidate it; deployed runtime changes are checked below.
                        // Native validation includes every regular file. Classify SDK
                        // configuration only when that filter is actually requested;
                        // walking every path segment also invokes IDE filesystem routing.
                        if (attrs.isRegularFile && (!configurationOnly || run {
                            val name = file.fileName.toString()
                            name.endsWith(".gradle.kts") || name.endsWith(".gradle") ||
                                name.endsWith(".toml") || name.endsWith(".properties") || name.endsWith(".json") ||
                                file.any { it.toString() in setOf("buildSrc", "build-logic", "gradle") }
                        })) {
                            if (files.add(file) && content) pending.add(file)
                            // walkFileTree already obtained the deployment metadata.
                            // Avoid two additional filesystem queries per output.
                            if (!content) outputAttributes[file] = attrs
                        }
                        check(files.size <= 30_000) { "Preview inputs require Gradle validation" }
                        return FileVisitResult.CONTINUE
                    }
                })
            }
            repeat(workers.size) { pending.add(end) }
            val scanned = System.nanoTime()
            var readBytes = 0L
            val hash = MessageDigest.getInstance("SHA-256")
            fun add(text: String) { hash.update(text.toByteArray()); hash.update(0.toByte()) }
            missing.forEach { add("missing:$it") }
            add("content-fingerprint-v2")
            if (!content) files.forEach { file ->
                val attrs = outputAttributes.getValue(file)
                add(file.toString()); add(attrs.size().toString()); add(attrs.lastModifiedTime().toString())
            } else {
                workers.forEach { it.get() }
                files.forEach { file ->
                    val (bytes, size) = results.getValue(file)
                    add(file.toString()); hash.update(bytes); hash.update(0.toByte()); readBytes += size
                }
            }
            com.intellij.openapi.diagnostic.Logger.getInstance(WinRTPreviewLaunchCache::class.java).info(
                "Preview fingerprint: content=$content, excludeBuild=$excludeBuild, files=${files.size}, bytes=$readBytes, scan=${(scanned - started) / 1_000_000}ms, hash=${(System.nanoTime() - scanned) / 1_000_000}ms")
            return java.util.HexFormat.of().formatHex(hash.digest())
        } finally { workers.forEach { it.cancel(true) } }
    }

    companion object {
        // A new bundle may resolve newer artifacts than the old Gradle receipt.
        // Packaging records payload identity without Maven publication timestamps; UI-only changes need not
        // rebuild Native. Older development bundles retain the streaming fallback.
        private val bundledToolchainIdentity: String by lazy {
            val started = System.nanoTime()
            val type = WinRTPreviewLaunchCache::class.java
            val packaged = type.getResourceAsStream("/templates/toolchain-content-sha256.txt")?.bufferedReader()?.use {
                it.readText().trim().takeIf { value -> value.matches(Regex("[0-9a-f]{64}")) }
            }
            packaged ?: type.getResourceAsStream("/templates/toolchain.zip")?.use { stream ->
                val hash = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
                java.util.HexFormat.of().formatHex(hash.digest()).also {
                    com.intellij.openapi.diagnostic.Logger.getInstance(type).info("Preview toolchain identity: ${(System.nanoTime() - started) / 1_000_000}ms")
                }
            } ?: type.protectionDomain.codeSource?.location?.toString().orEmpty()
        }
    }

    private fun digest(bytes: ByteArray) = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
