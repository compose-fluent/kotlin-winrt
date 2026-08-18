package io.github.composefluent.winrt.runtime

import windows.foundation.EventHandler
import windows.foundation.TypedEventHandler

/**
 * Adapts the frontend's SAM value to the nominal WinRT delegate declaration.
 *
 * The compiler plugin passes the closed descriptor assembled from the exact IR
 * type arguments. Keeping the descriptor here preserves the common runtime path
 * while avoiding a generic CCW for every event accessor call.
 */
fun <TArgs> adaptWinRTEventHandler(
    callback: Any?,
    descriptor: WinRTDelegateDescriptor,
): EventHandler<TArgs> =
    when (callback) {
        is EventHandler<*> -> {
            @Suppress("UNCHECKED_CAST")
            EventHandlerValueAdapter(callback as EventHandler<TArgs>, descriptor)
        }
        is Function2<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            FunctionEventHandlerValueAdapter(callback as (Any?, TArgs) -> Unit, descriptor)
        }
        else -> error("Unsupported EventHandler callback value: ${callback?.let { it::class }}")
    }

/** Adapts a closed `TypedEventHandler<TSender, TResult>` SAM value. */
fun <TSender, TResult> adaptWinRTTypedEventHandler(
    callback: Any?,
    descriptor: WinRTDelegateDescriptor,
): TypedEventHandler<TSender, TResult> =
    when (callback) {
        is TypedEventHandler<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            TypedEventHandlerValueAdapter(callback as TypedEventHandler<TSender, TResult>, descriptor)
        }
        is Function2<*, *, *> -> {
            @Suppress("UNCHECKED_CAST")
            FunctionTypedEventHandlerValueAdapter(callback as (TSender, TResult) -> Unit, descriptor)
        }
        else -> error("Unsupported TypedEventHandler callback value: ${callback?.let { it::class }}")
    }

private abstract class AdaptedEventHandler<TArgs>(
    private val descriptor: WinRTDelegateDescriptor,
) : EventHandler<TArgs> {
    override fun createWinRTDelegateHandle(): WinRTDelegateHandle =
        WinRTDelegateBridge.createDelegate(descriptor) { abiArgs ->
            @Suppress("UNCHECKED_CAST")
            invoke(
                abiArgs.getOrNull(0),
                abiArgs.getOrNull(1) as TArgs,
            )
            Unit
        }
}

private class EventHandlerValueAdapter<TArgs>(
    private val callback: EventHandler<TArgs>,
    descriptor: WinRTDelegateDescriptor,
) : AdaptedEventHandler<TArgs>(descriptor) {
    override fun invoke(sender: Any?, args: TArgs) {
        callback.invoke(sender, args)
    }
}

private class FunctionEventHandlerValueAdapter<TArgs>(
    private val callback: (Any?, TArgs) -> Unit,
    descriptor: WinRTDelegateDescriptor,
) : AdaptedEventHandler<TArgs>(descriptor) {
    override fun invoke(sender: Any?, args: TArgs) {
        callback(sender, args)
    }
}

private abstract class AdaptedTypedEventHandler<TSender, TResult>(
    private val descriptor: WinRTDelegateDescriptor,
) : TypedEventHandler<TSender, TResult> {
    override fun createWinRTDelegateHandle(): WinRTDelegateHandle =
        WinRTDelegateBridge.createDelegate(descriptor) { abiArgs ->
            @Suppress("UNCHECKED_CAST")
            invoke(
                abiArgs.getOrNull(0) as TSender,
                abiArgs.getOrNull(1) as TResult,
            )
            Unit
        }
}

private class TypedEventHandlerValueAdapter<TSender, TResult>(
    private val callback: TypedEventHandler<TSender, TResult>,
    descriptor: WinRTDelegateDescriptor,
) : AdaptedTypedEventHandler<TSender, TResult>(descriptor) {
    override fun invoke(sender: TSender, args: TResult) {
        callback.invoke(sender, args)
    }
}

private class FunctionTypedEventHandlerValueAdapter<TSender, TResult>(
    private val callback: (TSender, TResult) -> Unit,
    descriptor: WinRTDelegateDescriptor,
) : AdaptedTypedEventHandler<TSender, TResult>(descriptor) {
    override fun invoke(sender: TSender, args: TResult) {
        callback(sender, args)
    }
}
