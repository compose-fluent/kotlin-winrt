package io.github.composefluent.winrt.runtime

internal actual inline fun <R> withEventRegistrationLock(
    table: EventRegistrationTokenTable<*>,
    action: () -> R,
): R = synchronized(table, action)
