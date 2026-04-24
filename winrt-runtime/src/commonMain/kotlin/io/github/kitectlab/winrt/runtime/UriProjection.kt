package io.github.kitectlab.winrt.runtime

@WindowsRuntimeType("rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})")
internal object UriProjection {
    fun fromAbi(pointer: RawAddress): WinRtUri? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }
        return IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use(::fromInspectable)
    }

    fun fromInspectable(inspectable: IInspectableReference): WinRtUri {
        val raw = getStringProperty(inspectable, slot = 16)
        return WinRtUri(raw)
    }

    fun createReference(
        value: WinRtUri,
        interfaceId: Guid = IID.IInspectable,
    ): ComObjectReference {
        val inspectable =
            ActivationFactory.get("Windows.Foundation.Uri", IID.UriRuntimeClassFactory).use { factory ->
                HString.create(value.toString()).use { rawUri ->
                    PlatformAbi.confinedScope().use { scope ->
                        val resultOut = PlatformAbi.allocatePointerSlot(scope)
                        factory.comPtr.throwIfDisposed()
                        val hr =
                            ComVtableInvoker.invokeArgs(
                                factory.comPtr.raw,
                                6,
                                rawUri.handle,
                                resultOut,
                            )
                        WinRtPlatformApi.checkSucceededRaw(hr)
                        IInspectableReference(PlatformAbi.readPointer(resultOut).asRawComPtr(), IID.IInspectable)
                    }
                }
            }

        if (interfaceId == IID.IInspectable) {
            return inspectable
        }

        return try {
            inspectable.queryInterface(interfaceId).getOrThrow()
        } finally {
            inspectable.close()
        }
    }

    private fun getStringProperty(
        inspectable: IInspectableReference,
        slot: Int,
    ): String =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            inspectable.comPtr.throwIfDisposed()
            val hr = ComVtableInvoker.invokeArgs(inspectable.comPtr.raw, slot, resultOut)
            WinRtPlatformApi.checkSucceededRaw(hr)
            val handle = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(handle)) {
                ""
            } else {
                HString.fromHandle(handle, owner = true).use(HString::toKString)
            }
        }
}
