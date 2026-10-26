package io.github.composefluent.winrt.runtime

/** Ephemeral development state, not application persistence or executable code.
 * SDK access stays in generated typed delegates, like CsWinRT custom-property access.
 * Compiled names and unique SDK automation IDs survive process replacement. */
data class WinUIXamlSavedElement(val name: String, val kind: String, val values: List<WinUIXamlVisualProperty>)

data class WinUIXamlStateRequest(val className: String, val resourcePath: String, val instance: Int = 0,
    val restore: List<WinUIXamlSavedElement>? = null, val componentKey: String = "")

class WinUIXamlStateAdapter(
    val capture: (Any, Set<String>) -> WinUIXamlSavedElement?,
    val restore: (Any, WinUIXamlSavedElement, Set<String>) -> Unit,
    val discover: (Any) -> Map<String, Any> = { emptyMap() },
)

internal fun validateWinUIXamlSavedState(state: List<WinUIXamlSavedElement>) {
    require(state.size <= 4096 && state.map { it.name }.distinct().size == state.size) { "Invalid saved component state." }
    require(state.sumOf { entry -> entry.values.sumOf { it.value.encodeToByteArray().size.toLong() } } <= 8 * 1024 * 1024) { "Saved component state exceeds 8 MiB." }
    state.forEach { entry ->
        require(entry.name.length in 1..1024 && entry.kind.length in 1..128 && entry.values.size <= 128 &&
            entry.values.map { it.name }.distinct().size == entry.values.size &&
            entry.values.all { it.name.length in 1..128 && it.value.encodeToByteArray().size <= 128 * 1024 }) {
            "A saved control state exceeds its limits."
        }
    }
}

/** Application checkpoints for development rebuilds, available on JVM and Native.
 * Implement on a component or its DataContext. Use a versioned schema name; a
 * changed schema is skipped instead of being applied to an incompatible model.
 * Values are data, never executable continuations. Restore durable task inputs
 * here and restart/cancel asynchronous work using the application's own lifetime.
 * As with CsWinRT generated custom properties, access runs on the owner UI thread.
 */
interface WinUIXamlRestartParticipant {
    val restartStateSchema: String
    fun saveRestartState(): Map<String, String>
    fun restoreRestartState(values: Map<String, String>)
}

internal fun captureWinUIXamlParticipant(value: Any): WinUIXamlSavedElement? {
    val participant = value as? WinUIXamlRestartParticipant ?: return null
    val schema = participant.restartStateSchema
    require(schema.length in 1..112) { "Provide a versioned restart state schema (1–112 characters)." }
    return WinUIXamlSavedElement("", "Participant:$schema", participant.saveRestartState().map { (name, data) ->
        WinUIXamlVisualProperty(name, data)
    })
}
