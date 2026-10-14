package io.github.composefluent.winrt.runtime

import microsoft.ui.xaml.data.PropertyChangedEventHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class WinUIXamlPropertyChangedEventTest {
    @Test
    fun removal_and_reentrant_subscription_preserve_the_multicast_snapshot() {
        // CSharpPagePass1.tt invokes one multicast PropertyChangedEventHandler;
        // removing a duplicate or changing subscriptions must not alter that call.
        val event = WinUIXamlPropertyChangedEvent()
        val other = WinUIXamlPropertyChangedEvent()
        val sender = Any()
        val calls = mutableListOf<String>()
        val duplicate = PropertyChangedEventHandler { actualSender, args ->
            assertSame(sender, actualSender)
            calls += requireNotNull(args?.propertyName)
        }
        val changing = PropertyChangedEventHandler { _, _ -> event.remove(duplicate) }
        event.add(duplicate)
        event.add(changing)
        event.add(duplicate)
        event.raise(sender, "first")
        assertEquals(listOf("first", "first"), calls)
        event.raise(sender, "second")
        assertEquals(listOf("first", "first", "second"), calls)
        other.raise(sender, "unrelated")
        event.raise(sender, "third")
        assertEquals(listOf("first", "first", "second"), calls)
    }
}
