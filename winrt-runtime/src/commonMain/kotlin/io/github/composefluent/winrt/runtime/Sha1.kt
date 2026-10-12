package io.github.composefluent.winrt.runtime

/** UUID v5 hashing, matching CsWinRT GuidGenerator.CreateIIDForGenericType. */
internal expect object Sha1 {
    fun digest(prefix: ByteArray, input: ByteArray): ByteArray
}
