package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class WinRTXamlDesignDocument(val markup: String, val notes: List<String>) {
    companion object {
        private const val DESIGN = "http://schemas.microsoft.com/expression/blend/2008"
        fun prepare(text: String, application: String? = null, packagePath: String = "Page.xaml", applicationPath: String = "App.xaml",
            event: (String, String, String) -> Boolean = { _, _, _ -> false }): WinRTXamlDesignDocument {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            fun parse(value: String) = factory.newDocumentBuilder().parse(InputSource(StringReader(value)))
            val document = parse(text)
            var root = document.documentElement
            require(root.localName !in listOf("Application", "ResourceDictionary")) { "Select a visual Window, Page or UserControl to preview." }
            val notes = linkedSetOf<String>()
            fun children(tag: Element) = (0 until tag.childNodes.length).mapNotNull { tag.childNodes.item(it) as? Element }
            fun namespaces(from: Element, to: Element) { (0 until from.attributes.length).map { from.attributes.item(it) }
                .filter { it.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI }.forEach { to.setAttributeNS(it.namespaceURI, it.nodeName, it.nodeValue) } }
            fun sanitize(tag: Element, sourcePath: String) {
                val attributes = (0 until tag.attributes.length).map { tag.attributes.item(it) }
                val designValues = attributes.filter { it.namespaceURI == DESIGN && it.localName in setOf("Text", "Content", "Width", "Height", "Visibility") }
                    .associate { it.localName to it.nodeValue }
                attributes.forEach { attr ->
                    val uri = attr.namespaceURI.orEmpty(); val local = attr.localName ?: attr.nodeName
                    val extension = Regex("^\\{\\s*([^\\s,{}]+)").find(attr.nodeValue)?.groupValues?.get(1).orEmpty()
                    val compiledBinding = extension.substringAfter(':') == "Bind" && ':' in extension &&
                        tag.lookupNamespaceURI(extension.substringBefore(':')) == WinRTXamlCatalog.XAML
                    when {
                        uri == DESIGN || uri == "http://schemas.openxmlformats.org/markup-compatibility/2006" -> tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        uri == WinRTXamlCatalog.XAML && local in setOf("Class", "DataType", "DefaultBindMode", "Phase", "Load", "DeferLoadStrategy") -> tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        compiledBinding -> { tag.removeAttributeNode(attr as org.w3c.dom.Attr); notes += "Compiled x:Bind expressions use design values or control defaults in static preview." }
                        uri.isEmpty() && event(tag.namespaceURI.orEmpty(), tag.localName, local) -> {
                            tag.removeAttributeNode(attr as org.w3c.dom.Attr); notes += "Event handlers are excluded from static preview."
                        }
                        local == "Source" && !attr.nodeValue.startsWith('{') && !attr.nodeValue.contains(':') -> {
                            val base = java.net.URI("ms-appx", "", "/" + sourcePath.replace('\\', '/'), null)
                                .resolve(attr.nodeValue.replace('\\', '/').replace(" ", "%20"))
                            attr.nodeValue = "ms-appx:///" + base.normalize().rawPath.trimStart('/')
                        }
                    }
                }
                designValues.forEach { (name, value) -> tag.setAttribute(name, value) }
                children(tag).forEach { child -> if (child.namespaceURI == DESIGN) tag.removeChild(child) else sanitize(child, sourcePath) }
            }
            sanitize(root, packagePath)
            if (root.localName == "Window") {
                val content = children(root).firstOrNull { it.localName == "Window.Content" }?.let { children(it).singleOrNull() }
                    ?: children(root).singleOrNull { '.' !in it.localName }
                    ?: error("The Window needs one visual content element to preview.")
                val grid = document.createElementNS(WinRTXamlCatalog.PRESENTATION, "Grid")
                namespaces(root, grid); grid.appendChild(content.cloneNode(true)); root = grid
            }
            val viewport = document.createElementNS(WinRTXamlCatalog.PRESENTATION, "Grid")
            viewport.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", WinRTXamlCatalog.PRESENTATION)
            application?.let { appText ->
                val app = parse(appText).documentElement
                namespaces(app, viewport)
                children(app).firstOrNull { it.localName == "Application.Resources" }?.let { resources ->
                    sanitize(resources, applicationPath)
                    val group = document.createElementNS(WinRTXamlCatalog.PRESENTATION, "Grid.Resources")
                    children(resources).forEach { group.appendChild(document.importNode(it, true)) }
                    viewport.appendChild(group)
                }
            }
            viewport.appendChild(root.cloneNode(true))
            val transformer = TransformerFactory.newInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                .newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
            val markup = StringWriter().also { transformer.transform(DOMSource(viewport), StreamResult(it)) }.toString()
            require(markup.toByteArray().size <= 128 * 1024) { "This page and its application resources exceed the static preview limit." }
            return WinRTXamlDesignDocument(markup, notes.toList())
        }
    }
}
