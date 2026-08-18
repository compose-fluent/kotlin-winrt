package io.github.composefluent.winrt.runtime

import windows.foundation.EventRegistrationToken
import windows.foundation.EventHandler

typealias EventHandlerCallback<TArgs> = (Any?, TArgs) -> Unit

/**
 * Kotlin event-handler helper corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventHandlerEventSource*.cs`.
 *
 * The CLR `System.EventHandler` / `System.EventHandler<T>` pair narrows here to the shared
 * Kotlin function type `(Any?, TArgs) -> Unit`, which still keeps the same sender/args shape
 * and event-source ownership model for generated projections.
 */
class EventHandlerEventSource<TArgs> : EventSource<EventHandler<TArgs>> {
    private val interfaceId: Guid
    private val argsKind: WinRTDelegateValueKind
    private val abiEntryPoint: RawAddress?
    private val delegateDescriptor: WinRTDelegateDescriptor

    constructor(
        objectReference: ComObjectReference,
        interfaceId: Guid,
        argsKind: WinRTDelegateValueKind,
        vtableIndexForAddHandler: Int,
        abiEntryPoint: RawAddress? = null,
        delegateDescriptor: WinRTDelegateDescriptor? = null,
    ) : super(objectReference, vtableIndexForAddHandler) {
        this.interfaceId = interfaceId
        this.argsKind = argsKind
        this.abiEntryPoint = abiEntryPoint
        this.delegateDescriptor = delegateDescriptor ?: closedDelegateDescriptor(interfaceId, argsKind)
    }

    constructor(
        objectReference: ComObjectReference,
        interfaceId: Guid,
        argsKind: WinRTDelegateValueKind,
        addHandler: (ComObjectReference, ComObjectReference) -> EventRegistrationToken,
        removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
        index: Int = 0,
        abiEntryPoint: RawAddress? = null,
    ) : super(objectReference, addHandler, removeHandler, index) {
        this.interfaceId = interfaceId
        this.argsKind = argsKind
        this.abiEntryPoint = abiEntryPoint
        this.delegateDescriptor = closedDelegateDescriptor(interfaceId, argsKind)
    }

    override fun createMarshaler(handler: EventHandler<TArgs>): WinRTDelegateHandle {
        abiEntryPoint?.let { entryPoint ->
            return WinRTDelegateBridge.createUnitDelegateStatic(
                descriptor = delegateDescriptor,
                managedTarget = handler,
                abiEntryPoint = entryPoint,
            )
        }
        val argumentKind = argsKind
        return WinRTDelegateBridge.createUnitDelegateRaw(
            descriptor = delegateDescriptor,
            callback = { args -> handler(args[0], args[1] as TArgs) },
            rawWordCallback = ComRawWordCallback { senderWord, argsWord, _, _, _, _, _ ->
                @Suppress("UNCHECKED_CAST")
                handler.invoke(
                    WinRTDelegateAbiMarshaller.decodeRawWordArgument(WinRTDelegateValueKind.OBJECT, senderWord),
                    WinRTDelegateAbiMarshaller.decodeRawWordArgument(argumentKind, argsWord) as TArgs,
                )
                KnownHResults.S_OK.value
            },
        )
    }

    override fun createEventSourceState(): EventSourceState<EventHandler<TArgs>> =
        object : EventSourceState<EventHandler<TArgs>>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): EventHandler<TArgs> =
                EventHandler { sender, args ->
                    forEachHandler { handler -> handler(sender, args) }
                }
        }

    private fun closedDelegateDescriptor(
        interfaceId: Guid,
        argsKind: WinRTDelegateValueKind,
    ): WinRTDelegateDescriptor =
        WinRTDelegateDescriptor(
            interfaceId = interfaceId,
            parameterKinds = listOf(WinRTDelegateValueKind.OBJECT, argsKind),
            returnKind = WinRTDelegateValueKind.UNIT,
        )
}
