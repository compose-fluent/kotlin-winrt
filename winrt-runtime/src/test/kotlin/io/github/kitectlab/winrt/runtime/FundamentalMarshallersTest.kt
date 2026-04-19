package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FundamentalMarshallersTest {
    @Test
    fun boolean_marshaller_round_trips() {
        Arena.ofConfined().use { arena ->
            val memory = arena.allocate(1)
            BooleanMarshaller.copyTo(true, memory)
            assertTrue(BooleanMarshaller.readFrom(memory))

            BooleanMarshaller.copyTo(false, memory)
            assertFalse(BooleanMarshaller.readFrom(memory))
        }
    }

    @Test
    fun char_marshaller_round_trips() {
        Arena.ofConfined().use { arena ->
            val memory = arena.allocate(2)
            CharMarshaller.copyTo('K', memory)
            assertEquals('K', CharMarshaller.readFrom(memory))
        }
    }

    @Test
    fun guid_marshaller_round_trips() {
        Arena.ofConfined().use { arena ->
            val memory = arena.allocate(16)
            val guid = Guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
            GuidMarshaller.copyTo(guid, memory)
            assertEquals(guid, GuidMarshaller.readFrom(memory))
        }
    }

    @Test
    fun referenced_hstring_round_trips_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        HString.createReference("kotlin-winrt").use { referenced ->
            assertEquals("kotlin-winrt", referenced.toKString())
        }
    }
}
