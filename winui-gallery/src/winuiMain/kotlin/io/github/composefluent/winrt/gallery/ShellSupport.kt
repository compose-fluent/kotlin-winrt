// Ported from WinUI Gallery Samples/{AppNotification,BadgeNotificationManager,JumpList} (MIT).
package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import windows.foundation.Uri
import windows.globalization.Calendar
import windows.ui.startscreen.JumpList
import windows.ui.startscreen.JumpListItem

internal fun packageRequirement(api: String, available: Boolean) = InfoBar().apply {
    margin = Thickness(0.0, 10.0, 0.0, 0.0); isOpen = !available; isClosable = false; severity = InfoBarSeverity.Warning
    title = "$api is not available in unpackaged mode."; message = "This API requires the app to be running in packaged mode (MSIX)."
}
