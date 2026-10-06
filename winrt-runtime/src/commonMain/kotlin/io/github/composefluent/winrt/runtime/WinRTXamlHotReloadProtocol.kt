package io.github.composefluent.winrt.runtime

/** Development protocol, separate from XAMLC's compilation/declaration protocol. */
object WinRTXamlHotReloadProtocol {
    const val VERSION = 3
    const val MAGIC = 0x4B585248
    const val SESSION_DIRECTORY = "KOTLIN_WINRT_HOT_RELOAD_DIRECTORY"
    const val APPLIED = 0
    const val REJECTED = 1
    const val UNAVAILABLE = 2
    const val RESTART_REQUIRED = 3
}

/** Property and projected collection traversal. The runtime does not parse XAML. */
sealed interface WinRTXamlHotReloadStep {
    data class Property(val name: String) : WinRTXamlHotReloadStep
    data class Key(val name: String) : WinRTXamlHotReloadStep
    data class Index(val index: Int, val expectedSize: Int) : WinRTXamlHotReloadStep
}
data class WinRTXamlHotReloadTarget(val element: String = "", val path: List<WinRTXamlHotReloadStep> = emptyList())
data class WinRTXamlHotReloadChange(val element: String, val property: String, val literal: String,
    val path: List<WinRTXamlHotReloadStep> = emptyList())
data class WinRTXamlHotReloadResourceReference(val target: WinRTXamlHotReloadTarget, val property: String, val key: String)
/** Read the effective value after the transaction, including style-derived values. */
data class WinRTXamlHotReloadRead(val target: WinRTXamlHotReloadTarget, val property: String)
sealed interface WinRTXamlHotReloadItem {
    data class Existing(val index: Int) : WinRTXamlHotReloadItem
    data class Markup(val xaml: String) : WinRTXamlHotReloadItem
}
/** Reuse live children by their original index; parse new, unconnected subtrees. */
data class WinRTXamlHotReloadChildren(val target: WinRTXamlHotReloadTarget, val expectedSize: Int,
    val items: List<WinRTXamlHotReloadItem>)
/** A sealed Style is recreated by the SDK parser. Its dictionary and consumers
 * keep their identities; explicit references are reassigned in the transaction. */
data class WinRTXamlHotReloadResources(val target: WinRTXamlHotReloadTarget, val xaml: String,
    val expectedKeys: List<String>, val references: List<WinRTXamlHotReloadResourceReference>)
data class WinRTXamlHotReloadPatch(val className: String, val resourcePath: String, val expectedHash: String,
    val sourceHash: String, val version: Long, val changes: List<WinRTXamlHotReloadChange>,
    val resources: List<WinRTXamlHotReloadResources> = emptyList(),
    val reads: List<WinRTXamlHotReloadRead> = emptyList(),
    val children: List<WinRTXamlHotReloadChildren> = emptyList())
data class WinRTXamlHotReloadRoot(val className: String, val resourcePath: String, val sourceHash: String,
    val version: Long, val elements: List<String>)
data class WinRTXamlHotReloadValue(val element: String, val property: String, val value: String,
    val path: List<WinRTXamlHotReloadStep> = emptyList())
data class WinRTXamlHotReloadReply(val status: Int, val message: String, val roots: List<WinRTXamlHotReloadRoot> = emptyList(),
    val values: List<WinRTXamlHotReloadValue> = emptyList())
