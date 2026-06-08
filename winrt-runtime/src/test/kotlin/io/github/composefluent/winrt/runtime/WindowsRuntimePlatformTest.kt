package io.github.composefluent.winrt.runtime

import io.github.composefluent.winrt.runtime.exception.ManagedErrorInfoComObject
import io.github.composefluent.winrt.runtime.exception.ManagedRestrictedErrorInfoComObject
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
    fun iid_catalog_matches_reference_reference_values() {
        assertEquals(Guid("00000037-0000-0000-C000-000000000046"), IID.IWeakReference)
        assertEquals(Guid("00000038-0000-0000-C000-000000000046"), IID.IWeakReferenceSource)
        assertEquals(Guid("64BD43F8-BFEE-4EC4-B7EB-2935158DAE21"), IID.IReferenceTrackerTarget)
        assertEquals(Guid("4E897CAA-59D5-4613-8F8C-F7EBD1F399B0"), IID.IReferenceTrackerExtension)
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
                        descriptor = AbiFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
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
    fun managed_error_info_instances_share_vtable_callbacks() {
        assumeTrue(PlatformRuntime.isWindows)

        ManagedErrorInfoComObject(IllegalStateException("first")).detachReference().use { first ->
            ManagedErrorInfoComObject(IllegalStateException("second")).detachReference().use { second ->
                assertEquals(
                    PlatformAbi.pointerKey(PlatformAbi.readPointer(first.pointer.asRawAddress())),
                    PlatformAbi.pointerKey(PlatformAbi.readPointer(second.pointer.asRawAddress())),
                )

                first.queryInterface(IID.ISupportErrorInfo).getOrThrow().use { firstSupport ->
                    second.queryInterface(IID.ISupportErrorInfo).getOrThrow().use { secondSupport ->
                        assertEquals(
                            PlatformAbi.pointerKey(PlatformAbi.readPointer(firstSupport.pointer.asRawAddress())),
                            PlatformAbi.pointerKey(PlatformAbi.readPointer(secondSupport.pointer.asRawAddress())),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun managed_restricted_error_info_can_round_trip_details_through_irestrictederrorinfo_slots() {
        assumeTrue(PlatformRuntime.isWindows)

        ManagedRestrictedErrorInfoComObject(
            hResult = ExceptionHelpers.E_FAIL,
            errorInfo =
                WinRtRestrictedErrorInfo(
                    description = "outer message",
                    restrictedDescription = "inner message",
                    reference = "ABC123",
                    capabilitySid = null,
                ),
        ).detachReference().use { errorInfo ->
            java.lang.foreign.Arena.ofConfined().use { arena ->
                val descriptionOut = arena.allocate(java.lang.foreign.ValueLayout.ADDRESS)
                val hResultOut = arena.allocate(java.lang.foreign.ValueLayout.JAVA_INT)
                val restrictedDescriptionOut = arena.allocate(java.lang.foreign.ValueLayout.ADDRESS)
                val capabilitySidOut = arena.allocate(java.lang.foreign.ValueLayout.ADDRESS)
                ExceptionHelpers.throwExceptionForHR(
                    errorInfo.invokeAbi(
                        slot = 3,
                        descriptor = AbiFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                        ),
                        descriptionOut,
                        hResultOut,
                        restrictedDescriptionOut,
                        capabilitySidOut,
                    ),
                    "IRestrictedErrorInfo.GetErrorDetails",
                )
                assertEquals(
                    "outer message",
                    WindowsRuntimePlatform.readAndFreeBstr(
                        descriptionOut.get(java.lang.foreign.ValueLayout.ADDRESS, 0),
                    ),
                )
                assertEquals(ExceptionHelpers.E_FAIL.value, hResultOut.get(java.lang.foreign.ValueLayout.JAVA_INT, 0))
                assertEquals(
                    "inner message",
                    WindowsRuntimePlatform.readAndFreeBstr(
                        restrictedDescriptionOut.get(java.lang.foreign.ValueLayout.ADDRESS, 0),
                    ),
                )
                assertEquals(
                    "",
                    WindowsRuntimePlatform.readAndFreeBstr(
                        capabilitySidOut.get(java.lang.foreign.ValueLayout.ADDRESS, 0),
                    ),
                )

                val referenceOut = arena.allocate(java.lang.foreign.ValueLayout.ADDRESS)
                ExceptionHelpers.throwExceptionForHR(
                    errorInfo.invokeAbi(
                        slot = 4,
                        descriptor = AbiFunctionDescriptor.of(
                            NativeValueLayout.JAVA_INT,
                            NativeValueLayout.ADDRESS,
                            NativeValueLayout.ADDRESS,
                        ),
                        referenceOut,
                    ),
                    "IRestrictedErrorInfo.GetReference",
                )
                assertEquals(
                    "ABC123",
                    WindowsRuntimePlatform.readAndFreeBstr(
                        referenceOut.get(java.lang.foreign.ValueLayout.ADDRESS, 0),
                    ),
                )
            }
        }
    }

    @Test
    fun managed_restricted_error_info_instances_share_vtable_callbacks() {
        assumeTrue(PlatformRuntime.isWindows)

        val details = WinRtRestrictedErrorInfo(
            description = "outer message",
            restrictedDescription = "inner message",
            reference = "ABC123",
            capabilitySid = null,
        )
        ManagedRestrictedErrorInfoComObject(ExceptionHelpers.E_FAIL, details).detachReference().use { first ->
            ManagedRestrictedErrorInfoComObject(ExceptionHelpers.E_FAIL, details).detachReference().use { second ->
                assertEquals(
                    PlatformAbi.pointerKey(PlatformAbi.readPointer(first.pointer.asRawAddress())),
                    PlatformAbi.pointerKey(PlatformAbi.readPointer(second.pointer.asRawAddress())),
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

    @Test
    fun set_error_info_replays_restricted_error_info_for_runtime_exceptions() {
        assumeTrue(PlatformRuntime.isWindows)

        ExceptionHelpers.setErrorInfo(
            WinRtRuntimeException(
                message = "managed failure",
                hResult = ExceptionHelpers.E_FAIL,
                restrictedErrorInfo =
                    WinRtRestrictedErrorInfo(
                        description = "outer message",
                        restrictedDescription = "inner message",
                        reference = "ABC123",
                        capabilitySid = "S-1-15-3-1",
                    ),
            ),
        )

        val roundTrip = ExceptionHelpers.exceptionFor(ExceptionHelpers.E_FAIL, "Managed restricted error replay")
        assertEquals("outer message", roundTrip.restrictedErrorInfo?.description)
        assertEquals("inner message", roundTrip.restrictedErrorInfo?.restrictedDescription)
        assertEquals("ABC123", roundTrip.restrictedErrorInfo?.reference)
        assertEquals("S-1-15-3-1", roundTrip.restrictedErrorInfo?.capabilitySid)
    }
}
