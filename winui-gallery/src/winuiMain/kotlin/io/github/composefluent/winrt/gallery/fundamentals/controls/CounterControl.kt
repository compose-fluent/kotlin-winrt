// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.fundamentals.controls

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.documents.*
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.*

internal class CounterControl : Control() {
    private var ActionButton: Button? = null
    private var CountText: TextBlock? = null
    private var actionHandler: RoutedEventHandler? = null
    var Count: Int
        get() = getValue(CountProperty) as Int
        set(value) { setValue(CountProperty, value) }
    var Mode: CounterMode
        get() = getValue(ModeProperty) as CounterMode
        set(value) { setValue(ModeProperty, value) }
    init { defaultStyleKey = CounterControl::class }
    override fun onApplyTemplate() {
        actionHandler?.let { handler -> ActionButton?.click?.remove(handler) }
        super.onApplyTemplate()
        ActionButton = getTemplateChild("ActionButton")?.asWinRT<Button>()
        CountText = getTemplateChild("CountText")?.asWinRT<TextBlock>()
        actionHandler = RoutedEventHandler { _, _ -> Count += if (Mode == CounterMode.Increment) 1 else -1 }
        actionHandler?.let { ActionButton?.click?.add(it) }
        UpdateButtonText(); UpdateUI()
    }
    private fun UpdateButtonText() {
        val button = ActionButton ?: return
        val text = if (Mode == CounterMode.Increment) "Increase" else "Decrease"
        button.content = text; AutomationProperties.setName(button, "$text counter")
    }
    private fun UpdateUI() {
        CountText?.let { text -> text.text = Count.toString()
            if (AutomationPeer.listenerExists(AutomationEvents.LiveRegionChanged))
                FrameworkElementAutomationPeer.createPeerForElement(text).raiseAutomationEvent(AutomationEvents.LiveRegionChanged)
        }
    }
    companion object {
        val CountProperty: DependencyProperty = DependencyProperty.register("Count", Int::class, CounterControl::class, PropertyMetadata(0, PropertyChangedCallback { sender, _ -> checkNotNull(sender).asWinRT<CounterControl>().UpdateUI() }))
        val ModeProperty: DependencyProperty = DependencyProperty.register("Mode", CounterMode::class, CounterControl::class, PropertyMetadata(CounterMode.Increment, PropertyChangedCallback { sender, _ -> checkNotNull(sender).asWinRT<CounterControl>().UpdateButtonText() }))
    }
}
