package io.github.composefluent.winrt.gradle

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence
import javax.xml.parsers.DocumentBuilderFactory

internal data class MsBuildCopyLocalPayload(
    val source: Path,
    val targetRelativePath: Path,
)

internal object WinRTNuGetMsBuildPayloadResolver {
    fun resolveCopyLocalPayloads(
        packageRoot: Path,
        runtimeIdentifier: String,
    ): List<MsBuildCopyLocalPayload> {
        val platform = msBuildPlatform(runtimeIdentifier)
        val evaluator = Evaluator(
            packageRoot = packageRoot,
            initialProperties = linkedMapOf(
                "Platform" to platform,
                "RuntimeIdentifier" to runtimeIdentifier,
                "NETCoreSdkRuntimeIdentifier" to runtimeIdentifier,
                "TargetFramework" to "",
                "AppxPackage" to "false",
                "WebView2UseWinRT" to "true",
                "WebView2ProjectKind" to "native",
                "WindowsAppSDKFrameworkPackage" to "false",
                "WindowsAppSdkUndockedRegFreeWinRTInitialize" to "true",
                "WindowsAppSDKBackgroundTask" to "false",
            ),
        )
        evaluator.evaluatePackageBuildFiles()
        return evaluator.payloads()
    }

    private fun msBuildPlatform(runtimeIdentifier: String): String {
        val rid = runtimeIdentifier.lowercase()
        return when {
            rid.endsWith("-x86") -> "Win32"
            rid.endsWith("-arm64") -> "arm64"
            else -> "x64"
        }
    }

    private class Evaluator(
        private val packageRoot: Path,
        initialProperties: Map<String, String>,
    ) {
        private val properties = linkedMapOf<String, String>().apply { putAll(initialProperties) }
        private val payloads = linkedMapOf<String, MsBuildCopyLocalPayload>()
        private val evaluatedFiles = linkedSetOf<Path>()
        private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

        fun evaluatePackageBuildFiles() {
            listOf(
                packageRoot.resolve("build").resolve("native"),
                packageRoot.resolve("buildTransitive").resolve("native"),
            ).forEach(::evaluateBuildDirectory)
        }

        fun payloads(): List<MsBuildCopyLocalPayload> = payloads.values.toList()

        private fun evaluateBuildDirectory(directory: Path) {
            if (!Files.isDirectory(directory)) {
                return
            }
            Files.walk(directory).use { stream ->
                stream.asSequence()
                    .filter { path -> path.isRegularFile() && (path.name.endsWith(".props", true) || path.name.endsWith(".targets", true)) }
                    .sortedWith(compareBy<Path> { if (it.name.endsWith(".props", true)) 0 else 1 }.thenBy { it.toString() })
                    .forEach(::evaluateFile)
            }
        }

        private fun evaluateFile(file: Path) {
            val normalizedFile = file.toAbsolutePath().normalize()
            if (!evaluatedFiles.add(normalizedFile) || !normalizedFile.isRegularFile()) {
                return
            }
            val previousDirectory = properties["MSBuildThisFileDirectory"]
            properties["MSBuildThisFileDirectory"] = normalizedFile.parent.toString().trimEnd('\\', '/') + "\\"
            try {
                val project = runCatching {
                    documentBuilderFactory.newDocumentBuilder()
                        .parse(normalizedFile.toFile())
                        .documentElement
                }.getOrNull() ?: return
                processChildren(project)
            } finally {
                if (previousDirectory == null) {
                    properties.remove("MSBuildThisFileDirectory")
                } else {
                    properties["MSBuildThisFileDirectory"] = previousDirectory
                }
            }
        }

        private fun processChildren(parent: Element) {
            parent.childElements().forEach { element ->
                when (element.localTagName()) {
                    "Import" -> processImport(element)
                    "PropertyGroup" -> processPropertyGroup(element)
                    "ItemGroup" -> processItemGroup(element)
                    "Choose" -> processChoose(element)
                }
            }
        }

        private fun processImport(element: Element) {
            if (!conditionPasses(element.getAttribute("Condition"))) {
                return
            }
            val projectPath = expandProperties(element.getAttribute("Project")).takeIf(String::isNotBlank) ?: return
            evaluateFile(Path.of(projectPath).toAbsolutePath().normalize())
        }

        private fun processPropertyGroup(element: Element) {
            if (!conditionPasses(element.getAttribute("Condition"))) {
                return
            }
            element.childElements().forEach { property ->
                if (conditionPasses(property.getAttribute("Condition"))) {
                    properties[property.localTagName()] = expandProperties(property.textContent.trim())
                }
            }
        }

        private fun processItemGroup(element: Element) {
            if (!conditionPasses(element.getAttribute("Condition"))) {
                return
            }
            element.childElements().forEach { item ->
                if (!conditionPasses(item.getAttribute("Condition"))) {
                    return@forEach
                }
                when (item.localTagName()) {
                    "ReferenceCopyLocalPaths", "Content", "None" -> addCopyLocalItem(item)
                }
            }
        }

        private fun processChoose(element: Element) {
            element.childElements().forEach { branch ->
                when (branch.localTagName()) {
                    "When" -> {
                        if (conditionPasses(branch.getAttribute("Condition"))) {
                            processChildren(branch)
                            return
                        }
                    }
                    "Otherwise" -> {
                        processChildren(branch)
                        return
                    }
                }
            }
        }

        private fun addCopyLocalItem(item: Element) {
            val include = expandProperties(item.getAttribute("Include")).takeIf(String::isNotBlank) ?: return
            val destinationSubDirectory = item.metadata("DestinationSubDirectory")
            val link = item.metadata("Link")
            expandInclude(include).forEach { source ->
                val target = copyLocalTargetPath(source, include, destinationSubDirectory, link) ?: return@forEach
                val normalizedSource = source.toAbsolutePath().normalize()
                payloads.putIfAbsent(
                    target.toString().lowercase(),
                    MsBuildCopyLocalPayload(normalizedSource, target),
                )
            }
        }

        private fun expandInclude(include: String): List<Path> {
            val normalizedInclude = include.replace('/', '\\')
            if (!normalizedInclude.contains('*')) {
                return Path.of(normalizedInclude)
                    .toAbsolutePath()
                    .normalize()
                    .takeIf { it.isRegularFile() }
                    ?.let(::listOf)
                    .orEmpty()
            }
            val base = wildcardBase(normalizedInclude)
            val relativeGlob = wildcardRelativeGlob(normalizedInclude)
            if (!Files.isDirectory(base)) {
                return emptyList()
            }
            val matcher = Regex("^${globToRegex(relativeGlob)}$", RegexOption.IGNORE_CASE)
            return Files.walk(base).use { stream ->
                stream.asSequence()
                    .filter(Path::isRegularFile)
                    .filter { path -> matcher.matches(path.relativeTo(base).toString().replace('/', '\\')) }
                    .sorted()
                    .toList()
            }
        }

        private fun copyLocalTargetPath(
            source: Path,
            include: String,
            destinationSubDirectory: String?,
            link: String?,
        ): Path? {
            val fileName = source.fileName?.toString() ?: return null
            val expandedDestination = destinationSubDirectory
                ?.let { expandItemMetadata(it, source, include) }
                ?.replace('/', '\\')
                ?.trimStart('\\')
                ?.takeIf(String::isNotBlank)
            if (expandedDestination != null) {
                return Path.of(expandedDestination).resolve(fileName).normalize()
            }
            val expandedLink = link
                ?.let { expandItemMetadata(it, source, include) }
                ?.replace('/', '\\')
                ?.trimStart('\\')
                ?.takeIf(String::isNotBlank)
            if (expandedLink != null) {
                return Path.of(expandedLink).normalize()
            }
            return Path.of(fileName)
        }

        private fun expandItemMetadata(value: String, source: Path, include: String): String =
            expandProperties(value)
                .replace("%(Filename)", source.fileName.toString().substringBeforeLast('.'))
                .replace("%(Extension)", "." + source.fileName.toString().substringAfterLast('.', ""))
                .replace("%(RecursiveDir)", recursiveDir(source, include))

        private fun recursiveDir(source: Path, include: String): String {
            val marker = include.indexOf("**")
            if (marker < 0) {
                return ""
            }
            val rootText = include.substring(0, marker).replace('/', '\\')
            val root = Path.of(rootText).toAbsolutePath().normalize()
            val relativeParent = runCatching { source.parent.relativeTo(root) }.getOrNull() ?: return ""
            val value = relativeParent.toString().replace('/', '\\').trim('\\')
            return if (value.isBlank()) "" else "$value\\"
        }

        private fun wildcardBase(include: String): Path {
            val wildcard = include.indexOf('*')
            val prefix = include.substring(0, wildcard)
            val separator = prefix.lastIndexOf('\\')
            val base = if (separator >= 0) prefix.substring(0, separator) else prefix
            return Path.of(base).toAbsolutePath().normalize()
        }

        private fun wildcardRelativeGlob(include: String): String {
            val wildcard = include.indexOf('*')
            val prefix = include.substring(0, wildcard)
            val separator = prefix.lastIndexOf('\\')
            return if (separator >= 0) include.substring(separator + 1) else include
        }

        private fun globToRegex(pattern: String): String {
            val result = StringBuilder()
            var index = 0
            while (index < pattern.length) {
                val char = pattern[index]
                if (char == '*') {
                    if (index + 1 < pattern.length && pattern[index + 1] == '*') {
                        result.append(".*")
                        index += 2
                    } else {
                        result.append("""[^\\]*""")
                        index += 1
                    }
                } else {
                    result.append(Regex.escape(char.toString()))
                    index += 1
                }
            }
            return result.toString()
        }

        private fun conditionPasses(condition: String?): Boolean {
            val raw = condition?.trim().orEmpty()
            if (raw.isBlank()) {
                return true
            }
            val expanded = expandProperties(raw)
            return expanded.split(Regex("""\s+[Aa][Nn][Dd]\s+""")).all { andPart ->
                andPart.split(Regex("""\s+[Oo][Rr]\s+""")).any(::simpleConditionPasses)
            }
        }

        private fun simpleConditionPasses(condition: String): Boolean {
            val trimmed = condition.trim()
            val exists = Regex("""^Exists\('([^']+)'\)$""").find(trimmed)
            if (exists != null) {
                return Files.exists(Path.of(exists.groupValues[1]))
            }
            val comparison = Regex("""^'([^']*)'\s*(==|!=)\s*'([^']*)'$""").find(trimmed)
            if (comparison != null) {
                val left = comparison.groupValues[1]
                val operator = comparison.groupValues[2]
                val right = comparison.groupValues[3]
                return if (operator == "==") left.equals(right, ignoreCase = true) else !left.equals(right, ignoreCase = true)
            }
            return trimmed.equals("true", ignoreCase = true)
        }

        private fun expandProperties(value: String): String {
            var current = value
            repeat(12) {
                val next = propertyReference.replace(current) { match ->
                    properties[match.groupValues[1]].orEmpty()
                }
                if (next == current) {
                    return current
                }
                current = next
            }
            return current
        }

        private fun Element.metadata(name: String): String? {
            getAttribute(name).takeIf(String::isNotBlank)?.let { return it }
            return childElements()
                .firstOrNull { it.localTagName().equals(name, ignoreCase = true) }
                ?.textContent
                ?.trim()
                ?.takeIf(String::isNotBlank)
        }
    }
}

private fun Element.childElements(): List<Element> =
    buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node.nodeType == Node.ELEMENT_NODE) {
                add(node as Element)
            }
        }
    }

private fun Element.localTagName(): String =
    localName ?: tagName.substringAfter(':')

private val propertyReference = Regex("""\$\(([A-Za-z0-9_.-]+)\)""")
