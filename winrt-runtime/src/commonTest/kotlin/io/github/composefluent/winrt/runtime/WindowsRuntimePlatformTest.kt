package io.github.composefluent.winrt.runtime

import io.github.composefluent.winrt.runtime.exception.ManagedErrorInfoComObject
import io.github.composefluent.winrt.runtime.exception.ManagedRestrictedErrorInfoComObject
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsRuntimePlatformTest {
    @Test
    fun win32_last_error_is_translated_to_hresult() {
        assertEquals(HResult(0x80070002.toInt()), WinRTExceptionTranslator.hResultFromWin32(2))
        assertEquals(HResult(0x8007007E.toInt()), WinRTExceptionTranslator.hResultFromWin32(126))
    }

    @Test
    fun hresult_translation_returns_semantic_runtime_exception_types() {
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_INVALIDARG) is WinRTIllegalArgumentException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_POINTER) is WinRTNullReferenceException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_BOUNDS) is WinRTIndexOutOfBoundsException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_NOTIMPL) is WinRTNotImplementedException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_NOINTERFACE) is WinRTInvalidCastException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_OUTOFMEMORY) is WinRTOutOfMemoryException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.E_ACCESSDENIED) is WinRTAccessDeniedException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.ERROR_TIMEOUT) is WinRTTimeoutException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(KnownHResults.REGDB_E_CLASSNOTREG) is WinRTIllegalStateException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(ExceptionHelpers.ERROR_FILE_NOT_FOUND) is WinRTFileNotFoundException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(ExceptionHelpers.ERROR_BAD_FORMAT) is WinRTBadImageFormatException)
        assertTrue(WinRTExceptionTranslator.exceptionFor(ExceptionHelpers.E_XAMLPARSEFAILED) is WinRTXamlParseException)
    }

    @Test
    fun throwable_to_hresult_translation_matches_runtime_owner() {
        assertEquals(KnownHResults.E_INVALIDARG, WinRTExceptionTranslator.hResultFromException(IllegalArgumentException("bad arg")))
        assertEquals(KnownHResults.E_BOUNDS, WinRTExceptionTranslator.hResultFromException(IndexOutOfBoundsException("bad index")))
        assertEquals(KnownHResults.E_NOTSUPPORTED, WinRTExceptionTranslator.hResultFromException(UnsupportedOperationException("unsupported")))
        assertEquals(KnownHResults.ERROR_CANCELLED, WinRTExceptionTranslator.hResultFromException(CancellationException("cancelled")))
        assertEquals(
            KnownHResults.ERROR_TIMEOUT,
            WinRTExceptionTranslator.hResultFromException(WinRTTimeoutException("timeout", KnownHResults.ERROR_TIMEOUT)),
        )
        assertEquals(
            KnownHResults.E_ACCESSDENIED,
            WinRTExceptionTranslator.hResultFromException(WinRTAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)),
        )
        assertEquals(ExceptionHelpers.E_FAIL, WinRTExceptionTranslator.hResultFromException(IllegalStateException("missing")))
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
        if (!PlatformRuntime.isWindows) {
            return
        }

        val kernel32 = WinRTPlatformApi.tryLoadLibraryExWRaw("kernel32.dll", 0)
        try {
            assertTrue(!PlatformAbi.isNull(kernel32))
            val getLastError = WinRTPlatformApi.tryGetProcAddressRaw(kernel32, "GetLastError")
            assertTrue(!PlatformAbi.isNull(getLastError))
            assertEquals(
                PlatformAbi.nullPointer,
                WinRTPlatformApi.tryGetProcAddressRaw(kernel32, "DefinitelyMissingExport"),
            )
        } finally {
            WinRTPlatformApi.freeLibraryRaw(kernel32)
        }
    }

    @Test
    fun format_message_uses_windows_system_message_table() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val message = ExceptionHelpers.formatMessage(KnownHResults.ERROR_FILE_NOT_FOUND)
        assertTrue(!message.isNullOrBlank())
    }

    @Test
    fun set_error_info_accepts_managed_error_info_bridge() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        ExceptionHelpers.setErrorInfo(IllegalStateException("managed failure"))
        assertNotNull(ExceptionHelpers.formatMessage(ExceptionHelpers.E_FAIL))
    }

    @Test
    fun managed_error_info_can_round_trip_description_through_ierrorinfo_slots() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        ManagedErrorInfoComObject(IllegalStateException("boom")).detachReference().use { errorInfo ->
            PlatformAbi.confinedScope().use { scope ->
                val descriptionOut = PlatformAbi.allocatePointerSlot(scope)
                ExceptionHelpers.throwExceptionForHR(
                    ComVtableInvoker.invokeArgs(errorInfo.pointer, 5, descriptionOut),
                    "IErrorInfo.GetDescription",
                )
                assertEquals(
                    "boom",
                    WinRTPlatformApi.readAndFreeBstrRaw(PlatformAbi.readPointer(descriptionOut)),
                )
            }
        }
    }

    @Test
    fun managed_error_info_instances_share_vtable_callbacks() {
        if (!PlatformRuntime.isWindows) {
            return
        }

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
        if (!PlatformRuntime.isWindows) {
            return
        }

        ManagedRestrictedErrorInfoComObject(
            hResult = ExceptionHelpers.E_FAIL,
            errorInfo =
                WinRTRestrictedErrorInfo(
                    description = "outer message",
                    restrictedDescription = "inner message",
                    reference = "ABC123",
                    capabilitySid = null,
                ),
        ).detachReference().use { errorInfo ->
            PlatformAbi.confinedScope().use { scope ->
                val descriptionOut = PlatformAbi.allocatePointerSlot(scope)
                val hResultOut = PlatformAbi.allocateInt32Slot(scope)
                val restrictedDescriptionOut = PlatformAbi.allocatePointerSlot(scope)
                val capabilitySidOut = PlatformAbi.allocatePointerSlot(scope)
                ExceptionHelpers.throwExceptionForHR(
                    ComVtableInvoker.invokeArgs(
                        errorInfo.pointer,
                        3,
                        descriptionOut,
                        hResultOut,
                        restrictedDescriptionOut,
                        capabilitySidOut,
                    ),
                    "IRestrictedErrorInfo.GetErrorDetails",
                )
                assertEquals(
                    "outer message",
                    WinRTPlatformApi.readAndFreeBstrRaw(PlatformAbi.readPointer(descriptionOut)),
                )
                assertEquals(ExceptionHelpers.E_FAIL.value, PlatformAbi.readInt32(hResultOut))
                assertEquals(
                    "inner message",
                    WinRTPlatformApi.readAndFreeBstrRaw(PlatformAbi.readPointer(restrictedDescriptionOut)),
                )
                assertEquals(
                    "",
                    WinRTPlatformApi.readAndFreeBstrRaw(PlatformAbi.readPointer(capabilitySidOut)),
                )

                val referenceOut = PlatformAbi.allocatePointerSlot(scope)
                ExceptionHelpers.throwExceptionForHR(
                    ComVtableInvoker.invokeArgs(errorInfo.pointer, 4, referenceOut),
                    "IRestrictedErrorInfo.GetReference",
                )
                assertEquals(
                    "ABC123",
                    WinRTPlatformApi.readAndFreeBstrRaw(PlatformAbi.readPointer(referenceOut)),
                )
            }
        }
    }

    @Test
    fun managed_restricted_error_info_instances_share_vtable_callbacks() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val details = WinRTRestrictedErrorInfo(
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
    fun set_error_info_replays_restricted_error_info_for_runtime_exceptions() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        ExceptionHelpers.setErrorInfo(
            WinRTRuntimeException(
                message = "managed failure",
                hResult = ExceptionHelpers.E_FAIL,
                restrictedErrorInfo =
                    WinRTRestrictedErrorInfo(
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
