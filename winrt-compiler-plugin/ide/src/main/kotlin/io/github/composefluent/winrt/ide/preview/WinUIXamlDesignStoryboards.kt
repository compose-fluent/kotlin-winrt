package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class DesignerStoryboard(val owner: String, val key: String) {
    val id: String get() = "${owner.length}:$owner$key"
    val label: String get() = if (owner.isEmpty() || owner.startsWith("_KotlinDesignerStoryboardOwner")) key else "$owner · $key"
}

/** SDK resource clocks; never guess a template realization's namescope. */
internal object WinUIXamlDesignStoryboards {
    data class Document(val markup: String, val storyboards: List<DesignerStoryboard>, val syntheticOwners: Set<String> = emptySet())
    fun discover(markup: String): List<DesignerStoryboard> = prepare(markup).storyboards
    fun prepare(markup: String): Document {
        val document = WinUIXamlDesignStates.parse(markup)
        val root = document.documentElement
        fun children(element: Element) = (0 until element.childNodes.length).mapNotNull { element.childNodes.item(it) as? Element }
        fun resourceClocks(element: Element) = children(element).filter { it.localName.endsWith(".Resources") }.flatMap { property ->
            children(property).let { entries ->
                if (entries.size == 1 && entries.single().localName == "ResourceDictionary") children(entries.single()) else entries
            }.filter { it.namespaceURI == WinUIXamlCatalog.PRESENTATION && it.localName == "Storyboard" }
        }
        val candidates = mutableListOf<DesignerStoryboard>()
        val names = mutableMapOf<String, Int>()
        val usedNames = mutableSetOf<String>()
        fun collectNames(element: Element) {
            element.getAttributeNS(WinUIXamlCatalog.XAML, "Name").takeIf(String::isNotEmpty)?.let(usedNames::add)
            children(element).forEach(::collectNames)
        }
        collectNames(root)
        val syntheticOwners = mutableSetOf<String>()
        var generated = 0
        fun visit(element: Element) {
            if (element.namespaceURI == WinUIXamlCatalog.PRESENTATION && element.localName in listOf("ControlTemplate", "DataTemplate", "ItemsPanelTemplate", "Style")) return
            var name = element.getAttributeNS(WinUIXamlCatalog.XAML, "Name")
            val clocks = resourceClocks(element)
            if (element !== root && name.isEmpty() && clocks.isNotEmpty()) {
                do { name = "_KotlinDesignerStoryboardOwner${generated++}" } while (name in usedNames)
                usedNames += name
                syntheticOwners += name
                element.setAttributeNS(WinUIXamlCatalog.XAML, "x:Name", name)
            }
            if (name.isNotEmpty()) names[name] = (names[name] ?: 0) + 1
            if (element === root || name.isNotEmpty()) clocks.forEach { storyboard ->
                val key = storyboard.getAttributeNS(WinUIXamlCatalog.XAML, "Key").ifEmpty { storyboard.getAttributeNS(WinUIXamlCatalog.XAML, "Name") }
                if (key.isNotEmpty()) candidates += DesignerStoryboard(if (element === root) "" else name, key)
            }
            children(element).filterNot { it.localName.endsWith(".Resources") }.forEach(::visit)
        }
        visit(root)
        val output = if (generated == 0) markup else {
            val transformer = TransformerFactory.newInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }.newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            StringWriter().also { transformer.transform(DOMSource(document), StreamResult(it)) }.toString()
        }
        require(output.toByteArray().size <= 128 * 1024) { "Storyboard preview exceeds the design document limit." }
        return Document(output, candidates.filter { (it.owner.isEmpty() || names[it.owner] == 1) && candidates.count { other -> other.id == it.id } == 1 }, syntheticOwners)
    }
}
