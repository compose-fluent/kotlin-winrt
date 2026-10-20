package io.github.composefluent.windows.toolkit.gradle

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.FileTime
import org.gradle.api.Project
import org.gradle.api.logging.Logging

/** Gradle storage policy only; projection semantics remain owned by cswinrt/main.cpp's Kotlin peers. */
internal fun sharedWinRTCacheDirectory(project: Project, name: String): Path =
    project.gradle.gradleUserHomeDir.toPath().resolve("caches/kotlin-winrt/$name")

// File locks coordinate processes; stripes also serialize overlapping locks in the same JVM.
private val cacheLockStripes = Array(64) { Any() }

internal fun <T> withWinRTCacheLock(lockFile: Path, action: () -> T): T {
    val path = lockFile.toAbsolutePath().normalize()
    val stripe = (path.toString().lowercase().hashCode() and Int.MAX_VALUE) % cacheLockStripes.size
    return synchronized(cacheLockStripes[stripe]) {
        Files.createDirectories(path.parent)
        FileChannel.open(path, CREATE, WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }
}

internal fun <T> withPreparedSourceStoreLock(store: Path, action: () -> T): T =
    withWinRTCacheLock(store.resolve(".store.lock"), action)

/** Call under the store lock, after generation AND materialization have finished. */
internal fun retainRecentPreparedSources(store: Path, current: Path, retainedEntries: Int = 3) {
    retainRecentWinRTCacheEntries(store, current, retainedEntries)
}

/** Both prepared sources and successful Native owner compilations use committed manifests. */
internal fun retainRecentWinRTCacheEntries(store: Path, current: Path, retainedEntries: Int = 3) {
    require(retainedEntries > 0)
    require(current.parent == store && Files.isDirectory(current, NOFOLLOW_LINKS))
    Files.setLastModifiedTime(current, FileTime.fromMillis(System.currentTimeMillis()))
    val entries = Files.list(store).use { paths ->
        paths.filter { path ->
            path != current && path.fileName.toString().matches(Regex("[0-9a-f]{64}")) &&
                Files.isDirectory(path, NOFOLLOW_LINKS) && Files.isRegularFile(path.resolve("manifest.tsv"), NOFOLLOW_LINKS)
        }.sorted(compareByDescending<Path> { Files.getLastModifiedTime(it).toMillis() }.thenBy(Path::toString))
            .toList()
    }
    entries.drop(retainedEntries - 1).forEach { entry ->
        // Only immediate, recognized cache children are eligible. Never recurse through a supplied path.
        check(entry.parent == store)
        runCatching { GradleFileOperations.deleteDirectory(entry) }.onFailure { error ->
            Logging.getLogger("WinRTBuildCache").info("Could not retire projection cache $entry", error)
        }
    }
}

/** Eviction is a cache miss, not a missing task input or a partial source copy. */
internal fun materializeCachedPreparedSources(sourceRoot: Path, generatedRoot: Path): Boolean =
    withPreparedSourceStoreLock(sourceRoot.parent.parent) {
        if (!isPreparedStaticSourceValid(sourceRoot)) {
            false
        } else {
            materializePreparedStaticSources(sourceRoot, generatedRoot)
            Files.setLastModifiedTime(sourceRoot.parent, FileTime.fromMillis(System.currentTimeMillis()))
            true
        }
    }
