package io.github.composefluent.winrt.runtime

// Both targets use RawComObjectReferenceSupport's CsWinRT 3 lifetime contract.
// Native expands the transitions at the ABI call; JVM leaves small helpers for
// its JIT so projected getters stay below HotSpot's hot-method inlining budget.
@PublishedApi
internal expect inline fun beginComCall(support: RawComObjectReferenceSupport)

@PublishedApi
internal expect inline fun endComCall(reference: ComObjectReference)
