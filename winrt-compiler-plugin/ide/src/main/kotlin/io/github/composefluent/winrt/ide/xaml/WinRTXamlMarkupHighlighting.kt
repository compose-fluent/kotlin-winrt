package io.github.composefluent.winrt.ide.xaml

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

internal object WinRTXamlMarkupColors {
    private fun key(name: String, fallback: TextAttributesKey) = TextAttributesKey.createTextAttributesKey("WINRT_XAML_$name", fallback)
    val EXTENSION = key("MARKUP_EXTENSION", Colors.KEYWORD)
    val OPTION = key("MARKUP_OPTION", Colors.PARAMETER)
    val PATH = key("BINDING_PATH", HighlighterColors.TEXT)
    val MEMBER = key("RESOLVED_BINDING_MEMBER", Colors.INSTANCE_FIELD)
    val METHOD = key("RESOLVED_BINDING_METHOD", Colors.FUNCTION_CALL)
    val TYPE = key("BINDING_TYPE", Colors.CLASS_REFERENCE)
    val RESOURCE = key("RESOLVED_RESOURCE_KEY", Colors.CONSTANT)
    val STRING = key("MARKUP_STRING", Colors.STRING)
    val NUMBER = key("MARKUP_NUMBER", Colors.NUMBER)
    val PUNCTUATION = key("MARKUP_PUNCTUATION", Colors.BRACES)
    val ATTRIBUTE = key("PROPERTY_ATTRIBUTE", Colors.INSTANCE_FIELD)
    val DEPENDENCY_PROPERTY = key("DEPENDENCY_PROPERTY_ATTRIBUTE", Colors.STATIC_FIELD)
}

/** Presentation only: BindingPath.g4 and the existing binding/resource resolvers
 * still own semantics. Raw XML spans keep entity-escaped arguments at their
 * editor offsets, and the bounded scan also works while indexes are rebuilding. */
internal object WinRTXamlMarkupHighlighting {
    data class Part(val range: TextRange, val color: TextAttributesKey)
    data class Markup(val range: TextRange, val parts: List<Part>, val resourceKey: TextRange?)
    private val header = Regex("^\\s*\\{(?:(\\p{L}[\\p{L}\\p{N}_]*):)?(Bind|Binding|StaticResource|ThemeResource|TemplateBinding|Null)(?=\\s|})")

    fun parse(value: XmlAttributeValue): Markup? {
        if (!WinRTXamlSymbols.isXaml(value.containingFile)) return null
        val tag = (value.parent as? XmlAttribute)?.parent ?: return null
        val content = ElementManipulators.getValueTextRange(value)
        if (content.length > 16_384) return null
        val raw = value.text
        val text = raw.substring(content.startOffset, content.endOffset)
        val match = header.find(text) ?: return null
        val prefix = match.groupValues[1]
        val extension = match.groupValues[2]
        if (extension in setOf("Bind", "Null")) {
            if (tag.getNamespaceByPrefix(prefix) != WinRTXamlCatalog.XAML) return null
        } else if (prefix.isNotEmpty()) return null
        val start = content.startOffset + text.indexOf('{')
        val end = content.endOffset - text.length + text.trimEnd().length
        val parts = mutableListOf(Part(TextRange(start, start + 1), WinRTXamlMarkupColors.PUNCTUATION))
        val nameStart = start + 1
        var offset = content.startOffset + match.range.last + 1
        parts += Part(TextRange(nameStart, offset), WinRTXamlMarkupColors.EXTENSION)
        val resource = if (extension == "StaticResource" || extension == "ThemeResource") resourceKey(raw, offset, end) else null
        while (offset < end) {
            val char = raw[offset]
            if (char.isWhitespace()) { offset++; continue }
            val tokenStart = offset
            val quote = when {
                char == '\'' || char == '"' -> char.toString()
                raw.startsWith("&quot;", offset) -> "&quot;"
                raw.startsWith("&apos;", offset) -> "&apos;"
                else -> null
            }
            val color = when {
                resource != null && offset == resource.startOffset -> {
                    offset = resource.endOffset
                    WinRTXamlMarkupColors.PATH // A resource is colored as resolved only by its resolver.
                }
                quote != null -> {
                    offset += quote.length
                    while (offset < end) {
                        if (raw.startsWith("^$quote", offset)) offset += 1 + quote.length
                        else if (raw.startsWith(quote, offset)) { offset += quote.length; break }
                        else offset++
                    }
                    WinRTXamlMarkupColors.STRING
                }
                char.isDigit() || (char == '-' && raw.getOrNull(offset + 1)?.isDigit() == true) -> {
                    offset++
                    while (offset < end && (raw[offset].isDigit() || raw[offset] == '.')) offset++
                    WinRTXamlMarkupColors.NUMBER
                }
                Character.isJavaIdentifierStart(char) -> {
                    offset++
                    while (offset < end && Character.isJavaIdentifierPart(raw[offset])) offset++
                    var following = offset
                    while (following < end && raw[following].isWhitespace()) following++
                    if (raw.getOrNull(following) == '=') WinRTXamlMarkupColors.OPTION else WinRTXamlMarkupColors.PATH
                }
                else -> { offset++; WinRTXamlMarkupColors.PUNCTUATION }
            }
            parts += Part(TextRange(tokenStart, offset.coerceAtMost(end)), color)
        }
        return Markup(TextRange(start, end), parts, resource)
    }

    private fun resourceKey(raw: String, start: Int, end: Int): TextRange? {
        var first = start
        while (first < end && raw[first].isWhitespace()) first++
        val named = Regex("ResourceKey\\s*=\\s*").find(raw, first)?.takeIf { it.range.first == first }
        if (named != null) first = named.range.last + 1
        var last = end - if (raw.getOrNull(end - 1) == '}') 1 else 0
        while (last > first && raw[last - 1].isWhitespace()) last--
        if (first >= last || raw.substring(first, last).contains(',')) return null
        // Quotes delimit the key rather than participating in its reference.
        for (quote in listOf("&quot;", "&apos;", "\"", "'")) {
            if (raw.startsWith(quote, first) && raw.substring(0, last).endsWith(quote)) {
                first += quote.length; last -= quote.length; break
            }
        }
        return if (first < last) TextRange(first, last) else null
    }
}

class WinRTXamlMarkupAnnotator : Annotator, DumbAware {
    override fun annotate(element: com.intellij.psi.PsiElement, holder: AnnotationHolder) {
        val value = element as? XmlAttributeValue ?: return
        val markup = WinRTXamlMarkupHighlighting.parse(value) ?: return
        val base = value.textRange.startOffset
        // Remove the enclosing XML string style, including when the theme's
        // identifier/punctuation style has no foreground of its own.
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(markup.range.shiftRight(base))
            .enforcedTextAttributes(TextAttributes.ERASE_MARKER).create()
        for (part in markup.parts) holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(part.range.shiftRight(base)).textAttributes(part.color).create()
    }
}
