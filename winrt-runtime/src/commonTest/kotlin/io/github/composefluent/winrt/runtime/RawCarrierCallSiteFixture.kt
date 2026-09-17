package io.github.composefluent.winrt.runtime

/** Fixed ABI fixtures for the same vtable shapes exercised by generated scalar source. */
internal object RawCarrierCallSiteFixture {
    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Long, arg1: Long): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Int, arg1: Long): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Float): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Double): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Float, arg1: Long): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Double, arg1: Long): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Byte, arg1: Short, arg2: Short, arg3: Float): Int =
        TODO("Fixed WinRT ABI call")

    @WinRTAbiCallSite
    fun invoke(instance: RawComPtr, slot: Int, arg0: Int, arg1: Int): Int =
        TODO("Fixed WinRT ABI call")
}

