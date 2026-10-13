package io.github.composefluent.winrt.gallery.processor

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import java.io.StringReader
import java.io.StringWriter
import org.xml.sax.InputSource

/** Application source selection only: projected types and their ABI remain owned by Kotlin/WinRT. */
internal fun selectGalleryVariant(source: String, winUi2: Boolean): String {
    data class Branch(val parent: Boolean, val condition: Boolean, var otherwise: Boolean = false)
    val branches = ArrayDeque<Branch>()
    var enabled = true
    return source.lineSequence().joinToString("\n") { line ->
        val directive = line.trim().removePrefix("//").removePrefix("<!--").removeSuffix("-->").trim()
        when (directive) {
            "#if WINUI2", "#if WINUI3" -> {
                val condition = (directive == "#if WINUI2") == winUi2
                branches.addLast(Branch(enabled, condition))
                enabled = enabled && condition
                ""
            }
            "#else" -> {
                val branch = checkNotNull(branches.lastOrNull()) { "Unmatched #else" }
                check(!branch.otherwise) { "Duplicate #else" }
                branch.otherwise = true
                enabled = branch.parent && !branch.condition
                ""
            }
            "#endif" -> { enabled = branches.removeLastOrNull()?.parent ?: error("Unmatched #endif"); "" }
            else -> if (enabled) line else ""
        }
    }.also { check(branches.isEmpty()) { "Unclosed Gallery conditional" } }
}

/** Resolve each type against the selected SDK, preserving WinUI 2 extension types when present. */
internal class GalleryUwpTypeNames(private val types: Set<String>) {
    private val kotlinTypes = types.associateBy { type -> type.substringBeforeLast('.').lowercase() + "." + type.substringAfterLast('.') }

    fun runtimeName(name: String): String {
        if (name in types || !name.startsWith("Microsoft.UI.")) return name
        return name.replaceFirst("Microsoft.UI.", "Windows.UI.").takeIf { it in types } ?: name
    }

    fun kotlin(source: String): String {
        val expanded = Regex("(?m)^import (microsoft\\.ui(?:\\.`?[a-z]+`?)+)\\.\\*[^\\S\\r\\n]*$").replace(source) { match ->
            val namespace = match.groupValues[1].replace("`", "")
            val windows = namespace.replaceFirst("microsoft.ui.", "windows.ui.")
            val words = Regex("[A-Za-z_][A-Za-z0-9_]*").findAll(source).map { it.value }.toSet()
            buildList {
                if (kotlinTypes.keys.any { it.substringBeforeLast('.') == windows }) add("import $windows.*")
                // Explicit extension imports disambiguate controls that exist in both frameworks.
                kotlinTypes.keys.filter { it.substringBeforeLast('.') == namespace && it.substringAfterLast('.') in words }
                    .sorted().forEach { add("import $it") }
            }.joinToString("\n")
        }
        val mapped = Regex("\\bmicrosoft\\.ui(?:\\.`?[a-z]+`?)*\\.[A-Z][A-Za-z0-9_]*").replace(expanded) { match ->
            val candidate = match.value.replaceFirst("microsoft.ui.", "windows.ui.")
            if (match.value.replace("`", "") !in kotlinTypes && candidate.replace("`", "") in kotlinTypes) candidate else match.value
        }
        val imports = mutableSetOf<String>()
        return mapped.lineSequence().joinToString("\n") { line ->
            if (line.startsWith("import ") && !imports.add(line)) "" else line
        }
    }

    fun xaml(source: String): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(source)))
        val root = document.documentElement
        val namespaces = linkedMapOf<String, String>()
        fun qualified(type: String): String {
            val namespace = type.substringBeforeLast('.')
            val prefix = namespaces.getOrPut(namespace) { "galleryPlatform${namespaces.size}" }
            root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:$prefix", "using:$namespace")
            return "$prefix:${type.substringAfterLast('.')}"
        }
        fun projected(namespace: String?, local: String): String? {
            val owner = local.substringBefore('.')
            val original = when {
                namespace == "http://schemas.microsoft.com/winfx/2006/xaml/presentation" -> "Microsoft.UI.Xaml.Controls.$owner"
                namespace?.startsWith("using:Microsoft.UI.") == true -> namespace.removePrefix("using:") + ".$owner"
                else -> return null
            }
            val mapped = runtimeName(original)
            // The default presentation namespace already resolves Windows.UI.Xaml.
            if (namespace == "http://schemas.microsoft.com/winfx/2006/xaml/presentation" && mapped !in types) return null
            if (namespace == "http://schemas.microsoft.com/winfx/2006/xaml/presentation" && !mapped.startsWith("Microsoft.UI.")) return null
            return mapped.takeIf { it in types }
        }
        val elements = document.getElementsByTagName("*").let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }
        elements.forEach { element ->
            val local = element.localName
            projected(element.namespaceURI, local)?.let { mapped ->
                val name = qualified(mapped) + local.removePrefix(local.substringBefore('.'))
                document.renameNode(element, "using:${mapped.substringBeforeLast('.')}", name)
            }
            val attributes = (0 until element.attributes.length).map { element.attributes.item(it) }
            attributes.filter { it.localName.contains('.') }.forEach { attribute ->
                val local = attribute.localName
                val namespace = attribute.namespaceURI ?: element.lookupNamespaceURI(null)
                projected(namespace, local)?.let { mapped ->
                    document.renameNode(attribute, "using:${mapped.substringBeforeLast('.')}",
                        qualified(mapped) + local.removePrefix(local.substringBefore('.')))
                }
            }
            attributes.forEach { attribute ->
                // Attached property paths in Setter/Storyboard targets use the same type lookup.
                attribute.nodeValue = Regex("\\(([A-Za-z_][A-Za-z0-9_:]*)\\.([A-Za-z_][A-Za-z0-9_]*)\\)").replace(attribute.nodeValue) { match ->
                    val owner = match.groupValues[1]
                    val namespace = element.lookupNamespaceURI(owner.substringBefore(':', "").ifBlank { null })
                    projected(namespace, owner.substringAfter(':'))?.let {
                        "(${qualified(it)}.${match.groupValues[2]})"
                    } ?: match.value
                }
            }
            // Type-valued attributes also need the extension namespace (styles/templates).
            listOf("TargetType", "DataType", "Type").forEach { attribute ->
                val node = (0 until element.attributes.length).map { element.attributes.item(it) }
                    .firstOrNull { it.localName == attribute } ?: return@forEach
                val value = node.nodeValue
                if (!value.matches(Regex("[A-Za-z_][A-Za-z0-9_.:]*"))) return@forEach
                val prefix = value.substringBefore(':', "")
                val localType = value.substringAfter(':')
                val namespace = element.lookupNamespaceURI(prefix.takeIf { it.isNotBlank() })
                projected(namespace, localType)?.let { node.nodeValue = qualified(it) }
            }
        }
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}

/** args: version, input root, output root, overlay root, metadata index, exclusion manifest. */
fun main(args: Array<String>) {
    require(args.size == 6)
    val winUi2 = args[0] == "2"
    require(args[0] in setOf("2", "3"))
    val input = Path.of(args[1]).toAbsolutePath().normalize()
    val output = Path.of(args[2]).toAbsolutePath().normalize()
    val module = input.parent.parent.parent
    require(output.startsWith(module.resolve("build")) && output != module.resolve("build"))
    val overlay = Path.of(args[3])
    val exclusions = if (winUi2) Files.readAllLines(Path.of(args[5])).filter { it.isNotBlank() && !it.startsWith('#') }
        .map { it.substringBefore('\t') } else emptyList()
    val names = if (winUi2) GalleryUwpTypeNames(Files.readAllLines(Path.of(args[4])).map { it.substringBefore('\t') }.toSet()) else null
    val selected = linkedMapOf<Path, Path>()
    Files.walk(input).use { paths -> paths.filter(Files::isRegularFile).sorted().forEach { path ->
        val relative = input.relativize(path)
        val name = relative.toString().replace('\\', '/')
        if (exclusions.none { excluded ->
            name.endsWith(excluded) || name.contains("/$excluded/") ||
                Regex(".*" + excluded.split('*').joinToString(".*") { Regex.escape(it) }).matches(name)
        }) selected[relative] = path
    } }
    if (winUi2 && Files.isDirectory(overlay)) Files.walk(overlay).use { paths ->
        paths.filter(Files::isRegularFile).sorted().forEach { selected[overlay.relativize(it)] = it }
    }
    Files.createDirectories(output)
    Files.walk(output).use { paths -> paths.filter(Files::isRegularFile).forEach { path ->
        if (output.relativize(path) !in selected) Files.delete(path)
    } }
    selected.forEach { (relative, path) ->
        val target = output.resolve(relative)
        Files.createDirectories(target.parent)
        val source = try { selectGalleryVariant(Files.readString(path), winUi2) }
        catch (error: IllegalStateException) { throw IllegalStateException("$path: ${error.message}", error) }
        val result = when {
            names == null -> source
            path.toString().endsWith(".kt") -> names.kotlin(source)
            path.toString().endsWith(".xaml") -> names.xaml(source)
            else -> source
        }
        if (!Files.exists(target) || Files.readString(target) != result) Files.writeString(target, result)
    }
    println("Gallery WinUI ${args[0]}: ${selected.size} shared/selected source assets; ${exclusions.size} explicit exclusions")
}
