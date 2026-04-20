package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * JVM-side fallback for the `.cswinrt/src/WinRT.Runtime/Interop/ExceptionErrorInfo*` owner.
 *
 * The CLR-specific `RoOriginateLanguageException` path cannot be mirrored directly on the JVM because
 * there is no managed exception CCW shape equivalent to the .NET runtime exception object. The narrow
 * platform adaptation point is this IErrorInfo/ISupportErrorInfo host, which still keeps ABI-side error
 * propagation owned by `winrt-runtime` instead of ad hoc stub logic.
 */
internal class ManagedErrorInfoComObject(
    private val error: Throwable,
) : AutoCloseable {
    /**
     * `SetErrorInfo` and other COM consumers can retain the exposed pointer after the local Kotlin frame
     * has already released its last reference. Panama shared arenas are not a safe reclamation mechanism
     * for that lifetime, so this JVM adaptation keeps the backing memory in the global arena just like the
     * existing inspectable CCW helper does for the same reason.
     */
    private val arena = Arena.global()
    private val cleanedUp = AtomicBoolean(false)
    private val referenceCount = AtomicInteger(1)
    private val interfaceEntries = mapOf(
        IID.IErrorInfo to createInterfaceEntry(
            slotCount = 8,
            slotBuilder = ::populateErrorInfoSlots,
        ),
        IID.ISupportErrorInfo to createInterfaceEntry(
            slotCount = 4,
            slotBuilder = ::populateSupportErrorInfoSlots,
        ),
    )

    init {
        interfaceEntries.values.forEach { entry ->
            registry[pointerKey(entry.objectMemory)] = this
        }
    }

    override fun close() {
        releaseManagedReference()
    }

    fun detachReference(interfaceId: Guid = IID.IErrorInfo): IUnknownReference {
        val reference = createReference(interfaceId)
        releaseManagedReference()
        return reference
    }

    private fun createReference(interfaceId: Guid): IUnknownReference {
        addReference()
        return IUnknownReference(interfacePointer(interfaceId), interfaceId)
    }

    private fun releaseManagedReference() {
        releaseReference()
    }

    private fun interfacePointer(interfaceId: Guid): MemorySegment =
        interfaceEntries[interfaceId]?.objectMemory
            ?: throw WinRtUnsupportedOperationException(
                "Managed error info object does not implement '$interfaceId'.",
                KnownHResults.E_NOINTERFACE,
            )

    private fun createInterfaceEntry(
        slotCount: Int,
        slotBuilder: (MemorySegment) -> Unit,
    ): InterfaceEntry {
        val objectMemory = arena.allocate(ValueLayout.ADDRESS)
        val vtableMemory = arena.allocate(
            MemoryLayout.sequenceLayout(slotCount.toLong(), ValueLayout.ADDRESS),
        )
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.QueryInterface.toLong(), queryInterfaceStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.AddRef.toLong(), addRefStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.Release.toLong(), releaseStub)
        slotBuilder(vtableMemory)
        objectMemory.set(ValueLayout.ADDRESS, 0, vtableMemory)
        return InterfaceEntry(objectMemory = objectMemory)
    }

    private fun populateErrorInfoSlots(vtableMemory: MemorySegment) {
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, 3, getGuidStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, 4, getSourceStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, 5, getDescriptionStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, 6, getHelpFileStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, 7, getHelpFileContentStub)
    }

    private fun populateSupportErrorInfoSlots(vtableMemory: MemorySegment) {
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, 3, interfaceSupportsErrorInfoStub)
    }

    private fun queryInterface(requestedInterfaceId: Guid, resultPointer: MemorySegment): Int {
        val result = resultPointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        val targetPointer = when (requestedInterfaceId) {
            IID.IUnknown,
            IID.IErrorInfo,
            IID.ISupportErrorInfo,
            -> interfaceEntries[requestedInterfaceId]?.objectMemory ?: interfaceEntries.getValue(IID.IErrorInfo).objectMemory

            else -> null
        }
        return if (targetPointer != null) {
            addReference()
            result.set(ValueLayout.ADDRESS, 0, targetPointer)
            KnownHResults.S_OK.value
        } else {
            result.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
            KnownHResults.E_NOINTERFACE.value
        }
    }

    private fun addReference(): Int = referenceCount.incrementAndGet()

    private fun releaseReference(): Int {
        val updated = referenceCount.decrementAndGet()
        check(updated >= 0) { "Managed error info reference count cannot become negative." }
        if (updated == 0) {
            cleanup()
        }
        return updated
    }

    private fun getGuid(resultPointer: MemorySegment): Int {
        guidOf("00000000-0000-0000-0000-000000000000").writeTo(
            resultPointer.reinterpret(AbiLayouts.GUID.byteSize()),
        )
        return KnownHResults.S_OK.value
    }

    private fun getSource(resultPointer: MemorySegment): Int =
        writeBstr(resultPointer, error.javaClass.name)

    private fun getDescription(resultPointer: MemorySegment): Int =
        writeBstr(resultPointer, error.message ?: error.javaClass.name)

    private fun getHelpFile(resultPointer: MemorySegment): Int =
        writeBstr(resultPointer, null)

    private fun getHelpFileContent(resultPointer: MemorySegment): Int =
        writeBstr(resultPointer, null)

    private fun interfaceSupportsErrorInfo(@Suppress("UNUSED_PARAMETER") interfaceIdPointer: MemorySegment): Int =
        KnownHResults.S_OK.value

    private fun writeBstr(
        resultPointer: MemorySegment,
        value: String?,
    ): Int {
        val outPointer = resultPointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        outPointer.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
        return runCatching {
            outPointer.set(ValueLayout.ADDRESS, 0, WinRtPlatformApi.sysAllocStringRaw(value).asMemorySegment())
            KnownHResults.S_OK.value
        }.getOrElse { failure ->
            WinRtExceptionTranslator.hResultFromException(failure).value
        }
    }

    private fun cleanup() {
        if (cleanedUp.compareAndSet(false, true)) {
            interfaceEntries.values.forEach { entry ->
                registry.remove(pointerKey(entry.objectMemory))
            }
        }
    }

    private data class InterfaceEntry(
        val objectMemory: MemorySegment,
    )

    companion object {
        private val linker: Linker = Linker.nativeLinker()
        private val lookup = MethodHandles.lookup()
        private val sharedArena = Arena.global()
        private val registry = ConcurrentCacheMap<Long, ManagedErrorInfoComObject>()

        private val queryInterfaceStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                ManagedErrorInfoComObject::class.java,
                "queryInterfaceBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            ),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            sharedArena,
        )

        private val addRefStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                ManagedErrorInfoComObject::class.java,
                "addRefBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                ),
            ),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            sharedArena,
        )

        private val releaseStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                ManagedErrorInfoComObject::class.java,
                "releaseBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                ),
            ),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            sharedArena,
        )

        private val getGuidStub: MemorySegment = createOutPointerStub("getGuidBridge")
        private val getSourceStub: MemorySegment = createOutPointerStub("getSourceBridge")
        private val getDescriptionStub: MemorySegment = createOutPointerStub("getDescriptionBridge")
        private val getHelpFileStub: MemorySegment = createOutPointerStub("getHelpFileBridge")
        private val getHelpFileContentStub: MemorySegment = createOutPointerStub("getHelpFileContentBridge")

        private val interfaceSupportsErrorInfoStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                ManagedErrorInfoComObject::class.java,
                "interfaceSupportsErrorInfoBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    MemorySegment::class.java,
                    MemorySegment::class.java,
                ),
            ),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
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
            val host = registry[pointerKey(thisPointer)] ?: return KnownHResults.RO_E_CLOSED.value
            return host.queryInterface(Guid.readFrom(iidPointer), resultPointer)
        }

        @JvmStatic
        private fun addRefBridge(thisPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.addReference() ?: 0

        @JvmStatic
        private fun releaseBridge(thisPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.releaseReference() ?: 0

        @JvmStatic
        private fun getGuidBridge(thisPointer: MemorySegment, resultPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.getGuid(resultPointer) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun getSourceBridge(thisPointer: MemorySegment, resultPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.getSource(resultPointer) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun getDescriptionBridge(thisPointer: MemorySegment, resultPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.getDescription(resultPointer) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun getHelpFileBridge(thisPointer: MemorySegment, resultPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.getHelpFile(resultPointer) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun getHelpFileContentBridge(thisPointer: MemorySegment, resultPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.getHelpFileContent(resultPointer) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun interfaceSupportsErrorInfoBridge(
            thisPointer: MemorySegment,
            interfaceIdPointer: MemorySegment,
        ): Int = registry[pointerKey(thisPointer)]?.interfaceSupportsErrorInfo(interfaceIdPointer)
            ?: KnownHResults.RO_E_CLOSED.value

        private fun createOutPointerStub(methodName: String): MemorySegment =
            linker.upcallStub(
                lookup.findStatic(
                    ManagedErrorInfoComObject::class.java,
                    methodName,
                    MethodType.methodType(
                        Int::class.javaPrimitiveType,
                        MemorySegment::class.java,
                        MemorySegment::class.java,
                    ),
                ),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                sharedArena,
            )

        private fun pointerKey(pointer: MemorySegment): Long = pointer.address()
    }
}
