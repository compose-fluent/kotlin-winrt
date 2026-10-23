package io.github.composefluent.winrt.ide.preview

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.security.MessageDigest

/** Cache transformed source, never pixels: the current Native host still loads and renders it.
 * Bump this format when design-time transformation semantics change.
 */
internal class WinUIXamlDesignDocumentCache(directory: Path, source: String) {
    private val file = directory.resolve(digest(source))
    fun read(key: String): WinUIXamlDesignDocument? = runCatching {
        val json = Json.parseToJsonElement(Files.readString(file)).jsonObject
        if (json["key"]?.jsonPrimitive?.content != key) return null
        WinUIXamlDesignDocument(json.getValue("markup").jsonPrimitive.content,
            json.getValue("notes").jsonArray.map { it.jsonPrimitive.content })
    }.getOrNull()
    fun write(key: String, document: WinUIXamlDesignDocument) {
        Files.createDirectories(file.parent)
        val temporary = Files.createTempFile(file.parent, "document-", ".tmp")
        try {
            Files.writeString(temporary, buildJsonObject {
                put("key", key); put("markup", document.markup)
                put("notes", JsonArray(document.notes.map(::JsonPrimitive)))
            }.toString())
            Files.move(temporary, file, REPLACE_EXISTING, ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
    }
    companion object {
        fun key(inputs: List<String>): String = digest(buildString {
            append("native-design-document-v1;")
            inputs.forEach { append(it.length); append(':'); append(it) }
        })
        private fun digest(text: String) = java.util.HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    }
}
