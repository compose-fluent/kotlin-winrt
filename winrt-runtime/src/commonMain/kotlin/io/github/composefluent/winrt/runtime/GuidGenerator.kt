package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/**
 * Shared signature/GUID lookup corresponding to `.cswinrt/src/WinRT.Runtime/GuidGenerator.cs`.
 */
object GuidGenerator {
    fun getGuid(
        type: KClass<*>,
    ): Guid {
        type.registeredWinRtType()?.guid?.let { return it }
        type.registeredWinRtType()?.iid?.let { return it }
        val guidType = TypeExtensions.getGuidType(type)
        guidType.registeredWinRtType()?.guid?.let { return it }
        guidType.registeredWinRtType()?.iid?.let { return it }
        throw IllegalStateException("Unable to determine WinRT GUID for '${type.typeDisplayName()}'.")
    }

    fun getIID(
        type: KClass<*>,
    ): Guid {
        type.registeredWinRtType()?.iid?.let { return it }
        return getGuid(type)
    }

    fun getSignature(
        type: KClass<*>,
    ): String {
        WinRtTypeClassifier.classify(type)?.let { return it.signature.render() }
        type.registeredWinRtType()?.signature?.let { return it }

        val guidType = TypeExtensions.getGuidType(type)
        guidType.registeredWinRtType()?.signature?.let { return it }

        if (TypeExtensions.isDelegate(type)) {
            return WinRtTypeSignature.delegate(getGuid(type)).render()
        }

        Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(type)?.let { defaultInterface ->
            val runtimeClassName = type.registeredWinRtType()?.runtimeClassName
                ?: Projections.findCustomAbiTypeNameForType(type)?.takeIf(Projections::isProjectedRuntimeClassName)
                ?: throw IllegalStateException("Runtime class '${type.typeDisplayName()}' is missing a registered runtime class name.")
            return WinRtTypeSignature.runtimeClass(
                runtimeClassName = runtimeClassName,
                defaultInterface = WinRtTypeSignature.guid(getGuid(defaultInterface)),
            ).render()
        }

        return WinRtTypeSignature.guid(getGuid(type)).render()
    }

    fun createIID(
        type: KClass<*>,
    ): Guid {
        val signature = getSignature(type)
        return if (signature.startsWith("{") && signature.endsWith("}")) {
            Guid(signature.removePrefix("{").removeSuffix("}"))
        } else {
            ParameterizedInterfaceId.createFromSignature(signature)
        }
    }
}
