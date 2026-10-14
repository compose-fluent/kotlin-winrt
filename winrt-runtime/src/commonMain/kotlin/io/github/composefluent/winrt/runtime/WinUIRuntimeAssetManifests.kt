package io.github.composefluent.winrt.runtime

object WinUIRuntimeAssetManifests {
    const val xamlMetadataProvidersFileName: String = "kotlin-winrt-xaml-metadata-providers.txt"
}

internal object WinUIXamlMetadataProviderRuntimeAssets {
    fun loadProviderRuntimeClassNames(): List<String> =
        platformLoadWinUIXamlMetadataProviderRuntimeClassNameLines(
            WinUIRuntimeAssetManifests.xamlMetadataProvidersFileName,
        ).asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
}

internal expect fun platformLoadWinUIXamlMetadataProviderRuntimeClassNameLines(fileName: String): List<String>
