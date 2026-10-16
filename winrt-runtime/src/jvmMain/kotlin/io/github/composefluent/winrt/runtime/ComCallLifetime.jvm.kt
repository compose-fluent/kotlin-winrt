@file:Suppress("NOTHING_TO_INLINE")

package io.github.composefluent.winrt.runtime

@PublishedApi
internal actual inline fun beginComCall(support: RawComObjectReferenceSupport) = beginComCallJvm(support)

@PublishedApi
internal actual inline fun endComCall(reference: ComObjectReference) = endComCallJvm(reference)

@PublishedApi
internal fun beginComCallJvm(support: RawComObjectReferenceSupport) = support.beginCall()

@PublishedApi
internal fun endComCallJvm(reference: ComObjectReference) {
    try {
        reference.comPtr.support.endCall()
    } finally {
        winRTKeepAlive(reference)
    }
}
