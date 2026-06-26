package io.github.composefluent.winrt.runtime

internal object RawObjectAbiSupport {
    fun <T> nullableObjectResult(
        invoke: (RawAddress) -> Int,
        wrap: (RawAddress) -> T,
    ): T? =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invoke(resultOut)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            val pointer = PlatformAbi.readPointer(resultOut)
            return if (PlatformAbi.isNull(pointer)) null else wrap(pointer)
        }

    fun nullableAbiResult(
        invoke: (RawAddress) -> Int,
    ): RawAddress? =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invoke(resultOut)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            val pointer = PlatformAbi.readPointer(resultOut)
            return if (PlatformAbi.isNull(pointer)) null else pointer
        }

    fun indexOfResult(
        invoke: (indexOut: RawAddress, foundOut: RawAddress) -> Int,
    ): Pair<Boolean, UInt> =
        PlatformAbi.confinedScope().use { scope ->
            val indexOut = PlatformAbi.allocateInt32Slot(scope)
            val foundOut = PlatformAbi.allocateInt8Slot(scope)
            val hResult = invoke(indexOut, foundOut)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return (PlatformAbi.readInt8(foundOut).toInt() != 0) to PlatformAbi.readInt32(indexOut).toUInt()
        }

    fun <T> objectGetManyResult(
        capacity: Int,
        invoke: (itemsOut: RawAddress, countOut: RawAddress) -> Int,
        wrap: (RawAddress) -> T?,
    ): List<T?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return PlatformAbi.confinedScope().use { scope ->
            val itemsOut = PlatformAbi.allocatePointerArray(scope, capacity)
            val countOut = PlatformAbi.allocateInt32Slot(scope)
            val hResult = invoke(itemsOut, countOut)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            val actualCount = PlatformAbi.readInt32(countOut)
            return List(actualCount) { index ->
                wrap(PlatformAbi.readPointerAt(itemsOut, index))
            }
        }
    }

    fun replaceAllObjectArray(
        items: List<RawAddress>,
        invoke: (size: Int, itemsAbi: RawAddress) -> Int,
    ) {
        PlatformAbi.confinedScope().use { scope ->
            val itemsAbi = PlatformAbi.allocatePointerArray(scope, items.size)
            items.forEachIndexed { index, item ->
                PlatformAbi.writePointerAt(itemsAbi, index, item)
            }
            val hResult = invoke(items.size, itemsAbi)
            WinRTPlatformApi.checkSucceededRaw(hResult)
        }
    }

    fun pointerPairResult(
        invoke: (firstOut: RawAddress, secondOut: RawAddress) -> Int,
    ): Pair<RawAddress, RawAddress> =
        PlatformAbi.confinedScope().use { scope ->
            val firstOut = PlatformAbi.allocatePointerSlot(scope)
            val secondOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invoke(firstOut, secondOut)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            return PlatformAbi.readPointer(firstOut) to PlatformAbi.readPointer(secondOut)
        }
}
