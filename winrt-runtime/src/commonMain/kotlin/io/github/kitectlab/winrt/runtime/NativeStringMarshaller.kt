package io.github.kitectlab.winrt.runtime

object NativeStringMarshaller {
    fun createMarshaler(value: String?): ReferencedHString? =
        if (value.isNullOrEmpty()) {
            null
        } else {
            HString.createReference(value)
        }

    fun getAbi(value: ReferencedHString?): NativePointer =
        value?.handle ?: NativeInterop.nullPointer

    fun getAbi(value: HString?): NativePointer =
        value?.handle ?: NativeInterop.nullPointer

    fun disposeMarshaler(value: ReferencedHString?) {
        value?.close()
    }

    fun disposeAbi(handle: NativePointer) {
        if (!NativeInterop.isNull(handle)) {
            WinRtPlatformApi.windowsDeleteStringRaw(handle)
        }
    }

    fun fromAbi(handle: NativePointer): String =
        if (NativeInterop.isNull(handle)) {
            ""
        } else {
            HString.fromHandle(handle, owner = false).toKString()
        }

    fun fromManaged(value: String?): HString? =
        value?.let(HString::create)
}
