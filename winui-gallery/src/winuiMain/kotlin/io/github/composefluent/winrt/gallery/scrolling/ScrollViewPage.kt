package io.github.composefluent.winrt.gallery.scrolling

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

@GalleryPage(route = "ScrollView", title = "ScrollView", group = "Scrolling", order = 2)
internal class ScrollViewPage : Page() {
    private var ready = false
    private var correctingVelocity = false
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        loaded.add { _, _ ->
            scrollView1.zoomTo(4f, null, ScrollingZoomOptions(ScrollingAnimationMode.Enabled, ScrollingSnapPointsMode.Ignore))
            nbZoomFactor.numberFormatter = windows.globalization.numberformatting.DecimalFormatter().apply {
                integerDigits = 2; fractionDigits = 1
                numberRounder = windows.globalization.numberformatting.IncrementNumberRounder().apply {
                    increment = 0.1; roundingAlgorithm = windows.globalization.numberformatting.RoundingAlgorithm.RoundHalfUp
                }
            }
        }
    }
    private fun CmbZoomMode_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) scrollView1.zoomMode = ScrollingZoomMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun NbZoomFactor_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) { if (ready && !args.newValue.isNaN()) scrollView1.zoomTo(args.newValue.toFloat(), null) }
    private fun CmbHorizontalScrollMode_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) scrollView1.horizontalScrollMode = ScrollingScrollMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun CmbVerticalScrollMode_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) scrollView1.verticalScrollMode = ScrollingScrollMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun CmbHorizontalScrollBarVisibility_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) scrollView1.horizontalScrollBarVisibility = ScrollingScrollBarVisibility.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun CmbVerticalScrollBarVisibility_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) scrollView1.verticalScrollBarVisibility = ScrollingScrollBarVisibility.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun NbVerticalVelocity_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) {
        if (!ready || correctingVelocity || args.oldValue.isNaN() || args.newValue.isNaN()) return
        scrollView2.scrollBy(0.0, 0.0, ScrollingScrollOptions(ScrollingAnimationMode.Disabled, ScrollingSnapPointsMode.Ignore))
        var speed = args.newValue.toFloat()
        if (speed in -30f..30f) speed = if (args.newValue < args.oldValue) {
            if (scrollView2.verticalOffset == 0.0) 30f else -30f
        } else if (scrollView2.verticalOffset == scrollView2.scrollableHeight) -30f else 30f
        else if (speed < 30f && scrollView2.verticalOffset == 0.0) speed = 30f
        else if (speed > 30f && scrollView2.verticalOffset == scrollView2.scrollableHeight) speed = -30f
        correctingVelocity = true
        try { sender.value = speed.toDouble() } finally { correctingVelocity = false }
        scrollView2.addScrollVelocity(windows.foundation.numerics.Vector2(0f, speed), windows.foundation.numerics.Vector2(0f, 0f))
    }
    private fun GetTargetVerticalOffset(): Double = scrollView3.scrollableHeight * if (scrollView3.verticalOffset > scrollView3.scrollableHeight / 2) 0.2 else 0.8
    private fun BtnScrollWithAnimation_Click(sender: Any?, args: RoutedEventArgs) { scrollView3.scrollTo(scrollView3.horizontalOffset, GetTargetVerticalOffset(), ScrollingScrollOptions(ScrollingAnimationMode.Enabled, ScrollingSnapPointsMode.Ignore)) }
    private fun ScrollView_ScrollAnimationStarting(sender: ScrollView, args: ScrollingScrollAnimationStartingEventArgs) {
        val stock = checkNotNull(args.animation).asWinRT<microsoft.ui.composition.Vector3KeyFrameAnimation>()
        if (cmbVerticalAnimation.selectedIndex == 0) { stock.duration = nbAnimationDuration.value.milliseconds; return }
        val compositor = checkNotNull(stock.compositor)
        val custom = compositor.createVector3KeyFrameAnimation()
        val y = GetTargetVerticalOffset().toFloat(); val x = scrollView3.horizontalOffset.toFloat()
        val delta = y - scrollView3.verticalOffset.toFloat()
        if (cmbVerticalAnimation.selectedIndex == 1) {
            var bounce = 0.1f * delta
            repeat(3) { step -> custom.insertKeyFrame(1f - 0.4f / 2.0.pow(step).toFloat(), windows.foundation.numerics.Vector3(x, y + bounce, 0f)); bounce /= -2f }
            custom.insertKeyFrame(1f, windows.foundation.numerics.Vector3(x, y, 0f))
        } else {
            val start = compositor.createCubicBezierEasingFunction(windows.foundation.numerics.Vector2(1f, 0f), windows.foundation.numerics.Vector2(1f, 0f))
            val step = compositor.createStepEasingFunction(1)
            val end = compositor.createCubicBezierEasingFunction(windows.foundation.numerics.Vector2(0f, 1f), windows.foundation.numerics.Vector2(0f, 1f))
            custom.insertKeyFrame(0.499999f, windows.foundation.numerics.Vector3(x, y - 0.9f * delta, 0f), start)
            custom.insertKeyFrame(0.5f, windows.foundation.numerics.Vector3(x, y - 0.1f * delta, 0f), step)
            custom.insertKeyFrame(1f, windows.foundation.numerics.Vector3(x, y, 0f), end)
        }
        custom.duration = nbAnimationDuration.value.milliseconds; args.animation = custom
    }
    private fun cmbVerticalAnimation_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { UpdateExample3Content() }
    private fun nbAnimationDuration_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) { UpdateExample3Content() }
    private fun UpdateExample3Content() {
        if (!ready) return
        val source = GalleryCodeCatalog.sampleDefinition("ScrollView\\ScrollViewProgrammaticScrollCustomAnimation.txt")?.kotlin ?: return
        Example3.Kotlin = source.source.replace("nbAnimationDuration.value", nbAnimationDuration.value.toString())
    }
}
