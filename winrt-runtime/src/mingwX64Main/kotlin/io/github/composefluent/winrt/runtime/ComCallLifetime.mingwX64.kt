@file:Suppress("NOTHING_TO_INLINE")

package io.github.composefluent.winrt.runtime

@PublishedApi
internal actual inline fun beginComCall(support: RawComObjectReferenceSupport) = support.beginCall()

@PublishedApi
internal actual inline fun endComCall(reference: ComObjectReference) {
    try {
        reference.comPtr.support.endCall()
    } finally {
        winRTKeepAlive(reference)
    }
}
