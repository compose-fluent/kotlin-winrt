package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IWinRTObjectTest {
    @Test
    fun cached_interfaces_follow_owner_close_but_independent_queries_do_not() {
        // CsWinRT code_writers.h/write_class_objrefs_definition keeps lazy QI
        // references in the RCW graph. Kotlin deterministic close must cover the
        // generic IWinRTObject cache just as it covers generated interface fields.
        val cachedIid = Guid("12345678-1234-1234-1234-1234567890B0")
        val independentIid = Guid("12345678-1234-1234-1234-1234567890B1")
        val cached = FakeComObjectReference(cachedIid)
        val independent = FakeComObjectReference(independentIid)
        val owner = FakeComObjectReference(IID.IInspectable, mapOf(cachedIid to cached, independentIid to independent))
        val wrapper = FakeWinRTObject(owner)
        val type = WinRTTypeHandle("test.ICached", cachedIid)
        try {
            assertSame(cached, wrapper.getObjectReferenceForType(type))
            assertSame(independent, owner.tryQueryInterface(independentIid))
            owner.close()
            assertTrue(cached.isDisposed)
            assertFalse(independent.isDisposed)
            assertFailsWith<WinRTObjectDisposedException> { wrapper.getObjectReferenceForType(type) }
            assertFailsWith<WinRTObjectDisposedException> { wrapper.isInterfaceImplemented(type) }
        } finally {
            owner.close()
            independent.close()
        }
    }

    @Test
    fun primary_interface_fast_path_rejects_closed_owner() {
        val type = WinRTTypeHandle("test.IPrimary", IID.IInspectable)
        val owner = FakeComObjectReference(IID.IInspectable)
        val wrapper = FakeWinRTObject(owner, type)
        owner.close()
        assertFailsWith<WinRTObjectDisposedException> { wrapper.getObjectReferenceForType(type) }
        assertFailsWith<WinRTObjectDisposedException> { wrapper.isInterfaceImplemented(type) }
    }

    @Test
    fun caches_query_interface_results_by_type_handle() {
        val requestedInterface = Guid("12345678-1234-1234-1234-1234567890AB")
        val typeHandle = WinRTTypeHandle("test.IFoo", requestedInterface)
        val queriedReference = FakeComObjectReference(requestedInterface)
        val nativeReference = FakeComObjectReference(
            IID.IInspectable,
            mapOf(requestedInterface to queriedReference),
        )
        val instance = FakeWinRTObject(nativeReference)

        try {
            assertTrue(instance.isInterfaceImplemented(typeHandle, throwIfNotImplemented = false))
            assertTrue(instance.isInterfaceImplemented(typeHandle, throwIfNotImplemented = false))
            assertSame(queriedReference, instance.getObjectReferenceForType(typeHandle))
            assertEquals(1, nativeReference.queryCount(requestedInterface))
        } finally {
            queriedReference.close()
            nativeReference.close()
        }
    }

    @Test
    fun reports_missing_interface_without_caching_false_result() {
        val requestedInterface = Guid("12345678-1234-1234-1234-1234567890AC")
        val typeHandle = WinRTTypeHandle("test.IMissing", requestedInterface)
        val nativeReference = FakeComObjectReference(IID.IInspectable)
        val instance = FakeWinRTObject(nativeReference)

        try {
            assertFalse(instance.isInterfaceImplemented(typeHandle, throwIfNotImplemented = false))
            assertEquals(1, nativeReference.queryCount(requestedInterface))

            try {
                instance.getObjectReferenceForType(typeHandle)
                throw AssertionError("Expected missing interface lookup to fail")
            } catch (error: WinRTUnsupportedOperationException) {
                assertEquals(KnownHResults.E_NOINTERFACE, error.hResult)
            }

            assertEquals(2, nativeReference.queryCount(requestedInterface))
        } finally {
            nativeReference.close()
        }
    }

    @Test
    fun additional_type_data_is_stable_per_instance() {
        val typeHandle = WinRTTypeHandle("test.IHelper", Guid("12345678-1234-1234-1234-1234567890AD"))
        val nativeReference = FakeComObjectReference(IID.IInspectable)
        val instance = FakeWinRTObject(nativeReference)
        var factoryCalls = 0

        try {
            val first = instance.getOrAddAdditionalTypeData(typeHandle) {
                factoryCalls += 1
                mutableListOf("first")
            }
            val second = instance.getOrAddAdditionalTypeData(typeHandle) {
                factoryCalls += 1
                mutableListOf("second")
            }

            assertSame(first, second)
            assertEquals(1, factoryCalls)
        } finally {
            nativeReference.close()
        }
    }

    @Test
    fun primary_type_handle_short_circuits_query_interface_lookup() {
        val primaryInterface = Guid("12345678-1234-1234-1234-1234567890AE")
        val typeHandle = WinRTTypeHandle("test.IPrimary", primaryInterface)
        val nativeReference = FakeComObjectReference(primaryInterface)
        val instance = FakeWinRTObject(nativeReference, primaryTypeHandle = typeHandle)

        try {
            assertTrue(instance.isInterfaceImplemented(typeHandle, throwIfNotImplemented = true))
            assertSame(nativeReference, instance.getObjectReferenceForType(typeHandle))
            assertEquals(0, nativeReference.queryCount(primaryInterface))
        } finally {
            nativeReference.close()
        }
    }

    @Test
    fun runtime_class_primary_type_queries_inspectable_for_default_interface() {
        // CsWinRT MarshalInspectable<T>.CreateMarshaler2 calls objRef.AsValue(iid).
        // XAML connector targets start as IInspectable, not the class's default ABI.
        val defaultIid = Guid("12345678-1234-1234-1234-1234567890AF")
        val typeHandle = WinRTTypeHandle("test.XamlControl", defaultIid)
        val defaultReference = FakeComObjectReference(defaultIid)
        val inspectable = FakeComObjectReference(IID.IInspectable, mapOf(defaultIid to defaultReference))
        val wrapper = FakeWinRTObject(inspectable, primaryTypeHandle = typeHandle)
        try {
            assertTrue(wrapper.isInterfaceImplemented(typeHandle))
            assertSame(defaultReference, wrapper.getObjectReferenceForType(typeHandle))
            assertSame(defaultReference, wrapper.getObjectReferenceForType(typeHandle))
            assertEquals(1, inspectable.queryCount(defaultIid))
        } finally {
            defaultReference.close()
            inspectable.close()
        }
    }
}

private class FakeWinRTObject(
    override val nativeObject: ComObjectReference,
    override val primaryTypeHandle: WinRTTypeHandle? = null,
    override val hasUnwrappableNativeObject: Boolean = true,
) : IWinRTObject

private class FakeComObjectReference(
    interfaceId: Guid,
    private val queryResults: Map<Guid, ComObjectReference> = emptyMap(),
) : ComObjectReference(
        ComPtr.create(
            raw = allocateFakeComPointer(),
            interfaceId = interfaceId,
            ownershipMode = ComOwnershipMode.Borrowed,
            trackContext = false,
        ),
    ) {
    private val queryCounts = mutableMapOf<Guid, Int>()

    override fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? {
        queryCounts[requestedInterfaceId] = queryCount(requestedInterfaceId) + 1
        return queryResults[requestedInterfaceId]
    }

    fun queryCount(requestedInterfaceId: Guid): Int =
        queryCounts[requestedInterfaceId] ?: 0
}

private var nextFakeComPointer = 1L

private fun allocateFakeComPointer(): RawComPtr =
    RawComPtr(nextFakeComPointer++)
