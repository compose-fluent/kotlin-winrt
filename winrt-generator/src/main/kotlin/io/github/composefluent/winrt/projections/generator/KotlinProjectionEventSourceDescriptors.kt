package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTEventHelperSubclassDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventHandlerKind
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.winRTEventHandlerKindForTypeName
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal fun KotlinProjectionPlanner.eventSourceDescriptors(
    model: WinRTMetadataModel,
    plans: List<KotlinTypeProjectionPlan>,
): List<WinRTEventHelperSubclassDescriptor> {
    val helpers = model.semanticHelpers()
    val metadataDescriptors = model.namespaces
        .flatMap(WinRTNamespace::types)
        .flatMap(helpers::eventHelperSubclassDescriptors)
        .filter { descriptor -> descriptor.ownerTypeName to descriptor.eventTypeName in plans.requiredEventSourceDescriptorKeys() }
    val typesByQualifiedName = model.namespaces
        .flatMap(WinRTNamespace::types)
        .associateBy(WinRTTypeDefinition::qualifiedName)
    return (metadataDescriptors + plans.flatMap { plan ->
        boundRuntimeClassCollectionEventSourceDescriptors(plan, typesByQualifiedName)
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

private fun List<KotlinTypeProjectionPlan>.requiredEventSourceDescriptorKeys(): Set<Pair<String, String>> =
    buildSet {
        for (plan in this@requiredEventSourceDescriptorKeys) {
            plan.type.events
                .filterNot { event -> event.isStatic }
                .forEach { event ->
                    val binding = plan.instanceMemberBindings.firstOrNull {
                        it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
                    } ?: return@forEach
                    val eventTypeName = plan.typesByQualifiedName[binding.slotInterfaceQualifiedName]
                        ?.events
                        ?.firstOrNull { rawEvent -> rawEvent.name == event.name }
                        ?.delegateTypeName
                        ?: plan.eventInvokeDescriptors
                            .firstOrNull { descriptor -> descriptor.eventName == event.name && !descriptor.isStatic }
                            ?.delegateTypeName
                        ?: event.delegateTypeName
                    add(binding.slotInterfaceQualifiedName to eventTypeName)
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

private fun KotlinProjectionPlanner.boundRuntimeClassCollectionEventSourceDescriptors(
    plan: KotlinTypeProjectionPlan,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): List<WinRTEventHelperSubclassDescriptor> {
    if (plan.type.kind != io.github.composefluent.winrt.metadata.WinRTTypeKind.RuntimeClass) {
        return emptyList()
    }
    return plan.type.events
        .filterNot { event -> event.isStatic }
        .mapNotNull { event ->
            val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == "${event.name.uppercase()}_ADD_SLOT" }
                ?: return@mapNotNull null
            val runtimeEventKind = winRTEventHandlerKindForTypeName(event.delegateTypeName)
            if (runtimeEventKind == null || !runtimeEventKind.isCollectionEventSourceHandler()) {
                return@mapNotNull null
            }
            val ownerTypeName = binding.slotInterfaceQualifiedName
            if (!runtimeEventKind.matchesCollectionEventOwner(event.name, ownerTypeName)) {
                return@mapNotNull null
            }
            val runtimeEventType = event.delegateType.normalized()
            val eventTypeName = runtimeEventType.typeName
            WinRTEventHelperSubclassDescriptor(
                eventTypeName = eventTypeName,
                projectedEventTypeName = eventTypeName,
                abiEventTypeName = renderEventSourceAbiTypeName(runtimeEventType),
                ownerTypeName = ownerTypeName,
                sourceClassName = eventSourceSubclassName(ownerTypeName, eventTypeName),
                genericArgumentTypeNames = runtimeEventType.typeArguments.map { it.normalized().typeName },
                usesSharedEventHandlerSource = false,
                interfaceId = closedDelegateInterfaceId(eventTypeName, plan.type.namespace, typesByQualifiedName),
            )
        }
}

private fun WinRTEventHandlerKind.isCollectionEventSourceHandler(): Boolean =
    this == WinRTEventHandlerKind.VectorChangedEventHandler ||
        this == WinRTEventHandlerKind.BindableVectorChangedEventHandler ||
        this == WinRTEventHandlerKind.MapChangedEventHandler

private fun WinRTEventHandlerKind.matchesCollectionEventOwner(
    eventName: String,
    ownerTypeName: String,
): Boolean =
    when (this) {
        WinRTEventHandlerKind.VectorChangedEventHandler ->
            eventName == "VectorChanged" &&
                ownerTypeName.substringBefore('<').removeSuffix("?") == "Windows.Foundation.Collections.IObservableVector"
        WinRTEventHandlerKind.BindableVectorChangedEventHandler ->
            eventName == "VectorChanged" &&
                ownerTypeName.substringBefore('<').removeSuffix("?") in setOf(
                    "Microsoft.UI.Xaml.Interop.IBindableObservableVector",
                    "Windows.UI.Xaml.Interop.IBindableObservableVector",
                )
        WinRTEventHandlerKind.MapChangedEventHandler ->
            eventName == "MapChanged" &&
                ownerTypeName.substringBefore('<').removeSuffix("?") == "Windows.Foundation.Collections.IObservableMap"
        else -> false
    }

private fun KotlinProjectionPlanner.closedDelegateInterfaceId(
    eventTypeName: String,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): io.github.composefluent.winrt.runtime.Guid? {
    val binding = classifyAbiTypeBinding(
        typeName = eventTypeName,
        currentNamespace = currentNamespace,
        typesByQualifiedName = typesByQualifiedName,
    )
    val invokeShape = binding.delegateInvokeShape ?: return binding.interfaceId
    return delegateInterfaceIdForBinding(binding, invokeShape)
}

private fun delegateInterfaceIdForBinding(
    binding: KotlinProjectionAbiTypeBinding,
    invokeShape: KotlinProjectionDelegateInvokeShape,
): io.github.composefluent.winrt.runtime.Guid? {
    val delegateIid = invokeShape.interfaceId ?: return binding.interfaceId
    if (binding.typeArguments.isEmpty()) {
        return delegateIid
    }
    val signatures = binding.typeArguments.map { argument -> eventSourceTypeSignature(argument) ?: return null }
    return io.github.composefluent.winrt.runtime.ParameterizedInterfaceId.createFromSignature(
        io.github.composefluent.winrt.runtime.WinRTTypeSignature.parameterizedInterface(
            delegateIid,
            *signatures.toTypedArray(),
        ),
    )
}

private fun eventSourceTypeSignature(
    binding: KotlinProjectionAbiTypeBinding,
): io.github.composefluent.winrt.runtime.WinRTTypeSignature? =
    when (binding.kind) {
        KotlinProjectionAbiValueKind.String -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.string()
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.GenericParameter -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.object_()
        KotlinProjectionAbiValueKind.Boolean -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.boolean()
        KotlinProjectionAbiValueKind.Int8 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.int8()
        KotlinProjectionAbiValueKind.UInt8 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.uint8()
        KotlinProjectionAbiValueKind.Int16 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.int16()
        KotlinProjectionAbiValueKind.UInt16 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.uint16()
        KotlinProjectionAbiValueKind.Int32 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.int32()
        KotlinProjectionAbiValueKind.UInt32 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.uint32()
        KotlinProjectionAbiValueKind.Int64 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.int64()
        KotlinProjectionAbiValueKind.UInt64 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.uint64()
        KotlinProjectionAbiValueKind.Float -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.float32()
        KotlinProjectionAbiValueKind.Double -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.float64()
        KotlinProjectionAbiValueKind.Char16 -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.char16()
        KotlinProjectionAbiValueKind.GuidValue -> io.github.composefluent.winrt.runtime.WinRTTypeSignature.guidValue()
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            binding.interfaceId?.let { interfaceId ->
                if (binding.typeArguments.isEmpty()) {
                    io.github.composefluent.winrt.runtime.WinRTTypeSignature.guid(interfaceId)
                } else {
                    val arguments = binding.typeArguments.map { argument -> eventSourceTypeSignature(argument) ?: return null }
                    io.github.composefluent.winrt.runtime.WinRTTypeSignature.parameterizedInterface(interfaceId, *arguments.toTypedArray())
                }
            }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            binding.interfaceId?.let { interfaceId ->
                io.github.composefluent.winrt.runtime.WinRTTypeSignature.runtimeClass(
                    binding.resolvedTypeName,
                    io.github.composefluent.winrt.runtime.WinRTTypeSignature.guid(interfaceId),
                )
            }
        KotlinProjectionAbiValueKind.Delegate ->
            binding.delegateInvokeShape?.interfaceId?.let(io.github.composefluent.winrt.runtime.WinRTTypeSignature::delegate)
                ?: binding.interfaceId?.let(io.github.composefluent.winrt.runtime.WinRTTypeSignature::delegate)
        else -> null
    }

private fun renderEventSourceAbiTypeName(type: WinRTTypeRef): String {
    val normalized = type.normalized()
    if (normalized.typeArguments.isEmpty()) {
        return "ABI.${normalized.typeName}"
    }
    return "ABI.${normalized.typeName.substringBefore('<')}<${normalized.typeArguments.joinToString(", ") { renderEventSourceAbiTypeName(it).removePrefix("ABI.") }}>"
}

private fun eventSourceSubclassName(ownerTypeName: String, eventTypeName: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$ownerTypeName\t$eventTypeName".toByteArray(StandardCharsets.UTF_8))
    return "_EventSource_${digest.take(8).joinToString("") { byte -> "%02x".format(byte) }}"
}
