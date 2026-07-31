package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class WinRTObjectReferenceCacheTest {
    @Test
    fun reference_is_created_once_and_reused() {
        var createCount = 0
        val reference = TestReference()
        val cache = WinRTObjectReferenceCache {
            createCount += 1
            reference
        }

        assertSame(reference, cache.value)
        assertSame(reference, cache.value)
        assertEquals(1, createCount)
        assertEquals(0, reference.closeCount)
    }

    @Test
    fun failed_initialization_is_retried() {
        var createCount = 0
        val reference = TestReference()
        val cache = WinRTObjectReferenceCache {
            createCount += 1
            if (createCount == 1) {
                error("first initialization failed")
            }
            reference
        }

        assertFailsWith<IllegalStateException> { cache.value }
        assertSame(reference, cache.value)
        assertEquals(2, createCount)
    }

    private class TestReference : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }
}
