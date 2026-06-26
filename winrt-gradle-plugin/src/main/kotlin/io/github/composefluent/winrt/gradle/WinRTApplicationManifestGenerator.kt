package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

internal object WinRTApplicationManifestGenerator {
    fun writeApplicationManifest(outputRoot: Path, executableBaseName: String) {
        val fragmentXmls = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { path -> path.isRegularFile() && path.name.equals("LiftedWinRTClassRegistrations.xml", ignoreCase = true) }
                .sorted()
                .map { path -> Files.readString(path) }
                .toList()
        }
        val dllFileNames = Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { path -> path.isRegularFile() && path.name.endsWith(".dll", ignoreCase = true) }
                .map { path -> path.relativeTo(outputRoot).toString().replace('/', '\\') }
                .distinct()
                .sorted()
                .toList()
        }
        Files.writeString(
            outputRoot.resolve("$executableBaseName.exe.manifest"),
            buildApplicationManifest(fragmentXmls, readAuthoredHostManifestRegistrations(outputRoot), dllFileNames),
        )
    }

    private fun buildApplicationManifest(
        fragmentXmls: List<String>,
        authoredHostRegistrations: List<LiftedRegistrationEntry>,
        frameworkFileNames: List<String>,
    ): String {
        val entriesByFileName = linkedMapOf<String, LiftedRegistrationEntryBuilder>()
        val remainingFileNames = linkedMapOf<String, String>()
        frameworkFileNames.forEach { fileName -> remainingFileNames.putIfAbsent(fileName.lowercase(), fileName) }
        (fragmentXmls.flatMap(::parseLiftedRegistrationEntries) + authoredHostRegistrations)
            .forEach { entry ->
                val key = entry.path.lowercase()
                val builder = entriesByFileName.getOrPut(key) { LiftedRegistrationEntryBuilder(entry.path) }
                entry.activatableClasses
                    .filter(String::isNotBlank)
                    .forEach(builder.activatableClasses::add)
                if (builder.activatableClasses.isNotEmpty()) {
                    remainingFileNames.remove(key)
                }
            }
        return buildString {
            appendLine("<?xml version='1.0' encoding='utf-8' standalone='yes'?>")
            appendLine("<assembly manifestVersion='1.0'")
            appendLine("    xmlns:asmv3='urn:schemas-microsoft-com:asm.v3'")
            appendLine("    xmlns:winrtv1='urn:schemas-microsoft-com:winrt.v1'")
            appendLine("    xmlns='urn:schemas-microsoft-com:asm.v1'>")
            appendLine("    <assemblyIdentity type='win32' name='$executableAssemblyName' version='1.0.0.0' processorArchitecture='*'/>")
            appendLine("    <compatibility xmlns='urn:schemas-microsoft-com:compatibility.v1'>")
            appendLine("        <application>")
            appendLine("            <maxversiontested Id='10.0.18362.0'/>")
            appendLine("        </application>")
            appendLine("    </compatibility>")
            appendLine("    <asmv3:application>")
            appendLine("        <asmv3:windowsSettings>")
            appendLine("            <dpiAware xmlns='http://schemas.microsoft.com/SMI/2005/WindowsSettings'>true/PM</dpiAware>")
            appendLine("            <dpiAwareness xmlns='http://schemas.microsoft.com/SMI/2016/WindowsSettings'>PerMonitorV2, PerMonitor</dpiAwareness>")
            appendLine("        </asmv3:windowsSettings>")
            appendLine("    </asmv3:application>")
            entriesByFileName.values.forEach { entry ->
                val body = buildString {
                    entry.activatableClasses.forEach { className ->
                        append("        <winrtv1:activatableClass name='")
                        append(escapeXml(className))
                        append("' threadingModel='both'/>\n")
                    }
                }
                if (body.isNotEmpty()) {
                    append(manifestFileEntry(entry.path, body))
                }
            }
            remainingFileNames.values.forEach { fileName -> append(manifestFileEntry(fileName, "")) }
            appendLine("</assembly>")
        }
    }

    private fun manifestFileEntry(path: String, body: String): String =
        buildString {
            append("    <asmv3:file name='")
            append(escapeXml(path))
            append("' loadFrom='%MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY%")
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

    private fun readAuthoredHostManifestRegistrations(outputRoot: Path): List<LiftedRegistrationEntry> {
        val jvmRegistrations = readJvmAuthoringHostRuntimeConfigRegistrations(outputRoot)
        val jvmAuthoredClasses = jvmRegistrations
            .flatMap { entry -> entry.activatableClasses }
            .toSet()
        val entriesByFileName = linkedMapOf<String, LiftedRegistrationEntryBuilder>()
        jvmRegistrations.forEach { entry ->
            val key = entry.path.lowercase()
            val builder = entriesByFileName.getOrPut(key) { LiftedRegistrationEntryBuilder(entry.path) }
            entry.activatableClasses.forEach(builder.activatableClasses::add)
        }
        Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { path -> path.isRegularFile() && path.name.endsWith(".host.json", ignoreCase = true) }
                .sorted()
                .forEach { manifest ->
                    readAuthoredHostManifestEntries(manifest, jvmAuthoredClasses).forEach { entry ->
                        val key = entry.path.lowercase()
                        val builder = entriesByFileName.getOrPut(key) { LiftedRegistrationEntryBuilder(entry.path) }
                        entry.activatableClasses.forEach(builder.activatableClasses::add)
                    }
                }
        }
        return entriesByFileName.values
            .map { entry -> LiftedRegistrationEntry(entry.path, entry.activatableClasses.toList()) }
    }

    private fun readJvmAuthoringHostRuntimeConfigRegistrations(outputRoot: Path): List<LiftedRegistrationEntry> {
        val entries = mutableListOf<LiftedRegistrationEntry>()
        Files.walk(outputRoot).use { stream ->
            stream.asSequence()
                .filter { path -> path.isRegularFile() && path.name.endsWith(".runtimeconfig.json", ignoreCase = true) }
                .sorted()
                .forEach { runtimeConfig ->
                    val content = runCatching { Files.readString(runtimeConfig) }.getOrDefault("")
                    val classes = readManifestJsonStringMap(content, "activatableClasses")
                        .filterValues { target -> target.endsWith(".jar", ignoreCase = true) }
                        .keys
                        .filter { className -> className.isNotBlank() }
                        .sorted()
                    if (classes.isNotEmpty()) {
                        entries += LiftedRegistrationEntry(runtimeConfig.name.removeSuffix(".runtimeconfig.json") + ".dll", classes)
                    }
                }
        }
        return entries
    }

    private fun readAuthoredHostManifestEntries(
        manifest: Path,
        jvmAuthoredClasses: Set<String>,
    ): List<LiftedRegistrationEntry> {
        val content = runCatching { Files.readString(manifest) }.getOrDefault("")
        val assemblyName = readManifestJsonString(content, "assemblyName").orEmpty()
        val targetArtifact = readManifestJsonString(content, "targetArtifact").orEmpty()
        val defaultTargetClasses = readManifestJsonStringArrayField(content, "activatableClasses")
            .mapNotNull { className -> authoredHostDllForTarget(assemblyName, targetArtifact)?.let { dll -> dll to className } }
        val explicitTargetClasses = readManifestJsonStringMap(content, "activatableClassTargets")
            .mapNotNull { (className, target) -> authoredHostDllForTarget(assemblyName, target)?.let { dll -> dll to className } }
        return (defaultTargetClasses + explicitTargetClasses)
            .filter { (_, className) -> className.isNotBlank() }
            .filterNot { (dll, className) -> className in jvmAuthoredClasses && !dll.equals("$assemblyName.dll", ignoreCase = true) }
            .groupBy({ it.first }, { it.second })
            .map { (dll, classes) -> LiftedRegistrationEntry(dll, classes.distinct().sorted()) }
    }

    private const val executableAssemblyName = "io.github.composefluent.winrt.application"
}

private data class LiftedRegistrationEntry(
    val path: String,
    val activatableClasses: List<String>,
)

private data class LiftedRegistrationEntryBuilder(
    val path: String,
    val activatableClasses: LinkedHashSet<String> = linkedSetOf(),
)

private fun authoredHostDllForTarget(assemblyName: String, targetArtifact: String): String? =
    when {
        targetArtifact.endsWith(".dll", ignoreCase = true) -> targetArtifact
        targetArtifact.endsWith(".jar", ignoreCase = true) && assemblyName.isNotBlank() -> "$assemblyName.dll"
        else -> null
    }

private fun readManifestJsonString(content: String, name: String): String? =
    Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(content)
        ?.groupValues
        ?.get(1)
        ?.decodeManifestJsonString()

private fun readManifestJsonStringMap(content: String, name: String): Map<String, String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyMap()
    return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .associate { it.groupValues[1].decodeManifestJsonString() to it.groupValues[2].decodeManifestJsonString() }
}

private fun readManifestJsonStringArrayField(content: String, name: String): List<String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1].decodeManifestJsonString() }
        .toList()
}

private fun String.decodeManifestJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\")

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
