package io.github.composefluent.winrt.runtime

/** Development protocol, separate from XAMLC's compilation/declaration protocol. */
object WinUIXamlHotReloadProtocol {
    const val VERSION = 13
    const val MAX_IMAGE_DIMENSION = 4096
    const val PREVIEW_SOURCE_KEY = "__KotlinWinRT_DesignSource_86220cb49a98480ca48340ca11d02704"
    const val MAGIC = 0x4B585248
    const val SESSION_DIRECTORY = "KOTLIN_WINRT_HOT_RELOAD_DIRECTORY"
    const val PREVIEW_ARGUMENT = "--kotlin-winrt-xaml-preview"
    const val PREVIEW_ENVIRONMENT = "KOTLIN_WINRT_XAML_PREVIEW"
    const val PREVIEW_THEME_ENVIRONMENT = "KOTLIN_WINRT_XAML_PREVIEW_THEME"
    const val PREVIEW_THEME_ARGUMENT = "--kotlin-winrt-xaml-preview-theme="
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
    val path: List<WinUIXamlHotReloadStep> = emptyList(), val reset: Boolean = false)
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
data class WinUIXamlHotReloadEvent(val element: String, val event: String, val expectedHandler: String, val handler: String)

data class WinUIXamlHotReloadPatch(val className: String, val resourcePath: String, val expectedHash: String,
    val sourceHash: String, val version: Long, val changes: List<WinUIXamlHotReloadChange>,
    val resources: List<WinUIXamlHotReloadResources> = emptyList(),
    val reads: List<WinUIXamlHotReloadRead> = emptyList(),
    val children: List<WinUIXamlHotReloadChildren> = emptyList(),
    val events: List<WinUIXamlHotReloadEvent> = emptyList())
data class WinUIXamlHotReloadRoot(val className: String, val resourcePath: String, val sourceHash: String,
    val version: Long, val elements: List<String>, val instances: Int = 1,
    val stateKeys: List<String> = emptyList())
data class WinUIXamlHotReloadValue(val element: String, val property: String, val value: String,
    val path: List<WinUIXamlHotReloadStep> = emptyList())
data class WinUIXamlHotReloadReply(val status: Int, val message: String, val roots: List<WinUIXamlHotReloadRoot> = emptyList(),
    val values: List<WinUIXamlHotReloadValue> = emptyList(), val inspection: WinUIXamlVisualSnapshot? = null,
    val savedState: List<WinUIXamlSavedElement>? = null)

/** Visual paths use the SDK visual tree, including control-template children. */
data class WinUIXamlInspectionRequest(val className: String, val resourcePath: String, val instance: Int = 0,
    val selectedPath: List<Int> = emptyList(), val capture: Boolean = true,
    val previewMarkup: String = "", val width: Int = 800, val height: Int = 600, val theme: String = "Default", val renderScale: Double = 1.0, val animation: WinUIXamlPreviewAnimation? = null, val readProperties: Boolean = true)
/** Preview-only SDK clock command. Resource owner is an exact name in the root namescope. */
data class WinUIXamlPreviewAnimation(val owner: String, val key: String, val positionMillis: Int = 0, val action: String = "Seek") {
    fun validate() {
        require(owner.length <= 256 && key.isNotEmpty() && key.length <= 256 && positionMillis in 0..86_400_000 &&
            action in listOf("Seek", "Play", "Stop")) { "Invalid storyboard preview command." }
    }
}
data class WinUIXamlVisualBounds(val x: Double, val y: Double, val width: Double, val height: Double)
data class WinUIXamlVisualNode(val path: List<Int>, val typeName: String, val name: String, val bounds: WinUIXamlVisualBounds,
    val templateOwner: List<Int>? = null, val templateProperty: String = "", val sourceId: String = "")
data class WinUIXamlVisualProperty(val name: String, val value: String)
/** BGRA8 premultiplied pixels, as returned by WinUI RenderTargetBitmap.GetPixelsAsync. */
data class WinUIXamlVisualImage(val width: Int, val height: Int, val pixels: ByteArray)
data class WinUIXamlPreviewClock(val positionMillis: Int, val playing: Boolean)
data class WinUIXamlVisualSnapshot(val nodes: List<WinUIXamlVisualNode>, val properties: List<WinUIXamlVisualProperty>,
    val image: WinUIXamlVisualImage? = null, val clock: WinUIXamlPreviewClock? = null)

/** Application.RequestedTheme must be chosen during construction, before OnLaunched.
 * The generated SDK adaptation maps this option to its framework's enum. */
fun winUIXamlPreviewStartupTheme(): String =
    platformGetWindowsEnvironmentVariable(WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT)
        ?.takeIf { it == "Light" || it == "Dark" } ?: "Default"

/** Shared designer-host flag after the platform launcher has normalized activation
 * arguments. SDK adapters use it to isolate interactive design scheduling from
 * the user's live application dispatcher. */
fun isWinUIXamlPreviewHost(): Boolean =
    platformGetWindowsEnvironmentVariable(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT) == "1"
