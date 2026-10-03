package io.github.composefluent.winrt.gallery.processor

import io.github.composefluent.winrt.gallery.code.KotlinCodeDocument
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

internal fun kotlinLiteral(value: String): String = buildString {
    append('"')
    value.forEach { append(when (it) {
        '\\' -> "\\\\"; '"' -> "\\\""; '$' -> "\\$"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
        // Constants are chunked by UTF-16 length. Escape surrogate code units so
        // a pair split between chunks survives UTF-8 file output unchanged.
        else -> if (it.code < 32 || it.code in 0xD800..0xDFFF) "\\u" + it.code.toString(16).padStart(4, '0') else it.toString()
    }) }
    append('"')
}

internal fun generateCodeDocument(name: String, document: KotlinCodeDocument): String =
    generateCodeDocuments(listOf(name to document))

internal fun generateCodeDocuments(
    documents: List<Pair<String, KotlinCodeDocument>>,
    origins: Map<String, KotlinCodeOriginData> = emptyMap(),
): String = buildString {
    appendLine("// Generated from the sample's source files. Do not edit.")
    appendLine("package $galleryPackage")
    appendLine("import $galleryPackage.code.*")
    documents.forEach { (name, document) -> appendCodeDocument(name, document, origins[name]) }
}

private fun StringBuilder.appendCodeDocument(name: String, document: KotlinCodeDocument, origin: KotlinCodeOriginData?) {
    if (origin != null) {
        val lexer = KotlinLexer().apply { start(document.source) }
        val offsets = mutableListOf<Int>()
        while (lexer.tokenType != null) {
            if (lexer.tokenType == KtTokens.IDENTIFIER) {
                offsets += lexer.tokenEnd
                offsets += origin.fragment.originalOffsets[lexer.tokenStart]
                offsets += origin.fragment.originalOffsets[lexer.tokenEnd - 1] + 1
            }
            lexer.advance()
        }
        appendLine("@KotlinCodeOrigin(${kotlinLiteral(origin.path)}, [${offsets.joinToString()}])")
    }
    appendLine("internal object $name {")
    appendLine("  fun create() = KotlinCodeDocument(${kotlinLiteral(document.fileName)}, source(), spans())")
    // Bound both string constants and method bytecode, including unusually large sample files.
    appendLine("  private fun source() = listOf<String>(")
    document.source.chunked(2000).forEach { appendLine("    ${kotlinLiteral(it)},") }
    appendLine("  ).joinToString(\"\")")
    val chunks = document.spans.chunked(150)
    appendLine("  private fun spans() = buildList<KotlinCodeSpan> {")
    chunks.indices.forEach { appendLine("    addAll(part$it())") }
    appendLine("  }")
    chunks.forEachIndexed { i, chunk ->
        appendLine("  private fun part$i() = listOf(")
        chunk.forEach { appendLine("    KotlinCodeSpan(${it.end}, KotlinCodeKind.${it.kind}),") }
        appendLine("  )")
    }
    appendLine("}")
}
