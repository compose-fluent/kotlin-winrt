package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMethodDefinition

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
