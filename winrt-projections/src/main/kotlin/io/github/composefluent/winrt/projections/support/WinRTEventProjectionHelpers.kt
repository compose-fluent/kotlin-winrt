package io.github.composefluent.winrt.projections.support

// Deterministic generator handoff for .cswinrt WinRTEventProjectionHelpers writer parity.

internal data class EventSourceEntry(
    val eventType: String,
    val ownerType: String,
    val sourceClass: String,
    val abiEventType: String,
    val genericArguments: List<String>,
)

internal object WinRTEventProjectionHelpers {
    val EVENT_SOURCES: List<EventSourceEntry> = listOf(
        EventSourceEntry(
            eventType = "Windows.Foundation.Collections.MapChangedEventHandler<String, String>",
            ownerType = "Windows.Foundation.Collections.StringMap",
            sourceClass = "_EventSource_Windows_Foundation_Collections_MapChangedEventHandler_String__String_",
            abiEventType = "Windows.Foundation.Collections.MapChangedEventHandler<IntPtr, IntPtr>",
            genericArguments = listOf("String", "String"),
        ),
        EventSourceEntry(
            eventType = "Windows.Foundation.Collections.MapChangedEventHandler<String, System.Object>",
            ownerType = "Windows.Foundation.Collections.PropertySet",
            sourceClass = "_EventSource_Windows_Foundation_Collections_MapChangedEventHandler_String__System_Object_",
            abiEventType = "Windows.Foundation.Collections.MapChangedEventHandler<IntPtr, System.Object>",
            genericArguments = listOf("String", "System.Object"),
        ),
        EventSourceEntry(
            eventType = "Windows.Foundation.Collections.MapChangedEventHandler<String, System.Object>",
            ownerType = "Windows.Foundation.Collections.ValueSet",
            sourceClass = "_EventSource_Windows_Foundation_Collections_MapChangedEventHandler_String__System_Object_",
            abiEventType = "Windows.Foundation.Collections.MapChangedEventHandler<IntPtr, System.Object>",
            genericArguments = listOf("String", "System.Object"),
        ),
        EventSourceEntry(
            eventType = "Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>",
            ownerType = "Windows.Foundation.Collections.IObservableMap",
            sourceClass = "_EventSource_Windows_Foundation_Collections_MapChangedEventHandler_T0__T1_",
            abiEventType = "Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>",
            genericArguments = listOf("T0", "T1"),
        ),
        EventSourceEntry(
            eventType = "Windows.Foundation.Collections.VectorChangedEventHandler<T0>",
            ownerType = "Windows.Foundation.Collections.IObservableVector",
            sourceClass = "_EventSource_Windows_Foundation_Collections_VectorChangedEventHandler_T0_",
            abiEventType = "Windows.Foundation.Collections.VectorChangedEventHandler<T0>",
            genericArguments = listOf("T0"),
        ),
        EventSourceEntry(
            eventType = "Windows.Foundation.TypedEventHandler<Windows.Foundation.IMemoryBufferReference, System.Object>",
            ownerType = "Windows.Foundation.IMemoryBufferReference",
            sourceClass = "_EventSource_Windows_Foundation_TypedEventHandler_Windows_Foundation_IMemoryBufferReference__System_Object_",
            abiEventType = "Windows.Foundation.TypedEventHandler<Windows.Foundation.IMemoryBufferReference, System.Object>",
            genericArguments = listOf("Windows.Foundation.IMemoryBufferReference", "System.Object"),
        ),
    )
    val EVENT_SOURCE_MAPPING_KEYS: List<String> = listOf("Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>->_EventSource_Windows_Foundation_Collections_MapChangedEventHandler_T0__T1_", "Windows.Foundation.Collections.VectorChangedEventHandler<T0>->_EventSource_Windows_Foundation_Collections_VectorChangedEventHandler_T0_", "Windows.Foundation.TypedEventHandler<Windows.Foundation.IMemoryBufferReference, System.Object>->_EventSource_Windows_Foundation_TypedEventHandler_Windows_Foundation_IMemoryBufferReference__System_Object_")
    val EVENT_SOURCES_BY_EVENT_TYPE: Map<String, List<EventSourceEntry>> = EVENT_SOURCES.groupBy { it.eventType }
    val EVENT_SOURCES_BY_OWNER_TYPE: Map<String, List<EventSourceEntry>> = EVENT_SOURCES.groupBy { it.ownerType }

    fun sourcesForEventType(eventType: String): List<EventSourceEntry> =
        EVENT_SOURCES_BY_EVENT_TYPE[eventType].orEmpty()

    fun sourcesForOwnerType(ownerType: String): List<EventSourceEntry> =
        EVENT_SOURCES_BY_OWNER_TYPE[ownerType].orEmpty()

    fun installEventSources(install: (EventSourceEntry) -> Unit) {
        EVENT_SOURCES.forEach(install)
    }
}
