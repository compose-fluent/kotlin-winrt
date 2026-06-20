package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.IndexedWinRtType
import io.github.composefluent.winrt.compiler.authoring.KotlinImports
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoredRuntimeClassAnnotation
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRtAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.PROJECTION_PACKAGE_PREFIX
import io.github.composefluent.winrt.compiler.authoring.WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION
import io.github.composefluent.winrt.compiler.authoring.inheritedOverridableInterfaceNames
import io.github.composefluent.winrt.compiler.authoring.projectionPackageToMetadataName
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRtType
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
        KotlinWinRtAuthoringCandidateFile.write(options.output, candidates)
    }

    private fun scan(
        sourceRoots: Iterable<Path>,
        winRtTypes: Map<String, IndexedWinRtType>,
    ): List<KotlinWinRtAuthoredTypeCandidate> {
        val sourceFiles = sourceRoots
            .onEach { root ->
                require(Files.exists(root)) {
                    "kotlin-winrt authoring scanner source root $root does not exist."
                }
            }
            .flatMap(::kotlinSourceFiles)
            .distinct()
            .sorted()
        val sources = sourceFiles.map(::parseSource)
        val sourceClasses = sources.flatMap { source ->
            val packageName = source.packageName()
            val imports = parseImports(source)
            source.classes().mapNotNull { klass ->
                val className = source.className(klass) ?: return@mapNotNull null
                val sourceTypeName = if (packageName.isBlank()) className else "$packageName.$className"
                SourceClass(source, klass, packageName, imports, className, sourceTypeName)
            }
        }
        val sourceClassIndex = sourceClasses.associateBy(SourceClass::sourceTypeName)
        val sourceSubtypedNames = sourceClasses
            .flatMap { sourceClass ->
                sourceClass.source.superTypeNames(sourceClass.klass)
                    .mapNotNull { superType ->
                        resolveSourceTypeName(superType, sourceClass.packageName, sourceClass.imports, sourceClassIndex)
                    }
            }
            .toSet()
        val candidates = sourceClasses
            .mapNotNull { sourceClass -> scanSourceClass(sourceClass, sourceClassIndex, sourceSubtypedNames, winRtTypes) }
        val duplicateTypeNames = candidates
            .groupBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
            .filterValues { matches -> matches.size > 1 }
            .keys
            .sorted()
        require(duplicateTypeNames.isEmpty()) {
            "kotlin-winrt authoring scanner found duplicate authored type candidates: " +
                duplicateTypeNames.joinToString()
        }
        return candidates.sortedBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
    }

    private fun scanSourceClass(
        sourceClass: SourceClass,
        sourceClassIndex: Map<String, SourceClass>,
        sourceSubtypedNames: Set<String>,
        winRtTypes: Map<String, IndexedWinRtType>,
    ): KotlinWinRtAuthoredTypeCandidate? {
        val source = sourceClass.source
        val klass = sourceClass.klass
        val packageName = sourceClass.packageName
        val imports = sourceClass.imports
        val className = sourceClass.className
        val sourceTypeName = sourceClass.sourceTypeName
        if (!source.isEffectivelyAuthorableClass(klass)) {
            return null
        }
        if (sourceTypeName in sourceSubtypedNames && source.isUnsealedAuthoredClass(klass)) {
            return null
        }
        val projectedMetadataName = projectionPackageToMetadataName(sourceTypeName)
        if (sourceTypeName.startsWith(PROJECTION_PACKAGE_PREFIX) ||
            (projectedMetadataName != sourceTypeName && projectedMetadataName in winRtTypes)
        ) {
            return null
        }
        val annotation = source.authoredRuntimeClassAnnotation(klass, packageName, imports)
        val inheritedWinRtTypes = inheritedWinRtTypes(sourceClass, sourceClassIndex, winRtTypes)
        val annotatedBase = annotation.baseClassName
            ?.let { typeName ->
                resolveAnnotatedWinRtType(typeName, winRtTypes, sourceTypeName).also { type ->
                    require(type.kind == "RuntimeClass") {
                        "WinRT authored type $sourceTypeName annotation baseClassName must reference a WinRT runtime class: $typeName."
                    }
                }
            }
        val annotatedInterfaces = annotation.interfaceNames
            .map { typeName ->
                resolveAnnotatedWinRtType(typeName, winRtTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation interfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
        val annotatedOverridableInterfaces = annotation.overridableInterfaceNames
            .map { typeName ->
                resolveAnnotatedWinRtType(typeName, winRtTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation overridableInterfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
            .map(IndexedWinRtType::qualifiedName)
        val annotatedActivatableFactoryInterface = annotation.activatableFactoryInterfaceName
            ?.let { typeName ->
                resolveAnnotatedWinRtType(typeName, winRtTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation activatableFactoryInterfaceName must reference a WinRT interface: $typeName."
                    }
                }.qualifiedName
            }
        val annotatedStaticFactoryInterfaces = annotation.staticFactoryInterfaceNames
            .map { typeName ->
                resolveAnnotatedWinRtType(typeName, winRtTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation staticFactoryInterfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
            .map(IndexedWinRtType::qualifiedName)
        val resolvedWinRtTypes = listOfNotNull(annotatedBase) + annotatedInterfaces + inheritedWinRtTypes
        if (resolvedWinRtTypes.isEmpty()) {
            return null
        }
        require(source.isRuntimeClassDeclaration(klass)) {
            "WinRT authored type $sourceTypeName must be a concrete Kotlin class."
        }
        require(!source.isValueClass(klass)) {
            "WinRT authored type $sourceTypeName must not be a Kotlin value class."
        }
        require(!source.isEffectivelyPublicClass(klass) || source.hasPublicDefaultActivationConstructor(klass)) {
            "Public WinRT authored type $sourceTypeName must declare an accessible zero-argument constructor for default activation."
        }
        require(!source.hasTypeParameters(klass)) {
            "WinRT authored type $sourceTypeName must not be generic."
        }
        require(!source.isUnsealedAuthoredClass(klass)) {
            "WinRT authored class $sourceTypeName must be final."
        }
        require(!source.isNestedClass(klass)) {
            "WinRT authored type $sourceTypeName must be a top-level Kotlin type; " +
                "nested authored runtime classes are not supported."
        }
        val winRtBase = resolvedWinRtTypes.firstOrNull { type -> type.kind == "RuntimeClass" }
        val directInterfaces = resolvedWinRtTypes
            .filter { type -> type.kind == "Interface" }
            .map { type -> type.qualifiedName }
        val overridableInterfaces = (annotatedOverridableInterfaces + inheritedOverridableInterfaceNames(winRtBase, winRtTypes))
            .distinct()
            .sorted()
        return KotlinWinRtAuthoredTypeCandidate(
            packageName = packageName,
            className = className,
            sourceTypeName = sourceTypeName,
            winRtBaseClassName = winRtBase?.qualifiedName,
            winRtInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
            overridableInterfaceNames = overridableInterfaces,
            isPublic = source.isEffectivelyPublicClass(klass),
            activatableFactoryInterfaceName = annotatedActivatableFactoryInterface,
            staticFactoryInterfaceNames = annotatedStaticFactoryInterfaces.distinct().sorted(),
        )
    }

    private fun inheritedWinRtTypes(
        sourceClass: SourceClass,
        sourceClassIndex: Map<String, SourceClass>,
        winRtTypes: Map<String, IndexedWinRtType>,
        visitedSourceTypes: MutableSet<String> = mutableSetOf(),
    ): List<IndexedWinRtType> =
        sourceClass.source.superTypeNames(sourceClass.klass).flatMap { superType ->
            resolveIndexedWinRtType(superType, sourceClass.packageName, sourceClass.imports, winRtTypes)
                ?.let { return@flatMap listOf(it) }
            val resolvedSourceTypeName = resolveSourceTypeName(
                superType,
                sourceClass.packageName,
                sourceClass.imports,
                sourceClassIndex,
            ) ?: return@flatMap emptyList()
            if (!visitedSourceTypes.add(resolvedSourceTypeName)) {
                return@flatMap emptyList()
            }
            val resolvedSourceClass = sourceClassIndex[resolvedSourceTypeName] ?: return@flatMap emptyList()
            inheritedWinRtTypes(resolvedSourceClass, sourceClassIndex, winRtTypes, visitedSourceTypes)
        }

    private fun resolveSourceTypeName(
        typeName: String,
        packageName: String,
        imports: KotlinImports,
        sourceClassIndex: Map<String, SourceClass>,
    ): String? {
        val candidates = buildList {
            add(typeName)
            imports.explicit[typeName]?.let(::add)
            imports.wildcards.forEach { wildcard -> add("$wildcard.$typeName") }
            if (packageName.isNotBlank()) {
                add("$packageName.$typeName")
            }
        }
        return candidates.firstOrNull(sourceClassIndex::containsKey)
    }

    private data class SourceClass(
        val source: KotlinLightSource,
        val klass: LighterASTNode,
        val packageName: String,
        val imports: KotlinImports,
        val className: String,
        val sourceTypeName: String,
    )

    private fun parseSource(source: Path): KotlinLightSource {
        ensureKotlinApplicationEnvironment()
        val text = source.readText()
        val tree = KotlinLightParser.buildLightTree(
            text,
            InMemoryKtSourceFile(source.name, source.toAbsolutePath().toString(), text),
        ) { _: Int, _: Int, _: String? -> }
        return KotlinLightSource(text, tree)
    }

    private fun resolveAnnotatedWinRtType(
        typeName: String,
        winRtTypes: Map<String, IndexedWinRtType>,
        sourceTypeName: String,
    ): IndexedWinRtType =
        requireNotNull(winRtTypes[projectionPackageToMetadataName(typeName)]) {
            "WinRT authored type $sourceTypeName annotation references unknown WinRT metadata type $typeName."
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
        file.imports().forEach { imported ->
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
                            metadataIndex = Path.of(argumentValue(args, index))
                            index += 2
                        }
                        "--output" -> {
                            output = Path.of(argumentValue(args, index))
                            index += 2
                        }
                        "--source-root" -> {
                            sourceRoots.add(Path.of(argumentValue(args, index)))
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

            private fun argumentValue(args: Array<String>, index: Int): String =
                args.getOrNull(index + 1)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt authoring scanner argument ${args[index]} requires a path value.",
                    )
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
            Regex("""(?m)^\s*import\s+([^\r\n]+)""")
                .findAll(text)
                .map { match -> match.groupValues[1].trim() }
                .toList()

        fun classes(): List<LighterASTNode> =
            tree.root.descendantsOfType(KtNodeTypes.CLASS)

        fun isEffectivelyAuthorableClass(classNode: LighterASTNode): Boolean =
            isPublicOrInternalClass(classNode) &&
                classes()
                    .filter { candidate -> candidate !== classNode }
                    .filter { candidate -> candidate.startOffset < classNode.startOffset && candidate.endOffset > classNode.endOffset }
                    .all(::isPublicOrInternalClass)

        fun isEffectivelyPublicClass(classNode: LighterASTNode): Boolean =
            isPublicClass(classNode) &&
                classes()
                    .filter { candidate -> candidate !== classNode }
                    .filter { candidate -> candidate.startOffset < classNode.startOffset && candidate.endOffset > classNode.endOffset }
                    .all(::isPublicClass)

        fun isNestedClass(classNode: LighterASTNode): Boolean =
            classes()
                .filter { candidate -> candidate !== classNode }
                .any { candidate -> candidate.startOffset < classNode.startOffset && candidate.endOffset > classNode.endOffset }

        fun hasTypeParameters(classNode: LighterASTNode): Boolean =
            classNode.children().any { child -> child.tokenType == KtNodeTypes.TYPE_PARAMETER_LIST }

        fun isRuntimeClassDeclaration(classNode: LighterASTNode): Boolean =
            classDeclarationKeyword(classNode) == KtTokens.CLASS_KEYWORD

        fun isValueClass(classNode: LighterASTNode): Boolean =
            hasModifier(classNode, KtTokens.VALUE_KEYWORD, KtTokens.INLINE_KEYWORD)

        fun hasPublicDefaultActivationConstructor(classNode: LighterASTNode): Boolean {
            val constructors = classNode.descendantsOfType(KtNodeTypes.PRIMARY_CONSTRUCTOR) +
                classNode.descendantsOfType(KtNodeTypes.SECONDARY_CONSTRUCTOR)
            if (constructors.isEmpty()) {
                return true
            }
            return constructors.any { constructor ->
                isPublicConstructor(constructor) && !constructor.hasValueParameters()
            }
        }

        fun isUnsealedAuthoredClass(classNode: LighterASTNode): Boolean =
            classDeclarationKeyword(classNode) == KtTokens.CLASS_KEYWORD &&
                hasModifier(classNode, KtTokens.OPEN_KEYWORD, KtTokens.ABSTRACT_KEYWORD, KtTokens.SEALED_KEYWORD)

        private fun isPublicConstructor(constructorNode: LighterASTNode): Boolean =
            !hasModifier(constructorNode, KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD, KtTokens.PROTECTED_KEYWORD)

        private fun LighterASTNode.hasValueParameters(): Boolean =
            children()
                .firstOrNull { child -> child.tokenType == KtNodeTypes.VALUE_PARAMETER_LIST }
                ?.children()
                .orEmpty()
                .any { child -> child.tokenType == KtNodeTypes.VALUE_PARAMETER }

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

        fun authoredRuntimeClassAnnotation(
            classNode: LighterASTNode,
            packageName: String,
            imports: KotlinImports,
        ): KotlinWinRtAuthoredRuntimeClassAnnotation {
            val leadingDeclarationText = text.substring(0, classNode.startOffset)
            val modifierText = classNode.children()
                .firstOrNull { child -> child.tokenType == KtNodeTypes.MODIFIER_LIST }
                ?.let(::nodeText)
                .orEmpty()
            val modifierAnnotationText = authoredRuntimeClassAnnotationText(modifierText, packageName, imports)
            val annotationText = modifierAnnotationText
                ?.takeIf { annotation -> annotation.substringAfter('@').contains('(') }
                ?: authoredRuntimeClassAnnotationText(leadingDeclarationText, packageName, imports)
                ?: modifierAnnotationText
                ?: return KotlinWinRtAuthoredRuntimeClassAnnotation()
            val positionalArguments = annotationPositionalArguments(annotationText)
            return KotlinWinRtAuthoredRuntimeClassAnnotation(
                baseClassName = (
                    annotationStringArgument(annotationText, "baseClassName")
                        .takeIf(String::isNotBlank)
                        ?: positionalArguments.getOrNull(0).stringLiteralArgument()
                    ).takeIf(String::isNotBlank),
                interfaceNames = annotationStringArrayArgument(annotationText, "interfaceNames")
                    .ifEmpty { positionalArguments.getOrNull(1).stringArrayArgument() },
                overridableInterfaceNames = annotationStringArrayArgument(annotationText, "overridableInterfaceNames")
                    .ifEmpty { positionalArguments.getOrNull(2).stringArrayArgument() },
                activatableFactoryInterfaceName = annotationStringArgument(annotationText, "activatableFactoryInterfaceName")
                    .takeIf(String::isNotBlank)
                    ?: positionalArguments.getOrNull(3).stringLiteralArgument().takeIf(String::isNotBlank),
                staticFactoryInterfaceNames = annotationStringArrayArgument(annotationText, "staticFactoryInterfaceNames")
                    .ifEmpty { positionalArguments.getOrNull(4).stringArrayArgument() },
            )
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

        private fun isPublicOrInternalClass(classNode: LighterASTNode): Boolean {
            val modifierList = classNode.children().firstOrNull { child -> child.tokenType == KtNodeTypes.MODIFIER_LIST }
                ?: return true
            return modifierList.descendants().none { node ->
                node.tokenType == KtTokens.PRIVATE_KEYWORD ||
                    node.tokenType == KtTokens.PROTECTED_KEYWORD
            }
        }

        private fun hasModifier(classNode: LighterASTNode, vararg modifiers: IElementType): Boolean {
            val modifierTypes = modifiers.toSet()
            val modifierList = classNode.children().firstOrNull { child -> child.tokenType == KtNodeTypes.MODIFIER_LIST }
                ?: return false
            return modifierList.descendants().any { node -> node.tokenType in modifierTypes }
        }

        private fun classDeclarationKeyword(classNode: LighterASTNode): IElementType? =
            classNode.descendants()
                .firstOrNull { node ->
                    node.tokenType == KtTokens.CLASS_KEYWORD ||
                        node.tokenType == KtTokens.INTERFACE_KEYWORD ||
                        node.tokenType == KtTokens.OBJECT_KEYWORD
                }
                ?.tokenType

        private fun LighterASTNode.descendants(): Sequence<LighterASTNode> =
            sequence {
                yield(this@descendants)
                children().forEach { child -> yieldAll(child.descendants()) }
            }

        private fun LighterASTNode.descendantsOfType(type: IElementType): List<LighterASTNode> =
            descendants().filter { node -> node.tokenType == type }.toList()

        private fun nodeText(node: LighterASTNode): String =
            text.substring(node.startOffset, node.endOffset)

        private fun authoredRuntimeClassAnnotationText(
            modifierText: String,
            packageName: String,
            imports: KotlinImports,
        ): String? {
            val acceptedNames = linkedSetOf(
                WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION,
            )
            imports.explicit
                .filterValues { it == WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION }
                .keys
                .forEach(acceptedNames::add)
            if (WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION.substringBeforeLast('.') in imports.wildcards) {
                acceptedNames += "WinRtAuthoredRuntimeClass"
            }
            if (packageName == WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION.substringBeforeLast('.')) {
                acceptedNames += "WinRtAuthoredRuntimeClass"
            }
            return acceptedNames.firstNotNullOfOrNull { name ->
                annotationTextForName(modifierText, name)
            }
        }

        private fun annotationTextForName(modifierText: String, name: String): String? {
            val match = Regex("""@${Regex.escape(name)}\b""").findAll(modifierText).lastOrNull() ?: return null
            var index = match.range.last + 1
            while (index < modifierText.length && modifierText[index].isWhitespace()) {
                index += 1
            }
            if (modifierText.getOrNull(index) != '(') {
                return modifierText.substring(match.range.first, index)
            }
            var depth = 0
            var inString = false
            var escaped = false
            while (index < modifierText.length) {
                val char = modifierText[index]
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '(' -> depth += 1
                    !inString && char == ')' -> {
                        depth -= 1
                        if (depth == 0) {
                            return modifierText.substring(match.range.first, index + 1)
                        }
                    }
                }
                index += 1
            }
            return modifierText.substring(match.range.first)
        }

        private fun annotationStringArgument(annotationText: String, name: String): String =
            Regex("\\b${Regex.escape(name)}\\s*=\\s*\"([^\"]*)\"")
                .find(annotationText)
                ?.groupValues
                ?.get(1)
                .orEmpty()

        private fun annotationStringArrayArgument(annotationText: String, name: String): List<String> {
            val body = Regex("\\b${Regex.escape(name)}\\s*=\\s*\\[([^\\]]*)]")
                .find(annotationText)
                ?.groupValues
                ?.get(1)
                ?: return emptyList()
            return Regex("\"([^\"]*)\"")
                .findAll(body)
                .map { match -> match.groupValues[1] }
                .toList()
        }

        private fun annotationPositionalArguments(annotationText: String): List<String> {
            val body = annotationText.substringAfter('(', missingDelimiterValue = "")
                .substringBeforeLast(')', missingDelimiterValue = "")
                .takeIf(String::isNotBlank)
                ?: return emptyList()
            return splitTopLevelArguments(body)
                .filterNot(::hasTopLevelEquals)
        }

        private fun splitTopLevelArguments(body: String): List<String> {
            val arguments = mutableListOf<String>()
            var start = 0
            var bracketDepth = 0
            var inString = false
            var escaped = false
            body.forEachIndexed { index, char ->
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '[' -> bracketDepth += 1
                    !inString && char == ']' -> bracketDepth -= 1
                    !inString && char == ',' && bracketDepth == 0 -> {
                        arguments += body.substring(start, index).trim()
                        start = index + 1
                    }
                }
            }
            arguments += body.substring(start).trim()
            return arguments.filter(String::isNotBlank)
        }

        private fun hasTopLevelEquals(argument: String): Boolean {
            var inString = false
            var escaped = false
            argument.forEach { char ->
                when {
                    escaped -> escaped = false
                    char == '\\' && inString -> escaped = true
                    char == '"' -> inString = !inString
                    !inString && char == '=' -> return true
                }
            }
            return false
        }

        private fun String?.stringLiteralArgument(): String =
            this
                ?.let { Regex("^\\s*\"([^\"]*)\"\\s*$").find(it) }
                ?.groupValues
                ?.get(1)
                .orEmpty()

        private fun String?.stringArrayArgument(): List<String> {
            val body = this
                ?.let { Regex("^\\s*\\[([^\\]]*)]\\s*$").find(it) }
                ?.groupValues
                ?.get(1)
                ?: return emptyList()
            return Regex("\"([^\"]*)\"")
                .findAll(body)
                .map { match -> match.groupValues[1] }
                .toList()
        }
    }

    private class InMemoryKtSourceFile(
        override val name: String,
        override val path: String?,
        private val contents: String,
    ) : KtSourceFile {
        override fun getContentsAsStream(): InputStream =
            ByteArrayInputStream(contents.toByteArray())

        override fun equals(other: Any?): Boolean =
            this === other || other is InMemoryKtSourceFile &&
                name == other.name &&
                path == other.path &&
                contents == other.contents

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + (path?.hashCode() ?: 0)
            result = 31 * result + contents.hashCode()
            return result
        }
    }
}
