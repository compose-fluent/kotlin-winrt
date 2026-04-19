package io.github.kitectlab.winrt.runtime

import java.lang.reflect.Modifier

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
        val guidType = TypeExtensions.getGuidType(type)
        tryExtractGuid(guidType, "IID")?.let { return it }
        tryExtractGuid(guidType, "PIID")?.let { return it }
        guidType.getAnnotation(WinRtGuid::class.java)?.let { return Guid(it.value) }
        throw IllegalStateException("Unable to determine WinRT GUID for '${type.name}'.")
    }

    fun getIID(
        type: Class<*>,
    ): Guid {
        tryExtractGuid(type, "PIID")?.let { return it }
        return getGuid(type)
    }

    fun getSignature(
        type: Class<*>,
    ): String {
        if (type == Any::class.java) {
            return WinRtTypeSignature.object_().render()
        }
        if (type == String::class.java) {
            return WinRtTypeSignature.string().render()
        }
        if (type == java.lang.Byte.TYPE || type == java.lang.Byte::class.java) return WinRtTypeSignature.uint8().render()
        if (type == java.lang.Short.TYPE || type == java.lang.Short::class.java) return WinRtTypeSignature.int16().render()
        if (type == java.lang.Integer.TYPE || type == java.lang.Integer::class.java) return WinRtTypeSignature.int32().render()
        if (type == java.lang.Long.TYPE || type == java.lang.Long::class.java) return WinRtTypeSignature.int64().render()
        if (type == java.lang.Boolean.TYPE || type == java.lang.Boolean::class.java) return WinRtTypeSignature.boolean().render()
        if (type == java.lang.Character.TYPE || type == java.lang.Character::class.java) return WinRtTypeSignature.char16().render()
        if (type == java.lang.Float.TYPE || type == java.lang.Float::class.java) return WinRtTypeSignature.float32().render()
        if (type == java.lang.Double.TYPE || type == java.lang.Double::class.java) return WinRtTypeSignature.float64().render()
        if (type == Guid::class.java) return WinRtTypeSignature.guidValue().render()

        type.getAnnotation(WindowsRuntimeType::class.java)?.guidSignature?.takeIf { it.isNotBlank() }?.let { return it }

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

    private fun tryExtractGuid(
        type: Class<*>,
        fieldName: String,
    ): Guid? {
        val field = runCatching { type.getDeclaredField(fieldName) }.getOrNull() ?: return null
        if (!Modifier.isStatic(field.modifiers)) {
            return null
        }
        field.isAccessible = true
        val value = field.get(null) ?: return null
        return when (value) {
            is Guid -> value
            is String -> Guid(value)
            else -> null
        }
    }
}
