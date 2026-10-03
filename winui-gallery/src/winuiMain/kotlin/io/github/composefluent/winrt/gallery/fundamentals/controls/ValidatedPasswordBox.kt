// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.fundamentals.controls

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.documents.*
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.*

internal class ValidatedPasswordBox : Control() {
    private var PasswordInput: PasswordBox? = null
    private var ValidationRichText: RichTextBlock? = null
    var Password: String
        get() = getValue(PasswordProperty) as String
        set(value) { setValue(PasswordProperty, value) }
    var IsValid: Boolean
        get() = getValue(IsValidProperty) as Boolean
        set(value) { setValue(IsValidProperty, value) }
    var MinLength: Int
        get() = getValue(MinLengthProperty) as Int
        set(value) { setValue(MinLengthProperty, value) }
    var Header: String
        get() = getValue(HeaderProperty) as String
        set(value) { setValue(HeaderProperty, value) }
    var PlaceholderText: String
        get() = getValue(PlaceholderTextProperty) as String
        set(value) { setValue(PlaceholderTextProperty, value) }
    init { defaultStyleKey = ValidatedPasswordBox::class }
    override fun onApplyTemplate() {
        super.onApplyTemplate()
        PasswordInput = getTemplateChild("PasswordInput")?.asWinRT<PasswordBox>()
        ValidationRichText = getTemplateChild("ValidationRichText")?.asWinRT<RichTextBlock>()
        PasswordInput?.let { input -> input.header = Header; input.placeholderText = PlaceholderText
            input.passwordChanged.add { _, _ -> Password = input.password }
        }
        ValidationRichText?.actualThemeChanged?.add { _, _ -> UpdateValidationMessages() }
        UpdateValidationMessages()
    }
    private fun UpdateValidationMessages() {
        val errors = buildList {
            if (Password.none(Char::isUpperCase)) add("Missing uppercase")
            if (Password.none(Char::isDigit)) add("Missing number")
            if (Password.length < MinLength) add("Too short!")
        }
        IsValid = errors.isEmpty()
        val text = ValidationRichText ?: return
        text.visibility = if (Password.isEmpty()) Visibility.Collapsed else Visibility.Visible
        text.blocks.clear(); if (Password.isEmpty()) return
        val critical = !IsValid; val light = actualTheme == ElementTheme.Light
        val color = brush(if (critical) { if (light) 0xC42B1Cu else 0xFF99A4u } else { if (light) 0x0F7B0Fu else 0x6CCB5Fu })
        text.blocks.add(Paragraph().apply {
            (if (critical) errors else listOf("Password is valid")).forEachIndexed { index, message ->
                if (index > 0) inlines.add(LineBreak())
                inlines.add(InlineUIContainer().apply { child = FontIcon().apply { glyph = if (critical) "\uEA39" else "\uE930"; fontSize = 14.0; foreground = color } })
                inlines.add(Run().apply { this.text = " " }); inlines.add(Run().apply { this.text = message; foreground = color })
            }
        })
        if (AutomationPeer.listenerExists(AutomationEvents.LiveRegionChanged))
            FrameworkElementAutomationPeer.createPeerForElement(text).raiseAutomationEvent(AutomationEvents.LiveRegionChanged)
    }
    companion object {
        private fun OnPasswordChanged(owner: DependencyObject, args: DependencyPropertyChangedEventArgs) { owner.asWinRT<ValidatedPasswordBox>().UpdateValidationMessages() }
        val PasswordProperty: DependencyProperty = DependencyProperty.register("Password", String::class, ValidatedPasswordBox::class, PropertyMetadata("", ::OnPasswordChanged))
        val IsValidProperty: DependencyProperty = DependencyProperty.register("IsValid", Boolean::class, ValidatedPasswordBox::class, PropertyMetadata(false))
        val MinLengthProperty: DependencyProperty = DependencyProperty.register("MinLength", Int::class, ValidatedPasswordBox::class, PropertyMetadata(8, ::OnPasswordChanged))
        val HeaderProperty: DependencyProperty = DependencyProperty.register("Header", String::class, ValidatedPasswordBox::class, PropertyMetadata("", ::OnPasswordChanged))
        val PlaceholderTextProperty: DependencyProperty = DependencyProperty.register("PlaceholderText", String::class, ValidatedPasswordBox::class, PropertyMetadata("", ::OnPasswordChanged))
    }
}
