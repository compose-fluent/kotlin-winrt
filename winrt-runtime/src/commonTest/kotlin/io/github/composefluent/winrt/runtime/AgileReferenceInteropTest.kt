package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgileReferenceInteropTest {
    @Test
    fun agile_reference_interface_resolves_owned_reference_with_same_identity() {
        FakeAgileReferenceHost.create().use { host ->
            val agileReference = AgileReferenceInterfaceReference(host.agilePointer, IID.IAgileReference)

            agileReference.use { agile ->
                val resolved = agile.resolve(IID.IUnknown)
                assertNotNull(resolved)
                resolved.use { reference ->
                    assertTrue(reference.sameIdentity(IUnknownReference(host.objectPointer.asRawComPtr(), preventReleaseOnDispose = true)))
                }
            }

            assertEquals(1, host.resolveCalls)
            assertTrue(host.objectAddRefCalls >= 1)
            assertTrue(host.objectReleaseCalls >= 1)
            assertEquals(1, host.agileReleaseCalls)
        }
    }

    private class FakeAgileReferenceHost private constructor(
        private val scope: NativeScope,
        private val callbacks: List<NativeCallbackHandle>,
        val objectPointer: RawAddress,
        val agilePointer: RawAddress,
    ) : AutoCloseable {
        var objectAddRefCalls: Int = 0
            private set
        var objectReleaseCalls: Int = 0
            private set
        var agileReleaseCalls: Int = 0
            private set
        var resolveCalls: Int = 0
            private set

        override fun close() {
            callbacks.asReversed().forEach(NativeCallbackHandle::close)
            scope.close()
        }

        private fun queryInterface(args: List<Any?>): Int {
            val thisPointer = args[0] as RawAddress
            val iid = PlatformAbi.readGuid(args[1] as RawAddress)
            val resultPointer = args[2] as RawAddress
            val resolved = when {
                PlatformAbi.samePointer(thisPointer, objectPointer) &&
                    (iid == IID.IUnknown || iid == IID.IInspectable) -> objectPointer
                PlatformAbi.samePointer(thisPointer, agilePointer) &&
                    (iid == IID.IUnknown || iid == IID.IAgileReference) -> agilePointer
                else -> PlatformAbi.nullPointer
            }
            if (!PlatformAbi.isNull(resolved)) {
                addRef(listOf(resolved))
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
            when {
                PlatformAbi.samePointer(thisPointer, objectPointer) -> objectReleaseCalls++
                PlatformAbi.samePointer(thisPointer, agilePointer) -> agileReleaseCalls++
            }
            return 1
        }

        private fun resolve(args: List<Any?>): Int {
            val resultPointer = args[2] as RawAddress
            resolveCalls++
            addRef(listOf(objectPointer))
            PlatformAbi.writePointer(resultPointer, objectPointer)
            return KnownHResults.S_OK.value
        }

        companion object {
            fun create(): FakeAgileReferenceHost {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: FakeAgileReferenceHost
                val queryInterfaceCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.queryInterface(args) }
                val addRefCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.addRef(args) }
                val releaseCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) { args -> host.release(args) }
                val resolveCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args -> host.resolve(args) }

                val objectVtable = PlatformAbi.allocatePointerArray(scope, 3)
                PlatformAbi.writePointerAt(objectVtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(objectVtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                PlatformAbi.writePointerAt(objectVtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, objectVtable)

                val agileVtable = PlatformAbi.allocatePointerArray(scope, 4)
                PlatformAbi.writePointerAt(agileVtable, IUnknownVftblSlots.QueryInterface, queryInterfaceCallback.pointer)
                PlatformAbi.writePointerAt(agileVtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                PlatformAbi.writePointerAt(agileVtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                PlatformAbi.writePointerAt(agileVtable, 3, resolveCallback.pointer)
                val agileMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(agileMemory, agileVtable)

                host = FakeAgileReferenceHost(
                    scope = scope,
                    callbacks = listOf(
                        queryInterfaceCallback,
                        addRefCallback,
                        releaseCallback,
                        resolveCallback,
                    ),
                    objectPointer = objectMemory,
                    agilePointer = agileMemory,
                )
                return host
            }
        }
    }
}
