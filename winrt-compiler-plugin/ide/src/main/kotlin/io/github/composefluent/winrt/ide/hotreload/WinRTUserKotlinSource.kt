package io.github.composefluent.winrt.ide.hotreload

import java.nio.file.Path

/** Importers may include generated directories in kotlinRoots. A nested source
 * root must never override the owning project's build/output exclusion. */
internal fun isWinRTUserKotlinSource(file: Path, projectDirectories: List<Path>, kotlinRoots: List<Path>): Boolean {
    val ignored = setOf("build", ".gradle", ".git", ".idea", ".kotlin", ".konan")
    val path = file.toAbsolutePath().normalize()
    val owner = projectDirectories.map { it.toAbsolutePath().normalize() }
        .filter(path::startsWith).maxByOrNull { it.nameCount }
    if (owner != null) return owner.relativize(path).none { it.toString().lowercase() in ignored }
    return kotlinRoots.any { source ->
        val root = source.toAbsolutePath().normalize()
        path.startsWith(root) && root.relativize(path).none { it.toString().lowercase() in ignored }
    }
}
