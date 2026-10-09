package io.github.composefluent.winrt.ide.xaml

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.kotlin.psi.KtClassOrObject

class WinRTXamlRelatedMarker : RelatedItemLineMarkerProvider() {
    override fun getName() = "Kotlin class and XAML document"
    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        val owner = element.parent as? KtClassOrObject ?: return
        if (owner.nameIdentifier != element) return
        val name = owner.fqName?.asString() ?: return
        val project = element.project
        val pages = project.service<WinRTXamlSnapshotService>().state.value.values.flatMap { it.declarations.pages }.filter { it.className == name }
        if (pages.isEmpty()) return
        val roots = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }.flatMap { it.sourceRoots }.distinct()
        val targets = pages.flatMap { page -> roots.mapNotNull { root ->
            val file = LocalFileSystem.getInstance().findFileByPath("${root.replace('\\', '/')}/${page.resourcePath}") ?: return@mapNotNull null
            (PsiManager.getInstance(project).findFile(file) as? XmlFile)?.rootTag?.getAttribute("Class", WinRTXamlCatalog.XAML)?.valueElement
        } }
        if (targets.isNotEmpty()) result += NavigationGutterIconBuilder.create(AllIcons.FileTypes.Xml)
            .setTargets(targets).setTooltipText("Navigate to XAML document").createLineMarkerInfo(element)
    }
}
