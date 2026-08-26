package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReferenceTrackerInteropTest {
    @Test
    fun com_object_reference_initializes_and_releases_reference_tracker() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)

            assertTrue(reference.tryInitializeReferenceTracker())
            assertTrue(reference.hasReferenceTracker)
            assertEquals(1, host.managerSetHostCalls)
            assertEquals(1, host.trackerConnectCalls)
            assertEquals(2, host.trackerAddRefFromSourceCalls)

            reference.close()

            assertEquals(0, host.objectReleaseCalls)
            host.releaseDisconnectedReferenceSources()
            assertEquals(2, host.trackerReleaseFromSourceCalls)
            assertEquals(1, host.trackerDisconnectCalls)
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
            assertEquals(1, host.trackerAddRefFromSourceCalls)

            initialized.close()

            host.releaseDisconnectedReferenceSources()
            assertEquals(1, host.trackerReleaseFromSourceCalls)
            assertEquals(1, host.objectReleaseCalls)
            assertEquals(2, host.trackerReleaseCalls)
        }
    }

    @Test
    fun disconnect_queues_release_before_synchronous_host_callback() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)
            assertTrue(reference.tryInitializeReferenceTracker())
            host.releaseDisconnectedOnDisconnect = true

            reference.close()

            assertEquals(1, host.trackerDisconnectCalls)
            assertEquals(1, host.objectReleaseCalls)
            assertEquals(2, host.trackerReleaseFromSourceCalls)
            assertEquals(2, host.trackerReleaseCalls)
        }
    }

    @Test
    fun get_ref_adds_com_reference_without_tracker_source_reference() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)

            assertTrue(reference.tryInitializeReferenceTracker())
            assertEquals(2, host.trackerAddRefFromSourceCalls)

            val pointer = reference.getRefPointer()

            assertEquals(PlatformAbi.pointerKey(host.objectPointer), PlatformAbi.pointerKey(pointer))
            assertEquals(1, host.objectAddRefCalls)
            assertEquals(2, host.trackerAddRefFromSourceCalls)

            WinRTPlatformApi.releaseRaw(pointer.asRawAddress())
            reference.close()
            host.releaseDisconnectedReferenceSources()
            assertEquals(2, host.objectReleaseCalls)
        }
    }

    @Test
    fun acquired_interface_keeps_tracker_without_copying_com_reference() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)
            assertTrue(reference.tryInitializeReferenceTracker())

            val queried = acquireInterfaceReference(reference, IID.IInspectable)

            assertTrue(queried.hasReferenceTracker)
            assertEquals(0, host.objectAddRefCalls)
            assertEquals(4, host.trackerAddRefFromSourceCalls)

            queried.close()
            reference.close()

            host.releaseDisconnectedReferenceSources()
            assertEquals(4, host.trackerReleaseFromSourceCalls)
        }
    }

    @Test
    fun known_fast_abi_pointer_is_retained_and_inherits_reference_tracker() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)
            assertTrue(reference.tryInitializeReferenceTracker())

            val known = IUnknownReference(
                reference.comPtr.attachKnownPointer(host.objectPointer.asRawComPtr()),
            )

            assertTrue(known.hasReferenceTracker)
            assertEquals(1, host.objectAddRefCalls)
            assertEquals(4, host.trackerAddRefFromSourceCalls)

            known.close()

            assertEquals(1, host.objectReleaseCalls)
            assertEquals(2, host.trackerReleaseFromSourceCalls)

            reference.close()

            host.releaseDisconnectedReferenceSources()
            assertEquals(4, host.trackerReleaseFromSourceCalls)
        }
    }

    @Test
    fun manager_host_walks_live_tracker_and_maps_managed_ccw_target() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)
            assertTrue(reference.tryInitializeReferenceTracker())

            assertEquals(
                KnownHResults.S_OK.value,
                ComVtableInvoker.invokeArgs(
                    host.referenceTrackerHostPointer.asRawComPtr(),
                    ReferenceTrackerHostVftblSlots.DisconnectUnusedReferenceSources,
                    0,
                ),
            )
            assertEquals(1, host.managerTrackingStartedCalls)
            assertEquals(1, host.trackerFindTargetsCalls)
            assertEquals(1, host.managerFindTargetsCompletedCalls)
            assertEquals(1, host.managerTrackingCompletedCalls)

            val value = Any()
            ComWrappersSupport.createCCWForObject(value, IID.IUnknown).use { ccw ->
                PlatformAbi.confinedScope().use { scope ->
                    val targetOut = PlatformAbi.allocatePointerSlot(scope)
                    assertEquals(
                        KnownHResults.S_OK.value,
                        ComVtableInvoker.invokeArgs(
                            host.referenceTrackerHostPointer.asRawComPtr(),
                            ReferenceTrackerHostVftblSlots.GetTrackerTarget,
                            ccw.pointer,
                            targetOut,
                        ),
                    )
                    val target = PlatformAbi.readPointer(targetOut)
                    assertNotEquals(PlatformAbi.nullPointer, target)
                    assertTrue(platformTryWinRTProjectionInboundBinding(target.value)?.get() === value)
                    WinRTPlatformApi.releaseRaw(target)
                }
            }

            reference.close()
        }
    }

    @Test
    fun manager_host_completes_tracking_when_target_completion_fails() {
        FakeReferenceTrackerHost.create().use { host ->
            val reference = IInspectableReference(host.objectPointer.asRawComPtr(), IID.IInspectable)
            assertTrue(reference.tryInitializeReferenceTracker())
            host.failFindTrackerTargetsCompleted = true

            assertTrue(
                ComVtableInvoker.invokeArgs(
                    host.referenceTrackerHostPointer.asRawComPtr(),
                    ReferenceTrackerHostVftblSlots.DisconnectUnusedReferenceSources,
                    0,
                ) < 0,
            )
            assertEquals(1, host.managerFindTargetsCompletedCalls)
            assertEquals(1, host.managerTrackingCompletedCalls)

            reference.close()
        }
    }

    private class FakeReferenceTrackerHost private constructor(
        private val scope: NativeScope,
        private val callbacks: List<NativeCallbackHandle>,
        val objectPointer: RawAddress,
        private val trackerPointer: RawAddress,
        private val managerPointer: RawAddress,
    ) : AutoCloseable {
        var referenceTrackerHostPointer: RawAddress = PlatformAbi.nullPointer
            private set
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
        var trackerConnectCalls: Int = 0
            private set
        var trackerDisconnectCalls: Int = 0
            private set
        var trackerFindTargetsCalls: Int = 0
            private set
        var managerSetHostCalls: Int = 0
            private set
        var managerTrackingStartedCalls: Int = 0
            private set
        var managerFindTargetsCompletedCalls: Int = 0
            private set
        var managerTrackingCompletedCalls: Int = 0
            private set
        var failFindTrackerTargetsCompleted: Boolean = false
        var releaseDisconnectedOnDisconnect: Boolean = false

        fun releaseDisconnectedReferenceSources() {
            assertEquals(
                KnownHResults.S_OK.value,
                ComVtableInvoker.invoke(
                    referenceTrackerHostPointer.asRawComPtr(),
                    ReferenceTrackerHostVftblSlots.ReleaseDisconnectedReferenceSources,
                ),
            )
        }

        override fun close() {
            ReferenceTrackerManager.clearForTests()
            if (!PlatformAbi.isNull(referenceTrackerHostPointer)) {
                WinRTPlatformApi.releaseRaw(referenceTrackerHostPointer)
                referenceTrackerHostPointer = PlatformAbi.nullPointer
            }
            callbacks.asReversed().forEach(NativeCallbackHandle::close)
            scope.close()
        }

        private fun queryInterface(args: List<Any?>): Int {
            val iidPointer = args[1] as RawAddress
            val resultPointer = args[2] as RawAddress
            val iid = PlatformAbi.readGuid(iidPointer)
            val resolved = when (iid) {
                IID.IUnknown -> when {
                    PlatformAbi.samePointer(args[0] as RawAddress, managerPointer) -> managerPointer
                    else -> objectPointer
                }

                IID.IInspectable -> objectPointer

                IID.IReferenceTracker -> trackerPointer
                IID.IReferenceTrackerManager -> managerPointer
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
            } else if (PlatformAbi.samePointer(thisPointer, trackerPointer)) {
                trackerReleaseCalls++
            }
            return 1
        }

        private fun connectFromTrackerSource(args: List<Any?>): Int {
            args.single()
            trackerConnectCalls++
            return KnownHResults.S_OK.value
        }

        private fun disconnectFromTrackerSource(args: List<Any?>): Int {
            args.single()
            trackerDisconnectCalls++
            if (releaseDisconnectedOnDisconnect) {
                releaseDisconnectedReferenceSources()
            }
            return KnownHResults.S_OK.value
        }

        private fun findTrackerTargets(args: List<Any?>): Int {
            trackerFindTargetsCalls++
            return ComVtableInvoker.invokeArgs(
                (args[1] as RawAddress).asRawComPtr(),
                FindReferenceTargetsCallbackVftblSlots.FoundTrackerTarget,
                objectPointer,
            )
        }

        private fun getReferenceTrackerManager(args: List<Any?>): Int {
            PlatformAbi.writePointer(args[1] as RawAddress, managerPointer)
            return KnownHResults.S_OK.value
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

        private fun referenceTrackingStarted(args: List<Any?>): Int {
            args.single()
            managerTrackingStartedCalls++
            return KnownHResults.S_OK.value
        }

        private fun findTrackerTargetsCompleted(args: List<Any?>): Int {
            assertEquals(0, args[1] as Int)
            managerFindTargetsCompletedCalls++
            return if (failFindTrackerTargetsCompleted) KnownHResults.E_FAIL.value else KnownHResults.S_OK.value
        }

        private fun referenceTrackingCompleted(args: List<Any?>): Int {
            args.single()
            managerTrackingCompletedCalls++
            return KnownHResults.S_OK.value
        }

        private fun setReferenceTrackerHost(args: List<Any?>): Int {
            managerSetHostCalls++
            referenceTrackerHostPointer = args[1] as RawAddress
            WinRTPlatformApi.addRefRaw(referenceTrackerHostPointer)
            return KnownHResults.S_OK.value
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
                val connectFromTrackerSourceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.connectFromTrackerSource(args) }
                val disconnectFromTrackerSourceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.disconnectFromTrackerSource(args) }
                val findTrackerTargetsCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.findTrackerTargets(args) }
                val getReferenceTrackerManagerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.getReferenceTrackerManager(args) }
                val referenceTrackingStartedCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.referenceTrackingStarted(args) }
                val findTrackerTargetsCompletedCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Int32),
                ) { args -> host.findTrackerTargetsCompleted(args) }
                val referenceTrackingCompletedCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.referenceTrackingCompleted(args) }
                val setReferenceTrackerHostCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.setReferenceTrackerHost(args) }

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
                PlatformAbi.writePointerAt(trackerVtable, 3, connectFromTrackerSourceCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 4, disconnectFromTrackerSourceCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 5, findTrackerTargetsCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 6, getReferenceTrackerManagerCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 7, addRefFromTrackerSourceCallback.pointer)
                PlatformAbi.writePointerAt(trackerVtable, 8, releaseFromTrackerSourceCallback.pointer)
                val trackerMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(trackerMemory, trackerVtable)

                val managerVtable = PlatformAbi.allocatePointerArray(scope, 7)
                PlatformAbi.writePointerAt(managerVtable, 0, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(managerVtable, 1, addRefCallback.pointer)
                PlatformAbi.writePointerAt(managerVtable, 2, releaseCallback.pointer)
                PlatformAbi.writePointerAt(managerVtable, 3, referenceTrackingStartedCallback.pointer)
                PlatformAbi.writePointerAt(managerVtable, 4, findTrackerTargetsCompletedCallback.pointer)
                PlatformAbi.writePointerAt(managerVtable, 5, referenceTrackingCompletedCallback.pointer)
                PlatformAbi.writePointerAt(managerVtable, 6, setReferenceTrackerHostCallback.pointer)
                val managerMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(managerMemory, managerVtable)

                host = FakeReferenceTrackerHost(
                    scope = scope,
                    callbacks = listOf(
                        queryInterfaceCallback,
                        addRefCallback,
                        releaseCallback,
                        addRefFromTrackerSourceCallback,
                        releaseFromTrackerSourceCallback,
                        connectFromTrackerSourceCallback,
                        disconnectFromTrackerSourceCallback,
                        findTrackerTargetsCallback,
                        getReferenceTrackerManagerCallback,
                        referenceTrackingStartedCallback,
                        findTrackerTargetsCompletedCallback,
                        referenceTrackingCompletedCallback,
                        setReferenceTrackerHostCallback,
                    ),
                    objectPointer = objectMemory,
                    trackerPointer = trackerMemory,
                    managerPointer = managerMemory,
                )
                return host
            }
        }
    }
}
