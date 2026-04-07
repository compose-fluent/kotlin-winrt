package dev.winrt.core

internal fun splitTopLevel(
    source: String,
    separator: Char,
): List<String> = buildList {
    if (source.isBlank()) {
        return@buildList
    }
    var parenthesisDepth = 0
    var angleDepth = 0
    var start = 0
    source.forEachIndexed { index, char ->
        when (char) {
            '(' -> parenthesisDepth += 1
            ')' -> parenthesisDepth -= 1
            '<' -> angleDepth += 1
            '>' -> angleDepth -= 1
            separator -> if (parenthesisDepth == 0 && angleDepth == 0) {
                add(source.substring(start, index).trim())
                start = index + 1
            }
        }
    }
    add(source.substring(start).trim())
}
