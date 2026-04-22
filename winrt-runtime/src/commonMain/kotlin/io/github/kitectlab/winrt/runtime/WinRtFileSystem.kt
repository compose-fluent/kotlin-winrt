package io.github.kitectlab.winrt.runtime

import kotlinx.io.buffered
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString

/** Returns true if this path resolves to a regular file. */
internal fun Path.isRegularFile(): Boolean =
    SystemFileSystem.metadataOrNull(this)?.isRegularFile == true

/** Returns true if this path resolves to a directory. */
internal fun Path.isDirectory(): Boolean =
    SystemFileSystem.metadataOrNull(this)?.isDirectory == true

/** Returns the file name component of this path (the last segment). */
internal val Path.fileName: String get() = name

/** Returns the canonical (absolute, normalized) string for deduplication. */
internal fun Path.canonicalString(): String =
    runCatching { SystemFileSystem.canonicalize(this).toString() }.getOrElse { toString() }

/** Resolves a string path to its canonical absolute form when possible. */
internal fun absolutePath(path: String): String = Path(path).canonicalString()

/** Reads the entire file as a UTF-8 string. */
internal fun Path.readText(): String =
    SystemFileSystem.source(this).buffered().use { it.readString() }

/** Reads the entire file as a byte array. */
internal fun Path.readBytes(): ByteArray =
    SystemFileSystem.source(this).buffered().use { it.readByteArray() }

/**
 * Recursively collects all regular files under this directory (breadth-first).
 * If this path is a regular file, returns a single-element list.
 * Returns an empty list for anything else.
 */
internal fun Path.walkFiles(): List<Path> {
    val meta: FileMetadata = SystemFileSystem.metadataOrNull(this) ?: return emptyList()
    if (meta.isRegularFile) return listOf(this)
    if (!meta.isDirectory) return emptyList()
    val result = mutableListOf<Path>()
    val queue = ArrayDeque<Path>()
    queue.add(this)
    while (queue.isNotEmpty()) {
        val dir = queue.removeFirst()
        for (child in SystemFileSystem.list(dir)) {
            val childMeta = SystemFileSystem.metadataOrNull(child) ?: continue
            when {
                childMeta.isRegularFile -> result.add(child)
                childMeta.isDirectory -> queue.add(child)
            }
        }
    }
    return result
}
