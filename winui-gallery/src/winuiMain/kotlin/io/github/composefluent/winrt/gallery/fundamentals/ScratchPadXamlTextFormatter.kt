// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.fundamentals

import io.github.composefluent.winrt.gallery.rgb
import microsoft.ui.xaml.ElementTheme
import microsoft.ui.xaml.controls.RichEditBox

/** Sample-level equivalent of upstream ScratchPad's XamlTextFormatter. */
internal object ScratchPadXamlTextFormatter {
    fun applyColors(editor: RichEditBox, text: String) {
        val document = checkNotNull(editor.textDocument)
        val dark = editor.actualTheme == ElementTheme.Dark
        fun color(start: Int, end: Int, light: UInt, night: UInt) {
            if (end > start) checkNotNull(document.getRange(start, end).characterFormat).foregroundColor = rgb(if (dark) night else light)
        }
        document.beginUndoGroup()
        try {
            color(0, text.length, 0x000000u, 0xFFFFFFu)
            var index = 0
            while (index < text.length) {
                if (text.startsWith("<!--", index)) {
                    val close = text.indexOf("-->", index + 4)
                    val end = if (close < 0) text.length else close + 3
                    color(index, end, 0x008000u, 0x32CD32u)
                    index = end
                } else if (text[index] == '<') {
                    val start = index++
                    if (text.getOrNull(index) == '/') index++
                    color(start, index, 0x0000FFu, 0x808080u)
                    val nameStart = index
                    while (index < text.length && !text[index].isWhitespace() && text[index] != '>' && text[index] != '/') index++
                    color(nameStart, index, 0xA52A2Au, 0xFFFFFFu)
                    while (index < text.length && text[index] != '>') {
                        if (text[index].isWhitespace()) { index++; continue }
                        val propertyStart = index
                        if (text[index] == '/' || text[index] == '=') {
                            color(index, index + 1, 0x0000FFu, 0x808080u)
                            index++
                        } else if (text[index] == '"' || text[index] == '\'') {
                            val quote = text[index++]
                            while (index < text.length && text[index] != quote) index++
                            if (index < text.length) index++
                            color(propertyStart, index, 0x0000FFu, 0x1E90FFu)
                        } else {
                            while (index < text.length && !text[index].isWhitespace() && text[index] !in "=/>\"'") index++
                            color(propertyStart, index, 0xFF0000u, 0x87CEFAu)
                        }
                    }
                    if (index < text.length) { color(index, index + 1, 0x0000FFu, 0x808080u); index++ }
                } else index++
            }
        } finally { document.endUndoGroup() }
    }
}
