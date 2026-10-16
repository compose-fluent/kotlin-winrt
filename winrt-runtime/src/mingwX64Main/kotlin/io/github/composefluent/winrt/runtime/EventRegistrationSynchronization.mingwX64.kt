package io.github.composefluent.winrt.runtime

internal actual inline fun <R> withEventRegistrationLock(
    table: EventRegistrationTokenTable<*>,
    action: () -> R,
): R = table.lockForMutation().withLock(action)
