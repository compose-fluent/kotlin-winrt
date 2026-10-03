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

internal class DetailedInfoPage : Page() {
    var DetailedObject: CustomDataObject? = null
    override fun initializeComponent() { super.initializeComponent(); GoBackButton.loaded.add { _, _ -> GoBackButton.focus(FocusState.Programmatic); GoBackButton.startBringIntoView() } }
    override fun onNavigatedTo(args: NavigationEventArgs) {
        super.onNavigatedTo(args); DetailedObject = args.parameter as? CustomDataObject
        ConnectedAnimationService.getForCurrentView().getAnimation("ForwardConnectedAnimation")?.tryStart(detailedImage, listOf(coordinatedPanel))
    }
    override fun onNavigatingFrom(args: NavigatingCancelEventArgs) { super.onNavigatingFrom(args); ConnectedAnimationService.getForCurrentView().prepareToAnimate("BackConnectedAnimation", detailedImage) }
    private fun BackButton_Click(sender: Any?, args: RoutedEventArgs) { checkNotNull(frame).goBack() }
}
