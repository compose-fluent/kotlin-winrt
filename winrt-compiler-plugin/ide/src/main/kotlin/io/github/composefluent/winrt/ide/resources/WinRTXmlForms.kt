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
data class WinRTXmlField(val path: List<WinRTXmlStep>, val attribute: String?, val label: String, val value: String) {
    val id: String get() = path.joinToString { "${it.namespace}:${it.name}:${it.index}" } + "/${attribute.orEmpty()}"
}
data class WinRTXmlSnapshot(val fields: List<WinRTXmlField>, val errors: List<String>, val applicationCount: Int = 0)

/** Each operation edits the existing PSI node. Unknown XML is never reserialized. */
object WinRTXmlForms {
    const val FOUNDATION = "http://schemas.microsoft.com/appx/manifest/foundation/windows10"
    const val UAP = "http://schemas.microsoft.com/appx/manifest/uap/windows10"
    const val RESTRICTED = "http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"

    fun snapshot(file: XmlFile): WinRTXmlSnapshot {
        val root = file.rootTag ?: return WinRTXmlSnapshot(emptyList(), listOf("Enter a valid XML document."))
        val fields = mutableListOf<WinRTXmlField>()
        fun attributes(tag: XmlTag, names: List<String> = tag.attributes.map { it.name }.filterNot { it.startsWith("xmlns") }) {
            names.forEach { name -> fields += WinRTXmlField(address(tag), name, "${tag.name} · $name", tag.getAttributeValue(name).orEmpty()) }
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
                    attributes(app, listOf("Id", "Executable", "EntryPoint"))
                    fun visit(tag: XmlTag) {
                        attributes(tag)
                        if (tag.subTags.isEmpty() && tag.value.text.isNotBlank()) text(tag)
                        tag.subTags.forEach(::visit)
                    }
                    app.subTags.filter { it.localName in listOf("VisualElements", "Extensions", "ApplicationContentUriRules") }.forEach(::visit)
                }
                "Capabilities", "Extensions" -> {
                    fun visit(tag: XmlTag) { attributes(tag); tag.subTags.forEach(::visit) }
                    section.subTags.forEach(::visit)
                }
            }
        }
        return WinRTXmlSnapshot(fields, ProjectPriManifestSupport.validatePackageManifestText(file.text),
            root.findSubTags("Applications").flatMap { it.findSubTags("Application").asIterable() }.size)
    }

    fun set(project: Project, file: VirtualFile, field: WinRTXmlField, value: String) = edit(project, file, "Edit ${field.label}") { root ->
        val tag = resolve(root, field.path) ?: error("The XML node changed. Refresh the form and try again.")
        require((field.attribute?.let { tag.getAttributeValue(it).orEmpty() } ?: leafText(tag)) == field.value) {
            "The XML value changed. Refresh the form and try again."
        }
        if (field.attribute != null) tag.setAttribute(field.attribute, value.ifEmpty { null })
        else { require(tag.subTags.isEmpty()) { "Use the XML editor for structured content." }; setLeafText(tag, value) }
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
        section.addSubTag(capability, false)
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
