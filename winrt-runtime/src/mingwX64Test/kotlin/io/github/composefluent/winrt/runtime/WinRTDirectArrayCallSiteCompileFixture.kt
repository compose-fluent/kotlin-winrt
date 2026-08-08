package io.github.composefluent.winrt.runtime

private data class NativeDirectArrayStruct(
    val value: Int,
) {
    @WinRTProjectionAbiType(
        name = "io.github.composefluent.winrt.runtime.NativeDirectArrayStruct",
        kind = WinRTProjectionAbiTypeKind.STRUCT,
        carrier = WinRTProjectionAbiCarrier.INT32,
        size = 4,
        alignment = 4,
    )
    companion object Metadata {
        fun copyTo(value: NativeDirectArrayStruct, destination: RawAddress) {
            PlatformAbi.writeInt32(destination, value.value)
        }

        fun fromAbi(source: RawAddress): NativeDirectArrayStruct =
            NativeDirectArrayStruct(PlatformAbi.readInt32(source))

        fun disposeAbi(source: RawAddress) {
            PlatformAbi.pointerKey(source)
        }
    }
}

private interface NativeDirectUnknownProjection {
    companion object Metadata {
        val TYPE_HANDLE: WinRTTypeHandle = WinRTTypeHandle(
            "io.github.composefluent.winrt.runtime.NativeDirectUnknownProjection",
            Guid("11111111-2222-3333-4444-555555555555"),
        )

        fun wrap(reference: IUnknownReference): NativeDirectUnknownProjection =
            NativeDirectUnknownProjectionImpl(reference)
    }
}

private class NativeDirectUnknownProjectionImpl(
    private val reference: IUnknownReference,
) : NativeDirectUnknownProjection,
    IWinRTObject {
    override val nativeObject: ComObjectReference
        get() = reference
}

private object NativeDirectArrayCallSiteCompileFixture {
    @WinRTProjectionCallSite
    fun consumeLongs(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.PASS_ARRAY)
        values: Array<Long>,
    ): Unit = TODO("native direct primitive array input fixture")

    @WinRTProjectionCallSite
    fun produceLongs(reference: ComObjectReference, slot: Int): Array<Long> =
        TODO("native direct primitive array output fixture")

    @WinRTProjectionCallSite
    fun consumeStructs(
        reference: ComObjectReference,
        slot: Int,
        @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.PASS_ARRAY)
        values: Array<NativeDirectArrayStruct>,
    ): Unit = TODO("native direct struct array input fixture")

    @WinRTProjectionCallSite
    fun produceStructs(reference: ComObjectReference, slot: Int): Array<NativeDirectArrayStruct> =
        TODO("native direct struct array output fixture")

    @WinRTProjectionCallSite
    fun consumeProjection(
        reference: ComObjectReference,
        slot: Int,
        value: NativeDirectUnknownProjection?,
    ): Unit = TODO("native direct projection input fixture")

    @WinRTProjectionCallSite
    fun produceProjection(reference: ComObjectReference, slot: Int): NativeDirectUnknownProjection =
        TODO("native direct projection output fixture")
}
