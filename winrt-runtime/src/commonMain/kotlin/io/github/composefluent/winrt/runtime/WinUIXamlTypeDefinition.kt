@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/** Generated XAML type information, corresponding to XamlUserType in CSharpTypeInfoPass2.tt. */
class WinUIXamlTypeDefinition(
    val type: KClass<*>,
    val name: String,
    val baseName: String,
    val baseType: KClass<*>? = null,
    val activate: (() -> Any)? = null,
    val contentProperty: String? = null,
    members: List<WinUIXamlMemberDefinition> = emptyList(),
    val initializer: (() -> Unit)? = null,
    val isBindable: Boolean = members.any { !it.isAttachable },
    val createFromString: ((String) -> Any)? = null,
    val shape: WinUIXamlValueTypeDefinition? = null,
    val valueTypes: List<WinUIXamlValueTypeDefinition> = emptyList(),
    val isWinRTComponent: Boolean = true,
) {
    val members: Map<String, WinUIXamlMemberDefinition> = members.associateBy { it.name }

    init {
        require(name.isNotBlank() && baseName.isNotBlank() && name != baseName)
        require(this.members.size == members.size) { "Duplicate XAML member on $name" }
        require(contentProperty == null || contentProperty.isNotBlank())
    }
}

/** Accessors are generated Kotlin calls; the runtime owns only the IXamlMember ABI boundary. */
class WinUIXamlMemberDefinition(
    val name: String,
    val typeName: String,
    val type: KClass<*>? = null,
    val get: (Any) -> Any?,
    val set: ((Any, Any?) -> Unit)? = null,
    val isDependencyProperty: Boolean = false,
    val collection: WinUIXamlCollectionDefinition? = null,
    val isAttachable: Boolean = false,
    val dictionary: WinUIXamlDictionaryDefinition? = null,
    val targetTypeName: String? = null,
    val targetType: KClass<*>? = null,
    val valueTypes: List<WinUIXamlValueTypeDefinition> = emptyList(),
    @InternalWinRTApi
    val localValue: ((Any) -> WinUIXamlLocalValue)? = null,
    /** Generated vector shape; does not invoke unrelated application getters. */
    val isCollection: Boolean = collection != null || valueTypes.any {
        it.name == typeName && it.addToVector != null
    },
) {
    init {
        require(name.isNotBlank() && typeName.isNotBlank())
        require(collection == null || dictionary == null) { "A XAML member cannot have both vector and map insertion." }
    }
}

/** Closed type information supplied by the compiler, rather than inferred from erased KClass.
 * Corresponds to XamlUserType's array, item/key/boxed type and typed insertion delegates.
 */
class WinUIXamlValueTypeDefinition(
    val name: String,
    val type: KClass<*>,
    val isArray: Boolean = false,
    val itemTypeName: String? = null,
    val itemType: KClass<*>? = null,
    val keyTypeName: String? = null,
    val keyType: KClass<*>? = null,
    val boxedTypeName: String? = null,
    val boxedType: KClass<*>? = null,
    val addToVector: ((Any, Any?) -> Unit)? = null,
    val addToMap: ((Any, Any?, Any?) -> Unit)? = null,
) {
    init {
        require(name.isNotBlank())
        require((itemTypeName == null) == (itemType == null))
        require((keyTypeName == null) == (keyType == null))
        require((boxedTypeName == null) == (boxedType == null))
        require(addToVector == null || (itemType != null && addToMap == null))
        require(addToMap == null || (itemType != null && keyType != null))
    }
}

/** CSharpTypeInfoPass2's ItemType and CollectionAdd, with generated typed Add calls. */
class WinUIXamlCollectionDefinition(
    val type: KClass<*>,
    val itemTypeName: String,
    val itemType: KClass<*>,
    val add: (Any, Any?) -> Unit,
)

/** CSharpTypeInfoPass2's KeyType, ItemType and DictionaryAdd with generated typed insertion. */
class WinUIXamlDictionaryDefinition(
    val type: KClass<*>,
    val keyTypeName: String,
    val keyType: KClass<*>,
    val itemTypeName: String,
    val itemType: KClass<*>,
    val add: (Any, Any?, Any?) -> Unit,
)

fun registerWinUIXamlTypeDefinition(definition: WinUIXamlTypeDefinition) {
    if (definition.isWinRTComponent) Projections.registerAuthoredRuntimeClassType(definition.type, definition.name, definition.baseName)
    else {
        // CsWinRT's bindable managed models have XAML metadata without a WinRT type signature.
        TypeNameSupport.registerProjectionType(definition.type, definition.name)
        WinUIAuthoredTypeMetadata.register(definition.type, definition.name, definition.baseName)
    }
    WinUIAuthoredTypeMetadata.registerDefinition(definition)
}

/** CSharpTypeInfoPass2 also supplies XamlUserType entries for referenced WinMD types.
 * Keep their existing projection identity; a native library's metadata takes precedence.
 */
fun registerWinUIXamlProjectedTypeDefinition(definition: WinUIXamlTypeDefinition) {
    WinUIAuthoredTypeMetadata.registerProjectedDefinition(definition)
}

/** Application enums use Kotlin declaration ordinals as their Int32 values.
 * Mirrors XamlUserType's enum table; component ABI export is a separate contract. */
fun <T : Enum<T>> registerWinUIXamlEnumType(type: KClass<T>, name: String, entries: Array<T>) {
    Projections.registerEnumType(type, name, "enum($name;i4)", { it.ordinal }, entries)
    val byName = entries.associateBy { it.name }
    WinUIAuthoredTypeMetadata.registerEnum(type, name) { input ->
        val value = input.split(',').fold(0) { result, part ->
            val token = part.trim()
            val entry = byName[token] ?: byName.entries.firstOrNull { it.key.equals(token, true) }?.value
            result or (entry?.ordinal ?: token.toInt())
        }
        requireNotNull(entries.firstOrNull { it.ordinal == value }) { "Unknown $name value: $input" }
    }
}

/** SDK delegates capture local-value precedence before a development transaction.
 * ClearValue restores styling/defaults; restoring an effective getter value would
 * incorrectly create a local override. Generated code owns DependencyProperty ABI. */
@InternalWinRTApi
class WinUIXamlLocalValue(val clear: () -> Unit, val restore: () -> Unit)
