package io.github.kitectlab.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class AbiReferenceLeaseTest {
    @Test
    fun closesOwnedReferenceBeforeCleanup() {
        val events = mutableListOf<String>()
        val owned =
            object : AutoCloseable {
                override fun close() {
                    events += "owned"
                }
            }

        AbiReferenceLeaseSupport.create(
            abi = PlatformAbi.nullPointer,
            ownedReference = owned,
            cleanup = { events += "cleanup" },
        ).close()

        assertEquals(listOf("owned", "cleanup"), events)
    }

    @Test
    fun cleanupRunsWhenOwnedCloseThrows() {
        val events = mutableListOf<String>()
        val owned =
            object : AutoCloseable {
                override fun close() {
                    events += "owned"
                    error("boom")
                }
            }

        runCatching {
            AbiReferenceLeaseSupport.create(
                abi = PlatformAbi.nullPointer,
                ownedReference = owned,
                cleanup = { events += "cleanup" },
            ).close()
        }

        assertEquals(listOf("owned", "cleanup"), events)
    }
}
