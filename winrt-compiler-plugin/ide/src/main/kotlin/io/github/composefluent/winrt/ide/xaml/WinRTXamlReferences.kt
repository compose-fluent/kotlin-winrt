package io.github.composefluent.winrt.ide.xaml

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.*
import com.intellij.util.ProcessingContext

class WinRTXamlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttributeValue::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val value = element as XmlAttributeValue
                if (!WinRTXamlSymbols.isXaml(value.containingFile)) return PsiReference.EMPTY_ARRAY
                val attribute = value.parent as? XmlAttribute ?: return PsiReference.EMPTY_ARRAY
                val tag = attribute.parent
                val range = ElementManipulators.getValueTextRange(value)
                val reference = when {
                    WinRTXamlSymbols.isDirective(attribute, "Class") -> ValueReference(value, range, qualifiedClass = true) {
                        WinRTXamlSymbols.kotlinClass(value.containingFile, value.value)
                    }
                    WinRTXamlSymbols.isDirective(attribute, "Name") -> null // Definition, not a reference to a user Kotlin field.
                    attribute.localName == "ElementName" -> ValueReference(value, range) {
                        WinRTXamlReferences.namedElements(tag).firstOrNull { it.first == value.value }?.second
                    }
                    WinRTXamlSymbols.members(tag).any { it.name == attribute.localName && it.isEvent } -> ValueReference(value, range) {
                        WinRTXamlEventAnalysis.forAttribute(attribute)?.target(value.value)
                    }
                    else -> null
                }
                val extra = mutableListOf<PsiReference>()
                WinRTXamlBindingSyntax.parse(value)?.let { syntax ->
                    fun tokens(expression: WinRTXamlBindingSyntax.Expr?): List<WinRTXamlBindingSyntax.Token> = expression?.let {
                        listOfNotNull(it.token.takeIf { _ -> it.kind != "literal" }) + tokens(it.receiver) + it.arguments.flatMap(::tokens)
                    }.orEmpty()
                    val sites = (tokens(syntax.expression) + tokens(syntax.bindBack) + listOfNotNull(syntax.elementName)).distinctBy { it.range }
                    sites.filter { !it.range.isEmpty }.forEach { token ->
                        extra += ValueReference(value, token.range, binding = true) {
                            WinRTXamlBindingAnalysis.forValue(value)?.sites?.firstOrNull { it.range == token.range }?.target
                        }
                    }
                }
                return (listOfNotNull(reference) + extra).toTypedArray()
            }
        })
    }

    private class ValueReference(value: XmlAttributeValue, range: TextRange, private val qualifiedClass: Boolean = false,
        private val binding: Boolean = false, private val target: () -> PsiElement?) :
        PsiReferenceBase<XmlAttributeValue>(value, range, true) {
        private var projectedCase = false
        override fun resolve(): PsiElement? = target()?.also { resolved ->
            val original = element.text.substring(rangeInElement.startOffset, rangeInElement.endOffset)
            projectedCase = binding && original.firstOrNull()?.isUpperCase() == true &&
                (resolved as? org.jetbrains.kotlin.psi.KtNamedDeclaration)?.name?.firstOrNull()?.isLowerCase() == true
        }
        override fun handleElementRename(newElementName: String): PsiElement {
            val old = element.text.substring(rangeInElement.startOffset, rangeInElement.endOffset)
            val replacement = when {
                qualifiedClass && !newElementName.contains('.') && old.contains('.') -> old.substringBeforeLast('.') + "." + newElementName
                binding && old.contains(':') -> old.substringBeforeLast(':') + ":" + newElementName
                projectedCase -> newElementName.replaceFirstChar(Char::uppercase)
                else -> newElementName
            }
            return ElementManipulators.handleContentChange(element, rangeInElement, replacement)
        }
    }
}

internal object WinRTXamlReferences {
    fun namedElements(tag: XmlTag) = WinRTXamlScopes.namedElements(tag)
}
