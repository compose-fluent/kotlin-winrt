package io.github.composefluent.winrt.runtime

/** XamlCompiler CSharpPagePass2 connection identity, emitted alongside IXamlType.
 * Signatures cover the generated connection contract, never source coordinates.
 */
data class WinUIXamlDesignConnection(val id: Int, val signature: String, val scopeId: Int,
    val template: Boolean, val scopeRoot: Boolean, val controlTemplate: Boolean = false)

class WinUIXamlDesignRebuildRequired(message: String) : IllegalStateException(message)

/** Reuses generated Connect/Update/StopTracking bodies on the design tree.
 * SDK tree traversal and DataContext events belong to the generated adapter.
 * No binding expression is interpreted or reflected over by this runtime.
 */
class WinUIXamlDesignBindingSession internal constructor(
    val className: String,
    val owner: Any,
) {
    private val component = owner as? WinUIXamlComponent
        ?: throw WinUIXamlDesignRebuildRequired("Rebuild the XAML component $className.")
    private val connections = component._kotlinXamlDesignConnections().lineSequence().filter { it.isNotEmpty() }.map { line ->
        val fields = line.split('|')
        require(fields.size == 6) { "Invalid generated design connection." }
        WinUIXamlDesignConnection(fields[0].toInt(), fields[1], fields[2].toInt(), fields[3] == "1", fields[4] == "1", fields[5] == "1")
    }.toList().also { require(it.size <= 4096 && it.map { item -> item.id }.distinct().size == it.size) }.associateBy { it.id }
    private val disabled = linkedSetOf<String>()
    private val scopes = mutableListOf<WinUIXamlBindingScope>()
    private val connected = mutableSetOf<Pair<Int, Int>>()

    fun beginDocument() {
        (owner as? WinUIXamlPageBindingOwner)?.stopDesignBindings()
        scopes.forEach { it.recycle(); it.state.eventConnections.disconnectAll() }
        scopes.clear(); connected.clear(); disabled.clear()
    }

    fun connect(target: Any, marker: String, inherited: WinUIXamlBindingScope?): WinUIXamlBindingScope? {
        val fields = marker.split(':', limit = 3)
        val id = fields.firstOrNull()?.toIntOrNull()
        val connection = connections[id] ?: throw WinUIXamlDesignRebuildRequired("Project bindings require rebuilding: unknown connection $id in $className.")
        if (fields.getOrNull(1) != connection.signature)
            throw WinUIXamlDesignRebuildRequired("Project bindings require rebuilding: connection $id changed in $className.")
        val suppressed = fields.getOrNull(2).orEmpty().split(',').filter { it.isNotBlank() }.map { "$id:$it" }
        if (!connection.template) {
            if (connected.add(0 to connection.id)) component._kotlinXamlConnectDesign(connection.id, target)
            disabled += suppressed
            return inherited
        }
        val scope = if (connection.scopeRoot) {
            WinUIXamlBindingScope(owner as? WinUIXamlBindingScopeOwner
                ?: throw WinUIXamlDesignRebuildRequired("Rebuild template bindings for $className."), connection.scopeId).also(scopes::add)
        } else requireNotNull(inherited?.takeIf { it.scopeId == connection.scopeId }) { "The template binding scope is missing." }
        scope.state.addDesignSuppression(suppressed)
        scope.connect(connection.id, target)
        return scope
    }

    fun isControlTemplate(connectionId: Int): Boolean = connections[connectionId]?.controlTemplate == true

    fun validateDocument(contract: String) {
        val expected = connections.values.sortedBy { it.id }.joinToString(",") { "${it.id}:${it.signature}" }
        if (contract != expected) throw WinUIXamlDesignRebuildRequired("Project bindings require rebuilding: the component connection contract changed in $className.")
    }

    fun updatePage() { (owner as? WinUIXamlPageBindingOwner)?.updateDesignBindings(disabled.joinToString("\n")) }

    fun close() { beginDocument() }

    companion object {
        fun create(className: String): WinUIXamlDesignBindingSession {
            check(isWinUIXamlPreviewHost()) { "Design bindings require an isolated preview host." }
            val definition = WinUIAuthoredTypeMetadata.designDefinition(className)
                ?: throw WinUIXamlDesignRebuildRequired("Project bindings require rebuilding: $className is not registered.")
            val activate = definition.activate
                ?: error("$className needs a public default constructor or explicit design data.")
            definition.initializer?.invoke()
            val owner = constructWinUIXamlDesignSource(activate)
            promoteWinUIXamlDesignSource(owner)
            return WinUIXamlDesignBindingSession(className, owner)
        }
    }
}
