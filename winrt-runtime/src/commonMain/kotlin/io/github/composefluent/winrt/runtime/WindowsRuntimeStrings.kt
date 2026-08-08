package io.github.composefluent.winrt.runtime

@PublishedApi
internal expect fun consumeOwnedHString(
    handle: RawAddress,
): String

class HString private constructor(
    val handle: RawAddress,
    private val owner: Boolean,
) : AutoCloseable {
    fun toKString(): String = PlatformAbi.readHString(handle)

    override fun close() {
        if (owner && !PlatformAbi.isNull(handle)) {
            WinRTPlatformApi.windowsDeleteStringRaw(handle)
        }
    }

    companion object {
        fun create(value: String): HString {
            if (!PlatformRuntime.isWindows) {
                error("HSTRING is only available on Windows.")
            }

            PlatformAbi.confinedScope().use { scope ->
                val utf16 = PlatformAbi.allocateUtf16(scope, value)
                val out = PlatformAbi.allocatePointerSlot(scope)
                WinRTPlatformApi.checkSucceededRaw(
                    WinRTPlatformApi.windowsCreateStringRaw(
                        utf16,
                        value.length,
                        out,
                    ),
                )
                return HString(PlatformAbi.readPointer(out), owner = true)
            }
        }

        fun createReference(value: String): ReferencedHString {
            val frame = acquireInitializedNativeHStringReferenceFrame(value)
            try {
                return ReferencedHString(
                    handle = frame.handle,
                    lifetime = frame,
                    transientOut = frame.transientOut,
                )
            } catch (error: Throwable) {
                frame.close()
                throw error
            }
        }

        fun fromHandle(handle: RawAddress, owner: Boolean): HString = HString(handle, owner)
    }
}

@PublishedApi
internal fun acquireInitializedNativeHStringReferenceFrame(value: String): NativeHStringReferenceFrame {
    if (!PlatformRuntime.isWindows) {
        error("HSTRING is only available on Windows.")
    }
    val frame = acquireNativeHStringReferenceFrame(value)
    try {
        frame.initializeReference(value.length)
        return frame
    } catch (error: Throwable) {
        frame.close()
        throw error
    }
}

class ReferencedHString internal constructor(
    val handle: RawAddress,
    private val lifetime: AutoCloseable?,
    @PublishedApi internal val transientOut: RawAddress = PlatformAbi.nullPointer,
) : AutoCloseable {
    fun toKString(): String = PlatformAbi.readHString(handle)

    override fun close() {
        lifetime?.close()
    }
}
