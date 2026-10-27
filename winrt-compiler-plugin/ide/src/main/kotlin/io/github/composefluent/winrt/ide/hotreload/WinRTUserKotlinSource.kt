package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Path

/** IDE virtual files expand Windows 8.3 names, while Gradle and java.io.tmpdir may
 * retain them. Canonicalize existing parents too so delete/rename events still
 * match their source roots after the leaf disappears. */
internal fun winRTSourcePath(path: Path): Path = runCatching { path.toFile().canonicalFile.toPath() }
    .getOrElse { path.toAbsolutePath().normalize() }

/** Importers may include generated directories in kotlinRoots. A nested source
 * root must never override the owning project's build/output exclusion. */
internal fun isWinRTUserKotlinSource(file: Path, projectDirectories: List<Path>, kotlinRoots: List<Path>): Boolean {
    val ignored = setOf("build", ".gradle", ".git", ".idea", ".kotlin", ".konan")
    val path = winRTSourcePath(file)
    val owner = projectDirectories.map { winRTSourcePath(it) }
        .filter(path::startsWith).maxByOrNull { it.nameCount }
    if (owner != null) return owner.relativize(path).none { it.toString().lowercase() in ignored }
    return kotlinRoots.any { source ->
        val root = winRTSourcePath(source)
        path.startsWith(root) && root.relativize(path).none { it.toString().lowercase() in ignored }
    }
}
