package io.github.composefluent.winrt.ide.xaml

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.*
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory
import javax.swing.Action
import javax.swing.JComponent

private fun nameDefinition(element: PsiElement?): XmlAttributeValue? {
    val value = element as? XmlAttributeValue ?: PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) ?: return null
    val attribute = value.parent as? XmlAttribute ?: return null
    return value.takeIf { WinRTXamlSymbols.isXaml(it.containingFile) && WinRTXamlSymbols.isDirective(attribute, "Name") }
}

/** A word index only narrows the search. Every XML/Kotlin occurrence must
 * resolve to this exact scope/owner; identical names in another template or
 * a local Kotlin variable are never renamed by textual matching.
 */
class WinRTXamlNameReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val definition = nameDefinition(parameters.elementToSearch) ?: return true
        val name = definition.value
        if (name.isEmpty()) return true
        parameters.optimizer.searchWord(name, parameters.effectiveSearchScope,
            (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_STRINGS.toInt() or UsageSearchContext.IN_PLAIN_TEXT.toInt()).toShort(),
            true, object : RequestResultProcessor(definition) {
                override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, processor: Processor<in PsiReference>): Boolean {
                    val referenceExpression = PsiTreeUtil.getParentOfType(element, KtNameReferenceExpression::class.java, false)
                    if (referenceExpression != null && referenceExpression.getReferencedName() == name &&
                        WinRTXamlGeneratedNavigation.targets(referenceExpression).any { it == definition })
                        return processor.process(GeneratedNameReference(referenceExpression, definition))
                    val value = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false) ?: return true
                    if (value == definition) return true
                    val offset = element.textRange.startOffset + offsetInElement - value.textRange.startOffset
                    return PsiReferenceService.getService().getReferences(value, PsiReferenceService.Hints.NO_HINTS)
                        .filter { it.rangeInElement.containsOffset(offset) && it.isReferenceTo(definition) }.all { processor.process(it) }
                }
            })
        return true
    }

    private class GeneratedNameReference(expression: KtNameReferenceExpression, private val definition: XmlAttributeValue) :
        PsiReferenceBase<KtNameReferenceExpression>(expression, expression.getReferencedNameElement().textRangeInParent, false) {
        override fun resolve(): PsiElement = definition
        override fun handleElementRename(newElementName: String): PsiElement =
            element.replace(KtPsiFactory(element.project).createSimpleName(newElementName))
    }
}

class WinRTXamlNameRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement) = nameDefinition(element) == element
    override fun isInplaceRenameSupported() = false
    override fun isToSearchInComments(element: PsiElement) = false
    override fun isToSearchForTextOccurrences(element: PsiElement) = false
    override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
        val definition = nameDefinition(element) ?: return
        val tag = (definition.parent as XmlAttribute).parent
        if (!Regex("[\\p{L}_][\\p{L}\\p{N}_]*").matches(newName)) {
            conflicts.putValue(element, "An x:Name must be a XAML identifier.")
            return
        }
        if (WinRTXamlScopes.namedElements(tag).any { it.first == newName && it.second != definition })
            conflicts.putValue(element, "An element named '$newName' already exists in this XAML name scope.")
        val context = WinRTXamlScopes.context(tag)
        if (context?.boundary == (tag.containingFile as? com.intellij.psi.xml.XmlFile)?.rootTag &&
            WinRTXamlSymbols.ownerClass(tag)?.declarations?.filterIsInstance<KtNamedDeclaration>()?.any { it.name == newName } == true)
            conflicts.putValue(element, "A Kotlin declaration named '$newName' already exists in the owning class.")
    }
    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        val definition = nameDefinition(element) ?: throw IncorrectOperationException("Not an x:Name definition")
        usages.mapNotNull { it.reference }.sortedByDescending { it.element.textRange.startOffset + it.rangeInElement.startOffset }
            .forEach { it.handleElementRename(newName) }
        val range = ElementManipulators.getValueTextRange(definition)
        val renamed = ElementManipulators.handleContentChange(definition, range, newName)
        listener?.elementRenamed(renamed)
    }
}

/** Native rename action/usage preview with a Compose/Jewel name form. */
class WinRTXamlNameRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        if (nativeRename.getData(dataContext) == true) return false
        val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        if (nameDefinition(element) != null) return true
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        val atCaret = file.findElementAt(editor.caretModel.offset)
        if (nameDefinition(atCaret) != null) return true
        val reference = PsiTreeUtil.getParentOfType(atCaret, KtNameReferenceExpression::class.java, false) ?: return false
        val owner = PsiTreeUtil.getParentOfType(reference, org.jetbrains.kotlin.psi.KtClassOrObject::class.java) ?: return false
        if (owner.declarations.filterIsInstance<KtNamedDeclaration>().any { it.name == reference.getReferencedName() }) return false
        return file.project.getService(io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService::class.java).state.value.values
            .any { snapshot -> snapshot.declarations.pages.any { page -> page.className == owner.fqName?.asString() &&
                page.connections.any { !it.isTemplateChild && it.fieldName == reference.getReferencedName() } } }
    }
    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        val element = file.findElementAt(editor.caretModel.offset) ?: return
        invoke(project, arrayOf(element), dataContext)
    }
    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        val element = elements.firstOrNull() ?: return
        nameDefinition(element)?.let { RenameNameDialog(project, it).show(); return }
        val reference = PsiTreeUtil.getParentOfType(element, KtNameReferenceExpression::class.java, false) ?: return
        val pointer = SmartPointerManager.createPointer(reference)
        val editor = CommonDataKeys.EDITOR.getData(dataContext)
        val offset = editor?.caretModel?.offset
        ReadAction.nonBlocking<XmlAttributeValue?> { pointer.element?.let { WinRTXamlGeneratedNavigation.targets(it).singleOrNull() as? XmlAttributeValue } }
            .inSmartMode(project).expireWith(project).finishOnUiThread(ModalityState.nonModal()) { target ->
                if (pointer.element == null || editor?.isDisposed == true || editor?.caretModel?.offset != offset) return@finishOnUiThread
                if (target != null && target.isValid) RenameNameDialog(project, target).show()
                else {
                    // The inexpensive PSI/snapshot availability check can also
                    // see a local or parameter shadowing a generated x:Name.
                    // FIR resolves it off the UI thread; let the native registry
                    // choose the normal handler when it is not our declaration.
                    val context = nativeContext(dataContext)
                    val handler = RenameHandlerRegistry.getInstance().getRenameHandler(context) ?: return@finishOnUiThread
                    val file = CommonDataKeys.PSI_FILE.getData(context)
                    if (editor != null && file != null) handler.invoke(project, editor, file, context)
                    else handler.invoke(project, arrayOf(requireNotNull(pointer.element)), context)
                }
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    companion object {
        private val nativeRename = DataKey.create<Boolean>("winrt.xaml.native.rename")
        internal fun nativeContext(context: DataContext): DataContext = SimpleDataContext.getSimpleContext(nativeRename, true, context)
    }
}

private class RenameNameDialog(private val project: Project, private val definition: XmlAttributeValue) : DialogWrapper(project) {
    private val name = TextFieldState(definition.value)
    private var preview by mutableStateOf(true)
    private var failure by mutableStateOf<String?>(null)
    init { title = "Rename XAML element"; init() }
    override fun createActions(): Array<Action> = emptyArray()
    override fun createCenterPanel(): JComponent = compose(focusOnClickInside = true) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column(Modifier.width(420.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rename '${definition.value}' in this name scope and its Kotlin references")
            TextField(name, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            CheckboxRow("Preview usages", preview, onCheckedChange = { preview = it })
            failure?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DefaultButton(onClick = {
                    val next = name.text.toString()
                    val conflicts = MultiMap<PsiElement, String>()
                    WinRTXamlNameRenameProcessor().findExistingNameConflicts(definition, next, conflicts)
                    if (!conflicts.isEmpty) failure = conflicts.values().joinToString("\n")
                    else {
                        close(OK_EXIT_CODE)
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed && definition.isValid)
                                RenameProcessor(project, definition, next, false, false).apply { setPreviewUsages(preview) }.run()
                        }
                    }
                }) { Text("Rename") }
                DefaultButton(onClick = { close(CANCEL_EXIT_CODE) }) { Text("Cancel") }
            }
        }
    }
}
