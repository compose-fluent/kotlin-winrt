package io.github.composefluent.winrt.runtime

import io.github.composefluent.winrt.runtime.exception.ManagedErrorInfoComObject
import io.github.composefluent.winrt.runtime.exception.ManagedRestrictedErrorInfoComObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WindowsRuntimePlatformJvmTest {
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
