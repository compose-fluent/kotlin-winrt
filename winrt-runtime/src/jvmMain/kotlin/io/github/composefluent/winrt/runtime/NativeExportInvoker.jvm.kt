package io.github.composefluent.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.concurrent.ConcurrentHashMap

actual object WinRTNativeExportInvoker {
    private val linker: Linker = Linker.nativeLinker()
    private val handles = ConcurrentHashMap<String, MethodHandle>()
    private val hResultAddressAddressDescriptor: FunctionDescriptor =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    private val hResultStruct8AddressDescriptor: FunctionDescriptor =
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)

    actual fun invokeHResultAddressAddress(
        function: RawAddress,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int =
        handle(function, "hresult_address_address", hResultAddressAddressDescriptor)
            .invoke(arg0.asMemorySegment(), arg1.asMemorySegment()) as Int

    actual fun invokeHResultStruct8Address(
        function: RawAddress,
        structValue: RawAddress,
        arg1: RawAddress,
    ): Int =
        handle(function, "hresult_struct8_address", hResultStruct8AddressDescriptor)
            .invoke(PlatformAbi.readInt64(structValue), arg1.asMemorySegment()) as Int

    private fun handle(
        function: RawAddress,
        signature: String,
        descriptor: FunctionDescriptor,
    ): MethodHandle {
        require(!PlatformAbi.isNull(function)) { "Cannot call a null native export function pointer." }
        val key = "${function.value}:$signature"
        return handles.computeIfAbsent(key) {
            linker.downcallHandle(function.asMemorySegment(), descriptor)
        }
    }
}
