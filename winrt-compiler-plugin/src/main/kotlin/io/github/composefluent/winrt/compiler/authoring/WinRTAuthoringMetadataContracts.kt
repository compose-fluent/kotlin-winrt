package io.github.composefluent.winrt.compiler.authoring

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

data class IndexedWinRTType(
    val qualifiedName: String,
    val kind: String,
    val overridableInterfaces: List<String>,
    val baseTypeName: String,
)

data class KotlinWinRTAuthoredTypeCandidate(
    val packageName: String,
    val className: String,
    val sourceTypeName: String,
    val winRTBaseClassName: String?,
    val winRTInterfaceNames: List<String>,
    val overridableInterfaceNames: List<String>,
    val isPublic: Boolean = true,
    val activatableFactoryInterfaceName: String? = null,
    val staticFactoryInterfaceNames: List<String> = emptyList(),
)

data class KotlinWinRTAuthoredRuntimeClassAnnotation(
    val baseClassName: String? = null,
    val interfaceNames: List<String> = emptyList(),
    val overridableInterfaceNames: List<String> = emptyList(),
    val activatableFactoryInterfaceName: String? = null,
    val staticFactoryInterfaceNames: List<String> = emptyList(),
) {
    val hasMetadata: Boolean
        get() = baseClassName != null ||
            interfaceNames.isNotEmpty() ||
            overridableInterfaceNames.isNotEmpty() ||
            activatableFactoryInterfaceName != null ||
            staticFactoryInterfaceNames.isNotEmpty()
}

data class KotlinWinRTProjectionTypeIndexRecord(
    val sourceTypeName: String,
    val winRTTypeName: String,
    val kind: String,
    val baseTypeName: String,
) {
    fun render(): String =
        listOf(sourceTypeName, winRTTypeName, kind, baseTypeName).joinToString("\t")
}

data class KotlinImports(
    val explicit: Map<String, String>,
    val wildcards: List<String>,
) {
    companion object {
        val Empty: KotlinImports = KotlinImports(emptyMap(), emptyList())
    }
}

object KotlinWinRTAuthoringCandidateFile {
    fun read(path: Path): List<KotlinWinRTAuthoredTypeCandidate> {
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

    fun write(path: Path, candidates: List<KotlinWinRTAuthoredTypeCandidate>) {
        path.parent?.let(Files::createDirectories)
        path.writeText(
            candidates.joinToString(separator = "\n", postfix = if (candidates.isEmpty()) "" else "\n") { candidate ->
                listOf(
                    candidate.packageName,
                    candidate.className,
                    candidate.sourceTypeName,
                    candidate.winRTBaseClassName.orEmpty(),
                    candidate.winRTInterfaceNames.joinToString(";"),
                    candidate.overridableInterfaceNames.joinToString(";"),
                    candidate.isPublic.toString(),
                    candidate.activatableFactoryInterfaceName.orEmpty(),
                    candidate.staticFactoryInterfaceNames.joinToString(";"),
                ).joinToString("\t")
            },
        )
    }

    private fun parseLine(line: String): KotlinWinRTAuthoredTypeCandidate? {
        val parts = line.split('\t')
        if (parts.size != 7 && parts.size != 9) {
            return null
        }
        if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null
        }
        val isPublic = parts[6].toBooleanStrictOrNull() ?: return null
        return KotlinWinRTAuthoredTypeCandidate(
            packageName = parts[0],
            className = parts[1],
            sourceTypeName = parts[2],
            winRTBaseClassName = parts[3].takeIf(String::isNotBlank),
            winRTInterfaceNames = parts[4].semicolonListOrNull() ?: return null,
            overridableInterfaceNames = parts[5].semicolonListOrNull() ?: return null,
            isPublic = isPublic,
            activatableFactoryInterfaceName = parts.getOrNull(7)?.takeIf(String::isNotBlank),
            staticFactoryInterfaceNames = parts.getOrNull(8)?.semicolonListOrNull() ?: emptyList(),
        )
    }
}

fun projectionTypeIndexRecordForSourceType(
    sourceTypeName: String,
    winRTTypes: Map<String, IndexedWinRTType>,
): KotlinWinRTProjectionTypeIndexRecord? {
    if (sourceTypeName.startsWith(PROJECTION_PACKAGE_PREFIX)) {
        return null
    }
    val winRTTypeName = projectionPackageToMetadataName(sourceTypeName)
    val winRTType = winRTTypes[winRTTypeName] ?: return null
    return KotlinWinRTProjectionTypeIndexRecord(
        sourceTypeName = sourceTypeName,
        winRTTypeName = winRTType.qualifiedName,
        kind = winRTType.kind,
        baseTypeName = winRTType.baseTypeName,
    )
}

fun readAuthoringMetadataIndex(path: Path): Map<String, IndexedWinRTType> {
    require(Files.isRegularFile(path)) {
        "kotlin-winrt authoring metadata index $path does not exist."
    }
    return readAuthoringMetadataIndexRows(Files.readAllLines(path), path.toString())
}

fun readAuthoringMetadataIndexRows(
    lines: Iterable<String>,
    sourceName: String,
): Map<String, IndexedWinRTType> {
    val types = linkedMapOf<String, IndexedWinRTType>()
    lines.asSequence()
        .forEachIndexed { index, line ->
            if (line.isBlank()) {
                return@forEachIndexed
            } else {
                val type = parseAuthoringMetadataIndexLine(line)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt authoring metadata index row ${index + 1} in $sourceName must contain type name and kind columns.",
                    )
                require(!types.containsKey(type.qualifiedName)) {
                    "kotlin-winrt authoring metadata index row ${index + 1} in $sourceName duplicates type ${type.qualifiedName}."
                }
                types[type.qualifiedName] = type
            }
        }
    return types
}

fun writeAuthoringMetadataIndex(
    model: WinRTMetadataModel,
    output: Path,
) {
    writeAuthoringMetadataIndex(
        model.namespaces
            .flatMap { namespace -> namespace.types }
            .map { type ->
                IndexedWinRTType(
                    qualifiedName = type.qualifiedName,
                    kind = type.kind.name,
                    overridableInterfaces = type.implementedInterfaces
                        .filter { implementation -> implementation.isOverridable }
                        .map { implementation -> implementation.interfaceName }
                        .distinct()
                        .sorted(),
                    baseTypeName = type.baseTypeName.orEmpty(),
                )
            },
        output,
    )
}

fun writeAuthoringMetadataIndex(
    types: Iterable<IndexedWinRTType>,
    output: Path,
) {
    val lines = types
        .distinctBy(IndexedWinRTType::qualifiedName)
        .sortedBy(IndexedWinRTType::qualifiedName)
        .map(::renderAuthoringMetadataIndexRow)
    Files.write(output, lines)
}

fun renderAuthoringMetadataIndexRow(type: IndexedWinRTType): String =
    listOf(
        type.qualifiedName,
        type.kind,
        type.overridableInterfaces.distinct().sorted().joinToString(";"),
        type.baseTypeName,
    ).joinToString("\t")

private fun parseAuthoringMetadataIndexLine(line: String): IndexedWinRTType? {
    val parts = line.split('\t')
    if (parts.size !in 2..4 || parts[0].isBlank() || parts[1].isBlank()) {
        return null
    }
    if (parts[1] !in authoringMetadataIndexKinds) {
        return null
    }
    return IndexedWinRTType(
        qualifiedName = parts[0],
        kind = parts[1],
        overridableInterfaces = parseAuthoringMetadataIndexListField(parts.getOrElse(2) { "" })
            ?: return null,
        baseTypeName = parts.getOrElse(3) { "" },
    )
}

private val authoringMetadataIndexKinds = WinRTTypeKind.entries.map(WinRTTypeKind::name).toSet()

private fun parseAuthoringMetadataIndexListField(value: String): List<String>? =
    value.semicolonListOrNull()

fun inheritedOverridableInterfaceNames(
    winRTBase: IndexedWinRTType?,
    winRTTypes: Map<String, IndexedWinRTType>,
): List<String> {
    val names = linkedSetOf<String>()
    val visited = mutableSetOf<String>()
    var current = winRTBase
    while (current != null && visited.add(current.qualifiedName)) {
        names += current.overridableInterfaces
        val baseTypeName = current.baseTypeName
            .takeIf(String::isNotBlank)
            ?.takeUnless(::isWinRTObjectTypeName)
            ?: break
        current = requireNotNull(winRTTypes[baseTypeName]) {
            "kotlin-winrt authoring metadata index type ${current.qualifiedName} references missing base type $baseTypeName."
        }
    }
    return names.sorted()
}

fun resolveWinRTTypeName(
    typeName: String,
    packageName: String,
    imports: KotlinImports,
    winRTTypes: Map<String, IndexedWinRTType>,
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
        .firstOrNull { candidate -> candidate in winRTTypes }
}

fun resolveIndexedWinRTType(
    typeName: String,
    packageName: String,
    imports: KotlinImports,
    winRTTypes: Map<String, IndexedWinRTType>,
): IndexedWinRTType? =
    resolveWinRTTypeName(typeName, packageName, imports, winRTTypes)?.let(winRTTypes::get)

fun projectionPackageToMetadataName(typeName: String): String {
    return typeName.removePrefix(PROJECTION_PACKAGE_PREFIX)
        .split('.')
        .joinToString(".") { segment -> if (segment == "ui") "UI" else segment.replaceFirstChar(Char::uppercaseChar) }
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

const val PROJECTION_PACKAGE_PREFIX: String = "io.github.composefluent.winrt.projections."
const val WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION: String =
    "io.github.composefluent.winrt.runtime.WinRTAuthoredRuntimeClass"
