package io.github.composefluent.winrt.runtime

import kotlinx.io.files.Path

object WinRtWindowsAppSdkDeployment {
    enum class Mode {
        DynamicDependency,
        SelfContained,
    }

    class Scope internal constructor(
        val mode: Mode,
        private val activationContexts: List<AutoCloseable>,
        @Suppress("unused")
        private val loadedModules: List<RawAddress>,
        private val shutdownDynamicDependency: Boolean,
    ) : AutoCloseable {
        override fun close() {
            if (shutdownDynamicDependency) {
                platformWindowsAppSdkBootstrapShutdown()
            }
            activationContexts.asReversed().forEach { it.close() }
        }
    }

    private var processScope: Scope? = null

    fun initializeForUnpackagedApp(runtimeAssetsRoot: Path? = discoverRuntimeAssetsRoot()): Scope? {
        if (!PlatformRuntime.isWindows) {
            return null
        }
        processScope?.let { return it }
        val root = runtimeAssetsRoot ?: return null
        val processCompatibilityContext = activateProcessCompatibility(root)
        val activationContext = activateWindowsAppSdk(root)
        if (activationContext != null) {
            val module = loadSelfContainedWindowsAppRuntime(root)
            return Scope(
                mode = Mode.SelfContained,
                activationContexts = listOfNotNull(processCompatibilityContext, activationContext),
                loadedModules = listOfNotNull(module),
                shutdownDynamicDependency = false,
            ).also { processScope = it }
        }
        return initializeDynamicDependency(root, processCompatibilityContext)
            ?.also { processScope = it }
    }

    fun discoverRuntimeAssetsRoot(): Path? =
        platformDiscoverWindowsAppSdkRuntimeAssetsRoot(windowsAppRuntimeBootstrapDllName)

    private fun activateWindowsAppSdk(root: Path): AutoCloseable? {
        if (!root.isDirectory()) {
            return null
        }
        val fragments = root.walkFiles()
            .filter { path -> path.fileName.equals(liftedWinRtClassRegistrationsFileName, ignoreCase = true) }
            .sortedBy { path -> path.canonicalString() }
        if (fragments.isEmpty()) {
            return null
        }
        val dllFileNames = root.walkFiles()
            .filter { path -> path.fileName.endsWith(".dll", ignoreCase = true) }
            .map { path -> root.relativePathTo(path) }
            .distinct()
            .sorted()
        val manifestPath = platformWindowsAppSdkManifestPath(root, windowsAppSdkSelfContainedManifestFileName)
        manifestPath.writeText(buildWindowsAppSdkManifest(fragments, dllFileNames))
        platformSetWindowsEnvironmentVariable(
            windowsAppRuntimeBaseDirectoryVariableName,
            root.canonicalString().let { path -> if (path.endsWith("\\") || path.endsWith("/")) path else "$path\\" },
        )
        return platformActivateWindowsManifest(manifestPath)
    }

    private fun activateProcessCompatibility(root: Path): AutoCloseable? {
        if (!root.isDirectory()) {
            return null
        }
        val manifestPath = platformWindowsAppSdkManifestPath(root, processCompatibilityManifestFileName)
        manifestPath.writeText(buildProcessCompatibilityManifest())
        return platformActivateWindowsManifest(manifestPath)
    }

    private fun loadSelfContainedWindowsAppRuntime(root: Path): RawAddress? {
        val runtimeDll = findAsset(root, windowsAppRuntimeDllName) ?: return null
        val module = platformLoadWindowsLibrary(runtimeDll)
        val ensureIsLoaded = platformGetWindowsProcAddress(module, "WindowsAppRuntime_EnsureIsLoaded")
        platformCallWindowsAppRuntimeEnsureIsLoaded(ensureIsLoaded)
        return module
    }

    private fun initializeDynamicDependency(root: Path, processCompatibilityContext: AutoCloseable?): Scope? {
        val bootstrapDll = findAsset(root, windowsAppRuntimeBootstrapDllName) ?: return null
        val module = platformTryLoadWindowsLibrary(bootstrapDll) ?: return null
        val initialize = platformTryGetWindowsProcAddress(module, "MddBootstrapInitialize2")
        if (initialize == null) {
            platformFreeWindowsLibrary(module)
            return null
        }
        val versionInfo = discoverVersionInfo(root) ?: BootstrapVersionInfo(
            majorMinorVersion = defaultWindowsAppSdkMajorMinorVersion,
            versionTag = null,
            minVersion = defaultWindowsAppSdkMinVersion,
        )
        platformCallMddBootstrapInitialize2(
            initialize,
            versionInfo.majorMinorVersion,
            versionInfo.versionTag,
            versionInfo.minVersion,
        )
        platformRememberWindowsAppSdkBootstrapShutdown(module)
        return Scope(
            mode = Mode.DynamicDependency,
            activationContexts = listOfNotNull(processCompatibilityContext),
            loadedModules = listOf(module),
            shutdownDynamicDependency = true,
        )
    }

    private fun findAsset(root: Path, fileName: String): Path? =
        root.walkFiles()
            .filter { path -> path.fileName.equals(fileName, ignoreCase = true) }
            .sortedBy { path -> path.canonicalString() }
            .firstOrNull()

    private fun discoverVersionInfo(root: Path): BootstrapVersionInfo? {
        val header = root.parentSequence()
            .take(5)
            .map { path -> Path(path, versionInfoHeaderRelativePath) }
            .firstOrNull { path -> path.isRegularFile() }
            ?: return null
        val content = header.readText()
        val majorMinor = releaseMajorMinorRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.toInt(16)
            ?: return null
        val versionTag = releaseVersionTagRegex.find(content)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
        val minVersion = runtimeVersionRegex.find(content)
            ?.groupValues?.get(1)
            ?.removePrefix("0x")
            ?.removeSuffix("u")
            ?.toULong(16)
            ?.toLong()
            ?: return null
        return BootstrapVersionInfo(majorMinor, versionTag, minVersion)
    }

    private fun buildWindowsAppSdkManifest(fragmentPaths: List<Path>, frameworkFileNames: List<String>): String {
        val entryByFileName = linkedMapOf<String, String>()
        val remainingFileNames = linkedMapOf<String, String>()
        frameworkFileNames.forEach { fileName ->
            val key = fileName.lowercase()
            if (!remainingFileNames.containsKey(key)) {
                remainingFileNames[key] = fileName
            }
        }
        fragmentPaths.forEach { fragmentPath ->
            parseLiftedRegistrationEntries(fragmentPath.readText()).forEach { entry ->
                val body = buildString {
                    entry.activatableClasses.forEach { className ->
                        append("        <winrtv1:activatableClass name='")
                        append(escapeXml(className))
                        append("' threadingModel='both'/>\n")
                    }
                }
                if (body.isNotEmpty()) {
                    val key = entry.path.lowercase()
                    if (!entryByFileName.containsKey(key)) {
                        entryByFileName[key] = manifestFileEntry(entry.path, body)
                    }
                    remainingFileNames.remove(entry.path.lowercase())
                }
            }
        }
        return buildString {
            appendLine("<?xml version='1.0' encoding='utf-8' standalone='yes'?>")
            appendLine("<assembly manifestVersion='1.0'")
            appendLine("    xmlns:asmv3='urn:schemas-microsoft-com:asm.v3'")
            appendLine("    xmlns:winrtv1='urn:schemas-microsoft-com:winrt.v1'")
            appendLine("    xmlns='urn:schemas-microsoft-com:asm.v1'>")
            appendLine("    <assemblyIdentity type='win32' name='io.github.composefluent.winrt.windowsappsdk' version='1.0.0.0' processorArchitecture='*'/>")
            appendProcessCompatibility()
            entryByFileName.values.forEach(::append)
            remainingFileNames.values.forEach { fileName -> append(manifestFileEntry(fileName, "")) }
            appendLine("</assembly>")
        }
    }

    private fun buildProcessCompatibilityManifest(): String =
        buildString {
            appendLine("<?xml version='1.0' encoding='utf-8' standalone='yes'?>")
            appendLine("<assembly manifestVersion='1.0' xmlns='urn:schemas-microsoft-com:asm.v1'>")
            appendLine("    <assemblyIdentity type='win32' name='io.github.composefluent.winrt.process' version='1.0.0.0' processorArchitecture='*'/>")
            appendProcessCompatibility()
            appendLine("</assembly>")
        }

    private fun StringBuilder.appendProcessCompatibility() {
        appendLine("    <compatibility xmlns='urn:schemas-microsoft-com:compatibility.v1'>")
        appendLine("        <application>")
        appendLine("            <maxversiontested Id='10.0.18362.0'/>")
        appendLine("        </application>")
        appendLine("    </compatibility>")
    }

    private fun manifestFileEntry(path: String, body: String): String =
        buildString {
            append("    <asmv3:file name='")
            append(escapeXml(path))
            append("' loadFrom='%")
            append(windowsAppRuntimeBaseDirectoryVariableName)
            append("%")
            append(escapeXml(path))
            appendLine("'>")
            append(body)
            appendLine("    </asmv3:file>")
        }

    private fun parseLiftedRegistrationEntries(xml: String): List<LiftedRegistrationEntry> {
        val entries = mutableListOf<LiftedRegistrationEntry>()
        inProcessServerRegex.findAll(xml).forEach { server ->
            val body = server.groupValues[1]
            val path = pathRegex.find(body)?.groupValues?.get(1)?.decodeXmlText()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEach
            val classes = activatableClassRegex.findAll(body)
                .map { match -> match.groupValues[1].decodeXmlText().trim() }
                .filter { className -> className.isNotEmpty() }
                .toList()
            if (classes.isNotEmpty()) {
                entries += LiftedRegistrationEntry(path, classes)
            }
        }
        return entries
    }

    private fun Path.relativePathTo(child: Path): String {
        val root = canonicalString().trimEnd('\\', '/')
        val value = child.canonicalString()
        return value.removePrefix("$root\\").removePrefix("$root/").replace('/', '\\')
    }

    private fun Path.parentSequence(): Sequence<Path> = sequence {
        var current: Path? = this@parentSequence
        while (current != null) {
            yield(current)
            current = current.parentPath()
        }
    }

    private fun Path.parentPath(): Path? {
        val text = toString().trimEnd('\\', '/')
        val index = maxOf(text.lastIndexOf('\\'), text.lastIndexOf('/'))
        return if (index <= 0) null else Path(text.substring(0, index))
    }
}

internal expect fun platformDiscoverWindowsAppSdkRuntimeAssetsRoot(anchorFileName: String): Path?
internal expect fun platformWindowsAppSdkManifestPath(root: Path, fileName: String): Path
internal expect fun platformActivateWindowsManifest(manifestPath: Path): AutoCloseable
internal expect fun platformSetWindowsEnvironmentVariable(name: String, value: String)
internal expect fun platformTryLoadWindowsLibrary(path: Path): RawAddress?
internal expect fun platformLoadWindowsLibrary(path: Path): RawAddress
internal expect fun platformFreeWindowsLibrary(module: RawAddress)
internal expect fun platformTryGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress?
internal expect fun platformGetWindowsProcAddress(module: RawAddress, procedureName: String): RawAddress
internal expect fun platformCallWindowsAppRuntimeEnsureIsLoaded(procedure: RawAddress)
internal expect fun platformCallMddBootstrapInitialize2(
    procedure: RawAddress,
    majorMinorVersion: Int,
    versionTag: String?,
    minVersion: Long,
)
internal expect fun platformRememberWindowsAppSdkBootstrapShutdown(module: RawAddress)
internal expect fun platformWindowsAppSdkBootstrapShutdown()

private data class LiftedRegistrationEntry(
    val path: String,
    val activatableClasses: List<String>,
)

private data class BootstrapVersionInfo(
    val majorMinorVersion: Int,
    val versionTag: String?,
    val minVersion: Long,
)

private fun String.decodeXmlText(): String =
    replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")

private fun escapeXml(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;").replace("\"", "&quot;")

private val inProcessServerRegex = Regex("""<[^<:>]*:?\s*InProcessServer\b[^>]*>(.*?)</[^<:>]*:?\s*InProcessServer>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val pathRegex = Regex("""<[^<:>]*:?\s*Path\b[^>]*>(.*?)</[^<:>]*:?\s*Path>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val activatableClassRegex = Regex("""\bActivatableClassId\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val releaseMajorMinorRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_MAJORMINOR\s+(0x[0-9A-Fa-f]+)""")
private val releaseVersionTagRegex = Regex("""#define\s+WINDOWSAPPSDK_RELEASE_VERSION_TAG_W\s+L"([^"]*)"""")
private val runtimeVersionRegex = Regex("""#define\s+WINDOWSAPPSDK_RUNTIME_VERSION_UINT64\s+(0x[0-9A-Fa-f]+)u""")

private const val defaultWindowsAppSdkMajorMinorVersion: Int = 0x00010008
private const val defaultWindowsAppSdkMinVersion: Long = 0x1F40032608CC0000L
private const val versionInfoHeaderRelativePath = "include/WindowsAppSDK-VersionInfo.h"
private const val liftedWinRtClassRegistrationsFileName = "LiftedWinRTClassRegistrations.xml"
private const val windowsAppRuntimeBootstrapDllName = "Microsoft.WindowsAppRuntime.Bootstrap.dll"
private const val windowsAppRuntimeDllName = "Microsoft.WindowsAppRuntime.dll"
private const val windowsAppRuntimeBaseDirectoryVariableName = "MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY"
private const val windowsAppSdkSelfContainedManifestFileName = "WindowsAppSDK-SelfContained.manifest"
private const val processCompatibilityManifestFileName = "kotlin-winrt-process.manifest"
