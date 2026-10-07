package io.github.composefluent.winrt.runtime

import java.io.*

/** Bounded framing shared verbatim with the IDE; the authentication token is never a display DTO. */
object WinRTXamlHotReloadWire {
    private const val MAX_FRAME = 8 * 1024 * 1024
    private const val MAX_STRING = 128 * 1024
    private fun DataOutputStream.string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING) { "Hot Reload string exceeds its protocol limit." }
        writeInt(bytes.size); write(bytes)
    }
    private fun DataInputStream.string(): String {
        val length = readInt(); require(length in 0..MAX_STRING) { "Invalid Hot Reload string length." }
        return ByteArray(length).also(::readFully).toString(Charsets.UTF_8)
    }
    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) { "Invalid Hot Reload item count." } }
    private fun DataOutputStream.path(steps: List<WinRTXamlHotReloadStep>) {
        require(steps.size <= 64) { "Hot Reload object path is too long." }
        writeInt(steps.size)
        steps.forEach { step -> when (step) {
            is WinRTXamlHotReloadStep.Property -> { writeByte(0); string(step.name) }
            is WinRTXamlHotReloadStep.Key -> { writeByte(1); string(step.name) }
            is WinRTXamlHotReloadStep.Index -> { writeByte(2); writeInt(step.index); writeInt(step.expectedSize) }
        } }
    }
    private fun DataInputStream.path(): List<WinRTXamlHotReloadStep> = List(count(64)) {
        when (readUnsignedByte()) {
            0 -> WinRTXamlHotReloadStep.Property(string())
            1 -> WinRTXamlHotReloadStep.Key(string())
            2 -> WinRTXamlHotReloadStep.Index(count(4096), count(4096))
            else -> error("Invalid Hot Reload object path step.")
        }
    }
    private fun DataOutputStream.target(target: WinRTXamlHotReloadTarget) { string(target.element); path(target.path) }
    private fun DataInputStream.target() = WinRTXamlHotReloadTarget(string(), path())
    private fun write(stream: OutputStream, body: DataOutputStream.() -> Unit) {
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).use(body) }.toByteArray()
        require(bytes.size <= MAX_FRAME) { "Hot Reload frame exceeds its protocol limit." }
        DataOutputStream(stream).apply { writeInt(WinRTXamlHotReloadProtocol.MAGIC); writeInt(WinRTXamlHotReloadProtocol.VERSION)
            writeInt(bytes.size); write(bytes); flush() }
    }
    private fun <T> read(stream: InputStream, body: DataInputStream.() -> T): T {
        val input = DataInputStream(stream)
        require(input.readInt() == WinRTXamlHotReloadProtocol.MAGIC && input.readInt() == WinRTXamlHotReloadProtocol.VERSION) { "Unsupported Hot Reload protocol." }
        val bytes = ByteArray(input.count(MAX_FRAME)).also(input::readFully)
        return DataInputStream(ByteArrayInputStream(bytes)).use { bounded -> body(bounded).also {
            require(bounded.available() == 0) { "Unexpected Hot Reload payload." }
        } }
    }
    fun writeRequest(stream: OutputStream, token: String, patch: WinRTXamlHotReloadPatch?) = write(stream) {
        string(token); writeBoolean(patch != null)
        patch?.let { string(it.className); string(it.resourcePath); string(it.expectedHash); string(it.sourceHash); writeLong(it.version)
            writeInt(it.changes.size); it.changes.forEach { change -> string(change.element); string(change.property); string(change.literal); path(change.path) }
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
                    is WinRTXamlHotReloadItem.Existing -> { writeByte(0); writeInt(item.index) }
                    is WinRTXamlHotReloadItem.Markup -> { writeByte(1); string(item.xaml) }
                } }
            }
        }
    }
    internal data class Request(val token: String, val patch: WinRTXamlHotReloadPatch? = null, val inspection: WinRTXamlInspectionRequest? = null)
    fun writeInspectionRequest(stream: OutputStream, token: String, request: WinRTXamlInspectionRequest) = write(stream) {
        string(token); writeByte(2); string(request.className); string(request.resourcePath); writeInt(request.instance)
        require(request.selectedPath.size <= 64 && request.selectedPath.all { it in 0..2048 })
        writeInt(request.selectedPath.size); request.selectedPath.forEach { writeInt(it) }; writeBoolean(request.capture)
        string(request.previewMarkup); writeInt(request.width); writeInt(request.height); string(request.theme)
    }
    fun readRequest(stream: InputStream): Pair<String, WinRTXamlHotReloadPatch?> = readCommand(stream).let {
        require(it.inspection == null) { "Use the visual inspection command reader." }; it.token to it.patch
    }
    internal fun readCommand(stream: InputStream): Request = read(stream) {
        val token = string()
        val operation = readUnsignedByte()
        if (operation == 2) {
            val name = string(); val resource = string(); val instance = count(1024)
            val path = List(count(64)) { count(2048) }; val capture = readBoolean()
            val markup = string(); val width = count(4096); val height = count(4096); val theme = string()
            return@read Request(token, inspection = WinRTXamlInspectionRequest(name, resource, instance, path, capture, markup, width, height, theme))
        }
        require(operation == 0 || operation == 1) { "Invalid development command." }
        val patch = if (operation == 0) null else {
            val name = string(); val path = string(); val expected = string(); val hash = string(); val version = readLong()
            val changes = List(count(512)) { WinRTXamlHotReloadChange(string(), string(), string(), path()) }
            val resources = List(count(64)) {
                val target = target(); val xaml = string(); val keys = List(count(4096)) { string() }
                WinRTXamlHotReloadResources(target, xaml, keys, List(count(512)) {
                    WinRTXamlHotReloadResourceReference(target(), string(), string())
                })
            }
            val reads = List(count(512)) { WinRTXamlHotReloadRead(target(), string()) }
            val children = List(count(64)) {
                val target = target(); val size = count(4096)
                WinRTXamlHotReloadChildren(target, size, List(count(512)) {
                    when (readUnsignedByte()) {
                        0 -> WinRTXamlHotReloadItem.Existing(count(4096))
                        1 -> WinRTXamlHotReloadItem.Markup(string())
                        else -> error("Invalid Hot Reload child operation.")
                    }
                })
            }
            WinRTXamlHotReloadPatch(name, path, expected, hash, version, changes, resources, reads, children)
        }
        Request(token, patch)
    }
    fun writeReply(stream: OutputStream, reply: WinRTXamlHotReloadReply) = write(stream) {
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
                require(image.width in 1..768 && image.height in 1..768 && image.pixels.size == 4 * image.width * image.height)
                writeInt(image.width); writeInt(image.height); write(image.pixels)
            }
        }
    }
    fun readReply(stream: InputStream): WinRTXamlHotReloadReply = read(stream) {
        val status = readInt(); val message = string()
        val roots = List(count(1024)) { val name = string(); val path = string(); val hash = string(); val version = readLong()
            val instances = count(1024)
            WinRTXamlHotReloadRoot(name, path, hash, version, List(count(4096)) { string() }, instances) }
        val values = List(count(512)) { WinRTXamlHotReloadValue(string(), string(), string(), path()) }
        val inspection = if (!readBoolean()) null else {
            val nodes = List(count(2048)) {
                val path = List(count(64)) { count(2048) }
                val type = string(); val name = string()
                WinRTXamlVisualNode(path, type, name, WinRTXamlVisualBounds(readDouble(), readDouble(), readDouble(), readDouble()))
            }
            val properties = List(count(64)) { WinRTXamlVisualProperty(string(), string()) }
            val image = if (!readBoolean()) null else {
                val width = count(768); val height = count(768); require(width > 0 && height > 0)
                WinRTXamlVisualImage(width, height, ByteArray(4 * width * height).also(::readFully))
            }
            WinRTXamlVisualSnapshot(nodes, properties, image)
        }
        WinRTXamlHotReloadReply(status, message, roots, values, inspection)
    }
}
