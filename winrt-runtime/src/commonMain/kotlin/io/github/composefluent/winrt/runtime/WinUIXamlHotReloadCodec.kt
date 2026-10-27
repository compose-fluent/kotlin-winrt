package io.github.composefluent.winrt.runtime



/** Bounded framing shared verbatim with the IDE; the authentication token is never a display DTO. */
internal object WinUIXamlHotReloadCodec {
    const val MAX_FRAME = 80 * 1024 * 1024
    private const val MAX_STRING = 128 * 1024
    private fun Writer.string(value: String) {
        val bytes = value.encodeToByteArray()
        require(bytes.size <= MAX_STRING) { "Hot Reload string exceeds its protocol limit." }
        writeInt(bytes.size); write(bytes)
    }
    private fun Reader.string(): String {
        val length = readInt(); require(length in 0..MAX_STRING) { "Invalid Hot Reload string length." }
        return ByteArray(length).also(::readFully).decodeToString()
    }
    private fun Reader.count(max: Int): Int = readInt().also { require(it in 0..max) { "Invalid Hot Reload item count." } }
    private fun Writer.path(steps: List<WinUIXamlHotReloadStep>) {
        require(steps.size <= 64) { "Hot Reload object path is too long." }
        writeInt(steps.size)
        steps.forEach { step -> when (step) {
            is WinUIXamlHotReloadStep.Property -> { writeByte(0); string(step.name) }
            is WinUIXamlHotReloadStep.Key -> { writeByte(1); string(step.name) }
            is WinUIXamlHotReloadStep.Index -> { writeByte(2); writeInt(step.index); writeInt(step.expectedSize) }
        } }
    }
    private fun Reader.path(): List<WinUIXamlHotReloadStep> = List(count(64)) {
        when (readUnsignedByte()) {
            0 -> WinUIXamlHotReloadStep.Property(string())
            1 -> WinUIXamlHotReloadStep.Key(string())
            2 -> WinUIXamlHotReloadStep.Index(count(4096), count(4096))
            else -> error("Invalid Hot Reload object path step.")
        }
    }
    private fun Writer.target(target: WinUIXamlHotReloadTarget) { string(target.element); path(target.path) }
    private fun Reader.target() = WinUIXamlHotReloadTarget(string(), path())
    private fun write(body: Writer.() -> Unit): ByteArray = Writer().apply {
        // Reserve the wire header in the same buffer as the payload. A designer
        // bitmap can be tens of MiB; materializing and then wrapping a separate
        // payload copied every pixel twice more on both JVM and Native.
        writeInt(WinUIXamlHotReloadProtocol.MAGIC)
        writeInt(WinUIXamlHotReloadProtocol.VERSION)
        writeInt(0)
        body()
    }.frame()
    fun frameSize(header: ByteArray): Int = Reader(header).run {
        require(readInt() == WinUIXamlHotReloadProtocol.MAGIC && readInt() == WinUIXamlHotReloadProtocol.VERSION) { "Unsupported Hot Reload protocol." }
        count(MAX_FRAME)
    }
    private fun <T> read(frame: ByteArray, body: Reader.() -> T): T {
        require(frame.size >= 12) { "Truncated Hot Reload header." }
        val length = frameSize(frame.copyOfRange(0, 12))
        require(frame.size == length + 12) { "Invalid Hot Reload frame length." }
        val reader = Reader(frame, 12)
        return body(reader).also { require(reader.available() == 0) { "Unexpected Hot Reload payload." } }
    }
    fun writeRequest(token: String, patch: WinUIXamlHotReloadPatch?) = write() {
        string(token); writeBoolean(patch != null)
        patch?.let { string(it.className); string(it.resourcePath); string(it.expectedHash); string(it.sourceHash); writeLong(it.version)
            writeInt(it.changes.size); it.changes.forEach { change -> string(change.element); string(change.property); string(change.literal); path(change.path); writeBoolean(change.reset) }
            writeInt(it.resources.size); it.resources.forEach { resources ->
                target(resources.target); string(resources.xaml)
                writeInt(resources.expectedKeys.size); resources.expectedKeys.forEach { key -> string(key) }
                writeInt(resources.references.size); resources.references.forEach { reference ->
                    target(reference.target); string(reference.property); string(reference.key)
                }
            }
            writeInt(it.reads.size); it.reads.forEach { read -> target(read.target); string(read.property) }
            writeInt(it.children.size); it.children.forEach { children ->
                target(children.target); writeInt(children.expectedSize); writeInt(children.items.size)
                children.items.forEach { item -> when (item) {
                    is WinUIXamlHotReloadItem.Existing -> { writeByte(0); writeInt(item.index) }
                    is WinUIXamlHotReloadItem.Markup -> { writeByte(1); string(item.xaml) }
                } }
            }
            writeInt(it.events.size); it.events.forEach { event ->
                string(event.element); string(event.event); string(event.expectedHandler); string(event.handler)
            }
        }
    }
    private fun Writer.savedState(state: List<WinUIXamlSavedElement>) {
        validateWinUIXamlSavedState(state)
        writeInt(state.size)
        state.forEach { element ->
            string(element.name); string(element.kind); writeInt(element.values.size)
            element.values.forEach { string(it.name); string(it.value) }
        }
    }
    private fun Reader.savedState(): List<WinUIXamlSavedElement> = List(count(4096)) {
        WinUIXamlSavedElement(string(), string(), List(count(128)) { WinUIXamlVisualProperty(string(), string()) })
    }.also(::validateWinUIXamlSavedState)
    internal data class Request(val token: String, val patch: WinUIXamlHotReloadPatch? = null,
        val inspection: WinUIXamlInspectionRequest? = null, val state: WinUIXamlStateRequest? = null)
    fun writeStateRequest(token: String, request: WinUIXamlStateRequest) = write {
        require(request.instance in 0..1024)
        string(token); writeByte(3); string(request.className); string(request.resourcePath); writeInt(request.instance)
        require(request.componentKey.length <= 4096)
        string(request.componentKey)
        writeBoolean(request.restore != null); request.restore?.let { savedState(it) }
    }
    fun writeInspectionRequest(token: String, request: WinUIXamlInspectionRequest) = write() {
        string(token); writeByte(2); string(request.className); string(request.resourcePath); writeInt(request.instance)
        require(request.selectedPath.size <= WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH && request.selectedPath.all { it in 0..WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES })
        writeInt(request.selectedPath.size); request.selectedPath.forEach { writeInt(it) }; writeBoolean(request.capture)
        require(request.renderScale.isFinite() && request.renderScale in 0.125..32.0)
        string(request.previewMarkup); writeInt(request.width); writeInt(request.height); string(request.theme); writeDouble(request.renderScale)
        writeBoolean(request.animation != null)
        request.animation?.let {
            it.validate(); string(it.owner); string(it.key); writeInt(it.positionMillis); string(it.action)
            writeBoolean(it.instancePath != null)
            it.instancePath?.let { path -> writeInt(path.size); path.forEach { writeInt(it) } }
            string(it.sourceId); writeLong(it.instanceTreeToken)
        }
        writeBoolean(request.readProperties); writeBoolean(request.readTree)
        require(request.treeToken >= 0 && request.treeOffset in 0..WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES)
        writeLong(request.treeToken); writeInt(request.treeOffset)
        writeBoolean(request.captureBounds != null); request.captureBounds?.let { bounds(it) }
    }
    fun readRequest(frame: ByteArray): Pair<String, WinUIXamlHotReloadPatch?> = readCommand(frame).let {
        require(it.inspection == null && it.state == null) { "Use the visual inspection command reader." }; it.token to it.patch
    }
    internal fun readCommand(frame: ByteArray): Request = read(frame) {
        val token = string()
        val operation = readUnsignedByte()
        if (operation == 3) {
            val name = string(); val path = string(); val instance = count(1024)
            val key = string().also { require(it.length <= 4096) }
            return@read Request(token, state = WinUIXamlStateRequest(name, path, instance, if (readBoolean()) savedState() else null, key))
        }
        if (operation == 2) {
            val name = string(); val resource = string(); val instance = count(1024)
            val path = List(count(WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH)) { count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) }; val capture = readBoolean()
            val markup = string(); val width = count(WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION); val height = count(WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION); val theme = string(); val scale = readDouble()
            require(scale.isFinite() && scale in 0.125..32.0)
            val animation = if (readBoolean()) WinUIXamlPreviewAnimation(string(), string(), count(86_400_000), string(),
                if (readBoolean()) List(count(WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH)) { count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) } else null,
                string(), readLong()).also { it.validate() } else null
            return@read Request(token, inspection = WinUIXamlInspectionRequest(name, resource, instance, path, capture, markup, width, height, theme, scale, animation, readBoolean(), readBoolean(), readLong().also { require(it >= 0) }, count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES), if (readBoolean()) bounds() else null))
        }
        require(operation == 0 || operation == 1) { "Invalid development command." }
        val patch = if (operation == 0) null else {
            val name = string(); val path = string(); val expected = string(); val hash = string(); val version = readLong()
            val changes = List(count(512)) { WinUIXamlHotReloadChange(string(), string(), string(), path(), readBoolean()) }
            val resources = List(count(64)) {
                val target = target(); val xaml = string(); val keys = List(count(4096)) { string() }
                WinUIXamlHotReloadResources(target, xaml, keys, List(count(512)) {
                    WinUIXamlHotReloadResourceReference(target(), string(), string())
                })
            }
            val reads = List(count(512)) { WinUIXamlHotReloadRead(target(), string()) }
            val children = List(count(64)) {
                val target = target(); val size = count(4096)
                WinUIXamlHotReloadChildren(target, size, List(count(512)) {
                    when (readUnsignedByte()) {
                        0 -> WinUIXamlHotReloadItem.Existing(count(4096))
                        1 -> WinUIXamlHotReloadItem.Markup(string())
                        else -> error("Invalid Hot Reload child operation.")
                    }
                })
            }
            val events = List(count(512)) { WinUIXamlHotReloadEvent(string(), string(), string(), string()) }
            WinUIXamlHotReloadPatch(name, path, expected, hash, version, changes, resources, reads, children, events)
        }
        Request(token, patch)
    }
    fun writeReply(reply: WinUIXamlHotReloadReply) = write() {
        writeInt(reply.status); string(reply.message); writeInt(reply.roots.size)
        reply.roots.forEach { string(it.className); string(it.resourcePath); string(it.sourceHash); writeLong(it.version)
            writeInt(it.instances)
            writeInt(it.elements.size); it.elements.forEach { name -> string(name) }
            require(it.stateKeys.isEmpty() || it.stateKeys.size == it.instances)
            writeInt(it.stateKeys.size); it.stateKeys.forEach { key -> require(key.length <= 4096); string(key) } }
        writeInt(reply.values.size); reply.values.forEach { string(it.element); string(it.property); string(it.value); path(it.path) }
        writeBoolean(reply.inspection != null)
        reply.inspection?.let { view ->
            require(view.nodes.size <= WinUIXamlHotReloadProtocol.VISUAL_PAGE_SIZE && view.properties.size <= 64)
            writeInt(view.nodes.size)
            view.nodes.forEach { node ->
                require(node.path.size <= WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH); writeInt(node.path.size); node.path.forEach { writeInt(it) }
                string(node.typeName); string(node.name)
                with(node.bounds) { writeDouble(x); writeDouble(y); writeDouble(width); writeDouble(height) }
                writeBoolean(node.templateOwner != null)
                node.templateOwner?.let { owner ->
                    require(owner.size < node.path.size && node.path.take(owner.size) == owner && node.templateProperty in listOf("ContentTemplate", "ItemTemplate"))
                    writeInt(owner.size); owner.forEach { writeInt(it) }; string(node.templateProperty)
                }
                require(node.sourceId.length <= 4096); string(node.sourceId); require(node.layer in 0..64); writeInt(node.layer)
            }
            writeInt(view.properties.size); view.properties.forEach { string(it.name); string(it.value) }
            writeBoolean(view.image != null)
            view.image?.let { image ->
                require(image.width in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.height in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.pixels.size == 4 * image.width * image.height)
                writeInt(image.width); writeInt(image.height); writeBoolean(image.bounds != null); image.bounds?.let { bounds(it) }; write(image.pixels)
            }
            writeBoolean(view.clock != null)
            view.clock?.let { require(it.positionMillis in 0..86_400_000); writeInt(it.positionMillis); writeBoolean(it.playing) }
            require(view.treeToken >= 0 && view.totalNodes in view.nodes.size..WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES && view.nextOffset in 0..view.totalNodes)
            writeLong(view.treeToken); writeInt(view.nextOffset); writeInt(view.totalNodes)
        }
        writeBoolean(reply.savedState != null); reply.savedState?.let { savedState(it) }
    }
    fun readReply(frame: ByteArray): WinUIXamlHotReloadReply = read(frame) {
        val status = readInt(); val message = string()
        val roots = List(count(1024)) { val name = string(); val path = string(); val hash = string(); val version = readLong()
            val instances = count(1024)
            val elements = List(count(4096)) { string() }
            val keys = List(count(1024)) { string().also { require(it.length <= 4096) } }
            require(keys.isEmpty() || keys.size == instances)
            WinUIXamlHotReloadRoot(name, path, hash, version, elements, instances, keys) }
        val values = List(count(512)) { WinUIXamlHotReloadValue(string(), string(), string(), path()) }
        val inspection = if (!readBoolean()) null else {
            val nodes = List(count(WinUIXamlHotReloadProtocol.VISUAL_PAGE_SIZE)) {
                val path = List(count(WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH)) { count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) }
                val type = string(); val name = string()
                val bounds = WinUIXamlVisualBounds(readDouble(), readDouble(), readDouble(), readDouble())
                val owner = if (readBoolean()) List(count(WinUIXamlHotReloadProtocol.MAX_VISUAL_DEPTH)) { count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES) } else null
                val property = if (owner != null) string() else ""
                require(owner == null || owner.size < path.size && path.take(owner.size) == owner && property in listOf("ContentTemplate", "ItemTemplate"))
                WinUIXamlVisualNode(path, type, name, bounds, owner, property, string().also { require(it.length <= 4096) }, count(64))
            }
            val properties = List(count(64)) { WinUIXamlVisualProperty(string(), string()) }
            val image = if (!readBoolean()) null else {
                val width = count(WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION); val height = count(WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION); require(width > 0 && height > 0)
                val region = if (readBoolean()) bounds() else null
                WinUIXamlVisualImage(width, height, ByteArray(4 * width * height).also(::readFully), region)
            }
            WinUIXamlVisualSnapshot(nodes, properties, image, if (readBoolean()) WinUIXamlPreviewClock(count(86_400_000), readBoolean()) else null, readLong().also { require(it >= 0) }, count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES), count(WinUIXamlHotReloadProtocol.MAX_VISUAL_NODES))
        }
        WinUIXamlHotReloadReply(status, message, roots, values, inspection, if (readBoolean()) savedState() else null)
    }

    private fun Writer.bounds(value: WinUIXamlVisualBounds) {
        validateWinUIXamlCaptureBounds(value)
        writeDouble(value.x); writeDouble(value.y); writeDouble(value.width); writeDouble(value.height)
    }
    private fun Reader.bounds(): WinUIXamlVisualBounds = WinUIXamlVisualBounds(readDouble(), readDouble(), readDouble(), readDouble()).also(::validateWinUIXamlCaptureBounds)

    private class Writer {
        private var buffer = ByteArray(256)
        private var size = 0
        private fun reserve(count: Int) {
            require(count >= 0 && size <= MAX_FRAME + 12 - count) { "Hot Reload frame exceeds its protocol limit." }
            if (size + count > buffer.size) {
                // Leave room for metadata following a large pixel block instead
                // of immediately doubling/copying it for the final clock flag.
                val capacity = minOf(MAX_FRAME + 12, maxOf(buffer.size * 2, size + count + 256))
                buffer = buffer.copyOf(capacity)
            }
        }
        fun write(bytes: ByteArray) { reserve(bytes.size); bytes.copyInto(buffer, size); size += bytes.size }
        fun writeByte(value: Int) { reserve(1); buffer[size++] = value.toByte() }
        fun writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)
        fun writeInt(value: Int) { for (shift in 24 downTo 0 step 8) writeByte(value ushr shift) }
        fun writeLong(value: Long) { for (shift in 56 downTo 0 step 8) writeByte((value ushr shift).toInt()) }
        fun writeDouble(value: Double) = writeLong(value.toBits())
        fun frame(): ByteArray {
            val payloadSize = size - 12
            require(payloadSize in 0..MAX_FRAME) { "Hot Reload frame exceeds its protocol limit." }
            for (index in 0..3) buffer[8 + index] = (payloadSize ushr (24 - index * 8)).toByte()
            return buffer.copyOf(size)
        }
    }
    private class Reader(private val buffer: ByteArray, private var offset: Int = 0) {
        fun available() = buffer.size - offset
        fun readUnsignedByte(): Int { require(available() > 0) { "Truncated Hot Reload payload." }; return buffer[offset++].toInt() and 255 }
        fun readBoolean() = readUnsignedByte() != 0
        fun readInt(): Int { var result = 0; repeat(4) { result = (result shl 8) or readUnsignedByte() }; return result }
        fun readLong(): Long { var result = 0L; repeat(8) { result = (result shl 8) or readUnsignedByte().toLong() }; return result }
        fun readDouble() = Double.fromBits(readLong())
        fun readFully(bytes: ByteArray) {
            require(bytes.size <= available()) { "Truncated Hot Reload payload." }
            buffer.copyInto(bytes, 0, offset, offset + bytes.size); offset += bytes.size
        }
    }
}
