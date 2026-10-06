package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.fir.WinRTIdeTypeNames
import io.github.composefluent.winrt.metadata.winRTCollectionAbiNameForKotlinType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject

/** XamlCompiledBindingBodies' member lookup in an IDE Analysis API session.
 * Type scopes preserve substitutions through inherited/generic receivers.
 * Dynamic Binding has no assumed data root. Names and type projection come
 * from the owning compiler/metadata contracts, not another control registry.
 */
@OptIn(KaExperimentalApi::class)
internal object WinRTXamlBindingAnalysis {
    data class Site(val range: TextRange, val name: String, val target: PsiElement?, val candidates: List<String>, val problem: String?)
    data class Result(val syntax: WinRTXamlBindingSyntax.Markup, val sites: List<Site>)

    fun forValue(value: XmlAttributeValue): Result? {
        if (!WinRTXamlSymbols.isXaml(value.containingFile) || DumbService.isDumb(value.project)) return null
        val syntax = WinRTXamlBindingSyntax.parse(value) ?: return null
        return CachedValuesManager.getCachedValue(value) {
            val result = inspect(value, syntax)
            val snapshots = value.project.service<WinRTXamlSnapshotService>()
            CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(value.project), snapshots)
        }
    }

    private fun inspect(value: XmlAttributeValue, syntax: WinRTXamlBindingSyntax.Markup): Result {
        val tag = (value.parent as XmlAttribute).parent
        val context = WinRTXamlScopes.context(tag)
        val names = WinRTXamlScopes.namedElements(tag)
        val sites = mutableListOf<Site>()
        syntax.elementName?.let { token ->
            val target = names.singleOrNull { it.first == token.text }?.second
            sites += Site(token.range, token.text, target, names.map { it.first }, null)
        }
        val owner = WinRTXamlSymbols.ownerClass(tag)
        val anchor = owner ?: context?.dataType?.let { WinRTXamlSymbols.kotlinClass(value.containingFile, it) }
            ?: return Result(syntax, sites)
        analyze(anchor) {
            val root = if (syntax.compiled) context?.dataType?.let { type(it) } else syntax.elementName?.let { token ->
                names.singleOrNull { it.first == token.text }?.second?.let { name ->
                    ((name.parent as XmlAttribute).parent).let { source -> WinRTXamlSymbols.tagClass(source)?.classSymbol?.defaultType }
                }
            }
            fun members(receiver: KaType?, name: String? = null): List<KaCallableSignature<*>> {
                val scope = receiver?.scope ?: return emptyList()
                if (name == null) return scope.getCallableSignatures { true }.toList()
                val projected = if (name == "Count" && (receiver as? KaClassType)?.classId?.asSingleFqName()?.asString()
                        ?.let(::winRTCollectionAbiNameForKotlinType) != null) "size" else name.replaceFirstChar(Char::lowercase)
                return scope.getCallableSignatures(Name.identifier(name)).toList().ifEmpty {
                    scope.getCallableSignatures(Name.identifier(projected)).toList()
                }
            }
            fun candidates(receiver: KaType?, includeNames: Boolean): List<String> =
                (members(receiver).mapNotNull { (it.symbol as? KaNamedSymbol)?.name?.asString() }
                    .filterNot { it.startsWith('<') } + if (includeNames) names.map { it.first } else emptyList()).distinct()
            fun psi(signature: KaCallableSignature<*>, token: WinRTXamlBindingSyntax.Token, isRoot: Boolean): PsiElement? {
                val source = signature.symbol.psi
                // Compiler-generated x:Name members have the owning class as PSI.
                return if (source is KtClassOrObject && isRoot) names.singleOrNull { it.first == token.text }?.second else source
            }
            fun staticType(qualified: String): KaType? {
                val klass = findClass(ClassId.topLevel(FqName(WinRTIdeTypeNames.propertyType(qualified)))) ?: return null
                return ((klass as? KaNamedClassSymbol)?.companionObject ?: klass).defaultType
            }
            fun evaluate(expression: WinRTXamlBindingSyntax.Expr?, methodReference: Boolean = false): KaType? {
                expression ?: return null
                if (expression.kind == "root") return root
                val token = expression.token
                if (expression.kind == "literal") return when (token?.text) {
                    "string" -> type("System.String")
                    "number" -> null // The compiler chooses numeric type from the target/parameter.
                    "x:True", "x:False" -> type("System.Boolean")
                    else -> null
                }
                if (expression.kind == "static" || expression.kind == "cast") {
                    if (expression.kind == "cast") evaluate(expression.receiver)
                    val qualified = token?.let { WinRTXamlScopes.qualifiedType(tag, it.text) }
                    val target = qualified?.let { WinRTXamlSymbols.kotlinClass(value.containingFile, it) }
                    if (token != null) sites += Site(token.range, token.text, target, emptyList(), null)
                    return qualified?.let { if (expression.kind == "static") staticType(it) else type(it) }
                }
                if (expression.kind == "attached") {
                    val cast = expression.receiver ?: return null
                    evaluate(cast.receiver)
                    val qualified = cast.token?.let { WinRTXamlScopes.qualifiedType(tag, it.text) }
                    cast.token?.let { name -> sites += Site(name.range, name.text,
                        qualified?.let { WinRTXamlSymbols.kotlinClass(value.containingFile, it) }, emptyList(), null) }
                    val ownerType = qualified?.let(::staticType)
                    val getter = members(ownerType, "Get${token?.text.orEmpty()}").filterIsInstance<KaFunctionSignature<*>>()
                        .singleOrNull { it.valueParameters.size == 1 }
                    token?.let { name -> sites += Site(name.range, name.text, getter?.symbol?.psi, emptyList(), null) }
                    return getter?.returnType
                }
                val receiver = evaluate(expression.receiver)
                val isRoot = expression.receiver?.kind == "root"
                val available = candidates(receiver, isRoot && syntax.compiled)
                val args = expression.arguments.map { evaluate(it) }
                val matches = when (expression.kind) {
                    "member" -> token?.text?.takeIf { it.isNotEmpty() }?.let { name -> members(receiver, name).filter {
                        if (methodReference) it is KaFunctionSignature<*> else it.symbol is KaVariableSymbol
                    } }.orEmpty()
                    "call", "index" -> {
                        val functions = members(receiver, if (expression.kind == "index") "get" else token?.text.orEmpty())
                            .filterIsInstance<KaFunctionSignature<*>>().filter { it.valueParameters.size == args.size }
                        if (functions.size > 1 && args.all { it != null }) functions.filter { function ->
                            function.valueParameters.zip(args).all { (parameter, arg) -> arg!!.withNullability(false)
                                .semanticallyEquals(parameter.returnType.withNullability(false)) }
                        } else functions
                    }
                    else -> emptyList()
                }
                val match = matches.singleOrNull()
                val named = if (match == null && isRoot && syntax.compiled && expression.kind == "member")
                    names.singleOrNull { it.first == token?.text }?.second else null
                val target = match?.let { psi(it, token ?: return@let null, isRoot) } ?: named
                if (token != null) {
                    val canCheck = syntax.compiled && receiver != null && receiver !is KaErrorType && syntax.complete &&
                        expression.kind != "attached" && token.text.isNotEmpty()
                    val problem = if (canCheck && match == null && named == null) {
                        if (matches.size > 1) "Ambiguous x:Bind member '${token.text}'."
                        else "Unresolved x:Bind member '${token.text}'."
                    } else null
                    sites += Site(token.range, token.text, target, available, problem)
                }
                return match?.returnType ?: named?.let { name ->
                    WinRTXamlSymbols.tagClass((name.parent as XmlAttribute).parent)?.classSymbol?.defaultType
                } ?: receiver.takeIf { expression.kind == "member" && token?.text == "Value" }
            }
            evaluate(syntax.expression, WinRTXamlSymbols.member(tag, (value.parent as XmlAttribute).localName)?.isEvent == true)
            evaluate(syntax.bindBack, true)
        }
        return Result(syntax, sites)
    }

    context(session: KaSession)
    private fun type(name: String): KaType? = with(session) {
        val id = ClassId.topLevel(FqName(WinRTIdeTypeNames.propertyType(name)))
        findClass(id)?.let { buildClassType(it) }
    }
}

class WinRTXamlBindingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val value = element as? XmlAttributeValue ?: return
        val result = WinRTXamlBindingAnalysis.forValue(value) ?: return
        for (site in result.sites) site.problem?.let { problem ->
            holder.newAnnotation(HighlightSeverity.ERROR, problem).range(site.range.shiftRight(value.textRange.startOffset)).create()
        }
    }
}
