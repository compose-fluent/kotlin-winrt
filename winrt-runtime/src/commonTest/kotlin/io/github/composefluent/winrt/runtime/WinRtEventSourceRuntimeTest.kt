package io.github.composefluent.winrt.runtime

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WinRtEventSourceRuntimeTest {
    @AfterTest
    fun tearDown() {
        WinRtEventSourceRuntime.clearForTests()
    }

    @Test
    fun runtime_registers_event_source_descriptors_by_event_and_owner() {
        val descriptor = WinRtEventSourceDescriptor(
            eventType = "Windows.Foundation.EventHandler<Int>",
            ownerType = "Sample.Foundation.IWidget",
            sourceClass = "_EventSource_Windows_Foundation_EventHandler_Int",
            abiEventType = "Windows.Foundation.EventHandler<Int>",
            genericArguments = listOf("Int", "Int"),
            usesSharedEventHandlerSource = true,
            eventSourceFactory = { _, _ -> error("not invoked") },
        )

        WinRtEventSourceRuntime.registerEventSource(descriptor)

        val registered = assertNotNull(
            WinRtEventSourceRuntime.descriptorFor(
                eventType = "Windows.Foundation.EventHandler<Int>",
                ownerType = "Sample.Foundation.IWidget",
            ),
        )
        assertEquals(listOf("Int"), registered.genericArguments)
        assertEquals(true, registered.usesSharedEventHandlerSource)
        assertNotNull(registered.eventSourceFactory)
        assertEquals(listOf(registered), WinRtEventSourceRuntime.descriptorsForEventType("Windows.Foundation.EventHandler<Int>"))
        assertEquals(listOf(registered), WinRtEventSourceRuntime.descriptorsForOwnerType("Sample.Foundation.IWidget"))
    }
}
