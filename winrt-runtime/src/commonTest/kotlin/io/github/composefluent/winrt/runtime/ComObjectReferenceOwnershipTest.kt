package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComObjectReferenceOwnershipTest {
    // CsWinRT IInspectable.net5.cs/IWinRTObject.net5.cs retain lazy QI references on the RCW.
    // Kotlin's explicit owner close must release that graph without waiting for GC.
    @Test
    fun parent_close_releases_lazy_interfaces_and_tolerates_already_closed_children() {
        var cleanupCount = 0
        val parent = reference { cleanupCount++ }
        val first = acquireInterfaceReference(parent, IID.IInspectable)
        val second = acquireInterfaceReference(parent, IID.IInspectable)
        first.close()
        assertEquals(0, cleanupCount)

        parent.close()
        parent.close()
        second.close()

        assertTrue(first.isDisposed)
        assertTrue(second.isDisposed)
        assertEquals(1, cleanupCount)
    }

    @Test
    fun independent_query_reference_survives_parent_close() {
        var cleanupCount = 0
        val parent = reference { cleanupCount++ }
        val independent = parent.queryInterface(IID.IInspectable).getOrThrow()

        parent.close()
        assertEquals(0, cleanupCount)
        independent.close()
        assertEquals(1, cleanupCount)
    }

    @Test
    fun registration_after_parent_close_releases_the_incoming_reference() {
        var parentCleanup = 0
        var childCleanup = 0
        val parent = reference { parentCleanup++ }
        val child = reference { childCleanup++ }
        parent.close()

        assertFailsWith<WinRTObjectDisposedException> { parent.ownInterfaceReference(child) }
        assertTrue(child.isDisposed)
        assertEquals(1, parentCleanup)
        assertEquals(1, childCleanup)
    }

    @Test
    fun child_close_failure_still_releases_other_children_and_parent() {
        var cleanupCount = 0
        val parent = reference { cleanupCount++ }
        val firstFailure = IllegalStateException("first child")
        val secondFailure = IllegalStateException("second child")
        val first = parent.ownInterfaceReference(failingReference(firstFailure))
        val second = parent.ownInterfaceReference(failingReference(secondFailure))
        val last = acquireInterfaceReference(parent, IID.IInspectable)

        val actual = assertFailsWith<IllegalStateException> { parent.close() }
        assertSame(firstFailure, actual)
        assertEquals(listOf(secondFailure), actual.suppressedExceptions)
        assertTrue(first.isDisposed)
        assertTrue(second.isDisposed)
        assertTrue(last.isDisposed)
        assertTrue(parent.isDisposed)
        assertEquals(1, cleanupCount)
        parent.close()
    }

    private fun reference(cleanup: () -> Unit): IInspectableReference {
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(IID.IInspectable, emptyList())),
            defaultInterfaceId = IID.IInspectable,
            runtimeClassName = "test.InterfaceOwnership",
            cleanupAction = cleanup,
        )
        return IInspectableReference(host.detachReference(IID.IInspectable).asRawComPtr())
    }

    private fun failingReference(failure: Throwable): ComObjectReference {
        val inner = reference {}
        return object : ComObjectReference(inner.comPtr) {
            override fun close() {
                super.close()
                throw failure
            }
        }
    }
}
