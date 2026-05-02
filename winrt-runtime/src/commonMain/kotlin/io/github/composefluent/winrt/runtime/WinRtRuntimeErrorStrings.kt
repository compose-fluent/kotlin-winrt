package io.github.composefluent.winrt.runtime

/**
 * Runtime message owner corresponding to `.cswinrt/src/WinRT.Runtime/WinRTRuntimeErrorStrings.*`.
 *
 * Kotlin keeps the `.cswinrt` resource-key behavior, but currently narrows localized `.resx`
 * resources to English defaults plus key-only mode via [FeatureSwitches.useExceptionResourceKeys].
 */
internal object WinRtRuntimeErrorStrings {
    val ArgumentOutOfRange_Index: String
        get() = getResourceString(
            resourceKey = "ArgumentOutOfRange_Index",
            defaultMessage = "Index was out of range. Must be non-negative and less than the size of the collection.",
        )
    val ArgumentOutOfRange_IndexLargerThanMaxValue: String
        get() = getResourceString(
            resourceKey = "ArgumentOutOfRange_IndexLargerThanMaxValue",
            defaultMessage = "This collection cannot work with indices larger than Int32.MaxValue - 1 (0x7FFFFFFF - 1).",
        )
    val Argument_AddingDuplicate: String
        get() = getResourceString(
            resourceKey = "Argument_AddingDuplicate",
            defaultMessage = "An item with the same key has already been added.",
        )
    val Argument_AddingDuplicateWithKey: String
        get() = getResourceString(
            resourceKey = "Argument_AddingDuplicateWithKey",
            defaultMessage = "An item with the same key has already been added. Key: {0}",
        )
    val Argument_IndexOutOfArrayBounds: String
        get() = getResourceString(
            resourceKey = "Argument_IndexOutOfArrayBounds",
            defaultMessage = "The specified index is out of bounds of the specified array.",
        )
    val Argument_InsufficientSpaceToCopyCollection: String
        get() = getResourceString(
            resourceKey = "Argument_InsufficientSpaceToCopyCollection",
            defaultMessage = "The specified space is not sufficient to copy the elements from this Collection.",
        )
    val Arg_IndexOutOfRangeException: String
        get() = getResourceString(
            resourceKey = "Arg_IndexOutOfRangeException",
            defaultMessage = "Index was outside the bounds of the array.",
        )
    val Arg_KeyNotFound: String
        get() = getResourceString(
            resourceKey = "Arg_KeyNotFound",
            defaultMessage = "The given key was not present in the dictionary.",
        )
    val Arg_KeyNotFoundWithKey: String
        get() = getResourceString(
            resourceKey = "Arg_KeyNotFoundWithKey",
            defaultMessage = "The given key '{0}' was not present in the dictionary.",
        )
    val Arg_RankMultiDimNotSupported: String
        get() = getResourceString(
            resourceKey = "Arg_RankMultiDimNotSupported",
            defaultMessage = "Only single dimensional arrays are supported for the requested action.",
        )
    val InvalidOperation_CannotRemoveLastFromEmptyCollection: String
        get() = getResourceString(
            resourceKey = "InvalidOperation_CannotRemoveLastFromEmptyCollection",
            defaultMessage = "Cannot remove the last element from an empty collection.",
        )
    val InvalidOperation_CollectionBackingDictionaryTooLarge: String
        get() = getResourceString(
            resourceKey = "InvalidOperation_CollectionBackingDictionaryTooLarge",
            defaultMessage = "The collection backing this Dictionary contains too many elements.",
        )
    val InvalidOperation_CollectionBackingListTooLarge: String
        get() = getResourceString(
            resourceKey = "InvalidOperation_CollectionBackingListTooLarge",
            defaultMessage = "The collection backing this List contains too many elements.",
        )
    val InvalidOperation_EnumEnded: String
        get() = getResourceString(
            resourceKey = "InvalidOperation_EnumEnded",
            defaultMessage = "Enumeration already finished.",
        )
    val InvalidOperation_EnumFailedVersion: String
        get() = getResourceString(
            resourceKey = "InvalidOperation_EnumFailedVersion",
            defaultMessage = "Collection was modified; enumeration operation may not execute.",
        )
    val InvalidOperation_EnumNotStarted: String
        get() = getResourceString(
            resourceKey = "InvalidOperation_EnumNotStarted",
            defaultMessage = "Enumeration has not started. Call MoveNext.",
        )
    val NotSupported_KeyCollectionSet: String
        get() = getResourceString(
            resourceKey = "NotSupported_KeyCollectionSet",
            defaultMessage = "Mutating a key collection derived from a dictionary is not allowed.",
        )
    val NotSupported_ValueCollectionSet: String
        get() = getResourceString(
            resourceKey = "NotSupported_ValueCollectionSet",
            defaultMessage = "Mutating a value collection derived from a dictionary is not allowed.",
        )

    private fun getResourceString(
        resourceKey: String,
        defaultMessage: String,
    ): String = if (FeatureSwitches.useExceptionResourceKeys) resourceKey else defaultMessage
}
