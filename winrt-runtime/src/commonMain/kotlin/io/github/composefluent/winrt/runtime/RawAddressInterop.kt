package io.github.composefluent.winrt.runtime

@PublishedApi
internal inline fun RawComPtr.asRawAddress(): RawAddress = RawAddress(value)

@PublishedApi
internal inline fun RawAddress.asRawComPtr(): RawComPtr = RawComPtr(value)
