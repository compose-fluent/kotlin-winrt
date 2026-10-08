package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.*
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** The SDK owns literal conversion for both project and SDK designer accessors. */
internal fun writeXamlMemberConversionSource(root: Path) {
    val file = root.resolve("io/github/composefluent/winrt/generated/xaml/KotlinXamlMemberValue.kt")
    Files.createDirectories(file.parent)
    file.writeText("""
        package io.github.composefluent.winrt.generated.xaml
        internal inline fun <reified T> kotlinWinRTXamlMemberValue(value: Any?): T {
          if (value is T || value !is String) return value as T
          return io.github.composefluent.winrt.runtime.convertWinRTXamlLiteral(T::class, value) { type, text ->
            microsoft.ui.xaml.markup.XamlBindingHelper.convertValue(type, text)
          } as T
        }
    """.trimIndent() + "\n")
}

/** CSharpTypeInfoPass2 creates typed activators for referenced WinMD classes too.
 * Non-bindable library classes may be absent from the library's native type table.
 * These entries supplement that provider; they do not export Kotlin components.
 */
internal fun writeXamlProjectedTypeRegistrationSource(
    root: Path,
    references: List<Path>,
    names: Set<String>,
    assemblyName: String?,
): String? {
    if (names.isEmpty()) return null
    val model = WinRTMetadataLoader.loadSources(references.map(WinRTMetadataSource::path))
    return writeXamlProjectedTypeRegistrationSource(root, model, names, assemblyName)
}

internal fun writeXamlProjectedTypeRegistrationSource(root: Path, model: WinRTMetadataModel,
    names: Set<String>, assemblyName: String?): String? {
    if (names.isEmpty()) return null
    val semantics = model.semanticHelpers()
    val specialTypes = model.specialTypeResolver()
    val definitions = model.namespaces.flatMap { it.types }.associateBy { it.qualifiedName }
    val sdkNamespaces = WinRTXamlNamespaces.namespaces(WinRTXamlNamespaces.PRESENTATION).toSet()
    fun sdk(type: WinRTTypeDefinition) = type.qualifiedName.substringBeforeLast('.') in sdkNamespaces
    val requested = names.toMutableSet()
    names.mapNotNull(definitions::get).filter(::sdk).forEach { original ->
        var type: WinRTTypeDefinition? = original
        while (type != null && requested.add(type.baseTypeName.orEmpty())) type = definitions[type.baseTypeName]
    }
    val types = model.namespaces.flatMap { it.types }.filter { type ->
        type.qualifiedName in requested && type.kind == WinRTTypeKind.RuntimeClass &&
            // The projection emits the parameterless ActivationFactory constructor
            // from WinMD activation metadata, rather than a physical .ctor method.
            !type.isStaticType && type.genericParameterCount == 0 && (sdk(type) ||
                type.activation.isActivatable && type.customAttributes.none { it.typeName.substringAfterLast('.') == "BindableAttribute" })
    }.sortedBy { it.qualifiedName }
    if (types.isEmpty()) return null
    val suffix = assemblyName.orEmpty().replace(Regex("[^A-Za-z0-9_]"), "_")
    val register = "registerKotlinWinRTXamlProjectedTypes_$suffix"
    val packageName = "io.github.composefluent.winrt.generated.xaml"
    val file = root.resolve("${packageName.replace('.', '/')}/KotlinXamlProjectedTypes_$suffix.kt")
    Files.createDirectories(file.parent)
    fun literal(value: String) = JsonPrimitive(value).toString()
    file.writeText(buildString {
        appendLine("@file:Suppress(\"UNCHECKED_CAST\", \"DEPRECATION\")")
        appendLine("@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)")
        appendLine("package $packageName")
        fun emit(type: WinRTTypeDefinition, development: Boolean) {
            val name = type.qualifiedName
            val owner = xamlTypeClassId(name).asSingleFqName().asString()
            val baseName = type.baseTypeName ?: "System.Object"
            val base = xamlTypeClassId(baseName).asSingleFqName().asString()
            val registration = if (development) "registerWinRTXamlHotReloadAccessors" else "registerWinRTXamlProjectedTypeDefinition"
            appendLine("  io.github.composefluent.winrt.runtime.$registration(io.github.composefluent.winrt.runtime.WinRTXamlTypeDefinition(")
            appendLine("    type = $owner::class, name = ${literal(name)},")
            appendLine("    baseName = ${literal(baseName)}, baseType = $base::class,")
            if (!development) appendLine("    activate = { $owner() },")
            else appendLine("    isWinRTComponent = false,")
            appendLine("    isBindable = false,")
            appendLine("    members = listOf(")
            val convert: (String, String) -> String = { value, target -> "kotlinWinRTXamlMemberValue<$target>($value)" }
            val inherited = definitions[type.baseTypeName]?.let { semantics.classMemberMergeDescriptor(it).mergedProperties }
                .orEmpty().associateBy { it.propertyName }
            for (member in semantics.classMemberMergeDescriptor(type).mergedProperties.filter { it.isPublic && !it.isPrivate && it.getterTarget != null }) {
                // The runtime walks base definitions. SDK inspection should not
                // duplicate the same inherited accessor on every derived class.
                if (development && inherited[member.propertyName]?.let {
                    it.getterTarget == member.getterTarget && it.setterTarget == member.setterTarget
                } == true) continue
                val ref = WinRTTypeRef.fromDisplayName(member.propertyTypeName)
                val property = WinRTXamlApplicationProperty(member.propertyName, ref, isReadOnly = member.setterTarget == null)
                // Match the projection's shared property nullability and collection mappings.
                val nullable = WinRTPropertyDefinition(member.propertyName, member.propertyTypeName)
                    .isNullablePropertyProjection(name, definitions)
                val kotlinType = xamlProjectedPropertySourceType(ref, specialTypes).let { if (nullable && !it.endsWith('?')) "$it?" else it }
                appendLine(xamlPropertyRegistrationSource(owner, property, kotlinType, convert,
                    accessorName = member.propertyName.replaceFirstChar(Char::lowercase)).prependIndent("      ") + ",")
            }
            appendLine("    )," )
            appendLine("  ))")
        }
        // Keep SDK-wide registration below JVM method limits. Each helper owns
        // one type, just as generated XamlTypeInfo owns its member table.
        types.forEachIndexed { index, type ->
            appendLine("private fun ${register}_$index() {")
            emit(type, sdk(type))
            appendLine("}")
        }
        appendLine("internal fun $register() {")
        types.forEachIndexed { index, type -> if (!sdk(type)) appendLine("  ${register}_$index()") }
        if (types.any(::sdk)) {
            appendLine("  if (io.github.composefluent.winrt.runtime.isWinRTXamlHotReloadEnabled()) {")
            types.forEachIndexed { index, type -> if (sdk(type)) appendLine("    ${register}_$index()") }
            appendLine("  }")
        }
        appendLine("}")
    })
    return "$packageName.$register"
}

/** Render the same mapped property shape as KotlinProjectionTypeResolver. */
internal fun xamlProjectedPropertySourceType(ref: WinRTTypeRef, specialTypes: WinRTMetadataSpecialTypeResolver): String {
    val descriptor = specialTypes.resolveType(ref, "")
    val reference = descriptor as? WinRTReferenceTypeDescriptor
    if (reference?.kind == WinRTReferenceInterfaceKind.Reference)
        return xamlProjectedPropertySourceType(ref.typeArguments.single(), specialTypes).removeSuffix("?") + "?"
    val name = ref.qualifiedName ?: ref.typeName
    val source = (descriptor as? WinRTCollectionTypeDescriptor)?.kind?.kotlinProjectedName
        ?: xamlTypeClassId(name).asSingleFqName().asString()
    // CsWinRT maps TypeName to System.Type; the Kotlin projection owns
    // KClass<*>?, including nullable native TypeName values (Style.TargetType).
    val projectedType = isWinRTTypeTypeName(name)
    val args = if (projectedType) "<*>" else
        ref.typeArguments.takeIf { it.isNotEmpty() }?.joinToString(", ", "<", ">") { xamlProjectedPropertySourceType(it, specialTypes) }.orEmpty()
    return source + args + if (isWinRTObjectTypeName(name) || projectedType) "?" else ""
}
