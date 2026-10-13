@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.gallery

import kotlinx.cinterop.memScoped
import kotlinx.cinterop.wcstr
import platform.posix._wfopen
import platform.posix.fclose
import platform.posix.fputs

internal actual fun appendGalleryLog(path: String, message: String): Unit = memScoped {
    val file = _wfopen(path.wcstr.ptr, "a".wcstr.ptr) ?: return@memScoped
    try { fputs("$message\n", file) } finally { fclose(file) }
}
