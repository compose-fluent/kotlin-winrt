package io.github.composefluent.winrt.ide.preview

import com.intellij.psi.xml.XmlTag

/** Windows KeySpline's unit-square control points, shared by UWP and WinUI.
 * SDK reference: https://learn.microsoft.com/windows/apps/develop/motion/key-frame-and-easing-function-animations
 * This describes the source curve only; the SDK continues to execute interpolation. */
internal data class DesignerSpline(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    init { require(listOf(x1, y1, x2, y2).all { it.isFinite() && it in 0f..1f }) { "Curve coordinates must be between 0 and 1." } }
    override fun toString() = "$x1,$y1 $x2,$y2"
    companion object {
        val Linear = DesignerSpline(0f, 0f, 1f, 1f)
        val presets = linkedMapOf("Linear" to Linear, "Ease in" to DesignerSpline(.42f, 0f, 1f, 1f),
            "Ease out" to DesignerSpline(0f, 0f, .58f, 1f), "Ease in/out" to DesignerSpline(.42f, 0f, .58f, 1f))
        fun parse(text: String): DesignerSpline? = runCatching {
            val parts = text.trim().split(Regex("[ ,]+"))
            require(parts.size == 4)
            val values = parts.map(String::toFloat)
            DesignerSpline(values[0], values[1], values[2], values[3])
        }.getOrNull()
        fun read(frame: XmlTag): DesignerSpline? {
            frame.getAttributeValue("KeySpline")?.let { return parse(it) }
            val property = frame.subTags.singleOrNull { it.namespace == frame.namespace && it.localName == "${frame.localName}.KeySpline" }
            val spline = property?.subTags?.singleOrNull { it.namespace == frame.namespace && it.localName == "KeySpline" }
            if (spline != null) return parse("${spline.getAttributeValue("ControlPoint1") ?: "0,0"} ${spline.getAttributeValue("ControlPoint2") ?: "1,1"}")
            return if (frame.localName.startsWith("Spline") || frame.localName.startsWith("Linear")) Linear else null
        }
    }
}
