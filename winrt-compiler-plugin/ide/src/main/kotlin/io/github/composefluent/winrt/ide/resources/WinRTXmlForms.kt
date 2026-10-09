package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.windows.toolkit.gradle.ProjectPriManifestSupport

data class WinRTXmlStep(val namespace: String, val name: String, val index: Int)
data class WinRTXmlField(val path: List<WinRTXmlStep>, val attribute: String?, val label: String, val value: String,
    val attributeNamespace: String = "", val createIfMissing: Boolean = false, val choices: List<String> = emptyList()) {
    val id: String get() = path.joinToString { "${it.namespace}:${it.name}:${it.index}" } + "/$attributeNamespace:${attribute.orEmpty()}"
}
data class WinRTXmlNode(val path: List<WinRTXmlStep>, val source: String) {
    val id get() = path.joinToString { "${it.namespace}:${it.name}:${it.index}" }
}
data class WinRTXmlSnapshot(val fields: List<WinRTXmlField>, val errors: List<String>, val applicationCount: Int = 0,
    val nodes: List<WinRTXmlNode> = emptyList())

/** Each operation edits the existing PSI node. Unknown XML is never reserialized. */
object WinRTXmlForms {
    const val FOUNDATION = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
    const val UAP = "http://schemas.microsoft.com/appx/manifest/uap/windows10"
    const val RESTRICTED = "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"

    fun snapshot(file: XmlFile): WinRTXmlSnapshot {
        val root = file.rootTag ?: return WinRTXmlSnapshot(emptyList(), listOf("Enter a valid XML document."))
        val fields = mutableListOf<WinRTXmlField>()
        fun attributes(tag: XmlTag, names: List<String> = tag.attributes.map { it.name }.filterNot { it.startsWith("xmlns") }) {
            names.forEach { name -> fields += WinRTXmlField(address(tag), name.substringAfter(':'), "${tag.name} · $name", tag.getAttributeValue(name).orEmpty(),
                if (':' in name) tag.getNamespaceByPrefix(name.substringBefore(':')) else "") }
        }
        fun text(tag: XmlTag) { fields += WinRTXmlField(address(tag), null, tag.name, leafText(tag)) }
        if (root.localName == "root") {
            val data = root.subTags.filter { it.localName == "data" }
            data.forEach { tag ->
                attributes(tag, listOf("name"))
                tag.subTags.filter { it.localName in listOf("value", "comment") }.forEach(::text)
            }
            val errors = data.map { it.getAttributeValue("name").orEmpty() }.groupingBy { it }.eachCount()
                .filter { it.key.isBlank() || it.value > 1 }.keys.map { "Duplicate or empty .resw key: $it" }
            return WinRTXmlSnapshot(fields, errors)
        }
        if (root.localName != "Package" || root.namespace != FOUNDATION)
            return WinRTXmlSnapshot(emptyList(), listOf("This form requires a Windows 10 AppX Package document."))
        root.subTags.filter { it.namespace == FOUNDATION }.forEach { section ->
            when (section.localName) {
                "Identity" -> attributes(section, listOf("Name", "Publisher", "Version", "ProcessorArchitecture"))
                "Properties" -> section.subTags.filter { it.localName in listOf("DisplayName", "PublisherDisplayName", "Description", "Logo") }.forEach(::text)
                "Resources" -> section.subTags.filter { it.localName == "Resource" }.forEach { attributes(it, listOf("Language")) }
                "Dependencies" -> section.subTags.filter { it.localName == "TargetDeviceFamily" }.forEach {
                    attributes(it, listOf("Name", "MinVersion", "MaxVersionTested"))
                }
                "Applications" -> section.subTags.filter { it.localName == "Application" }.forEach { app ->
                    attributes(app, (listOf("Id", "Executable", "EntryPoint") + app.attributes.map { it.name }.filterNot { it.startsWith("xmlns") }).distinct())
                    fun visit(tag: XmlTag) {
                        attributes(tag)
                        if (tag.subTags.isEmpty() && tag.value.text.isNotBlank()) text(tag)
                        tag.subTags.forEach(::visit)
                    }
                    app.subTags.filter { it.localName in listOf("VisualElements", "Extensions", "ApplicationContentUriRules") }.forEach(::visit)
                }
                "Capabilities", "Extensions" -> {
                    fun visit(tag: XmlTag) { attributes(tag); if (tag.subTags.isEmpty() && tag.value.text.isNotBlank()) text(tag); tag.subTags.forEach(::visit) }
                    section.subTags.forEach(::visit)
                }
            }
        }
        val count = root.findSubTags("Applications").flatMap { it.findSubTags("Application").asIterable() }.size
        WinRTManifestFields.fields(count).forEach { descriptor ->
            val existing = fields.indexOfFirst { it.id == descriptor.id }
            if (existing >= 0) fields[existing] = descriptor.copy(value = fields[existing].value)
            else fields += descriptor
        }
        fun nodes(tag: XmlTag): List<WinRTXmlNode> = listOf(WinRTXmlNode(address(tag), tag.text)) + tag.subTags.flatMap(::nodes)
        return WinRTXmlSnapshot(fields, ProjectPriManifestSupport.validatePackageManifestText(file.text), count, nodes(root))
    }

    fun set(project: Project, file: VirtualFile, field: WinRTXmlField, value: String) = edit(project, file, "Edit ${field.label}") { root ->
        val existing = resolve(root, field.path)
        require((existing?.let { current -> field.attribute?.let { current.getAttributeValue(it, field.attributeNamespace).orEmpty() } ?: leafText(current) } ?: "") == field.value) {
            "The XML value changed. Refresh the form and try again."
        }
        if (existing == null && value.isEmpty() && field.createIfMissing) return@edit
        val tag = existing ?: if (field.createIfMissing) ensure(root, field.path) else error("The XML node changed. Refresh the form and try again.")
        if (field.attribute != null) {
            if (field.attributeNamespace.isNotEmpty()) prefix(root, field.attributeNamespace, namespacePrefix(field.attributeNamespace))
            tag.setAttribute(field.attribute, field.attributeNamespace, value.ifEmpty { null })
        }
        else { require(tag.subTags.isEmpty()) { "Use the XML editor for structured content." }; setLeafText(tag, value) }
        if (value.isEmpty() && field.createIfMissing) {
            if (field.path.last().name == "LockScreen" && field.attribute == "Notification") tag.delete()
            else if (tag.subTags.isEmpty() && tag.attributes.none { !it.name.startsWith("xmlns") } && tag.value.text.isBlank() &&
                field.path.last().name in listOf("DefaultTile", "SplashScreen", "TileUpdate", "Description")) tag.delete()
        }
    }

    fun removeEntry(project: Project, file: VirtualFile, field: WinRTXmlField) = edit(project, file, "Remove XML entry") { root ->
        val tag = resolve(root, field.path) ?: error("The XML node changed. Refresh the form.")
        require(tag.localName in listOf("data", "Capability", "DeviceCapability", "Protocol", "FileTypeAssociation", "Resource", "Rule"))
        require(field.attribute?.let { tag.getAttributeValue(it).orEmpty() } == field.value) { "The XML value changed. Refresh the form." }
        val parent = tag.parentTag
        if (parent?.localName == "Extension" && parent.subTags.size == 1) parent.delete() else tag.delete()
    }

    fun addResw(project: Project, file: VirtualFile, key: String, value: String) = edit(project, file, "Add .resw resource") { root ->
        require(root.localName == "root" && key.isNotBlank()) { "Enter a nonempty resource key." }
        require(root.findSubTags("data").none { it.getAttributeValue("name") == key }) { "The resource key already exists: $key" }
        val data = XmlElementFactory.getInstance(project).createTagFromText("<data xml:space=\"preserve\"><value/></data>")
        data.setAttribute("name", key)
        setLeafText(data.findFirstSubTag("value")!!, value)
        root.addSubTag(data, false)
    }

    fun addContentUriRule(project: Project, file: VirtualFile, application: Int, match: String, type: String) = edit(project, file, "Add content URI rule") { root ->
        require(match.isNotBlank() && match.none { it == '\r' || it == '\n' } && type in listOf("include", "exclude")) { "Enter a URI and choose Include or Exclude." }
        val app = root.findFirstSubTag("Applications")?.findSubTags("Application")?.getOrNull(application) ?: error("Select an application entry.")
        val rules = child(app, "ApplicationContentUriRules", UAP)
        require(rules.subTags.none { it.getAttributeValue("Match") == match && it.getAttributeValue("Type") == type }) { "The URI rule already exists." }
        val prefix = prefix(root, UAP, "uap")
        val rule = XmlElementFactory.getInstance(project).createTagFromText("<$prefix:Rule xmlns:$prefix=\"$UAP\"/>")
        rule.setAttribute("Match", match)
        rule.setAttribute("Type", type)
        rules.addSubTag(rule, false)
    }

    fun addCapability(project: Project, file: VirtualFile, name: String, restricted: Boolean, device: Boolean = false) = edit(project, file, "Add AppX capability") { root ->
        require(name.isNotBlank()) { "Enter a capability name." }
        val namespace = if (restricted) RESTRICTED else FOUNDATION
        val section = child(root, "Capabilities", FOUNDATION)
        require(section.subTags.none { it.namespace == namespace && it.getAttributeValue("Name") == name }) { "The capability already exists." }
        val prefix = if (restricted) prefix(root, namespace, "rescap") + ":" else ""
        val tagName = if (device) "DeviceCapability" else "Capability"
        val capability = XmlElementFactory.getInstance(project).createTagFromText("<$prefix$tagName xmlns${if (prefix.isEmpty()) "" else ":${prefix.dropLast(1)}"}=\"$namespace\"/>")
        capability.setAttribute("Name", name)
        insertCapability(section, capability)
    }

    internal fun addCapability(project: Project, file: VirtualFile, capability: WinRTManifestCapability) = edit(project, file, "Add AppX capability") { root ->
        val section = child(root, "Capabilities", FOUNDATION)
        val name = if (capability.device) "DeviceCapability" else "Capability"
        require(section.subTags.none { it.namespace == capability.namespace && it.localName == name && it.getAttributeValue("Name") == capability.name }) { "The capability already exists." }
        if (capability.namespace != FOUNDATION) prefix(root, capability.namespace, namespacePrefix(capability.namespace))
        val tag = section.createChildTag(name, capability.namespace, null, false)
        tag.setAttribute("Name", capability.name)
        insertCapability(section, tag)
    }

    internal fun addDeclaration(project: Project, file: VirtualFile, application: Int, declaration: WinRTManifestDeclaration) = edit(project, file, "Add manifest declaration") { root ->
        val owner = if (declaration.packageLevel) root else resolve(root, WinRTManifestFields.application(application)) ?: error("Select an application entry.")
        val section = child(owner, "Extensions", FOUNDATION)
        if (declaration.namespace != FOUNDATION) prefix(root, declaration.namespace, namespacePrefix(declaration.namespace))
        val extension = section.addSubTag(section.createChildTag("Extension", declaration.namespace, null, false), false)
        extension.setAttribute("Category", declaration.category)
        declaration.body?.let { body ->
            if (body.namespace != FOUNDATION) prefix(root, body.namespace, namespacePrefix(body.namespace))
            extension.addSubTag(extension.createChildTag(body.name, body.namespace, null, false), false)
        }
    }

    fun addExtension(project: Project, file: VirtualFile, application: Int, name: String, fileType: String?) =
        edit(project, file, "Add AppX ${if (fileType == null) "protocol" else "file association"}") { root ->
            require(name.isNotBlank()) { "Enter an extension name." }
            if (fileType != null) require(fileType.startsWith('.') && fileType.length > 1 && fileType.none { it in "/\\\r\n" }) { "Enter a file extension such as .txt." }
            val app = root.findFirstSubTag("Applications")?.findSubTags("Application")?.getOrNull(application)
                ?: error("Select an application entry.")
            val section = child(app, "Extensions", FOUNDATION)
            val prefix = prefix(root, UAP, "uap")
            val category = if (fileType == null) "windows.protocol" else "windows.fileTypeAssociation"
            val body = if (fileType == null) "<$prefix:Protocol/>" else
                "<$prefix:FileTypeAssociation><$prefix:SupportedFileTypes><$prefix:FileType/></$prefix:SupportedFileTypes></$prefix:FileTypeAssociation>"
            val extension = XmlElementFactory.getInstance(project).createTagFromText("<$prefix:Extension xmlns:$prefix=\"$UAP\" Category=\"$category\">$body</$prefix:Extension>")
            extension.subTags.single().setAttribute("Name", name)
            if (fileType != null) setLeafText(extension.subTags.single().subTags.single().subTags.single(), fileType)
            section.addSubTag(extension, false)
        }

    private fun edit(project: Project, file: VirtualFile, title: String, action: (XmlTag) -> Unit) {
        WriteCommandAction.runWriteCommandAction(project, title, null, Runnable {
            val document = FileDocumentManager.getInstance().getDocument(file) ?: error("No editable document.")
            PsiDocumentManager.getInstance(project).commitDocument(document)
            val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: error("Open this file as XML.")
            action(xml.rootTag ?: error("No XML root element."))
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
        })
    }

    private fun child(parent: XmlTag, name: String, namespace: String): XmlTag =
        parent.subTags.firstOrNull { it.localName == name && it.namespace == namespace }
            ?: parent.addSubTag(parent.createChildTag(name, namespace, null, false), false)

    private fun insertCapability(section: XmlTag, tag: XmlTag) {
        val before = if (tag.localName == "Capability") section.subTags.firstOrNull { it.localName in listOf("CustomCapability", "DeviceCapability") } else null
        if (before == null) section.addSubTag(tag, false) else section.addBefore(tag, before)
    }

    private fun ensure(root: XmlTag, path: List<WinRTXmlStep>): XmlTag {
        require(path.firstOrNull() == WinRTXmlStep(root.namespace, root.localName, 0))
        var tag = root
        path.drop(1).forEach { step ->
            val children = tag.subTags.filter { it.localName == step.name && it.namespace == step.namespace }
            tag = children.getOrNull(step.index) ?: run {
                require(step.index == 0 && step.name != "Application") { "The application entry changed. Refresh the form." }
                if (step.namespace != FOUNDATION) prefix(root, step.namespace, namespacePrefix(step.namespace))
                child(tag, step.name, step.namespace)
            }
        }
        return tag
    }

    internal fun namespacePrefix(namespace: String): String = when {
        namespace == UAP -> "uap"
        namespace.startsWith("$UAP/") -> "uap${namespace.substringAfterLast('/')}"
        namespace.contains("restrictedcapabilities") -> "rescap${namespace.substringAfterLast('/').takeIf { it.toIntOrNull() != null }.orEmpty()}"
        namespace.contains("desktop") -> "desktop${namespace.substringAfterLast('/').takeIf { it.toIntOrNull() != null }.orEmpty()}"
        else -> "ext"
    }

    internal fun setSelection(project: Project, file: VirtualFile, application: Int, rotation: Boolean, value: String, checked: Boolean) = edit(project, file, "Edit manifest selection") { root ->
        val allowed = if (rotation) listOf("landscape", "portrait", "landscapeFlipped", "portraitFlipped")
            else listOf("square150x150Logo", "wide310x150Logo", "square310x310Logo")
        require(value in allowed)
        val path = WinRTManifestFields.visual(application) + if (rotation) listOf(WinRTXmlStep(UAP, "InitialRotationPreference", 0))
            else listOf(WinRTXmlStep(UAP, "DefaultTile", 0), WinRTXmlStep(UAP, "ShowNameOnTiles", 0))
        val group = resolve(root, path) ?: if (checked) ensure(root, path) else return@edit
        val name = if (rotation) "Rotation" else "ShowOn"
        val attribute = if (rotation) "Preference" else "Tile"
        val existing = group.subTags.firstOrNull { it.namespace == UAP && it.localName == name && it.getAttributeValue(attribute) == value }
        if (checked && existing == null) group.addSubTag(group.createChildTag(name, UAP, null, false), false).setAttribute(attribute, value)
        if (!checked) existing?.delete()
        if (group.subTags.isEmpty()) group.delete()
    }

    internal fun removeNode(project: Project, file: VirtualFile, node: WinRTXmlNode) = edit(project, file, "Remove manifest declaration") { root ->
        require(node.path.any { it.name == "Extensions" })
        val tag = resolve(root, node.path) ?: error("The declaration changed. Refresh the form.")
        require(tag.text == node.source) { "The declaration changed. Refresh the form." }
        tag.delete()
    }

    internal fun addNode(project: Project, file: VirtualFile, parent: WinRTXmlNode, namespace: String, name: String) = edit(project, file, "Add manifest declaration property") { root ->
        require(parent.path.any { it.name == "Extensions" } && name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
        val tag = resolve(root, parent.path) ?: error("The declaration changed. Refresh the form.")
        require(tag.text == parent.source) { "The declaration changed. Refresh the form." }
        if (namespace != FOUNDATION) prefix(root, namespace, namespacePrefix(namespace))
        tag.addSubTag(tag.createChildTag(name, namespace, null, false), false)
    }

    private fun leafText(tag: XmlTag) = tag.value.textElements.joinToString("") { it.value }
    private fun setLeafText(tag: XmlTag, text: String) {
        val fragments = tag.value.textElements
        if (fragments.isEmpty()) tag.add(XmlElementFactory.getInstance(tag.project).createDisplayText(text))
        else {
            fragments.first().value = text
            fragments.drop(1).forEach { it.delete() }
        }
        // XmlTagValue.setText removes other children; edit text fragments only
        // so comments and processing instructions inside a leaf survive.
    }

    private fun prefix(root: XmlTag, namespace: String, base: String): String {
        val existing = root.getPrefixByNamespace(namespace)?.takeIf(String::isNotEmpty)
        val chosen = existing ?: generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base$it" }
            .first { root.getNamespaceByPrefix(it).isEmpty() }.also { root.setAttribute("xmlns:$it", namespace) }
        val ignored = root.getAttributeValue("IgnorableNamespaces").orEmpty().split(Regex("\\s+")).filter(String::isNotEmpty)
        if (chosen !in ignored) root.setAttribute("IgnorableNamespaces", (ignored + chosen).joinToString(" "))
        return chosen
    }

    private fun address(tag: XmlTag): List<WinRTXmlStep> = generateSequence(tag) { it.parentTag }.toList().asReversed().map {
        WinRTXmlStep(it.namespace, it.localName, it.parentTag?.subTags?.filter { sibling -> sibling.localName == it.localName && sibling.namespace == it.namespace }?.indexOf(it) ?: 0)
    }
    private fun resolve(root: XmlTag, path: List<WinRTXmlStep>): XmlTag? {
        if (path.firstOrNull()?.let { it.name == root.localName && it.namespace == root.namespace } != true) return null
        var tag = root
        for (step in path.drop(1)) tag = tag.subTags.filter { it.localName == step.name && it.namespace == step.namespace }.getOrNull(step.index) ?: return null
        return tag
    }
}
