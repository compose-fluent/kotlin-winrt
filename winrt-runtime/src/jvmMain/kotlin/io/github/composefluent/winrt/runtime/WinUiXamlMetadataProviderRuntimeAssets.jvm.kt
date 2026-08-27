package io.github.composefluent.winrt.runtime

import java.nio.file.Files

internal actual fun platformLoadWinUiXamlMetadataProviderRuntimeClassNameLines(fileName: String): List<String> {
    val manifest = WinRTRuntimeAssets.resolveAssetPath(fileName) ?: return emptyList()
    return Files.readAllLines(manifest)
}
