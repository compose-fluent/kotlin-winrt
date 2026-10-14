package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.compiler.KotlinWinRTCommandLineProcessor
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTTypeByProjectedName
import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.XamlFramework
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** Private handlers remain private in application schema, never in the public component ABI. */
@OptIn(ExperimentalCompilerApi::class)
internal object XamlSemanticOptions {
    private val keys = listOf("xamlDeclarations", "xamlSemanticOutput", "xamlReferences", "xamlReferencesFile", "xamlApplicationHeader", "xamlImplementation", "xamlCompile", "xamlFramework")
        .associateWith { CompilerConfigurationKey<String>(it) }
    val options = keys.keys.map { CliOption(it, "<path>", "Kotlin XAML semantic compilation $it", false) }

    fun process(name: String, value: String, configuration: CompilerConfiguration): Boolean {
        val key = keys[name] ?: return false
        configuration.put(key, value)
        return true
    }

    fun framework(configuration: CompilerConfiguration): XamlFramework =
        configuration.get(keys.getValue("xamlFramework"))?.let(::parseXamlFramework) ?: XamlFramework.WinUI

    fun compilation(configuration: CompilerConfiguration): XamlCompilation? {
        fun value(name: String) = configuration.get(keys.getValue(name))
        if (keys.keys.filterNot { it == "xamlFramework" }.all { value(it) == null }) return null
        val declarationPath = Path.of(requireNotNull(value("xamlDeclarations")))
        val declarations = WinUIXamlDeclarations.parse(Files.readString(declarationPath))
        value("xamlFramework")?.let { require(parseXamlFramework(it) == declarations.framework) {
            "XAML framework option does not match the declaration index"
        } }
        val finalPath = value("xamlImplementation")
        if (finalPath != null) {
            require(value("xamlSemanticOutput") == null) { "XAML final and semantic modes are mutually exclusive" }
            val actual = WinUIXamlDeclarations.readCompilerOutput(Path.of(finalPath))
            val plan = Json.parseToJsonElement(Files.readString(Path.of(finalPath))).jsonObject
                .getValue("KotlinImplementation").jsonObject
            require(plan.getValue("SchemaVersion").jsonPrimitive.int in 1..WinUIXamlDeclarations.SCHEMA_VERSION &&
                plan.getValue("DeclarationFingerprint").jsonPrimitive.content == WinUIXamlDeclarations.fingerprint(declarations) &&
                WinUIXamlDeclarations.canonicalText(actual) == WinUIXamlDeclarations.canonicalText(declarations)) {
                "XAML final plan does not match declaration input"
            }
            return XamlCompilation(declarations, null)
        }
        val output = Path.of(requireNotNull(value("xamlSemanticOutput")))
        // Invalidate before frontend analysis too: syntax/type errors never reach IR export.
        Files.deleteIfExists(output)
        Files.deleteIfExists(output.resolveSibling("KotlinXaml.winmd"))
        require(value("xamlReferences") == null || value("xamlReferencesFile") == null) {
            "Specify either xamlReferences or xamlReferencesFile"
        }
        val references = value("xamlReferencesFile")?.let { Files.readAllLines(Path.of(it)).filter(String::isNotBlank) }
            ?: requireNotNull(value("xamlReferences")).split(File.pathSeparator)
        val metadataIndex = Path.of(requireNotNull(configuration.get(KotlinWinRTCommandLineProcessor.METADATA_INDEX_KEY)))
        val semanticOnly = value("xamlCompile")?.toBooleanStrict()?.not() ?: true
        return XamlCompilation(declarations, XamlSemanticExport(declarationPath, output, references.map(Path::of), metadataIndex,
            value("xamlApplicationHeader")?.let(Path::of), semanticOnly), semanticOnly)
    }
}

internal data class XamlCompilation(val declarations: WinUIXamlDeclarationIndex, val semanticExport: IrGenerationExtension?,
    val semanticOnly: Boolean = false)

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class XamlSemanticExport(
    private val declarationPath: Path,
    private val output: Path,
    private val references: List<Path>,
    private val metadataIndex: Path,
    private val applicationHeader: Path?,
    private val semanticOnly: Boolean,
) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        Files.deleteIfExists(output)
        Files.deleteIfExists(output.resolveSibling("KotlinXaml.winmd"))
        val declarations = WinUIXamlDeclarations.parse(Files.readString(declarationPath)).let { index ->
            // Runtime/IDE source hashes belong to executable page bodies, not XAMLC's
            // semantic contract. Both isolated and combined export use the same schema.
            index.copy(pages = index.pages.map { it.copy(sourceHash = "") })
        }
        val types = readAuthoringMetadataIndex(metadataIndex)
        val classes = moduleFragment.files.flatMap { file ->
            file.declarations.filterIsInstance<IrClass>().map { it to file }
        }
        // The CLR symbol sidecar cannot express Kotlin nullability. Validate against
        // the projected delegate here, before XamlCompiler accepts that sidecar.
        var invalidHandler = false
        for (page in declarations.pages) {
            val (klass, file) = classes.singleOrNull { it.first.fqNameWhenAvailable?.asString() == page.className }
                ?: continue
            for (event in page.connections.flatMap { it.events }) {
                val handler = xamlIrFunctions(klass, event.handlerName).singleOrNull()
                val declaringId = xamlTypeClassId(event.declaringTypeName)
                val declaringClass = classes.firstOrNull {
                    it.first.fqNameWhenAvailable?.asString() == event.declaringTypeName ||
                        it.first.fqNameWhenAvailable == declaringId.asSingleFqName()
                }?.first
                    ?: pluginContext.referenceClass(declaringId)?.owner
                val delegateType = declaringClass?.let { xamlIrFunctions(it, "add${event.name}") }?.singleOrNull()
                    ?.parameters?.singleOrNull { it.kind == IrParameterKind.Regular }?.type
                if (handler == null || delegateType == null || !xamlEventHandlerMatches(handler, delegateType)) {
                    @Suppress("DEPRECATION")
                    pluginContext.messageCollector.report(CompilerMessageSeverity.ERROR,
                        "XAML handler ${page.className}.${event.handlerName} does not match projected delegate " +
                            "${event.delegateTypeName}; parameter types must accept all delegate arguments, including null " +
                            "for nullable arguments, and the handler must be an ordinary non-generic instance method.",
                        CompilerMessageLocation.create(
                            Path.of(file.fileEntry.name).resolveSibling(Path.of(file.fileEntry.name).fileName.toString()
                                .removeSuffix(".kt") + ".xaml").toString(),
                            event.location.line, event.location.column, null))
                    invalidHandler = true
                }
            }
        }
        if (invalidHandler) return
        val authored = mutableListOf<WinUIXamlApplicationTypeDescriptor>()
        val applicationMembers = mutableMapOf<String, WinUIXamlApplicationTypeMembers>()
        val headerTypes = applicationHeader?.let { WinRTMetadataLoader.load(it).namespaces.flatMap { it.types } }.orEmpty()
        val dependencyTypes = references.filter { it.fileName.toString().endsWith(".KotlinXaml.winmd") }
            .flatMap { WinRTMetadataLoader.load(it).namespaces.flatMap { namespace -> namespace.types } }
        val applicationTypes = (declarations.pages.map { it.className } + headerTypes.map { it.qualifiedName } +
            dependencyTypes.map { it.qualifiedName }).toSet()
        val pages = declarations.pages.sortedBy { it.className }.map { page ->
            val (klass, file) = requireNotNull(classes.singleOrNull { it.first.fqNameWhenAvailable?.asString() == page.className }) {
                "XAML ${page.resourcePath}: missing top-level Kotlin class ${page.className}"
            }
            val source = Path.of(file.fileEntry.name)
            require(source.fileName.toString() == page.className.substringAfterLast('.') + ".kt" &&
                Files.isRegularFile(source.resolveSibling(source.fileName.toString().removeSuffix(".kt") + ".xaml"))) {
                "XAML ${page.className}: requires same-directory, same-basename .kt and .xaml files"
            }
            require(klass.superTypes.any { type -> type.classFqName?.asString()?.let {
                if (it in applicationTypes) it else resolveIndexedWinRTTypeByProjectedName(it, types, declarations.framework)?.qualifiedName
            } == page.baseTypeName }) { "XAML ${page.className}: expected direct base ${page.baseTypeName}" }
            val isActivatable = klass.declarations.filterIsInstance<IrConstructor>().any { constructor ->
                constructor.visibility == org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PUBLIC &&
                    constructor.parameters.none { it.kind == IrParameterKind.Regular }
            }
            // LoadComponent fills this already constructed root. A no-argument
            // constructor is needed only if IXamlType activates it from markup.
            authored += WinUIXamlApplicationTypeDescriptor(page.className, page.baseTypeName,
                declarations.framework.xamlComponentInterfaceNames, isActivatable = isActivatable)
            applicationMembers[page.className] = xamlApplicationProperties(klass, types, applicationTypes,
                xamlProperties = page.properties, framework = declarations.framework)
            val handlers = page.connections.flatMap { it.events }.map { it.handlerName }.distinct().sorted().map { name ->
                val handler = requireNotNull(xamlIrFunctions(klass, name).singleOrNull()) {
                    "XAML ${page.className}: missing or overloaded handler $name"
                }
                require(!handler.isSuspend && handler.typeParameters.isEmpty() &&
                    handler.parameters.none { it.kind == IrParameterKind.ExtensionReceiver || it.kind == IrParameterKind.Context } &&
                    handler.dispatchReceiverParameter != null) { "XAML ${page.className}.$name: expected ordinary instance method" }
                require(handler.returnType.classFqName?.asString() == "kotlin.Unit") {
                    "XAML ${page.className}.$name: handler must return Unit"
                }
                val parameters = handler.parameters.filter { it.kind == IrParameterKind.Regular }.map { parameter ->
                    require(parameter.varargElementType == null) { "XAML handler parameters cannot be vararg" }
                    val kotlinName = requireNotNull(parameter.type.classFqName?.asString()) { "Unresolved XAML handler parameter" }
                    if (kotlinName == "kotlin.Any") "System.Object" else
                        requireNotNull(resolveIndexedWinRTTypeByProjectedName(kotlinName, types, declarations.framework)?.qualifiedName) {
                            "XAML ${page.className}.$name: unsupported parameter type $kotlinName"
                        }
                }
                buildJsonObject {
                    put("Name", name); put("ReturnTypeName", "System.Void")
                    put("ParameterTypeNames", JsonArray(parameters.map(::JsonPrimitive)))
                }
            }
            buildJsonObject { put("ClassName", page.className); put("Handlers", JsonArray(handlers)) }
        }
        headerTypes.filterNot { type -> declarations.pages.any { it.className == type.qualifiedName } }
            .sortedBy { it.qualifiedName }.forEach { type ->
                val klass = requireNotNull(classes.singleOrNull { it.first.fqNameWhenAvailable?.asString() == type.qualifiedName }) {
                    "XAML application type ${type.qualifiedName} is missing from Kotlin semantic compilation"
                }.first
                authored += WinUIXamlApplicationTypeDescriptor(type.qualifiedName, type.baseTypeName,
                    type.implementedInterfaces.map { it.interfaceName }, type.activation.isActivatable,
                    isSealed = klass.modality == org.jetbrains.kotlin.descriptors.Modality.FINAL,
                    enumEntries = if (klass.kind == org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS)
                        klass.declarations.filterIsInstance<IrEnumEntry>().map { it.name.asString() } else null)
                applicationMembers[type.qualifiedName] = xamlApplicationProperties(klass, types, applicationTypes, framework = declarations.framework)
            }
        val symbols = buildJsonObject {
            put("SchemaVersion", declarations.schemaVersion)
            put("DeclarationFingerprint", WinUIXamlDeclarations.fingerprint(declarations))
            put("Declarations", Json.parseToJsonElement(WinUIXamlDeclarations.canonicalText(declarations)))
            put("Pages", JsonArray(pages))
        }
        Files.createDirectories(output.toAbsolutePath().parent)
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd(
            applicationHeader?.let { WinRTMetadataLoader.loadTypeAssemblyNames(listOf(it)).values.distinct().singleOrNull() }
                ?: "KotlinXaml", authored, applicationMembers, output.resolveSibling("KotlinXaml.winmd"),
            loadXamlReferenceTypeAssemblyNames(references),
            types.values.filter { it.kind == WinRTTypeKind.Enum.name || it.kind == WinRTTypeKind.Struct.name }
                .mapTo(mutableSetOf()) { it.qualifiedName },
            framework = declarations.framework,
        )
        Files.writeString(output, symbols.toString())
        // This isolated compilation owns only schema/symbol outputs. Keep full frontend
        // analysis (including inferred and inherited signatures), but consume its IR after
        // export so JVM/Native lowering and code generation do not compile business bodies
        // that the final application compilation must compile again. Never do this in the
        // final XAML implementation pass or a library's ordinary compilation.
        if (semanticOnly) moduleFragment.consumeXamlSemanticDeclarations()
    }
}
