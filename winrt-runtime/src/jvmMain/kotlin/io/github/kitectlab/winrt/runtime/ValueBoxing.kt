@file:JvmName("PlatformValueBoxingInteropJvmCompat")

package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import kotlin.jvm.JvmName

internal fun WinRtReferenceReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
): WinRtReferenceReference =
    WinRtReferenceReference(pointer.asNativePointer(), interfaceId, preventReleaseOnDispose)

internal fun WinRtReferenceArrayReference(
    pointer: MemorySegment,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
): WinRtReferenceArrayReference =
    WinRtReferenceArrayReference(pointer.asNativePointer(), interfaceId, preventReleaseOnDispose)

internal fun WinRtPropertyValueReference(
    pointer: MemorySegment,
    preventReleaseOnDispose: Boolean = false,
): WinRtPropertyValueReference =
    WinRtPropertyValueReference(pointer.asNativePointer(), preventReleaseOnDispose)
