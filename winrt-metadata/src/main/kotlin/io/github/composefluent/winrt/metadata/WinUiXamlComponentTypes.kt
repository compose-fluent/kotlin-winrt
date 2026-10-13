package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.XamlFramework

/** Component resources follow the SDK ResourceDictionary roots selected for projection. */
fun WinRTMetadataModel.xamlComponentResourceDictionaryTypes(includedTypeNames: Set<String>): List<String> =
    XamlFramework.entries.filter { it.xamlTypeName("ResourceDictionary") in includedTypeNames }
        .flatMap { winUiXamlResourceDictionaryTypes(it) }.distinct().sorted()

/** WinUI component discovery is based on metadata inheritance, including indirect bases. */
fun WinRTMetadataModel.winUiXamlResourceDictionaryTypes(framework: XamlFramework = XamlFramework.WinUi): List<String> {
    val resolver = closureResolver()
    return namespaces.flatMap { it.types }
        .filter { it.kind == WinRTTypeKind.RuntimeClass && resolver.derivesFrom(it, framework.xamlTypeName("ResourceDictionary")) }
        .map { it.qualifiedName }
        .distinct()
        .sorted()
}

fun WinRTMetadataModel.winUiXamlMetadataProviderTypes(framework: XamlFramework = XamlFramework.WinUi): List<String> {
    val resolver = closureResolver()
    return namespaces.flatMap { it.types }
        .filter { it.kind == WinRTTypeKind.RuntimeClass && resolver.implementsInterface(it, framework.xamlTypeName("Markup.IXamlMetadataProvider")) }
        .map { it.qualifiedName }
        .distinct()
        .sorted()
}
