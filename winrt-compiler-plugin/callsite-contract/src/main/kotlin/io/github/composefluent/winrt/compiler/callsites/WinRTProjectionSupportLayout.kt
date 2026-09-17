package io.github.composefluent.winrt.compiler.callsites

/** Shared source-host/IR registration layout; the base anchor also hosts one chunk. */
object WinRTProjectionSupportLayout {
    const val REGISTRAR_CHUNK_SIZE: Int = 128
    private const val MAX_ANCHOR_FILES: Int = 17

    fun anchorFileCount(registrationCount: Int): Int {
        require(registrationCount >= 0)
        return ((registrationCount.toLong() + REGISTRAR_CHUNK_SIZE - 1) / REGISTRAR_CHUNK_SIZE)
            .coerceIn(1, MAX_ANCHOR_FILES.toLong()).toInt()
    }
}
