package io.github.composefluent.winrt.runtime

/** Development protocol, separate from XAMLC's compilation/declaration protocol. */
object WinUiXamlHotReloadProtocol {
    const val VERSION = 4
    const val MAGIC = 0x4B585248
    const val SESSION_DIRECTORY = "KOTLIN_WINRT_HOT_RELOAD_DIRECTORY"
    const val PREVIEW_ARGUMENT = "--kotlin-winrt-xaml-preview"
    const val PREVIEW_ENVIRONMENT = "KOTLIN_WINRT_XAML_PREVIEW"
    const val PREVIEW_CLASS = "KotlinWinRT.XamlPreview"
    const val APPLIED = 0
    const val REJECTED = 1
    const val UNAVAILABLE = 2
    const val RESTART_REQUIRED = 3
}

/** Property and projected collection traversal. The runtime does not parse XAML. */
sealed interface WinUiXamlHotReloadStep {
    data class Property(val name: String) : WinUiXamlHotReloadStep
    data class Key(val name: String) : WinUiXamlHotReloadStep
    data class Index(val index: Int, val expectedSize: Int) : WinUiXamlHotReloadStep
}
data class WinUiXamlHotReloadTarget(val element: String = "", val path: List<WinUiXamlHotReloadStep> = emptyList())
data class WinUiXamlHotReloadChange(val element: String, val property: String, val literal: String,
    val path: List<WinUiXamlHotReloadStep> = emptyList())
data class WinUiXamlHotReloadResourceReference(val target: WinUiXamlHotReloadTarget, val property: String, val key: String)
/** Read the effective value after the transaction, including style-derived values. */
data class WinUiXamlHotReloadRead(val target: WinUiXamlHotReloadTarget, val property: String)
sealed interface WinUiXamlHotReloadItem {
    data class Existing(val index: Int) : WinUiXamlHotReloadItem
    data class Markup(val xaml: String) : WinUiXamlHotReloadItem
}
/** Reuse live children by their original index; parse new, unconnected subtrees. */
data class WinUiXamlHotReloadChildren(val target: WinUiXamlHotReloadTarget, val expectedSize: Int,
    val items: List<WinUiXamlHotReloadItem>)
/** A sealed Style is recreated by the SDK parser. Its dictionary and consumers
 * keep their identities; explicit references are reassigned in the transaction. */
data class WinUiXamlHotReloadResources(val target: WinUiXamlHotReloadTarget, val xaml: String,
    val expectedKeys: List<String>, val references: List<WinUiXamlHotReloadResourceReference>)
data class WinUiXamlHotReloadPatch(val className: String, val resourcePath: String, val expectedHash: String,
    val sourceHash: String, val version: Long, val changes: List<WinUiXamlHotReloadChange>,
    val resources: List<WinUiXamlHotReloadResources> = emptyList(),
    val reads: List<WinUiXamlHotReloadRead> = emptyList(),
    val children: List<WinUiXamlHotReloadChildren> = emptyList())
data class WinUiXamlHotReloadRoot(val className: String, val resourcePath: String, val sourceHash: String,
    val version: Long, val elements: List<String>, val instances: Int = 1)
data class WinUiXamlHotReloadValue(val element: String, val property: String, val value: String,
    val path: List<WinUiXamlHotReloadStep> = emptyList())
data class WinUiXamlHotReloadReply(val status: Int, val message: String, val roots: List<WinUiXamlHotReloadRoot> = emptyList(),
    val values: List<WinUiXamlHotReloadValue> = emptyList(), val inspection: WinUiXamlVisualSnapshot? = null)

/** Visual paths use the SDK visual tree, including control-template children. */
data class WinUiXamlInspectionRequest(val className: String, val resourcePath: String, val instance: Int = 0,
    val selectedPath: List<Int> = emptyList(), val capture: Boolean = true,
    val previewMarkup: String = "", val width: Int = 800, val height: Int = 600, val theme: String = "Default")
data class WinUiXamlVisualBounds(val x: Double, val y: Double, val width: Double, val height: Double)
data class WinUiXamlVisualNode(val path: List<Int>, val typeName: String, val name: String, val bounds: WinUiXamlVisualBounds)
data class WinUiXamlVisualProperty(val name: String, val value: String)
/** BGRA8 premultiplied pixels, as returned by WinUI RenderTargetBitmap.GetPixelsAsync. */
data class WinUiXamlVisualImage(val width: Int, val height: Int, val pixels: ByteArray)
data class WinUiXamlVisualSnapshot(val nodes: List<WinUiXamlVisualNode>, val properties: List<WinUiXamlVisualProperty>,
    val image: WinUiXamlVisualImage? = null)
