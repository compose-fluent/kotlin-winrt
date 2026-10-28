@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.runtime

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream

/** Stream adapter; JVM and Native share the bounded development wire codec. */
@InternalWinRTApi
object WinUIXamlHotReloadWire {
    private fun read(stream: InputStream): ByteArray {
        val input = DataInputStream(stream)
        val header = ByteArray(12).also(input::readFully)
        val bodySize = WinUIXamlHotReloadCodec.frameSize(header)
        return ByteArray(12 + bodySize).also { frame ->
            header.copyInto(frame)
            input.readFully(frame, 12, bodySize)
        }
    }
    private fun write(stream: OutputStream, bytes: ByteArray) { stream.write(bytes); stream.flush() }
    fun writeRequest(stream: OutputStream, token: String, patch: WinUIXamlHotReloadPatch?) =
        write(stream, WinUIXamlHotReloadCodec.writeRequest(token, patch))
    fun writeInspectionRequest(stream: OutputStream, token: String, request: WinUIXamlInspectionRequest) =
        write(stream, WinUIXamlHotReloadCodec.writeInspectionRequest(token, request))
    fun writeStateRequest(stream: OutputStream, token: String, request: WinUIXamlStateRequest) =
        write(stream, WinUIXamlHotReloadCodec.writeStateRequest(token, request))
    fun readRequest(stream: InputStream): Pair<String, WinUIXamlHotReloadPatch?> = WinUIXamlHotReloadCodec.readRequest(read(stream))
    internal fun readCommand(stream: InputStream): WinUIXamlHotReloadCodec.Request = WinUIXamlHotReloadCodec.readCommand(read(stream))
    fun writeReply(stream: OutputStream, reply: WinUIXamlHotReloadReply) = write(stream, WinUIXamlHotReloadCodec.writeReply(reply))
    fun readReply(stream: InputStream): WinUIXamlHotReloadReply = WinUIXamlHotReloadCodec.readReply(read(stream))
}