package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventHelperSubclassDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiDelegateDescriptor
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
import com.squareup.kotlinpoet.STAR
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
    private val typeRenderer = KotlinProjectionRenderer()
    private val planner = KotlinProjectionPlanner()
    private val AUTHORING_ABI_OPERATIONS = listOf(
        "GetAbi",
        "FromAbi",
        "FromManaged",
        "CreateMarshaler",
        "CreateMarshaler2",
        "CreateMarshalerArray",
        "GetAbiArray",
        "FromAbiArray",
        "CopyAbiArray",
        "FromManagedArray",
        "DisposeMarshaler",
        "DisposeMarshalerArray",
        "DisposeAbi",
        "DisposeAbiArray",
    )
    private val AUTHORING_CUSTOM_QI_NOT_HANDLED_INTERFACES = listOf(
        "IInspectable",
        "IWeakReferenceSource",
    )

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
            renderEventProjectionHelpers(model, plans, inventory),
            renderAbiImplementationPlan(plans),
            renderTypeShapeWriterPlan(inventory, plans),
            renderAuthoringMetadataTypeMappingHelper(inventory),
            renderAuthoringWrapperPlan(inventory, plans),
            renderAuthoringAbiClassPlan(inventory, plans, semanticHelpers),
            renderAuthoringWrappers(inventory, plans),
            renderAuthoringAbiClasses(inventory, plans, semanticHelpers),
            renderAuthoringCustomQueryInterfacePlan(inventory, plans, semanticHelpers),
            renderAuthoringActivationFactoryPlan(inventory, plans, semanticHelpers),
            renderAuthoringModuleActivationFactoryPlan(inventory, plans, semanticHelpers),
            renderAuthoringServerActivationFactories(inventory, plans, semanticHelpers),
            renderAuthoringCcwFactories(inventory, plans, semanticHelpers),
            renderNamespaceAdditions(inventory),
        )
    }

    private fun renderGenericAbiRegistry(inventory: WinRtGenericAbiInventory): KotlinProjectionFile? {
        if (inventory.genericAbiDelegates.isEmpty() && inventory.derivedGenericInterfaces.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "GenericAbiDelegateEntry")
        val fileSpec = supportFileSpec("WinRTGenericAbiRegistry")
            .addType(
                dataClass(
                    className = "GenericAbiDelegateEntry",
                    fields = listOf(
                        "name" to stringTypeName(),
                        "sourceGenericType" to stringTypeName(),
                        "operation" to stringTypeName(),
                        "declaration" to stringTypeName(),
                        "abiParameterTypes" to stringListTypeName(),
                        "typeArrayShape" to stringListTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTGenericAbiRegistry")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(stringListProperty("DERIVED_GENERIC_INTERFACES", inventory.derivedGenericInterfaces))
                    .addProperty(
                        PropertySpec.builder("GENERIC_ABI_DELEGATES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(genericAbiDelegateEntriesCode(inventory.genericAbiDelegates, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("DELEGATES_BY_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("GENERIC_ABI_DELEGATES.associateBy({ it.name })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "DELEGATES_BY_SOURCE_TYPE",
                            Map::class.asClassName().parameterizedBy(stringTypeName(), List::class.asClassName().parameterizedBy(entryClass)),
                        )
                            .initializer("GENERIC_ABI_DELEGATES.groupBy({ it.sourceGenericType })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("DERIVED_GENERIC_INTERFACE_SET", Set::class.asClassName().parameterizedBy(stringTypeName()))
                            .initializer("DERIVED_GENERIC_INTERFACES.toSet()")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("delegateNamed")
                            .addParameter("name", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return DELEGATES_BY_NAME[name]")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("delegatesForSourceType")
                            .addParameter("sourceGenericType", String::class)
                            .returns(List::class.asClassName().parameterizedBy(entryClass))
                            .addStatement("return DELEGATES_BY_SOURCE_TYPE[sourceGenericType].orEmpty()")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("isDerivedGenericInterface")
                            .addParameter("typeName", String::class)
                            .returns(Boolean::class)
                            .addStatement("return typeName in DERIVED_GENERIC_INTERFACE_SET")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("registerAbiDelegates")
                            .addParameter(
                                "register",
                                Function2::class.asClassName().parameterizedBy(stringListTypeName(), stringTypeName(), UNIT),
                            )
                            .addCode(
                                CodeBlock.of(
                                    "GENERIC_ABI_DELEGATES.forEach { entry ->\n⇥register(entry.typeArrayShape, entry.name)\n⇤}\n",
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return supportFile("WinRTGenericAbiRegistry.kt", fileSpec)
    }

    private fun renderGenericTypeInstantiations(
        descriptors: List<WinRtGenericInstantiationWriterDescriptor>,
    ): KotlinProjectionFile? {
        if (descriptors.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "GenericTypeInstantiationEntry")
        val bindingClass = ClassName(SUPPORT_PACKAGE, "GenericTypeInstantiationRuntimeBinding")
        val fileSpec = supportFileSpec("WinRTGenericTypeInstantiations")
            .addType(
                dataClass(
                    className = "GenericTypeInstantiationEntry",
                    fields = listOf(
                        "className" to stringTypeName(),
                        "sourceType" to stringTypeName(),
                        "isDelegate" to Boolean::class.asClassName(),
                        "rcwFunctions" to stringListTypeName(),
                        "vtableFunctions" to stringListTypeName(),
                        "propertyAccessors" to stringListTypeName(),
                        "genericReturnOnlyRcwFunctions" to stringListTypeName(),
                        "projectedGenericFallbacks" to stringListTypeName(),
                        "dependencies" to stringListTypeName(),
                    ),
                ),
            )
            .addType(genericTypeInstantiationRuntimeBindingType(entryClass))
            .addType(
                TypeSpec.objectBuilder("WinRTGenericTypeInstantiations")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(genericTypeInstantiationEntriesCode(descriptors, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_CLASS_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("ENTRIES.associateBy({ it.className })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_SOURCE_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("ENTRIES.associateBy({ it.sourceType })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("INITIALIZED_CLASS_NAMES", MUTABLE_SET_CLASS_NAME.parameterizedBy(stringTypeName()))
                            .addModifiers(KModifier.PRIVATE)
                            .initializer("linkedSetOf()")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("runtimeBinding", bindingClass)
                            .addModifiers(KModifier.PRIVATE)
                            .mutable()
                            .initializer(defaultGenericTypeRuntimeBindingCode(bindingClass))
                            .build(),
                    )
                    .addFunctions(genericTypeInstantiationFunctions(entryClass, bindingClass))
                    .build(),
            )
            .build()
        return supportFile("WinRTGenericTypeInstantiations.kt", fileSpec)
    }

    private fun renderEventProjectionHelpers(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
        inventory: WinRtMetadataProjectionInventory,
    ): KotlinProjectionFile? {
        val helpers = model.semanticHelpers()
        val plansByType = plans.associateBy { it.type.qualifiedName }
        val typesByQualifiedName = plans.associate { it.type.qualifiedName to it.type }
        val eventSourceEntries = model.namespaces
            .flatMap(WinRtNamespace::types)
            .flatMap(helpers::eventHelperSubclassDescriptors)
            .distinctBy { it.eventTypeName to it.ownerTypeName }
            .sortedWith(compareBy({ it.eventTypeName }, { it.ownerTypeName }))
        val eventSourceFactoryDescriptors = eventSourceEntries
            .distinctBy {
                if (it.usesSharedEventHandlerSource) {
                    it.eventTypeName
                } else {
                    it.sourceClassName
                }
            }
        val eventSourceClassDescriptors = eventSourceEntries
            .filterNot { it.usesSharedEventHandlerSource }
            .distinctBy { it.sourceClassName }
        if (inventory.eventSourceMappings.isEmpty() && eventSourceEntries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "EventSourceEntry")
        val objectBuilder = TypeSpec.objectBuilder("WinRTEventProjectionHelpers")
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("EVENT_SOURCES", List::class.asClassName().parameterizedBy(entryClass))
                    .initializer(eventSourceEntriesCode(eventSourceEntries, entryClass))
                    .build(),
            )
            .addProperty(stringListProperty("EVENT_SOURCE_MAPPING_KEYS", inventory.eventSourceMappings.map { "${it.eventTypeName}->${it.sourceClassName}" }))
            .addProperty(
                PropertySpec.builder(
                    "EVENT_SOURCES_BY_EVENT_TYPE",
                    Map::class.asClassName().parameterizedBy(stringTypeName(), List::class.asClassName().parameterizedBy(entryClass)),
                )
                    .initializer("EVENT_SOURCES.groupBy({ it.eventType })")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder(
                    "EVENT_SOURCES_BY_OWNER_TYPE",
                    Map::class.asClassName().parameterizedBy(stringTypeName(), List::class.asClassName().parameterizedBy(entryClass)),
                )
                    .initializer("EVENT_SOURCES.groupBy({ it.ownerType })")
                    .build(),
            )
            .addFunctions(eventProjectionHelperFunctions(entryClass, eventSourceFactoryDescriptors, plansByType, typesByQualifiedName))
        val fileBuilder = supportFileSpec("WinRTEventProjectionHelpers")
            .addType(
                dataClass(
                    className = "EventSourceEntry",
                    fields = listOf(
                        "eventType" to stringTypeName(),
                        "ownerType" to stringTypeName(),
                        "sourceClass" to stringTypeName(),
                        "abiEventType" to stringTypeName(),
                        "genericArguments" to stringListTypeName(),
                        "usesSharedEventHandlerSource" to Boolean::class.asClassName(),
                    ),
                ),
            )
        eventSourceClassDescriptors
            .forEach { descriptor ->
                val rawEventType = descriptor.projectedEventTypeName.substringBefore('<')
                val delegatePlan = plansByType[rawEventType] ?: return@forEach
                val invokeShape = concreteEventInvokeShape(descriptor, typesByQualifiedName) ?: return@forEach
                if (invokeShape.isSupportedProjectedDelegateShape()) {
                    fileBuilder.addType(eventSourceSubclassType(descriptor, delegatePlan, invokeShape))
                }
            }
        val fileSpec = fileBuilder
            .addType(objectBuilder.build())
            .build()
        return supportFile("WinRTEventProjectionHelpers.kt", fileSpec)
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
        val entryClass = ClassName(SUPPORT_PACKAGE, "AbiImplementationEntry")
        val sortedAbiPlans = abiPlans.sortedBy { it.type.qualifiedName }
        val chunkedAbiPlans = sortedAbiPlans.chunked(96)
        val objectBuilder = TypeSpec.objectBuilder("WinRTAbiImplementationPlan")
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                    .initializer(abiEntriesBuildListCode(chunkedAbiPlans.indices))
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("ENTRIES_BY_TYPE_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                    .initializer("ENTRIES.associateBy({ it.typeName })")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("entryForType")
                    .addParameter("typeName", String::class)
                    .returns(entryClass.copy(nullable = true))
                    .addStatement("return ENTRIES_BY_TYPE_NAME[typeName]")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("requiresAbi")
                    .addParameter("typeName", String::class)
                    .returns(Boolean::class)
                    .addStatement("return entryForType(typeName)?.writesAbi == true")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("installAbiImplementations")
                    .addParameter("install", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                    .addStatement("ENTRIES.forEach(install)")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("requiredInterfaceNames")
                    .returns(Set::class.asClassName().parameterizedBy(stringTypeName()))
                    .addStatement("return ENTRIES.flatMap { it.requiredInterfaces }.toSet()")
                    .build(),
            )
        chunkedAbiPlans.forEachIndexed { index, chunk ->
            objectBuilder.addFunction(
                FunSpec.builder("entriesChunk$index")
                    .addModifiers(KModifier.PRIVATE)
                    .returns(List::class.asClassName().parameterizedBy(entryClass))
                    .addStatement("return %L", abiImplementationEntriesCode(chunk, entryClass))
                    .build(),
            )
        }
        val fileSpec = supportFileSpec("WinRTAbiImplementationPlan")
            .addType(
                dataClass(
                    className = "AbiImplementationEntry",
                    fields = listOf(
                        "typeName" to stringTypeName(),
                        "writesAbi" to Boolean::class.asClassName(),
                        "writesImplementationClass" to Boolean::class.asClassName(),
                        "vtableSlots" to stringListTypeName(),
                        "genericInvokeSlots" to stringListTypeName(),
                        "requiredInterfaces" to stringListTypeName(),
                        "explicitForwards" to stringListTypeName(),
                        "requiredMappedHelpers" to stringListTypeName(),
                    ),
                ),
            )
            .addType(objectBuilder.build())
            .build()
        return supportFile("WinRTAbiImplementationPlan.kt", fileSpec)
    }

    private fun renderTypeShapeWriterPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        if (plans.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "TypeShapeEntry")
        val fileSpec = supportFileSpec("WinRTTypeShapeWriterPlan")
            .addType(
                dataClass(
                    className = "TypeShapeEntry",
                    fields = listOf(
                        "typeName" to stringTypeName(),
                        "kind" to stringTypeName(),
                        "mappedMembers" to stringListTypeName(),
                        "mappedCallMode" to stringTypeName(),
                        "mappedExplicit" to Boolean::class.asClassName(),
                        "mappedPrivate" to Boolean::class.asClassName(),
                        "deferredAuthoringFactoryMembers" to stringListTypeName(),
                        "deferredModuleActivationEntries" to stringListTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTTypeShapeWriterPlan")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(stringListProperty("HELPER_OUTPUTS", inventory.helperOutputs.requiredHelperFileNames))
                    .addProperty(stringListProperty("BASE_TYPE_MAPPINGS", inventory.baseTypeMappings.map { "${it.typeName}->${it.baseTypeName}" }))
                    .addProperty(stringListProperty("AUTHORING_METADATA_MAPPINGS", inventory.authoredMetadataTypeMappings.map { "${it.projectedTypeName}->${it.metadataTypeName}" }))
                    .addProperty(
                        PropertySpec.builder("TYPES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(typeShapeEntriesCode(plans.sortedBy { it.type.qualifiedName }, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("TYPES_BY_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("TYPES.associateBy({ it.typeName })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("BASE_TYPE_MAPPING_TABLE", Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()))
                            .initializer("BASE_TYPE_MAPPINGS.toArrowMap()")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("AUTHORING_METADATA_MAPPING_TABLE", Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()))
                            .initializer("AUTHORING_METADATA_MAPPINGS.toArrowMap()")
                            .build(),
                    )
                    .addFunctions(typeShapePlanFunctions(entryClass))
                    .build(),
            )
            .build()
        return supportFile("WinRTTypeShapeWriterPlan.kt", fileSpec)
    }

    private fun renderAuthoringMetadataTypeMappingHelper(
        inventory: WinRtMetadataProjectionInventory,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val fileSpec = supportFileSpec("AuthoringMetadataTypeMappingHelper")
            .addImport("io.github.kitectlab.winrt.runtime", "ComWrappersSupport")
            .addType(
                TypeSpec.objectBuilder("AuthoringMetadataTypeMappingHelper")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        stringListProperty(
                            "AUTHORING_METADATA_MAPPINGS",
                            inventory.authoredMetadataTypeMappings.map { "${it.projectedTypeName}->${it.metadataTypeName}" },
                        ),
                    )
                    .addProperty(
                        PropertySpec.builder("AUTHORING_METADATA_MAPPING_TABLE", Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()))
                            .addModifiers(KModifier.PRIVATE)
                            .initializer("AUTHORING_METADATA_MAPPINGS.toArrowMap()")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("initialize")
                            .addCode(
                                CodeBlock.of(
                                    "if (AUTHORING_METADATA_MAPPING_TABLE.isNotEmpty()) {\n⇥ComWrappersSupport.registerAuthoringMetadataTypeMappings(AUTHORING_METADATA_MAPPING_TABLE)\n⇤}\n",
                                ),
                            )
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("getMetadataTypeMapping")
                            .addParameter("projectedTypeName", String::class)
                            .returns(String::class.asClassName().copy(nullable = true))
                            .addStatement("return AUTHORING_METADATA_MAPPING_TABLE[projectedTypeName]")
                            .build(),
                    )
                    .addFunction(toArrowMapFunction())
                    .build(),
            )
            .build()
        return supportFile("AuthoringMetadataTypeMappingHelper.kt", fileSpec)
    }

    private fun renderAuthoringWrapperPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val mappingsByProjectedName = inventory.authoredMetadataTypeMappings.associateBy { it.projectedTypeName }
        val entries = plans.mapNotNull { plan ->
            val mapping = mappingsByProjectedName[plan.type.qualifiedName] ?: return@mapNotNull null
            plan to mapping
        }
        if (entries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "AuthoringWrapperEntry")
        val objectBuilder = TypeSpec.objectBuilder("WinRTAuthoringWrapperPlan")
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("WRAPPERS", List::class.asClassName().parameterizedBy(entryClass))
                    .initializer(authoringWrapperEntriesCode(entries, entryClass))
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("WRAPPERS_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                    .initializer("WRAPPERS.associateBy({ it.projectedTypeName })")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("wrapperForProjectedType")
                    .addParameter("projectedTypeName", String::class)
                    .returns(entryClass.copy(nullable = true))
                    .addStatement("return WRAPPERS_BY_PROJECTED_TYPE[projectedTypeName]")
                    .build(),
            )
        val fileSpec = supportFileSpec("WinRTAuthoringWrapperPlan")
            .addType(
                dataClass(
                    className = "AuthoringWrapperEntry",
                    fields = listOf(
                        "projectedTypeName" to stringTypeName(),
                        "metadataTypeName" to stringTypeName(),
                        "kind" to stringTypeName(),
                        "defaultInterfaceName" to stringTypeName().copy(nullable = true),
                        "implementedInterfaceNames" to stringListTypeName(),
                        "factoryMemberNames" to stringListTypeName(),
                        "composableBaseTypeName" to stringTypeName().copy(nullable = true),
                    ),
                ),
            )
            .addType(objectBuilder.build())
            .build()
        return supportFile("WinRTAuthoringWrapperPlan.kt", fileSpec)
    }

    private fun renderAuthoringAbiClassPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && !semanticHelpers.isStatic(it.type) }
            .filter { plan -> inventory.authoredMetadataTypeMappings.any { it.projectedTypeName == plan.type.qualifiedName } }
        if (entries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "AuthoringAbiClassEntry")
        val fileSpec = supportFileSpec("WinRTAuthoringAbiClassPlan")
            .addType(
                dataClass(
                    className = "AuthoringAbiClassEntry",
                    fields = listOf(
                        "projectedTypeName" to stringTypeName(),
                        "abiTypeName" to stringTypeName(),
                        "ccwTypeName" to stringTypeName(),
                        "defaultInterfaceName" to stringTypeName().copy(nullable = true),
                        "defaultInterfaceIsExclusiveTo" to Boolean::class.asClassName(),
                        "marshalerFamily" to stringTypeName(),
                        "operations" to stringListTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTAuthoringAbiClassPlan")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("ABI_OPERATIONS", stringListTypeName())
                            .initializer(stringListCode(AUTHORING_ABI_OPERATIONS))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("CLASSES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(authoringAbiClassEntriesCode(entries, semanticHelpers, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("CLASSES_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("CLASSES.associateBy({ it.projectedTypeName })")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("abiClassForProjectedType")
                            .addParameter("projectedTypeName", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return CLASSES_BY_PROJECTED_TYPE[projectedTypeName]")
                            .build(),
                    )
                    .build(),
            )
            .build()
        return supportFile("WinRTAuthoringAbiClassPlan.kt", fileSpec)
    }

    private fun renderAuthoringWrappers(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { KotlinProjectionSpecializationKind.StaticClass in it.specializationKinds }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec("WinRTAuthoringWrappers")
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            fileBuilder.addType(authoringWrapperObject(plan))
        }
        fileBuilder.addType(
            TypeSpec.objectBuilder("WinRTAuthoringWrappers")
                .addModifiers(KModifier.INTERNAL)
                .addFunction(authoringWrapperLookupFunction(entries))
                .build(),
        )
        return supportFile("WinRTAuthoringWrappers.kt", fileBuilder.build())
    }

    private fun renderAuthoringAbiClasses(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec("WinRTAuthoringAbiClasses")
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            fileBuilder.addType(authoringAbiClassObject(plan))
        }
        fileBuilder.addFunction(unsupportedAuthoringAbiArrayOperationFunction())
        fileBuilder.addType(
            TypeSpec.objectBuilder("WinRTAuthoringAbiClasses")
                .addModifiers(KModifier.INTERNAL)
                .addFunction(authoringAbiClassFromAbiLookupFunction(entries))
                .build(),
        )
        return supportFile("WinRTAuthoringAbiClasses.kt", fileBuilder.build())
    }

    private fun unsupportedAuthoringAbiArrayOperationFunction(): FunSpec =
        FunSpec.builder("unsupportedAuthoringAbiArrayOperation")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("projectedTypeName", String::class)
            .addParameter("operation", String::class)
            .addCode(
                CodeBlock.of(
                    "throw %T(%S + operation + %S + projectedTypeName)\n",
                    UnsupportedOperationException::class,
                    "Authoring ABI array operation is not implemented yet: ",
                    " for ",
                ),
            )
            .build()

    private fun renderAuthoringCustomQueryInterfacePlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "AuthoringCustomQueryInterfaceEntry")
        val fileSpec = supportFileSpec("WinRTAuthoringCustomQueryInterfacePlan")
            .addType(
                dataClass(
                    className = "AuthoringCustomQueryInterfaceEntry",
                    fields = listOf(
                        "projectedTypeName" to stringTypeName(),
                        "visibility" to stringTypeName(),
                        "overridableModifier" to stringTypeName(),
                        "overridableInterfaceNames" to stringListTypeName(),
                        "delegatesToBase" to Boolean::class.asClassName(),
                        "notHandledInterfaceNames" to stringListTypeName(),
                        "forwardTarget" to stringTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTAuthoringCustomQueryInterfacePlan")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("NOT_HANDLED_INTERFACE_NAMES", stringListTypeName())
                            .initializer(stringListCode(AUTHORING_CUSTOM_QI_NOT_HANDLED_INTERFACES))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(authoringCustomQiEntriesCode(entries, semanticHelpers, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("ENTRIES.associateBy({ it.projectedTypeName })")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("customQueryInterfaceForProjectedType")
                            .addParameter("projectedTypeName", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return ENTRIES_BY_PROJECTED_TYPE[projectedTypeName]")
                            .build(),
                    )
                    .build(),
            )
            .build()
        return supportFile("WinRTAuthoringCustomQueryInterfacePlan.kt", fileSpec)
    }

    private fun renderAuthoringActivationFactoryPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "AuthoringActivationFactoryEntry")
        val fileSpec = supportFileSpec("WinRTAuthoringActivationFactoryPlan")
            .addType(
                dataClass(
                    className = "AuthoringActivationFactoryEntry",
                    fields = listOf(
                        "projectedTypeName" to stringTypeName(),
                        "serverFactoryTypeName" to stringTypeName(),
                        "isActivatable" to Boolean::class.asClassName(),
                        "implementsIActivationFactory" to Boolean::class.asClassName(),
                        "factoryInterfaceNames" to stringListTypeName(),
                        "activatableFactoryInterfaceNames" to stringListTypeName(),
                        "staticFactoryInterfaceNames" to stringListTypeName(),
                        "activatableFactoryMemberNames" to stringListTypeName(),
                        "staticFactoryMemberNames" to stringListTypeName(),
                        "composableFactoryMemberNames" to stringListTypeName(),
                        "makeMethod" to stringTypeName(),
                        "activateInstanceBehavior" to stringTypeName(),
                        "runClassConstructorTypeName" to stringTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTAuthoringActivationFactoryPlan")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("FACTORIES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(authoringActivationFactoryEntriesCode(entries, semanticHelpers, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("FACTORIES_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("FACTORIES.associateBy({ it.projectedTypeName })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("FACTORIES_BY_SERVER_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("FACTORIES.associateBy({ it.serverFactoryTypeName })")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("factoryForProjectedType")
                            .addParameter("projectedTypeName", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return FACTORIES_BY_PROJECTED_TYPE[projectedTypeName]")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("factoryForServerType")
                            .addParameter("serverFactoryTypeName", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return FACTORIES_BY_SERVER_TYPE[serverFactoryTypeName]")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("installActivationFactories")
                            .addParameter("install", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                            .addStatement("FACTORIES.forEach(install)")
                            .build(),
                    )
                    .build(),
            )
            .build()
        return supportFile("WinRTAuthoringActivationFactoryPlan.kt", fileSpec)
    }

    private fun renderNamespaceAdditions(inventory: WinRtMetadataProjectionInventory): KotlinProjectionFile? {
        if (inventory.namespaceAdditions.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "NamespaceAdditionEntry")
        val fileSpec = supportFileSpec("WinRTNamespaceAdditions")
            .addType(
                dataClass(
                    className = "NamespaceAdditionEntry",
                    fields = listOf(
                        "namespace" to stringTypeName(),
                        "kind" to stringTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTNamespaceAdditions")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(namespaceAdditionEntriesCode(inventory, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_NAMESPACE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("ENTRIES.associateBy({ it.namespace })")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("entryForNamespace")
                            .addParameter("namespace", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return ENTRIES_BY_NAMESPACE[namespace]")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("installNamespaceAdditions")
                            .addParameter("install", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                            .addStatement("ENTRIES.forEach(install)")
                            .build(),
                    )
                    .build(),
            )
            .build()
        return supportFile("WinRTNamespaceAdditions.kt", fileSpec)
    }

    private fun renderAuthoringModuleActivationFactoryPlan(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "AuthoringModuleActivationFactoryEntry")
        val fileSpec = supportFileSpec("WinRTAuthoringModuleActivationFactoryPlan")
            .addType(
                dataClass(
                    className = "AuthoringModuleActivationFactoryEntry",
                    fields = listOf(
                        "runtimeClassName" to stringTypeName(),
                        "serverFactoryTypeName" to stringTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder("WinRTAuthoringModuleActivationFactoryPlan")
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .initializer(authoringModuleActivationFactoryEntriesCode(entries, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_RUNTIME_CLASS_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .initializer("ENTRIES.associateBy({ it.runtimeClassName })")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("entryForRuntimeClassName")
                            .addParameter("runtimeClassName", String::class)
                            .returns(entryClass.copy(nullable = true))
                            .addStatement("return ENTRIES_BY_RUNTIME_CLASS_NAME[runtimeClassName]")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("getActivationFactory")
                            .addParameter("runtimeClassName", String::class)
                            .addParameter("interfaceId", GUID_CLASS_NAME)
                            .addParameter("factory", Function2::class.asClassName().parameterizedBy(entryClass, GUID_CLASS_NAME, RAW_ADDRESS_CLASS_NAME))
                            .addParameter("fallback", Function2::class.asClassName().parameterizedBy(stringTypeName(), GUID_CLASS_NAME, RAW_ADDRESS_CLASS_NAME))
                            .returns(RAW_ADDRESS_CLASS_NAME)
                            .addCode(
                                "val entry = entryForRuntimeClassName(runtimeClassName)\n" +
                                    "return if (entry != null) {\n" +
                                    "    factory(entry, interfaceId)\n" +
                                    "} else {\n" +
                                    "    fallback(runtimeClassName, interfaceId)\n" +
                                    "}\n",
                            )
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("installModuleActivationFactories")
                            .addParameter("install", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                            .addStatement("ENTRIES.forEach(install)")
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("registerModuleActivationFactories")
                            .addParameter("createFactory", Function1::class.asClassName().parameterizedBy(entryClass, COM_OBJECT_REFERENCE_CLASS_NAME))
                            .addCode(
                                "ENTRIES.forEach { entry ->\n" +
                                    "    %T.registerAuthoringActivationFactory(entry.runtimeClassName) {\n" +
                                    "        createFactory(entry)\n" +
                                    "    }\n" +
                                    "}\n",
                                COM_WRAPPERS_SUPPORT_CLASS_NAME,
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        return supportFile("WinRTAuthoringModuleActivationFactoryPlan.kt", fileSpec)
    }

    private fun renderAuthoringServerActivationFactories(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec("WinRTAuthoringServerActivationFactories")
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            fileBuilder.addType(authoringServerActivationFactoryClass(plan, semanticHelpers))
        }
        fileBuilder.addType(
            TypeSpec.objectBuilder("WinRTAuthoringServerActivationFactories")
                .addModifiers(KModifier.INTERNAL)
                .addFunction(authoringServerActivationFactoryRegisterFunction(entries))
                .build(),
        )
        return supportFile("WinRTAuthoringServerActivationFactories.kt", fileBuilder.build())
    }

    private fun authoringServerActivationFactoryClass(
        plan: KotlinTypeProjectionPlan,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): TypeSpec {
        val projectedType = ClassName(plan.packageName, plan.type.name)
        val isActivatable = !semanticHelpers.isStatic(plan.type) && semanticHelpers.hasDefaultConstructor(plan.type)
        return TypeSpec.classBuilder(authoringServerActivationFactoryClassName(plan))
            .addModifiers(KModifier.INTERNAL)
            .addSuperinterface(WINRT_ACTIVATION_FACTORY_CLASS_NAME)
            .addFunction(
                FunSpec.builder("activateInstance")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(COM_OBJECT_REFERENCE_CLASS_NAME)
                    .apply {
                        if (isActivatable) {
                            addStatement("return %T.createCCWForObject(%T())", COM_WRAPPERS_SUPPORT_CLASS_NAME, projectedType)
                        } else {
                            addStatement(
                                "throw %T(%S)",
                                UnsupportedOperationException::class.asClassName(),
                                "Runtime class '${plan.type.qualifiedName}' does not expose default activation.",
                            )
                        }
                    }
                    .build(),
            )
            .build()
    }

    private fun authoringServerActivationFactoryRegisterFunction(
        entries: List<KotlinTypeProjectionPlan>,
    ): FunSpec {
        val code = CodeBlock.builder()
        code.add("%T.registerModuleActivationFactories { entry ->\n", ClassName(SUPPORT_PACKAGE, "WinRTAuthoringModuleActivationFactoryPlan"))
        code.indent()
        code.add("when (entry.runtimeClassName) {\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            code.add(
                "%S -> %T.createCCWForObject(%T(), %T.IActivationFactory)\n",
                plan.type.qualifiedName,
                COM_WRAPPERS_SUPPORT_CLASS_NAME,
                ClassName(SUPPORT_PACKAGE, authoringServerActivationFactoryClassName(plan)),
                IID_CLASS_NAME,
            )
        }
        code.add("else -> error(%S + entry.runtimeClassName)\n", "No authored activation factory for runtime class: ")
        code.unindent()
        code.add("}\n")
        code.unindent()
        code.add("}\n")
        return FunSpec.builder("register")
            .addCode(code.build())
            .build()
    }

    private fun authoringServerActivationFactoryClassName(plan: KotlinTypeProjectionPlan): String =
        "_ServerActivationFactory_" + plan.type.qualifiedName
            .replace('.', '_')
            .replace('`', '_')

    private fun authoringWrapperObject(plan: KotlinTypeProjectionPlan): TypeSpec {
        val projectedType = projectionClassNameForQualifiedName(plan.type.qualifiedName)
        return TypeSpec.objectBuilder(authoringWrapperClassName(plan))
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("projectedTypeName", String::class)
                    .initializer("%S", plan.type.qualifiedName)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("metadataTypeName", String::class)
                    .initializer("%S", "ABI.${plan.type.qualifiedName}")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("defaultInterfaceName", String::class.asClassName().copy(nullable = true))
                    .initializer("%L", nullableStringCode(plan.defaultInterfaceName))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("wrap")
                    .addParameter("instance", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .returns(projectedType)
                    .addCode("return %T.Metadata.wrap(instance)\n", projectedType)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("fromAbi")
                    .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
                    .returns(projectedType.copy(nullable = true))
                    .addCode(
                        CodeBlock.of(
                            """
                            if (%T.isNull(pointer)) return null
                            return wrap(%T(%T.toRawComPtr(pointer)))
                            """.trimIndent() + "\n",
                            PLATFORM_ABI_CLASS_NAME,
                            IINSPECTABLE_REFERENCE_CLASS_NAME,
                            PLATFORM_ABI_CLASS_NAME,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    private fun authoringWrapperLookupFunction(entries: List<KotlinTypeProjectionPlan>): FunSpec {
        val code = CodeBlock.builder()
        code.add("return when (runtimeClassName) {\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            code.add("%S -> %T.fromAbi(pointer)\n", plan.type.qualifiedName, ClassName(SUPPORT_PACKAGE, authoringWrapperClassName(plan)))
        }
        code.add("else -> null\n")
        code.unindent()
        code.add("}\n")
        return FunSpec.builder("fromAbi")
            .addParameter("runtimeClassName", String::class)
            .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
            .returns(ANY.copy(nullable = true))
            .addCode(code.build())
            .build()
    }

    private fun authoringAbiClassObject(plan: KotlinTypeProjectionPlan): TypeSpec {
        val projectedType = projectionClassNameForQualifiedName(plan.type.qualifiedName)
        val wrapperType = ClassName(SUPPORT_PACKAGE, authoringWrapperClassName(plan))
        val defaultInterfaceCode = plan.defaultInterfaceName
            ?.substringBefore('<')
            ?.let { CodeBlock.of("%T.Metadata.IID", projectionClassNameForQualifiedName(it)) }
            ?: CodeBlock.of("%T.IInspectable", IID_CLASS_NAME)
        return TypeSpec.objectBuilder(authoringAbiClassName(plan))
            .addModifiers(KModifier.INTERNAL)
            .addProperty(
                PropertySpec.builder("projectedTypeName", String::class)
                    .initializer("%S", plan.type.qualifiedName)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("abiTypeName", String::class)
                    .initializer("%S", "ABI.${plan.type.qualifiedName}")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("defaultInterfaceId", GUID_CLASS_NAME)
                    .initializer("%L", defaultInterfaceCode)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("CreateMarshaler")
                    .addParameter("value", projectedType.copy(nullable = true))
                    .returns(COM_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addCode("return CreateMarshaler2(value, defaultInterfaceId)\n")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("CreateMarshaler2")
                    .addParameter("value", projectedType.copy(nullable = true))
                    .addParameter("interfaceId", GUID_CLASS_NAME)
                    .returns(COM_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addCode("return value?.let { %T.tryUnwrapObject(it) ?: %T.createCCWForObject(it, interfaceId) }\n", COM_WRAPPERS_SUPPORT_CLASS_NAME, COM_WRAPPERS_SUPPORT_CLASS_NAME)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("GetAbi")
                    .addParameter("marshaler", COM_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .returns(RAW_ADDRESS_CLASS_NAME)
                    .addCode("return marshaler?.pointer?.asRawAddress() ?: %T.nullPointer\n", PLATFORM_ABI_CLASS_NAME)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("FromAbi")
                    .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
                    .returns(projectedType.copy(nullable = true))
                    .addCode("return %T.fromAbi(pointer)\n", wrapperType)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("FromManaged")
                    .addParameter("value", projectedType.copy(nullable = true))
                    .returns(RAW_ADDRESS_CLASS_NAME)
                    .addCode(
                        CodeBlock.of(
                            """
                            val marshaler = CreateMarshaler(value) ?: return %T.nullPointer
                            return try {
                                marshaler.getRefPointer().asRawAddress()
                            } finally {
                                marshaler.close()
                            }
                            """.trimIndent() + "\n",
                            PLATFORM_ABI_CLASS_NAME,
                        ),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("CopyAbi")
                    .addParameter("marshaler", COM_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode("%T.writePointer(destination, GetAbi(marshaler))\n", PLATFORM_ABI_CLASS_NAME)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("DisposeMarshaler")
                    .addParameter("marshaler", COM_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addCode("marshaler?.close()\n")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("DisposeAbi")
                    .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
                    .addCode(
                        CodeBlock.of(
                            """
                            if (!%T.isNull(pointer)) {
                                %T(%T.toRawComPtr(pointer)).close()
                            }
                            """.trimIndent() + "\n",
                            PLATFORM_ABI_CLASS_NAME,
                            IUNKNOWN_REFERENCE_CLASS_NAME,
                            PLATFORM_ABI_CLASS_NAME,
                        ),
                    )
                    .build(),
            )
            .addFunction(authoringAbiArrayUnsupportedFunction("CreateMarshalerArray", projectedType))
            .addFunction(authoringAbiArrayUnsupportedFunction("GetAbiArray", projectedType))
            .addFunction(authoringAbiArrayUnsupportedFunction("FromAbiArray", projectedType))
            .addFunction(authoringAbiArrayUnsupportedFunction("CopyAbiArray", projectedType))
            .addFunction(authoringAbiArrayUnsupportedFunction("FromManagedArray", projectedType))
            .addFunction(authoringAbiArrayUnsupportedFunction("DisposeMarshalerArray", projectedType))
            .addFunction(authoringAbiArrayUnsupportedFunction("DisposeAbiArray", projectedType))
            .build()
    }

    private fun authoringAbiArrayUnsupportedFunction(name: String, projectedType: ClassName): FunSpec {
        val builder = FunSpec.builder(name)
        when (name) {
            "CreateMarshalerArray", "FromManagedArray" -> {
                builder.addParameter("values", Array::class.asClassName().parameterizedBy(projectedType.copy(nullable = true)).copy(nullable = true))
                builder.returns(ANY.copy(nullable = true))
            }
            "GetAbiArray", "DisposeMarshalerArray" -> {
                builder.addParameter("marshaler", ANY.copy(nullable = true))
                if (name == "GetAbiArray") {
                    builder.returns(RAW_ADDRESS_CLASS_NAME)
                }
            }
            "FromAbiArray", "DisposeAbiArray" -> {
                builder.addParameter("length", Int::class)
                builder.addParameter("data", RAW_ADDRESS_CLASS_NAME)
                if (name == "FromAbiArray") {
                    builder.returns(List::class.asClassName().parameterizedBy(projectedType.copy(nullable = true)).copy(nullable = true))
                }
            }
            "CopyAbiArray" -> {
                builder.addParameter("marshaler", ANY.copy(nullable = true))
                builder.addParameter("destination", RAW_ADDRESS_CLASS_NAME)
            }
        }
        val returnStatement = when (name) {
            "GetAbiArray" -> "return %T.nullPointer\n"
            "FromAbiArray" -> "return null\n"
            "CreateMarshalerArray", "FromManagedArray" -> "return null\n"
            else -> ""
        }
        return builder
            .addCode(
                CodeBlock.builder()
                    .add("unsupportedAuthoringAbiArrayOperation(%S, %S)\n", projectedType.canonicalName, name)
                    .apply {
                        if (returnStatement == "return %T.nullPointer\n") {
                            add(returnStatement, PLATFORM_ABI_CLASS_NAME)
                        } else {
                            add(returnStatement)
                        }
                    }
                    .build(),
            )
            .build()
    }

    private fun authoringAbiClassFromAbiLookupFunction(entries: List<KotlinTypeProjectionPlan>): FunSpec {
        val code = CodeBlock.builder()
        code.add("return when (runtimeClassName) {\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            code.add("%S -> %T.FromAbi(pointer)\n", plan.type.qualifiedName, ClassName(SUPPORT_PACKAGE, authoringAbiClassName(plan)))
        }
        code.add("else -> null\n")
        code.unindent()
        code.add("}\n")
        return FunSpec.builder("FromAbi")
            .addParameter("runtimeClassName", String::class)
            .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
            .returns(ANY.copy(nullable = true))
            .addCode(code.build())
            .build()
    }

    private fun authoringWrapperClassName(plan: KotlinTypeProjectionPlan): String =
        "_AuthoringWrapper_" + plan.type.qualifiedName
            .replace('.', '_')
            .replace('`', '_')

    private fun authoringAbiClassName(plan: KotlinTypeProjectionPlan): String =
        "_AuthoringAbiClass_" + plan.type.qualifiedName
            .replace('.', '_')
            .replace('`', '_')

    private fun renderAuthoringCcwFactories(
        inventory: WinRtMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        val entries = plans
            .filter { it.type.kind == WinRtTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec("WinRTAuthoringCcwFactories")
        val plansByQualifiedName = plans.associateBy { it.type.qualifiedName }
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            fileBuilder.addFunction(authoringCcwQueryInterfaceFunction(plan))
            fileBuilder.addFunction(authoringCcwDefinitionFunction(plan, plansByQualifiedName))
        }
        fileBuilder.addType(
            TypeSpec.objectBuilder("WinRTAuthoringCcwFactories")
                .addModifiers(KModifier.INTERNAL)
                .addFunction(authoringCcwFactoryRegisterFunction(entries))
                .build(),
        )
        return supportFile("WinRTAuthoringCcwFactories.kt", fileBuilder.build())
    }

    private fun authoringCcwDefinitionFunction(
        plan: KotlinTypeProjectionPlan,
        plansByQualifiedName: Map<String, KotlinTypeProjectionPlan>,
    ): FunSpec {
        val projectedType = projectionClassNameForQualifiedName(plan.type.qualifiedName)
        val functionName = authoringCcwDefinitionFunctionName(plan)
        val defaultInterface = plan.defaultInterfaceName
        return FunSpec.builder(functionName)
            .addModifiers(KModifier.PRIVATE)
            .addParameter("value", projectedType)
            .returns(WINRT_CCW_DEFINITION_CLASS_NAME)
            .addCode(authoringCcwDefinitionCode(plan, defaultInterface, plansByQualifiedName))
            .build()
    }

    private fun authoringCcwDefinitionCode(
        plan: KotlinTypeProjectionPlan,
        defaultInterface: String?,
        plansByQualifiedName: Map<String, KotlinTypeProjectionPlan>,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("return %T(\n", WINRT_CCW_DEFINITION_CLASS_NAME)
        code.indent()
        code.add("interfaceDefinitions = listOf(\n")
        code.indent()
        plan.type.implementedInterfaces
            .map { it.interfaceName.substringBefore('<') }
            .distinct()
            .sorted()
            .forEach { interfaceName ->
                val interfacePlan = plansByQualifiedName[interfaceName]
                code.add("%T(\n", WINRT_INSPECTABLE_INTERFACE_DEFINITION_CLASS_NAME)
                code.indent()
                code.add("interfaceId = %T.Metadata.IID,\n", projectionClassNameForQualifiedName(interfaceName))
                code.add("methods = %L,\n", authoringCcwInterfaceMethodsCode(interfacePlan))
                code.unindent()
                code.add("),\n")
            }
        code.unindent()
        code.add("),\n")
        code.add(
            "defaultInterfaceId = %L,\n",
            defaultInterface
                ?.substringBefore('<')
                ?.let { CodeBlock.of("%T.Metadata.IID", projectionClassNameForQualifiedName(it)) }
                ?: CodeBlock.of("%T.IInspectable", IID_CLASS_NAME),
        )
        code.add("runtimeClassName = %S,\n", plan.type.qualifiedName)
        code.add(
            "queryInterfaceFallback = { obj, requestedInterfaceId -> %L(obj as %T, requestedInterfaceId) },\n",
            authoringCcwQueryInterfaceFunctionName(plan),
            projectionClassNameForQualifiedName(plan.type.qualifiedName),
        )
        code.unindent()
        code.add(")\n")
        return code.build()
    }

    private fun authoringCcwQueryInterfaceFunction(plan: KotlinTypeProjectionPlan): FunSpec {
        val projectedType = projectionClassNameForQualifiedName(plan.type.qualifiedName)
        return FunSpec.builder(authoringCcwQueryInterfaceFunctionName(plan))
            .addModifiers(KModifier.PRIVATE)
            .addParameter("value", projectedType)
            .addParameter("requestedInterfaceId", GUID_CLASS_NAME)
            .returns(RAW_ADDRESS_CLASS_NAME.copy(nullable = true))
            .addCode(
                CodeBlock.of(
                    """
                    if (requestedInterfaceId == %T.IUnknown ||
                        requestedInterfaceId == %T.IInspectable ||
                        requestedInterfaceId == %T.IWeakReferenceSource) {
                        return null
                    }
                    val winRtObject = value as? %T ?: return null
                    return winRtObject.nativeObject
                        .tryQueryInterface(requestedInterfaceId)
                        ?.use { queried -> %T.fromRawComPtr(queried.getRefPointer()) }
                    """.trimIndent() + "\n",
                    IID_CLASS_NAME,
                    IID_CLASS_NAME,
                    IID_CLASS_NAME,
                    IWINRT_OBJECT_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                ),
            )
            .build()
    }

    private fun authoringCcwInterfaceMethodsCode(interfacePlan: KotlinTypeProjectionPlan?): CodeBlock {
        if (interfacePlan == null || interfacePlan.instanceMemberBindings.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }
        val slotOrder = interfacePlan.abiSlotBindings.associate { it.constantName to it.slot }
        val methods = interfacePlan.instanceMemberBindings.sortedWith(
            compareBy<KotlinProjectionInstanceMemberBinding> { slotOrder[it.slotConstantName] ?: Int.MAX_VALUE }
                .thenBy { it.bindingName },
        )
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        methods.forEach { binding ->
            code.add("%T(\n", WINRT_INSPECTABLE_METHOD_DEFINITION_CLASS_NAME)
            code.indent()
            code.add("signature = %L,\n", authoringCcwMethodSignatureCode(binding))
            code.add("handler = { rawArgs ->\n")
            code.indent()
            code.add("%L", authoringCcwMethodHandlerCode(interfacePlan.type, binding))
            code.unindent()
            code.add("},\n")
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringCcwMethodSignatureCode(binding: KotlinProjectionInstanceMemberBinding): CodeBlock {
        val explicitKinds = binding.parameterBindings.map { authoringCcwAbiValueKindCode(it.typeBinding) } +
            if (binding.returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
                emptyList()
            } else {
                listOf(CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME))
            }
        return if (explicitKinds.isEmpty()) {
            CodeBlock.of("%T()", COM_METHOD_SIGNATURE_CLASS_NAME)
        } else {
            CodeBlock.builder()
                .add("%T.of(", COM_METHOD_SIGNATURE_CLASS_NAME)
                .apply {
                    explicitKinds.forEachIndexed { index, kind ->
                        if (index > 0) {
                            add(", ")
                        }
                        add("%L", kind)
                    }
                }
                .add(")")
                .build()
        }
    }

    private fun authoringCcwAbiValueKindCode(binding: KotlinProjectionAbiTypeBinding): CodeBlock = when (binding.kind) {
        KotlinProjectionAbiValueKind.Boolean,
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.Int8", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16,
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.Int16", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.Int32", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.Int64", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.Float", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.Double", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum -> when (binding.enumUnderlyingType) {
            WinRtIntegralType.Int8,
            WinRtIntegralType.UInt8 -> CodeBlock.of("%T.Int8", COM_ABI_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.Int16,
            WinRtIntegralType.UInt16 -> CodeBlock.of("%T.Int16", COM_ABI_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.Int64,
            WinRtIntegralType.UInt64 -> CodeBlock.of("%T.Int64", COM_ABI_VALUE_KIND_CLASS_NAME)
            WinRtIntegralType.Int32,
            WinRtIntegralType.UInt32,
            null -> CodeBlock.of("%T.Int32", COM_ABI_VALUE_KIND_CLASS_NAME)
        }
        KotlinProjectionAbiValueKind.Struct ->
            if (binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken") {
                CodeBlock.of("%T.Int64", COM_ABI_VALUE_KIND_CLASS_NAME)
            } else {
                CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME)
            }
        else -> CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME)
    }

    private fun authoringCcwMethodHandlerCode(
        interfaceType: WinRtTypeDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        val event = interfaceType.events.firstOrNull { event ->
            binding.bindingName == "${event.name.uppercase()}_ADD_SLOT" ||
                binding.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
        }
        return if (event != null) {
            authoringCcwEventHandlerCode(event, binding)
        } else if (authoringCcwBindingIsSupported(binding)) {
            authoringCcwOrdinaryMemberHandlerCode(interfaceType, binding)
        } else {
            authoringCcwUnsupportedMemberHandlerCode(binding)
        }
    }

    private fun authoringCcwEventHandlerCode(
        event: WinRtEventDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("try {\n")
        code.indent()
        if (binding.bindingName.endsWith("_ADD_SLOT")) {
            val handlerType = binding.parameterBindings.singleOrNull()?.typeBinding?.resolvedTypeName
            val handlerClass = handlerType?.let(::projectionClassNameForQualifiedName)
            if (handlerClass == null) {
                code.add("%T.E_INVALIDARG.value\n", KNOWN_HRESULTS_CLASS_NAME)
            } else {
                code.add(
                    "val handler = %T.Metadata.fromAbi(rawArgs[0] as %T)\n",
                    handlerClass,
                    RAW_ADDRESS_CLASS_NAME,
                )
                code.add("if (handler == null) {\n")
                code.indent()
                code.add("%T.E_POINTER.value\n", KNOWN_HRESULTS_CLASS_NAME)
                code.unindent()
                code.add("} else {\n")
                code.indent()
                code.add("val token = value.add%L(handler)\n", event.name)
                code.add("%T.Metadata.copyTo(token, rawArgs[1] as %T)\n", EVENT_REGISTRATION_TOKEN_CLASS_NAME, RAW_ADDRESS_CLASS_NAME)
                code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
                code.unindent()
                code.add("}\n")
            }
        } else {
            code.add("value.remove%L(%T(rawArgs[0] as Long))\n", event.name, EVENT_REGISTRATION_TOKEN_CLASS_NAME)
            code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
        }
        code.unindent()
        code.add("} catch (error: %T) {\n", Throwable::class.asClassName())
        code.indent()
        code.add("%T.setErrorInfo(error)\n", EXCEPTION_HELPERS_CLASS_NAME)
        code.add("%T.hResultFromException(error).value\n", EXCEPTION_HELPERS_CLASS_NAME)
        code.unindent()
        code.add("}\n")
        return code.build()
    }

    private fun authoringCcwOrdinaryMemberHandlerCode(
        interfaceType: WinRtTypeDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("try {\n")
        code.indent()
        binding.parameterBindings.forEachIndexed { index, parameter ->
            code.add(
                "val %L = %L\n",
                parameter.name,
                authoringCcwDecodeArgumentCode(parameter.typeBinding, index),
            )
        }
        val invocation = authoringCcwInvocationCode(interfaceType, binding)
        if (binding.returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
            code.add("%L\n", invocation)
            code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
        } else {
            code.add("val __result = %L\n", invocation)
            code.add("%L\n", authoringCcwWriteReturnCode(binding.returnBinding, "rawArgs[${binding.parameterBindings.size}] as RawAddress", "__result"))
            code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
        }
        code.unindent()
        code.add("} catch (error: %T) {\n", Throwable::class.asClassName())
        code.indent()
        code.add("%T.setErrorInfo(error)\n", EXCEPTION_HELPERS_CLASS_NAME)
        code.add("%T.hResultFromException(error).value\n", EXCEPTION_HELPERS_CLASS_NAME)
        code.unindent()
        code.add("}\n")
        return code.build()
    }

    private fun authoringCcwInvocationCode(
        interfaceType: WinRtTypeDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        interfaceType.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .firstOrNull { method -> binding.bindingName == method.abiSlotConstantName(interfaceType.methods) }
            ?.let { method ->
                return CodeBlock.builder()
                    .add("value.%L(", method.projectedMethodName())
                    .apply {
                        binding.parameterBindings.forEachIndexed { index, parameter ->
                            if (index > 0) {
                                add(", ")
                            }
                            add("%L", parameter.name)
                        }
                    }
                    .add(")")
                    .build()
            }
        interfaceType.properties.firstOrNull { property ->
            binding.bindingName == "${property.name.uppercase()}_GETTER_SLOT" ||
                binding.bindingName == "${property.name.uppercase()}_SETTER_SLOT"
        }?.let { property ->
            val propertyName = property.name.replaceFirstChar(Char::lowercase)
            return if (binding.bindingName.endsWith("_GETTER_SLOT")) {
                CodeBlock.of("value.%L", propertyName)
            } else {
                CodeBlock.of("value.%L = %L", propertyName, binding.parameterBindings.single().name)
            }
        }
        return CodeBlock.of("error(%S)", "No authored member body for ${interfaceType.qualifiedName}.${binding.bindingName}")
    }

    private fun authoringCcwBindingIsSupported(binding: KotlinProjectionInstanceMemberBinding): Boolean =
        binding.parameterBindings.all { authoringCcwAbiBindingIsSupported(it.typeBinding) } &&
            authoringCcwAbiBindingIsSupported(binding.returnBinding)

    private fun authoringCcwAbiBindingIsSupported(binding: KotlinProjectionAbiTypeBinding): Boolean = when (binding.kind) {
        KotlinProjectionAbiValueKind.Unit,
        KotlinProjectionAbiValueKind.String,
        KotlinProjectionAbiValueKind.Boolean,
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8,
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16,
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32,
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64,
        KotlinProjectionAbiValueKind.Float,
        KotlinProjectionAbiValueKind.Double,
        KotlinProjectionAbiValueKind.Char16,
        KotlinProjectionAbiValueKind.GuidValue,
        KotlinProjectionAbiValueKind.Enum,
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> true
        KotlinProjectionAbiValueKind.Struct -> binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken"
        else -> false
    }

    private fun authoringCcwDecodeArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock = when (binding.kind) {
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.fromAbi(rawArgs[%L] as %T)", NATIVE_STRING_MARSHALER_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("(rawArgs[%L] as Byte).toInt() != 0", index)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("rawArgs[%L] as Byte", index)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("(rawArgs[%L] as Byte).toUByte()", index)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("rawArgs[%L] as Short", index)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("(rawArgs[%L] as Short).toUShort()", index)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("rawArgs[%L] as Int", index)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("(rawArgs[%L] as Int).toUInt()", index)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("rawArgs[%L] as Long", index)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("(rawArgs[%L] as Long).toULong()", index)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("rawArgs[%L] as Float", index)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("rawArgs[%L] as Double", index)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("(rawArgs[%L] as Short).toInt().toChar()", index)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.readGuid(rawArgs[%L] as %T)", PLATFORM_ABI_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        KotlinProjectionAbiValueKind.Enum -> CodeBlock.of(
            "%T.Metadata.fromAbi(%L)",
            typeRenderer.resolveTypeName(binding.resolvedTypeName),
            authoringCcwDecodeEnumRawCode(binding, index),
        )
        KotlinProjectionAbiValueKind.Struct -> CodeBlock.of("%T(rawArgs[%L] as Long)", EVENT_REGISTRATION_TOKEN_CLASS_NAME, index)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> CodeBlock.of(
            "%T.Metadata.wrap(%T(rawArgs[%L] as %T).inspectable())",
            typeRenderer.resolveTypeName(binding.resolvedTypeName),
            IUNKNOWN_REFERENCE_CLASS_NAME,
            index,
            RAW_ADDRESS_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("%T(rawArgs[%L] as %T)", IUNKNOWN_REFERENCE_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of("%T(rawArgs[%L] as %T).inspectable()", IUNKNOWN_REFERENCE_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        else -> CodeBlock.of("error(%S)", "Unsupported authored ABI argument ${binding.describeAbiKind()}")
    }

    private fun authoringCcwDecodeEnumRawCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock = when (binding.enumUnderlyingType) {
        WinRtIntegralType.Int8 -> CodeBlock.of("rawArgs[%L] as Byte", index)
        WinRtIntegralType.UInt8 -> CodeBlock.of("(rawArgs[%L] as Byte).toUByte()", index)
        WinRtIntegralType.Int16 -> CodeBlock.of("rawArgs[%L] as Short", index)
        WinRtIntegralType.UInt16 -> CodeBlock.of("(rawArgs[%L] as Short).toUShort()", index)
        WinRtIntegralType.Int64 -> CodeBlock.of("rawArgs[%L] as Long", index)
        WinRtIntegralType.UInt64 -> CodeBlock.of("(rawArgs[%L] as Long).toULong()", index)
        WinRtIntegralType.Int32,
        WinRtIntegralType.UInt32,
        null -> CodeBlock.of("rawArgs[%L] as Int", index)
    }

    private fun authoringCcwWriteReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock = when (binding.kind) {
        KotlinProjectionAbiValueKind.String -> CodeBlock.of("%T.writePointer(%L, %T.create(%L).handle)", PLATFORM_ABI_CLASS_NAME, outExpression, HSTRING_CLASS_NAME, valueExpression)
        KotlinProjectionAbiValueKind.Boolean -> CodeBlock.of("%T.writeInt8(%L, if (%L) 1.toByte() else 0.toByte())", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.UInt8 -> CodeBlock.of("%T.writeInt8(%L, %L.toByte())", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.UInt16 -> CodeBlock.of("%T.writeInt16(%L, %L.toShort())", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.UInt32 -> CodeBlock.of("%T.writeInt32(%L, %L.toInt())", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.UInt64 -> CodeBlock.of("%T.writeInt64(%L, %L.toLong())", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Float -> CodeBlock.of("%T.writeFloat(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Double -> CodeBlock.of("%T.writeDouble(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Char16 -> CodeBlock.of("%T.writeInt16(%L, %L.code.toShort())", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.GuidValue -> CodeBlock.of("%T.writeGuid(%L, %L)", PLATFORM_ABI_CLASS_NAME, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Enum -> CodeBlock.of(
            "%L",
            authoringCcwWriteReturnCode(
                KotlinProjectionAbiTypeBinding(
                    kind = when (binding.enumUnderlyingType) {
                        WinRtIntegralType.Int8 -> KotlinProjectionAbiValueKind.Int8
                        WinRtIntegralType.UInt8 -> KotlinProjectionAbiValueKind.UInt8
                        WinRtIntegralType.Int16 -> KotlinProjectionAbiValueKind.Int16
                        WinRtIntegralType.UInt16 -> KotlinProjectionAbiValueKind.UInt16
                        WinRtIntegralType.Int64 -> KotlinProjectionAbiValueKind.Int64
                        WinRtIntegralType.UInt64 -> KotlinProjectionAbiValueKind.UInt64
                        WinRtIntegralType.Int32,
                        WinRtIntegralType.UInt32,
                        null -> KotlinProjectionAbiValueKind.Int32
                    },
                    typeName = binding.typeName,
                ),
                outExpression,
                CodeBlock.of("%T.Metadata.toAbi(%L)", typeRenderer.resolveTypeName(binding.resolvedTypeName), valueExpression).toString(),
            ),
        )
        KotlinProjectionAbiValueKind.Struct -> CodeBlock.of("%T.Metadata.copyTo(%L, %L)", EVENT_REGISTRATION_TOKEN_CLASS_NAME, valueExpression, outExpression)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> authoringCcwWriteObjectReturnCode(binding, outExpression, valueExpression)
        else -> CodeBlock.of("error(%S)", "Unsupported authored ABI return ${binding.describeAbiKind()}")
    }

    private fun authoringCcwWriteObjectReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val interfaceId = binding.interfaceId?.let { CodeBlock.of("%T(%S)", GUID_CLASS_NAME, it.toString()) }
            ?: CodeBlock.of("%T.IInspectable", IID_CLASS_NAME)
        return CodeBlock.of(
            "%T.createCCWForObject(%L, %L).use { __returnReference -> %T.writePointer(%L, %T.fromRawComPtr(__returnReference.getRefPointer())) }",
            COM_WRAPPERS_SUPPORT_CLASS_NAME,
            valueExpression,
            interfaceId,
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            PLATFORM_ABI_CLASS_NAME,
        )
    }

    private fun authoringCcwUnsupportedMemberHandlerCode(binding: KotlinProjectionInstanceMemberBinding): CodeBlock =
        CodeBlock.of(
            "%T.E_NOTIMPL.value\n",
            KNOWN_HRESULTS_CLASS_NAME,
        )

    private fun authoringCcwFactoryRegisterFunction(entries: List<KotlinTypeProjectionPlan>): FunSpec {
        val code = CodeBlock.builder()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            val projectedType = projectionClassNameForQualifiedName(plan.type.qualifiedName)
            code.add(
                "%T.registerCcwFactory(%T::class) { value ->\n",
                COM_WRAPPERS_SUPPORT_CLASS_NAME,
                projectedType,
            )
            code.indent()
            code.add("%L(value as %T)\n", authoringCcwDefinitionFunctionName(plan), projectedType)
            code.unindent()
            code.add("}\n")
        }
        return FunSpec.builder("register")
            .addCode(code.build())
            .build()
    }

    private fun authoringCcwDefinitionFunctionName(plan: KotlinTypeProjectionPlan): String =
        "createCcwDefinitionFor" + plan.type.qualifiedName
            .replace(".", "_")
            .replace("`", "_")

    private fun authoringCcwQueryInterfaceFunctionName(plan: KotlinTypeProjectionPlan): String =
        "queryInterfaceFor" + plan.type.qualifiedName
            .replace(".", "_")
            .replace("`", "_")

    private fun delegateValueKindList(bindings: List<KotlinProjectionAbiTypeBinding>): String =
        bindings.joinToString(prefix = "listOf(", postfix = ")") { delegateValueKindName(it) }

    private fun delegateValueKindName(binding: KotlinProjectionAbiTypeBinding): String =
        typeRenderer.delegateInvokeValueKindCode(binding).toString()

    private fun defaultValueExpression(binding: KotlinProjectionAbiTypeBinding): String =
        when (binding.kind) {
            KotlinProjectionAbiValueKind.Boolean -> "false"
            KotlinProjectionAbiValueKind.Int8 -> "0.toByte()"
            KotlinProjectionAbiValueKind.UInt8 -> "0.toUByte()"
            KotlinProjectionAbiValueKind.Int16 -> "0.toShort()"
            KotlinProjectionAbiValueKind.UInt16 -> "0.toUShort()"
            KotlinProjectionAbiValueKind.Char16 -> "'\\u0000'"
            KotlinProjectionAbiValueKind.Int32,
            KotlinProjectionAbiValueKind.Enum -> "0"
            KotlinProjectionAbiValueKind.UInt32 -> "0.toUInt()"
            KotlinProjectionAbiValueKind.Int64 -> "0L"
            KotlinProjectionAbiValueKind.UInt64 -> "0.toULong()"
            KotlinProjectionAbiValueKind.Float -> "0.0f"
            KotlinProjectionAbiValueKind.Double -> "0.0"
            KotlinProjectionAbiValueKind.String -> "\"\""
            else -> "null"
        }

    private fun supportFile(fileName: String, contents: String): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = "io/github/kitectlab/winrt/projections/support/$fileName",
            packageName = SUPPORT_PACKAGE,
            contents = contents,
        )

    private fun supportFile(fileName: String, fileSpec: FileSpec): KotlinProjectionFile =
        supportFile(fileName, fileSpec.toString())

    private fun supportFileSpec(fileName: String): FileSpec.Builder =
        FileSpec.builder(SUPPORT_PACKAGE, fileName)
            .addFileComment("Deterministic generator handoff for .cswinrt %L writer parity.", fileName)

    private fun dataClass(
        className: String,
        fields: List<Pair<String, TypeName>>,
    ): TypeSpec {
        val constructor = FunSpec.constructorBuilder()
        val type = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.INTERNAL, KModifier.DATA)
        fields.forEach { (name, typeName) ->
            constructor.addParameter(name, typeName)
            type.addProperty(
                PropertySpec.builder(name, typeName)
                    .initializer(name)
                    .build(),
            )
        }
        return type.primaryConstructor(constructor.build()).build()
    }

    private fun stringListProperty(name: String, values: List<String>): PropertySpec =
        PropertySpec.builder(name, stringListTypeName())
            .initializer(stringListCode(values))
            .build()

    private fun genericAbiDelegateEntriesCode(
        entries: List<WinRtGenericAbiDelegateDescriptor>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.forEach { delegate ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("name = %S,\n", delegate.abiDelegateName)
            code.add("sourceGenericType = %S,\n", delegate.sourceGenericType.typeName)
            code.add("operation = %S,\n", delegate.operationName)
            code.add("declaration = %S,\n", delegate.declaration)
            code.add("abiParameterTypes = %L,\n", stringListCode(delegate.abiParameterTypeNames))
            code.add("typeArrayShape = %L,\n", stringListCode(delegate.typeArrayShape))
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun genericTypeInstantiationRuntimeBindingType(entryClass: ClassName): TypeSpec {
        val entryStringListFunction = Function2::class.asClassName().parameterizedBy(entryClass, stringListTypeName(), UNIT)
        val entryFunction = Function1::class.asClassName().parameterizedBy(entryClass, UNIT)
        val constructor = FunSpec.constructorBuilder()
            .addParameter(
                ParameterSpec.builder("initRcwHelpers", entryStringListFunction)
                    .defaultValue("{ _, _ -> }")
                    .build(),
            )
            .addParameter(
                ParameterSpec.builder("initVtableFunctions", entryStringListFunction)
                    .defaultValue("{ _, _ -> }")
                    .build(),
            )
            .addParameter(
                ParameterSpec.builder("initPropertyAccessors", entryStringListFunction)
                    .defaultValue("{ _, _ -> }")
                    .build(),
            )
            .addParameter(
                ParameterSpec.builder("initDelegateCcwInvoke", entryFunction)
                    .defaultValue("{}")
                    .build(),
            )
            .addParameter(
                ParameterSpec.builder("initGenericReturnOnlyRcwHelpers", entryStringListFunction)
                    .defaultValue("{ _, _ -> }")
                    .build(),
            )
            .addParameter(
                ParameterSpec.builder("initProjectedGenericFallbacks", entryStringListFunction)
                    .defaultValue("{ _, _ -> }")
                    .build(),
            )
        return TypeSpec.classBuilder("GenericTypeInstantiationRuntimeBinding")
            .addModifiers(KModifier.INTERNAL, KModifier.DATA)
            .primaryConstructor(constructor.build())
            .addProperty(PropertySpec.builder("initRcwHelpers", entryStringListFunction).initializer("initRcwHelpers").build())
            .addProperty(PropertySpec.builder("initVtableFunctions", entryStringListFunction).initializer("initVtableFunctions").build())
            .addProperty(PropertySpec.builder("initPropertyAccessors", entryStringListFunction).initializer("initPropertyAccessors").build())
            .addProperty(PropertySpec.builder("initDelegateCcwInvoke", entryFunction).initializer("initDelegateCcwInvoke").build())
            .addProperty(PropertySpec.builder("initGenericReturnOnlyRcwHelpers", entryStringListFunction).initializer("initGenericReturnOnlyRcwHelpers").build())
            .addProperty(PropertySpec.builder("initProjectedGenericFallbacks", entryStringListFunction).initializer("initProjectedGenericFallbacks").build())
            .build()
    }

    private fun genericTypeInstantiationEntriesCode(
        descriptors: List<WinRtGenericInstantiationWriterDescriptor>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        descriptors.forEach { descriptor ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("className = %S,\n", descriptor.instantiationClassName)
            code.add("sourceType = %S,\n", descriptor.sourceTypeName)
            code.add("isDelegate = %L,\n", descriptor.isDelegateInstantiation)
            code.add("rcwFunctions = %L,\n", stringListCode(descriptor.rcwFunctionNames))
            code.add("vtableFunctions = %L,\n", stringListCode(descriptor.vtableFunctionNames))
            code.add("propertyAccessors = %L,\n", stringListCode(descriptor.propertyAccessorFunctionNames))
            code.add("genericReturnOnlyRcwFunctions = %L,\n", stringListCode(descriptor.genericReturnOnlyRcwFunctionNames))
            code.add("projectedGenericFallbacks = %L,\n", stringListCode(descriptor.projectedGenericFallbackFunctionNames))
            code.add("dependencies = %L,\n", stringListCode(descriptor.initializationDependencies))
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun defaultGenericTypeRuntimeBindingCode(bindingClass: ClassName): CodeBlock =
        CodeBlock.of(
            """
            %T(
                initRcwHelpers = { entry, functions ->
                    io.github.kitectlab.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindRcwHelpers(
                        className = entry.className,
                        sourceType = entry.sourceType,
                        isDelegate = entry.isDelegate,
                        functions = functions,
                    )
                },
                initVtableFunctions = { entry, functions ->
                    io.github.kitectlab.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindVtableFunctions(
                        className = entry.className,
                        sourceType = entry.sourceType,
                        isDelegate = entry.isDelegate,
                        functions = functions,
                    )
                },
                initPropertyAccessors = { entry, accessors ->
                    io.github.kitectlab.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindPropertyAccessors(
                        className = entry.className,
                        sourceType = entry.sourceType,
                        isDelegate = entry.isDelegate,
                        functions = accessors,
                    )
                },
                initDelegateCcwInvoke = { entry ->
                    io.github.kitectlab.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindDelegateCcwInvoke(
                        className = entry.className,
                        sourceType = entry.sourceType,
                        isDelegate = entry.isDelegate,
                    )
                },
                initGenericReturnOnlyRcwHelpers = { entry, functions ->
                    io.github.kitectlab.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindGenericReturnOnlyRcwHelpers(
                        className = entry.className,
                        sourceType = entry.sourceType,
                        isDelegate = entry.isDelegate,
                        functions = functions,
                    )
                },
                initProjectedGenericFallbacks = { entry, functions ->
                    io.github.kitectlab.winrt.runtime.WinRtGenericTypeInstantiationRuntime.bindProjectedGenericFallbacks(
                        className = entry.className,
                        sourceType = entry.sourceType,
                        isDelegate = entry.isDelegate,
                        functions = functions,
                    )
                },
            )
            """.trimIndent(),
            bindingClass,
        )

    private fun genericTypeInstantiationFunctions(
        entryClass: ClassName,
        bindingClass: ClassName,
    ): List<FunSpec> =
        listOf(
            FunSpec.builder("entryForClassName")
                .addParameter("className", String::class)
                .returns(entryClass.copy(nullable = true))
                .addStatement("return ENTRIES_BY_CLASS_NAME[className]")
                .build(),
            FunSpec.builder("entryForSourceType")
                .addParameter("sourceType", String::class)
                .returns(entryClass.copy(nullable = true))
                .addStatement("return ENTRIES_BY_SOURCE_TYPE[sourceType]")
                .build(),
            FunSpec.builder("installRuntimeBinding")
                .addParameter("binding", bindingClass)
                .addStatement("runtimeBinding = binding")
                .build(),
            FunSpec.builder("isInitialized")
                .addParameter("entry", entryClass)
                .returns(Boolean::class)
                .addStatement("return entry.className in INITIALIZED_CLASS_NAMES")
                .build(),
            FunSpec.builder("initializeAll")
                .addCode("val visited = linkedSetOf<String>()\nENTRIES.forEach { initializeWithDependencies(it, visited) }\n")
                .build(),
            FunSpec.builder("initializeBySourceType")
                .addParameter("sourceType", String::class)
                .addStatement("entryForSourceType(sourceType)?.let(::initializeEntry)")
                .build(),
            FunSpec.builder("initializeEntry")
                .addParameter("entry", entryClass)
                .addStatement("initializeWithDependencies(entry, linkedSetOf())")
                .build(),
            FunSpec.builder("initializeDependencies")
                .addParameter("entry", entryClass)
                .addParameter("initialize", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                .addCode(
                    "val visited = linkedSetOf(entry.className)\n" +
                        "entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)\n" +
                        "    .forEach { initializeWithDependencies(it, visited, initialize) }\n",
                )
                .build(),
            FunSpec.builder("initializeDependencies")
                .addParameter("entry", entryClass)
                .addCode(
                    "val visited = linkedSetOf(entry.className)\n" +
                        "entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)\n" +
                        "    .forEach { initializeWithDependencies(it, visited) }\n",
                )
                .build(),
            FunSpec.builder("initializeWithDependencies")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("entry", entryClass)
                .addParameter("visited", MUTABLE_SET_CLASS_NAME.parameterizedBy(stringTypeName()))
                .addParameter("initialize", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                .addCode(
                    "if (!visited.add(entry.className)) return\n" +
                        "entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)\n" +
                        "    .forEach { initializeWithDependencies(it, visited, initialize) }\n" +
                        "initialize(entry)\n",
                )
                .build(),
            FunSpec.builder("initializeWithDependencies")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("entry", entryClass)
                .addParameter("visited", MUTABLE_SET_CLASS_NAME.parameterizedBy(stringTypeName()))
                .addCode(
                    "if (!visited.add(entry.className)) return\n" +
                        "entry.dependencies.mapNotNull(ENTRIES_BY_SOURCE_TYPE::get)\n" +
                        "    .forEach { initializeWithDependencies(it, visited) }\n" +
                        "registerGenericInstantiation(entry)\n",
                )
                .build(),
            FunSpec.builder("registerGenericInstantiation")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("entry", entryClass)
                .addCode(
                    """
                    if (!INITIALIZED_CLASS_NAMES.add(entry.className)) return
                    if (entry.rcwFunctions.isNotEmpty() || !entry.isDelegate) {
                        runtimeBinding.initRcwHelpers(entry, entry.rcwFunctions)
                    }
                    if (entry.vtableFunctions.isNotEmpty()) {
                        runtimeBinding.initVtableFunctions(entry, entry.vtableFunctions)
                    }
                    if (entry.propertyAccessors.isNotEmpty()) {
                        runtimeBinding.initPropertyAccessors(entry, entry.propertyAccessors)
                    }
                    if (entry.genericReturnOnlyRcwFunctions.isNotEmpty()) {
                        runtimeBinding.initGenericReturnOnlyRcwHelpers(entry, entry.genericReturnOnlyRcwFunctions)
                    }
                    if (entry.projectedGenericFallbacks.isNotEmpty()) {
                        runtimeBinding.initProjectedGenericFallbacks(entry, entry.projectedGenericFallbacks)
                    }
                    if (entry.isDelegate) {
                        runtimeBinding.initDelegateCcwInvoke(entry)
                    }
                    """.trimIndent() + "\n",
                )
                .build(),
        )

    private fun eventSourceEntriesCode(
        descriptors: List<WinRtEventHelperSubclassDescriptor>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        descriptors.forEach { descriptor ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("eventType = %S,\n", descriptor.eventTypeName)
            code.add("ownerType = %S,\n", descriptor.ownerTypeName)
            code.add("sourceClass = %S,\n", descriptor.sourceClassName)
            code.add("abiEventType = %S,\n", descriptor.abiEventTypeName)
            code.add("genericArguments = %L,\n", stringListCode(descriptor.genericArgumentTypeNames))
            code.add("usesSharedEventHandlerSource = %L,\n", descriptor.usesSharedEventHandlerSource)
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun eventSourceSubclassType(
        descriptor: WinRtEventHelperSubclassDescriptor,
        delegatePlan: KotlinTypeProjectionPlan,
        invokeShape: KotlinProjectionDelegateInvokeShape,
    ): TypeSpec {
        val delegateType = typeRenderer.resolveTypeName(descriptor.projectedEventTypeName)
        return TypeSpec.classBuilder(descriptor.sourceClassName)
            .addModifiers(KModifier.INTERNAL)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("objectReference", ClassName("io.github.kitectlab.winrt.runtime", "ComObjectReference"))
                    .addParameter("vtableIndexForAddHandler", Int::class)
                    .build(),
            )
            .superclass(ClassName("io.github.kitectlab.winrt.runtime", "EventSource").parameterizedBy(delegateType))
            .addSuperclassConstructorParameter("objectReference")
            .addSuperclassConstructorParameter("vtableIndexForAddHandler")
            .addFunction(
                FunSpec.builder("createMarshaler")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("handler", delegateType)
                    .returns(ClassName("io.github.kitectlab.winrt.runtime", "WinRtDelegateHandle"))
                    .addCode(eventSourceCreateMarshalerCode(invokeShape))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("createEventSourceState")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("io.github.kitectlab.winrt.runtime", "EventSourceState").parameterizedBy(delegateType))
                    .addCode(eventSourceStateCode(delegatePlan, invokeShape, delegateType))
                    .build(),
            )
            .build()
    }

    private fun eventSourceCreateMarshalerCode(invokeShape: KotlinProjectionDelegateInvokeShape): CodeBlock {
        val callbackArguments = invokeShape.parameterBindings.mapIndexed { index, binding ->
            eventSourceCallbackArgumentCode(index, binding.typeBinding).toString()
        }
        return CodeBlock.of(
            """
            if (handler is io.github.kitectlab.winrt.runtime.WinRtProjectedDelegate) {
                return handler.createWinRtDelegateHandle()
            }
            return io.github.kitectlab.winrt.runtime.WinRtDelegateBridge.createDelegate(
                iid = io.github.kitectlab.winrt.runtime.Guid(%S),
                parameterKinds = %L,
                returnKind = %L,
            ) { __args ->
                handler.invoke(%L)
            }
            """.trimIndent() + "\n",
            invokeShape.interfaceId.toString(),
            eventSourceDelegateValueKindList(invokeShape.parameterBindings.map { it.typeBinding }),
            delegateValueKindName(invokeShape.returnBinding),
            callbackArguments.joinToString(", "),
        )
    }

    private fun eventSourceCallbackArgumentCode(
        index: Int,
        binding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock =
        if (mappedTypeByAbiName(binding.typeName) != null) {
            CodeBlock.of("__args[%L] as %T", index, typeRenderer.resolveTypeName(binding.typeName))
        } else {
            typeRenderer.delegateCallbackArgumentCode(index, binding)
        }

    private fun eventSourceDelegateValueKindList(bindings: List<KotlinProjectionAbiTypeBinding>): String =
        bindings.joinToString(prefix = "listOf(", postfix = ")") { eventSourceDelegateValueKindName(it) }

    private fun eventSourceDelegateValueKindName(binding: KotlinProjectionAbiTypeBinding): String =
        when (binding.kind) {
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
            KotlinProjectionAbiValueKind.InspectableReference,
            -> "io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind.IINSPECTABLE"
            else -> delegateValueKindName(binding)
        }

    private fun eventSourceStateCode(
        delegatePlan: KotlinTypeProjectionPlan,
        invokeShape: KotlinProjectionDelegateInvokeShape,
        delegateType: TypeName,
    ): CodeBlock {
        val lambdaParameterNames = invokeShape.parameterBindings.joinToString(", ") { it.name }
        val invokeArguments = lambdaParameterNames
        val lambdaParameterList = CodeBlock.builder().apply {
            invokeShape.parameterBindings.forEachIndexed { index, binding ->
                if (index > 0) add(", ")
                add("%L: %T", binding.name, typeRenderer.resolveTypeName(binding.typeBinding.typeName))
            }
        }.build()
        val lambdaBody = eventSourceStateLambdaBody(invokeShape, invokeArguments)
        val lambdaHeader = if (lambdaParameterNames.isBlank()) "" else "$lambdaParameterNames ->\n"
        val typedLambdaHeader = if (lambdaParameterNames.isBlank()) "" else CodeBlock.of("%L ->\n", lambdaParameterList)
        val lambdaCode = if (delegateType.toString().startsWith("kotlin.Function") || mappedTypeByAbiName(delegatePlan.type.qualifiedName) != null) {
            CodeBlock.of("{ %L%L\n}", lambdaHeader, lambdaBody)
        } else {
            CodeBlock.of("%T({ %L%L\n})", delegateType, typedLambdaHeader, lambdaBody)
        }
        return CodeBlock.of(
            "return object : %T<%T>(%T(nativeObjectReference.pointer.value), eventIndex) {\n" +
                "    override fun createEventInvoke(): %T = %L\n" +
                "}\n",
            ClassName("io.github.kitectlab.winrt.runtime", "EventSourceState"),
            delegateType,
            ClassName("io.github.kitectlab.winrt.runtime", "RawAddress"),
            delegateType,
            lambdaCode,
        )
    }

    private fun eventSourceStateLambdaBody(
        invokeShape: KotlinProjectionDelegateInvokeShape,
        invokeArguments: String,
    ): CodeBlock =
        if (invokeShape.returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
            CodeBlock.of("snapshotHandlers().forEach { handler -> handler.invoke(%L) }", invokeArguments)
        } else {
            CodeBlock.of(
                "var __result = %L\nsnapshotHandlers().forEach { handler -> __result = handler.invoke(%L) }\n__result",
                defaultValueExpression(invokeShape.returnBinding),
                invokeArguments,
            )
        }

    private fun eventProjectionHelperFunctions(
        entryClass: ClassName,
        subclassDescriptors: List<WinRtEventHelperSubclassDescriptor>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): List<FunSpec> =
        listOf(
            FunSpec.builder("sourcesForEventType")
                .addParameter("eventType", String::class)
                .returns(List::class.asClassName().parameterizedBy(entryClass))
                .addStatement("return EVENT_SOURCES_BY_EVENT_TYPE[eventType].orEmpty()")
                .build(),
            FunSpec.builder("sourcesForOwnerType")
                .addParameter("ownerType", String::class)
                .returns(List::class.asClassName().parameterizedBy(entryClass))
                .addStatement("return EVENT_SOURCES_BY_OWNER_TYPE[ownerType].orEmpty()")
                .build(),
            FunSpec.builder("installEventSources")
                .addCode(
                    """
                    EVENT_SOURCES.forEach { entry ->
                        WinRTGenericTypeInstantiations.initializeBySourceType(entry.eventType)
                        io.github.kitectlab.winrt.runtime.WinRtEventSourceRuntime.registerEventSource(
                            io.github.kitectlab.winrt.runtime.WinRtEventSourceDescriptor(
                                eventType = entry.eventType,
                                ownerType = entry.ownerType,
                                sourceClass = entry.sourceClass,
                                abiEventType = entry.abiEventType,
                                genericArguments = entry.genericArguments,
                                usesSharedEventHandlerSource = entry.usesSharedEventHandlerSource,
                                eventSourceFactory = eventSourceFactoryFor(entry),
                            ),
                        )
                    }
                    """.trimIndent() + "\n",
                )
                .build(),
            FunSpec.builder("createEventSource")
                .addParameter("eventType", String::class)
                .addParameter("ownerType", String::class)
                .addParameter("objectReference", ClassName("io.github.kitectlab.winrt.runtime", "ComObjectReference"))
                .addParameter("vtableIndexForAddHandler", Int::class)
                .returns(ClassName("io.github.kitectlab.winrt.runtime", "EventSource").parameterizedBy(STAR).copy(nullable = true))
                .addCode(
                    """
                    installEventSources()
                    return io.github.kitectlab.winrt.runtime.WinRtEventSourceRuntime.createEventSource(
                        eventType = eventType,
                        ownerType = ownerType,
                        objectReference = objectReference,
                        vtableIndexForAddHandler = vtableIndexForAddHandler,
                    )
                    """.trimIndent() + "\n",
                )
                .build(),
            FunSpec.builder("eventSourceFactoryFor")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("entry", entryClass)
                .returns(ClassName("io.github.kitectlab.winrt.runtime", "WinRtEventSourceFactory").copy(nullable = true))
                .addCode(eventSourceFactoryForCode(subclassDescriptors, plansByType, typesByQualifiedName))
                .build(),
            FunSpec.builder("eventHandlerEventSourceFactoryFor")
                .addModifiers(KModifier.PRIVATE)
                .addParameter("entry", entryClass)
                .returns(ClassName("io.github.kitectlab.winrt.runtime", "WinRtEventSourceFactory").copy(nullable = true))
                .addCode(eventHandlerEventSourceFactoryForCode(subclassDescriptors, typesByQualifiedName))
                .build(),
            FunSpec.builder("installEventSources")
                .addParameter("install", Function1::class.asClassName().parameterizedBy(entryClass, UNIT))
                .addStatement("EVENT_SOURCES.forEach(install)")
                .build(),
        )

    private fun eventSourceFactoryForCode(
        subclassDescriptors: List<WinRtEventHelperSubclassDescriptor>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("return when (entry.sourceClass) {\n")
        code.indent()
        if (subclassDescriptors.any { it.usesSharedEventHandlerSource }) {
            code.add("%S -> eventHandlerEventSourceFactoryFor(entry)\n", "EventHandlerEventSource")
        }
        subclassDescriptors.filterNot { it.usesSharedEventHandlerSource }.forEach { descriptor ->
            val rawEventType = descriptor.projectedEventTypeName.substringBefore('<')
            val delegatePlan = plansByType[rawEventType] ?: return@forEach
            val invokeShape = concreteEventInvokeShape(descriptor, typesByQualifiedName) ?: return@forEach
            if (invokeShape.isSupportedProjectedDelegateShape()) {
                code.add("%S -> { obj, index -> %L(obj, index) }\n", descriptor.sourceClassName, descriptor.sourceClassName)
            }
        }
        code.add("else -> null\n")
        code.unindent()
        code.add("}\n")
        return code.build()
    }

    private fun concreteEventInvokeShape(
        descriptor: WinRtEventHelperSubclassDescriptor,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): KotlinProjectionDelegateInvokeShape? {
        val typeBinding = planner.classifyAbiTypeBinding(
            typeName = descriptor.eventTypeName,
            currentNamespace = descriptor.ownerTypeName.substringBeforeLast('.', missingDelimiterValue = ""),
            typesByQualifiedName = typesByQualifiedName,
        )
        return typeRenderer.outboundDelegateInvokeShape(typeBinding)
    }

    private fun eventHandlerEventSourceFactoryForCode(
        subclassDescriptors: List<WinRtEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("return when (entry.eventType) {\n")
        code.indent()
        subclassDescriptors.filter { it.usesSharedEventHandlerSource }.forEach { descriptor ->
            code.add(sharedEventHandlerFactoryCode(descriptor, typesByQualifiedName))
        }
        code.add("else -> null\n")
        code.unindent()
        code.add("}\n")
        return code.build()
    }

    private fun sharedEventHandlerFactoryCode(
        descriptor: WinRtEventHelperSubclassDescriptor,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val typeBinding = planner.classifyAbiTypeBinding(
            typeName = descriptor.eventTypeName,
            currentNamespace = descriptor.ownerTypeName.substringBeforeLast('.', missingDelimiterValue = ""),
            typesByQualifiedName = typesByQualifiedName,
        )
        val invokeShape = typeBinding.delegateInvokeShape ?: return CodeBlock.of("")
        val argumentBinding = typeBinding.typeArguments.singleOrNull() ?: return CodeBlock.of("")
        val interfaceId = typeRenderer.delegateInterfaceIdCode(typeBinding, invokeShape) ?: return CodeBlock.of("")
        val argumentKind = typeRenderer.delegateInvokeValueKindCode(argumentBinding)
        val argumentType = typeRenderer.resolveTypeName(argumentBinding.typeName)
        return CodeBlock.of(
            """
            %S -> { obj, index ->
                io.github.kitectlab.winrt.runtime.EventHandlerEventSource<%T>(
                    objectReference = obj,
                    interfaceId = %L,
                    argsKind = %L,
                    vtableIndexForAddHandler = index,
                )
            }
            """.trimIndent() + "\n",
            descriptor.eventTypeName,
            argumentType,
            interfaceId,
            argumentKind,
        )
    }

    private fun abiEntriesBuildListCode(indices: IntRange): CodeBlock {
        val code = CodeBlock.builder()
        code.add("buildList {\n")
        code.indent()
        indices.forEach { index -> code.add("addAll(entriesChunk%L())\n", index) }
        code.unindent()
        code.add("}")
        return code.build()
    }

    private fun abiImplementationEntriesCode(
        plans: List<KotlinTypeProjectionPlan>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        plans.forEach { plan ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("typeName = %S,\n", plan.type.qualifiedName)
            code.add("writesAbi = %L,\n", plan.typeDeclarationDescriptor.writesAbiDeclaration)
            code.add("writesImplementationClass = %L,\n", plan.typeDeclarationDescriptor.writesImplementationClass)
            code.add("vtableSlots = %L,\n", stringListCode(plan.abiSlotBindings.map { it.constantName }))
            code.add("genericInvokeSlots = %L,\n", stringListCode(plan.genericAbiClassInitializationDescriptor?.invokeSlotNames.orEmpty()))
            code.add("requiredInterfaces = %L,\n", stringListCode(plan.requiredInterfaceAugmentationDescriptor?.requiredInterfaceNames.orEmpty()))
            code.add("explicitForwards = %L,\n", stringListCode(plan.requiredInterfaceAugmentationDescriptor?.explicitForwardMemberNames.orEmpty()))
            code.add("requiredMappedHelpers = %L,\n", stringListCode(plan.requiredInterfaceAugmentationDescriptor?.mappedHelperPlans.orEmpty().map { it.toSupportPlanString() }))
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun typeShapeEntriesCode(
        plans: List<KotlinTypeProjectionPlan>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        plans.forEach { plan ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("typeName = %S,\n", plan.type.qualifiedName)
            code.add("kind = %S,\n", plan.type.kind.name)
            code.add("mappedMembers = %L,\n", stringListCode(plan.customMappedMemberOutputDescriptor?.memberPlans.orEmpty()))
            code.add("mappedCallMode = %S,\n", plan.customMappedMemberOutputDescriptor?.callMode ?: "")
            code.add("mappedExplicit = %L,\n", plan.customMappedMemberOutputDescriptor?.emitsExplicitMembers ?: false)
            code.add("mappedPrivate = %L,\n", plan.customMappedMemberOutputDescriptor?.emitsPrivateMembers ?: false)
            code.add("deferredAuthoringFactoryMembers = %L,\n", stringListCode(plan.moduleActivationAndAuthoringDescriptor?.factoryMemberNames.orEmpty()))
            code.add("deferredModuleActivationEntries = %L,\n", stringListCode(plan.moduleActivationAndAuthoringDescriptor?.moduleActivationFactoryEntries.orEmpty()))
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun typeShapePlanFunctions(entryClass: ClassName): List<FunSpec> =
        listOf(
            FunSpec.builder("typeShape")
                .addParameter("typeName", String::class)
                .returns(entryClass.copy(nullable = true))
                .addStatement("return TYPES_BY_NAME[typeName]")
                .build(),
            FunSpec.builder("registerBaseTypeMappings")
                .addParameter(
                    "register",
                    Function1::class.asClassName().parameterizedBy(Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()), UNIT),
                )
                .addCode("if (BASE_TYPE_MAPPING_TABLE.isNotEmpty()) {\n    register(BASE_TYPE_MAPPING_TABLE)\n}\n")
                .build(),
            FunSpec.builder("registerAuthoringMetadataMappings")
                .addParameter(
                    "register",
                    Function1::class.asClassName().parameterizedBy(Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()), UNIT),
                )
                .addCode("if (AUTHORING_METADATA_MAPPING_TABLE.isNotEmpty()) {\n    register(AUTHORING_METADATA_MAPPING_TABLE)\n}\n")
                .build(),
            FunSpec.builder("deferredAuthoringFactoryEntries")
                .returns(List::class.asClassName().parameterizedBy(Pair::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName())))
                .addCode(
                    "return TYPES.flatMap { type ->\n" +
                        "    type.deferredModuleActivationEntries.map { factoryMember -> type.typeName to factoryMember }\n" +
                        "}\n",
                )
                .build(),
            toArrowMapFunction(),
        )

    private fun toArrowMapFunction(): FunSpec =
        FunSpec.builder("toArrowMap")
            .addModifiers(KModifier.PRIVATE)
            .receiver(stringListTypeName())
            .returns(Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()))
            .addCode(
                "return mapNotNull { entry ->\n" +
                    "    val separator = entry.indexOf(\"->\")\n" +
                    "    if (separator < 0) null else entry.substring(0, separator) to entry.substring(separator + 2)\n" +
                    "}.toMap()\n",
            )
            .build()

    private fun namespaceAdditionEntriesCode(
        inventory: WinRtMetadataProjectionInventory,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        inventory.namespaceAdditions.forEach { addition ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("namespace = %S,\n", addition.namespace)
            code.add("kind = %S,\n", addition.kind.name)
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringWrapperEntriesCode(
        entries: List<Pair<KotlinTypeProjectionPlan, io.github.kitectlab.winrt.metadata.WinRtAuthoredMetadataTypeMapping>>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.first.type.qualifiedName }.forEach { (plan, mapping) ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("projectedTypeName = %S,\n", mapping.projectedTypeName)
            code.add("metadataTypeName = %S,\n", mapping.metadataTypeName)
            code.add("kind = %S,\n", plan.type.kind.name)
            code.add("defaultInterfaceName = %L,\n", nullableStringCode(plan.type.defaultInterfaceName))
            code.add("implementedInterfaceNames = %L,\n", stringListCode(plan.type.implementedInterfaces.map { it.interfaceName }))
            code.add("factoryMemberNames = %L,\n", stringListCode(plan.moduleActivationAndAuthoringDescriptor?.factoryMemberNames.orEmpty()))
            code.add("composableBaseTypeName = %L,\n", nullableStringCode(plan.type.baseTypeName))
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringAbiClassEntriesCode(
        entries: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            val defaultInterface = plan.type.defaultInterfaceName?.let { semanticHelpers.resolveType(WinRtTypeRef.fromDisplayName(it), plan.type.namespace) }
            val defaultInterfaceIsExclusiveTo = defaultInterface?.isExclusiveTo == true
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("projectedTypeName = %S,\n", plan.type.qualifiedName)
            code.add("abiTypeName = %S,\n", "ABI.${plan.type.qualifiedName}")
            code.add("ccwTypeName = %S,\n", "ABI.${plan.type.qualifiedName}")
            code.add("defaultInterfaceName = %L,\n", nullableStringCode(plan.type.defaultInterfaceName))
            code.add("defaultInterfaceIsExclusiveTo = %L,\n", defaultInterfaceIsExclusiveTo)
            code.add("marshalerFamily = %S,\n", if (defaultInterfaceIsExclusiveTo) "MarshalInspectable" else "MarshalInterface")
            code.add("operations = ABI_OPERATIONS,\n")
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringCustomQiEntriesCode(
        entries: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            val descriptor = semanticHelpers.customQueryInterfaceDescriptor(plan.type)
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("projectedTypeName = %S,\n", descriptor.classTypeName)
            code.add("visibility = %S,\n", descriptor.visibility)
            code.add("overridableModifier = %S,\n", descriptor.overridableModifier)
            code.add("overridableInterfaceNames = %L,\n", stringListCode(descriptor.overridableInterfaceNames))
            code.add("delegatesToBase = %L,\n", descriptor.delegatesToBase)
            code.add("notHandledInterfaceNames = NOT_HANDLED_INTERFACE_NAMES,\n")
            code.add("forwardTarget = %S,\n", "NativeObject.TryAs")
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringActivationFactoryEntriesCode(
        entries: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            val factory = plan.factorySurfaceDescriptor ?: semanticHelpers.factorySurfaceDescriptor(plan.type)
            val isActivatable = !semanticHelpers.isStatic(plan.type) && semanticHelpers.hasDefaultConstructor(plan.type)
            val activatableInterfaces = factory.constructorFactories
            val staticInterfaces = factory.staticMemberTargets
            val composableInterfaces = factory.composableFactories
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("projectedTypeName = %S,\n", plan.type.qualifiedName)
            code.add("serverFactoryTypeName = %S,\n", "ABI.${plan.type.qualifiedName}ServerActivationFactory")
            code.add("isActivatable = %L,\n", isActivatable)
            code.add("implementsIActivationFactory = true,\n")
            code.add("factoryInterfaceNames = %L,\n", stringListCode((activatableInterfaces + staticInterfaces).distinct()))
            code.add("activatableFactoryInterfaceNames = %L,\n", stringListCode(activatableInterfaces))
            code.add("staticFactoryInterfaceNames = %L,\n", stringListCode(staticInterfaces))
            code.add("activatableFactoryMemberNames = %L,\n", stringListCode(authoringFactoryMemberReferences(plan, activatableInterfaces)))
            code.add("staticFactoryMemberNames = %L,\n", stringListCode(authoringFactoryMemberReferences(plan, staticInterfaces)))
            code.add("composableFactoryMemberNames = %L,\n", stringListCode(authoringFactoryMemberReferences(plan, composableInterfaces)))
            code.add("makeMethod = %S,\n", "MarshalInspectable.CreateMarshaler2(IID.IActivationFactory).Detach")
            code.add("activateInstanceBehavior = %S,\n", if (isActivatable) "newProjectedInstanceToMarshalInspectable" else "notImplemented")
            code.add("runClassConstructorTypeName = %S,\n", plan.type.qualifiedName)
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringFactoryMemberReferences(
        plan: KotlinTypeProjectionPlan,
        interfaceNames: List<String>,
    ): List<String> =
        interfaceNames
            .flatMap { interfaceName ->
                val interfaceType = plan.typesByQualifiedName[interfaceName] ?: return@flatMap emptyList()
                buildList {
                    interfaceType.methods.forEach { add("$interfaceName.${it.name}") }
                    interfaceType.properties.forEach { add("$interfaceName.${it.name}") }
                    interfaceType.events.forEach { add("$interfaceName.${it.name}") }
                }
            }
            .distinct()
            .sorted()

    private fun authoringModuleActivationFactoryEntriesCode(
        entries: List<KotlinTypeProjectionPlan>,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("runtimeClassName = %S,\n", plan.type.qualifiedName)
            code.add("serverFactoryTypeName = %S,\n", "ABI.${plan.type.qualifiedName}ServerActivationFactory")
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun nullableStringCode(value: String?): CodeBlock =
        value?.let { CodeBlock.of("%S", it) } ?: CodeBlock.of("null")

    private fun stringListCode(values: List<String>): CodeBlock {
        if (values.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }
        val code = CodeBlock.builder()
        code.add("listOf(")
        values.forEachIndexed { index, value ->
            if (index > 0) code.add(", ")
            code.add("%S", value)
        }
        code.add(")")
        return code.build()
    }

    private fun stringTypeName(): TypeName = String::class.asClassName()

    private fun stringListTypeName(): TypeName =
        List::class.asClassName().parameterizedBy(stringTypeName())

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
