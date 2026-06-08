package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtDispatcherQueueSchedulerTest {
    @Test
    fun scheduler_reuses_single_handler_for_multiple_actions() {
        var handlerCreateCount = 0
        val enqueuedHandlers = mutableListOf<() -> Unit>()
        val scheduler = WinRtDispatcherQueueScheduler<() -> Unit>(
            tryEnqueueHandler = { handler ->
                enqueuedHandlers += handler
                true
            },
            handlerFactory = { drain ->
                handlerCreateCount += 1
                drain
            },
        )
        val events = mutableListOf<String>()

        assertTrue(scheduler.enqueue { events += "first" })
        assertTrue(scheduler.enqueue { events += "second" })

        assertEquals(1, handlerCreateCount)
        assertEquals(1, enqueuedHandlers.size)
        enqueuedHandlers.single().invoke()
        assertEquals(listOf("first", "second"), events)
        assertEquals(0, scheduler.pendingActionCountForTesting())
    }

    @Test
    fun scheduler_reschedules_after_drain_completes() {
        val enqueuedHandlers = mutableListOf<() -> Unit>()
        val scheduler = WinRtDispatcherQueueScheduler<() -> Unit>(
            tryEnqueueHandler = { handler ->
                enqueuedHandlers += handler
                true
            },
            handlerFactory = { drain -> drain },
        )
        val events = mutableListOf<String>()

        assertTrue(scheduler.enqueue { events += "first" })
        enqueuedHandlers.removeAt(0).invoke()
        assertTrue(scheduler.enqueue { events += "second" })
        enqueuedHandlers.removeAt(0).invoke()

        assertEquals(listOf("first", "second"), events)
        assertEquals(0, scheduler.pendingActionCountForTesting())
    }

    @Test
    fun scheduler_drains_actions_added_while_handler_is_running() {
        val enqueuedHandlers = mutableListOf<() -> Unit>()
        lateinit var scheduler: WinRtDispatcherQueueScheduler<() -> Unit>
        val events = mutableListOf<String>()
        scheduler = WinRtDispatcherQueueScheduler(
            tryEnqueueHandler = { handler ->
                enqueuedHandlers += handler
                true
            },
            handlerFactory = { drain -> drain },
        )

        assertTrue(
            scheduler.enqueue {
                events += "first"
                scheduler.enqueue { events += "second" }
            },
        )
        enqueuedHandlers.single().invoke()

        assertEquals(listOf("first", "second"), events)
        assertEquals(1, enqueuedHandlers.size)
        assertEquals(0, scheduler.pendingActionCountForTesting())
    }

    @Test
    fun scheduler_rolls_back_action_when_initial_enqueue_fails() {
        val scheduler = WinRtDispatcherQueueScheduler<() -> Unit>(
            tryEnqueueHandler = { false },
            handlerFactory = { drain -> drain },
        )

        assertFalse(scheduler.enqueue { error("must not run") })
        assertEquals(0, scheduler.pendingActionCountForTesting())
    }
}
