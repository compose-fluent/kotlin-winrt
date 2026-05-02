package io.github.composefluent.winrt.runtime

/**
 * Kotlin-side equivalent of `.cswinrt/src/WinRT.Runtime/CastExtensions.cs`.
 *
 * The Kotlin runtime cannot mirror C# generic interface casting literally, so the cast path is keyed by the
 * runtime `WinRtTypeHandle` used elsewhere in `winrt-runtime`.
 */
fun Any.winrtAs(typeHandle: WinRtTypeHandle): Any {
    if (this is IWinRTObject && primaryTypeHandle == typeHandle) {
        return this
    }

    val objRef = ComWrappersSupport.tryUnwrapObject(this)
        ?: throw IllegalArgumentException(
            "The source object type ('${this::class.qualifiedName}') is not a projected WinRT type.",
        )

    objRef.use {
        return ComWrappersSupport.createRcwForComObject(it.getRefPointer().asRawAddress(), typeHandle)
            ?: throw IllegalArgumentException(
                "Unable to create a WinRT wrapper for '${typeHandle.projectedTypeName}'.",
            )
    }
}
