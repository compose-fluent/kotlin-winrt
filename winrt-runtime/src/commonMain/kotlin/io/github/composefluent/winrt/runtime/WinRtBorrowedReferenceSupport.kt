package io.github.composefluent.winrt.runtime

internal data class BorrowableWinRtObject<T>(
    val hasUnwrappableNativeObject: Boolean,
    val nativeObject: T,
    val isInterfaceImplemented: (WinRtTypeHandle) -> Boolean,
    val getObjectReferenceForType: (WinRtTypeHandle) -> T,
)

internal object WinRtBorrowedReferenceSupport {
    fun <T> tryBorrowReference(
        value: Any?,
        interfaceType: WinRtTypeHandle?,
        unwrapWinRtObject: (Any) -> BorrowableWinRtObject<T>?,
        cloneReference: (T) -> T,
    ): T? {
        val winrtObject = value?.let(unwrapWinRtObject) ?: return null
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
