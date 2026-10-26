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
    fun mark(document: Document, text: String) {
        val fingerprint = hash(text)
        fun visit(tag: Element, path: List<Int>) {
            val children = (0 until tag.childNodes.length).mapNotNull { tag.childNodes.item(it) as? Element }
            if (tag.namespaceURI == WinUIXamlCatalog.PRESENTATION && tag.localName in setOf("DataTemplate", "ControlTemplate", "ItemsPanelTemplate")) {
                children.singleOrNull { '.' !in it.localName }?.setAttributeNS(NAMESPACE, "kwrt:Source", id(fingerprint, path + children.indexOfFirst { '.' !in it.localName }))
            }
            children.forEachIndexed { index, child -> visit(child, path + index) }
        }
        visit(document.documentElement, emptyList())
    }
    fun materialize(tag: Element) {
        val source = tag.getAttributeNS(NAMESPACE, "Source").takeIf { it.isNotEmpty() } ?: return
        tag.removeAttributeNS(NAMESPACE, "Source")
        if (tag.hasAttribute("Resources")) return
        fun children(value: Element) = (0 until value.childNodes.length).mapNotNull { value.childNodes.item(it) as? Element }
        val resources = children(tag).singleOrNull { it.localName.endsWith(".Resources") }
            ?: tag.ownerDocument.createElementNS(tag.namespaceURI, tag.tagName + ".Resources").also { tag.insertBefore(it, tag.firstChild) }
        val dictionary = children(resources).singleOrNull { it.localName == "ResourceDictionary" } ?: resources
        if (children(dictionary).any { it.getAttributeNS(WinUIXamlCatalog.XAML, "Key") == WinUIXamlHotReloadProtocol.PREVIEW_SOURCE_KEY }) return
        val marker = tag.ownerDocument.createElementNS(WinUIXamlCatalog.XAML, "kwrtx:String")
        marker.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:kwrtx", WinUIXamlCatalog.XAML)
        marker.setAttributeNS(WinUIXamlCatalog.XAML, "kwrtx:Key", WinUIXamlHotReloadProtocol.PREVIEW_SOURCE_KEY)
        marker.textContent = source
        dictionary.appendChild(marker)
    }
}
