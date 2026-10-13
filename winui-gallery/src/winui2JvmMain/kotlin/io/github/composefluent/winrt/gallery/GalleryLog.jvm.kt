package io.github.composefluent.winrt.gallery

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal actual fun appendGalleryLog(path: String, message: String) {
    Files.writeString(Path.of(path), "$message\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
}
