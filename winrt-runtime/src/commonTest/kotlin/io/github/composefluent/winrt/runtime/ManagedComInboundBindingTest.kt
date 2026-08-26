package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class ManagedComInboundBindingTest {
    @Test
    fun hot_entry_tracks_the_exact_interface_pointer_and_is_cleared_on_close() {
        val primaryInterfaceId = Guid("dbb1bb41-c88c-4011-8ed6-2e90560942c7")
        val secondaryInterfaceId = Guid("465c1f08-b859-4186-8e43-c649318dc742")
        val managedValue = Any()
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(primaryInterfaceId, methods = emptyList()),
                WinRTInspectableInterfaceDefinition(secondaryInterfaceId, methods = emptyList()),
            ),
            defaultInterfaceId = primaryInterfaceId,
            managedValue = managedValue,
        )
        lateinit var binding: ManagedComInboundBinding

        try {
            val primaryPointer = host.borrowCachedInterfacePointer(primaryInterfaceId)
            val secondaryPointer = host.borrowCachedInterfacePointer(secondaryInterfaceId)

            binding = checkNotNull(winRTProjectionInboundBinding(primaryPointer.value))
            assertSame(managedValue, winRTProjectionInboundManagedValue(primaryPointer.value))
            assertEquals(primaryPointer.value, managedComInboundHotEntry.load()?.thisWord)
            assertSame(binding, managedComInboundHotEntry.load()?.binding)

            assertSame(binding, winRTProjectionInboundBinding(secondaryPointer.value))
            assertEquals(secondaryPointer.value, managedComInboundHotEntry.load()?.thisWord)
            assertSame(binding, managedComInboundHotEntry.load()?.binding)
        } finally {
            host.close()
        }

        assertTrue(managedComInboundHotEntry.load()?.binding !== binding)
    }

    @Test
    fun close_invalidates_by_binding_identity_when_a_pointer_key_is_reused() {
        val pointerWord = 0x1234_5678L
        val firstHost = WinRTInspectableComObject.inspectableBox("first")
        val secondHost = WinRTInspectableComObject.inspectableBox("second")
        val firstBinding = checkNotNull(
            winRTProjectionInboundBinding(firstHost.borrowCachedInterfacePointer(IID.IInspectable).value),
        )
        val secondBinding = checkNotNull(
            winRTProjectionInboundBinding(secondHost.borrowCachedInterfacePointer(IID.IInspectable).value),
        )

        try {
            assertTrue(firstBinding.publishHotEntry(pointerWord))
            assertTrue(secondBinding.publishHotEntry(pointerWord))
            firstHost.close()

            assertSame(secondBinding, managedComInboundHotEntry.load()?.binding)
            assertEquals(pointerWord, managedComInboundHotEntry.load()?.thisWord)
        } finally {
            firstHost.close()
            secondHost.close()
        }

        assertTrue(managedComInboundHotEntry.load()?.binding !== secondBinding)
    }

    @Test
    fun hot_binding_does_not_retain_a_weak_managed_value() {
        val fixture = createWeakHotBindingFixture()

        try {
            repeat(10) {
                PlatformFinalization.drain()
                if (fixture.value.get() == null) {
                    return@repeat
                }
                val pressure = List(128) { ByteArray(1024) }
                assertEquals(128, pressure.size)
            }
            assertNull(fixture.value.get())
        } finally {
            fixture.host.close()
        }
    }

    private fun createWeakHotBindingFixture(): WeakHotBindingFixture {
        val managedValue = Any()
        val value = PlatformManagedWeakReference(managedValue)
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(IID.IInspectable, methods = emptyList()),
            ),
            defaultInterfaceId = IID.IInspectable,
            managedValue = managedValue,
            weakManagedValue = true,
        )
        val pointer = host.borrowCachedInterfacePointer(IID.IInspectable)
        assertSame(managedValue, winRTProjectionInboundManagedValue(pointer.value))
        return WeakHotBindingFixture(host, value)
    }

    private data class WeakHotBindingFixture(
        val host: WinRTInspectableComObject,
        val value: PlatformManagedWeakReference<Any>,
    )
}
