// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import kotlin.time.Duration.Companion.milliseconds

@GalleryPage(route = "XamlCompInterop", title = "Animation interop", group = "Motion", order = 0)
internal class XamlCompInteropPage : Page() {
    private val compositor = CompositionTarget.getCompositorForCurrentThread()
    private var springAnimation: microsoft.ui.composition.SpringVector3NaturalMotionAnimation? = null
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; unloaded.add { _, _ -> Popup.isOpen = false } }
    private fun UpdateSpringAnimation(final: Float) {
        val animation = springAnimation ?: compositor.createSpringVector3Animation().apply { target = "Scale" }.also { springAnimation = it }
        animation.finalValue = windows.foundation.numerics.Vector3(final, final, final)
        animation.dampingRatio = DampingStackPanel.selectedItem?.asWinRT<RadioButton>()?.content?.toString()?.toFloatOrNull() ?: 0.6f
        animation.period = PeriodSlider.value.milliseconds
    }
    private fun NaturalMotionExample_Loaded(sender: Any?, args: RoutedEventArgs) { UpdateSpringAnimation(1f) }
    private fun element_PointerEntered(sender: Any?, args: microsoft.ui.xaml.input.PointerRoutedEventArgs) { if (springAnimation != null) { UpdateSpringAnimation(1.5f); checkNotNull(sender).asWinRT<UIElement>().startAnimation(checkNotNull(springAnimation)) } }
    private fun element_PointerExited(sender: Any?, args: microsoft.ui.xaml.input.PointerRoutedEventArgs) { if (springAnimation != null) { UpdateSpringAnimation(1f); checkNotNull(sender).asWinRT<UIElement>().startAnimation(checkNotNull(springAnimation)) } }
    private fun ExpressionSample_Loaded(sender: Any?, args: RoutedEventArgs) {
        val animation = compositor.createExpressionAnimation("Vector3(1/scaleElement.Scale.X, 1/scaleElement.Scale.Y, 1)").apply { target = "Scale"; setExpressionReferenceParameter("scaleElement", rectangle) }
        ellipse.startAnimation(animation)
    }
    private fun StackedButtonsExample_Loaded(sender: Any?, args: RoutedEventArgs) {
        val animation = compositor.createExpressionAnimation("(above.Scale.Y - 1) * 50 + above.Translation.Y % (50 * index)").apply { target = "Translation.Y" }
        val buttons = listOf(ExpressionButton1, ExpressionButton2, ExpressionButton3, ExpressionButton4)
        for (index in 1..3) { animation.setExpressionReferenceParameter("above", buttons[index - 1]); animation.setScalarParameter("index", index.toFloat()); buttons[index].startAnimation(animation) }
    }
    private fun ActualSizeExample_Loaded(sender: Any?, args: RoutedEventArgs) {
        if (LayoutPanel.children.isNotEmpty()) return
        val radius = "(source.ActualSize.X / 2)"
        val theta = ".02 * $radius + ((2 * Pi)/total)*index"
        repeat(8) { index ->
            val button = Button().apply { content = "Button"; microsoft.ui.xaml.automation.AutomationProperties.setName(this, "Button $index") }
            LayoutPanel.children.add(button)
            val animation = compositor.createExpressionAnimation("Vector3($radius*cos($theta)+$radius, $radius*sin($theta)+0,0)").apply {
                setScalarParameter("index", (index + 1).toFloat()); setScalarParameter("total", 8f); target = "Translation"; setExpressionReferenceParameter("source", LayoutPanel)
            }
            button.startAnimation(animation)
        }
    }
    private fun RadiusSlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) { LayoutPanel.width = args.newValue; LayoutPanel.height = args.newValue } }
    private fun ActualOffsetExample_Loaded(sender: Any?, args: RoutedEventArgs) {
        val animation = compositor.createExpressionAnimation("Vector3(source.ActualOffset.X + source.ActualSize.X, source.ActualOffset.Y + source.ActualSize.Y / 2 - 25, 0)").apply {
            target = "Translation"; setExpressionReferenceParameter("source", PopupTarget)
        }
        Popup.startAnimation(animation); Popup.isOpen = true
    }
}
