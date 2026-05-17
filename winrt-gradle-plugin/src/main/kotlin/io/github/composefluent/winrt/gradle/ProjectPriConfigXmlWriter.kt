package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal object ProjectPriConfigXmlWriter {
    fun write(
        config: Path,
        configRoot: Path,
        projectPriRoot: Path,
        defaultQualifiers: List<Pair<String, String>>,
    ) {
        Files.createDirectories(config.parent)
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val resources = document.createElement("resources")
        resources.setAttribute("targetOsVersion", "10.0.0")
        resources.setAttribute("majorVersion", "1")
        document.appendChild(resources)
        addIndex(
            document = document,
            resources = resources,
            root = "\\",
            startIndexAt = configRoot.resolve("filtered.layout.resfiles").toString(),
            indexers = listOf("RESFILES" to mapOf("qualifierDelimiter" to ".")),
            defaultQualifiers = defaultQualifiers,
        )
        addIndex(
            document = document,
            resources = resources,
            root = "\\",
            startIndexAt = configRoot.resolve("resources.resfiles").toString(),
            indexers = listOf(
                "RESW" to mapOf("convertDotsToSlashes" to "true"),
                "RESJSON" to emptyMap(),
                "RESFILES" to mapOf("qualifierDelimiter" to "."),
            ),
            defaultQualifiers = defaultQualifiers,
        )
        addIndex(
            document = document,
            resources = resources,
            root = "\\",
            startIndexAt = configRoot.resolve("pri.resfiles").toString(),
            indexers = listOf(
                "PRI" to emptyMap(),
                "RESFILES" to mapOf("qualifierDelimiter" to "."),
            ),
            defaultQualifiers = defaultQualifiers,
        )
        addIndex(
            document = document,
            resources = resources,
            root = projectPriRoot.resolve("embed").toString(),
            startIndexAt = configRoot.resolve("embed/embed.resfiles").toString(),
            indexers = listOf(
                "RESFILES" to mapOf("qualifierDelimiter" to "."),
                "EMBEDFILES" to emptyMap(),
            ),
            defaultQualifiers = defaultQualifiers,
        )
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "utf-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.transform(DOMSource(document), StreamResult(config.toFile()))
    }

    private fun addIndex(
        document: org.w3c.dom.Document,
        resources: org.w3c.dom.Element,
        root: String,
        startIndexAt: String,
        indexers: List<Pair<String, Map<String, String>>>,
        defaultQualifiers: List<Pair<String, String>>,
    ) {
        val index = document.createElement("index")
        index.setAttribute("root", root)
        index.setAttribute("startIndexAt", startIndexAt)
        resources.appendChild(index)
        val defaults = document.createElement("default")
        index.appendChild(defaults)
        defaultQualifiers.forEach { (name, value) ->
            val qualifier = document.createElement("qualifier")
            qualifier.setAttribute("name", name)
            qualifier.setAttribute("value", value)
            defaults.appendChild(qualifier)
        }
        indexers.forEach { (type, attributes) ->
            val indexer = document.createElement("indexer-config")
            indexer.setAttribute("type", type)
            attributes.forEach { (name, value) -> indexer.setAttribute(name, value) }
            index.appendChild(indexer)
        }
    }
}
