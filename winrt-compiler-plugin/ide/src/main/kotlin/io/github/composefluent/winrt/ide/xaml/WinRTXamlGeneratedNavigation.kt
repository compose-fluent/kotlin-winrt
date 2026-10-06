package io.github.composefluent.winrt.ide.xaml

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtExperimentalApi

/** Generated FIR properties have no physical Kotlin declaration to navigate to. */
class WinRTXamlGeneratedNavigation : GotoDeclarationHandler {
    @OptIn(KaExperimentalApi::class, KtExperimentalApi::class)
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val expression = PsiTreeUtil.getParentOfType(sourceElement, KtNameReferenceExpression::class.java, false) ?: return null
        if (DumbService.isDumb(expression.project)) return null
        val owner = analyze(expression) {
            val symbol = expression.resolveSymbol() as? KaCallableSymbol ?: return@analyze null
            // A user property with the same name keeps normal Kotlin navigation.
            if (symbol.psi != null && symbol.psi !is org.jetbrains.kotlin.psi.KtClassOrObject) return@analyze null
            symbol.callableId?.classId?.asSingleFqName()?.asString()
        } ?: return null
        val project = expression.project
        val name = expression.getReferencedName()
        val pages = project.service<WinRTXamlSnapshotService>().state.value.values.flatMap { it.declarations.pages }
            .filter { it.className == owner && it.connections.any { connection -> connection.fieldName == name && !connection.isTemplateChild } }
        val roots = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }.flatMap { it.sourceRoots }.distinct()
        val targets = pages.flatMap { page -> roots.mapNotNull { root ->
            val file = LocalFileSystem.getInstance().findFileByPath("${root.replace('\\', '/')}/${page.resourcePath}") ?: return@mapNotNull null
            val psi = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@mapNotNull null
            PsiTreeUtil.findChildrenOfType(psi, XmlAttribute::class.java).firstOrNull {
                WinRTXamlSymbols.isDirective(it, "Name") && it.value == name
            }?.valueElement
        } }.distinct()
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
