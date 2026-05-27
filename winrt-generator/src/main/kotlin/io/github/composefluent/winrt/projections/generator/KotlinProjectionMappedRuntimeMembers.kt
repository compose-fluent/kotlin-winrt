package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition

internal fun mappedCollectionMemberNames(plan: KotlinTypeProjectionPlan): Set<String> =
    if (!plan.hasMappedCollectionOrIteratorRuntimeProjection) {
        emptySet()
    } else {
        mappedCollectionRuntimeMemberNames
    }

internal fun WinRtMethodDefinition.isMappedCollectionRuntimeMethod(
    plan: KotlinTypeProjectionPlan,
    mappedCollectionMemberNames: Set<String>,
): Boolean {
    if (name !in mappedCollectionMemberNames) {
        return false
    }
    val bindingName = abiSlotConstantName(plan.type.methods)
    val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == bindingName }
    if (binding != null) {
        return binding.isMappedCollectionOrIteratorBinding
    }
    return plan.hasMappedCollectionOrIteratorRuntimeProjection
}

internal fun WinRtPropertyDefinition.isMappedCollectionRuntimeProperty(
    plan: KotlinTypeProjectionPlan,
    mappedCollectionMemberNames: Set<String>,
): Boolean {
    if (name !in mappedCollectionMemberNames) {
        return false
    }
    if (plan.mutableCollectionBindings.isNotEmpty() || plan.readOnlyCollectionBindings.isNotEmpty()) {
        val hasGetterBinding = !hasNativeProjectionGetterAccessor() ||
            plan.instanceMemberBindings.any { it.bindingName == "${name.uppercase()}_GETTER_SLOT" }
        val hasSetterBinding = !hasNativeProjectionSetterAccessor() ||
            plan.instanceMemberBindings.any { it.bindingName == "${name.uppercase()}_SETTER_SLOT" }
        if (!hasGetterBinding || !hasSetterBinding) {
            return true
        }
    }
    val getterIsMapped = !hasNativeProjectionGetterAccessor() ||
        plan.instanceMemberBindings
            .firstOrNull { it.bindingName == "${name.uppercase()}_GETTER_SLOT" }
            ?.isMappedCollectionOrIteratorBinding == true
    val setterIsMapped = !hasNativeProjectionSetterAccessor() ||
        plan.instanceMemberBindings
            .firstOrNull { it.bindingName == "${name.uppercase()}_SETTER_SLOT" }
            ?.isMappedCollectionOrIteratorBinding == true
    return getterIsMapped && setterIsMapped
}

internal val KotlinProjectionInstanceMemberBinding.isMappedCollectionOrIteratorBinding: Boolean
    get() = mappedTypeByAbiName(ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?"))?.let { mappedType ->
        mappedType.readOnlyCollectionKind != null ||
            mappedType.mutableCollectionKind != null ||
            mappedType.descriptionName == "Iterator"
    } == true

private val KotlinTypeProjectionPlan.hasMappedCollectionOrIteratorRuntimeProjection: Boolean
    get() = mutableCollectionBindings.isNotEmpty() ||
        readOnlyCollectionBindings.isNotEmpty() ||
        requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("Iterator")

private val mappedCollectionRuntimeMemberNames = setOf(
    "First",
    "Current",
    "HasCurrent",
    "MoveNext",
    "GetAt",
    "Size",
    "IndexOf",
    "SetAt",
    "InsertAt",
    "RemoveAt",
    "Append",
    "RemoveAtEnd",
    "Clear",
    "GetMany",
    "ReplaceAll",
    "GetView",
    "HasKey",
    "Lookup",
    "Insert",
    "Remove",
    "Split",
    "Key",
    "Value",
)
