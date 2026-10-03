package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "ImplicitTransition", title = "Implicit Transitions", group = "Motion", order = 3)
internal class ImplicitTransitionPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent(); OpacityRectangle.opacityTransition = ScalarTransition(); RotationRectangle.rotationTransition = ScalarTransition()
        ScaleRectangle.scaleTransition = Vector3Transition(); TranslateRectangle.translationTransition = Vector3Transition()
        BrushPresenter.backgroundTransition = BrushTransition(); ThemeExampleGrid.backgroundTransition = BrushTransition()
    }
    private fun EnsureValueIsNumber(box: NumberBox): Float { if (box.value.isNaN()) box.value = 0.0; return box.value.toFloat() }
    private fun ApplyOpacity(value: Float) { OpacityRectangle.opacity = value.toDouble(); OpacityValue.Value = value; announce(OpacityBtn, "Rectangle opacity changed by $value points", "RectangleChangedNotificationActivityId") }
    private fun ApplyRotation(value: Float) { RotationRectangle.centerPoint = windows.foundation.numerics.Vector3((RotationRectangle.actualWidth / 2).toFloat(), (RotationRectangle.actualHeight / 2).toFloat(), 0f); RotationRectangle.rotation = value; RotationValue.Value = value; announce(RotateBtn, "Rectangle rotated by $value degrees", "RectangleChangedNotificationActivityId") }
    private fun Components(x: CheckBox, y: CheckBox, z: CheckBox): Vector3TransitionComponents =
        (if (x.isChecked == true) Vector3TransitionComponents.X else Vector3TransitionComponents(0u)) or
        (if (y.isChecked == true) Vector3TransitionComponents.Y else Vector3TransitionComponents(0u)) or
        (if (z.isChecked == true) Vector3TransitionComponents.Z else Vector3TransitionComponents(0u))
    private fun ApplyScale(value: Float) { checkNotNull(ScaleRectangle.scaleTransition).components = Components(ScaleX, ScaleY, ScaleZ); ScaleRectangle.scale = windows.foundation.numerics.Vector3(value, value, value); ScaleValue.Value = value; announce(ScaleBtn, "Rectangle scaled by $value points", "RectangleChangedNotificationActivityId") }
    private fun ApplyTranslation(value: Float) { checkNotNull(TranslateRectangle.translationTransition).components = Components(TranslateX, TranslateY, TranslateZ); TranslateRectangle.translation = windows.foundation.numerics.Vector3(value, value, value); TranslationValue.Value = value; announce(TranslateBtn, "Rectangle translated by $value points", "RectangleChangedNotificationActivityId") }
    private fun OpacityButton_Click(sender: Any?, args: RoutedEventArgs) { ApplyOpacity(EnsureValueIsNumber(OpacityNumberBox)) }
    private fun RotationButton_Click(sender: Any?, args: RoutedEventArgs) { ApplyRotation(EnsureValueIsNumber(RotationNumberBox)) }
    private fun ScaleButton_Click(sender: Any?, args: RoutedEventArgs) { ApplyScale(sender?.asWinRT<Button>()?.tag?.toString()?.toFloatOrNull() ?: EnsureValueIsNumber(ScaleNumberBox)) }
    private fun TranslateButton_Click(sender: Any?, args: RoutedEventArgs) { ApplyTranslation(sender?.asWinRT<Button>()?.tag?.toString()?.toFloatOrNull() ?: EnsureValueIsNumber(TranslationNumberBox)) }
    private fun NumberBox_KeyDown(sender: Any?, args: microsoft.ui.xaml.input.KeyRoutedEventArgs) { if (args.key != windows.system.VirtualKey.Enter) return; when (checkNotNull(sender).asWinRT<NumberBox>().header?.toString()) { "Opacity (0.0 to 1.0)" -> ApplyOpacity(EnsureValueIsNumber(OpacityNumberBox)); "Rotation (0.0 to 360.0)" -> ApplyRotation(EnsureValueIsNumber(RotationNumberBox)); "Scale (0.0 to 5.0)" -> ApplyScale(EnsureValueIsNumber(ScaleNumberBox)); "Translation (0.0 to 200.0)" -> ApplyTranslation(EnsureValueIsNumber(TranslationNumberBox)) } }
    private fun BackgroundButton_Click(sender: Any?, args: RoutedEventArgs) { val blue = BrushPresenter.background?.asWinRT<SolidColorBrush>()?.color == rgb(0x0000FFu); BrushPresenter.background = SolidColorBrush(if (blue) rgb(0xFFFF00u) else rgb(0x0000FFu)); announce(BgColorBtn, "Rectangle color changed to " + if (blue) "Yellow" else "Blue", "RectangleChangedNotificationActivityId") }
    private fun ThemeButton_Click(sender: Any?, args: RoutedEventArgs) { ThemeExampleGrid.requestedTheme = if (ThemeExampleGrid.requestedTheme == ElementTheme.Dark) ElementTheme.Light else ElementTheme.Dark; announce(ChangeThemeBtn, "UI local theme changed", "RectangleChangedNotificationActivityId") }
}
