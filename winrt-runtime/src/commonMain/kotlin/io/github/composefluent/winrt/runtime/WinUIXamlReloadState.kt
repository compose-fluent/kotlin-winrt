package io.github.composefluent.winrt.runtime

/** Ephemeral development state, not application persistence or executable code.
 * SDK access stays in generated typed delegates, like CsWinRT custom-property access.
 * Only compiled names can be matched across a process replacement. */
data class WinUIXamlSavedElement(val name: String, val kind: String, val values: List<WinUIXamlVisualProperty>)

data class WinUIXamlStateRequest(val className: String, val resourcePath: String, val instance: Int = 0,
    val restore: List<WinUIXamlSavedElement>? = null, val componentKey: String = "")

class WinUIXamlStateAdapter(
    val capture: (Any, Set<String>) -> WinUIXamlSavedElement?,
    val restore: (Any, WinUIXamlSavedElement, Set<String>) -> Unit,
)

internal fun validateWinUIXamlSavedState(state: List<WinUIXamlSavedElement>) {
    require(state.size <= 512 && state.map { it.name }.distinct().size == state.size) { "Invalid saved component state." }
    state.forEach { entry ->
        require(entry.name.length in 1..256 && entry.kind.length in 1..128 && entry.values.size <= 16 &&
            entry.values.map { it.name }.distinct().size == entry.values.size &&
            entry.values.all { it.name.length in 1..128 && it.value.encodeToByteArray().size <= 128 * 1024 }) {
            "A saved control state exceeds its limits."
        }
    }
}
