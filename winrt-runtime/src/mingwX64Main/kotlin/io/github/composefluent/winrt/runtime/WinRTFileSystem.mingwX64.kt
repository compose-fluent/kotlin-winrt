@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.io.IOException
import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import platform.windows.ERROR_FILE_NOT_FOUND
import platform.windows.ERROR_PATH_NOT_FOUND
import platform.windows.FILE_ATTRIBUTE_DIRECTORY
import platform.windows.GET_FILEEX_INFO_LEVELS
import platform.windows.GetFileAttributesExW
import platform.windows.GetLastError
import platform.windows.WIN32_FILE_ATTRIBUTE_DATA

internal actual fun platformFileMetadataOrNull(path: Path): FileMetadata? = memScoped {
    // CsWinRT's application-base-directory lookup belongs at the platform boundary
    // (ActivationFactory.cs / Platform.cs). MinGW's CRT stat can enumerate a directory's
    // parent, which an AppContainer cannot necessarily access even for its own package
    // root. Query the path itself through the Unicode Windows API, supported by both
    // desktop and UWP, instead of treating that CRT failure as a missing directory.
    val attributes = alloc<WIN32_FILE_ATTRIBUTE_DATA>()
    if (GetFileAttributesExW(path.toString(), GET_FILEEX_INFO_LEVELS.GetFileExInfoStandard, attributes.ptr) == 0) {
        val error = GetLastError()
        if (error == ERROR_FILE_NOT_FOUND.toUInt() || error == ERROR_PATH_NOT_FOUND.toUInt()) {
            return@memScoped null
        }
        throw IOException("GetFileAttributesExW failed for $path with Windows error $error.")
    }
    val isDirectory = (attributes.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u
    val size = (attributes.nFileSizeHigh.toLong() shl 32) or attributes.nFileSizeLow.toLong()
    FileMetadata(!isDirectory, isDirectory, if (isDirectory) -1L else size)
}
