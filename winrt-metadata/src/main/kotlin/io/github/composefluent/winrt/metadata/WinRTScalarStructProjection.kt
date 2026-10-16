package io.github.composefluent.winrt.metadata

/**
 * The scalar ABI adapter selected by the shared mapped-type table. Mirrors the
 * ownership of mapped_type/get_mapped_type in cswinrt/helpers.h: consumption and
 * authoring emitters use the same marshaler contract, without local type ladders.
 */
data class WinRTScalarStructProjection(
    val helperQualifiedName: String,
    val fromAbiFunctionName: String,
    val copyToFunctionName: String,
    val toAbiFunctionName: String,
    val fromAbiCarrierFunctionName: String,
    val abiKindName: String = "Int64",
    val abiCarrierQualifiedName: String = "kotlin.Long",
    val sizeBytes: Long = 8,
    val alignmentBytes: Int = 8,
)
