package io.github.composefluent.winrt.runtime

/** Development protocol, separate from XAMLC's compilation/declaration protocol. */
object WinUIXamlHotReloadProtocol {
    const val VERSION = 18
    const val MAX_IMAGE_DIMENSION = 4096
    const val MAX_ARTBOARD_DIMENSION = 16384
    const val MAX_VISUAL_NODES = 65536
    const val VISUAL_PAGE_SIZE = 2048
    const val MAX_VISUAL_DEPTH = 128
    const val PREVIEW_POPUP_OPEN_KEY = "__KotlinWinRT_DesignPopupOpen_86220cb49a98480ca48340ca11d02704"
    const val PREVIEW_COMPONENT_KEY = "__KotlinWinRT_DesignComponent_86220cb49a98480ca48340ca11d02704"
    const val PREVIEW_CONNECTION_KEY = "__KotlinWinRT_DesignConnection_86220cb49a98480ca48340ca11d02704"
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

/** Visual paths use the SDK visual tree, including control-template children.
 * Without readTree, report root bounds and follow only the selected property path;
 * the caller retains its last complete tree until explicitly requesting a new one. */
data class WinUIXamlInspectionRequest(val className: String, val resourcePath: String, val instance: Int = 0,
    val selectedPath: List<Int> = emptyList(), val capture: Boolean = true,
    val previewMarkup: String = "", val width: Int = 800, val height: Int = 600, val theme: String = "Default", val renderScale: Double = 1.0, val animation: WinUIXamlPreviewAnimation? = null, val readProperties: Boolean = true, val readTree: Boolean = true,
    val treeToken: Long = 0, val treeOffset: Int = 0, val captureBounds: WinUIXamlVisualBounds? = null)
/** Preview-only SDK clock command. An instance path is tied to one inspected tree
 * and source identity; a root namescope owner remains available for ordinary resources. */
data class WinUIXamlPreviewAnimation(val owner: String, val key: String, val positionMillis: Int = 0, val action: String = "Seek",
    val instancePath: List<Int>? = null, val sourceId: String = "", val instanceTreeToken: Long = 0) {
    fun validate() {
        require(if (instancePath == null) sourceId.isEmpty() && instanceTreeToken == 0L else
            owner.isEmpty() && sourceId.isNotEmpty() && sourceId.length <= 4096 && instanceTreeToken > 0 &&
                instancePath.size <= WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH &&
                instancePath.all { it in 0..WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES }) { "Invalid storyboard instance." }
        require(owner.length <= 256 && key.isNotEmpty() && key.length <= 256 && positionMillis in 0..86_400_000 &&
            action in listOf("Seek", "Play", "Stop")) { "Invalid storyboard preview command." }
    }
}
data class WinUIXamlVisualBounds(val x: Double, val y: Double, val width: Double, val height: Double)
data class WinUIXamlVisualNode(val path: List<Int>, val typeName: String, val name: String, val bounds: WinUIXamlVisualBounds,
    val templateOwner: List<Int>? = null, val templateProperty: String = "", val sourceId: String = "", val layer: Int = 0)
data class WinUIXamlVisualProperty(val name: String, val value: String)
/** BGRA8 premultiplied pixels, as returned by WinUI RenderTargetBitmap.GetPixelsAsync. */
data class WinUIXamlVisualImage(val width: Int, val height: Int, val pixels: ByteArray, val bounds: WinUIXamlVisualBounds? = null, val captureBackend: String = "Xaml")
data class WinUIXamlPreviewClock(val positionMillis: Int, val playing: Boolean)
data class WinUIXamlVisualSnapshot(val nodes: List<WinUIXamlVisualNode>, val properties: List<WinUIXamlVisualProperty>,
    val image: WinUIXamlVisualImage? = null, val clock: WinUIXamlPreviewClock? = null, val treeToken: Long = 0, val nextOffset: Int = 0, val totalNodes: Int = nodes.size)

/** Application.RequestedTheme must be chosen during construction, before OnLaunched.
 * The generated SDK adaptation maps this option to its framework's enum. */
fun winUIXamlPreviewStartupTheme(): String =
    if (!isWinUIXamlDevelopmentBuild()) "Default" else platformGetWindowsEnvironmentVariable(WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT)
        ?.takeIf { it == "Light" || it == "Dark" } ?: "Default"

/** Shared designer-host flag after the platform launcher has normalized activation
 * arguments. SDK adapters use it to isolate interactive design scheduling from
 * the user's live application dispatcher. */
fun isWinUIXamlPreviewHost(): Boolean =
    isWinUIXamlDevelopmentBuild() && platformGetWindowsEnvironmentVariable(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT) == "1"

/** Artifact-owned permission, never derived from launch arguments, properties or environment.
 * CsWinRT has no IDE transport. Its compile-time DEBUG boundary is mirrored by the
 * Native binary flag; JVM uses the runtime JAR's own development-only manifest. */
expect fun isWinUIXamlDevelopmentBuild(): Boolean
