package io.github.kitectlab.winrt.runtime

/**
 * Runtime-side async interface IDs and slot layouts corresponding to the async owner set in
 * `.cswinrt/src/WinRT.Runtime`.
 */
object WinRtAsyncInterfaceIds {
    val IAsyncInfo: Guid = Guid("00000036-0000-0000-C000-000000000046")
    val IAsyncAction: Guid = Guid("5A648006-843A-4DA9-865B-9D26E5DFAD7B")
    val IAsyncOperationGeneric: Guid = Guid("9FC2B0BB-E446-44E2-AA61-9CAB8F636AF2")
    val AsyncActionCompletedHandler: Guid = Guid("A4ED5C81-76C9-40BD-8BE6-B1D90FB20AE7")
    val AsyncOperationCompletedHandlerGeneric: Guid = Guid("FCDCF02C-E5D8-4478-915A-4D90B74B83A5")
}

object WinRtAsyncInfoVftblSlots {
    const val Id = 6
    const val Status = 7
    const val ErrorCode = 8
    const val Cancel = 9
    const val Close = 10
}

object WinRtAsyncActionVftblSlots {
    const val PutCompleted = 11
    const val GetCompleted = 12
    const val GetResults = 13
}

object WinRtAsyncOperationVftblSlots {
    const val PutCompleted = 11
    const val GetCompleted = 12
    const val GetResults = 13
}

enum class WinRtAsyncStatus(val abiValue: Int) {
    Started(0),
    Completed(1),
    Canceled(2),
    Error(3);

    companion object {
        fun fromAbi(value: Int): WinRtAsyncStatus =
            entries.firstOrNull { it.abiValue == value }
                ?: throw WinRtIllegalArgumentException(
                    "Unknown WinRT async status value: $value",
                    KnownHResults.E_INVALIDARG,
                )
    }
}
