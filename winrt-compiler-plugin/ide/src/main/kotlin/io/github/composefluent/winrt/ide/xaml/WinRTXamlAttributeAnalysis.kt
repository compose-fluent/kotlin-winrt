package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.fir.WinRTIdeTypeNames
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.name.Name

/** Mirrors DirectUIXamlType's instance/attachable lookup and its
 * <Name>Property dependency-property convention. Metadata supplies the XAML
 * vocabulary; the Kotlin member scope supplies inheritance and real PSI. */
@OptIn(KaExperimentalApi::class)
internal object WinRTXamlAttributeAnalysis {
    data class Member(val primary: PsiElement?, val dependencyProperty: PsiElement?, val owner: PsiElement?, val attached: Boolean) {
        val targets get() = listOfNotNull(primary, dependencyProperty).distinct()
    }

    fun forAttribute(attribute: XmlAttribute): Member? {
        if (!WinRTXamlSymbols.isXaml(attribute.containingFile) || attribute.isNamespaceDeclaration ||
            attribute.namespace == WinRTXamlCatalog.XAML || DumbService.isDumb(attribute.project)) return null
        return CachedValuesManager.getCachedValue(attribute) {
            CachedValueProvider.Result.create(inspect(attribute.parent, attribute.name), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(attribute.project), attribute.project.service<WinRTXamlSnapshotService>(),
                attribute.project.service<WinRTXamlCatalogService>().modificationTracker)
        }
    }

    fun forName(tag: XmlTag, name: String): Member? = tag.getAttribute(name)?.let(::forAttribute) ?: inspect(tag, name)

    private fun inspect(tag: XmlTag, name: String): Member? {
        if (DumbService.isDumb(tag.project)) return null
        val attached = name.contains('.')
        val metadata = WinRTXamlSymbols.member(tag, name)
        val owner = if (attached) WinRTXamlSymbols.tagClass(tag, name.substringBefore('.'))
            else metadata?.owner?.let { WinRTXamlSymbols.kotlinClass(tag.containingFile, it) } ?: WinRTXamlSymbols.tagClass(tag)
        owner ?: return null
        val property = name.substringAfter('.')
        if (property.contains(':')) return null
        return analyze(owner) {
            val symbol = owner.classSymbol as? KaNamedClassSymbol ?: return@analyze null
            val names = WinRTXamlSymbols.memberNames(property)
            fun instance(): PsiElement? {
                val scope = symbol.defaultType.scope ?: return null
                for (candidate in names) {
                    val matches = scope.getCallableSignatures(Name.identifier(candidate)).filter { it.symbol is KaVariableSymbol }.toList()
                    matches.singleOrNull()?.symbol?.psi?.let { return it }
                }
                if (metadata?.isEvent == true) return scope.getCallableSignatures(Name.identifier("add$property"))
                    .singleOrNull()?.symbol?.psi
                return null
            }
            var primary = if (attached) null else instance()
            var dependency: PsiElement? = null
            val seen = hashSetOf<KaNamedClassSymbol>()
            fun statics(current: KaNamedClassSymbol) {
                if (!seen.add(current) || seen.size > 64) return
                val scope = current.companionObject?.defaultType?.scope
                if (scope != null) {
                    if (attached && primary == null) {
                        // GetX/SetX remain ordinary projected methods; accept the
                        // same Kotlin casing adaptation as property lookup.
                        primary = listOf("Get$property" to 1, "Set$property" to 2).firstNotNullOfOrNull { (method, arity) ->
                            WinRTXamlSymbols.memberNames(method).firstNotNullOfOrNull { candidate ->
                                scope.getCallableSignatures(Name.identifier(candidate)).filterIsInstance<org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature<*>>()
                                    .singleOrNull { it.valueParameters.size == arity }?.symbol?.psi
                            }
                        }
                    }
                    if (dependency == null) {
                        dependency = WinRTXamlSymbols.memberNames("${property}Property").firstNotNullOfOrNull { candidate ->
                            scope.getCallableSignatures(Name.identifier(candidate)).filter { it.symbol is KaVariableSymbol }.singleOrNull()
                                ?.takeIf { variable -> (variable.returnType as? KaClassType)?.classId?.asSingleFqName()?.asString() in
                                    setOf("Microsoft.UI.Xaml.DependencyProperty", "Windows.UI.Xaml.DependencyProperty",
                                        WinRTIdeTypeNames.projection("Microsoft.UI.Xaml.DependencyProperty"),
                                        WinRTIdeTypeNames.projection("Windows.UI.Xaml.DependencyProperty")) }?.symbol?.psi
                        }
                    }
                }
                current.superTypes.mapNotNull { it.expandedSymbol as? KaNamedClassSymbol }.forEach(::statics)
            }
            statics(symbol)
            Member(primary, dependency, owner, attached).takeIf { it.targets.isNotEmpty() }
        }
    }
}

/** XML descriptors supply the ordinary Ctrl+B target. Offer the actual DP
 * registration as a second destination instead of jumping to its owner class. */
class WinRTXamlAttributeNavigation : com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int,
        editor: com.intellij.openapi.editor.Editor): Array<PsiElement>? {
        val attribute = com.intellij.psi.util.PsiTreeUtil.getParentOfType(sourceElement, XmlAttribute::class.java, false) ?: return null
        val name = attribute.nameElement ?: return null
        if (!name.textRange.containsOffset(offset)) return null
        val member = WinRTXamlAttributeAnalysis.forAttribute(attribute) ?: return null
        if (member.attached && offset < name.textRange.startOffset + name.text.lastIndexOf('.'))
            return member.owner?.let { arrayOf(it) }
        return member.targets.toTypedArray().takeIf { it.isNotEmpty() }
    }
}

class WinRTXamlAttributeAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val attribute = element as? XmlAttribute ?: return
        val member = WinRTXamlAttributeAnalysis.forAttribute(attribute) ?: return
        val name = attribute.nameElement ?: return
        val color = if (member.dependencyProperty != null) WinRTXamlMarkupColors.DEPENDENCY_PROPERTY else WinRTXamlMarkupColors.ATTRIBUTE
        val range = if (member.attached) com.intellij.openapi.util.TextRange(
            name.textRange.startOffset + name.text.lastIndexOf('.') + 1, name.textRange.endOffset) else name.textRange
        holder.newSilentAnnotation(HighlightSeverity.TEXT_ATTRIBUTES).range(range).textAttributes(color).create()
    }
}
