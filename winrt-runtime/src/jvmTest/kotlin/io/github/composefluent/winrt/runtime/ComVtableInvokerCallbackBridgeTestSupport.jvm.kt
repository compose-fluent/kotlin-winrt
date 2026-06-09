package io.github.composefluent.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout

internal actual fun invokeInt32PointerCallbackForTest(
    callbackPointer: RawAddress,
    argument: RawAddress,
): Int {
    val downcall = callbackBridgeLinker.downcallHandle(
        callbackPointer.asMemorySegment(),
        int32PointerCallbackDescriptor,
    )
    return downcall.invokeWithArguments(argument.asMemorySegment()) as Int
}

private val callbackBridgeLinker: Linker = Linker.nativeLinker()

private val int32PointerCallbackDescriptor: FunctionDescriptor =
    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
