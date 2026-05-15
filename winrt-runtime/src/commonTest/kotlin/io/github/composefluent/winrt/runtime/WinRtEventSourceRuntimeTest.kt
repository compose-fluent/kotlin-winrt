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

    @Test
    fun runtime_keeps_more_complete_event_source_descriptor_for_duplicate_keys() {
        val incomplete = WinRtEventSourceDescriptor(
            eventType = "Windows.Foundation.EventHandler<System.Object>",
            ownerType = "Sample.Foundation.IWidget",
            sourceClass = "EventHandlerEventSource",
            abiEventType = "Windows.Foundation.EventHandler<System.Object>",
            genericArguments = listOf("System.Object"),
            usesSharedEventHandlerSource = true,
        )
        val complete = incomplete.copy(
            interfaceId = Guid("c50898f6-c536-5f47-8583-8b2c2438a13b"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.OBJECT),
            returnKind = WinRtDelegateValueKind.UNIT,
            parameterTypeNames = listOf("Any", "System.Object"),
            eventSourceFactory = { _, _ -> error("not invoked") },
        )

        WinRtEventSourceRuntime.registerEventSource(complete)
        WinRtEventSourceRuntime.registerEventSource(incomplete)

        val registered = assertNotNull(
            WinRtEventSourceRuntime.descriptorFor(
                eventType = "Windows.Foundation.EventHandler<System.Object>",
                ownerType = "Sample.Foundation.IWidget",
            ),
        )
        assertEquals(complete.interfaceId, registered.interfaceId)
        assertEquals(complete.parameterKinds, registered.parameterKinds)
        assertNotNull(registered.eventSourceFactory)
    }
}
