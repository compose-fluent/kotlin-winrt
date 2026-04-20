package io.github.kitectlab.winrt.runtime

internal object RawAbiResultSupport {
    fun <T> objectResult(
        invoke: (NativePointer) -> Int,
        wrap: (NativePointer) -> T,
    ): T =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return wrap(NativeInterop.readPointer(resultOut))
        }

    fun hStringResult(
        invoke: (NativePointer) -> Int,
    ): HString =
        objectResult(
            invoke = invoke,
            wrap = { HString.fromHandle(it, owner = true) },
        )

    fun int32Result(
        invoke: (NativePointer) -> Int,
    ): Int =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocateInt32Slot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return NativeInterop.readInt32(resultOut)
        }

    fun uint32Result(
        invoke: (NativePointer) -> Int,
    ): UInt = int32Result(invoke).toUInt()
}
