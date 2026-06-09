package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Test

class EventRuntimeInfrastructureTest {
    @Test
    fun event_source_state_treats_reference_tracker_refs_as_native_refs() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = testEventInterfaceId,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.INT32),
        ) { }
        val state =
            object : EventSourceState<(Any?, Int) -> Unit>(PlatformAbi.nullPointer, 61) {
                override fun createEventInvoke(): (Any?, Int) -> Unit = { _, _ -> }
            }

        handle.use {
            it.createReference().use { reference ->
                state.initializeReferenceTracking(PlatformAbi.fromRawComPtr(reference.pointer))
                reference.queryInterface(IID.IReferenceTrackerTarget).getOrThrow().use { trackerTarget ->
                    val trackerPointer = trackerTarget.pointer
                    trackerTarget.close()
                    reference.close()

                    assertEquals(false, state.hasComReferences())
                    ComVtableInvoker.invoke(trackerPointer, ReferenceTrackerTargetVftblSlots.AddRefFromReferenceTracker)
                    assertEquals(true, state.hasComReferences())
                    ComVtableInvoker.invoke(trackerPointer, ReferenceTrackerTargetVftblSlots.ReleaseFromReferenceTracker)
                    assertEquals(false, state.hasComReferences())
                }
            }
        }
    }

    @Test
    fun event_source_cache_tracks_weak_reference_source_objects() {
        assumeTrue(PlatformRuntime.isWindows)
        EventSourceCache.clearForTests()

        RuntimeScope.initializeMultithreaded().use {
            WinRtRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow().use { instance ->
                val sentinel = Any()
                val state = WeakReference<Any>(sentinel)

                EventSourceCache.create(instance, 41, state)
                assertSame(state, EventSourceCache.getState(instance, 41))

                EventSourceCache.remove(PlatformAbi.pointerKey(instance.pointer), 41, state)
                assertNull(EventSourceCache.getState(instance, 41))
            }
        }
    }

    companion object {
        private val testEventInterfaceId = Guid("0f0f0f0f-1111-2222-3333-444444444444")
    }
}
