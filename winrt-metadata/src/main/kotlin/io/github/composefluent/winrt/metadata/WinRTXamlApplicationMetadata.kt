package io.github.composefluent.winrt.metadata

/** Compile-time application schema only; these properties are not exported WinRT ABI members. */
data class WinRTXamlApplicationProperty(
    val name: String,
    val type: WinRTTypeRef,
    val isReadOnly: Boolean = false,
    val isPublic: Boolean = true,
    val isStatic: Boolean = false,
    val isDependencyProperty: Boolean = false,
) {
    init { require(name.isNotBlank()) }
}

/** A projected add/remove handler pair consumed by the generated Kotlin connector. */
data class WinRTXamlApplicationEvent(val name: String, val handlerType: WinRTTypeRef) {
    init { require(name.isNotBlank()) }
}

/** Temporary CLR method signatures for x:Bind and static attached accessors.
 * Calls remain typed Kotlin IR; this does not publish component ABI methods. */
data class WinRTXamlApplicationMethod(
    val name: String,
    val returnType: WinRTTypeRef,
    val parameterTypes: List<WinRTTypeRef> = emptyList(),
    val isStatic: Boolean = false,
    val isPublic: Boolean = true,
)

data class WinRTXamlApplicationTypeMembers(
    val properties: List<WinRTXamlApplicationProperty> = emptyList(),
    val contentProperty: String? = null,
    val events: List<WinRTXamlApplicationEvent> = emptyList(),
    val methods: List<WinRTXamlApplicationMethod> = emptyList(),
    val createFromStringMethod: String? = null,
) {
    init {
        require(properties.map { it.name }.distinct().size == properties.size)
        require(events.map { it.name }.distinct().size == events.size)
        require(contentProperty == null || contentProperty.isNotBlank())
        require(createFromStringMethod == null || createFromStringMethod.isNotBlank())
    }
}

/** XamlSchemaCodeInfo.GetFullGenericNestedName's IDL names, distinct from Kotlin API spelling.
 * The CLR primitives use SByte/Byte/Single, arrays retain their rank, and closed generics retain arity.
 */
fun WinRTTypeRef.xamlStandardTypeName(): String = when (kind) {
    WinRTTypeRefKind.Array -> requireNotNull(elementType).xamlStandardTypeName() + "[" + ",".repeat(arrayRank.coerceAtLeast(1) - 1) + "]"
    WinRTTypeRefKind.Named -> {
        val name = qualifiedName.orEmpty()
        val fundamental = winRTFundamentalTypeForName(name.removePrefix("kotlin."))
        val standardName = when {
            fundamental != null -> when (fundamental) {
                WinRTFundamentalType.Int8 -> "SByte"
                WinRTFundamentalType.UInt8 -> "Byte"
                WinRTFundamentalType.Float -> "Single"
                else -> fundamental.name
            }
            isWinRTObjectTypeName(name) -> "Object"
            isWinRTGuidTypeName(name) -> "Guid"
            else -> name.substringBefore('`')
        }
        if (typeArguments.isEmpty()) standardName else "$standardName`${typeArguments.size}" +
            typeArguments.joinToString(", ", "<", ">") { it.xamlStandardTypeName() }
    }
    else -> error("A XAML type needs a concrete metadata name: $typeName")
}
