package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Reuse checks within the daemon, invalidating on changes to file identity, size, or timestamps. */
internal object PreparedProjectionFileFingerprints {
    private data class Stamp(val size: Long, val modified: FileTime, val created: FileTime, val fileKey: Any?)
    private data class Entry(val stamp: Stamp, val digest: ByteArray)
    private val content = ConcurrentHashMap<Path, Entry>()
    private val archives = ConcurrentHashMap<Path, Entry>()

    fun content(path: Path): ByteArray = fingerprint(content, path) {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest()
    }

    fun archive(path: Path, calculate: () -> ByteArray): ByteArray = fingerprint(archives, path, calculate)

    private fun fingerprint(cache: ConcurrentHashMap<Path, Entry>, path: Path, calculate: () -> ByteArray): ByteArray {
        val key = path.toAbsolutePath().normalize()
        fun stamp(): Stamp = Files.readAttributes(key, BasicFileAttributes::class.java).let {
            require(it.isRegularFile) { "Projection fingerprint input is not a file: $key" }
            Stamp(it.size(), it.lastModifiedTime(), it.creationTime(), it.fileKey())
        }
        val before = stamp()
        cache[key]?.takeIf { it.stamp == before }?.let { return it.digest }
        val digest = calculate()
        if (stamp() == before) {
            // Bound retained paths when a daemon services many independent consumer projects.
            if (cache.size >= 65_536) cache.clear()
            cache[key] = Entry(before, digest)
        }
        return digest
    }
}
