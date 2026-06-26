package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition

internal fun mappedCollectionMemberNames(plan: KotlinTypeProjectionPlan): Set<String> =
    if (!plan.hasMappedCollectionOrIteratorRuntimeProjection) {
        emptySet()
    } else {
        mappedCollectionRuntimeMemberNames
    }

internal fun WinRTMethodDefinition.isMappedCollectionRuntimeMethod(
    plan: KotlinTypeProjectionPlan,
    mappedCollectionMemberNames: Set<String>,
): Boolean {
    if (name !in mappedCollectionMemberNames) {
        return false
    }
    val bindingName = abiSlotConstantName(plan.type.methods)
    val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == bindingName }
    if (binding != null) {
        return name in mappedCollectionRuntimeMethodNames(plan) &&
            binding.isMappedCollectionOrIteratorBinding(plan)
    }
    if (plan.mutableCollectionBindings.isNotEmpty() || plan.readOnlyCollectionBindings.isNotEmpty()) {
        return name in mappedCollectionRuntimeMethodNames(plan)
    }
    return plan.hasMappedCollectionOrIteratorRuntimeProjection
}

internal fun WinRTPropertyDefinition.isMappedCollectionRuntimeProperty(
    plan: KotlinTypeProjectionPlan,
    mappedCollectionMemberNames: Set<String>,
): Boolean {
    if (name !in mappedCollectionMemberNames) {
        return false
    }
    val getterIsMapped = !hasNativeProjectionGetterAccessor() ||
        plan.instanceMemberBindings
            .firstOrNull { it.bindingName == "${name.uppercase()}_GETTER_SLOT" }
            ?.isMappedCollectionOrIteratorBinding(plan) == true
    val setterIsMapped = !hasNativeProjectionSetterAccessor() ||
        plan.instanceMemberBindings
            .firstOrNull { it.bindingName == "${name.uppercase()}_SETTER_SLOT" }
            ?.isMappedCollectionOrIteratorBinding(plan) == true
    if (getterIsMapped && setterIsMapped) {
        return name in mappedCollectionRuntimeMethodNames(plan)
    }
    return name in mappedCollectionRuntimeMethodNames(plan) &&
        (plan.mutableCollectionBindings.isNotEmpty() || plan.readOnlyCollectionBindings.isNotEmpty())
}

internal val KotlinProjectionInstanceMemberBinding.isMappedCollectionOrIteratorBinding: Boolean
    get() = mappedTypeByAbiName(ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?"))?.let { mappedType ->
        mappedType.readOnlyCollectionKind != null ||
            mappedType.mutableCollectionKind != null ||
            mappedType.descriptionName == "Iterator"
    } == true

internal val KotlinProjectionInstanceMemberBinding.isRuntimeOwnedMappedBinding: Boolean
    get() = isRuntimeOwnedMappedTypeName(ownerInterfaceQualifiedName) ||
        isRuntimeOwnedMappedTypeName(slotInterfaceQualifiedName)

internal val KotlinProjectionInstanceMemberBinding.isMappedRuntimeHelperBinding: Boolean
    get() = slotInterfaceQualifiedName.isMappedRuntimeHelperInterfaceName()

internal fun String.isMappedRuntimeHelperInterfaceName(): Boolean {
    val mappedType = mappedTypeByAbiName(substringBefore('<').removeSuffix("?")) ?: return false
    return mappedType.readOnlyCollectionKind != null ||
        mappedType.mutableCollectionKind != null ||
        mappedType.descriptionName == "Iterator" ||
        mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedAsyncAction ||
        mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress ||
        mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedAsyncOperation ||
        mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress
}

private fun KotlinProjectionInstanceMemberBinding.isMappedCollectionOrIteratorBinding(
    plan: KotlinTypeProjectionPlan,
): Boolean =
    ownerInterfaceQualifiedName.hasMappedCollectionOrIteratorInterfaceAncestor(plan, mutableSetOf())

private fun String.hasMappedCollectionOrIteratorInterfaceAncestor(
    plan: KotlinTypeProjectionPlan,
    visiting: MutableSet<String>,
): Boolean {
    val rawInterfaceName = substringBefore('<').removeSuffix("?")
    if (!visiting.add(rawInterfaceName)) {
        return false
    }
    val mappedType = mappedTypeByAbiName(rawInterfaceName)
    if (mappedType?.readOnlyCollectionKind != null ||
        mappedType?.mutableCollectionKind != null ||
        mappedType?.descriptionName == "Iterator"
    ) {
        return true
    }
    val type = plan.typesByQualifiedName[rawInterfaceName] ?: return false
    return type.implementedInterfaces.any { implemented ->
        implemented.interfaceName.hasMappedCollectionOrIteratorInterfaceAncestor(plan, visiting)
    }
}

private val KotlinTypeProjectionPlan.hasMappedCollectionOrIteratorRuntimeProjection: Boolean
    get() = mutableCollectionBindings.isNotEmpty() ||
        readOnlyCollectionBindings.isNotEmpty() ||
        requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("Iterator")

internal fun mappedCollectionRuntimeMethodNames(plan: KotlinTypeProjectionPlan): Set<String> =
    buildSet {
        if (plan.readOnlyCollectionBindings.isNotEmpty() || plan.mutableCollectionBindings.isNotEmpty()) {
            addAll(KotlinProjectionReadOnlyCollectionKind.Iterable.runtimeMethodNames)
        }
        plan.readOnlyCollectionBindings.forEach { binding ->
            addAll(binding.kind.runtimeMethodNames)
        }
        plan.mutableCollectionBindings.forEach { binding ->
            addAll(binding.kind.runtimeMethodNames)
        }
        if (plan.requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("Iterator")) {
            addAll(iteratorRuntimeMethodNames)
        }
    }

private val KotlinProjectionReadOnlyCollectionKind.runtimeMethodNames: Set<String>
    get() = when (this) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> setOf("First")
        KotlinProjectionReadOnlyCollectionKind.VectorView -> setOf("GetAt", "Size", "IndexOf", "GetMany")
        KotlinProjectionReadOnlyCollectionKind.MapView -> setOf("Lookup", "Size", "HasKey", "Split")
    }

private val KotlinProjectionMutableCollectionKind.runtimeMethodNames: Set<String>
    get() = when (this) {
        KotlinProjectionMutableCollectionKind.Vector -> setOf(
            "GetAt",
            "Size",
            "GetView",
            "IndexOf",
            "SetAt",
            "InsertAt",
            "RemoveAt",
            "Append",
            "RemoveAtEnd",
            "Clear",
            "GetMany",
            "ReplaceAll",
        )
        KotlinProjectionMutableCollectionKind.Map -> setOf("Lookup", "Size", "HasKey", "GetView", "Insert", "Remove", "Clear")
    }

private val iteratorRuntimeMethodNames = setOf("Current", "HasCurrent", "MoveNext", "GetMany")

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
