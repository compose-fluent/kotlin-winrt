@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import platform.windows.CREATE_NEW
import platform.windows.CloseHandle
import platform.windows.CreateDirectoryW
import platform.windows.CreateFileW
import platform.windows.DeleteFileW
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_READ
import platform.windows.GENERIC_WRITE
import platform.windows.GetLastError
import platform.windows.GetTempPathW
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.RemoveDirectoryW
import platform.windows.WriteFile
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinRTFileSystemNativeTest {
    @Test
    fun windows_metadata_classifies_unicode_files_directories_and_missing_paths() {
        // ActivationFactory.cs anchors application assets at the host directory. This
        // exercises the owning Windows metadata boundary, independently of CRT stat's
        // ANSI path conversion and parent-directory enumeration.
        val temporaryDirectory = PlatformAbi.confinedScope().use { scope ->
            val buffer = PlatformAbi.allocateBytes(scope, 32768L * 2L)
            val length = GetTempPathW(32768u, buffer.value.toCPointer()).toInt()
            assertTrue(length in 1 until 32768)
            Path(PlatformAbi.readUtf16(buffer, length))
        }
        val directory = Path(temporaryDirectory, "kotlin-winrt-${Random.nextLong()}-目录-🚀")
        assertTrue(CreateDirectoryW(directory.toString(), null) != 0, "CreateDirectoryW: ${GetLastError()}")
        val file = Path(directory, "配置-🚀.dat")
        try {
            val handle = CreateFileW(
                file.toString(), GENERIC_WRITE.toUInt(), FILE_SHARE_READ.toUInt(), null,
                CREATE_NEW.toUInt(), FILE_ATTRIBUTE_NORMAL.toUInt(), null,
            )
            assertTrue(handle != INVALID_HANDLE_VALUE, "CreateFileW: ${GetLastError()}")
            try {
                memScoped {
                    val bytes = allocArray<ByteVar>(3)
                    bytes[0] = 65
                    bytes[1] = 66
                    bytes[2] = 67
                    val written = alloc<UIntVar>()
                    assertTrue(WriteFile(handle, bytes, 3u, written.ptr, null) != 0)
                    assertEquals(3u, written.value)
                }
            } finally {
                CloseHandle(handle)
            }

            val directoryMetadata = assertNotNull(platformFileMetadataOrNull(directory))
            assertTrue(directoryMetadata.isDirectory)
            assertFalse(directoryMetadata.isRegularFile)
            assertTrue(directory.isDirectory())
            assertFalse(directory.isRegularFile())
            val fileMetadata = assertNotNull(platformFileMetadataOrNull(file))
            assertTrue(fileMetadata.isRegularFile)
            assertFalse(fileMetadata.isDirectory)
            assertEquals(3L, fileMetadata.size)
            assertTrue(file.isRegularFile())
            assertFalse(file.isDirectory())
            val missing = Path(directory, "missing")
            assertNull(platformFileMetadataOrNull(missing))
            assertFalse(missing.isRegularFile())
            assertFalse(missing.isDirectory())
        } finally {
            DeleteFileW(file.toString())
            RemoveDirectoryW(directory.toString())
        }
    }
}
