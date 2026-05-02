package io.github.composefluent.winrt.runtime

internal object RawAbiResultSupport {
    fun <T> objectResult(
        invoke: (RawAddress) -> Int,
        wrap: (RawAddress) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
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
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt32Slot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return PlatformAbi.readInt32(resultOut)
        }

    fun uint32Result(
        invoke: (RawAddress) -> Int,
    ): UInt = int32Result(invoke).toUInt()

    fun booleanResult(
        invoke: (RawAddress) -> Int,
    ): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return PlatformAbi.readInt8(resultOut).toInt() != 0
        }

    fun doubleResult(
        invoke: (RawAddress) -> Int,
    ): Double =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateDoubleSlot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return PlatformAbi.readDouble(resultOut)
        }
}
