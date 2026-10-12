package io.github.composefluent.winrt.metadata

/** WinUI component discovery is based on metadata inheritance, including indirect bases. */
fun WinRTMetadataModel.winUiXamlResourceDictionaryTypes(): List<String> {
    val resolver = closureResolver()
    return namespaces.flatMap { it.types }
        .filter { it.kind == WinRTTypeKind.RuntimeClass && resolver.derivesFrom(it, "Microsoft.UI.Xaml.ResourceDictionary") }
        .map { it.qualifiedName }
        .distinct()
        .sorted()
}

fun WinRTMetadataModel.winUiXamlMetadataProviderTypes(): List<String> {
    val resolver = closureResolver()
    return namespaces.flatMap { it.types }
        .filter { it.kind == WinRTTypeKind.RuntimeClass && resolver.implementsInterface(it, "Microsoft.UI.Xaml.Markup.IXamlMetadataProvider") }
        .map { it.qualifiedName }
        .distinct()
        .sorted()
}
