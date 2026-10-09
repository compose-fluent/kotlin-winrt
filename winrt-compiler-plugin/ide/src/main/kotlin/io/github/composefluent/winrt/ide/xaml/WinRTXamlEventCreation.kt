package io.github.composefluent.winrt.ide.xaml

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render

/** EventAnalysis closes the actual projected delegate signature before a PSI
 * write. Only immutable source text and the owning class pointer leave analysis. */
internal object WinRTXamlEventCreation {
    private val identifier = Regex("[\\p{L}_][\\p{L}\\p{N}_]*")
    fun isIdentifier(name: String) = identifier.matches(name)

    fun proposal(attribute: XmlAttribute, name: String, result: WinRTXamlEventAnalysis.Result): Creation? {
        if (!isIdentifier(name) || result.matching(name).isNotEmpty()) return null
        val parameters = result.parameters ?: return null
        val owner = WinRTXamlSymbols.ownerClass(attribute.parent)?.takeIf { it.containingFile.isWritable } ?: return null
        val arguments = parameters.joinToString(", ") { "${Name.identifier(it.name).render()}: ${it.type}" }
        return Creation(name, SmartPointerManager.createPointer(owner), "private fun ${Name.identifier(name).render()}($arguments) {\n\n}")
            .takeIf { it.canCreate() }
    }

    data class Creation(val name: String, val owner: SmartPsiElementPointer<KtClassOrObject>, val source: String) {
        fun canCreate() = owner.element?.let { target -> target.containingFile.isWritable &&
            target.declarations.filterIsInstance<KtNamedDeclaration>().none { it.name == name } } == true

        fun create(): KtNamedFunction? {
            if (!canCreate()) return null
            val target = owner.element ?: return null
            val function = target.addDeclaration(KtPsiFactory(target.project).createFunction(source))
            val pointer = SmartPointerManager.createPointer(function)
            ShortenReferencesFacility.getInstance().shorten(function.containingKtFile, function.textRange)
            return pointer.element?.let { CodeStyleManager.getInstance(target.project).reformat(it) as KtNamedFunction }
        }
    }

    fun navigate(function: KtNamedFunction) {
        val file = function.containingFile.virtualFile ?: return
        val offset = function.bodyBlockExpression?.lBrace?.textRange?.endOffset ?: function.textOffset
        OpenFileDescriptor(function.project, file, offset).navigate(true)
    }
}

internal class WinRTXamlCreateEventHandler(value: XmlAttributeValue, private val creation: WinRTXamlEventCreation.Creation) : IntentionAction {
    private val value = SmartPointerManager.createPointer(value)
    override fun getText() = "Create event handler '${creation.name}'"
    override fun getFamilyName() = "Create Kotlin XAML event handler"
    override fun startInWriteAction() = false
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        value.element?.value == creation.name && creation.canCreate()

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val target = creation.owner.element ?: return
        if (!isAvailable(project, editor, file) || !FileModificationService.getInstance().preparePsiElementForWrite(target)) return
        var created: KtNamedFunction? = null
        WriteCommandAction.writeCommandAction(project, target.containingFile).withName(text)
            .run<RuntimeException> { created = creation.create() }
        created?.let(WinRTXamlEventCreation::navigate)
    }
}
