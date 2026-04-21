package io.github.kitectlab.winrt.runtime

private fun readFloatField(
    source: NativePointer,
    layout: NativeStructLayout,
    fieldName: String,
): Float = NativeInterop.readFloat(layout.slice(source, fieldName))

private fun writeFloatField(
    destination: NativePointer,
    layout: NativeStructLayout,
    fieldName: String,
    value: Float,
) {
    NativeInterop.writeFloat(layout.slice(destination, fieldName), value)
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Vector2;f4;f4)")
data class Vector2(
    val x: Float,
    val y: Float,
) {
    companion object Metadata : NativeStructAdapter<Vector2> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Vector2 =
            Vector2(
                x = readFloatField(source, layout, "x"),
                y = readFloatField(source, layout, "y"),
            )

        override fun write(
            value: Vector2,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "x", value.x)
            writeFloatField(destination, layout, "y", value.y)
        }

        fun fromAbi(source: NativePointer): Vector2 = read(source)

        fun copyTo(
            value: Vector2,
            destination: NativePointer,
        ) {
            write(value, destination)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Vector3;f4;f4;f4)")
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    companion object Metadata : NativeStructAdapter<Vector3> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("z", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Vector3 =
            Vector3(
                x = readFloatField(source, layout, "x"),
                y = readFloatField(source, layout, "y"),
                z = readFloatField(source, layout, "z"),
            )

        override fun write(
            value: Vector3,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "x", value.x)
            writeFloatField(destination, layout, "y", value.y)
            writeFloatField(destination, layout, "z", value.z)
        }

        fun fromAbi(source: NativePointer): Vector3 = read(source)

        fun copyTo(
            value: Vector3,
            destination: NativePointer,
        ) {
            write(value, destination)
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
    companion object Metadata : NativeStructAdapter<Vector4> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("z", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("w", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Vector4 =
            Vector4(
                x = readFloatField(source, layout, "x"),
                y = readFloatField(source, layout, "y"),
                z = readFloatField(source, layout, "z"),
                w = readFloatField(source, layout, "w"),
            )

        override fun write(
            value: Vector4,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "x", value.x)
            writeFloatField(destination, layout, "y", value.y)
            writeFloatField(destination, layout, "z", value.z)
            writeFloatField(destination, layout, "w", value.w)
        }

        fun fromAbi(source: NativePointer): Vector4 = read(source)

        fun copyTo(
            value: Vector4,
            destination: NativePointer,
        ) {
            write(value, destination)
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
    companion object Metadata : NativeStructAdapter<Quaternion> {
        override val layout: NativeStructLayout = Vector4.Metadata.layout

        override fun read(source: NativePointer): Quaternion =
            Quaternion(
                x = readFloatField(source, layout, "x"),
                y = readFloatField(source, layout, "y"),
                z = readFloatField(source, layout, "z"),
                w = readFloatField(source, layout, "w"),
            )

        override fun write(
            value: Quaternion,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "x", value.x)
            writeFloatField(destination, layout, "y", value.y)
            writeFloatField(destination, layout, "z", value.z)
            writeFloatField(destination, layout, "w", value.w)
        }

        fun fromAbi(source: NativePointer): Quaternion = read(source)

        fun copyTo(
            value: Quaternion,
            destination: NativePointer,
        ) {
            write(value, destination)
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
    companion object Metadata : NativeStructAdapter<Matrix3x2> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("m11", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m12", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m21", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m22", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m31", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m32", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Matrix3x2 =
            Matrix3x2(
                m11 = readFloatField(source, layout, "m11"),
                m12 = readFloatField(source, layout, "m12"),
                m21 = readFloatField(source, layout, "m21"),
                m22 = readFloatField(source, layout, "m22"),
                m31 = readFloatField(source, layout, "m31"),
                m32 = readFloatField(source, layout, "m32"),
            )

        override fun write(
            value: Matrix3x2,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "m11", value.m11)
            writeFloatField(destination, layout, "m12", value.m12)
            writeFloatField(destination, layout, "m21", value.m21)
            writeFloatField(destination, layout, "m22", value.m22)
            writeFloatField(destination, layout, "m31", value.m31)
            writeFloatField(destination, layout, "m32", value.m32)
        }

        fun fromAbi(source: NativePointer): Matrix3x2 = read(source)

        fun copyTo(
            value: Matrix3x2,
            destination: NativePointer,
        ) {
            write(value, destination)
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
    companion object Metadata : NativeStructAdapter<Matrix4x4> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("m11", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m12", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m13", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m14", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m21", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m22", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m23", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m24", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m31", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m32", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m33", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m34", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m41", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m42", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m43", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("m44", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Matrix4x4 =
            Matrix4x4(
                m11 = readFloatField(source, layout, "m11"),
                m12 = readFloatField(source, layout, "m12"),
                m13 = readFloatField(source, layout, "m13"),
                m14 = readFloatField(source, layout, "m14"),
                m21 = readFloatField(source, layout, "m21"),
                m22 = readFloatField(source, layout, "m22"),
                m23 = readFloatField(source, layout, "m23"),
                m24 = readFloatField(source, layout, "m24"),
                m31 = readFloatField(source, layout, "m31"),
                m32 = readFloatField(source, layout, "m32"),
                m33 = readFloatField(source, layout, "m33"),
                m34 = readFloatField(source, layout, "m34"),
                m41 = readFloatField(source, layout, "m41"),
                m42 = readFloatField(source, layout, "m42"),
                m43 = readFloatField(source, layout, "m43"),
                m44 = readFloatField(source, layout, "m44"),
            )

        override fun write(
            value: Matrix4x4,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "m11", value.m11)
            writeFloatField(destination, layout, "m12", value.m12)
            writeFloatField(destination, layout, "m13", value.m13)
            writeFloatField(destination, layout, "m14", value.m14)
            writeFloatField(destination, layout, "m21", value.m21)
            writeFloatField(destination, layout, "m22", value.m22)
            writeFloatField(destination, layout, "m23", value.m23)
            writeFloatField(destination, layout, "m24", value.m24)
            writeFloatField(destination, layout, "m31", value.m31)
            writeFloatField(destination, layout, "m32", value.m32)
            writeFloatField(destination, layout, "m33", value.m33)
            writeFloatField(destination, layout, "m34", value.m34)
            writeFloatField(destination, layout, "m41", value.m41)
            writeFloatField(destination, layout, "m42", value.m42)
            writeFloatField(destination, layout, "m43", value.m43)
            writeFloatField(destination, layout, "m44", value.m44)
        }

        fun fromAbi(source: NativePointer): Matrix4x4 = read(source)

        fun copyTo(
            value: Matrix4x4,
            destination: NativePointer,
        ) {
            write(value, destination)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Numerics.Plane;struct(Windows.Foundation.Numerics.Vector3;f4;f4;f4);f4)")
data class Plane(
    val normal: Vector3,
    val d: Float,
) {
    companion object Metadata : NativeStructAdapter<Plane> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeNestedStructFieldSpec("normal", Vector3.Metadata.layout),
                NativeScalarFieldSpec("d", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Plane =
            Plane(
                normal = Vector3.Metadata.read(layout.slice(source, "normal")),
                d = readFloatField(source, layout, "d"),
            )

        override fun write(
            value: Plane,
            destination: NativePointer,
        ) {
            Vector3.Metadata.write(value.normal, layout.slice(destination, "normal"))
            writeFloatField(destination, layout, "d", value.d)
        }

        fun fromAbi(source: NativePointer): Plane = read(source)

        fun copyTo(
            value: Plane,
            destination: NativePointer,
        ) {
            write(value, destination)
        }
    }
}
