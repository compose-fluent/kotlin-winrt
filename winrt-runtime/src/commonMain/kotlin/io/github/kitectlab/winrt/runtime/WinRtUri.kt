package io.github.kitectlab.winrt.runtime

data class WinRtUri(
    val raw: String,
) {
    override fun toString(): String = raw

    object Metadata {
        val DEFAULT_INTERFACE_IID: Guid = Guid("9E365E57-48B2-4160-956F-C7385120BBFC")

        fun wrap(instance: IInspectableReference): WinRtUri =
            UriProjection.fromInspectable(instance)
    }
}
