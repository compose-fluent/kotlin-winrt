package io.github.composefluent.winrt.runtime

typealias WinRtEventSourceFactory = (ComObjectReference, Int) -> EventSource<*>

data class WinRtEventSourceDescriptor(
    val eventType: String,
    val ownerType: String,
    val sourceClass: String,
    val abiEventType: String,
    val genericArguments: List<String>,
    val usesSharedEventHandlerSource: Boolean = false,
    val interfaceId: Guid? = null,
    val parameterKinds: List<WinRtDelegateValueKind> = emptyList(),
    val returnKind: WinRtDelegateValueKind = WinRtDelegateValueKind.UNIT,
    val parameterTypeNames: List<String> = emptyList(),
    val eventSourceFactory: WinRtEventSourceFactory? = null,
)

object WinRtEventSourceRuntime {
    private val descriptorsByKey = ConcurrentCacheMap<String, WinRtEventSourceDescriptor>()

    fun registerEventSource(descriptor: WinRtEventSourceDescriptor) {
        descriptorsByKey[eventSourceKey(descriptor.eventType, descriptor.ownerType)] = descriptor.copy(
            genericArguments = descriptor.genericArguments.distinct(),
        )
    }

    fun descriptorFor(
        eventType: String,
        ownerType: String,
    ): WinRtEventSourceDescriptor? =
        descriptorsByKey[eventSourceKey(eventType, ownerType)]

    fun descriptorsForEventType(eventType: String): List<WinRtEventSourceDescriptor> =
        descriptorsByKey.values
            .filter { descriptor -> descriptor.eventType == eventType }
            .sortedWith(compareBy(WinRtEventSourceDescriptor::ownerType, WinRtEventSourceDescriptor::sourceClass))

    fun descriptorsForOwnerType(ownerType: String): List<WinRtEventSourceDescriptor> =
        descriptorsByKey.values
            .filter { descriptor -> descriptor.ownerType == ownerType }
            .sortedWith(compareBy(WinRtEventSourceDescriptor::eventType, WinRtEventSourceDescriptor::sourceClass))

    fun createEventSource(
        eventType: String,
        ownerType: String,
        objectReference: ComObjectReference,
        vtableIndexForAddHandler: Int,
    ): EventSource<*>? =
        descriptorFor(eventType, ownerType)
            ?.eventSourceFactory
            ?.invoke(objectReference, vtableIndexForAddHandler)

    internal fun clearForTests() {
        descriptorsByKey.clear()
    }

    private fun eventSourceKey(
        eventType: String,
        ownerType: String,
    ): String = "$eventType->$ownerType"
}
