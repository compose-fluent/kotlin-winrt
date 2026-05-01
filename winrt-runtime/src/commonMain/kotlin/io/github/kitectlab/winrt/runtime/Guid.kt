package io.github.kitectlab.winrt.runtime

class Guid(value: String) {
    val value: String = value.uppercase()

    init {
        require(guidRegex.matches(this.value)) { "Invalid GUID: $value" }
    }

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean =
        other is Guid && value == other.value

    override fun hashCode(): Int = value.hashCode()

    fun toNetworkBytes(): ByteArray {
        val hex = value.replace("-", "")
        return ByteArray(BYTE_SIZE) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun toLittleEndianBytes(): ByteArray {
        val network = toNetworkBytes()
        return byteArrayOf(
            network[3],
            network[2],
            network[1],
            network[0],
            network[5],
            network[4],
            network[7],
            network[6],
            network[8],
            network[9],
            network[10],
            network[11],
            network[12],
            network[13],
            network[14],
            network[15],
        )
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
