package io.github.composefluent.winrt.runtime.consumer

import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.IID
import io.github.composefluent.winrt.runtime.PlatformAbi

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--invoke-invalid-proof") {
        val reference = ComObjectReference(
            pointer = PlatformAbi.nullComPtr,
            interfaceId = IID.IUnknown,
            preventReleaseOnDispose = true,
        )
        consumeRuntimeOwnedWrappers(reference, slot = 6)
    }
    println("published-runtime-only-consumer")
}
