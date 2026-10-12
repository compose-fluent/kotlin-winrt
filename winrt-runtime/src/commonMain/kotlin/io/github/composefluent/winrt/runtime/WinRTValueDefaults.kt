package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/** CLR default(T) for WinRT value types, decoded by the registered ABI adapter. */
internal fun defaultWinRTValue(type: KClass<*>): Any {
    val adapter = ValueBoxingInterop.requireValueAdapter(type)
    return PlatformAbi.confinedScope().use { scope ->
        val memory = PlatformAbi.allocateBytes(scope, adapter.abiLayout.byteSize, adapter.abiLayout.byteAlignment)
        PlatformAbi.zeroBytes(memory, adapter.abiLayout.byteSize)
        adapter.readValue(memory)
    }
}
