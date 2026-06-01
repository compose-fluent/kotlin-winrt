package io.github.composefluent.winrt.runtime

import java.nio.file.Files

internal actual object WinUiXamlMetadataProviderRuntimeAssets {
    actual fun loadProviderRuntimeClassNames(): List<String> {
        val manifest = WinRtRuntimeAssets.resolveAssetPath(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName)
            ?: return emptyList()
        return Files.readAllLines(manifest)
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .distinct()
            .toList()
    }
}
