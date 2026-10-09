package io.github.composefluent.windows.toolkit.gradle

import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** The WinApp CLI's resolved-input contract, shared with the IDE without Gradle APIs. */
internal data class WinAppRestoreLockfile(
    val schema: Int,
    val nugetCacheDirectory: Path?,
    val packages: List<WinAppRestoredPackage>,
) {
    val winmdFiles: List<Path>
        get() = packages.flatMap(WinAppRestoredPackage::winmdFiles)
            .distinctBy { it.toAbsolutePath().normalize().toString().lowercase() }

    fun packageRoot(path: Path, pkg: WinAppRestoredPackage): Path {
        val declared = nugetCacheDirectory
        require(declared != null && declared.isAbsolute) {
            "WinApp restore lockfile $path has packages but no absolute nuget_cache_dir."
        }
        val cache = declared.normalize()
        val result = cache.resolve(pkg.name.lowercase()).resolve(pkg.version).normalize()
        require(result.startsWith(cache)) {
            "WinApp restore lockfile $path contains an unsafe package path for ${pkg.name} ${pkg.version}."
        }
        return result
    }
}

internal data class WinAppRestoredPackage(val name: String, val version: String, val winmdFiles: List<Path>)

internal object WinAppRestoreLockfileCodec {
    const val SUPPORTED_SCHEMA: Int = 3

    fun read(path: Path): WinAppRestoreLockfile {
        require(Files.isRegularFile(path)) { "WinApp restore did not produce $path." }
        val root = runCatching { Json.parseToJsonElement(Files.readString(path)).jsonObject }.getOrElse {
            throw IllegalArgumentException("Cannot parse WinApp restore lockfile $path: ${it.message}", it)
        }
        val schema = root["schema"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("WinApp restore lockfile $path is missing integer 'schema'.")
        require(schema == SUPPORTED_SCHEMA) {
            "Unsupported WinApp restore lockfile schema $schema in $path; expected $SUPPORTED_SCHEMA."
        }
        val cache = root.optionalString("nuget_cache_dir")?.takeIf(String::isNotBlank)?.let(Path::of)
        val packages = root.requiredArray("packages", path).mapIndexed { index, element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("WinApp restore lockfile $path has a non-object package at index $index.")
            val name = obj.requiredString("name", path)
            val version = obj.requiredString("version", path)
            val winmds = obj.requiredArray("winmds", path).mapIndexed { winmdIndex, winmd ->
                val value = runCatching { winmd.jsonPrimitive.content }.getOrNull()?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("WinApp restore lockfile $path has an invalid winmd at package $name index $winmdIndex.")
                Path.of(value)
            }
            WinAppRestoredPackage(name, version, winmds)
        }
        return WinAppRestoreLockfile(schema, cache, packages)
    }

    private fun JsonObject.optionalString(name: String): String? = this[name]?.let {
        runCatching { it.jsonPrimitive.content }.getOrNull()
    }
    private fun JsonObject.requiredString(name: String, path: Path): String =
        optionalString(name)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("WinApp restore lockfile $path is missing string '$name'.")
    private fun JsonObject.requiredArray(name: String, path: Path): JsonArray =
        this[name]?.let { runCatching { it.jsonArray }.getOrNull() }
            ?: throw IllegalArgumentException("WinApp restore lockfile $path is missing array '$name'.")
}
