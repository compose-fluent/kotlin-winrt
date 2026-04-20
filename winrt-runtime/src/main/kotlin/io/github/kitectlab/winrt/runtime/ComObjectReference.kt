package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

open class ComObjectReference(
    val pointer: MemorySegment,
    val interfaceId: Guid,
    referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
    private val preventReleaseOnDispose: Boolean = false,
) : AutoCloseable {
    private val support = RawComObjectReferenceSupport(
        pointer = pointer.asNativePointer(),
        interfaceId = interfaceId,
        preventReleaseOnDispose = preventReleaseOnDispose,
    )

    init {
        require(pointer != MemorySegment.NULL) {
            "COM object reference cannot wrap a null pointer."
        }
        if (referenceTrackerPointer != MemorySegment.NULL) {
            support.attachReferenceTracker(
                trackerPointer = referenceTrackerPointer.asNativePointer(),
                addRefFromTrackerSource = true,
                retainTrackerPointer = { invokeUIntMethodUncheckedOnPointer(it, IUnknownVftblSlots.AddRef) },
                addRefFromTrackerSourceCallback = { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) },
            )
        }
    }

    val isDisposed: Boolean
        get() = support.isDisposed

    val hasReferenceTracker: Boolean
        get() = support.hasReferenceTracker

    internal val referenceTrackerHandle: MemorySegment
        get() = support.referenceTrackerHandle.asMemorySegment()

    fun addRef(): UInt =
        support.addRef { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) }

    fun release(): UInt =
        support.release { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.ReleaseFromTrackerSource) }

    fun getRef(): MemorySegment =
        support.getRef { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) }
            .asMemorySegment()

    open fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? =
        support.tryQueryInterface(requestedInterfaceId, ::wrapQueriedReference)

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        support.tryInitializeReferenceTracker(
            addRefFromTrackerSource = addRefFromTrackerSource,
            retainTrackerPointer = { invokeUIntMethodUncheckedOnPointer(it, IUnknownVftblSlots.AddRef) },
            addRefFromTrackerSourceCallback = { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) },
        )

    fun queryInterface(requestedInterfaceId: Guid): Result<ComObjectReference> =
        support.queryInterface(requestedInterfaceId, ::wrapQueriedReference)

    fun tryAsInspectable(): IInspectableReference? =
        tryQueryInterface(IID.IInspectable)?.let { IInspectableReference(it.pointer, IID.IInspectable) }

    open fun invokeObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference {
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
            WinRtPlatformApi.checkSucceededRaw(hr)
            return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    open fun invokeBooleanMethodWithObjectArg(slot: Int, value: ComObjectReference): Boolean {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_BYTE)
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
            WinRtPlatformApi.checkSucceededRaw(hr)
            return resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
        }
    }

    open fun invokeBooleanMethodWithTwoObjectArgs(
        slot: Int,
        first: ComObjectReference,
        second: ComObjectReference,
    ): Boolean {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                first.pointer,
                second.pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
        }
    }

    open fun invokeObjectMethod(slot: Int): IUnknownReference {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    fun invokeHStringMethod(slot: Int): HString {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return HString.fromHandle(resultOut.get(ValueLayout.ADDRESS, 0).asNativePointer(), owner = true)
        }
    }

    fun invokeDoubleMethod(slot: Int): Double {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_DOUBLE)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return resultOut.get(ValueLayout.JAVA_DOUBLE, 0)
        }
    }

    open fun invokeInt32Method(slot: Int): Int {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return resultOut.get(ValueLayout.JAVA_INT, 0)
        }
    }

    open fun invokeUnitMethod(slot: Int) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            pointer,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun invokeUnitMethodWithObjectArg(slot: Int, value: ComObjectReference) {
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
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun invokeUnitMethodWithInt64Arg(slot: Int, value: Long) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG,
            ),
            pointer,
            value,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun invokeBooleanMethod(slot: Int): Boolean {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
        }
    }

    open fun invokeUInt32Method(slot: Int): UInt {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            return resultOut.get(ValueLayout.JAVA_INT, 0).toUInt()
        }
    }

    fun asInspectable(): IInspectableReference =
        tryAsInspectable()
            ?: throw WinRtUnsupportedOperationException(
                "QueryInterface failed for ${IID.IInspectable} with ${KnownHResults.E_NOINTERFACE}",
                KnownHResults.E_NOINTERFACE,
            )

    fun sameIdentity(other: ComObjectReference): Boolean =
        support.sameIdentity(other.support)

    override fun close() {
        support.close(
            releaseFromTrackerSourceCallback = {
                invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.ReleaseFromTrackerSource)
            },
            releaseTrackerPointer = { invokeUIntMethodUncheckedOnPointer(it, IUnknownVftblSlots.Release) },
        )
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

    fun invokeAbi(
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Int = invokeIntMethod(slot, descriptor, pointer, *args)

    private fun invokeUIntMethodUncheckedOnPointer(
        targetPointer: NativePointer,
        slot: Int,
    ): UInt {
        val method = Linker.nativeLinker().downcallHandle(
            RawVtableCallSupport.entry(targetPointer.asMemorySegment(), slot),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
        return (method.invokeWithArguments(targetPointer.asMemorySegment()) as Int).toUInt()
    }

    protected fun invokeIntMethod(
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Int {
        throwIfDisposed()
        return invokeIntMethodUnchecked(slot, descriptor, *args)
    }

    private fun invokeIntMethodUnchecked(
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any,
    ): Int {
        val method = Linker.nativeLinker().downcallHandle(vtableEntryUnchecked(slot), descriptor)
        return method.invokeWithArguments(*args) as Int
    }

    protected fun vtableEntry(slot: Int): MemorySegment {
        throwIfDisposed()
        return vtableEntryUnchecked(slot)
    }

    private fun vtableEntryUnchecked(slot: Int): MemorySegment =
        RawVtableCallSupport.entry(pointer, slot)

    protected fun throwIfDisposed() {
        support.throwIfDisposed()
    }

    private fun invokeReferenceTrackerMethod(
        trackerPointer: NativePointer,
        slot: Int,
    ): UInt {
        val method = Linker.nativeLinker().downcallHandle(
            RawVtableCallSupport.entry(trackerPointer.asMemorySegment(), slot),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
        return (method.invokeWithArguments(trackerPointer.asMemorySegment()) as Int).toUInt()
    }

    private fun wrapQueriedReference(
        queriedPointer: NativePointer,
        queriedInterfaceId: Guid,
        trackerHandle: NativePointer,
        queriedPreventReleaseOnDispose: Boolean,
    ): ComObjectReference =
        ComObjectReference(
            pointer = queriedPointer.asMemorySegment(),
            interfaceId = queriedInterfaceId,
            referenceTrackerPointer = trackerHandle.asMemorySegment(),
            preventReleaseOnDispose = queriedPreventReleaseOnDispose,
        )
}

open class IUnknownReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IUnknown,
    referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
    preventReleaseOnDispose: Boolean = false,
) : ComObjectReference(pointer, interfaceId, referenceTrackerPointer, preventReleaseOnDispose)

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
            WinRtPlatformApi.checkSucceededRaw(hr)
            return IInspectableReference(instanceOut.get(ValueLayout.ADDRESS, 0), IID.IInspectable).also {
                it.tryInitializeReferenceTracker()
            }
        }
    }
}

class InspectableReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IInspectable,
) : ComObjectReference(pointer, interfaceId), IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = this

    fun tryGetRuntimeClassName(): String? = getRuntimeClassName(noThrow = true)

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
                WinRtPlatformApi.checkSucceededRaw(hr)
            }

            val hstring = hstringOut.get(ValueLayout.ADDRESS, 0)
            if (hstring == MemorySegment.NULL) {
                return null
            }
            return HString.fromHandle(hstring.asNativePointer(), owner = true).use(HString::toKString)
        }
    }
}

typealias IInspectableReference = InspectableReference
