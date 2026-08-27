package io.github.composefluent.winrt.runtime

object WinUiRuntimeAssetManifests {
    const val xamlMetadataProvidersFileName: String = "kotlin-winrt-xaml-metadata-providers.txt"
}

internal object WinUiXamlMetadataProviderRuntimeAssets {
    fun loadProviderRuntimeClassNames(): List<String> =
        platformLoadWinUiXamlMetadataProviderRuntimeClassNameLines(
            WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName,
        ).asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
}

internal expect fun platformLoadWinUiXamlMetadataProviderRuntimeClassNameLines(fileName: String): List<String>
