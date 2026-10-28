@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.runtime

/** SDK-reported realized content root, its consuming object and template property. */
@InternalWinRTApi
data class WinUIXamlTemplateReference(val root: Any, val owner: Any, val property: String)

/** SDK calls live in the compiler-generated typed adapter. Runtime ownership,
 * bounded traversal and UI dispatch mirror CsWinRT's generated accessor/runtime split.
 * The same contract is used by JVM and mingwX64 development transports. */
@InternalWinRTApi
class WinUIXamlVisualInspector(
    private val visualRoot: (Any) -> Any?,
    private val children: (Any) -> List<Any>,
    private val describe: (Any, Any) -> WinUIXamlVisualNode,
    private val properties: (Any) -> List<WinUIXamlVisualProperty>,
    private val capture: (Any, Double, (Result<WinUIXamlVisualImage>) -> Unit) -> Unit,
    private val preview: (Any, WinUIXamlInspectionRequest) -> Unit,
    private val template: (Any) -> WinUIXamlTemplateReference? = { null },
    private val sameObject: (Any, Any) -> Boolean = { first, second -> first === second },
    private val clock: () -> WinUIXamlPreviewClock? = { null },
    internal val formatValue: (Any?) -> String = { it?.toString().orEmpty() },
    private val overlays: (Any) -> List<Any> = { emptyList() },
    private val captureRegion: ((Any, Double, WinUIXamlVisualBounds?, (Result<WinUIXamlVisualImage>) -> Unit) -> Unit)? = null,
    private val animate: (Any, WinUIXamlPreviewAnimation) -> Unit = { _, _ -> error("Rebuild the preview host to inspect storyboards.") },
) {
    private data class Tree(val root: Any, val token: Long, val snapshot: WinUIXamlVisualSnapshot, val instances: Map<List<Int>, Any>)
    private var cachedTree: Tree? = null
    private var nextTreeToken = 0L
    private fun page(tree: Tree, offset: Int): WinUIXamlVisualSnapshot {
        require(offset in 0 until tree.snapshot.nodes.size && offset % WinUIXamlHotReloadProtocol.VISUAL_PAGE_SIZE == 0) { "Invalid visual tree page." }
        val end = minOf(offset + WinUIXamlHotReloadProtocol.VISUAL_PAGE_SIZE, tree.snapshot.nodes.size)
        return tree.snapshot.copy(nodes = tree.snapshot.nodes.subList(offset, end),
            image = tree.snapshot.image.takeIf { offset == 0 }, properties = if (offset == 0) tree.snapshot.properties else emptyList(),
            treeToken = tree.token, nextOffset = if (end < tree.snapshot.nodes.size) end else 0, totalNodes = tree.snapshot.nodes.size)
    }

    internal fun isSameObject(first: Any, second: Any): Boolean = first === second || sameObject(first, second)

    fun inspect(owner: Any, request: WinUIXamlInspectionRequest, complete: (Result<WinUIXamlVisualSnapshot>) -> Unit) {
        val prepared = runCatching {
            require(request.selectedPath.size <= WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH && request.selectedPath.all { it in 0..WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES }) { "Invalid visual selection path." }
            request.animation?.let {
                it.validate()
                require(request.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS) { "Storyboard commands require an isolated preview host." }
            }
            require(request.renderScale.isFinite() && request.renderScale in 0.125..32.0) { "Invalid capture scale." }
            if (request.previewMarkup.isNotEmpty()) {
                require(request.previewMarkup.length <= 128 * 1024 && request.width in 64..WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION && request.height in 64..WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION &&
                    request.theme in listOf("Default", "Light", "Dark")) { "Invalid XAML preview parameters." }
                cachedTree = null
                preview(owner, request)
            }
            val root = requireNotNull(visualRoot(owner)) { "This component has no visual content. Select a Window or page." }
            request.animation?.let { command ->
                val path = command.instancePath
                val target = if (path == null) root else {
                    val tree = requireNotNull(cachedTree?.takeIf { it.token == command.instanceTreeToken && isSameObject(it.root, root) }) {
                        "The storyboard instance expired. Select it again in the refreshed tree."
                    }
                    require(tree.snapshot.nodes.singleOrNull { it.path == path }?.sourceId == command.sourceId) { "The storyboard source changed." }
                    var value = root
                    val childPath = if (path.firstOrNull() == WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) {
                        value = requireNotNull(overlays(root).getOrNull(path.getOrNull(1) ?: -1)) { "The storyboard popup closed." }
                        path.drop(2)
                    } else path
                    for (index in childPath) value = requireNotNull(children(value).getOrNull(index)) { "The storyboard instance disappeared." }
                    // CsWinRT WindowsRuntimeObject retains native object identity. Check
                    // both provenance and identity: recycled template slots can share both a path and source.
                    require(tree.instances[path]?.let { isSameObject(it, value) } == true && describe(value, root).sourceId == command.sourceId) {
                        "The storyboard instance was replaced. Refresh and select it again."
                    }
                    value
                }
                animate(target, command)
            }
            require(request.treeToken >= 0 && request.treeOffset in 0..WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES)
            request.captureBounds?.let { validateWinUIXamlCaptureBounds(it) }
            root
        }
        if (request.treeToken != 0L) {
            complete(prepared.mapCatching { root ->
                val cached = requireNotNull(cachedTree?.takeIf { it.token == request.treeToken && isSameObject(it.root, root) }) { "The visual snapshot expired. Refresh the tree." }
                require(request.readTree && !request.capture && request.previewMarkup.isEmpty() && request.animation == null) { "Tree continuation cannot change the visual document." }
                page(cached, request.treeOffset)
            })
            return
        }
        fun snapshot(root: Any, image: WinUIXamlVisualImage? = null): Result<WinUIXamlVisualSnapshot> = runCatching {
            val nodes = mutableListOf<WinUIXamlVisualNode>()
            val instances = mutableMapOf<List<Int>, Any>()
            var selected: Any? = null
            val ancestors = mutableListOf<Pair<Any, List<Int>>>()
            fun visit(value: Any, path: List<Int>, inherited: List<Pair<WinUIXamlTemplateReference, List<Int>>>, layer: Int = 0) {
                require(nodes.size < WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES && path.size <= WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH) { "The visual tree exceeds the inspection limit; select a smaller component." }
                val realization = inherited.filter { sameObject(it.first.root, value) }
                    .map { it.second to it.first.property }.distinct().singleOrNull()
                nodes += describe(value, root).copy(path = path, templateOwner = realization?.first,
                    templateProperty = realization?.second.orEmpty(), layer = layer)
                instances[path] = value
                ancestors += value to path
                val reference = template(value)
                val ownerPath = reference?.let { item -> ancestors.lastOrNull { sameObject(it.first, item.owner) }?.second }
                val active = if (reference != null && ownerPath != null && reference.property in listOf("ContentTemplate", "ItemTemplate"))
                    inherited + (reference to ownerPath) else inherited
                if (path == request.selectedPath) selected = value
                val items = children(value)
                require(items.size < WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) { "The visual child collection exceeds the inspection limit." }
                items.forEachIndexed { index, child -> visit(child, path + index, active, layer) }
                ancestors.removeAt(ancestors.lastIndex)
            }
            if (request.readTree) {
                visit(root, emptyList(), emptyList())
                val popupRoots = overlays(root)
                require(popupRoots.size <= 64) { "Too many popup layers." }
                if (popupRoots.isNotEmpty()) {
                    nodes += WinUIXamlVisualNode(listOf(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES), "KotlinWinRT.PopupLayer", "Popups", describe(root, root).bounds)
                    popupRoots.forEachIndexed { index, popup -> visit(popup, listOf(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES, index), emptyList(), index + 1) }
                }
                requireNotNull(selected) { "The selected visual no longer exists. Refresh the tree." }
            } else {
                // The first bitmap needs only the root's DIP bounds. Keep SDK
                // template realization and full-tree traversal off that response.
                nodes += describe(root, root).copy(path = emptyList())
                if (request.readProperties) {
                    var value = root
                    val path = if (request.selectedPath.firstOrNull() == WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) {
                        value = requireNotNull(overlays(root).getOrNull(request.selectedPath.getOrNull(1) ?: -1)) { "The popup closed. Refresh the tree." }
                        request.selectedPath.drop(2)
                    } else request.selectedPath
                    for (index in path) {
                        val items = children(value)
                        require(items.size < WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) { "The visual child collection exceeds the inspection limit." }
                        value = requireNotNull(items.getOrNull(index)) { "The selected visual no longer exists. Refresh the tree." }
                    }
                    selected = value
                    if (request.selectedPath.isNotEmpty()) nodes += describe(value, root).copy(path = request.selectedPath)
                }
            }
            val result = WinUIXamlVisualSnapshot(nodes, if (request.readProperties) properties(requireNotNull(selected)).take(64)
                .map { it.copy(value = it.value.take(4096)) } else emptyList(), image, clock())
            if (!request.readTree) result else {
                val cached = Tree(root, ++nextTreeToken, result, instances)
                cachedTree = cached
                page(cached, 0)
            }
        }
        prepared.fold({ root ->
            // RenderTargetBitmap completes on the owner's UI dispatcher after layout.
            // Read bounds and template children only then, especially for a new preview.
            if (!request.capture) complete(snapshot(root)) else try {
                val done: (Result<WinUIXamlVisualImage>) -> Unit = { image -> complete(image.fold({ snapshot(root, it) }, { Result.failure(it) })) }
                if (captureRegion != null) captureRegion.invoke(root, request.renderScale, request.captureBounds, done)
                else capture(root, request.renderScale, done)
            } catch (error: Exception) { complete(Result.failure(error)) }
        }, { complete(Result.failure(it)) })
    }
}

/** Preserve the generated type identity of a composed Kotlin control. Like CsWinRT's
 * WindowsRuntimeComWrappers, use the managed instance's exact type metadata rather
 * than treating the native base object's runtime class name as the derived type.
 * Unknown SDK wrappers retain the native runtime class name fallback in the adapter. */
@InternalWinRTApi
fun winUIXamlInspectionTypeName(value: Any): String? = WinUIAuthoredTypeMetadata.registeredTypeName(value)

/** Read generated ICustomProperty accessors; inspect failures stay local to a property. */
@InternalWinRTApi
fun winUIXamlInspectionProperties(
    value: Any,
    format: (Any?) -> String = { it?.toString().orEmpty() },
): List<WinUIXamlVisualProperty> = winUIXamlInspectionProperties(value, emptyList(), format)

/** The designer adapter chooses priority names; generated ICustomProperty metadata
 * still owns lookup and inheritance. Prioritization never raises the getter budget. */
@InternalWinRTApi
fun winUIXamlInspectionProperties(
    value: Any,
    preferredNames: List<String>,
    format: (Any?) -> String = { it?.toString().orEmpty() },
): List<WinUIXamlVisualProperty> {
    require(preferredNames.size <= 64)
    val priorities = preferredNames.withIndex().associate { it.value to it.index }
    return WinUIAuthoredTypeMetadata.customProperties(value)
        .sortedBy { priorities[it.name] ?: preferredNames.size }.take(64).map { member ->
            WinUIXamlVisualProperty(member.name, runCatching { format(member.getValue(value)).take(4096) }
                .getOrElse { "Unavailable: ${it.message}" })
        }
}

/** Mirrors CsWinRT WinRT.Runtime2/Windows.Storage.Streams/Extensions/WindowsRuntimeBufferExtensions.cs
 * CopyTo: copy a contiguous span with its IBufferByteAccess owner leased. Uses the robuffer.h
 * IUnknown + Buffer(byte**) ABI. Keep the QI owner alive while copying pixels;
 * both JVM and mingwX64 use the existing scoped platform/vtable contracts. */
@InternalWinRTApi
fun readWinUIXamlVisualPixels(buffer: ComObjectReference, count: Int): ByteArray {
    require(count in 0..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION * WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION * 4)
    if (count == 0) return byteArrayOf()
    return buffer.queryInterface(IID.IBufferByteAccess).getOrThrow().use { access ->
        PlatformAbi.confinedScope().use { scope ->
            val slot = PlatformAbi.allocatePointerSlot(scope)
            ExceptionHelpers.throwExceptionForHR(access.comPtr.withCallPointer { leasedPointer -> ComVtableInvoker.invokeArgs(leasedPointer, 3, slot) }, "IBufferByteAccess.Buffer")
            val bytes = PlatformAbi.readPointer(slot)
            check(!PlatformAbi.isNull(bytes)) { "The rendered pixel buffer is unavailable." }
            PlatformAbi.readBytes(bytes, count)
        }
    }
}

/** RenderTargetBitmap takes view pixels and returns physical pixels. Keep the
 * artboard layout in DIP; increasing capture density must not reflow controls.
 * Bounds apply before allocating the bitmap and again at the protocol boundary.
 */
@InternalWinRTApi
fun winUIXamlCaptureSize(width: Double, height: Double, dpi: Double, renderScale: Double): Pair<Int, Int> {
    require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
    require(dpi.isFinite() && dpi > 0 && renderScale.isFinite() && renderScale in 0.125..32.0)
    val limit = WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION
    val scale = minOf(renderScale / dpi, limit / (maxOf(width, height) * dpi))
    return (width * scale).toInt().coerceIn(1, limit) to (height * scale).toInt().coerceIn(1, limit)
}

/** Viewport captures keep DirectX texture limits independent of logical artboard size. */
@InternalWinRTApi
fun validateWinUIXamlCaptureBounds(bounds: WinUIXamlVisualBounds) {
    require(listOf(bounds.x, bounds.y, bounds.width, bounds.height).all { it.isFinite() } &&
        bounds.x >= 0 && bounds.y >= 0 && bounds.width > 0 && bounds.height > 0 &&
        bounds.x + bounds.width <= WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION &&
        bounds.y + bounds.height <= WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION) { "Invalid capture viewport." }
}

/** Premultiplied BGRA source-over, matching RenderTargetBitmap.GetPixelsAsync.
 * Popup visuals are captured separately by the SDK adapter; no substitute renderer. */
@InternalWinRTApi
fun compositeWinUIXamlOverlay(base: WinUIXamlVisualImage, baseBounds: WinUIXamlVisualBounds,
    overlay: WinUIXamlVisualImage, bounds: WinUIXamlVisualBounds): WinUIXamlVisualImage {
    if (bounds.width <= 0 || bounds.height <= 0) return base
    val pixels = base.pixels
    val left = maxOf(0, ((bounds.x - baseBounds.x) / baseBounds.width * base.width).toInt())
    val top = maxOf(0, ((bounds.y - baseBounds.y) / baseBounds.height * base.height).toInt())
    val right = minOf(base.width, kotlin.math.ceil((bounds.x + bounds.width - baseBounds.x) / baseBounds.width * base.width).toInt())
    val bottom = minOf(base.height, kotlin.math.ceil((bounds.y + bounds.height - baseBounds.y) / baseBounds.height * base.height).toInt())
    for (y in top until bottom) for (x in left until right) {
        val dipX = baseBounds.x + (x + 0.5) * baseBounds.width / base.width
        val dipY = baseBounds.y + (y + 0.5) * baseBounds.height / base.height
        val sx = ((dipX - bounds.x) / bounds.width * overlay.width).toInt().coerceIn(0, overlay.width - 1)
        val sy = ((dipY - bounds.y) / bounds.height * overlay.height).toInt().coerceIn(0, overlay.height - 1)
        val source = (sy * overlay.width + sx) * 4
        val destination = (y * base.width + x) * 4
        val inverse = 255 - (overlay.pixels[source + 3].toInt() and 255)
        for (channel in 0..3) pixels[destination + channel] = minOf(255,
            (overlay.pixels[source + channel].toInt() and 255) + ((pixels[destination + channel].toInt() and 255) * inverse + 127) / 255).toByte()
    }
    return base
}
