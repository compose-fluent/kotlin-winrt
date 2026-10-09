package io.github.composefluent.winrt.ide.xaml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.FakePsiElement
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.codeInsight.daemon.ImplicitUsageProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.refactoring.util.RelatedUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import com.intellij.util.QueryExecutor
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.lexer.KtTokens

private fun declaration(element: PsiElement): KtNamedDeclaration? =
    ((element as? KtNamedDeclaration) ?: (element.navigationElement as? KtNamedDeclaration))?.takeUnless {
        it is KtNamedFunction && it.isLocal || it is KtProperty && it.isLocal
    }

private fun xamlScope(element: PsiElement) = GlobalSearchScope.getScopeRestrictedByFileTypes(
    GlobalSearchScope.allScope(element.project), WinRTXamlFileType.INSTANCE, XmlFileType.INSTANCE)

private fun isPrivate(target: KtNamedDeclaration) = target.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
    generateSequence(target.parent) { it.parent }.filterIsInstance<KtClassOrObject>()
        .any { it.hasModifier(KtTokens.PRIVATE_KEYWORD) }

private val xamlSearchContext = (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_STRINGS.toInt() or
    UsageSearchContext.IN_PLAIN_TEXT.toInt()).toShort()

private fun spellings(target: KtNamedDeclaration): Set<String> {
    val name = target.name ?: return emptySet()
    val privateMember = isPrivate(target)
    return buildSet {
        if (privateMember) add(name)
        add(name.replaceFirstChar(Char::uppercase))
        if (target is KtNamedFunction) listOf("Get", "Set", "get", "set").forEach { prefix ->
            if (name.startsWith(prefix) && name.length > prefix.length && name[prefix.length].isUpperCase())
                add(name.removePrefix(prefix))
        }
    }.let { if (privateMember) it else it - name }
}

/** Projection casing and attachable GetX/SetX names differ from XAML spelling.
 * The index narrows candidates; resolving each occurrence prevents matching
 * another owner, dynamic Binding or an unrelated string/comment. Kotlin's
 * DeclarationScopeOptimizer limits private members to their Kotlin file even
 * after UseScopeEnlarger. XAMLC connections therefore honor the user-selected
 * scope while checking accessibility/ownership through our actual resolver. */
class WinRTXamlKotlinReferencesSearch : QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val target = declaration(parameters.elementToSearch) ?: return true
        val scope = parameters.scopeDeterminedByUser.intersectWith(xamlScope(target))
        spellings(target).forEach { spelling ->
            parameters.optimizer.searchWord(spelling, scope, xamlSearchContext,
                true, target, object : RequestResultProcessor(target) {
                    override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, processor: Processor<in PsiReference>): Boolean {
                        val owner = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
                            ?: PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
                            ?: PsiTreeUtil.getParentOfType(element, XmlTag::class.java, false) ?: return true
                        if (!WinRTXamlSymbols.isXaml(owner.containingFile)) return true
                        val offset = element.textRange.startOffset + offsetInElement - owner.textRange.startOffset
                        return PsiReferenceService.getService().getReferences(owner, PsiReferenceService.Hints.NO_HINTS)
                            .filter { reference -> reference.rangeInElement.containsOffset(offset) &&
                                reference.resolve()?.let(::declaration)?.let { target.manager.areElementsEquivalent(it, target) } == true }
                            .all { processor.process(it) }
                    }
                })
        }
        return true
    }
}

/** The native unused-symbol inspection also applies Kotlin's private file
 * restriction. Claim implicit use only after a resolved XAML reference is
 * found; unrelated private functions retain their normal unused diagnostic. */
class WinRTXamlImplicitUsageProvider : ImplicitUsageProvider {
    private fun used(element: PsiElement): Boolean {
        val target = declaration(element) ?: return false
        if (target !is KtClassOrObject && PsiTreeUtil.getParentOfType(target, KtClassOrObject::class.java) == null) return false
        return CachedValuesManager.getCachedValue(target) {
            val found = ReferencesSearch.search(target, xamlScope(target), true).findFirst() != null
            CachedValueProvider.Result.create(found, PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(target.project),
                target.project.service<WinRTXamlCatalogService>().modificationTracker,
                target.project.service<io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService>())
        }
    }
    override fun isImplicitUsage(element: PsiElement) = used(element)
    override fun isImplicitRead(element: PsiElement) = used(element)
    override fun isImplicitWrite(element: PsiElement) = false
}

/** Kotlin's rename processor supplies its private file scope to reference
 * searches. Add the resolved XAML connections as a related rename target so
 * they appear in the native preview/undo transaction. Kotlin still owns the
 * declaration, its normal usages, overrides and name-conflict checks. */
class WinRTXamlKotlinRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement): Boolean = element is Connections ||
        declaration(element)?.let { target -> isPrivate(target) &&
            (target is KtNamedFunction || target is KtProperty) &&
            PsiTreeUtil.getParentOfType(target, KtClassOrObject::class.java) != null } == true

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        if (element is Connections) return
        val target = declaration(element) ?: return
        // Native prepareRenaming runs on EDT. Use only the word index here;
        // actual resolution belongs to background findReferences below.
        if (hasCandidate(target))
            allRenames[Connections(target)] = newName
    }

    private fun hasCandidate(target: KtNamedDeclaration): Boolean = spellings(target).any { spelling ->
        var found = false
        PsiSearchHelper.getInstance(target.project).processCandidateFilesForText(xamlScope(target), xamlSearchContext,
            true, spelling) { file ->
                found = file.extension.equals("xaml", true)
                !found
            }
        found
    }

    override fun findReferences(element: PsiElement, searchScope: SearchScope, searchInComments: Boolean): Collection<PsiReference> {
        if (element !is Connections) return super.findReferences(element, searchScope, searchInComments)
        val target = element.target.element ?: return emptyList()
        return ReferencesSearch.search(target, searchScope.intersectWith(xamlScope(target)), true).findAll()
    }

    override fun createUsageInfo(element: PsiElement, reference: PsiReference, referenceElement: PsiElement): UsageInfo {
        if (element !is Connections) return super.createUsageInfo(element, reference, referenceElement)
        return object : RelatedUsageInfo(reference, element.target.element ?: element, element) {}
    }

    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? =
        if (element is Connections) null else super.getElementToSearchInStringsAndComments(element)

    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        if (element is Connections) {
            usages.sortedByDescending { it.element?.textRange?.startOffset ?: -1 }.forEach { RenameUtil.rename(it, newName) }
            return
        }
        val native = allForElement(element).firstOrNull { it !is WinRTXamlKotlinRenameProcessor } ?: DEFAULT
        native.renameElement(element, newName, usages, listener)
    }

    private class Connections(source: KtNamedDeclaration) : FakePsiElement() {
        val target = SmartPointerManager.createPointer(source)
        private val originalName = source.name
        override fun getParent(): PsiElement? = target.element?.parent
        override fun getContainingFile(): PsiFile? = target.element?.containingFile
        override fun getName(): String? = originalName
        override fun getPresentableText() = "XAML references to ${originalName.orEmpty()}"
        override fun getNavigationElement(): PsiElement = target.element ?: this
        override fun getUseScope(): SearchScope = xamlScope(this)
        override fun isValid() = target.element != null
    }
}
