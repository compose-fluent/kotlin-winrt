package io.github.kitectlab.winrt.runtime

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_POINTER) is WinRtNullReferenceException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_BOUNDS) is WinRtIndexOutOfBoundsException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_NOTIMPL) is WinRtNotImplementedException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_NOINTERFACE) is WinRtInvalidCastException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_OUTOFMEMORY) is WinRtOutOfMemoryException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.E_ACCESSDENIED) is WinRtAccessDeniedException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.ERROR_TIMEOUT) is WinRtTimeoutException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(KnownHResults.REGDB_E_CLASSNOTREG) is WinRtIllegalStateException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(ExceptionHelpers.ERROR_FILE_NOT_FOUND) is WinRtFileNotFoundException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(ExceptionHelpers.ERROR_BAD_FORMAT) is WinRtBadImageFormatException)
        assertTrue(WinRtExceptionTranslator.exceptionFor(ExceptionHelpers.E_XAMLPARSEFAILED) is WinRtXamlParseException)
    }

    @Test
    fun throwable_to_hresult_translation_matches_runtime_owner() {
        assertEquals(KnownHResults.E_INVALIDARG, WinRtExceptionTranslator.hResultFromException(IllegalArgumentException("bad arg")))
        assertEquals(KnownHResults.E_BOUNDS, WinRtExceptionTranslator.hResultFromException(IndexOutOfBoundsException("bad index")))
        assertEquals(KnownHResults.E_NOTSUPPORTED, WinRtExceptionTranslator.hResultFromException(UnsupportedOperationException("unsupported")))
        assertEquals(KnownHResults.ERROR_CANCELLED, WinRtExceptionTranslator.hResultFromException(CancellationException("cancelled")))
        assertEquals(
            KnownHResults.ERROR_TIMEOUT,
            WinRtExceptionTranslator.hResultFromException(WinRtTimeoutException("timeout", KnownHResults.ERROR_TIMEOUT)),
        )
        assertEquals(KnownHResults.E_ACCESSDENIED, WinRtExceptionTranslator.hResultFromException(WinRtAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)))
        assertEquals(ExceptionHelpers.E_FAIL, WinRtExceptionTranslator.hResultFromException(IllegalStateException("missing")))
    }

    @Test
    fun iid_catalog_matches_cswinrt_reference_values() {
        assertEquals(Guid("00000037-0000-0000-C000-000000000046"), IID.IWeakReference)
        assertEquals(Guid("00000038-0000-0000-C000-000000000046"), IID.IWeakReferenceSource)
        assertEquals(Guid("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90"), IID.IAgileObject)
        assertEquals(Guid("C03F6A43-65A4-9818-987E-E0B810D2A6F2"), IID.IAgileReference)
        assertEquals(Guid("00000003-0000-0000-C000-000000000046"), IID.IMarshal)
        assertEquals(Guid("000001DA-0000-0000-C000-000000000046"), IID.IContextCallback)
        assertEquals(Guid("82BA7092-4C88-427D-A7BC-16DD93FEB67E"), IID.IRestrictedErrorInfo)
        assertEquals(Guid("00000146-0000-0000-C000-000000000046"), IID.IGlobalInterfaceTable)
        assertEquals(Guid("44A9796F-723E-4FDF-A218-033E75B0C084"), IID.UriRuntimeClassFactory)
        assertEquals(Guid("FD416DFB-2A07-52EB-AAE3-DFCE14116C05"), IID.NullableString)
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

    @Test
    fun format_message_uses_windows_system_message_table() {
        assumeTrue(PlatformRuntime.isWindows)

        val message = ExceptionHelpers.formatMessage(KnownHResults.ERROR_FILE_NOT_FOUND)
        assertTrue(!message.isNullOrBlank())
    }

    @Test
    fun managed_error_info_can_round_trip_description_through_ierrorinfo_slots() {
        assumeTrue(PlatformRuntime.isWindows)

        ManagedErrorInfoComObject(IllegalStateException("boom")).detachReference().use { errorInfo ->
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val descriptionOut = arena.allocate(java.lang.foreign.ValueLayout.ADDRESS)
                ExceptionHelpers.throwExceptionForHR(
                    errorInfo.invokeAbi(
                        slot = 5,
                        descriptor = java.lang.foreign.FunctionDescriptor.of(
                            java.lang.foreign.ValueLayout.JAVA_INT,
                            java.lang.foreign.ValueLayout.ADDRESS,
                            java.lang.foreign.ValueLayout.ADDRESS,
                        ),
                        descriptionOut,
                    ),
                    "IErrorInfo.GetDescription",
                )
                assertEquals(
                    "boom",
                    WindowsRuntimePlatform.readAndFreeBstr(
                        descriptionOut.get(java.lang.foreign.ValueLayout.ADDRESS, 0),
                    ),
                )
            }
        }
    }

    @Test
    fun set_error_info_accepts_managed_error_info_bridge() {
        assumeTrue(PlatformRuntime.isWindows)

        ExceptionHelpers.setErrorInfo(IllegalStateException("managed failure"))
        assertNotNull(ExceptionHelpers.formatMessage(ExceptionHelpers.E_FAIL))
    }
}
