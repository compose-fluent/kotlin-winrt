package io.github.composefluent.winrt.gallery.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getConstructors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class GalleryProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        GalleryProcessor(
            environment.codeGenerator,
            environment.logger,
            requireNotNull(environment.options["gallery.repositoryRoot"]) { "Gallery KSP requires gallery.repositoryRoot" },
        )
}

private class GalleryProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val repositoryRoot: String,
) : SymbolProcessor {
    private val entries = linkedMapOf<String, Entry>()
    private val sources = linkedSetOf<KSFile>()
    private val symbolNames = sortedSetOf<String>()
    private var failed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferred = mutableListOf<KSAnnotated>()
        // Symbol is a projected value class, not a Kotlin enum. Resolve its
        // generated companion through KSP so JVM and Native need no reflection.
        val symbolType = "microsoft.ui.xaml.controls.Symbol"
        resolver.getClassDeclarationByName(resolver.getKSNameFromString(symbolType))?.let { symbol ->
            symbol.containingFile?.let(sources::add)
            symbol.declarations.filterIsInstance<KSClassDeclaration>().filter { it.isCompanionObject }.forEach { companion ->
                companion.declarations.filterIsInstance<KSPropertyDeclaration>().filter {
                    it.type.resolve().declaration.qualifiedName?.asString() == symbolType
                }.forEach { symbolNames += it.simpleName.asString() }
            }
        }
        for (kind in listOf("GalleryGroupEntry", "GalleryPage", "GallerySample")) {
            val annotationName = "$galleryPackage.$kind"
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                val declaration = symbol as? KSDeclaration
                if (declaration == null) {
                    logger.error("@$kind requires a declaration", symbol)
                    failed = true
                    continue
                }
                val annotation = declaration.annotations.firstOrNull {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
                }
                if (annotation == null) {
                    deferred += symbol
                    continue
                }
                val arguments = annotation.arguments.associate { it.name!!.asString() to it.value.toString() }
                val homePage = kind == "GalleryPage" && arguments["route"] == "Home"
                if (declaration.parentDeclaration != null && !homePage) {
                    logger.error("@$kind requires a top-level declaration", symbol)
                    failed = true
                    continue
                }
                val pageClass = kind == "GalleryPage" && declaration is KSClassDeclaration
                if (pageClass) {
                    val page = declaration as KSClassDeclaration
                    val accessibleConstructor = page.getConstructors().any {
                        it.parameters.isEmpty() && Modifier.PRIVATE !in it.modifiers && Modifier.PROTECTED !in it.modifiers
                    }
                    val isElement = page.getAllSuperTypes().any {
                        it.declaration.qualifiedName?.asString() == "microsoft.ui.xaml.UIElement"
                    }
                    if (page.classKind != ClassKind.CLASS || Modifier.ABSTRACT in page.modifiers ||
                        Modifier.SEALED in page.modifiers || Modifier.INNER in page.modifiers ||
                        Modifier.PRIVATE in page.modifiers || page.typeParameters.isNotEmpty() ||
                        !accessibleConstructor || !isElement) {
                        logger.error("@GalleryPage requires an accessible, concrete, non-generic UIElement subclass with a zero-parameter constructor", symbol)
                        failed = true
                        continue
                    }
                }
                if (kind in setOf("GalleryPage", "GallerySample") && !homePage && !pageClass && (declaration !is KSFunctionDeclaration ||
                        (kind == "GalleryPage" && declaration.parameters.isNotEmpty()) || declaration.extensionReceiver != null ||
                        declaration.typeParameters.isNotEmpty() || Modifier.SUSPEND in declaration.modifiers ||
                        Modifier.PRIVATE in declaration.modifiers)) {
                    logger.error("@$kind requires an accessible, non-generic, non-suspend top-level function; pages must be parameterless", symbol)
                    failed = true
                    continue
                }
                val name = declaration.qualifiedName?.asString() ?: continue
                val file = declaration.containingFile
                entries["$kind:$name"] = Entry(
                    kind, arguments, name, file?.fileName.orEmpty(),
                    if (kind == "GalleryPage") repositoryRelativePath(checkNotNull(file).filePath, repositoryRoot) else "",
                )
                file?.let(sources::add)
            }
        }
        return deferred
    }

    override fun finish() {
        if (failed || entries.isEmpty()) return
        val descriptions = checkNotNull(javaClass.getResourceAsStream("/ControlInfoData.json"))
            .bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
        val output = try {
            generate(entries.values.toList(), descriptions)
        } catch (exception: IllegalArgumentException) {
            logger.error(exception.message.orEmpty())
            return
        }
        codeGenerator.createNewFile(
            Dependencies(aggregating = true, *sources.toTypedArray()), galleryPackage, "GeneratedGallery",
        ).bufferedWriter().use { it.write(output) }
        generateSourcePreviews()
        if (symbolNames.isEmpty()) {
            logger.error("The generated WinUI Symbol projection must be available to Gallery KSP processing")
            return
        }
        codeGenerator.createNewFile(
            Dependencies(aggregating = true, *sources.toTypedArray()), galleryPackage, "GeneratedGallerySymbols",
        ).bufferedWriter().use { writer ->
            writer.appendLine("// Generated from WinUI Symbol companion properties by KSP.")
            writer.appendLine("package $galleryPackage")
            writer.appendLine("internal actual object GallerySymbols {")
            writer.appendLine("  actual fun find(name: String): microsoft.ui.xaml.controls.Symbol? = when (name) {")
            symbolNames.forEach { name -> writer.appendLine("    \"$name\" -> microsoft.ui.xaml.controls.Symbol.`$name`") }
            writer.appendLine("    else -> null")
            writer.appendLine("  }")
            writer.appendLine("}")
        }
    }

    private fun generateSourcePreviews() {
        val pages = entries.values.filter { it.kind == "GalleryPage" && it.route != "Home" }.sortedBy { it.route }
        val samples = entries.values.filter { it.kind == "GallerySample" }
        val sampleByTitle = samples.associateBy { it.route to it.value("title") }
        require(sampleByTitle.size == samples.size) { "Duplicate @GallerySample route/title" }
        require(samples.all { sample -> pages.any { it.route == sample.route } }) { "@GallerySample references an unknown page" }
        val texts = (pages + samples).map { it.source }.distinct().associateWith { fileName ->
            val file = sources.single { it.fileName == fileName }
            java.io.File(file.filePath).readText().replace("\r\n", "\n").replace('\r', '\n')
        }
        val extractor = KotlinExampleExtractor()
        val sampleExtractor = KotlinSampleSourceExtractor()
        val parser = KotlinSourceParser()
        val xamlDocuments = linkedMapOf<String, Map<String, String>>()
        val sampleDefinitions = linkedMapOf<String, Triple<String, String, String>>()
        val sourceDocuments = linkedMapOf<String, String>()
        data class RoutePreviews(val titleIndices: Map<String, Int>, val count: Int)
        val examples = pages.mapIndexed { routeIndex, page ->
            val pageFile = java.io.File(sources.single { it.fileName == page.source }.filePath)
            val xamlFile = java.io.File(pageFile.parentFile, "${pageFile.nameWithoutExtension}.xaml")
            val pageDeclaration = sources.single { it.fileName == page.source }.declarations.firstOrNull {
                it.qualifiedName?.asString() == page.symbol
            }
            if (xamlFile.isFile || pageDeclaration is KSClassDeclaration) {
                val definitions = java.io.File(pageFile.parentFile, "SampleDefinitions/${page.route}")
                    .listFiles()?.filter { it.extension == "txt" }?.sortedBy { it.name }.orEmpty()
                if (definitions.isNotEmpty()) {
                    val (structured, raw) = definitions.partition { file -> file.readLines().any { it.startsWith("--- ") } }
                    val parsed = structured.map { it to GallerySampleDefinition.parse(it.readText()) }
                    val headers = parsed.map { it.second.header }.filter(String::isNotBlank)
                    require(headers.distinct().size == headers.size) {
                        "Duplicate sample header for ${page.route}"
                    }
                    val documents = parsed.flatMapIndexed { index, (file, sample) -> listOf(
                        "GalleryCode${routeIndex}_$index" to parser.parse("${file.nameWithoutExtension}.kt", sample.kotlin, isScript = true),
                        "GalleryXaml${routeIndex}_$index" to XamlSourceParser().parse("${file.nameWithoutExtension}.xaml", sample.xaml),
                    ) }.toMutableList()
                    raw.forEachIndexed { index, file ->
                        val name = "GallerySource${routeIndex}_$index"
                        documents += name to XamlSourceParser().parse("${file.nameWithoutExtension}.xaml", file.readText())
                        sourceDocuments["${page.route}\\${file.name}"] = name
                    }
                    if (parsed.isEmpty()) {
                        documents += "GalleryCode${routeIndex}_0" to parser.parse(pageFile.name, pageFile.readText(), isScript = false)
                        if (xamlFile.isFile) documents += "GalleryXaml${routeIndex}_0" to XamlSourceParser().parse(xamlFile.name, xamlFile.readText())
                    }
                    parsed.forEachIndexed { index, (file, sample) ->
                        val name = file.name.replace(Regex("^\\d+-"), "")
                        val path = "${page.route}\\$name"
                        require(path !in sampleDefinitions) { "Duplicate sample definition $path" }
                        sampleDefinitions[path] = Triple(sample.header, "GalleryCode${routeIndex}_$index", "GalleryXaml${routeIndex}_$index")
                        sourceDocuments[path] = "GalleryXaml${routeIndex}_$index"
                    }
                    codeGenerator.createNewFile(
                        Dependencies(true, *sources.toTypedArray()), galleryPackage, "GalleryCode$routeIndex",
                    ).bufferedWriter().use { it.write(generateCodeDocuments(documents, emptyMap())) }
                    xamlDocuments[page.route] = if (parsed.isEmpty()) mapOf("" to "GalleryXaml${routeIndex}_0") else parsed.mapIndexed { index, (file, sample) ->
                        sample.header.ifBlank { file.name } to "GalleryXaml${routeIndex}_$index"
                    }.toMap()
                    return@mapIndexed RoutePreviews(parsed.mapIndexedNotNull { index, (_, sample) ->
                        sample.header.takeIf(String::isNotBlank)?.let { it to index }
                    }.toMap(), parsed.size.coerceAtLeast(1))
                }
                // Class registrations show the complete code-behind, with adjacent markup when present.
                val source = texts.getValue(page.source)
                val kotlinName = "GalleryCode${routeIndex}_0"
                val xamlName = "GalleryXaml$routeIndex"
                val documents = buildList {
                    add(kotlinName to parser.parse(page.source, source, isScript = false))
                    if (xamlFile.isFile) add(xamlName to XamlSourceParser().parse(xamlFile.name, xamlFile.readText()))
                }
                val origins = mapOf(kotlinName to KotlinCodeOriginData(
                    repositoryRelativePath(pageFile.path, repositoryRoot),
                    KotlinSourceFragment(source, IntArray(source.length) { it }),
                ))
                codeGenerator.createNewFile(
                    Dependencies(true, *sources.toTypedArray()), galleryPackage, "GalleryCode$routeIndex",
                ).bufferedWriter().use { it.write(generateCodeDocuments(documents, origins)) }
                if (xamlFile.isFile) xamlDocuments[page.route] = mapOf("" to xamlName)
                return@mapIndexed RoutePreviews(emptyMap(), 1)
            }
            val origins = mutableMapOf<String, KotlinCodeOriginData>()
            fun document(name: String, fileName: String, fragment: KotlinSourceFragment): Pair<String, io.github.composefluent.winrt.gallery.code.KotlinCodeDocument> {
                origins[name] = KotlinCodeOriginData(
                    repositoryRelativePath(sources.single { it.fileName == fileName }.filePath, repositoryRoot), fragment)
                return name to parser.parse(fileName, fragment.source, isScript = true)
            }
            val snippets = extractor.extract(texts.getValue(page.source), page.symbol.substringAfterLast('.'))
            require(snippets.isNotEmpty()) { "Could not locate source for ${page.symbol} in ${page.source}" }
            val objects = snippets.mapIndexed { exampleIndex, snippet ->
                val sample = snippet.titles.firstNotNullOfOrNull { sampleByTitle[page.route to it] }
                val fragment = sample?.let {
                    sampleExtractor.extractFragment(texts.getValue(it.source), it.symbol.substringAfterLast('.'))
                } ?: snippet.fragment
                document("GalleryCode${routeIndex}_$exampleIndex", sample?.source ?: page.source, fragment)
            }.toMutableList()
            val titleIndices = snippets.flatMapIndexed { index, snippet -> snippet.titles.map { it to index } }
                .toMap().toMutableMap()
            // Some pages compose examples through a helper rather than calling
            // example() directly. Their annotated sample factories still own the
            // CodeView source and are indexed by their declared titles.
            samples.filter { it.route == page.route && it.value("title") !in titleIndices }.forEach { sample ->
                val index = objects.size
                val body = sampleExtractor.extractFragment(texts.getValue(sample.source), sample.symbol.substringAfterLast('.'))
                objects += document("GalleryCode${routeIndex}_$index", sample.source, body)
                titleIndices[sample.value("title")] = index
            }
            codeGenerator.createNewFile(
                Dependencies(true, *sources.toTypedArray()), galleryPackage, "GalleryCode$routeIndex",
            ).bufferedWriter().use { it.write(generateCodeDocuments(objects, origins)) }
            RoutePreviews(titleIndices, objects.size)
        }
        codeGenerator.createNewFile(
            Dependencies(true, *sources.toTypedArray()), galleryPackage, "GeneratedGalleryCode",
        ).bufferedWriter().use { writer ->
            writer.appendLine("package $galleryPackage")
            writer.appendLine("internal actual object GalleryCodeCatalog {")
            writer.appendLine("  actual fun document(route: String, title: String, index: Int): $galleryPackage.code.KotlinCodeDocument? = when (route) {")
            pages.forEachIndexed { routeIndex, page ->
                val previews = examples[routeIndex]
                writer.appendLine("    ${kotlinLiteral(page.route)} -> when (title) {")
                previews.titleIndices.forEach { (title, exampleIndex) ->
                    writer.appendLine("      ${kotlinLiteral(title)} -> GalleryCode${routeIndex}_$exampleIndex.create()")
                }
                writer.appendLine("      else -> when (index) {")
                (0 until previews.count).forEach { exampleIndex ->
                    writer.appendLine("        $exampleIndex -> GalleryCode${routeIndex}_$exampleIndex.create()")
                }
                writer.appendLine("        else -> GalleryCode${routeIndex}_${previews.count - 1}.create()")
                writer.appendLine("      }")
                writer.appendLine("    }")
            }
            writer.appendLine("    else -> null\n  }")
            writer.appendLine("  actual fun xamlDocument(route: String, title: String, index: Int): $galleryPackage.code.KotlinCodeDocument? = when (route) {")
            xamlDocuments.forEach { (route, documents) ->
                writer.appendLine("    ${kotlinLiteral(route)} -> when (title) {")
                documents.forEach { (title, name) -> writer.appendLine("      ${kotlinLiteral(title)} -> $name.create()") }
                writer.appendLine("      else -> when (index) {")
                documents.values.forEachIndexed { index, name -> writer.appendLine("        $index -> $name.create()") }
                writer.appendLine("        else -> null\n      }\n    }")
            }
            writer.appendLine("    else -> null\n  }")
            writer.appendLine("  actual fun sampleDefinition(path: String): GallerySampleCode? = when (path.replace('/', '\\\\')) {")
            sampleDefinitions.forEach { (path, document) ->
                writer.appendLine("    ${kotlinLiteral(path)} -> GallerySampleCode(${kotlinLiteral(document.first)}, ${document.second}.create(), ${document.third}.create())")
            }
            writer.appendLine("    else -> null\n  }")
            writer.appendLine("  actual fun sourceDocument(path: String): $galleryPackage.code.KotlinCodeDocument? = when (path.replace('/', '\\\\')) {")
            sourceDocuments.forEach { (path, name) ->
                writer.appendLine("    ${kotlinLiteral(path)} -> $name.create()")
            }
            writer.appendLine("    else -> null\n  }")
            writer.appendLine("}")
        }
    }
}
