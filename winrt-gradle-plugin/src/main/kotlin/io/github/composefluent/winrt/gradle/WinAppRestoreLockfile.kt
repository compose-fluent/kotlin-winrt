package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRTNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRTNuGetPackageResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal data class WinAppRestoreLockfile(
    val schema: Int,
    val nugetCacheDirectory: Path?,
    val packages: List<WinAppRestoredPackage>,
) {
    val winmdFiles: List<Path>
        get() = packages
            .flatMap(WinAppRestoredPackage::winmdFiles)
            .distinctBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
}

internal data class WinAppRestoredPackage(
    val name: String,
    val version: String,
    val winmdFiles: List<Path>,
)

internal object WinAppRestoreLockfileReader {
    const val SUPPORTED_SCHEMA: Int = 3

    fun read(path: Path): WinAppRestoreLockfile {
        if (!Files.isRegularFile(path)) {
            throw GradleException("WinApp restore did not produce $path.")
        }
        val root = runCatching {
            Json.parseToJsonElement(Files.readString(path)).jsonObject
        }.getOrElse { error ->
            throw GradleException("Cannot parse WinApp restore lockfile $path: ${error.message}", error)
        }
        val schema = root.requiredInt("schema", path)
        if (schema != SUPPORTED_SCHEMA) {
            throw GradleException(
                "Unsupported WinApp restore lockfile schema $schema in $path; expected $SUPPORTED_SCHEMA.",
            )
        }
        val nugetCacheDirectory = root.optionalString("nuget_cache_dir")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        val packages = root.requiredArray("packages", path).mapIndexed { index, element ->
            val packageObject = element as? JsonObject
                ?: throw GradleException("WinApp restore lockfile $path has a non-object package at index $index.")
            val name = packageObject.requiredString("name", path)
            val version = packageObject.requiredString("version", path)
            val winmds = packageObject.requiredArray("winmds", path).mapIndexed { winmdIndex, winmd ->
                val value = runCatching { winmd.jsonPrimitive.content }.getOrNull()
                    ?.takeIf(String::isNotBlank)
                    ?: throw GradleException(
                        "WinApp restore lockfile $path has an invalid winmd at package $name index $winmdIndex.",
                    )
                Path.of(value)
            }
            WinAppRestoredPackage(name, version, winmds)
        }
        return WinAppRestoreLockfile(schema, nugetCacheDirectory, packages)
    }
}

internal fun readWinAppProjectionWinmdFiles(
    lockFiles: Iterable<File>,
    rootPackageSpecs: Iterable<String>,
): List<Path> {
    val rootPackages = declaredWinAppRootPackages(
        rootPackageSpecs,
        WinAppConfigurationDefaults.toolingPackageIds,
    )
    if (rootPackages.isEmpty()) {
        return emptyList()
    }
    val restoredLockfiles = readWinAppRestoreLockfiles(lockFiles)
    validateWinAppDeclaredPackageRoots(
        restoredLockfiles = restoredLockfiles,
        rootPackages = rootPackages,
        usage = "projection generation",
    )
    val winmdFiles = restoredLockfiles.flatMap { restoredLockfile ->
        val packagesById = restoredLockfile.packagesById
        val selectedPackageIds = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        rootPackages.forEach { identity ->
            val key = identity.normalizedPackageId.lowercase()
            if (key in packagesById) {
                queue += key
            }
        }
        while (queue.isNotEmpty()) {
            val packageId = queue.removeFirst()
            if (!selectedPackageIds.add(packageId)) {
                continue
            }
            val restored = packagesById.getValue(packageId)
            val packageRoot = restoredPackageRoot(
                restoredLockfile.path,
                restoredLockfile.lockfile,
                restored,
            )
            WinRTNuGetPackageResolver.dependencies(packageRoot).forEach { dependency ->
                val dependencyId = dependency.normalizedPackageId.lowercase()
                if (
                    dependencyId in packagesById &&
                    dependencyId !in WinAppConfigurationDefaults.toolingPackageIds
                ) {
                    queue += dependencyId
                }
            }
        }
        selectedPackageIds
            .map(packagesById::getValue)
            .flatMap(WinAppRestoredPackage::winmdFiles)
    }
    return winmdFiles
        .distinctBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
        .sortedBy { path -> path.toAbsolutePath().normalize().toString().lowercase() }
}

internal fun readWinAppRestoredPackageRoots(
    lockFiles: Iterable<File>,
    rootPackageSpecs: Iterable<String> = emptyList(),
    excludedPackageIds: Iterable<String> = WinAppConfigurationDefaults.toolingPackageIds,
): List<Path> {
    val excludedIds = excludedPackageIds.mapTo(linkedSetOf(), String::lowercase)
    val restoredLockfiles = readWinAppRestoreLockfiles(lockFiles)
    validateWinAppDeclaredPackageRoots(
        restoredLockfiles = restoredLockfiles,
        rootPackages = declaredWinAppRootPackages(rootPackageSpecs, excludedIds),
        usage = "runtime asset staging",
    )
    return restoredLockfiles
        .flatMap { restoredLockfile ->
            if (restoredLockfile.lockfile.packages.isEmpty()) {
                return@flatMap emptyList()
            }
            restoredLockfile.lockfile.packages
                .filterNot { pkg -> pkg.name.lowercase() in excludedIds }
                .map { pkg ->
                    restoredPackageRoot(restoredLockfile.path, restoredLockfile.lockfile, pkg)
                }
        }
        .distinctBy { path -> path.toString().lowercase() }
        .sortedBy { path -> path.toString().lowercase() }
}

private data class RestoredWinAppLockfile(
    val path: Path,
    val lockfile: WinAppRestoreLockfile,
    val packagesById: Map<String, WinAppRestoredPackage>,
)

private fun readWinAppRestoreLockfiles(lockFiles: Iterable<File>): List<RestoredWinAppLockfile> =
    lockFiles
        .filter(File::isFile)
        .map { file ->
            val path = file.toPath()
            val lockfile = WinAppRestoreLockfileReader.read(path)
            RestoredWinAppLockfile(path, lockfile, lockfile.packages.associateByUniqueId(path))
        }

private fun declaredWinAppRootPackages(
    packageSpecs: Iterable<String>,
    excludedPackageIds: Iterable<String>,
): List<WinRTNuGetPackageIdentity> {
    val excludedIds = excludedPackageIds.mapTo(linkedSetOf(), String::lowercase)
    return packageSpecs
        .map(::parseNuGetPackageIdentity)
        .filterNot { identity -> identity.normalizedPackageId.lowercase() in excludedIds }
        .distinctBy { identity ->
            "${identity.normalizedPackageId.lowercase()}:${identity.normalizedVersion.lowercase()}"
        }
}

private fun validateWinAppDeclaredPackageRoots(
    restoredLockfiles: List<RestoredWinAppLockfile>,
    rootPackages: List<WinRTNuGetPackageIdentity>,
    usage: String,
) {
    val missingRoots = mutableListOf<WinRTNuGetPackageIdentity>()
    rootPackages.forEach { identity ->
        val key = identity.normalizedPackageId.lowercase()
        var found = false
        restoredLockfiles.forEach lockfileLoop@ { restoredLockfile ->
            val restored = restoredLockfile.packagesById[key] ?: return@lockfileLoop
            found = true
            if (!restored.version.equals(identity.normalizedVersion, ignoreCase = true)) {
                throw GradleException(
                    "WinApp restore lockfile ${restoredLockfile.path} resolved " +
                        "${restored.name}@${restored.version}, but $usage requires " +
                        "${identity.normalizedPackageId}@${identity.normalizedVersion}.",
                )
            }
        }
        if (!found) {
            missingRoots += identity
        }
    }
    if (missingRoots.isNotEmpty()) {
        throw GradleException(
            "WinApp restore lockfile does not contain declared packages required for $usage: " +
                missingRoots.joinToString { identity ->
                    "${identity.normalizedPackageId}@${identity.normalizedVersion}"
                },
        )
    }
}

private fun List<WinAppRestoredPackage>.associateByUniqueId(lockfilePath: Path): Map<String, WinAppRestoredPackage> {
    val duplicateIds = groupBy { pkg -> pkg.name.lowercase() }
        .filterValues { packages -> packages.size > 1 }
        .keys
    if (duplicateIds.isNotEmpty()) {
        throw GradleException(
            "WinApp restore lockfile $lockfilePath contains multiple resolved versions for: " +
                duplicateIds.sorted().joinToString(),
        )
    }
    return associateBy { pkg -> pkg.name.lowercase() }
}

private fun restoredPackageRoot(
    lockfilePath: Path,
    lockfile: WinAppRestoreLockfile,
    pkg: WinAppRestoredPackage,
): Path {
    val declaredCacheRoot = lockfile.nugetCacheDirectory
    if (declaredCacheRoot == null || !declaredCacheRoot.isAbsolute) {
        throw GradleException(
            "WinApp restore lockfile $lockfilePath has packages but no absolute nuget_cache_dir.",
        )
    }
    val cacheRoot = declaredCacheRoot.normalize()
    val packageRoot = cacheRoot
        .resolve(pkg.name.lowercase())
        .resolve(pkg.version)
        .normalize()
    if (!packageRoot.startsWith(cacheRoot)) {
        throw GradleException(
            "WinApp restore lockfile $lockfilePath contains an unsafe package path for ${pkg.name} ${pkg.version}.",
        )
    }
    return packageRoot
}

private fun JsonObject.requiredInt(name: String, path: Path): Int =
    this[name]?.jsonPrimitive?.intOrNull
        ?: throw GradleException("WinApp restore lockfile $path is missing integer '$name'.")

private fun JsonObject.requiredString(name: String, path: Path): String =
    optionalString(name)?.takeIf(String::isNotBlank)
        ?: throw GradleException("WinApp restore lockfile $path is missing string '$name'.")

private fun JsonObject.optionalString(name: String): String? =
    this[name]?.let { element -> runCatching { element.jsonPrimitive.content }.getOrNull() }

private fun JsonObject.requiredArray(name: String, path: Path): JsonArray =
    this[name]?.let { element -> runCatching { element.jsonArray }.getOrNull() }
        ?: throw GradleException("WinApp restore lockfile $path is missing array '$name'.")
