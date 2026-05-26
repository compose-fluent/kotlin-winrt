package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path

data class KotlinWinRtAuthoredTypeCandidate(
    val packageName: String,
    val className: String,
    val sourceTypeName: String,
    val winRtBaseClassName: String?,
    val winRtInterfaceNames: List<String>,
    val overridableInterfaceNames: List<String>,
    val isPublic: Boolean = true,
)

object KotlinWinRtAuthoringCandidateFile {
    fun read(path: Path): List<KotlinWinRtAuthoredTypeCandidate> {
        if (!Files.exists(path)) {
            return emptyList()
        }
        return Files.readAllLines(path)
            .mapIndexedNotNull { index, line ->
                if (line.isBlank()) {
                    null
                } else {
                    parseLine(line)
                        ?: throw IllegalArgumentException(
                            "kotlin-winrt authored candidate row ${index + 1} in $path is malformed.",
                        )
                }
            }
    }

    private fun parseLine(line: String): KotlinWinRtAuthoredTypeCandidate? {
        val parts = line.split('\t', limit = 7)
        if (parts.size < 7) {
            return null
        }
        if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null
        }
        val isPublic = parts[6].toBooleanStrictOrNull() ?: return null
        return KotlinWinRtAuthoredTypeCandidate(
            packageName = parts[0],
            className = parts[1],
            sourceTypeName = parts[2],
            winRtBaseClassName = parts[3].takeIf(String::isNotBlank),
            winRtInterfaceNames = parts[4].semicolonListOrNull() ?: return null,
            overridableInterfaceNames = parts[5].semicolonListOrNull() ?: return null,
            isPublic = isPublic,
        )
    }

    private fun String.semicolonListOrNull(): List<String>? {
        if (isBlank()) {
            return emptyList()
        }
        val parts = split(';')
        if (parts.any(String::isBlank)) {
            return null
        }
        return parts
    }
}
