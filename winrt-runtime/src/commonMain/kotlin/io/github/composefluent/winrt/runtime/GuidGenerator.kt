package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

/**
 * Shared signature/GUID lookup corresponding to `.cswinrt/src/WinRT.Runtime/GuidGenerator.cs`.
 */
object GuidGenerator {
    fun getGuid(
        type: KClass<*>,
    ): Guid {
        type.registeredWinRTType()?.guid?.let { return it }
        type.registeredWinRTType()?.iid?.let { return it }
        val guidType = TypeExtensions.getGuidType(type)
        guidType.registeredWinRTType()?.guid?.let { return it }
        guidType.registeredWinRTType()?.iid?.let { return it }
        throw IllegalStateException("Unable to determine WinRT GUID for '${type.typeDisplayName()}'.")
    }

    fun getIID(
        type: KClass<*>,
    ): Guid {
        type.registeredWinRTType()?.iid?.let { return it }
        return getGuid(type)
    }

    fun getSignature(
        type: KClass<*>,
    ): String {
        WinRTTypeClassifier.classify(type)?.let { return it.signature.render() }
        type.registeredWinRTType()?.signature?.let { return it }

        val guidType = TypeExtensions.getGuidType(type)
        guidType.registeredWinRTType()?.signature?.let { return it }

        if (TypeExtensions.isDelegate(type)) {
            return WinRTTypeSignature.delegate(getGuid(type)).render()
        }

        Projections.tryGetDefaultInterfaceTypeForRuntimeClassType(type)?.let { defaultInterface ->
            val runtimeClassName = type.registeredWinRTType()?.runtimeClassName
                ?: Projections.findCustomAbiTypeNameForType(type)?.takeIf(Projections::isProjectedRuntimeClassName)
                ?: throw IllegalStateException("Runtime class '${type.typeDisplayName()}' is missing a registered runtime class name.")
            return WinRTTypeSignature.runtimeClass(
                runtimeClassName = runtimeClassName,
                defaultInterface = WinRTTypeSignature.guid(getGuid(defaultInterface)),
            ).render()
        }

        val runtimeClassName = type.registeredWinRTType()?.runtimeClassName
            ?: Projections.findCustomAbiTypeNameForType(type)?.takeIf(Projections::isProjectedRuntimeClassName)
        runtimeClassName
            ?.let(Projections::tryGetDefaultInterfaceSignatureForRuntimeClassName)
            ?.let { defaultInterfaceSignature ->
                return "rc($runtimeClassName;$defaultInterfaceSignature)"
            }

        return WinRTTypeSignature.guid(getGuid(type)).render()
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
