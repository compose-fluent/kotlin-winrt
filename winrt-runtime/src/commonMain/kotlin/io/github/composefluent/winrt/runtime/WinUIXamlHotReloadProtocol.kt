package io.github.composefluent.winrt.runtime

/** Development protocol, separate from XAMLC's compilation/declaration protocol. */
object WinUIXamlHotReloadProtocol {
    const val VERSION = 5
    const val MAX_IMAGE_DIMENSION = 4096
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
sealed interface WinUIXamlHotReloadStep {
    data class Property(val name: String) : WinUIXamlHotReloadStep
    data class Key(val name: String) : WinUIXamlHotReloadStep
    data class Index(val index: Int, val expectedSize: Int) : WinUIXamlHotReloadStep
}
data class WinUIXamlHotReloadTarget(val element: String = "", val path: List<WinUIXamlHotReloadStep> = emptyList())
data class WinUIXamlHotReloadChange(val element: String, val property: String, val literal: String,
    val path: List<WinUIXamlHotReloadStep> = emptyList())
data class WinUIXamlHotReloadResourceReference(val target: WinUIXamlHotReloadTarget, val property: String, val key: String)
/** Read the effective value after the transaction, including style-derived values. */
data class WinUIXamlHotReloadRead(val target: WinUIXamlHotReloadTarget, val property: String)
sealed interface WinUIXamlHotReloadItem {
    data class Existing(val index: Int) : WinUIXamlHotReloadItem
    data class Markup(val xaml: String) : WinUIXamlHotReloadItem
}
/** Reuse live children by their original index; parse new, unconnected subtrees. */
data class WinUIXamlHotReloadChildren(val target: WinUIXamlHotReloadTarget, val expectedSize: Int,
    val items: List<WinUIXamlHotReloadItem>)
/** A sealed Style is recreated by the SDK parser. Its dictionary and consumers
 * keep their identities; explicit references are reassigned in the transaction. */
data class WinUIXamlHotReloadResources(val target: WinUIXamlHotReloadTarget, val xaml: String,
    val expectedKeys: List<String>, val references: List<WinUIXamlHotReloadResourceReference>)
data class WinUIXamlHotReloadPatch(val className: String, val resourcePath: String, val expectedHash: String,
    val sourceHash: String, val version: Long, val changes: List<WinUIXamlHotReloadChange>,
    val resources: List<WinUIXamlHotReloadResources> = emptyList(),
    val reads: List<WinUIXamlHotReloadRead> = emptyList(),
    val children: List<WinUIXamlHotReloadChildren> = emptyList())
data class WinUIXamlHotReloadRoot(val className: String, val resourcePath: String, val sourceHash: String,
    val version: Long, val elements: List<String>, val instances: Int = 1)
data class WinUIXamlHotReloadValue(val element: String, val property: String, val value: String,
    val path: List<WinUIXamlHotReloadStep> = emptyList())
data class WinUIXamlHotReloadReply(val status: Int, val message: String, val roots: List<WinUIXamlHotReloadRoot> = emptyList(),
    val values: List<WinUIXamlHotReloadValue> = emptyList(), val inspection: WinUIXamlVisualSnapshot? = null)

/** Visual paths use the SDK visual tree, including control-template children. */
data class WinUIXamlInspectionRequest(val className: String, val resourcePath: String, val instance: Int = 0,
    val selectedPath: List<Int> = emptyList(), val capture: Boolean = true,
    val previewMarkup: String = "", val width: Int = 800, val height: Int = 600, val theme: String = "Default", val renderScale: Double = 1.0)
data class WinUIXamlVisualBounds(val x: Double, val y: Double, val width: Double, val height: Double)
data class WinUIXamlVisualNode(val path: List<Int>, val typeName: String, val name: String, val bounds: WinUIXamlVisualBounds)
data class WinUIXamlVisualProperty(val name: String, val value: String)
/** BGRA8 premultiplied pixels, as returned by WinUI RenderTargetBitmap.GetPixelsAsync. */
data class WinUIXamlVisualImage(val width: Int, val height: Int, val pixels: ByteArray)
data class WinUIXamlVisualSnapshot(val nodes: List<WinUIXamlVisualNode>, val properties: List<WinUIXamlVisualProperty>,
    val image: WinUIXamlVisualImage? = null)
