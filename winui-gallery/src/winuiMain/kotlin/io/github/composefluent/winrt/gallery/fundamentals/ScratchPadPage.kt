// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.fundamentals

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.text.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.input.KeyRoutedEventArgs
import windows.system.VirtualKey

@GalleryPage(route = "ScratchPad", title = "Scratch Pad", group = "FundamentalsItem", order = 6)
internal class ScratchPadPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private var ready = false
    private var lastChangeFromTyping = false
    private var oldText = ""
    private val document get() = checkNotNull(textbox.textDocument)
    override fun initializeComponent() {
        super.initializeComponent()
        ready = true
        resetContent()
    }
    private fun readText(): String = WinRTOut<String>().also { document.getText(TextGetOptions.None, it) }.value.orEmpty()
    private fun resetContent() {
        lastChangeFromTyping = false
        oldText = checkNotNull(GalleryCodeCatalog.sourceDocument("ScratchPad/DefaultXaml.txt")).source
        document.setText(TextSetOptions.None, oldText)
        applyColors()
        scratchPad.content = TextBlock().apply {
            text = "Click the Load button to load the content below."
            horizontalAlignment = HorizontalAlignment.Center
            verticalAlignment = VerticalAlignment.Center
            textWrapping = TextWrapping.Wrap
        }
        loadStatus.text = ""
    }
    private fun ResetToDefaultClick(sender: Any?, args: RoutedEventArgs) { tasks.launch {
        val dialog = ContentDialog().apply {
            xamlRoot = this@ScratchPadPage.xamlRoot
            title = "Are you sure you want to reset?"
            content = "Resetting to the default content will replace your current content. Are you sure you want to reset?"
            primaryButtonText = "Reset"; closeButtonText = "Cancel"
            defaultButton = ContentDialogButton.Primary; requestedTheme = this@ScratchPadPage.actualTheme
        }
        if (dialog.showAsync().await() == ContentDialogResult.Primary) resetContent()
    } }
    private fun LoadClick(sender: Any?, args: RoutedEventArgs) = loadContent()
    private fun loadContent() {
        loadStatus.text = ""
        try {
            val xml = readText().trim()
            val index = xml.indexOfFirst { it.isWhitespace() || it == '/' || it == '>' }
            require(index >= 0) { "No end tag." }
            val qualified = xml.take(index) + " xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' xmlns:x='http://schemas.microsoft.com/winfx/2006/xaml' " + xml.drop(index)
            scratchPad.content = checkNotNull(microsoft.ui.xaml.markup.XamlReader.load(qualified)).asWinRT<UIElement>()
            loadStatus.text = "Load successful."
        } catch (error: Exception) { loadStatus.text = error.message.orEmpty() }
        loadStatus.opacity = 1.0
        lastChangeFromTyping = false
        applyColors()
    }
    private fun insertText(text: String, moveCursor: Boolean) {
        val cursor = checkNotNull(document.selection).startPosition
        lastChangeFromTyping = false
        document.getRange(cursor, cursor).text = text
        checkNotNull(document.selection).startPosition = cursor + if (moveCursor) text.length else 0
    }
    private fun textbox_PreviewKeyDown(sender: Any?, args: KeyRoutedEventArgs) {}
    private fun textbox_KeyDown(sender: Any?, args: KeyRoutedEventArgs) {
        lastChangeFromTyping = true
        if (args.key != VirtualKey.Tab || checkNotNull(document.selection).length <= 1) return
        val text = readText()
        val selection = checkNotNull(document.selection)
        var start = selection.startPosition
        var end = selection.endPosition
        val lineStart = text.take(start).lastIndexOfAny(charArrayOf('\r', '\n')) + 1
        val shift = microsoft.ui.input.InputKeyboardSource.getKeyStateForCurrentThread(VirtualKey.Shift).abiValue and windows.ui.core.CoreVirtualKeyStates.Down.abiValue != 0u
        document.beginUndoGroup()
        try {
            var range = document.getRange(lineStart, lineStart)
            var first = true
            while (range.startPosition < end) {
                val delta = if (shift) {
                    range.moveEnd(TextRangeUnit.Character, 4)
                    val count = range.text.takeWhile { it == ' ' || it == '\t' }.length
                    range = document.getRange(range.startPosition, range.startPosition + count)
                    range.text = ""
                    -count
                } else { range.text = "    "; 4 }
                if (first) { start += delta; first = false }
                end += delta
                val position = range.startPosition
                range.move(TextRangeUnit.Paragraph, 1)
                if (range.startPosition <= position) break
            }
            selection.startPosition = start.coerceAtLeast(lineStart)
            selection.endPosition = end
            args.handled = true
        } finally { document.endUndoGroup() }
    }
    private fun textbox_PreviewKeyUp(sender: Any?, args: KeyRoutedEventArgs) {
        lastChangeFromTyping = true
        when (args.key) {
            VirtualKey.F5 -> loadContent()
            VirtualKey.Enter -> {
                val text = readText()
                val end = text.take(checkNotNull(document.selection).startPosition).trimEnd('\r', '\n').length
                val start = text.take(end).lastIndexOfAny(charArrayOf('\r', '\n')) + 1
                val indent = text.substring(start, end).takeWhile { it == ' ' || it == '\t' }
                document.beginUndoGroup()
                try {
                    insertText(indent, true)
                    val cursor = checkNotNull(document.selection).startPosition
                    if (readText().drop(cursor).startsWith("</")) {
                        insertText("    ", true)
                        insertText("\n" + indent, false)
                    }
                } finally { document.endUndoGroup() }
            }
        }
    }
    private fun textbox_TextChanged(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        val text = readText()
        val cursor = checkNotNull(document.selection).startPosition
        if (checkNotNull(document.selection).length == 0 && lastChangeFromTyping) {
            if (loadStatus.text == "Load successful.") loadStatus.text = "" else loadStatus.opacity = 0.5
            if (text.length == oldText.length + 1 && cursor in 1..text.length) {
                val open = text.lastIndexOf('<', cursor - 1)
                if (text[cursor - 1] == '>' && cursor >= 2 && text[cursor - 2] != '/' && open >= 0 && text.lastIndexOf('>', cursor - 2) < open) {
                    val name = text.substring(open + 1, cursor - 1).takeWhile { !it.isWhitespace() && it != '/' }
                    if (name.isNotEmpty() && !name.startsWith('!') && !name.startsWith('/')) insertText("</$name>", false)
                } else if (text[cursor - 1] == '=' && text.getOrNull(cursor) != '"' && open > text.lastIndexOf('>', cursor - 1)) {
                    val quote = text.lastIndexOf('"', cursor - 1)
                    if (quote < open || text.getOrNull(quote - 1) != '=') {
                        insertText("\"\"", false)
                        checkNotNull(document.selection).startPosition = cursor + 1
                    }
                }
            }
        }
        oldText = readText()
    }
    private fun textbox_ActualThemeChanged(sender: FrameworkElement, args: Any?) { if (ready) applyColors() }
    private fun applyColors() {
        lastChangeFromTyping = false
        ScratchPadXamlTextFormatter.applyColors(textbox, readText())
    }
}
