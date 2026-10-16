package io.github.composefluent.winrt.runtime

// Kotlin's deterministic-close graph must avoid a single process-wide
// serialization point. Only managed ownership changes belong in this scope;
// COM Release and callbacks must run after leaving it.
internal expect inline fun <T> withObjectReferenceLock(owner: Any, action: () -> T): T

// CsWinRT publishes independently constructed dictionaries with Interlocked.
// The platform fallback protects only nullable-field publication: construct the
// candidate first, and never invoke allocation, callbacks, or nested locks here.
internal expect inline fun <T> withObjectReferenceCachePublication(owner: Any, action: () -> T): T
