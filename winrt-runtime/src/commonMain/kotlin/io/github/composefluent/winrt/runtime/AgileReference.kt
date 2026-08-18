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
    comPtr: ComPtr,
) : IUnknownReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = IID.IAgileReference,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId, trackContext = false))

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
    comPtr: ComPtr,
) : IUnknownReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = IID.IGlobalInterfaceTable,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId, trackContext = false))

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

class AgileReference internal constructor(
    pointer: RawAddress,
) : AutoCloseable {
    private val agileReference: AgileReferenceInterfaceReference?
    private val cookie: RawAddress

    constructor(instance: ComObjectReference?) :
        this(instance?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer)

    init {
        if (PlatformAbi.isNull(pointer)) {
            agileReference = null
            cookie = PlatformAbi.nullPointer
        } else {
            val result = WinRTPlatformApi.roGetAgileReferenceRaw(pointer, IID.IUnknown)
            val hResult = HResult(result.hResultValue)
            if (result.isSuccess) {
                agileReference = AgileReferenceInterfaceReference(result.pointer, IID.IAgileReference)
                cookie = PlatformAbi.nullPointer
            } else if (hResult == KnownHResults.E_NOTIMPL) {
                agileReference = null
                cookie = git().registerInterfaceInGlobal(pointer, IID.IUnknown)
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

    internal fun getReference(typeHandle: WinRTTypeHandle): IUnknownReference? =
        getReference(typeHandle.interfaceId)

    internal fun getReference(interfaceId: Guid): IUnknownReference? =
        if (PlatformAbi.isNull(cookie)) {
            agileReference?.resolve(interfaceId)
        } else {
            git().getInterfaceFromGlobal(cookie, interfaceId)
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
            val result = WinRTPlatformApi.coCreateInstanceRaw(
                classId = stdGlobalInterfaceTableClsid,
                interfaceId = IID.IGlobalInterfaceTable,
            )
            HResult(result.hResultValue).requireSuccess("CoCreateInstance(CLSID_StdGlobalInterfaceTable)")
            GlobalInterfaceTableReference(result.pointer, IID.IGlobalInterfaceTable)
        }

        private fun git(): GlobalInterfaceTableReference = globalInterfaceTable
    }
}
