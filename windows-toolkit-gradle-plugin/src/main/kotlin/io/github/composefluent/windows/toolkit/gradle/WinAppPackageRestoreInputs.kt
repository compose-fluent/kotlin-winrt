package io.github.composefluent.windows.toolkit.gradle

import java.util.Locale

/** NuGet's extraction bookkeeping does not contribute to WinMDs or copied package assets. */
internal fun isWinAppPackageRestoreInput(relativePath: String): Boolean {
    val path = relativePath.replace('\\', '/').lowercase(Locale.ROOT)
    if (path == "_rels" || path.startsWith("_rels/")) return false
    if ('/' in path) return true
    return path != ".nupkg.metadata" && path != ".signature.p7s" && path != "[content_types].xml" &&
        !path.endsWith(".nupkg") && !path.endsWith(".nupkg.sha512")
}
