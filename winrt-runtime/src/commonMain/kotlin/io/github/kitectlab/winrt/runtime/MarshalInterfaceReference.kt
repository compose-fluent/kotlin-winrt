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

internal object MarshalInterfaceVftbl {
    const val GetUnmarshalClass: Int = 3
    const val GetMarshalSizeMax: Int = 4
    const val MarshalInterface: Int = 5
    const val UnmarshalInterface: Int = 6
    const val ReleaseMarshalData: Int = 7
    const val DisconnectObject: Int = 8
}

internal class MarshalInterfaceReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IMarshal,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun getUnmarshalClass(
        interfaceId: Guid,
        sourcePointer: RawAddress = PlatformAbi.nullPointer,
        destinationContext: Int = WinRtMarshalingContext.InProc,
        destinationContextPointer: RawAddress = PlatformAbi.nullPointer,
        flags: Int = WinRtMarshalingFlags.Normal,
    ): Guid =
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            val resultOut = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    MarshalInterfaceVftbl.GetUnmarshalClass,
                    interfaceIdMemory,
                    sourcePointer,
                    destinationContext,
                    destinationContextPointer,
                    flags,
                    resultOut,
                ),
            ).requireSuccess("IMarshal.GetUnmarshalClass")
            PlatformAbi.readGuid(resultOut)
        }

    fun getMarshalSizeMax(
        interfaceId: Guid,
        sourcePointer: RawAddress = PlatformAbi.nullPointer,
        destinationContext: Int = WinRtMarshalingContext.InProc,
        destinationContextPointer: RawAddress = PlatformAbi.nullPointer,
        flags: Int = WinRtMarshalingFlags.Normal,
    ): UInt =
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            val resultOut = PlatformAbi.allocateInt32Slot(scope)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    MarshalInterfaceVftbl.GetMarshalSizeMax,
                    interfaceIdMemory,
                    sourcePointer,
                    destinationContext,
                    destinationContextPointer,
                    flags,
                    resultOut,
                ),
            ).requireSuccess("IMarshal.GetMarshalSizeMax")
            PlatformAbi.readInt32(resultOut).toUInt()
        }

    fun marshalInterface(
        streamPointer: RawAddress,
        interfaceId: Guid,
        interfacePointer: RawAddress = PlatformAbi.nullPointer,
        destinationContext: Int = WinRtMarshalingContext.InProc,
        destinationContextPointer: RawAddress = PlatformAbi.nullPointer,
        flags: Int = WinRtMarshalingFlags.Normal,
    ) {
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    MarshalInterfaceVftbl.MarshalInterface,
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
        streamPointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference? =
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(interfaceIdMemory)
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    MarshalInterfaceVftbl.UnmarshalInterface,
                    streamPointer,
                    interfaceIdMemory,
                    resultOut,
                ),
            ).requireSuccess("IMarshal.UnmarshalInterface")
            val resolvedPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer.asRawComPtr(), interfaceId)
            }
        }

    fun releaseMarshalData(streamPointer: RawAddress) {
        comPtr.throwIfDisposed()
        HResult(
            ComVtableInvoker.invokeArgs(comPtr.raw, MarshalInterfaceVftbl.ReleaseMarshalData, streamPointer),
        ).requireSuccess("IMarshal.ReleaseMarshalData")
    }

    fun disconnectObject(reserved: Int = 0) {
        comPtr.throwIfDisposed()
        HResult(
            ComVtableInvoker.invokeArgs(comPtr.raw, MarshalInterfaceVftbl.DisconnectObject, reserved),
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
            check(!PlatformAbi.isNull(result.pointer)) {
                "CoCreateFreeThreadedMarshaler returned a null pointer with success HRESULT."
            }

            val createdProxy = IUnknownReference(result.pointer.asRawComPtr(), IID.IUnknown).use { unknown ->
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
