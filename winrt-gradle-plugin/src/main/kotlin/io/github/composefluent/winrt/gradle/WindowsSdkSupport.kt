package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WindowsSdkRootDiscovery
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

internal fun WinRTMetadataSource.withWindowsSdkRegistryRoots(
    registryRoots: List<Path>?,
): WinRTMetadataSource =
    if (this is WinRTMetadataSource.WindowsSdk &&
        this.sdkRoot == null &&
        this.registryRoots == null &&
        registryRoots != null
    ) {
        copy(registryRoots = registryRoots)
    } else {
        this
    }

internal data class WindowsSdkLayout(
    val root: Path,
    val version: String,
    val includeRoot: Path,
    val libRoot: Path,
    val binRoot: Path,
) {
    fun tool(name: String, architecture: String): Path? {
        val versionedTool = binRoot.resolve(architecture).resolve(name)
        if (versionedTool.isRegularFile()) {
            return versionedTool
        }
        val directTool = binRoot.resolve(name)
        return directTool.takeIf { it.isRegularFile() }
    }
}

internal fun findWindowsSdk(
    version: String? = null,
    registryRoots: List<String>? = null,
): WindowsSdkLayout? {
    val requestedVersion = version?.takeIf(String::isNotBlank)
    val roots = registryRoots?.let { suppliedRoots ->
        WindowsSdkRootDiscovery.candidateRoots(registryRoots = suppliedRoots)
    } ?: WindowsSdkRootDiscovery.candidateRootsWithRegistry()
    return roots
        .asSequence()
        .mapNotNull { root -> findWindowsSdk(root, requestedVersion) }
        .firstOrNull()
}

/** An empty ValueSource result means that the registry should be queried at execution time. */
internal fun List<String>.orNullIfEmpty(): List<String>? = takeIf { it.isNotEmpty() }

private fun findWindowsSdk(root: Path, requestedVersion: String?): WindowsSdkLayout? {
    val resolvedVersion = requestedVersion ?: latestWindowsSdkVersion(root) ?: return null
    val includeRoot = root.resolve("Include").resolve(resolvedVersion)
    val libRoot = root.resolve("Lib").resolve(resolvedVersion)
    val binRoot = root.resolve("bin").resolve(resolvedVersion)
    if (!includeRoot.isDirectory() || !libRoot.isDirectory()) {
        return null
    }
    return WindowsSdkLayout(
        root = root,
        version = resolvedVersion,
        includeRoot = includeRoot,
        libRoot = libRoot,
        binRoot = binRoot,
    )
}

internal fun windowsSdkArchitecture(runtimeIdentifier: String): String {
    val rid = runtimeIdentifier.lowercase()
    return when {
        rid.endsWith("-arm64") -> "arm64"
        rid.endsWith("-x86") -> "x86"
        else -> "x64"
    }
}

internal fun winRTManifestProcessorArchitecture(runtimeIdentifier: String): String {
    val rid = runtimeIdentifier.lowercase()
    return when {
        rid.endsWith("-arm64") -> "arm64"
        rid.endsWith("-x86") -> "x86"
        else -> "amd64"
    }
}

private fun latestWindowsSdkVersion(root: Path): String? {
    val includeRoot = root.resolve("Include")
    if (!includeRoot.isDirectory()) {
        return null
    }
    return Files.list(includeRoot).use { stream ->
        stream
            .filter { it.isDirectory() }
            .map { it.name }
            .filter { WINDOWS_SDK_VERSION.matches(it) }
            .filter { includeRoot.resolve(it).resolve("um").isDirectory() }
            .sorted(Comparator.reverseOrder())
            .findFirst()
            .orElse(null)
    }
}

private val WINDOWS_SDK_VERSION = Regex("""\d+\.\d+\.\d+\.\d+""")
