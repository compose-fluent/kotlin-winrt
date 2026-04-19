package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
        val factoryCalls = AtomicInteger(0)

        try {
            val first = instance.getOrAddAdditionalTypeData(typeHandle) {
                factoryCalls.incrementAndGet()
                mutableListOf("first")
            }
            val second = instance.getOrAddAdditionalTypeData(typeHandle) {
                factoryCalls.incrementAndGet()
                mutableListOf("second")
            }

            assertSame(first, second)
            assertEquals(1, factoryCalls.get())
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
) : ComObjectReference(pointer = allocatePointer(), interfaceId = interfaceId, preventReleaseOnDispose = true) {
    private val queryCounts = ConcurrentHashMap<Guid, AtomicInteger>()

    override fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? {
        queryCounts.computeIfAbsent(requestedInterfaceId) { AtomicInteger(0) }.incrementAndGet()
        return queryResults[requestedInterfaceId]
    }

    fun queryCount(requestedInterfaceId: Guid): Int =
        queryCounts[requestedInterfaceId]?.get() ?: 0
}

private fun allocatePointer(): MemorySegment =
    Arena.ofAuto().allocate(8)
