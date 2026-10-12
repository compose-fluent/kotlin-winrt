package io.github.composefluent.winrt.runtime

import java.lang.ref.Reference
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertNull
import windows.foundation.EventRegistrationToken

class WinRTEventLifetimeTest {
    @Test
    fun removing_token_releases_handler_while_event_remains_alive() {
        // CsWinRT Interop/EventSource<TDelegate>.Unsubscribe removes the strong delegate.
        val event = WinRTEvent<Any>({ EventRegistrationToken(1L) }, {})
        val handler = subscribeAndRemove(event)
        repeat(50) {
            if (handler.get() == null) return@repeat
            System.gc()
            Thread.sleep(10)
        }
        assertNull(handler.get())
        Reference.reachabilityFence(event)
    }

    private fun subscribeAndRemove(event: WinRTEvent<Any>): WeakReference<Any> {
        val handler = Any()
        val reference = WeakReference(handler)
        event.remove(event.add(handler))
        return reference
    }
}
