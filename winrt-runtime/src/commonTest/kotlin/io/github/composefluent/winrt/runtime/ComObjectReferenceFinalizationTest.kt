package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComObjectReferenceFinalizationTest {
    @Test
    fun abandoned_owned_reference_releases_its_com_pointer() {
        FakeOwnedReferenceHost.create().use { host ->
            val abandoned = abandonOwnedReference(host)

            repeat(20) {
                PlatformFinalization.drain()
                if (abandoned.get() == null && host.releaseCalls == 1) {
                    return@use
                }
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }

            assertNull(abandoned.get())
            assertEquals(1, host.releaseCalls)
        }
    }

    @Test
    fun explicit_close_and_finalization_release_owned_pointer_once() {
        FakeOwnedReferenceHost.create().use { host ->
            val abandoned = closeAndAbandonOwnedReference(host)
            assertEquals(1, host.releaseCalls)

            repeat(20) {
                PlatformFinalization.drain()
                if (abandoned.get() == null) {
                    assertEquals(1, host.releaseCalls)
                    return@use
                }
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }

            assertNull(abandoned.get())
            assertEquals(1, host.releaseCalls)
        }
    }

    private fun abandonOwnedReference(
        host: FakeOwnedReferenceHost,
    ): PlatformManagedWeakReference<IInspectableReference> {
        val reference = host.createReference()
        return PlatformManagedWeakReference(reference)
    }

    private fun closeAndAbandonOwnedReference(
        host: FakeOwnedReferenceHost,
    ): PlatformManagedWeakReference<IInspectableReference> {
        val reference = host.createReference()
        reference.close()
        return PlatformManagedWeakReference(reference)
    }

    private class FakeOwnedReferenceHost private constructor(
        private val scope: NativeScope,
        private val releaseCallback: NativeCallbackHandle,
        private val pointer: RawAddress,
    ) : AutoCloseable {
        var releaseCalls: Int = 0
            private set

        fun createReference(): IInspectableReference =
            IInspectableReference(pointer.asRawComPtr(), IID.IInspectable)

        override fun close() {
            releaseCallback.close()
            scope.close()
        }

        companion object {
            fun create(): FakeOwnedReferenceHost {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: FakeOwnedReferenceHost
                val releaseCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) {
                    host.releaseCalls++
                    1
                }
                val vtable = PlatformAbi.allocatePointerArray(scope, 3)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                val pointer = PlatformAbi.allocatePointerSlot(scope).also {
                    PlatformAbi.writePointer(it, vtable)
                }
                host = FakeOwnedReferenceHost(scope, releaseCallback, pointer)
                return host
            }
        }
    }
}
