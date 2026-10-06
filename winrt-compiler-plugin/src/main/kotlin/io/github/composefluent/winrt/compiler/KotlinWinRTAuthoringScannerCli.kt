package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.metadata.WinRTXamlNamespaces

import io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType
import io.github.composefluent.winrt.compiler.authoring.KotlinImports
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredRuntimeClassAnnotation
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.PROJECTION_PACKAGE_PREFIX
import io.github.composefluent.winrt.compiler.authoring.WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION
import io.github.composefluent.winrt.compiler.authoring.inheritedOverridableInterfaceNames
import io.github.composefluent.winrt.compiler.authoring.requiresComponentAuthoring
import io.github.composefluent.winrt.compiler.authoring.projectionPackageToMetadataName
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTType
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTTypeByProjectedName
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import io.github.composefluent.winrt.metadata.WinRTAuthoredRuntimeClassDescriptor
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTXamlApplicationProperty
import io.github.composefluent.winrt.metadata.WinRTXamlApplicationEvent
import io.github.composefluent.winrt.metadata.WinRTXamlApplicationMethod
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeMembers
import io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeDescriptor
import io.github.composefluent.winrt.metadata.WinRTCollectionInterfaceKind
import io.github.composefluent.winrt.compiler.xaml.writeXamlBindingSupportSource
import io.github.composefluent.winrt.compiler.xaml.loadXamlReferenceTypeAssemblyNames
import io.github.composefluent.winrt.compiler.xaml.xamlKotlinTypeArguments
import io.github.composefluent.winrt.compiler.xaml.xamlPropertyRegistrationSource
import io.github.composefluent.winrt.compiler.xaml.xamlAttachedRegistrationSources
import io.github.composefluent.winrt.compiler.xaml.xamlCollectionRegistrationSources
import io.github.composefluent.winrt.compiler.xaml.xamlCreateFromStringMethodSource
import io.github.composefluent.winrt.compiler.xaml.XamlStaticAccessor
import io.github.composefluent.winrt.compiler.xaml.writeXamlProjectedTypeRegistrationSource
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.winRTArrayElementForKotlinType
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
import io.github.composefluent.winrt.metadata.winRTCollectionAbiNameForKotlinType
import io.github.composefluent.winrt.metadata.winRTCollectionKindForAbiName
import io.github.composefluent.winrt.metadata.toKotlinProjectionTypeName
import io.github.composefluent.winrt.metadata.isWinRTValueType
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
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

object KotlinWinRTAuthoringScannerCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = CliOptions.parse(args)
        val index = readAuthoringMetadataIndex(options.metadataIndex)
        if (options.xamlHeader) {
            writeXamlHeader(options, index)
            return
        }
        val sourceTypeNames = mutableSetOf<String>()
        val scanned = scan(options.sourceRoots, index, options.sourceRootOwners, sourceTypeNames)
        val pages = options.xamlDeclarations.flatMap { WinRTXamlDeclarations.parse(it.readText()).pages }.distinctBy { it.className }
        val byName = scanned.associateBy { it.sourceTypeName }
        val connector = "Microsoft.UI.Xaml.Markup.IComponentConnector"
        if (pages.isNotEmpty()) {
            require(index[connector]?.kind == "Interface") { "XAML authoring requires $connector in the metadata index." }
        }
        pages.forEach { page ->
            val candidate = requireNotNull(byName[page.className]) {
                "XAML class ${page.className} must resolve to an authored Kotlin class."
            }
            // Application bases are checked against Kotlin's direct superclass by
            // semantic compilation; the authored ABI candidate owns the SDK base.
            require(page.baseTypeName in sourceTypeNames || candidate.winRTBaseClassName == page.baseTypeName) {
                "XAML class ${page.className} requires base ${page.baseTypeName}, found ${candidate.winRTBaseClassName}."
            }
        }
        val pageNames = pages.map { it.className }.toSet()
        val candidates = scanned.map { candidate ->
            if (candidate.sourceTypeName in pageNames) candidate.copy(
                winRTInterfaceNames = (candidate.winRTInterfaceNames + connector).distinct().sorted(),
            ) else candidate
        }
        KotlinWinRTAuthoringCandidateFile.write(options.output, candidates)
    }

    /** Source declarations precede XamlCompiler pass 1; Kotlin IR checks them in the semantic pass. */
    private fun writeXamlHeader(options: CliOptions, index: Map<String, IndexedWinRTType>) {
        val sources = options.sourceRoots.flatMap(::kotlinSourceFiles).distinct().sorted()
        val candidates = scan(options.sourceRoots, index, options.sourceRootOwners).associateBy { it.sourceTypeName }
        val declarations = sources.flatMap { path ->
            val source = parseSource(path)
            source.classes().filter { !source.isNestedClass(it) && source.isEffectivelyAuthorableClass(it) &&
                (source.isRuntimeClassDeclaration(it) || source.isObjectDeclaration(it)) && !source.hasTypeParameters(it) }.mapNotNull { klass ->
                // An object expression in a top-level property or function is an unnamed
                // node outside every class. It declares no type that markup could name.
                val simpleName = source.className(klass) ?: return@mapNotNull null
                val name = listOf(source.packageName(), simpleName).filter(String::isNotBlank).joinToString(".")
                XamlHeaderClass(source, klass, name, candidates[name], sourceSetName = sourceSetForPath(path, options.sourceRootOwners))
            }
        }.associateBy { it.name }
        val classes = declarations.mapValues { (_, type) ->
            val imports = parseImports(type.source)
            if (type.source.enumEntries(type.klass) != null) return@mapValues type.copy(schemaBaseName = "System.Enum")
            val base = type.source.superTypeNames(type.klass).firstNotNullOfOrNull { name ->
                val local = resolveApplicationTypeName(name, type.packageName, imports, declarations.keys)
                if (local != null && declarations.getValue(local).source.isRuntimeClassDeclaration(declarations.getValue(local).klass)) local
                else resolveIndexedWinRTType(name, type.packageName, imports, index)?.takeIf { it.kind == "RuntimeClass" }?.qualifiedName
            }
            type.copy(schemaBaseName = base)
        }
        val pageNames = sources.filter { Files.isRegularFile(it.resolveSibling("${it.name.removeSuffix(".kt")}.xaml")) }
            .mapTo(mutableSetOf()) { path ->
                val simpleName = path.name.removeSuffix(".kt")
                val source = parseSource(path)
                val name = listOf(source.packageName(), simpleName).filter(String::isNotBlank).joinToString(".")
                val type = requireNotNull(classes[name]) { "XAML $path requires one top-level Kotlin class $name" }
                require(type.candidate?.winRTBaseClassName != null) {
                    "XAML $name requires a WinRT base"
                }
                name
            }
        val selected = (pageNames + referencedXamlTypes(options.sourceRoots, classes.keys)).toMutableSet()
        // Dependency application schemas retain Kotlin names; they are not projected
        // SDK types and must never enter component authoring/projection generation.
        val dependencyTypes = options.references.filter { it.name.endsWith(".KotlinXaml.winmd") }
            .flatMap { WinRTMetadataLoader.load(it).namespaces.flatMap { namespace -> namespace.types } }
        val applicationNames = classes.keys + dependencyTypes.map { it.qualifiedName }
        val enumNames = classes.values.filter { it.source.enumEntries(it.klass) != null }.mapTo(mutableSetOf()) { it.name } +
            dependencyTypes.filter { it.kind == WinRTTypeKind.Enum }.map { it.qualifiedName }
        val superInterfaces = mutableMapOf<String, List<Pair<WinRTTypeRef, String>>>()
        fun interfaces(name: String) = superInterfaces.getOrPut(name) {
            val type = classes.getValue(name)
            val imports = parseImports(type.source)
            val resolve = type.source.xamlTypeResolver(applicationNames, index, enumNames)
            type.source.superTypes(type.klass).mapNotNull { raw ->
                val base = raw.substringBefore('<').trim()
                if (winRTCollectionAbiNameForKotlinType(base) == null &&
                    resolveIndexedWinRTType(base, type.packageName, imports, index)?.kind != WinRTTypeKind.Interface.name) null
                else resolve(raw) to raw
            }
        }
        val schemas = mutableMapOf<String, XamlSourceMembers>()
        val sourceSchemas = mutableMapOf<String, Result<XamlSourceMembers>>()
        fun sourceSchema(name: String): Result<XamlSourceMembers> = sourceSchemas.getOrPut(name) {
            val type = classes.getValue(name)
            runCatching { if (type.source.enumEntries(type.klass) != null)
                XamlSourceMembers(WinRTXamlApplicationTypeMembers(), emptyList())
            else type.source.xamlApplicationMembers(type.klass, applicationNames, index, enumNames) }
        }
        // Private x:Bind inputs belong only to the compiler's temporary schema. An
        // unrelated private implementation object must not pull an unprojectable
        // object graph into the application WinMD. Public members remain strict.
        fun representable(ref: WinRTTypeRef, visiting: Set<String> = emptySet()): Boolean {
            if (!ref.typeArguments.all { representable(it, visiting) }) return false
            val name = ref.qualifiedName?.takeIf(classes::containsKey) ?: return true
            if (name in visiting) return true
            val dependency = sourceSchema(name).getOrNull() ?: return false
            val path = visiting + name
            return dependency.properties.filter { it.metadata.isPublic }.all { representable(it.metadata.type, path) } &&
                dependency.metadata.events.all { representable(it.handlerType, path) }
        }
        // Schema dependencies, including data models and collection elements, precede pass 1.
        while (schemas.keys != selected) {
            for (name in selected.toList().sorted().filterNot(schemas::containsKey)) {
                classes.getValue(name).schemaBaseName?.takeIf(classes::containsKey)?.let(selected::add)
                val raw = sourceSchema(name).getOrThrow()
                val properties = raw.properties.filter { it.metadata.isPublic || representable(it.metadata.type) }
                val methods = raw.methods.filter { method ->
                    (method.metadata.parameterTypes + method.metadata.returnType).all { representable(it) }
                }
                val schema = raw.copy(properties = properties, methods = methods,
                    metadata = raw.metadata.copy(properties = properties.map(XamlSourceProperty::metadata),
                        methods = methods.map(XamlSourceMethod::metadata)))
                schemas[name] = schema
                fun include(ref: WinRTTypeRef) {
                    ref.qualifiedName?.takeIf(classes::containsKey)?.let(selected::add)
                    ref.typeArguments.forEach(::include)
                }
                schema.properties.forEach { include(it.metadata.type) }
                schema.metadata.events.forEach { include(it.handlerType) }
                schema.metadata.methods.forEach { method -> (method.parameterTypes + method.returnType).forEach(::include) }
                schema.metadata.createFromStringMethod?.substringBeforeLast('.', "")?.takeIf(classes::containsKey)?.let(selected::add)
                interfaces(name).forEach { include(it.first) }
            }
        }
        val applicationClasses = selected.sorted().map(classes::getValue)
        val members = schemas.mapValues { it.value.metadata }
        val descriptors = applicationClasses.map { type ->
            WinRTXamlApplicationTypeDescriptor(type.name, type.schemaBaseName,
                ((if (type.name in pageNames) listOf("Microsoft.UI.Xaml.Markup.IComponentConnector")
                else type.candidate?.winRTInterfaceNames.orEmpty()) + interfaces(type.name).map { it.first.typeName }).distinct(),
                isActivatable = type.source.enumEntries(type.klass) == null && !type.source.isObjectDeclaration(type.klass) && !type.source.isAbstractClass(type.klass) &&
                    type.source.hasPublicDefaultActivationConstructor(type.klass, allowDefaultArguments = true),
                isSealed = !type.source.isUnsealedAuthoredClass(type.klass),
                enumEntries = type.source.enumEntries(type.klass))
        }
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd(
            options.xamlAssemblyName?.let { "$it.KotlinXaml" } ?: "KotlinXaml", descriptors, members, options.output,
            loadXamlReferenceTypeAssemblyNames(options.references),
            index.values.filter { it.kind == WinRTTypeKind.Enum.name || it.kind == WinRTTypeKind.Struct.name }
                .mapTo(mutableSetOf()) { it.qualifiedName },
        )
        options.xamlHeaderSources?.let { writeXamlRegistrationSources(it, applicationClasses, schemas, superInterfaces, options.xamlAssemblyName,
            options.references, referencedXamlTypes(options.sourceRoots, index.keys)) }
    }

    private data class XamlHeaderClass(val source: KotlinLightSource, val klass: LighterASTNode,
        val name: String, val candidate: KotlinWinRTAuthoredTypeCandidate?,
        val schemaBaseName: String? = candidate?.winRTBaseClassName,
        val sourceSetName: String? = candidate?.sourceSetName) {
        val className get() = name.substringAfterLast('.')
        val packageName get() = name.substringBeforeLast('.', "")
    }

    /** Namespace discovery only; XamlCompiler remains responsible for parsing XAML and binding paths. */
    private fun referencedXamlTypes(roots: List<Path>, names: Set<String>): Set<String> = buildSet {
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        }
        roots.flatMap { root ->
            if (Files.isDirectory(root)) Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.extension.equals("xaml", true) }.toList()
            } else emptyList()
        }.distinct().sorted().forEach { path ->
            Files.newInputStream(path).use { input ->
                val xml = factory.createXMLStreamReader(input)
                try { while (xml.hasNext()) {
                    if (xml.next() != XMLStreamConstants.START_ELEMENT) continue
                    fun include(namespace: String?, local: String) {
                        WinRTXamlNamespaces.namespaces(namespace.orEmpty()).firstNotNullOfOrNull { ns ->
                            "$ns.${local.substringBefore('.')}".takeIf { it in names }
                        }?.let(::add)
                    }
                    include(xml.namespaceURI, xml.localName)
                    // Conditional namespaces refer to condition types in the URI query.
                    // This discovers their headers; XamlCompiler parses and validates the condition.
                    for (i in 0 until xml.namespaceCount) {
                        Regex("""\b([\w]+):([\w]+)\(""").findAll(xml.getNamespaceURI(i).orEmpty().substringAfter('?', "")).forEach { match ->
                            include(xml.getNamespaceURI(match.groupValues[1]), match.groupValues[2])
                        }
                    }
                    for (i in 0 until xml.attributeCount) {
                        include(xml.getAttributeNamespace(i), xml.getAttributeLocalName(i))
                        Regex("""\b([\w]+):([\w]+)""").findAll(xml.getAttributeValue(i)).forEach { match ->
                            include(xml.getNamespaceURI(match.groupValues[1]), match.groupValues[2])
                        }
                    }
                } } finally { xml.close() }
            }
        }
    }

    private data class XamlSourceProperty(val metadata: WinRTXamlApplicationProperty, val kotlinType: String)
    private data class XamlSourceMethod(val metadata: WinRTXamlApplicationMethod,
        val kotlinReturnType: String, val kotlinParameterTypes: List<String>)
    private data class XamlSourceMembers(
        val metadata: WinRTXamlApplicationTypeMembers,
        val properties: List<XamlSourceProperty>,
        val methods: List<XamlSourceMethod> = emptyList(),
    )

    private fun String.kotlinLiteral(): String = buildString {
        append('"')
        for (character in this@kotlinLiteral) when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(character)
        }
        append('"')
    }

    private fun writeXamlRegistrationSources(
        root: Path,
        pages: List<XamlHeaderClass>,
        schemas: Map<String, XamlSourceMembers>,
        superInterfaces: Map<String, List<Pair<WinRTTypeRef, String>>>,
        assemblyName: String? = null,
        references: List<Path> = emptyList(),
        projectedNames: Set<String> = emptySet(),
    ) {
        Files.createDirectories(root)
        val registrations = pages.map { type ->
            val source = type.source
            val klass = type.klass
            val candidate = type
            val schema = schemas.getValue(candidate.name)
            val registerName = "registerKotlinWinRTXaml${candidate.className}"
            val ownerRoot = candidate.sourceSetName?.let { root.resolve("sourceSets/$it") } ?: root
            val file = ownerRoot.resolve(candidate.packageName.replace('.', '/'))
                .resolve("KotlinWinRTXaml${candidate.className}.kt")
            Files.createDirectories(file.parent)
            val code = buildString {
                appendLine("@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)")
                if (candidate.packageName.isNotBlank()) appendLine("package ${candidate.packageName}")
                source.imports().forEach { appendLine("import $it") }
                appendLine()
                appendLine("internal fun $registerName() {")
                if (source.enumEntries(klass) != null) {
                    appendLine("  io.github.composefluent.winrt.runtime.registerWinRTXamlEnumType(${candidate.className}::class,")
                    appendLine("    ${candidate.name.kotlinLiteral()}, ${candidate.className}.entries.toTypedArray())")
                    appendLine("}")
                    return@buildString
                }
                appendLine("  io.github.composefluent.winrt.runtime.registerWinRTXamlTypeDefinition(")
                appendLine("    io.github.composefluent.winrt.runtime.WinRTXamlTypeDefinition(")
                appendLine("      type = ${candidate.className}::class,")
                appendLine("      name = ${candidate.name.kotlinLiteral()},")
                appendLine("      baseName = ${(candidate.schemaBaseName ?: "System.Object").kotlinLiteral()},")
                appendLine("      baseType = ${if (candidate.schemaBaseName == null) "Any" else source.superTypeNames(klass).first()}::class,")
                appendLine("      isWinRTComponent = ${candidate.candidate != null},")
                if (!source.isObjectDeclaration(klass) && !source.isAbstractClass(klass) && source.hasPublicDefaultActivationConstructor(klass, allowDefaultArguments = true)) appendLine("      activate = { ${candidate.className}() },")
                source.staticInitializerOwner(klass)?.let { owner ->
                    appendLine("      initializer = { ${candidate.className}${if (owner.isEmpty()) "" else ".$owner"}; Unit },")
                }
                schema.metadata.contentProperty?.let { appendLine("      contentProperty = ${it.kotlinLiteral()},") }
                xamlCreateFromStringMethodSource(candidate.name, schemas.mapValues { it.value.metadata })?.let { factory ->
                    appendLine("      createFromString = { $factory(it) },")
                }
                val convert: (String, String) -> String = { value, type -> "io.github.composefluent.winrt.generated.xaml.kotlinWinRTXamlMemberValue<$type>($value)" }
                val shape = xamlCollectionRegistrationSources(superInterfaces[candidate.name].orEmpty(), convert)
                if (shape.isNotEmpty()) {
                    appendLine("      shape = ${shape.last()},")
                    appendLine("      valueTypes = listOf(${shape.joinToString(",\n")}),")
                }
                appendLine("      members = listOf(")
                schema.properties.filter { it.metadata.isPublic && !it.metadata.isStatic }.forEach { property ->
                    appendLine(xamlPropertyRegistrationSource(candidate.className, property.metadata, property.kotlinType, convert).prependIndent("        ") + ",")
                }
                xamlAttachedRegistrationSources(candidate.className, schema.metadata, schema.methods.map {
                    XamlStaticAccessor(it.metadata, it.kotlinReturnType, it.kotlinParameterTypes)
                }, convert).forEach { member ->
                    appendLine(member.prependIndent("        ") + ",")
                }
                appendLine("      ),")
                appendLine("    ),")
                appendLine("  )")
                appendLine("}")
            }
            file.writeText(code)
            candidate.sourceSetName to "${candidate.packageName}.$registerName".removePrefix(".")
        }.toMutableList()
        val projectedOwner = pages.firstOrNull()?.sourceSetName
        val projectedRoot = projectedOwner?.let { root.resolve("sourceSets/$it") } ?: root
        if (pages.isNotEmpty()) writeXamlProjectedTypeRegistrationSource(projectedRoot, references, projectedNames, assemblyName)?.let {
            registrations.add(projectedOwner to it)
        }
        // Resource-only KMP libraries still need a semantic compilation. Their
        // shared source root is attached even when there are no adjacent page classes.
        val supportRoot = root.resolve("shared")
        if (pages.isEmpty()) {
            // Keep the compiler invocation alive so it exports the empty schema,
            // without pulling SDK projections into a pure XAML resource library.
            val suffix = assemblyName?.replace(Regex("[^A-Za-z0-9_]"), "_")?.let { "_$it" }.orEmpty()
            val marker = supportRoot.resolve("io/github/composefluent/winrt/generated/xaml/KotlinXamlResources$suffix.kt")
            Files.createDirectories(marker.parent)
            marker.writeText("package io.github.composefluent.winrt.generated.xaml\n" +
                "private const val kotlinWinRTXamlResources$suffix = ${(assemblyName ?: "KotlinXaml").kotlinLiteral()}\n")
            root.resolve("registrars.tsv").writeText("className\n")
            return
        }
        val converter = supportRoot.resolve("io/github/composefluent/winrt/generated/xaml/KotlinXamlMemberValue.kt")
        Files.createDirectories(converter.parent)
        converter.writeText(buildString {
            appendLine("package io.github.composefluent.winrt.generated.xaml")
            // XamlTypeExtensions.GetStringToTypeConversion uses the same SDK
            // converter when a XAML literal has not already been boxed as its
            // target type. Keep parsing out of application code and runtime ABI.
            appendLine("internal inline fun <reified T> kotlinWinRTXamlMemberValue(value: Any?): T {")
            appendLine("  if (value is T || value !is String) return value as T")
            appendLine("  return io.github.composefluent.winrt.runtime.convertWinRTXamlLiteral(T::class, value) { type, text ->")
            appendLine("    microsoft.ui.xaml.markup.XamlBindingHelper.convertValue(type, text)")
            appendLine("  } as T")
            appendLine("}")
        })
        val registrarNames = registrations.groupBy { it.first }.toSortedMap(compareBy { it.orEmpty() })
            .map { (owner, entries) ->
                val ownerRoot = owner?.let { root.resolve("sourceSets/$it") } ?: root
                val moduleSuffix = assemblyName?.replace(Regex("[^A-Za-z0-9_]"), "_")?.let { "_$it" }.orEmpty()
                val registryName = "KotlinXamlApplicationDefinitions$moduleSuffix" + (owner?.let { "_$it" } ?: "")
                val registry = ownerRoot.resolve("io/github/composefluent/winrt/generated/xaml/$registryName.kt")
                Files.createDirectories(registry.parent)
                registry.writeText(buildString {
                    appendLine("package io.github.composefluent.winrt.generated.xaml")
                    appendLine("import io.github.composefluent.winrt.runtime.asWinRT")
                    appendLine("object $registryName {")
                    appendLine("  private val registration: Unit = run {")
                    appendLine("    io.github.composefluent.winrt.runtime.configureWinRTXamlHotReload(")
                    appendLine("      dispatcherFactory = {")
                    appendLine("        val queue = microsoft.ui.dispatching.DispatcherQueue.getForCurrentThread()")
                    appendLine("        val enqueue: (() -> Unit) -> Boolean = { action ->")
                    appendLine("          queue.tryEnqueue(microsoft.ui.dispatching.DispatcherQueueHandler { action() })")
                    appendLine("        }")
                    appendLine("        enqueue")
                    appendLine("      },")
                    appendLine("      sdkConvert = { type, text -> microsoft.ui.xaml.markup.XamlBindingHelper.convertValue(type, text) },")
                    appendLine("      loadResources = { markup ->")
                    appendLine("        requireNotNull(microsoft.ui.xaml.markup.XamlReader.load(markup))")
                    appendLine("          .asWinRT<microsoft.ui.xaml.ResourceDictionary>()")
                    appendLine("      },")
                    appendLine("      loadElement = { markup ->")
                    appendLine("        requireNotNull(microsoft.ui.xaml.markup.XamlReader.load(markup))")
                    appendLine("          .asWinRT<microsoft.ui.xaml.UIElement>()")
                    appendLine("      },")
                    appendLine("    )")
                    entries.forEach { appendLine("    ${it.second}()") }
                    appendLine("  }")
                    appendLine("  fun registerAll() { registration }")
                    appendLine("}")
                })
                "io.github.composefluent.winrt.generated.xaml.$registryName"
            }
        root.resolve("registrars.tsv").writeText((listOf("className") + registrarNames).joinToString("\n", postfix = "\n"))
        writeXamlBindingSupportSource(supportRoot, assemblyName)
    }

    private fun scan(
        sourceRoots: Iterable<Path>,
        winRTTypes: Map<String, IndexedWinRTType>,
        sourceRootOwners: Map<Path, String> = emptyMap(),
        sourceTypeNames: MutableSet<String>? = null,
    ): List<KotlinWinRTAuthoredTypeCandidate> {
        val sourceFiles = sourceRoots
            .onEach { root ->
                require(Files.exists(root)) {
                    "kotlin-winrt authoring scanner source root $root does not exist."
                }
            }
            .flatMap(::kotlinSourceFiles)
            .distinct()
            .sorted()
        val sources = sourceFiles.map { parseSource(it) to sourceSetForPath(it, sourceRootOwners) }
        val sourceClasses = sources.flatMap { source ->
            val (declarationSource, owner) = source
            val packageName = declarationSource.packageName()
            val imports = parseImports(declarationSource)
            declarationSource.classes().mapNotNull { klass ->
                val className = declarationSource.className(klass) ?: return@mapNotNull null
                val sourceTypeName = if (packageName.isBlank()) className else "$packageName.$className"
                SourceClass(declarationSource, klass, packageName, imports, className, sourceTypeName, owner)
            }
        }
        val sourceClassIndex = sourceClasses.associateBy(SourceClass::sourceTypeName)
        sourceTypeNames?.addAll(sourceClassIndex.keys)
        val sourceSubtypedNames = sourceClasses
            .flatMap { sourceClass ->
                sourceClass.source.superTypeNames(sourceClass.klass)
                    .mapNotNull { superType ->
                        resolveSourceTypeName(superType, sourceClass.packageName, sourceClass.imports, sourceClassIndex)
                    }
            }
            .toSet()
        val candidates = sourceClasses
            .mapNotNull { sourceClass -> scanSourceClass(sourceClass, sourceClassIndex, sourceSubtypedNames, winRTTypes) }
        val duplicateTypeNames = candidates
            .groupBy(KotlinWinRTAuthoredTypeCandidate::sourceTypeName)
            .filterValues { matches -> matches.size > 1 }
            .keys
            .sorted()
        require(duplicateTypeNames.isEmpty()) {
            "kotlin-winrt authoring scanner found duplicate authored type candidates: " +
                duplicateTypeNames.joinToString()
        }
        return candidates.sortedBy(KotlinWinRTAuthoredTypeCandidate::sourceTypeName)
    }

    private fun scanSourceClass(
        sourceClass: SourceClass,
        sourceClassIndex: Map<String, SourceClass>,
        sourceSubtypedNames: Set<String>,
        winRTTypes: Map<String, IndexedWinRTType>,
    ): KotlinWinRTAuthoredTypeCandidate? {
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
            (projectedMetadataName != sourceTypeName && projectedMetadataName in winRTTypes)
        ) {
            return null
        }
        val annotation = source.authoredRuntimeClassAnnotation(klass, packageName, imports)
        val inheritedWinRTTypes = inheritedWinRTTypes(sourceClass, sourceClassIndex, winRTTypes)
        val annotatedBase = annotation.baseClassName
            ?.let { typeName ->
                resolveAnnotatedWinRTType(typeName, winRTTypes, sourceTypeName).also { type ->
                    require(type.kind == "RuntimeClass") {
                        "WinRT authored type $sourceTypeName annotation baseClassName must reference a WinRT runtime class: $typeName."
                    }
                }
            }
        val annotatedInterfaces = annotation.interfaceNames
            .map { typeName ->
                resolveAnnotatedWinRTType(typeName, winRTTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation interfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
        val annotatedOverridableInterfaces = annotation.overridableInterfaceNames
            .map { typeName ->
                resolveAnnotatedWinRTType(typeName, winRTTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation overridableInterfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
            .map(IndexedWinRTType::qualifiedName)
        val annotatedActivatableFactoryInterface = annotation.activatableFactoryInterfaceName
            ?.let { typeName ->
                resolveAnnotatedWinRTType(typeName, winRTTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation activatableFactoryInterfaceName must reference a WinRT interface: $typeName."
                    }
                }.qualifiedName
            }
        val annotatedStaticFactoryInterfaces = annotation.staticFactoryInterfaceNames
            .map { typeName ->
                resolveAnnotatedWinRTType(typeName, winRTTypes, sourceTypeName).also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type $sourceTypeName annotation staticFactoryInterfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
            .map(IndexedWinRTType::qualifiedName)
        val resolvedWinRTTypes = listOfNotNull(annotatedBase) + annotatedInterfaces + inheritedWinRTTypes
        if (!requiresComponentAuthoring(resolvedWinRTTypes, annotation.isPresent)) {
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
        val winRTBase = resolvedWinRTTypes.firstOrNull { type -> type.kind == "RuntimeClass" }
        val directInterfaces = resolvedWinRTTypes
            .filter { type -> type.kind == "Interface" }
            .map { type -> type.qualifiedName }
        val overridableInterfaces = (annotatedOverridableInterfaces + inheritedOverridableInterfaceNames(winRTBase, winRTTypes))
            .distinct()
            .sorted()
        return KotlinWinRTAuthoredTypeCandidate(
            packageName = packageName,
            className = className,
            sourceTypeName = sourceTypeName,
            sourceSetName = sourceClass.sourceSetName,
            winRTBaseClassName = winRTBase?.qualifiedName,
            winRTInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
            overridableInterfaceNames = overridableInterfaces,
            isPublic = source.isEffectivelyPublicClass(klass),
            activatableFactoryInterfaceName = annotatedActivatableFactoryInterface,
            staticFactoryInterfaceNames = annotatedStaticFactoryInterfaces.distinct().sorted(),
        )
    }

    private fun inheritedWinRTTypes(
        sourceClass: SourceClass,
        sourceClassIndex: Map<String, SourceClass>,
        winRTTypes: Map<String, IndexedWinRTType>,
        visitedSourceTypes: MutableSet<String> = mutableSetOf(),
    ): List<IndexedWinRTType> =
        sourceClass.source.superTypeNames(sourceClass.klass).flatMap { superType ->
            resolveIndexedWinRTType(superType, sourceClass.packageName, sourceClass.imports, winRTTypes)
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
            inheritedWinRTTypes(resolvedSourceClass, sourceClassIndex, winRTTypes, visitedSourceTypes)
        }

    private fun resolveSourceTypeName(
        typeName: String,
        packageName: String,
        imports: KotlinImports,
        sourceClassIndex: Map<String, SourceClass>,
    ): String? = resolveApplicationTypeName(typeName, packageName, imports, sourceClassIndex.keys)

    private fun resolveApplicationTypeName(
        typeName: String,
        packageName: String,
        imports: KotlinImports,
        names: Set<String>,
    ): String? {
        val candidates = buildList {
            add(typeName)
            imports.explicit[typeName]?.let(::add)
            imports.wildcards.forEach { wildcard -> add("$wildcard.$typeName") }
            if (packageName.isNotBlank()) {
                add("$packageName.$typeName")
            }
        }
        return candidates.firstOrNull(names::contains)
    }

    private data class SourceClass(
        val source: KotlinLightSource,
        val klass: LighterASTNode,
        val packageName: String,
        val imports: KotlinImports,
        val className: String,
        val sourceTypeName: String,
        val sourceSetName: String? = null,
    )

    private fun parseSource(source: Path): KotlinLightSource {
        ensureKotlinApplicationEnvironment()
        // KotlinLightParser receives the LF-normalized text used by Kotlin's
        // source loader. Raw CRLF otherwise splits multiline property accessors
        // into error nodes, losing setter visibility and other member syntax.
        val text = source.readText().replace("\r\n", "\n").replace('\r', '\n')
        val tree = KotlinLightParser.buildLightTree(
            text,
            InMemoryKtSourceFile(source.name, source.toAbsolutePath().toString(), text),
        ) { _: Int, _: Int, _: String? -> }
        return KotlinLightSource(text, tree)
    }

    private fun resolveAnnotatedWinRTType(
        typeName: String,
        winRTTypes: Map<String, IndexedWinRTType>,
        sourceTypeName: String,
    ): IndexedWinRTType =
        requireNotNull(resolveIndexedWinRTTypeByProjectedName(typeName, winRTTypes)) {
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
            // Backticks escape Kotlin tokens; they are not part of metadata identity.
            val path = imported.substringBefore(" as ").trim().split('.')
                .joinToString(".") { it.removeSurrounding("`") }
            if (path.endsWith(".*")) {
                wildcards += path.removeSuffix(".*")
            } else if (path.isNotBlank()) {
                val alias = imported.substringAfter(" as ", missingDelimiterValue = "")
                    .trim()
                    .removeSurrounding("`")
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
        val xamlDeclarations: List<Path>,
        val xamlHeader: Boolean,
        val references: List<Path>,
        val xamlHeaderSources: Path?,
        val sourceRootOwners: Map<Path, String>,
        val xamlAssemblyName: String?,
    ) {
        companion object {
            fun parse(args: Array<String>): CliOptions {
                var metadataIndex: Path? = null
                var output: Path? = null
                val xamlDeclarations = mutableListOf<Path>()
                val sourceRootOwners = linkedMapOf<Path, String>()
                var xamlAssemblyName: String? = null
                var xamlHeader = false
                val references = mutableListOf<Path>()
                var xamlHeaderSources: Path? = null
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
                        "--xaml-declarations" -> {
                            xamlDeclarations.add(Path.of(argumentValue(args, index)))
                            index += 2
                        }
                        "--source-root-owner" -> {
                            val path = Path.of(argumentValue(args, index)).toAbsolutePath().normalize()
                            val owner = requireNotNull(args.getOrNull(index + 2)) { "--source-root-owner requires a source set" }
                            require(owner.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid Kotlin source set: $owner" }
                            require(sourceRootOwners.put(path, owner).let { it == null || it == owner }) { "Ambiguous source set owner for $path" }
                            index += 3
                        }
                        "--xaml-header" -> { xamlHeader = true; index += 1 }
                        "--xaml-assembly-name" -> { xamlAssemblyName = argumentValue(args, index); index += 2 }
                        "--reference" -> { references.add(Path.of(argumentValue(args, index))); index += 2 }
                        "--xaml-header-sources" -> { xamlHeaderSources = Path.of(argumentValue(args, index)); index += 2 }
                        else -> error("Unknown kotlin-winrt authoring scanner argument: ${args[index]}")
                    }
                }
                return CliOptions(
                    metadataIndex = requireNotNull(metadataIndex) { "--metadata-index is required" },
                    output = requireNotNull(output) { "--output is required" },
                    sourceRoots = sourceRoots,
                    xamlDeclarations = xamlDeclarations,
                    xamlHeader = xamlHeader,
                    references = references,
                    xamlHeaderSources = xamlHeaderSources,
                    sourceRootOwners = sourceRootOwners,
                    xamlAssemblyName = xamlAssemblyName,
                )
            }

            private fun argumentValue(args: Array<String>, index: Int): String =
                args.getOrNull(index + 1)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt authoring scanner argument ${args[index]} requires a path value.",
                    )
        }
    }

    private fun sourceSetForPath(path: Path, owners: Map<Path, String>): String? = owners.entries
        .filter { path.toAbsolutePath().normalize().startsWith(it.key) }.maxByOrNull { it.key.nameCount }?.value

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
            tree.root.descendantsOfType(KtNodeTypes.CLASS) + tree.root.descendantsOfType(KtNodeTypes.OBJECT_DECLARATION)

        fun xamlTypeResolver(
            applicationNames: Set<String>,
            indexedTypes: Map<String, IndexedWinRTType>,
            applicationEnumNames: Set<String> = emptySet(),
        ): (String) -> WinRTTypeRef {
            val packageName = packageName()
            val imports = parseImports(this)
            fun resolve(raw: String): WinRTTypeRef {
                val nullable = raw.trim().endsWith('?')
                val parsed = WinRTTypeRef.fromDisplayName(raw.trim().removeSuffix("?"))
                val arguments = xamlKotlinTypeArguments(raw).map(::resolve)
                val name = parsed.qualifiedName ?: parsed.typeName
                if (isWinRTVoidTypeName(name.removePrefix("kotlin."))) return WinRTTypeRef.named("System.Void")
                if (parsed.kind == WinRTTypeRefKind.Array || name == "Array" || name == "kotlin.Array") {
                    require(arguments.size == 1) { "XAML property Array requires one element type: $raw" }
                    return WinRTTypeRef.array(arguments.single())
                }
                winRTArrayElementForKotlinType(name)?.let { return WinRTTypeRef.array(WinRTTypeRef.named(it.toKotlinProjectionTypeName())) }
                val primitive = winRTFundamentalTypeForName(name.removePrefix("kotlin."))
                val local = buildList {
                    add(name)
                    imports.explicit[name]?.let(::add)
                    imports.wildcards.forEach { add("$it.$name") }
                    if (packageName.isNotBlank()) add("$packageName.$name")
                }.firstOrNull(applicationNames::contains)
                val indexed = resolveIndexedWinRTType(name, packageName, imports, indexedTypes)
                val collection = winRTCollectionAbiNameForKotlinType(name)
                val metadataName = when {
                    primitive != null -> primitive.toKotlinProjectionTypeName()
                    name == "Any" || name == "kotlin.Any" || name == "System.Object" -> "System.Object"
                    local != null -> local
                    collection != null -> "$collection`${arguments.size}"
                    indexed != null -> indexed.qualifiedName.substringBefore('`') +
                        if (arguments.isEmpty()) "" else "`${arguments.size}"
                    else -> error("XAML property type $raw has no WinRT metadata projection")
                }
                val result = WinRTTypeRef.named(metadataName, arguments)
                val isValue = primitive?.isWinRTValueType == true ||
                    indexed?.kind in setOf(WinRTTypeKind.Enum.name, WinRTTypeKind.Struct.name) || local in applicationEnumNames
                return if (nullable && isValue)
                    WinRTTypeRef.named("Windows.Foundation.IReference`1", listOf(result)) else result
            }
            return ::resolve
        }

        fun xamlApplicationMembers(
            classNode: LighterASTNode,
            applicationNames: Set<String>,
            indexedTypes: Map<String, IndexedWinRTType>,
            applicationEnumNames: Set<String> = emptySet(),
        ): XamlSourceMembers {
            val resolve = xamlTypeResolver(applicationNames, indexedTypes, applicationEnumNames)
            val body = classNode.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }
            val companions = body?.children().orEmpty().filter { it.tokenType == KtNodeTypes.OBJECT_DECLARATION &&
                hasModifier(it, KtTokens.COMPANION_KEYWORD) }
            val staticProperties = companions.flatMap { companion ->
                companion.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }?.children().orEmpty()
                    .filter { it.tokenType == KtNodeTypes.PROPERTY }
            }
            val privateStaticProperties = companions.filter { hasModifier(it, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD) }
                .flatMap { companion -> companion.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }?.children().orEmpty()
                    .filter { it.tokenType == KtNodeTypes.PROPERTY } }.toSet()
            val dependencyPropertyNames = (staticProperties + if (isObjectDeclaration(classNode))
                body?.children().orEmpty().filter { it.tokenType == KtNodeTypes.PROPERTY } else emptyList()).mapNotNull { property ->
                if (property.children().firstOrNull { it.tokenType == KtNodeTypes.TYPE_REFERENCE }?.let(::nodeText)?.substringAfterLast('.') != "DependencyProperty") null
                else property.descendants().dropWhile { it.tokenType != KtTokens.VAL_KEYWORD && it.tokenType != KtTokens.VAR_KEYWORD }
                    .drop(1).firstOrNull { it.tokenType == KtTokens.IDENTIFIER }?.let(::nodeText)
            }.toSet()
            val constructorProperties = classNode.children().filter { it.tokenType == KtNodeTypes.PRIMARY_CONSTRUCTOR }
                .flatMap { it.descendantsOfType(KtNodeTypes.VALUE_PARAMETER) }
                .filter { it.children().any { child -> child.tokenType == KtTokens.VAL_KEYWORD || child.tokenType == KtTokens.VAR_KEYWORD } }
            val properties = (body?.children().orEmpty().filter { it.tokenType == KtNodeTypes.PROPERTY }
                .map { it to isObjectDeclaration(classNode) } + constructorProperties.map { it to false } + staticProperties.map { it to true })
                .mapNotNull { (property, isStatic) ->
                    val private = hasModifier(property, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD) || property in privateStaticProperties
                    val typeNode = property.children().firstOrNull {
                        it.tokenType == KtNodeTypes.TYPE_REFERENCE
                    } ?: if (private || isStatic) return@mapNotNull null else error(
                        "Public Kotlin XAML property in ${className(classNode)} requires an explicit type " +
                            "so XamlCompiler pass 1 can resolve it before Kotlin semantic compilation")
                    val name = property.descendants().dropWhile {
                        it.tokenType != KtTokens.VAL_KEYWORD && it.tokenType != KtTokens.VAR_KEYWORD
                    }.drop(1).firstOrNull { it.tokenType == KtTokens.IDENTIFIER }?.let(::nodeText)
                        ?: error("XAML property declaration is missing a name")
                    val setter = property.children().firstOrNull { accessor ->
                        accessor.tokenType == KtNodeTypes.PROPERTY_ACCESSOR &&
                            accessor.children().any { it.tokenType == KtTokens.SET_KEYWORD }
                    }
                    val mutable = property.descendants().any { it.tokenType == KtTokens.VAR_KEYWORD }
                    val rawType = nodeText(typeNode).trim()
                    val resolved = if (private) runCatching { resolve(rawType) }.getOrNull() ?: return@mapNotNull null else resolve(rawType)
                    XamlSourceProperty(WinRTXamlApplicationProperty(name, resolved,
                        isReadOnly = !mutable || setter?.let {
                            hasModifier(it, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD)
                        } == true, isPublic = !private, isStatic = isStatic,
                        isDependencyProperty = !isStatic && "${name}Property" in dependencyPropertyNames), rawType)
                }
            val contentProperty = xamlStringAnnotation(classNode, "WinRTXamlContentProperty", "name")
            val parser = xamlStringAnnotation(classNode, "WinRTXamlCreateFromString", "methodName")?.let { value ->
                if ('.' !in value) value else {
                    val owner = resolveApplicationTypeName(value.substringBeforeLast('.'), packageName(), parseImports(this), applicationNames)
                    (owner ?: value.substringBeforeLast('.')) + "." + value.substringAfterLast('.')
                }
            }
            // Match the same add/remove handler convention as projected Kotlin events.
            // Only the schema gets CLR Event rows; the Kotlin connector calls these typed methods.
            val functions = body?.children().orEmpty().filter { it.tokenType == KtNodeTypes.FUN &&
                !hasModifier(it, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD, KtTokens.OVERRIDE_KEYWORD) }
                .groupBy { function -> function.children().firstOrNull { it.tokenType == KtTokens.IDENTIFIER }?.let(::nodeText) }
            fun handlerType(function: LighterASTNode): String? {
                val parameters = function.children().firstOrNull { it.tokenType == KtNodeTypes.VALUE_PARAMETER_LIST }
                    ?.children().orEmpty().filter { it.tokenType == KtNodeTypes.VALUE_PARAMETER }
                return parameters.singleOrNull()?.children()?.firstOrNull { it.tokenType == KtNodeTypes.TYPE_REFERENCE }?.let(::nodeText)
            }
            val events = functions.keys.filterNotNull().filter { it.startsWith("add") && it.length > 3 && it[3].isUpperCase() }
                .mapNotNull { name ->
                    val add = functions.getValue(name).singleOrNull() ?: return@mapNotNull null
                    val remove = functions["remove${name.removePrefix("add")}"]?.singleOrNull() ?: return@mapNotNull null
                    val raw = handlerType(add) ?: return@mapNotNull null
                    if (raw != handlerType(remove)) return@mapNotNull null
                    val type = runCatching { resolve(raw) }.getOrNull() ?: return@mapNotNull null
                    if (indexedTypes[type.qualifiedName?.substringBefore('`')]?.kind != WinRTTypeKind.Delegate.name) return@mapNotNull null
                    WinRTXamlApplicationEvent(name.removePrefix("add"), type)
                }
            val methods = (listOf(classNode) + companions).flatMap { owner ->
                owner.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }?.children().orEmpty()
                    .filter { it.tokenType == KtNodeTypes.FUN &&
                        !hasModifier(it, KtTokens.OVERRIDE_KEYWORD, KtTokens.SUSPEND_KEYWORD) &&
                        it.children().none { child -> child.tokenType == KtNodeTypes.TYPE_PARAMETER_LIST || child.tokenType == KtTokens.DOT } }
                    .mapNotNull { function ->
                        val name = function.children().firstOrNull { it.tokenType == KtTokens.IDENTIFIER }?.let(::nodeText)
                            ?: return@mapNotNull null
                        if (events.any { name == "add${it.name}" || name == "remove${it.name}" }) return@mapNotNull null
                        val parameters = function.children().firstOrNull { it.tokenType == KtNodeTypes.VALUE_PARAMETER_LIST }
                            ?.children().orEmpty().filter { it.tokenType == KtNodeTypes.VALUE_PARAMETER }
                        if (parameters.any { hasModifier(it, KtTokens.VARARG_KEYWORD) }) return@mapNotNull null
                        val rawParameters = parameters.map { parameter ->
                            parameter.children().firstOrNull { it.tokenType == KtNodeTypes.TYPE_REFERENCE }?.let(::nodeText)
                                ?: return@mapNotNull null
                        }
                        val rawReturn = function.children().firstOrNull { it.tokenType == KtNodeTypes.TYPE_REFERENCE }?.let(::nodeText)
                            ?: if (function.children().any { it.tokenType == KtNodeTypes.BLOCK }) "Unit" else return@mapNotNull null
                        runCatching {
                            XamlSourceMethod(WinRTXamlApplicationMethod(name, resolve(rawReturn), rawParameters.map(resolve),
                                isStatic = isObjectDeclaration(owner),
                                isPublic = !hasModifier(function, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD) &&
                                    !hasModifier(owner, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD)), rawReturn, rawParameters)
                        }.getOrNull()
                    }
            }
            return XamlSourceMembers(WinRTXamlApplicationTypeMembers(
                properties.map(XamlSourceProperty::metadata), contentProperty, events, methods.map(XamlSourceMethod::metadata), parser), properties, methods)
        }

        private fun xamlStringAnnotation(classNode: LighterASTNode, name: String, argumentName: String): String? {
            val modifier = classNode.children().firstOrNull { it.tokenType == KtNodeTypes.MODIFIER_LIST }?.let(::nodeText).orEmpty()
            val qualified = "io.github.composefluent.winrt.runtime.$name"
            val imports = parseImports(this)
            val names = linkedSetOf(qualified)
            imports.explicit.filterValues { it == qualified }.keys.forEach(names::add)
            if (qualified.substringBeforeLast('.') in imports.wildcards || packageName() == qualified.substringBeforeLast('.')) names += name
            val annotation = names.firstNotNullOfOrNull { annotationTextForName(modifier, it) } ?: return null
            return annotationStringArgument(annotation, argumentName).ifEmpty {
                annotationPositionalArguments(annotation).firstOrNull().stringLiteralArgument()
            }
        }

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

        fun enumEntries(classNode: LighterASTNode): List<String>? {
            if (!hasModifier(classNode, KtTokens.ENUM_KEYWORD)) return null
            return classNode.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }?.children().orEmpty()
                .filter { it.tokenType == KtNodeTypes.ENUM_ENTRY }
                .map { entry -> nodeText(requireNotNull(entry.children().firstOrNull { it.tokenType == KtTokens.IDENTIFIER })) }
        }

        fun isObjectDeclaration(classNode: LighterASTNode): Boolean =
            classDeclarationKeyword(classNode) == KtTokens.OBJECT_KEYWORD

        // XamlUserType.RunInitializer ensures static DP registration before native lookup.
        // Typed object access has the same initialization semantics on JVM and Native.
        fun staticInitializerOwner(classNode: LighterASTNode): String? {
            if (isObjectDeclaration(classNode)) return ""
            val companion = classNode.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }?.children().orEmpty()
                .firstOrNull { it.tokenType == KtNodeTypes.OBJECT_DECLARATION && hasModifier(it, KtTokens.COMPANION_KEYWORD) &&
                    !hasModifier(it, KtTokens.PRIVATE_KEYWORD, KtTokens.PROTECTED_KEYWORD) } ?: return null
            return companion.children().firstOrNull { it.tokenType == KtTokens.IDENTIFIER }?.let(::nodeText) ?: "Companion"
        }

        fun isValueClass(classNode: LighterASTNode): Boolean =
            hasModifier(classNode, KtTokens.VALUE_KEYWORD, KtTokens.INLINE_KEYWORD)

        fun hasPublicDefaultActivationConstructor(classNode: LighterASTNode, allowDefaultArguments: Boolean = false): Boolean {
            // CsWinRT Authoring examines this type's InstanceConstructors.
            // Nested classes' constructors do not affect the containing type.
            val constructors = classNode.children().filter { it.tokenType == KtNodeTypes.PRIMARY_CONSTRUCTOR } +
                classNode.children().firstOrNull { it.tokenType == KtNodeTypes.CLASS_BODY }?.children().orEmpty()
                    .filter { it.tokenType == KtNodeTypes.SECONDARY_CONSTRUCTOR }
            if (constructors.isEmpty()) {
                return true
            }
            return constructors.any { constructor ->
                isPublicConstructor(constructor) && constructor.valueParameters().all { parameter ->
                    // XAML application activators call ordinary Kotlin constructors.
                    // Public component ABI authoring still requires zero declared arguments.
                    allowDefaultArguments && parameter.children().any { it.tokenType == KtTokens.EQ }
                }
            }
        }

        fun isUnsealedAuthoredClass(classNode: LighterASTNode): Boolean =
            classDeclarationKeyword(classNode) == KtTokens.CLASS_KEYWORD &&
                hasModifier(classNode, KtTokens.OPEN_KEYWORD, KtTokens.ABSTRACT_KEYWORD, KtTokens.SEALED_KEYWORD)

        fun isAbstractClass(classNode: LighterASTNode): Boolean =
            hasModifier(classNode, KtTokens.ABSTRACT_KEYWORD, KtTokens.SEALED_KEYWORD)

        private fun isPublicConstructor(constructorNode: LighterASTNode): Boolean =
            !hasModifier(constructorNode, KtTokens.PRIVATE_KEYWORD, KtTokens.INTERNAL_KEYWORD, KtTokens.PROTECTED_KEYWORD)

        private fun LighterASTNode.valueParameters(): List<LighterASTNode> =
            children()
                .firstOrNull { child -> child.tokenType == KtNodeTypes.VALUE_PARAMETER_LIST }
                ?.children()
                .orEmpty()
                .filter { child -> child.tokenType == KtNodeTypes.VALUE_PARAMETER }

        fun className(classNode: LighterASTNode): String? {
            var seenDeclarationKeyword = false
            // An unnamed companion has no class-name token. Descending into its
            // body would mistake its first function name for an authored class.
            return classNode.children().firstNotNullOfOrNull { node ->
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

        fun superTypeNames(classNode: LighterASTNode): List<String> = superTypes(classNode).map { it.substringBefore('<').trim() }

        fun superTypes(classNode: LighterASTNode): List<String> =
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
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                }

        fun authoredRuntimeClassAnnotation(
            classNode: LighterASTNode,
            packageName: String,
            imports: KotlinImports,
        ): KotlinWinRTAuthoredRuntimeClassAnnotation {
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
                ?: return KotlinWinRTAuthoredRuntimeClassAnnotation()
            val positionalArguments = annotationPositionalArguments(annotationText)
            return KotlinWinRTAuthoredRuntimeClassAnnotation(
                isPresent = true,
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
                acceptedNames += "WinRTAuthoredRuntimeClass"
            }
            if (packageName == WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION.substringBeforeLast('.')) {
                acceptedNames += "WinRTAuthoredRuntimeClass"
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
