package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

internal actual fun platformLoadWinUiXamlMetadataProviderRuntimeClassNameLines(fileName: String): List<String> {
    val manifest = Path(WinRTPlatformApi.resolveModulePathRaw(fileName))
    if (!manifest.isRegularFile()) {
        return emptyList()
    }
    return manifest.readText().lineSequence().toList()
}
