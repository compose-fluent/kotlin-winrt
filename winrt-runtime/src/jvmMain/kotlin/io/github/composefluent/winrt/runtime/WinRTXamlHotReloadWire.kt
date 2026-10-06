package io.github.composefluent.winrt.runtime

import java.io.*

/** Bounded framing shared verbatim with the IDE; the authentication token is never a display DTO. */
object WinRTXamlHotReloadWire {
    private const val MAX_FRAME = 2 * 1024 * 1024
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
    fun readRequest(stream: InputStream): Pair<String, WinRTXamlHotReloadPatch?> = read(stream) {
        val token = string()
        token to if (!readBoolean()) null else {
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
    }
    fun writeReply(stream: OutputStream, reply: WinRTXamlHotReloadReply) = write(stream) {
        writeInt(reply.status); string(reply.message); writeInt(reply.roots.size)
        reply.roots.forEach { string(it.className); string(it.resourcePath); string(it.sourceHash); writeLong(it.version)
            writeInt(it.elements.size); it.elements.forEach { name -> string(name) } }
        writeInt(reply.values.size); reply.values.forEach { string(it.element); string(it.property); string(it.value); path(it.path) }
    }
    fun readReply(stream: InputStream): WinRTXamlHotReloadReply = read(stream) {
        val status = readInt(); val message = string()
        val roots = List(count(1024)) { val name = string(); val path = string(); val hash = string(); val version = readLong()
            WinRTXamlHotReloadRoot(name, path, hash, version, List(count(4096)) { string() }) }
        WinRTXamlHotReloadReply(status, message, roots, List(count(512)) { WinRTXamlHotReloadValue(string(), string(), string(), path()) })
    }
}
