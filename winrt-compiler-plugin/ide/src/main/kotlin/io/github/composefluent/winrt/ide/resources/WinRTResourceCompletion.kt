package io.github.composefluent.winrt.ide.resources

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile

class WinRTResourceCompletion : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile as? XmlFile ?: return
        if (!file.virtualFile.extension.equals("xaml", true)) return
        val lookup = file.project.service<WinRTResourceIndex>().forFile(file.virtualFile.path) ?: return
        val value = PsiTreeUtil.getParentOfType(parameters.position, XmlAttributeValue::class.java) ?: return
        val attribute = value.parent as? XmlAttribute ?: return
        val start = value.textRange.startOffset + 1
        val before = value.text.take((parameters.offset - start + 1).coerceIn(0, value.textLength)).drop(1)
        val prefix = if (attribute.localName == "ResourceKey" && attribute.parent.localName.removeSuffix("Extension") in
            setOf("StaticResource", "ThemeResource")) before else {
            val match = Regex("^\\{(?:StaticResource|ThemeResource)\\s+(?:ResourceKey\\s*=\\s*)?([^,}]*)$").find(before) ?: return
            match.groupValues[1].trim().trim('\'', '"')
        }
        val candidates = WinRTResourceReferences.resourceKeys(file, lookup, attribute.parent, null).distinctBy { it.value }
        val completion = result.withPrefixMatcher(prefix)
        candidates.forEach { key -> completion.addElement(LookupElementBuilder.create(key, key.value)
            .withTypeText(key.containingFile.name, true)) }
    }
}
