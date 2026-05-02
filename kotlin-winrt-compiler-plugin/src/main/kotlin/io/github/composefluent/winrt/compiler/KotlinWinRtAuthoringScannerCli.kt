package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.com.intellij.lang.LighterASTNode
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.lexer.KtTokens
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
import kotlin.streams.asSequence

object KotlinWinRtAuthoringScannerCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = CliOptions.parse(args)
        val index = readAuthoringMetadataIndex(options.metadataIndex)
        val candidates = scan(options.sourceRoots, index)
        options.output.parent?.let(Files::createDirectories)
        options.output.writeText(
            candidates.joinToString(separator = "\n", postfix = if (candidates.isEmpty()) "" else "\n") { candidate ->
                listOf(
                    candidate.packageName,
                    candidate.className,
                    candidate.sourceTypeName,
                    candidate.winRtBaseClassName.orEmpty(),
                    candidate.winRtInterfaceNames.joinToString(";"),
                    candidate.overridableInterfaceNames.joinToString(";"),
                ).joinToString("\t")
            },
        )
    }

    private fun scan(
        sourceRoots: Iterable<Path>,
        winRtTypes: Map<String, IndexedWinRtType>,
    ): List<KotlinWinRtAuthoredTypeCandidate> {
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

    private fun scanSource(
        source: KotlinLightSource,
        winRtTypes: Map<String, IndexedWinRtType>,
    ): List<KotlinWinRtAuthoredTypeCandidate> {
        val packageName = source.packageName()
        val imports = parseImports(source)
        return source.classes().filter(source::isEffectivelyPublicClass).mapNotNull { klass ->
            val className = source.className(klass) ?: return@mapNotNull null
            val sourceTypeName = if (packageName.isBlank()) className else "$packageName.$className"
            val resolvedWinRtTypes = source.superTypeNames(klass)
                .mapNotNull { superType -> resolveWinRtTypeName(superType, packageName, imports, winRtTypes) }
                .mapNotNull(winRtTypes::get)
            if (resolvedWinRtTypes.isEmpty()) {
                return@mapNotNull null
            }
            val winRtBase = resolvedWinRtTypes.firstOrNull { type -> type.kind == "RuntimeClass" }
            val directInterfaces = resolvedWinRtTypes
                .filter { type -> type.kind == "Interface" }
                .map { type -> type.qualifiedName }
            val overridableInterfaces = winRtBase
                ?.overridableInterfaces
                .orEmpty()
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

    private val kotlinApplicationEnvironment by lazy {
        ensureIntellijHomePath()
        KotlinCoreApplicationEnvironment.create(
            Disposer.newDisposable("kotlin-winrt-authoring-light-tree"),
            KotlinCoreApplicationEnvironmentMode.UnitTest,
        )
    }

    private fun ensureIntellijHomePath() {
        if (System.getProperty("idea.home.path") != null) return
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
            stream.asSequence()
                .filter(Files::isRegularFile)
                .filter { path -> path.extension == "kt" }
                .toList()
        }
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

    private data class CliOptions(
        val metadataIndex: Path,
        val output: Path,
        val sourceRoots: List<Path>,
    ) {
        companion object {
            fun parse(args: Array<String>): CliOptions {
                var metadataIndex: Path? = null
                var output: Path? = null
                val sourceRoots = mutableListOf<Path>()
                var index = 0
                while (index < args.size) {
                    when (args[index]) {
                        "--metadata-index" -> {
                            metadataIndex = Path.of(args[index + 1])
                            index += 2
                        }
                        "--output" -> {
                            output = Path.of(args[index + 1])
                            index += 2
                        }
                        "--source-root" -> {
                            sourceRoots.add(Path.of(args[index + 1]))
                            index += 2
                        }
                        else -> error("Unknown kotlin-winrt authoring scanner argument: ${args[index]}")
                    }
                }
                return CliOptions(
                    metadataIndex = requireNotNull(metadataIndex) { "--metadata-index is required" },
                    output = requireNotNull(output) { "--output is required" },
                    sourceRoots = sourceRoots,
                )
            }
        }
    }

    private data class KotlinLightSource(
        val text: String,
        val tree: FlyweightCapableTreeStructure<LighterASTNode>,
    ) {
        fun packageName(): String =
            tree.root.descendantsOfType(KtNodeTypes.PACKAGE_DIRECTIVE)
                .firstOrNull()
                ?.let(::nodeText)
                ?.substringAfter("package", missingDelimiterValue = "")
                ?.trim()
                .orEmpty()

        fun imports(): List<String> =
            tree.root.descendantsOfType(KtNodeTypes.IMPORT_DIRECTIVE).map(::nodeText)

        fun classes(): List<LighterASTNode> =
            tree.root.descendantsOfType(KtNodeTypes.CLASS)

        fun isEffectivelyPublicClass(classNode: LighterASTNode): Boolean =
            isPublicClass(classNode) &&
                classes()
                    .filter { candidate -> candidate !== classNode }
                    .filter { candidate -> candidate.startOffset < classNode.startOffset && candidate.endOffset > classNode.endOffset }
                    .all(::isPublicClass)

        fun className(classNode: LighterASTNode): String? {
            var seenDeclarationKeyword = false
            return classNode.descendants().firstNotNullOfOrNull { node ->
                when (node.tokenType) {
                    KtTokens.CLASS_KEYWORD,
                    KtTokens.INTERFACE_KEYWORD,
                    KtTokens.OBJECT_KEYWORD,
                    -> {
                        seenDeclarationKeyword = true
                        null
                    }
                    KtTokens.IDENTIFIER -> if (seenDeclarationKeyword) nodeText(node) else null
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

        private fun LighterASTNode.children(): List<LighterASTNode> = getChildren(tree)

        private fun isPublicClass(classNode: LighterASTNode): Boolean {
            val modifierList = classNode.children().firstOrNull { child -> child.tokenType == KtNodeTypes.MODIFIER_LIST }
                ?: return true
            return modifierList.descendants().none { node ->
                node.tokenType == KtTokens.PRIVATE_KEYWORD ||
                    node.tokenType == KtTokens.INTERNAL_KEYWORD ||
                    node.tokenType == KtTokens.PROTECTED_KEYWORD
            }
        }

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
