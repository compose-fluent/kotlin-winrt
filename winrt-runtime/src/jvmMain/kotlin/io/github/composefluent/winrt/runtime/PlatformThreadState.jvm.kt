package io.github.composefluent.winrt.runtime

internal actual fun platformCurrentThreadToken(): Long =
    Thread.currentThread().threadId()

internal actual fun platformCurrentThreadIsVirtual(): Boolean =
    Thread.currentThread().isVirtual
