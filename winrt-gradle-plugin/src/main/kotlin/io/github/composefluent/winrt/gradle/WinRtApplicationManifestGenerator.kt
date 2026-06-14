package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

internal object WinRtApplicationManifestGenerator {
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
            buildApplicationManifest(fragmentXmls, dllFileNames),
        )
    }

    private fun buildApplicationManifest(fragmentXmls: List<String>, frameworkFileNames: List<String>): String {
        val entryByFileName = linkedMapOf<String, String>()
        val remainingFileNames = linkedMapOf<String, String>()
        frameworkFileNames.forEach { fileName -> remainingFileNames.putIfAbsent(fileName.lowercase(), fileName) }
        fragmentXmls.forEach { xml ->
            parseLiftedRegistrationEntries(xml).forEach { entry ->
                val body = buildString {
                    entry.activatableClasses.forEach { className ->
                        append("        <winrtv1:activatableClass name='")
                        append(escapeXml(className))
                        append("' threadingModel='both'/>\n")
                    }
                }
                if (body.isNotEmpty()) {
                    entryByFileName.putIfAbsent(entry.path.lowercase(), manifestFileEntry(entry.path, body))
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
            appendLine("    <assemblyIdentity type='win32' name='$executableAssemblyName' version='1.0.0.0' processorArchitecture='*'/>")
            appendLine("    <compatibility xmlns='urn:schemas-microsoft-com:compatibility.v1'>")
            appendLine("        <application>")
            appendLine("            <maxversiontested Id='10.0.18362.0'/>")
            appendLine("        </application>")
            appendLine("    </compatibility>")
            entryByFileName.values.forEach(::append)
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

    private const val executableAssemblyName = "io.github.composefluent.winrt.application"
}

private data class LiftedRegistrationEntry(
    val path: String,
    val activatableClasses: List<String>,
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
