package io.github.composefluent.winrt.runtime

/**
 * Pure runtime identity models shared across the KMP runtime split.
 *
 * These map to the non-platform-specific ownership in `.cswinrt/src/WinRT.Runtime`
 * and can live in `commonMain` before the heavier object-reference owners move.
 */
data class RuntimeClassId(val value: String)

/**
 * Threading classification of a known runtime class from WinMD MarshalingBehavior.
 * Mirrors CsWinRT 3 CreateObjectReferenceMarshalingType. Use Unknown for arbitrary
 * interface pointers; an interface IID alone does not establish the object's agility.
 * Generated IActivationFactory activation supplies this hint. Parameterized factories
 * and other ABI return paths currently retain dynamic classification.
 */
enum class WinRTObjectReferenceMarshalingType {
    Unknown,
    Agile,
    Standard,
}

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
