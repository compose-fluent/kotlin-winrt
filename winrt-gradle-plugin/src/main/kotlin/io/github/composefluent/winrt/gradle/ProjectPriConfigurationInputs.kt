package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

internal data class ProjectPriConfigurationInputs(
    val unfilteredLayout: List<Path> = emptyList(),
    val filteredLayout: List<Path> = emptyList(),
    val excludedLayout: List<Path> = emptyList(),
    val resources: List<Path> = emptyList(),
    val pri: List<Path> = emptyList(),
    val embed: List<Path> = emptyList(),
) {
    fun write(configRoot: Path, projectPriRoot: Path) {
        writeResfiles(configRoot.resolve("unfiltered.layout.resfiles"), projectPriRoot, unfilteredLayout)
        writeResfiles(configRoot.resolve("filtered.layout.resfiles"), projectPriRoot, filteredLayout)
        writeResfiles(configRoot.resolve("excluded.layout.resfiles"), projectPriRoot, excludedLayout)
        writeResfiles(configRoot.resolve("resources.resfiles"), projectPriRoot, resources)
        writeResfiles(configRoot.resolve("pri.resfiles"), projectPriRoot, pri)
        writeResfiles(configRoot.resolve("embed/embed.resfiles"), projectPriRoot.resolve("embed"), embed)
    }

    private fun writeResfiles(path: Path, root: Path, files: List<Path>) {
        Files.createDirectories(path.parent)
        val lines = files.asSequence()
            .map { it.relativeTo(root).toPortablePath() }
            .sorted()
            .toList()
        Files.write(path, lines)
    }

    private fun Path.toPortablePath(): String =
        joinToString("/") { it.toString() }
}
