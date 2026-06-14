package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IWinRTObjectTest {
    @Test
    fun caches_query_interface_results_by_type_handle() {
        val requestedInterface = Guid("12345678-1234-1234-1234-1234567890AB")
        val typeHandle = WinRtTypeHandle("test.IFoo", requestedInterface)
        val queriedReference = FakeComObjectReference(requestedInterface)
        val nativeReference = FakeComObjectReference(
            IID.IInspectable,
            mapOf(requestedInterface to queriedReference),
        )
        val instance = FakeWinRtObject(nativeReference)

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
        val typeHandle = WinRtTypeHandle("test.IMissing", requestedInterface)
        val nativeReference = FakeComObjectReference(IID.IInspectable)
        val instance = FakeWinRtObject(nativeReference)

        try {
            assertFalse(instance.isInterfaceImplemented(typeHandle, throwIfNotImplemented = false))
            assertEquals(1, nativeReference.queryCount(requestedInterface))

            try {
                instance.getObjectReferenceForType(typeHandle)
                throw AssertionError("Expected missing interface lookup to fail")
            } catch (error: WinRtUnsupportedOperationException) {
                assertEquals(KnownHResults.E_NOINTERFACE, error.hResult)
            }

            assertEquals(2, nativeReference.queryCount(requestedInterface))
        } finally {
            nativeReference.close()
        }
    }

    @Test
    fun additional_type_data_is_stable_per_instance() {
        val typeHandle = WinRtTypeHandle("test.IHelper", Guid("12345678-1234-1234-1234-1234567890AD"))
        val nativeReference = FakeComObjectReference(IID.IInspectable)
        val instance = FakeWinRtObject(nativeReference)
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
        val typeHandle = WinRtTypeHandle("test.IPrimary", primaryInterface)
        val nativeReference = FakeComObjectReference(primaryInterface)
        val instance = FakeWinRtObject(nativeReference, primaryTypeHandle = typeHandle)

        try {
            assertTrue(instance.isInterfaceImplemented(typeHandle, throwIfNotImplemented = true))
            assertSame(nativeReference, instance.getObjectReferenceForType(typeHandle))
            assertEquals(0, nativeReference.queryCount(primaryInterface))
        } finally {
            nativeReference.close()
        }
    }
}

private class FakeWinRtObject(
    override val nativeObject: ComObjectReference,
    override val primaryTypeHandle: WinRtTypeHandle? = null,
    override val hasUnwrappableNativeObject: Boolean = true,
) : IWinRTObject

private class FakeComObjectReference(
    interfaceId: Guid,
    private val queryResults: Map<Guid, ComObjectReference> = emptyMap(),
) : ComObjectReference(pointer = allocateFakeComPointer(), interfaceId = interfaceId, preventReleaseOnDispose = true) {
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
