package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import io.github.composefluent.winrt.runtime.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Compare markup, then use the compiler-owned accessors; no visual-tree replacement or reflection. */
internal class WinRTHotReloadMarkup private constructor(val text: String, private val root: XamlElement) {
    val className: String = root.attributes[Name(WinRTXamlCatalog.XAML, "Class")].orEmpty()
    val hash: String = WinRTXamlDeclarations.sourceFingerprint(text)

    private data class Name(val uri: String, val local: String)
    private data class XamlElement(val type: Name, val attributes: Map<Name, String>, val content: List<Any>) {
        val elementName get() = attributes[Name(WinRTXamlCatalog.XAML, "Name")] ?: attributes[Name("", "Name")].orEmpty()
        val template get() = type.uri == WinRTXamlCatalog.PRESENTATION && type.local in setOf("DataTemplate", "ControlTemplate", "ItemsPanelTemplate")
    }

    fun patch(previous: WinRTHotReloadMarkup, loaded: WinRTXamlHotReloadRoot,
        propertyProblem: (uri: String, type: String, property: String) -> String? = { _, _, _ -> null }): WinRTXamlHotReloadPatch {
        require(className == loaded.className && previous.className == className) { "Changing x:Class requires rebuilding and restarting." }
        require(previous.hash == loaded.sourceHash) { "The loaded component does not match the previous source. Reconnect or rebuild." }
        val changes = mutableListOf<WinRTXamlHotReloadChange>()
        fun literal(value: String): String {
            require(!value.startsWith('{') || value.startsWith("{}")) { "Binding and resource expressions require rebuilding and restarting." }
            return if (value.startsWith("{}")) value.drop(2) else value
        }
        fun compare(old: XamlElement, next: XamlElement, owner: Boolean, inTemplate: Boolean) {
            require(old.type == next.type && old.elementName == next.elementName) { "Changing element types or names requires rebuilding and restarting." }
            require(next.attributes.keys.containsAll(old.attributes.keys)) { "Removing a property requires rebuilding to restore its default value." }
            val template = inTemplate || next.template
            next.attributes.forEach { (name, value) ->
                val before = old.attributes[name]
                if (before == value) return@forEach
                require(!template) { "Template changes require rebuilding and restarting." }
                require(name.uri.isEmpty() && '.' !in name.local) { "Directives, namespaces and attached properties require rebuilding and restarting." }
                require(name.local != "Name") { "Changing element names requires rebuilding and restarting." }
                require(owner || next.elementName in loaded.elements) { "Add x:Name and rebuild before updating this element's properties." }
                propertyProblem(next.type.uri, next.type.local, name.local)?.let { error(it) }
                before?.let(::literal)
                changes += WinRTXamlHotReloadChange(if (owner) "" else next.elementName, name.local, literal(value))
            }
            require(old.content.size == next.content.size) { "Adding or removing elements requires rebuilding and restarting." }
            old.content.zip(next.content).forEach { (a, b) ->
                if (a is XamlElement && b is XamlElement) compare(a, b, false, template)
                else require(a == b) { "Changing element content requires rebuilding and restarting." }
            }
        }
        compare(previous.root, root, true, false)
        require(changes.size <= 512) { "Too many changed properties; rebuild the application." }
        return WinRTXamlHotReloadPatch(className, loaded.resourcePath, previous.hash, hash, loaded.version + 1, changes)
    }

    companion object {
        fun parse(text: String): WinRTHotReloadMarkup {
            require(text.length <= 2 * 1024 * 1024) { "XAML is too large for a property update." }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val builder = factory.newDocumentBuilder().apply {
                setErrorHandler(object : DefaultHandler() { override fun fatalError(e: SAXParseException) { throw e } })
            }
            val xml = builder.parse(InputSource(StringReader(text.removePrefix("\uFEFF"))))
            var count = 0
            val names = hashSetOf<String>()
            fun element(value: Element, depth: Int, inTemplate: Boolean): XamlElement {
                require(++count <= 4096 && depth <= 64) { "XAML exceeds the property update complexity limit." }
                val attributes = (0 until value.attributes.length).associate { index ->
                    val attribute = value.attributes.item(index)
                    Name(attribute.namespaceURI.orEmpty(), attribute.localName ?: attribute.nodeName) to attribute.nodeValue
                }
                val type = Name(value.namespaceURI.orEmpty(), value.localName ?: value.tagName)
                val template = inTemplate || (type.uri == WinRTXamlCatalog.PRESENTATION && type.local in setOf("DataTemplate", "ControlTemplate", "ItemsPanelTemplate"))
                val name = attributes[Name(WinRTXamlCatalog.XAML, "Name")].orEmpty()
                if (!template && name.isNotEmpty()) require(names.add(name)) { "Duplicate x:Name '$name'. Fix XAML before updating." }
                val content = buildList<Any> {
                    for (i in 0 until value.childNodes.length) {
                        val child = value.childNodes.item(i)
                        if (child is Element) add(element(child, depth + 1, template))
                        else if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                            if (!child.nodeValue.isBlank()) add(child.nodeValue)
                        } else if (child.nodeType == Node.PROCESSING_INSTRUCTION_NODE) add(child.nodeName to child.nodeValue)
                    }
                }
                return XamlElement(type, attributes, content)
            }
            return WinRTHotReloadMarkup(text, element(xml.documentElement, 0, false))
        }
    }
}
