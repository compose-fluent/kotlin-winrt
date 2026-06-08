package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtEventHelperSubclassDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventHandlerKind
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.winRtEventHandlerKindForTypeName
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal fun KotlinProjectionPlanner.eventSourceDescriptors(
    model: WinRtMetadataModel,
    plans: List<KotlinTypeProjectionPlan>,
): List<WinRtEventHelperSubclassDescriptor> {
    val helpers = model.semanticHelpers()
    val metadataDescriptors = model.namespaces
        .flatMap(WinRtNamespace::types)
        .flatMap(helpers::eventHelperSubclassDescriptors)
        .filter { descriptor -> descriptor.ownerTypeName to descriptor.eventTypeName in plans.requiredEventSourceDescriptorKeys() }
    val typesByQualifiedName = model.namespaces
        .flatMap(WinRtNamespace::types)
        .associateBy(WinRtTypeDefinition::qualifiedName)
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
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): List<WinRtEventHelperSubclassDescriptor> {
    if (plan.type.kind != io.github.composefluent.winrt.metadata.WinRtTypeKind.RuntimeClass) {
        return emptyList()
    }
    return plan.type.events
        .filterNot { event -> event.isStatic }
        .mapNotNull { event ->
            val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == "${event.name.uppercase()}_ADD_SLOT" }
                ?: return@mapNotNull null
            val runtimeEventKind = winRtEventHandlerKindForTypeName(event.delegateTypeName)
            if (runtimeEventKind == null || !runtimeEventKind.isCollectionEventSourceHandler()) {
                return@mapNotNull null
            }
            val ownerTypeName = binding.slotInterfaceQualifiedName
            if (!runtimeEventKind.matchesCollectionEventOwner(event.name, ownerTypeName)) {
                return@mapNotNull null
            }
            val runtimeEventType = event.delegateType.normalized()
            val eventTypeName = runtimeEventType.typeName
            WinRtEventHelperSubclassDescriptor(
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

private fun WinRtEventHandlerKind.isCollectionEventSourceHandler(): Boolean =
    this == WinRtEventHandlerKind.VectorChangedEventHandler ||
        this == WinRtEventHandlerKind.BindableVectorChangedEventHandler ||
        this == WinRtEventHandlerKind.MapChangedEventHandler

private fun WinRtEventHandlerKind.matchesCollectionEventOwner(
    eventName: String,
    ownerTypeName: String,
): Boolean =
    when (this) {
        WinRtEventHandlerKind.VectorChangedEventHandler ->
            eventName == "VectorChanged" &&
                ownerTypeName.substringBefore('<').removeSuffix("?") == "Windows.Foundation.Collections.IObservableVector"
        WinRtEventHandlerKind.BindableVectorChangedEventHandler ->
            eventName == "VectorChanged" &&
                ownerTypeName.substringBefore('<').removeSuffix("?") in setOf(
                    "Microsoft.UI.Xaml.Interop.IBindableObservableVector",
                    "Windows.UI.Xaml.Interop.IBindableObservableVector",
                )
        WinRtEventHandlerKind.MapChangedEventHandler ->
            eventName == "MapChanged" &&
                ownerTypeName.substringBefore('<').removeSuffix("?") == "Windows.Foundation.Collections.IObservableMap"
        else -> false
    }

private fun KotlinProjectionPlanner.closedDelegateInterfaceId(
    eventTypeName: String,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
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
        io.github.composefluent.winrt.runtime.WinRtTypeSignature.parameterizedInterface(
            delegateIid,
            *signatures.toTypedArray(),
        ),
    )
}

private fun eventSourceTypeSignature(
    binding: KotlinProjectionAbiTypeBinding,
): io.github.composefluent.winrt.runtime.WinRtTypeSignature? =
    when (binding.kind) {
        KotlinProjectionAbiValueKind.String -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.string()
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.GenericParameter -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.object_()
        KotlinProjectionAbiValueKind.Boolean -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.boolean()
        KotlinProjectionAbiValueKind.Int8 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.int8()
        KotlinProjectionAbiValueKind.UInt8 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.uint8()
        KotlinProjectionAbiValueKind.Int16 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.int16()
        KotlinProjectionAbiValueKind.UInt16 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.uint16()
        KotlinProjectionAbiValueKind.Int32 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.int32()
        KotlinProjectionAbiValueKind.UInt32 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.uint32()
        KotlinProjectionAbiValueKind.Int64 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.int64()
        KotlinProjectionAbiValueKind.UInt64 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.uint64()
        KotlinProjectionAbiValueKind.Float -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.float32()
        KotlinProjectionAbiValueKind.Double -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.float64()
        KotlinProjectionAbiValueKind.Char16 -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.char16()
        KotlinProjectionAbiValueKind.GuidValue -> io.github.composefluent.winrt.runtime.WinRtTypeSignature.guidValue()
        KotlinProjectionAbiValueKind.ProjectedInterface ->
            binding.interfaceId?.let { interfaceId ->
                if (binding.typeArguments.isEmpty()) {
                    io.github.composefluent.winrt.runtime.WinRtTypeSignature.guid(interfaceId)
                } else {
                    val arguments = binding.typeArguments.map { argument -> eventSourceTypeSignature(argument) ?: return null }
                    io.github.composefluent.winrt.runtime.WinRtTypeSignature.parameterizedInterface(interfaceId, *arguments.toTypedArray())
                }
            }
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            binding.interfaceId?.let { interfaceId ->
                io.github.composefluent.winrt.runtime.WinRtTypeSignature.runtimeClass(
                    binding.resolvedTypeName,
                    io.github.composefluent.winrt.runtime.WinRtTypeSignature.guid(interfaceId),
                )
            }
        KotlinProjectionAbiValueKind.Delegate ->
            binding.delegateInvokeShape?.interfaceId?.let(io.github.composefluent.winrt.runtime.WinRtTypeSignature::delegate)
                ?: binding.interfaceId?.let(io.github.composefluent.winrt.runtime.WinRtTypeSignature::delegate)
        else -> null
    }

private fun renderEventSourceAbiTypeName(type: WinRtTypeRef): String {
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
