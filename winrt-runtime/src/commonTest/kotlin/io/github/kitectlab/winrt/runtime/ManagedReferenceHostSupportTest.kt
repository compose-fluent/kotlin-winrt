package io.github.kitectlab.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagedReferenceHostSupportTest {
    @Test
    fun detachReferenceReleasesManagedReferenceImmediately() {
        val events = mutableListOf<String>()

        ManagedReferenceHostSupport.detachReference(
            createReference = {
                events += "create"
                PlatformAbi.nullPointer
            },
            releaseManagedReference = { events += "cleanup" },
        )

        assertEquals(listOf("create", "cleanup"), events)
    }

    @Test
    fun createLeaseClosesReferenceBeforeManagedCleanup() {
        val events = mutableListOf<String>()
        val lease = ManagedReferenceHostSupport.createLease(
            createReference = {
                events += "create"
                object : AutoCloseable {
                    override fun close() {
                        events += "owned"
                    }
                }
            },
            releaseManagedReference = { events += "cleanup" },
            abiOf = { PlatformAbi.nullPointer },
        )

        lease.close()

        assertEquals(listOf("create", "owned", "cleanup"), events)
    }

    @Test
    fun wrapOwnedReferenceCleansUpWhenWrapperCreationFails() {
        val events = mutableListOf<String>()

        runCatching {
            ManagedReferenceHostSupport.wrapOwnedReference(
                createReference = {
                    events += "create"
                    object : AutoCloseable {
                        override fun close() {
                            events += "owned"
                        }
                    }
                },
                releaseManagedReference = { events += "cleanup" },
            ) { _, _ ->
                error("boom")
            }
        }

        assertEquals(listOf("create", "owned", "cleanup"), events)
    }

    @Test
    fun wrapOwnedReferenceDefersCleanupUntilReturnedOwnerCloses() {
        val events = mutableListOf<String>()
        val wrapped = ManagedReferenceHostSupport.wrapOwnedReference(
            createReference = {
                events += "create"
                object : AutoCloseable {
                    override fun close() {
                        events += "owned"
                    }
                }
            },
            releaseManagedReference = { events += "cleanup" },
        ) { inner, cleanup ->
            object : AutoCloseable {
                override fun close() {
                    try {
                        inner.close()
                    } finally {
                        cleanup()
                    }
                }
            }
        }

        assertEquals(listOf("create"), events)
        wrapped.close()
        assertEquals(listOf("create", "owned", "cleanup"), events)
        assertTrue(events.last() == "cleanup")
    }
}
