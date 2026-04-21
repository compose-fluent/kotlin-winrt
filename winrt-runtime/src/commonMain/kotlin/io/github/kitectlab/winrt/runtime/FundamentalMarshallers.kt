package io.github.kitectlab.winrt.runtime

object BooleanMarshaller {
    fun toAbi(value: Boolean): Byte = if (value) 1 else 0

    fun fromAbi(value: Byte): Boolean = value.toInt() != 0

    fun copyTo(value: Boolean, destination: NativePointer) {
        NativeInterop.writeInt8(destination, toAbi(value))
    }

    fun readFrom(source: NativePointer): Boolean = fromAbi(NativeInterop.readInt8(source))
}

object CharMarshaller {
    fun toAbi(value: Char): Short = value.code.toShort()

    fun fromAbi(value: Short): Char = value.toInt().toChar()

    fun copyTo(value: Char, destination: NativePointer) {
        NativeInterop.writeChar16(destination, value)
    }

    fun readFrom(source: NativePointer): Char = NativeInterop.readChar16(source)
}

object GuidMarshaller {
    fun copyTo(value: Guid, destination: NativePointer) {
        value.writeTo(destination)
    }

    fun readFrom(source: NativePointer): Guid = Guid.readFrom(source)
}
