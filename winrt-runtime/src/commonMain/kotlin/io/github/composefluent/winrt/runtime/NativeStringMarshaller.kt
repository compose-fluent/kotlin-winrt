package io.github.composefluent.winrt.runtime

object NativeStringMarshaller {
    fun createMarshaler(value: String?): ReferencedHString? =
        if (value.isNullOrEmpty()) {
            null
        } else {
            HString.createReference(value)
        }

    fun getAbi(value: ReferencedHString?): RawAddress =
        value?.handle ?: PlatformAbi.nullPointer

    fun getAbi(value: HString?): RawAddress =
        value?.handle ?: PlatformAbi.nullPointer

    fun disposeMarshaler(value: ReferencedHString?) {
        value?.close()
    }

    fun disposeAbi(handle: RawAddress) {
        if (!PlatformAbi.isNull(handle)) {
            WinRTPlatformApi.windowsDeleteStringRaw(handle)
        }
    }

    fun fromAbi(handle: RawAddress): String = PlatformAbi.readHString(handle)

    fun fromManaged(value: String?): HString? =
        value?.let(HString::create)
}
