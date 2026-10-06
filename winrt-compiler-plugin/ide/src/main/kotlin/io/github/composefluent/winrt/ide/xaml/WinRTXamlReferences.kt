package io.github.composefluent.winrt.ide.xaml

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import com.intellij.util.ProcessingContext
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService

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
                Regex("\\bElementName\\s*=\\s*([A-Za-z_][A-Za-z0-9_]*)").findAll(value.value).forEach { match ->
                    val token = match.groups[1]!!
                    extra += ValueReference(value, TextRange(range.startOffset + token.range.first, range.startOffset + token.range.last + 1)) {
                        WinRTXamlReferences.namedElements(tag).firstOrNull { it.first == token.value }?.second
                    }
                }
                return (listOfNotNull(reference) + extra).toTypedArray()
            }
        })
    }

    private class ValueReference(value: XmlAttributeValue, range: TextRange, private val qualifiedClass: Boolean = false, private val target: () -> PsiElement?) :
        PsiReferenceBase<XmlAttributeValue>(value, range, true) {
        override fun resolve(): PsiElement? = target()
        override fun handleElementRename(newElementName: String): PsiElement {
            val replacement = if (qualifiedClass && !newElementName.contains('.') && element.value.contains('.'))
                element.value.substringBeforeLast('.') + "." + newElementName else newElementName
            return ElementManipulators.handleContentChange(element, rangeInElement, replacement)
        }
    }
}

internal object WinRTXamlReferences {
    fun namedElements(tag: XmlTag): List<Pair<String, XmlAttributeValue>> {
        val file = tag.containingFile as? XmlFile ?: return emptyList()
        val root = file.rootTag ?: return emptyList()
        val className = root.getAttributeValue("Class", WinRTXamlCatalog.XAML) ?: return emptyList()
        val page = file.project.service<WinRTXamlSnapshotService>().state.value.values
            .firstNotNullOfOrNull { it.declarations.pages.firstOrNull { page -> page.className == className } } ?: return emptyList()
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
        val ancestors = generateSequence(tag) { it.parentTag }
        val scopeId = ancestors.firstNotNullOfOrNull { ancestor ->
            val line = document.getLineNumber(ancestor.textOffset) + 1
            page.connections.firstOrNull { it.location.line == line }?.scopeId
        } ?: 0
        val names = page.connections.filter { it.scopeId == scopeId }.mapNotNull { it.elementName ?: it.fieldName }.toSet()
        return PsiTreeUtil.findChildrenOfType(root, XmlAttribute::class.java).filter {
            WinRTXamlSymbols.isDirective(it, "Name") && it.value in names
        }.mapNotNull { attribute -> attribute.valueElement?.let { attribute.value!! to it } }
    }
}
