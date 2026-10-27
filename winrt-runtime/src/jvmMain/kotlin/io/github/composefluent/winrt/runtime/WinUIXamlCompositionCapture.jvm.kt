package io.github.composefluent.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal actual fun platformCreateXamlD3DDevice(procedure: RawAddress, driver: Int, output: RawAddress): Int {
    val pointer = ValueLayout.ADDRESS
    val int = ValueLayout.JAVA_INT
    val function = Linker.nativeLinker().downcallHandle(MemorySegment.ofAddress(procedure.value),
        FunctionDescriptor.of(int, pointer, int, pointer, int, pointer, int, int, pointer, pointer, pointer))
    return function.invokeWithArguments(MemorySegment.NULL, driver, MemorySegment.NULL, 0x20,
        MemorySegment.NULL, 0, 7, MemorySegment.ofAddress(output.value), MemorySegment.NULL, MemorySegment.NULL) as Int
}

internal actual fun platformWrapXamlD3DDevice(procedure: RawAddress, device: RawAddress, output: RawAddress): Int =
    invokeCapturePointerPair(procedure, device, output)

private fun invokeCapturePointerPair(procedure: RawAddress, device: RawAddress, output: RawAddress): Int =
    Linker.nativeLinker().downcallHandle(MemorySegment.ofAddress(procedure.value),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
        .invokeWithArguments(MemorySegment.ofAddress(device.value), MemorySegment.ofAddress(output.value)) as Int

internal actual fun platformReadXamlCaptureWindow(procedure: RawAddress, window: RawAddress, output: RawAddress): Int =
    invokeCapturePointerPair(procedure, window, output)

internal actual fun platformXamlCaptureDwmAttribute(procedure: RawAddress, window: RawAddress, attribute: Int, output: RawAddress, bytes: Int): Int =
    Linker.nativeLinker().downcallHandle(MemorySegment.ofAddress(procedure.value),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
        .invokeWithArguments(MemorySegment.ofAddress(window.value), attribute, MemorySegment.ofAddress(output.value), bytes) as Int
