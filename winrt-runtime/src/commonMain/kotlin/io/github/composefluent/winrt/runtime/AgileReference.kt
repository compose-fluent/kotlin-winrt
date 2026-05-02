package io.github.composefluent.winrt.runtime

private object AgileReferenceInterfaceVftbl {
    const val Resolve: Int = 3
}

private object GlobalInterfaceTableVftbl {
    const val RegisterInterfaceInGlobal: Int = 3
    const val RevokeInterfaceFromGlobal: Int = 4
    const val GetInterfaceFromGlobal: Int = 5
}

internal class AgileReferenceInterfaceReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IAgileReference,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? =
        PlatformAbi.confinedScope().use { scope ->
            val iidMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(comPtr.raw, AgileReferenceInterfaceVftbl.Resolve, iidMemory, resultOut),
            ).requireSuccess("IAgileReference.Resolve")
            val resolvedPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer.asRawComPtr(), interfaceId)
            }
        }
}

internal class GlobalInterfaceTableReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IGlobalInterfaceTable,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun registerInterfaceInGlobal(
        interfacePointer: RawAddress,
        interfaceId: Guid,
    ): RawAddress =
        PlatformAbi.confinedScope().use { scope ->
            val iidMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val cookieOut = PlatformAbi.allocatePointerSlot(scope)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    GlobalInterfaceTableVftbl.RegisterInterfaceInGlobal,
                    interfacePointer,
                    iidMemory,
                    cookieOut,
                ),
            ).requireSuccess("IGlobalInterfaceTable.RegisterInterfaceInGlobal")
            PlatformAbi.readPointer(cookieOut)
        }

    fun tryRevokeInterfaceFromGlobal(cookie: RawAddress): HResult =
        HResult(
            run {
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(comPtr.raw, GlobalInterfaceTableVftbl.RevokeInterfaceFromGlobal, cookie)
            },
        )

    fun getInterfaceFromGlobal(
        cookie: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference? =
        PlatformAbi.confinedScope().use { scope ->
            val iidMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    comPtr.raw,
                    GlobalInterfaceTableVftbl.GetInterfaceFromGlobal,
                    cookie,
                    iidMemory,
                    resultOut,
                ),
            ).requireSuccess("IGlobalInterfaceTable.GetInterfaceFromGlobal")
            val resolvedPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer.asRawComPtr(), interfaceId)
            }
        }
}

class AgileReference(
    instance: ComObjectReference?,
) : AutoCloseable {
    private val agileReference: AgileReferenceInterfaceReference?
    private val cookie: RawAddress

    init {
        if (instance == null || PlatformAbi.isNull(instance.pointer)) {
            agileReference = null
            cookie = PlatformAbi.nullPointer
        } else {
            val result = WinRtPlatformApi.roGetAgileReferenceRaw(instance.pointer.asRawAddress(), IID.IUnknown)
            val hResult = HResult(result.hResultValue)
            if (result.isSuccess) {
                agileReference = AgileReferenceInterfaceReference(result.pointer, IID.IAgileReference)
                cookie = PlatformAbi.nullPointer
            } else if (hResult == KnownHResults.E_NOTIMPL) {
                agileReference = null
                cookie = git().registerInterfaceInGlobal(instance.pointer.asRawAddress(), IID.IUnknown)
            } else {
                throwHResultFailure(hResult, "RoGetAgileReference")
            }
        }
    }

    fun get(): IUnknownReference? =
        if (PlatformAbi.isNull(cookie)) {
            agileReference?.resolve(IID.IUnknown)
        } else {
            git().getInterfaceFromGlobal(cookie, IID.IUnknown)
        }

    internal fun getReference(typeHandle: WinRtTypeHandle): IUnknownReference? =
        if (PlatformAbi.isNull(cookie)) {
            agileReference?.resolve(typeHandle.interfaceId)
        } else {
            git().getInterfaceFromGlobal(cookie, typeHandle.interfaceId)
        }

    override fun close() {
        agileReference?.close()
        if (!PlatformAbi.isNull(cookie)) {
            git().tryRevokeInterfaceFromGlobal(cookie)
        }
    }

    companion object {
        private val stdGlobalInterfaceTableClsid = guidOf("00000323-0000-0000-C000-000000000046")
        private val globalInterfaceTable by lazy {
            val result = WinRtPlatformApi.coCreateInstanceRaw(
                classId = stdGlobalInterfaceTableClsid,
                interfaceId = IID.IGlobalInterfaceTable,
            )
            HResult(result.hResultValue).requireSuccess("CoCreateInstance(CLSID_StdGlobalInterfaceTable)")
            GlobalInterfaceTableReference(result.pointer, IID.IGlobalInterfaceTable)
        }

        private fun git(): GlobalInterfaceTableReference = globalInterfaceTable
    }
}
