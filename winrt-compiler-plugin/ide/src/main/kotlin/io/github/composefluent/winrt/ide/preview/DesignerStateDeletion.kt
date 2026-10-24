package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog

/** Source-only SDK state removal. Resolve usages before changing either the state
 * or its transitions; generated connections are never silently left dangling. */
internal class DesignerStateDeletion private constructor(
    val label: String,
    val transitions: Int,
    private val targets: List<SmartPsiElementPointer<XmlTag>>,
    private val revision: Long,
) {
    fun apply(project: Project) {
        WriteCommandAction.runWriteCommandAction(project, "Delete XAML visual state", null, Runnable {
            val documents = PsiDocumentManager.getInstance(project)
            documents.commitAllDocuments()
            check(PsiModificationTracker.getInstance(project).modificationCount == revision) {
                "The project changed. Review the deletion again."
            }
            val elements = targets.map { it.element ?: error("The state changed. Review the deletion again.") }
            check(elements.all { it.isValid && it.containingFile.isWritable }) { "The XAML source is no longer writable." }
            val file = elements.first().containingFile
            elements.sortedByDescending { it.textRange.startOffset }.forEach { it.delete() }
            documents.getDocument(file)?.let(documents::doPostponedOperationsAndUnblockDocument)
        })
    }
    companion object {
        /** Called in a smart background read action; Kotlin reference resolution
         * must not block the Compose EDT. */
        fun prepare(project: Project, file: VirtualFile, group: DesignerStateGroup, state: String?): DesignerStateDeletion {
            val owner = DesignerStateAuthoring.groupTag(project, file, group)
            fun sdk(tag: XmlTag, name: String) = tag.namespace == WinUIXamlCatalog.PRESENTATION && tag.localName == name
            val target = if (state == null) owner else owner.subTags.singleOrNull {
                sdk(it, "VisualState") && it.getAttributeValue("Name", WinUIXamlCatalog.XAML) == state
            } ?: error("The state changed. Select it again.")
            val transitions = if (state == null) emptyList() else owner.subTags.filter { sdk(it, "VisualStateGroup.Transitions") }
                .flatMap { it.subTags.toList() }.filter {
                    sdk(it, "VisualTransition") && (it.getAttributeValue("From") == state || it.getAttributeValue("To") == state)
                }
            val removed = listOf(target) + transitions
            val names = (listOf(target) + PsiTreeUtil.findChildrenOfType(target, XmlTag::class.java)).mapNotNull {
                it.getAttribute("Name", WinUIXamlCatalog.XAML)?.valueElement
            }
            val used = names.filter { definition -> ReferencesSearch.search(definition).any { reference ->
                removed.none { PsiTreeUtil.isAncestor(it, reference.element, false) }
            } }
            check(used.isEmpty()) { "Still referenced outside this state: ${used.joinToString { it.value }}. Update those usages before deleting." }
            return DesignerStateDeletion(if (state == null) "group '${group.label}'" else "state '$state'", transitions.size,
                removed.map { SmartPointerManager.createPointer(it) }, PsiModificationTracker.getInstance(project).modificationCount)
        }
    }
}
