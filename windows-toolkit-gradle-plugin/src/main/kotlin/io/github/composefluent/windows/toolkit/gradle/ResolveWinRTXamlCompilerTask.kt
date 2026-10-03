package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@DisableCachingByDefault(because = "Downloads a pinned tool package into a checksum-verified local cache")
abstract class ResolveWinRTXamlCompilerTask : DefaultTask() {
    @get:Input abstract val compilerVersion: Property<String>
    @get:Input abstract val archiveUrl: Property<String>
    @get:Input abstract val archiveSha256: Property<String>
    @get:Input abstract val offline: Property<Boolean>
    @get:Internal abstract val cacheDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun resolve() {
        require(System.getProperty("os.name").startsWith("Windows") &&
            System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")) {
            "Kotlin XamlCompiler currently requires a Windows x64 build host."
        }
        val hash = archiveSha256.get().lowercase()
        require(hash.matches(Regex("[0-9a-f]{64}"))) { "A pinned XamlCompiler SHA-256 is required." }
        val cache = cacheDirectory.get().asFile.toPath().resolve(hash)
        Files.createDirectories(cache)
        FileChannel.open(cache.resolve("download.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val archive = cache.resolve("compiler.zip")
                if (!Files.isRegularFile(archive) || xamlSha256(archive) != hash) {
                    check(!offline.get()) { "Kotlin XamlCompiler ${compilerVersion.get()} is not cached; Gradle is offline." }
                    val uri = URI(archiveUrl.get())
                    require(uri.scheme == "https") { "XamlCompiler download requires HTTPS." }
                    val temporary = cache.resolve("download.part")
                    try {
                        val connection = uri.toURL().openConnection().apply { connectTimeout = 30_000; readTimeout = 60_000 }
                        connection.getInputStream().use { input -> Files.newOutputStream(temporary).use(input::copyTo) }
                        check(xamlSha256(temporary) == hash) { "XamlCompiler archive checksum mismatch." }
                        Files.move(temporary, archive, StandardCopyOption.REPLACE_EXISTING)
                    } finally { Files.deleteIfExists(temporary) }
                }
                extractXamlCompiler(archive, outputDirectory.get().asFile.toPath(), compilerVersion.get())
            }
        }
    }
}

internal fun xamlSha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path).use { input ->
        val buffer = ByteArray(65536)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun extractXamlCompiler(archive: Path, target: Path, version: String) {
    val absoluteTarget = target.toAbsolutePath().normalize()
    Files.createDirectories(absoluteTarget.parent)
    val extraction = Files.createTempDirectory(absoluteTarget.parent, ".xamlc-")
    try {
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val destination = extraction.resolve(entry.name.replace('\\', '/')).normalize()
                require(destination.startsWith(extraction)) { "Unsafe XamlCompiler archive entry: ${entry.name}" }
                if (!entry.isDirectory) {
                    Files.createDirectories(destination.parent)
                    Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW).use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
        validateXamlCompilerPackage(extraction, version)
        if (Files.exists(absoluteTarget)) GradleFileOperations.deleteDirectory(absoluteTarget)
        Files.move(extraction, absoluteTarget)
    } finally {
        if (Files.exists(extraction)) GradleFileOperations.deleteDirectory(extraction)
    }
}

internal fun validateXamlCompilerPackage(root: Path, version: String? = null): JsonObject {
    val manifest = Json.parseToJsonElement(Files.readString(root.resolve("kotlin-xamlc.json")).removePrefix("\uFEFF")).jsonObject
    require(manifest["schemaVersion"]?.jsonPrimitive?.int == 1 && manifest["protocolVersion"]?.jsonPrimitive?.int in 1..2) {
        "Unsupported Kotlin XamlCompiler package or protocol version."
    }
    require((version == null || manifest["version"]?.jsonPrimitive?.content == version) && manifest["host"]?.jsonPrimitive?.content == "win-x64") {
        "XamlCompiler package version or host mismatch."
    }
    require(manifest["executable"]?.jsonPrimitive?.content == "XamlCompiler.exe") { "Unexpected XamlCompiler entry point." }
    val files = manifest.getValue("files").jsonObject
    require("XamlCompiler.exe" in files) { "XamlCompiler package is missing the executable checksum." }
    files.forEach { (name, expected) ->
        val file = root.resolve(name).normalize()
        require(file.startsWith(root) && Files.isRegularFile(file) && xamlSha256(file) == expected.jsonPrimitive.content) {
            "XamlCompiler package file checksum mismatch: $name"
        }
    }
    if (manifest["protocolVersion"]?.jsonPrimitive?.int == 2) {
        val execution = manifest.getValue("execution").jsonObject
        require(execution["kind"]?.jsonPrimitive?.content == "executable" &&
            execution["arguments"]?.jsonArray?.map { it.jsonPrimitive.content } == listOf("input.json", "output.json")) {
            "Unsupported Kotlin XamlCompiler execution contract."
        }
        val runtime = manifest.getValue("runtime").jsonObject
        require(runtime["kind"]?.jsonPrimitive?.content == "net-framework" &&
            runtime["minimumRelease"]?.jsonPrimitive?.intOrNull != null) {
            "Unsupported Kotlin XamlCompiler runtime contract."
        }
        require(manifest["compatibility"] is JsonObject && manifest["features"] is JsonArray) {
            "XamlCompiler protocol 2 requires compatibility and feature declarations."
        }
    }
    return manifest
}

internal fun validateXamlCompilerHost(manifest: JsonObject) {
    require(System.getProperty("os.name").startsWith("Windows") &&
        System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")) {
        "Kotlin XamlCompiler requires a Windows x64 build host."
    }
    val runtime = manifest["runtime"]?.jsonObject ?: return
    val required = runtime.getValue("minimumRelease").jsonPrimitive.int
    val process = ProcessBuilder("reg.exe", "query", "HKLM\\SOFTWARE\\Microsoft\\NET Framework Setup\\NDP\\v4\\Full",
        "/v", "Release", "/reg:64").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val installed = Regex("Release\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)").find(output)
        ?.groupValues?.get(1)?.toIntOrNull(16)
    require(process.waitFor() == 0 && installed != null && installed >= required) {
        "Kotlin XamlCompiler requires .NET Framework ${runtime["minimumVersion"]?.jsonPrimitive?.content} " +
            "(Release >= $required); installed Release is ${installed ?: "unavailable"}."
    }
}
