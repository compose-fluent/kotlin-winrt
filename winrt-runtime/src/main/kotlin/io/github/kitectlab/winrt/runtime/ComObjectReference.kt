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
    private val state = ComObjectReferenceState()

    init {
        require(pointer != MemorySegment.NULL) {
            "COM object reference cannot wrap a null pointer."
        }
        if (referenceTrackerPointer != MemorySegment.NULL) {
            attachReferenceTracker(referenceTrackerPointer, addRefFromTrackerSource = true)
        }
    }

    val isDisposed: Boolean
        get() = state.isDisposed

    val hasReferenceTracker: Boolean
        get() = state.hasReferenceTracker

    internal val referenceTrackerHandle: MemorySegment
        get() = state.referenceTrackerHandle.asMemorySegment()

    fun addRef(): UInt {
        val count = invokeUIntMethod(IUnknownVftblSlots.AddRef)
        addRefFromTrackerSource()
        return count
    }

    fun release(): UInt {
        releaseFromTrackerSource()
        return invokeUIntMethod(IUnknownVftblSlots.Release)
    }

    fun getRef(): MemorySegment {
        addRef()
        return pointer
    }

    open fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? {
        val (hResult, resultPointer) = queryInterfacePointer(requestedInterfaceId)
        if (hResult == KnownHResults.E_NOINTERFACE || resultPointer == MemorySegment.NULL) {
            return null
        }
        WinRtPlatformApi.checkSucceededRaw(hResult.value)
        return ComObjectReference(
            pointer = resultPointer,
            interfaceId = requestedInterfaceId,
            referenceTrackerPointer = referenceTrackerHandle,
            preventReleaseOnDispose = preventReleaseOnDispose,
        )
    }

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean {
        if (referenceTrackerHandle != MemorySegment.NULL) {
            return true
        }

        val (hResult, trackerPointer) = queryInterfacePointer(IID.IReferenceTracker)
        if (hResult == KnownHResults.E_NOINTERFACE || trackerPointer == MemorySegment.NULL) {
            return false
        }
        WinRtPlatformApi.checkSucceededRaw(hResult.value)
        try {
            attachReferenceTracker(trackerPointer, addRefFromTrackerSource)
        } finally {
            invokeUIntMethodUncheckedOnPointer(trackerPointer.asNativePointer(), IUnknownVftblSlots.Release)
        }
        return true
    }

    fun queryInterface(requestedInterfaceId: Guid): Result<ComObjectReference> {
        return runCatching {
            tryQueryInterface(requestedInterfaceId)
                ?: throw WinRtUnsupportedOperationException(
                    "QueryInterface failed for $requestedInterfaceId with ${KnownHResults.E_NOINTERFACE}",
                    KnownHResults.E_NOINTERFACE,
                )
        }
    }

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

    fun sameIdentity(other: ComObjectReference): Boolean {
        throwIfDisposed()
        other.throwIfDisposed()

        val thisIdentity = tryQueryInterface(IID.IUnknown)?.let { IUnknownReference(it.pointer, IID.IUnknown) }
            ?: return false
        val otherIdentity = try {
            other.tryQueryInterface(IID.IUnknown)?.let { IUnknownReference(it.pointer, IID.IUnknown) }
                ?: return false
        } catch (error: Throwable) {
            thisIdentity.close()
            throw error
        }

        return try {
            thisIdentity.pointer == otherIdentity.pointer
        } finally {
            thisIdentity.close()
            otherIdentity.close()
        }
    }

    override fun close() {
        if (state.beginDispose()) {
            try {
                if (!preventReleaseOnDispose) {
                    releaseFromTrackerSource()
                    invokeUIntMethodUnchecked(IUnknownVftblSlots.Release)
                }
            } finally {
                disposeReferenceTracker()
            }
        }
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

    private fun invokeUIntMethodUnchecked(slot: Int): UInt =
        invokeIntMethodUnchecked(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            pointer,
        ).toUInt()

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
        if (state.isDisposed) {
            throw WinRtObjectDisposedException("Object reference is disposed.")
        }
    }

    private fun queryInterfacePointer(requestedInterfaceId: Guid): Pair<HResult, MemorySegment> {
        throwIfDisposed()
        Arena.ofConfined().use { arena ->
            val iidMemory = arena.allocate(AbiLayouts.GUID)
            requestedInterfaceId.writeTo(iidMemory)
            val resultPtr = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethodUnchecked(
                slot = IUnknownVftblSlots.QueryInterface,
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
            return HResult(hr) to resultPtr.get(ValueLayout.ADDRESS, 0)
        }
    }

    private fun attachReferenceTracker(
        trackerPointer: MemorySegment,
        addRefFromTrackerSource: Boolean,
    ) {
        state.attachReferenceTracker(
            trackerPointer = trackerPointer.asNativePointer(),
            addRefFromTrackerSource = addRefFromTrackerSource,
            retainTrackerPointer = { invokeUIntMethodUncheckedOnPointer(it, IUnknownVftblSlots.AddRef) },
            addRefFromTrackerSourceCallback = { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) },
        )
    }

    private fun addRefFromTrackerSource() {
        state.addRefFromTrackerSourceIfNeeded {
            invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource)
        }
    }

    private fun releaseFromTrackerSource() {
        state.releaseFromTrackerSourceIfNeeded {
            invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.ReleaseFromTrackerSource)
        }
    }

    private fun disposeReferenceTracker() {
        state.disposeReferenceTracker {
            invokeUIntMethodUncheckedOnPointer(it, IUnknownVftblSlots.Release)
        }
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
