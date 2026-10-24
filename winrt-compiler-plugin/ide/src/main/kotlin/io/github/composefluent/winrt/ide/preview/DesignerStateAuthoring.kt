package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog

/** Source-only VisualStateManager authoring. Runtime state execution remains SDK-owned. */
internal object DesignerStateAuthoring {
    private fun sdk(tag: XmlTag, name: String) = tag.namespace == WinUIXamlCatalog.PRESENTATION && tag.localName == name
    private fun name(tag: XmlTag) = tag.getAttributeValue("Name", WinUIXamlCatalog.XAML)
    private fun scope(tag: XmlTag): XmlTag = generateSequence(tag) { it.parentTag }
        .first { it.parentTag == null || sdk(it, "ControlTemplate") || sdk(it, "DataTemplate") }
    private fun validateName(owner: XmlTag, value: String) {
        require(value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Use a XAML name starting with a letter or underscore." }
        val root = scope(owner)
        require((listOf(root) + PsiTreeUtil.findChildrenOfType(root, XmlTag::class.java))
            .none { scope(it) == root && name(it) == value }) { "The name '$value' already exists in this namescope." }
    }
    private fun named(owner: XmlTag, type: String, value: String): XmlTag =
        owner.createChildTag(type, WinUIXamlCatalog.PRESENTATION, null, false).also { tag ->
            val prefix = owner.getPrefixByNamespace(WinUIXamlCatalog.XAML)?.takeIf(String::isNotEmpty) ?: "x".also {
                tag.setAttribute("xmlns:x", WinUIXamlCatalog.XAML)
            }
            tag.setAttribute("$prefix:Name", value)
        }
    private fun edit(project: Project, title: String, action: () -> XmlTag) {
        WriteCommandAction.runWriteCommandAction(project, title, null, Runnable {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            val changed = action()
            documents.getDocument(changed.containingFile)?.let(documents::doPostponedOperationsAndUnblockDocument)
        })
    }
    fun addGroup(project: Project, selection: DesignerSelection, groupName: String, firstState: String) =
        edit(project, "Create XAML visual state group") {
            val owner = selection.pointer.element ?: error("Select a source element first.")
            check(owner.isValid && owner.containingFile.isWritable && owner.text == selection.text) { "XAML changed. Select the element again." }
            validateName(owner, groupName); validateName(owner, firstState)
            require(groupName != firstState) { "The group and state need different names." }
            val collections = owner.subTags.filter { sdk(it, "VisualStateManager.VisualStateGroups") }
            check(collections.size <= 1) { "The element has duplicate state collections. Edit it in XAML." }
            val group = named(owner, "VisualStateGroup", groupName)
            group.addSubTag(named(owner, "VisualState", firstState), false)
            val existing = collections.singleOrNull()
            if (existing != null) existing.addSubTag(group, false)
            else {
                // Keep context-dependent PSI fragments valid: assemble the entire
                // subtree before the one mutation that replaces the owner's text.
                val collection = owner.createChildTag("VisualStateManager.VisualStateGroups",
                    WinUIXamlCatalog.PRESENTATION, null, false)
                collection.addSubTag(group, false)
                owner.addSubTag(collection, true)
            }
        }
    fun addState(project: Project, path: String, group: DesignerStateGroup, stateName: String) = addState(project,
        LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/')) ?: error("The source file is unavailable."), group, stateName)

    fun addState(project: Project, file: com.intellij.openapi.vfs.VirtualFile, group: DesignerStateGroup, stateName: String) =
        edit(project, "Create XAML visual state") {
            val xml = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: error("Open a XAML source file.")
            check(xml.isWritable) { "This XAML file is read only." }
            val owner = PsiTreeUtil.findChildrenOfType(xml, XmlTag::class.java).filter {
                sdk(it, "VisualStateGroup") && name(it) == group.label &&
                    it.subTags.filter { child -> sdk(child, "VisualState") }.map(::name) == group.states
            }.singleOrNull() ?: error("The state group changed or is ambiguous. Select it again.")
            validateName(owner, stateName)
            owner.addSubTag(named(owner, "VisualState", stateName), false)
        }
}
