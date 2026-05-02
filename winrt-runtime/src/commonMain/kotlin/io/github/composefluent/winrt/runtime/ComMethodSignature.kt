package io.github.composefluent.winrt.runtime

sealed class ComAbiValueKind {
    data object Pointer : ComAbiValueKind()
    data object Int8 : ComAbiValueKind()
    data object Int16 : ComAbiValueKind()
    data object Int32 : ComAbiValueKind()
    data object Int64 : ComAbiValueKind()
    data object Float : ComAbiValueKind()
    data object Double : ComAbiValueKind()
    data class Struct(val layout: NativeAbiLayout) : ComAbiValueKind()
}

data class ComMethodSignature(
    val explicitParameterKinds: List<ComAbiValueKind> = emptyList(),
    val resultKind: ComAbiValueKind = ComAbiValueKind.Int32,
) {
    companion object {
        fun of(
            vararg explicitParameterKinds: ComAbiValueKind,
        ): ComMethodSignature = ComMethodSignature(explicitParameterKinds.toList())
    }
}
