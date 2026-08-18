package io.github.composefluent.winrt.compiler.callsites.lowering

import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol

/** Compiler-private ABI plan composed from typed IR and generated ABI metadata. */
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
    MANAGED_INSPECTABLE,
    PROJECTED_INTERFACE,
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
    /** The exact fromAbi signature accepts an owning COM-reference wrapper, not a borrowed address. */
    val fromAbiConsumesOwnedReference: Boolean = false,
    /** Exact generated IR entry point when overload resolution cannot be represented by name/arity. */
    val fromAbiSymbol: IrSimpleFunctionSymbol? = null,
) {
    init {
        require(ownerFqName.isNotBlank()) { "Typed ABI callables require an owner." }
        require(createMarshaler.isEmpty() || carrierProperty.isNotBlank()) {
            "A typed ABI marshaler factory requires its first carrier property."
        }
        require(createMarshaler.isEmpty() || closeMarshaler.isNotBlank()) {
            "A typed ABI marshaler factory requires a cleanup function."
        }
    }
}

internal data class WinRTProjectionCallSiteStructField(
    val offsetBytes: Int,
    val recipe: WinRTProjectionCallSiteRecipe,
) {
    init {
        require(offsetBytes >= 0) { "A struct field offset cannot be negative." }
    }
}

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
    /** Exact generated interface type handle used to select the ABI interface pointer on input. */
    val projectedTypeHandleSymbol: IrSimpleFunctionSymbol? = null,
    val callables: WinRTProjectionCallSiteCallables? = null,
    val children: List<WinRTProjectionCallSiteRecipe> = emptyList(),
    val fields: List<WinRTProjectionCallSiteStructField> = emptyList(),
    val typeSignature: String,
) {
    init {
        require(typeSignature.isNotBlank()) { "A typed ABI recipe requires a closed type signature." }
        require(abiCarriers.isNotEmpty()) { "A typed ABI recipe must contribute at least one carrier." }
        require(sizeBytes >= 0 && alignmentBytes >= 0) { "A typed ABI layout cannot be negative." }
        when (kind) {
            WinRTProjectionCallSiteRecipeKind.VALUE -> {
                require(valueCarrier != null && abiCarriers == listOf(valueCarrier))
                require(valueCarrier != WinRTProjectionCallSiteAbiCarrier.ADDRESS)
                require(children.isEmpty() && fields.isEmpty() && callables == null && referenceAccess == null)
            }
            WinRTProjectionCallSiteRecipeKind.HSTRING ->
                require(valueCarrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS && abiCarriers == listOf(valueCarrier))
            WinRTProjectionCallSiteRecipeKind.GUID -> {
                require(valueCarrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS && abiCarriers == listOf(valueCarrier))
                require(sizeBytes == 16 && alignmentBytes > 0)
            }
            WinRTProjectionCallSiteRecipeKind.ENUM -> {
                require(children.size == 1 && children.single().kind == WinRTProjectionCallSiteRecipeKind.VALUE)
                require(callables?.toAbi?.isNotBlank() == true && callables.fromAbi.isNotBlank())
                require(abiCarriers == children.single().abiCarriers)
            }
            WinRTProjectionCallSiteRecipeKind.STRUCT -> {
                require(sizeBytes > 0 && alignmentBytes > 0)
                require(abiCarriers.size == 1 && valueCarrier == abiCarriers.single())
                require(abiCarriers.single() in STRUCT_CARRIERS)
            }
            WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> {
                require(valueCarrier == WinRTProjectionCallSiteAbiCarrier.ADDRESS && abiCarriers == listOf(valueCarrier))
                require(referenceAccess != null && children.isEmpty())
                require(
                    referenceAccess != WinRTProjectionCallSiteReferenceAccess.PROJECTED_INTERFACE ||
                        projectedTypeHandleSymbol != null,
                ) { "A projected interface input requires its exact generated type handle." }
            }
            WinRTProjectionCallSiteRecipeKind.ARRAY -> {
                require(children.size == 1)
                require(
                    abiCarriers == listOf(
                        WinRTProjectionCallSiteAbiCarrier.INT32,
                        WinRTProjectionCallSiteAbiCarrier.ADDRESS,
                    ),
                )
            }
            WinRTProjectionCallSiteRecipeKind.PROJECTION -> {
                require(children.size == 1 && callables != null) {
                    "Projection recipe '$typeSignature' requires one storage child and typed callables."
                }
                require(abiCarriers == children.single().abiCarriers) {
                    "Projection recipe '$typeSignature' carriers $abiCarriers disagree with storage " +
                        "carriers ${children.single().abiCarriers}."
                }
            }
        }
        structuralProjectedKotlinTypeName()
    }

    val storageSizeBytes: Int
        get() = when (kind) {
            WinRTProjectionCallSiteRecipeKind.GUID,
            WinRTProjectionCallSiteRecipeKind.STRUCT -> sizeBytes
            WinRTProjectionCallSiteRecipeKind.PROJECTION -> children.single().storageSizeBytes
            else -> valueCarrier?.storageSizeBytes ?: 0
        }
}

internal data class WinRTProjectionCallSiteSlot(
    val direction: WinRTProjectionCallSiteSlotDirection,
    val ownership: WinRTProjectionCallSiteOwnership = WinRTProjectionCallSiteOwnership.NONE,
    val recipe: WinRTProjectionCallSiteRecipe,
) {
    init {
        val storage = recipe.storageRecipe
        when (direction) {
            WinRTProjectionCallSiteSlotDirection.PASS_ARRAY,
            WinRTProjectionCallSiteSlotDirection.FILL_ARRAY -> {
                require(storage.kind == WinRTProjectionCallSiteRecipeKind.ARRAY)
                require(
                    recipe.hasExactInputFactory ||
                        direction == WinRTProjectionCallSiteSlotDirection.PASS_ARRAY &&
                        recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY &&
                        recipe.callables == null,
                )
                require(
                    direction != WinRTProjectionCallSiteSlotDirection.FILL_ARRAY ||
                        recipe.callables?.copyFromAbi?.isNotBlank() == true,
                )
            }
            WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY -> {
                require(recipe.kind == WinRTProjectionCallSiteRecipeKind.ARRAY) {
                    "$direction recipe '${recipe.typeSignature}' must expose array output storage directly."
                }
                require(recipe.hasExactOutputCodec || recipe.callables == null) {
                    "$direction recipe '${recipe.typeSignature}' has no exact typed output codec."
                }
            }
            else -> require(storage.kind != WinRTProjectionCallSiteRecipeKind.ARRAY) {
                "$direction recipe '${recipe.typeSignature}' uses array storage and requires an array direction."
            }
        }
        require(
            when (direction) {
                WinRTProjectionCallSiteSlotDirection.IN,
                WinRTProjectionCallSiteSlotDirection.REF,
                WinRTProjectionCallSiteSlotDirection.PASS_ARRAY,
                WinRTProjectionCallSiteSlotDirection.FILL_ARRAY -> recipe.isLowerableInput
                else -> recipe.isLowerableOutput
            },
        ) { "$direction recipe '${recipe.typeSignature}' has no exact typed codec." }
        val expectedOwnership = when {
            !recipe.requiresNativeOwnership -> WinRTProjectionCallSiteOwnership.NONE
            direction == WinRTProjectionCallSiteSlotDirection.IN ||
                direction == WinRTProjectionCallSiteSlotDirection.PASS_ARRAY ->
                WinRTProjectionCallSiteOwnership.BORROWED
            else -> WinRTProjectionCallSiteOwnership.OWNED
        }
        require(ownership == expectedOwnership) {
            "$direction recipe '${recipe.typeSignature}' requires $expectedOwnership ownership, not $ownership."
        }
    }

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
    init {
        val resultIndexes = slots.indices.filter { index -> slots[index].direction.isProjectedResult }
        require(resultIndexes.size <= 1)
        require(resultIndexes.isEmpty() || resultIndexes.single() == slots.lastIndex)
        require(hResultPolicy != WinRTProjectionCallSiteHResultPolicy.RETURN || resultIndexes.isEmpty())
        require(slots.none { slot -> slot.direction == WinRTProjectionCallSiteSlotDirection.CALLER_OUT } || resultIndexes.isEmpty())
    }

    val functionParameterCount: Int
        get() = slots.sumOf(WinRTProjectionCallSiteSlot::functionParameterCount)

    val projectedResultTypeName: String?
        get() = slots.lastOrNull()
            ?.takeIf { slot -> slot.direction.isProjectedResult }
            ?.recipe
            ?.projectedKotlinTypeName
}

internal val WinRTProjectionCallSiteRecipe.storageRecipe: WinRTProjectionCallSiteRecipe
    get() = if (kind == WinRTProjectionCallSiteRecipeKind.PROJECTION) children.single().storageRecipe else this

internal val WinRTProjectionCallSiteRecipe.inboundDecodeConsumesOwnedComReference: Boolean
    get() = kind == WinRTProjectionCallSiteRecipeKind.PROJECTION &&
        callables?.fromAbiConsumesOwnedReference == true

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
    get() = structuralProjectedKotlinTypeName()

private fun WinRTProjectionCallSiteRecipe.structuralProjectedKotlinTypeName(): String {
    val projectedType = when (kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> requireNotNull(valueCarrier).projectedValueType(valueTransform)
        WinRTProjectionCallSiteRecipeKind.HSTRING -> "kotlin.String"
        WinRTProjectionCallSiteRecipeKind.GUID -> "io.github.composefluent.winrt.runtime.Guid"
        WinRTProjectionCallSiteRecipeKind.ENUM -> typeSignature.compoundProjectedType("Enum")
        WinRTProjectionCallSiteRecipeKind.STRUCT -> typeSignature.compoundProjectedType("Struct")
        WinRTProjectionCallSiteRecipeKind.ARRAY -> typeSignature.compoundProjectedType("Array")
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> typeSignature.compoundProjectedType()
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> when (referenceAccess) {
            WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS -> "io.github.composefluent.winrt.runtime.RawAddress"
            WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR -> "io.github.composefluent.winrt.runtime.RawComPtr"
            WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE ->
                "io.github.composefluent.winrt.runtime.IUnknownReference"
            WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE ->
                "io.github.composefluent.winrt.runtime.InspectableReference"
            WinRTProjectionCallSiteReferenceAccess.MANAGED_INSPECTABLE ->
                typeSignature.compoundProjectedType()
            WinRTProjectionCallSiteReferenceAccess.PROJECTED_INTERFACE,
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

private val WinRTProjectionCallSiteRecipe.hasExactInputFactory: Boolean
    get() = kind == WinRTProjectionCallSiteRecipeKind.PROJECTION &&
        callables?.createMarshaler?.isNotBlank() == true &&
        1 + callables.extraCarrierProperties.size == abiCarriers.size

private val WinRTProjectionCallSiteRecipe.hasExactOutputCodec: Boolean
    get() = callables?.fromAbi?.isNotBlank() == true &&
        (kind != WinRTProjectionCallSiteRecipeKind.ARRAY || callables.disposeAbi.isNotBlank())

private val WinRTProjectionCallSiteRecipe.isLowerableInput: Boolean
    get() = when (kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.GUID -> true
        WinRTProjectionCallSiteRecipeKind.ENUM -> callables?.toAbi?.isNotBlank() == true
        WinRTProjectionCallSiteRecipeKind.STRUCT -> if (abiCarriers.single() == WinRTProjectionCallSiteAbiCarrier.ADDRESS) {
            callables?.copyToAbi?.isNotBlank() == true
        } else {
            callables?.toAbi?.isNotBlank() == true || callables?.copyToAbi?.isNotBlank() == true
        }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> referenceAccess in INPUT_REFERENCE_ACCESS
        WinRTProjectionCallSiteRecipeKind.ARRAY ->
            callables == null && children.single().isLowerableInput
        WinRTProjectionCallSiteRecipeKind.PROJECTION -> when {
            hasExactInputFactory -> true
            callables?.toAbi?.isNotBlank() == true -> abiCarriers.size == 1
            else -> children.single().isLowerableInput
        }
    }

private val WinRTProjectionCallSiteRecipe.isLowerableOutput: Boolean
    get() = when (kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE,
        WinRTProjectionCallSiteRecipeKind.HSTRING,
        WinRTProjectionCallSiteRecipeKind.GUID -> true
        WinRTProjectionCallSiteRecipeKind.ENUM -> callables?.fromAbi?.isNotBlank() == true
        WinRTProjectionCallSiteRecipeKind.STRUCT -> if (abiCarriers.single() == WinRTProjectionCallSiteAbiCarrier.ADDRESS) {
            callables?.fromAbi?.isNotBlank() == true
        } else {
            callables?.fromAbiCarrier?.isNotBlank() == true || callables?.fromAbi?.isNotBlank() == true
        }
        WinRTProjectionCallSiteRecipeKind.COM_REFERENCE -> referenceAccess in OUTPUT_REFERENCE_ACCESS
        WinRTProjectionCallSiteRecipeKind.ARRAY ->
            hasExactOutputCodec || callables == null && children.single().isLowerableOutput
        WinRTProjectionCallSiteRecipeKind.PROJECTION ->
            abiCarriers == listOf(WinRTProjectionCallSiteAbiCarrier.ADDRESS) &&
                callables?.fromAbi?.isNotBlank() == true
    }

private val WinRTProjectionCallSiteSlotDirection.isProjectedResult: Boolean
    get() = this == WinRTProjectionCallSiteSlotDirection.RETURN ||
        this == WinRTProjectionCallSiteSlotDirection.RECEIVE_ARRAY

private val WinRTProjectionCallSiteAbiCarrier.storageSizeBytes: Int
    get() = when (this) {
        WinRTProjectionCallSiteAbiCarrier.INT8 -> 1
        WinRTProjectionCallSiteAbiCarrier.INT16 -> 2
        WinRTProjectionCallSiteAbiCarrier.INT32,
        WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> 4
        WinRTProjectionCallSiteAbiCarrier.ADDRESS,
        WinRTProjectionCallSiteAbiCarrier.INT64,
        WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> 8
    }

private val INPUT_REFERENCE_ACCESS = setOf(
    WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS,
    WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR,
    WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE,
    WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE,
    WinRTProjectionCallSiteReferenceAccess.MANAGED_INSPECTABLE,
    WinRTProjectionCallSiteReferenceAccess.PROJECTED_INTERFACE,
    WinRTProjectionCallSiteReferenceAccess.PROJECTED_OBJECT,
)

private val OUTPUT_REFERENCE_ACCESS = setOf(
    WinRTProjectionCallSiteReferenceAccess.RAW_ADDRESS,
    WinRTProjectionCallSiteReferenceAccess.RAW_COM_PTR,
    WinRTProjectionCallSiteReferenceAccess.UNKNOWN_REFERENCE,
    WinRTProjectionCallSiteReferenceAccess.INSPECTABLE_REFERENCE,
)

private val STRUCT_CARRIERS = setOf(
    WinRTProjectionCallSiteAbiCarrier.ADDRESS,
    WinRTProjectionCallSiteAbiCarrier.INT8,
    WinRTProjectionCallSiteAbiCarrier.INT16,
    WinRTProjectionCallSiteAbiCarrier.INT32,
    WinRTProjectionCallSiteAbiCarrier.INT64,
)
