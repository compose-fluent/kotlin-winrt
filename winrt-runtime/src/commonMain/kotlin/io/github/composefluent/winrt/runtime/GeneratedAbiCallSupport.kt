package io.github.composefluent.winrt.runtime

/** The generated equivalent of cswinrt's GC.KeepAlive on the receiver after the ABI call. */
inline fun <T> withWinRTAbiReference(reference: ComObjectReference, block: (RawComPtr) -> T): T =
    try {
        block(reference.pointer)
    } finally {
        winRTKeepAlive(reference)
    }

/** Pooled, cleared storage for one scalar out value; conversion remains in generated source. */
inline fun <T> withWinRTScalarResult(block: (RawAddress) -> T): T {
    val frame = acquireNativeScalarScratchFrame()
    return try {
        block(frame.pointer)
    } finally {
        frame.close()
    }
}
