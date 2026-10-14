package io.github.composefluent.winrt.ide.xaml

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType
import com.intellij.util.ProcessingContext
import io.github.composefluent.winrt.metadata.WinRTFundamentalType
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName

class WinUIXamlCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                val file = parameters.originalFile
                if (!WinUIXamlSymbols.isXaml(file)) return
                val value = PsiTreeUtil.getParentOfType(parameters.position, XmlAttributeValue::class.java, false)
                if (value == null) {
                    completeTag(parameters, result)
                    return
                }
                val attribute = value.parent as? XmlAttribute ?: return
                val tag = attribute.parent
                val binding = WinUIXamlBindingAnalysis.forValue(value)
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
                WinUIXamlEventAnalysis.forAttribute(attribute)?.let { event ->
                    event.candidates.groupBy { it.name }.filterValues { it.size == 1 && it.single().problem == null }
                        .keys.forEach { result.addElement(LookupElementBuilder.create(it)) }
                    val entered = value.value.replace(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED, "").trim()
                    val name = entered.takeIf(WinUIXamlEventCreation::isIdentifier) ?: (
                        tag.getAttributeValue("Name", WinUIXamlCatalog.XAML)?.takeIf(WinUIXamlEventCreation::isIdentifier)
                            ?: tag.localName) + "_" + attribute.localName
                    WinUIXamlEventCreation.proposal(attribute, name, event)?.let { creation ->
                        result.addElement(LookupElementBuilder.create(name).withTailText(" — Create event handler", true)
                            .withTypeText("Kotlin").withInsertHandler { insertion, _ ->
                                insertion.commitDocument()
                                val current = PsiTreeUtil.getParentOfType(insertion.file.findElementAt(insertion.startOffset),
                                    XmlAttributeValue::class.java, false) ?: return@withInsertHandler
                                if (!creation.canCreate()) return@withInsertHandler
                                (current.parent as XmlAttribute).setValue(name)
                                creation.create()?.let { function -> insertion.setLaterRunnable { WinUIXamlEventCreation.navigate(function) } }
                            })
                    }
                    return
                }
                val catalog = WinUIXamlSymbols.catalog(file)
                val values = when {
                    attribute.isNamespaceDeclaration -> listOf(WinUIXamlCatalog.PRESENTATION, WinUIXamlCatalog.XAML) +
                        catalog?.model?.namespaces.orEmpty().map { "using:${it.name}" } +
                        WinUIXamlSymbols.classNames(file).map { "using:${it.substringBeforeLast('.', "")}" }.filter { it != "using:" }
                    WinUIXamlSymbols.isDirective(attribute, "Class") -> WinUIXamlSymbols.classNames(file)
                    attribute.localName == "ElementName" -> WinUIXamlReferences.namedElements(tag).map { it.first }
                    else -> {
                        val member = WinUIXamlSymbols.member(tag, attribute.name)
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

    private fun completeTag(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        if (position.node.elementType != XmlTokenType.XML_NAME ||
            position.prevSibling?.node?.elementType != XmlTokenType.XML_START_TAG_START) return
        val tag = PsiTreeUtil.getParentOfType(position, XmlTag::class.java, false) ?: return
        val parent = tag.parentTag
        val context = parent ?: tag
        val properties = parent?.let(WinUIXamlSymbols::propertyElements).orEmpty()
        val names = context.descriptor?.getElementsDescriptors(context).orEmpty().map { it.name } +
            if (parent == null && tag.namespace.isBlank()) WinUIXamlSymbols.catalog(parameters.originalFile)
                ?.candidates(WinUIXamlCatalog.PRESENTATION).orEmpty().map { it.name } else emptyList()
        names.distinct().filter { !it.substringAfter(':').contains('.') || it in properties }.forEach { name ->
            var item = LookupElementBuilder.create(name).withInsertHandler(XmlTagInsertHandler.INSTANCE)
            properties[name]?.let { member ->
                // Match camel-word suffixes both with and without the owner:
                // Backdrop / Window.Backdrop -> Window.SystemBackdrop.
                val words = member.name.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
                val suffixes = words.indices.map { words.drop(it).joinToString("") }
                val owner = name.substringBeforeLast('.')
                item = item.withLookupStrings(suffixes + suffixes.map { "$owner.$it" })
                    .withPresentableText(name)
                    .withTypeText(member.typeName.substringAfterLast('.').takeIf(String::isNotEmpty) ?: "Property")
            }
            result.addElement(item)
        }
        result.stopHere()
    }
}
