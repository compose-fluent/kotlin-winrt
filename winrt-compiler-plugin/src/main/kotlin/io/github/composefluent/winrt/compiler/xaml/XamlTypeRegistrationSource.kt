package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.*
import kotlinx.serialization.json.JsonPrimitive

private val xamlSpecialTypes = WinRTMetadataModel(emptyList()).specialTypeResolver()
private const val runtimePackage = "io.github.composefluent.winrt.runtime."

/** Source and IR schema passes share the same runtime type/member contract.
 * CSharpTypeInfoPass2 owns these typed delegates; classification stays in metadata.
 */
internal fun xamlPropertyRegistrationSource(
    owner: String,
    property: WinRTXamlApplicationProperty,
    kotlinType: String,
    convert: (String, String) -> String,
): String = buildString {
    appendLine("${runtimePackage}WinRTXamlMemberDefinition(")
    appendLine("  name = ${literal(property.name)}, typeName = ${literal(property.type.xamlStandardTypeName())}, type = ${classLiteral(kotlinType)},")
    appendLine("  isDependencyProperty = ${property.isDependencyProperty}, get = { (it as $owner).`${property.name}` },")
    if (!property.isReadOnly) appendLine("  set = { instance, value -> (instance as $owner).`${property.name}` = ${convert("value", kotlinType)} },")
    val values = xamlValueTypeRegistrationSources(property.type, kotlinType, convert)
    if (values.isNotEmpty()) appendLine("  valueTypes = listOf(${values.joinToString(",\n")}),")
    append(")")
}

internal data class XamlStaticAccessor(
    val method: WinRTXamlApplicationMethod,
    val returnType: String,
    val parameterTypes: List<String>,
)

/** DirectUISchemaContext resolves a public static factory with one String parameter. */
internal fun xamlCreateFromStringMethodSource(
    typeName: String,
    members: Map<String, WinRTXamlApplicationTypeMembers>,
): String? {
    val name = members.getValue(typeName).createFromStringMethod ?: return null
    val qualified = if ('.' in name) name else "$typeName.$name"
    val owner = qualified.substringBeforeLast('.')
    val methodName = qualified.substringAfterLast('.')
    require(owner.isNotBlank() && methodName.isNotBlank()) { "$typeName has an invalid CreateFromString method: $name" }
    val schema = members[owner] ?: return "$owner.`$methodName`"
    val factory = schema.methods.singleOrNull { method -> method.isPublic && method.isStatic &&
        method.name.equals(methodName, ignoreCase = true) && method.parameterTypes.size == 1 &&
        method.parameterTypes.single().xamlStandardTypeName() == "String" && !isWinRTVoidTypeName(method.returnType.typeName) }
    require(factory != null) { "$typeName CreateFromString method $qualified must be a public companion/object factory with one String parameter and a return value" }
    return "$owner.`${factory.name}`"
}

/** Prefer the mutable contract when a collection implements several projected interfaces. */
internal fun xamlCollectionRegistrationSources(
    interfaces: List<Pair<WinRTTypeRef, String>>,
    convert: (String, String) -> String,
): List<String> = interfaces.mapNotNull { (type, source) ->
    val descriptor = xamlSpecialTypes.resolveType(WinRTTypeRef.fromDisplayName(type.typeName), "") as? WinRTCollectionTypeDescriptor
        ?: return@mapNotNull null
    val priority = when (descriptor.kind) {
        WinRTCollectionInterfaceKind.Vector, WinRTCollectionInterfaceKind.Map -> 0
        WinRTCollectionInterfaceKind.VectorView, WinRTCollectionInterfaceKind.MapView -> 1
        WinRTCollectionInterfaceKind.Iterable -> 2
        else -> return@mapNotNull null
    }
    Triple(priority, type, source)
}.minByOrNull { it.first }?.let { (_, type, source) -> xamlValueTypeRegistrationSources(type, source, convert) }.orEmpty()

internal fun xamlAttachedRegistrationSources(
    owner: String,
    members: WinRTXamlApplicationTypeMembers,
    accessors: List<XamlStaticAccessor>,
    convert: (String, String) -> String,
): List<String> = accessors.filter { it.method.isStatic && it.method.isPublic &&
    it.method.name.startsWith("Get") && it.method.name.length > 3 &&
    it.method.parameterTypes.size == 1 && !isWinRTVoidTypeName(it.method.returnType.typeName) }.map { getter ->
    val name = getter.method.name.removePrefix("Get")
    val setter = accessors.singleOrNull { it.method.isStatic && it.method.isPublic &&
        it.method.name == "Set$name" && it.method.parameterTypes == getter.method.parameterTypes + getter.method.returnType &&
        isWinRTVoidTypeName(it.method.returnType.typeName) }
    buildString {
        appendLine("${runtimePackage}WinRTXamlMemberDefinition(")
        appendLine("  name = ${literal(name)}, typeName = ${literal(getter.method.returnType.xamlStandardTypeName())}, type = ${classLiteral(getter.returnType)},")
        appendLine("  isAttachable = true, isDependencyProperty = ${members.properties.any { it.isStatic && it.name == "${name}Property" && it.type.qualifiedName == "Microsoft.UI.Xaml.DependencyProperty" }},")
        appendLine("  targetTypeName = ${literal(getter.method.parameterTypes.single().xamlStandardTypeName())}, targetType = ${classLiteral(getter.parameterTypes.single())},")
        appendLine("  get = { $owner.`${getter.method.name}`(it as ${getter.parameterTypes.single()}) },")
        if (setter != null) appendLine("  set = { instance, value -> $owner.`${setter.method.name}`(instance as ${setter.parameterTypes[0]}, ${convert("value", setter.parameterTypes[1])}) },")
        val values = xamlValueTypeRegistrationSources(getter.method.returnType, getter.returnType, convert)
        if (values.isNotEmpty()) appendLine("  valueTypes = listOf(${values.joinToString(",\n")}),")
        append(")")
    }
}

/** Registers nested closed types too, including a nullable item in a collection.
 * Native and JVM cannot reconstruct those arguments from an erased KClass at runtime.
 */
internal fun xamlValueTypeRegistrationSources(
    type: WinRTTypeRef,
    kotlinType: String,
    convert: (String, String) -> String,
): List<String> {
    val arguments = xamlKotlinTypeArguments(kotlinType)
    val descriptor = xamlSpecialTypes.resolveType(WinRTTypeRef.fromDisplayName(type.typeName), "")
    val children = when {
        type.kind == WinRTTypeRefKind.Array -> listOf(requireNotNull(type.elementType) to
            (arguments.singleOrNull() ?: requireNotNull(winRTArrayElementForKotlinType(kotlinType.removeSuffix("?"))).toKotlinProjectionTypeName()))
        descriptor is WinRTReferenceTypeDescriptor && descriptor.kind == WinRTReferenceInterfaceKind.Reference ->
            listOf(type.typeArguments.single() to (arguments.singleOrNull() ?: kotlinType.removeSuffix("?")))
        else -> type.typeArguments.zip(arguments)
    }
    val own = buildString {
        val collection = descriptor as? WinRTCollectionTypeDescriptor
        val boxed = descriptor as? WinRTReferenceTypeDescriptor
        if (type.kind != WinRTTypeRefKind.Array &&
            collection?.kind !in setOf(WinRTCollectionInterfaceKind.Iterable, WinRTCollectionInterfaceKind.VectorView,
                WinRTCollectionInterfaceKind.Vector, WinRTCollectionInterfaceKind.MapView, WinRTCollectionInterfaceKind.Map) &&
            boxed?.kind != WinRTReferenceInterfaceKind.Reference) return@buildString
        appendLine("${runtimePackage}WinRTXamlValueTypeDefinition(")
        appendLine("  name = ${literal(type.xamlStandardTypeName())}, type = ${classLiteral(kotlinType)},")
        fun element(prefix: String, index: Int) {
            val (metadata, source) = children[index]
            appendLine("  ${prefix}TypeName = ${literal(metadata.xamlStandardTypeName())}, ${prefix}Type = ${classLiteral(source)},")
        }
        when {
            type.kind == WinRTTypeRefKind.Array -> { appendLine("  isArray = true,"); element("item", 0) }
            boxed?.kind == WinRTReferenceInterfaceKind.Reference -> element("boxed", 0)
            collection?.keyType != null -> { element("key", 0); element("item", 1) }
            else -> element("item", 0)
        }
        if (collection?.kind == WinRTCollectionInterfaceKind.Vector) appendLine(
            "  addToVector = { instance, value -> (instance as ${kotlinType.removeSuffix("?")}).add(${convert("value", children[0].second)}); Unit },")
        if (collection?.kind == WinRTCollectionInterfaceKind.Map) appendLine(
            "  addToMap = { instance, key, value -> (instance as ${kotlinType.removeSuffix("?")})[${convert("key", children[0].second)}] = ${convert("value", children[1].second)} },")
        append(")")
    }
    return children.flatMap { (metadata, source) -> xamlValueTypeRegistrationSources(metadata, source, convert) } + listOfNotNull(own.takeIf(String::isNotEmpty))
}

/** Keeps nested nullable arguments which WinRTTypeRef.fromDisplayName intentionally erases. */
internal fun xamlKotlinTypeArguments(type: String): List<String> {
    val raw = type.trim().removeSuffix("?")
    val start = raw.indexOf('<')
    if (start < 0 || !raw.endsWith('>')) return emptyList()
    val body = raw.substring(start + 1, raw.length - 1)
    var depth = 0
    var offset = 0
    return buildList {
        body.forEachIndexed { index, ch -> when (ch) {
            '<' -> depth++
            '>' -> depth--
            ',' -> if (depth == 0) { add(body.substring(offset, index).trim()); offset = index + 1 }
        } }
        add(body.substring(offset).trim())
    }
}

private fun literal(value: String): String = JsonPrimitive(value).toString().replace("$", "\\$")
private fun classLiteral(type: String): String = type.trim().removeSuffix("?").substringBefore('<') + "::class"
