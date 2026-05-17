package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.relativeTo

internal object ApplicationPackagePayloadWriter {
    fun copyPackagePayloads(projectRoot: Path, packageRoot: Path, items: Set<ApplicationPackageItem>) {
        items.asSequence()
            .filter { it.kind.isPackagePayload }
            .sortedBy { it.targetKey }
            .forEach { item ->
                copyFile(item.target, packageRoot.resolve(item.target.relativeTo(projectRoot)))
            }
    }

    private fun copyFile(source: Path, target: Path) {
        Files.createDirectories(target.parent)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
