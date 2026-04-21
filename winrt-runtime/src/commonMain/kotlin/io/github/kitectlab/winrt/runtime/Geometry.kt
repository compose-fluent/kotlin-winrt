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

@WindowsRuntimeType("struct(Windows.Foundation.Point;f4;f4)")
data class Point(
    val x: Float,
    val y: Float,
) {
    companion object Metadata : NativeStructAdapter<Point> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Point =
            Point(
                x = readFloatField(source, layout, "x"),
                y = readFloatField(source, layout, "y"),
            )

        override fun write(
            value: Point,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "x", value.x)
            writeFloatField(destination, layout, "y", value.y)
        }

        fun fromAbi(source: NativePointer): Point = read(source)

        fun copyTo(
            value: Point,
            destination: NativePointer,
        ) {
            write(value, destination)
        }
    }
}

@WindowsRuntimeType("struct(Windows.Foundation.Size;f4;f4)")
data class Size(
    val width: Float,
    val height: Float,
) {
    companion object Metadata : NativeStructAdapter<Size> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("width", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("height", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Size =
            Size(
                width = readFloatField(source, layout, "width"),
                height = readFloatField(source, layout, "height"),
            )

        override fun write(
            value: Size,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "width", value.width)
            writeFloatField(destination, layout, "height", value.height)
        }

        fun fromAbi(source: NativePointer): Size = read(source)

        fun copyTo(
            value: Size,
            destination: NativePointer,
        ) {
            write(value, destination)
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
    companion object Metadata : NativeStructAdapter<Rect> {
        override val layout: NativeStructLayout =
            NativeStructLayout.sequential(
                NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("width", NativeStructScalarKind.FLOAT32),
                NativeScalarFieldSpec("height", NativeStructScalarKind.FLOAT32),
            )

        override fun read(source: NativePointer): Rect =
            Rect(
                x = readFloatField(source, layout, "x"),
                y = readFloatField(source, layout, "y"),
                width = readFloatField(source, layout, "width"),
                height = readFloatField(source, layout, "height"),
            )

        override fun write(
            value: Rect,
            destination: NativePointer,
        ) {
            writeFloatField(destination, layout, "x", value.x)
            writeFloatField(destination, layout, "y", value.y)
            writeFloatField(destination, layout, "width", value.width)
            writeFloatField(destination, layout, "height", value.height)
        }

        fun fromAbi(source: NativePointer): Rect = read(source)

        fun copyTo(
            value: Rect,
            destination: NativePointer,
        ) {
            write(value, destination)
        }
    }
}
