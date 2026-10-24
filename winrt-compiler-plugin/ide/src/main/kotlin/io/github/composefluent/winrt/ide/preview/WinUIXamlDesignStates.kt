package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
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

internal data class DesignerStateGroup(val key: String, val label: String, val states: List<String>) {
    // Creation keeps targeting the same group when its list of states grows.
    // Rendering still uses the complete signature to reject stale overrides.
    val locationKey: String get() = key.substringBeforeLast(':')
}

/** Design-only trigger overrides. The SDK still owns state selection, setters and
 * storyboards, exactly as for normal VisualStateManager.VisualStateGroups XAML.
 * Source text and the cached base document are never changed by state selection.
 */
internal object WinUIXamlDesignStates {
    private fun parse(markup: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder().parse(InputSource(StringReader(markup)))

    private fun children(element: Element) = (0 until element.childNodes.length)
        .mapNotNull { element.childNodes.item(it) as? Element }
    private fun sdk(element: Element, name: String) =
        element.namespaceURI == WinUIXamlCatalog.PRESENTATION && element.localName == name
    private fun groups(root: Element): List<Pair<DesignerStateGroup, Element>> {
        val result = mutableListOf<Pair<DesignerStateGroup, Element>>()
        fun visit(element: Element, path: String) {
            if (sdk(element, "VisualStateGroup")) {
                val states = children(element).filter { sdk(it, "VisualState") }
                    .map { it.getAttributeNS(WinUIXamlCatalog.XAML, "Name") }
                // Duplicate/missing names have no unambiguous SDK transition target.
                if (states.all(String::isNotEmpty) && states.distinct().size == states.size) {
                    val name = element.getAttributeNS(WinUIXamlCatalog.XAML, "Name")
                    result += DesignerStateGroup("$path:$name:${states.joinToString("|")}",
                        name.ifEmpty { "State group ${result.size + 1}" }, states) to element
                }
            }
            children(element).forEachIndexed { index, child -> visit(child, "$path/$index") }
        }
        visit(root, "")
        return result
    }
    fun groups(markup: String): List<DesignerStateGroup> = groups(parse(markup).documentElement).map { it.first }

    fun select(markup: String, selections: Map<String, String>): String {
        if (selections.isEmpty()) return markup
        val document = parse(markup)
        val groups = groups(document.documentElement)
        selections.forEach { (key, state) ->
            val (group, element) = groups.singleOrNull { it.first.key == key }
                ?: error("The visual state group changed. Select its state again.")
            require(state in group.states) { "The selected visual state no longer exists." }
            children(element).filter { sdk(it, "VisualState") }.forEach { visualState ->
                children(visualState).filter { sdk(it, "VisualState.StateTriggers") }.forEach(visualState::removeChild)
                val property = document.createElementNS(WinUIXamlCatalog.PRESENTATION, "VisualState.StateTriggers")
                val trigger = document.createElementNS(WinUIXamlCatalog.PRESENTATION, "StateTrigger")
                trigger.setAttribute("IsActive", (visualState.getAttributeNS(WinUIXamlCatalog.XAML, "Name") == state).toString())
                property.appendChild(trigger)
                visualState.insertBefore(property, visualState.firstChild)
            }
        }
        val transformer = TransformerFactory.newInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            .newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes") }
        return StringWriter().also { transformer.transform(DOMSource(document), StreamResult(it)) }.toString().also {
            require(it.toByteArray().size <= 128 * 1024) { "The selected visual states exceed the static preview limit." }
        }
    }
}
