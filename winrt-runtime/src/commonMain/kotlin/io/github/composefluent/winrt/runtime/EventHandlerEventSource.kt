package io.github.composefluent.winrt.runtime

typealias EventHandlerCallback<TArgs> = (Any?, TArgs) -> Unit

/**
 * Kotlin event-handler helper corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventHandlerEventSource*.cs`.
 *
 * The CLR `System.EventHandler` / `System.EventHandler<T>` pair narrows here to the shared
 * Kotlin function type `(Any?, TArgs) -> Unit`, which still keeps the same sender/args shape
 * and event-source ownership model for generated projections.
 */
class EventHandlerEventSource<TArgs> : EventSource<EventHandlerCallback<TArgs>> {
    private val interfaceId: Guid
    private val argsKind: WinRTDelegateValueKind

    constructor(
        objectReference: ComObjectReference,
        interfaceId: Guid,
        argsKind: WinRTDelegateValueKind,
        vtableIndexForAddHandler: Int,
    ) : super(objectReference, vtableIndexForAddHandler) {
        this.interfaceId = interfaceId
        this.argsKind = argsKind
    }

    constructor(
        objectReference: ComObjectReference,
        interfaceId: Guid,
        argsKind: WinRTDelegateValueKind,
        addHandler: (ComObjectReference, ComObjectReference) -> EventRegistrationToken,
        removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
        index: Int = 0,
    ) : super(objectReference, addHandler, removeHandler, index) {
        this.interfaceId = interfaceId
        this.argsKind = argsKind
    }

    override fun createMarshaler(handler: EventHandlerCallback<TArgs>): WinRTDelegateHandle =
        WinRTDelegateBridge.createUnitDelegate(
            iid = interfaceId,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, argsKind),
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            handler(args[0], args[1] as TArgs)
        }

    override fun createEventSourceState(): EventSourceState<EventHandlerCallback<TArgs>> =
        object : EventSourceState<EventHandlerCallback<TArgs>>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): EventHandlerCallback<TArgs> =
                { sender, args ->
                    snapshotHandlers().forEach { handler -> handler(sender, args) }
                }
        }
}
