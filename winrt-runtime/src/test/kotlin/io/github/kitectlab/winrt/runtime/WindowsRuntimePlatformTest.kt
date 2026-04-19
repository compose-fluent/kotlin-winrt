package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WindowsRuntimePlatformTest {
    @Test
    fun win32_last_error_is_translated_to_hresult() {
        assertEquals(HResult(0x80070002.toInt()), WinRtExceptionTranslator.hResultFromWin32(2))
        assertEquals(HResult(0x8007007E.toInt()), WinRtExceptionTranslator.hResultFromWin32(126))
    }

    @Test
    fun hresult_translation_returns_semantic_runtime_exception_types() {
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_INVALIDARG) is WinRtIllegalArgumentException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_BOUNDS) is WinRtIndexOutOfBoundsException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_ACCESSDENIED) is WinRtAccessDeniedException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.ERROR_TIMEOUT) is WinRtTimeoutException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.REGDB_E_CLASSNOTREG) is WinRtIllegalStateException)
    }

    @Test
    fun try_load_library_and_try_get_proc_address_cover_platform_loader_flow_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        val kernel32 = WindowsRuntimePlatform.tryLoadLibraryExW("kernel32.dll", 0)
        try {
            assertTrue(kernel32 != java.lang.foreign.MemorySegment.NULL)
            val getLastError = WindowsRuntimePlatform.tryGetProcAddress(kernel32, "GetLastError")
            assertTrue(getLastError != java.lang.foreign.MemorySegment.NULL)
            assertEquals(java.lang.foreign.MemorySegment.NULL, WindowsRuntimePlatform.tryGetProcAddress(kernel32, "DefinitelyMissingExport"))
        } finally {
            WindowsRuntimePlatform.freeLibrary(kernel32)
        }
    }
}