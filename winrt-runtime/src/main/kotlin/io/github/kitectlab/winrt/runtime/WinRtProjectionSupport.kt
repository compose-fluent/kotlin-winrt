package io.github.kitectlab.winrt.runtime

object WinRtProjectionSupport {
    inline fun <T> withStaticInterface(
        runtimeClassName: String,
        interfaceId: Guid,
        block: (IUnknownReference) -> T,
    ): T =
        JvmWinRtRuntime.getActivationFactory(
            runtimeClassName = runtimeClassName,
            interfaceId = interfaceId,
        ).getOrThrow().use(block)

    fun activateUnknown(runtimeClassName: String): IUnknownReference =
        ActivationFactory.get(runtimeClassName).use { factory ->
            IUnknownReference(factory.activateInstance().pointer)
        }
}
