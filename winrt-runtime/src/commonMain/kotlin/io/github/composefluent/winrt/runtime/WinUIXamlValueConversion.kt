package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/** Mapped CLR scalar converters live with their runtime type; WinUI owns other XAML conversions. */
fun convertWinUIXamlLiteral(
    type: KClass<*>,
    text: String,
    sdkConvert: (KClass<*>, String) -> Any?,
): Any? = WinUIAuthoredTypeMetadata.literalParser(type)?.invoke(text)
    ?: WinRTTypeClassifier.classify(type)?.xamlLiteralParser?.invoke(text) ?: sdkConvert(type, text)

/** Zero-initialized WinRT value fields for generated x:Property declarations. Reuse the same enum
 * and struct adapters that already own ABI decoding; never classify them here.
 */
fun defaultWinUIXamlValue(type: KClass<*>): Any = defaultWinRTValue(type)
