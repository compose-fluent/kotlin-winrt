// Generated port of MIT-licensed WinUI Gallery Assets/AnimatedVisuals/LottieLogo1.cs.
// Regenerate with tools/import-lottie-source.py <WinUI-Gallery checkout>.
package io.github.composefluent.winrt.gallery

import io.github.composefluent.winrt.runtime.WinRTOut
import io.github.composefluent.winrt.runtime.asWinRT
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import microsoft.graphics.canvas.CanvasDevice
import microsoft.graphics.canvas.ICanvasResourceCreator
import microsoft.graphics.canvas.geometry.*
import microsoft.ui.composition.*
import microsoft.ui.xaml.controls.IAnimatedVisual
import microsoft.ui.xaml.controls.IAnimatedVisualSource
import windows.foundation.numerics.*
import windows.ui.Color

class GalleryLottieSource : IAnimatedVisualSource {
    override fun tryCreateAnimatedVisual(compositor: Compositor?, diagnostics: WinRTOut<Any?>): IAnimatedVisual {
        diagnostics.value = null
        return GalleryLottieVisual(requireNotNull(compositor))
    }
}

internal class GalleryLottieVisual(private val _c: Compositor) : IAnimatedVisual {
    private val _device = requireNotNull(CanvasDevice.getSharedDevice()).asWinRT<ICanvasResourceCreator>()
    private val _reusableExpressionAnimation = requireNotNull(_c.createExpressionAnimation())
    private lateinit var _colorBrush_AlmostTeal_FF007A87: CompositionColorBrush
    private lateinit var _colorBrush_White: CompositionColorBrush
    private lateinit var _compositionPath_00: CompositionPath
    private lateinit var _compositionPath_01: CompositionPath
    private lateinit var _compositionPath_02: CompositionPath
    private lateinit var _compositionPath_03: CompositionPath
    private lateinit var _compositionPath_04: CompositionPath
    private lateinit var _compositionPath_05: CompositionPath
    private lateinit var _compositionPath_06: CompositionPath
    private lateinit var _compositionPath_07: CompositionPath
    private lateinit var _compositionPath_08: CompositionPath
    private lateinit var _cubicBezierEasingFunction_02: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_03: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_04: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_05: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_07: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_08: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_11: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_12: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_15: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_16: CubicBezierEasingFunction
    private lateinit var _cubicBezierEasingFunction_23: CubicBezierEasingFunction
    private lateinit var _ellipse_4p7: CompositionEllipseGeometry
    private lateinit var _holdThenStepEasingFunction: StepEasingFunction
    private lateinit var _linearEasingFunction: LinearEasingFunction
    private lateinit var _root: ContainerVisual
    private lateinit var _scalarAnimation_0_to_0p249: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_0_to_1_2: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_0p87_to_0_02: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_10: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_12: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_13: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_14: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_15: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_18: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_19: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_27: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_28: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_30: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_1_to_0_31: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_to_1_02: ScalarKeyFrameAnimation
    private lateinit var _scalarAnimation_to_1_06: ScalarKeyFrameAnimation
    private lateinit var _scalarExpressionAnimation: ExpressionAnimation
    private lateinit var _stepThenHoldEasingFunction: StepEasingFunction
    private lateinit var _vector2Animation_02: Vector2KeyFrameAnimation
    private lateinit var _vector2Animation_03: Vector2KeyFrameAnimation
    private lateinit var _vector2Animation_04: Vector2KeyFrameAnimation
    private lateinit var _vector2Animation_05: Vector2KeyFrameAnimation
    private lateinit var _vector2Animation_06: Vector2KeyFrameAnimation
    private lateinit var _vector2Animation_07: Vector2KeyFrameAnimation

    // Rectangle Path 1
    private fun ColorBrush_AlmostDarkTurquoise_FF00D1C1(): CompositionColorBrush
    {
        return _c.createColorBrush(Color(0xFFu, 0x00u, 0xD1u, 0xC1u))!!
    }

    private fun ColorBrush_AlmostTeal_FF007A87(): CompositionColorBrush
    {
        return _c.createColorBrush(Color(0xFFu, 0x00u, 0x7Au, 0x87u))!!.also { _colorBrush_AlmostTeal_FF007A87 = it }
    }

    private fun ColorBrush_White(): CompositionColorBrush
    {
        return _c.createColorBrush(Color(0xFFu, 0xFFu, 0xFFu, 0xFFu))!!.also { _colorBrush_White = it }
    }

    private fun CompositionPath_00(): CompositionPath
    {
        val result = CompositionPath(Geometry_00()).also { _compositionPath_00 = it }
        return result
    }

    private fun CompositionPath_01(): CompositionPath
    {
        val result = CompositionPath(Geometry_01()).also { _compositionPath_01 = it }
        return result
    }

    private fun CompositionPath_02(): CompositionPath
    {
        val result = CompositionPath(Geometry_02()).also { _compositionPath_02 = it }
        return result
    }

    private fun CompositionPath_03(): CompositionPath
    {
        val result = CompositionPath(Geometry_03()).also { _compositionPath_03 = it }
        return result
    }

    private fun CompositionPath_04(): CompositionPath
    {
        val result = CompositionPath(Geometry_04()).also { _compositionPath_04 = it }
        return result
    }

    private fun CompositionPath_05(): CompositionPath
    {
        val result = CompositionPath(Geometry_05()).also { _compositionPath_05 = it }
        return result
    }

    private fun CompositionPath_06(): CompositionPath
    {
        val result = CompositionPath(Geometry_06()).also { _compositionPath_06 = it }
        return result
    }

    private fun CompositionPath_07(): CompositionPath
    {
        val result = CompositionPath(Geometry_07()).also { _compositionPath_07 = it }
        return result
    }

    private fun CompositionPath_08(): CompositionPath
    {
        val result = CompositionPath(Geometry_08()).also { _compositionPath_08 = it }
        return result
    }

    // Layer (Shape): Dot-Y
    private fun ContainerShape_00(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_01())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_00())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): Dot-Y
    private fun ContainerShape_01(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_02())
        return result
    }

    // Transforms for Bncr
    private fun ContainerShape_02(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(164.781998f, 57.4729996f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_03())
        result.startAnimation("Position", Vector2Animation_01())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(60,60)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Transforms for Dot-Y
    private fun ContainerShape_03(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(43.2630005f, 59.75f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_01())
        result.startAnimation("Position", Vector2Animation_00())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", ScalarExpressionAnimation())
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(196.791,266.504)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E3-Y
    private fun ContainerShape_04(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_05())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_00())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E3-Y
    private fun ContainerShape_05(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_06())
        return result
    }

    // Transforms for E3-Y
    private fun ContainerShape_06(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(119.167f, 57.4790001f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_02())
        result.startAnimation("Position", Vector2Animation_02())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(345.124,261.801)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E3-B
    private fun ContainerShape_07(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_08())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_01())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E3-B
    private fun ContainerShape_08(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_09())
        return result
    }

    // Transforms for E3-Y
    private fun ContainerShape_09(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(119.167f, 57.4790001f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_03())
        result.startAnimation("Position", _vector2Animation_02)
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(345.124,261.801)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): I-Y
    private fun ContainerShape_10(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_11())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_01())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): I-Y
    private fun ContainerShape_11(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_12())
        return result
    }

    // Transforms for I-Y
    private fun ContainerShape_12(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(93.5940018f, 62.8610001f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_04())
        result.startAnimation("Position", Vector2Animation_03())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(303.802,282.182)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): I-B
    private fun ContainerShape_13(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_14())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_02())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): I-B
    private fun ContainerShape_14(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_15())
        return result
    }

    // Transforms for I-Y
    private fun ContainerShape_15(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(93.5940018f, 62.8610001f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_05())
        result.startAnimation("Position", _vector2Animation_03)
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(303.802,282.182)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E2-Y
    private fun ContainerShape_16(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_17())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_02())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E2-Y
    private fun ContainerShape_17(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_18())
        return result
    }

    // Transforms for E2-Y
    private fun ContainerShape_18(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(109.092003f, 33.6100006f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_06())
        result.startAnimation("Position", Vector2Animation_04())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(332.05,237.932)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E2-B
    private fun ContainerShape_19(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_20())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_03())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E2-B
    private fun ContainerShape_20(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_21())
        return result
    }

    // Transforms for E2-Y
    private fun ContainerShape_21(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(109.092003f, 33.6100006f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_07())
        result.startAnimation("Position", _vector2Animation_04)
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(332.05,237.932)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E1-Y
    private fun ContainerShape_22(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_23())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_03())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E1-Y
    private fun ContainerShape_23(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_24())
        return result
    }

    // Transforms for E1-Y
    private fun ContainerShape_24(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(113.714996f, 9.14599991f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_08())
        result.startAnimation("Position", Vector2Animation_05())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(344.672,214.842)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E1-B
    private fun ContainerShape_25(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_26())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_04())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): E1-B
    private fun ContainerShape_26(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_27())
        return result
    }

    // Transforms for E1-Y
    private fun ContainerShape_27(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(113.714996f, 9.14599991f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_09())
        result.startAnimation("Position", _vector2Animation_05)
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(344.672,214.842)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1a-Y
    private fun ContainerShape_28(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_29())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_04())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1a-Y
    private fun ContainerShape_29(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_30())
        return result
    }

    // Transforms for T1a-Y
    private fun ContainerShape_30(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(39.0429993f, 48.6780014f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_10())
        result.startAnimation("Position", Vector2Animation_06())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(250,250)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T2b-Y
    private fun ContainerShape_31(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_11())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_05())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T2a-Y
    private fun ContainerShape_32(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_12())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_06())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T2b-B
    private fun ContainerShape_33(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_13())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_05())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1b-Y
    private fun ContainerShape_34(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_14())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_07())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1b-B
    private fun ContainerShape_35(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_15())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_to_1_02)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): O-Y
    private fun ContainerShape_36(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_37())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_06())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): O-Y
    private fun ContainerShape_37(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_38())
        return result
    }

    // Transforms for O-Y
    private fun ContainerShape_38(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(-62.7919998f, 73.0569992f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_16())
        result.startAnimation("Position", Vector2Animation_08())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(196.791,266.504)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): O-B
    private fun ContainerShape_39(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_40())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_to_1_06)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): O-B
    private fun ContainerShape_40(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_41())
        return result
    }

    // Transforms for O-B
    private fun ContainerShape_41(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(-62.7919998f, 73.0569992f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_17())
        result.startAnimation("Position", Vector2Animation_09())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(196.791,266.504)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1a-Y 2
    private fun ContainerShape_42(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_43())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_07())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1a-Y 2
    private fun ContainerShape_43(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_44())
        return result
    }

    // Transforms for T1a-Y 2
    private fun ContainerShape_44(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(39.0429993f, 48.6780014f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_18())
        result.startAnimation("Position", _vector2Animation_06)
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(250,250)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T2a-B
    private fun ContainerShape_45(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_19())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_08())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1a-B
    private fun ContainerShape_46(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_47())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_09())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): T1a-B
    private fun ContainerShape_47(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_48())
        return result
    }

    // Transforms for T1a-Y
    private fun ContainerShape_48(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(39.0429993f, 48.6780014f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_20())
        result.startAnimation("Position", _vector2Animation_06)
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(250,250)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): Dot-Y
    private fun ContainerShape_49(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_50())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_08())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): Dot-Y
    private fun ContainerShape_50(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_51())
        return result
    }

    // Transforms for N
    private fun ContainerShape_51(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(-33.6669998f, 8.18200016f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_52())
        result.startAnimation("Position", Vector2Animation_11())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(60,60)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Transforms for Dot-Y
    private fun ContainerShape_52(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(39.875f, 60f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_21())
        result.startAnimation("Position", Vector2Animation_10())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(196.791,266.504)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): L-Y
    private fun ContainerShape_53(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_22())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_10())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): L-B
    private fun ContainerShape_54(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_23())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_1_11())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): Dot1
    private fun ContainerShape_55(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_56())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_to_0())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): Dot1
    private fun ContainerShape_56(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 154.457001f, 287.821991f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(ContainerShape_57())
        return result
    }

    // Transforms for Dot1
    private fun ContainerShape_57(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertVector2("Position", Vector2(295.770996f, 108.994003f))
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_24())
        result.startAnimation("Position", Vector2Animation_12())
        var controller = result.tryGetAnimationController("Position")!!
        controller.pause()
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "(_.Progress * 0.9835165) + 0.01648352"
        _reusableExpressionAnimation.setReferenceParameter("_", _root)
        controller.startAnimation("Progress", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.Position - Vector2(196.791,266.504)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("Offset", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S1-Y
    private fun ContainerShape_58(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_25())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_10())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S2-Y
    private fun ContainerShape_59(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_26())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_10)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S7
    private fun ContainerShape_60(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_27())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_13())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S8
    private fun ContainerShape_61(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_28())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_13)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S3-Y
    private fun ContainerShape_62(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_29())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_15())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S4-Y
    private fun ContainerShape_63(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_30())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_15)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S5-Y
    private fun ContainerShape_64(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_31())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_15)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S6-Y
    private fun ContainerShape_65(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_32())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_15)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S3-Y 2
    private fun ContainerShape_66(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_33())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_19())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S4-Y 2
    private fun ContainerShape_67(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_34())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_19)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S5-Y 2
    private fun ContainerShape_68(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_35())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_19)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S11
    private fun ContainerShape_69(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_36())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_22())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S12
    private fun ContainerShape_70(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_37())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_24())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S13
    private fun ContainerShape_71(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_38())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_26())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S3-Y 3
    private fun ContainerShape_72(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_39())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_28())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S4-Y 3
    private fun ContainerShape_73(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_40())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_28)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S5-Y 3
    private fun ContainerShape_74(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_41())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_28)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S3-Y 4
    private fun ContainerShape_75(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_42())
        result.startAnimation("TransformMatrix._11", ScalarAnimation_1_to_0_31())
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S4-Y 4
    private fun ContainerShape_76(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_43())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_31)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Layer (Shape): S5-Y 4
    private fun ContainerShape_77(): CompositionContainerShape
    {
        val result = _c.createContainerShape()!!
        result.transformMatrix = Matrix3x2(0f, 0f, 0f, 0f, 0f, 0f)
        val shapes = checkNotNull(result.shapes)
        shapes.add(SpriteShape_44())
        result.startAnimation("TransformMatrix._11", _scalarAnimation_1_to_0_31)
        var controller = result.tryGetAnimationController("TransformMatrix._11")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "my.TransformMatrix._11"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TransformMatrix._22", _reusableExpressionAnimation)
        return result
    }

    // Transforms: Dot-Y
    //   Position
    private fun CubicBezierEasingFunction_00(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0f, 0f), Vector2(0f, 0.811999977f))!!
    }

    // Transforms: Dot-Y
    //   Position
    private fun CubicBezierEasingFunction_01(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.389999986f, 0.707000017f), Vector2(0.708000004f, 1f))!!
    }

    private fun CubicBezierEasingFunction_02(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.180000007f, 1f))!!.also { _cubicBezierEasingFunction_02 = it }
    }

    private fun CubicBezierEasingFunction_03(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.819999993f, 0f), Vector2(0.833000004f, 0.833000004f))!!.also { _cubicBezierEasingFunction_03 = it }
    }

    private fun CubicBezierEasingFunction_04(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.833000004f, 0.833000004f))!!.also { _cubicBezierEasingFunction_04 = it }
    }

    private fun CubicBezierEasingFunction_05(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.666999996f, 1f))!!.also { _cubicBezierEasingFunction_05 = it }
    }

    // Position
    private fun CubicBezierEasingFunction_06(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0f), Vector2(0.666999996f, 1f))!!
    }

    private fun CubicBezierEasingFunction_07(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.119999997f, 1f))!!.also { _cubicBezierEasingFunction_07 = it }
    }

    private fun CubicBezierEasingFunction_08(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0f), Vector2(0.119999997f, 1f))!!.also { _cubicBezierEasingFunction_08 = it }
    }

    // Position
    private fun CubicBezierEasingFunction_09(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.119999997f, 0.119999997f))!!
    }

    // TStart
    private fun CubicBezierEasingFunction_10(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.300999999f, 0f), Vector2(0.833000004f, 1f))!!
    }

    private fun CubicBezierEasingFunction_11(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.300999999f, 0f), Vector2(0.666999996f, 1f))!!.also { _cubicBezierEasingFunction_11 = it }
    }

    private fun CubicBezierEasingFunction_12(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.0599999987f, 1f))!!.also { _cubicBezierEasingFunction_12 = it }
    }

    // Layer (Shape): T1b-B
    //   Path 1
    //     Path 1.pathGeometry
    //       TrimEnd
    private fun CubicBezierEasingFunction_13(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.209999993f, 1f))!!
    }

    // Radius
    private fun CubicBezierEasingFunction_14(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.333000004f, 0f), Vector2(0.666999996f, 1f))!!
    }

    private fun CubicBezierEasingFunction_15(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.180000007f, 0f), Vector2(0.34799999f, 1f))!!.also { _cubicBezierEasingFunction_15 = it }
    }

    private fun CubicBezierEasingFunction_16(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.693000019f, 0f), Vector2(0.270000011f, 1f))!!.also { _cubicBezierEasingFunction_16 = it }
    }

    // Transforms: O-B
    //   Ellipse Path 1
    //     Ellipse Path 1.ellipseGeometry
    //       TrimStart
    private fun CubicBezierEasingFunction_17(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 1f), Vector2(0.432000011f, 1f))!!
    }

    // Transforms: T1a-Y
    //   Path 1
    //     Path 1.pathGeometry
    //       TrimEnd
    private fun CubicBezierEasingFunction_18(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.672999978f, 1f))!!
    }

    // Transforms: N
    //   Position
    private fun CubicBezierEasingFunction_19(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.25999999f, 1f))!!
    }

    // Transforms: N
    //   Position
    private fun CubicBezierEasingFunction_20(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.74000001f, 0f), Vector2(0.833000004f, 0.833000004f))!!
    }

    // TStart
    private fun CubicBezierEasingFunction_21(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.703000009f, 0.856999993f))!!
    }

    // TStart
    private fun CubicBezierEasingFunction_22(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.333000004f, 0.202000007f), Vector2(0.938000023f, 1f))!!
    }

    private fun CubicBezierEasingFunction_23(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.337000012f, 1f))!!.also { _cubicBezierEasingFunction_23 = it }
    }

    // TStart
    private fun CubicBezierEasingFunction_24(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.166999996f), Vector2(0.703000009f, 0.82099998f))!!
    }

    // TStart
    private fun CubicBezierEasingFunction_25(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.0370000005f, 0.167999998f), Vector2(0.263000011f, 1f))!!
    }

    // Transforms: Dot1
    //   Position
    private fun CubicBezierEasingFunction_26(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.823000014f, 0f), Vector2(0.833000004f, 0.833000004f))!!
    }

    private fun CubicBezierEasingFunction_27(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.166999996f, 0.197999999f), Vector2(0.638000011f, 1f))!!
    }

    private fun CubicBezierEasingFunction_28(): CubicBezierEasingFunction
    {
        return _c.createCubicBezierEasingFunction(Vector2(0.523000002f, 0f), Vector2(0.795000017f, 1f))!!
    }

    // Transforms: O-Y
    //   Ellipse Path 1
    // Ellipse Path 1.ellipseGeometry
    private fun Ellipse_1p5_0(): CompositionEllipseGeometry
    {
        val result = _c.createEllipseGeometry()!!
        result.center = Vector2(0.800000012f, -0.5f)
        result.radius = Vector2(1.5f, 1.5f)
        result.startAnimation("Radius", Vector2Animation_07())
        var controller = result.tryGetAnimationController("Radius")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: O-B
    //   Ellipse Path 1
    // Ellipse Path 1.ellipseGeometry
    private fun Ellipse_1p5_1(): CompositionEllipseGeometry
    {
        val result = _c.createEllipseGeometry()!!
        result.center = Vector2(0.800000012f, -0.5f)
        result.radius = Vector2(1.5f, 1.5f)
        result.startAnimation("Radius", _vector2Animation_07)
        var controller = result.tryGetAnimationController("Radius")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TrimStart", ScalarAnimation_0_to_0p399())
        controller = result.tryGetAnimationController("TrimStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TrimEnd", ScalarAnimation_1_to_0p88())
        controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: Dot-Y
    //   Ellipse Path 1
    // Ellipse Path 1.ellipseGeometry
    private fun Ellipse_4p6(): CompositionEllipseGeometry
    {
        val result = _c.createEllipseGeometry()!!
        result.center = Vector2(0.800000012f, -0.5f)
        result.radius = Vector2(4.5999999f, 4.5999999f)
        return result
    }

    // Ellipse Path 1.ellipseGeometry
    private fun Ellipse_4p7(): CompositionEllipseGeometry
    {
        val result = _c.createEllipseGeometry()!!.also { _ellipse_4p7 = it }
        result.center = Vector2(0.800000012f, -0.5f)
        result.radius = Vector2(4.69999981f, 4.69999981f)
        return result
    }

    private fun Geometry_00(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-13.6639996f, -0.144999996f))
            builder.addLine(Vector2(75.663002f, 0.289999992f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_01(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(0.859000027f, -21.1429996f))
            builder.addLine(Vector2(-4.35900021f, 70.3919983f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_02(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-26.6700001f, -0.282999992f))
            builder.addLine(Vector2(99.1709976f, 0.0659999996f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_03(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-13.6639996f, -0.144999996f))
            builder.addLine(Vector2(62.1629982f, 0.289999992f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_04(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-30.7199993f, 63.7610016f))
            builder.addCubicBezier(Vector2(-30.6889992f, 63.1669998f), Vector2(-30.7889996f, 50.8470001f), Vector2(-30.7409992f, 45.1920013f))
            builder.addCubicBezier(Vector2(-30.6650009f, 36.2140007f), Vector2(-37.3429985f, 27.0739994f), Vector2(-37.3969994f, 27.0139999f))
            builder.addCubicBezier(Vector2(-38.5579987f, 25.7140007f), Vector2(-39.7519989f, 24.1469994f), Vector2(-40.6980019f, 22.6609993f))
            builder.addCubicBezier(Vector2(-46.637001f, 13.3339996f), Vector2(-47.8400002f, 0.933000028f), Vector2(-37.8730011f, -7.1170001f))
            builder.addCubicBezier(Vector2(-13.1960001f, -27.0459995f), Vector2(8.96000004f, 11.559f), Vector2(49.5060005f, 11.559f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_05(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(246.649994f, 213.813995f))
            builder.addLine(Vector2(340.955994f, 213.628006f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_06(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(1.68099999f, -29.9920006f))
            builder.addLine(Vector2(-1.68099999f, 29.9920006f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_07(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(1.76800001f, -25.9659996f))
            builder.addLine(Vector2(-1.76800001f, 25.9659996f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_08(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-8.83699989f, -58.2290001f))
            builder.addCubicBezier(Vector2(-8.83699989f, -58.2290001f), Vector2(-10.1630001f, 29.4950008f), Vector2(-35.8339996f, 33.6619987f))
            builder.addCubicBezier(Vector2(-44.0579987f, 34.9970016f), Vector2(-50.2319984f, 30.0499992f), Vector2(-51.6879997f, 23.1480007f))
            builder.addCubicBezier(Vector2(-53.144001f, 16.2450008f), Vector2(-49.6549988f, 9.15600014f), Vector2(-41.1739998f, 7.29300022f))
            builder.addCubicBezier(Vector2(-17.3570004f, 2.05999994f), Vector2(4.23500013f, 57.1879997f), Vector2(51.7970009f, 44.1780014f))
            builder.addCubicBezier(Vector2(51.9570007f, 44.1339989f), Vector2(52.6870003f, 43.8740005f), Vector2(53.1879997f, 43.7410011f))
            builder.addCubicBezier(Vector2(53.6889992f, 43.6080017f), Vector2(68.9710007f, 41.3569984f), Vector2(140.393997f, 43.6720009f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_09(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-67.125f, -112f))
            builder.addCubicBezier(Vector2(-67.125f, -112f), Vector2(-73.5579987f, -100.719002f), Vector2(-75.4580002f, -89.9509964f))
            builder.addCubicBezier(Vector2(-78.625f, -72f), Vector2(-79.375f, -58.25f), Vector2(-80.375f, -39.25f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_10(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-67.25f, -105.5f))
            builder.addCubicBezier(Vector2(-67.25f, -105.5f), Vector2(-70.4329987f, -94.9690018f), Vector2(-72.3330002f, -84.2009964f))
            builder.addCubicBezier(Vector2(-75.5f, -66.25f), Vector2(-75.5f, -56.75f), Vector2(-76.5f, -37.75f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_11(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(34.5f, -13.0500002f))
            builder.addCubicBezier(Vector2(7.5f, -14.5f), Vector2(-4f, -37f), Vector2(-35.0460014f, -35.5789986f))
            builder.addCubicBezier(Vector2(-61.4720001f, -34.3689995f), Vector2(-62.25f, -5.75f), Vector2(-62.25f, -5.75f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_12(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-3f, 35.9500008f))
            builder.addCubicBezier(Vector2(-3f, 35.9500008f), Vector2(-1.5f, 7.5f), Vector2(-1.352f, -6.75600004f))
            builder.addCubicBezier(Vector2(-9.90299988f, -15.0190001f), Vector2(-21.5699997f, -20.5790005f), Vector2(-32.0460014f, -20.5790005f))
            builder.addCubicBezier(Vector2(-53.5f, -20.5790005f), Vector2(-42.25f, 4.25f), Vector2(-42.25f, 4.25f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_13(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(16.2310009f, 39.0730019f))
            builder.addLine(Vector2(-32.769001f, 57.3650017f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_14(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(7.44999981f, 21.9500008f))
            builder.addLine(Vector2(-32.75f, 55.75f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_15(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-94.5f, 37.0730019f))
            builder.addLine(Vector2(-48.769001f, 55.3650017f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_16(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(-87.5f, 20.9500008f))
            builder.addLine(Vector2(-48.75f, 54.75f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_17(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(166.731003f, -7.92700005f))
            builder.addLine(Vector2(136.731003f, 7.11499977f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_18(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(156.449997f, -23.0499992f))
            builder.addLine(Vector2(132f, 2.75f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_19(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(169.5f, 18.073f))
            builder.addLine(Vector2(137.481003f, 11.3649998f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_20(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(119.5f, -45.0499992f))
            builder.addLine(Vector2(82.75f, -44.75f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_21(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(119.25f, -20.0499992f))
            builder.addLine(Vector2(63.5f, -20.5f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_22(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(128f, 3.6500001f))
            builder.addLine(Vector2(78.25f, 3.5f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_23(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(149.623993f, 8.24400043f))
            builder.addLine(Vector2(136.647995f, 10.1560001f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_24(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(144.429001f, -5.39699984f))
            builder.addLine(Vector2(132.274994f, 4.73099995f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_25(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(145.677002f, 22.2199993f))
            builder.addLine(Vector2(134.921997f, 14.7489996f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_26(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(147.699005f, 13.0249996f))
            builder.addLine(Vector2(133.195007f, 13.21f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_27(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(142.182999f, -5.11199999f))
            builder.addLine(Vector2(130.029007f, 5.01599979f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun Geometry_28(): CanvasGeometry
    {
        lateinit var result: CanvasGeometry
        CanvasPathBuilder(_device).use { builder ->
            builder.beginFigure(Vector2(142.037994f, 29.2779999f))
            builder.addLine(Vector2(131.281998f, 21.8069992f))
            builder.endFigure(CanvasFigureLoop.Open)
            result = CanvasGeometry.createPath(builder)!!
        }
        return result
    }

    private fun HoldThenStepEasingFunction(): StepEasingFunction
    {
        val result = _c.createStepEasingFunction()!!.also { _holdThenStepEasingFunction = it }
        result.isFinalStepSingleFrame  = true
        return result
    }

    private fun LinearEasingFunction(): LinearEasingFunction
    {
        return _c.createLinearEasingFunction()!!.also { _linearEasingFunction = it }
    }

    // Transforms: E3-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_00(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_00())!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p316_0())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: E3-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_01(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_00)!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p316_1())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: I-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_02(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_01())!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p457_0())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: I-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_03(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_01)!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p457_1())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: E2-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_04(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_02())!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p43_0())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: E2-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_05(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_02)!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p43_1())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: E1-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_06(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_03())!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p375_0())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: E1-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_07(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_03)!!
        result.trimEnd = 0f
        result.startAnimation("TrimEnd", ScalarAnimation_0_to_0p375_1())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_08(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_04())!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0f)
        propertySet.insertScalar("TEnd", 0f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0_to_0p249())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_0_to_1_0())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Layer (Shape): T2b-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_09(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_05())!!
        result.trimEnd = 0.411000013f
        result.trimStart = 0.289999992f
        result.startAnimation("TrimStart", ScalarAnimation_0p29_to_0_0())
        var controller = result.tryGetAnimationController("TrimStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TrimEnd", ScalarAnimation_0p411_to_0p665_0())
        controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Layer (Shape): T2a-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_10(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_06())!!
        result.trimEnd = 0.5f
        result.trimStart = 0.5f
        result.startAnimation("TrimStart", ScalarAnimation_0p5_to_0_0())
        var controller = result.tryGetAnimationController("TrimStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TrimEnd", ScalarAnimation_0p5_to_1_0())
        controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Layer (Shape): T2b-B
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_11(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_05)!!
        result.trimEnd = 0.411000013f
        result.trimStart = 0.289999992f
        result.startAnimation("TrimStart", ScalarAnimation_0p29_to_0_1())
        var controller = result.tryGetAnimationController("TrimStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TrimEnd", ScalarAnimation_0p411_to_0p665_1())
        controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Layer (Shape): T1b-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_12(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_07())!!
        result.trimEnd = 0.116999999f
        result.startAnimation("TrimEnd", ScalarAnimation_0p117_to_1_0())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Layer (Shape): T1b-B
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_13(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_07)!!
        result.trimEnd = 0.116999999f
        result.startAnimation("TrimEnd", ScalarAnimation_0p117_to_1_1())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_14(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_04)!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0f)
        propertySet.insertScalar("TEnd", 0f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", _scalarAnimation_0_to_0p249)
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_0_to_1_1())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Layer (Shape): T2a-B
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_15(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_06)!!
        result.trimEnd = 0.5f
        result.trimStart = 0.5f
        result.startAnimation("TrimStart", ScalarAnimation_0p5_to_0_1())
        var controller = result.tryGetAnimationController("TrimStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TrimEnd", ScalarAnimation_0p5_to_1_1())
        controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Transforms: T1a-Y
    //   Path 1
    // Path 1.pathGeometry
    private fun PathGeometry_16(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_04)!!
        result.trimEnd = 0.248999998f
        result.trimStart = 0.248999998f
        result.startAnimation("TrimEnd", ScalarAnimation_0p249_to_0p891())
        var controller = result.tryGetAnimationController("TrimEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_17(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath_08())!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.800000012f)
        propertySet.insertScalar("TEnd", 0.810000002f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p8_to_0())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_0p81_to_0p734_0())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_18(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(_compositionPath_08)!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.800000012f)
        propertySet.insertScalar("TEnd", 0.810000002f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p8_to_0p3())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_0p81_to_0p734_1())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_19(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_09()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_00())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_09())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_20(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_10()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_01())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_11())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_21(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_11()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_02())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_12())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_22(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_12()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", _scalarAnimation_0p87_to_0_02)
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", _scalarAnimation_1_to_0_12)
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_23(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_13()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_03())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_14())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_24(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_14()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_04())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", _scalarAnimation_1_to_0_14)
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_25(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_15()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_05())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_16())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_26(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_16()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_06())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_17())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_27(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_17()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_07())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_18())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_28(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_18()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_08())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", _scalarAnimation_1_to_0_18)
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_29(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_19()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_09())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_20())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_30(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_20()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_10())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_21())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_31(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_21()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_11())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_23())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_32(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_22()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_12())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_25())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_33(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_23()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_13())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_27())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_34(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_24()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_14())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", _scalarAnimation_1_to_0_27)
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_35(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_25()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_15())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_29())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_36(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_26()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_16())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_30())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_37(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_27()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_17())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", _scalarAnimation_1_to_0_30)
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Path 1.pathGeometry
    private fun PathGeometry_38(): CompositionPathGeometry
    {
        val result = _c.createPathGeometry(CompositionPath(Geometry_28()))!!
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("TStart", 0.870000005f)
        propertySet.insertScalar("TEnd", 1f)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Min(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimStart", _reusableExpressionAnimation)
        _reusableExpressionAnimation.clearAllParameters()
        _reusableExpressionAnimation.expression = "Max(my.TStart, my.TEnd)"
        _reusableExpressionAnimation.setReferenceParameter("my", result)
        result.startAnimation("TrimEnd", _reusableExpressionAnimation)
        result.startAnimation("TStart", ScalarAnimation_0p87_to_0_18())
        var controller = result.tryGetAnimationController("TStart")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("TEnd", ScalarAnimation_1_to_0_32())
        controller = result.tryGetAnimationController("TEnd")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // The root of the composition.
    private fun Root(): ContainerVisual
    {
        val result = _c.createContainerVisual()!!.also { _root = it }
        val propertySet = checkNotNull(result.properties)
        propertySet.insertScalar("Progress", 0f)
        propertySet.insertScalar("t0", 0f)
        propertySet.insertScalar("t1", 0f)
        val children = checkNotNull(result.children)
        children.insertAtTop(ShapeVisual())
        result.startAnimation("t0", ScalarAnimation_0_to_1_2())
        var controller = result.tryGetAnimationController("t0")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        result.startAnimation("t1", _scalarAnimation_0_to_1_2)
        controller = result.tryGetAnimationController("t1")!!
        controller.pause()
        controller.startAnimation("Progress", _scalarExpressionAnimation)
        return result
    }

    // Rectangle Path 1
    // Rectangle Path 1.rectangleGeometry
    private fun RoundedRectangle_375x667(): CompositionRoundedRectangleGeometry
    {
        val result = _c.createRoundedRectangleGeometry()!!
        result.cornerRadius = Vector2(9.99999997E-07f, 9.99999997E-07f)
        result.offset = Vector2(-187.5f, -333.5f)
        result.size = Vector2(375f, 667f)
        return result
    }

    // TStart
    private fun ScalarAnimation_0_to_0p249(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_0_to_0p249 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.391061455f, 0.248999998f, CubicBezierEasingFunction_10())
        return result
    }

    // Transforms: E3-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p316_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.469273746f, 0f, LinearEasingFunction())
        result.insertKeyFrame(0.513966501f, 0.316000015f, CubicBezierEasingFunction_04())
        return result
    }

    // Transforms: E3-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p316_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.513966501f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.541899443f, 0.316000015f, _cubicBezierEasingFunction_04)
        return result
    }

    // Transforms: E1-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p375_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.441340774f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.491620123f, 0.375f, _cubicBezierEasingFunction_07)
        return result
    }

    // Transforms: E1-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p375_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.469273746f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.519553065f, 0.375f, _cubicBezierEasingFunction_07)
        return result
    }

    // Transforms: O-B
    //   Ellipse Path 1
    //     Ellipse Path 1.ellipseGeometry
    // TrimStart
    private fun ScalarAnimation_0_to_0p399(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.351955295f, 0.300000012f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.508379877f, 0.398999989f, CubicBezierEasingFunction_17())
        return result
    }

    // Transforms: E2-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p43_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.463687152f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.513966501f, 0.430000007f, _cubicBezierEasingFunction_07)
        return result
    }

    // Transforms: E2-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p43_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.480446935f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.530726254f, 0.430000007f, _cubicBezierEasingFunction_07)
        return result
    }

    // Transforms: I-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p457_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.43575418f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.491620123f, 0.456999987f, CubicBezierEasingFunction_07())
        return result
    }

    // Transforms: I-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0_to_0p457_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.452513963f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.508379877f, 0.456999987f, _cubicBezierEasingFunction_07)
        return result
    }

    // TEnd
    private fun ScalarAnimation_0_to_1_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.413407832f, 1f, CubicBezierEasingFunction_11())
        return result
    }

    // TEnd
    private fun ScalarAnimation_0_to_1_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0f, _linearEasingFunction)
        result.insertKeyFrame(0.43575418f, 1f, _cubicBezierEasingFunction_11)
        return result
    }

    private fun ScalarAnimation_0_to_1_2(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_0_to_1_2 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.196966588f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.245809957f, 1f, CubicBezierEasingFunction_27())
        result.insertKeyFrame(0.245810062f, 0f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675886f, 1f, CubicBezierEasingFunction_28())
        return result
    }

    // Layer (Shape): T1b-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p117_to_1_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.116999999f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.391061455f, 0.116999999f, _linearEasingFunction)
        result.insertKeyFrame(0.418994427f, 1f, _cubicBezierEasingFunction_04)
        return result
    }

    // Layer (Shape): T1b-B
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p117_to_1_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.116999999f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.452513963f, 0.116999999f, _linearEasingFunction)
        result.insertKeyFrame(0.491620123f, 1f, CubicBezierEasingFunction_13())
        return result
    }

    // Transforms: T1a-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p249_to_0p891(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.248999998f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.391061455f, 0.248999998f, _linearEasingFunction)
        result.insertKeyFrame(0.469273746f, 0.890999973f, CubicBezierEasingFunction_18())
        return result
    }

    // Layer (Shape): T2b-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimStart
    private fun ScalarAnimation_0p29_to_0_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.289999992f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 0.289999992f, _linearEasingFunction)
        result.insertKeyFrame(0.47486034f, 0f, _cubicBezierEasingFunction_07)
        return result
    }

    // Layer (Shape): T2b-B
    //   Path 1
    //     Path 1.pathGeometry
    // TrimStart
    private fun ScalarAnimation_0p29_to_0_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.289999992f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.458100557f, 0.289999992f, _linearEasingFunction)
        result.insertKeyFrame(0.508379877f, 0f, _cubicBezierEasingFunction_07)
        return result
    }

    // Layer (Shape): T2b-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p411_to_0p665_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.411000013f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 0.411000013f, _linearEasingFunction)
        result.insertKeyFrame(0.47486034f, 0.665000021f, _cubicBezierEasingFunction_07)
        return result
    }

    // Layer (Shape): T2b-B
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p411_to_0p665_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.411000013f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.458100557f, 0.411000013f, _linearEasingFunction)
        result.insertKeyFrame(0.508379877f, 0.665000021f, _cubicBezierEasingFunction_07)
        return result
    }

    // Layer (Shape): T2a-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimStart
    private fun ScalarAnimation_0p5_to_0_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.5f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.402234644f, 0.5f, _linearEasingFunction)
        result.insertKeyFrame(0.458100557f, 0f, CubicBezierEasingFunction_12())
        return result
    }

    // Layer (Shape): T2a-B
    //   Path 1
    //     Path 1.pathGeometry
    // TrimStart
    private fun ScalarAnimation_0p5_to_0_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.5f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 0.5f, _linearEasingFunction)
        result.insertKeyFrame(0.47486034f, 0f, _cubicBezierEasingFunction_12)
        return result
    }

    // Layer (Shape): T2a-Y
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p5_to_1_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.5f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.402234644f, 0.5f, _linearEasingFunction)
        result.insertKeyFrame(0.458100557f, 1f, _cubicBezierEasingFunction_12)
        return result
    }

    // Layer (Shape): T2a-B
    //   Path 1
    //     Path 1.pathGeometry
    // TrimEnd
    private fun ScalarAnimation_0p5_to_1_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.5f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 0.5f, _linearEasingFunction)
        result.insertKeyFrame(0.47486034f, 1f, _cubicBezierEasingFunction_12)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p8_to_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.800000012f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.0893854722f, 0.800000012f, _linearEasingFunction)
        result.insertKeyFrame(0.111731842f, 0.5f, CubicBezierEasingFunction_21())
        result.insertKeyFrame(0.156424582f, 0f, CubicBezierEasingFunction_22())
        return result
    }

    // TStart
    private fun ScalarAnimation_0p8_to_0p3(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.800000012f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.100558661f, 0.800000012f, _linearEasingFunction)
        result.insertKeyFrame(0.128491625f, 0.5f, CubicBezierEasingFunction_24())
        result.insertKeyFrame(0.30726257f, 0.300000012f, CubicBezierEasingFunction_25())
        return result
    }

    // TEnd
    private fun ScalarAnimation_0p81_to_0p734_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.810000002f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.0893854722f, 0.810000002f, _linearEasingFunction)
        result.insertKeyFrame(0.150837988f, 0.734000027f, CubicBezierEasingFunction_23())
        return result
    }

    // TEnd
    private fun ScalarAnimation_0p81_to_0p734_1(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.810000002f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.100558661f, 0.810000002f, _linearEasingFunction)
        result.insertKeyFrame(0.162011176f, 0.734000027f, _cubicBezierEasingFunction_23)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_00(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.162011176f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.184357539f, 0.375330001f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.201117322f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_01(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.162011176f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.184357539f, 0.253329992f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.201117322f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_02(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_0p87_to_0_02 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.363128483f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.391061455f, 0.212329999f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.418994427f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_03(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.421330005f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_04(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.438329995f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_05(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.506330013f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_06(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.439330012f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_07(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.541899443f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.558659196f, 0.421330005f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.597765386f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_08(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.541899443f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.558659196f, 0.438329995f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.597765386f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_09(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.541899443f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.558659196f, 0.506330013f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.597765386f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_10(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.446927369f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.463687152f, 0.212329999f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.486033529f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_11(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.469273746f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.486033529f, 0.212329999f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.508379877f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_12(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.47486034f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.502793312f, 0.212329999f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.525139689f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_13(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.43575418f, 0.421330005f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.458100557f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_14(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.43575418f, 0.438329995f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.458100557f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_15(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.43575418f, 0.506330013f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.458100557f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_16(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.441340774f, 0.421330005f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.463687152f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_17(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.441340774f, 0.438329995f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.463687152f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TStart
    private fun ScalarAnimation_0p87_to_0_18(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 0.870000005f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 0.870000005f, _linearEasingFunction)
        result.insertKeyFrame(0.441340774f, 0.506330013f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.463687152f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // Layer (Shape): E3-Y
    private fun ScalarAnimation_1_to_0_00(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.469273746f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.569832385f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): I-Y
    private fun ScalarAnimation_1_to_0_01(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.43575418f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.519553065f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): E2-Y
    private fun ScalarAnimation_1_to_0_02(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.463687152f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.536312878f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): E1-Y
    private fun ScalarAnimation_1_to_0_03(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.441340774f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.525139689f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T1a-Y
    private fun ScalarAnimation_1_to_0_04(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.329608947f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.87150836f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T2b-Y
    private fun ScalarAnimation_1_to_0_05(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.424580991f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.513966501f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T2a-Y
    private fun ScalarAnimation_1_to_0_06(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.402234644f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.497206718f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T1b-Y
    private fun ScalarAnimation_1_to_0_07(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.391061455f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.899441361f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): Dot-Y
    private fun ScalarAnimation_1_to_0_08(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.156424582f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.301675975f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_09(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.162011176f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.184357539f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.201117322f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    private fun ScalarAnimation_1_to_0_10(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_10 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.167597771f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.206703916f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_11(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.162011176f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.184357539f, 0.690559983f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.201117322f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_12(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_12 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.363128483f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.391061455f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.418994427f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    private fun ScalarAnimation_1_to_0_13(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_13 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.363128483f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.418994427f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_14(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_14 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    private fun ScalarAnimation_1_to_0_15(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_15 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.301675975f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.357541889f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_16(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.758560002f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_17(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.318435758f, 0.704559982f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.357541889f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_18(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_18 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.541899443f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.558659196f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.597765386f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    private fun ScalarAnimation_1_to_0_19(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_19 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.541899443f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.597765386f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_20(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.541899443f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.558659196f, 0.758560002f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.597765386f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_21(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.446927369f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.463687152f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.486033529f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // Layer (Shape): S11
    private fun ScalarAnimation_1_to_0_22(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.446927369f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.502793312f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_23(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.469273746f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.486033529f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.508379877f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // Layer (Shape): S12
    private fun ScalarAnimation_1_to_0_24(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.469273746f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.525139689f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_25(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.47486034f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.502793312f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.525139689f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // Layer (Shape): S13
    private fun ScalarAnimation_1_to_0_26(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.47486034f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.530726254f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_27(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_27 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.43575418f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.458100557f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    private fun ScalarAnimation_1_to_0_28(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_28 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.418994427f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.463687152f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_29(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.418994427f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.43575418f, 0.758560002f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.458100557f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_30(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_30 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.441340774f, 0.663559973f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.463687152f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    private fun ScalarAnimation_1_to_0_31(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_1_to_0_31 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.424580991f, 1f, _holdThenStepEasingFunction)
        result.insertKeyFrame(0.469273746f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // TEnd
    private fun ScalarAnimation_1_to_0_32(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.424580991f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.441340774f, 0.758560002f, _cubicBezierEasingFunction_04)
        result.insertKeyFrame(0.463687152f, 0f, _cubicBezierEasingFunction_04)
        return result
    }

    // Transforms: O-B
    //   Ellipse Path 1
    //     Ellipse Path 1.ellipseGeometry
    // TrimEnd
    private fun ScalarAnimation_1_to_0p88(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, 1f, _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, 1f, _linearEasingFunction)
        result.insertKeyFrame(0.351955295f, 0.879999995f, _cubicBezierEasingFunction_04)
        return result
    }

    // Layer (Shape): Dot1
    private fun ScalarAnimation_to_0(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.0949720666f, 0f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): Dot-Y
    private fun ScalarAnimation_to_1_00(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.536312878f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): E3-B
    private fun ScalarAnimation_to_1_01(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.513966501f, 1f, _holdThenStepEasingFunction)
        return result
    }

    private fun ScalarAnimation_to_1_02(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_to_1_02 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.452513963f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): E2-B
    private fun ScalarAnimation_to_1_03(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.480446935f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): E1-B
    private fun ScalarAnimation_to_1_04(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.469273746f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T2b-B
    private fun ScalarAnimation_to_1_05(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.458100557f, 1f, _holdThenStepEasingFunction)
        return result
    }

    private fun ScalarAnimation_to_1_06(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!.also { _scalarAnimation_to_1_06 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.301675975f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T1a-Y 2
    private fun ScalarAnimation_to_1_07(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.329608947f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T2a-B
    private fun ScalarAnimation_to_1_08(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.418994427f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): T1a-B
    private fun ScalarAnimation_to_1_09(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.391061455f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): L-Y
    private fun ScalarAnimation_to_1_10(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.0893854722f, 1f, _holdThenStepEasingFunction)
        return result
    }

    // Layer (Shape): L-B
    private fun ScalarAnimation_to_1_11(): ScalarKeyFrameAnimation
    {
        val result = _c.createScalarKeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0.100558661f, 1f, _holdThenStepEasingFunction)
        return result
    }

    private fun ScalarExpressionAnimation(): ExpressionAnimation
    {
        val result = _c.createExpressionAnimation()!!.also { _scalarExpressionAnimation = it }
        result.setReferenceParameter("_", _root)
        result.expression = "_.Progress"
        return result
    }

    private fun ShapeVisual(): ShapeVisual
    {
        val result = _c.createShapeVisual()!!
        result.size = Vector2(375f, 667f)
        val shapes = checkNotNull(result.shapes)
        // Rectangle Path 1
        shapes.add(SpriteShape_00())
        // Layer (Shape): Dot-Y
        shapes.add(ContainerShape_00())
        // Layer (Shape): E3-Y
        shapes.add(ContainerShape_04())
        // Layer (Shape): E3-B
        shapes.add(ContainerShape_07())
        // Layer (Shape): I-Y
        shapes.add(ContainerShape_10())
        // Layer (Shape): I-B
        shapes.add(ContainerShape_13())
        // Layer (Shape): E2-Y
        shapes.add(ContainerShape_16())
        // Layer (Shape): E2-B
        shapes.add(ContainerShape_19())
        // Layer (Shape): E1-Y
        shapes.add(ContainerShape_22())
        // Layer (Shape): E1-B
        shapes.add(ContainerShape_25())
        // Layer (Shape): T1a-Y
        shapes.add(ContainerShape_28())
        // Layer (Shape): T2b-Y
        shapes.add(ContainerShape_31())
        // Layer (Shape): T2a-Y
        shapes.add(ContainerShape_32())
        // Layer (Shape): T2b-B
        shapes.add(ContainerShape_33())
        // Layer (Shape): T1b-Y
        shapes.add(ContainerShape_34())
        // Layer (Shape): T1b-B
        shapes.add(ContainerShape_35())
        // Layer (Shape): O-Y
        shapes.add(ContainerShape_36())
        // Layer (Shape): O-B
        shapes.add(ContainerShape_39())
        // Layer (Shape): T1a-Y 2
        shapes.add(ContainerShape_42())
        // Layer (Shape): T2a-B
        shapes.add(ContainerShape_45())
        // Layer (Shape): T1a-B
        shapes.add(ContainerShape_46())
        // Layer (Shape): Dot-Y
        shapes.add(ContainerShape_49())
        // Layer (Shape): L-Y
        shapes.add(ContainerShape_53())
        // Layer (Shape): L-B
        shapes.add(ContainerShape_54())
        // Layer (Shape): Dot1
        shapes.add(ContainerShape_55())
        // Layer (Shape): S1-Y
        shapes.add(ContainerShape_58())
        // Layer (Shape): S2-Y
        shapes.add(ContainerShape_59())
        // Layer (Shape): S7
        shapes.add(ContainerShape_60())
        // Layer (Shape): S8
        shapes.add(ContainerShape_61())
        // Layer (Shape): S3-Y
        shapes.add(ContainerShape_62())
        // Layer (Shape): S4-Y
        shapes.add(ContainerShape_63())
        // Layer (Shape): S5-Y
        shapes.add(ContainerShape_64())
        // Layer (Shape): S6-Y
        shapes.add(ContainerShape_65())
        // Layer (Shape): S3-Y 2
        shapes.add(ContainerShape_66())
        // Layer (Shape): S4-Y 2
        shapes.add(ContainerShape_67())
        // Layer (Shape): S5-Y 2
        shapes.add(ContainerShape_68())
        // Layer (Shape): S11
        shapes.add(ContainerShape_69())
        // Layer (Shape): S12
        shapes.add(ContainerShape_70())
        // Layer (Shape): S13
        shapes.add(ContainerShape_71())
        // Layer (Shape): S3-Y 3
        shapes.add(ContainerShape_72())
        // Layer (Shape): S4-Y 3
        shapes.add(ContainerShape_73())
        // Layer (Shape): S5-Y 3
        shapes.add(ContainerShape_74())
        // Layer (Shape): S3-Y 4
        shapes.add(ContainerShape_75())
        // Layer (Shape): S4-Y 4
        shapes.add(ContainerShape_76())
        // Layer (Shape): S5-Y 4
        shapes.add(ContainerShape_77())
        return result
    }

    // Rectangle Path 1
    private fun SpriteShape_00(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 187.5f, 333.5f)
        result.fillBrush = ColorBrush_AlmostDarkTurquoise_FF00D1C1()
        result.geometry = RoundedRectangle_375x667()
        return result
    }

    // Transforms: Dot-Y
    // Ellipse Path 1
    private fun SpriteShape_01(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 196f, 267f)
        result.fillBrush = ColorBrush_White()
        result.geometry = Ellipse_4p6()
        return result
    }

    // Transforms: E3-Y
    // Path 1
    private fun SpriteShape_02(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 344.674011f, 261.877014f)
        result.geometry = PathGeometry_00()
        result.strokeBrush = ColorBrush_AlmostTeal_FF007A87()
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.56200027f
        return result
    }

    // Transforms: E3-Y
    // Path 1
    private fun SpriteShape_03(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 344.739014f, 261.877014f)
        result.geometry = PathGeometry_01()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.56200027f
        return result
    }

    // Transforms: I-Y
    // Path 1
    private fun SpriteShape_04(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 304.13501f, 282.408997f)
        result.geometry = PathGeometry_02()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Square
        result.strokeEndCap = CompositionStrokeCap.Square
        result.strokeStartCap = CompositionStrokeCap.Square
        result.strokeMiterLimit = 10f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Transforms: I-Y
    // Path 1
    private fun SpriteShape_05(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 304.13501f, 282.408997f)
        result.geometry = PathGeometry_03()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Square
        result.strokeEndCap = CompositionStrokeCap.Square
        result.strokeStartCap = CompositionStrokeCap.Square
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Transforms: E2-Y
    // Path 1
    private fun SpriteShape_06(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 331.664001f, 238.139999f)
        result.geometry = PathGeometry_04()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Transforms: E2-Y
    // Path 1
    private fun SpriteShape_07(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 331.664001f, 238.139999f)
        result.geometry = PathGeometry_05()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.56200027f
        return result
    }

    // Transforms: E1-Y
    // Path 1
    private fun SpriteShape_08(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 344.671997f, 214.841995f)
        result.geometry = PathGeometry_06()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Transforms: E1-Y
    // Path 1
    private fun SpriteShape_09(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 344.671997f, 214.841995f)
        result.geometry = PathGeometry_07()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.56200027f
        return result
    }

    // Transforms: T1a-Y
    // Path 1
    private fun SpriteShape_10(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 227.677002f, 234.375f)
        result.geometry = PathGeometry_08()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Layer (Shape): T2b-Y
    // Path 1
    private fun SpriteShape_11(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, -56.5f, 83.5f)
        result.geometry = PathGeometry_09()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Layer (Shape): T2a-Y
    // Path 1
    private fun SpriteShape_12(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 221.197998f, 330.757996f)
        result.geometry = PathGeometry_10()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Square
        result.strokeEndCap = CompositionStrokeCap.Square
        result.strokeStartCap = CompositionStrokeCap.Square
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Layer (Shape): T2b-B
    // Path 1
    private fun SpriteShape_13(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, -56.5f, 83.5f)
        result.geometry = PathGeometry_11()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Layer (Shape): T1b-Y
    // Path 1
    private fun SpriteShape_14(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 186.255997f, 349.080994f)
        result.geometry = PathGeometry_12()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeLineJoin = CompositionStrokeLineJoin.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Layer (Shape): T1b-B
    // Path 1
    private fun SpriteShape_15(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 186.255997f, 349.080994f)
        result.geometry = PathGeometry_13()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeLineJoin = CompositionStrokeLineJoin.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Transforms: O-Y
    // Ellipse Path 1
    private fun SpriteShape_16(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 196f, 267f)
        result.geometry = Ellipse_1p5_0()
        result.strokeBrush = _colorBrush_White
        result.strokeMiterLimit = 4f
        result.strokeThickness = 8.80000019f
        return result
    }

    // Transforms: O-B
    // Ellipse Path 1
    private fun SpriteShape_17(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 196f, 267f)
        result.geometry = Ellipse_1p5_1()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Transforms: T1a-Y 2
    // Path 1
    private fun SpriteShape_18(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 227.677002f, 234.375f)
        result.geometry = PathGeometry_14()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Layer (Shape): T2a-B
    // Path 1
    private fun SpriteShape_19(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 221.197998f, 330.757996f)
        result.geometry = PathGeometry_15()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Square
        result.strokeEndCap = CompositionStrokeCap.Square
        result.strokeStartCap = CompositionStrokeCap.Square
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Transforms: T1a-Y
    // Path 1
    private fun SpriteShape_20(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 227.677002f, 234.375f)
        result.geometry = PathGeometry_16()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Transforms: Dot-Y
    // Ellipse Path 1
    private fun SpriteShape_21(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 196f, 267f)
        result.fillBrush = _colorBrush_White
        result.geometry = Ellipse_4p7()
        return result
    }

    // Layer (Shape): L-Y
    // Path 1
    private fun SpriteShape_22(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 109.529007f, 354.143005f)
        result.geometry = PathGeometry_17()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 8.39999962f
        return result
    }

    // Layer (Shape): L-B
    // Path 1
    private fun SpriteShape_23(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 109.529007f, 354.143005f)
        result.geometry = PathGeometry_18()
        result.strokeBrush = _colorBrush_AlmostTeal_FF007A87
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 10f
        result.strokeThickness = 9.19400024f
        return result
    }

    // Transforms: Dot1
    // Ellipse Path 1
    private fun SpriteShape_24(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 196f, 267f)
        result.fillBrush = _colorBrush_White
        result.geometry = _ellipse_4p7
        return result
    }

    // Layer (Shape): S1-Y
    // Path 1
    private fun SpriteShape_25(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_19()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S2-Y
    // Path 1
    private fun SpriteShape_26(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_20()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S7
    // Path 1
    private fun SpriteShape_27(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_21()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S8
    // Path 1
    private fun SpriteShape_28(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_22()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S3-Y
    // Path 1
    private fun SpriteShape_29(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_23()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S4-Y
    // Path 1
    private fun SpriteShape_30(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_24()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S5-Y
    // Path 1
    private fun SpriteShape_31(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_25()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S6-Y
    // Path 1
    private fun SpriteShape_32(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_26()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S3-Y 2
    // Path 1
    private fun SpriteShape_33(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_27()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S4-Y 2
    // Path 1
    private fun SpriteShape_34(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_28()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S5-Y 2
    // Path 1
    private fun SpriteShape_35(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_29()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S11
    // Path 1
    private fun SpriteShape_36(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_30()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S12
    // Path 1
    private fun SpriteShape_37(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_31()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S13
    // Path 1
    private fun SpriteShape_38(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(1f, 0f, 0f, 1f, 179.5f, 333.5f)
        result.geometry = PathGeometry_32()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 1.5f
        return result
    }

    // Layer (Shape): S3-Y 3
    // Path 1
    private fun SpriteShape_39(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(-0.137444615f, 0.99050945f, -0.99050945f, -0.137444615f, 212.662003f, 248.427994f)
        result.geometry = PathGeometry_33()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S4-Y 3
    // Path 1
    private fun SpriteShape_40(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(-0.137444615f, 0.99050945f, -0.99050945f, -0.137444615f, 212.662003f, 248.427994f)
        result.geometry = PathGeometry_34()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S5-Y 3
    // Path 1
    private fun SpriteShape_41(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(-0.137444615f, 0.99050945f, -0.99050945f, -0.137444615f, 212.662003f, 248.427994f)
        result.geometry = PathGeometry_35()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S3-Y 4
    // Path 1
    private fun SpriteShape_42(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(0.0157073997f, -0.999876618f, 0.999876618f, 0.0157073997f, 207.662003f, 419.427979f)
        result.geometry = PathGeometry_36()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S4-Y 4
    // Path 1
    private fun SpriteShape_43(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(0.0157073997f, -0.999876618f, 0.999876618f, 0.0157073997f, 207.662003f, 419.427979f)
        result.geometry = PathGeometry_37()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    // Layer (Shape): S5-Y 4
    // Path 1
    private fun SpriteShape_44(): CompositionSpriteShape
    {
        val result = _c.createSpriteShape()!!
        result.transformMatrix = Matrix3x2(0.0157073997f, -0.999876618f, 0.999876618f, 0.0157073997f, 207.662003f, 419.427979f)
        result.geometry = PathGeometry_38()
        result.strokeBrush = _colorBrush_White
        result.strokeDashCap = CompositionStrokeCap.Round
        result.strokeEndCap = CompositionStrokeCap.Round
        result.strokeStartCap = CompositionStrokeCap.Round
        result.strokeMiterLimit = 4f
        result.strokeThickness = 2f
        return result
    }

    private fun StepThenHoldEasingFunction(): StepEasingFunction
    {
        val result = _c.createStepEasingFunction()!!.also { _stepThenHoldEasingFunction = it }
        result.isInitialStepSingleFrame  = true
        return result
    }

    // Transforms: Dot-Y
    // Position
    private fun Vector2Animation_00(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(43.2630005f, 59.75f), StepThenHoldEasingFunction())
        result.insertKeyFrame(0.536312878f, Vector2(43.2630005f, 59.75f), HoldThenStepEasingFunction())
        result.insertKeyFrame(0.603351951f, Vector2(62.5130005f, 59.75f), CubicBezierEasingFunction_00())
        result.insertKeyFrame(0.642458081f, Vector2(63.7630005f, 59.75f), CubicBezierEasingFunction_01())
        return result
    }

    // Transforms: Bncr
    // Position
    private fun Vector2Animation_01(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(164.781998f, 57.4729996f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.536312878f, Vector2(164.781998f, 57.4729996f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.553072631f, Vector2(164.781998f, 55.4729996f), CubicBezierEasingFunction_02())
        result.insertKeyFrame(0.569832385f, Vector2(164.781998f, 57.4729996f), CubicBezierEasingFunction_03())
        result.insertKeyFrame(0.586592197f, Vector2(164.781998f, 56.9090004f), _cubicBezierEasingFunction_02)
        result.insertKeyFrame(0.603351951f, Vector2(164.781998f, 57.4729996f), _cubicBezierEasingFunction_03)
        return result
    }

    // Position
    private fun Vector2Animation_02(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!.also { _vector2Animation_02 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(119.167f, 57.4790001f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.469273746f, Vector2(119.167f, 57.4790001f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.513966501f, Vector2(137.167007f, 57.4790001f), CubicBezierEasingFunction_05())
        result.insertKeyFrame(0.536312878f, Vector2(134.167007f, 57.4790001f), CubicBezierEasingFunction_06())
        return result
    }

    // Position
    private fun Vector2Animation_03(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!.also { _vector2Animation_03 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(93.5940018f, 62.8610001f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.43575418f, Vector2(93.5940018f, 62.8610001f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.491620123f, Vector2(92.6259995f, 82.8290024f), _cubicBezierEasingFunction_07)
        result.insertKeyFrame(0.513966501f, Vector2(92.8440018f, 77.8610001f), CubicBezierEasingFunction_08())
        return result
    }

    // Position
    private fun Vector2Animation_04(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!.also { _vector2Animation_04 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(109.092003f, 33.6100006f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.463687152f, Vector2(109.092003f, 33.6100006f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.513966501f, Vector2(121.092003f, 33.6100006f), _cubicBezierEasingFunction_07)
        result.insertKeyFrame(0.536312878f, Vector2(121.092003f, 33.6100006f), CubicBezierEasingFunction_09())
        return result
    }

    // Position
    private fun Vector2Animation_05(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!.also { _vector2Animation_05 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(113.714996f, 9.14599991f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.441340774f, Vector2(113.714996f, 9.14599991f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.491620123f, Vector2(137.714996f, 9.14599991f), _cubicBezierEasingFunction_07)
        result.insertKeyFrame(0.513966501f, Vector2(133.714996f, 9.14599991f), _cubicBezierEasingFunction_08)
        return result
    }

    // Position
    private fun Vector2Animation_06(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!.also { _vector2Animation_06 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(39.0429993f, 48.6780014f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.312849164f, Vector2(39.0429993f, 48.6780014f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.357541889f, Vector2(39.0429993f, 45.6780014f), _cubicBezierEasingFunction_05)
        return result
    }

    // Radius
    private fun Vector2Animation_07(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!.also { _vector2Animation_07 = it }
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(1.5f, 1.5f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, Vector2(1.5f, 1.5f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.340782136f, Vector2(22.2999992f, 22.2999992f), CubicBezierEasingFunction_14())
        return result
    }

    // Transforms: O-Y
    // Position
    private fun Vector2Animation_08(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.setReferenceParameter("_", _root)
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(-62.7919998f, 73.0569992f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.17318435f, Vector2(-62.7919998f, 73.0569992f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.196966484f, Vector2(-53.7919998f, 7.55700016f), _cubicBezierEasingFunction_04)
        result.insertExpressionKeyFrame(0.245809957f, "(Pow(1 - _.t0, 3) * Vector2((-53.792),7.557)) + (3 * Square(1 - _.t0) * _.t0 * Vector2((-53.792),7.557)) + (3 * (1 - _.t0) * Square(_.t0) * Vector2((-52.82329),(-71.07968))) + (Pow(_.t0, 3) * Vector2((-33.667),(-72.818)))", _stepThenHoldEasingFunction)
        result.insertExpressionKeyFrame(0.301675886f, "(Pow(1 - _.t0, 3) * Vector2((-33.667),(-72.818))) + (3 * Square(1 - _.t0) * _.t0 * Vector2((-17.45947),(-74.28873))) + (3 * (1 - _.t0) * Square(_.t0) * Vector2((-14.167),102.182)) + (Pow(_.t0, 3) * Vector2((-14.167),102.182))", _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, Vector2(-14.1669998f, 102.181999f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.351955295f, Vector2(-14.1669998f, 59.1819992f), CubicBezierEasingFunction_15())
        result.insertKeyFrame(0.407821238f, Vector2(-14.1669998f, 62.1819992f), CubicBezierEasingFunction_16())
        return result
    }

    // Transforms: O-B
    // Position
    private fun Vector2Animation_09(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.setReferenceParameter("_", _root)
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(-62.7919998f, 73.0569992f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.17318435f, Vector2(-62.7919998f, 73.0569992f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.196966484f, Vector2(-53.7919998f, 7.55700016f), _cubicBezierEasingFunction_04)
        result.insertExpressionKeyFrame(0.245809957f, "(Pow(1 - _.t1, 3) * Vector2((-53.792),7.557)) + (3 * Square(1 - _.t1) * _.t1 * Vector2((-53.792),7.557)) + (3 * (1 - _.t1) * Square(_.t1) * Vector2((-52.82329),(-71.07968))) + (Pow(_.t1, 3) * Vector2((-33.667),(-72.818)))", _stepThenHoldEasingFunction)
        result.insertExpressionKeyFrame(0.301675886f, "(Pow(1 - _.t1, 3) * Vector2((-33.667),(-72.818))) + (3 * Square(1 - _.t1) * _.t1 * Vector2((-17.45947),(-74.28873))) + (3 * (1 - _.t1) * Square(_.t1) * Vector2((-14.167),102.182)) + (Pow(_.t1, 3) * Vector2((-14.167),102.182))", _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.301675975f, Vector2(-14.1669998f, 102.181999f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.351955295f, Vector2(-14.1669998f, 59.1819992f), _cubicBezierEasingFunction_15)
        result.insertKeyFrame(0.407821238f, Vector2(-14.1669998f, 62.1819992f), _cubicBezierEasingFunction_16)
        return result
    }

    // Transforms: Dot-Y
    // Position
    private fun Vector2Animation_10(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(39.875f, 60f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.156424582f, Vector2(39.875f, 60f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.301675975f, Vector2(79.375f, 60f), _cubicBezierEasingFunction_04)
        return result
    }

    // Transforms: N
    // Position
    private fun Vector2Animation_11(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(-33.6669998f, 8.18200016f), _stepThenHoldEasingFunction)
        result.insertKeyFrame(0.156424582f, Vector2(-33.6669998f, 8.18200016f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.223463684f, Vector2(-33.6669998f, -72.8180008f), CubicBezierEasingFunction_19())
        result.insertKeyFrame(0.301675975f, Vector2(-33.6669998f, 102.056999f), CubicBezierEasingFunction_20())
        return result
    }

    // Transforms: Dot1
    // Position
    private fun Vector2Animation_12(): Vector2KeyFrameAnimation
    {
        val result = _c.createVector2KeyFrameAnimation()!!
        result.duration = 5_967_000_000L.nanoseconds
        result.insertKeyFrame(0f, Vector2(295.770996f, 108.994003f), _holdThenStepEasingFunction)
        result.insertKeyFrame(0.104395606f, Vector2(35.7709999f, 108.994003f), CubicBezierEasingFunction_26())
        return result
    }


    init { Root() }
    override val rootVisual: Visual get() = _root
    override val duration: Duration get() = 5_967_000_000L.nanoseconds
    override val size: Vector2 get() = Vector2(375f, 667f)
    override fun close() { _root.close(); _reusableExpressionAnimation.close() }
}
