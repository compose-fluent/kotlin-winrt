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
            WinRtPlatformApi.windowsDeleteStringRaw(handle)
        }
    }

    fun fromAbi(handle: RawAddress): String =
        if (PlatformAbi.isNull(handle)) {
            ""
        } else {
            HString.fromHandle(handle, owner = false).toKString()
        }

    fun fromManaged(value: String?): HString? =
        value?.let(HString::create)
}
