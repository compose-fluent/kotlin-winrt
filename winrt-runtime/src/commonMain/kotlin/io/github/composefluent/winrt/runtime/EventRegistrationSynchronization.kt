package io.github.composefluent.winrt.runtime

// CsWinRT EventRegistrationTokenTable<T> uses a monitor belonging to each token dictionary.
// The platform boundary owns monitor adaptation, not token assignment or handler snapshots.
internal expect inline fun <R> withEventRegistrationLock(
    table: EventRegistrationTokenTable<*>,
    action: () -> R,
): R
