package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "PageTransition", title = "Page Transitions", group = "Motion", order = 4)
internal class PageTransitionPage : Page() {
    private var transitionInfo: NavigationTransitionInfo? = null
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent()
        ready = true
        ContentFrame.navigationFailed.add { _, args ->
            println("Gallery page transition navigation failed: ${args.exception.stackTraceToString()}")
        }
        ContentFrame.navigate(SamplePage1::class)
    }
    private fun ForwardButton1_Click(sender: Any?, args: RoutedEventArgs) {
        val page = if (ContentFrame.backStackDepth % 2 == 1) SamplePage1::class else SamplePage2::class
        val transition = transitionInfo
        if (transition == null) ContentFrame.navigate(page, null) else ContentFrame.navigate(page, null, transition)
    }
    private fun BackwardButton1_Click(sender: Any?, args: RoutedEventArgs) { if (ContentFrame.canGoBack) ContentFrame.goBack() }
    private fun TransitionRadioButton_Checked(sender: Any?, args: RoutedEventArgs) {
        val selected = checkNotNull(sender).asWinRT<RadioButton>().content?.toString()
        transitionInfo = when (selected) { "Entrance" -> EntranceNavigationTransitionInfo(); "DrillIn" -> DrillInNavigationTransitionInfo(); "Suppress" -> SuppressNavigationTransitionInfo(); "Common" -> CommonNavigationTransitionInfo(); "Continuum" -> ContinuumNavigationTransitionInfo(); "Slide from Right", "Slide from Left" -> SlideNavigationTransitionInfo().apply { effect = if (selected == "Slide from Right") SlideNavigationTransitionEffect.FromRight else SlideNavigationTransitionEffect.FromLeft }; else -> null }
        if (ready) TransitionValue.Value = if (selected == "Default") "" else ", ${transitionInfo?.let { it::class.simpleName }}()"
    }
}
