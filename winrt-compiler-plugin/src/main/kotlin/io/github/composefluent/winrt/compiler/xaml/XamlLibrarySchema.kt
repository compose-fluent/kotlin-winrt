package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.compiler.KotlinWinRTCommandLineProcessor
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTTypeByProjectedName
import io.github.composefluent.winrt.compiler.authoring.requiresComponentAuthoring
import io.github.composefluent.winrt.compiler.authoring.WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION
import io.github.composefluent.winrt.metadata.*
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** Like CSharpTypeInfoPass2, accessors are compiled in the assembly that owns the model.
 * The schema is private compiler input; none of these classes becomes a WinRT component.
 * IR supplies inferred Kotlin property types, without runtime reflection on either target.
 */
@OptIn(ExperimentalCompilerApi::class)
internal object XamlLibraryOptions {
    private val keys = listOf("xamlLibraryOutput", "xamlLibraryAssembly", "xamlLibraryReferences")
        .associateWith { CompilerConfigurationKey<String>(it) }
    val options = keys.keys.map { CliOption(it, "<value>", "Kotlin XAML library schema $it", false) }

    fun process(name: String, value: String, configuration: CompilerConfiguration): Boolean {
        val key = keys[name] ?: return false
        configuration.put(key, value)
        return true
    }

    fun export(configuration: CompilerConfiguration): IrGenerationExtension? {
        val output = configuration.get(keys.getValue("xamlLibraryOutput")) ?: return null
        val root = Path.of(output)
        // Frontend errors must not leave a usable schema from an earlier build.
        Files.deleteIfExists(root.resolve("KotlinXaml.winmd"))
        Files.deleteIfExists(root.resolve("kotlin-winrt-support/compiler-support.tsv"))
        return XamlLibrarySchema(root,
            requireNotNull(configuration.get(keys.getValue("xamlLibraryAssembly"))),
            Path.of(requireNotNull(configuration.get(KotlinWinRTCommandLineProcessor.METADATA_INDEX_KEY))),
            configuration.get(keys.getValue("xamlLibraryReferences"))?.let { file ->
                Files.readAllLines(Path.of(file)).filter(String::isNotBlank).map(Path::of)
            }.orEmpty())
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class XamlLibrarySchema(private val root: Path, private val assembly: String,
    private val metadataIndex: Path, private val references: List<Path>) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val types = readAuthoringMetadataIndex(metadataIndex)
        fun publicClasses(declarations: List<IrDeclaration>): List<IrClass> = declarations.filterIsInstance<IrClass>()
            .filter { it.visibility == DescriptorVisibilities.PUBLIC && xamlLibraryDeclarationAccessible(it) }
            .flatMap { listOf(it) + publicClasses(it.declarations) }
        val classes = moduleFragment.files.flatMap { publicClasses(it.declarations) }
            .filter { it.origin == IrDeclarationOrigin.DEFINED && it.typeParameters.isEmpty() && !it.isInner &&
                it.kind in setOf(ClassKind.CLASS, ClassKind.OBJECT, ClassKind.ENUM_CLASS) && !it.isCompanion &&
                it.fqNameWhenAvailable != null && !it.fqNameWhenAvailable!!.asString().startsWith("io.github.composefluent.winrt.generated.") &&
                resolveIndexedWinRTTypeByProjectedName(it.fqNameWhenAvailable!!.asString(), types) == null }
            .sortedBy { it.fqNameWhenAvailable!!.asString() }
        val names = classes.mapTo(mutableSetOf()) { it.fqNameWhenAvailable!!.asString() } + references
            .filter { it.fileName.toString().endsWith(".KotlinXaml.winmd") }
            .flatMap { WinRTMetadataLoader.load(it).namespaces.flatMap { namespace -> namespace.types }.map { type -> type.qualifiedName } }
        val interfaces = classes.associate { klass -> klass to klass.superTypes.mapNotNull { type ->
            val name = type.classFqName?.asString().orEmpty()
            if (resolveIndexedWinRTTypeByProjectedName(name, types)?.kind != WinRTTypeKind.Interface.name &&
                winRTCollectionAbiNameForKotlinType(name) == null) null else
                xamlApplicationTypeReference(type, types, names) to type.sourceType()
        } }
        val members = classes.associate { klass ->
            klass.fqNameWhenAvailable!!.asString() to xamlApplicationProperties(klass, types, names,
                strictPublicProperties = false, includeInternal = false,
                accessible = ::xamlLibraryDeclarationAccessible).let { schema -> schema.copy(
                properties = schema.properties.filter { property -> property.isPublic &&
                    (klass.declarations + klass.companionObject()?.declarations.orEmpty()).filterIsInstance<IrProperty>().any { it.name.asString() == property.name &&
                        it.getter?.visibility == DescriptorVisibilities.PUBLIC } },
                methods = schema.methods.filter { method -> method.isPublic &&
                    (klass.declarations + klass.companionObject()?.declarations.orEmpty()).filterIsInstance<IrSimpleFunction>()
                        .any { it.name.asString() == method.name && it.visibility == DescriptorVisibilities.PUBLIC } },
            ) }
        }
        val descriptors = classes.map { klass ->
            val base = klass.superTypes.firstNotNullOfOrNull { type ->
                val name = type.classFqName?.asString() ?: return@firstNotNullOfOrNull null
                if (name in names) name else resolveIndexedWinRTTypeByProjectedName(name, types)
                    ?.takeIf { it.kind == WinRTTypeKind.RuntimeClass.name }?.qualifiedName
            }
            WinRTXamlApplicationTypeDescriptor(klass.fqNameWhenAvailable!!.asString(), base,
                interfaces.getValue(klass).map { it.first.typeName },
                isActivatable = defaultConstructor(klass) != null, isSealed = klass.modality == Modality.FINAL,
                enumEntries = if (klass.kind == ClassKind.ENUM_CLASS) klass.declarations.filterIsInstance<IrEnumEntry>()
                    .filter(::xamlLibraryDeclarationAccessible).map { it.name.asString() } else null)
        }
        Files.createDirectories(root)
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("$assembly.KotlinXaml", descriptors, members,
            root.resolve("KotlinXaml.winmd"), loadXamlReferenceTypeAssemblyNames(references), types.values
                .filter { it.kind in setOf(WinRTTypeKind.Enum.name, WinRTTypeKind.Struct.name) }.mapTo(mutableSetOf()) { it.qualifiedName })
        val registrar = "KotlinXamlLibraryDefinitions_" + assembly.replace(Regex("[^A-Za-z0-9_]"), "_")
        val source = root.resolve("src/io/github/composefluent/winrt/generated/xaml/$registrar.kt")
        Files.createDirectories(source.parent)
        source.writeText(buildString {
            appendLine("@file:Suppress(\"UNCHECKED_CAST\", \"DEPRECATION\")")
            appendLine("@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)")
            appendLine("package io.github.composefluent.winrt.generated.xaml")
            appendLine("object $registrar {")
            appendLine("  private val registration: Unit = run {")
            for ((klass, descriptor) in classes.zip(descriptors)) {
                val name = descriptor.runtimeClassName
                if (klass.kind == ClassKind.ENUM_CLASS) {
                    val entries = requireNotNull(descriptor.enumEntries).joinToString(", ") { literal(it) }
                    appendLine("    io.github.composefluent.winrt.runtime.registerWinRTXamlEnumType($name::class, ${literal(name)}, $name.entries.filter { it.name in setOf($entries) }.toTypedArray())")
                    continue
                }
                appendLine("    io.github.composefluent.winrt.runtime.registerWinRTXamlTypeDefinition(io.github.composefluent.winrt.runtime.WinRTXamlTypeDefinition(")
                appendLine("      type = $name::class, name = ${literal(name)},")
                val baseType = klass.superTypes.firstOrNull { type -> type.classFqName?.asString() == descriptor.baseRuntimeClassName ||
                    resolveIndexedWinRTTypeByProjectedName(type.classFqName?.asString().orEmpty(), types)?.qualifiedName == descriptor.baseRuntimeClassName }
                appendLine("      baseName = ${literal(descriptor.baseRuntimeClassName ?: "System.Object")}, baseType = ${baseType?.sourceType()?.substringBefore('<') ?: "kotlin.Any"}::class,")
                fun componentTypes(klass: IrClass, seen: MutableSet<IrClass> = mutableSetOf()): List<io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType> =
                    if (!seen.add(klass)) emptyList() else klass.superTypes.flatMap { superType ->
                        listOfNotNull(resolveIndexedWinRTTypeByProjectedName(superType.classFqName?.asString().orEmpty(), types)) +
                            (superType.classOrNull?.owner?.let { componentTypes(it, seen) } ?: emptyList())
                    }
                appendLine("      isWinRTComponent = ${requiresComponentAuthoring(componentTypes(klass), klass.hasAnnotation(org.jetbrains.kotlin.name.FqName(WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION)))},")
                if (descriptor.isActivatable) appendLine("      activate = { $name() },")
                val initializer = if (klass.kind == ClassKind.OBJECT) name else klass.companionObject()
                    ?.takeIf { it.visibility == DescriptorVisibilities.PUBLIC && xamlLibraryDeclarationAccessible(it) }?.let { "$name.${it.name.asString()}" }
                initializer?.let { appendLine("      initializer = { $it; Unit },") }
                members.getValue(name).contentProperty?.let { content ->
                    appendLine("      contentProperty = ${literal(content)},")
                }
                xamlCreateFromStringMethodSource(name, members)?.let { factory -> appendLine("      createFromString = { $factory(it) },") }
                val convert: (String, String) -> String = { value, type -> "$value as $type" }
                val shape = xamlCollectionRegistrationSources(interfaces.getValue(klass), convert)
                if (shape.isNotEmpty()) {
                    appendLine("      shape = ${shape.last()},")
                    appendLine("      valueTypes = listOf(${shape.joinToString(",\n")}),")
                }
                appendLine("      members = listOf(")
                for (member in members.getValue(name).properties.filterNot { it.isStatic }) {
                    val property = klass.declarations.filterIsInstance<IrProperty>().single { it.name.asString() == member.name }
                    val propertyType = property.getter!!.returnType
                    val kotlinType = propertyType.sourceType()
                    appendLine(xamlPropertyRegistrationSource(name, member, kotlinType, convert).prependIndent("        ") + ",")
                }
                val functions = (klass.declarations + klass.companionObject()?.takeIf(::xamlLibraryDeclarationAccessible)?.declarations.orEmpty())
                    .filterIsInstance<IrSimpleFunction>().filter(::xamlLibraryDeclarationAccessible)
                val accessors = members.getValue(name).methods.filter { it.isStatic && it.isPublic }.map { method ->
                    val function = functions.single { function -> function.name.asString() == method.name && function.visibility == DescriptorVisibilities.PUBLIC &&
                        (function.parentClassOrNull?.kind == ClassKind.OBJECT) == method.isStatic &&
                        runCatching { xamlApplicationTypeReference(function.returnType, types, names) == method.returnType &&
                            function.parameters.filter { it.kind == IrParameterKind.Regular }.map { xamlApplicationTypeReference(it.type, types, names) } == method.parameterTypes
                        }.getOrDefault(false) }
                    XamlStaticAccessor(method, function.returnType.sourceType(), function.parameters.filter { it.kind == IrParameterKind.Regular }.map { it.type.sourceType() })
                }
                xamlAttachedRegistrationSources(name, members.getValue(name), accessors, convert).forEach {
                    appendLine(it.prependIndent("        ") + ",")
                }
                appendLine("      )," )
                appendLine("    ))")
            }
            appendLine("  }")
            appendLine("  fun registerAll() { registration }")
            appendLine("}")
        })
        val support = root.resolve("kotlin-winrt-support")
        Files.createDirectories(support)
        support.resolve("xaml-type-registrars.tsv").writeText("className\nio.github.composefluent.winrt.generated.xaml.$registrar\n")
        support.resolve("compiler-support.tsv").writeText("kind\tclassName\tsourceFile\tentries\towner\n" +
            "xaml-type-registrar\tio.github.composefluent.winrt.generated.xaml.$registrar\txaml-type-registrars.tsv\t1\t\n")
    }

    private fun defaultConstructor(klass: IrClass): IrConstructor? = if (klass.kind != ClassKind.CLASS || klass.modality == Modality.ABSTRACT) null
        else klass.constructors.firstOrNull { it.visibility == DescriptorVisibilities.PUBLIC && xamlLibraryDeclarationAccessible(it) &&
            it.parameters.filter { it.kind == IrParameterKind.Regular }.all { it.defaultValue != null } }

    private fun IrType.sourceType(): String {
        val name = requireNotNull(classFqName?.asString())
        val args = (this as? IrSimpleType)?.arguments.orEmpty().map { requireNotNull(it.typeOrNull).sourceType() }
        return name + (if (args.isEmpty()) "" else args.joinToString(", ", "<", ">")) + if (isNullable()) "?" else ""
    }
    private fun literal(value: String): String = kotlinx.serialization.json.JsonPrimitive(value).toString()
}

/** Kotlin's HIDDEN and ERROR declarations cannot be named by a compiled accessor.
 * Keep this adaptation at the IR schema boundary; CsWinRT's public member filtering
 * remains the owner of the projected shape in xamlApplicationProperties.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun xamlLibraryDeclarationAccessible(declaration: IrDeclaration): Boolean {
    val annotation = (declaration as? IrAnnotationContainer)?.getAnnotation(FqName("kotlin.Deprecated")) ?: return true
    val levelIndex = annotation.symbol.owner.parameters.indexOfFirst { it.name.asString() == "level" }
    val level = (annotation.arguments.getOrNull(levelIndex) as? IrGetEnumValue)?.symbol?.owner?.name?.asString()
    return level != "ERROR" && level != "HIDDEN"
}
