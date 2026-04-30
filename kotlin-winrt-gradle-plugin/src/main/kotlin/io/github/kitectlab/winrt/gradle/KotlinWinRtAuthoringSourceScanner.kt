package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

data class KotlinWinRtAuthoredTypeCandidate(
    val packageName: String,
    val className: String,
    val sourceTypeName: String,
    val winRtBaseClassName: String?,
    val winRtInterfaceNames: List<String>,
    val overridableInterfaceNames: List<String>,
)

object KotlinWinRtAuthoringSourceScanner {
    private const val PROJECTION_PACKAGE_PREFIX = "io.github.kitectlab.winrt.projections."

    fun scan(
        sourceRoots: Iterable<Path>,
        metadataModel: WinRtMetadataModel,
    ): List<KotlinWinRtAuthoredTypeCandidate> {
        val winRtTypes = metadataModel.namespaces
            .flatMap { namespace -> namespace.types }
            .associateBy { type -> type.qualifiedName }
        val sourceFiles = sourceRoots
            .filter(Files::exists)
            .flatMap(::kotlinSourceFiles)
            .distinct()
            .sorted()

        return sourceFiles
            .flatMap { source -> scanSource(source, winRtTypes) }
            .distinctBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
            .sortedBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
    }

    private fun kotlinSourceFiles(root: Path): List<Path> {
        if (Files.isRegularFile(root)) {
            return if (root.extension == "kt") listOf(root) else emptyList()
        }
        if (!Files.isDirectory(root)) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .filter { path -> path.extension == "kt" }
                .toList()
        }
    }

    private fun scanSource(
        source: Path,
        winRtTypes: Map<String, WinRtTypeDefinition>,
    ): List<KotlinWinRtAuthoredTypeCandidate> {
        val text = source.readText()
        val packageName = PACKAGE_REGEX.find(text)?.groupValues?.get(1).orEmpty()
        val imports = parseImports(text)
        return CLASS_REGEX.findAll(text).mapNotNull { match ->
            val className = match.groupValues[1]
            val sourceTypeName = if (packageName.isBlank()) className else "$packageName.$className"
            val superTypes = match.groupValues.getOrNull(2)
                ?.takeIf(String::isNotBlank)
                ?.let(::splitSuperTypes)
                .orEmpty()
            val resolvedWinRtTypes = superTypes
                .mapNotNull { superType -> resolveWinRtTypeName(superType, packageName, imports, winRtTypes) }
                .mapNotNull(winRtTypes::get)
            if (resolvedWinRtTypes.isEmpty()) {
                return@mapNotNull null
            }
            val winRtBase = resolvedWinRtTypes.firstOrNull { it.kind == WinRtTypeKind.RuntimeClass }
            val directInterfaces = resolvedWinRtTypes
                .filter { it.kind == WinRtTypeKind.Interface }
                .map { it.qualifiedName }
            val overridableInterfaces = winRtBase
                ?.implementedInterfaces
                .orEmpty()
                .filter { implementation -> implementation.isOverridable }
                .map { implementation -> implementation.interfaceName }
            KotlinWinRtAuthoredTypeCandidate(
                packageName = packageName,
                className = className,
                sourceTypeName = sourceTypeName,
                winRtBaseClassName = winRtBase?.qualifiedName,
                winRtInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
                overridableInterfaceNames = overridableInterfaces.distinct().sorted(),
            )
        }.toList()
    }

    private fun parseImports(text: String): KotlinImports {
        val explicit = linkedMapOf<String, String>()
        val wildcards = mutableListOf<String>()
        IMPORT_REGEX.findAll(text).forEach { match ->
            val imported = match.groupValues[1]
            if (imported.endsWith(".*")) {
                wildcards += imported.removeSuffix(".*")
            } else {
                explicit[imported.substringAfterLast('.')] = imported
            }
        }
        return KotlinImports(explicit, wildcards)
    }

    private fun splitSuperTypes(superTypes: String): List<String> =
        superTypes
            .split(',')
            .map { raw -> raw.substringBefore('(').substringBefore('<').trim() }
            .filter(String::isNotBlank)

    private fun resolveWinRtTypeName(
        typeName: String,
        packageName: String,
        imports: KotlinImports,
        winRtTypes: Map<String, WinRtTypeDefinition>,
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

    private fun projectionPackageToMetadataName(typeName: String): String {
        if (!typeName.startsWith(PROJECTION_PACKAGE_PREFIX)) {
            return typeName
        }
        val relativeName = typeName.removePrefix(PROJECTION_PACKAGE_PREFIX)
        return relativeName
            .split('.')
            .joinToString(".") { segment -> projectionNamespaceSegmentToMetadataName(segment) }
    }

    private fun projectionNamespaceSegmentToMetadataName(segment: String): String =
        when (segment) {
            "ui" -> "UI"
            else -> segment.replaceFirstChar(Char::uppercaseChar)
        }

    private data class KotlinImports(
        val explicit: Map<String, String>,
        val wildcards: List<String>,
    )

    private val PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
    private val IMPORT_REGEX = Regex("""(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_.]*(?:\.\*)?)""")
    private val CLASS_REGEX = Regex(
        """(?m)^\s*(?:public|internal|private|protected|open|sealed|abstract|final|data|value|annotation|inner|\s)*class\s+([A-Za-z_][A-Za-z0-9_]*)(?:\s*\([^)]*\))?(?:\s*:\s*([^\n{]+))?""",
    )
}
