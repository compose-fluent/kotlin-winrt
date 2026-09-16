package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy

private const val RUNTIME = "io.github.composefluent.winrt.runtime"

/** cswinrt code_writers.h abi_marshaler: conversions and cleanup belong to generated source. */
internal fun KotlinTypedProjectionCallSitePlan.sourceMarshalingBody(
    arguments: List<CodeBlock>,
    abiCall: (List<ClassName>) -> CodeBlock,
): CodeBlock? {
    val slots = descriptor.slots
    if (slots.any { it.direction != WinRTProjectionCallSiteSlotDirection.IN &&
            it.direction != WinRTProjectionCallSiteSlotDirection.RETURN }) return null
    fun supported(recipe: WinRTProjectionCallSiteRecipe): Boolean =
        recipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING || !recipe.nullable && when (recipe.kind) {
        WinRTProjectionCallSiteRecipeKind.VALUE -> recipe.valueCarrier != WinRTProjectionCallSiteAbiCarrier.ADDRESS
        WinRTProjectionCallSiteRecipeKind.GUID -> true
        WinRTProjectionCallSiteRecipeKind.ENUM -> recipe.children.singleOrNull()?.let(::supported) == true &&
            recipe.callables?.toAbi?.isNotEmpty() == true && recipe.callables.fromAbi.isNotEmpty()
        else -> false
    }
    if (slots.any { !supported(it.recipe) }) return null
    val inputs = slots.filter { it.direction == WinRTProjectionCallSiteSlotDirection.IN }
    val result = slots.singleOrNull { it.direction == WinRTProjectionCallSiteSlotDirection.RETURN }
    if (slots.count { it.direction == WinRTProjectionCallSiteSlotDirection.RETURN } > 1) return null
    if (result?.recipe?.kind == WinRTProjectionCallSiteRecipeKind.HSTRING &&
        result.ownership != WinRTProjectionCallSiteOwnership.OWNED) return null
    if (result == null && returnType.toString() != "kotlin.Unit" &&
        !(descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN && returnType.toString() == "kotlin.Int")) return null
    require(arguments.size == inputs.size + 2)
    val carriers = inputs.map { it.recipe.scalarCarrierType() } +
        if (result != null) listOf(ClassName(RUNTIME, "RawAddress")) else emptyList()
    val rawCall = abiCall(carriers)
    return CodeBlock.builder().apply {
        // Keep the trailing lambda attached when KotlinPoet wraps a long expression-body signature.
        add("kotlin.run·{\n").indent()
        add("val __instance = %L\nval __slot = %L\n", arguments[0], arguments[1])
        inputs.forEachIndexed { index, _ -> add("val __arg%L: %T = %L\n", index, parameters[index].type, arguments[index + 2]) }
        add("%M(__instance) { __receiver ->\n", MemberName(RUNTIME, "withWinRTAbiReference")).indent()
        val scopedInputs = inputs.withIndex().filter {
            it.value.recipe.kind == WinRTProjectionCallSiteRecipeKind.GUID ||
                it.value.recipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING
        }
        scopedInputs.forEach { (index, input) ->
            if (input.recipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING) {
                add("%M(__arg%L) { __input%L ->\n", MemberName(RUNTIME, "withWinRTHStringReference"), index, index).indent()
            } else {
                add("%M(%LL, %LL) { __input%L ->\n", MemberName(RUNTIME, "withWinRTStructStorage"), input.recipe.sizeBytes, input.recipe.alignmentBytes, index).indent()
                add("%T.writeGuid(__input%L, __arg%L)\n", ClassName(RUNTIME, "PlatformAbi"), index, index)
            }
        }
        if (result != null) {
            if (result.recipe.kind == WinRTProjectionCallSiteRecipeKind.GUID) {
                add("%M(%LL, %LL) { __result ->\n", MemberName(RUNTIME, "withWinRTStructStorage"), result.recipe.sizeBytes, result.recipe.alignmentBytes).indent()
            } else {
                val scope = if (result.recipe.kind == WinRTProjectionCallSiteRecipeKind.HSTRING)
                    "withWinRTOwnedHStringResult" else "withWinRTScalarResult"
                add("%M { __result ->\n", MemberName(RUNTIME, scope)).indent()
            }
        }
        add("val __hr = %L(__receiver, __slot", rawCall)
        inputs.forEachIndexed { index, input ->
            add(", %L", if (scopedInputs.any { it.index == index }) CodeBlock.of("__input%L", index)
                else input.recipe.scalarToAbi(CodeBlock.of("__arg%L", index)))
        }
        if (result != null) add(", __result")
        add(")\n")
        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
            add("%T(__hr).requireSuccess()\n", ClassName(RUNTIME, "HResult"))
        }
        when {
            result?.recipe?.kind == WinRTProjectionCallSiteRecipeKind.HSTRING ->
                add("%T.fromAbi(%T.readPointer(__result))\n", ClassName(RUNTIME, "NativeStringMarshaller"), ClassName(RUNTIME, "PlatformAbi"))
            result?.recipe?.kind == WinRTProjectionCallSiteRecipeKind.GUID ->
                add("%T.readGuid(__result)\n", ClassName(RUNTIME, "PlatformAbi"))
            result != null -> {
                val readName = when (result.recipe.storageRecipe.valueCarrier) {
                    WinRTProjectionCallSiteAbiCarrier.FLOAT32 -> "readFloat"
                    WinRTProjectionCallSiteAbiCarrier.FLOAT64 -> "readDouble"
                    else -> "read${result.recipe.storageRecipe.valueCarrier!!.name.lowercase().replaceFirstChar(Char::uppercaseChar)}"
                }
                add("%L\n", result.recipe.scalarFromAbi(CodeBlock.of("%T.%L(__result)", ClassName(RUNTIME, "PlatformAbi"), readName)))
            }
            descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.RETURN -> add("__hr\n")
            else -> add("kotlin.Unit\n")
        }
        if (result != null) unindent().add("}\n")
        scopedInputs.forEach { _ -> unindent().add("}\n") }
        unindent().add("}\n")
        unindent().add("}")
    }.build()
}

private fun WinRTProjectionCallSiteRecipe.scalarCarrierType(): ClassName =
    if (kind == WinRTProjectionCallSiteRecipeKind.GUID || kind == WinRTProjectionCallSiteRecipeKind.HSTRING) ClassName(RUNTIME, "RawAddress")
    else if (kind == WinRTProjectionCallSiteRecipeKind.ENUM) children.single().scalarCarrierType()
    else ClassName.bestGuess(copy(valueTransform = WinRTProjectionCallSiteValueTransform.IDENTITY).projectedKotlinTypeName)

private fun WinRTProjectionCallSiteRecipe.scalarToAbi(value: CodeBlock): CodeBlock {
    if (kind == WinRTProjectionCallSiteRecipeKind.ENUM) {
        val codec = requireNotNull(callables)
        return children.single().scalarToAbi(CodeBlock.of("%T.%L(%L)", ClassName.bestGuess(codec.ownerFqName), codec.toAbi, value))
    }
    return when (valueTransform) {
        WinRTProjectionCallSiteValueTransform.IDENTITY -> value
        WinRTProjectionCallSiteValueTransform.BOOLEAN -> CodeBlock.of("(if (%L) 1 else 0).toByte()", value)
        WinRTProjectionCallSiteValueTransform.CHAR16 -> CodeBlock.of("%L.code.toShort()", value)
        WinRTProjectionCallSiteValueTransform.UNSIGNED -> CodeBlock.of("%L.to%L()", value, scalarCarrierType().simpleName)
    }
}

private fun WinRTProjectionCallSiteRecipe.scalarFromAbi(value: CodeBlock): CodeBlock {
    if (kind == WinRTProjectionCallSiteRecipeKind.ENUM) {
        val codec = requireNotNull(callables)
        return CodeBlock.of("%T.%L(%L)", ClassName.bestGuess(codec.ownerFqName), codec.fromAbi, children.single().scalarFromAbi(value))
    }
    return when (valueTransform) {
        WinRTProjectionCallSiteValueTransform.IDENTITY -> value
        WinRTProjectionCallSiteValueTransform.BOOLEAN -> CodeBlock.of("(%L != 0.toByte())", value)
        WinRTProjectionCallSiteValueTransform.CHAR16 -> CodeBlock.of("%L.toInt().and(0xffff).toChar()", value)
        WinRTProjectionCallSiteValueTransform.UNSIGNED -> CodeBlock.of("%L.to%L()", value, ClassName.bestGuess(projectedKotlinTypeName).simpleName)
    }
}
