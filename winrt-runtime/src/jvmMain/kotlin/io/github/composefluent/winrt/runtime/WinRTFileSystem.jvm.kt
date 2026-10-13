package io.github.composefluent.winrt.runtime

import kotlinx.io.files.FileMetadata
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun platformFileMetadataOrNull(path: Path): FileMetadata? =
    SystemFileSystem.metadataOrNull(path)
