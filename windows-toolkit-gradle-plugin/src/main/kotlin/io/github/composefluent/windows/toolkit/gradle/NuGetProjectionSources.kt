package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTNuGetPackageIdentity
import io.github.composefluent.winrt.metadata.WinRTNuGetPackageResolver
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Resolves the projection package set using the same cache-first policy for configuration and
 * task execution. Restoring a missing package remains the caller's responsibility so each
 * lifecycle can choose an appropriate output root.
 */
internal fun resolveNuGetProjectionMetadataSources(
    packageSpecs: Iterable<String>,
    explicitGlobalPackagesRoots: Iterable<Path>,
    cliGlobalPackagesRoots: Iterable<Path>,
    restoreNuGetPackages: Boolean,
    restoreMissing: (List<WinRTNuGetPackageIdentity>) -> List<Path>,
): List<WinRTMetadataSource> {
    val packageIdentities = packageSpecs
        .distinct()
        .sorted()
        .map(::parseNuGetPackageIdentity)
    if (packageIdentities.isEmpty()) {
        return emptyList()
    }

    val globalPackagesRoots = (explicitGlobalPackagesRoots + cliGlobalPackagesRoots)
        .map { root -> root.toAbsolutePath().normalize() }
        .distinctBy { root -> root.toString().lowercase() }
    val identitiesFromRoots = if (restoreNuGetPackages) {
        packageIdentities.filter { identity ->
            isNuGetPackageClosureAvailable(identity, globalPackagesRoots)
        }
    } else {
        val missing = packageIdentities.filterNot { identity ->
            isNuGetPackageClosureAvailable(identity, globalPackagesRoots)
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "NuGet packages are missing from the configured NuGet cache and " +
                    "restoreNuGetPackages is false: ${missing.joinToString()}",
            )
        }
        packageIdentities
    }
    val restoredPackageDirectories = if (restoreNuGetPackages) {
        val identitiesFromRootsSet = identitiesFromRoots.toSet()
        restoreMissing(packageIdentities.filterNot { identity -> identity in identitiesFromRootsSet })
    } else {
        emptyList()
    }

    return identitiesFromRoots.map { identity ->
        WinRTMetadataSource.nugetPackage(
            packageId = identity.normalizedPackageId,
            version = identity.normalizedVersion,
            globalPackagesRoots = globalPackagesRoots,
        )
    } + restoredPackageDirectories.map(WinRTMetadataSource::nugetPackage)
}

internal fun isNuGetPackageClosureAvailable(
    identity: WinRTNuGetPackageIdentity,
    globalPackagesRoots: Iterable<Path>,
): Boolean {
    val roots = WinRTNuGetPackageResolver.globalPackagesRoots(
        explicitRoots = globalPackagesRoots.toList(),
    )
    return runCatching {
        WinRTNuGetPackageResolver.resolveClosure(identity, roots)
    }.isSuccess
}

internal fun resolveNuGetCliGlobalPackagesRoots(
    enabled: Boolean,
    executable: String,
    cliVersion: String,
    cliCacheDirectory: Path,
    scratchDirectory: Path?,
    logger: Logger,
): List<Path> {
    if (!enabled) {
        return emptyList()
    }
    return runCatching {
        val invocation = NuGetCliSupport(
            executable = executable,
            cliVersion = cliVersion,
            cliCacheDirectory = cliCacheDirectory,
            scratchDirectory = scratchDirectory,
            logger = logger,
        ).run(
            arguments = listOf("locals", "global-packages", "-list"),
            description = "locate global-packages",
        )
        WinRTNuGetPackageResolver.parseNuGetGlobalPackagesOutput(invocation.output)
    }.getOrElse { error ->
        logger.info("NuGet CLI global-packages lookup failed: ${error.message}")
        emptyList()
    }
}

/**
 * Restores missing packages into a stable project-owned root. The lock protects the directory
 * from simultaneous configuration and task preparation; a failed install leaves no success
 * marker, so the next invocation retries it through the same NuGet CLI path.
 */
internal fun restoreNuGetPackagesToDirectory(
    packageIdentities: List<WinRTNuGetPackageIdentity>,
    installRoot: Path,
    nuGetCli: NuGetCliSupport,
): List<Path> {
    if (packageIdentities.isEmpty()) {
        return emptyList()
    }
    Files.createDirectories(installRoot)
    val lockPath = installRoot.resolve(".kotlin-winrt-restore.lock")
    FileChannel.open(
        lockPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
    ).use { channel ->
        channel.lock().use {
            val missing = packageIdentities.filterNot { identity ->
                isNuGetPackageClosureAvailable(identity, listOf(installRoot))
            }
            missing.forEach { identity ->
                nuGetCli.run(
                    arguments = listOf(
                        "install",
                        identity.normalizedPackageId,
                        "-Version",
                        identity.normalizedVersion,
                        "-NonInteractive",
                        "-OutputDirectory",
                        installRoot.toString(),
                    ),
                    workingDirectory = installRoot,
                    description = "install $identity",
                )
            }
        }
    }
    return packageIdentities
        .flatMap { identity -> WinRTNuGetPackageResolver.resolveClosure(identity, listOf(installRoot)) }
        .map { it.packageRoot }
        .distinct()
        .sortedBy { it.toString().lowercase() }
}
