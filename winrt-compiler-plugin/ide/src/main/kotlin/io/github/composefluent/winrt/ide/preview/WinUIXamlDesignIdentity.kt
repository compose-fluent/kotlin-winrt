package io.github.composefluent.winrt.ide.preview

import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol
import org.w3c.dom.Document
import org.w3c.dom.Element
import javax.xml.XMLConstants
import java.security.MessageDigest

/** Design-copy provenance. A local string resource survives actual template
 * selection without changing layout, names, bindings or invoking a selector. */
internal object WinUIXamlDesignIdentity {
    private const val NAMESPACE = "urn:kotlin-winrt:design-source"
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun id(hash: String, path: List<Int>) = "d1:$hash:${path.joinToString(".")}"
    fun source(tag: XmlTag): String {
        val path = generateSequence(tag) { it.parentTag }.toList().asReversed().drop(1)
            .map { it.parentTag!!.subTags.indexOf(it) }
        return id(hash(tag.containingFile.text), path)
    }
    fun mark(document: Document, text: String, visualType: ((String, String) -> Boolean)? = null) {
        val fingerprint = hash(text)
        fun visit(tag: Element, path: List<Int>) {
            val children = (0 until tag.childNodes.length).mapNotNull { tag.childNodes.item(it) as? Element }
            if (visualType?.invoke(tag.namespaceURI.orEmpty(), tag.localName) == true)
                tag.setAttributeNS(NAMESPACE, "kwrt:Source", id(fingerprint, path))
            if (tag.namespaceURI == WinUIXamlCatalog.PRESENTATION && tag.localName in setOf("DataTemplate", "ControlTemplate", "ItemsPanelTemplate")) {
                children.singleOrNull { '.' !in it.localName }?.setAttributeNS(NAMESPACE, "kwrt:Source", id(fingerprint, path + children.indexOfFirst { '.' !in it.localName }))
            }
            children.forEachIndexed { index, child -> visit(child, path + index) }
        }
        visit(document.documentElement, emptyList())
    }

    fun markConnections(document: Document, text: String, page: io.github.composefluent.winrt.metadata.WinUIXamlPageDeclaration) {
        val connections = io.github.composefluent.winrt.metadata.WinUIXamlDesignConnections.connections(page)
        val paths = io.github.composefluent.winrt.metadata.WinUIXamlDesignConnections.paths(text, connections)
        fun children(tag: Element) = (0 until tag.childNodes.length).mapNotNull { tag.childNodes.item(it) as? Element }
        paths.entries.sortedBy { it.key.size }.forEach { (path, connection) ->
            var tag = document.documentElement
            for (index in path) tag = children(tag).getOrNull(index) ?: return@forEach
            if (connection.isTemplateChild && connection.isScopeRoot) tag = children(tag).singleOrNull { '.' !in it.localName } ?: return@forEach
            val suppressed = connection.bindings.filter {
                tag.hasAttributeNS("http://schemas.microsoft.com/expression/blend/2008", it.name) || children(tag).any { child ->
                    child.namespaceURI == "http://schemas.microsoft.com/expression/blend/2008" && child.localName.substringAfter('.') == it.name
                }
            }.joinToString(",") { it.name }
            val value = "${connection.id}:${io.github.composefluent.winrt.metadata.WinUIXamlDeclarations.designConnectionSignature(connection)}:$suppressed"
            val previous = tag.getAttributeNS(NAMESPACE, "Connection")
            tag.setAttributeNS(NAMESPACE, "kwrt:Connection", if (previous.isEmpty()) value else previous + ";" + value)
        }
    }

    fun materialize(tag: Element) {
        listOf("Source" to WinUIXamlHotReloadProtocol.PREVIEW_SOURCE_KEY,
            "Connection" to WinUIXamlHotReloadProtocol.PREVIEW_CONNECTION_KEY).forEach { (attribute, key) ->
            val value = tag.getAttributeNS(NAMESPACE, attribute).takeIf { it.isNotEmpty() } ?: return@forEach
            tag.removeAttributeNS(NAMESPACE, attribute)
            resource(tag, key, value)
        }
    }

    fun resource(tag: Element, key: String, value: String) {
        if (tag.hasAttribute("Resources")) return
        fun children(value: Element) = (0 until value.childNodes.length).mapNotNull { value.childNodes.item(it) as? Element }
        val resources = children(tag).singleOrNull { it.localName.endsWith(".Resources") }
            ?: tag.ownerDocument.createElementNS(tag.namespaceURI, tag.tagName + ".Resources").also { tag.insertBefore(it, tag.firstChild) }
        val dictionary = children(resources).singleOrNull { it.localName == "ResourceDictionary" } ?: resources
        if (children(dictionary).any { it.getAttributeNS(WinUIXamlCatalog.XAML, "Key") == key }) return
        val marker = tag.ownerDocument.createElementNS(WinUIXamlCatalog.XAML, "kwrtx:String")
        marker.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:kwrtx", WinUIXamlCatalog.XAML)
        marker.setAttributeNS(WinUIXamlCatalog.XAML, "kwrtx:Key", key)
        marker.textContent = value
        dictionary.appendChild(marker)
    }
}
