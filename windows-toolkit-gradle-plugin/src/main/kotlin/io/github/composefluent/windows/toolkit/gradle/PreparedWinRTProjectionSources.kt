package io.github.composefluent.windows.toolkit.gradle

import com.squareup.kotlinpoet.ClassName
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataModelCodec
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.projections.generator.KotlinProjectionGenerator
import io.github.composefluent.winrt.runtime.Guid
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.jar.JarFile

private const val PREPARED_STATIC_HEADER = "kotlin-winrt-prepared-static-sources-v4"

/** CsWinRTGenerateProjection owns generation; configuration only wires its inputs and consumers. */
internal fun prepareProjectionSources(
    store: Path,
    key: String,
    output: Path,
    model: WinRTMetadataModel,
    generate: (Path) -> Unit,
): Path = withPreparedSourceStoreLock(store) {
    require(key.matches(Regex("[0-9a-f]{64}")))
    val entry = store.resolve(key)
    val sources = entry.resolve("sources")
    if (!isPreparedStaticSourceValid(sources)) {
        val temporary = Files.createTempDirectory(store, ".$key-")
        try {
            val destination = Files.createDirectories(temporary.resolve("sources"))
            generate(destination)
            writePreparedStaticManifest(temporary.resolve("manifest.tsv"), emptyList(), model)
            check(isPreparedStaticSourceValid(destination))
            if (Files.exists(entry)) GradleFileOperations.deleteDirectory(entry)
            try {
                Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, entry)
            }
        } finally {
            if (Files.exists(temporary)) GradleFileOperations.deleteDirectory(temporary)
        }
    }
    materializePreparedStaticSources(sources, output)
    retainRecentPreparedSources(store, entry)
    sources
}

internal fun preparedProjectionKey(
    model: WinRTMetadataModel,
    sourceModel: WinRTMetadataModel,
    context: WinRTMetadataProjectionContext,
    suppressedTypes: Set<String>,
    suppressedAdditions: Set<String>,
    owner: String,
    emitJvmHost: Boolean,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    fun field(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
        digest.update(bytes)
    }
    field("task-projection-sources-v1")
    field(preparedStaticImplementationFingerprint(preparedStaticImplementationRoots()))
    field(WinRTMetadataModelCodec.fingerprint(model))
    // completeProjectionModel may load supplemental declarations from these inputs.
    field(WinRTMetadataModelCodec.fingerprint(sourceModel))
    field(context.copy(
        sources = emptyList(), outputFolder = null,
        include = context.include.toSortedSet(), exclude = context.exclude.toSortedSet(),
        excludedTypes = context.excludedTypes.toSortedSet(), additionExclude = context.additionExclude.toSortedSet(),
        inaccessibleDependencyTypes = context.inaccessibleDependencyTypes.toSortedSet(),
    ).toString())
    field(context.sources.isNotEmpty().toString())
    for (names in listOf(suppressedTypes, suppressedAdditions)) {
        field(names.size.toString())
        names.sorted().forEach(::field)
    }
    field(owner)
    field(emitJvmHost.toString())
    return java.util.HexFormat.of().formatHex(digest.digest())
}

private fun preparedStaticImplementationRoots(): List<Path> = listOf(
    KotlinProjectionGenerator::class.java, WinRTMetadataModel::class.java,
    Guid::class.java, io.github.composefluent.winrt.runtime.ComObjectReference::class.java, ClassName::class.java,
    io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteCatalog::class.java,
).map { Path.of(it.protectionDomain.codeSource.location.toURI()) }.distinct()

// The plugin bundles several tools in one jar. Only projection implementation and its
// dependencies affect this cache; compiler diagnostics and Gradle configuration do not.
private val projectionImplementationPrefixes = listOf(
    "io/github/composefluent/winrt/projections/generator/",
    "io/github/composefluent/winrt/metadata/",
    "io/github/composefluent/winrt/runtime/",
    "io/github/composefluent/winrt/compiler/callsites/",
    "com/squareup/kotlinpoet/",
)

private fun projectionImplementationEntry(name: String): Boolean =
    !name.startsWith("io/github/composefluent/winrt/compiler/callsites/lowering/") &&
        projectionImplementationPrefixes.any(name::startsWith)

internal fun preparedStaticImplementationFingerprint(roots: Iterable<Path>): String {
    val entries = sortedSetOf<String>()
    fun record(name: String, bytes: ByteArray) {
        if (projectionImplementationEntry(name)) {
            entries += name + ":" + java.util.HexFormat.of().formatHex(bytes)
        }
    }
    roots.map { it.toAbsolutePath().normalize() }.distinct().forEach { root ->
        if (Files.isDirectory(root)) {
            Files.walk(root).use { paths ->
                paths.filter(Files::isRegularFile).forEach { path ->
                    val name = root.relativize(path).toString().replace('\\', '/')
                    if (projectionImplementationEntry(name)) record(name, PreparedProjectionFileFingerprints.content(path))
                }
            }
        } else {
            JarFile(root.toFile()).use { jar ->
                jar.entries().asSequence().filter { !it.isDirectory && projectionImplementationEntry(it.name) }
                    .forEach { entry ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        java.security.DigestInputStream(jar.getInputStream(entry), digest).use {
                            it.copyTo(java.io.OutputStream.nullOutputStream())
                        }
                        record(entry.name, digest.digest())
                    }
            }
        }
    }
    check(entries.isNotEmpty()) { "Projection implementation classes are unavailable" }
    val digest = MessageDigest.getInstance("SHA-256")
    entries.forEach { digest.update(it.toByteArray(Charsets.UTF_8)); digest.update(0) }
    return java.util.HexFormat.of().formatHex(digest.digest())
}

private fun updateFileContents(digest: MessageDigest, path: Path) {
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
}

internal fun writePreparedStaticManifest(path: Path, files: List<Path>, model: WinRTMetadataModel) {
    val names = model.namespaces.flatMap { namespace -> namespace.types }.map { type -> type.qualifiedName }.sorted()
    val encodedFiles = files.sortedBy(Path::toString).joinToString("\n") { file ->
        val digest = MessageDigest.getInstance("SHA-256")
        updateFileContents(digest, file)
        val digestHex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        "file\t${Base64.getUrlEncoder().withoutPadding().encodeToString(file.toString().toByteArray())}\t$digestHex"
    }
    val content = buildString {
        append(PREPARED_STATIC_HEADER).append('\n')
        append("types\t").append(names.joinToString(",")).append('\n')
        if (encodedFiles.isNotEmpty()) append(encodedFiles).append('\n')
        val root = path.parent.resolve("sources")
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile).sorted().forEach { file ->
                val relative = root.relativize(file).toString().replace('\\', '/')
                val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(relative.toByteArray(Charsets.UTF_8))
                append("output\t").append(encoded).append('\t').append(preparedOutputHash(file)).append('\n')
            }
        }
    }
    GradleFileOperations.writeStringIfChanged(path, content)
}

private fun preparedOutputHash(path: Path): String {
    return java.util.HexFormat.of().formatHex(PreparedProjectionFileFingerprints.content(path))
}

/** Validate both the output inventory and bytes before reusing a prepared projection. */
internal fun isPreparedStaticSourceValid(sourceRoot: Path): Boolean = runCatching {
    val manifest = sourceRoot.parent.resolve("manifest.tsv")
    if (!Files.isDirectory(sourceRoot) || !Files.isRegularFile(manifest)) return false
    val lines = Files.readAllLines(manifest)
    if (lines.firstOrNull() != PREPARED_STATIC_HEADER) return false
    val root = sourceRoot.toAbsolutePath().normalize()
    val expected = mutableSetOf<Path>()
    for (line in lines.drop(1).filter { it.startsWith("output\t") }) {
        val fields = line.split('\t')
        if (fields.size != 3) return false
        val relative = Path.of(String(Base64.getUrlDecoder().decode(fields[1]), Charsets.UTF_8))
        val file = root.resolve(relative).normalize()
        if (relative.isAbsolute || !file.startsWith(root) || !expected.add(file)) return false
        if (!Files.isRegularFile(file) || preparedOutputHash(file) != fields[2]) return false
    }
    Files.walk(root).use { paths ->
        paths.filter(Files::isRegularFile).allMatch { it in expected }
    }
}.getOrDefault(false)

internal fun materializePreparedStaticSources(sourceRoot: Path, generatedRoot: Path) {
    if (!Files.isDirectory(sourceRoot)) {
        clearPreparedStaticSources(generatedRoot)
        return
    }
    val manifest = generatedRoot.resolve(".kotlin-winrt-prepared-static-files.tsv")
    val previousFiles = (if (Files.isRegularFile(manifest)) {
        Files.readAllLines(manifest).filter(String::isNotBlank).toSet()
    } else {
        emptySet()
    })
    val previousOwnedFiles = previousFiles + markedGeneratedProjectionFiles(generatedRoot, previousFiles)
    val currentFiles = mutableSetOf<String>()
    Files.walk(sourceRoot).use { stream ->
        stream.filter(Files::isRegularFile).forEach { source ->
            val relative = sourceRoot.relativize(source).toString().replace('\\', '/')
            currentFiles += relative
            val target = generatedRoot.resolve(relative)
            Files.createDirectories(target.parent)
            if (!Files.isRegularFile(target) || preparedOutputHash(source) != preparedOutputHash(target)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    previousOwnedFiles.asSequence()
        .filterNot(currentFiles::contains)
        .map(generatedRoot::resolve)
        .forEach(Files::deleteIfExists)
    GradleFileOperations.writeStringIfChanged(
        manifest,
        currentFiles.sorted().joinToString(separator = "\n", postfix = if (currentFiles.isEmpty()) "" else "\n"),
    )
}

/** Adopts files from task generation predating the prepared-source ownership manifest. */
private fun markedGeneratedProjectionFiles(root: Path, knownFiles: Set<String> = emptySet()): Set<String> {
    if (!Files.isDirectory(root)) return emptySet()
    return Files.walk(root).use { paths ->
        paths.filter { root.relativize(it).toString().replace('\\', '/') !in knownFiles }
            .filter { isGeneratedWinRTProjectionSource(it.toFile()) }
            .map { root.relativize(it).toString().replace('\\', '/') }
            .toList().toSet()
    }
}

internal fun clearPreparedStaticSources(generatedRoot: Path) {
    val manifest = generatedRoot.resolve(".kotlin-winrt-prepared-static-files.tsv")
    val previousFiles = (if (Files.isRegularFile(manifest)) Files.readAllLines(manifest) else emptyList()) +
        markedGeneratedProjectionFiles(generatedRoot)
    if (previousFiles.isEmpty()) return
    val normalizedRoot = generatedRoot.toAbsolutePath().normalize()
    previousFiles
        .asSequence()
        .distinct()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { relative ->
            require(!Path.of(relative).isAbsolute) {
                "Prepared static manifest contains an absolute path: $relative"
            }
            normalizedRoot.resolve(relative).normalize().also { target ->
                require(target.startsWith(normalizedRoot)) {
                    "Prepared static manifest escapes its generated root: $relative"
                }
            }
        }
        .forEach(Files::deleteIfExists)
    GradleFileOperations.writeStringIfChanged(manifest, "")
}
