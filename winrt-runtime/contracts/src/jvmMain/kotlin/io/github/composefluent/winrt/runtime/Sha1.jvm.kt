package io.github.composefluent.winrt.runtime

import java.security.MessageDigest

internal actual object Sha1 {
    actual fun digest(prefix: ByteArray, input: ByteArray): ByteArray {
        // Like CsWinRT, use the platform hash provider. Each invocation owns its digest:
        // no shared mutable state, thread-local retention, or concatenated input buffer.
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(prefix)
        return digest.digest(input)
    }
}
