package io.github.composefluent.winrt.ide.xaml

import io.github.composefluent.winrt.metadata.*

data class WinRTXamlMember(val name: String, val typeName: String, val owner: String, val isEvent: Boolean = false)

/** An editor view of normalized WinMD, not a second control or projection registry. */
class WinRTXamlCatalog(val model: WinRTMetadataModel) {
    val types = model.namespaces.flatMap { it.types }.associateBy { it.qualifiedName }
    private val closure = model.closureResolver()

    fun candidates(uri: String): List<WinRTTypeDefinition> = namespaces(uri).flatMap { ns ->
        model.namespaces.firstOrNull { it.name == ns }?.types.orEmpty()
    }.filter { !it.isProjectionInternal && it.genericParameterCount == 0 &&
        it.kind in setOf(WinRTTypeKind.RuntimeClass, WinRTTypeKind.Struct, WinRTTypeKind.Enum) }

    fun resolve(uri: String, name: String): WinRTTypeDefinition? = namespaces(uri).firstNotNullOfOrNull { types["$it.$name"] }

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
        const val PRESENTATION = "http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        const val XAML = "http://schemas.microsoft.com/winfx/2006/xaml"
        // DirectUISchemaContext.DirectUI2010Paths in kotlin-winrt-xamlc.
        // Only namespace aliases live here; types and members come from WinMD.
        private val presentationNamespaces = listOf("Microsoft.UI.Xaml", "Microsoft.UI.Xaml.Automation",
            "Microsoft.UI.Xaml.Automation.Peers", "Microsoft.UI.Xaml.Automation.Provider", "Microsoft.UI.Xaml.Controls",
            "Microsoft.UI.Xaml.Controls.Primitives", "Microsoft.UI.Xaml.Data", "Microsoft.UI.Xaml.Documents",
            "Microsoft.UI.Xaml.Input", "Microsoft.UI.Xaml.Interop", "Microsoft.UI.Xaml.Markup", "Microsoft.UI.Xaml.Media",
            "Microsoft.UI.Xaml.Media.Animation", "Microsoft.UI.Xaml.Media.Imaging", "Microsoft.UI.Xaml.Media.Media3D",
            "Microsoft.UI.Xaml.Navigation", "Microsoft.UI.Xaml.Resources", "Microsoft.UI.Xaml.Shapes",
            "Microsoft.UI.Xaml.Threading", "Windows.UI", "Windows.UI.Text")

        fun namespaces(uri: String): List<String> = when {
            uri.substringBefore('?') in setOf(PRESENTATION, "http://schemas.microsoft.com/windows/2010/directui") -> presentationNamespaces
            uri.startsWith("using:") -> listOf(uri.removePrefix("using:").substringBefore('?')).filter { it.isNotBlank() }
            else -> emptyList()
        }
    }
}
