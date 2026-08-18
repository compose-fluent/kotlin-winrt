package io.github.composefluent.winrt.runtime

import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import windows.foundation.EventRegistrationToken

/**
 * Immutable event-handler state published by [EventRegistrationTokenTable].  The single-handler
 * field keeps the common one-registration raise path free of a collection lookup; multi-handler
 * snapshots retain registration order and duplicate registrations exactly as before.
 */
@PublishedApi
internal class EventRegistrationTokenTableSnapshot<T : Any>(
    @PublishedApi
    internal val singleHandler: T? = null,
    @PublishedApi
    internal val manyHandlers: List<T>? = null,
)

/**
 * Stores handler -> token mappings for CCW-sourced WinRT events.
 *
 * `.cswinrt` uses `typeof(T).GetHashCode()` for the upper 32 bits of each token.
 * Kotlin common code has no direct equivalent for that CLR-specific generic type identity,
 * so this owner uses the delegate type display name hash instead while preserving the same
 * two-part token layout and the non-zero upper-32-bits constraint.
 */
@OptIn(ExperimentalAtomicApi::class)
class EventRegistrationTokenTable<T : Any> private constructor(
    private val delegateTypeHash: Int,
) {
    private val lock = PlatformLock()
    private val tokens = linkedMapOf<Int, T>()
    /**
     * Immutable update-time snapshot used by authored event raisers.
     *
     * This is the Kotlin equivalent of the multicast delegate field behind a C# event: raising
     * an event only loads the already-composed snapshot and never copies the token map.
     */
    @PublishedApi
    internal val handlerSnapshot = AtomicReference(EventRegistrationTokenTableSnapshot<T>())
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
            handlerSnapshot.store(composeHandlerSnapshot())
            EventRegistrationToken(composeTokenValue(delegateTypeHash, tokenLow32Bits))
        }
    }

    fun removeEventHandler(token: EventRegistrationToken): T? {
        if (upper32Bits(token) != delegateTypeHash) {
            return null
        }

        return lock.withLock {
            tokens.remove(lower32Bits(token)).also {
                if (it != null) {
                    handlerSnapshot.store(composeHandlerSnapshot())
                }
            }
        }
    }

    /**
     * Invokes the current handler snapshot without allocating an iterator or copying the token
     * table. The inline body lets Native callers keep the event-specific callback at the call
     * site, matching the update-time composition used by `.cswinrt`.
     */
    inline fun forEachHandler(action: (T) -> Unit) {
        val snapshot = handlerSnapshot.load()
        val singleHandler = snapshot.singleHandler
        if (singleHandler != null) {
            action(singleHandler)
            return
        }

        val manyHandlers = snapshot.manyHandlers ?: return
        val count = manyHandlers.size
        var index = 0
        while (index < count) {
            action(manyHandlers[index])
            index += 1
        }
    }

    private fun composeHandlerSnapshot(): EventRegistrationTokenTableSnapshot<T> =
        when (tokens.size) {
            0 -> EventRegistrationTokenTableSnapshot()
            1 -> EventRegistrationTokenTableSnapshot(singleHandler = tokens.values.first())
            else -> EventRegistrationTokenTableSnapshot(manyHandlers = tokens.values.toList())
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
