package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import io.github.composefluent.winrt.ide.xaml.*

/** Clipboard text is XAML source. All checks finish before mutating the destination document. */
internal object DesignerClipboard {
    private fun tags(selections: List<DesignerSelection>): List<XmlTag> {
        require(selections.isNotEmpty()) { "Select source elements first." }
        val tags = selections.map { selection ->
            val tag = selection.pointer.element ?: error("Select the elements again.")
            require(tag.isValid && tag.text == selection.text) { "XAML changed. Select the elements again." }
            tag
        }.distinct().sortedBy { it.textOffset }
        require(tags.map { it.containingFile }.distinct().size == 1) { "Select elements from one document." }
        return tags.filter { tag -> tags.none { it != tag && PsiTreeUtil.isAncestor(it, tag, true) } }
    }

    fun copy(selections: List<DesignerSelection>): String = tags(selections).joinToString("\n") { tag ->
        val copy = tag.copy() as XmlTag
        val namespaces = linkedMapOf<String, String>()
        generateSequence(tag) { it.parentTag }.forEach { ancestor -> ancestor.attributes
            .filter { it.name == "xmlns" || it.name.startsWith("xmlns:") }
            .forEach { attribute -> attribute.value?.let { namespaces.putIfAbsent(attribute.name, it) } } }
        namespaces.forEach { (name, value) -> copy.setAttribute(name, value) }
        copy.text
    }

    fun remove(project: Project, selections: List<DesignerSelection>) {
        WriteCommandAction.writeCommandAction(project).withName("Delete XAML elements").run<RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val tags = tags(selections)
            require(tags.all { it.parentTag != null && it.containingFile.isWritable }) { "The document root cannot be deleted." }
            val file = tags.first().containingFile
            tags.asReversed().forEach { it.delete() }
            documents.getDocument(file)?.let(documents::doPostponedOperationsAndUnblockDocument)
        }
    }

    fun fragment(project: Project, text: String, usedNames: Set<String>): List<XmlTag> {
        require(text.isNotBlank() && text.length <= 128 * 1024) { "Paste a XAML fragment up to 128 KiB." }
        val file = PsiFileFactory.getInstance(project).createFileFromText("Clipboard.xaml", com.intellij.lang.xml.XMLLanguage.INSTANCE, "<Grid xmlns=\"${WinUIXamlCatalog.PRESENTATION}\" xmlns:x=\"${WinUIXamlCatalog.XAML}\">$text</Grid>") as XmlFile
        val wrapper = requireNotNull(file.rootTag)
        require(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) == null && wrapper.value.trimmedText.isEmpty()) { "The clipboard does not contain valid XAML elements." }
        val roots = wrapper.subTags.toList()
        require(roots.isNotEmpty() && roots.size <= 100) { "Paste between 1 and 100 elements." }
        val all = PsiTreeUtil.findChildrenOfType(wrapper, XmlTag::class.java).filter { it != wrapper }
        require(all.none { tag -> tag.attributes.any { WinUIXamlSymbols.isDirective(it, "Class") } }) { "Paste child controls, not an x:Class document." }
        val definitions = all.flatMap { it.attributes.toList() }.filter { WinUIXamlSymbols.isDirective(it, "Name") || it.name == "Name" }
        require(definitions.mapNotNull { it.value }.distinct().size == definitions.size) { "This fragment has multiple name scopes. Paste one named scope at a time." }
        val used = usedNames.toMutableSet()
        val renamed = linkedMapOf<String, String>()
        definitions.forEach { attribute ->
            val old = requireNotNull(attribute.value)
            var fresh = old; var suffix = 1
            while (fresh in used) fresh = "$old${suffix++}"
            used += fresh
            if (old != fresh) renamed[old] = fresh
        }
        // Reuse the binding parser's semantic spans; do not replace text literals or property names.
        all.forEach { tag -> tag.attributes.forEach { attribute ->
            val value = attribute.valueElement ?: return@forEach
            val syntax = WinUIXamlBindingSyntax.parse(value)
            fun roots(expression: WinUIXamlBindingSyntax.Expr?): List<WinUIXamlBindingSyntax.Token> = expression?.let {
                listOfNotNull(it.token.takeIf { _ -> it.kind == "member" && it.receiver?.kind == "root" }) +
                    roots(it.receiver) + it.arguments.flatMap(::roots)
            }.orEmpty()
            val tokens = listOfNotNull(syntax?.elementName) + if (syntax?.compiled == true) roots(syntax.expression) + roots(syntax.bindBack) else emptyList()
            tokens.distinctBy { it.range }.sortedByDescending { it.range.startOffset }.forEach { token ->
                renamed[token.text]?.let { ElementManipulators.handleContentChange(value, token.range, it) }
            }
            if (attribute.localName in setOf("ElementName", "Storyboard.TargetName", "TargetName"))
                renamed[attribute.value]?.let { attribute.setValue(it) }
        } }
        definitions.forEach { attribute -> renamed[attribute.value]?.let { attribute.setValue(it) } }
        return roots.map { root ->
            // The wrapper's default declarations must survive extracting each root.
            val copy = root.copy() as XmlTag
            if (copy.getAttribute("xmlns") == null) copy.setAttribute("xmlns", root.namespace)
            if (copy.getAttribute("xmlns:x") == null) copy.setAttribute("xmlns:x", root.getNamespaceByPrefix("x"))
            copy
        }
    }

    fun paste(project: Project, selection: DesignerSelection, text: String): List<XmlTag> =
        WriteCommandAction.writeCommandAction(project).withName("Paste XAML elements").compute<List<XmlTag>, RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val selected = tags(listOf(selection)).single()
            require(selected.containingFile.isWritable) { "The XAML document is read only." }
            val catalog = WinUIXamlSymbols.catalog(selected.containingFile) ?: error("XAML metadata is loading.")
            val names = PsiTreeUtil.findChildrenOfType(selected.containingFile, XmlTag::class.java)
                .flatMap { it.attributes.toList() }.filter { it.localName == "Name" }.mapNotNull { it.value }.toSet()
            val copies = fragment(project, text, names)
            val types = copies.map { catalog.resolve(it.namespace, it.localName) ?: error("Unknown control ${it.name}.") }
            require(types.all(catalog::isVisual)) { "Only visual controls can be pasted onto the canvas." }
            val target = generateSequence(selected) { it.parentTag }.firstOrNull { candidate ->
                types.all { DesignerStructureEditing.availableContent(candidate, catalog, it) != null } &&
                    (copies.size == 1 || catalog.resolve(candidate.namespace, candidate.localName)?.let(catalog::contentMember)?.collection == true)
            } ?: error("Select a container with compatible available content.")
            val content = requireNotNull(DesignerStructureEditing.availableContent(target, catalog, types.first()))
            if (target.localName == "Canvas" && target.namespace == WinUIXamlCatalog.PRESENTATION) {
                copies.forEach { copy ->
                    val left = copy.attributes.firstOrNull { it.localName == "Canvas.Left" }
                    val top = copy.attributes.firstOrNull { it.localName == "Canvas.Top" }
                    val x = (left?.value ?: "0").toDoubleOrNull()
                    val y = (top?.value ?: "0").toDoubleOrNull()
                    if (x != null && y != null && x.isFinite() && y.isFinite()) {
                        copy.setAttribute(left?.name ?: "${target.name}.Left", (x + 16).toString())
                        copy.setAttribute(top?.name ?: "${target.name}.Top", (y + 16).toString())
                    }
                }
            }
            val inserted = copies.map { content.addSubTag(it, false) }
            documents.getDocument(selected.containingFile)?.let(documents::doPostponedOperationsAndUnblockDocument)
            inserted
        }
}
