package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiInventory
import io.github.kitectlab.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.kitectlab.winrt.metadata.WinRtMetadataParameterCategory
import io.github.kitectlab.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeRef
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtMetadataValidationOptions
import io.github.kitectlab.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.kitectlab.winrt.metadata.requireValidForProjection
import io.github.kitectlab.winrt.metadata.semanticHelpers
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.Marshaler
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.ParameterizedInterfaceId
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.NativeNestedStructFieldSpec
import io.github.kitectlab.winrt.runtime.NativeScalarFieldSpec
import io.github.kitectlab.winrt.runtime.NativeStructLayout
import io.github.kitectlab.winrt.runtime.NativeStructScalarKind
import io.github.kitectlab.winrt.runtime.WinRtBindableIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.kitectlab.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.kitectlab.winrt.runtime.WinRtDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtListProjection
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyListProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceArrayProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceValueAdapter
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtTypeSignature
import io.github.kitectlab.winrt.runtime.WinRtTypeHandle
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

class KotlinProjectionSupportRenderer {
    fun render(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    ): List<KotlinProjectionFile> {
        val inventory = WinRtMetadataProjectionInventoryBuilder.create(model, context).build()
        val semanticHelpers = model.semanticHelpers()
        val genericInstantiationWriters = semanticHelpers.genericInstantiationWriterDescriptors(context)
        return listOfNotNull(
            renderGenericAbiRegistry(inventory.genericAbiInventory),
            renderGenericTypeInstantiations(genericInstantiationWriters),
            renderEventProjectionHelpers(model, inventory),
            renderAbiImplementationPlan(plans),
            renderTypeShapeWriterPlan(inventory, plans),
            renderNamespaceAdditions(inventory),
        )
    }

    private fun renderGenericAbiRegistry(inventory: WinRtGenericAbiInventory): KotlinProjectionFile? {
        if (inventory.genericAbiDelegates.isEmpty() && inventory.derivedGenericInterfaces.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTGenericAbiRegistry")
            appendLine("internal data class GenericAbiDelegateEntry(")
            appendLine("    val name: String,")
            appendLine("    val sourceGenericType: String,")
            appendLine("    val operation: String,")
            appendLine("    val declaration: String,")
            appendLine("    val abiParameterTypes: List<String>,")
            appendLine("    val typeArrayShape: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTGenericAbiRegistry {")
            appendStringList("DERIVED_GENERIC_INTERFACES", inventory.derivedGenericInterfaces)
            appendLine("    val GENERIC_ABI_DELEGATES: List<GenericAbiDelegateEntry> = listOf(")
            inventory.genericAbiDelegates.forEach { delegate ->
                appendLine("        GenericAbiDelegateEntry(")
                appendLine("            name = ${delegate.abiDelegateName.kotlinString()},")
                appendLine("            sourceGenericType = ${delegate.sourceGenericType.typeName.kotlinString()},")
                appendLine("            operation = ${delegate.operationName.kotlinString()},")
                appendLine("            declaration = ${delegate.declaration.kotlinString()},")
                appendLine("            abiParameterTypes = ${delegate.abiParameterTypeNames.kotlinListLiteral()},")
                appendLine("            typeArrayShape = ${delegate.typeArrayShape.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val DELEGATES_BY_NAME: Map<String, GenericAbiDelegateEntry> = GENERIC_ABI_DELEGATES.associateBy { it.name }")
            appendLine("    val DELEGATES_BY_SOURCE_TYPE: Map<String, List<GenericAbiDelegateEntry>> = GENERIC_ABI_DELEGATES.groupBy { it.sourceGenericType }")
            appendLine("    val DERIVED_GENERIC_INTERFACE_SET: Set<String> = DERIVED_GENERIC_INTERFACES.toSet()")
            appendLine()
            appendLine("    fun delegateNamed(name: String): GenericAbiDelegateEntry? = DELEGATES_BY_NAME[name]")
            appendLine()
            appendLine("    fun delegatesForSourceType(sourceGenericType: String): List<GenericAbiDelegateEntry> =")
            appendLine("        DELEGATES_BY_SOURCE_TYPE[sourceGenericType].orEmpty()")
            appendLine()
            appendLine("    fun isDerivedGenericInterface(typeName: String): Boolean =")
            appendLine("        typeName in DERIVED_GENERIC_INTERFACE_SET")
            appendLine()
            appendLine("    fun registerAbiDelegates(register: (typeArrayShape: List<String>, delegateName: String) -> Unit) {")
            appendLine("        GENERIC_ABI_DELEGATES.forEach { entry ->")
            appendLine("            register(entry.typeArrayShape, entry.name)")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTGenericAbiRegistry.kt", contents)
    }

    private fun renderGenericTypeInstantiations(
        descriptors: List<WinRtGenericInstantiationWriterDescriptor>,
    ): KotlinProjectionFile? {
        if (descriptors.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTGenericTypeInstantiations")
            appendLine("internal data class GenericTypeInstantiationEntry(")
            appendLine("    val className: String,")
            appendLine("    val sourceType: String,")
            appendLine("    val isDelegate: Boolean,")
            appendLine("    val rcwFunctions: List<String>,")
            appendLine("    val vtableFunctions: List<String>,")
            appendLine("    val propertyAccessors: List<String>,")
            appendLine("    val dependencies: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTGenericTypeInstantiations {")
            appendLine("    val ENTRIES: List<GenericTypeInstantiationEntry> = listOf(")
            descriptors.forEach { descriptor ->
                appendLine("        GenericTypeInstantiationEntry(")
                appendLine("            className = ${descriptor.instantiationClassName.kotlinString()},")
                appendLine("            sourceType = ${descriptor.sourceTypeName.kotlinString()},")
                appendLine("            isDelegate = ${descriptor.isDelegateInstantiation},")
                appendLine("            rcwFunctions = ${descriptor.rcwFunctionNames.kotlinListLiteral()},")
                appendLine("            vtableFunctions = ${descriptor.vtableFunctionNames.kotlinListLiteral()},")
                appendLine("            propertyAccessors = ${descriptor.propertyAccessorFunctionNames.kotlinListLiteral()},")
                appendLine("            dependencies = ${descriptor.initializationDependencies.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val ENTRIES_BY_CLASS_NAME: Map<String, GenericTypeInstantiationEntry> = ENTRIES.associateBy { it.className }")
            appendLine("    val ENTRIES_BY_SOURCE_TYPE: Map<String, GenericTypeInstantiationEntry> = ENTRIES.associateBy { it.sourceType }")
            appendLine()
            appendLine("    fun entryForClassName(className: String): GenericTypeInstantiationEntry? =")
            appendLine("        ENTRIES_BY_CLASS_NAME[className]")
            appendLine()
            appendLine("    fun entryForSourceType(sourceType: String): GenericTypeInstantiationEntry? =")
            appendLine("        ENTRIES_BY_SOURCE_TYPE[sourceType]")
            appendLine()
            appendLine("    fun initializeAll(initialize: (GenericTypeInstantiationEntry) -> Unit) {")
            appendLine("        val visited = linkedSetOf<String>()")
            appendLine("        ENTRIES.forEach { initializeWithDependencies(it, visited, initialize) }")
            appendLine("    }")
            appendLine()
            appendLine("    fun initializeDependencies(")
            appendLine("        entry: GenericTypeInstantiationEntry,")
            appendLine("        initialize: (GenericTypeInstantiationEntry) -> Unit,")
            appendLine("    ) {")
            appendLine("        val visited = linkedSetOf(entry.className)")
            appendLine("        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)")
            appendLine("            .forEach { initializeWithDependencies(it, visited, initialize) }")
            appendLine("    }")
            appendLine()
            appendLine("    private fun initializeWithDependencies(")
            appendLine("        entry: GenericTypeInstantiationEntry,")
            appendLine("        visited: MutableSet<String>,")
            appendLine("        initialize: (GenericTypeInstantiationEntry) -> Unit,")
            appendLine("    ) {")
            appendLine("        if (!visited.add(entry.className)) return")
            appendLine("        entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)")
            appendLine("            .forEach { initializeWithDependencies(it, visited, initialize) }")
            appendLine("        initialize(entry)")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTGenericTypeInstantiations.kt", contents)
    }

    private fun renderEventProjectionHelpers(
        model: WinRtMetadataModel,
        inventory: WinRtMetadataProjectionInventory,
    ): KotlinProjectionFile? {
        val helpers = model.semanticHelpers()
        val subclassDescriptors = model.namespaces
            .flatMap(WinRtNamespace::types)
            .flatMap(helpers::eventHelperSubclassDescriptors)
            .distinctBy { it.eventTypeName to it.ownerTypeName }
            .sortedWith(compareBy({ it.eventTypeName }, { it.ownerTypeName }))
        if (inventory.eventSourceMappings.isEmpty() && subclassDescriptors.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTEventProjectionHelpers")
            appendLine("internal data class EventSourceEntry(")
            appendLine("    val eventType: String,")
            appendLine("    val ownerType: String,")
            appendLine("    val sourceClass: String,")
            appendLine("    val abiEventType: String,")
            appendLine("    val genericArguments: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTEventProjectionHelpers {")
            appendLine("    val EVENT_SOURCES: List<EventSourceEntry> = listOf(")
            subclassDescriptors.forEach { descriptor ->
                appendLine("        EventSourceEntry(")
                appendLine("            eventType = ${descriptor.eventTypeName.kotlinString()},")
                appendLine("            ownerType = ${descriptor.ownerTypeName.kotlinString()},")
                appendLine("            sourceClass = ${descriptor.sourceClassName.kotlinString()},")
                appendLine("            abiEventType = ${descriptor.abiEventTypeName.kotlinString()},")
                appendLine("            genericArguments = ${descriptor.genericArgumentTypeNames.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendStringList("EVENT_SOURCE_MAPPING_KEYS", inventory.eventSourceMappings.map { "${it.eventTypeName}->${it.sourceClassName}" })
            appendLine("    val EVENT_SOURCES_BY_EVENT_TYPE: Map<String, List<EventSourceEntry>> = EVENT_SOURCES.groupBy { it.eventType }")
            appendLine("    val EVENT_SOURCES_BY_OWNER_TYPE: Map<String, List<EventSourceEntry>> = EVENT_SOURCES.groupBy { it.ownerType }")
            appendLine()
            appendLine("    fun sourcesForEventType(eventType: String): List<EventSourceEntry> =")
            appendLine("        EVENT_SOURCES_BY_EVENT_TYPE[eventType].orEmpty()")
            appendLine()
            appendLine("    fun sourcesForOwnerType(ownerType: String): List<EventSourceEntry> =")
            appendLine("        EVENT_SOURCES_BY_OWNER_TYPE[ownerType].orEmpty()")
            appendLine()
            appendLine("    fun installEventSources(install: (EventSourceEntry) -> Unit) {")
            appendLine("        EVENT_SOURCES.forEach(install)")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTEventProjectionHelpers.kt", contents)
    }

    private fun renderAbiImplementationPlan(plans: List<KotlinTypeProjectionPlan>): KotlinProjectionFile? {
        val abiPlans = plans.filter { plan ->
            plan.typeDeclarationDescriptor.writesAbiDeclaration ||
                plan.typeDeclarationDescriptor.writesImplementationClass ||
                plan.genericAbiClassInitializationDescriptor != null ||
                plan.requiredInterfaceAugmentationDescriptor != null
        }
        if (abiPlans.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTAbiImplementationPlan")
            appendLine("internal data class AbiImplementationEntry(")
            appendLine("    val typeName: String,")
            appendLine("    val writesAbi: Boolean,")
            appendLine("    val writesImplementationClass: Boolean,")
            appendLine("    val vtableSlots: List<String>,")
            appendLine("    val genericInvokeSlots: List<String>,")
            appendLine("    val requiredInterfaces: List<String>,")
            appendLine("    val explicitForwards: List<String>,")
            appendLine("    val requiredMappedHelpers: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTAbiImplementationPlan {")
            appendLine("    val ENTRIES: List<AbiImplementationEntry> = listOf(")
            abiPlans.sortedBy { it.type.qualifiedName }.forEach { plan ->
                appendLine("        AbiImplementationEntry(")
                appendLine("            typeName = ${plan.type.qualifiedName.kotlinString()},")
                appendLine("            writesAbi = ${plan.typeDeclarationDescriptor.writesAbiDeclaration},")
                appendLine("            writesImplementationClass = ${plan.typeDeclarationDescriptor.writesImplementationClass},")
                appendLine("            vtableSlots = ${plan.abiSlotBindings.map { it.constantName }.kotlinListLiteral()},")
                appendLine("            genericInvokeSlots = ${plan.genericAbiClassInitializationDescriptor?.invokeSlotNames.orEmpty().kotlinListLiteral()},")
                appendLine("            requiredInterfaces = ${plan.requiredInterfaceAugmentationDescriptor?.requiredInterfaceNames.orEmpty().kotlinListLiteral()},")
                appendLine("            explicitForwards = ${plan.requiredInterfaceAugmentationDescriptor?.explicitForwardMemberNames.orEmpty().kotlinListLiteral()},")
                appendLine("            requiredMappedHelpers = ${plan.requiredInterfaceAugmentationDescriptor?.mappedHelperPlans.orEmpty().map { it.toSupportPlanString() }.kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val ENTRIES_BY_TYPE_NAME: Map<String, AbiImplementationEntry> = ENTRIES.associateBy { it.typeName }")
            appendLine()
            appendLine("    fun entryForType(typeName: String): AbiImplementationEntry? =")
            appendLine("        ENTRIES_BY_TYPE_NAME[typeName]")
            appendLine()
            appendLine("    fun requiresAbi(typeName: String): Boolean =")
            appendLine("        entryForType(typeName)?.writesAbi == true")
            appendLine()
            appendLine("    fun installAbiImplementations(install: (AbiImplementationEntry) -> Unit) {")
            appendLine("        ENTRIES.forEach(install)")
            appendLine("    }")
            appendLine()
            appendLine("    fun requiredInterfaceNames(): Set<String> =")
            appendLine("        ENTRIES.flatMap { it.requiredInterfaces }.toSet()")
            appendLine("}")
        }
        return supportFile("WinRTAbiImplementationPlan.kt", contents)
    }

    private fun renderTypeShapeWriterPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        if (plans.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTTypeShapeWriterPlan")
            appendLine("internal data class TypeShapeEntry(")
            appendLine("    val typeName: String,")
            appendLine("    val kind: String,")
            appendLine("    val mappedMembers: List<String>,")
            appendLine("    val mappedCallMode: String,")
            appendLine("    val mappedExplicit: Boolean,")
            appendLine("    val mappedPrivate: Boolean,")
            appendLine("    val factoryMembers: List<String>,")
            appendLine("    val moduleActivationEntries: List<String>,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTTypeShapeWriterPlan {")
            appendStringList("HELPER_OUTPUTS", inventory.helperOutputs.requiredHelperFileNames)
            appendStringList("BASE_TYPE_MAPPINGS", inventory.baseTypeMappings.map { "${it.typeName}->${it.baseTypeName}" })
            appendStringList("AUTHORING_METADATA_MAPPINGS", inventory.authoredMetadataTypeMappings.map { "${it.projectedTypeName}->${it.metadataTypeName}" })
            appendLine("    val TYPES: List<TypeShapeEntry> = listOf(")
            plans.sortedBy { it.type.qualifiedName }.forEach { plan ->
                appendLine("        TypeShapeEntry(")
                appendLine("            typeName = ${plan.type.qualifiedName.kotlinString()},")
                appendLine("            kind = ${plan.type.kind.name.kotlinString()},")
                appendLine("            mappedMembers = ${plan.customMappedMemberOutputDescriptor?.memberPlans.orEmpty().kotlinListLiteral()},")
                appendLine("            mappedCallMode = ${(plan.customMappedMemberOutputDescriptor?.callMode ?: "").kotlinString()},")
                appendLine("            mappedExplicit = ${plan.customMappedMemberOutputDescriptor?.emitsExplicitMembers ?: false},")
                appendLine("            mappedPrivate = ${plan.customMappedMemberOutputDescriptor?.emitsPrivateMembers ?: false},")
                appendLine("            factoryMembers = ${plan.moduleActivationAndAuthoringDescriptor?.factoryMemberNames.orEmpty().kotlinListLiteral()},")
                appendLine("            moduleActivationEntries = ${plan.moduleActivationAndAuthoringDescriptor?.moduleActivationFactoryEntries.orEmpty().kotlinListLiteral()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val TYPES_BY_NAME: Map<String, TypeShapeEntry> = TYPES.associateBy { it.typeName }")
            appendLine("    val BASE_TYPE_MAPPING_TABLE: Map<String, String> = BASE_TYPE_MAPPINGS.toArrowMap()")
            appendLine("    val AUTHORING_METADATA_MAPPING_TABLE: Map<String, String> = AUTHORING_METADATA_MAPPINGS.toArrowMap()")
            appendLine()
            appendLine("    fun typeShape(typeName: String): TypeShapeEntry? = TYPES_BY_NAME[typeName]")
            appendLine()
            appendLine("    fun registerBaseTypeMappings(register: (Map<String, String>) -> Unit) {")
            appendLine("        if (BASE_TYPE_MAPPING_TABLE.isNotEmpty()) {")
            appendLine("            register(BASE_TYPE_MAPPING_TABLE)")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    fun registerAuthoringMetadataMappings(register: (Map<String, String>) -> Unit) {")
            appendLine("        if (AUTHORING_METADATA_MAPPING_TABLE.isNotEmpty()) {")
            appendLine("            register(AUTHORING_METADATA_MAPPING_TABLE)")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    fun installModuleActivationFactories(install: (typeName: String, factoryMember: String) -> Unit) {")
            appendLine("        TYPES.forEach { type ->")
            appendLine("            type.moduleActivationEntries.forEach { factoryMember ->")
            appendLine("                install(type.typeName, factoryMember)")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    private fun List<String>.toArrowMap(): Map<String, String> =")
            appendLine("        mapNotNull { entry ->")
            appendLine("            val separator = entry.indexOf(\"->\")")
            appendLine("            if (separator < 0) null else entry.substring(0, separator) to entry.substring(separator + 2)")
            appendLine("        }.toMap()")
            appendLine("}")
        }
        return supportFile("WinRTTypeShapeWriterPlan.kt", contents)
    }

    private fun renderNamespaceAdditions(inventory: WinRtMetadataProjectionInventory): KotlinProjectionFile? {
        if (inventory.namespaceAdditions.isEmpty()) {
            return null
        }
        val contents = buildString {
            appendHeader("WinRTNamespaceAdditions")
            appendLine("internal data class NamespaceAdditionEntry(")
            appendLine("    val namespace: String,")
            appendLine(")")
            appendLine()
            appendLine("internal object WinRTNamespaceAdditions {")
            appendLine("    val ENTRIES: List<NamespaceAdditionEntry> = listOf(")
            inventory.namespaceAdditions.forEach { addition ->
                appendLine("        NamespaceAdditionEntry(")
                appendLine("            namespace = ${addition.namespace.kotlinString()},")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("    val ENTRIES_BY_NAMESPACE: Map<String, NamespaceAdditionEntry> = ENTRIES.associateBy { it.namespace }")
            appendLine()
            appendLine("    fun entryForNamespace(namespace: String): NamespaceAdditionEntry? =")
            appendLine("        ENTRIES_BY_NAMESPACE[namespace]")
            appendLine()
            appendLine("    fun installNamespaceAdditions(install: (NamespaceAdditionEntry) -> Unit) {")
            appendLine("        ENTRIES.forEach(install)")
            appendLine("    }")
            appendLine("}")
        }
        return supportFile("WinRTNamespaceAdditions.kt", contents)
    }

    private fun supportFile(fileName: String, contents: String): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = "io/github/kitectlab/winrt/projections/support/$fileName",
            packageName = SUPPORT_PACKAGE,
            contents = contents,
        )

    private fun StringBuilder.appendHeader(fileName: String) {
        appendLine("package $SUPPORT_PACKAGE")
        appendLine()
        appendLine("// Deterministic generator handoff for .cswinrt $fileName writer parity.")
        appendLine()
    }

    private fun StringBuilder.appendStringList(name: String, values: List<String>) {
        appendLine("    val $name: List<String> = ${values.kotlinListLiteral()}")
    }

    private fun String.kotlinString(): String = buildString {
        append('"')
        this@kotlinString.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun List<String>.kotlinListLiteral(): String =
        if (isEmpty()) {
            "emptyList()"
        } else {
            joinToString(prefix = "listOf(", postfix = ")") { it.kotlinString() }
        }

    private fun io.github.kitectlab.winrt.metadata.WinRtRequiredMappedHelperPlanDescriptor.toSupportPlanString(): String =
        listOf(
            interfaceName,
            memberFamily,
            callMode,
            "helper=${helperWrapperName.orEmpty()}",
            "adapter=${adapterFieldName.orEmpty()}",
            "private=$emitsPrivateMembers",
            "mappedHelpers=$emitsMappedTypeHelpers",
            "removeEnumerable=$removesNonGenericEnumerable",
            "removeGeneric=${removesGenericEnumerableName.orEmpty()}",
        ).joinToString("|")

    private companion object {
        const val SUPPORT_PACKAGE = "io.github.kitectlab.winrt.projections.support"
    }
}
