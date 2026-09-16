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

/** Storage/lifetime only; generated abi_marshaler code owns the layout and conversion. */
inline fun <T> withWinRTStructStorage(sizeBytes: Long, alignmentBytes: Long, block: (RawAddress) -> T): T {
    val frame = acquireNativeStructScratchFrame(sizeBytes, alignmentBytes, clear = true)
    return try {
        block(frame.pointer)
    } finally {
        frame.close()
    }
}
