package io.github.kitectlab.winrt.runtime

private val resolveDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

private val registerInGlobalDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

private val revokeFromGlobalDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

private val getFromGlobalDescriptor = NativeFunctionDescriptor.of(
    NativeValueLayout.JAVA_INT,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
    NativeValueLayout.ADDRESS,
)

internal class AgileReferenceInterfaceReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IAgileReference,
) : IUnknownReference(pointer, interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? =
        NativeInterop.confinedScope().use { scope ->
            val iidMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            HResult(
                invokeAbi(
                    slot = 3,
                    descriptor = resolveDescriptor,
                    iidMemory,
                    resultOut,
                ),
            ).requireSuccess("IAgileReference.Resolve")
            val resolvedPointer = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer, interfaceId)
            }
        }
}

internal class GlobalInterfaceTableReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IGlobalInterfaceTable,
) : IUnknownReference(pointer, interfaceId) {
    fun registerInterfaceInGlobal(
        interfacePointer: NativePointer,
        interfaceId: Guid,
    ): NativePointer =
        NativeInterop.confinedScope().use { scope ->
            val iidMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val cookieOut = NativeInterop.allocatePointerSlot(scope)
            HResult(
                invokeAbi(
                    slot = 3,
                    descriptor = registerInGlobalDescriptor,
                    interfacePointer,
                    iidMemory,
                    cookieOut,
                ),
            ).requireSuccess("IGlobalInterfaceTable.RegisterInterfaceInGlobal")
            NativeInterop.readPointer(cookieOut)
        }

    fun tryRevokeInterfaceFromGlobal(cookie: NativePointer): HResult =
        HResult(
            invokeAbi(
                slot = 4,
                descriptor = revokeFromGlobalDescriptor,
                cookie,
            ),
        )

    fun getInterfaceFromGlobal(
        cookie: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference? =
        NativeInterop.confinedScope().use { scope ->
            val iidMemory = NativeInterop.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            HResult(
                invokeAbi(
                    slot = 5,
                    descriptor = getFromGlobalDescriptor,
                    cookie,
                    iidMemory,
                    resultOut,
                ),
            ).requireSuccess("IGlobalInterfaceTable.GetInterfaceFromGlobal")
            val resolvedPointer = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer, interfaceId)
            }
        }
}

class AgileReference(
    instance: ComObjectReference?,
) : AutoCloseable {
    private val agileReference: AgileReferenceInterfaceReference?
    private val cookie: NativePointer

    init {
        if (instance == null || NativeInterop.isNull(instance.pointer)) {
            agileReference = null
            cookie = NativeInterop.nullPointer
        } else {
            val result = WinRtPlatformApi.roGetAgileReferenceRaw(instance.pointer, IID.IUnknown)
            val hResult = HResult(result.hResultValue)
            if (result.isSuccess) {
                agileReference = AgileReferenceInterfaceReference(result.pointer, IID.IAgileReference)
                cookie = NativeInterop.nullPointer
            } else if (hResult == KnownHResults.E_NOTIMPL) {
                agileReference = null
                cookie = git().registerInterfaceInGlobal(instance.pointer, IID.IUnknown)
            } else {
                throwHResultFailure(hResult, "RoGetAgileReference")
            }
        }
    }

    fun get(): IUnknownReference? =
        if (NativeInterop.isNull(cookie)) {
            agileReference?.resolve(IID.IUnknown)
        } else {
            git().getInterfaceFromGlobal(cookie, IID.IUnknown)
        }

    internal fun getReference(typeHandle: WinRtTypeHandle): IUnknownReference? =
        if (NativeInterop.isNull(cookie)) {
            agileReference?.resolve(typeHandle.interfaceId)
        } else {
            git().getInterfaceFromGlobal(cookie, typeHandle.interfaceId)
        }

    override fun close() {
        agileReference?.close()
        if (!NativeInterop.isNull(cookie)) {
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
