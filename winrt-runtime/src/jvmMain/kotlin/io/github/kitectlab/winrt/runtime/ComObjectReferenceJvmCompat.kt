package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

fun ComObjectReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
    preventReleaseOnDispose: Boolean = false,
): ComObjectReference = ComObjectReference(
    pointer = pointer.asNativePointer(),
    interfaceId = interfaceId,
    referenceTrackerPointer = referenceTrackerPointer.asNativePointer(),
    preventReleaseOnDispose = preventReleaseOnDispose,
)

fun IUnknownReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IUnknown,
    referenceTrackerPointer: MemorySegment = MemorySegment.NULL,
    preventReleaseOnDispose: Boolean = false,
): IUnknownReference = IUnknownReference(
    pointer = pointer.asNativePointer(),
    interfaceId = interfaceId,
    referenceTrackerPointer = referenceTrackerPointer.asNativePointer(),
    preventReleaseOnDispose = preventReleaseOnDispose,
)

fun ActivationFactoryReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IActivationFactory,
): ActivationFactoryReference = ActivationFactoryReference(pointer.asNativePointer(), interfaceId)

fun InspectableReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IInspectable,
): InspectableReference = InspectableReference(pointer.asNativePointer(), interfaceId)

fun IInspectableReference(
    pointer: MemorySegment,
    interfaceId: Guid = IID.IInspectable,
): InspectableReference = InspectableReference(pointer.asNativePointer(), interfaceId)

fun ComObjectReference.getRef(): MemorySegment = getRefPointer().asMemorySegment()

fun ComObjectReference.invokeAbi(
    slot: Int,
    descriptor: FunctionDescriptor,
    vararg args: Any?,
): Int {
    val adjustedArgs = trimExplicitThisArgument(pointer, descriptor, args)
    return invokeAbi(slot, descriptor.toNativeDescriptor(), *adjustedArgs.toNativeInvokeArguments())
}

fun ComObjectReference.invokeIntMethod(
    slot: Int,
    descriptor: FunctionDescriptor,
    vararg args: Any?,
): Int {
    val adjustedArgs = trimExplicitThisArgument(pointer, descriptor, args)
    return invokeAbi(slot, descriptor.toNativeDescriptor(), *adjustedArgs.toNativeInvokeArguments())
}

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
