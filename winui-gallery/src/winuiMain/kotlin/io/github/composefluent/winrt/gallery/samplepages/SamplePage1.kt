// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.samplepages

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.collections.CustomDataObject
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.input.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*
import microsoft.ui.xaml.navigation.*

internal class SamplePage1 : Page() {
    fun PrepareConnectedAnimation(config: ConnectedAnimationConfiguration?) { ConnectedAnimationService.getForCurrentView().prepareToAnimate("ForwardConnectedAnimation", SourceElement).let { if (config != null) it.configuration = config } }
    override fun onNavigatedTo(args: NavigationEventArgs) { super.onNavigatedTo(args); ConnectedAnimationService.getForCurrentView().getAnimation("BackwardConnectedAnimation")?.tryStart(SourceElement) }
}
