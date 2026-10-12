package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/** Mapped CLR scalar converters live with their runtime type; WinUI owns other XAML conversions. */
fun convertWinUiXamlLiteral(
    type: KClass<*>,
    text: String,
    sdkConvert: (KClass<*>, String) -> Any?,
): Any? = WinUiAuthoredTypeMetadata.literalParser(type)?.invoke(text)
    ?: WinRTTypeClassifier.classify(type)?.xamlLiteralParser?.invoke(text) ?: sdkConvert(type, text)

/** Zero-initialized WinRT value fields for generated x:Property declarations. Reuse the same enum
 * and struct adapters that already own ABI decoding; never classify them here.
 */
fun defaultWinUiXamlValue(type: KClass<*>): Any {
    Projections.isTypeWindowsRuntimeType(type)
    ValueBoxingMetadata.enumMetadataForClass(type)?.let { return it.fromAbiBits(0) }
    val descriptor = requireNotNull(ValueBoxingMetadata.descriptorForClass(type)) { "Unregistered XAML value type: $type" }
    val interfaceId = requireNotNull(descriptor.nullableInterfaceId) { "XAML value type cannot be boxed: $type" }
    val adapter = requireNotNull(ValueBoxingInterop.adapterForReferenceInterface(interfaceId)) {
        "XAML value type has no ABI adapter: $type"
    }
    return PlatformAbi.confinedScope().use { scope ->
        val memory = PlatformAbi.allocateBytes(scope, adapter.abiLayout.byteSize, adapter.abiLayout.byteAlignment)
        PlatformAbi.zeroBytes(memory, adapter.abiLayout.byteSize)
        adapter.readValue(memory)
    }
}
