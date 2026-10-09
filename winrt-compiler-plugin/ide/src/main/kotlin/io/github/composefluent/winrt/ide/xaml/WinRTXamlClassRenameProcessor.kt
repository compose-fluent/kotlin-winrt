package io.github.composefluent.winrt.ide.xaml

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.kotlin.psi.KtClassOrObject

/** XamlSemanticExport requires a same-basename Kotlin/XAML pair. Let the Kotlin
 * processor own its normal class/file rename and add the paired XAML file to
 * that same native refactoring transaction.
 */
class WinRTXamlClassRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean =
        element is KtClassOrObject && paired(element) != null

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        val klass = element as? KtClassOrObject ?: return
        if (klass.containingFile.name != "${klass.name}.kt") return
        paired(klass)?.let { allRenames[it] = "$newName.xaml" }
    }

    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        // Normally the bundled Kotlin processor precedes this supplemental
        // processor. Preserve its semantics even if extension ordering changes.
        val native = allForElement(element).firstOrNull { it !is WinRTXamlClassRenameProcessor }
            ?: DEFAULT
        native.renameElement(element, newName, usages, listener)
    }

    private fun paired(klass: KtClassOrObject): XmlFile? {
        val project = klass.project
        val name = klass.fqName?.asString() ?: return null
        val sibling = klass.containingFile.virtualFile?.parent?.findChild("${klass.name}.xaml")
            ?.let { PsiManager.getInstance(project).findFile(it) as? XmlFile }
        if (sibling?.rootTag?.getAttributeValue("Class", WinRTXamlCatalog.XAML) == name) return sibling
        val pages = project.service<WinRTXamlSnapshotService>().state.value.values.flatMap { it.declarations.pages }
            .filter { it.className == name }.map { it.resourcePath }
        val roots = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }.flatMap { it.sourceRoots }.distinct()
        val paths = roots.flatMap { root -> (pages + "${klass.name}.xaml").map { "${root.replace('\\', '/')}/$it" } }
        return paths.distinct().mapNotNull { path -> LocalFileSystem.getInstance().findFileByPath(path)?.let {
            PsiManager.getInstance(project).findFile(it) as? XmlFile
        } }.singleOrNull { file -> file.name == "${klass.name}.xaml" &&
            file.rootTag?.getAttributeValue("Class", WinRTXamlCatalog.XAML) == name }
    }
}
