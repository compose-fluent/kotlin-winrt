@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.internal.interopCallMarker

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun winRTKeepAlive(owner: Any?) {
    interopCallMarker(owner)
}
