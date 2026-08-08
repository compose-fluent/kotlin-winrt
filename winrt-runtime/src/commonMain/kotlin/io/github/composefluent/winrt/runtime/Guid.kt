package io.github.composefluent.winrt.runtime

class Guid(value: String) {
    val value: String = value.uppercase()

    private val littleEndianBytes: ByteArray

    internal val abiLowBits: Long

    internal val abiHighBits: Long

    init {
        require(guidRegex.matches(this.value)) { "Invalid GUID: $value" }
        littleEndianBytes =
            byteArrayOf(
                parseHexByte(6),
                parseHexByte(4),
                parseHexByte(2),
                parseHexByte(0),
                parseHexByte(11),
                parseHexByte(9),
                parseHexByte(16),
                parseHexByte(14),
                parseHexByte(19),
                parseHexByte(21),
                parseHexByte(24),
                parseHexByte(26),
                parseHexByte(28),
                parseHexByte(30),
                parseHexByte(32),
                parseHexByte(34),
            )
        abiLowBits = packAbiWord(0)
        abiHighBits = packAbiWord(Long.SIZE_BYTES)
    }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other is Guid && value == other.value

    override fun hashCode(): Int = value.hashCode()

    fun toNetworkBytes(): ByteArray =
        byteArrayOf(
            littleEndianBytes[3],
            littleEndianBytes[2],
            littleEndianBytes[1],
            littleEndianBytes[0],
            littleEndianBytes[5],
            littleEndianBytes[4],
            littleEndianBytes[7],
            littleEndianBytes[6],
            littleEndianBytes[8],
            littleEndianBytes[9],
            littleEndianBytes[10],
            littleEndianBytes[11],
            littleEndianBytes[12],
            littleEndianBytes[13],
            littleEndianBytes[14],
            littleEndianBytes[15],
        )

    fun toLittleEndianBytes(): ByteArray = littleEndianBytes.copyOf()

    private fun parseHexByte(firstCharacterIndex: Int): Byte =
        ((value[firstCharacterIndex].digitToInt(16) shl 4) or
            value[firstCharacterIndex + 1].digitToInt(16)).toByte()

    private fun packAbiWord(byteOffset: Int): Long {
        var word = 0L
        repeat(Long.SIZE_BYTES) { index ->
            word = word or (littleEndianBytes[byteOffset + index].toUByte().toLong() shl (index * Byte.SIZE_BITS))
        }
        return word
    }

    companion object {
        const val BYTE_SIZE: Int = 16

        fun fromNetworkBytes(bytes: ByteArray): Guid {
            require(bytes.size == BYTE_SIZE) { "GUID bytes must be exactly $BYTE_SIZE bytes long." }
            val hex = bytes.joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
            return Guid(
                "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                    "${hex.substring(16, 20)}-${hex.substring(20, 32)}",
            )
        }

        fun fromLittleEndianBytes(bytes: ByteArray): Guid {
            require(bytes.size == BYTE_SIZE) { "GUID bytes must be exactly $BYTE_SIZE bytes long." }
            return fromNetworkBytes(
                byteArrayOf(
                    bytes[3],
                    bytes[2],
                    bytes[1],
                    bytes[0],
                    bytes[5],
                    bytes[4],
                    bytes[7],
                    bytes[6],
                    bytes[8],
                    bytes[9],
                    bytes[10],
                    bytes[11],
                    bytes[12],
                    bytes[13],
                    bytes[14],
                    bytes[15],
                ),
            )
        }
    }
}

private val guidRegex = Regex("""[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}""")

fun guidOf(value: String): Guid = Guid(value)

fun Guid.writeTo(destination: RawAddress) {
    PlatformAbi.writeGuid(destination, this)
}

fun Guid.Companion.readFrom(source: RawAddress): Guid =
    PlatformAbi.readGuid(source)
