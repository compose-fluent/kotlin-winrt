package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeakReferenceInteropTest {
    @Test
    fun set_target_releases_the_previous_native_weak_reference_once() {
        FakeWeakReferenceHost.create().use { host ->
            val weakReference = WeakReference(host.createProjectedTarget())

            weakReference.setTarget(host.createProjectedTarget())

            assertEquals(1, host.weakReferenceReleaseCalls)

            weakReference.setTarget(null)
            assertEquals(2, host.weakReferenceReleaseCalls)
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
                    host.weakReferenceReleaseCalls == 1
                ) {
                    return@use
                }
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }

            assertNull(abandoned.wrapper.get())
            assertNull(abandoned.target.get())
            assertEquals(1, host.weakReferenceReleaseCalls)
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
        val target = ProjectedWeakReferenceTarget(PlatformAbi.fromRawComPtr(ccw.getRefPointer()))

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
    ) : IWinRTObject {
        override val nativeObject: ComObjectReference = IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)
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

    private class FakeWeakReferenceHost private constructor(
        private val scope: NativeScope,
        private val callbacks: List<NativeCallbackHandle>,
        private val targetPointer: RawAddress,
        private val weakReferenceSourcePointer: RawAddress,
        private val weakReferencePointer: RawAddress,
    ) : AutoCloseable {
        var weakReferenceReleaseCalls: Int = 0
            private set

        fun createProjectedTarget(): ProjectedWeakReferenceTarget =
            ProjectedWeakReferenceTarget(targetPointer)

        override fun close() {
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
                addRef(listOf(resolved))
            }
            PlatformAbi.writePointer(resultPointer, resolved)
            return if (PlatformAbi.isNull(resolved)) KnownHResults.E_NOINTERFACE.value else KnownHResults.S_OK.value
        }

        private fun addRef(args: List<Any?>): Int = 2

        private fun release(args: List<Any?>): Int {
            if (PlatformAbi.samePointer(args.single() as RawAddress, weakReferencePointer)) {
                weakReferenceReleaseCalls++
            }
            return 1
        }

        private fun getWeakReference(args: List<Any?>): Int {
            PlatformAbi.writePointer(args[1] as RawAddress, weakReferencePointer)
            return KnownHResults.S_OK.value
        }

        companion object {
            fun create(): FakeWeakReferenceHost {
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

                fun createObject(vtableSize: Int): RawAddress {
                    val vtable = PlatformAbi.allocatePointerArray(scope, vtableSize)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                    return PlatformAbi.allocatePointerSlot(scope).also { PlatformAbi.writePointer(it, vtable) }
                }

                val targetPointer = createObject(3)
                val weakReferenceSourcePointer = createObject(4)
                val weakReferencePointer = createObject(3)
                val weakReferenceSourceVtable = PlatformAbi.readPointer(weakReferenceSourcePointer)
                PlatformAbi.writePointerAt(
                    weakReferenceSourceVtable,
                    WeakReferenceSourceVftblSlots.GetWeakReference,
                    getWeakReferenceCallback.pointer,
                )

                host = FakeWeakReferenceHost(
                    scope = scope,
                    callbacks = listOf(queryInterfaceCallback, addRefCallback, releaseCallback, getWeakReferenceCallback),
                    targetPointer = targetPointer,
                    weakReferenceSourcePointer = weakReferenceSourcePointer,
                    weakReferencePointer = weakReferencePointer,
                )
                return host
            }
        }
    }
}
