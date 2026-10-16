package io.github.composefluent.winrt.runtime

internal actual inline fun <T> withObjectReferenceLock(owner: Any, action: () -> T): T =
    synchronized(owner, action)
