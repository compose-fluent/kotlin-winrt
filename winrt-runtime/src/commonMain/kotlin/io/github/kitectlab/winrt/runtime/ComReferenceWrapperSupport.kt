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
        pointer: RawComPtr,
        interfaceId: Guid,
        wrapUnknown: (RawComPtr, Guid) -> T,
        wrapInspectable: (RawComPtr, Guid) -> T,
        wrapActivationFactory: (RawComPtr, Guid) -> T,
        ): T =
        when (kind) {
            ComReferenceWrapperKind.Unknown -> wrapUnknown(pointer, interfaceId)
            ComReferenceWrapperKind.Inspectable -> wrapInspectable(pointer, interfaceId)
            ComReferenceWrapperKind.ActivationFactory -> wrapActivationFactory(pointer, interfaceId)
        }

    fun <T> wrap(
        kind: ComReferenceWrapperKind,
        comPtr: ComPtr,
        wrapUnknown: (ComPtr) -> T,
        wrapInspectable: (ComPtr) -> T,
        wrapActivationFactory: (ComPtr) -> T,
    ): T =
        when (kind) {
            ComReferenceWrapperKind.Unknown -> wrapUnknown(comPtr)
            ComReferenceWrapperKind.Inspectable -> wrapInspectable(comPtr)
            ComReferenceWrapperKind.ActivationFactory -> wrapActivationFactory(comPtr)
        }
}
