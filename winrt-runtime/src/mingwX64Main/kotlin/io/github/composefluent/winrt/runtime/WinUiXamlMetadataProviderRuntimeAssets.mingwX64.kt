package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

internal actual object WinUiXamlMetadataProviderRuntimeAssets {
    actual fun loadProviderRuntimeClassNames(): List<String> =
        loadRuntimeClassNames(WinUiRuntimeAssetManifests.xamlMetadataProvidersFileName)
}

private fun loadRuntimeClassNames(fileName: String): List<String> {
    val manifest = Path(WinRtPlatformApi.resolveModulePathRaw(fileName))
    if (!manifest.isRegularFile()) {
        return emptyList()
    }
    return manifest.readText()
        .lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct()
        .toList()
}
