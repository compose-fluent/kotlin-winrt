package io.github.composefluent.winrt.metadata

import org.xml.sax.Locator
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** XamlCompiler DOM source locations identify the exact element, including
 * unnamed elements and template instances. Paths count XML elements, not visuals.
 */
object WinUIXamlDesignConnections {
    fun connections(page: WinUIXamlPageDeclaration): List<WinUIXamlConnectionDeclaration> {
        val scopes = page.connections.filter { it.isTemplateChild && it.bindings.isNotEmpty() }.map { it.scopeId }.toSet()
        return page.connections.filter { !it.isTemplateChild || it.scopeId in scopes }
    }

    fun paths(text: String, connections: List<WinUIXamlConnectionDeclaration>): Map<List<Int>, WinUIXamlConnectionDeclaration> {
        if (connections.isEmpty()) return emptyMap()
        val source = text.removePrefix("\uFEFF")
        val starts = mutableListOf(0)
        source.forEachIndexed { index, char -> if (char == '\n') starts += index + 1 }
        fun offset(line: Int, column: Int): Int = (starts.getOrNull(line - 1) ?: source.length) + (column - 1).coerceAtLeast(0)
        data class Level(val path: List<Int>, var children: Int = 0)
        val stack = mutableListOf<Level>()
        val result = linkedMapOf<List<Int>, WinUIXamlConnectionDeclaration>()
        val locations = connections.groupBy { offset(it.location.line, it.location.column) }
        var locator: Locator? = null
        val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
        reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        reader.contentHandler = object : DefaultHandler() {
            override fun setDocumentLocator(value: Locator) { locator = value }
            override fun startElement(uri: String, local: String, qualified: String, attributes: org.xml.sax.Attributes) {
                val parent = stack.lastOrNull()
                val path = parent?.let { it.path + it.children++ } ?: emptyList()
                stack += Level(path)
                val end = locator?.let { offset(it.lineNumber, it.columnNumber) }?.coerceAtMost(source.length) ?: return
                val start = source.lastIndexOf('<', (end - 1).coerceAtLeast(0))
                val matches = locations.filterKeys { it in start until end }.values.flatten()
                if (matches.size == 1) result[path] = matches.single()
            }
            override fun endElement(uri: String, local: String, qualified: String) { stack.removeAt(stack.lastIndex) }
        }
        reader.parse(InputSource(StringReader(source)))
        return result
    }
}
