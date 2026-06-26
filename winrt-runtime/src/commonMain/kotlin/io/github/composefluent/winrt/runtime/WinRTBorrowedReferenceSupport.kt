package io.github.composefluent.winrt.runtime

internal data class BorrowableWinRTObject<T>(
    val hasUnwrappableNativeObject: Boolean,
    val nativeObject: T,
    val isInterfaceImplemented: (WinRTTypeHandle) -> Boolean,
    val getObjectReferenceForType: (WinRTTypeHandle) -> T,
)

internal object WinRTBorrowedReferenceSupport {
    fun <T> tryBorrowReference(
        value: Any?,
        interfaceType: WinRTTypeHandle?,
        unwrapWinRTObject: (Any) -> BorrowableWinRTObject<T>?,
        cloneReference: (T) -> T,
    ): T? {
        val winrtObject = value?.let(unwrapWinRTObject) ?: return null
        if (!winrtObject.hasUnwrappableNativeObject) {
            return null
        }
        return when {
            interfaceType == null -> cloneReference(winrtObject.nativeObject)
            winrtObject.isInterfaceImplemented(interfaceType) ->
                cloneReference(winrtObject.getObjectReferenceForType(interfaceType))

            else -> null
        }
    }
}
