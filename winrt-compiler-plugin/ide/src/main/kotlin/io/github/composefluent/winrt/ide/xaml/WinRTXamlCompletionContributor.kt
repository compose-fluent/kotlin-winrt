package io.github.composefluent.winrt.ide.xaml

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import io.github.composefluent.winrt.metadata.WinRTFundamentalType
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName

class WinRTXamlCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val file = parameters.originalFile
                if (!WinRTXamlSymbols.isXaml(file)) return
                val value = PsiTreeUtil.getParentOfType(parameters.position, XmlAttributeValue::class.java, false) ?: return
                val attribute = value.parent as? XmlAttribute ?: return
                val tag = attribute.parent
                val catalog = WinRTXamlSymbols.catalog(file)
                val values = when {
                    attribute.isNamespaceDeclaration -> listOf(WinRTXamlCatalog.PRESENTATION, WinRTXamlCatalog.XAML) +
                        catalog?.model?.namespaces.orEmpty().map { "using:${it.name}" } +
                        WinRTXamlSymbols.classNames(file).map { "using:${it.substringBeforeLast('.', "")}" }.filter { it != "using:" }
                    WinRTXamlSymbols.isDirective(attribute, "Class") -> WinRTXamlSymbols.classNames(file)
                    value.value.trimStart().startsWith("{x:Bind ") -> {
                        val owner = WinRTXamlSymbols.ownerClass(tag)
                        owner?.declarations.orEmpty().filterIsInstance<org.jetbrains.kotlin.psi.KtNamedDeclaration>().mapNotNull { it.name } +
                            WinRTXamlReferences.namedElements(tag).map { it.first }
                    }
                    WinRTXamlSymbols.member(tag, attribute.localName)?.isEvent == true ->
                        WinRTXamlEventAnalysis.forAttribute(attribute)?.candidates.orEmpty()
                            .groupBy { it.name }.filterValues { it.size == 1 && it.single().problem == null }.keys.toList()
                    attribute.localName == "ElementName" -> WinRTXamlReferences.namedElements(tag).map { it.first }
                    else -> {
                        val member = WinRTXamlSymbols.member(tag, attribute.name)
                        when {
                            winRTFundamentalTypeForName(member?.typeName.orEmpty()) == WinRTFundamentalType.Boolean -> listOf("True", "False")
                            else -> catalog?.types?.get(member?.typeName)?.enumMembers.orEmpty().map { it.name }
                        }
                    }
                }
                values.distinct().forEach { result.addElement(LookupElementBuilder.create(it)) }
            }
        })
    }
}
