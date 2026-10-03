package io.github.composefluent.winrt.runtime

internal object RawAbiResultSupport {
    fun <T> objectResult(
        invoke: (RawAddress) -> Int,
        wrap: (RawAddress) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invoke(resultOut)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return wrap(PlatformAbi.readPointer(resultOut))
        }

    fun hStringResult(
        invoke: (RawAddress) -> Int,
    ): HString =
        objectResult(
            invoke = invoke,
            wrap = { HString.fromHandle(it, owner = true) },
        )

    fun int32Result(
        invoke: (RawAddress) -> Int,
    ): Int =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return frame.readInt32()
        }

    fun uint32Result(
        invoke: (RawAddress) -> Int,
    ): UInt = int32Result(invoke).toUInt()

    fun booleanResult(
        invoke: (RawAddress) -> Int,
    ): Boolean =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return frame.readInt8().toInt() != 0
        }

    fun doubleResult(
        invoke: (RawAddress) -> Int,
    ): Double =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return frame.readDouble()
        }
}
