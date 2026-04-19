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

    inline fun <T> invokeStaticObjectMethodWithStringArg(
        runtimeClassName: String,
        interfaceId: Guid,
        slot: Int,
        value: String,
        wrap: (IUnknownReference) -> T,
    ): T = withStaticInterface(runtimeClassName, interfaceId) { statics ->
        wrap(statics.invokeObjectMethodWithStringArg(slot, value))
    }

    inline fun <T> invokeStaticObjectMethodWithBooleanArg(
        runtimeClassName: String,
        interfaceId: Guid,
        slot: Int,
        value: Boolean,
        wrap: (IUnknownReference) -> T,
    ): T = withStaticInterface(runtimeClassName, interfaceId) { statics ->
        wrap(statics.invokeObjectMethodWithBooleanArg(slot, value))
    }

    inline fun <T> invokeStaticObjectMethodWithDoubleArg(
        runtimeClassName: String,
        interfaceId: Guid,
        slot: Int,
        value: Double,
        wrap: (IUnknownReference) -> T,
    ): T = withStaticInterface(runtimeClassName, interfaceId) { statics ->
        wrap(statics.invokeObjectMethodWithDoubleArg(slot, value))
    }

    inline fun <T> tryInvokeStaticObjectMethodWithStringArg(
        runtimeClassName: String,
        interfaceId: Guid,
        slot: Int,
        value: String,
        wrap: (IUnknownReference) -> T,
    ): T? = withStaticInterface(runtimeClassName, interfaceId) { statics ->
        val (reference, succeeded) = statics.invokeTryParseObjectMethodWithStringArg(slot, value)
        if (!succeeded || reference == null) {
            reference?.close()
            null
        } else {
            wrap(reference)
        }
    }

    fun invokeStaticUInt32MethodWithInt32Arg(
        runtimeClassName: String,
        interfaceId: Guid,
        slot: Int,
        value: Int,
    ): UInt = withStaticInterface(runtimeClassName, interfaceId) { statics ->
        statics.invokeUInt32MethodWithInt32Arg(slot, value)
    }
}
