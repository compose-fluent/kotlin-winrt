package io.github.composefluent.winrt.gallery.processor

import io.github.composefluent.winrt.gallery.code.KotlinCodeDocument
import io.github.composefluent.winrt.gallery.code.KotlinCodeKind
import io.github.composefluent.winrt.gallery.code.KotlinCodeSpan

/** Display-only XML tokenization. XamlCompiler owns markup validation and execution. */
internal class XamlSourceParser {
    fun parse(fileName: String, source: String): KotlinCodeDocument {
        val spans = mutableListOf<KotlinCodeSpan>()
        var offset = 0
        var inTag = false
        var tagName = false
        fun emit(end: Int, kind: KotlinCodeKind) {
            if (end <= offset) return
            if (spans.lastOrNull()?.kind == kind) spans.removeAt(spans.lastIndex)
            spans += KotlinCodeSpan(end, kind)
            offset = end
        }
        fun through(delimiter: String, from: Int): Int =
            source.indexOf(delimiter, from).let { if (it < 0) source.length else it + delimiter.length }
        fun entityEnd(): Int = source.indexOf(';', offset + 1).let {
            if (it >= 0 && source.substring(offset + 1, it).none { c -> c.isWhitespace() || c in "<>&\"'" }) it + 1
            else offset + 1
        }
        while (offset < source.length) {
            when {
                source.startsWith("<!--", offset) -> emit(through("-->", offset + 4), KotlinCodeKind.Comment)
                source.startsWith("<![CDATA[", offset) -> emit(through("]]>", offset + 9), KotlinCodeKind.String)
                source.startsWith("<?", offset) -> emit(through("?>", offset + 2), KotlinCodeKind.Keyword)
                source[offset] == '<' -> {
                    inTag = true
                    tagName = true
                    emit(offset + 1, KotlinCodeKind.Plain)
                }
                source[offset] == '>' -> {
                    inTag = false
                    emit(offset + 1, KotlinCodeKind.Plain)
                }
                inTag && source[offset] in "\"'" -> {
                    val quote = source[offset]
                    emit(offset + 1, KotlinCodeKind.String)
                    while (offset < source.length && source[offset] != quote) {
                        if (source[offset] == '&') emit(entityEnd(), KotlinCodeKind.Escape)
                        else {
                            var end = offset + 1
                            while (end < source.length && source[end] != quote && source[end] != '&') end++
                            emit(end, KotlinCodeKind.String)
                        }
                    }
                    if (offset < source.length) emit(offset + 1, KotlinCodeKind.String)
                }
                source[offset] == '&' -> emit(entityEnd(), KotlinCodeKind.Escape)
                inTag && (source[offset].isLetter() || source[offset] in "_:") -> {
                    var end = offset + 1
                    while (end < source.length && (source[end].isLetterOrDigit() || source[end] in "_.:-")) end++
                    emit(end, if (tagName) KotlinCodeKind.Type else KotlinCodeKind.Property)
                    tagName = false
                }
                else -> emit(offset + 1, KotlinCodeKind.Plain)
            }
        }
        return KotlinCodeDocument(fileName, source, spans)
    }
}
