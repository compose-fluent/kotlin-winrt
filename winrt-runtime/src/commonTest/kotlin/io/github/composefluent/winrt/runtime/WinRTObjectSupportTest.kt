package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class WinRTObjectSupportTest {
    @Test
    fun competing_query_result_is_released_without_retaining_it_in_the_owner() {
        // CsWinRT keeps only the winning QI result in the RCW graph.
        val owner = TestOwner()
        val type = WinRTTypeHandle("test.IRaced", IID.IInspectable)
        val primary = TestReference(IID.IUnknown)
        val winner = TestReference(IID.IInspectable)
        val loser = TestReference(IID.IInspectable)
        var ownedCount = 0
        val support = WinRTObjectSupport<TestOwner, TestReference>(
            queryInterfaceCacheFor = { it.state.queryInterfaceCache },
            additionalTypeDataFor = { it.state.additionalTypeData },
            closeReference = TestReference::close,
            validateReference = { check(it.closeCount == 0) },
            ownReference = { _, reference -> ownedCount++; reference },
        )
        val result = support.getObjectReferenceForType(
            owner, null, type, primary,
            tryQueryInterface = {
                owner.state.queryInterfaceCache[type] = winner
                loser
            },
            missingInterfaceError = ::missingInterfaceError,
        )
        assertSame(winner, result)
        assertEquals(1, loser.closeCount)
        assertEquals(0, ownedCount)
    }

    private val support = WinRTObjectSupport<TestOwner, TestReference>(
        queryInterfaceCacheFor = { it.state.queryInterfaceCache },
        additionalTypeDataFor = { it.state.additionalTypeData },
        closeReference = TestReference::close,
        validateReference = { check(it.closeCount == 0) },
        ownReference = { _, reference -> reference },
    )

    @Test
    fun caches_query_interface_results_by_type_handle() {
        val requestedInterface = Guid("12345678-1234-1234-1234-1234567890AB")
        val typeHandle = WinRTTypeHandle("test.IFoo", requestedInterface)
        val queriedReference = TestReference(requestedInterface)
        val nativeReference = TestReference(
            IID.IInspectable,
            queryResults = mapOf(requestedInterface to queriedReference),
        )
        val owner = TestOwner()

        assertTrue(
            support.isInterfaceImplemented(
                instance = owner,
                primaryTypeHandle = null,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                throwIfNotImplemented = false,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            ),
        )
        assertTrue(
            support.isInterfaceImplemented(
                instance = owner,
                primaryTypeHandle = null,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                throwIfNotImplemented = false,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            ),
        )

        assertSame(
            queriedReference,
            support.getObjectReferenceForType(
                instance = owner,
                primaryTypeHandle = null,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            ),
        )
        assertEquals(1, nativeReference.queryCount(requestedInterface))
    }

    @Test
    fun reports_missing_interface_without_caching_false_result() {
        val requestedInterface = Guid("12345678-1234-1234-1234-1234567890AC")
        val typeHandle = WinRTTypeHandle("test.IMissing", requestedInterface)
        val nativeReference = TestReference(IID.IInspectable)
        val owner = TestOwner()

        assertFalse(
            support.isInterfaceImplemented(
                instance = owner,
                primaryTypeHandle = null,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                throwIfNotImplemented = false,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            ),
        )
        assertEquals(1, nativeReference.queryCount(requestedInterface))

        try {
            support.getObjectReferenceForType(
                instance = owner,
                primaryTypeHandle = null,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            )
            fail("Expected missing interface lookup to fail")
        } catch (error: WinRTUnsupportedOperationException) {
            assertEquals(KnownHResults.E_NOINTERFACE, error.hResult)
        }

        assertEquals(2, nativeReference.queryCount(requestedInterface))
    }

    @Test
    fun additional_type_data_is_stable_per_instance() {
        val typeHandle = WinRTTypeHandle("test.IHelper", Guid("12345678-1234-1234-1234-1234567890AD"))
        val nativeReference = TestReference(IID.IInspectable)
        val owner = TestOwner()
        var factoryCalls = 0

        val first = support.getOrAddAdditionalTypeData(owner, typeHandle) {
            factoryCalls += 1
            mutableListOf("first")
        }
        val second = support.getOrAddAdditionalTypeData(owner, typeHandle) {
            factoryCalls += 1
            mutableListOf("second")
        }

        assertSame(first, second)
        assertEquals(1, factoryCalls)
        assertTrue(support.additionalTypeData(owner).containsKey(typeHandle))
        nativeReference.close()
    }

    @Test
    fun primary_type_handle_short_circuits_query_interface_lookup() {
        val primaryInterface = Guid("12345678-1234-1234-1234-1234567890AE")
        val typeHandle = WinRTTypeHandle("test.IPrimary", primaryInterface)
        val nativeReference = TestReference(primaryInterface)
        val owner = TestOwner()

        assertTrue(
            support.isInterfaceImplemented(
                instance = owner,
                primaryTypeHandle = typeHandle,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                throwIfNotImplemented = true,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            ),
        )
        assertSame(
            nativeReference,
            support.getObjectReferenceForType(
                instance = owner,
                primaryTypeHandle = typeHandle,
                interfaceType = typeHandle,
                nativeObject = nativeReference,
                tryQueryInterface = nativeReference::tryQueryInterface,
                missingInterfaceError = ::missingInterfaceError,
            ),
        )
        assertEquals(0, nativeReference.queryCount(primaryInterface))
    }
}

private class TestOwner {
    val state = WinRTObjectState<TestReference>()
}

private class TestReference(
    val interfaceId: Guid,
    private val queryResults: Map<Guid, TestReference> = emptyMap(),
) : AutoCloseable {
    private val queryCounts = mutableMapOf<Guid, Int>()
    var closeCount: Int = 0
        private set

    fun tryQueryInterface(requestedInterfaceId: Guid): TestReference? {
        queryCounts[requestedInterfaceId] = (queryCounts[requestedInterfaceId] ?: 0) + 1
        return queryResults[requestedInterfaceId]
    }

    fun queryCount(requestedInterfaceId: Guid): Int =
        queryCounts[requestedInterfaceId] ?: 0

    override fun close() {
        closeCount += 1
    }
}

private fun missingInterfaceError(interfaceType: WinRTTypeHandle): Throwable =
    WinRTUnsupportedOperationException(
        "Interface '${interfaceType.projectedTypeName}' is not implemented.",
        KnownHResults.E_NOINTERFACE,
    )
