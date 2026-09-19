package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import windows.foundation.EventRegistrationToken

@OptIn(ExperimentalAtomicApi::class)
class EventRegistrationTokenTableTest {
    @Test
    fun token_type_identity_matches_the_runtime_class_across_table_instances() {
        // CsWinRT EventRegistrationTokenTable<T>.TypeOfTHashCode uses typeof(T), not its name.
        val expectedHash = TestHandler::class.hashCode().let { if (it == 0) 0x5FC74196 else it }
        val first = EventRegistrationTokenTable.create<TestHandler>()
        val second = EventRegistrationTokenTable.create(TestHandler::class)
        assertEquals(expectedHash, upper32Bits(first.addEventHandler(TestHandler("first"))))
        assertEquals(expectedHash, upper32Bits(second.addEventHandler(TestHandler("second"))))
    }

    @Test
    fun empty_table_defers_handler_snapshot_until_first_registration() {
        val table = EventRegistrationTokenTable.create<TestHandler>()

        assertNull(table.handlerSnapshot)

        table.addEventHandler(TestHandler("first"))

        assertTrue(table.handlerSnapshot != null)
    }

    @Test
    fun zero_type_hash_keeps_token_upper_bits_nonzero() {
        // CsWinRT GetTypeOfTHashCode reserves non-zero high bits even when the hash is zero.
        val table = EventRegistrationTokenTable.create<TestHandler>("")
        val handler = TestHandler("zero hash")
        val token = table.addEventHandler(handler)
        assertEquals(0x5FC74196, upper32Bits(token))
        assertSame(handler, table.removeEventHandler(token))
    }

    @Test
    fun concurrent_first_registrations_preserve_unique_tokens_and_handlers() = runBlocking {
        // CsWinRT EventRegistrationTokenTable<T> serializes token updates with its monitor.
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val handler = TestHandler("concurrent")
        val start = CompletableDeferred<Unit>()
        val workers = List(4) {
            async(Dispatchers.Default) {
                start.await()
                List(64) { table.addEventHandler(handler) }
            }
        }
        start.complete(Unit)
        val tokens = workers.awaitAll().flatten()
        assertEquals(256, tokens.toSet().size)
        var handlerCount = 0
        table.forEachHandler {
            assertSame(handler, it)
            handlerCount++
        }
        assertEquals(256, handlerCount)
        tokens.forEach { assertSame(handler, table.removeEventHandler(it)) }
        table.forEachHandler { error("Removed handler remains in the event snapshot") }
    }

    @Test
    fun event_registration_token_uses_registered_winrt_struct_mapping() {
        ComWrappersSupport.clearRegistriesForTests()

        assertEquals("Windows.Foundation.EventRegistrationToken", TypeNameSupport.getNameForType(EventRegistrationToken::class))
        assertEquals("struct(Windows.Foundation.EventRegistrationToken;i8)", GuidGenerator.getSignature(EventRegistrationToken::class))
        assertEquals(EventRegistrationToken::class, TypeExtensions.findHelperType(EventRegistrationToken::class))
        assertEquals("Windows.Foundation.EventRegistrationToken", Projections.findCustomAbiTypeNameForType(EventRegistrationToken::class))
        assertTrue(Projections.isTypeWindowsRuntimeType(EventRegistrationToken::class))
    }

    @Test
    fun event_registration_token_table_assigns_unique_tokens_for_repeated_registrations() {
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val handler = TestHandler("alpha")

        val first = table.addEventHandler(handler)
        val second = table.addEventHandler(handler)

        assertNotEquals(EventRegistrationToken(), first)
        assertNotEquals(first, second)
        assertEquals(upper32Bits(first), upper32Bits(second))
        assertSame(handler, table.removeEventHandler(first))
        assertSame(handler, table.removeEventHandler(second))
        assertNull(table.removeEventHandler(first))
    }

    @Test
    fun event_registration_token_table_returns_default_token_for_null_handlers() {
        val table = EventRegistrationTokenTable.create<TestHandler>()

        assertEquals(EventRegistrationToken(), table.addEventHandler(null))
    }

    @Test
    fun event_registration_token_table_rejects_tokens_from_other_delegate_types() {
        val handlerTable = EventRegistrationTokenTable.create<TestHandler>()
        val otherTable = EventRegistrationTokenTable.create<OtherHandler>()
        val token = handlerTable.addEventHandler(TestHandler("alpha"))

        assertNull(otherTable.removeEventHandler(token))
        assertNotEquals(0, upper32Bits(token))
    }

    @Test
    fun event_registration_token_table_invokes_an_update_time_snapshot_in_registration_order() {
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val calls = mutableListOf<String>()
        val first = TestHandler("first")
        val second = TestHandler("second")

        table.addEventHandler(first)
        table.addEventHandler(second)
        table.forEachHandler { handler -> calls += handler.name }

        assertEquals(listOf("first", "second"), calls)

        table.removeEventHandler(EventRegistrationToken())
        calls.clear()
        table.forEachHandler { handler -> calls += handler.name }
        assertEquals(listOf("first", "second"), calls)

        table.removeEventHandler(table.addEventHandler(first))
        calls.clear()
        table.forEachHandler { handler -> calls += handler.name }
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun event_registration_token_table_uses_the_single_handler_snapshot() {
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val handler = TestHandler("single")
        val calls = mutableListOf<String>()

        table.addEventHandler(handler)
        val snapshot = requireNotNull(table.handlerSnapshot).load()
        table.forEachHandler { calls += it.name }

        assertSame(handler, snapshot.singleHandler)
        assertNull(snapshot.manyHandlers)
        assertEquals(listOf("single"), calls)
    }

    @Test
    fun duplicate_registrations_are_invoked_and_removed_independently() {
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val handler = TestHandler("duplicate")
        val firstToken = table.addEventHandler(handler)
        val secondToken = table.addEventHandler(handler)

        val initialCalls = mutableListOf<String>()
        table.forEachHandler { initialCalls += it.name }
        assertEquals(listOf("duplicate", "duplicate"), initialCalls)

        assertSame(handler, table.removeEventHandler(firstToken))
        val remainingCalls = mutableListOf<String>()
        table.forEachHandler { remainingCalls += it.name }
        assertEquals(listOf("duplicate"), remainingCalls)

        assertSame(handler, table.removeEventHandler(secondToken))
        var invoked = false
        table.forEachHandler { invoked = true }
        assertFalse(invoked)
    }

    @Test
    fun mutation_during_invoke_only_changes_the_next_snapshot() {
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val first = TestHandler("first")
        val second = TestHandler("second")
        val third = TestHandler("third")
        table.addEventHandler(first)
        val secondToken = table.addEventHandler(second)
        val firstCalls = mutableListOf<String>()

        table.forEachHandler { handler ->
            firstCalls += handler.name
            if (handler === first) {
                assertSame(second, table.removeEventHandler(secondToken))
                table.addEventHandler(third)
            }
        }

        assertEquals(listOf("first", "second"), firstCalls)
        val nextCalls = mutableListOf<String>()
        table.forEachHandler { nextCalls += it.name }
        assertEquals(listOf("first", "third"), nextCalls)
    }

    @Test
    fun removing_handlers_publishes_a_snapshot_without_their_references() {
        val table = EventRegistrationTokenTable.create<TestHandler>()
        val first = TestHandler("first")
        val second = TestHandler("second")
        val firstToken = table.addEventHandler(first)
        val secondToken = table.addEventHandler(second)

        assertSame(first, table.removeEventHandler(firstToken))
        val remainingSnapshot = requireNotNull(table.handlerSnapshot).load()
        assertSame(second, remainingSnapshot.singleHandler)
        assertNull(remainingSnapshot.manyHandlers)

        assertSame(second, table.removeEventHandler(secondToken))
        val emptySnapshot = requireNotNull(table.handlerSnapshot).load()
        assertNull(emptySnapshot.singleHandler)
        assertNull(emptySnapshot.manyHandlers)
    }

    private fun upper32Bits(token: EventRegistrationToken): Int =
        (token.value.toULong() shr 32).toInt()

    private data class TestHandler(
        val name: String,
    )

    private data class OtherHandler(
        val id: Int = 0,
    )
}
