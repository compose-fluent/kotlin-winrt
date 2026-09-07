package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

/** A resource file together with the path it will have at the AppX package root. */
internal data class AppxResourceInput(
    val source: Path,
    val relativePath: Path,
) {
    val relativePathString: String
        get() = relativePath.toString().replace('\\', '/')
}

/**
 * Collects layered AppX resource roots into the final package view.
 *
 * Roots must be ordered from least specific to most specific. Windows package paths are
 * case-insensitive, so a later root replaces an earlier file with the same normalized path.
 */
internal fun collectAppxResourceInputs(resourceRoots: Iterable<Path>): List<AppxResourceInput> {
    val selected = linkedMapOf<String, AppxResourceInput>()
    resourceRoots
        .map { it.toAbsolutePath().normalize() }
        .distinct()
        .forEach { root ->
            if (!root.isDirectory()) return@forEach
            Files.walk(root).use { stream ->
                stream.asSequence()
                    .filter { path -> path.isRegularFile() }
                    .map { source ->
                        AppxResourceInput(
                            source = source.toAbsolutePath().normalize(),
                            relativePath = source.relativeTo(root),
                        )
                    }
                    .sortedBy { it.relativePathString.lowercase() }
                    .forEach { input ->
                        selected[input.relativePathKey()] = input
                    }
            }
        }
    return selected.values.sortedBy { it.relativePathString.lowercase() }
}

internal fun AppxResourceInput.relativePathKey(): String =
    relativePathString.trimStart('/').lowercase()

/**
 * Normalizes an input path for Windows comparisons without changing the path spelling used in
 * package output. Explicit PRI/payload maps are persisted as absolute paths, so comparing their
 * normalized keys keeps the layout and PRI stages consistent when a caller changes casing.
 */
internal fun Path.toNormalizedInputPathKey(): String =
    toAbsolutePath()
        .normalize()
        .toString()
        .replace('\\', '/')
        .lowercase(Locale.ROOT)

internal fun findAppxManifest(resourceInputs: Iterable<AppxResourceInput>): Path? =
    resourceInputs
        .firstOrNull { input ->
            input.relativePath.parent == null && input.relativePath.name.equals("AppxManifest.xml", ignoreCase = true)
        }
        ?.source

internal fun appxResourceFiles(resourceRoots: Iterable<Path>): List<Path> =
    collectAppxResourceInputs(resourceRoots).map(AppxResourceInput::source)
