package io.github.composefluent.winrt.ide.xaml

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.xml.XmlAttributeValue

/** Source spans for editing BindingPath.g4 syntax. XAMLC remains the build
 * parser/authority; this bounded parser also accepts an unfinished final member
 * so native completion can work before a new harvester snapshot exists.
 */
internal object WinRTXamlBindingSyntax {
    data class Token(val text: String, val range: TextRange)
    data class Expr(val kind: String, val token: Token? = null, val receiver: Expr? = null,
        val arguments: List<Expr> = emptyList())
    data class Markup(val compiled: Boolean, val expression: Expr?, val bindBack: Expr?,
        val elementName: Token?, val pathRange: TextRange, val complete: Boolean)

    fun parse(value: XmlAttributeValue): Markup? {
        val range = ElementManipulators.getValueTextRange(value)
        // Work on raw XML to preserve offsets even after escaped string arguments.
        val text = value.text.substring(range.startOffset, range.endOffset)
        val header = Regex("^\\s*\\{(?:([\\p{L}_][\\p{L}\\p{N}_]*):)?(Bind|Binding)\\s+").find(text) ?: return null
        val tag = (value.parent as? com.intellij.psi.xml.XmlAttribute)?.parent ?: return null
        val compiled = header.groupValues[2] == "Bind"
        if (compiled && tag.getNamespaceByPrefix(header.groupValues[1]) != WinRTXamlCatalog.XAML) return null
        if (!compiled && header.groupValues[1].isNotEmpty()) return null
        if (text.length > 16_384) return null
        val start = range.startOffset + header.range.last + 1
        val bodyEnd = range.endOffset - if (text.trimEnd().endsWith('}')) 1 + text.length - text.trimEnd().length else 0
        val parser = Parser(value.text, start, bodyEnd)
        return parser.markup(compiled)
    }

    private class Parser(val text: String, start: Int, val end: Int) {
        var offset = start
        var nodes = 0
        var complete = true
        fun markup(compiled: Boolean): Markup {
            whitespace()
            val pathStart = offset
            val first = identifier()
            var element: Token? = null
            var back: Expr? = null
            var expression: Expr? = null
            if (first != null && take('=')) {
                when (first.text) {
                    "Path" -> expression = path()
                    "ElementName" -> element = identifier()
                    "BindBack" -> back = path()
                    else -> skipOption()
                }
            } else {
                offset = pathStart
                expression = if (peek() == '}' || peek() == ',' || offset >= end)
                    Expr("member", Token("", TextRange(offset, offset)), Expr("root")) else path()
            }
            val pathEnd = offset
            while (take(',')) {
                val key = identifier() ?: break
                if (!take('=')) { complete = false; break }
                when (key.text) {
                    "ElementName" -> element = identifier()
                    "Path" -> expression = path()
                    "BindBack" -> back = path()
                    else -> skipOption()
                }
            }
            whitespace()
            if (offset < end) complete = false
            return Markup(compiled, expression, back, element, TextRange(pathStart, pathEnd), complete)
        }

        fun path(): Expr? {
            if (++nodes > 128) { complete = false; offset = end; return null }
            whitespace()
            var expression = when {
                take('(') -> {
                    if (take('(')) {
                        val type = typeToken() ?: return invalid()
                        if (!take(')')) return invalid()
                        val receiver = path() ?: Expr("root")
                        if (!take(')')) complete = false
                        Expr("cast", type, receiver)
                    } else {
                        val type = typeToken() ?: return invalid()
                        if (!take(')')) return invalid()
                        val receiver = if (identifierStart(peek())) path() ?: Expr("root") else Expr("root")
                        Expr("cast", type, receiver)
                    }
                }
                else -> {
                    val token = identifier() ?: return invalid()
                    if (take(':')) {
                        val typeName = identifier() ?: return invalid()
                        val type = Token("${token.text}:${typeName.text}", TextRange(token.range.startOffset, typeName.range.endOffset))
                        if (type.text in setOf("x:True", "x:False", "x:Null")) Expr("literal", type)
                        else Expr("static", type)
                    } else member(Expr("root"), token)
                }
            }
            while (offset < end) {
                expression = when {
                    take('.') -> {
                        if (take('(')) {
                            val type = typeToken() ?: return invalid()
                            if (!take('.')) return invalid()
                            val name = identifier() ?: return invalid()
                            if (!take(')')) complete = false
                            Expr("attached", name, Expr("cast", type, expression))
                        } else {
                            val name = identifier() ?: Token("", TextRange(offset, offset))
                            member(expression, name)
                        }
                    }
                    take('[') -> {
                        val argument = literal() ?: return invalid()
                        if (!take(']')) complete = false
                        Expr("index", receiver = expression, arguments = listOf(argument))
                    }
                    else -> return expression
                }
            }
            return expression
        }

        fun member(receiver: Expr, name: Token): Expr {
            if (!take('(')) return Expr("member", name, receiver)
            val arguments = mutableListOf<Expr>()
            if (!take(')')) {
                do { arguments += literal() ?: path() ?: break } while (take(','))
                if (!take(')')) complete = false
            }
            return Expr("call", name, receiver, arguments)
        }

        fun typeToken(): Token? {
            val name = identifier() ?: return null
            if (!take(':')) return name
            val type = identifier() ?: return null
            return Token("${name.text}:${type.text}", TextRange(name.range.startOffset, type.range.endOffset))
        }

        fun literal(): Expr? {
            whitespace()
            val start = offset
            val quote = when {
                peek() == '\'' || peek() == '"' -> text[offset++].toString()
                text.startsWith("&quot;", offset) -> "&quot;".also { offset += it.length }
                text.startsWith("&apos;", offset) -> "&apos;".also { offset += it.length }
                else -> null
            }
            if (quote != null) {
                while (offset < end) {
                    if (text.startsWith("^$quote", offset)) offset += quote.length + 1
                    else if (text.startsWith(quote, offset)) { offset += quote.length; break }
                    else offset++
                }
                return Expr("literal", Token("string", TextRange(start, offset)))
            }
            if (peek().isDigit() || (peek() == '-' && text.getOrNull(offset + 1)?.isDigit() == true)) {
                offset++
                while (offset < end && (text[offset].isDigit() || text[offset] == '.')) offset++
                return Expr("literal", Token("number", TextRange(start, offset)))
            }
            return null
        }

        fun skipOption() {
            var depth = 0
            while (offset < end) {
                if (literal() != null) continue
                when (peek()) { '{', '(', '[' -> depth++; '}', ')', ']' -> depth--; ',' -> if (depth == 0) return }
                offset++
            }
        }
        fun identifier(): Token? {
            whitespace()
            val start = offset
            if (!identifierStart(peek())) return null
            offset++
            while (offset < end && Character.isJavaIdentifierPart(text[offset])) offset++
            return Token(text.substring(start, offset), TextRange(start, offset))
        }
        fun identifierStart(char: Char) = char != '\u0000' && Character.isJavaIdentifierStart(char)
        fun whitespace() { while (offset < end && text[offset].isWhitespace()) offset++ }
        fun peek(): Char { whitespace(); return if (offset < end) text[offset] else '\u0000' }
        fun take(char: Char): Boolean = (peek() == char).also { if (it) offset++ }
        fun invalid(): Expr? { complete = false; return null }
    }
}
