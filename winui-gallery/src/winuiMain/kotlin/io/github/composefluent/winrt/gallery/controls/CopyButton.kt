package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.media.animation.Storyboard
import microsoft.ui.xaml.automation.peers.*

internal class CopyButton : Button() {
    var CopiedMessage: String
        get() = (getValue(CopiedMessageProperty) as? String).orEmpty()
        set(value) { setValue(CopiedMessageProperty, value) }
    init {
        defaultStyleKey = CopyButton::class
        click.add { _, _ ->
            getTemplateChild("CopyToClipboardSuccessAnimation")?.asWinRT<Storyboard>()?.begin()
            FrameworkElementAutomationPeer.fromElement(this)?.raiseNotificationEvent(
                AutomationNotificationKind.ActionCompleted, AutomationNotificationProcessing.ImportantMostRecent,
                CopiedMessage, "CopiedToClipboardActivityId")
        }
    }
    companion object {
        val CopiedMessageProperty = DependencyProperty.register("CopiedMessage", String::class, CopyButton::class, PropertyMetadata("Copied to clipboard"))
    }
}
