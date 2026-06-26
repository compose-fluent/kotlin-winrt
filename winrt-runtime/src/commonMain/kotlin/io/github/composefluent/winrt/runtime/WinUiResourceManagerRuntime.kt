package io.github.composefluent.winrt.runtime

internal object WinUiResourceManagerRuntime {
    val resourceManagerRequestedEventArgsIid: Guid = guidOf("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a")

    fun resourceManagerRequestedHandlerIid(): Guid {
        val argsSignature = WinRTTypeSignature.runtimeClass(
            resourceManagerRequestedEventArgsClassName,
            WinRTTypeSignature.guid(resourceManagerRequestedEventArgsIid),
        )
        val signature = WinRTTypeSignature.parameterizedInterface(
            typedEventHandlerGuid,
            WinRTTypeSignature.object_(),
            argsSignature,
        )
        return ParameterizedInterfaceId.createFromSignature(signature)
    }

    private const val typedEventHandlerGuid = "9de1c534-6ae1-11e0-84e1-18a905bcc53f"
    private const val resourceManagerRequestedEventArgsClassName =
        "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs"
}
