package io.github.kitectlab.winrt.metadata

enum class WinRtMetadataParameterCategory {
    In,
    Ref,
    Out,
    PassArray,
    FillArray,
    ReceiveArray,
}

data class WinRtMethodSignatureParameter(
    val parameter: WinRtParameterDefinition,
    val category: WinRtMetadataParameterCategory,
)

data class WinRtMethodSignatureDescriptor(
    val method: WinRtMethodDefinition,
    val returnType: WinRtTypeRef,
    val returnParameterName: String = "__return_value__",
    val parameters: List<WinRtMethodSignatureParameter>,
) {
    fun hasParams(): Boolean = parameters.isNotEmpty()
}

data class WinRtPropertyAccessorDescriptor(
    val getterMethodName: String?,
    val setterMethodName: String?,
    val getterMethodRowId: Int?,
    val setterMethodRowId: Int?,
    val isStatic: Boolean,
    val hasValidAccessors: Boolean,
)

data class WinRtEventAccessorDescriptor(
    val addMethodName: String?,
    val removeMethodName: String?,
    val addMethodRowId: Int?,
    val removeMethodRowId: Int?,
    val isStatic: Boolean,
    val hasValidAccessors: Boolean,
)

class WinRtMetadataSemanticHelpers(private val model: WinRtMetadataModel) {
    private val normalizedModel = model.normalized()
    private val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)

    fun methodSignature(method: WinRtMethodDefinition): WinRtMethodSignatureDescriptor =
        WinRtMethodSignatureDescriptor(
            method = method,
            returnType = method.returnType,
            parameters = method.parameters.map { parameter ->
                WinRtMethodSignatureParameter(
                    parameter = parameter,
                    category = parameterCategory(parameter),
                )
            },
        )

    fun parameterCategory(parameter: WinRtParameterDefinition): WinRtMetadataParameterCategory {
        return metadataParameterCategoryFor(parameter)
    }

    fun isRemoveOverload(method: WinRtMethodDefinition): Boolean =
        method.isRemoveOverload || (method.isSpecialName && method.name.startsWith("remove_"))

    fun isNoException(method: WinRtMethodDefinition): Boolean =
        isRemoveOverload(method) || method.isNoException

    fun isNoException(property: WinRtPropertyDefinition): Boolean = property.isNoException

    fun isExclusiveTo(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Interface && type.isExclusiveTo

    fun isOverridable(implementation: WinRtInterfaceImplementationDefinition): Boolean =
        implementation.isOverridable

    fun isProjectionInternal(type: WinRtTypeDefinition): Boolean = type.isProjectionInternal

    fun isFlagsEnum(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Enum && type.customAttributes.any { it.typeName == SYSTEM_FLAGS_ATTRIBUTE }

    fun isApiContractType(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Struct && type.isApiContract

    fun isAttributeType(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.RuntimeClass && type.isAttributeType

    fun isParameterizedType(type: WinRtTypeDefinition): Boolean =
        type.genericParameterCount > 0 || type.genericParameters.isNotEmpty()

    fun isStatic(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.RuntimeClass && type.isStaticType

    fun isConstructor(method: WinRtMethodDefinition): Boolean =
        method.isRuntimeSpecialName && method.name == ".ctor"

    fun hasDefaultConstructor(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.RuntimeClass &&
            type.methods.any { method -> isConstructor(method) && method.parameters.isEmpty() }

    fun isSpecial(method: WinRtMethodDefinition): Boolean =
        method.isSpecialName || method.isRuntimeSpecialName

    fun isStatic(method: WinRtMethodDefinition): Boolean = method.isStatic

    fun getDelegateInvoke(type: WinRtTypeDefinition): WinRtMethodDefinition? =
        type.methods.firstOrNull { method -> method.name == "Invoke" && method.isSpecialName }
            ?: type.methods.firstOrNull { method -> method.name == "Invoke" }

    fun getDefaultInterface(type: WinRtTypeDefinition): WinRtTypeRef? =
        type.defaultInterface
            ?: type.implementedInterfaces.firstOrNull { it.isDefault }?.interfaceType
            ?: if (type.implementedInterfaces.isEmpty()) null else {
                throw IllegalArgumentException("Type '${type.qualifiedName}' does not have a default interface")
            }

    fun getPropertyMethods(property: WinRtPropertyDefinition): WinRtPropertyAccessorDescriptor =
        WinRtPropertyAccessorDescriptor(
            getterMethodName = property.getterMethodName,
            setterMethodName = property.setterMethodName,
            getterMethodRowId = property.getterMethodRowId,
            setterMethodRowId = property.setterMethodRowId,
            isStatic = property.isStatic,
            hasValidAccessors = property.hasValidAccessors,
        )

    fun getEventMethods(event: WinRtEventDefinition): WinRtEventAccessorDescriptor =
        WinRtEventAccessorDescriptor(
            addMethodName = event.addMethodName,
            removeMethodName = event.removeMethodName,
            addMethodRowId = event.addMethodRowId,
            removeMethodRowId = event.removeMethodRowId,
            isStatic = event.isStatic,
            hasValidAccessors = event.hasValidAccessors,
        )

    fun resolveType(type: WinRtTypeRef, currentNamespace: String): WinRtTypeDefinition? {
        val normalized = type.normalized()
        val qualifiedName = normalized.qualifiedName ?: return null
        return typesByQualifiedName[qualifiedName]
            ?: if ('.' !in qualifiedName) typesByQualifiedName["$currentNamespace.$qualifiedName"] else null
    }

    companion object {
        private const val SYSTEM_FLAGS_ATTRIBUTE = "System.FlagsAttribute"
    }
}

fun WinRtMetadataModel.semanticHelpers(): WinRtMetadataSemanticHelpers =
    WinRtMetadataSemanticHelpers(this)

internal fun metadataParameterCategoryFor(parameter: WinRtParameterDefinition): WinRtMetadataParameterCategory {
    val type = parameter.type.normalized()
    if (type.kind == WinRtTypeRefKind.Array) {
        return when {
            parameter.isInParameter -> WinRtMetadataParameterCategory.PassArray
            parameter.typeIsByRef -> WinRtMetadataParameterCategory.ReceiveArray
            parameter.isOutParameter -> WinRtMetadataParameterCategory.FillArray
            parameter.direction == WinRtParameterDirection.Out -> WinRtMetadataParameterCategory.FillArray
            else -> WinRtMetadataParameterCategory.PassArray
        }
    }
    return when {
        parameter.isOutParameter -> WinRtMetadataParameterCategory.Out
        parameter.typeIsByRef -> WinRtMetadataParameterCategory.Ref
        else -> WinRtMetadataParameterCategory.In
    }
}
