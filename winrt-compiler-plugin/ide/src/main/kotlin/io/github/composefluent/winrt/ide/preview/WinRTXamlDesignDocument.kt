package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinRTXamlResourceExpression
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
            resource: ((String, String) -> WinRTXamlDesignResource?)? = null,
            sdkType: ((String, String) -> Boolean)? = null,
            visualType: ((String, String) -> Boolean)? = null,
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
            val omittedResources = linkedSetOf<String>()
            fun children(tag: Element) = (0 until tag.childNodes.length).mapNotNull { tag.childNodes.item(it) as? Element }
            fun namespaces(from: Element, to: Element) { (0 until from.attributes.length).map { from.attributes.item(it) }
                .filter { it.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI }.forEach { to.setAttributeNS(it.namespaceURI, it.nodeName, it.nodeValue) } }
            var expandedSize = text.length + application.orEmpty().length
            fun sanitize(tag: Element, sourcePath: String, stack: Set<String> = setOf(sourcePath)) {
                if (tag.namespaceURI == WinRTXamlCatalog.XAML && tag.localName == "Properties") {
                    tag.parentNode.removeChild(tag)
                    notes += "Authored property declarations are excluded from static preview."
                    return
                }
                val targetType = tag.getAttribute("TargetType").trim()
                if (sdkType != null && tag.localName in setOf("Style", "ControlTemplate") && ':' in targetType) {
                    val uri = tag.lookupNamespaceURI(targetType.substringBefore(':')).orEmpty()
                    if (uri.startsWith("using:") && !sdkType(uri, targetType.substringAfter(':'))) {
                        tag.getAttributeNS(WinRTXamlCatalog.XAML, "Key").takeIf { it.isNotEmpty() }?.let(omittedResources::add)
                        tag.parentNode.removeChild(tag)
                        notes += "Templates and styles targeting project types require enabling project code."
                        return
                    }
                }
                val externalDictionary = resource != null && tag.localName == "ResourceDictionary" && tag.hasAttribute("Source")
                val projectDictionary = if (resource != null && sdkType != null && tag.namespaceURI.orEmpty().startsWith("using:") &&
                    !sdkType(tag.namespaceURI, tag.localName.substringBefore('.')))
                    resource("${tag.namespaceURI}.${tag.localName}", sourcePath) else null
                if (externalDictionary || projectDictionary != null) {
                    val uri = tag.getAttribute("Source")
                    val loaded = projectDictionary ?: resource!!(uri, sourcePath)
                        ?: error("Resource dictionary '$uri' could not be found from '$sourcePath'.")
                    require(loaded.packagePath !in stack) { "Resource dictionary cycle at '${loaded.packagePath}'." }
                    expandedSize += loaded.text.length
                    require(expandedSize <= 128 * 1024) { "The expanded design resources exceed the static preview limit." }
                    val dictionary = parse(loaded.text).documentElement
                    require(dictionary.localName == "ResourceDictionary") { "'$uri' must contain a ResourceDictionary." }
                    val replacement = tag.ownerDocument.importNode(dictionary, true) as Element
                    namespaces(tag, replacement)
                    if (tag.hasAttributeNS(WinRTXamlCatalog.XAML, "Key"))
                        replacement.setAttributeNS(WinRTXamlCatalog.XAML, "x:Key", tag.getAttributeNS(WinRTXamlCatalog.XAML, "Key"))
                    sanitize(replacement, loaded.packagePath, stack + loaded.packagePath)
                    tag.parentNode.replaceChild(replacement, tag)
                    return
                }
                if (sdkType != null && tag.namespaceURI.orEmpty().startsWith("using:") &&
                    !sdkType(tag.namespaceURI, tag.localName.substringBefore('.'))) {
                    val type = tag.tagName
                    if ('.' in tag.localName || tag.parentNode is Element && ((tag.parentNode as Element).localName == "ResourceDictionary" ||
                        (tag.parentNode as Element).localName.endsWith(".Resources"))) {
                        tag.getAttributeNS(WinRTXamlCatalog.XAML, "Key").takeIf { it.isNotEmpty() }?.let(omittedResources::add)
                        tag.parentNode.removeChild(tag)
                        notes += "Project resource '$type' requires enabling project code."
                    } else {
                        val placeholder = tag.ownerDocument.createElementNS(WinRTXamlCatalog.PRESENTATION, "Border")
                        namespaces(tag, placeholder)
                        val layout = setOf("Width", "Height", "MinWidth", "MinHeight", "MaxWidth", "MaxHeight", "Margin",
                            "HorizontalAlignment", "VerticalAlignment", "Visibility", "Opacity", "Grid.Row", "Grid.Column", "Grid.RowSpan", "Grid.ColumnSpan")
                        (0 until tag.attributes.length).map { tag.attributes.item(it) }.filter {
                            it.nodeName in layout || it.namespaceURI == WinRTXamlCatalog.XAML && it.localName == "Name"
                        }.forEach { placeholder.setAttributeNS(it.namespaceURI, it.nodeName, it.nodeValue) }
                        // An unavailable container must not erase its SDK
                        // children. Property-element content remains in source
                        // order, without treating nonvisual values as controls.
                        val visuals = mutableListOf<Element>()
                        fun containsVisual(from: Element): Boolean = visualType?.invoke(from.namespaceURI.orEmpty(), from.localName) == true ||
                            children(from).any(::containsVisual)
                        fun retain(from: Element, propertyValue: Boolean = false) {
                            children(from).forEach { child ->
                                val property = child.localName.substringAfter('.', "")
                                if (child.namespaceURI == tag.namespaceURI && child.localName.substringBefore('.') == tag.localName && property.isNotEmpty()) {
                                    if (property == "Resources") {
                                        val resources = tag.ownerDocument.renameNode(child.cloneNode(true), WinRTXamlCatalog.PRESENTATION, "Border.Resources") as Element
                                        placeholder.appendChild(resources)
                                    } else retain(child, propertyValue = true)
                                } else if (visualType?.invoke(child.namespaceURI.orEmpty(), child.localName) == true) {
                                    visuals += child.cloneNode(true) as Element
                                } else if (child.namespaceURI.orEmpty().startsWith("using:") && '.' !in child.localName &&
                                    !sdkType(child.namespaceURI, child.localName) && (!propertyValue || containsVisual(child))) {
                                    visuals += child.cloneNode(true) as Element
                                }
                            }
                        }
                        retain(tag)
                        if (visuals.size == 1) placeholder.appendChild(visuals.single())
                        else if (visuals.isNotEmpty()) {
                            val content = tag.ownerDocument.createElementNS(WinRTXamlCatalog.PRESENTATION, "StackPanel")
                            visuals.forEach(content::appendChild)
                            placeholder.appendChild(content)
                        } else {
                            val label = tag.ownerDocument.createElementNS(WinRTXamlCatalog.PRESENTATION, "TextBlock")
                            label.setAttribute("Text", type); label.setAttribute("Opacity", "0.6")
                            placeholder.appendChild(label)
                        }
                        tag.parentNode.replaceChild(placeholder, tag)
                        sanitize(placeholder, sourcePath, stack)
                        notes += "'$type' is a placeholder. Enable project code to render custom controls."
                    }
                    return
                }
                val attributes = (0 until tag.attributes.length).map { tag.attributes.item(it) }
                val designValues = attributes.filter { it.namespaceURI == DESIGN && it.localName in setOf("Text", "Content", "Width", "Height", "Visibility") }
                    .associate { it.localName to it.nodeValue }
                attributes.forEach { attr ->
                    val uri = attr.namespaceURI.orEmpty(); val local = attr.localName ?: attr.nodeName
                    val extension = Regex("^\\{\\s*([^\\s,{}]+)").find(attr.nodeValue)?.groupValues?.get(1).orEmpty()
                    val compiledBinding = extension.substringAfter(':') == "Bind" && ':' in extension &&
                        tag.lookupNamespaceURI(extension.substringBefore(':')) == WinRTXamlCatalog.XAML
                    when {
                        sdkType != null && uri.startsWith("using:") && !sdkType(uri, local.substringBefore('.')) -> {
                            tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                            notes += "Project attached properties require enabling project code."
                        }
                        uri == DESIGN || uri == "http://schemas.openxmlformats.org/markup-compatibility/2006" -> tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        uri == WinRTXamlCatalog.XAML && local in setOf("Class", "DataType", "DefaultBindMode", "Phase", "Load", "DeferLoadStrategy", "FieldModifier") -> tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        compiledBinding -> { tag.removeAttributeNode(attr as org.w3c.dom.Attr); notes += "Compiled x:Bind expressions use design values or control defaults in static preview." }
                        uri.isEmpty() && event(tag.namespaceURI.orEmpty(), tag.localName, local) -> {
                            tag.removeAttributeNode(attr as org.w3c.dom.Attr); notes += "Event handlers are excluded from static preview."
                        }
                        local in setOf("Source", "UriSource") && !attr.nodeValue.startsWith('{') && !attr.nodeValue.contains(':') -> {
                            val base = java.net.URI("ms-appx", "", "/" + sourcePath.replace('\\', '/'), null)
                                .resolve(attr.nodeValue.replace('\\', '/').replace(" ", "%20"))
                            attr.nodeValue = "ms-appx:///" + base.normalize().rawPath.trimStart('/')
                        }
                    }
                }
                designValues.forEach { (name, value) -> tag.setAttribute(name, value) }
                children(tag).forEach { child ->
                    if (child.namespaceURI == DESIGN) {
                        if (child.localName in setOf("DesignInstance", "DesignData", "DataContext")) {
                            tag.removeChild(child); notes += "Project design data requires enabling project code."
                            return@forEach
                        }
                        val replacement = tag.ownerDocument.renameNode(child.cloneNode(true), WinRTXamlCatalog.PRESENTATION, child.localName) as Element
                        tag.replaceChild(replacement, child)
                        sanitize(replacement, sourcePath, stack)
                    } else sanitize(child, sourcePath, stack)
                }
                // Loose XAML loads merged dictionaries immediately, unlike
                // compiled resource dictionaries' deferred materialization.
                // Establish this dictionary's theme keys before merged styles
                // refer to them; dictionary lookup/override order is unchanged.
                if (tag.localName == "ResourceDictionary") children(tag)
                    .firstOrNull { it.localName == "ResourceDictionary.ThemeDictionaries" }
                    ?.let { tag.insertBefore(it, tag.firstChild) }
            }
            sanitize(root, packagePath)
            root = document.documentElement
            if (root.localName == "Window") {
                val content = children(root).firstOrNull { it.localName == "Window.Content" }?.let { children(it).singleOrNull() }
                    ?: children(root).singleOrNull { '.' !in it.localName }
                    ?: error("The Window needs one visual content element to preview.")
                val grid = document.createElementNS(WinRTXamlCatalog.PRESENTATION, "Grid")
                namespaces(root, grid); grid.appendChild(content.cloneNode(true)); root = grid
            }
            val viewport = document.createElementNS(WinRTXamlCatalog.PRESENTATION, "Grid")
            viewport.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", WinRTXamlCatalog.PRESENTATION)
            // The artboard must remain legible independently of the IDE theme
            // when the document itself has a transparent background.
            viewport.setAttribute("Background", "{ThemeResource ApplicationPageBackgroundThemeBrush}")
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
            // A disabled custom converter/style must not prevent rendering the
            // rest of a page. Keep genuinely missing SDK/resource keys as errors.
            val retainedKeys = (0 until viewport.getElementsByTagName("*").length).map {
                (viewport.getElementsByTagName("*").item(it) as Element).getAttributeNS(WinRTXamlCatalog.XAML, "Key")
            }.toSet()
            fun removeOmittedReferences(tag: Element) {
                (0 until tag.attributes.length).map { tag.attributes.item(it) }.forEach { attr ->
                    val direct = WinRTXamlResourceExpression.parse(attr.nodeValue)?.key
                    val converter = Regex("\\{\\s*(?:StaticResource|ThemeResource)\\s+([^{}\\s]+)\\s*}").findAll(attr.nodeValue)
                        .map { it.groupValues[1] }.any { it in omittedResources && it !in retainedKeys }
                    if (direct in omittedResources && direct !in retainedKeys || converter) {
                        tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        notes += "References to disabled project resources use control defaults."
                    }
                }
                children(tag).forEach(::removeOmittedReferences)
            }
            if (omittedResources.isNotEmpty()) removeOmittedReferences(viewport)
            val transformer = TransformerFactory.newInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                .newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
            val markup = StringWriter().also { transformer.transform(DOMSource(viewport), StreamResult(it)) }.toString()
            require(markup.toByteArray().size <= 128 * 1024) { "This page and its application resources exceed the static preview limit." }
            return WinRTXamlDesignDocument(markup, notes.toList())
        }
    }
}

internal data class WinRTXamlDesignResource(val text: String, val packagePath: String)
