package io.github.kitectlab.winrt.runtime

internal object WinRtMarshalingContext {
    const val Local = 0
    const val NoSharedMemory = 1
    const val DifferentMachine = 2
    const val InProc = 3
    const val CrossContext = 4
}

internal object WinRtMarshalingFlags {
    const val Normal = 0
    const val TableStrong = 1
    const val TableWeak = 2
    const val NoPing = 4
}

internal val getUnmarshalClassDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
)

internal val getMarshalSizeMaxDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
)

internal val marshalInterfaceDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.JAVA_INT,
)

internal val unmarshalInterfaceDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

internal val releaseMarshalDataDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
)

internal val disconnectObjectDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.JAVA_INT,
)

internal class MarshalInterfaceReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IMarshal,
) : IUnknownReference(pointer, interfaceId) {
    fun getUnmarshalClass(
        interfaceId: Guid,
        sourcePointer: NativePointer = NativeInterop.nullPointer,
        destinationContext: Int = WinRtMarshalingContext.InProc,
        destinationContextPointer: NativePointer = NativeInterop.nullPointer,
        flags: Int = WinRtMarshalingFlags.Normal,
    ): Guid =
        NativeInterop.confinedScope().use { scope ->
            val interfaceIdMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            val resultOut = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            HResult(
                invokeAbi(
                    slot = 3,
                    descriptor = getUnmarshalClassDescriptor,
                    interfaceIdMemory,
                    sourcePointer,
                    destinationContext,
                    destinationContextPointer,
                    flags,
                    resultOut,
                ),
            ).requireSuccess("IMarshal.GetUnmarshalClass")
            NativeInterop.readGuid(resultOut)
        }

    fun getMarshalSizeMax(
        interfaceId: Guid,
        sourcePointer: NativePointer = NativeInterop.nullPointer,
        destinationContext: Int = WinRtMarshalingContext.InProc,
        destinationContextPointer: NativePointer = NativeInterop.nullPointer,
        flags: Int = WinRtMarshalingFlags.Normal,
    ): UInt =
        NativeInterop.confinedScope().use { scope ->
            val interfaceIdMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            val resultOut = NativeInterop.allocateInt32Slot(scope)
            HResult(
                invokeAbi(
                    slot = 4,
                    descriptor = getMarshalSizeMaxDescriptor,
                    interfaceIdMemory,
                    sourcePointer,
                    destinationContext,
                    destinationContextPointer,
                    flags,
                    resultOut,
                ),
            ).requireSuccess("IMarshal.GetMarshalSizeMax")
            NativeInterop.readInt32(resultOut).toUInt()
        }

    fun marshalInterface(
        streamPointer: NativePointer,
        interfaceId: Guid,
        interfacePointer: NativePointer = NativeInterop.nullPointer,
        destinationContext: Int = WinRtMarshalingContext.InProc,
        destinationContextPointer: NativePointer = NativeInterop.nullPointer,
        flags: Int = WinRtMarshalingFlags.Normal,
    ) {
        NativeInterop.confinedScope().use { scope ->
            val interfaceIdMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            HResult(
                invokeAbi(
                    slot = 5,
                    descriptor = marshalInterfaceDescriptor,
                    streamPointer,
                    interfaceIdMemory,
                    interfacePointer,
                    destinationContext,
                    destinationContextPointer,
                    flags,
                ),
            ).requireSuccess("IMarshal.MarshalInterface")
        }
    }

    fun unmarshalInterface(
        streamPointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference? =
        NativeInterop.confinedScope().use { scope ->
            val interfaceIdMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            HResult(
                invokeAbi(
                    slot = 6,
                    descriptor = unmarshalInterfaceDescriptor,
                    streamPointer,
                    interfaceIdMemory,
                    resultOut,
                ),
            ).requireSuccess("IMarshal.UnmarshalInterface")
            val resolvedPointer = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer, interfaceId)
            }
        }

    fun releaseMarshalData(streamPointer: NativePointer) {
        HResult(
            invokeAbi(
                slot = 7,
                descriptor = releaseMarshalDataDescriptor,
                streamPointer,
            ),
        ).requireSuccess("IMarshal.ReleaseMarshalData")
    }

    fun disconnectObject(reserved: Int = 0) {
        HResult(
            invokeAbi(
                slot = 8,
                descriptor = disconnectObjectDescriptor,
                reserved,
            ),
        ).requireSuccess("IMarshal.DisconnectObject")
    }
}

internal object FreeThreadedMarshalerSupport {
    private val lock = PlatformLock()
    private var proxy: MarshalInterfaceReference? = null
    private var inProcFreeThreadedMarshalerIid: Guid? = null

    fun proxy(): MarshalInterfaceReference =
        lock.withLock {
            proxy?.let { return@withLock it }

            val result = WinRtPlatformApi.coCreateFreeThreadedMarshalerRaw()
            HResult(result.hResultValue).requireSuccess("CoCreateFreeThreadedMarshaler")
            check(!NativeInterop.isNull(result.pointer)) {
                "CoCreateFreeThreadedMarshaler returned a null pointer with success HRESULT."
            }

            val createdProxy = IUnknownReference(result.pointer, IID.IUnknown).use { unknown ->
                val marshal = unknown.queryInterface(IID.IMarshal).getOrThrow()
                MarshalInterfaceReference(marshal.useAndGetRef(), IID.IMarshal)
            }
            proxy = createdProxy
            createdProxy
        }

    fun inProcFreeThreadedMarshalerIid(): Guid =
        lock.withLock {
            inProcFreeThreadedMarshalerIid?.let { return@withLock it }
            val resolved = proxy().getUnmarshalClass(
                interfaceId = IID.IUnknown,
                destinationContext = WinRtMarshalingContext.InProc,
            )
            inProcFreeThreadedMarshalerIid = resolved
            resolved
        }

    fun clearForTests() {
        lock.withLock {
            proxy?.close()
            proxy = null
            inProcFreeThreadedMarshalerIid = null
        }
    }
}
