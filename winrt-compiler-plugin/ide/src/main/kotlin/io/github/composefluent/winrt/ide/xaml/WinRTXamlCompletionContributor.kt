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
                val binding = WinRTXamlBindingAnalysis.forValue(value)
                if (binding != null) {
                    val offset = parameters.offset - value.textRange.startOffset
                    val site = binding.sites.firstOrNull { offset >= it.range.startOffset && offset <= it.range.endOffset }
                    if (site != null) {
                        val prefix = value.text.substring(site.range.startOffset, offset.coerceAtMost(site.range.endOffset))
                        val matching = result.withPrefixMatcher(prefix)
                        site.candidates.forEach { matching.addElement(LookupElementBuilder.create(it)) }
                    }
                    return
                }
                WinRTXamlEventAnalysis.forAttribute(attribute)?.let { event ->
                    event.candidates.groupBy { it.name }.filterValues { it.size == 1 && it.single().problem == null }
                        .keys.forEach { result.addElement(LookupElementBuilder.create(it)) }
                    val entered = value.value.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "").trim()
                    val name = entered.takeIf(WinRTXamlEventCreation::isIdentifier) ?: (
                        tag.getAttributeValue("Name", WinRTXamlCatalog.XAML)?.takeIf(WinRTXamlEventCreation::isIdentifier)
                            ?: tag.localName) + "_" + attribute.localName
                    WinRTXamlEventCreation.proposal(attribute, name, event)?.let { creation ->
                        result.addElement(LookupElementBuilder.create(name).withTailText(" — Create event handler", true)
                            .withTypeText("Kotlin").withInsertHandler { insertion, _ ->
                                insertion.commitDocument()
                                val current = PsiTreeUtil.getParentOfType(insertion.file.findElementAt(insertion.startOffset),
                                    XmlAttributeValue::class.java, false) ?: return@withInsertHandler
                                if (!creation.canCreate()) return@withInsertHandler
                                (current.parent as XmlAttribute).setValue(name)
                                creation.create()?.let { function -> insertion.setLaterRunnable { WinRTXamlEventCreation.navigate(function) } }
                            })
                    }
                    return
                }
                val catalog = WinRTXamlSymbols.catalog(file)
                val values = when {
                    attribute.isNamespaceDeclaration -> listOf(WinRTXamlCatalog.PRESENTATION, WinRTXamlCatalog.XAML) +
                        catalog?.model?.namespaces.orEmpty().map { "using:${it.name}" } +
                        WinRTXamlSymbols.classNames(file).map { "using:${it.substringBeforeLast('.', "")}" }.filter { it != "using:" }
                    WinRTXamlSymbols.isDirective(attribute, "Class") -> WinRTXamlSymbols.classNames(file)
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
