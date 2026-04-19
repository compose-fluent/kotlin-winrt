package io.github.kitectlab.winrt.runtime

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object ParameterizedInterfaceId {
    private val namespace = UUID.fromString("d57af411-737b-c042-abae-878b1e16adee")

    fun createFromSignature(signature: WinRtTypeSignature): Guid =
        createFromSignature(signature.render())

    fun createFromParameterizedInterface(genericInterface: Guid, vararg arguments: WinRtTypeSignature): Guid =
        createFromSignature(WinRtTypeSignature.parameterizedInterface(genericInterface, *arguments))

    fun createFromParameterizedInterface(genericInterface: String, vararg arguments: WinRtTypeSignature): Guid =
        createFromSignature(WinRtTypeSignature.parameterizedInterface(genericInterface, *arguments))

    fun createFromSignature(signature: String): Guid {
        val namespaceBytes = guidToNetworkBytes(namespace)
        val signatureBytes = signature.toByteArray(StandardCharsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-1").digest(namespaceBytes + signatureBytes)
        val guidBytes = hash.copyOfRange(0, 16)
        guidBytes[6] = ((guidBytes[6].toInt() and 0x0F) or 0x50).toByte()
        guidBytes[8] = ((guidBytes[8].toInt() and 0x3F) or 0x80).toByte()
        return Guid(uuidFromNetworkBytes(guidBytes))
    }

    private fun guidToNetworkBytes(value: UUID): ByteArray {
        val bytes = ByteArray(16)
        val msb = value.mostSignificantBits
        val lsb = value.leastSignificantBits
        for (index in 0 until 8) {
            bytes[index] = (msb ushr (56 - index * 8)).toByte()
            bytes[index + 8] = (lsb ushr (56 - index * 8)).toByte()
        }
        return bytes
    }

    private fun uuidFromNetworkBytes(bytes: ByteArray): UUID {
        var msb = 0L
        var lsb = 0L
        for (index in 0 until 8) {
            msb = (msb shl 8) or (bytes[index].toLong() and 0xFF)
            lsb = (lsb shl 8) or (bytes[index + 8].toLong() and 0xFF)
        }
        return UUID(msb, lsb)
    }
}
