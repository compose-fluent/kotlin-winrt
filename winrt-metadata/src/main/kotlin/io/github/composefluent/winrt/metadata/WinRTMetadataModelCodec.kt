package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.Guid
import java.nio.file.Files
import java.nio.file.Path
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val MODEL_CACHE_HEADER = "kotlin-winrt-normalized-model-v1"
private const val MODEL_CACHE_SCHEMA = 1

/** Stable serialization for the complete normalized metadata model. */
object WinRTMetadataModelCodec {
    private val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = true
        explicitNulls = true
    }

    fun read(path: Path): WinRTMetadataModel {
        val root = runCatching { json.parseToJsonElement(Files.readString(path)) }
            .getOrElse { error ->
                throw SerializationException("Cannot read WinRT metadata model cache $path: ${error.message}", error)
            }
        val objectRoot = root as? JsonObject
            ?: throw SerializationException("WinRT metadata model cache $path is not a JSON object.")
        require(objectRoot["header"]?.jsonPrimitive?.content == MODEL_CACHE_HEADER) {
            "WinRT metadata model cache $path has an incompatible header."
        }
        require(objectRoot["schema"]?.jsonPrimitive?.content?.toIntOrNull() == MODEL_CACHE_SCHEMA) {
            "WinRT metadata model cache $path has an incompatible schema."
        }
        val modelElement = objectRoot["model"]
            ?: throw SerializationException("WinRT metadata model cache $path is missing its model payload.")
        return runCatching { json.decodeFromJsonElement(WinRTMetadataModel.serializer(), modelElement).normalized() }
            .getOrElse { error ->
                throw SerializationException("Cannot decode WinRT metadata model cache $path: ${error.message}", error)
            }
    }

    fun writeAtomic(path: Path, model: WinRTMetadataModel) {
        val normalized = model.normalized()
        val document = buildJsonObject {
            put("header", MODEL_CACHE_HEADER)
            put("schema", MODEL_CACHE_SCHEMA)
            put("model", json.encodeToJsonElement(WinRTMetadataModel.serializer(), normalized))
        }
        Files.createDirectories(path.parent)
        val lockPath = path.resolveSibling(".${path.fileName}.lock")
        FileChannel.open(lockPath, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val temporary = path.resolveSibling(".${path.fileName}.tmp-${UUID.randomUUID()}")
                try {
                    Files.writeString(temporary, json.encodeToString(JsonObject.serializer(), document))
                    try {
                        Files.move(
                            temporary,
                            path,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    }
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        }
    }

    fun cacheKey(files: List<Path>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.map { it.toAbsolutePath().normalize() }
            .sortedBy { it.toString().lowercase() }
            .forEach { file ->
                digest.update(file.toString().toByteArray(Charsets.UTF_8))
                digest.update(0)
                java.security.DigestInputStream(Files.newInputStream(file), digest).use { input ->
                    input.copyTo(java.io.OutputStream.nullOutputStream())
                }
            }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }
}

object WinRTGuidAsStringSerializer : KSerializer<Guid> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("WinRTGuid", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Guid) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): Guid = Guid(decoder.decodeString())
}
