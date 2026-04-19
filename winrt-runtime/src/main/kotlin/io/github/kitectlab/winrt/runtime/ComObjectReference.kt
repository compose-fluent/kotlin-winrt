package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

open class ComObjectReference(
    val pointer: MemorySegment,
    val interfaceId: Guid,
) : AutoCloseable {
    init {
        require(pointer != MemorySegment.NULL) {
            "COM object reference cannot wrap a null pointer."
        }
    }

    fun addRef(): UInt = invokeUIntMethod(1)

    fun release(): UInt = invokeUIntMethod(2)

    fun queryInterface(requestedInterfaceId: Guid): Result<ComObjectReference> {
        return runCatching {
            Arena.ofConfined().use { arena ->
                val iidMemory = arena.allocate(16)
                requestedInterfaceId.writeTo(iidMemory)
                val resultPtr = arena.allocate(ValueLayout.ADDRESS)
                val hr = invokeIntMethod(
                    slot = 0,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    pointer,
                    iidMemory,
                    resultPtr,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                ComObjectReference(
                    pointer = resultPtr.get(ValueLayout.ADDRESS, 0),
                    interfaceId = requestedInterfaceId,
                )
            }
        }
    }

    fun invokeObjectMethodWithStringArg(slot: Int, value: String): IUnknownReference {
        HString.create(value).use { hString ->
            Arena.ofConfined().use { arena ->
                val resultOut = arena.allocate(ValueLayout.ADDRESS)
                val hr = invokeIntMethod(
                    slot = slot,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    pointer,
                    hString.handle,
                    resultOut,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
            }
        }
    }

    fun invokeObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.pointer,
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    fun invokeHStringMethodWithStringArg(slot: Int, value: String): HString {
        HString.create(value).use { hString ->
            Arena.ofConfined().use { arena ->
                val resultOut = arena.allocate(ValueLayout.ADDRESS)
                val hr = invokeIntMethod(
                    slot = slot,
                    descriptor = FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                    ),
                    pointer,
                    hString.handle,
                    resultOut,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                return HString.fromHandle(resultOut.get(ValueLayout.ADDRESS, 0), owner = true)
            }
        }
    }

    fun invokeUnitMethod(slot: Int) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            pointer,
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    fun invokeUnitMethodWithObjectArg(slot: Int, value: ComObjectReference) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            pointer,
            value.pointer,
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    fun asInspectable(): IInspectableReference =
        queryInterface(IID.IInspectable).getOrThrow().let { IInspectableReference(it.pointer) }

    override fun close() {
        release()
    }

    protected fun invokeUIntMethod(slot: Int): UInt =
        invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            pointer,
        ).toUInt()

    protected fun invokeIntMethod(
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Int {
        val method = Linker.nativeLinker().downcallHandle(vtableEntry(slot), descriptor)
        return method.invokeWithArguments(*args) as Int
    }

    protected fun vtableEntry(slot: Int): MemorySegment {
        val objectMemory = pointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        val vtable = objectMemory.get(ValueLayout.ADDRESS, 0)
        val vtableMemory = vtable.reinterpret((slot + 1L) * ValueLayout.ADDRESS.byteSize())
        return vtableMemory.getAtIndex(ValueLayout.ADDRESS, slot.toLong())
    }
}

open class IUnknownReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IUnknown,
) : ComObjectReference(pointer, interfaceId)

class ActivationFactoryReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IActivationFactory,
) : IUnknownReference(pointer, interfaceId) {
    fun activateInstance(): IInspectableReference {
        Arena.ofConfined().use { arena ->
            val instanceOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = 6,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                instanceOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return IInspectableReference(instanceOut.get(ValueLayout.ADDRESS, 0), IID.IInspectable)
        }
    }
}

class InspectableReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IInspectable,
) : ComObjectReference(pointer, interfaceId) {
    fun getRuntimeClassName(noThrow: Boolean = false): String? {
        Arena.ofConfined().use { arena ->
            val hstringOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = 4,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                hstringOut,
            )
            if (HResult(hr).isFailure) {
                if (noThrow) {
                    return null
                }
                WindowsRuntimePlatform.checkSucceeded(hr)
            }

            val hstring = hstringOut.get(ValueLayout.ADDRESS, 0)
            if (hstring == MemorySegment.NULL) {
                return null
            }
            return HString.fromHandle(hstring, owner = true).use(HString::toKString)
        }
    }
}

typealias IInspectableReference = InspectableReference

object IID {
    val IUnknown: Guid = Guid("00000000-0000-0000-C000-000000000046")
    val IInspectable: Guid = Guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
    val IActivationFactory: Guid = Guid("00000035-0000-0000-C000-000000000046")
}
