package io.github.composefluent.windows.toolkit.gradle

import java.nio.file.Path

/** Shared by staging and IDE navigation; one owner for package path constraints. */
internal fun String.toSafeRelativePath(label: String): Path {
    val normalized = trim().replace('\\', '/')
    if (normalized.isBlank()) return Path.of("")
    val path = Path.of(normalized).normalize()
    require(!normalized.startsWith("/") && !WINDOWS_DRIVE_PATH.matches(normalized) && !path.isAbsolute && !path.startsWith("..")) {
        "$label must be a relative path inside the package root: $this"
    }
    return path
}

private val WINDOWS_DRIVE_PATH = Regex("""^[A-Za-z]:($|/.*)""")
