package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ManagedComQueryInterfaceTableTest {
    @Test
    fun shared_type_entries_resolve_each_instance_and_survive_another_instances_release() {
        // CsWinRT 3 ComputeVtables shares ComInterfaceEntry metadata, while ComWrappers
        // resolves each instance's identity. Exercise the actual native QI stub on both targets.
        val firstId = Guid("8C8DD861-00A8-4864-A7D9-8BD0521A2CF1")
        val secondId = Guid("8C8DD861-00A8-4864-A7D9-8BD0521A2CF2")
        val definition = WinRTCcwDefinition(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(firstId, emptyList()),
                WinRTInspectableInterfaceDefinition(secondId, emptyList()),
            ),
            defaultInterfaceId = firstId,
        )
        fun host() = WinRTInspectableComObject(
            interfaceDefinitions = definition.interfaceDefinitions,
            defaultInterfaceId = firstId,
            shapeCacheKey = definition,
        )
        // A definition's first use keeps entries inline; subsequent uses share type storage.
        host().use { initial ->
            initial.createReference(firstId).use { reference ->
                val table = PlatformAbi.readPointerAt(reference.pointer.asRawAddress(), managedComQueryInterfaceTableSlot)
                assertEquals(
                    RawAddress(table.value + managedComQueryInterfaceTableHeaderWordCount * Long.SIZE_BYTES),
                    PlatformAbi.readPointerAt(table, managedComQueryInterfaceTableEntriesWord),
                )
            }
        }
        val first = host()
        val second = host()
        try {
            first.createReference(firstId).use { firstReference ->
                second.createReference(firstId).use { secondReference ->
                    fun table(reference: ComObjectReference): RawAddress = PlatformAbi.readPointerAt(
                        reference.pointer.asRawAddress(), managedComQueryInterfaceTableSlot,
                    )
                    val firstTable = table(firstReference)
                    val secondTable = table(secondReference)
                    assertNotEquals(firstTable, secondTable)
                    assertEquals(
                        PlatformAbi.readPointerAt(firstTable, managedComQueryInterfaceTableEntriesWord),
                        PlatformAbi.readPointerAt(secondTable, managedComQueryInterfaceTableEntriesWord),
                    )
                    for ((reference, owner) in listOf(firstReference to first, secondReference to second)) {
                        reference.queryInterface(secondId).getOrThrow().use { queried ->
                            assertEquals(owner.borrowCachedInterfacePointer(secondId), queried.pointer.asRawAddress())
                            queried.queryInterface(IID.IUnknown).getOrThrow().use { identity ->
                                assertEquals(reference.pointer, identity.pointer)
                            }
                        }
                        assertEquals(2u, WinRTInspectableComObject.tryProbeReferenceCount(reference.pointer.asRawAddress()))
                    }
                }
            }
            first.close()
            PlatformFinalization.drain()
            second.createReference(firstId).use { reference ->
                reference.queryInterface(secondId).getOrThrow().use { queried ->
                    assertEquals(second.borrowCachedInterfacePointer(secondId), queried.pointer.asRawAddress())
                }
                assertTrue(reference.queryInterface(Guid("8C8DD861-00A8-4864-A7D9-8BD0521A2CFF")).isFailure)
            }
        } finally {
            first.close()
            second.close()
        }
    }
}
