package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRTNuGetPackageResolver
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal object WinAppRestoreLockfileReader {
    const val SUPPORTED_SCHEMA: Int = WinAppRestoreLockfileCodec.SUPPORTED_SCHEMA
    fun read(path: Path): WinAppRestoreLockfile = try {
        WinAppRestoreLockfileCodec.read(path)
    } catch (error: Exception) {
        throw GradleException(error.message ?: "Cannot read WinApp restore lockfile $path.", error)
    }
}

/** A missing task-produced lock must fail rather than activate an unrelated NuGet downloader. */
internal fun projectionRestoreLockFiles(files: Iterable<File>): List<File> =
    files.toList().also { locks ->
            val missing = locks.filterNot(File::isFile)
            if (missing.isNotEmpty()) {
                throw GradleException("WinApp restore lockfiles are missing: ${missing.joinToString()}. Run restoreWinAppDependencies first.")
            }
    }

/** CsWinRT's resolved-input boundary: package references always use the authoritative restore. */
internal fun resolveWinAppProjectionSources(
    explicitSources: List<WinRTMetadataSource>,
    packageSpecs: List<String>,
    lockFiles: Iterable<File>,
): List<WinRTMetadataSource> {
    val references = explicitSources.filterIsInstance<WinRTMetadataSource.NuGetPackageReference>()
    val specs = (packageSpecs + references.map { "${it.packageId}@${it.version}" }).distinct()
    val localSources = explicitSources.filterNot { it is WinRTMetadataSource.NuGetPackageReference }
    if (specs.isEmpty()) return localSources
    return localSources + readWinAppProjectionWinmdFiles(
        projectionRestoreLockFiles(lockFiles), specs,
    ).map(WinRTMetadataSource::path)
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
            .flatMap { packageEntry ->
                val packageRoot = restoredPackageRoot(
                    restoredLockfile.path,
                    restoredLockfile.lockfile,
                    packageEntry,
                )
                packageEntry.winmdFiles + discoverWinmdFiles(packageRoot)
            }
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
    return try { lockfile.packageRoot(lockfilePath, pkg) }
    catch (error: IllegalArgumentException) { throw GradleException(error.message.orEmpty(), error) }
}

/**
 * WinApp CLI versions may not recognize every valid NuGet metadata layout when writing
 * winmds.lock.json. The lockfile still gives us the resolved package root, so use that
 * root as the boundary for a layout-agnostic metadata fallback. This deliberately does
 * not inspect any global cache or infer a package-specific directory convention.
 */
private fun discoverWinmdFiles(packageRoot: Path): List<Path> {
    if (!Files.isDirectory(packageRoot)) {
        return emptyList()
    }
    return runCatching {
        Files.walk(packageRoot).use { stream ->
            stream
                .filter(Files::isRegularFile)
                .filter { path -> path.fileName.toString().endsWith(".winmd", ignoreCase = true) }
                .map { path -> path.toAbsolutePath().normalize() }
                .sorted()
                .toList()
        }
    }.getOrDefault(emptyList())
}
