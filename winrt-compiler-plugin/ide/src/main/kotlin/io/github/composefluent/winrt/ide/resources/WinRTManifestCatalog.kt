package io.github.composefluent.winrt.ide.resources

import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

internal data class WinRTManifestCapability(val name: String, val namespace: String, val device: Boolean = false) {
    val id get() = "$namespace:${if (device) "DeviceCapability" else "Capability"}:$name"
    val title get() = manifestTitle(name)
    val description get() = when (name) {
        "internetClient" -> "Access the Internet as a client."
        "internetClientServer" -> "Receive incoming network connections and access the Internet."
        "privateNetworkClientServer" -> "Send and receive data on private networks."
        "documentsLibrary" -> "Access the user's Documents library. File type restrictions may apply."
        "picturesLibrary" -> "Access the user's Pictures library."
        "videosLibrary" -> "Access the user's Videos library."
        "musicLibrary" -> "Access the user's Music library."
        "webcam" -> "Access camera devices. The user can control camera access in Windows settings."
        "microphone" -> "Access microphone devices. The user can control microphone access in Windows settings."
        "runFullTrust" -> "Activate a full trust desktop process from the package."
        "backgroundMediaPlayback" -> "Play audio or video while the application is in the background."
        "spatialPerception" -> "Access spatial mapping information."
        "userNotificationListener" -> "Read and manage notifications with the user's permission."
        "location" -> "Access the device's location with the user's permission."
        else -> "Manifest capability $name."
    } + if (namespace.contains("restrictedcapabilities")) " This is a restricted capability; distribution requirements apply." else ""
}
internal data class WinRTManifestAttribute(val name: String, val namespace: String, val required: Boolean, val choices: List<String>)
internal data class WinRTManifestChild(val key: String, val name: String, val namespace: String, val minimum: Int, val maximum: Int)
internal data class WinRTManifestDefinition(val key: String, val name: String, val namespace: String,
    val attributes: List<WinRTManifestAttribute>, val children: List<WinRTManifestChild>, val text: Boolean, val choices: List<String>)
internal data class WinRTManifestDeclaration(val category: String, val namespace: String, val definition: String, val body: WinRTManifestChild?, val packageLevel: Boolean) {
    val id get() = "$namespace:$category:$packageLevel"
    val title get() = manifestTitle(category.substringAfterLast('.')) + if (packageLevel) " (package)" else ""
}

/** Read the same local SDK schemas used by the XML resolver. No network fetches
 * or handwritten declaration/category allowlists are involved. */
internal class WinRTManifestCatalog private constructor(private val elements: Map<String, Element>, private val types: Map<String, Element>,
    private val groups: Map<String, Element>, private val attributes: Map<String, Element>) {
    private val definitions = mutableMapOf<String, WinRTManifestDefinition>()
    private val localElements = elements.toMutableMap()
    private val substitution = elements.filterValues { it.hasAttribute("substitutionGroup") }.entries.groupBy { qualified(it.value, it.value.getAttribute("substitutionGroup")) }
    val capabilities: List<WinRTManifestCapability>
    val declarations: List<WinRTManifestDeclaration>

    init {
        capabilities = elements.entries.filter { it.value.getAttribute("name") in listOf("Capability", "DeviceCapability") &&
            namespace(it.value).startsWith("http://schemas.microsoft.com/appx/manifest/") }.flatMap { (key, element) ->
            definition(key)?.attributes?.firstOrNull { it.name == "Name" }?.choices.orEmpty()
                .map { WinRTManifestCapability(it, namespace(element), element.getAttribute("name") == "DeviceCapability") }
        }.plus(commonOpenCapabilities.filter { "${it.namespace}:${if (it.device) "DeviceCapability" else "Capability"}" in elements })
            .distinctBy { it.id }.sortedBy { it.title.lowercase() }
        declarations = elements.entries.filter { it.value.getAttribute("name") == "Extension" &&
            namespace(it.value).startsWith("http://schemas.microsoft.com/appx/manifest/") }.flatMap { (key, element) ->
            val definition = definition(key) ?: return@flatMap emptyList()
            val packageLevel = element.getAttribute("substitutionGroup").substringAfter(':') != "ApplicationExtensionChoice"
            definition.attributes.firstOrNull { it.name == "Category" }?.choices.orEmpty().map { category ->
                val expected = category.substringAfterLast('.')
                WinRTManifestDeclaration(category, namespace(element), key,
                    definition.children.firstOrNull { it.name.equals(expected, true) }, packageLevel)
            }
        }.distinctBy { it.id }.sortedWith(compareBy<WinRTManifestDeclaration> { it.title.lowercase() }.thenBy { it.namespace })
    }

    @Synchronized
    fun definition(key: String): WinRTManifestDefinition? = definitions[key] ?: localElements[key]?.let { element ->
        val attrs = mutableListOf<WinRTManifestAttribute>()
        val children = mutableListOf<WinRTManifestChild>()
        var simple = false
        var textChoices = emptyList<String>()
        val visited = mutableSetOf<String>()
        fun visit(node: Element) {
            when (node.localName) {
                "attribute" -> {
                    val referenced = node.getAttribute("ref").takeIf { it.isNotEmpty() }?.let { attributes[qualified(node, it)] }
                    val owner = referenced ?: node
                    val ns = if (referenced != null || owner.getAttribute("form") == "qualified") namespace(owner) else ""
                    val name = owner.getAttribute("name")
                    if (name.isNotEmpty() && node.getAttribute("use") != "prohibited") attrs += WinRTManifestAttribute(name, ns,
                        node.getAttribute("use") == "required", enumValues(owner))
                }
                "attributeGroup" -> {
                    val reference = qualified(node, node.getAttribute("ref"))
                    if (visited.add(reference)) groups[reference]?.children()?.forEach(::visit)
                }
                "element" -> {
                    val reference = node.getAttribute("ref")
                    val choices = if (reference.isNotEmpty()) {
                        val q = qualified(node, reference)
                        val global = elements[q]
                        if (global?.getAttribute("abstract") == "true") substitution[q].orEmpty().filter { it.value.getAttribute("abstract") != "true" }.map { it.key to it.value }
                        else listOfNotNull(global?.let { q to it })
                    } else listOf("$key/${children.size}:${node.getAttribute("name")}" to node)
                    choices.forEach { (childKey, child) ->
                        localElements[childKey] = child
                        val optionalGroup = generateSequence(node.parentNode as? Element) { it.parentNode as? Element }
                            .takeWhile { it.localName != "complexType" }.any { it.localName == "choice" || it.getAttribute("minOccurs") == "0" }
                        children += WinRTManifestChild(childKey, child.getAttribute("name"), namespace(child),
                            if (optionalGroup) 0 else node.getAttribute("minOccurs").toIntOrNull() ?: 1,
                            node.getAttribute("maxOccurs").let { if (it == "unbounded") Int.MAX_VALUE else it.toIntOrNull() ?: 1 })
                    }
                }
                "extension", "restriction" -> {
                    val base = qualified(node, node.getAttribute("base"))
                    val type = types[base]
                    if (type?.localName == "complexType" && visited.add(base)) type.children().forEach(::visit)
                    else { simple = true; textChoices = enumValues(node) }
                    node.children().forEach(::visit)
                }
                "simpleContent" -> { simple = true; node.children().forEach(::visit) }
                "group" -> {
                    val reference = qualified(node, node.getAttribute("ref"))
                    if (node.hasAttribute("ref") && visited.add(reference)) groups[reference]?.children()?.forEach(::visit)
                    else node.children().forEach(::visit)
                }
                "complexType", "complexContent", "all", "sequence", "choice" -> node.children().forEach(::visit)
            }
        }
        val inline = element.children().firstOrNull { it.localName == "complexType" }
        val type = types[qualified(element, element.getAttribute("type"))]
        if (inline != null) visit(inline)
        else if (type?.localName == "complexType") visit(type)
        else { simple = element.hasAttribute("type") || element.children().any { it.localName == "simpleType" }; textChoices = enumValues(element) }
        WinRTManifestDefinition(key, element.getAttribute("name"), namespace(element), attrs.distinctBy { "${it.namespace}:${it.name}" },
            children.distinctBy { it.key }, simple, textChoices).also { definitions[key] = it }
    }

    fun declarationFields(snapshot: WinRTXmlSnapshot, declaration: WinRTManifestDeclaration, extension: WinRTXmlNode): List<WinRTXmlField> {
        val result = snapshot.fields.filter { it.path.take(extension.path.size) == extension.path }.toMutableList()
        fun visit(node: WinRTXmlNode, key: String) {
            val definition = definition(key) ?: return
            definition.attributes.filterNot { node == extension && it.name == "Category" }.forEach { attribute ->
                val field = WinRTXmlField(node.path, attribute.name, manifestTitle(attribute.name), "", attribute.namespace, true,
                    if (attribute.choices.isEmpty()) emptyList() else (if (attribute.required) emptyList() else listOf("")) + attribute.choices)
                val existing = result.indexOfFirst { it.id == field.id }
                if (existing < 0) result += field else result[existing] = field.copy(value = result[existing].value)
            }
            if (definition.text && result.none { it.path == node.path && it.attribute == null }) result += WinRTXmlField(node.path, null, definition.name, "", createIfMissing = true, choices = definition.choices)
            definition.children.forEach { child -> snapshot.nodes.filter { it.path.size == node.path.size + 1 && it.path.dropLast(1) == node.path &&
                it.path.last().name == child.name && it.path.last().namespace == child.namespace }.forEach { visit(it, child.key) } }
        }
        visit(extension, declaration.definition)
        return result
    }

    fun nodeDefinition(snapshot: WinRTXmlSnapshot, declaration: WinRTManifestDeclaration, extension: WinRTXmlNode, node: WinRTXmlNode): WinRTManifestDefinition? {
        var current = definition(declaration.definition) ?: return null
        node.path.drop(extension.path.size).forEach { step ->
            current = current.children.firstOrNull { it.name == step.name && it.namespace == step.namespace }?.let { definition(it.key) } ?: return null
        }
        return current
    }

    private fun enumValues(element: Element, visited: Set<String> = emptySet()): List<String> {
        val values = element.getElementsByTagNameNS(XSD, "enumeration")
        if (values.length > 0) return (0 until values.length).map { (values.item(it) as Element).getAttribute("value") }
        val refs = listOf("type", "base", "memberTypes").flatMap { element.getAttribute(it).split(' ').filter(String::isNotEmpty) }
        val children = element.children().filter { it.localName in listOf("simpleType", "restriction", "union") }
        return (refs.flatMap { ref ->
            val key = qualified(element, ref)
            if (key in visited) emptyList() else if (key == "$XSD:boolean") listOf("true", "false")
            else types[key]?.let { enumValues(it, visited + key) }.orEmpty()
        } + children.flatMap { enumValues(it, visited) }).distinct()
    }

    companion object {
        private const val XSD = "http://www.w3.org/2001/XMLSchema"
        // These schema types accept an open name/pattern, not an enumeration.
        // Common choices from Microsoft's App capability declarations reference:
        // https://learn.microsoft.com/windows/apps/package-and-deploy/app-capability-declarations
        private val commonOpenCapabilities = listOf("location", "microphone", "proximity", "webcam", "usb", "humaninterfacedevice",
            "pointOfService", "bluetooth", "wiFiControl", "radios", "optical", "activity", "humanPresence", "serialcommunication", "lowLevel")
            .map { WinRTManifestCapability(it, WinRTXmlForms.FOUNDATION, true) } +
            listOf("runFullTrust", "broadFileSystemAccess", "inputInjectionBrokered", "appCaptureSettings", "enterpriseDataPolicy",
                "cellularDeviceControl", "cellularDeviceIdentity", "cellularMessaging", "deviceUnlock", "dualSimTiles", "enterpriseDeviceLockdown")
                .map { WinRTManifestCapability(it, WinRTXmlForms.RESTRICTED) }
        val Empty = WinRTManifestCatalog(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        fun read(schemas: Map<String, Path>): WinRTManifestCatalog {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val globals = schemas.values.distinct().flatMap { file -> Files.newInputStream(file).use { factory.newDocumentBuilder().parse(it).documentElement.children() } } +
                WinRTManifestBundledSchemas.sources().filterKeys { it !in schemas }.values.flatMap { text ->
                    text.byteInputStream().use { factory.newDocumentBuilder().parse(it).documentElement.children() }
                }
            fun global(kind: String) = globals.filter { it.localName == kind && it.hasAttribute("name") }.associateBy { "${namespace(it)}:${it.getAttribute("name")}" }
            val packageExtension = globals.firstOrNull { it.localName == "complexType" && it.getAttribute("name") == "CT_PackageExtensions" }
                ?.getElementsByTagNameNS(XSD, "element")?.let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element }.firstOrNull { it.getAttribute("name") == "Extension" } }
            val elementMap = global("element") + listOfNotNull(packageExtension?.let { "${namespace(it)}:PackageExtension" to it }).toMap()
            return WinRTManifestCatalog(elementMap, global("complexType") + global("simpleType"), global("attributeGroup") + global("group"), global("attribute"))
        }
        private fun namespace(element: Element) = element.ownerDocument.documentElement.getAttribute("targetNamespace")
        private fun qualified(element: Element, name: String): String = if (':' in name) "${element.lookupNamespaceURI(name.substringBefore(':')).orEmpty()}:${name.substringAfter(':')}"
            else "${namespace(element)}:$name"
        private fun Element.children(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.namespaceURI == XSD }
    }
}

internal fun manifestTitle(name: String) = name.substringAfter(':').replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replaceFirstChar(Char::uppercase)
