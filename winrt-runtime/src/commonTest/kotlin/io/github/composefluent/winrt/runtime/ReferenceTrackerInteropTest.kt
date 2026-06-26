package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceTrackerInteropTest {
    @Test
    fun com_object_reference_initializes_and_releases_reference_tracker() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)

            assertTrue(reference.tryInitializeReferenceTracker())
            assertTrue(reference.hasReferenceTracker)
            assertEquals(1, host.trackerAddRefFromSourceCalls)

            reference.close()

            assertEquals(2, host.trackerReleaseFromSourceCalls)
            assertEquals(1, host.objectReleaseCalls)
            assertEquals(2, host.trackerReleaseCalls)
        }
    }

    @Test
    fun composable_reference_initialization_tracks_without_addref_from_tracker_source() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)

            val initialized = ComWrappersSupport.initializeComposableReference(reference)

            assertTrue(initialized.hasReferenceTracker)
            assertEquals(0, host.trackerAddRefFromSourceCalls)

            initialized.close()

            assertEquals(1, host.trackerReleaseFromSourceCalls)
            assertEquals(1, host.objectReleaseCalls)
            assertEquals(2, host.trackerReleaseCalls)
        }
    }

    @Test
    fun get_ref_adds_com_reference_without_tracker_source_reference() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)

            assertTrue(reference.tryInitializeReferenceTracker())
            assertEquals(1, host.trackerAddRefFromSourceCalls)

            val pointer = reference.getRefPointer()

            assertEquals(PlatformAbi.pointerKey(host.objectPointer), PlatformAbi.pointerKey(pointer))
            assertEquals(1, host.objectAddRefCalls)
            assertEquals(1, host.trackerAddRefFromSourceCalls)

            WinRTPlatformApi.releaseRaw(pointer.asRawAddress())
            reference.close()
            assertEquals(2, host.objectReleaseCalls)
        }
    }

    private class FakeReferenceTrackerHost private constructor(
        private val scope: NativeScope,
        private val callbacks: List<NativeCallbackHandle>,
        val objectPointer: RawAddress,
        private val trackerPointer: RawAddress,
    ) : AutoCloseable {
        var objectAddRefCalls: Int = 0
            private set
        var objectReleaseCalls: Int = 0
            private set
        var trackerReleaseCalls: Int = 0
            private set
        var trackerAddRefFromSourceCalls: Int = 0
            private set
        var trackerReleaseFromSourceCalls: Int = 0
            private set

        override fun close() {
            callbacks.asReversed().forEach(NativeCallbackHandle::close)
            scope.close()
        }

        private fun queryInterface(args: List<Any?>): Int {
            val iidPointer = args[1] as RawAddress
            val resultPointer = args[2] as RawAddress
            val iid = PlatformAbi.readGuid(iidPointer)
            val resolved = when (iid) {
                IID.IUnknown,
                IID.IInspectable,
                -> objectPointer

                IID.IReferenceTracker -> trackerPointer
                else -> PlatformAbi.nullPointer
            }
            PlatformAbi.writePointer(resultPointer, resolved)
            return if (PlatformAbi.isNull(resolved)) {
                KnownHResults.E_NOINTERFACE.value
            } else {
                KnownHResults.S_OK.value
            }
        }

        private fun addRef(args: List<Any?>): Int {
            val thisPointer = args.single() as RawAddress
            if (PlatformAbi.samePointer(thisPointer, objectPointer)) {
                objectAddRefCalls++
            }
            return 2
        }

        private fun release(args: List<Any?>): Int {
            val thisPointer = args.single() as RawAddress
            if (PlatformAbi.samePointer(thisPointer, objectPointer)) {
                objectReleaseCalls++
            } else {
                trackerReleaseCalls++
            }
            return 1
        }

        private fun addRefFromTrackerSource(args: List<Any?>): Int {
            args.single()
            trackerAddRefFromSourceCalls++
            return 1
        }

        private fun releaseFromTrackerSource(args: List<Any?>): Int {
            args.single()
            trackerReleaseFromSourceCalls++
            return 1
        }

        companion object {
            fun create(): FakeReferenceTrackerHost {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: FakeReferenceTrackerHost
                val queryInterfaceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.queryInterface(args) }
                val addRefCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.addRef(args) }
                val releaseCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.release(args) }
                val addRefFromTrackerSourceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.addRefFromTrackerSource(args) }
                val releaseFromTrackerSourceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.releaseFromTrackerSource(args) }

                val objectVtable = PlatformAbi.allocatePointerArray(scope, 3)
                PlatformAbi.writePointerAt(objectVtable, 0, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(objectVtable, 1, addRefCallback.pointer)
                PlatformAbi.writePointerAt(objectVtable, 2, releaseCallback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, objectVtable)

                val trackerVtable = PlatformAbi.allocatePointerArray(scope, 10)
                PlatformAbi.writePointerAt(trackerVtable, 0, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 1, addRefCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 2, releaseCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 7, addRefFromTrackerSourceCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 8, releaseFromTrackerSourceCallback.pointer)
                val trackerMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(trackerMemory, trackerVtable)

                host = FakeReferenceTrackerHost(
                    scope = scope,
                    callbacks = listOf(
                        queryInterfaceCallback,
                        addRefCallback,
                        releaseCallback,
                        addRefFromTrackerSourceCallback,
                        releaseFromTrackerSourceCallback,
                    ),
                    objectPointer = objectMemory,
                    trackerPointer = trackerMemory,
                )
                return host
            }
        }
    }
}
