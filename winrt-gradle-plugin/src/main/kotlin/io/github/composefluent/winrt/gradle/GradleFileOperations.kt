package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import kotlin.io.path.isDirectory
import kotlin.streams.asSequence

internal object GradleFileOperations {
    fun copyFile(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    fun cleanDirectory(directory: Path) {
        if (!directory.isDirectory()) return
        Files.walk(directory).use { stream ->
            stream.asSequence()
                .sortedWith(Comparator.reverseOrder())
                .filter { it != directory }
                .forEach(Files::deleteIfExists)
        }
    }
}
