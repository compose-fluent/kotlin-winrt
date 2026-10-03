package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.gallery.code.*

internal fun substituteGalleryCode(document: KotlinCodeDocument, Substitutions: List<ControlExampleSubstitution>): KotlinCodeDocument {
        val replacements = Regex("""\$\(([^)]+)\)""").findAll(document.source).map { match ->
            match to requireNotNull(Substitutions.firstOrNull { it.Key == match.groupValues[1] }) {
                "Unknown sample substitution ${match.groupValues[1]} in ${document.fileName}"
            }.ValueAsString()
        }.toList()
        if (replacements.isEmpty()) return document
        val source = buildString {
            var start = 0
            replacements.forEach { (match, value) ->
                append(document.source, start, match.range.first); append(value)
                start = match.range.last + 1
            }
            append(document.source, start, document.source.length)
        }
        fun mapOffset(offset: Int): Int {
            var delta = 0
            for ((match, value) in replacements) {
                if (offset <= match.range.first) break
                if (offset <= match.range.last + 1) return match.range.first + delta + value.length
                delta += value.length - match.value.length
            }
            return offset + delta
        }
        var end = 0
        val spans = document.spans.mapNotNull { span ->
            val mapped = mapOffset(span.end)
            if (mapped <= end) null else KotlinCodeSpan(mapped, span.kind).also { end = mapped }
        }
        return document.copy(source = source, spans = spans)
    }
