package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/** CLR default(T) for WinRT value types, decoded by the registered ABI adapter. */
internal fun defaultWinRTValue(type: KClass<*>): Any {
    Projections.isTypeWindowsRuntimeType(type)
    ValueBoxingMetadata.enumMetadataForClass(type)?.let { return it.fromAbiBits(0) }
    val descriptor = requireNotNull(ValueBoxingMetadata.descriptorForClass(type)) { "Unregistered WinRT value type: $type" }
    val interfaceId = requireNotNull(descriptor.nullableInterfaceId) { "WinRT value type cannot be boxed: $type" }
    val adapter = requireNotNull(ValueBoxingInterop.adapterForReferenceInterface(interfaceId)) {
        "WinRT value type has no ABI adapter: $type"
    }
    return PlatformAbi.confinedScope().use { scope ->
        val memory = PlatformAbi.allocateBytes(scope, adapter.abiLayout.byteSize, adapter.abiLayout.byteAlignment)
        PlatformAbi.zeroBytes(memory, adapter.abiLayout.byteSize)
        adapter.readValue(memory)
    }
}
