@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.github.kitectlab.winrt.runtime

import kotlin.uuid.Uuid

data class Guid(val value: Uuid) {
    constructor(value: String) : this(Uuid.parse(value))

    override fun toString(): String = value.toString().uppercase()

    fun toNetworkBytes(): ByteArray = value.toByteArray()

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
            return Guid(Uuid.fromByteArray(bytes.copyOf()))
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

fun guidOf(value: String): Guid = Guid(value)

fun Guid.writeTo(destination: RawAddress) {
    PlatformAbi.writeGuid(destination, this)
}

fun Guid.Companion.readFrom(source: RawAddress): Guid =
    PlatformAbi.readGuid(source)
