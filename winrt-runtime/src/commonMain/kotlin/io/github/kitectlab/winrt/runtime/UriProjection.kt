package io.github.kitectlab.winrt.runtime

@WindowsRuntimeType("rc(Windows.Foundation.Uri;{9e365e57-48b2-4160-956f-c7385120bbfc})")
internal object UriProjection {
    fun fromAbi(pointer: NativePointer): WinRtUri? {
        if (NativeInterop.isNull(pointer)) {
            return null
        }
        return IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true).asInspectable().use(::fromInspectable)
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
                    NativeInterop.confinedScope().use { scope ->
                        val resultOut = NativeInterop.allocatePointerSlot(scope)
                        val hr = factory.invokeAbi(
                            slot = 6,
                            descriptor = NativeFunctionDescriptor.of(
                                NativeValueLayout.JAVA_INT,
                                NativeValueLayout.ADDRESS,
                                NativeValueLayout.ADDRESS,
                                NativeValueLayout.ADDRESS,
                            ),
                            rawUri.handle,
                            resultOut,
                        )
                        WinRtPlatformApi.checkSucceededRaw(hr)
                        IInspectableReference(NativeInterop.readPointer(resultOut), IID.IInspectable)
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
        NativeInterop.confinedScope().use { scope ->
            val resultOut = NativeInterop.allocatePointerSlot(scope)
            val hr = inspectable.invokeAbi(
                slot = slot,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                resultOut,
            )
            WinRtPlatformApi.checkSucceededRaw(hr)
            val handle = NativeInterop.readPointer(resultOut)
            if (NativeInterop.isNull(handle)) {
                ""
            } else {
                HString.fromHandle(handle, owner = true).use(HString::toKString)
            }
        }
}

