package io.github.composefluent.winrt.runtime

object WinUiRuntimeAssetManifests {
    const val xamlMetadataProvidersFileName: String = "kotlin-winrt-xaml-metadata-providers.txt"
}

internal expect object WinUiXamlMetadataProviderRuntimeAssets {
    fun loadProviderRuntimeClassNames(): List<String>
}
