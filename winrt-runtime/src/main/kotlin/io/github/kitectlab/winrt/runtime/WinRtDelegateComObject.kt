package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object WinRtDelegateVftblSlots {
    const val Invoke = 3
}

internal class WinRtDelegateComObject(
    private val descriptor: WinRtDelegateDescriptor,
    private val callback: (List<Any?>) -> Unit,
) {
    private val arena = Arena.ofShared()
    private val objectMemory = arena.allocate(ValueLayout.ADDRESS)
    private val vtableMemory = arena.allocate(
        MemoryLayout.sequenceLayout((WinRtDelegateVftblSlots.Invoke + 1).toLong(), ValueLayout.ADDRESS),
    )
    private val cleanedUp = AtomicBoolean(false)
    private val referenceCount = AtomicInteger(1)

    init {
        registry[pointerKey(objectMemory)] = this
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.QueryInterface.toLong(), queryInterfaceStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.AddRef.toLong(), addRefStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.Release.toLong(), releaseStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, WinRtDelegateVftblSlots.Invoke.toLong(), createInvokeStub())
        objectMemory.set(ValueLayout.ADDRESS, 0, vtableMemory)
    }

    fun createReference(): WinRtDelegateReference {
        addReference()
        return WinRtDelegateReference(
            pointer = objectMemory,
            descriptor = descriptor,
        )
    }

    fun releaseManagedReference() {
        releaseReference()
    }

    private fun queryInterface(requestedInterfaceId: Guid, resultPointer: MemorySegment): Int {
        val outPointer = resultPointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        return if (requestedInterfaceId == IID.IUnknown || requestedInterfaceId == descriptor.interfaceId) {
            addReference()
            outPointer.set(ValueLayout.ADDRESS, 0, objectMemory)
            KnownHResults.S_OK.value
        } else {
            outPointer.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
            KnownHResults.E_NOINTERFACE.value
        }
    }

    private fun addReference(): Int = referenceCount.incrementAndGet()

    private fun releaseReference(): Int {
        val updated = referenceCount.decrementAndGet()
        check(updated >= 0) { "Delegate reference count cannot become negative." }
        if (updated == 0) {
            cleanup()
        }
        return updated
    }

    private fun invoke(rawArguments: Array<Any?>): Int {
        return try {
            callback(
                WinRtDelegateAbiMarshaller.decodeArguments(
                    parameterKinds = descriptor.parameterKinds,
                    abiArguments = rawArguments.toList(),
                ),
            )
            KnownHResults.S_OK.value
        } catch (error: WinRtRuntimeException) {
            (error.hResult ?: KnownHResults.E_FAIL).value
        } catch (_: Throwable) {
            KnownHResults.E_FAIL.value
        }
    }

    private fun cleanup() {
        if (cleanedUp.compareAndSet(false, true)) {
            registry.remove(pointerKey(objectMemory))
            arena.close()
        }
    }

    private fun createInvokeStub(): MemorySegment {
        val baseHandle = lookup.findStatic(
            WinRtDelegateComObject::class.java,
            "invokeBridge",
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                MemorySegment::class.java,
                Array<Any?>::class.java,
            ),
        )
        val collectedHandle = baseHandle.asCollector(Array<Any?>::class.java, descriptor.parameterKinds.size)
        val exactHandle = collectedHandle.asType(
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                listOf(MemorySegment::class.java) + descriptor.parameterKinds.map(WinRtDelegateAbiMarshaller::carrierClass),
            ),
        )
        return linker.upcallStub(
            exactHandle,
            WinRtDelegateAbiMarshaller.functionDescriptor(descriptor.parameterKinds),
            arena,
        )
    }

    companion object {
        private val linker: Linker = Linker.nativeLinker()
        private val lookup = MethodHandles.lookup()
        private val registry = ConcurrentHashMap<Long, WinRtDelegateComObject>()
        private val sharedArena: Arena = Arena.global()
        private val queryInterfaceStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtDelegateComObject::class.java,
                "queryInterfaceBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            ),
            java.lang.foreign.FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            sharedArena,
        )
        private val addRefStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtDelegateComObject::class.java,
                "addRefBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                ),
            ),
            java.lang.foreign.FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            sharedArena,
        )
        private val releaseStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtDelegateComObject::class.java,
                "releaseBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                ),
            ),
            java.lang.foreign.FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            sharedArena,
        )

        @JvmStatic
        private fun queryInterfaceBridge(
            thisPointer: MemorySegment,
            iidPointer: MemorySegment,
            resultPointer: MemorySegment,
        ): Int {
            val delegate = registry[pointerKey(thisPointer)] ?: return KnownHResults.RO_E_CLOSED.value
            return delegate.queryInterface(Guid.readFrom(iidPointer), resultPointer)
        }

        @JvmStatic
        private fun addRefBridge(thisPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.addReference() ?: 0

        @JvmStatic
        private fun releaseBridge(thisPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.releaseReference() ?: 0

        @JvmStatic
        private fun invokeBridge(thisPointer: MemorySegment, rawArguments: Array<Any?>): Int =
            registry[pointerKey(thisPointer)]?.invoke(rawArguments) ?: KnownHResults.RO_E_CLOSED.value

        private fun pointerKey(pointer: MemorySegment): Long = pointer.address()
    }
}

class WinRtDelegateReference internal constructor(
    pointer: MemorySegment,
    val descriptor: WinRtDelegateDescriptor,
) : ComObjectReference(pointer, descriptor.interfaceId) {
    fun invokeAbi(arguments: List<Any?>): HResult {
        val encodedArguments = WinRtDelegateAbiMarshaller.encodeArguments(descriptor.parameterKinds, arguments)
        return HResult(
            invokeIntMethod(
                slot = WinRtDelegateVftblSlots.Invoke,
                descriptor = WinRtDelegateAbiMarshaller.functionDescriptor(descriptor.parameterKinds),
                *arrayOf(pointer, *encodedArguments.toTypedArray()),
            ),
        )
    }
}