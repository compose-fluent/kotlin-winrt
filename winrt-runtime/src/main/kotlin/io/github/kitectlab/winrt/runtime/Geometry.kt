package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

@WindowsRuntimeType("struct(Windows.Foundation.Point;f4;f4)")
data class Point(
    val x: Float,
    val y: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("x"),
            ValueLayout.JAVA_FLOAT.withName("y"),
        )

        fun fromAbi(source: MemorySegment): Point =
            Point(
                x = source.get(ValueLayout.JAVA_FLOAT, 0),
                y = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
            )

        fun copyTo(value: Point, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.x)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.y)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Size;f4;f4)")
data class Size(
    val width: Float,
    val height: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("width"),
            ValueLayout.JAVA_FLOAT.withName("height"),
        )

        fun fromAbi(source: MemorySegment): Size =
            Size(
                width = source.get(ValueLayout.JAVA_FLOAT, 0),
                height = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
            )

        fun copyTo(value: Size, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.width)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.height)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Rect;f4;f4;f4;f4)")
data class Rect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("x"),
            ValueLayout.JAVA_FLOAT.withName("y"),
            ValueLayout.JAVA_FLOAT.withName("width"),
            ValueLayout.JAVA_FLOAT.withName("height"),
        )

        fun fromAbi(source: MemorySegment): Rect =
            Rect(
                x = source.get(ValueLayout.JAVA_FLOAT, 0),
                y = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
                width = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2),
                height = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3),
            )

        fun copyTo(value: Rect, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.x)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.y)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2, value.width)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3, value.height)
        }
    }
}
