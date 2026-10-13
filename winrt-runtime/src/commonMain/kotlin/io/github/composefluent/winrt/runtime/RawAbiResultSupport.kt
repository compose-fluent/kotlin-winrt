package io.github.composefluent.winrt.runtime

internal object RawAbiResultSupport {
    inline fun <T> objectResult(
        invoke: (RawAddress) -> Int,
        wrap: (RawAddress) -> T,
    ): T =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return wrap(frame.readPointer())
        }

    inline fun hStringResult(
        invoke: (RawAddress) -> Int,
    ): HString =
        objectResult(
            invoke = invoke,
            wrap = { HString.fromHandle(it, owner = true) },
        )

    inline fun int32Result(
        invoke: (RawAddress) -> Int,
    ): Int =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return frame.readInt32()
        }

    inline fun uint32Result(
        invoke: (RawAddress) -> Int,
    ): UInt = int32Result(invoke).toUInt()

    inline fun booleanResult(
        invoke: (RawAddress) -> Int,
    ): Boolean =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return frame.readInt8().toInt() != 0
        }

    inline fun doubleResult(
        invoke: (RawAddress) -> Int,
    ): Double =
        acquireNativeScalarScratchFrame().use { frame ->
            val hResult = invoke(frame.pointer)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return frame.readDouble()
        }
}
