package io.github.composefluent.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import io.github.composefluent.winrt.runtime.XamlFramework

/** DirectUISchemaContext.DirectUI2010Paths in XAMLC owns the presentation namespace aliases. */
object WinUiXamlNamespaces {
    const val PRESENTATION = "http://schemas.microsoft.com/winfx/2006/xaml/presentation"
    const val XAML = "http://schemas.microsoft.com/winfx/2006/xaml"
    private val presentationSuffixes = listOf("", ".Automation", ".Automation.Peers", ".Automation.Provider",
        ".Controls", ".Controls.Primitives", ".Data", ".Documents", ".Input", ".Interop", ".Markup", ".Media",
        ".Media.Animation", ".Media.Imaging", ".Media.Media3D", ".Navigation", ".Resources", ".Shapes", ".Threading")

    fun namespaces(uri: String, framework: XamlFramework = XamlFramework.WinUi): List<String> = when {
        uri.substringBefore('?') in setOf(PRESENTATION, "http://schemas.microsoft.com/windows/2010/directui") ->
            presentationSuffixes.map { framework.xamlNamespace + it } + listOf("Windows.UI", "Windows.UI.Text")
        uri.startsWith("using:") -> listOf(uri.removePrefix("using:").substringBefore('?')).filter { it.isNotBlank() }
        else -> emptyList()
    }

    /** Namespace discovery for application headers only; XAMLC owns syntax and binding validation. */
    fun typeReferences(path: Path, framework: XamlFramework = XamlFramework.WinUi): List<List<String>> {
        val references = linkedSetOf<List<String>>()
        val factory = XMLInputFactory.newFactory().apply {
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty("javax.xml.stream.isSupportingExternalEntities", false)
        }
        Files.newInputStream(path).use { input ->
            val xml = factory.createXMLStreamReader(input)
            try {
                while (xml.hasNext()) {
                    if (xml.next() != XMLStreamConstants.START_ELEMENT) continue
                    fun include(namespace: String?, local: String) {
                        namespaces(namespace.orEmpty(), framework).map { "$it.${local.substringBefore('.')}" }
                            .takeIf { it.isNotEmpty() }?.let(references::add)
                    }
                    include(xml.namespaceURI, xml.localName)
                    for (i in 0 until xml.namespaceCount) {
                        Regex("""\b([\w]+):([\w]+)\(""")
                            .findAll(xml.getNamespaceURI(i).orEmpty().substringAfter('?', "")).forEach { match ->
                                include(xml.getNamespaceURI(match.groupValues[1]), match.groupValues[2])
                            }
                    }
                    for (i in 0 until xml.attributeCount) {
                        include(xml.getAttributeNamespace(i), xml.getAttributeLocalName(i))
                        Regex("""\b([\w]+):([\w]+)""").findAll(xml.getAttributeValue(i)).forEach { match ->
                            include(xml.getNamespaceURI(match.groupValues[1]), match.groupValues[2])
                        }
                    }
                }
            } finally { xml.close() }
        }
        return references.sortedBy { it.joinToString("\u0000") }
    }
}
