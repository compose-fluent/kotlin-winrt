package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.atomic.AtomicBoolean

open class ComObjectReference(
    val pointer: MemorySegment,
    val interfaceId: Guid,
    referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
    private val preventReleaseOnDispose: Boolean = false,
) : AutoCloseable {
    private val disposed = AtomicBoolean(false)
    private var referenceTrackerPointer: MemorySegment = MemorySegment.NULL
    private var releaseFromTrackerSourceOnDispose: Boolean = false

    init {
        require(pointer != MemorySegment.NULL) {
            "COM object reference cannot wrap a null pointer."
        }
        if (referenceTrackerPointer != MemorySegment.NULL) {
            attachReferenceTracker(referenceTrackerPointer, addRefFromTrackerSource = true)
        }
    }

    val isDisposed: Boolean
        get() = disposed.get()

    val hasReferenceTracker: Boolean
        get() = referenceTrackerPointer != MemorySegment.NULL

    internal val referenceTrackerHandle: MemorySegment
        get() = referenceTrackerPointer

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

    fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? {
        val (hResult, resultPointer) = queryInterfacePointer(requestedInterfaceId)
        if (hResult == KnownHResults.E_NOINTERFACE || resultPointer == MemorySegment.NULL) {
            return null
        }
        WindowsRuntimePlatform.checkSucceeded(hResult.value)
        return ComObjectReference(
            pointer = resultPointer,
            interfaceId = requestedInterfaceId,
            referenceTrackerPointer = referenceTrackerPointer,
            preventReleaseOnDispose = preventReleaseOnDispose,
        )
    }

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean {
        if (referenceTrackerPointer != MemorySegment.NULL) {
            return true
        }

        val (hResult, trackerPointer) = queryInterfacePointer(IID.IReferenceTracker)
        if (hResult == KnownHResults.E_NOINTERFACE || trackerPointer == MemorySegment.NULL) {
            return false
        }
        WindowsRuntimePlatform.checkSucceeded(hResult.value)
        try {
            attachReferenceTracker(trackerPointer, addRefFromTrackerSource)
        } finally {
            invokeUIntMethodUncheckedOnPointer(trackerPointer, IUnknownVftblSlots.Release)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
            return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    open fun invokeObjectMethodWithUInt32Arg(slot: Int, value: UInt): IUnknownReference {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.toInt(),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    fun invokeObjectMethodWithBooleanArg(slot: Int, value: Boolean): IUnknownReference {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_BYTE,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                if (value) 1.toByte() else 0.toByte(),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
        }
    }

    fun invokeObjectMethodWithDoubleArg(slot: Int, value: Double): IUnknownReference {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value,
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
            WindowsRuntimePlatform.checkSucceeded(hr)
            return HString.fromHandle(resultOut.get(ValueLayout.ADDRESS, 0), owner = true)
        }
    }

    fun invokeHStringMethodWithUInt32Arg(slot: Int, value: UInt): HString {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.toInt(),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return HString.fromHandle(resultOut.get(ValueLayout.ADDRESS, 0), owner = true)
        }
    }

    open fun invokeUnitMethodWithStringArg(slot: Int, value: String) {
        HString.create(value).use { hString ->
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                hString.handle,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
        }
    }

    open fun invokeUnitMethodWithUInt32Arg(slot: Int, value: UInt) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
            pointer,
            value.toInt(),
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    open fun invokeUnitMethodWithBooleanArg(slot: Int, value: Boolean) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_BYTE,
            ),
            pointer,
            if (value) 1.toByte() else 0.toByte(),
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    open fun invokeUnitMethodWithDoubleArg(slot: Int, value: Double) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_DOUBLE,
            ),
            pointer,
            value,
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
            return resultOut.get(ValueLayout.JAVA_DOUBLE, 0)
        }
    }

    fun invokeDoubleMethodWithStringArg(slot: Int, value: String): Double {
        HString.create(value).use { hString ->
            Arena.ofConfined().use { arena ->
                val resultOut = arena.allocate(ValueLayout.JAVA_DOUBLE)
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
                return resultOut.get(ValueLayout.JAVA_DOUBLE, 0)
            }
        }
    }

    fun invokeDoubleMethodWithUInt32Arg(slot: Int, value: UInt): Double {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_DOUBLE)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.toInt(),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return resultOut.get(ValueLayout.JAVA_DOUBLE, 0)
        }
    }

    fun invokeBooleanMethodWithStringArg(slot: Int, value: String): Boolean {
        HString.create(value).use { hString ->
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
                    hString.handle,
                    resultOut,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                return resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
            }
        }
    }

    fun invokeBooleanMethodWithUInt32Arg(slot: Int, value: UInt): Boolean {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.toInt(),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return resultOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
        }
    }

    fun invokeUInt32MethodWithInt32Arg(slot: Int, value: Int): UInt {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value,
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return resultOut.get(ValueLayout.JAVA_INT, 0).toUInt()
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
            WindowsRuntimePlatform.checkSucceeded(hr)
            return resultOut.get(ValueLayout.JAVA_INT, 0)
        }
    }

    fun invokeTryParseObjectMethodWithStringArg(slot: Int, value: String): Pair<IUnknownReference?, Boolean> {
        HString.create(value).use { hString ->
            Arena.ofConfined().use { arena ->
                val objectOut = arena.allocate(ValueLayout.ADDRESS)
                val successOut = arena.allocate(ValueLayout.JAVA_BYTE)
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
                    hString.handle,
                    objectOut,
                    successOut,
                )
                WindowsRuntimePlatform.checkSucceeded(hr)
                val succeeded = successOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0
                val resultPointer = objectOut.get(ValueLayout.ADDRESS, 0)
                val result = if (resultPointer == MemorySegment.NULL) null else IUnknownReference(resultPointer)
                return result to succeeded
            }
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
        WindowsRuntimePlatform.checkSucceeded(hr)
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
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    fun invokeUnitMethodWithStringAndObjectArg(slot: Int, name: String, value: ComObjectReference) {
        HString.create(name).use { hString ->
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
                value.pointer,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
        }
    }

    fun invokeInt64MethodWithObjectArg(slot: Int, value: ComObjectReference): Long {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.JAVA_LONG)
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
            return resultOut.get(ValueLayout.JAVA_LONG, 0)
        }
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
        WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
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
            WindowsRuntimePlatform.checkSucceeded(hr)
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
        if (disposed.compareAndSet(false, true)) {
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
        targetPointer: MemorySegment,
        slot: Int,
    ): UInt {
        val method = Linker.nativeLinker().downcallHandle(
            RawVtableCallSupport.entry(targetPointer, slot),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
        return (method.invokeWithArguments(targetPointer) as Int).toUInt()
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
        if (isDisposed) {
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
        if (referenceTrackerPointer != MemorySegment.NULL) {
            return
        }
        referenceTrackerPointer = trackerPointer
        invokeUIntMethodUncheckedOnPointer(referenceTrackerPointer, IUnknownVftblSlots.AddRef)
        if (addRefFromTrackerSource) {
            invokeReferenceTrackerMethod(ReferenceTrackerVftblSlots.AddRefFromTrackerSource)
            releaseFromTrackerSourceOnDispose = true
        }
    }

    private fun addRefFromTrackerSource() {
        if (!hasReferenceTracker) {
            return
        }
        invokeReferenceTrackerMethod(ReferenceTrackerVftblSlots.AddRefFromTrackerSource)
    }

    private fun releaseFromTrackerSource() {
        if (!hasReferenceTracker || !releaseFromTrackerSourceOnDispose) {
            return
        }
        invokeReferenceTrackerMethod(ReferenceTrackerVftblSlots.ReleaseFromTrackerSource)
    }

    private fun disposeReferenceTracker() {
        if (referenceTrackerPointer == MemorySegment.NULL) {
            return
        }
        invokeUIntMethodUncheckedOnPointer(referenceTrackerPointer, IUnknownVftblSlots.Release)
        referenceTrackerPointer = MemorySegment.NULL
        releaseFromTrackerSourceOnDispose = false
    }

    private fun invokeReferenceTrackerMethod(slot: Int): UInt {
        val method = Linker.nativeLinker().downcallHandle(
            RawVtableCallSupport.entry(referenceTrackerPointer, slot),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
        )
        return (method.invokeWithArguments(referenceTrackerPointer) as Int).toUInt()
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
            WindowsRuntimePlatform.checkSucceeded(hr)
            return IInspectableReference(instanceOut.get(ValueLayout.ADDRESS, 0), IID.IInspectable).also {
                it.tryInitializeReferenceTracker()
            }
        }
    }
}

class InspectableReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IInspectable,
) : ComObjectReference(pointer, interfaceId) {
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
    val IReferenceTracker: Guid = Guid("11D3B13A-180E-4789-A8BE-7712882893E6")
    val IReferenceTrackerTarget: Guid = Guid("64BD43F8-BFEE-4EC4-B7EB-2935158DAE21")
}

object ReferenceTrackerVftblSlots {
    const val ConnectFromTrackerSource = 3
    const val DisconnectFromTrackerSource = 4
    const val FindTrackerTargets = 5
    const val GetReferenceTrackerManager = 6
    const val AddRefFromTrackerSource = 7
    const val ReleaseFromTrackerSource = 8
    const val PegFromTrackerSource = 9
}
