package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.runtime.ActivationResult
import io.github.composefluent.winrt.runtime.ComWrappersSupport
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.KnownHResults
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.PlatformRuntime
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import java.net.JarURLConnection
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

data class WinRtAuthoringHostManifest(
    val assemblyName: String,
    val hostExportsClass: String,
    val targetArtifact: String,
    val activatableClasses: List<String>,
    val activatableClassTargets: Map<String, String>,
    val sourceDirectory: Path? = null,
)

object WinRtAuthoringHostManifestLoader {
    private const val RUNTIME_ASSETS_RESOURCE_DIRECTORY = "kotlin-winrt-runtime-assets"

    private data class HostExportEntry(
        val hostExportsClass: String,
        val classLoader: ClassLoader?,
    )

    fun read(path: Path): WinRtAuthoringHostManifest {
        return readContent(Files.readString(path), path.parent)
    }

    private fun readContent(
        content: String,
        sourceDirectory: Path?,
    ): WinRtAuthoringHostManifest =
        WinRtAuthoringHostManifest(
            assemblyName = readJsonString(content, "assemblyName").orEmpty(),
            hostExportsClass = readJsonString(content, "hostExportsClass").orEmpty(),
            targetArtifact = readJsonString(content, "targetArtifact").orEmpty(),
            activatableClasses = readJsonStringArray(content, "activatableClasses"),
            activatableClassTargets = readJsonStringMap(content, "activatableClassTargets"),
            sourceDirectory = sourceDirectory,
        )

    fun installFromDirectory(directory: Path) {
        install(readDirectory(directory))
    }

    fun installFromRuntimeAssets(
        classLoader: ClassLoader = Thread.currentThread().contextClassLoader ?: WinRtAuthoringHostManifestLoader::class.java.classLoader,
    ) {
        val resources = classLoader.getResources(RUNTIME_ASSETS_RESOURCE_DIRECTORY).toList()
        resources.forEach { resource ->
            when {
                resource.protocol.equals("file", ignoreCase = true) -> installFromDirectory(Paths.get(resource.toURI()))
                resource.protocol.equals("jar", ignoreCase = true) -> install(readJarDirectory(resource))
            }
        }
    }

    fun install(manifests: List<WinRtAuthoringHostManifest>) {
        val entries = manifests
            .filter { it.hostExportsClass.isNotBlank() && it.runtimeClassNames().isNotEmpty() }
            .flatMap { manifest ->
                val entry = manifest.hostExportEntry()
                manifest.runtimeClassNames().map { runtimeClassName -> runtimeClassName to entry }
            }
            .toMap()
        if (entries.isEmpty()) {
            return
        }
        ComWrappersSupport.registerAuthoringActivationFactoryFallback { runtimeClassName, interfaceId ->
            val entry = entries[runtimeClassName]
                ?: return@registerAuthoringActivationFactoryFallback ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)
            activate(entry, runtimeClassName, interfaceId)
        }
    }

    private fun readDirectory(directory: Path): List<WinRtAuthoringHostManifest> {
        if (!Files.isDirectory(directory)) {
            return emptyList()
        }
        return Files.list(directory).use { stream ->
            stream
                .filter { it.isRegularFile() && it.extension.equals("json", ignoreCase = true) && it.fileName.toString().endsWith(".host.json", ignoreCase = true) }
                .map(::read)
                .toList()
        }
    }

    private fun readJarDirectory(resource: java.net.URL): List<WinRtAuthoringHostManifest> {
        val connection = resource.openConnection() as? JarURLConnection ?: return emptyList()
        val prefix = connection.entryName.trimEnd('/') + "/"
        return connection.jarFile.use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith(prefix) && it.name.endsWith(".host.json", ignoreCase = true) }
                .map { entry ->
                    jar.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                        readContent(reader.readText(), sourceDirectory = null)
                    }
                }
                .toList()
        }
    }

    private fun activate(
        entry: HostExportEntry,
        runtimeClassName: String,
        interfaceId: Guid,
    ): ActivationResult {
        if (!PlatformRuntime.isWindows) {
            return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)
        }
        // Bounded host-shim parity path: CsWinRT's
        // `.cswinrt/src/Authoring/WinRT.Host.Shim/Module.cs` loads the target
        // assembly and invokes its generated activation-factory entrypoint by
        // reflection. Generated/native Kotlin hosts still enter the fixed
        // `WinRTAuthoringHostExports` JNI path; do not expand this into a
        // general projection-runtime discovery mechanism.
        val exportsClass = Class.forName(entry.hostExportsClass, true, entry.classLoader ?: defaultHostClassLoader())
        runCatching {
            exportsClass.getMethod("registerActivationFactories").invoke(exportsInstance(exportsClass))
        }
        PlatformAbi.confinedScope().use { scope ->
            HString.createReference(runtimeClassName).use { classId ->
                val factoryOut = PlatformAbi.allocatePointerSlot(scope)
                val hResult = exportsClass
                    .getMethod("dllGetActivationFactoryAddress", java.lang.Long.TYPE, java.lang.Long.TYPE)
                    .invoke(exportsInstance(exportsClass), classId.handle.value, factoryOut.value) as Int
                if (hResult < 0) {
                    return ActivationResult(KnownHResults.REGDB_E_CLASSNOTREG, PlatformAbi.nullPointer)
                }
                val factory = PlatformAbi.readPointer(factoryOut)
                if (PlatformAbi.isNull(factory) || interfaceId == IID.IActivationFactory) {
                    return ActivationResult(KnownHResults.S_OK, factory)
                }
                return try {
                    val result = WinRtPlatformApi.queryInterfaceRaw(factory, interfaceId)
                    ActivationResult(HResult(result.hResultValue), result.pointer)
                } finally {
                    WinRtPlatformApi.releaseRaw(factory)
                }
            }
        }
    }

    private fun exportsInstance(exportsClass: Class<*>): Any? =
        runCatching { exportsClass.getField("INSTANCE").get(null) }.getOrNull()

    private fun WinRtAuthoringHostManifest.hostExportEntry(): HostExportEntry =
        HostExportEntry(
            hostExportsClass = hostExportsClass,
            classLoader = targetArtifactClassLoader(),
        )

    private fun WinRtAuthoringHostManifest.targetArtifactClassLoader(): ClassLoader? {
        val directory = sourceDirectory ?: return defaultHostClassLoader()
        val artifactName = targetArtifact.takeIf { it.isNotBlank() } ?: return defaultHostClassLoader()
        val artifact = directory.resolve(artifactName)
        if (!artifact.isRegularFile()) {
            return defaultHostClassLoader()
        }
        return URLClassLoader(arrayOf(artifact.toUri().toURL()), defaultHostClassLoader())
    }

    private fun defaultHostClassLoader(): ClassLoader? =
        Thread.currentThread().contextClassLoader ?: WinRtAuthoringHostManifestLoader::class.java.classLoader

    private fun readJsonString(content: String, name: String): String? =
        Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.decodeJsonString()

    private fun readJsonStringArray(content: String, name: String): List<String> {
        val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
            .find(content) ?: return emptyList()
        return Regex(""""((?:\\.|[^"\\])*)"""")
            .findAll(match.groupValues[1])
            .map { it.groupValues[1].decodeJsonString() }
            .toList()
    }

    private fun readJsonStringMap(content: String, name: String): Map<String, String> {
        val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
            .find(content) ?: return emptyMap()
        return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
            .findAll(match.groupValues[1])
            .associate { it.groupValues[1].decodeJsonString() to it.groupValues[2].decodeJsonString() }
    }

    private fun String.decodeJsonString(): String =
        replace("\\\"", "\"").replace("\\\\", "\\")

    private fun WinRtAuthoringHostManifest.runtimeClassNames(): List<String> =
        (activatableClasses + activatableClassTargets.keys).distinct()
}
