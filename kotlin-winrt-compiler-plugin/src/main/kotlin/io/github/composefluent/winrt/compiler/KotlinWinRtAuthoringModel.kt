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

internal data class KotlinWinRtProjectionTypeIndexRecord(
    val sourceTypeName: String,
    val winRtTypeName: String,
    val kind: String,
) {
    fun render(): String =
        listOf(sourceTypeName, winRtTypeName, kind).joinToString("\t")
}

internal data class KotlinImports(
    val explicit: Map<String, String>,
    val wildcards: List<String>,
) {
    companion object {
        val Empty: KotlinImports = KotlinImports(emptyMap(), emptyList())
    }
}

internal fun projectionTypeIndexRecordForSourceType(
    sourceTypeName: String,
    winRtTypes: Map<String, IndexedWinRtType>,
): KotlinWinRtProjectionTypeIndexRecord? {
    if (sourceTypeName.startsWith(PROJECTION_PACKAGE_PREFIX)) {
        return null
    }
    val winRtTypeName = projectionPackageToMetadataName(sourceTypeName)
    val winRtType = winRtTypes[winRtTypeName] ?: return null
    return KotlinWinRtProjectionTypeIndexRecord(
        sourceTypeName = sourceTypeName,
        winRtTypeName = winRtType.qualifiedName,
        kind = winRtType.kind,
    )
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
    return typeName.removePrefix(PROJECTION_PACKAGE_PREFIX)
        .split('.')
        .joinToString(".") { segment -> if (segment == "ui") "UI" else segment.replaceFirstChar(Char::uppercaseChar) }
}

internal const val PROJECTION_PACKAGE_PREFIX: String = "io.github.composefluent.winrt.projections."
