@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import io.github.composefluent.winrt.ide.xaml.*
import io.github.composefluent.winrt.metadata.WinRTTypeKind

internal data class DesignerObjectValue(val xml: String, val nodes: List<Pair<String, DesignerSelection>>, val types: List<Pair<String, String>>)

/** Property elements remain source objects. WinMD owns assignability and members;
 * PSI owns the atomic replacement, namespace context and undo, including setters. */
internal object DesignerObjectEditing {
    private fun containers(selection: DesignerSelection, property: String): Pair<XmlTag, List<XmlTag>> {
        val target = requireNotNull(selection.pointer.element) { "Select the element again." }
        val style = selection.style?.pointer?.element
        val state = selection.state?.pointer?.element
        if (style != null || state != null) {
            val owner = style ?: requireNotNull(state)
            val setters = (owner.subTags.toList() + owner.subTags.filter { it.localName.endsWith(".Setters") }.flatMap { it.subTags.toList() })
                .filter { it.localName == "Setter" && if (style != null) it.getAttributeValue("Property") == property else
                    it.getAttributeValue("Target") == selection.state!!.targetName + "." + if ('.' in property) "($property)" else property }
            check(setters.size <= 1) { "Duplicate setters must be resolved before editing this value." }
            val setter = setters.singleOrNull() ?: return owner to emptyList()
            return setter to setter.subTags.filter { it.localName == "Setter.Value" }
        }
        return target to target.subTags.filter { it.localName == (if ('.' in property) property else "${target.localName}.$property") }
    }

    fun inspect(selection: DesignerSelection, property: DesignerProperty): DesignerObjectValue {
        val (owner, collections) = containers(selection, property.name)
        check(collections.size <= 1) { "Duplicate property elements must be resolved first." }
        val catalog = WinUIXamlSymbols.catalog(owner.containingFile)
        val roots = collections.singleOrNull()?.subTags.orEmpty()
        val nodes = roots.flatMap { listOf(it) + PsiTreeUtil.findChildrenOfType(it, XmlTag::class.java) }.distinct()
            .filter { '.' !in it.localName }.map { tag ->
                val properties = catalog?.let { WinUIXamlDesignerEditing.properties(tag, it) }.orEmpty()
                tag.localName + " · line " + (tag.containingFile.text.take(tag.textOffset).count { it == '\n' } + 1) to
                    DesignerSelection(SmartPointerManager.getInstance(tag.project).createSmartPsiElementPointer(tag), tag.text,
                        properties, selection.bounds, tag.parentTag?.localName.orEmpty(), tag.attributes.associate { it.name to it.value.orEmpty() },
                        selection.resourceKeys, sharedTemplate = selection.sharedTemplate)
            }
        val types = catalog?.types?.values.orEmpty().filter {
            it.kind == WinRTTypeKind.RuntimeClass && it.genericParameterCount == 0 && !it.isProjectionInternal &&
                (it.activation.isActivatable || it.activation.composableFactoryInterfaceName != null) && catalog!!.acceptsType(property.type, it)
        }.sortedBy { it.qualifiedName }.map { it.qualifiedName to it.name }
        return DesignerObjectValue(roots.joinToString("\n") { it.text }, nodes, types)
    }

    fun fragment(context: XmlTag, text: String): List<XmlTag> {
        require(text.isNotBlank() && text.length <= 128 * 1024 && "<!DOCTYPE" !in text.uppercase()) { "Enter XAML object values up to 128 KiB." }
        val namespaces = linkedMapOf<String, String>()
        generateSequence(context) { it.parentTag }.forEach { tag -> tag.attributes.filter { it.name == "xmlns" || it.name.startsWith("xmlns:") }
            .forEach { namespaces.putIfAbsent(it.name, it.value.orEmpty()) } }
        fun escape(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        val declarations = namespaces.entries.joinToString(" ") { (name, value) -> "$name=\"${escape(value)}\"" }
        val file = PsiFileFactory.getInstance(context.project).createFileFromText("ObjectValue.xaml", XMLLanguage.INSTANCE,
            "<ObjectValue $declarations>$text</ObjectValue>") as XmlFile
        val wrapper = requireNotNull(file.rootTag)
        require(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) == null && wrapper.value.trimmedText.isEmpty()) { "Enter valid XAML object elements." }
        val all = PsiTreeUtil.findChildrenOfType(wrapper, XmlTag::class.java)
        require(all.size <= 1024 && wrapper.subTags.isNotEmpty() && all.none { tag -> tag.attributes.any { WinUIXamlSymbols.isDirective(it, "Class") } }) { "Use child objects without x:Class, up to 1024 elements." }
        return wrapper.subTags.toList()
    }

    fun replace(selection: DesignerSelection, property: DesignerProperty, xml: String?) {
        val project = requireNotNull(selection.pointer.element).project
        WriteCommandAction.writeCommandAction(project).withName(if (xml == null) "Reset ${property.name}" else "Edit ${property.name} object value").run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val target = requireNotNull(selection.pointer.element) { "Select the element again." }
            check(target.isValid && target.containingFile.isWritable && target.text == selection.text) { "XAML changed. Select the element again." }
            selection.style?.let { check(it.pointer.element?.containingFile?.text == it.sourceText) { "The style changed. Select it again." } }
            selection.state?.let { check(it.pointer.element?.containingFile?.text == it.sourceText) { "The visual state changed. Select it again." } }
            check(selection.properties.any { it.name == property.name && it.writable }) { "${property.name} is read only." }
            val (owner, values) = containers(selection, property.name)
            check(values.size <= 1) { "Duplicate property elements must be resolved first." }
            val parsed = xml?.let { fragment(owner, it) }
            val catalog = WinUIXamlSymbols.catalog(target.containingFile)
            if (parsed != null && catalog != null) {
                val definition = catalog.resolve(target.namespace, target.localName)
                val expected = if (selection.style == null && selection.state == null && definition != null) catalog.propertyValueType(definition, property.name) else property.type
                val collection = definition?.let { catalog.propertyContent(it, property.name)?.collection } == true
                require(collection || parsed.size == 1) { "This property accepts one object." }
                parsed.forEach { tag ->
                    val type = catalog.resolve(tag.namespace, tag.localName) ?: error("Unknown XAML object ${tag.name}.")
                    require(expected != null && catalog.acceptsType(expected, type)) { "${tag.name} cannot be assigned to ${property.name}." }
                }
            }
            if (xml == null) {
                if (owner.localName == "Setter") owner.delete() else { values.forEach { it.delete() }; target.setAttribute(property.name, null) }
            } else {
                var setter = owner
                if ((selection.style != null || selection.state != null) && owner.localName != "Setter") {
                    val collectionName = if (selection.style != null) "Style.Setters" else "VisualState.Setters"
                    val collections = owner.subTags.filter { it.localName == collectionName }
                    check(collections.size <= 1) { "Duplicate setter collections." }
                    val collection = collections.singleOrNull() ?: owner.addSubTag(owner.createChildTag(collectionName, WinUIXamlCatalog.PRESENTATION, null, false), false)
                    setter = collection.addSubTag(collection.createChildTag("Setter", WinUIXamlCatalog.PRESENTATION, null, false), false)
                    if (selection.style != null) setter.setAttribute("Property", property.name)
                    else setter.setAttribute("Target", selection.state!!.targetName + "." + if ('.' in property.name) "(${property.name})" else property.name)
                }
                val name = if (setter.localName == "Setter") "Setter.Value" else if ('.' in property.name) property.name else "${target.localName}.${property.name}"
                val replacement = setter.createChildTag(name, WinUIXamlCatalog.PRESENTATION, null, false)
                requireNotNull(parsed).forEach { replacement.addSubTag(it, false) }
                values.singleOrNull()?.replace(replacement) ?: setter.addSubTag(replacement, false)
                setter.setAttribute(if (setter.localName == "Setter") "Value" else property.name, null)
            }
            documents.getDocument(target.containingFile)?.let(documents::doPostponedOperationsAndUnblockDocument)
        }
    }
}
