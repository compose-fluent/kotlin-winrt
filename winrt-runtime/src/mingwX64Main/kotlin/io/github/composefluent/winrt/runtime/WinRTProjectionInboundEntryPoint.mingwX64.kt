package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.rawValue

@PublishedApi
@OptIn(ExperimentalForeignApi::class)
internal fun winRTNativeProjectionInboundEntryPoint(pointer: COpaquePointer): RawAddress =
    RawAddress(pointer.rawValue.toLong())
