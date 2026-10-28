@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Presentation only: never authorizes launch, source edits, selection or hot reload.
 * Stored pixels are explicitly labelled as the previous render until a new host replies.
 * No visual tree, property values, process identity or authentication is persisted.
 */
internal class WinUIXamlPreviewPlaceholder(private val directory: Path, source: String) {
    private val file = directory.resolve(java.util.HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))) + ".frame")

    fun read(theme: String, width: Int, height: Int): WinUIXamlVisualSnapshot? = runCatching {
        val started = System.nanoTime()
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) return null
        DataInputStream(InflaterInputStream(Files.newInputStream(file).buffered())).use { input ->
            if (input.readInt() != FORMAT || input.readUTF() != theme ||
                input.readInt() != width || input.readInt() != height) return null
            val pixelsWide = input.readInt()
            val pixelsHigh = input.readInt()
            val size = pixelBytes(pixelsWide, pixelsHigh)
            val pixels = ByteArray(size)
            input.readFully(pixels)
            require(input.read() == -1)
            WinUIXamlVisualSnapshot(listOf(WinUIXamlVisualNode(emptyList(), "Previous render", "",
                WinUIXamlVisualBounds(0.0, 0.0, width.toDouble(), height.toDouble()))), emptyList(),
                WinUIXamlVisualImage(pixelsWide, pixelsHigh, pixels)).also {
                com.intellij.openapi.diagnostic.Logger.getInstance(WinUIXamlPreviewPlaceholder::class.java)
                    .info("Previous XAML preview restored: ${(System.nanoTime() - started) / 1_000_000}ms")
            }
        }
    }.getOrNull()

    fun write(theme: String, width: Int, height: Int, image: WinUIXamlVisualImage) {
        if (image.bounds != null) return
        require(width in 64..WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION && height in 64..WinUIXamlHotReloadProtocol.MAX_ARTBOARD_DIMENSION)
        require(image.pixels.size == pixelBytes(image.width, image.height))
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "frame-", ".tmp")
        try {
            DataOutputStream(DeflaterOutputStream(Files.newOutputStream(temporary).buffered())).use { output ->
                output.writeInt(FORMAT); output.writeUTF(theme)
                output.writeInt(width); output.writeInt(height)
                output.writeInt(image.width); output.writeInt(image.height)
                output.write(image.pixels)
            }
            if (Files.size(temporary) > MAX_FILE_BYTES) return
            Files.move(temporary, file, REPLACE_EXISTING, ATOMIC_MOVE)
            // Keep this disposable local cache bounded, including unopened documents.
            Files.list(directory).use { entries -> entries.filter { it != file && it.fileName.toString().endsWith(".frame") }
                .sorted(compareByDescending { Files.getLastModifiedTime(it) }).skip(7)
                .forEach { Files.deleteIfExists(it) } }
        } finally { Files.deleteIfExists(temporary) }
    }

    private fun pixelBytes(width: Int, height: Int): Int {
        require(width in 1..4096 && height in 1..4096)
        val size = width.toLong() * height * 4
        require(size <= 32L * 1024 * 1024)
        return size.toInt()
    }

    private companion object {
        const val FORMAT = 0x58504601
        const val MAX_FILE_BYTES = 4L * 1024 * 1024
    }
}
