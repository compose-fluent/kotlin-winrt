package io.github.composefluent.winrt.ide.xaml

import io.github.composefluent.winrt.metadata.*

data class WinRTXamlMember(val name: String, val typeName: String, val owner: String, val isEvent: Boolean = false)
data class WinRTXamlContentMember(val name: String, val collection: Boolean)

/** An editor view of normalized WinMD, not a second control or projection registry. */
class WinRTXamlCatalog(val model: WinRTMetadataModel) {
    val types = model.namespaces.flatMap { it.types }.associateBy { it.qualifiedName }
    private val closure = model.closureResolver()
    private val specialTypes = model.specialTypeResolver()

    fun candidates(uri: String): List<WinRTTypeDefinition> = namespaces(uri).flatMap { ns ->
        model.namespaces.firstOrNull { it.name == ns }?.types.orEmpty()
    }.filter { !it.isProjectionInternal && it.genericParameterCount == 0 &&
        it.kind in setOf(WinRTTypeKind.RuntimeClass, WinRTTypeKind.Struct, WinRTTypeKind.Enum) }

    fun resolve(uri: String, name: String): WinRTTypeDefinition? = namespaces(uri).firstNotNullOfOrNull { types["$it.$name"] }

    /** ContentPropertyAttribute and the existing normalized collection closure
     * determine traversal; no IDE list of container controls is maintained. */
    fun contentMember(type: WinRTTypeDefinition): WinRTXamlContentMember? {
        val seen = hashSetOf<String>()
        var current: WinRTTypeDefinition? = type
        while (current != null && seen.add(current.qualifiedName)) {
            val definition = current
            val name = definition.customAttributes.firstOrNull { it.typeName in setOf(
                "Microsoft.UI.Xaml.Markup.ContentPropertyAttribute", "Windows.UI.Xaml.Markup.ContentPropertyAttribute") }
                ?.namedArguments?.firstOrNull { it.name == "Name" }?.value?.stringValue
            if (name != null) return propertyContent(type, name)
            current = definition.baseTypeName?.let(types::get)
        }
        return null
    }

    fun propertyContent(type: WinRTTypeDefinition, name: String): WinRTXamlContentMember? {
        val member = members(type).firstOrNull { it.name == name && !it.isEvent } ?: return null
        val reference = WinRTTypeRef.fromDisplayName(member.typeName)
        fun mutable(ref: WinRTTypeRef) = when (val shape = specialTypes.resolveType(ref, type.namespace)) {
            is WinRTCollectionTypeDescriptor -> shape.kind == WinRTCollectionInterfaceKind.Vector
            is WinRTBindableCollectionTypeDescriptor -> shape.kind == WinRTBindableCollectionKind.Vector
            else -> false
        }
        val definition = types[reference.qualifiedName]
        val collection = mutable(reference) || definition?.takeIf { it.kind == WinRTTypeKind.RuntimeClass }
            ?.let { closure.resolveRuntimeClass(it).instanceInterfaceClosure.any { contract -> mutable(contract.interfaceType) } } == true
        return WinRTXamlContentMember(name, collection)
    }

    fun members(type: WinRTTypeDefinition): List<WinRTXamlMember> = buildList {
        val seen = hashSetOf<String>()
        var current: WinRTTypeDefinition? = type
        while (current != null && seen.add(current.qualifiedName)) {
            val owner = current
            // CsWinRT code_writers.h's class-member writers use the default and
            // implemented interfaces. Their closure stays in the metadata owner.
            val definitions = listOf(owner) + if (owner.kind == WinRTTypeKind.RuntimeClass)
                closure.resolveRuntimeClass(owner).instanceInterfaceClosure.mapNotNull { it.definitionType } else emptyList()
            definitions.forEach { definition ->
                definition.properties.filter { !it.isStatic && it.hasValidAccessors }.forEach {
                    add(WinRTXamlMember(it.name, it.typeName, owner.qualifiedName))
                }
                definition.events.filter { !it.isStatic && it.hasValidAccessors }.forEach {
                    add(WinRTXamlMember(it.name, it.delegateTypeName, owner.qualifiedName, true))
                }
            }
            current = owner.baseTypeName?.let { types[it] }
        }
    }.distinctBy { it.name }

    fun attachedMembers(type: WinRTTypeDefinition): List<WinRTXamlMember> {
        val definitions = listOf(type) + if (type.kind == WinRTTypeKind.RuntimeClass)
            closure.resolveRuntimeClass(type).activation.staticInterfaces.mapNotNull { it.definitionType } else emptyList()
        val methods = definitions.flatMap { it.methods }
        // XAMLC DirectUIXamlType's attachable-member convention: matching
        // GetX(target)/SetX(target, value), rather than a list of known properties.
        return methods.filter { it.name.startsWith("Set") && it.parameters.size == 2 }.mapNotNull { setter ->
            val name = setter.name.removePrefix("Set")
            val getter = methods.firstOrNull { it.name == "Get$name" && it.parameters.size == 1 }
                ?: return@mapNotNull null
            if (getter.returnTypeName != setter.parameters[1].typeName) return@mapNotNull null
            WinRTXamlMember(name, getter.returnTypeName, type.qualifiedName)
        }.distinctBy { it.name }
    }

    companion object {
        const val PRESENTATION = WinRTXamlNamespaces.PRESENTATION
        const val XAML = WinRTXamlNamespaces.XAML
        fun namespaces(uri: String): List<String> = WinRTXamlNamespaces.namespaces(uri)
    }
}
