package io.github.kitectlab.winrt.runtime

internal enum class ComReferenceWrapperKind {
    Unknown,
    Inspectable,
    ActivationFactory,
}

internal object ComReferenceWrapperSupport {
    fun kindForInterfaceId(interfaceId: Guid): ComReferenceWrapperKind =
        when (interfaceId) {
            IID.IInspectable -> ComReferenceWrapperKind.Inspectable
            IID.IActivationFactory -> ComReferenceWrapperKind.ActivationFactory
            else -> ComReferenceWrapperKind.Unknown
        }

    fun <T> wrap(
        kind: ComReferenceWrapperKind,
        pointer: NativePointer,
        interfaceId: Guid,
        wrapUnknown: (NativePointer, Guid) -> T,
        wrapInspectable: (NativePointer, Guid) -> T,
        wrapActivationFactory: (NativePointer, Guid) -> T,
    ): T =
        when (kind) {
            ComReferenceWrapperKind.Unknown -> wrapUnknown(pointer, interfaceId)
            ComReferenceWrapperKind.Inspectable -> wrapInspectable(pointer, interfaceId)
            ComReferenceWrapperKind.ActivationFactory -> wrapActivationFactory(pointer, interfaceId)
        }
}
