package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeakReferenceInteropTest {
    @Test
    fun set_target_preserves_shared_native_weak_reference_ownership() {
        // CsWinRT WeakReference.SetTarget replaces its shared IWeakReference RCW field.
        // It must not dispose the RCW while another managed owner still holds it.
        FakeWeakReferenceHost.create().use { host ->
            val target = host.createProjectedTarget()
            var replacement: ProjectedWeakReferenceTarget? = null
            var retained: NativeWeakReferenceHandle? = null
            var weakReference: WeakReference<ProjectedWeakReferenceTarget>? = null
            try {
                replacement = host.createProjectedTarget()
                retained = assertNotNull(WeakReferenceInterop.tryCreateNativeWeakReference(target))
                weakReference = WeakReference(target)
                weakReference.setTarget(replacement)
                weakReference.setTarget(null)

                assertEquals(1, host.weakReferenceOwnedBalance)
                // Only the initial cache miss needs the balanced canonical IUnknown QI.
                assertEquals(1, host.weakReferenceQueryInterfaceCalls)
                assertNotNull(retained.resolve(IID.IUnknown)).use { resolved ->
                    assertTrue(PlatformAbi.samePointer(resolved.pointer.asRawAddress(), target.nativeObject.pointer.asRawAddress()))
                }
            } finally {
                weakReference?.setTarget(null)
                retained?.close()
                replacement?.nativeObject?.close()
                target.nativeObject.close()
            }
            // FakeWeakReferenceHost.close drains cleanup before freeing its ABI callbacks.
        }
    }

    @Test
    fun failed_native_weak_reference_source_releases_written_owned_output() {
        // CsWinRT IWeakReferenceSourceMethods.GetWeakReference finally DisposeAbi
        // covers a non-null ABI out even when the source returns a failed HRESULT.
        FakeWeakReferenceHost.create(getWeakReferenceResult = KnownHResults.E_FAIL).use { host ->
            val target = host.createProjectedTarget()
            try {
                val projectedFailure = assertFailsWith<WinRTRuntimeException> {
                    WeakReferenceInterop.tryCreateNativeWeakReference(target)
                }
                assertEquals(KnownHResults.E_FAIL, projectedFailure.hResult)
                assertEquals(0, host.weakReferenceOwnedBalance)
                assertEquals(1, host.weakReferenceReleaseCalls)
                assertEquals(0, host.weakReferenceQueryInterfaceCalls)

                // EventSourceCache's exclusive entry shares the same ABI failure owner.
                val exclusiveFailure = assertFailsWith<WinRTRuntimeException> {
                    target.nativeObject.tryGetWeakReference()
                }
                assertEquals(KnownHResults.E_FAIL, exclusiveFailure.hResult)
                assertEquals(0, host.weakReferenceOwnedBalance)
                assertEquals(2, host.weakReferenceReleaseCalls)
            } finally {
                target.nativeObject.close()
            }
            // Host.close verifies both target and weak owners are gone before callbacks free.
        }
    }

    @Test
    fun failed_native_weak_reference_resolve_releases_written_owned_output() {
        // CsWinRT's current Resolve starts its DisposeAbi finally after ThrowForHR. Keep
        // GetWeakReference's cleared owned-output contract when native Resolve writes +1 then fails.
        FakeWeakReferenceHost.create(resolveResult = KnownHResults.E_FAIL).use { host ->
            val target = host.createProjectedTarget()
            var shared: NativeWeakReferenceHandle? = null
            var exclusive: WeakReferenceReference? = null
            try {
                // The fixture baseline and this live projected target each own one reference.
                assertEquals(1, host.targetOwnedBalance)
                val sharedReference = assertNotNull(WeakReferenceInterop.tryCreateNativeWeakReference(target))
                shared = sharedReference
                val sharedFailure = assertFailsWith<WinRTRuntimeException> {
                    sharedReference.resolve(IID.IUnknown)
                }
                assertEquals(KnownHResults.E_FAIL, sharedFailure.hResult)
                assertEquals(1, host.targetOwnedBalance)

                val exclusiveReference = assertNotNull(target.nativeObject.tryGetWeakReference())
                exclusive = exclusiveReference
                assertEquals(2, host.weakReferenceOwnedBalance)
                val exclusiveFailure = assertFailsWith<WinRTRuntimeException> {
                    exclusiveReference.resolve(IID.IUnknown)
                }
                assertEquals(KnownHResults.E_FAIL, exclusiveFailure.hResult)
                assertEquals(1, host.targetOwnedBalance)
            } finally {
                exclusive?.close()
                shared?.close()
                target.nativeObject.close()
            }
            assertEquals(0, host.targetOwnedBalance)
            // Host.close drains the shared RCW, then checks both balances before callbacks free.
        }
    }

    @Test
    fun abandoned_wrapper_releases_current_native_weak_reference_without_retaining_target() {
        FakeWeakReferenceHost.create().use { host ->
            val abandoned = createAbandonedWeakReference(host)

            repeat(10) {
                PlatformFinalization.drain()
                if (
                    abandoned.wrapper.get() == null &&
                    abandoned.target.get() == null &&
                    host.targetOwnedBalance == 0 &&
                    host.weakReferenceOwnedBalance == 0
                ) {
                    return@use
                }
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }

            assertNull(abandoned.wrapper.get())
            assertNull(abandoned.target.get())
            assertEquals(0, host.targetOwnedBalance)
            assertEquals(0, host.weakReferenceOwnedBalance)
            assertEquals(host.weakReferenceQueryInterfaceCalls + 1, host.weakReferenceReleaseCalls)
        }
    }

    @Test
    fun native_weak_reference_resolves_ccw_backed_object_identity() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        ComWrappersSupport.clearRegistriesForTests()

        val managed = ManagedWeakReferenceTarget("weak")
        val ccw = ComWrappersSupport.createCCWForObject(managed, IID.IInspectable)
        val target = ProjectedWeakReferenceTarget(
            pointer = PlatformAbi.fromRawComPtr(ccw.getRefPointer()),
            preventReleaseOnDispose = false,
        )

        try {
            val weakReference = WeakReferenceInterop.tryCreateNativeWeakReference(target)
            assertNotNull(weakReference)

            weakReference.use { reference ->
                val resolved = WeakReferenceInterop.resolveNativeWeakReference(reference) as? IWinRTObject
                assertNotNull(resolved)
                try {
                    assertTrue(resolved.nativeObject.sameIdentity(target.nativeObject))
                } finally {
                    if (resolved !== target) {
                        resolved.nativeObject.close()
                    }
                }
            }
        } finally {
            target.nativeObject.close()
            ccw.close()
        }
    }

    private data class ManagedWeakReferenceTarget(
        val name: String,
    )

    private class ProjectedWeakReferenceTarget(
        pointer: RawAddress,
        preventReleaseOnDispose: Boolean,
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(
            pointer.asRawComPtr(),
            IID.IInspectable,
            preventReleaseOnDispose = preventReleaseOnDispose,
        )
    }

    private fun createAbandonedWeakReference(host: FakeWeakReferenceHost): AbandonedWeakReference {
        val target = host.createProjectedTarget()
        val wrapper = WeakReference(target)
        return AbandonedWeakReference(
            wrapper = PlatformManagedWeakReference(wrapper),
            target = PlatformManagedWeakReference(target),
        )
    }

    private data class AbandonedWeakReference(
        val wrapper: PlatformManagedWeakReference<WeakReference<ProjectedWeakReferenceTarget>>,
        val target: PlatformManagedWeakReference<ProjectedWeakReferenceTarget>,
    )

    @OptIn(ExperimentalAtomicApi::class)
    private class FakeWeakReferenceHost private constructor(
        private val scope: NativeScope,
        private val callbacks: List<NativeCallbackHandle>,
        private val targetPointer: RawAddress,
        private val weakReferenceSourcePointer: RawAddress,
        private val weakReferencePointer: RawAddress,
        private val getWeakReferenceResult: HResult,
        private val resolveResult: HResult,
    ) : AutoCloseable {
        // The fixture owns one baseline COM reference until it closes. All external
        // owned references and temporary canonical QI references are balanced separately.
        private val targetReferenceCount = AtomicInt(1)
        private val weakReferenceReferenceCount = AtomicInt(1)
        private val weakReferenceReleases = AtomicInt(0)
        private val weakReferenceQueries = AtomicInt(0)

        val weakReferenceReleaseCalls: Int
            get() = weakReferenceReleases.load()

        val weakReferenceQueryInterfaceCalls: Int
            get() = weakReferenceQueries.load()

        val targetOwnedBalance: Int
            get() = targetReferenceCount.load() - 1

        val weakReferenceOwnedBalance: Int
            get() = weakReferenceReferenceCount.load() - 1

        fun createProjectedTarget(): ProjectedWeakReferenceTarget {
            addRef(listOf(targetPointer))
            return ProjectedWeakReferenceTarget(
                pointer = targetPointer,
                preventReleaseOnDispose = false,
            )
        }

        override fun close() {
            for (attempt in 0 until 10) {
                PlatformFinalization.drain()
                if (targetOwnedBalance == 0 && weakReferenceOwnedBalance == 0) break
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }
            // Never free callbacks/vtables while any target or shared weak RCW owns them.
            assertEquals(0, targetOwnedBalance, "Target references must be released before their Fake ABI closes.")
            assertEquals(0, weakReferenceOwnedBalance, "Shared native weak RCW must be released before its Fake ABI closes.")
            release(listOf(targetPointer))
            release(listOf(weakReferencePointer)) // Drop only the two fixture baselines.
            callbacks.asReversed().forEach(NativeCallbackHandle::close)
            scope.close()
        }

        private fun queryInterface(args: List<Any?>): Int {
            val thisPointer = args[0] as RawAddress
            val interfaceId = PlatformAbi.readGuid(args[1] as RawAddress)
            val resultPointer = args[2] as RawAddress
            val resolved =
                when {
                    PlatformAbi.samePointer(thisPointer, targetPointer) &&
                        (interfaceId == IID.IUnknown || interfaceId == IID.IInspectable) -> targetPointer
                    PlatformAbi.samePointer(thisPointer, targetPointer) && interfaceId == IID.IWeakReferenceSource ->
                        weakReferenceSourcePointer
                    PlatformAbi.samePointer(thisPointer, weakReferenceSourcePointer) &&
                        (interfaceId == IID.IUnknown || interfaceId == IID.IWeakReferenceSource) -> weakReferenceSourcePointer
                    PlatformAbi.samePointer(thisPointer, weakReferencePointer) &&
                        (interfaceId == IID.IUnknown || interfaceId == IID.IWeakReference) -> weakReferencePointer
                    else -> PlatformAbi.nullPointer
                }
            if (!PlatformAbi.isNull(resolved)) {
                if (PlatformAbi.samePointer(resolved, weakReferencePointer)) {
                    weakReferenceQueries.fetchAndAdd(1)
                }
                addRef(listOf(resolved))
            }
            PlatformAbi.writePointer(resultPointer, resolved)
            return if (PlatformAbi.isNull(resolved)) KnownHResults.E_NOINTERFACE.value else KnownHResults.S_OK.value
        }

        private fun addRef(args: List<Any?>): Int {
            val pointer = args.single() as RawAddress
            return when {
                PlatformAbi.samePointer(pointer, targetPointer) -> targetReferenceCount.fetchAndAdd(1) + 1
                PlatformAbi.samePointer(pointer, weakReferencePointer) -> weakReferenceReferenceCount.fetchAndAdd(1) + 1
                else -> 2
            }
        }

        private fun release(args: List<Any?>): Int {
            val pointer = args.single() as RawAddress
            if (PlatformAbi.samePointer(pointer, targetPointer)) {
                return targetReferenceCount.fetchAndAdd(-1) - 1
            }
            if (PlatformAbi.samePointer(pointer, weakReferencePointer)) {
                weakReferenceReleases.fetchAndAdd(1)
                return weakReferenceReferenceCount.fetchAndAdd(-1) - 1
            }
            return 1
        }

        private fun getWeakReference(args: List<Any?>): Int {
            addRef(listOf(weakReferencePointer)) // GetWeakReference returns an owned out reference.
            PlatformAbi.writePointer(args[1] as RawAddress, weakReferencePointer)
            return getWeakReferenceResult.value
        }

        private fun resolveWeakReference(args: List<Any?>): Int {
            val interfaceId = PlatformAbi.readGuid(args[1] as RawAddress)
            val supported = interfaceId == IID.IUnknown || interfaceId == IID.IInspectable
            val resolved = if (supported) targetPointer else PlatformAbi.nullPointer
            if (supported) addRef(listOf(resolved))
            PlatformAbi.writePointer(args[2] as RawAddress, resolved)
            return if (supported) resolveResult.value else KnownHResults.E_NOINTERFACE.value
        }

        companion object {
            fun create(
                getWeakReferenceResult: HResult = KnownHResults.S_OK,
                resolveResult: HResult = KnownHResults.S_OK,
            ): FakeWeakReferenceHost {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: FakeWeakReferenceHost
                val queryInterfaceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.queryInterface(args) }
                val addRefCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.addRef(args) }
                val releaseCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.release(args) }
                val getWeakReferenceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.getWeakReference(args) }
                val resolveWeakReferenceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.resolveWeakReference(args) }

                fun createObject(vtableSize: Int): RawAddress {
                    val vtable = PlatformAbi.allocatePointerArray(scope, vtableSize)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                    return PlatformAbi.allocatePointerSlot(scope).also { PlatformAbi.writePointer(it, vtable) }
                }

                val targetPointer = createObject(3)
                val weakReferenceSourcePointer = createObject(4)
                val weakReferencePointer = createObject(4)
                val weakReferenceSourceVtable = PlatformAbi.readPointer(weakReferenceSourcePointer)
                PlatformAbi.writePointerAt(
                    weakReferenceSourceVtable,
                    WeakReferenceSourceVftblSlots.GetWeakReference,
                    getWeakReferenceCallback.pointer,
                )
                PlatformAbi.writePointerAt(
                    PlatformAbi.readPointer(weakReferencePointer),
                    WeakReferenceVftblSlots.Resolve,
                    resolveWeakReferenceCallback.pointer,
                )

                host = FakeWeakReferenceHost(
                    scope = scope,
                    callbacks = listOf(queryInterfaceCallback, addRefCallback, releaseCallback, getWeakReferenceCallback, resolveWeakReferenceCallback),
                    targetPointer = targetPointer,
                    weakReferenceSourcePointer = weakReferenceSourcePointer,
                    weakReferencePointer = weakReferencePointer,
                    getWeakReferenceResult = getWeakReferenceResult,
                    resolveResult = resolveResult,
                )
                return host
            }
        }
    }
}
