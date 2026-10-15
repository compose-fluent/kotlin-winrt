package io.github.composefluent.winrt.runtime

internal object WinRTBorrowedReferenceSupport {
    // CsWinRT 3 WindowsRuntimeObjectMarshaller / WindowsRuntimeInterfaceMarshaller<T> access
    // the projected object's reference directly. Do not allocate an adapter and bound callbacks.
    // This returns a borrowed reference; callers retain it when they need independent ownership.
    fun tryGetReference(
        value: Any?,
        interfaceType: WinRTTypeHandle?,
    ): ComObjectReference? {
        val winrtObject = value as? IWinRTObject ?: return null
        if (!winrtObject.hasUnwrappableNativeObject) {
            return null
        }
        return when {
            interfaceType == null -> winrtObject.nativeObject
            winrtObject.isInterfaceImplemented(interfaceType, false) ->
                winrtObject.getObjectReferenceForType(interfaceType)

            else -> null
        }
    }
}
