@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ComObjectReferenceFinalizationTest {
    @Test
    fun owned_abi_disposal_releases_once_without_querying_or_wrapping_the_pointer() {
        // WindowsRuntimeUnknownMarshaller.Free works for any COM ABI pointer and
        // never queries its interfaces, even when the requested projection differs.
        FakeOwnedReferenceHost.create(supportAgileInspectable = true).use { host ->
            host.createReference(trackContext = false).use { parent ->
                val descriptor = WinRTDelegateDescriptor(
                    interfaceId = IID.IUnknown,
                    parameterKinds = emptyList(),
                    returnKind = WinRTDelegateValueKind.UNIT,
                )
                MarshalInspectable.any().disposeAbi(parent.acquireOwnedPointer().asRawAddress())
                MarshalDelegate.disposeAbi(parent.acquireOwnedPointer().asRawAddress(), descriptor)
                disposeComAbi(PlatformAbi.nullPointer)
                assertEquals(0, host.agilityQueries)
                assertEquals(2, host.releaseCalls)
                assertEquals(1, host.ownedReferenceBalance)
            }
            assertEquals(3, host.releaseCalls)
        }
    }

    // CsWinRT 3.0 FreeThreadedObjectReference.DerivedTryAsNative carries the
    // parent's agility into the queried reference without another ABI probe.
    @Test
    fun queried_agile_references_preserve_classification_and_independent_ownership() {
        FakeOwnedReferenceHost.create(supportAgileInspectable = true).use { host ->
            val parent = host.createReference()
            val child = parent.queryInterface(IID.IInspectable).getOrThrow()
            try {
                parent.close()
                child.queryInterface(IID.IUnknown).getOrThrow().use {
                    assertEquals(1, host.agilityQueries)
                    assertEquals(2, host.ownedReferenceBalance)
                }
                assertEquals(1, host.ownedReferenceBalance)
            } finally {
                parent.close()
                child.close()
            }
        }
    }

    @Test
    fun unclassified_parent_does_not_skip_queried_reference_agility_probe() {
        FakeOwnedReferenceHost.create(supportAgileInspectable = true).use { host ->
            host.createReference(trackContext = false).use { parent ->
                assertEquals(0, host.agilityQueries)
                parent.queryInterface(IID.IInspectable).getOrThrow().use {
                    assertEquals(1, host.agilityQueries)
                }
            }
        }
    }

    @Test
    fun non_agile_parent_does_not_promote_queried_reference_to_free_threaded() {
        FakeOwnedReferenceHost.create(supportAgileInspectable = true, reportsAgile = false).use { host ->
            host.createReference().use { parent ->
                assertEquals(1, host.agilityQueries)
                parent.queryInterface(IID.IInspectable).getOrThrow().use {
                    assertEquals(2, host.agilityQueries)
                }
            }
        }
    }

    // CsWinRT keeps additional type data on the RCW. A cache value may refer
    // back to its owner; this must remain a collectible managed cycle.
    @Test
    fun abandoned_wrapper_with_self_referencing_type_data_is_reclaimed() {
        FakeOwnedReferenceHost.create().use { host ->
            val abandoned = abandonSelfReferencingWrapper(host)
            repeat(20) {
                PlatformFinalization.drain()
                if (abandoned.get() == null && host.releaseCalls == 1) return@use
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }
            assertNull(abandoned.get())
            assertEquals(1, host.releaseCalls)
        }
    }

    private fun abandonSelfReferencingWrapper(
        host: FakeOwnedReferenceHost,
    ): PlatformManagedWeakReference<ProjectedInspectableObject> {
        val value = ProjectedInspectableObject(host.createReference())
        value.additionalTypeData[WinRTTypeHandle("test.Owner", IID.IInspectable)] = value
        return PlatformManagedWeakReference(value)
    }

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

    // CsWinRT MarshalInspectable.CreateMarshaler2 unwraps an RCW and calls AsValue(iid).
    // Kotlin's public object marshaler also registers its scoped owned QI +1 for forgotten close.
    @Test
    fun abandoned_projected_object_marshaler_releases_its_owned_query_reference() {
        FakeOwnedReferenceHost.create(supportAgileInspectable = true).use { host ->
            val parent = host.createReference()
            try {
                val releaseBaseline = host.releaseCalls
                val abandoned = abandonProjectedObjectMarshaler(host, parent, releaseBaseline)

                awaitProjectedObjectMarshalerFinalization(host, abandoned, releaseBaseline + 1)
                assertEquals(1, host.ownedReferenceBalance)
            } finally {
                parent.close()
            }
            assertEquals(0, host.ownedReferenceBalance)
        }
    }

    // ObjectReferenceValue.Dispose owns one matching Release; Kotlin's shared cleanup once-guard
    // must preserve that ownership through repeated explicit close and subsequent Cleaner execution.
    @Test
    fun explicit_close_twice_and_finalization_release_projected_object_marshaler_once() {
        FakeOwnedReferenceHost.create(supportAgileInspectable = true).use { host ->
            val parent = host.createReference()
            try {
                val releaseBaseline = host.releaseCalls
                val abandoned = abandonProjectedObjectMarshaler(
                    host,
                    parent,
                    releaseBaseline,
                    closeExplicitly = true,
                )
                assertEquals(releaseBaseline + 1, host.releaseCalls)
                assertEquals(1, host.ownedReferenceBalance)

                awaitProjectedObjectMarshalerFinalization(host, abandoned, releaseBaseline + 1)
                assertEquals(1, host.ownedReferenceBalance)
            } finally {
                parent.close()
            }
            assertEquals(0, host.ownedReferenceBalance)
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

    private fun abandonProjectedObjectMarshaler(
        host: FakeOwnedReferenceHost,
        parent: ComObjectReference,
        releaseBaseline: Int,
        closeExplicitly: Boolean = false,
    ): AbandonedProjectedObjectMarshaler {
        val value = ProjectedInspectableObject(parent)
        val marshaler = WinRTObjectMarshaller.createMarshaler(value)
        try {
            assertEquals(releaseBaseline, host.releaseCalls)
            assertEquals(2, host.ownedReferenceBalance)
            if (closeExplicitly) {
                marshaler.close()
                marshaler.close()
            }
            return AbandonedProjectedObjectMarshaler(
                marshaler = PlatformManagedWeakReference(marshaler),
                value = PlatformManagedWeakReference(value),
            )
        } finally {
            winRTKeepAlive(marshaler)
            winRTKeepAlive(value)
        }
    }

    private fun awaitProjectedObjectMarshalerFinalization(
        host: FakeOwnedReferenceHost,
        abandoned: AbandonedProjectedObjectMarshaler,
        expectedReleaseCalls: Int,
    ) {
        repeat(20) {
            PlatformFinalization.drain()
            if (abandoned.marshaler.get() == null && abandoned.value.get() == null &&
                host.releaseCalls == expectedReleaseCalls
            ) {
                return
            }
            val pressure = List(128) { ByteArray(1024) }
            assertEquals(128, pressure.size)
        }
        assertNull(abandoned.marshaler.get())
        assertNull(abandoned.value.get())
        assertEquals(expectedReleaseCalls, host.releaseCalls)
    }

    private data class AbandonedProjectedObjectMarshaler(
        val marshaler: PlatformManagedWeakReference<WinRTObjectMarshaler>,
        val value: PlatformManagedWeakReference<ProjectedInspectableObject>,
    )

    private class ProjectedInspectableObject(
        override val nativeObject: ComObjectReference,
    ) : IWinRTObject

    private class FakeOwnedReferenceHost private constructor(
        private val scope: NativeScope,
        private val callbacks: List<NativeCallbackHandle>,
        private val pointer: RawAddress,
    ) : AutoCloseable {
        private val releaseCount = AtomicInt(0)
        private val ownedReferences = AtomicInt(0)
        private val agilityQueryCount = AtomicInt(0)

        val agilityQueries: Int
            get() = agilityQueryCount.load()

        val releaseCalls: Int
            get() = releaseCount.load()

        val ownedReferenceBalance: Int
            get() = ownedReferences.load()

        fun createReference(trackContext: Boolean = true): IInspectableReference {
            ownedReferences.addAndFetch(1)
            return IInspectableReference(ComPtr.create(
                raw = pointer.asRawComPtr(),
                interfaceId = IID.IInspectable,
                trackContext = trackContext,
            ))
        }

        override fun close() {
            // An unclosed parent or a leaked scoped QI keeps the ABI storage live on failure.
            assertEquals(0, ownedReferenceBalance)
            callbacks.asReversed().forEach(NativeCallbackHandle::close)
            scope.close()
        }

        companion object {
            fun create(supportAgileInspectable: Boolean = false, reportsAgile: Boolean = true): FakeOwnedReferenceHost {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: FakeOwnedReferenceHost
                val releaseCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer),
                ) {
                    val remaining = host.ownedReferences.addAndFetch(-1)
                    host.releaseCount.addAndFetch(1)
                    if (supportAgileInspectable) remaining else 1
                }
                val callbacks = mutableListOf(releaseCallback)
                val vtable = PlatformAbi.allocatePointerArray(scope, if (supportAgileInspectable) 6 else 3)
                PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.Release, releaseCallback.pointer)
                val pointer = PlatformAbi.allocatePointerSlot(scope).also {
                    PlatformAbi.writePointer(it, vtable)
                }
                if (supportAgileInspectable) {
                    val addRefCallback = ComAbiInteropBridge.createRawInt32Callback(
                        listOf(ComAbiValueKind.Pointer),
                    ) { host.ownedReferences.addAndFetch(1) }
                    val queryCallback = ComAbiInteropBridge.createRawInt32Callback(
                        listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                    ) { arguments ->
                        val requestedIid = PlatformAbi.readGuid(arguments[1] as RawAddress)
                        val resultOut = arguments[2] as RawAddress
                        if (requestedIid == IID.IAgileObject) host.agilityQueryCount.addAndFetch(1)
                        if (requestedIid == IID.IUnknown || requestedIid == IID.IInspectable ||
                            (requestedIid == IID.IAgileObject && reportsAgile)
                        ) {
                            host.ownedReferences.addAndFetch(1)
                            PlatformAbi.writePointer(resultOut, pointer)
                            KnownHResults.S_OK.value
                        } else {
                            PlatformAbi.writePointer(resultOut, PlatformAbi.nullPointer)
                            KnownHResults.E_NOINTERFACE.value
                        }
                    }
                    callbacks += addRefCallback
                    callbacks += queryCallback
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.AddRef, addRefCallback.pointer)
                    PlatformAbi.writePointerAt(vtable, IUnknownVftblSlots.QueryInterface, queryCallback.pointer)
                }
                host = FakeOwnedReferenceHost(scope, callbacks, pointer)
                return host
            }
        }
    }
}
