package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

@Suppress("FunctionName")
internal fun WinRtInspectableMethodDefinition(
    descriptor: FunctionDescriptor,
    handler: (Array<Any?>) -> Int,
): WinRtInspectableMethodDefinition =
    WinRtInspectableMethodDefinition(
        descriptor = descriptor.toNativeDescriptor(),
        handler = { args ->
            handler(
                args.map { argument ->
                    when (argument) {
                        is NativePointer -> argument.asMemorySegment()
                        else -> argument
                    }
                }.toTypedArray(),
            )
        },
    )

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
        else -> error("Unsupported JVM FFM layout '$this' in inspectable host compatibility bridge.")
    }
