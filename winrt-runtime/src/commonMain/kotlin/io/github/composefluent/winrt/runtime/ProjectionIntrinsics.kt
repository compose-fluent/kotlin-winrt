package io.github.composefluent.winrt.runtime

/** The fixed runtime-owned subset of the shared projection call-site contract. */
object WinRTProjectionIntrinsic {
    @WinRTProjectionCallSite
    fun getString(reference: ComObjectReference, slot: Int): String = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun getBoolean(reference: ComObjectReference, slot: Int): Boolean = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite(hResult = WinRTCallSiteHResultPolicy.IGNORE)
    fun getNoExceptionBoolean(reference: ComObjectReference, slot: Int): Boolean =
        TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun getInt32(reference: ComObjectReference, slot: Int): Int = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite(returnAbiType = "kotlin.UInt")
    fun getUInt32(reference: ComObjectReference, slot: Int): UInt = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun getInt64(reference: ComObjectReference, slot: Int): Long = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite(returnAbiType = "kotlin.ULong")
    fun getUInt64(reference: ComObjectReference, slot: Int): ULong = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun getFloat(reference: ComObjectReference, slot: Int): Float = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun getDouble(reference: ComObjectReference, slot: Int): Double = TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun setString(reference: ComObjectReference, slot: Int, value: String): Unit =
        TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun setInt32(reference: ComObjectReference, slot: Int, value: Int): Unit =
        TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun setUInt32(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(abiType = "kotlin.UInt") value: UInt,
    ): Unit =
        TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun setInt64(reference: ComObjectReference, slot: Int, value: Long): Unit =
        TODO("Lowered while building winrt-runtime")

    @WinRTProjectionCallSite
    fun setUInt64(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(abiType = "kotlin.ULong") value: ULong,
    ): Unit =
        TODO("Lowered while building winrt-runtime")
}
