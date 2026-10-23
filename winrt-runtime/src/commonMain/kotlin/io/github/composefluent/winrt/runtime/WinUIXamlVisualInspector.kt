package io.github.composefluent.winrt.runtime

/** SDK calls live in the compiler-generated typed adapter. Runtime ownership,
 * bounded traversal and UI dispatch mirror CsWinRT's generated accessor/runtime split.
 * The same contract is used by JVM and mingwX64 development transports. */
class WinUIXamlVisualInspector(
    private val visualRoot: (Any) -> Any?,
    private val children: (Any) -> List<Any>,
    private val describe: (Any, Any) -> WinUIXamlVisualNode,
    private val properties: (Any) -> List<WinUIXamlVisualProperty>,
    private val capture: (Any, (Result<WinUIXamlVisualImage>) -> Unit) -> Unit,
    private val preview: (Any, WinUIXamlInspectionRequest) -> Unit,
) {
    fun inspect(owner: Any, request: WinUIXamlInspectionRequest, complete: (Result<WinUIXamlVisualSnapshot>) -> Unit) {
        val prepared = runCatching {
            if (request.previewMarkup.isNotEmpty()) {
                require(request.previewMarkup.length <= 128 * 1024 && request.width in 64..4096 && request.height in 64..4096 &&
                    request.theme in listOf("Default", "Light", "Dark")) { "Invalid XAML preview parameters." }
                preview(owner, request)
            }
            val root = requireNotNull(visualRoot(owner)) { "This component has no visual content. Select a Window or page." }
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
            WinUIXamlVisualSnapshot(nodes, properties(values).take(64).map { it.copy(value = it.value.take(4096)) }, image)
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
    require(count in 0..768 * 768 * 4)
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
