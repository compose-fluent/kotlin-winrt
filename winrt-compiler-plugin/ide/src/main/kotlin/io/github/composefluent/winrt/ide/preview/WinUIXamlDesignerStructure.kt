package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog
import io.github.composefluent.winrt.ide.xaml.WinUIXamlSymbols

internal data class DesignerContainer(val pointer: SmartPsiElementPointer<XmlTag>, val text: String, val label: String)

internal data class DesignerStructure(val rows: List<String>? = null, val columns: List<String>? = null,
    val previous: Boolean = false, val next: Boolean = false, val containers: List<DesignerContainer> = emptyList())

/** IDE authoring commands. WinMD owns container content; PSI owns source and undo.
 * Grid definition editing preserves all unrelated attributes and resource expressions. */
internal object DesignerStructureEditing {
    fun inspect(tag: XmlTag, catalog: WinUIXamlCatalog, candidates: List<XmlTag> = emptyList()): DesignerStructure {
        val grid = tag.localName == "Grid" && tag.namespace == WinUIXamlCatalog.PRESENTATION
        val parent = tag.parentTag
        val owner = if (parent?.localName?.contains('.') == true) parent.parentTag else parent
        val type = owner?.let { catalog.resolve(it.namespace, it.localName) }
        val member = type?.let { if (parent != owner) catalog.propertyContent(it, parent!!.localName.substringAfter('.')) else catalog.contentMember(it) }
        val siblings = if (member?.collection == true) parent!!.subTags.filter { '.' !in it.localName } else emptyList()
        val index = siblings.indexOf(tag)
        fun sizes(axis: String) = tag.subTags.firstOrNull { it.localName == "Grid.${axis}Definitions" }?.subTags
            ?.map { it.getAttributeValue(if (axis == "Row") "Height" else "Width") ?: "*" }.orEmpty()
        return DesignerStructure(if (grid) sizes("Row") else null, if (grid) sizes("Column") else null,
            index > 0, index >= 0 && index < siblings.lastIndex,
            candidates.filter { it != owner && !PsiTreeUtil.isAncestor(tag, it, false) && availableContent(it, catalog, catalog.resolve(tag.namespace, tag.localName)) != null }.map { target ->
                val name = target.attributes.firstOrNull { it.localName == "Name" }?.value
                DesignerContainer(SmartPointerManager.getInstance(tag.project).createSmartPsiElementPointer(target), target.text,
                    if (name != null) "${target.localName} · $name" else "${target.localName} · line ${target.containingFile.text.take(target.textOffset).count { it == '\n' } + 1}")
            })
    }


    /** Resolve writable content through the existing metadata contract. */
    fun availableContent(tag: XmlTag, catalog: WinUIXamlCatalog, child: io.github.composefluent.winrt.metadata.WinRTTypeDefinition? = null): XmlTag? {
        val type = catalog.resolve(tag.namespace, tag.localName) ?: return null
        if (!catalog.isVisual(type) || child == null || !catalog.acceptsContent(type, child)) return null
        val member = catalog.contentMember(type) ?: return null
        if (tag.getAttributeValue(member.name) != null) return null
        val content = tag.subTags.firstOrNull { it.localName == "${tag.localName}.${member.name}" } ?: tag
        if (!member.collection && (content.subTags.any { '.' !in it.localName } || content.value.trimmedText.isNotEmpty())) return null
        return content
    }

    fun reparent(project: Project, selection: DesignerSelection, destination: DesignerContainer): XmlTag =
        edit(project, selection, "Move XAML element into ${destination.label}") { tag ->
            val target = destination.pointer.element ?: error("The destination no longer exists.")
            require(target.containingFile == tag.containingFile && target.text == destination.text) { "Destination changed. Select it again." }
            require(!PsiTreeUtil.isAncestor(tag, target, false)) { "An element cannot contain itself." }
            val catalog = WinUIXamlSymbols.catalog(tag.containingFile) ?: error("XAML metadata is loading.")
            val content = availableContent(target, catalog, catalog.resolve(tag.namespace, tag.localName)) ?: error("The destination content is occupied or unavailable.")
            require(content != tag.parentTag) { "The element is already in this container." }
            val copy = tag.copy() as XmlTag
            // Keep inherited namespace meanings when the destination shadows a prefix.
            val namespaces = linkedMapOf<String, String>()
            generateSequence(tag) { it.parentTag }.forEach { ancestor -> ancestor.attributes.filter { it.name == "xmlns" || it.name.startsWith("xmlns:") }
                .forEach { attribute -> attribute.value?.let { namespaces.putIfAbsent(attribute.name, it) } } }
            namespaces.forEach { (name, uri) ->
                val prefix = if (name == "xmlns") "" else name.substringAfter(':')
                if (target.getNamespaceByPrefix(prefix) != uri) copy.setAttribute(name, uri)
            }
            val oldParent = tag.parentTag?.let { if ('.' in it.localName) it.parentTag else it }
            oldParent?.let { catalog.resolve(it.namespace, it.localName) }?.let { owner ->
                val attached = catalog.attachedMembers(owner).map { "${oldParent.localName}.${it.name}" }.toSet()
                copy.attributes.filter { it.localName in attached }.forEach { it.delete() }
            }
            val moved = content.addSubTag(copy, false)
            tag.delete()
            moved
        }

    fun validLength(value: String): Boolean {
        if (value == "Auto") return true
        val number = value.removeSuffix("*").ifEmpty { if (value == "*") "1" else "" }.toDoubleOrNull()
        return number != null && number.isFinite() && number >= 0
    }

    fun definition(project: Project, selection: DesignerSelection, axis: String, index: Int, value: String?) {
        require(axis == "Row" || axis == "Column")
        require(value == null || validLength(value)) { "Use Auto, a non-negative size, or a star size such as 2*." }
        edit(project, selection, "Edit Grid $axis definitions") { grid ->
            require(grid.localName == "Grid" && grid.namespace == WinUIXamlCatalog.PRESENTATION) { "Select a Grid." }
            val property = "Grid.${axis}Definitions"
            val existing = grid.subTags.firstOrNull { it.localName == property }
            val definitions = existing?.subTags.orEmpty()
            require(index in 0..definitions.size) { "Definitions changed. Select the Grid again." }
            require(definitions.all { it.localName == "${axis}Definition" && it.namespace == grid.namespace }) { "Edit custom definitions in XAML." }
            val size = if (axis == "Row") "Height" else "Width"
            if (value == null) {
                require(index == definitions.lastIndex) { "Only the last definition can be removed." }
                val remaining = (definitions.size - 1).coerceAtLeast(1)
                val content = grid.subTags.firstOrNull { it.localName == "Grid.Children" } ?: grid
                content.subTags.filter { '.' !in it.localName }.forEach { child ->
                    fun attached(name: String) = child.attributes.firstOrNull { it.localName == name }?.value
                    val start = attached("Grid.$axis")?.toIntOrNull()
                        ?: if (attached("Grid.$axis") == null) 0 else error("A child uses an expression for Grid.$axis.")
                    val span = attached("Grid.${axis}Span")?.toIntOrNull()
                        ?: if (attached("Grid.${axis}Span") == null) 1 else error("A child uses an expression for Grid.${axis}Span.")
                    require(start >= 0 && span > 0 && start.toLong() + span <= remaining) { "Move children out of the last $axis or reduce their span before removing it." }
                }
                definitions[index].delete()
                if (definitions.size == 1) existing!!.delete()
            } else if (index < definitions.size) {
                require(definitions[index].localName == "${axis}Definition") { "Edit this definition in XAML." }
                require(definitions[index].getAttributeValue(size)?.startsWith("{") != true &&
                    definitions[index].subTags.none { it.localName.endsWith(".$size") }) { "This size uses an expression. Edit it in XAML." }
                definitions[index].setAttribute(size, value)
            } else {
                require(grid.getAttributeValue("${axis}Definitions") == null) { "Definitions use an expression. Edit them in XAML." }
                val holder = existing ?: grid.addSubTag(grid.createChildTag(property, grid.namespace, null, false), true)
                val definition = holder.createChildTag("${axis}Definition", grid.namespace, null, false)
                definition.setAttribute(size, value)
                holder.addSubTag(definition, false)
            }
        }
    }

    fun reorderNeighbor(tag: XmlTag, forward: Boolean): XmlTag {
        val siblings = tag.parentTag!!.subTags.filter { '.' !in it.localName }
        return siblings[siblings.indexOf(tag) + if (forward) 1 else -1]
    }

    fun reorder(project: Project, selection: DesignerSelection, forward: Boolean): XmlTag =
        edit(project, selection, if (forward) "Move XAML element later" else "Move XAML element earlier") { tag ->
            val catalog = WinUIXamlSymbols.catalog(tag.containingFile) ?: error("XAML metadata is loading.")
            val state = inspect(tag, catalog)
            require(if (forward) state.next else state.previous) { "No sibling in that direction." }
            val parent = tag.parentTag!!
            val neighbor = reorderNeighbor(tag, forward)
            val moved = (if (forward) parent.addAfter(tag.copy(), neighbor) else parent.addBefore(tag.copy(), neighbor)) as XmlTag
            tag.delete()
            moved
        }

    private fun <T> edit(project: Project, selection: DesignerSelection, title: String, change: (XmlTag) -> T): T =
        WriteCommandAction.writeCommandAction(project).withName(title).compute<T, RuntimeException> {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val tag = selection.pointer.element ?: error("Select the element again.")
            require(tag.isValid && tag.text == selection.text && tag.containingFile.isWritable) { "XAML changed or is read only. Select the element again." }
            val file = tag.containingFile
            val result = change(tag)
            documents.getDocument(file)?.let(documents::doPostponedOperationsAndUnblockDocument)
            result
        }
}
