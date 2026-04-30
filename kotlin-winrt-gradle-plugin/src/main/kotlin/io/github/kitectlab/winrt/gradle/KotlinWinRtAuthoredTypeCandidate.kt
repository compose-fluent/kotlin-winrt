package io.github.kitectlab.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path

data class KotlinWinRtAuthoredTypeCandidate(
    val packageName: String,
    val className: String,
    val sourceTypeName: String,
    val winRtBaseClassName: String?,
    val winRtInterfaceNames: List<String>,
    val overridableInterfaceNames: List<String>,
)

object KotlinWinRtAuthoringCandidateFile {
    fun read(path: Path): List<KotlinWinRtAuthoredTypeCandidate> {
        if (!Files.exists(path)) {
            return emptyList()
        }
        return Files.readAllLines(path)
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split('\t')
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = parts.getOrElse(0) { "" },
                    className = parts.getOrElse(1) { "" },
                    sourceTypeName = parts.getOrElse(2) { "" },
                    winRtBaseClassName = parts.getOrElse(3) { "" }.takeIf(String::isNotBlank),
                    winRtInterfaceNames = parts.getOrElse(4) { "" }.semicolonList(),
                    overridableInterfaceNames = parts.getOrElse(5) { "" }.semicolonList(),
                )
            }
    }

    private fun String.semicolonList(): List<String> =
        split(';').filter(String::isNotBlank)
}
