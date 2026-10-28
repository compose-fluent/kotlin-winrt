package io.github.composefluent.winrt.ide.preview

import org.w3c.dom.Element

/** DesignData is serialized sample data, not a request to construct project types.
 * Matches the UWP designer's inherited d:DataContext; x:Bind keeps its separate
 * compiled owner semantics and is deliberately not evaluated against DataContext. */
internal object WinUIXamlDesignData {
    private const val DESIGN = "http://schemas.microsoft.com/expression/blend/2008"
    private fun children(element: Element) = (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }

    fun apply(root: Element, source: String, load: ((String, String) -> WinUIXamlDesignResource?)?,
        parse: (String) -> Element, notes: MutableSet<String>) {
        var size = 0
        fun value(context: Element, path: String): String? {
            var node = context
            if (path.isEmpty() || path == ".") return node.textContent.takeIf { children(node).isEmpty() }
            val parts = path.split('.')
            if (parts.any { !Regex("[A-Za-z_][A-Za-z0-9_]*").matches(it) }) return null
            parts.forEachIndexed { index, part ->
                if (index == parts.lastIndex && node.hasAttribute(part)) return node.getAttribute(part)
                val property = children(node).singleOrNull { it.localName.substringAfter('.') == part } ?: return null
                val nested = children(property)
                if (index == parts.lastIndex && nested.isEmpty()) return property.textContent
                node = nested.singleOrNull() ?: return null
            }
            return node.textContent.takeIf { children(node).isEmpty() }
        }
        fun visit(tag: Element, inherited: Element?) {
            var context = if (tag.localName == "DataTemplate") null else inherited
            val design = tag.getAttributeNS(DESIGN, "DataContext")
            if (tag.hasAttributeNS(DESIGN, "DataContext")) {
                context = null // An explicit context shadows its ancestor even if unavailable.
                val match = Regex("^\\{\\s*([\\w]+):DesignData\\s+(?:Source\\s*=\\s*)?([^{}]+)\\}$").matchEntire(design.trim())
                if (match != null && tag.lookupNamespaceURI(match.groupValues[1]) == DESIGN) {
                    tag.removeAttributeNS(DESIGN, "DataContext")
                    val uri = match.groupValues[2].trim().trim('\'', '"')
                    val data = load?.invoke(uri, source) ?: error("Design data '$uri' could not be found from '$source'.")
                    size += data.text.length
                    require(size <= 128 * 1024) { "The design data exceeds the static preview limit." }
                    context = parse(data.text)
                }
            }
            children(tag).firstOrNull { it.namespaceURI == DESIGN &&
                (it.localName == "DataContext" || it.localName == "${tag.localName}.DataContext") }?.let { property ->
                context = children(property).singleOrNull()
                tag.removeChild(property)
            }
            context?.let { sample ->
                (0 until tag.attributes.length).map { tag.attributes.item(it) }.forEach { attr ->
                    // Leave converters, ElementName and RelativeSource bindings to
                    // WinUI. Only plain sample-data paths can become scalar literals.
                    val binding = Regex("^\\{\\s*Binding(?:\\s+(?:Path\\s*=\\s*)?([^,{}]*))?\\s*}$").matchEntire(attr.nodeValue) ?: return@forEach
                    if (attr.namespaceURI == DESIGN || attr.namespaceURI.isNullOrEmpty()) {
                        val resolved = value(sample, binding.groupValues[1].trim())
                        if (resolved != null) attr.nodeValue = if (resolved.startsWith('{')) "{}$resolved" else resolved
                        else notes += "Sample data has no scalar value for '${binding.groupValues[1].trim()}'."
                    }
                }
            }
            children(tag).forEach { visit(it, context) }
        }
        visit(root, null)
    }
}
