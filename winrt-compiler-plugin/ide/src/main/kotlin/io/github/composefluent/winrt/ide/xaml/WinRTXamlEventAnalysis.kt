package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.components.service
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtNamedFunction

/** IDE form of XamlSemanticExport/XamlPageBodies' instance-method contract.
 * The projected add<Event> parameter closes generic delegate arguments before
 * looking up invoke; no event/control-specific signature table lives in the IDE.
 * Only PSI and immutable diagnostics escape the Analysis API session.
 */
@OptIn(KaExperimentalApi::class)
internal object WinRTXamlEventAnalysis {
    data class Handler(val name: String, val declaration: PsiElement?, val problem: String?)
    data class Result(val candidates: List<Handler>, val delegateAvailable: Boolean) {
        fun matching(name: String) = candidates.filter { it.name == name }.ifEmpty {
            candidates.filter { it.name == name.replaceFirstChar(Char::lowercase) }
        }
        fun target(name: String) = matching(name).singleOrNull()?.declaration
        fun problem(name: String): String? {
            val handlers = matching(name)
            return when {
                handlers.isEmpty() -> "XAML event handler '$name' was not found in the owning Kotlin class."
                handlers.size > 1 -> "XAML event handler '$name' is overloaded; an unambiguous instance method is required."
                else -> handlers.single().problem
            }
        }
    }

    fun forAttribute(attribute: XmlAttribute): Result? {
        if (!WinRTXamlSymbols.isXaml(attribute.containingFile) || DumbService.isDumb(attribute.project)) return null
        val member = WinRTXamlSymbols.member(attribute.parent, attribute.localName)?.takeIf { it.isEvent } ?: return null
        return CachedValuesManager.getCachedValue(attribute) {
            val result = inspect(attribute.parent, member)
            CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(attribute.project), attribute.project.service<WinRTXamlSnapshotService>())
        }
    }

    private fun inspect(tag: XmlTag, member: WinRTXamlMember): Result? {
        val owner = WinRTXamlSymbols.ownerClass(tag) ?: return null
        return analyze(owner) {
                val ownerType = owner.classSymbol?.defaultType ?: return@analyze null
                val handlers = ownerType.scope?.getCallableSignatures { true }
                    ?.filterIsInstance<KaFunctionSignature<*>>()
                    ?.filter { it.symbol is KaNamedFunctionSymbol && it.symbol.psi is KtNamedFunction }
                    ?.toList().orEmpty()
                val invoke = delegateInvoke(tag, member)
                Result(handlers.map { handler ->
                    val symbol = handler.symbol as KaNamedFunctionSymbol
                    Handler(symbol.name.asString(), symbol.psi, handlerProblem(handler, invoke))
                }, invoke != null)
        }
    }

    context(session: KaSession)
    private fun delegateInvoke(tag: XmlTag, member: WinRTXamlMember): KaFunctionSignature<*>? = with(session) {
        val control = WinRTXamlSymbols.tagClass(tag) ?: return null
        val type = control.classSymbol?.defaultType ?: return null
        val add = type.scope?.getCallableSignatures(Name.identifier("add${member.name}"))
            ?.filterIsInstance<KaFunctionSignature<*>>()?.singleOrNull() ?: return null
        val delegate = add.valueParameters.singleOrNull()?.returnType ?: return null
        return delegate.scope?.getCallableSignatures(Name.identifier("invoke"))
            ?.filterIsInstance<KaFunctionSignature<*>>()?.singleOrNull()
    }

    context(session: KaSession)
    private fun handlerProblem(handler: KaFunctionSignature<*>, invoke: KaFunctionSignature<*>?): String? = with(session) {
        val symbol = handler.symbol as KaNamedFunctionSymbol
        val name = symbol.name.asString()
        if (symbol.isSuspend || symbol.isStatic || symbol.isExtension || symbol.typeParameters.isNotEmpty() ||
            symbol.contextParameters.isNotEmpty() || symbol.valueParameters.any { it.isVararg })
            return "XAML event handler '$name' must be an ordinary instance method without type, context or vararg parameters."
        if (handler.returnType !is KaErrorType && !handler.returnType.semanticallyEquals(builtinTypes.unit))
            return "XAML event handler '$name' must return Unit."
        if (invoke == null) return null // Dependencies may still be preparing; do not invent a signature.
        if (handler.valueParameters.size != invoke.valueParameters.size)
            return "XAML event handler '$name' requires ${invoke.valueParameters.size} parameters."
        handler.valueParameters.zip(invoke.valueParameters).forEachIndexed { index, (destination, source) ->
            if (resolved(source.returnType) && resolved(destination.returnType) &&
                !source.returnType.isSubtypeOf(destination.returnType))
                return "XAML event handler '$name' parameter ${index + 1} cannot accept the event delegate parameter (including nullability)."
        }
        return null
    }

    private fun resolved(type: KaType) = type !is KaErrorType
}

class WinRTXamlEventAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val value = element as? XmlAttributeValue ?: return
        val attribute = value.parent as? XmlAttribute ?: return
        if (value.value.isBlank() || value.value.trimStart().startsWith('{')) return
        val result = WinRTXamlEventAnalysis.forAttribute(attribute) ?: return
        val problem = result.problem(value.value) ?: return
        holder.newAnnotation(HighlightSeverity.ERROR, problem).range(value.valueTextRange).create()
    }
}
