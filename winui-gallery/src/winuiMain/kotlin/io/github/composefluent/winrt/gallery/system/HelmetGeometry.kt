// Original procedural geometry; the existing Gallery model is retained.
package io.github.composefluent.winrt.gallery.system

import io.github.composefluent.winrt.gallery.rgb
import kotlin.time.Duration.Companion.seconds
import microsoft.ui.composition.*
import windows.foundation.numerics.*

internal fun proceduralHelmet(compositor: Compositor): ContainerVisual {
    val root = compositor.createContainerVisual().apply { size = Vector2(400f, 400f) }
    val helmet = compositor.createContainerVisual().apply {
        size = Vector2(280f, 300f); offset = Vector3(60f, 50f, 0f); centerPoint = Vector3(140f, 150f, 0f); rotationAxis = Vector3(0f, 1f, 0f)
    }
    fun plate(x: Float, y: Float, width: Float, height: Float, radius: Float, color: UInt, z: Float) {
        val shape = compositor.createRoundedRectangleGeometry().apply { size = Vector2(width, height); cornerRadius = Vector2(radius, radius) }
        checkNotNull(helmet.children).insertAtTop(compositor.createShapeVisual().apply {
            size = Vector2(width, height); offset = Vector3(x, y, z)
            checkNotNull(shapes).add(compositor.createSpriteShape(shape).apply { fillBrush = compositor.createColorBrush(rgb(color)) })
        })
    }
    // Layered shell gives the original procedural model depth under rotation.
    for (layer in -15..15) {
        val depth = layer.toFloat() * 2f
        val inset = (layer * layer).toFloat() / 15f
        val shade = (92 + layer * 2).coerceIn(0, 255).toUInt()
        plate(30f + inset, 18f + inset / 2, 220f - inset * 2, 264f - inset, 76f, (shade shl 16) or ((shade + 8u) shl 8) or (shade + 14u), depth)
    }
    plate(47f, 80f, 186f, 100f, 32f, 0x172A35u, 33f)
    plate(55f, 87f, 170f, 79f, 27f, 0x46758Au, 35f)
    plate(63f, 92f, 145f, 18f, 9f, 0x91BBC6u, 36f)
    plate(85f, 187f, 110f, 65f, 18f, 0x343D43u, 34f)
    repeat(4) { index -> plate(97f, 198f + index * 11, 86f, 5f, 2f, 0x172229u, 36f) }
    plate(18f, 110f, 35f, 74f, 15f, 0x323C44u, 3f)
    plate(227f, 110f, 35f, 74f, 15f, 0x323C44u, 3f)
    checkNotNull(root.children).insertAtTop(helmet)
    helmet.startAnimation("RotationAngleInDegrees", compositor.createScalarKeyFrameAnimation().apply {
        insertKeyFrame(0f, 0f); insertKeyFrame(0.5f, 360f); insertKeyFrame(1f, 0f); duration = 15.seconds; iterationBehavior = AnimationIterationBehavior.Forever
    })
    return root
}
