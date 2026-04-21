package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

private val pointAbiLayout: MemoryLayout =
    MemoryLayout.structLayout(
        ValueLayout.JAVA_FLOAT.withName("x"),
        ValueLayout.JAVA_FLOAT.withName("y"),
    )

private val sizeAbiLayout: MemoryLayout =
    MemoryLayout.structLayout(
        ValueLayout.JAVA_FLOAT.withName("width"),
        ValueLayout.JAVA_FLOAT.withName("height"),
    )

private val rectAbiLayout: MemoryLayout =
    MemoryLayout.structLayout(
        ValueLayout.JAVA_FLOAT.withName("x"),
        ValueLayout.JAVA_FLOAT.withName("y"),
        ValueLayout.JAVA_FLOAT.withName("width"),
        ValueLayout.JAVA_FLOAT.withName("height"),
    )

val Point.Metadata.ABI_LAYOUT: MemoryLayout
    get() = pointAbiLayout

fun Point.Metadata.fromAbi(source: MemorySegment): Point = fromAbi(source.asNativePointer())

fun Point.Metadata.copyTo(
    value: Point,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Size.Metadata.ABI_LAYOUT: MemoryLayout
    get() = sizeAbiLayout

fun Size.Metadata.fromAbi(source: MemorySegment): Size = fromAbi(source.asNativePointer())

fun Size.Metadata.copyTo(
    value: Size,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Rect.Metadata.ABI_LAYOUT: MemoryLayout
    get() = rectAbiLayout

fun Rect.Metadata.fromAbi(source: MemorySegment): Rect = fromAbi(source.asNativePointer())

fun Rect.Metadata.copyTo(
    value: Rect,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}
