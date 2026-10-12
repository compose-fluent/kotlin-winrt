package io.github.composefluent.winrt.runtime

/**
 * CsWinRT's RuntimeWrapperCache coordinates publication by canonical IUnknown identity.
 * Kotlin keeps creation single-flight as well: factories transfer explicit ComPtr ownership,
 * so racing disposable candidates must not escape to user factories. Only active creations
 * retain a lock; unrelated COM identities never run their factories under the registry lock.
 */
internal class RcwCreationLocks {
    private class Entry {
        val lock = PlatformLock()
        var users = 0
    }

    private val registryLock = PlatformLock()
    private val entries = mutableMapOf<Long, Entry>()

    fun <T> withIdentity(identity: Long, block: () -> T): T {
        val entry = registryLock.withLock {
            entries.getOrPut(identity) { Entry() }.also { it.users++ }
        }
        try {
            return entry.lock.withLock(block)
        } finally {
            registryLock.withLock {
                if (--entry.users == 0) entries.remove(identity)
            }
        }
    }
}
