package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/** Mapped CLR scalar converters live with their runtime type; WinUI owns other XAML conversions. */
fun convertWinRTXamlLiteral(
    type: KClass<*>,
    text: String,
    sdkConvert: (KClass<*>, String) -> Any?,
): Any? = WinRTTypeClassifier.classify(type)?.xamlLiteralParser?.invoke(text) ?: sdkConvert(type, text)
