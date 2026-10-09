package io.github.composefluent.windows.toolkit.gradle

import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * CsWinRT's build targets track metadata references, not the SDK's headers and tools.
 * Discover only the directories consumed by the Windows SDK metadata resolver. Keep
 * all versions visible so installing a new SDK still invalidates automatic selection.
 */
internal fun windowsSdkMetadataFiles(root: Path): List<File> = buildList {
    val locations = listOf(
        root.resolve("Platforms/UAP") to setOf("platform.xml"),
        root.resolve("References") to emptySet<String>(),
        root.resolve("Extension SDKs") to setOf("sdkmanifest.xml"),
    )
    locations.forEach { (directory, manifests) ->
        if (Files.isDirectory(directory)) Files.walk(directory).use { paths ->
            paths.filter(Files::isRegularFile).filter { file ->
                val name = file.fileName.toString().lowercase()
                name in manifests || (manifests != setOf("platform.xml") && name.endsWith(".winmd"))
            }.forEach { add(it.toFile()) }
        }
    }
}
