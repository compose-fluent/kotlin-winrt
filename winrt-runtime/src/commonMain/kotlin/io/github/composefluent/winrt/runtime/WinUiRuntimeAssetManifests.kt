package io.github.composefluent.winrt.runtime

object WinUiRuntimeAssetManifests {
    const val xamlMetadataProvidersFileName: String = "kotlin-winrt-xaml-metadata-providers.txt"
    const val xamlResourceDictionariesFileName: String = "kotlin-winrt-xaml-resource-dictionaries.txt"
}

internal expect object WinUiXamlMetadataProviderRuntimeAssets {
    fun loadProviderRuntimeClassNames(): List<String>
}

internal expect object WinUiXamlResourceDictionaryRuntimeAssets {
    fun loadResourceDictionaryRuntimeClassNames(): List<String>
}
