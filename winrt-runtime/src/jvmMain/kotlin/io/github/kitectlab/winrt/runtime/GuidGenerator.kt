package io.github.kitectlab.winrt.runtime

/**
 * JVM-side signature/GUID lookup corresponding to `.cswinrt/src/WinRT.Runtime/GuidGenerator.cs`.
 *
 * The JVM path uses annotations and explicit helper registration where the .NET runtime would use
 * `Type.GUID`, helper metadata, or generated static fields.
 */
object GuidGenerator {
    fun getGuid(
        type: Class<*>,
    ): Guid {
        type.registeredWinRtType()?.guid?.let { return it }
        type.registeredWinRtType()?.iid?.let { return it }
        val guidType = TypeExtensions.getGuidType(type)
        guidType.registeredWinRtType()?.guid?.let { return it }
        guidType.registeredWinRtType()?.iid?.let { return it }
        throw IllegalStateException("Unable to determine WinRT GUID for '${type.name}'.")
    }

    fun getIID(
        type: Class<*>,
    ): Guid {
        type.registeredWinRtType()?.iid?.let { return it }
        return getGuid(type)
    }

    fun getSignature(
        type: Class<*>,
    ): String {
        WinRtTypeClassifier.classify(type)?.let { return it.signature.render() }
        type.registeredWinRtType()?.signature?.let { return it }

        val guidType = TypeExtensions.getGuidType(type)
        guidType.registeredWinRtType()?.signature?.let { return it }

        if (TypeExtensions.isDelegate(type)) {
            return WinRtTypeSignature.delegate(getGuid(type)).render()
        }

        Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(type)?.let { defaultInterface ->
            val runtimeClassName = TypeNameSupport.inferRuntimeClassName(type)
                ?: throw IllegalStateException("Runtime class '${type.name}' is missing a registered runtime class name.")
            return WinRtTypeSignature.runtimeClass(
                runtimeClassName = runtimeClassName,
                defaultInterface = WinRtTypeSignature.guid(getGuid(defaultInterface)),
            ).render()
        }

        return WinRtTypeSignature.guid(getGuid(type)).render()
    }

    fun createIID(
        type: Class<*>,
    ): Guid {
        val signature = getSignature(type)
        return if (signature.startsWith("{") && signature.endsWith("}")) {
            Guid(signature.removePrefix("{").removeSuffix("}"))
        } else {
            ParameterizedInterfaceId.createFromSignature(signature)
        }
    }
}
