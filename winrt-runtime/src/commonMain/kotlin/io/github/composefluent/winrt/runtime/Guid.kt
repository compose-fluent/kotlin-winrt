package io.github.composefluent.winrt.runtime

class Guid private constructor(
    @PublishedApi
    internal val abiLowBits: Long,
    @PublishedApi
    internal val abiHighBits: Long,
    private val canonicalValue: String?,
) {
    constructor(value: String) : this(parseGuid(value))

    private constructor(parsed: ParsedGuid) : this(
        abiLowBits = parsed.abiLowBits,
        abiHighBits = parsed.abiHighBits,
        canonicalValue = parsed.canonicalValue,
    )

    val value: String
        get() = canonicalValue ?: formatCanonicalValue(abiLowBits, abiHighBits)

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other is Guid && abiLowBits == other.abiLowBits && abiHighBits == other.abiHighBits

    override fun hashCode(): Int = 31 * abiLowBits.hashCode() + abiHighBits.hashCode()

    fun toNetworkBytes(): ByteArray {
        val littleEndianBytes = toLittleEndianBytes()
        return byteArrayOf(
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
    }

    fun toLittleEndianBytes(): ByteArray =
        ByteArray(BYTE_SIZE).also { bytes ->
            unpackAbiWord(abiLowBits, bytes, 0)
            unpackAbiWord(abiHighBits, bytes, Long.SIZE_BYTES)
        }

    companion object {
        const val BYTE_SIZE: Int = 16

        /**
         * Rehydrates a GUID that was already read in its native ABI layout.
         *
         * The ABI callback path uses this only for uncommon lookup misses and
         * fallback QI.  Keeping the constructor here avoids a temporary byte
         * array when the caller already has the two native words.
         */
        internal fun fromAbiWords(
            abiLowBits: Long,
            abiHighBits: Long,
        ): Guid = Guid(abiLowBits, abiHighBits, canonicalValue = null)

        fun fromNetworkBytes(bytes: ByteArray): Guid {
            require(bytes.size == BYTE_SIZE) { "GUID bytes must be exactly $BYTE_SIZE bytes long." }
            return fromNetworkBytesPrefix(bytes)
        }

        internal fun fromNetworkBytesPrefix(bytes: ByteArray): Guid {
            require(bytes.size >= BYTE_SIZE) { "GUID bytes must contain at least $BYTE_SIZE bytes." }
            return Guid(
                abiLowBits = packAbiWord(
                    bytes[3],
                    bytes[2],
                    bytes[1],
                    bytes[0],
                    bytes[5],
                    bytes[4],
                    bytes[7],
                    bytes[6],
                ),
                abiHighBits = packAbiWord(
                    bytes[8],
                    bytes[9],
                    bytes[10],
                    bytes[11],
                    bytes[12],
                    bytes[13],
                    bytes[14],
                    bytes[15],
                ),
                canonicalValue = null,
            )
        }

        fun fromLittleEndianBytes(bytes: ByteArray): Guid {
            require(bytes.size == BYTE_SIZE) { "GUID bytes must be exactly $BYTE_SIZE bytes long." }
            return Guid(
                abiLowBits = packAbiWord(bytes, 0),
                abiHighBits = packAbiWord(bytes, Long.SIZE_BYTES),
                canonicalValue = null,
            )
        }
    }
}

private data class ParsedGuid(
    val abiLowBits: Long,
    val abiHighBits: Long,
    val canonicalValue: String,
)

private fun parseGuid(value: String): ParsedGuid {
    val canonicalValue = value.uppercase()
    require(guidRegex.matches(canonicalValue)) { "Invalid GUID: $value" }
    return ParsedGuid(
        abiLowBits = packAbiWord(
            canonicalValue.parseHexByte(6),
            canonicalValue.parseHexByte(4),
            canonicalValue.parseHexByte(2),
            canonicalValue.parseHexByte(0),
            canonicalValue.parseHexByte(11),
            canonicalValue.parseHexByte(9),
            canonicalValue.parseHexByte(16),
            canonicalValue.parseHexByte(14),
        ),
        abiHighBits = packAbiWord(
            canonicalValue.parseHexByte(19),
            canonicalValue.parseHexByte(21),
            canonicalValue.parseHexByte(24),
            canonicalValue.parseHexByte(26),
            canonicalValue.parseHexByte(28),
            canonicalValue.parseHexByte(30),
            canonicalValue.parseHexByte(32),
            canonicalValue.parseHexByte(34),
        ),
        canonicalValue = canonicalValue,
    )
}

private fun String.parseHexByte(firstCharacterIndex: Int): Byte =
    ((this[firstCharacterIndex].digitToInt(16) shl 4) or
        this[firstCharacterIndex + 1].digitToInt(16)).toByte()

private fun packAbiWord(
    byte0: Byte,
    byte1: Byte,
    byte2: Byte,
    byte3: Byte,
    byte4: Byte,
    byte5: Byte,
    byte6: Byte,
    byte7: Byte,
): Long =
    byte0.toUByte().toLong() or
        (byte1.toUByte().toLong() shl 8) or
        (byte2.toUByte().toLong() shl 16) or
        (byte3.toUByte().toLong() shl 24) or
        (byte4.toUByte().toLong() shl 32) or
        (byte5.toUByte().toLong() shl 40) or
        (byte6.toUByte().toLong() shl 48) or
        (byte7.toUByte().toLong() shl 56)

private fun packAbiWord(
    bytes: ByteArray,
    offset: Int,
): Long =
    packAbiWord(
        bytes[offset],
        bytes[offset + 1],
        bytes[offset + 2],
        bytes[offset + 3],
        bytes[offset + 4],
        bytes[offset + 5],
        bytes[offset + 6],
        bytes[offset + 7],
    )

private fun unpackAbiWord(
    word: Long,
    destination: ByteArray,
    offset: Int,
) {
    repeat(Long.SIZE_BYTES) { index ->
        destination[offset + index] = (word ushr (index * Byte.SIZE_BITS)).toByte()
    }
}

private fun formatCanonicalValue(
    abiLowBits: Long,
    abiHighBits: Long,
): String {
    val bytes = ByteArray(Guid.BYTE_SIZE)
    unpackAbiWord(abiLowBits, bytes, 0)
    unpackAbiWord(abiHighBits, bytes, Long.SIZE_BYTES)
    return buildString(36) {
        appendHexByte(bytes[3])
        appendHexByte(bytes[2])
        appendHexByte(bytes[1])
        appendHexByte(bytes[0])
        append('-')
        appendHexByte(bytes[5])
        appendHexByte(bytes[4])
        append('-')
        appendHexByte(bytes[7])
        appendHexByte(bytes[6])
        append('-')
        appendHexByte(bytes[8])
        appendHexByte(bytes[9])
        append('-')
        for (index in 10 until Guid.BYTE_SIZE) {
            appendHexByte(bytes[index])
        }
    }
}

private fun StringBuilder.appendHexByte(value: Byte) {
    val unsigned = value.toInt() and 0xFF
    append(HEX_DIGITS[unsigned ushr 4])
    append(HEX_DIGITS[unsigned and 0x0F])
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private val guidRegex = Regex("""[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}""")

fun guidOf(value: String): Guid = Guid(value)

fun Guid.writeTo(destination: RawAddress) {
    PlatformAbi.writeGuid(destination, this)
}

fun Guid.Companion.readFrom(source: RawAddress): Guid =
    PlatformAbi.readGuid(source)
