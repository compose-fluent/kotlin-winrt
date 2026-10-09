package io.github.composefluent.winrt.ide.nuget

import com.google.gson.JsonParser
import io.github.composefluent.windows.toolkit.gradle.WinAppRestoreLockfileCodec
import io.github.composefluent.windows.toolkit.gradle.WinRTNuGetMsBuildPayloadResolver
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import java.nio.file.Files
import java.nio.file.Path

data class WinRTNuGetPackageStatus(val id: String, val version: String, val root: Path?, val direct: Boolean,
    val projection: Boolean?, val winmds: List<Path>, val source: String?, val problems: List<String>)
data class WinRTNuGetInventory(val packages: List<WinRTNuGetPackageStatus>, val errors: List<String>)
data class WinRTNuGetContributions(val winmds: List<Path>, val nativeFiles: List<Path>, val copyLocal: List<Pair<Path, Path>>,
    val buildFiles: List<Path>, val errors: List<String>)

object WinRTNuGetInventoryReader {
    fun read(module: WinRTModuleData): WinRTNuGetInventory {
        val errors = mutableListOf<String>()
        val resolved = module.restoreLockFiles.flatMap { name ->
            val path = Path.of(name)
            runCatching {
                val lock = WinAppRestoreLockfileCodec.read(path)
                lock.packages.map { pkg ->
                    val declared = module.packages.firstOrNull { it.id.equals(pkg.name, true) }
                    val root = lock.packageRoot(path, pkg)
                    val source = root.resolve(".nupkg.metadata").takeIf(Files::isRegularFile)?.let {
                        runCatching { JsonParser.parseString(Files.readString(it)).asJsonObject["source"]?.asString?.let { address ->
                            val uri = runCatching { java.net.URI(address) }.getOrNull()
                            if (uri?.userInfo != null) java.net.URI(uri.scheme, null, uri.host, uri.port, uri.path, null, null).toString() else address
                        } }.getOrNull()
                    }
                    val problems = buildList {
                        if (!Files.isDirectory(root)) add("The restored package directory is missing.")
                        if (pkg.winmdFiles.any { !Files.isRegularFile(it) }) add("The lock references missing WinMD files.")
                        if (declared != null && !declared.version.equals(pkg.version, true)) add("Declared ${declared.version}; lock resolved ${pkg.version}. Restore again.")
                    }
                    WinRTNuGetPackageStatus(pkg.name, pkg.version, root, declared != null, declared?.generateProjection, pkg.winmdFiles, source, problems)
                }
            }.getOrElse { errors += it.message.orEmpty(); emptyList() }
        }.distinctBy { "${it.id.lowercase()}:${it.version.lowercase()}" }
        val missing = module.packages.filter { declared -> resolved.none { it.id.equals(declared.id, true) } }.map {
            WinRTNuGetPackageStatus(it.id, it.version, null, true, it.generateProjection, emptyList(), null, listOf("Not present in the restore lock."))
        }
        val conflicts = resolved.groupBy { it.id.lowercase() }.filterValues { it.size > 1 }.keys
        if (conflicts.isNotEmpty()) errors += "Different restored versions: ${conflicts.joinToString()}"
        return WinRTNuGetInventory((resolved + missing).sortedWith(compareByDescending<WinRTNuGetPackageStatus> { it.direct }.thenBy { it.id.lowercase() }), errors)
    }

    fun contributions(pkg: WinRTNuGetPackageStatus, rid: String): WinRTNuGetContributions {
        val root = pkg.root ?: return WinRTNuGetContributions(emptyList(), emptyList(), emptyList(), emptyList(), pkg.problems)
        if (!Files.isDirectory(root)) return WinRTNuGetContributions(emptyList(), emptyList(), emptyList(), emptyList(), pkg.problems)
        val files = Files.walk(root).use { stream -> stream.filter(Files::isRegularFile).toList() }
        val payload = runCatching { WinRTNuGetMsBuildPayloadResolver.resolveCopyLocalPayloads(root, rid) }
        return WinRTNuGetContributions(
            (pkg.winmds + files.filter { it.fileName.toString().endsWith(".winmd", true) }).distinct(),
            files.filter { it.fileName.toString().endsWith(".dll", true) &&
                ("runtimes/$rid/native/" in root.relativize(it).toString().replace('\\', '/') || "build/native/" in root.relativize(it).toString().replace('\\', '/')) },
            payload.getOrDefault(emptyList()).map { it.source to it.targetRelativePath },
            files.filter { it.fileName.toString().endsWith(".props", true) || it.fileName.toString().endsWith(".targets", true) },
            pkg.problems + listOfNotNull(payload.exceptionOrNull()?.message),
        )
    }
}
