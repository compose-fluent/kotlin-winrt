package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.util.getChildren
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
    private val kotlinApplicationEnvironment by lazy {
        ensureIntellijHomePath()
        KotlinCoreApplicationEnvironment.create(
            Disposer.newDisposable("kotlin-winrt-authoring-light-tree"),
            KotlinCoreApplicationEnvironmentMode.UnitTest,
        )
    }

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
            .flatMap { source -> scanSource(parseSource(source), winRtTypes) }
            .distinctBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
            .sortedBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
    }

    private fun parseSource(source: Path): KotlinLightSource {
        ensureKotlinApplicationEnvironment()
        val text = source.readText()
        val tree = KotlinLightParser.buildLightTree(
            text,
            InMemoryKtSourceFile(source.name, source.toAbsolutePath().toString(), text),
        ) { _: Int, _: Int, _: String? -> }
        return KotlinLightSource(text, tree)
    }

    private fun ensureKotlinApplicationEnvironment() {
        if (ApplicationManager.getApplication() == null) {
            kotlinApplicationEnvironment
        }
    }

    private fun ensureIntellijHomePath() {
        if (System.getProperty("idea.home.path") != null) {
            return
        }
        val home = Files.createTempDirectory("kotlin-winrt-intellij-home-")
        home.resolve("product-info.json").writeText("""{"name":"kotlin-winrt","version":"0"}""")
        System.setProperty("idea.home.path", home.toString())
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
        source: KotlinLightSource,
        winRtTypes: Map<String, WinRtTypeDefinition>,
    ): List<KotlinWinRtAuthoredTypeCandidate> {
        val packageName = source.packageName()
        val imports = parseImports(source)
        return source.classes().mapNotNull { klass ->
            val className = source.className(klass) ?: return@mapNotNull null
            val sourceTypeName = if (packageName.isBlank()) className else "$packageName.$className"
            val resolvedWinRtTypes = source.superTypeNames(klass)
                .mapNotNull { superType -> resolveWinRtTypeName(superType, packageName, imports, winRtTypes) }
                .mapNotNull(winRtTypes::get)
            if (resolvedWinRtTypes.isEmpty()) {
                return@mapNotNull null
            }
            val winRtBase = resolvedWinRtTypes.firstOrNull { type -> type.kind == WinRtTypeKind.RuntimeClass }
            val directInterfaces = resolvedWinRtTypes
                .filter { type -> type.kind == WinRtTypeKind.Interface }
                .map { type -> type.qualifiedName }
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

    private fun parseImports(file: KotlinLightSource): KotlinImports {
        val explicit = linkedMapOf<String, String>()
        val wildcards = mutableListOf<String>()
        file.imports().forEach { directive ->
            val imported = directive.substringAfter("import").trim()
            val path = imported.substringBefore(" as ").trim()
            if (path.endsWith(".*")) {
                wildcards += path.removeSuffix(".*")
            } else if (path.isNotBlank()) {
                val alias = imported.substringAfter(" as ", missingDelimiterValue = "")
                    .trim()
                    .takeIf(String::isNotBlank)
                explicit[alias ?: path.substringAfterLast('.')] = path
            }
        }
        return KotlinImports(explicit, wildcards)
    }

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

    private data class KotlinLightSource(
        val text: String,
        val tree: FlyweightCapableTreeStructure<LighterASTNode>,
    ) {
        fun packageName(): String =
            tree.root
                .descendantsOfType(KtNodeTypes.PACKAGE_DIRECTIVE)
                .firstOrNull()
                ?.let(::nodeText)
                ?.substringAfter("package", missingDelimiterValue = "")
                ?.trim()
                .orEmpty()

        fun imports(): List<String> =
            tree.root
                .descendantsOfType(KtNodeTypes.IMPORT_DIRECTIVE)
                .map(::nodeText)

        fun classes(): List<LighterASTNode> =
            tree.root.descendantsOfType(KtNodeTypes.CLASS)

        fun className(classNode: LighterASTNode): String? {
            var seenClassKeyword = false
            return classNode.descendants()
                .firstNotNullOfOrNull { node ->
                    when (node.tokenType) {
                        KtTokens.CLASS_KEYWORD -> {
                            seenClassKeyword = true
                            null
                        }
                        KtTokens.IDENTIFIER -> if (seenClassKeyword) nodeText(node) else null
                        else -> null
                    }
                }
        }

        fun superTypeNames(classNode: LighterASTNode): List<String> =
            classNode.children()
                .firstOrNull { child -> child.tokenType == KtNodeTypes.SUPER_TYPE_LIST }
                ?.children()
                .orEmpty()
                .filter { child ->
                    child.tokenType == KtNodeTypes.SUPER_TYPE_ENTRY ||
                        child.tokenType == KtNodeTypes.SUPER_TYPE_CALL_ENTRY ||
                        child.tokenType == KtNodeTypes.DELEGATED_SUPER_TYPE_ENTRY
                }
                .mapNotNull { entry ->
                    entry.descendantsOfType(KtNodeTypes.USER_TYPE)
                        .firstOrNull()
                        ?.let(::nodeText)
                        ?.substringBefore('<')
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                }

        private fun LighterASTNode.children(): List<LighterASTNode> =
            getChildren(tree)

        private fun LighterASTNode.descendants(): Sequence<LighterASTNode> =
            sequence {
                yield(this@descendants)
                children().forEach { child -> yieldAll(child.descendants()) }
            }

        private fun LighterASTNode.descendantsOfType(type: IElementType): List<LighterASTNode> =
            descendants().filter { node -> node.tokenType == type }.toList()

        private fun nodeText(node: LighterASTNode): String =
            text.substring(node.startOffset, node.endOffset)
    }

    private class InMemoryKtSourceFile(
        override val name: String,
        override val path: String?,
        private val contents: String,
    ) : KtSourceFile {
        override fun getContentsAsStream(): InputStream =
            ByteArrayInputStream(contents.toByteArray())
    }
}
