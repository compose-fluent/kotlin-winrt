package io.github.composefluent.winrt.runtime

// CsWinRT WindowsRuntimeObject publishes caches per object with Interlocked.
// Kotlin's lazy state and deterministic-close graph must likewise avoid a single
// process-wide serialization point. Only managed state changes belong in this scope;
// COM Release and callbacks must run after leaving it.
internal expect inline fun <T> withObjectReferenceLock(owner: Any, action: () -> T): T
