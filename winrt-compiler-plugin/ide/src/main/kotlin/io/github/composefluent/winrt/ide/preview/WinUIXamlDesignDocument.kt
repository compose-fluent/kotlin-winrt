@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinUIXamlResourceExpression
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

internal data class WinUIXamlDesignDocument(val markup: String, val notes: List<String>) {
    companion object {
        private const val DESIGN = "http://schemas.microsoft.com/expression/blend/2008"
        fun prepare(text: String, application: String? = null, packagePath: String = "Page.xaml", applicationPath: String = "App.xaml",
            resource: ((String, String) -> WinUIXamlDesignResource?)? = null,
            sdkType: ((String, String) -> Boolean)? = null,
            visualType: ((String, String) -> Boolean)? = null,
            designData: ((String, String) -> WinUIXamlDesignResource?)? = resource,
            compiledResourceFallback: Boolean = false,
            projectBindings: Boolean = false,
            bindingPage: io.github.composefluent.winrt.metadata.WinUIXamlPageDeclaration? = null,
            event: (String, String, String) -> Boolean = { _, _, _ -> false }): WinUIXamlDesignDocument {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            fun parse(value: String) = factory.newDocumentBuilder().parse(InputSource(StringReader(value))).also { WinUIXamlDesignIdentity.mark(it, value, visualType) }
            val document = parse(text)
            var root = document.documentElement
            val componentClass = root.getAttributeNS(WinUIXamlCatalog.XAML, "Class")
            if (projectBindings && bindingPage?.className == componentClass) WinUIXamlDesignIdentity.markConnections(document, text, bindingPage)
            require(root.localName !in listOf("Application", "ResourceDictionary")) { "Select a visual Window, Page or UserControl to preview." }
            val notes = linkedSetOf<String>()
            WinUIXamlDesignData.apply(root, packagePath, designData, { parse(it).documentElement }, notes)
            val omittedResources = linkedSetOf<String>()
            fun children(tag: Element) = (0 until tag.childNodes.length).mapNotNull { tag.childNodes.item(it) as? Element }
            fun namespaces(from: Element, to: Element) { (0 until from.attributes.length).map { from.attributes.item(it) }
                .filter { it.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI }.forEach { to.setAttributeNS(it.namespaceURI, it.nodeName, it.nodeValue) } }
            var expandedSize = text.length + application.orEmpty().length
            fun sanitize(tag: Element, sourcePath: String, stack: Set<String> = setOf(sourcePath)) {
                // Visual Studio's design-time properties override their runtime
                // counterparts before the ordinary XAML processing pipeline.
                // This also gives d:Source the resource's original package base.
                (0 until tag.attributes.length).map { tag.attributes.item(it) }
                    .filter { it.namespaceURI == DESIGN }.forEach { attribute ->
                        val name = attribute.localName
                        val value = attribute.nodeValue
                        tag.removeAttributeNode(attribute as org.w3c.dom.Attr)
                        val extension = Regex("^\\{\\s*([^\\s,{}]+)").find(value)?.groupValues?.get(1)
                        val designExtension = extension?.contains(':') == true &&
                            tag.lookupNamespaceURI(extension.substringBefore(':')) == DESIGN
                        when {
                            name == "IsHidden" -> if (value.equals("true", true)) tag.setAttribute("Visibility", "Collapsed")
                            name in setOf("DesignWidth", "DesignHeight") -> {
                                val property = name.removePrefix("Design")
                                if (!tag.hasAttribute(property)) tag.setAttribute(property, value)
                            }
                            designExtension -> notes += "Design extension '$extension' needs explicit design values or inline design data."
                            else -> tag.setAttribute(name, value)
                        }
                    }
                children(tag).filter { it.namespaceURI == DESIGN && '.' in it.localName }.forEach { design ->
                    val member = design.localName.substringAfter('.')
                    // A design property replaces, rather than appends to, the
                    // runtime property. Items must not coexist with ItemsSource.
                    tag.removeAttribute(member)
                    if (member == "Items") tag.removeAttribute("ItemsSource")
                    children(tag).filter { it !== design && it.namespaceURI != DESIGN &&
                        (it.localName == design.localName || member == "Items" && it.localName.endsWith(".ItemsSource"))
                    }.forEach(tag::removeChild)
                }
                if (tag.namespaceURI == WinUIXamlCatalog.XAML && tag.localName == "Properties") {
                    tag.parentNode.removeChild(tag)
                    notes += "Authored property declarations are excluded from static preview."
                    return
                }
                val targetType = tag.getAttribute("TargetType").trim()
                if (sdkType != null && tag.localName in setOf("Style", "ControlTemplate") && ':' in targetType) {
                    val uri = tag.lookupNamespaceURI(targetType.substringBefore(':')).orEmpty()
                    if (uri.startsWith("using:") && !sdkType(uri, targetType.substringAfter(':'))) {
                        tag.getAttributeNS(WinUIXamlCatalog.XAML, "Key").takeIf { it.isNotEmpty() }?.let(omittedResources::add)
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
                    if (loaded == null && !compiledResourceFallback)
                        error("Resource dictionary '$uri' could not be found from '$sourcePath'.")
                    if (loaded == null) notes += "Resource dictionary '$uri' uses the compiled package because its source is unavailable."
                    else {
                        require(loaded.packagePath !in stack) { "Resource dictionary cycle at '${loaded.packagePath}'." }
                        expandedSize += loaded.text.length
                        require(expandedSize <= 128 * 1024) { "The expanded design resources exceed the static preview limit." }
                        val dictionary = parse(loaded.text).documentElement
                        require(dictionary.localName == "ResourceDictionary") { "'$uri' must contain a ResourceDictionary." }
                        val descendants = listOf(dictionary) + (0 until dictionary.getElementsByTagName("*").length).map { dictionary.getElementsByTagName("*").item(it) as Element }
                        val compiledBindings = descendants.any { item -> (0 until item.attributes.length).any { index ->
                            io.github.composefluent.winrt.ide.xaml.isCompiledXamlBinding(item.attributes.item(index).nodeValue) { item.lookupNamespaceURI(it) }
                        } }
                        if (projectBindings && dictionary.hasAttributeNS(WinUIXamlCatalog.XAML, "Class") && compiledBindings) {
                            // Its compiled template connector belongs to the dictionary,
                            // not the visual page. Keep that exact generated owner.
                            if (externalDictionary) tag.setAttribute("Source", java.net.URI("ms-appx", "", "/" + loaded.packagePath.replace('\\', '/').trimStart('/'), null).toASCIIString())
                            notes += "Compiled resource dictionaries retain their generated bindings; edits rebuild automatically."
                            return
                        }
                        val replacement = tag.ownerDocument.importNode(dictionary, true) as Element
                        namespaces(tag, replacement)
                        if (tag.hasAttributeNS(WinUIXamlCatalog.XAML, "Key"))
                            replacement.setAttributeNS(WinUIXamlCatalog.XAML, "x:Key", tag.getAttributeNS(WinUIXamlCatalog.XAML, "Key"))
                        sanitize(replacement, loaded.packagePath, stack + loaded.packagePath)
                        tag.parentNode.replaceChild(replacement, tag)
                        return
                    }
                }
                if (sdkType != null && tag.namespaceURI.orEmpty().startsWith("using:") &&
                    !sdkType(tag.namespaceURI, tag.localName.substringBefore('.'))) {
                    val type = tag.tagName
                    if ('.' in tag.localName || tag.parentNode is Element && ((tag.parentNode as Element).localName == "ResourceDictionary" ||
                        (tag.parentNode as Element).localName.endsWith(".Resources"))) {
                        tag.getAttributeNS(WinUIXamlCatalog.XAML, "Key").takeIf { it.isNotEmpty() }?.let(omittedResources::add)
                        tag.parentNode.removeChild(tag)
                        notes += "Project resource '$type' requires enabling project code."
                    } else {
                        val placeholder = tag.ownerDocument.createElementNS(WinUIXamlCatalog.PRESENTATION, "Border")
                        namespaces(tag, placeholder)
                        val layout = setOf("Width", "Height", "MinWidth", "MinHeight", "MaxWidth", "MaxHeight", "Margin",
                            "HorizontalAlignment", "VerticalAlignment", "Visibility", "Opacity", "Grid.Row", "Grid.Column", "Grid.RowSpan", "Grid.ColumnSpan")
                        (0 until tag.attributes.length).map { tag.attributes.item(it) }.filter {
                            it.nodeName in layout || it.namespaceURI == WinUIXamlCatalog.XAML && it.localName == "Name"
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
                                        val resources = tag.ownerDocument.renameNode(child.cloneNode(true), WinUIXamlCatalog.PRESENTATION, "Border.Resources") as Element
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
                            val content = tag.ownerDocument.createElementNS(WinUIXamlCatalog.PRESENTATION, "StackPanel")
                            visuals.forEach(content::appendChild)
                            placeholder.appendChild(content)
                        } else {
                            val label = tag.ownerDocument.createElementNS(WinUIXamlCatalog.PRESENTATION, "TextBlock")
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
                attributes.forEach { attr ->
                    val uri = attr.namespaceURI.orEmpty(); val local = attr.localName ?: attr.nodeName
                    val compiledBinding = io.github.composefluent.winrt.ide.xaml.isCompiledXamlBinding(attr.nodeValue) { tag.lookupNamespaceURI(it) }
                    when {
                        sdkType != null && uri.startsWith("using:") && !sdkType(uri, local.substringBefore('.')) -> {
                            tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                            notes += "Project attached properties require enabling project code."
                        }
                        uri == DESIGN || uri == "http://schemas.openxmlformats.org/markup-compatibility/2006" -> tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        uri == WinUIXamlCatalog.XAML && local in setOf("Class", "DataType", "DefaultBindMode", "Phase", "Load", "DeferLoadStrategy", "FieldModifier") -> tag.removeAttributeNode(attr as org.w3c.dom.Attr)
                        compiledBinding -> { tag.removeAttributeNode(attr as org.w3c.dom.Attr); notes += if (projectBindings && bindingPage != null) "Compiled bindings use the project's generated binding connectors."
                            else "Compiled x:Bind expressions use design values or control defaults in static preview." }
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
                children(tag).forEach { child ->
                    if (child.namespaceURI == DESIGN) {
                        if (child.localName in setOf("DesignInstance", "DesignData", "DataContext")) {
                            tag.removeChild(child); notes += "Project design data requires enabling project code."
                            return@forEach
                        }
                        val replacement = tag.ownerDocument.renameNode(child.cloneNode(true), WinUIXamlCatalog.PRESENTATION, child.localName) as Element
                        tag.replaceChild(replacement, child)
                        sanitize(replacement, sourcePath, stack)
                    } else sanitize(child, sourcePath, stack)
                }
                // Popup.IsOpen requires an attached XamlRoot. Loose XAML is
                // parsed off-tree; the generated SDK adapter opens it after attach.
                if (tag.localName == "Popup" && tag.getAttribute("IsOpen").equals("True", true) &&
                    (tag.namespaceURI == WinUIXamlCatalog.PRESENTATION || tag.namespaceURI in setOf("using:Microsoft.UI.Xaml.Controls.Primitives", "using:Windows.UI.Xaml.Controls.Primitives"))) {
                    tag.setAttribute("IsOpen", "False")
                    WinUIXamlDesignIdentity.resource(tag, io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_POPUP_OPEN_KEY, "True")
                }
                WinUIXamlDesignIdentity.materialize(tag)
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
                val grid = document.createElementNS(WinUIXamlCatalog.PRESENTATION, "Grid")
                namespaces(root, grid); grid.appendChild(content.cloneNode(true)); root = grid
            }
            val viewport = document.createElementNS(WinUIXamlCatalog.PRESENTATION, "Grid")
            viewport.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", WinUIXamlCatalog.PRESENTATION)
            // The artboard must remain legible independently of the IDE theme
            // when the document itself has a transparent background.
            viewport.setAttribute("Background", "{ThemeResource ApplicationPageBackgroundThemeBrush}")
            application?.let { appText ->
                val app = parse(appText).documentElement
                namespaces(app, viewport)
                children(app).firstOrNull { it.localName == "Application.Resources" }?.let { resources ->
                    sanitize(resources, applicationPath)
                    val group = document.createElementNS(WinUIXamlCatalog.PRESENTATION, "Grid.Resources")
                    children(resources).forEach { group.appendChild(document.importNode(it, true)) }
                    viewport.appendChild(group)
                }
            }
            viewport.appendChild(root.cloneNode(true))
            if (projectBindings && componentClass.isNotBlank())
                WinUIXamlDesignIdentity.resource(viewport, io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol.PREVIEW_COMPONENT_KEY,
                    componentClass + if (bindingPage == null) "" else "|" + io.github.composefluent.winrt.metadata.WinUIXamlDesignConnections.connections(bindingPage).sortedBy { it.id }
                        .joinToString(",") { "${it.id}:${io.github.composefluent.winrt.metadata.WinUIXamlDeclarations.designConnectionSignature(it)}" })
            // A disabled custom converter/style must not prevent rendering the
            // rest of a page. Keep genuinely missing SDK/resource keys as errors.
            val retainedKeys = (0 until viewport.getElementsByTagName("*").length).map {
                (viewport.getElementsByTagName("*").item(it) as Element).getAttributeNS(WinUIXamlCatalog.XAML, "Key")
            }.toSet()
            fun removeOmittedReferences(tag: Element) {
                (0 until tag.attributes.length).map { tag.attributes.item(it) }.forEach { attr ->
                    val direct = WinUIXamlResourceExpression.parse(attr.nodeValue)?.key
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
            return WinUIXamlDesignDocument(markup, notes.toList())
        }
    }
}

internal data class WinUIXamlDesignResource(val text: String, val packagePath: String)
