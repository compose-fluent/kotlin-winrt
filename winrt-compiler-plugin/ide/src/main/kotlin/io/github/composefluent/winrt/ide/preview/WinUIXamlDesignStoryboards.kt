package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import org.w3c.dom.Element
import io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol
import io.github.composefluent.winrt.runtime.WinUIXamlVisualNode
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal data class DesignerStoryboard(val owner: String, val key: String, val sourceId: String = "",
    val instancePath: List<Int>? = null, val instanceLabel: String = "") {
    val id: String get() = "${owner.length}:$owner$key" + if (sourceId.isEmpty()) "" else ":$sourceId:${instancePath?.joinToString(".").orEmpty()}"
    val label: String get() = if (instanceLabel.isNotEmpty()) "$instanceLabel · $key" else if (owner.isEmpty() || owner.startsWith("_KotlinDesignerStoryboardOwner")) key else "$owner · $key"
}

/** SDK resource clocks with design provenance for exact realized template instances. */
internal object WinUIXamlDesignStoryboards {
    data class Document(val markup: String, val storyboards: List<DesignerStoryboard>, val syntheticOwners: Set<String> = emptySet()) {
        fun realized(nodes: List<WinUIXamlVisualNode>): List<DesignerStoryboard> = storyboards.flatMap { clock ->
            if (clock.sourceId.isEmpty()) listOf(clock) else nodes.filter { it.sourceId == clock.sourceId }.map { node ->
                val host = nodes.filter { it.path.size < node.path.size && node.path.take(it.path.size) == it.path && it.name.isNotEmpty() }
                    .maxByOrNull { it.path.size }
                clock.copy(instancePath = node.path, instanceLabel =
                    (host?.name?.let { "$it / " }.orEmpty()) + node.name.ifEmpty { node.typeName.substringAfterLast('.') } +
                        " [${node.path.joinToString(".").ifEmpty { "root" }}]")
            }
        }
    }
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
        // A template definition is not a visual instance. Only provenance-marked
        // resource owners may become playback choices after the SDK realizes them.
        fun visitTemplates(element: Element, inTemplate: Boolean = false) {
            val scoped = inTemplate || element.namespaceURI == WinUIXamlCatalog.PRESENTATION &&
                element.localName in listOf("ControlTemplate", "DataTemplate", "ItemsPanelTemplate")
            if (scoped) {
                val resources = children(element).filter { it.localName.endsWith(".Resources") }.flatMap { property ->
                    children(property).let { if (it.size == 1 && it.single().localName == "ResourceDictionary") children(it.single()) else it }
                }
                val identity = resources.singleOrNull { it.getAttributeNS(WinUIXamlCatalog.XAML, "Key") == WinUIXamlHotReloadProtocol.PREVIEW_SOURCE_KEY }?.textContent.orEmpty()
                if (identity.isNotEmpty()) resourceClocks(element).forEach { clock ->
                    val key = clock.getAttributeNS(WinUIXamlCatalog.XAML, "Key").ifEmpty { clock.getAttributeNS(WinUIXamlCatalog.XAML, "Name") }
                    if (key.isNotEmpty()) candidates += DesignerStoryboard("", key, identity)
                }
            }
            children(element).forEach { visitTemplates(it, scoped) }
        }
        visitTemplates(root)
        val output = if (generated == 0) markup else {
            val transformer = TransformerFactory.newInstance().apply { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }.newTransformer()
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            StringWriter().also { transformer.transform(DOMSource(document), StreamResult(it)) }.toString()
        }
        require(output.toByteArray().size <= 128 * 1024) { "Storyboard preview exceeds the design document limit." }
        return Document(output, candidates.filter { (it.owner.isEmpty() || names[it.owner] == 1) && candidates.count { other -> other.id == it.id } == 1 }, syntheticOwners)
    }
}
