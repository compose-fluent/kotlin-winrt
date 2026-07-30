package io.github.composefluent.winrt.runtime

class HString private constructor(
    val handle: RawAddress,
    private val owner: Boolean,
) : AutoCloseable {
    fun toKString(): String =
        if (PlatformAbi.isNull(handle)) {
            ""
        } else acquireNativeScalarScratchFrame().use { frame ->
            val lengthOut = frame.pointer
            val buffer = WinRTPlatformApi.windowsGetStringRawBufferRaw(handle, lengthOut)
            PlatformAbi.readUtf16(buffer, PlatformAbi.readInt32(lengthOut))
        }

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
            if (!PlatformRuntime.isWindows) {
                error("HSTRING is only available on Windows.")
            }
            val frame = acquireNativeHStringReferenceFrame(value)
            try {
                val handle = if (value.isEmpty()) {
                    PlatformAbi.nullPointer
                } else {
                    WinRTPlatformApi.checkSucceededRaw(
                        WinRTPlatformApi.windowsCreateStringReferenceRaw(
                            utf16Chars = frame.utf16Chars,
                            length = value.length,
                            header = frame.header,
                            outHandle = frame.transientOut,
                        ),
                    )
                    PlatformAbi.readPointer(frame.transientOut)
                }
                PlatformAbi.writePointer(frame.transientOut, PlatformAbi.nullPointer)
                return ReferencedHString(
                    handle = handle,
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

class ReferencedHString internal constructor(
    val handle: RawAddress,
    private val lifetime: AutoCloseable?,
    @PublishedApi internal val transientOut: RawAddress = PlatformAbi.nullPointer,
) : AutoCloseable {
    fun toKString(): String =
        if (PlatformAbi.isNull(handle)) {
            ""
        } else {
            HString.fromHandle(handle, owner = false).toKString()
        }

    override fun close() {
        lifetime?.close()
    }
}
