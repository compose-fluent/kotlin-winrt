package io.github.composefluent.winrt.ide.xaml

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*

/** A materialized view of the metadata-owned member lookup, not another XAML schema.
 * Native design markup only needs event names before the full editor catalog loads. */
internal class WinUIXamlPreviewEvents(private val events: Map<String, Set<String>>) {
    fun isEvent(uri: String, type: String, member: String): Boolean =
        WinUIXamlCatalog.namespaces(uri).any { member in events["$it.$type"].orEmpty() }

    fun save(directory: Path, key: String) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "events-", ".tmp")
        try {
            Files.writeString(temporary, buildJsonObject {
                events.toSortedMap().forEach { (name, members) -> put(name, JsonArray(members.sorted().map(::JsonPrimitive))) }
            }.toString())
            Files.move(temporary, file(directory, key), REPLACE_EXISTING, ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
    }

    companion object {
        private fun file(directory: Path, key: String): Path {
            require(key.matches(Regex("[0-9a-f]{64}")))
            return directory.resolve("preview-events-v1-$key.json")
        }
        fun read(directory: Path, key: String): WinUIXamlPreviewEvents? = runCatching {
            val path = file(directory, key)
            require(Files.size(path) <= 8 * 1024 * 1024)
            WinUIXamlPreviewEvents(Json.parseToJsonElement(Files.readString(path)).jsonObject.mapValues { (_, value) ->
                value.jsonArray.map { it.jsonPrimitive.content }.toSet()
            })
        }.getOrNull()
        fun from(catalog: WinUIXamlCatalog): WinUIXamlPreviewEvents = WinUIXamlPreviewEvents(
            catalog.types.values.associate { type -> type.qualifiedName to
                catalog.members(type).filter { it.isEvent }.map { it.name }.toSet() }.filterValues { it.isNotEmpty() })
    }
}
