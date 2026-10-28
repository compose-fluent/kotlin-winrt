package io.github.composefluent.winrt.ide.model

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/** Gradle records resolved build inputs after successful deployment. The IDE checks
 * their contents before bypassing Gradle; missing/old records must fail closed. */
object WinRTPreviewBuildInputs {
    const val FILE_NAME = "preview-build-inputs.txt"
    private const val HEADER = "kotlin-winrt-preview-inputs-v1"
    private const val MAX_FILES = 10_000
    fun write(file: Path, inputs: Collection<Path>) {
        val paths = inputs.map { it.toAbsolutePath().normalize().toString() }.distinct().sorted()
        require(paths.isNotEmpty() && paths.size <= MAX_FILES)
        val text = (listOf(HEADER) + paths.map { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }).joinToString("\n")
        require(text.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }
    fun read(file: Path): List<Path> {
        require(!Files.isSymbolicLink(file) && Files.size(file) <= 2 * 1024 * 1024)
        val lines = Files.readAllLines(file)
        require(lines.firstOrNull() == HEADER && lines.size in 2..MAX_FILES + 1)
        return lines.drop(1).map { encoded ->
            Path.of(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).also { require(it.isAbsolute) }
        }
    }
}
