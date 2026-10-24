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
    private fun write(body: Writer.() -> Unit): ByteArray {
        val payload = Writer().apply(body).bytes()
        require(payload.size <= MAX_FRAME) { "Hot Reload frame exceeds its protocol limit." }
        return Writer().apply {
            writeInt(WinUIXamlHotReloadProtocol.MAGIC)
            writeInt(WinUIXamlHotReloadProtocol.VERSION)
            writeInt(payload.size)
            write(payload)
        }.bytes()
    }
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
        }
    }
    internal data class Request(val token: String, val patch: WinUIXamlHotReloadPatch? = null, val inspection: WinUIXamlInspectionRequest? = null)
    fun writeInspectionRequest(token: String, request: WinUIXamlInspectionRequest) = write() {
        string(token); writeByte(2); string(request.className); string(request.resourcePath); writeInt(request.instance)
        require(request.selectedPath.size <= 64 && request.selectedPath.all { it in 0..2048 })
        writeInt(request.selectedPath.size); request.selectedPath.forEach { writeInt(it) }; writeBoolean(request.capture)
        require(request.renderScale.isFinite() && request.renderScale in 0.125..32.0)
        string(request.previewMarkup); writeInt(request.width); writeInt(request.height); string(request.theme); writeDouble(request.renderScale)
        writeBoolean(request.animation != null)
        request.animation?.let { it.validate(); string(it.owner); string(it.key); writeInt(it.positionMillis); string(it.action) }
    }
    fun readRequest(frame: ByteArray): Pair<String, WinUIXamlHotReloadPatch?> = readCommand(frame).let {
        require(it.inspection == null) { "Use the visual inspection command reader." }; it.token to it.patch
    }
    internal fun readCommand(frame: ByteArray): Request = read(frame) {
        val token = string()
        val operation = readUnsignedByte()
        if (operation == 2) {
            val name = string(); val resource = string(); val instance = count(1024)
            val path = List(count(64)) { count(2048) }; val capture = readBoolean()
            val markup = string(); val width = count(4096); val height = count(4096); val theme = string(); val scale = readDouble()
            require(scale.isFinite() && scale in 0.125..32.0)
            val animation = if (readBoolean()) WinUIXamlPreviewAnimation(string(), string(), count(86_400_000), string()).also { it.validate() } else null
            return@read Request(token, inspection = WinUIXamlInspectionRequest(name, resource, instance, path, capture, markup, width, height, theme, scale, animation))
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
            WinUIXamlHotReloadPatch(name, path, expected, hash, version, changes, resources, reads, children)
        }
        Request(token, patch)
    }
    fun writeReply(reply: WinUIXamlHotReloadReply) = write() {
        writeInt(reply.status); string(reply.message); writeInt(reply.roots.size)
        reply.roots.forEach { string(it.className); string(it.resourcePath); string(it.sourceHash); writeLong(it.version)
            writeInt(it.instances)
            writeInt(it.elements.size); it.elements.forEach { name -> string(name) } }
        writeInt(reply.values.size); reply.values.forEach { string(it.element); string(it.property); string(it.value); path(it.path) }
        writeBoolean(reply.inspection != null)
        reply.inspection?.let { view ->
            require(view.nodes.size <= 2048 && view.properties.size <= 64)
            writeInt(view.nodes.size)
            view.nodes.forEach { node ->
                require(node.path.size <= 64); writeInt(node.path.size); node.path.forEach { writeInt(it) }
                string(node.typeName); string(node.name)
                with(node.bounds) { writeDouble(x); writeDouble(y); writeDouble(width); writeDouble(height) }
            }
            writeInt(view.properties.size); view.properties.forEach { string(it.name); string(it.value) }
            writeBoolean(view.image != null)
            view.image?.let { image ->
                require(image.width in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.height in 1..WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION && image.pixels.size == 4 * image.width * image.height)
                writeInt(image.width); writeInt(image.height); write(image.pixels)
            }
            writeBoolean(view.clock != null)
            view.clock?.let { require(it.positionMillis in 0..86_400_000); writeInt(it.positionMillis); writeBoolean(it.playing) }
        }
    }
    fun readReply(frame: ByteArray): WinUIXamlHotReloadReply = read(frame) {
        val status = readInt(); val message = string()
        val roots = List(count(1024)) { val name = string(); val path = string(); val hash = string(); val version = readLong()
            val instances = count(1024)
            WinUIXamlHotReloadRoot(name, path, hash, version, List(count(4096)) { string() }, instances) }
        val values = List(count(512)) { WinUIXamlHotReloadValue(string(), string(), string(), path()) }
        val inspection = if (!readBoolean()) null else {
            val nodes = List(count(2048)) {
                val path = List(count(64)) { count(2048) }
                val type = string(); val name = string()
                WinUIXamlVisualNode(path, type, name, WinUIXamlVisualBounds(readDouble(), readDouble(), readDouble(), readDouble()))
            }
            val properties = List(count(64)) { WinUIXamlVisualProperty(string(), string()) }
            val image = if (!readBoolean()) null else {
                val width = count(WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION); val height = count(WinUIXamlHotReloadProtocol.MAX_IMAGE_DIMENSION); require(width > 0 && height > 0)
                WinUIXamlVisualImage(width, height, ByteArray(4 * width * height).also(::readFully))
            }
            WinUIXamlVisualSnapshot(nodes, properties, image, if (readBoolean()) WinUIXamlPreviewClock(count(86_400_000), readBoolean()) else null)
        }
        WinUIXamlHotReloadReply(status, message, roots, values, inspection)
    }

    private class Writer {
        private var buffer = ByteArray(256)
        private var size = 0
        private fun reserve(count: Int) {
            require(count >= 0 && size <= MAX_FRAME + 12 - count) { "Hot Reload frame exceeds its protocol limit." }
            if (size + count > buffer.size) buffer = buffer.copyOf(maxOf(size + count, minOf(buffer.size * 2, MAX_FRAME + 12)))
        }
        fun write(bytes: ByteArray) { reserve(bytes.size); bytes.copyInto(buffer, size); size += bytes.size }
        fun writeByte(value: Int) { reserve(1); buffer[size++] = value.toByte() }
        fun writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)
        fun writeInt(value: Int) { for (shift in 24 downTo 0 step 8) writeByte(value ushr shift) }
        fun writeLong(value: Long) { for (shift in 56 downTo 0 step 8) writeByte((value ushr shift).toInt()) }
        fun writeDouble(value: Double) = writeLong(value.toBits())
        fun bytes() = buffer.copyOf(size)
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
