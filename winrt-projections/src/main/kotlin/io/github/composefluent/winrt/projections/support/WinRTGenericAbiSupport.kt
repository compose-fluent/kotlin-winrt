package io.github.composefluent.winrt.projections.support

internal data class GenericAbiDelegateEntry(
    val name: String,
    val sourceGenericType: String,
    val operation: String,
    val declaration: String,
    val abiParameterTypes: List<String>,
    val typeArrayShape: List<String>,
)
