package io.github.composefluent.winrt.runtime

/**
 * Pure runtime identity models shared across the KMP runtime split.
 *
 * These map to the non-platform-specific ownership in `.cswinrt/src/WinRT.Runtime`
 * and can live in `commonMain` before the heavier object-reference owners move.
 */
data class RuntimeClassId(val value: String)

data class WinRTTypeHandle(
    val projectedTypeName: String,
    val interfaceId: Guid,
)

data class WinRTInspectableInfo(
    val runtimeClassName: String?,
    val interfaceIds: List<Guid>,
)

interface IInspectable {
    val runtimeClassId: RuntimeClassId
}
