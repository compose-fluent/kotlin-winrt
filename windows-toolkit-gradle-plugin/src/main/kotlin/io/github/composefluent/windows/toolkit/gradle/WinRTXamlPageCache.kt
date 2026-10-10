package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTXamlDeclarationIndex
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * XAMLC's KotlinXamlDeclarationWriter owns one XAML file per class. Cache that complete native
 * result, including XBF, and submit changed resources to the same DOM/harvester and validator.
 * No Kotlin interpretation of XAML syntax or binding expressions is involved.
 */
internal class WinRTXamlPageCache(private val root: Path) {
    data class Page(val output: JsonObject, val directory: Path, val files: Map<String, String>)

    fun key(context: String, resource: String, source: File, hash: String): String =
        textHash(listOf(context, resource, source.absolutePath, hash, fileHash(source.toPath())).joinToString("\u0000"))

    fun read(key: String, resource: String): Page? = runCatching {
        val directory = root.resolve(key)
        val manifest = Json.parseToJsonElement(Files.readString(directory.resolve("manifest.json"))).jsonObject
        val outputFile = directory.resolve("output.json")
        require(fileHash(outputFile) == manifest.getValue("outputHash").jsonPrimitive.content)
        val output = Json.parseToJsonElement(Files.readString(outputFile)).jsonObject
        val plan = WinRTXamlDeclarations.readCompilerOutput(outputFile)
        require(plan.resources.isEmpty() && plan.pages.single().resourcePath == resource)
        require(canIsolate(plan))
        output["KotlinImplementation"]?.takeIf { it != JsonNull }?.jsonObject?.let {
            require(it.getValue("DeclarationFingerprint").jsonPrimitive.content == WinRTXamlDeclarations.fingerprint(plan))
        }
        val files = manifest.getValue("files").jsonObject.mapValues { it.value.jsonPrimitive.content }
        require((output.paths("GeneratedXamlFiles") + output.paths("GeneratedXbfFiles")).all {
            val relative = directory.resolve("compiled").toAbsolutePath().normalize()
                .relativize(Path.of(it).toAbsolutePath().normalize()).portable()
            require(payloadFile(directory, relative) == Path.of(it).toAbsolutePath().normalize())
            relative in setOf(resource, resource.substringBeforeLast('.') + ".xbf") &&
                (output["KotlinImplementation"] == JsonNull || relative in files)
        })
        files.forEach { (relative, hash) -> require(fileHash(payloadFile(directory, relative)) == hash) }
        val payload = directory.resolve("compiled")
        if (Files.isDirectory(payload)) Files.walk(payload).use { paths ->
            require(paths.filter(Files::isRegularFile).allMatch { payload.relativize(it).portable() in files })
        }
        Page(output, directory, files)
    }.getOrNull()

    fun write(key: String, resource: String, output: JsonObject, compiled: Path): Page? {
        val plan = WinRTXamlDeclarations.parse(output.getValue("KotlinDeclarations").toString())
        val selected = select(plan, setOf(resource))
        val names = setOf(resource, resource.substringBeforeLast('.') + ".xbf")
        // Unexpected shared code/payload needs a full native invocation, not an incomplete cache hit.
        if (output.paths("GeneratedCodeFiles").isNotEmpty()) return null
        val files = (output.paths("GeneratedXamlFiles") + output.paths("GeneratedXbfFiles"))
            .map(Path::of).map { compiled.relativize(it.toAbsolutePath().normalize()).portable() }
            .filter { it in names && Files.isRegularFile(compiled.resolve(it)) }.distinct()
        // Pass 1 advertises planned XAML/XBF paths but only emits declarations.
        if (!canIsolate(selected) || (output["KotlinImplementation"] != JsonNull && !files.containsAll(names))) return null
        val directory = root.resolve(key)
        if (Files.exists(directory)) GradleFileOperations.deleteDirectory(directory)
        Files.createDirectories(directory)
        val hashes = files.associateWith { relative ->
            val source = compiled.resolve(relative)
            val target = payloadFile(directory, relative)
            Files.createDirectories(target.parent)
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            fileHash(target)
        }
        val partial = JsonObject(output + buildMap {
            put("KotlinDeclarations", Json.parseToJsonElement(WinRTXamlDeclarations.canonicalText(selected)))
            output["KotlinImplementation"]?.takeIf { it != JsonNull }?.jsonObject?.let { implementation ->
                put("KotlinImplementation", JsonObject(implementation + mapOf(
                    "DeclarationFingerprint" to JsonPrimitive(WinRTXamlDeclarations.fingerprint(selected)),
                    "Declarations" to Json.parseToJsonElement(WinRTXamlDeclarations.canonicalText(selected)),
                )))
            }
            for (field in listOf("GeneratedXamlFiles", "GeneratedXbfFiles")) {
                put(field, JsonArray(output.paths(field).filter { path ->
                    compiled.relativize(Path.of(path).toAbsolutePath().normalize()).portable() in names
                }.map { path -> JsonPrimitive(payloadFile(directory,
                    compiled.relativize(Path.of(path).toAbsolutePath().normalize()).portable()).toString()) }))
            }
            put("GeneratedXamlPagesFiles", JsonArray(output.paths("GeneratedXamlPagesFiles")
                .filter { it.replace('\\', '/').endsWith(resource) }.map(::JsonPrimitive)))
        })
        Files.writeString(directory.resolve("output.json"), partial.toString())
        Files.writeString(directory.resolve("manifest.json"), buildJsonObject {
            put("outputHash", fileHash(directory.resolve("output.json")))
            put("files", JsonObject(hashes.mapValues { JsonPrimitive(it.value) }))
        }.toString())
        return read(key, resource)
    }

    fun materialize(page: Page, compiled: Path): JsonObject {
        page.files.forEach { (relative, hash) ->
            val target = payloadFile(compiled.parent, relative, compiled.fileName.toString())
            Files.createDirectories(target.parent)
            if (!Files.isRegularFile(target) || fileHash(target) != hash) {
                Files.copy(payloadFile(page.directory, relative), target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return JsonObject(page.output + listOf("GeneratedXamlFiles", "GeneratedXbfFiles").associateWith { field ->
            JsonArray(page.output.paths(field).map { path -> JsonPrimitive(compiled.resolve(
                page.directory.resolve("compiled").toAbsolutePath().normalize()
                    .relativize(Path.of(path).toAbsolutePath().normalize())).toString()) })
        })
    }

    fun retain(keys: Set<String>) {
        if (!Files.isDirectory(root)) return
        Files.list(root).use { entries -> entries.filter { it.fileName.toString().matches(Regex("[0-9a-f]{64}")) }
            .filter { it.fileName.toString() !in keys }
            .forEach(GradleFileOperations::deleteDirectory) }
    }

    companion object {
        // The native protocol has no resource-dictionary dependency graph. Keep its full batch
        // behavior when application/shared resources could influence another page's output.
        fun canIsolate(index: WinRTXamlDeclarationIndex): Boolean =
            index.resources.isEmpty() &&
                index.pages.none { it.isApplication || it.baseTypeName.endsWith(".ResourceDictionary") }

        private fun Path.portable() = toString().replace('\\', '/')
        private fun payloadFile(directory: Path, relative: String, child: String = "compiled"): Path {
            val payload = directory.resolve(child).toAbsolutePath().normalize()
            val path = payload.resolve(relative).normalize()
            require(!Path.of(relative).isAbsolute && path.startsWith(payload) && path != payload)
            return path
        }

        fun context(input: JsonObject, files: Collection<File>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("kotlin-xaml-page-cache-v1\u0000".toByteArray())
            digest.update(JsonObject(input - "XamlPages").toString().toByteArray())
            digest.update(JsonArray(input.getValue("XamlPages").jsonArray.map {
                it.jsonObject.getValue("FullPath")
            }).toString().toByteArray())
            files.filter(File::isFile).distinctBy { it.absolutePath }.sortedBy { it.absolutePath }.forEach { file ->
                digest.update(file.absolutePath.toByteArray()); digest.update(0)
                // Metadata is content-addressed even if a producer preserves its timestamps.
                if (file.extension.equals("winmd", true)) digest.update(fileHash(file.toPath()).toByteArray())
                else digest.update(PreparedProjectionFileFingerprints.content(file.toPath()))
                digest.update(0)
            }
            return java.util.HexFormat.of().formatHex(digest.digest())
        }

        fun symbolsForResources(symbols: JsonObject, resources: Set<String>): JsonObject {
            val selected = select(WinRTXamlDeclarations.parse(symbols.getValue("Declarations").toString()), resources)
            val classes = selected.pages.map { it.className }.toSet()
            return JsonObject(symbols + mapOf(
                "Declarations" to Json.parseToJsonElement(WinRTXamlDeclarations.canonicalText(selected)),
                "DeclarationFingerprint" to JsonPrimitive(WinRTXamlDeclarations.fingerprint(selected)),
                "Pages" to JsonArray(symbols.getValue("Pages").jsonArray.filter {
                    it.jsonObject.getValue("ClassName").jsonPrimitive.content in classes
                }),
            ))
        }

        fun merge(outputs: List<JsonObject>, symbols: JsonObject?): JsonObject {
            require(outputs.isNotEmpty())
            val indexes = outputs.map { WinRTXamlDeclarations.parse(it.getValue("KotlinDeclarations").toString()) }
            require(indexes.map { it.schemaVersion }.distinct().size == 1)
            val plan = indexes.first().copy(resources = indexes.flatMap { it.resources }.distinct().sorted(),
                pages = indexes.flatMap { it.pages }.sortedBy { it.className })
            val declarations = Json.parseToJsonElement(WinRTXamlDeclarations.canonicalText(plan))
            return JsonObject(outputs.first() + buildMap {
                put("KotlinDeclarations", declarations)
                if (symbols != null) put("KotlinImplementation", buildJsonObject {
                    put("SchemaVersion", plan.schemaVersion)
                    // The caller checks the merged native plan against the ORIGINAL semantic contract.
                    put("DeclarationFingerprint", symbols.getValue("DeclarationFingerprint"))
                    put("Declarations", declarations)
                })
                for (field in listOf("GeneratedXamlFiles", "GeneratedXbfFiles", "GeneratedCodeFiles", "GeneratedXamlPagesFiles", "MSBuildLogEntries"))
                    put(field, JsonArray(outputs.flatMap { (it[field] as? JsonArray).orEmpty() }.distinct()))
            })
        }

        private fun select(index: WinRTXamlDeclarationIndex, resources: Set<String>) = index.copy(
            resources = index.resources.filter { it in resources }.sorted(),
            pages = index.pages.filter { it.resourcePath in resources },
        )
        private fun JsonObject.paths(field: String) = (get(field) as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
        private fun textHash(text: String) = java.util.HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))
        private fun fileHash(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
            }
            return java.util.HexFormat.of().formatHex(digest.digest())
        }
    }
}
