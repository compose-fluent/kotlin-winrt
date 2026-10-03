package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "EasingFunction", title = "Easing Functions", group = "Motion", order = 2)
internal class EasingFunctionPage : Page() {
    private val EasingFunctions: List<NamedEasingFunction> = listOf("BackEase" to BackEase(), "BounceEase" to BounceEase(), "CircleEase" to CircleEase(), "CubicEase" to CubicEase(), "ElasticEase" to ElasticEase(), "ExponentialEase" to ExponentialEase(), "PowerEase" to PowerEase(), "QuadraticEase" to QuadraticEase(), "QuarticEase" to QuarticEase(), "QuinticEase" to QuinticEase(), "SineEase" to SineEase()).map { NamedEasingFunction(it.first, it.second) }
    override fun initializeComponent() {
        super.initializeComponent()
        gotFocus.add { _, args -> val control = runCatching { args.originalSource?.asWinRT<Control>() }.getOrNull(); if (control != null && control.focusState != FocusState.Pointer) control.startBringIntoView() }
    }
    private fun animate(storyboard: Storyboard, translation: TranslateTransform) {
        val animation = storyboard.children[0].asWinRT<DoubleAnimation>()
        animation.from = translation.x; animation.to = if (translation.x > 0) 0.0 else 200.0; storyboard.begin()
    }
    private fun Button1_Click(sender: Any?, args: RoutedEventArgs) { animate(Storyboard1, Translation1) }
    private fun Button2_Click(sender: Any?, args: RoutedEventArgs) { animate(Storyboard2, Translation2) }
    private fun Button3_Click(sender: Any?, args: RoutedEventArgs) { animate(Storyboard3, Translation3) }
    private fun Button4_Click(sender: Any?, args: RoutedEventArgs) {
        val selected = EasingComboBox.selectedItem as? NamedEasingFunction ?: return
        selected.EasingFunctionBase.easingMode = when { easeOutRB.isChecked == true -> EasingMode.EaseOut; easeInRB.isChecked == true -> EasingMode.EaseIn; else -> EasingMode.EaseInOut }
        storyboardAnimation(Storyboard4).easingFunction = selected.EasingFunctionBase
        animate(Storyboard4, Translation4)
    }
    private fun storyboardAnimation(storyboard: Storyboard): DoubleAnimation = storyboard.children[0].asWinRT<DoubleAnimation>()
}
