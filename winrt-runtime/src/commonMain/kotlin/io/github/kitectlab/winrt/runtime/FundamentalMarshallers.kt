package io.github.kitectlab.winrt.runtime

object BooleanMarshaller {
    fun toAbi(value: Boolean): Byte = if (value) 1 else 0

    fun fromAbi(value: Byte): Boolean = value.toInt() != 0

    fun copyTo(value: Boolean, destination: RawAddress) {
        PlatformAbi.writeInt8(destination, toAbi(value))
    }

    fun readFrom(source: RawAddress): Boolean = fromAbi(PlatformAbi.readInt8(source))
}

object CharMarshaller {
    fun toAbi(value: Char): Short = value.code.toShort()

    fun fromAbi(value: Short): Char = value.toInt().toChar()

    fun copyTo(value: Char, destination: RawAddress) {
        PlatformAbi.writeChar16(destination, value)
    }

    fun readFrom(source: RawAddress): Char = PlatformAbi.readChar16(source)
}

object GuidMarshaller {
    fun copyTo(value: Guid, destination: RawAddress) {
        value.writeTo(destination)
    }

    fun readFrom(source: RawAddress): Guid = Guid.readFrom(source)
}
