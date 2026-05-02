package io.github.composefluent.winrt.runtime

import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * Stores handler -> token mappings for CCW-sourced WinRT events.
 *
 * `.cswinrt` uses `typeof(T).GetHashCode()` for the upper 32 bits of each token.
 * Kotlin common code has no direct equivalent for that CLR-specific generic type identity,
 * so this owner uses the delegate type display name hash instead while preserving the same
 * two-part token layout and the non-zero upper-32-bits constraint.
 */
class EventRegistrationTokenTable<T : Any> private constructor(
    private val delegateTypeHash: Int,
) {
    private val lock = PlatformLock()
    private val tokens = mutableMapOf<Int, T>()
    private var nextLow32Bits: Int = Random.nextInt()

    fun addEventHandler(handler: T?): EventRegistrationToken {
        if (handler == null) {
            return EventRegistrationToken()
        }

        return lock.withLock {
            var tokenLow32Bits: Int
            do {
                tokenLow32Bits = nextLow32Bits++
            } while (tokens.containsKey(tokenLow32Bits))
            tokens[tokenLow32Bits] = handler
            EventRegistrationToken(composeTokenValue(delegateTypeHash, tokenLow32Bits))
        }
    }

    fun removeEventHandler(token: EventRegistrationToken): T? {
        if (upper32Bits(token) != delegateTypeHash) {
            return null
        }

        return lock.withLock {
            tokens.remove(lower32Bits(token))
        }
    }

    companion object {
        private const val zeroHashReplacement = 0x5FC74196

        inline fun <reified T : Any> create(): EventRegistrationTokenTable<T> =
            create(T::class)

        fun <T : Any> create(delegateType: KClass<out T>): EventRegistrationTokenTable<T> =
            EventRegistrationTokenTable(typeHash(delegateType.typeDisplayName()))

        internal fun <T : Any> create(typeIdentity: String): EventRegistrationTokenTable<T> =
            EventRegistrationTokenTable(typeHash(typeIdentity))

        private fun typeHash(typeIdentity: String): Int {
            val hash = typeIdentity.hashCode()
            return if (hash == 0) {
                zeroHashReplacement
            } else {
                hash
            }
        }

        private fun composeTokenValue(
            typeHash: Int,
            tokenLow32Bits: Int,
        ): Long =
            (((typeHash.toUInt().toULong()) shl 32) or tokenLow32Bits.toUInt().toULong()).toLong()

        private fun upper32Bits(token: EventRegistrationToken): Int =
            (token.value.toULong() shr 32).toInt()

        private fun lower32Bits(token: EventRegistrationToken): Int = token.value.toInt()
    }
}
