package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy

/** Generator-owned WinMD composition model. Nothing in this model is emitted as CallSite metadata. */
internal enum class WinRTProjectionCallSiteAbiCarrier {
    ADDRESS,
    INT8,
    INT16,
    INT32,
    INT64,
    FLOAT32,
    FLOAT64,
}

internal enum class WinRTProjectionCallSiteRecipeKind {
    VALUE,
    HSTRING,
    GUID,
    ENUM,
    STRUCT,
    COM_REFERENCE,
    ARRAY,
    PROJECTION,
}

internal enum class WinRTProjectionCallSiteValueTransform {
    IDENTITY,
    BOOLEAN,
    UNSIGNED,
    CHAR16,
}

internal enum class WinRTProjectionCallSiteReferenceAccess {
    RAW_ADDRESS,
    RAW_COM_PTR,
    UNKNOWN_REFERENCE,
    INSPECTABLE_REFERENCE,
    PROJECTED_OBJECT,
}

internal enum class WinRTProjectionCallSiteSlotDirection {
    IN,
    REF,
    OUT,
    CALLER_OUT,
    PASS_ARRAY,
    FILL_ARRAY,
    RECEIVE_ARRAY,
    RETURN,
}

internal enum class WinRTProjectionCallSiteOwnership {
    NONE,
    BORROWED,
    OWNED,
}

internal data class WinRTProjectionCallSiteCallables(
    val ownerFqName: String,
    val toAbi: String = "",
    val fromAbi: String = "",
    val copyToAbi: String = "",
    val copyFromAbi: String = "",
    val disposeAbi: String = "",
    val fromAbiCarrier: String = "",
    val createMarshaler: String = "",
    val carrierProperty: String = "",
    val extraCarrierProperties: List<String> = emptyList(),
    val closeMarshaler: String = "close",
)

internal data class WinRTProjectionCallSiteStructField(
    val offsetBytes: Int,
    val recipe: WinRTProjectionCallSiteRecipe,
)

internal data class WinRTProjectionCallSiteRecipe(
    val kind: WinRTProjectionCallSiteRecipeKind,
    val abiCarriers: List<WinRTProjectionCallSiteAbiCarrier>,
    val valueCarrier: WinRTProjectionCallSiteAbiCarrier? = null,
    val valueTransform: WinRTProjectionCallSiteValueTransform =
        WinRTProjectionCallSiteValueTransform.IDENTITY,
    val referenceAccess: WinRTProjectionCallSiteReferenceAccess? = null,
    val nullable: Boolean = false,
    val sizeBytes: Int = 0,
    val alignmentBytes: Int = 0,
    val callables: WinRTProjectionCallSiteCallables? = null,
    val children: List<WinRTProjectionCallSiteRecipe> = emptyList(),
    val fields: List<WinRTProjectionCallSiteStructField> = emptyList(),
    val typeSignature: String,
) {
    init {
        require(abiCarriers.isNotEmpty()) { "A generated ABI recipe must contribute a carrier." }
        require(typeSignature.isNotBlank()) { "A generated ABI recipe requires a closed type signature." }
        require(sizeBytes >= 0 && alignmentBytes >= 0) { "A generated ABI layout cannot be negative." }
    }
}

internal data class WinRTProjectionCallSiteSlot(
    val direction: WinRTProjectionCallSiteSlotDirection,
    val ownership: WinRTProjectionCallSiteOwnership = WinRTProjectionCallSiteOwnership.NONE,
    val recipe: WinRTProjectionCallSiteRecipe,
) {
    val abiCarriers: List<WinRTProjectionCallSiteAbiCarrier>
        get() = when (direction) {
            WinRTProjectionCallSiteSlotDirection.IN,
            WinRTProjectionCallSiteSlotDirection.PASS_ARRAY,
            WinRTProjectionCallSiteSlotDirection.FILL_ARRAY -> recipe.abiCarriers
            WinRTProjectionCallSiteSlotDirection.REF,
            WinRTProjectionCallSiteSlotDirection.OUT,
            WinRTProjectionCallSiteSlotDirection.CALLER_OUT -> listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS)
            WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY,
            WinRTProjectionCallSiteSlotDirection.RETURN ->
                List(recipe.abiCarriers.size) { WinRTProjectionCallSiteAbiCarrier.ADDRESS }
        }

    val functionParameterCount: Int
        get() = when (direction) {
            WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY,
            WinRTProjectionCallSiteSlotDirection.RETURN,
            WinRTProjectionCallSiteSlotDirection.CALLER_OUT -> 0
            else -> 1
        }
}

internal data class WinRTProjectionCallSiteDescriptor(
    val hResultPolicy: WinRTProjectionCallSiteHResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
    val slots: List<WinRTProjectionCallSiteSlot> = emptyList(),
) {
    val functionParameterCount: Int
        get() = slots.sumOf(WinRTProjectionCallSiteSlot::functionParameterCount)

    val returnSlot: WinRTProjectionCallSiteSlot?
        get() = slots.lastOrNull()?.takeIf { slot ->
            slot.direction == WinRTProjectionCallSiteSlotDirection.RETURN ||
                slot.direction == WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY
        }

    val resultSlot: WinRTProjectionCallSiteSlot?
        get() = returnSlot
}

internal val WinRTProjectionCallSiteRecipe.storageRecipe: WinRTProjectionCallSiteRecipe
    get() = if (kind == WinRTProjectionCallSiteRecipeKind.PROJECTION) children.single().storageRecipe else this

internal val WinRTProjectionCallSiteRecipe.requiresNativeOwnership: Boolean
    get() = when (kind) {
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE,
        WinRTProjectionCallSiteRecipeKind.ARRAY -> true
        WinRTProjectionCallSiteRecipeKind.ENUM,
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> children.any { child -> child.requiresNativeOwnership }
        WinRTProjectionCallSiteRecipeKind.STRUCT ->
            callables?.disposeAbi?.isNotEmpty() == true || fields.any { field -> field.recipe.requiresNativeOwnership }
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.GUID -> false
    }

internal val WinRTProjectionCallSiteRecipe.projectedKotlinTypeName: String
    get() {
        val projectedType = when (kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE -> requireNotNull(valueCarrier).projectedValueType(valueTransform)
            WinRTProjectionCallSiteRecipeKind.HSTRING -> "kotlin.String"
            WinRTProjectionCallSiteRecipeKind.GUID -> "io.github.composefluent.winrt.runtime.Guid"
            WinRTProjectionCallSiteRecipeKind.ENUM -> typeSignature.compoundProjectedType("Enum")
            WinRTProjectionCallSiteRecipeKind.STRUCT -> typeSignature.compoundProjectedType("Struct")
            WinRTProjectionCallSiteRecipeKind.ARRAY -> typeSignature.compoundProjectedType("Array")
            WinRTProjectionCallSiteRecipeKind.PROJECTION -> typeSignature.compoundProjectedType()
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> when (referenceAccess) {
                WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS ->
                    "io.github.composefluent.winrt.runtime.RawAddress"
                WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR ->
                    "io.github.composefluent.winrt.runtime.RawComPtr"
                WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE ->
                    "io.github.composefluent.winrt.runtime.IUnknownReference"
                WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE ->
                    "io.github.composefluent.winrt.runtime.InspectableReference"
                WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT -> typeSignature.compoundProjectedType()
                null -> error("A COM reference requires a typed access strategy.")
            }
        }.replace(" ", "")
        return when {
            nullable && !projectedType.endsWith('?') -> "$projectedType?"
            !nullable -> projectedType.removeSuffix("?")
            else -> projectedType
        }
    }

private fun WinRTProjectionCallSiteAbiCarrier.projectedValueType(
    transform: WinRTProjectionCallSiteValueTransform,
): String = when (transform) {
    WinRTProjectionCallSiteValueTransform.BOOLEAN -> "kotlin.Boolean"
    WinRTProjectionCallSiteValueTransform.CHAR16 -> "kotlin.Char"
    WinRTProjectionCallSiteValueTransform.UNSIGNED -> when (this) {
        WinRTProjectionCallSiteAbiCarrier.INT8 -> "kotlin.UByte"
        WinRTProjectionCallSiteAbiCarrier.INT16 -> "kotlin.UShort"
        WinRTProjectionCallSiteAbiCarrier.INT32 -> "kotlin.UInt"
        WinRTProjectionCallSiteAbiCarrier.INT64 -> "kotlin.ULong"
        else -> error("Unsigned values require an integral carrier.")
    }
    WinRTProjectionCallSiteValueTransform.IDENTITY -> when (this) {
        WinRTProjectionCallSiteAbiCarrier.INT8 -> "kotlin.Byte"
        WinRTProjectionCallSiteAbiCarrier.INT16 -> "kotlin.Short"
        WinRTProjectionCallSiteAbiCarrier.INT32 -> "kotlin.Int"
        WinRTProjectionCallSiteAbiCarrier.INT64 -> "kotlin.Long"
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> "kotlin.Float"
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> "kotlin.Double"
        WinRTProjectionCallSiteAbiCarrier.ADDRESS -> error("An address requires a non-value recipe.")
    }
}

private fun String.compoundProjectedType(expectedKind: String? = null): String {
    val open = indexOf('(')
    val separator = indexOf('|', open + 1)
    require(open > 0 && separator > open + 1 && endsWith(')')) {
        "Recipe type signature '$this' is not closed."
    }
    require(expectedKind == null || substring(0, open) == expectedKind)
    return substring(open + 1, separator).trim().also { projectedType -> require(projectedType.isNotBlank()) }
}
