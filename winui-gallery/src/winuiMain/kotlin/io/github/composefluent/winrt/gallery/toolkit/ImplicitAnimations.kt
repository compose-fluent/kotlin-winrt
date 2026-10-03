// Home's show/hide timelines port CommunityToolkit/Windows components/Animations/src/Xaml (MIT).
package io.github.composefluent.winrt.gallery.toolkit
import io.github.composefluent.winrt.runtime.WinRTXamlContentProperty
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.hosting.ElementCompositionPreview
import microsoft.ui.xaml.media.animation.EasingMode
import microsoft.ui.composition.*
import windows.foundation.numerics.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal abstract class ImplicitTimeline : DependencyObject() {
    var From: String = "0"
    var To: String = "0"
    var Duration: Duration = 1.seconds
    var EasingMode: EasingMode = microsoft.ui.xaml.media.animation.EasingMode.EaseInOut
    protected fun easing(compositor: Compositor): CompositionEasingFunction = when (EasingMode) {
        microsoft.ui.xaml.media.animation.EasingMode.EaseIn -> compositor.createCubicBezierEasingFunction(Vector2(0.7f,0f),Vector2(1f,0.5f))
        microsoft.ui.xaml.media.animation.EasingMode.EaseOut -> compositor.createCubicBezierEasingFunction(Vector2(0f,0.5f),Vector2(0.3f,1f))
        else -> compositor.createCubicBezierEasingFunction(Vector2(0.7f,0f),Vector2(0.3f,1f))
    }
    abstract fun create(compositor: Compositor): CompositionAnimation
}
internal class OffsetAnimation : ImplicitTimeline() {
    private fun vector(text: String): Vector3 {
        val values = text.split(',').map { it.trim().toFloat() }
        return if (values.size == 1) Vector3(values[0],values[0],values[0]) else Vector3(values[0],values[1],values[2])
    }
    override fun create(compositor: Compositor): CompositionAnimation = compositor.createVector3KeyFrameAnimation().apply {
        target = "Offset"; duration = Duration
        val curve = easing(compositor)
        insertKeyFrame(0f,vector(From)); insertKeyFrame(1f,vector(To),curve); curve.close()
    }
}
internal class OpacityAnimation : ImplicitTimeline() {
    override fun create(compositor: Compositor): CompositionAnimation = compositor.createScalarKeyFrameAnimation().apply {
        target = "Opacity"; duration = Duration
        val curve = easing(compositor)
        insertKeyFrame(0f,From.toFloat()); insertKeyFrame(1f,To.toFloat(),curve); curve.close()
    }
}
@WinRTXamlContentProperty("Animations")
internal class ImplicitAnimationSet : DependencyObject() {
    val Animations: MutableList<ImplicitTimeline> = mutableListOf()
    fun create(element: UIElement): CompositionAnimationGroup {
        val compositor = checkNotNull(ElementCompositionPreview.getElementVisual(element).compositor)
        return compositor.createAnimationGroup().apply { Animations.forEach { timeline -> timeline.create(compositor).let { add(it); it.close() } } }
    }
}
internal object Implicit {
    val ShowAnimationsProperty: DependencyProperty = DependencyProperty.registerAttached("ShowAnimations",ImplicitAnimationSet::class,Implicit::class,
        PropertyMetadata(null,PropertyChangedCallback { sender,args ->
            val element = checkNotNull(sender).asWinRT<UIElement>()
            (args.newValue as? ImplicitAnimationSet)?.create(element)?.let { ElementCompositionPreview.setImplicitShowAnimation(element,it); it.close() }
        }))
    val HideAnimationsProperty: DependencyProperty = DependencyProperty.registerAttached("HideAnimations",ImplicitAnimationSet::class,Implicit::class,
        PropertyMetadata(null,PropertyChangedCallback { sender,args ->
            val element = checkNotNull(sender).asWinRT<UIElement>()
            (args.newValue as? ImplicitAnimationSet)?.create(element)?.let { ElementCompositionPreview.setImplicitHideAnimation(element,it); it.close() }
        }))
    fun GetShowAnimations(element: UIElement): ImplicitAnimationSet? = element.getValue(ShowAnimationsProperty) as? ImplicitAnimationSet
    fun SetShowAnimations(element: UIElement,value: ImplicitAnimationSet?) { element.setValue(ShowAnimationsProperty,value) }
    fun GetHideAnimations(element: UIElement): ImplicitAnimationSet? = element.getValue(HideAnimationsProperty) as? ImplicitAnimationSet
    fun SetHideAnimations(element: UIElement,value: ImplicitAnimationSet?) { element.setValue(HideAnimationsProperty,value) }
}
