package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

private fun hResultDescriptor(
    vararg argumentLayouts: NativeValueLayout,
): NativeFunctionDescriptor = NativeFunctionDescriptor.of(NativeValueLayout.JAVA_INT, *argumentLayouts)

private fun FunctionDescriptor.toNativeDescriptor(): NativeFunctionDescriptor {
    val arguments = argumentLayouts().map(MemoryLayout::toNativeValueLayout).toTypedArray()
    val returnLayout = returnLayout()
    return if (returnLayout.isPresent) {
        NativeFunctionDescriptor.of(returnLayout.get().toNativeValueLayout(), *arguments)
    } else {
        NativeFunctionDescriptor.ofVoid(*arguments)
    }
}

private fun MemoryLayout.toNativeValueLayout(): NativeValueLayout =
    when (this) {
        ValueLayout.ADDRESS -> NativeValueLayout.ADDRESS
        ValueLayout.JAVA_BYTE -> NativeValueLayout.JAVA_BYTE
        ValueLayout.JAVA_INT -> NativeValueLayout.JAVA_INT
        ValueLayout.JAVA_LONG -> NativeValueLayout.JAVA_LONG
        ValueLayout.JAVA_DOUBLE -> NativeValueLayout.JAVA_DOUBLE
        else -> error("Unsupported JVM FFM layout '$this' in runtime compatibility bridge.")
    }

private fun Array<out Any?>.toNativeInvokeArguments(): Array<out Any?> =
    map { argument ->
        if (argument is MemorySegment) {
            argument.asNativePointer()
        } else {
            argument
        }
    }.toTypedArray()

private fun trimExplicitThisArgument(
    ownerPointer: NativePointer,
    descriptor: FunctionDescriptor,
    args: Array<out Any?>,
): Array<out Any?> {
    if (descriptor.argumentLayouts().isEmpty() || descriptor.argumentLayouts().first() != ValueLayout.ADDRESS || args.isEmpty()) {
        return args
    }
    val firstPointer = when (val first = args.first()) {
        is MemorySegment -> first.asNativePointer()
        is NativePointer -> first
        else -> null
    } ?: return args
    return if (NativeInterop.samePointer(ownerPointer, firstPointer)) {
        args.copyOfRange(1, args.size)
    } else {
        args
    }
}

open class ComObjectReference(
    val pointer: NativePointer,
    val interfaceId: Guid,
    referenceTrackerPointer: NativePointer = NativeInterop.nullPointer,
    private val preventReleaseOnDispose: Boolean = false,
) : AutoCloseable {
    constructor(
        pointer: MemorySegment,
        interfaceId: Guid,
        referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
        preventReleaseOnDispose: Boolean = false,
    ) : this(
        pointer = pointer.asNativePointer(),
        interfaceId = interfaceId,
        referenceTrackerPointer = referenceTrackerPointer.asNativePointer(),
        preventReleaseOnDispose = preventReleaseOnDispose,
    )

    private val support = RawComObjectReferenceSupport(
        pointer = pointer,
        interfaceId = interfaceId,
        preventReleaseOnDispose = preventReleaseOnDispose,
    )

    init {
        require(!NativeInterop.isNull(pointer)) {
            "COM object reference cannot wrap a null pointer."
        }
        if (!NativeInterop.isNull(referenceTrackerPointer)) {
            support.attachReferenceTracker(
                trackerPointer = referenceTrackerPointer,
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

    internal open val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.Unknown

    internal val referenceTrackerHandle: NativePointer
        get() = support.referenceTrackerHandle

    fun addRef(): UInt =
        support.addRef { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) }

    fun release(): UInt =
        support.release { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.ReleaseFromTrackerSource) }

    fun getRef(): MemorySegment = getRefPointer().asMemorySegment()

    fun getRefPointer(): NativePointer =
        support.getRef { invokeReferenceTrackerMethod(it, ReferenceTrackerVftblSlots.AddRefFromTrackerSource) }

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

    open fun invokeObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = hResultDescriptor(
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    value.pointer,
                    resultOut,
                )
            },
            wrap = ::IUnknownReference,
        )

    open fun invokeBooleanMethodWithObjectArg(slot: Int, value: ComObjectReference): Boolean =
        RawAbiResultSupport.booleanResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = hResultDescriptor(
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    value.pointer,
                    resultOut,
                )
            },
        )

    open fun invokeBooleanMethodWithTwoObjectArgs(
        slot: Int,
        first: ComObjectReference,
        second: ComObjectReference,
    ): Boolean =
        RawAbiResultSupport.booleanResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = hResultDescriptor(
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    first.pointer,
                    second.pointer,
                    resultOut,
                )
            },
        )

    open fun invokeObjectMethod(slot: Int): IUnknownReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = hResultDescriptor(
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    resultOut,
                )
            },
            wrap = ::IUnknownReference,
        )

    fun invokeHStringMethod(slot: Int): HString =
        RawAbiResultSupport.hStringResult { resultOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = hResultDescriptor(
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
        }

    fun invokeDoubleMethod(slot: Int): Double =
        RawAbiResultSupport.doubleResult { resultOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = hResultDescriptor(
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
        }

    open fun invokeInt32Method(slot: Int): Int =
        RawAbiResultSupport.int32Result { resultOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = hResultDescriptor(
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
        }

    open fun invokeUnitMethod(slot: Int) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = hResultDescriptor(NativeValueLayout.ADDRESS),
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun invokeUnitMethodWithObjectArg(slot: Int, value: ComObjectReference) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = hResultDescriptor(
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
            value.pointer,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    fun invokeUnitMethodWithInt64Arg(slot: Int, value: Long) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = hResultDescriptor(
                NativeValueLayout.ADDRESS,
                NativeValueLayout.JAVA_LONG,
            ),
            value,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun invokeBooleanMethod(slot: Int): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = hResultDescriptor(
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
        }

    open fun invokeUInt32Method(slot: Int): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = hResultDescriptor(
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
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
            descriptor = hResultDescriptor(NativeValueLayout.ADDRESS),
        ).toUInt()

    fun invokeAbi(
        slot: Int,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int = invokeIntMethod(slot, descriptor, *args)

    fun invokeAbi(
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any?,
    ): Int {
        val adjustedArgs = trimExplicitThisArgument(pointer, descriptor, args)
        return invokeAbi(slot, descriptor.toNativeDescriptor(), *adjustedArgs.toNativeInvokeArguments())
    }

    private fun invokeUIntMethodUncheckedOnPointer(
        targetPointer: NativePointer,
        slot: Int,
    ): UInt =
        NativeInterop.invokeVtableInt32(
            instance = targetPointer,
            slot = slot,
            descriptor = hResultDescriptor(NativeValueLayout.ADDRESS),
        ).toUInt()

    protected fun invokeIntMethod(
        slot: Int,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int {
        throwIfDisposed()
        return NativeInterop.invokeVtableInt32(pointer, slot, descriptor, *args)
    }

    protected fun invokeIntMethod(
        slot: Int,
        descriptor: FunctionDescriptor,
        vararg args: Any?,
    ): Int {
        val adjustedArgs = trimExplicitThisArgument(pointer, descriptor, args)
        return invokeIntMethod(slot, descriptor.toNativeDescriptor(), *adjustedArgs.toNativeInvokeArguments())
    }

    protected fun throwIfDisposed() {
        support.throwIfDisposed()
    }

    private fun invokeReferenceTrackerMethod(
        trackerPointer: NativePointer,
        slot: Int,
    ): UInt =
        NativeInterop.invokeVtableInt32(
            instance = trackerPointer,
            slot = slot,
            descriptor = hResultDescriptor(NativeValueLayout.ADDRESS),
        ).toUInt()

    private fun wrapQueriedReference(
        queriedPointer: NativePointer,
        queriedInterfaceId: Guid,
        trackerHandle: NativePointer,
        queriedPreventReleaseOnDispose: Boolean,
    ): ComObjectReference =
        ComObjectReference(
            pointer = queriedPointer,
            interfaceId = queriedInterfaceId,
            referenceTrackerPointer = trackerHandle,
            preventReleaseOnDispose = queriedPreventReleaseOnDispose,
        )
}

open class IUnknownReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IUnknown,
    referenceTrackerPointer: NativePointer = NativeInterop.nullPointer,
    preventReleaseOnDispose: Boolean = false,
) : ComObjectReference(pointer, interfaceId, referenceTrackerPointer, preventReleaseOnDispose) {
    constructor(
        pointer: MemorySegment,
        interfaceId: Guid = IID.IUnknown,
        referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
        preventReleaseOnDispose: Boolean = false,
    ) : this(
        pointer = pointer.asNativePointer(),
        interfaceId = interfaceId,
        referenceTrackerPointer = referenceTrackerPointer.asNativePointer(),
        preventReleaseOnDispose = preventReleaseOnDispose,
    )
}

class ActivationFactoryReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IActivationFactory,
) : IUnknownReference(pointer, interfaceId) {
    constructor(
        pointer: MemorySegment,
        interfaceId: Guid = IID.IActivationFactory,
    ) : this(pointer.asNativePointer(), interfaceId)

    internal override val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.ActivationFactory

    fun activateInstance(): IInspectableReference =
        ActivationFactoryReferenceSupport.activateInstance(
            invokeActivate = { instanceOut ->
                invokeIntMethod(
                    slot = 6,
                    descriptor = hResultDescriptor(
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    instanceOut,
                )
            },
            wrapInspectable = { inspectedPointer -> IInspectableReference(inspectedPointer, IID.IInspectable) },
            initializeReferenceTracker = { it.tryInitializeReferenceTracker() },
        )
}

class InspectableReference(
    pointer: NativePointer,
    interfaceId: Guid = IID.IInspectable,
) : ComObjectReference(pointer, interfaceId), IWinRTObject {
    constructor(
        pointer: MemorySegment,
        interfaceId: Guid = IID.IInspectable,
    ) : this(pointer.asNativePointer(), interfaceId)

    internal override val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.Inspectable

    override val nativeObject: ComObjectReference
        get() = this

    fun tryGetRuntimeClassName(): String? = getRuntimeClassName(noThrow = true)

    fun getRuntimeClassName(noThrow: Boolean = false): String? =
        InspectableReferenceSupport.getRuntimeClassName(
            noThrow = noThrow,
            invokeGetRuntimeClassName = { hStringOut ->
                invokeIntMethod(
                    slot = 4,
                    descriptor = hResultDescriptor(
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    hStringOut,
                )
            },
        )
}

typealias IInspectableReference = InspectableReference
