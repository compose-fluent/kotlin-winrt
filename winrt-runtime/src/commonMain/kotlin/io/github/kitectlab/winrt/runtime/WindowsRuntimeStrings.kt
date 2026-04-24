package io.github.kitectlab.winrt.runtime

class HString private constructor(
    val handle: RawAddress,
    private val owner: Boolean,
) : AutoCloseable {
    fun toKString(): String =
        PlatformAbi.confinedScope().use { scope ->
            val lengthOut = PlatformAbi.allocateInt32Slot(scope)
            val buffer = WinRtPlatformApi.windowsGetStringRawBufferRaw(handle, lengthOut)
            PlatformAbi.readUtf16(buffer, PlatformAbi.readInt32(lengthOut))
        }

    override fun close() {
        if (owner && !PlatformAbi.isNull(handle)) {
            WinRtPlatformApi.windowsDeleteStringRaw(handle)
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
                WinRtPlatformApi.checkSucceededRaw(
                    WinRtPlatformApi.windowsCreateStringRaw(
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
            if (value.isEmpty()) {
                return ReferencedHString(
                    handle = PlatformAbi.nullPointer,
                    lifetime = null,
                )
            }

            val scope = PlatformAbi.confinedScope()
            try {
                val utf16 = PlatformAbi.allocateUtf16(scope, value, nulTerminated = true)
                val header = PlatformAbi.allocateBytes(scope, PlatformAbi.hStringHeaderSizeBytes)
                val out = PlatformAbi.allocatePointerSlot(scope)
                WinRtPlatformApi.checkSucceededRaw(
                    WinRtPlatformApi.windowsCreateStringReferenceRaw(
                        utf16Chars = utf16,
                        length = value.length,
                        header = header,
                        outHandle = out,
                    ),
                )
                return ReferencedHString(
                    handle = PlatformAbi.readPointer(out),
                    lifetime = scope,
                )
            } catch (error: Throwable) {
                scope.close()
                throw error
            }
        }

        fun fromHandle(handle: RawAddress, owner: Boolean): HString = HString(handle, owner)
    }
}

class ReferencedHString internal constructor(
    val handle: RawAddress,
    private val lifetime: AutoCloseable?,
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
