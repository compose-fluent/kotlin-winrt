package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalog
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/** Source locations in an SDK dictionary which exceeds the IDE's PSI limit.
 * These are read on the resource index's IO thread, never during completion. */
internal data class WinRTFrameworkResourceKey(val value: String, val source: String, val range: TextRange,
    val modified: Long) {
    fun element(file: PsiFile) = object : FakePsiElement() {
        override fun getParent() = file
        override fun getName() = value
        override fun getText() = value
        override fun getTextRange() = range
        override fun getTextOffset() = range.startOffset
        override fun isValid() = file.isValid && file.virtualFile.timeStamp == modified
        override fun navigationRequest() = NavigationRequests.getInstance()
            .sourceNavigationRequest(file.project, file.virtualFile, range.startOffset, range)
    }

    companion object {
        private enum class Scope { Dictionary, Group, Other }

        /** Match ResourceDictionary's direct keys and merged/theme dictionaries,
         * excluding resources private to a style's template or a nested control. */
        fun read(path: Path): List<WinRTFrameworkResourceKey> {
            val modified = Files.getLastModifiedTime(path).toMillis()
            // Editor offsets use LF and exclude the encoding marker.
            val text = StringUtil.convertLineSeparators(Files.readString(path)).removePrefix("\uFEFF")
            // IDE distributions can install other StAX providers whose location
            // points at the start of an event. Use the JDK reader's end offset.
            val factory = XMLInputFactory.newDefaultFactory().apply {
                setProperty(XMLInputFactory.SUPPORT_DTD, false)
                setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            }
            val reader = factory.createXMLStreamReader(StringReader(text))
            val scopes = ArrayDeque<Scope>()
            val keys = mutableListOf<WinRTFrameworkResourceKey>()
            try {
                while (reader.hasNext()) when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        val parent = scopes.lastOrNull()
                        val local = reader.localName
                        val scope = when {
                            local == "ResourceDictionary" && (scopes.isEmpty() || parent in setOf(Scope.Dictionary, Scope.Group)) -> Scope.Dictionary
                            (local.endsWith(".MergedDictionaries") || local.endsWith(".ThemeDictionaries")) && parent == Scope.Dictionary -> Scope.Group
                            else -> Scope.Other
                        }
                        if (parent == Scope.Dictionary) {
                            val key = (0 until reader.attributeCount).firstOrNull {
                                reader.getAttributeNamespace(it) == WinRTXamlCatalog.XAML && reader.getAttributeLocalName(it) == "Key"
                            }
                            if (key != null) {
                                val end = reader.location.characterOffset.coerceAtMost(text.length)
                                val start = text.lastIndexOf('<', end - 1)
                                if (start >= 0) {
                                    val name = reader.getAttributePrefix(key).let { if (it.isEmpty()) "Key" else "$it:Key" }
                                    val attribute = Regex("\\b${Regex.escape(name)}\\s*=\\s*(['\"])(.*?)\\1", RegexOption.DOT_MATCHES_ALL)
                                        .find(text.substring(start, end))?.groups?.get(2)
                                    if (attribute != null) keys += WinRTFrameworkResourceKey(reader.getAttributeValue(key),
                                        path.toAbsolutePath().normalize().toString(), TextRange(start + attribute.range.first,
                                            start + attribute.range.last + 1), modified)
                                }
                            }
                        }
                        scopes.addLast(scope)
                    }
                    XMLStreamConstants.END_ELEMENT -> scopes.removeLast()
                }
            } finally { reader.close() }
            return keys
        }
    }
}
