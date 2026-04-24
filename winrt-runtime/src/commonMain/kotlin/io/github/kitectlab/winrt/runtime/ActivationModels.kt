package io.github.kitectlab.winrt.runtime

data class ActivationResult(
    val hResult: HResult,
    val pointer: RawAddress,
) {
    val isSuccess: Boolean
        get() = hResult.isSuccess && !PlatformAbi.isNull(pointer)
}

internal fun NativePointerResult.toActivationResult(): ActivationResult =
    ActivationResult(HResult(hResultValue), pointer)

internal fun manifestFreeActivationCandidateDllNames(runtimeClassName: String): List<String> {
    val parts = runtimeClassName.split('.')
    if (parts.size < 2) {
        return emptyList()
    }

    return buildList {
        for (i in parts.lastIndex downTo 1) {
            add(parts.take(i).joinToString(".") + ".dll")
        }
    }.distinct()
}
