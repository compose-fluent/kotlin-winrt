package io.github.kitectlab.winrt.runtime

class HString private constructor(
    val handle: NativePointer,
    private val owner: Boolean,
) : AutoCloseable {
    fun toKString(): String =
        NativeInterop.confinedScope().use { scope ->
            val lengthOut = NativeInterop.allocateInt32Slot(scope)
            val buffer = WinRtPlatformApi.windowsGetStringRawBufferRaw(handle, lengthOut)
            NativeInterop.readUtf16(buffer, NativeInterop.readInt32(lengthOut))
        }

    override fun close() {
        if (owner && !NativeInterop.isNull(handle)) {
            WinRtPlatformApi.windowsDeleteStringRaw(handle)
        }
    }

    companion object {
        fun create(value: String): HString {
            if (!PlatformRuntime.isWindows) {
                error("HSTRING is only available on Windows.")
            }

            NativeInterop.confinedScope().use { scope ->
                val utf16 = NativeInterop.allocateUtf16(scope, value)
                val out = NativeInterop.allocatePointerSlot(scope)
                WinRtPlatformApi.checkSucceededRaw(
                    WinRtPlatformApi.windowsCreateStringRaw(
                        utf16,
                        value.length,
                        out,
                    ),
                )
                return HString(NativeInterop.readPointer(out), owner = true)
            }
        }

        fun createReference(value: String): ReferencedHString {
            if (!PlatformRuntime.isWindows) {
                error("HSTRING is only available on Windows.")
            }
            if (value.isEmpty()) {
                return ReferencedHString(
                    handle = NativeInterop.nullPointer,
                    lifetime = null,
                )
            }

            val scope = NativeInterop.confinedScope()
            try {
                val utf16 = NativeInterop.allocateUtf16(scope, value, nulTerminated = true)
                val header = NativeInterop.allocateBytes(scope, NativeInterop.hStringHeaderSizeBytes)
                val out = NativeInterop.allocatePointerSlot(scope)
                WinRtPlatformApi.checkSucceededRaw(
                    WinRtPlatformApi.windowsCreateStringReferenceRaw(
                        utf16Chars = utf16,
                        length = value.length,
                        header = header,
                        outHandle = out,
                    ),
                )
                return ReferencedHString(
                    handle = NativeInterop.readPointer(out),
                    lifetime = scope,
                )
            } catch (error: Throwable) {
                scope.close()
                throw error
            }
        }

        fun fromHandle(handle: NativePointer, owner: Boolean): HString = HString(handle, owner)
    }
}

class ReferencedHString internal constructor(
    val handle: NativePointer,
    private val lifetime: AutoCloseable?,
) : AutoCloseable {
    fun toKString(): String =
        if (NativeInterop.isNull(handle)) {
            ""
        } else {
            HString.fromHandle(handle, owner = false).toKString()
        }

    override fun close() {
        lifetime?.close()
    }
}
