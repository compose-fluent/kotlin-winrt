package io.github.composefluent.windows.toolkit.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** CsWinRT's interop targets track each output's compiler inputs. Recipe owners only become
 * known after business compilation, so reuse their independently keyed outputs here rather
 * than creating Gradle tasks during execution. The final optimized link is unchanged. */
abstract class NativeProjectionCompilationService : BuildService<NativeProjectionCompilationService.Parameters>, AutoCloseable {
    interface Parameters : BuildServiceParameters {
        /** Storage location only; the task passes the current execution's reuse policy. */
        val cacheDirectory: DirectoryProperty
        val projectIdentity: Property<String>
    }

    private data class Result(val file: File, val digest: String)
    private val results = ConcurrentHashMap<String, CompletableFuture<Result>>()

    internal fun copyOrCompile(
        key: String,
        output: File,
        owner: String? = null,
        allowPersistentReuse: Boolean = false,
        compile: () -> Unit,
    ): Boolean {
        val pending = CompletableFuture<Result>()
        val existing = results.putIfAbsent(key, pending)
        if (existing != null) {
            val result = existing.get()
            check(result.file.isFile && nativeCompilationFileDigest(result.file) == result.digest) {
                "Native implementation changed during compilation reuse: ${result.file}"
            }
            copy(result.file.toPath(), output.toPath())
            return true
        }
        try {
            val store = if (allowPersistentReuse) owner?.let { name -> parameters.cacheDirectory.orNull?.asFile?.toPath()
                ?.resolve(projectionContractDigest("${parameters.projectIdentity.get()}\u0000$name")) } else null
            if (store != null) require(key.matches(Regex("[0-9a-f]{64}")))
            val restored = store != null && cacheOperation { restore(store, key, output) }
            if (!restored) {
                compile()
                check(output.isFile) { "Native compilation did not produce $output" }
                if (store != null) cacheOperation { persist(store, key, output); true }
            }
            pending.complete(Result(output, nativeCompilationFileDigest(output)))
            return restored
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            results.remove(key, pending)
            throw error
        }
    }

    private fun restore(store: Path, key: String, output: File): Boolean =
        withWinRTCacheLock(store.resolve(".store.lock")) {
            val entry = store.resolve(key)
            val manifest = entry.resolve("manifest.tsv")
            val artifact = entry.resolve("artifact.klib")
            if (!Files.isDirectory(entry, NOFOLLOW_LINKS) ||
                !Files.isRegularFile(manifest, NOFOLLOW_LINKS) || !Files.isRegularFile(artifact, NOFOLLOW_LINKS)) {
                false
            } else {
                val state = Files.readAllLines(manifest)
                if (state.size != 3 || state[0] != "native-projection-compilation-v1" || state[1] != key ||
                    state[2] != nativeCompilationFileDigest(artifact.toFile())) {
                    false
                } else {
                    copy(artifact, output.toPath())
                    retainRecentWinRTCacheEntries(store, entry)
                    true
                }
            }
        }

    private fun persist(store: Path, key: String, output: File) =
        withWinRTCacheLock(store.resolve(".store.lock")) {
            val entry = store.resolve(key)
            Files.createDirectories(entry)
            if (!Files.isDirectory(entry, NOFOLLOW_LINKS)) return@withWinRTCacheLock
            val digest = nativeCompilationFileDigest(output)
            replaceAtomically(entry.resolve("artifact.klib")) { Files.copy(output.toPath(), it) }
            replaceAtomically(entry.resolve("manifest.tsv")) {
                Files.writeString(it, "native-projection-compilation-v1\n$key\n$digest\n")
            }
            retainRecentWinRTCacheEntries(store, entry)
        }

    private fun replaceAtomically(target: Path, write: (Path) -> Unit) {
        val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            write(temporary)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun copy(source: Path, target: Path) {
        if (source.toAbsolutePath() != target.toAbsolutePath()) {
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun cacheOperation(action: () -> Boolean): Boolean = try { action() }
    catch (error: IOException) {
        Logging.getLogger(javaClass).info("Native implementation cache unavailable; compiling from recipe", error)
        false
    }

    override fun close() { results.clear() }
}

internal fun nativeCompilationFileDigest(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun nativeCompilationInputDigest(file: File): String = when {
    file.isDirectory -> projectionContractDigest(file.walkTopDown().filter(File::isFile)
        .sortedBy { it.relativeTo(file).invariantSeparatorsPath }
        .joinToString("\n") { it.relativeTo(file).invariantSeparatorsPath + ":" + nativeCompilationFileDigest(it) })
    file.exists() -> nativeCompilationFileDigest(file)
    else -> "<missing>"
}

/** Compiler/plugin/distribution inputs are immutable during a task; hash them once per task. */
internal fun nativeCompilationToolDigests(files: List<File>): Map<File, String> =
    files.associateWith(::nativeCompilationInputDigest)

internal fun nativeCompilationKey(
    sourceRoot: File,
    sources: List<File>,
    supportFiles: List<File>,
    output: File,
    dependencies: List<File>,
    toolFiles: List<File>,
    toolIdentity: String,
    jvmArgs: List<String>,
    arguments: List<String>,
    toolDigests: Map<File, String> = nativeCompilationToolDigests(toolFiles),
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun add(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte()); digest.update(bytes); digest.update(0.toByte())
    }
    add("native-projection-compilation-v2")
    add(toolIdentity)
    jvmArgs.forEach(::add)
    // Java ignores missing optional classpath entries. Track their absence as an input too.
    toolFiles.forEach { add(it.absolutePath); add(toolDigests.getValue(it)) }
    (sources + supportFiles).distinct().sortedBy { it.relativeTo(sourceRoot).invariantSeparatorsPath }.forEach {
        add(it.relativeTo(sourceRoot).invariantSeparatorsPath); add(nativeCompilationInputDigest(it))
    }
    val replacements = dependencies.mapIndexed { index, dependency ->
        dependency.absolutePath to "<dependency:$index:${nativeCompilationInputDigest(dependency)}>"
    } + listOf(sourceRoot.absolutePath to "<sources>", output.absolutePath to "<output>")
    val orderedReplacements = replacements.sortedByDescending { it.first.length }
    arguments.forEach { argument ->
        add(orderedReplacements.fold(argument) { value, (path, replacement) -> value.replace(path, replacement) })
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
