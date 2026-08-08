@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WinRTObjectReferenceCacheTest {
    @Test
    fun reference_is_created_once_and_reused() {
        var createCount = 0
        val reference = TestReference()
        val slot = AtomicReference<TestReference?>(null)
        fun getReference(): TestReference = getOrCreateWinRTObjectReference(slot) {
            createCount += 1
            reference
        }

        assertSame(reference, getReference())
        assertSame(reference, getReference())
        assertEquals(1, createCount)
        assertEquals(0, reference.closeCount)
    }

    @Test
    fun failed_initialization_is_retried() {
        var createCount = 0
        val reference = TestReference()
        val slot = AtomicReference<TestReference?>(null)
        fun getReference(): TestReference = getOrCreateWinRTObjectReference(slot) {
            createCount += 1
            if (createCount == 1) {
                error("first initialization failed")
            }
            reference
        }

        assertFailsWith<IllegalStateException> { getReference() }
        assertSame(reference, getReference())
        assertEquals(2, createCount)
    }

    @Test
    fun generated_field_publication_reuses_winner_and_closes_loser() {
        var cached: TestReference? = null
        val winner = TestReference()
        val loser = TestReference()

        assertSame(
            winner,
            publishGeneratedWinRTObjectReference(winner, { cached }, { cached = it }),
        )
        assertSame(
            winner,
            publishGeneratedWinRTObjectReference(loser, { cached }, { cached = it }),
        )
        assertSame(winner, cached)
        assertEquals(0, winner.closeCount)
        assertEquals(1, loser.closeCount)
    }

    @Test
    fun generated_value_publication_reuses_winner() {
        var cached: Any? = null
        val winner = Any()
        val loser = Any()

        assertSame(
            winner,
            publishGeneratedWinRTValue(winner, { cached }, { cached = it }),
        )
        assertSame(
            winner,
            publishGeneratedWinRTValue(loser, { cached }, { cached = it }),
        )
        assertSame(winner, cached)
    }

    private class TestReference : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }
}
