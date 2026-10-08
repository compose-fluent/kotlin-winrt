package io.github.composefluent.winrt.ide.hotreload

import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinRTXamlResourceExpression
import io.github.composefluent.winrt.ide.xaml.WinRTXamlContentMember
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import io.github.composefluent.winrt.runtime.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Compare markup, then use the compiler-owned accessors and WinUI's resource parser. */
internal class WinRTHotReloadMarkup private constructor(val text: String, private val root: XamlElement) {
    val className: String = root.attributes[Name(WinRTXamlCatalog.XAML, "Class")].orEmpty()
    val isApplication: Boolean = root.type == Name(WinRTXamlCatalog.PRESENTATION, "Application")
    val isResourceDictionary: Boolean = root.type == Name(WinRTXamlCatalog.PRESENTATION, "ResourceDictionary")
    val hash: String = WinRTXamlDeclarations.sourceFingerprint(text)

    private data class Name(val uri: String, val local: String)
    private class XamlElement(val type: Name, val attributes: Map<Name, String>, val content: List<Any>, val node: Element) {
        val elementName get() = attributes[Name(WinRTXamlCatalog.XAML, "Name")] ?: attributes[Name("", "Name")].orEmpty()
        val template get() = type.uri == WinRTXamlCatalog.PRESENTATION && type.local in setOf("DataTemplate", "ControlTemplate", "ItemsPanelTemplate")
        fun matches(other: XamlElement): Boolean = type == other.type && attributes == other.attributes &&
            content.size == other.content.size && content.zip(other.content).all { (a, b) ->
                if (a is XamlElement && b is XamlElement) a.matches(b) else a == b
            }
    }

    fun patch(previous: WinRTHotReloadMarkup, loaded: WinRTXamlHotReloadRoot,
        propertyProblem: (uri: String, type: String, property: String) -> String? = { _, _, _ -> null },
        contentMember: (uri: String, type: String, property: String?) -> WinRTXamlContentMember? = { _, _, _ -> null }): WinRTXamlHotReloadPatch {
        require(className == loaded.className && previous.className == className) { "Changing x:Class requires rebuilding and restarting." }
        require(previous.hash == loaded.sourceHash) { "The loaded component does not match the previous source. Reconnect or rebuild." }
        val changes = mutableListOf<WinRTXamlHotReloadChange>()
        val resources = mutableListOf<WinRTXamlHotReloadResources>()
        val reads = mutableListOf<WinRTXamlHotReloadRead>()
        val children = mutableListOf<WinRTXamlHotReloadChildren>()
        fun XamlElement.address(parent: WinRTXamlHotReloadTarget?, owner: Boolean = false) = when {
            owner -> WinRTXamlHotReloadTarget()
            elementName in loaded.elements -> WinRTXamlHotReloadTarget(elementName)
            else -> parent
        }
        fun literal(value: String): String {
            require(!value.startsWith('{') || value.startsWith("{}")) { "Binding and resource expressions require rebuilding and restarting." }
            return if (value.startsWith("{}")) value.drop(2) else value
        }
        fun resourceEntries(container: XamlElement): List<XamlElement> {
            require(container.content.all { it is XamlElement }) { "Resource text changes require rebuilding." }
            val children = container.content.filterIsInstance<XamlElement>()
            val entries = if (children.singleOrNull()?.type == Name(WinRTXamlCatalog.PRESENTATION, "ResourceDictionary")) {
                val dictionary = children.single()
                require(dictionary.attributes.keys.all { it.uri == XMLConstants.XMLNS_ATTRIBUTE_NS_URI }) {
                    "External or named dictionaries require rebuilding."
                }
                require(dictionary.content.all { it is XamlElement }) { "Resource text changes require rebuilding." }
                dictionary.content.filterIsInstance<XamlElement>()
            } else children
            require(entries.all { it.attributes[Name(WinRTXamlCatalog.XAML, "Key")]?.isNotEmpty() == true }) {
                "Implicit keys, merged and theme dictionaries require rebuilding for a style update."
            }
            require(entries.map { it.attributes[Name(WinRTXamlCatalog.XAML, "Key")] }.distinct().size == entries.size) {
                "Duplicate resource keys require fixing XAML before updating."
            }
            return entries
        }
        fun key(entry: XamlElement) = entry.attributes.getValue(Name(WinRTXamlCatalog.XAML, "Key"))
        fun styleChanged(old: XamlElement, next: XamlElement): Boolean = !old.matches(next) &&
            (old.type == Name(WinRTXamlCatalog.PRESENTATION, "Style") || next.type == Name(WinRTXamlCatalog.PRESENTATION, "Style") ||
                old.content.filterIsInstance<XamlElement>().zip(next.content.filterIsInstance<XamlElement>()).any { (a, b) -> styleChanged(a, b) })
        fun validateFragment(element: XamlElement, keys: Set<String>) {
            require(element.elementName.isEmpty()) { "Named resources require rebuilding to reconnect generated fields." }
            element.attributes.forEach { (name, value) ->
                require(name.uri != WinRTXamlCatalog.XAML || name.local == "Key") {
                    "Compiled directives in resources require rebuilding."
                }
                if (name.uri.isEmpty()) propertyProblem(element.type.uri, element.type.local, name.local)?.let { error(it) }
                if (value.startsWith('{') && !value.startsWith("{}")) {
                    val reference = WinRTXamlResourceExpression.parse(value)
                    require(reference != null && !reference.theme && reference.key in keys) {
                        "The replacement dictionary must resolve its resource expressions locally; bindings and theme expressions require rebuilding."
                    }
                }
            }
            element.content.filterIsInstance<XamlElement>().forEach { validateFragment(it, keys) }
        }
        fun references(owner: XamlElement, dictionary: XamlElement, target: WinRTXamlHotReloadTarget, keys: Set<String>): List<WinRTXamlHotReloadResourceReference> {
            val result = mutableListOf<WinRTXamlHotReloadResourceReference>()
            fun visit(element: XamlElement, ownerElement: Boolean, available: Set<String>, inDictionary: Boolean = false, inTemplate: Boolean = false) {
                if (element === dictionary) return
                val localKeys = element.content.filterIsInstance<XamlElement>().filter { it.type.local.endsWith(".Resources") && it !== dictionary }
                    .flatMap { block -> runCatching { resourceEntries(block).map(::key) }.getOrElse {
                        error("A nested opaque dictionary prevents resolving the resource consumers; rebuild.")
                    } }.toSet()
                val visible = available - localKeys
                element.attributes.forEach { (name, value) ->
                    val reference = WinRTXamlResourceExpression.parse(value) ?: return@forEach
                    if (reference.key !in visible) return@forEach
                    require(!reference.theme) { "Replacing a resource used by ThemeResource requires rebuilding to preserve its theme expression." }
                    require(!inDictionary && !inTemplate && !element.template) { "A resource captured by another dictionary or template requires rebuilding." }
                    require(name.uri.isEmpty() && '.' !in name.local) { "Attached resource consumers require rebuilding." }
                    propertyProblem(element.type.uri, element.type.local, name.local)?.let { error(it) }
                    val address = if (ownerElement) target else element.address(null)
                    require(address != null) { "Add x:Name and rebuild before refreshing this resource consumer." }
                    result += WinRTXamlHotReloadResourceReference(address, name.local, reference.key)
                }
                element.content.filterIsInstance<XamlElement>().forEach { child ->
                    visit(child, false, visible, inDictionary || child.type.local.endsWith(".Resources"), inTemplate || element.template)
                }
            }
            visit(owner, true, keys)
            return result.distinct()
        }
        fun validateVisual(element: XamlElement) {
            require(element.type.uri == WinRTXamlCatalog.PRESENTATION && element.elementName.isEmpty() && !element.template && '.' !in element.type.local) {
                "A new or removed subtree must use SDK elements without names, templates or property elements; rebuild to update its connections."
            }
            element.attributes.forEach { (name, value) ->
                require(name.uri.isEmpty() || name.uri == XMLConstants.XMLNS_ATTRIBUTE_NS_URI) {
                    "Compiled directives in a new or removed subtree require rebuilding."
                }
                if (name.uri.isEmpty()) {
                    // A missing catalog must not let an ordinary event attribute
                    // pass as a literal when removing a connected subtree.
                    require(contentMember(element.type.uri, element.type.local, name.local) != null) {
                        "Resolve SDK property '${name.local}' before changing this subtree; event connections require rebuilding."
                    }
                    propertyProblem(element.type.uri, element.type.local, name.local)?.let { error(it) }
                }
                literal(value)
            }
            element.content.filterIsInstance<XamlElement>().forEach(::validateVisual)
        }
        fun sequence(old: List<Any>, next: List<Any>, target: WinRTXamlHotReloadTarget?,
            visit: (XamlElement, XamlElement, WinRTXamlHotReloadTarget?) -> Unit) {
            require(target != null && old.all { it is XamlElement } && next.all { it is XamlElement }) {
                "The collection owner and its element content must be resolvable before updating."
            }
            val before = old.filterIsInstance<XamlElement>()
            val after = next.filterIsInstance<XamlElement>()
            val used = hashSetOf<Int>()
            val matches = arrayOfNulls<Int>(after.size)
            after.forEachIndexed { index, element ->
                if (element.elementName.isNotEmpty()) {
                    val match = before.indexOfFirst { it.elementName == element.elementName && it.type == element.type }
                    require(match >= 0 && used.add(match)) { "Changing a connected name, type or parent requires rebuilding." }
                    matches[index] = match
                }
            }
            after.forEachIndexed { index, element ->
                if (matches[index] != null) return@forEachIndexed
                val match = before.indices.firstOrNull { it !in used && before[it].elementName.isEmpty() && before[it].matches(element) }
                if (match != null) { matches[index] = match; used += match }
            }
            // A property edit at a stable unnamed position retains the object.
            if (before.size == after.size) after.forEachIndexed { index, element ->
                if (matches[index] == null && index !in used && before[index].elementName.isEmpty() &&
                    element.elementName.isEmpty() && before[index].type == element.type) {
                    matches[index] = index; used += index
                }
            }
            before.indices.filterNot { it in used }.forEach { validateVisual(before[it]) }
            val items = after.mapIndexed { index, element -> matches[index]?.let { WinRTXamlHotReloadItem.Existing(it) }
                ?: run { validateVisual(element); WinRTXamlHotReloadItem.Markup(fragmentMarkup(element.node)) } }
            if (matches.toList() != before.indices.toList()) children += WinRTXamlHotReloadChildren(target, before.size, items)
            after.forEachIndexed { index, element ->
                matches[index]?.let { oldIndex ->
                    val address = element.address(null) ?: target.copy(path = target.path + WinRTXamlHotReloadStep.Index(index, after.size))
                    visit(before[oldIndex], element, address)
                }
            }
        }
        fun compare(old: XamlElement, next: XamlElement, target: WinRTXamlHotReloadTarget?, inTemplate: Boolean, resourceObject: Boolean = false) {
            if (old.matches(next)) return
            require(old.type == next.type && old.elementName == next.elementName) { "Changing element types or names requires rebuilding and restarting." }
            require(next.attributes.keys.containsAll(old.attributes.keys)) { "Removing a property requires rebuilding to restore its default value." }
            val template = inTemplate || next.template
            next.attributes.forEach { (name, value) ->
                val before = old.attributes[name]
                if (before == value) return@forEach
                require(!template) { "Template changes require rebuilding and restarting." }
                require(name.uri.isEmpty() && '.' !in name.local) { "Directives, namespaces and attached properties require rebuilding and restarting." }
                require(name.local != "Name") { "Changing element names requires rebuilding and restarting." }
                require(target != null) { "Add x:Name and rebuild before updating this element's properties." }
                propertyProblem(next.type.uri, next.type.local, name.local)?.let { error(it) }
                before?.let(::literal)
                changes += WinRTXamlHotReloadChange(target.element, name.local, literal(value), target.path)
            }
            if (template) require(old.matches(next)) { "Template changes require rebuilding and restarting." }
            val oldProperties = old.content.filterIsInstance<XamlElement>().filter { '.' in it.type.local }
            val nextProperties = next.content.filterIsInstance<XamlElement>().filter { '.' in it.type.local }
            require(oldProperties.map { it.type } == nextProperties.map { it.type }) { "Adding, removing or moving property elements requires rebuilding." }
            oldProperties.zip(nextProperties).forEach { (a, b) ->
                if (a is XamlElement && b is XamlElement) {
                    if (b.type.uri == WinRTXamlCatalog.PRESENTATION && b.type.local.endsWith(".Resources")) {
                        if (a.matches(b)) return@forEach
                        require(a.attributes == b.attributes) { "Changing a property element's directives requires rebuilding." }
                        require(!template && target != null && a.type == b.type && a.attributes == b.attributes) {
                            "The resource owner must be root or named; rebuild to establish its identity."
                        }
                        val oldEntries = resourceEntries(a)
                        val nextEntries = resourceEntries(b)
                        val oldByKey = oldEntries.associateBy(::key)
                        val nextByKey = nextEntries.associateBy(::key)
                        require(oldByKey.keys == nextByKey.keys) { "Adding or removing resource keys requires rebuilding." }
                        val resourceTarget = target.copy(path = target.path + WinRTXamlHotReloadStep.Property("Resources"))
                        if (nextEntries.any { styleChanged(oldByKey.getValue(key(it)), it) }) {
                            nextEntries.forEach { validateFragment(it, nextByKey.keys) }
                            val consumers = references(next, b, target, nextByKey.keys)
                            resources += WinRTXamlHotReloadResources(resourceTarget, dictionaryMarkup(b), nextEntries.map(::key), consumers)
                            consumers.filter { it.property == "Style" }.forEach { consumer ->
                                fun setterProperties(element: XamlElement): List<String> =
                                    element.content.filterIsInstance<XamlElement>().flatMap { child ->
                                        if (child.type == Name(WinRTXamlCatalog.PRESENTATION, "Setter"))
                                            listOfNotNull(child.attributes[Name("", "Property")]?.takeIf { '.' !in it })
                                        else setterProperties(child)
                                    }
                                setterProperties(nextByKey.getValue(consumer.key)).distinct().forEach { property ->
                                    reads += WinRTXamlHotReloadRead(consumer.target, property)
                                }
                            }
                        } else nextEntries.forEach { entry ->
                            compare(oldByKey.getValue(key(entry)), entry,
                                resourceTarget.copy(path = resourceTarget.path + WinRTXamlHotReloadStep.Key(key(entry))), false, true)
                        }
                    } else {
                        if (a.matches(b)) return@forEach
                        require(a.attributes == b.attributes) { "Changing a property element's directives requires rebuilding." }
                        val property = b.type.local.substringAfter('.')
                        val shape = contentMember(next.type.uri, next.type.local, property)
                        val propertyTarget = target?.copy(path = target.path + WinRTXamlHotReloadStep.Property(property))
                        if (shape?.collection == true) sequence(a.content, b.content, propertyTarget) { first, second, address ->
                            compare(first, second, address, template)
                        } else {
                            require(shape != null && a.attributes == b.attributes && a.content.size == 1 && b.content.size == 1 &&
                                a.content.single() is XamlElement && b.content.single() is XamlElement) { "This property element requires rebuilding." }
                            val first = a.content.single() as XamlElement
                            val second = b.content.single() as XamlElement
                            compare(first, second, second.address(null) ?: propertyTarget, template)
                        }
                    }
                }
                else require(a == b) { "Changing element content requires rebuilding and restarting." }
            }
            val first = old.content.filter { it !in oldProperties }
            val second = next.content.filter { it !in nextProperties }
            val shape = if (resourceObject) null else contentMember(next.type.uri, next.type.local, null)
            val contentTarget = shape?.let { target?.copy(path = target.path + WinRTXamlHotReloadStep.Property(it.name)) }
            if (shape?.collection == true && !template) sequence(first, second, contentTarget) { a, b, address -> compare(a, b, address, false) }
            else {
                require(first.size == second.size) { "Adding or removing this content requires rebuilding." }
                first.zip(second).forEach { (a, b) ->
                    if (a is XamlElement && b is XamlElement) compare(a, b,
                        b.address(null) ?: contentTarget?.takeIf { first.size == 1 }, template)
                    else require(a == b) { "Changing element text content requires rebuilding." }
                }
            }
        }
        compare(previous.root, root, WinRTXamlHotReloadTarget(), false)
        val readbacks = reads.distinct()
        require(changes.size + resources.sumOf { 1 + it.references.size } + readbacks.size + children.sumOf { 1 + it.items.size } <= 512 &&
            resources.size <= 64 && children.size <= 64) { "Too many changes; rebuild the application." }
        return WinRTXamlHotReloadPatch(className, loaded.resourcePath, previous.hash, hash, loaded.version + 1, changes, resources, readbacks, children)
    }

    private fun dictionaryMarkup(container: XamlElement): String {
        val document = container.node.ownerDocument
        val original = container.content.filterIsInstance<XamlElement>().singleOrNull()
            ?.takeIf { it.type == Name(WinRTXamlCatalog.PRESENTATION, "ResourceDictionary") }?.node
        val dictionary = original?.cloneNode(true) as? Element
            ?: document.createElementNS(WinRTXamlCatalog.PRESENTATION, "ResourceDictionary").apply {
                container.content.filterIsInstance<XamlElement>().forEach { appendChild(it.node.cloneNode(true)) }
            }
        return fragmentMarkup(dictionary, original ?: container.node)
    }

    private fun fragmentMarkup(element: Element, context: Element = element): String {
        val fragment = element.cloneNode(true) as Element
        // A fragment must carry the namespaces it inherited from the page.
        val ancestors = generateSequence(context as Node?) { it.parentNode }.filterIsInstance<Element>().toList().asReversed()
        ancestors.forEach { element ->
            for (i in 0 until element.attributes.length) {
                val attribute = element.attributes.item(i)
                if (attribute.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI) fragment.setAttributeNS(attribute.namespaceURI, attribute.nodeName, attribute.nodeValue)
            }
        }
        if (fragment.prefix.isNullOrEmpty() && fragment.namespaceURI != null)
            fragment.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", fragment.namespaceURI)
        val transformer = TransformerFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        }.newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
        return StringWriter().also { transformer.transform(DOMSource(fragment), StreamResult(it)) }.toString().also {
            require(it.length <= 128 * 1024) { "The fragment is too large for a live replacement; rebuild." }
        }
    }

    companion object {
        fun parse(text: String): WinRTHotReloadMarkup {
            require(text.length <= 2 * 1024 * 1024) { "XAML is too large for a property update." }
            val factory = DocumentBuilderFactory.newDefaultInstance().apply {
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
                return XamlElement(type, attributes, content, value)
            }
            return WinRTHotReloadMarkup(text, element(xml.documentElement, 0, false))
        }
    }
}
