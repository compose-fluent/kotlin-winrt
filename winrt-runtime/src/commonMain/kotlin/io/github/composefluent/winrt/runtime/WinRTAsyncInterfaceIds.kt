package io.github.composefluent.winrt.runtime

/**
 * Runtime-side async interface IDs and slot layouts corresponding to the async owner set in
 * `.cswinrt/src/WinRT.Runtime`.
 */
object WinRTAsyncInterfaceIds {
    val IAsyncInfo: Guid = Guid("00000036-0000-0000-C000-000000000046")
    val IAsyncAction: Guid = Guid("5A648006-843A-4DA9-865B-9D26E5DFAD7B")
    val IAsyncActionWithProgressGeneric: Guid = Guid("1F6DB258-E803-48A1-9546-EB7353398884")
    val IAsyncOperationGeneric: Guid = Guid("9FC2B0BB-E446-44E2-AA61-9CAB8F636AF2")
    val IAsyncOperationWithProgressGeneric: Guid = Guid("B5D036D7-E297-498F-BA60-0289E76E23DD")
    val AsyncActionCompletedHandler: Guid = Guid("A4ED5C81-76C9-40BD-8BE6-B1D90FB20AE7")
    val AsyncActionProgressHandlerGeneric: Guid = Guid("6D844858-0CFF-4590-AE89-95A5A5C8B4B8")
    val AsyncActionWithProgressCompletedHandlerGeneric: Guid = Guid("9C029F91-CC84-44FD-AC26-0A6C4E555281")
    val AsyncOperationCompletedHandlerGeneric: Guid = Guid("FCDCF02C-E5D8-4478-915A-4D90B74B83A5")
    val AsyncOperationProgressHandlerGeneric: Guid = Guid("55690902-0AAB-421A-8778-F8CE5026D758")
    val AsyncOperationWithProgressCompletedHandlerGeneric: Guid = Guid("E85DF41D-6AA7-46E3-A8E2-F009D840C627")
}

object WinRTAsyncInfoVftblSlots {
    const val Id = 6
    const val Status = 7
    const val ErrorCode = 8
    const val Cancel = 9
    const val Close = 10
}

object WinRTAsyncActionVftblSlots {
    const val PutCompleted = 11
    const val GetCompleted = 12
    const val GetResults = 13
}

object WinRTAsyncOperationVftblSlots {
    const val PutCompleted = 11
    const val GetCompleted = 12
    const val GetResults = 13
}

object WinRTAsyncActionWithProgressVftblSlots {
    const val PutProgress = 11
    const val GetProgress = 12
    const val PutCompleted = 13
    const val GetCompleted = 14
    const val GetResults = 15
}

object WinRTAsyncOperationWithProgressVftblSlots {
    const val PutProgress = 11
    const val GetProgress = 12
    const val PutCompleted = 13
    const val GetCompleted = 14
    const val GetResults = 15
}

enum class WinRTAsyncStatus(val abiValue: Int) {
    Started(0),
    Completed(1),
    Canceled(2),
    Error(3);

    companion object {
        fun fromAbi(value: Int): WinRTAsyncStatus =
            entries.firstOrNull { it.abiValue == value }
                ?: throw WinRTIllegalArgumentException(
                    "Unknown WinRT async status value: $value",
                    KnownHResults.E_INVALIDARG,
                )
    }
}
