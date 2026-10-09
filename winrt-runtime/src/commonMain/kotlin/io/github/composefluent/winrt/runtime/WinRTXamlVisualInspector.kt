package io.github.composefluent.winrt.runtime

/** SDK calls live in the compiler-generated typed adapter. Runtime ownership,
 * bounded traversal and UI dispatch mirror CsWinRT's generated accessor/runtime split.
 * The same contract is usable by JVM and mingwX64; only the current transport is JVM. */
class WinRTXamlVisualInspector(
    private val visualRoot: (Any) -> Any?,
    private val children: (Any) -> List<Any>,
    private val describe: (Any, Any) -> WinRTXamlVisualNode,
    private val properties: (Any) -> List<WinRTXamlVisualProperty>,
    private val capture: (Any, (Result<WinRTXamlVisualImage>) -> Unit) -> Unit,
    private val preview: (Any, WinRTXamlInspectionRequest) -> Unit,
) {
    fun inspect(owner: Any, request: WinRTXamlInspectionRequest, complete: (Result<WinRTXamlVisualSnapshot>) -> Unit) {
        val prepared = runCatching {
            if (request.previewMarkup.isNotEmpty()) {
                require(request.previewMarkup.length <= 128 * 1024 && request.width in 64..4096 && request.height in 64..4096 &&
                    request.theme in listOf("Default", "Light", "Dark")) { "Invalid XAML preview parameters." }
                preview(owner, request)
            }
            val root = requireNotNull(visualRoot(owner)) { "This component has no visual content. Select a Window or page." }
            root
        }
        fun snapshot(root: Any, image: WinRTXamlVisualImage? = null): Result<WinRTXamlVisualSnapshot> = runCatching {
            val nodes = mutableListOf<WinRTXamlVisualNode>()
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
            WinRTXamlVisualSnapshot(nodes, properties(values).take(64).map { it.copy(value = it.value.take(4096)) }, image)
        }
        prepared.fold({ root ->
            // RenderTargetBitmap completes on the owner's UI dispatcher after layout.
            // Read bounds and template children only then, especially for a new preview.
            if (!request.capture) complete(snapshot(root)) else try {
                capture(root) { image -> complete(image.fold({ snapshot(root, it) }, { Result.failure(it) })) }
            } catch (error: Exception) { complete(Result.failure(error)) }
        }, { complete(Result.failure(it)) })
    }
}

/** Read generated ICustomProperty accessors; inspect failures stay local to a property. */
fun winRTXamlInspectionProperties(value: Any): List<WinRTXamlVisualProperty> =
    WinUiAuthoredTypeMetadata.customProperties(value).take(64).map { member ->
        WinRTXamlVisualProperty(member.name, runCatching { member.getValue(value)?.toString().orEmpty().take(4096) }
            .getOrElse { "Unavailable: ${it.message}" })
    }

/** IBufferByteAccess uses CsWinRT's Interop/IID.g.cs identity and the robuffer.h
 * IUnknown + Buffer(byte**) ABI. Keep the QI owner alive while copying pixels;
 * both JVM and mingwX64 use the existing scoped platform/vtable contracts. */
fun readWinRTXamlVisualPixels(buffer: ComObjectReference, count: Int): ByteArray {
    require(count in 0..768 * 768 * 4)
    if (count == 0) return byteArrayOf()
    return buffer.queryInterface(IID.IBufferByteAccess).getOrThrow().use { access ->
        PlatformAbi.confinedScope().use { scope ->
            val slot = PlatformAbi.allocatePointerSlot(scope)
            ExceptionHelpers.throwExceptionForHR(ComVtableInvoker.invokeArgs(access.pointer, 3, slot), "IBufferByteAccess.Buffer")
            val bytes = PlatformAbi.readPointer(slot)
            check(!PlatformAbi.isNull(bytes)) { "The rendered pixel buffer is unavailable." }
            ByteArray(count) { PlatformAbi.readInt8(PlatformAbi.slice(bytes, it.toLong(), 1)) }
        }
    }
}
