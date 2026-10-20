package io.github.composefluent.windows.toolkit.gradle

import java.io.File

/** Shared ownership rule for compilation and prepared-source migration/cleanup. */
internal fun isGeneratedWinRTProjectionSource(file: File): Boolean {
    if (!file.isFile || !file.name.endsWith(".kt", ignoreCase = true)) {
        return false
    }
    return runCatching {
        file.useLines { lines ->
            val header = lines.take(32).toList()
            io.github.composefluent.winrt.projections.generator.isProjectionImplementationSourceHeader(header)
        }
    }.getOrDefault(false)
}
