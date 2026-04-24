package io.github.kitectlab.winrt.runtime

internal fun RawComPtr.asRawAddress(): RawAddress = RawAddress(value)

internal fun RawAddress.asRawComPtr(): RawComPtr = RawComPtr(value)
