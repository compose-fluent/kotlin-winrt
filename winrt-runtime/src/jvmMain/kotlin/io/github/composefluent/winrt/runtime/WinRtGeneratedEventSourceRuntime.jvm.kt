package io.github.composefluent.winrt.runtime

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object WinRtGeneratedEventSourceRuntime {
    @JvmStatic
    fun createEventSourceFactory(descriptor: WinRtEventSourceDescriptor): WinRtEventSourceFactory? {
        if (descriptor.interfaceId == null || descriptor.parameterKinds.isEmpty()) {
            return null
        }
        return { objectReference, index ->
            GeneratedEventSource(
                descriptor = descriptor,
                objectReference = objectReference,
                vtableIndexForAddHandler = index,
            )
        }
    }
}

private class GeneratedEventSource(
    private val descriptor: WinRtEventSourceDescriptor,
    objectReference: ComObjectReference,
    vtableIndexForAddHandler: Int,
) : EventSource<Any>(objectReference, vtableIndexForAddHandler) {
    override fun createMarshaler(handler: Any): WinRtDelegateHandle =
        WinRtDelegateBridge.createDelegate(
            iid = requireNotNull(descriptor.interfaceId) {
                "Generated event source '${descriptor.eventType}' is missing an interface id."
            },
            parameterKinds = descriptor.parameterKinds,
            returnKind = descriptor.returnKind,
        ) { arguments ->
            invokeHandler(handler, projectArguments(arguments))
        }

    override fun createEventSourceState(): EventSourceState<Any> =
        object : EventSourceState<Any>(nativeObjectReference.pointer.asRawAddress(), eventIndex) {
            override fun createEventInvoke(): Any {
                val eventInterface = generatedEventInterface(descriptor.eventType)
                return Proxy.newProxyInstance(
                    eventInterface.classLoader,
                    arrayOf(eventInterface),
                    InvocationHandler { _, method, args ->
                        when {
                            method.name == "invoke" -> invokeSnapshotHandlers(args.orEmpty().toList())
                            method.name == "toString" && args.isNullOrEmpty() -> "GeneratedEventInvoke(${descriptor.eventType})"
                            method.name == "hashCode" && args.isNullOrEmpty() -> System.identityHashCode(this)
                            method.name == "equals" && args?.size == 1 -> false
                            else -> defaultReturnValue(descriptor.returnKind)
                        }
                    },
                )
            }

            private fun invokeSnapshotHandlers(arguments: List<Any?>): Any? {
                var result: Any? = defaultReturnValue(descriptor.returnKind)
                snapshotHandlers().forEach { handler ->
                    result = invokeHandler(handler, arguments)
                }
                return result
            }
        }

    private fun projectArguments(arguments: List<Any?>): List<Any?> =
        arguments.mapIndexed { index, value ->
            val kind = descriptor.parameterKinds.getOrNull(index)
            val typeName = descriptor.parameterTypeNames.getOrNull(index).orEmpty()
            projectArgument(kind, typeName, value)
        }

    private fun projectArgument(
        kind: WinRtDelegateValueKind?,
        typeName: String,
        value: Any?,
    ): Any? =
        when (kind) {
            WinRtDelegateValueKind.IINSPECTABLE -> projectInspectableArgument(value)
            WinRtDelegateValueKind.IUNKNOWN -> projectUnknownArgument(value)
            WinRtDelegateValueKind.OBJECT -> projectObjectArgument(value)
            else -> value
        }

    private fun projectUnknownArgument(value: Any?): Any? =
        when (value) {
            is IUnknownReference -> value.use { reference ->
                ComWrappersSupport.createRcwForComObject(reference.pointer.asRawAddress())
            }
            is ComObjectReference -> ComWrappersSupport.createRcwForComObject(value.pointer.asRawAddress())
            else -> value
        }

    private fun projectInspectableArgument(value: Any?): Any? =
        when (value) {
            is IInspectableReference -> value.use { reference ->
                ComWrappersSupport.createRcwForComObject(reference.pointer.asRawAddress())
            }
            is IUnknownReference -> value.asInspectable().use { reference ->
                ComWrappersSupport.createRcwForComObject(reference.pointer.asRawAddress())
            }
            is ComObjectReference -> value.tryAsInspectable()?.use { reference ->
                ComWrappersSupport.createRcwForComObject(reference.pointer.asRawAddress())
            } ?: value
            else -> value
        }

    private fun projectObjectArgument(value: Any?): Any? =
        when (value) {
            is IInspectableReference -> value.use { reference ->
                WinRtObjectMarshaller.fromAbi(reference.pointer.asRawAddress())
            }
            is IUnknownReference -> value.asInspectable().use { reference ->
                WinRtObjectMarshaller.fromAbi(reference.pointer.asRawAddress())
            }
            is ComObjectReference -> value.tryAsInspectable()?.use { reference ->
                WinRtObjectMarshaller.fromAbi(reference.pointer.asRawAddress())
            } ?: value
            is RawAddress -> WinRtObjectMarshaller.fromAbi(value)
            else -> value
        }
}

private fun generatedEventInterface(eventType: String): Class<*> {
    val rawTypeName = eventType.substringBefore('<')
    return Class.forName(projectedJvmClassName(rawTypeName))
}

private fun projectedJvmClassName(typeName: String): String {
    val packageName = typeName.substringBeforeLast('.', missingDelimiterValue = "")
    val className = typeName.substringAfterLast('.')
    if (packageName.isBlank()) {
        return className
    }
    return packageName.split('.').joinToString(".") { it.replaceFirstChar(Char::lowercase) } + ".$className"
}

private fun invokeHandler(
    handler: Any,
    arguments: List<Any?>,
): Any? {
    val method = handler.javaClass.methods.firstOrNull { method ->
        method.name == "invoke" && method.parameterCount == arguments.size
    } ?: throw WinRtUnsupportedOperationException(
        "Generated event handler '${handler.javaClass.name}' does not expose invoke/${arguments.size}.",
        KnownHResults.E_NOTIMPL,
    )
    return method.invoke(handler, *arguments.toTypedArray())
}

private fun defaultReturnValue(kind: WinRtDelegateValueKind): Any? =
    when (kind) {
        WinRtDelegateValueKind.UNIT -> Unit
        WinRtDelegateValueKind.BOOLEAN -> false
        WinRtDelegateValueKind.INT8 -> 0.toByte()
        WinRtDelegateValueKind.UINT8 -> 0.toUByte()
        WinRtDelegateValueKind.INT16 -> 0.toShort()
        WinRtDelegateValueKind.UINT16 -> 0.toUShort()
        WinRtDelegateValueKind.INT32 -> 0
        WinRtDelegateValueKind.UINT32 -> 0.toUInt()
        WinRtDelegateValueKind.INT64 -> 0L
        WinRtDelegateValueKind.UINT64 -> 0.toULong()
        WinRtDelegateValueKind.FLOAT -> 0f
        WinRtDelegateValueKind.DOUBLE -> 0.0
        WinRtDelegateValueKind.CHAR16 -> 0.toChar()
        WinRtDelegateValueKind.GUID -> Guid("00000000-0000-0000-0000-000000000000")
        WinRtDelegateValueKind.STRUCT,
        WinRtDelegateValueKind.HSTRING,
        WinRtDelegateValueKind.OBJECT,
        WinRtDelegateValueKind.IUNKNOWN,
        WinRtDelegateValueKind.IINSPECTABLE,
        -> null
    }
