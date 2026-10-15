package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComInterfaceProbeTest {
    @Test
    fun probe_releases_the_queried_interface_and_handles_failure_or_null_output() {
        if (!PlatformRuntime.isWindows) return
        // CsWinRT 3 ComObjectHelpers.IsFreeThreadedUnsafe balances the QI result,
        // which may have a different address and vtable from the source interface.
        val interfaceId = Guid("f0ceaf85-c391-4d37-8919-0e3529fb5e2b")
        PlatformAbi.confinedScope().use { scope ->
            val source = PlatformAbi.allocatePointerSlot(scope)
            val result = PlatformAbi.allocatePointerSlot(scope)
            val sourceVtable = PlatformAbi.allocatePointerArray(scope, 3)
            val resultVtable = PlatformAbi.allocatePointerArray(scope, 3)
            PlatformAbi.writePointer(source, sourceVtable)
            PlatformAbi.writePointer(result, resultVtable)
            var observedInterfaceId: Guid? = null
            var releasedPointer = RawAddress.Null
            var releases = 0
            var hResult = KnownHResults.S_OK.value
            var returnedPointer = result
            ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.QueryInterface) { _, iid, output, _, _, _, _ ->
                observedInterfaceId = PlatformAbi.readGuid(RawAddress(iid))
                PlatformAbi.writePointer(RawAddress(output), returnedPointer)
                hResult
            }.use { query ->
                ComAbiInteropBridge.createRawWordComMethodCallback(IUnknownVftbl.Release) { thisWord, _, _, _, _, _, _ ->
                    releasedPointer = RawAddress(thisWord)
                    releases++
                    1
                }.use { release ->
                    PlatformAbi.writePointerAt(sourceVtable, IUnknownVftblSlots.QueryInterface, query.pointer)
                    PlatformAbi.writePointerAt(resultVtable, IUnknownVftblSlots.Release, release.pointer)

                    repeat(3) { assertTrue(supportsComInterface(source, interfaceId)) }
                    assertEquals(interfaceId, observedInterfaceId)
                    assertEquals(result, releasedPointer)
                    assertEquals(3, releases)

                    returnedPointer = RawAddress.Null
                    hResult = KnownHResults.E_NOINTERFACE.value
                    assertFalse(supportsComInterface(source, interfaceId))
                    hResult = KnownHResults.S_OK.value
                    assertFalse(supportsComInterface(source, interfaceId))
                    assertFalse(supportsComInterface(RawAddress.Null, interfaceId))
                    assertEquals(3, releases)
                }
            }
        }
    }
}
