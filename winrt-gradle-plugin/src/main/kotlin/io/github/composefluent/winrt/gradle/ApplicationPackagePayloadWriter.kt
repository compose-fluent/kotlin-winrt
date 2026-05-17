package io.github.composefluent.winrt.gradle

import java.nio.file.Path
import kotlin.io.path.relativeTo

internal object ApplicationPackagePayloadWriter {
    fun copyPackagePayloads(projectRoot: Path, packageRoot: Path, items: Set<ApplicationPackageItem>) {
        items.asSequence()
            .filter { it.kind.isPackagePayload }
            .sortedBy { it.targetKey }
            .forEach { item ->
                GradleFileOperations.copyFile(item.target, packageRoot.resolve(item.target.relativeTo(projectRoot)))
            }
    }
}
