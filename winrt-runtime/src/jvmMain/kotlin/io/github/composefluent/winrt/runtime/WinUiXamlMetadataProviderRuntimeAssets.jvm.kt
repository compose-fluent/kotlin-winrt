package io.github.composefluent.winrt.runtime

import java.nio.file.Files

internal actual object WinUiXamlMetadataProviderRuntimeAssets {
    actual fun loadProviderRuntimeClassNames(): List<String> {
        return loadRuntimeClassNames(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName)
    }
}

private fun loadRuntimeClassNames(fileName: String): List<String> {
    val manifest = WinRtRuntimeAssets.resolveAssetPath(fileName) ?: return emptyList()
    return Files.readAllLines(manifest)
        .asSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct()
        .toList()
}
