package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

private fun floatStructLayout(vararg names: String): MemoryLayout =
    MemoryLayout.structLayout(*names.map { ValueLayout.JAVA_FLOAT.withName(it) }.toTypedArray())

private val vector2AbiLayout = floatStructLayout("x", "y")
private val vector3AbiLayout = floatStructLayout("x", "y", "z")
private val vector4AbiLayout = floatStructLayout("x", "y", "z", "w")
private val matrix3x2AbiLayout = floatStructLayout("m11", "m12", "m21", "m22", "m31", "m32")
private val matrix4x4AbiLayout =
    floatStructLayout(
        "m11",
        "m12",
        "m13",
        "m14",
        "m21",
        "m22",
        "m23",
        "m24",
        "m31",
        "m32",
        "m33",
        "m34",
        "m41",
        "m42",
        "m43",
        "m44",
    )
private val planeAbiLayout =
    MemoryLayout.structLayout(
        vector3AbiLayout.withName("normal"),
        ValueLayout.JAVA_FLOAT.withName("d"),
    )

val Vector2.Metadata.ABI_LAYOUT: MemoryLayout
    get() = vector2AbiLayout

fun Vector2.Metadata.fromAbi(source: MemorySegment): Vector2 = fromAbi(source.asNativePointer())

fun Vector2.Metadata.copyTo(
    value: Vector2,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Vector3.Metadata.ABI_LAYOUT: MemoryLayout
    get() = vector3AbiLayout

fun Vector3.Metadata.fromAbi(source: MemorySegment): Vector3 = fromAbi(source.asNativePointer())

fun Vector3.Metadata.copyTo(
    value: Vector3,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Vector4.Metadata.ABI_LAYOUT: MemoryLayout
    get() = vector4AbiLayout

fun Vector4.Metadata.fromAbi(source: MemorySegment): Vector4 = fromAbi(source.asNativePointer())

fun Vector4.Metadata.copyTo(
    value: Vector4,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Quaternion.Metadata.ABI_LAYOUT: MemoryLayout
    get() = vector4AbiLayout

fun Quaternion.Metadata.fromAbi(source: MemorySegment): Quaternion = fromAbi(source.asNativePointer())

fun Quaternion.Metadata.copyTo(
    value: Quaternion,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Matrix3x2.Metadata.ABI_LAYOUT: MemoryLayout
    get() = matrix3x2AbiLayout

fun Matrix3x2.Metadata.fromAbi(source: MemorySegment): Matrix3x2 = fromAbi(source.asNativePointer())

fun Matrix3x2.Metadata.copyTo(
    value: Matrix3x2,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Matrix4x4.Metadata.ABI_LAYOUT: MemoryLayout
    get() = matrix4x4AbiLayout

fun Matrix4x4.Metadata.fromAbi(source: MemorySegment): Matrix4x4 = fromAbi(source.asNativePointer())

fun Matrix4x4.Metadata.copyTo(
    value: Matrix4x4,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}

val Plane.Metadata.ABI_LAYOUT: MemoryLayout
    get() = planeAbiLayout

fun Plane.Metadata.fromAbi(source: MemorySegment): Plane = fromAbi(source.asNativePointer())

fun Plane.Metadata.copyTo(
    value: Plane,
    destination: MemorySegment,
) {
    copyTo(value, destination.asNativePointer())
}
