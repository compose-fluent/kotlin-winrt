package io.github.kitectlab.winrt.runtime

internal enum class ComAbiValueKind {
    Pointer,
    Int8,
    Int16,
    Int32,
    Int64,
    Float,
    Double,
}

internal data class ComMethodSignature(
    val explicitParameterKinds: List<ComAbiValueKind> = emptyList(),
    val resultKind: ComAbiValueKind = ComAbiValueKind.Int32,
) {
    companion object {
        fun of(
            vararg explicitParameterKinds: ComAbiValueKind,
        ): ComMethodSignature = ComMethodSignature(explicitParameterKinds.toList())
    }
}
