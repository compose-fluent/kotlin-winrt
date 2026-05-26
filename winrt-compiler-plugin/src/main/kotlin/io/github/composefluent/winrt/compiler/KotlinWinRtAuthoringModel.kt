package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.isWinRtObjectTypeName
import java.nio.file.Files
import java.nio.file.Path

internal data class IndexedWinRtType(
    val qualifiedName: String,
    val kind: String,
    val overridableInterfaces: List<String>,
    val baseTypeName: String,
)

internal data class KotlinWinRtAuthoredTypeCandidate(
    val packageName: String,
    val className: String,
    val sourceTypeName: String,
    val winRtBaseClassName: String?,
    val winRtInterfaceNames: List<String>,
    val overridableInterfaceNames: List<String>,
    val isPublic: Boolean,
)

internal data class KotlinWinRtProjectionTypeIndexRecord(
    val sourceTypeName: String,
    val winRtTypeName: String,
    val kind: String,
    val baseTypeName: String,
) {
    fun render(): String =
        listOf(sourceTypeName, winRtTypeName, kind, baseTypeName).joinToString("\t")
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
        baseTypeName = winRtType.baseTypeName,
    )
}

internal fun readAuthoringMetadataIndex(path: Path): Map<String, IndexedWinRtType> {
    require(Files.isRegularFile(path)) {
        "kotlin-winrt authoring metadata index $path does not exist."
    }
    val types = linkedMapOf<String, IndexedWinRtType>()
    Files.readAllLines(path)
        .asSequence()
        .forEachIndexed { index, line ->
            if (line.isBlank()) {
                return@forEachIndexed
            } else {
                val type = parseAuthoringMetadataIndexLine(line)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt authoring metadata index row ${index + 1} in $path must contain type name and kind columns.",
                    )
                require(!types.containsKey(type.qualifiedName)) {
                    "kotlin-winrt authoring metadata index row ${index + 1} in $path duplicates type ${type.qualifiedName}."
                }
                types[type.qualifiedName] = type
            }
        }
    return types
}

private fun parseAuthoringMetadataIndexLine(line: String): IndexedWinRtType? {
    val parts = line.split('\t')
    if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) {
        return null
    }
    if (parts[1] !in authoringMetadataIndexKinds) {
        return null
    }
    return IndexedWinRtType(
        qualifiedName = parts[0],
        kind = parts[1],
        overridableInterfaces = parseAuthoringMetadataIndexListField(parts.getOrElse(2) { "" })
            ?: return null,
        baseTypeName = parts.getOrElse(3) { "" },
    )
}

private val authoringMetadataIndexKinds = WinRtTypeKind.entries.map(WinRtTypeKind::name).toSet()

private fun parseAuthoringMetadataIndexListField(value: String): List<String>? {
    if (value.isBlank()) {
        return emptyList()
    }
    val parts = value.split(';')
    if (parts.any(String::isBlank)) {
        return null
    }
    return parts
}

internal fun inheritedOverridableInterfaceNames(
    winRtBase: IndexedWinRtType?,
    winRtTypes: Map<String, IndexedWinRtType>,
): List<String> {
    val names = linkedSetOf<String>()
    val visited = mutableSetOf<String>()
    var current = winRtBase
    while (current != null && visited.add(current.qualifiedName)) {
        names += current.overridableInterfaces
        val baseTypeName = current.baseTypeName
            .takeIf(String::isNotBlank)
            ?.takeUnless(::isWinRtObjectTypeName)
            ?: break
        current = requireNotNull(winRtTypes[baseTypeName]) {
            "kotlin-winrt authoring metadata index type ${current.qualifiedName} references missing base type $baseTypeName."
        }
    }
    return names.sorted()
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
