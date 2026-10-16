package io.github.composefluent.winrt.runtime

internal actual inline fun <T> withObjectReferenceLock(owner: Any, action: () -> T): T =
    synchronized(owner, action)

internal actual inline fun <T> withObjectReferenceCachePublication(owner: Any, action: () -> T): T =
    synchronized(owner, action)
