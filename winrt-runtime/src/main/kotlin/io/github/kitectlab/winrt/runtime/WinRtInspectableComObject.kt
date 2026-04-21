package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

data class WinRtInspectableMethodDefinition(
    val descriptor: FunctionDescriptor,
    val handler: (Array<Any?>) -> Int,
)

data class WinRtInspectableInterfaceDefinition(
    val interfaceId: Guid,
    val methods: List<WinRtInspectableMethodDefinition>,
)

internal data class WinRtInspectableInfoSnapshot(
    val runtimeClassName: String?,
    val interfaceIds: List<Guid>,
)

internal class WinRtInspectableComObject(
    interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
    private val runtimeClassName: String? = null,
    private val trustLevel: Int = 0,
    private val managedValue: Any? = null,
) : ManagedReferenceHost, AutoCloseable {
    /**
     * `.cswinrt` owns CCW backing allocation through the broader ComWrappers/object-reference
     * infrastructure. The current JVM runtime has not landed that allocator/registration layer yet,
     * and Panama shared arenas cannot be safely closed here while the returned interface segments are
     * still live. Keep the backing memory in the global arena for this helper slice, and let the
     * later Runtime 1.13 / Inventory B work take ownership of precise CCW allocation reclamation.
     */
    private val arena = Arena.global()
    private val state = ManagedComHostState(::cleanup)
    private val interfaces = interfaceDefinitions.associateBy { it.interfaceId }
    private val interfaceEntries = interfaceDefinitions.associate { definition ->
        definition.interfaceId to createInterfaceEntry(definition)
    }
    private val interfaceIdsMemory = arena.allocateArray(interfaceDefinitions.map(WinRtInspectableInterfaceDefinition::interfaceId))
    private val primaryInterfaceId = interfaceDefinitions.firstOrNull()?.interfaceId
        ?: error("Inspectable COM object must expose at least one interface.")

    init {
        interfaceEntries.values.forEach { entry ->
            registry[pointerKey(entry.objectMemory)] = this
        }
    }

    override fun close() {
        releaseManagedReference()
    }

    override fun createReference(interfaceId: Guid): ComObjectReference {
        addReference()
        return ComObjectReference(interfacePointer(interfaceId), interfaceId)
    }

    fun createPrimaryReference(): ComObjectReference = createReference(primaryInterfaceId)

    fun detachReference(interfaceId: Guid = primaryInterfaceId): MemorySegment =
        ManagedReferenceHostSupport.detachReference(
            createReference = {
                addReference()
                interfacePointer(interfaceId)
            },
            releaseManagedReference = ::releaseManagedReference,
        )

    override fun releaseManagedReference() {
        releaseReference()
    }

    private fun interfacePointer(interfaceId: Guid): MemorySegment =
        when (interfaceId) {
            IID.IUnknown, IID.IInspectable -> interfaceEntries.getValue(primaryInterfaceId).objectMemory
            else -> interfaceEntries[interfaceId]?.objectMemory
        } ?: throw WinRtUnsupportedOperationException(
            "Managed COM object does not implement interface '$interfaceId'.",
            KnownHResults.E_NOINTERFACE,
        )

    private fun createInterfaceEntry(
        definition: WinRtInspectableInterfaceDefinition,
    ): WinRtInspectableInterfaceEntry {
        val objectMemory = arena.allocate(ValueLayout.ADDRESS)
        val vtableMemory = arena.allocate(
            MemoryLayout.sequenceLayout((IInspectableVftblSlots.FirstCustom + definition.methods.size).toLong(), ValueLayout.ADDRESS),
        )
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.QueryInterface.toLong(), queryInterfaceStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.AddRef.toLong(), addRefStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IUnknownVftblSlots.Release.toLong(), releaseStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IInspectableVftblSlots.GetIids.toLong(), getIidsStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IInspectableVftblSlots.GetRuntimeClassName.toLong(), getRuntimeClassNameStub)
        vtableMemory.setAtIndex(ValueLayout.ADDRESS, IInspectableVftblSlots.GetTrustLevel.toLong(), getTrustLevelStub)
        definition.methods.forEachIndexed { index, method ->
            vtableMemory.setAtIndex(
                ValueLayout.ADDRESS,
                (IInspectableVftblSlots.FirstCustom + index).toLong(),
                createMethodStub(methodToken(definition.interfaceId, index), method.descriptor),
            )
        }
        objectMemory.set(ValueLayout.ADDRESS, 0, vtableMemory)
        return WinRtInspectableInterfaceEntry(
            objectMemory = objectMemory,
            vtableMemory = vtableMemory,
        )
    }

    private fun queryInterface(requestedInterfaceId: Guid, resultPointer: MemorySegment): Int {
        val targetPointer = when (requestedInterfaceId) {
            IID.IUnknown, IID.IInspectable -> interfacePointer(primaryInterfaceId)
            else -> interfaceEntries[requestedInterfaceId]?.objectMemory
        }
        val outPointer = resultPointer.reinterpret(ValueLayout.ADDRESS.byteSize())
        val queryResult = state.queryInterface(requestedInterfaceId) { _ -> targetPointer }
        return if (queryResult.target != null) {
            outPointer.set(ValueLayout.ADDRESS, 0, queryResult.target)
            queryResult.hResult.value
        } else {
            outPointer.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
            queryResult.hResult.value
        }
    }

    private fun addReference(): Int = state.addReference()

    private fun releaseReference(): Int = state.releaseReference()

    private fun getIids(countOut: MemorySegment, idsOut: MemorySegment): Int {
        countOut.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, interfaces.size)
        idsOut.reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, interfaceIdsMemory)
        return KnownHResults.S_OK.value
    }

    private fun getRuntimeClassName(resultOut: MemorySegment): Int {
        val result = resultOut.reinterpret(ValueLayout.ADDRESS.byteSize())
        if (runtimeClassName == null) {
            result.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
            return KnownHResults.S_OK.value
        }
        result.set(ValueLayout.ADDRESS, 0, HString.create(runtimeClassName).handle.asMemorySegment())
        return KnownHResults.S_OK.value
    }

    private fun getTrustLevel(resultOut: MemorySegment): Int {
        resultOut.reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, trustLevel)
        return KnownHResults.S_OK.value
    }

    private fun invokeMethod(token: String, rawArguments: Array<Any?>): Int =
        runCatching {
            val interfaceId = token.substringBefore('#').let(::Guid)
            val methodIndex = token.substringAfter('#').toInt()
            interfaces.getValue(interfaceId).methods[methodIndex].handler(rawArguments)
        }.getOrElse { error ->
            ExceptionHelpers.setErrorInfo(error)
            WinRtExceptionTranslator.hResultFromException(error).value
        }

    private fun cleanup() {
        interfaceEntries.values.forEach { entry ->
            registry.remove(pointerKey(entry.objectMemory))
        }
    }

    private fun createMethodStub(
        token: String,
        descriptor: FunctionDescriptor,
    ): MemorySegment {
        val baseHandle = lookup.findStatic(
            WinRtInspectableComObject::class.java,
            "invokeMethodBridge",
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                MemorySegment::class.java,
                String::class.java,
                Array<Any?>::class.java,
            ),
        )
        val tokenHandle = MethodHandles.insertArguments(baseHandle, 1, token)
        val collectedHandle = tokenHandle.asCollector(Array<Any?>::class.java, descriptor.argumentLayouts().size - 1)
        val exactHandle = collectedHandle.asType(
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                listOf(MemorySegment::class.java) + descriptor.argumentLayouts().drop(1).map(::carrierClass),
            ),
        )
        return linker.upcallStub(exactHandle, descriptor, sharedArena)
    }

    private fun methodToken(interfaceId: Guid, methodIndex: Int): String = "$interfaceId#$methodIndex"

    private data class WinRtInspectableInterfaceEntry(
        val objectMemory: MemorySegment,
        @Suppress("unused")
        val vtableMemory: MemorySegment,
    )

    companion object {
        private val linker: Linker = Linker.nativeLinker()
        private val lookup = MethodHandles.lookup()
        private val sharedArena: Arena = Arena.global()
        private val registry = ConcurrentCacheMap<Long, WinRtInspectableComObject>()

        internal fun findManagedValue(pointer: NativePointer): Any? =
            registry[NativeInterop.pointerKey(pointer)]?.managedValue

        internal fun findInspectableInfo(pointer: NativePointer): WinRtInspectableInfoSnapshot? =
            registry[NativeInterop.pointerKey(pointer)]?.let { host ->
                WinRtInspectableInfoSnapshot(
                    runtimeClassName = host.runtimeClassName,
                    interfaceIds = host.interfaces.keys.toList(),
                )
            }

        internal fun inspectableBox(
            value: Any?,
            runtimeClassName: String? = null,
        ): WinRtInspectableComObject =
            WinRtInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = IID.IInspectable,
                        methods = emptyList(),
                    ),
                ),
                runtimeClassName = runtimeClassName,
                managedValue = value,
            )

        private val queryInterfaceStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtInspectableComObject::class.java,
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
                WinRtInspectableComObject::class.java,
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
                WinRtInspectableComObject::class.java,
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
        private val getIidsStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtInspectableComObject::class.java,
                "getIidsBridge",
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
        private val getRuntimeClassNameStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtInspectableComObject::class.java,
                "getRuntimeClassNameBridge",
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
        private val getTrustLevelStub: MemorySegment = linker.upcallStub(
            lookup.findStatic(
                WinRtInspectableComObject::class.java,
                "getTrustLevelBridge",
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
        ): Int =
            registry[pointerKey(thisPointer)]?.queryInterface(Guid.readFrom(iidPointer), resultPointer)
                ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun addRefBridge(thisPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.addReference() ?: 0

        @JvmStatic
        private fun releaseBridge(thisPointer: MemorySegment): Int =
            registry[pointerKey(thisPointer)]?.releaseReference() ?: 0

        @JvmStatic
        private fun getIidsBridge(
            thisPointer: MemorySegment,
            countOut: MemorySegment,
            idsOut: MemorySegment,
        ): Int =
            registry[pointerKey(thisPointer)]?.getIids(countOut, idsOut) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun getRuntimeClassNameBridge(
            thisPointer: MemorySegment,
            resultOut: MemorySegment,
        ): Int =
            registry[pointerKey(thisPointer)]?.getRuntimeClassName(resultOut) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun getTrustLevelBridge(
            thisPointer: MemorySegment,
            resultOut: MemorySegment,
        ): Int =
            registry[pointerKey(thisPointer)]?.getTrustLevel(resultOut) ?: KnownHResults.RO_E_CLOSED.value

        @JvmStatic
        private fun invokeMethodBridge(
            thisPointer: MemorySegment,
            token: String,
            rawArguments: Array<Any?>,
        ): Int =
            registry[pointerKey(thisPointer)]?.invokeMethod(token, rawArguments) ?: KnownHResults.RO_E_CLOSED.value

        private fun carrierClass(layout: MemoryLayout): Class<*> = when (layout) {
            ValueLayout.ADDRESS -> MemorySegment::class.java
            ValueLayout.JAVA_INT -> Int::class.javaPrimitiveType!!
            ValueLayout.JAVA_LONG -> Long::class.javaPrimitiveType!!
            ValueLayout.JAVA_DOUBLE -> Double::class.javaPrimitiveType!!
            ValueLayout.JAVA_BYTE -> Byte::class.javaPrimitiveType!!
            else -> error("Unsupported inspectable COM stub carrier: $layout")
        }

        private fun Arena.allocateArray(values: List<Guid>): MemorySegment {
            val memory = allocate(MemoryLayout.sequenceLayout(values.size.toLong(), AbiLayouts.GUID))
            values.forEachIndexed { index, guid ->
                guid.writeTo(memory.asSlice(index.toLong() * AbiLayouts.GUID.byteSize(), AbiLayouts.GUID.byteSize()))
            }
            return memory
        }

        private fun pointerKey(pointer: MemorySegment): Long = pointer.address()
    }
}

