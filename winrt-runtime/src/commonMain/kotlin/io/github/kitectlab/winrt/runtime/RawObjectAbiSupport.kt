package io.github.kitectlab.winrt.runtime

internal object RawObjectAbiSupport {
    fun <T> nullableObjectResult(
        invoke: (NativePointer) -> Int,
        wrap: (NativePointer) -> T,
    ): T? =
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            val hResult = invoke(resultOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            val pointer = NativeInterop.readPointer(resultOut)
            return if (NativeInterop.isNull(pointer)) null else wrap(pointer)
        }

    fun indexOfResult(
        invoke: (indexOut: NativePointer, foundOut: NativePointer) -> Int,
    ): Pair<Boolean, UInt> =
        NativeInterop.confinedScope().use { scope ->
            val indexOut = NativeInterop.allocateInt32Slot(scope)
            val foundOut = NativeInterop.allocateInt8Slot(scope)
            val hResult = invoke(indexOut, foundOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return (NativeInterop.readInt8(foundOut).toInt() != 0) to NativeInterop.readInt32(indexOut).toUInt()
        }

    fun <T> objectGetManyResult(
        capacity: Int,
        invoke: (itemsOut: NativePointer, countOut: NativePointer) -> Int,
        wrap: (NativePointer) -> T?,
    ): List<T?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return NativeInterop.confinedScope().use { scope ->
            val itemsOut = NativeInterop.allocatePointerArray(scope, capacity)
            val countOut = NativeInterop.allocateInt32Slot(scope)
            val hResult = invoke(itemsOut, countOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            val actualCount = NativeInterop.readInt32(countOut)
            return List(actualCount) { index ->
                wrap(NativeInterop.readPointerAt(itemsOut, index))
            }
        }
    }

    fun replaceAllObjectArray(
        items: List<NativePointer>,
        invoke: (size: Int, itemsAbi: NativePointer) -> Int,
    ) {
        NativeInterop.confinedScope().use { scope ->
            val itemsAbi = NativeInterop.allocatePointerArray(scope, items.size)
            items.forEachIndexed { index, item ->
                NativeInterop.writePointerAt(itemsAbi, index, item)
            }
            val hResult = invoke(items.size, itemsAbi)
            WinRtPlatformApi.checkSucceededRaw(hResult)
        }
    }

    fun pointerPairResult(
        invoke: (firstOut: NativePointer, secondOut: NativePointer) -> Int,
    ): Pair<NativePointer, NativePointer> =
        NativeInterop.confinedScope().use { scope ->
            val firstOut = NativeInterop.allocatePointerSlot(scope)
            val secondOut = NativeInterop.allocatePointerSlot(scope)
            val hResult = invoke(firstOut, secondOut)
            WinRtPlatformApi.checkSucceededRaw(hResult)
            return NativeInterop.readPointer(firstOut) to NativeInterop.readPointer(secondOut)
        }
}
