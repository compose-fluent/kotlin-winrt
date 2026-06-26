package io.github.composefluent.winrt.runtime

@WindowsRuntimeType("rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})")
internal object UriProjection {
    fun fromAbi(pointer: RawAddress): WinRTUri? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }
        val borrowed = IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true)
        return try {
            borrowed.asInspectable().use(::fromInspectable)
        } finally {
            borrowed.close()
        }
    }

    fun fromInspectable(inspectable: IInspectableReference): WinRTUri {
        val raw = getStringProperty(inspectable, slot = 16)
        return WinRTUri(raw)
    }

    fun createReference(
        value: WinRTUri,
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
                        WinRTPlatformApi.checkSucceededRaw(hr)
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
            WinRTPlatformApi.checkSucceededRaw(hr)
            val handle = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(handle)) {
                ""
            } else {
                HString.fromHandle(handle, owner = true).use(HString::toKString)
            }
        }
}
