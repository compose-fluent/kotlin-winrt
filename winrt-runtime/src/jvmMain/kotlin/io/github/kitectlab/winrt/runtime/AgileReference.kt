package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal class AgileReferenceInterfaceReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IAgileReference,
) : IUnknownReference(pointer, interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(AbiLayouts.GUID)
            interfaceId.writeTo(iidMemory)
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    iidMemory,
                    resultOut,
                ),
                operation = "IAgileReference.Resolve",
            )
            val pointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (pointer == MemorySegment.NULL) {
                null
            } else {
                IUnknownReference(pointer, interfaceId)
            }
        }
    }
}

internal class GlobalInterfaceTableReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IGlobalInterfaceTable,
) : IUnknownReference(pointer, interfaceId) {
    fun registerInterfaceInGlobal(
        interfacePointer: NativePointer,
        interfaceId: Guid,
    ): NativePointer {
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(AbiLayouts.GUID)
            interfaceId.writeTo(iidMemory)
            val cookieOut = arena.allocate(ValueLayout.ADDRESS)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 3,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    interfacePointer.asMemorySegment(),
                    iidMemory,
                    cookieOut,
                ),
                operation = "IGlobalInterfaceTable.RegisterInterfaceInGlobal",
            )
            return cookieOut.get(ValueLayout.ADDRESS, 0).asNativePointer()
        }
    }

    fun tryRevokeInterfaceFromGlobal(cookie: NativePointer): HResult =
        HResult(
            invokeAbi(
                slot = 4,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                cookie.asMemorySegment(),
            ),
        )

    fun getInterfaceFromGlobal(
        cookie: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(AbiLayouts.GUID)
            interfaceId.writeTo(iidMemory)
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            ExceptionHelpers.throwExceptionForHR(
                invokeAbi(
                    slot = 5,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    cookie.asMemorySegment(),
                    iidMemory,
                    resultOut,
                ),
                operation = "IGlobalInterfaceTable.GetInterfaceFromGlobal",
            )
            val pointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (pointer == MemorySegment.NULL) {
                null
            } else {
                IUnknownReference(pointer, interfaceId)
            }
        }
    }
}

class AgileReference(
    instance: ComObjectReference?,
) : AutoCloseable {
    private val agileReference: AgileReferenceInterfaceReference?
    private val cookie: NativePointer

    init {
        if (instance == null || instance.pointer == MemorySegment.NULL) {
            agileReference = null
            cookie = NativeInterop.nullPointer
        } else {
            val result = WinRtPlatformApi.roGetAgileReferenceRaw(instance.pointer.asNativePointer(), IID.IUnknown)
            val hResult = HResult(result.hResultValue)
            if (result.isSuccess) {
                agileReference = AgileReferenceInterfaceReference(result.pointer.asMemorySegment(), IID.IAgileReference)
                cookie = NativeInterop.nullPointer
            } else if (hResult == KnownHResults.E_NOTIMPL) {
                agileReference = null
                cookie = git().registerInterfaceInGlobal(instance.pointer.asNativePointer(), IID.IUnknown)
            } else {
                throw WinRtExceptionTranslator.exceptionFor(hResult, "RoGetAgileReference")
            }
        }
    }

    fun get(): IUnknownReference? =
        if (NativeInterop.isNull(cookie)) {
            agileReference?.resolve(IID.IUnknown)
        } else {
            git().getInterfaceFromGlobal(cookie, IID.IUnknown)
        }

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> get(typeHandle: WinRtTypeHandle): T? {
        val reference = if (NativeInterop.isNull(cookie)) {
            agileReference?.resolve(typeHandle.interfaceId)
        } else {
            git().getInterfaceFromGlobal(cookie, typeHandle.interfaceId)
        } ?: return null
        return reference.use {
            ComWrappersSupport.createRcwForComObject(reference.pointer, typeHandle) as? T
        }
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
            GlobalInterfaceTableReference(result.pointer.asMemorySegment(), IID.IGlobalInterfaceTable)
        }

        private fun git(): GlobalInterfaceTableReference = globalInterfaceTable
    }
}

class AgileReferenceTyped<T : Any>(
    private val typeHandle: WinRtTypeHandle,
    instance: ComObjectReference?,
) : AutoCloseable {
    private val reference = AgileReference(instance)

    fun get(): T? = reference.get<T>(typeHandle)

    override fun close() {
        reference.close()
    }
}
