package io.github.composefluent.winrt.runtime

import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal object WinRTRuntimeAssets {
    const val runtimeAssetsRootPropertyName: String = "kotlin.winrt.runtimeAssetsRoot"
    const val runtimeAssetsDirectoryName: String = "kotlin-winrt-runtime-assets"

    fun resolveAssetPath(fileName: String): Path? =
        explicitRuntimeAssetsRoot()?.resolve(fileName)?.takeIf { it.isRegularFile() }
            ?: classLoaderResourceAssetPath(fileName)
            ?: classpathRuntimeAssetPath(fileName)
            ?: workingDirectoryRuntimeAssetPath(fileName)

    fun discoverRuntimeAssetsRoot(anchorFileName: String? = null): Path? =
        explicitRuntimeAssetsRoot()
            ?: anchorFileName?.let { classLoaderResourceAssetPath(it)?.parent }
            ?: classLoaderResourceRoot()
            ?: classpathRuntimeAssetsRoot()
            ?: workingDirectoryRuntimeAssetsRoot()

    private fun explicitRuntimeAssetsRoot(): Path? =
        System.getProperty(runtimeAssetsRootPropertyName)
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)

    private fun classLoaderResourceAssetPath(fileName: String): Path? {
        val resourceName = "$runtimeAssetsDirectoryName/$fileName"
        return classLoaders().firstNotNullOfOrNull { loader ->
            loader.getResource(resourceName)?.toFilePath()?.takeIf { it.isRegularFile() }
        }
    }

    private fun classLoaderResourceRoot(): Path? =
        classLoaders().firstNotNullOfOrNull { loader ->
            loader.getResource(runtimeAssetsDirectoryName)?.toFilePath()?.takeIf { it.isDirectory() }
        }

    private fun classpathRuntimeAssetPath(fileName: String): Path? =
        classpathEntries()
            .flatMap { entry -> runtimeAssetCandidates(entry, fileName) }
            .firstOrNull { it.isRegularFile() }

    private fun classpathRuntimeAssetsRoot(): Path? =
        classpathEntries()
            .flatMap(::runtimeAssetsRootCandidates)
            .firstOrNull { it.isDirectory() }

    private fun workingDirectoryRuntimeAssetPath(fileName: String): Path? =
        workingDirectoryRuntimeAssetsRoot()
            ?.resolve(fileName)
            ?.takeIf { it.isRegularFile() }

    private fun workingDirectoryRuntimeAssetsRoot(): Path? =
        workingDirectoryRuntimeAssetsRootCandidates()
            .firstOrNull { it.isDirectory() }

    private fun classLoaders(): Sequence<ClassLoader> =
        sequenceOf(
            Thread.currentThread().contextClassLoader,
            WinRTRuntimeAssets::class.java.classLoader,
        ).filterNotNull().distinct()

    private fun classpathEntries(): Sequence<Path> =
        System.getProperty("java.class.path")
            .orEmpty()
            .split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { entry -> runCatching { Path.of(entry) }.getOrNull() }

    private fun runtimeAssetCandidates(classpathEntry: Path, fileName: String): Sequence<Path> =
        runtimeAssetsRootCandidates(classpathEntry).map { root -> root.resolve(fileName) }

    private fun runtimeAssetsRootCandidates(classpathEntry: Path): Sequence<Path> = sequence {
        if (classpathEntry.isDirectory()) {
            yield(classpathEntry.resolve(runtimeAssetsDirectoryName))
            yield(classpathEntry.resolve("kotlin-winrt").resolve("runtime-assets"))
        }
        val applicationHome = classpathEntry.parent?.parent
        if (applicationHome != null) {
            yield(applicationHome.resolve(runtimeAssetsDirectoryName))
            yield(applicationHome.resolve("kotlin-winrt").resolve("runtime-assets"))
        }
    }

    private fun workingDirectoryRuntimeAssetsRootCandidates(): Sequence<Path> = sequence {
        val workingDirectory = Path.of(System.getProperty("user.dir") ?: ".")
        yield(workingDirectory.resolve(runtimeAssetsDirectoryName))
        yield(workingDirectory.resolve("kotlin-winrt").resolve("runtime-assets"))
        yield(workingDirectory.resolve("build").resolve("kotlin-winrt").resolve("runtime-assets"))
    }

    private fun java.net.URL.toFilePath(): Path? {
        if (!protocol.equals("file", ignoreCase = true)) {
            return null
        }
        return runCatching { Path.of(toURI()) }.getOrNull()
    }
}
