package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTMetadataCache
import io.github.composefluent.winrt.metadata.WinRTMetadataSourceKind
import org.gradle.api.GradleException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.Base64

internal fun readPreparedMetadataCache(path: Path): WinRTMetadataCache {
    val lines = Files.readAllLines(path)
    require(lines.firstOrNull() == "kotlin-winrt-prepared-metadata-v1") {
        "Prepared WinRT metadata manifest $path has an unexpected header."
    }
    val resolvedFiles = mutableListOf<io.github.composefluent.winrt.metadata.WinRTResolvedMetadataFile>()
    val sdkSelections = mutableListOf<io.github.composefluent.winrt.metadata.WinRTWindowsSdkSelection>()
    lines.drop(1).filter(String::isNotBlank).forEachIndexed { index, line ->
        val parts = line.split('\t')
        when (parts.firstOrNull()) {
            "file" -> {
                require(parts.size == 4) {
                    "Prepared WinRT metadata manifest $path has malformed file row ${index + 2}."
                }
                val sourceKind = runCatching { WinRTMetadataSourceKind.valueOf(parts[1]) }.getOrElse {
                    throw GradleException("Prepared WinRT metadata manifest $path has unknown source kind '${parts[1]}'.")
                }
                val sourceDescription = decodePreparedMetadataField(parts[2])
                val file = Path.of(decodePreparedMetadataField(parts[3])).toAbsolutePath().normalize()
                require(Files.isRegularFile(file)) {
                    "Prepared WinRT metadata manifest $path references missing metadata file $file."
                }
                resolvedFiles += io.github.composefluent.winrt.metadata.WinRTResolvedMetadataFile(
                    file = file,
                    sourceKind = sourceKind,
                    sourceDescription = sourceDescription,
                )
            }
            "sdk" -> {
                require(parts.size == 3) {
                    "Prepared WinRT metadata manifest $path has malformed SDK row ${index + 2}."
                }
                val contracts = if (parts[2].isBlank()) {
                    emptyList()
                } else {
                    parts[2].split(';').map { encodedContract ->
                        val separator = encodedContract.indexOf('=')
                        require(separator > 0 && separator < encodedContract.lastIndex) {
                            "Prepared WinRT metadata manifest $path has malformed SDK contract row ${index + 2}."
                        }
                        io.github.composefluent.winrt.metadata.WinRTWindowsSdkContract(
                            name = decodePreparedMetadataField(encodedContract.substring(0, separator)),
                            version = decodePreparedMetadataField(encodedContract.substring(separator + 1)),
                        )
                    }
                }
                sdkSelections += io.github.composefluent.winrt.metadata.WinRTWindowsSdkSelection(
                    version = parts[1],
                    contracts = contracts,
                ).normalized()
            }
            else -> throw GradleException(
                "Prepared WinRT metadata manifest $path has unknown row kind '${parts.firstOrNull()}'.",
            )
        }
    }
    val files = resolvedFiles.map { it.file }
    return WinRTMetadataCache(
        files = files,
        resolvedFiles = resolvedFiles,
        windowsSdkSelections = sdkSelections,
    )
}

private fun decodePreparedMetadataField(value: String): String =
    String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)