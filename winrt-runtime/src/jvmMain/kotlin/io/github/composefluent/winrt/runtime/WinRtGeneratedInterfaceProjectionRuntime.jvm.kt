package io.github.composefluent.winrt.runtime

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

enum class GeneratedInterfaceProjectionMemberKind {
    Method,
    PropertyGet,
    PropertySet,
    EventGet,
    EventAdd,
    EventRemove,
}

enum class GeneratedInterfaceProjectionValueKind {
    Unit,
    Boolean,
    Int8,
    UInt8,
    Int16,
    UInt16,
    Int32,
    UInt32,
    Int64,
    UInt64,
    Float,
    Double,
    Char16,
    Guid,
    String,
    Object,
    UnknownReference,
    InspectableReference,
    Unsupported,
}

data class GeneratedInterfaceProjectionMemberDescriptor(
    val kind: GeneratedInterfaceProjectionMemberKind,
    val jvmName: String,
    val slot: Int,
    val returnKind: GeneratedInterfaceProjectionValueKind,
    val parameterKinds: List<GeneratedInterfaceProjectionValueKind>,
    val suppressHResultCheck: Boolean,
    val eventTypeName: String = "",
    val ownerTypeName: String = "",
)

object WinRtGeneratedInterfaceProjectionRuntime {
    @JvmStatic
    fun create(
        interfaceClass: Class<*>,
        typeHandle: WinRtTypeHandle,
        nativeObject: IUnknownReference,
        members: List<GeneratedInterfaceProjectionMemberDescriptor>,
    ): Any {
        val handler = GeneratedInterfaceProjectionInvocationHandler(typeHandle, nativeObject, members)
        return Proxy.newProxyInstance(
            interfaceClass.classLoader,
            arrayOf(interfaceClass, IWinRTObject::class.java),
            handler,
        )
    }
}

private class GeneratedInterfaceProjectionInvocationHandler(
    private val typeHandle: WinRtTypeHandle,
    private val nativeObject: IUnknownReference,
    members: List<GeneratedInterfaceProjectionMemberDescriptor>,
) : InvocationHandler {
    private val membersByJvmShape = members.associateBy { it.jvmName to it.parameterKinds.size }
    private val winRtObject = SingleInterfaceOptimizedObject(typeHandle, nativeObject)
    private val eventCache = ConcurrentCacheMap<String, WinRtEvent<Any>>()

    override fun invoke(
        proxy: Any,
        method: Method,
        args: Array<out Any?>?,
    ): Any? {
        val argumentValues = args.orEmpty()
        when (method.name) {
            "getNativeObject" -> return nativeObject
            "getPrimaryTypeHandle" -> return typeHandle
            "getHasUnwrappableNativeObject" -> return true
            "getQueryInterfaceCache" -> return winRtObject.queryInterfaceCache
            "getAdditionalTypeData" -> return winRtObject.additionalTypeData
            "isInterfaceImplemented" -> return winRtObject.isInterfaceImplemented(
                argumentValues[0] as WinRtTypeHandle,
                argumentValues.getOrNull(1) as? Boolean ?: false,
            )
            "getObjectReferenceForType" -> return winRtObject.getObjectReferenceForType(argumentValues[0] as WinRtTypeHandle)
            "getOrAddAdditionalTypeData" -> return winRtObject.getOrAddAdditionalTypeData(
                argumentValues[0] as WinRtTypeHandle,
                argumentValues[1] as () -> Any,
            )
            "toString" -> if (argumentValues.isEmpty()) return "GeneratedInterfaceProjection(${typeHandle.projectedTypeName})"
            "hashCode" -> if (argumentValues.isEmpty()) return System.identityHashCode(proxy)
            "equals" -> if (argumentValues.size == 1) return proxy === argumentValues[0]
        }
        val descriptor = membersByJvmShape[method.name to argumentValues.size]
            ?: throw WinRtUnsupportedOperationException(
                "Generated interface projection member '${method.name}' is not registered for '${typeHandle.projectedTypeName}'.",
                KnownHResults.E_NOTIMPL,
            )
        if (descriptor.kind == GeneratedInterfaceProjectionMemberKind.EventGet) {
            return eventFor(descriptor)
        }
        if (descriptor.kind == GeneratedInterfaceProjectionMemberKind.EventAdd) {
            return eventFor(descriptor).add(argumentValues[0] as Any)
        }
        if (descriptor.kind == GeneratedInterfaceProjectionMemberKind.EventRemove) {
            eventFor(descriptor).remove(argumentValues[0] as EventRegistrationToken)
            return Unit
        }
        return invokeAbi(descriptor, argumentValues)
    }

    @Suppress("UNCHECKED_CAST")
    private fun eventFor(descriptor: GeneratedInterfaceProjectionMemberDescriptor): WinRtEvent<Any> {
        val eventType = descriptor.eventTypeName.takeIf(String::isNotBlank)
            ?: throw WinRtUnsupportedOperationException("Generated event descriptor is missing an event type.", KnownHResults.E_NOTIMPL)
        val ownerType = descriptor.ownerTypeName.takeIf(String::isNotBlank)
            ?: throw WinRtUnsupportedOperationException("Generated event descriptor is missing an owner type.", KnownHResults.E_NOTIMPL)
        return eventCache.computeIfAbsent(descriptor.jvmName) {
            val source = WinRtEventSourceRuntime.createEventSource(
                eventType = eventType,
                ownerType = ownerType,
                objectReference = nativeObject,
                vtableIndexForAddHandler = descriptor.slot,
            ) ?: throw WinRtUnsupportedOperationException(
                "Event source '$eventType' for '$ownerType' is not registered.",
                KnownHResults.E_NOTIMPL,
            )
            WinRtEvent(source as EventSource<Any>)
        }
    }

    private fun invokeAbi(
        descriptor: GeneratedInterfaceProjectionMemberDescriptor,
        args: Array<out Any?>,
    ): Any? {
        if (descriptor.returnKind == GeneratedInterfaceProjectionValueKind.Unsupported ||
            descriptor.parameterKinds.any { it == GeneratedInterfaceProjectionValueKind.Unsupported }
        ) {
            throw WinRtUnsupportedOperationException(
                "Generated interface projection member '${descriptor.jvmName}' on '${typeHandle.projectedTypeName}' is not supported by the JVM artifact runtime.",
                KnownHResults.E_NOTIMPL,
            )
        }
        if (descriptor.returnKind == GeneratedInterfaceProjectionValueKind.Unit && descriptor.parameterKinds.isEmpty()) {
            val hResult = ComVtableInvoker.invoke(nativeObject.pointer, descriptor.slot)
            if (!descriptor.suppressHResultCheck) {
                HResult(hResult).requireSuccess()
            }
            return Unit
        }
        PlatformAbi.confinedScope().use { scope ->
            val ownedInputs = mutableListOf<AutoCloseable>()
            try {
                val encodedArgs = LongArray(descriptor.parameterKinds.size + if (descriptor.returnKind == GeneratedInterfaceProjectionValueKind.Unit) 0 else 1)
                val explicitKinds = mutableListOf<ComAbiValueKind>()
                descriptor.parameterKinds.forEachIndexed { index, kind ->
                    explicitKinds += kind.comAbiKind()
                    encodedArgs[index] = encodeArgument(kind, args[index], ownedInputs)
                }
                val resultOut = if (descriptor.returnKind == GeneratedInterfaceProjectionValueKind.Unit) {
                    PlatformAbi.nullPointer
                } else {
                    allocateResultSlot(scope, descriptor.returnKind).also { slot ->
                        explicitKinds += ComAbiValueKind.Pointer
                        encodedArgs[encodedArgs.lastIndex] = PlatformAbi.pointerKey(slot)
                    }
                }
                val hResult = nativeObject.comPtr.invokeGeneric(
                    slot = descriptor.slot,
                    signature = ComMethodSignature(explicitKinds),
                    args = encodedArgs,
                )
                if (!descriptor.suppressHResultCheck) {
                    HResult(hResult).requireSuccess()
                }
                return readResult(descriptor.returnKind, resultOut)
            } finally {
                ownedInputs.asReversed().forEach { it.close() }
            }
        }
    }

    private fun encodeArgument(
        kind: GeneratedInterfaceProjectionValueKind,
        value: Any?,
        ownedInputs: MutableList<AutoCloseable>,
    ): Long =
        when (kind) {
            GeneratedInterfaceProjectionValueKind.Boolean -> (if (value as Boolean) 1 else 0).toLong()
            GeneratedInterfaceProjectionValueKind.Int8 -> (value as Byte).toLong()
            GeneratedInterfaceProjectionValueKind.UInt8 -> (value as UByte).toLong()
            GeneratedInterfaceProjectionValueKind.Int16 -> (value as Short).toLong()
            GeneratedInterfaceProjectionValueKind.UInt16 -> (value as UShort).toLong()
            GeneratedInterfaceProjectionValueKind.Int32 -> (value as Int).toLong()
            GeneratedInterfaceProjectionValueKind.UInt32 -> (value as UInt).toLong()
            GeneratedInterfaceProjectionValueKind.Int64 -> value as Long
            GeneratedInterfaceProjectionValueKind.UInt64 -> (value as ULong).toLong()
            GeneratedInterfaceProjectionValueKind.Float -> java.lang.Float.floatToRawIntBits(value as Float).toLong()
            GeneratedInterfaceProjectionValueKind.Double -> java.lang.Double.doubleToRawLongBits(value as Double)
            GeneratedInterfaceProjectionValueKind.Char16 -> (value as Char).code.toLong()
            GeneratedInterfaceProjectionValueKind.String -> HString.createReference(value as String).also(ownedInputs::add).handle.let(PlatformAbi::pointerKey)
            GeneratedInterfaceProjectionValueKind.Object -> {
                val marshaler = WinRtObjectMarshaller.createMarshaler(value)
                ownedInputs += marshaler
                PlatformAbi.pointerKey(marshaler.abi)
            }
            GeneratedInterfaceProjectionValueKind.UnknownReference,
            GeneratedInterfaceProjectionValueKind.InspectableReference,
            -> PlatformAbi.pointerKey((value as IWinRTObject).nativeObject.pointer)
            GeneratedInterfaceProjectionValueKind.Guid,
            GeneratedInterfaceProjectionValueKind.Unit,
            GeneratedInterfaceProjectionValueKind.Unsupported,
            -> unsupportedArgumentKind(kind)
        }

    private fun unsupportedArgumentKind(kind: GeneratedInterfaceProjectionValueKind): Nothing =
        throw WinRtUnsupportedOperationException(
            "Generated interface projection argument kind '$kind' is not supported by the JVM artifact runtime.",
            KnownHResults.E_NOTIMPL,
        )

    private fun allocateResultSlot(
        scope: NativeScope,
        kind: GeneratedInterfaceProjectionValueKind,
    ): RawAddress =
        when (kind) {
            GeneratedInterfaceProjectionValueKind.Boolean,
            GeneratedInterfaceProjectionValueKind.Int8,
            GeneratedInterfaceProjectionValueKind.UInt8,
            -> PlatformAbi.allocateInt8Slot(scope)
            GeneratedInterfaceProjectionValueKind.Int16,
            GeneratedInterfaceProjectionValueKind.UInt16,
            GeneratedInterfaceProjectionValueKind.Char16,
            -> PlatformAbi.allocateBytes(scope, 2, 2)
            GeneratedInterfaceProjectionValueKind.Int32,
            GeneratedInterfaceProjectionValueKind.UInt32,
            GeneratedInterfaceProjectionValueKind.Float,
            -> PlatformAbi.allocateInt32Slot(scope)
            GeneratedInterfaceProjectionValueKind.Int64,
            GeneratedInterfaceProjectionValueKind.UInt64,
            GeneratedInterfaceProjectionValueKind.Double,
            -> PlatformAbi.allocateInt64Slot(scope)
            GeneratedInterfaceProjectionValueKind.Guid -> PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong(), 8)
            GeneratedInterfaceProjectionValueKind.String,
            GeneratedInterfaceProjectionValueKind.Object,
            GeneratedInterfaceProjectionValueKind.UnknownReference,
            GeneratedInterfaceProjectionValueKind.InspectableReference,
            -> PlatformAbi.allocatePointerSlot(scope)
            GeneratedInterfaceProjectionValueKind.Unit -> PlatformAbi.nullPointer
            GeneratedInterfaceProjectionValueKind.Unsupported ->
                throw WinRtUnsupportedOperationException(
                    "Generated interface projection result kind '$kind' is not supported by the JVM artifact runtime.",
                    KnownHResults.E_NOTIMPL,
                )
        }

    private fun readResult(
        kind: GeneratedInterfaceProjectionValueKind,
        resultOut: RawAddress,
    ): Any? =
        when (kind) {
            GeneratedInterfaceProjectionValueKind.Unit -> Unit
            GeneratedInterfaceProjectionValueKind.Boolean -> PlatformAbi.readInt8(resultOut).toInt() != 0
            GeneratedInterfaceProjectionValueKind.Int8 -> PlatformAbi.readInt8(resultOut)
            GeneratedInterfaceProjectionValueKind.UInt8 -> PlatformAbi.readInt8(resultOut).toUByte()
            GeneratedInterfaceProjectionValueKind.Int16 -> PlatformAbi.readInt16(resultOut)
            GeneratedInterfaceProjectionValueKind.UInt16 -> PlatformAbi.readInt16(resultOut).toUShort()
            GeneratedInterfaceProjectionValueKind.Int32 -> PlatformAbi.readInt32(resultOut)
            GeneratedInterfaceProjectionValueKind.UInt32 -> PlatformAbi.readInt32(resultOut).toUInt()
            GeneratedInterfaceProjectionValueKind.Int64 -> PlatformAbi.readInt64(resultOut)
            GeneratedInterfaceProjectionValueKind.UInt64 -> PlatformAbi.readInt64(resultOut).toULong()
            GeneratedInterfaceProjectionValueKind.Float -> PlatformAbi.readFloat(resultOut)
            GeneratedInterfaceProjectionValueKind.Double -> PlatformAbi.readDouble(resultOut)
            GeneratedInterfaceProjectionValueKind.Char16 -> PlatformAbi.readChar16(resultOut)
            GeneratedInterfaceProjectionValueKind.Guid -> PlatformAbi.readGuid(resultOut)
            GeneratedInterfaceProjectionValueKind.String -> {
                val handle = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(handle)) {
                    ""
                } else {
                    HString.fromHandle(handle, owner = true).use { it.toKString() }
                }
            }
            GeneratedInterfaceProjectionValueKind.Object -> {
                WinRtObjectMarshaller.fromAbi(PlatformAbi.readPointer(resultOut))
            }
            GeneratedInterfaceProjectionValueKind.UnknownReference -> {
                val pointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(pointer)) null else IUnknownReference(PlatformAbi.toRawComPtr(pointer))
            }
            GeneratedInterfaceProjectionValueKind.InspectableReference -> {
                val pointer = PlatformAbi.readPointer(resultOut)
                if (PlatformAbi.isNull(pointer)) null else IInspectableReference(PlatformAbi.toRawComPtr(pointer), IID.IInspectable)
            }
            GeneratedInterfaceProjectionValueKind.Unsupported ->
                throw WinRtUnsupportedOperationException(
                    "Generated interface projection result kind '$kind' is not supported by the JVM artifact runtime.",
                    KnownHResults.E_NOTIMPL,
                )
        }

    private fun GeneratedInterfaceProjectionValueKind.comAbiKind(): ComAbiValueKind =
        when (this) {
            GeneratedInterfaceProjectionValueKind.Boolean,
            GeneratedInterfaceProjectionValueKind.Int8,
            GeneratedInterfaceProjectionValueKind.UInt8,
            -> ComAbiValueKind.Int8
            GeneratedInterfaceProjectionValueKind.Int16,
            GeneratedInterfaceProjectionValueKind.UInt16,
            GeneratedInterfaceProjectionValueKind.Char16,
            -> ComAbiValueKind.Int16
            GeneratedInterfaceProjectionValueKind.Int32,
            GeneratedInterfaceProjectionValueKind.UInt32,
            -> ComAbiValueKind.Int32
            GeneratedInterfaceProjectionValueKind.Int64,
            GeneratedInterfaceProjectionValueKind.UInt64,
            -> ComAbiValueKind.Int64
            GeneratedInterfaceProjectionValueKind.Float -> ComAbiValueKind.Float
            GeneratedInterfaceProjectionValueKind.Double -> ComAbiValueKind.Double
            GeneratedInterfaceProjectionValueKind.String,
            GeneratedInterfaceProjectionValueKind.Object,
            GeneratedInterfaceProjectionValueKind.UnknownReference,
            GeneratedInterfaceProjectionValueKind.InspectableReference,
            -> ComAbiValueKind.Pointer
            GeneratedInterfaceProjectionValueKind.Guid,
            GeneratedInterfaceProjectionValueKind.Unit,
            GeneratedInterfaceProjectionValueKind.Unsupported,
            -> unsupportedArgumentKind(this)
        }
}
