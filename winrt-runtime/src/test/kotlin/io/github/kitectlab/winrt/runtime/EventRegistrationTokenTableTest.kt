package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRegistrationTokenTableTest {
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

    private fun upper32Bits(token: EventRegistrationToken): Int =
        (token.value.toULong() shr 32).toInt()

    private data class TestHandler(
        val name: String,
    )

    private data class OtherHandler(
        val id: Int = 0,
    )
}
