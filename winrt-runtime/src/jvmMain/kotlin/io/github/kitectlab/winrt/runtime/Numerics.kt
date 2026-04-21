package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Vector2;f4;f4)")
data class Vector2(
    val x: Float,
    val y: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("x"),
            ValueLayout.JAVA_FLOAT.withName("y"),
        )

        fun fromAbi(source: MemorySegment): Vector2 =
            Vector2(
                x = source.get(ValueLayout.JAVA_FLOAT, 0),
                y = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
            )

        fun copyTo(value: Vector2, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.x)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.y)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Vector3;f4;f4;f4)")
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("x"),
            ValueLayout.JAVA_FLOAT.withName("y"),
            ValueLayout.JAVA_FLOAT.withName("z"),
        )

        fun fromAbi(source: MemorySegment): Vector3 =
            Vector3(
                x = source.get(ValueLayout.JAVA_FLOAT, 0),
                y = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
                z = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2),
            )

        fun copyTo(value: Vector3, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.x)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.y)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2, value.z)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Vector4;f4;f4;f4;f4)")
data class Vector4(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("x"),
            ValueLayout.JAVA_FLOAT.withName("y"),
            ValueLayout.JAVA_FLOAT.withName("z"),
            ValueLayout.JAVA_FLOAT.withName("w"),
        )

        fun fromAbi(source: MemorySegment): Vector4 =
            Vector4(
                x = source.get(ValueLayout.JAVA_FLOAT, 0),
                y = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
                z = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2),
                w = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3),
            )

        fun copyTo(value: Vector4, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.x)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.y)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2, value.z)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3, value.w)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Quaternion;f4;f4;f4;f4)")
data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = Vector4.Metadata.ABI_LAYOUT

        fun fromAbi(source: MemorySegment): Quaternion =
            Quaternion(
                x = source.get(ValueLayout.JAVA_FLOAT, 0),
                y = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
                z = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2),
                w = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3),
            )

        fun copyTo(value: Quaternion, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.x)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.y)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2, value.z)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3, value.w)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Matrix3x2;f4;f4;f4;f4;f4;f4)")
data class Matrix3x2(
    val m11: Float,
    val m12: Float,
    val m21: Float,
    val m22: Float,
    val m31: Float,
    val m32: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("m11"),
            ValueLayout.JAVA_FLOAT.withName("m12"),
            ValueLayout.JAVA_FLOAT.withName("m21"),
            ValueLayout.JAVA_FLOAT.withName("m22"),
            ValueLayout.JAVA_FLOAT.withName("m31"),
            ValueLayout.JAVA_FLOAT.withName("m32"),
        )

        fun fromAbi(source: MemorySegment): Matrix3x2 =
            Matrix3x2(
                m11 = source.get(ValueLayout.JAVA_FLOAT, 0),
                m12 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
                m21 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2),
                m22 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3),
                m31 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 4),
                m32 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 5),
            )

        fun copyTo(value: Matrix3x2, destination: MemorySegment) {
            destination.set(ValueLayout.JAVA_FLOAT, 0, value.m11)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize(), value.m12)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2, value.m21)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3, value.m22)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 4, value.m31)
            destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 5, value.m32)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Matrix4x4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4;f4)")
data class Matrix4x4(
    val m11: Float,
    val m12: Float,
    val m13: Float,
    val m14: Float,
    val m21: Float,
    val m22: Float,
    val m23: Float,
    val m24: Float,
    val m31: Float,
    val m32: Float,
    val m33: Float,
    val m34: Float,
    val m41: Float,
    val m42: Float,
    val m43: Float,
    val m44: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            ValueLayout.JAVA_FLOAT.withName("m11"),
            ValueLayout.JAVA_FLOAT.withName("m12"),
            ValueLayout.JAVA_FLOAT.withName("m13"),
            ValueLayout.JAVA_FLOAT.withName("m14"),
            ValueLayout.JAVA_FLOAT.withName("m21"),
            ValueLayout.JAVA_FLOAT.withName("m22"),
            ValueLayout.JAVA_FLOAT.withName("m23"),
            ValueLayout.JAVA_FLOAT.withName("m24"),
            ValueLayout.JAVA_FLOAT.withName("m31"),
            ValueLayout.JAVA_FLOAT.withName("m32"),
            ValueLayout.JAVA_FLOAT.withName("m33"),
            ValueLayout.JAVA_FLOAT.withName("m34"),
            ValueLayout.JAVA_FLOAT.withName("m41"),
            ValueLayout.JAVA_FLOAT.withName("m42"),
            ValueLayout.JAVA_FLOAT.withName("m43"),
            ValueLayout.JAVA_FLOAT.withName("m44"),
        )

        fun fromAbi(source: MemorySegment): Matrix4x4 =
            Matrix4x4(
                m11 = source.get(ValueLayout.JAVA_FLOAT, 0),
                m12 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize()),
                m13 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 2),
                m14 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 3),
                m21 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 4),
                m22 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 5),
                m23 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 6),
                m24 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 7),
                m31 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 8),
                m32 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 9),
                m33 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 10),
                m34 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 11),
                m41 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 12),
                m42 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 13),
                m43 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 14),
                m44 = source.get(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * 15),
            )

        fun copyTo(value: Matrix4x4, destination: MemorySegment) {
            val values = floatArrayOf(
                value.m11,
                value.m12,
                value.m13,
                value.m14,
                value.m21,
                value.m22,
                value.m23,
                value.m24,
                value.m31,
                value.m32,
                value.m33,
                value.m34,
                value.m41,
                value.m42,
                value.m43,
                value.m44,
            )
            values.forEachIndexed { index, component ->
                destination.set(ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT.byteSize() * index, component)
            }
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Plane;struct(Windows.Foundation.Numerics.Vector3;f4;f4;f4);f4)")
data class Plane(
    val normal: Vector3,
    val d: Float,
) {
    companion object Metadata {
        val ABI_LAYOUT: MemoryLayout = MemoryLayout.structLayout(
            Vector3.Metadata.ABI_LAYOUT.withName("normal"),
            ValueLayout.JAVA_FLOAT.withName("d"),
        )

        fun fromAbi(source: MemorySegment): Plane =
            Plane(
                normal = Vector3.Metadata.fromAbi(source.asSlice(0, Vector3.Metadata.ABI_LAYOUT.byteSize())),
                d = source.get(ValueLayout.JAVA_FLOAT, Vector3.Metadata.ABI_LAYOUT.byteSize()),
            )

        fun copyTo(value: Plane, destination: MemorySegment) {
            Vector3.Metadata.copyTo(value.normal, destination.asSlice(0, Vector3.Metadata.ABI_LAYOUT.byteSize()))
            destination.set(ValueLayout.JAVA_FLOAT, Vector3.Metadata.ABI_LAYOUT.byteSize(), value.d)
        }
    }
}
