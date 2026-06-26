package io.github.composefluent.winrt.runtime

object ParameterizedInterfaceId {
    private val namespace = Guid("D57AF411-737B-C042-ABAE-878B1E16ADEE")

    fun createFromSignature(signature: WinRTTypeSignature): Guid =
        createFromSignature(signature.render())

    fun createFromParameterizedInterface(
        genericInterface: Guid,
        vararg arguments: WinRTTypeSignature,
    ): Guid = createFromSignature(WinRTTypeSignature.parameterizedInterface(genericInterface, *arguments))

    fun createFromParameterizedInterface(
        genericInterface: String,
        vararg arguments: WinRTTypeSignature,
    ): Guid = createFromSignature(WinRTTypeSignature.parameterizedInterface(genericInterface, *arguments))

    fun createFromSignature(signature: String): Guid {
        val hash = Sha1.digest(namespace.toLittleEndianBytes() + signature.encodeToByteArray())
        val guidBytes = hash.copyOfRange(0, Guid.BYTE_SIZE)
        guidBytes[6] = ((guidBytes[6].toInt() and 0x0F) or 0x50).toByte()
        guidBytes[8] = ((guidBytes[8].toInt() and 0x3F) or 0x80).toByte()
        return Guid.fromNetworkBytes(guidBytes)
    }
}
