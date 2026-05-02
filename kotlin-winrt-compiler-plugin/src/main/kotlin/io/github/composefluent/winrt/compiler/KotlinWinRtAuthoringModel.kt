package io.github.composefluent.winrt.compiler

import java.nio.file.Files
import java.nio.file.Path

internal data class IndexedWinRtType(
    val qualifiedName: String,
    val kind: String,
    val overridableInterfaces: List<String>,
)

internal data class KotlinWinRtAuthoredTypeCandidate(
    val packageName: String,
    val className: String,
    val sourceTypeName: String,
    val winRtBaseClassName: String?,
    val winRtInterfaceNames: List<String>,
    val overridableInterfaceNames: List<String>,
)

internal data class KotlinImports(
    val explicit: Map<String, String>,
    val wildcards: List<String>,
) {
    companion object {
        val Empty: KotlinImports = KotlinImports(emptyMap(), emptyList())
    }
}

internal fun readAuthoringMetadataIndex(path: Path): Map<String, IndexedWinRtType> =
    if (!Files.exists(path)) {
        emptyMap()
    } else {
        Files.readAllLines(path)
            .filter(String::isNotBlank)
            .map { line ->
                val parts = line.split('\t')
                IndexedWinRtType(
                    qualifiedName = parts.getOrElse(0) { "" },
                    kind = parts.getOrElse(1) { "" },
                    overridableInterfaces = parts.getOrElse(2) { "" }
                        .split(';')
                        .filter(String::isNotBlank),
                )
            }
            .associateBy(IndexedWinRtType::qualifiedName)
    }

internal fun resolveWinRtTypeName(
    typeName: String,
    packageName: String,
    imports: KotlinImports,
    winRtTypes: Map<String, IndexedWinRtType>,
): String? {
    val candidates = buildList {
        add(typeName)
        imports.explicit[typeName]?.let(::add)
        imports.wildcards.forEach { wildcard -> add("$wildcard.$typeName") }
        if (packageName.isNotBlank()) {
            add("$packageName.$typeName")
        }
    }
    return candidates
        .flatMap { candidate -> listOf(candidate, projectionPackageToMetadataName(candidate)) }
        .firstOrNull { candidate -> candidate in winRtTypes }
}

internal fun projectionPackageToMetadataName(typeName: String): String {
    val prefix = "io.github.composefluent.winrt.projections."
    return typeName.removePrefix(prefix)
        .split('.')
        .joinToString(".") { segment -> if (segment == "ui") "UI" else segment.replaceFirstChar(Char::uppercaseChar) }
}
