package io.github.composefluent.winrt.runtime

/** SDK calls live in the compiler-generated typed adapter. Runtime ownership,
 * bounded traversal and UI dispatch mirror CsWinRT's generated accessor/runtime split.
 * The same contract is used by JVM and mingwX64 development transports. */
class WinUIXamlVisualInspector(
    private val visualRoot: (Any) -> Any?,
    private val children: (Any) -> List<Any>,
    private val describe: (Any, Any) -> WinUIXamlVisualNode,
    private val properties: (Any) -> List<WinUIXamlVisualProperty>,
    private val capture: (Any, Double, (Result<WinUIXamlVisualImage>) -> Unit) -> Unit,
    private val preview: (Any, WinUIXamlInspectionRequest) -> Unit,
    private val clock: () -> WinUIXamlPreviewClock? = { null },
    private val animate: (Any, WinUIXamlPreviewAnimation) -> Unit = { _, _ -> error("Rebuild the preview host to inspect storyboards.") },
) {
    fun inspect(owner: Any, request: WinUIXamlInspectionRequest, complete: (Result<WinUIXamlVisualSnapshot>) -> Unit) {
        val prepared = runCatching {
            request.animation?.let {
                it.validate()
                require(request.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS) { "Storyboard commands require an isolated preview host." }
            }
            require(request.renderScale.isFinite() && request.renderScale in 0.125..32.0) { "Invalid capture scale." }
            if (request.previewMarkup.isNotEmpty()) {
                require(request.previewMarkup.length <= 128 * 1024 && request.width in 64..4096 && request.height in 64..4096 &&
                    request.theme in listOf("Default", "Light", "Dark")) { "Invalid XAML preview parameters." }
                preview(owner, request)
            }
            val root = requireNotNull(visualRoot(owner)) { "This component has no visual content. Select a Window or page." }
            request.animation?.let { animate(root, it) }
            root
        }
        fun snapshot(root: Any, image: WinUIXamlVisualImage? = null): Result<WinUIXamlVisualSnapshot> = runCatching {
            val nodes = mutableListOf<WinUIXamlVisualNode>()
            var selected: Any? = null
            fun visit(value: Any, path: List<Int>) {
                require(nodes.size < 2048 && path.size <= 64) { "The visual tree exceeds the inspection limit; select a smaller component." }
                nodes += describe(value, root).copy(path = path)
                if (path == request.selectedPath) selected = value
                val items = children(value)
                require(items.size <= 2048) { "The visual child collection exceeds the inspection limit." }
                items.forEachIndexed { index, child -> visit(child, path + index) }
            }
            visit(root, emptyList())
            val values = requireNotNull(selected) { "The selected visual no longer exists. Refresh the tree." }
            WinUIXamlVisualSnapshot(nodes, properties(values).take(64).map { it.copy(value = it.value.take(4096)) }, image, clock())
        }
        prepared.fold({ root ->
            // RenderTargetBitmap completes on the owner's UI dispatcher after layout.
            // Read bounds and template children only then, especially for a new preview.
            if (!request.capture) complete(snapshot(root)) else try {
                capture(root, request.renderScale) { image -> complete(image.fold({ snapshot(root, it) }, { Result.failure(it) })) }
            } catch (error: Exception) { complete(Result.failure(error)) }
        }, { complete(Result.failure(it)) })
    }
}

/** Preserve the generated type identity of a composed Kotlin control. Like CsWinRT's
 * WindowsRuntimeComWrappers, use the managed instance's exact type metadata rather
 * than treating the native base object's runtime class name as the derived type.
 * Unknown SDK wrappers retain the native runtime class name fallback in the adapter. */
fun winUIXamlInspectionTypeName(value: Any): String? = WinUIAuthoredTypeMetadata.registeredTypeName(value)

/** Read generated ICustomProperty accessors; inspect failures stay local to a property. */
fun winUIXamlInspectionProperties(value: Any): List<WinUIXamlVisualProperty> =
    WinUIAuthoredTypeMetadata.customProperties(value).take(64).map { member ->
        WinUIXamlVisualProperty(member.name, runCatching { member.getValue(value)?.toString().orEmpty().take(4096) }
            .getOrElse { "Unavailable: ${it.message}" })
    }

/** Mirrors CsWinRT WinRT.Runtime2/Windows.Storage.Streams/Extensions/WindowsRuntimeBufferExtensions.cs
 * CopyTo: copy a contiguous span with its IBufferByteAccess owner leased. Uses the robuffer.h
 * IUnknown + Buffer(byte**) ABI. Keep the QI owner alive while copying pixels;
 * both JVM and mingwX64 use the existing scoped platform/vtable contracts. */
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
fun winUIXamlCaptureSize(width: Double, height: Double, dpi: Double, renderScale: Double): Pair<Int, Int> {
    require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
    require(dpi.isFinite() && dpi > 0 && renderScale.isFinite() && renderScale in 0.125..32.0)
    val limit = WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION
    val scale = minOf(renderScale / dpi, limit / (maxOf(width, height) * dpi))
    return (width * scale).toInt().coerceIn(1, limit) to (height * scale).toInt().coerceIn(1, limit)
}
