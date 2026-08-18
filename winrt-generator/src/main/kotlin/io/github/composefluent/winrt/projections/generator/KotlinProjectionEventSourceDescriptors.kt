package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventHelperSubclassDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericTypeInstantiationDescriptor
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.semanticHelpers
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal data class KotlinProjectionBoundInstanceEventSource(
    val memberBinding: KotlinProjectionInstanceMemberBinding,
    val ownerTypeName: String,
    val eventTypeBinding: KotlinProjectionAbiTypeBinding,
)

internal fun KotlinTypeProjectionPlan.boundInstanceEventSource(
    event: WinRTEventDefinition,
): KotlinProjectionBoundInstanceEventSource? {
    val memberBinding = instanceMemberBindings.firstOrNull { binding ->
        binding.bindingName == "${event.name.uppercase()}_ADD_SLOT"
    } ?: return null
    val eventTypeBinding = memberBinding.parameterBindings.singleOrNull { parameter ->
        parameter.name == "handler"
    }?.typeBinding ?: return null
    return KotlinProjectionBoundInstanceEventSource(
        memberBinding = memberBinding,
        ownerTypeName = memberBinding.ownerInterfaceQualifiedName,
        eventTypeBinding = eventTypeBinding,
    )
}

internal fun KotlinProjectionPlanner.eventSourceDescriptors(
    model: WinRTMetadataModel,
    plans: List<KotlinTypeProjectionPlan>,
    instantiations: List<WinRTGenericTypeInstantiationDescriptor> = emptyList(),
    closedGenericPlans: List<KotlinTypeProjectionPlan> = plans,
): List<WinRTEventHelperSubclassDescriptor> {
    val helpers = model.semanticHelpers()
    val closedGenericTypeNames = closedGenericPlans.mapTo(mutableSetOf()) { plan -> plan.type.qualifiedName }
    val requiredDescriptorKeys = plans.requiredEventSourceDescriptorKeys()
    val metadataDescriptors = model.namespaces
        .flatMap(WinRTNamespace::types)
        .flatMap(helpers::eventHelperSubclassDescriptors)
        .filter { descriptor -> descriptor.ownerTypeName to descriptor.eventTypeName in requiredDescriptorKeys }
        .filterNot { descriptor -> descriptor.eventTypeName.containsOpenGenericType() }
    val closedGenericDescriptors = instantiations
        .asSequence()
        .filter { instantiation -> instantiation.definitionType?.qualifiedName in closedGenericTypeNames }
        .flatMap { instantiation ->
            val definition = requireNotNull(instantiation.definitionType)
            helpers.eventHelperSubclassDescriptors(
                type = definition,
                genericTypeArguments = instantiation.genericArguments,
                ownerTypeName = instantiation.type.normalized().typeName,
            ).asSequence()
        }
        .filterNot { descriptor -> descriptor.eventTypeName.containsOpenGenericType() }
        .toList()
    return (metadataDescriptors + closedGenericDescriptors + plans.flatMap { plan ->
        boundRuntimeClassEventSourceDescriptors(plan, helpers)
    })
        .map { descriptor ->
            if (descriptor.usesSharedEventHandlerSource) {
                descriptor
            } else {
                descriptor.copy(sourceClassName = eventSourceSubclassName(descriptor.ownerTypeName, descriptor.eventTypeName))
            }
        }
        .distinctBy { it.eventTypeName to it.ownerTypeName }
        .sortedWith(compareBy({ it.eventTypeName }, { it.ownerTypeName }))
}

private fun String.containsOpenGenericType(): Boolean =
    WinRTTypeRef.fromDisplayName(this).normalized().containsOpenGenericType()

private fun WinRTTypeRef.containsOpenGenericType(): Boolean =
    kind == WinRTTypeRefKind.GenericTypeParameter ||
        kind == WinRTTypeRefKind.MethodTypeParameter ||
        typeArguments.any(WinRTTypeRef::containsOpenGenericType) ||
        elementType?.containsOpenGenericType() == true

private fun List<KotlinTypeProjectionPlan>.requiredEventSourceDescriptorKeys(): Set<Pair<String, String>> =
    buildSet {
        for (plan in this@requiredEventSourceDescriptorKeys) {
            plan.type.events
                .filterNot { event -> event.isStatic }
                .forEach { event ->
                    val eventSource = plan.boundInstanceEventSource(event) ?: return@forEach
                    add(eventSource.ownerTypeName to eventSource.eventTypeBinding.typeName)
                }
            plan.type.events
                .filter { event -> event.isStatic }
                .forEach { event ->
                    val binding = plan.staticMemberBindings.firstOrNull {
                        it.bindingName == "STATIC_${event.name.uppercase()}_ADD_SLOT"
                    } ?: return@forEach
                    val eventTypeName = plan.eventInvokeDescriptors
                        .firstOrNull { descriptor -> descriptor.eventName == event.name && descriptor.isStatic }
                        ?.delegateTypeName
                        ?: event.delegateTypeName
                    add(binding.ownerInterfaceQualifiedName to eventTypeName)
                }
        }
    }

private fun boundRuntimeClassEventSourceDescriptors(
    plan: KotlinTypeProjectionPlan,
    helpers: WinRTMetadataSemanticHelpers,
): List<WinRTEventHelperSubclassDescriptor> {
    if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
        return emptyList()
    }
    return plan.type.events
        .filterNot { event -> event.isStatic }
        .mapNotNull { event ->
            val eventSource = plan.boundInstanceEventSource(event) ?: return@mapNotNull null
            val eventType = WinRTTypeRef.fromDisplayName(eventSource.eventTypeBinding.typeName).normalized()
            val boundEvent = event.copy(
                delegateTypeName = eventType.typeName,
                delegateTypeSignature = eventType,
            )
            helpers.eventHelperSubclassDescriptors(
                type = plan.type.copy(events = listOf(boundEvent)),
                ownerTypeName = eventSource.ownerTypeName,
            ).singleOrNull()
        }
}

private fun eventSourceSubclassName(ownerTypeName: String, eventTypeName: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$ownerTypeName\t$eventTypeName".toByteArray(StandardCharsets.UTF_8))
    return "_EventSource_${digest.take(8).joinToString("") { byte -> "%02x".format(byte) }}"
}
