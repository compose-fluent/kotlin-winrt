// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls
import microsoft.ui.xaml.controls.UserControl
internal class HomePageHeader : UserControl() {
    // #if WINUI2
    val WinAppSdkDetails: String = "WinUI 2.8.7 / UWP"
    // #else
    val WinAppSdkDetails: String = "Windows App SDK 2.5.1"
    // #endif
}
