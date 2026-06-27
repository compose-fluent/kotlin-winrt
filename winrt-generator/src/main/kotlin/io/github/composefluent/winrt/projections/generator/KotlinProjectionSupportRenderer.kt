package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventHelperSubclassDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiDelegateDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRTGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.Marshaler
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.NativeNestedStructFieldSpec
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.WinRTBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRTCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRTDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTIterableProjection
import io.github.composefluent.winrt.runtime.WinRTListProjection
import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRTReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateReference
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTEvent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
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
    private val supportTypeRenderer = KotlinProjectionRenderer(
        useInterfaceProjectionArtifacts = true,
        useProjectionIntrinsics = true,
    )
    private val planner = KotlinProjectionPlanner()
    private val eventProjectionHelperTypesPerFile: Int = 96
    private val projectionSupportAnchorShardCount: Int = 16
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
    private val GENERIC_ABI_SOURCE_SHARD_SIZE = 48

    fun render(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
        emitProjectionRegistrar: Boolean = true,
        excludedProjectionTypeNames: Set<String> = emptySet(),
        authoredRuntimeClassNames: Set<String> = emptySet(),
        genericTypeInstantiationsClassName: ClassName = WINRT_GENERIC_TYPE_INSTANTIATIONS_CLASS_NAME,
        authoringHostExportsClassName: ClassName = WINRT_AUTHORING_HOST_EXPORTS_CLASS_NAME,
        authoringServerActivationFactoriesClassName: ClassName = WINRT_AUTHORING_SERVER_ACTIVATION_FACTORIES_CLASS_NAME,
        authoringModuleActivationFactoryPlanClassName: ClassName = WINRT_AUTHORING_MODULE_ACTIVATION_FACTORY_PLAN_CLASS_NAME,
        emitJvmAuthoringHostExports: Boolean = true,
        genericAbiSupportFileName: String = "WinRTGenericAbiSupport",
        eventProjectionHelperFilePrefix: String = "WinRTEventProjectionHelper",
        namespaceAdditionsClassName: ClassName = WINRT_NAMESPACE_ADDITIONS_CLASS_NAME,
        supportOwnerIdentity: String? = null,
    ): List<KotlinProjectionFile> {
        val inventory = WinRTMetadataProjectionInventoryBuilder.create(model, context).build()
        validateAuthoringMetadataProjectionPlans(inventory, plans)
        val semanticHelpers = model.semanticHelpers()
        val genericInstantiationWriters = semanticHelpers.genericInstantiationWriterDescriptors(context)
        val registrarPlans = registrarProjectionPlans(plans, inventory, excludedProjectionTypeNames)
        return buildList {
            listOfNotNull(
                renderTypeShapeDescriptorCompilerInput(plans),
                renderProjectionRegistrarCompilerInput(registrarPlans).takeIf { emitProjectionRegistrar },
                renderGenericAbiRegistryCompilerInput(inventory.genericAbiInventory),
                renderGenericTypeInstantiationCompilerInput(genericInstantiationWriters),
                renderGenericTypeInstantiations(genericInstantiationWriters, genericTypeInstantiationsClassName),
                renderCompilerSupportManifest(
                    model,
                    plans,
                    inventory,
                    genericInstantiationWriters,
                    excludedProjectionTypeNames,
                    emitProjectionRegistrar,
                    genericTypeInstantiationsClassName,
                    genericAbiSupportFileName,
                    supportOwnerIdentity,
                ),
                renderWinUiXamlComponentResourceInput(model, plans),
                renderAuthoringMetadataTypeMappingHelper(inventory),
                renderAuthoringWrapperPlan(inventory, plans, authoredRuntimeClassNames),
                renderAuthoringAbiClassPlan(inventory, plans, semanticHelpers, authoredRuntimeClassNames),
                renderAuthoringWrappers(inventory, plans, authoredRuntimeClassNames),
                renderAuthoringAbiClasses(inventory, plans, semanticHelpers, authoredRuntimeClassNames),
                renderAuthoringCustomQueryInterfacePlan(inventory, plans, semanticHelpers, authoredRuntimeClassNames),
                renderAuthoringActivationFactoryPlan(inventory, plans, semanticHelpers, authoredRuntimeClassNames),
                renderAuthoringModuleActivationFactoryPlan(inventory, plans, semanticHelpers, authoringModuleActivationFactoryPlanClassName, authoredRuntimeClassNames),
                renderAuthoringServerActivationFactories(
                    inventory,
                    plans,
                    semanticHelpers,
                    authoringServerActivationFactoriesClassName,
                    authoringModuleActivationFactoryPlanClassName,
                    authoredRuntimeClassNames,
                ),
                renderAuthoringHostExports(
                    inventory,
                    plans,
                    semanticHelpers,
                    authoringHostExportsClassName,
                    authoringServerActivationFactoriesClassName,
                    authoredRuntimeClassNames,
                    emitJvmAddressWrappers = emitJvmAuthoringHostExports,
                ),
                renderAuthoringCcwFactories(inventory, plans, semanticHelpers, authoredRuntimeClassNames),
                renderNamespaceAdditions(inventory, namespaceAdditionsClassName),
            ).forEach(::add)
            if (emitProjectionRegistrar) {
                addAll(renderProjectionSupportAnchors(supportOwnerIdentity))
            }
            addAll(renderGenericAbiSupportSources(inventory.genericAbiInventory, genericAbiSupportFileName))
            addAll(renderDispatcherQueueSynchronizationContextAdditions(plans))
            addAll(renderEventProjectionHelpers(
                model,
                eventOwnerPlans = plans.filterNot { plan -> plan.type.qualifiedName in excludedProjectionTypeNames },
                allPlans = plans,
                eventProjectionHelperFilePrefix = eventProjectionHelperFilePrefix,
            ))
        }
    }

    private fun renderProjectionSupportAnchors(supportOwnerIdentity: String?): List<KotlinProjectionFile> {
        val baseFileName = winRTProjectionSupportAnchorFileName(supportOwnerIdentity)
        return (listOf(baseFileName) + (0 until projectionSupportAnchorShardCount).map { index ->
            "${baseFileName}_${index.toString().padStart(3, '0')}"
        }).map { fileName ->
            supportFile(
                "$fileName.kt",
                supportFileSpec(fileName)
                    .addFileComment("Owner-scoped compiler-plugin anchor for generated projection support initializers.")
                    .build(),
            )
        }
    }

    private fun renderDispatcherQueueSynchronizationContextAdditions(plans: List<KotlinTypeProjectionPlan>): List<KotlinProjectionFile> {
        val planNames = plans.mapTo(linkedSetOf()) { it.type.qualifiedName }
        return listOfNotNull(
            dispatcherQueueSynchronizationContextAddition(
                dispatcherQueueTypeName = "Microsoft.UI.Dispatching.DispatcherQueue",
                dispatcherQueueHandlerTypeName = "Microsoft.UI.Dispatching.DispatcherQueueHandler",
                planNames = planNames,
            ),
            dispatcherQueueSynchronizationContextAddition(
                dispatcherQueueTypeName = "Windows.System.DispatcherQueue",
                dispatcherQueueHandlerTypeName = "Windows.System.DispatcherQueueHandler",
                planNames = planNames,
            ),
        )
    }

    private fun dispatcherQueueSynchronizationContextAddition(
        dispatcherQueueTypeName: String,
        dispatcherQueueHandlerTypeName: String,
        planNames: Set<String>,
    ): KotlinProjectionFile? {
        if (dispatcherQueueTypeName !in planNames || dispatcherQueueHandlerTypeName !in planNames) {
            return null
        }
        val dispatcherQueueType = projectionClassNameForQualifiedName(dispatcherQueueTypeName)
        val dispatcherQueueHandlerType = projectionClassNameForQualifiedName(dispatcherQueueHandlerTypeName)
        val packageName = dispatcherQueueType.packageName
        val contents = """
            package $packageName

            import io.github.composefluent.winrt.runtime.ExceptionHelpers
            import io.github.composefluent.winrt.runtime.WinRTProjectionLock
            import kotlin.coroutines.CoroutineContext
            import kotlinx.coroutines.CoroutineDispatcher
            import kotlinx.coroutines.Runnable

            /**
             * Projection-owned coroutine dispatcher corresponding to CsWinRT's
             * DispatcherQueueSynchronizationContext source addition.
             *
             * Kotlin/JVM uses CoroutineDispatcher instead of System.Threading.SynchronizationContext.
             * The dispatcher owns one WinRT handler delegate and drains posted coroutine blocks
             * through it, so repeated dispatches do not allocate a new WinRT delegate callback.
             */
            public class DispatcherQueueCoroutineDispatcher(
              private val dispatcherQueue: ${dispatcherQueueType.simpleName},
            ) : CoroutineDispatcher() {
              private val lock: WinRTProjectionLock = WinRTProjectionLock()
              private val pendingPosts: MutableList<() -> Unit> = mutableListOf()
              private var drainScheduled: Boolean = false
              private val drainHandler: ${dispatcherQueueHandlerType.simpleName} = ${dispatcherQueueHandlerType.simpleName} {
                drain()
              }

              override fun dispatch(context: CoroutineContext, block: Runnable) {
                if (!post { block.run() }) {
                  ExceptionHelpers.reportUnhandledError(
                    IllegalStateException("DispatcherQueue.TryEnqueue returned false."),
                  )
                }
              }

              public fun post(action: () -> Unit): Boolean {
                val shouldSchedule = lock.withLock {
                  pendingPosts += action
                  if (drainScheduled) {
                    false
                  } else {
                    drainScheduled = true
                    true
                  }
                }
                if (!shouldSchedule) {
                  return true
                }
                if (dispatcherQueue.tryEnqueue(drainHandler)) {
                  return true
                }
                lock.withLock {
                  pendingPosts.remove(action)
                  if (pendingPosts.isEmpty()) {
                    drainScheduled = false
                  }
                }
                return false
              }

              public fun createCopy(): DispatcherQueueCoroutineDispatcher =
                DispatcherQueueCoroutineDispatcher(dispatcherQueue)

              private fun drain() {
                while (true) {
                  val action = lock.withLock {
                    if (pendingPosts.isEmpty()) {
                      drainScheduled = false
                      null
                    } else {
                      pendingPosts.removeAt(0)
                    }
                  } ?: return
                  try {
                    action()
                  } catch (error: Throwable) {
                    ExceptionHelpers.reportUnhandledError(error)
                  }
                }
              }
            }

            public fun ${dispatcherQueueType.simpleName}.asCoroutineDispatcher(): DispatcherQueueCoroutineDispatcher =
              DispatcherQueueCoroutineDispatcher(this)
        """.trimIndent() + "\n"
        return KotlinProjectionFile(
            relativePath = "${packageName.replace('.', '/')}/DispatcherQueueCoroutineDispatcher.kt",
            packageName = packageName,
            contents = contents,
        )
    }

    private fun renderWinUiXamlComponentResourceInput(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        val resourceDictionaryRuntimeClassNames = winUiXamlComponentResourceDictionaryRuntimeClassNames(model, plans)
        if (resourceDictionaryRuntimeClassNames.isEmpty()) {
            return null
        }
        return KotlinProjectionFile(
            relativePath = "kotlin-winrt-support/xaml-component-resources.tsv",
            packageName = "",
            contents = resourceDictionaryRuntimeClassNames.joinToString(
                separator = "\n",
                postfix = "\n",
                prefix = "runtimeClassName\n",
            ),
        )
    }

    private fun winUiXamlComponentResourceDictionaryRuntimeClassNames(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ): List<String> {
        if (plans.none { plan -> plan.type.qualifiedName == "Microsoft.UI.Xaml.ResourceDictionary" }) {
            return emptyList()
        }
        return model.namespaces
            .asSequence()
            .flatMap { namespace -> namespace.types.asSequence() }
            .filter { type -> type.kind == WinRTTypeKind.RuntimeClass }
            .filter { type -> type.baseTypeName == "Microsoft.UI.Xaml.ResourceDictionary" }
            .map(WinRTTypeDefinition::qualifiedName)
            .filterNot { runtimeClassName ->
                runtimeClassName.startsWith("Microsoft.") || runtimeClassName.startsWith("Windows.")
            }
            .distinct()
            .sorted()
            .toList()
    }

    private fun validateAuthoringMetadataProjectionPlans(
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return
        }
        val plansByType = plans.associateBy { it.type.qualifiedName }
        inventory.authoredMetadataTypeMappings.forEach { mapping ->
            require(mapping.projectedTypeName in plansByType) {
                "Support renderer requires authored metadata type ${mapping.projectedTypeName} to have a projection plan before rendering authoring support files."
            }
        }
    }

    private fun registrarProjectionPlans(
        plans: List<KotlinTypeProjectionPlan>,
        inventory: WinRTMetadataProjectionInventory,
        excludedProjectionTypeNames: Set<String>,
    ): List<KotlinTypeProjectionPlan> {
        val authoredTypes = inventory.authoredMetadataTypeMappings
            .mapTo(excludedProjectionTypeNames.toMutableSet()) { it.projectedTypeName }
        return plans
            .asSequence()
            .filterNot { it.type.qualifiedName in authoredTypes }
            .filterNot { it.type.kind == WinRTTypeKind.Unknown }
            .sortedBy { it.type.qualifiedName }
            .toList()
    }


    private fun renderTypeShapeDescriptorCompilerInput(plans: List<KotlinTypeProjectionPlan>): KotlinProjectionFile? {
        val rows = buildList {
            add("projectedTypeName\tkey\tvalue")
            plans.sortedBy { it.type.qualifiedName }.forEach { plan ->
                addTypeShapeDescriptorRows(plan)
            }
        }
        if (rows.size == 1) {
            return null
        }
        return KotlinProjectionFile(
            relativePath = "kotlin-winrt-support/type-shape-descriptors.tsv",
            packageName = "",
            contents = rows.joinToString(separator = "\n", postfix = "\n"),
        )
    }

    private fun MutableList<String>.addTypeShapeDescriptorRows(plan: KotlinTypeProjectionPlan) {
        val typeName = plan.type.qualifiedName
        fun row(key: String, value: String) {
            add(listOf(typeName, key, value).joinToString("\t"))
        }
        fun listRow(key: String, values: List<String>) {
            if (values.isNotEmpty()) {
                row(key, values.joinToString(TYPE_SHAPE_DESCRIPTOR_LIST_SEPARATOR))
            }
        }
        fun boolRow(key: String, value: Boolean) {
            row(key, value.toString())
        }

        listRow(
            "PROJECTED_ATTRIBUTES",
            plan.projectedAttributes.map { attribute ->
                listOf(
                    attribute.projectedTypeName,
                    attribute.metadataTypeName,
                    "platform=${attribute.isPlatformAttribute}",
                    "args=${attribute.renderedArguments.joinToString(",")}",
                ).joinToString("|")
            },
        )
        val declaration = plan.typeDeclarationDescriptor
        boolRow("WRITES_ABI_DECLARATION", declaration.writesAbiDeclaration)
        boolRow("WRITES_WRAPPER_DECLARATION", declaration.writesWrapperDeclaration)
        boolRow("WRITES_HELPER_CLASS", declaration.writesHelperClass)
        plan.factorySurfaceDescriptor?.let { descriptor ->
            row("FACTORY_CACHE_NAME", descriptor.activationFactoryCacheName)
            listRow("FACTORY_STATIC_TARGETS", descriptor.staticMemberTargets)
            listRow("FACTORY_CONSTRUCTOR_TARGETS", descriptor.constructorFactories)
            listRow("FACTORY_COMPOSABLE_TARGETS", descriptor.composableFactories)
        }
        plan.objectReferenceSurfaceDescriptor?.let { descriptor ->
            listRow("OBJECT_REFERENCE_NAMES", descriptor.objectReferenceNames)
            listRow("OBJECT_REFERENCE_METADATA_NAMES", descriptor.exposedTypeMetadataNames)
            listRow(
                "OBJECT_REFERENCE_PLANS",
                descriptor.objectReferencePlans.map { referencePlan ->
                    listOf(
                        referencePlan.interfaceName,
                        "cache=${referencePlan.cacheName}",
                        "default=${referencePlan.isDefaultInterface}",
                        "skip=${referencePlan.skippedReason.orEmpty()}",
                        "inner=${referencePlan.usesInner}",
                        "defaultObjRef=${referencePlan.usesDefaultInterfaceObjRef}",
                        "hierarchy=${referencePlan.defaultInterfaceHierarchyIndex?.toString().orEmpty()}",
                        "defaultObjRefSlot=${referencePlan.defaultInterfaceObjRefVtableSlot?.toString().orEmpty()}",
                        "generic=${referencePlan.requiresGenericInstantiation}",
                    ).joinToString("|")
                },
            )
        }
        plan.guidSignatureDescriptor?.let { descriptor ->
            row("GUID_SIGNATURE_FRAGMENT", descriptor.signatureFragment)
        }
        plan.interfaceMemberSignatureSetDescriptor?.let { descriptor ->
            listRow(
                "INTERFACE_METHOD_SIGNATURES",
                descriptor.methodSignatures.map { signature ->
                    "${signature.methodName}:${signature.returnTypeName}(${signature.parameterTypeNames.joinToString(",")})"
                },
            )
            listRow("INTERFACE_PROPERTY_NAMES", descriptor.propertyNames)
            listRow("INTERFACE_EVENT_NAMES", descriptor.eventNames)
        }
        plan.customMappedMemberOutputDescriptor?.let { descriptor ->
            listRow("CUSTOM_MAPPED_MEMBER_PLANS", descriptor.memberPlans)
            row("CUSTOM_MAPPED_MEMBER_CALL_MODE", descriptor.callMode)
            boolRow("CUSTOM_MAPPED_MEMBER_EXPLICIT", descriptor.emitsExplicitMembers)
            boolRow("CUSTOM_MAPPED_MEMBER_PRIVATE", descriptor.emitsPrivateMembers)
        }
        plan.genericAbiClassInitializationDescriptor?.let { descriptor ->
            listRow("GENERIC_ABI_INVOKE_SLOTS", descriptor.invokeSlotNames)
            listRow("GENERIC_ABI_TYPE_ARRAYS", descriptor.genericTypeArrayDependencies)
        }
        plan.requiredInterfaceAugmentationDescriptor?.let { descriptor ->
            listRow("REQUIRED_INTERFACE_NAMES", descriptor.requiredInterfaceNames)
            listRow("REQUIRED_EXPLICIT_FORWARD_MEMBERS", descriptor.explicitForwardMemberNames)
            listRow("REQUIRED_MAPPED_AUGMENTATION_MEMBERS", descriptor.mappedAugmentationMembers)
            listRow(
                "REQUIRED_MAPPED_HELPER_PLANS",
                descriptor.mappedHelperPlans.map { helperPlan -> helperPlan.toSupportPlanString() },
            )
        }
        plan.fastAbiClassDescriptor?.let { descriptor ->
            listRow(
                "FAST_ABI_INTERFACE_SLOTS",
                descriptor.interfaceSlots.map { slot ->
                    listOf(
                        slot.interfaceName,
                        "default=${slot.isDefault}",
                        "start=${slot.vtableStartIndex}",
                        "count=${slot.methodCount}",
                        "hierarchyOffset=${slot.hierarchyOffsetAfterDefault}",
                        "next=${slot.nextVtableStartIndex}",
                    ).joinToString("|")
                },
            )
            listRow(
                "FAST_ABI_PROPERTY_SLOTS",
                descriptor.propertySlots.map { slot ->
                    listOf(
                        slot.propertyName,
                        "start=${slot.vtableStartIndex}",
                        "get=${slot.getterVtableIndex ?: ""}",
                        "set=${slot.setterVtableIndex ?: ""}",
                    ).joinToString("|")
                },
            )
        }
        plan.moduleActivationAndAuthoringDescriptor?.let { module ->
            listRow("DEFERRED_AUTHORING_FACTORY_MEMBERS", module.factoryMemberNames)
            listRow("DEFERRED_MODULE_ACTIVATION_FACTORY_ENTRIES", module.moduleActivationFactoryEntries)
        }
    }

    private fun renderCompilerSupportManifest(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
        inventory: WinRTMetadataProjectionInventory,
        genericInstantiationWriters: List<WinRTGenericInstantiationWriterDescriptor>,
        excludedProjectionTypeNames: Set<String>,
        emitProjectionRegistrar: Boolean,
        genericTypeInstantiationsClassName: ClassName,
        genericAbiSupportFileName: String,
        supportOwnerIdentity: String?,
    ): KotlinProjectionFile? {
        val registrarEntries = if (emitProjectionRegistrar) {
            registrarProjectionPlans(plans, inventory, excludedProjectionTypeNames).size
        } else {
            0
        }
        val genericInstantiationEntries = genericInstantiationWriters.size
        val genericAbiRegistryEntries = inventory.genericAbiInventory.genericAbiDelegates.size +
            inventory.genericAbiInventory.derivedGenericInterfaces.size
        val xamlComponentResourceEntries = winUiXamlComponentResourceDictionaryRuntimeClassNames(model, plans).size
        val rows = listOfNotNull(
            compilerSupportManifestRow(
                kind = "projection-registrar",
                className = WINRT_PROJECTION_SUPPORT_INTRINSIC_CLASS_NAME.canonicalName,
                sourceFile = "projection-registrar.tsv",
                entries = registrarEntries,
                owner = supportOwnerIdentity.orEmpty(),
            ),
            compilerSupportManifestRow(
                kind = "generic-type-instantiation",
                className = genericTypeInstantiationsClassName.canonicalName,
                sourceFile = "generic-instantiations.tsv",
                entries = genericInstantiationEntries,
                owner = supportOwnerIdentity.orEmpty(),
            ),
            compilerSupportManifestRow(
                kind = "generic-abi-registry",
                className = "$SUPPORT_PACKAGE.$genericAbiSupportFileName",
                sourceFile = "generic-abi-registry.tsv",
                entries = genericAbiRegistryEntries,
                owner = supportOwnerIdentity.orEmpty(),
            ),
            compilerSupportManifestRow(
                kind = "xaml-component-resource",
                className = "$SUPPORT_PACKAGE.WinUiXamlComponentResources",
                sourceFile = "xaml-component-resources.tsv",
                entries = xamlComponentResourceEntries,
                owner = supportOwnerIdentity.orEmpty(),
            ),
        )
        if (rows.isEmpty()) {
            return null
        }
        return KotlinProjectionFile(
            relativePath = "kotlin-winrt-support/compiler-support.tsv",
            packageName = "",
            contents = rows.joinToString(
                separator = "\n",
                postfix = "\n",
                prefix = "kind\tclassName\tsourceFile\tentries\towner\n",
            ),
        )
    }

    private fun compilerSupportManifestRow(
        kind: String,
        className: String,
        sourceFile: String,
        entries: Int,
        owner: String,
    ): String? =
        if (entries == 0) {
            null
        } else {
            listOf(kind, className, sourceFile, entries.toString(), owner).joinToString("\t")
        }

    private fun renderGenericTypeInstantiationCompilerInput(
        descriptors: List<WinRTGenericInstantiationWriterDescriptor>,
    ): KotlinProjectionFile? {
        if (descriptors.isEmpty()) {
            return null
        }
        val rows = descriptors.joinToString(
            separator = "\n",
            postfix = "\n",
            prefix = "className\tsourceType\tisDelegate\trcwFunctions\tvtableFunctions\tpropertyAccessors\tgenericReturnOnlyRcwFunctions\tprojectedGenericFallbacks\tdependencies\n",
        ) { descriptor ->
            listOf(
                descriptor.instantiationClassName,
                descriptor.sourceTypeName,
                descriptor.isDelegateInstantiation.toString(),
                descriptor.rcwFunctionNames.joinToString(","),
                descriptor.vtableFunctionNames.joinToString(","),
                descriptor.propertyAccessorFunctionNames.joinToString(","),
                descriptor.genericReturnOnlyRcwFunctionNames.joinToString(","),
                descriptor.projectedGenericFallbackFunctionNames.joinToString(","),
                descriptor.initializationDependencies.joinToString(","),
            ).joinToString("\t")
        }
        return KotlinProjectionFile(
            relativePath = "kotlin-winrt-support/generic-instantiations.tsv",
            packageName = "",
            contents = rows,
        )
    }

    private fun renderProjectionRegistrarCompilerInput(
        registrationPlans: List<KotlinTypeProjectionPlan>,
    ): KotlinProjectionFile? {
        if (registrationPlans.isEmpty()) {
            return null
        }
        val rows = registrationPlans.joinToString(
            separator = "\n",
            postfix = "\n",
            prefix = "kotlinClassName\tprojectedTypeName\tkind\tbaseTypeName\tmetadataClassName\tinterfaceIid\n",
        ) { plan ->
            val projectedClassName = projectionClassNameForQualifiedName(plan.type.qualifiedName)
            listOf(
                projectedClassName.canonicalName,
                plan.type.qualifiedName,
                plan.type.kind.name,
                plan.type.baseTypeName.orEmpty(),
                projectedClassName
                    .takeIf { plan.hasGeneratedRuntimeClassMetadataRegistration() }
                    ?.let { "${it.canonicalName}.Metadata" }
                    .orEmpty(),
                plan.interfaceIid?.toString().orEmpty(),
            ).joinToString("\t")
        }
        return KotlinProjectionFile(
            relativePath = "kotlin-winrt-support/projection-registrar.tsv",
            packageName = "",
            contents = rows,
        )
    }

    private fun renderGenericAbiRegistryCompilerInput(inventory: WinRTGenericAbiInventory): KotlinProjectionFile? {
        if (inventory.genericAbiDelegates.isEmpty() && inventory.derivedGenericInterfaces.isEmpty()) {
            return null
        }
        val rows = buildList {
            add("kind\tname\tsourceGenericType\toperation\tdeclaration\tabiParameterTypes\ttypeArrayShape")
            inventory.derivedGenericInterfaces.forEach { typeName ->
                add(
                    listOf(
                        "derived-interface",
                        typeName,
                        "",
                        "",
                        "",
                        "",
                        "",
                    ).joinToString("\t"),
                )
            }
            inventory.genericAbiDelegates.forEach { descriptor ->
                add(
                    listOf(
                        "delegate",
                        descriptor.abiDelegateName,
                        descriptor.sourceGenericType.typeName,
                        descriptor.operationName,
                        descriptor.declaration,
                        descriptor.abiParameterTypeNames.joinToString(GENERIC_ABI_REGISTRY_LIST_SEPARATOR),
                        descriptor.typeArrayShape.joinToString(GENERIC_ABI_REGISTRY_LIST_SEPARATOR),
                    ).joinToString("\t"),
                )
            }
        }.joinToString(separator = "\n", postfix = "\n")
        return KotlinProjectionFile(
            relativePath = "kotlin-winrt-support/generic-abi-registry.tsv",
            packageName = "",
            contents = rows,
        )
    }

    private fun renderGenericAbiSupportSources(
        inventory: WinRTGenericAbiInventory,
        genericAbiSupportFileName: String,
    ): List<KotlinProjectionFile> {
        if (inventory.genericAbiDelegates.isEmpty() && inventory.derivedGenericInterfaces.isEmpty()) {
            return emptyList()
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "GenericAbiDelegateEntry")
        val delegates = inventory.genericAbiDelegates.sortedWith(
            compareBy(
                { descriptor -> descriptor.abiDelegateName },
                { descriptor -> descriptor.sourceGenericType.typeName },
            ),
        ).distinctBy { descriptor ->
            descriptor.abiDelegateName + "\u0000" + descriptor.sourceGenericType.typeName + "\u0000" +
                descriptor.typeArrayShape.joinToString("\u0000")
        }
        val delegatesByName = delegates.distinctBy(WinRTGenericAbiDelegateDescriptor::abiDelegateName)
        val delegatesBySourceType = delegates.groupBy { descriptor -> descriptor.sourceGenericType.typeName }.toSortedMap()
        val derivedInterfaces = inventory.derivedGenericInterfaces.distinct().sorted()
        val delegatesByNameChunks = delegatesByName.chunked(GENERIC_ABI_SOURCE_SHARD_SIZE)
        val delegatesBySourceTypeChunks = delegatesBySourceType.entries.chunked(GENERIC_ABI_SOURCE_SHARD_SIZE)
        val derivedInterfaceChunks = derivedInterfaces.chunked(GENERIC_ABI_SOURCE_SHARD_SIZE)
        val delegateRegistrationChunks = delegates.chunked(GENERIC_ABI_SOURCE_SHARD_SIZE)
        val shardPrefix = genericAbiSupportShardFunctionPrefix(genericAbiSupportFileName)
        val objectBuilder = TypeSpec.objectBuilder(genericAbiSupportFileName)
            .addModifiers(KModifier.INTERNAL)
            .addFunction(
                FunSpec.builder("delegateNamed")
                    .addParameter("name", stringTypeName())
                    .returns(entryClass.copy(nullable = true))
                    .apply {
                        delegatesByNameChunks.forEachIndexed { index, _ ->
                            val resultName = "delegateNamedChunk${index}Result"
                            addStatement("val %L = %LDelegateNamedChunk%L(name)", resultName, shardPrefix, index)
                            addStatement("if (%L != null) return %L", resultName, resultName)
                        }
                        addStatement("return null")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("delegatesForSourceType")
                    .addParameter("sourceGenericType", stringTypeName())
                    .returns(List::class.asClassName().parameterizedBy(entryClass))
                    .apply {
                        delegatesBySourceTypeChunks.forEachIndexed { index, _ ->
                            val resultName = "delegatesForSourceTypeChunk${index}Result"
                            addStatement("val %L = %LDelegatesForSourceTypeChunk%L(sourceGenericType)", resultName, shardPrefix, index)
                            addStatement("if (%L != null) return %L", resultName, resultName)
                        }
                        addStatement("return emptyList()")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("isDerivedGenericInterface")
                    .addParameter("typeName", stringTypeName())
                    .returns(Boolean::class)
                    .apply {
                        derivedInterfaceChunks.forEachIndexed { index, _ ->
                            val resultName = "isDerivedGenericInterfaceChunk${index}Result"
                            addStatement("val %L = %LIsDerivedGenericInterfaceChunk%L(typeName)", resultName, shardPrefix, index)
                            addStatement("if (%L != null) return %L", resultName, resultName)
                        }
                        addStatement("return false")
                    }
                    .build(),
            )
            .addFunction(
                FunSpec.builder("registerAbiDelegates")
                    .addParameter("register", genericAbiRegisterFunctionTypeName())
                    .apply {
                        delegateRegistrationChunks.forEachIndexed { index, _ ->
                            addStatement("%LRegisterAbiDelegatesChunk%L(register)", shardPrefix, index)
                        }
                    }
                    .build(),
            )
        val fileSpec = supportFileSpec(genericAbiSupportFileName)
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
            .addType(objectBuilder.build())
            .build()
        val shardFiles = (0 until maxOf(
            delegatesByNameChunks.size,
            delegatesBySourceTypeChunks.size,
            derivedInterfaceChunks.size,
            delegateRegistrationChunks.size,
        )).map { index ->
            val shardFileName = "${genericAbiSupportFileName}_${index.toString().padStart(3, '0')}"
            val builder = supportFileSpec(shardFileName)
            delegatesByNameChunks.getOrNull(index)?.let { chunk ->
                builder.addFunction(genericAbiDelegateNamedChunk("${shardPrefix}DelegateNamedChunk$index", chunk, entryClass))
            }
            delegatesBySourceTypeChunks.getOrNull(index)?.let { chunk ->
                builder.addFunction(genericAbiDelegatesForSourceTypeChunk("${shardPrefix}DelegatesForSourceTypeChunk$index", chunk, entryClass))
            }
            derivedInterfaceChunks.getOrNull(index)?.let { chunk ->
                builder.addFunction(genericAbiDerivedInterfaceChunk("${shardPrefix}IsDerivedGenericInterfaceChunk$index", chunk))
            }
            delegateRegistrationChunks.getOrNull(index)?.let { chunk ->
                builder.addFunction(genericAbiRegisterDelegatesChunk("${shardPrefix}RegisterAbiDelegatesChunk$index", chunk))
            }
            supportFile("$shardFileName.kt", builder.build())
        }
        return listOf(supportFile("$genericAbiSupportFileName.kt", fileSpec)) + shardFiles
    }

    private fun genericAbiDelegateNamedChunk(
        functionName: String,
        delegates: List<WinRTGenericAbiDelegateDescriptor>,
        entryClass: ClassName,
    ): FunSpec =
        FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addParameter("name", stringTypeName())
            .returns(entryClass.copy(nullable = true))
            .apply {
                beginControlFlow("return when (name)")
                delegates.forEach { descriptor ->
                    addStatement("%S -> %L", descriptor.abiDelegateName, genericAbiDelegateEntryCode(descriptor))
                }
                addStatement("else -> null")
                endControlFlow()
            }
            .build()

    private fun genericAbiDelegatesForSourceTypeChunk(
        functionName: String,
        entries: List<Map.Entry<String, List<WinRTGenericAbiDelegateDescriptor>>>,
        entryClass: ClassName,
    ): FunSpec =
        FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addParameter("sourceGenericType", stringTypeName())
            .returns(List::class.asClassName().parameterizedBy(entryClass).copy(nullable = true))
            .apply {
                beginControlFlow("return when (sourceGenericType)")
                entries.forEach { (sourceGenericType, descriptors) ->
                    addStatement("%S -> %L", sourceGenericType, genericAbiDelegateEntryListCode(descriptors))
                }
                addStatement("else -> null")
                endControlFlow()
            }
            .build()

    private fun genericAbiDerivedInterfaceChunk(
        functionName: String,
        typeNames: List<String>,
    ): FunSpec =
        FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addParameter("typeName", stringTypeName())
            .returns(Boolean::class.asClassName().copy(nullable = true))
            .apply {
                beginControlFlow("return when (typeName)")
                typeNames.forEach { typeName ->
                    addStatement("%S -> true", typeName)
                }
                addStatement("else -> null")
                endControlFlow()
            }
            .build()

    private fun genericAbiRegisterDelegatesChunk(
        functionName: String,
        delegates: List<WinRTGenericAbiDelegateDescriptor>,
    ): FunSpec =
        FunSpec.builder(functionName)
            .addModifiers(KModifier.INTERNAL)
            .addParameter("register", genericAbiRegisterFunctionTypeName())
            .apply {
                delegates.forEach { descriptor ->
                    addStatement("register(%L, %S)", stringListCode(descriptor.typeArrayShape), descriptor.abiDelegateName)
                }
            }
            .build()

    private fun genericAbiSupportShardFunctionPrefix(genericAbiSupportFileName: String): String =
        genericAbiSupportFileName.replaceFirstChar { char -> char.lowercase() }

    private fun genericAbiDelegateEntryListCode(
        delegates: List<WinRTGenericAbiDelegateDescriptor>,
    ): CodeBlock {
        if (delegates.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        delegates.forEach { descriptor ->
            code.add("%L,\n", genericAbiDelegateEntryCode(descriptor))
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun genericAbiDelegateEntryCode(
        descriptor: WinRTGenericAbiDelegateDescriptor,
    ): CodeBlock =
        CodeBlock.of(
            "%T(%S, %S, %S, %S, %L, %L)",
            ClassName(SUPPORT_PACKAGE, "GenericAbiDelegateEntry"),
            descriptor.abiDelegateName,
            descriptor.sourceGenericType.typeName,
            descriptor.operationName,
            descriptor.declaration,
            stringListCode(descriptor.abiParameterTypeNames),
            stringListCode(descriptor.typeArrayShape),
        )

    private fun renderGenericTypeInstantiations(
        descriptors: List<WinRTGenericInstantiationWriterDescriptor>,
        genericTypeInstantiationsClassName: ClassName,
    ): KotlinProjectionFile? {
        if (descriptors.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "GenericTypeInstantiationEntry")
        val fileSpec = supportFileSpec(genericTypeInstantiationsClassName.simpleName)
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
            .addType(
                TypeSpec.objectBuilder(genericTypeInstantiationsClassName.simpleName)
                    .addModifiers(KModifier.INTERNAL)
                    .addFunctions(genericTypeInstantiationFunctions(entryClass))
                    .build(),
            )
            .build()
        return supportFile("${genericTypeInstantiationsClassName.simpleName}.kt", fileSpec)
    }

    private fun renderEventProjectionHelpers(
        model: WinRTMetadataModel,
        eventOwnerPlans: List<KotlinTypeProjectionPlan>,
        allPlans: List<KotlinTypeProjectionPlan>,
        eventProjectionHelperFilePrefix: String,
    ): List<KotlinProjectionFile> {
        val eventSourceEntries = planner.eventSourceDescriptors(model, allPlans)
        val delegateEventSourceEntries = planner.eventSourceDescriptors(model, allPlans)
        if (eventSourceEntries.isEmpty() && delegateEventSourceEntries.isEmpty()) {
            return emptyList()
        }
        val plansByType = allPlans.associateBy { plan -> plan.type.qualifiedName }
        val typesByQualifiedName = model.namespaces
            .flatMap(WinRTNamespace::types)
            .associateBy(WinRTTypeDefinition::qualifiedName)
        val helperTypes = buildList {
            delegateEventSourceEntries
            .filterNot { it.usesSharedEventHandlerSource }
            .distinctBy(WinRTEventHelperSubclassDescriptor::sourceClassName)
            .forEach { descriptor ->
                val rawEventType = descriptor.projectedEventTypeName.substringBefore('<')
                val delegatePlan = plansByType[rawEventType] ?: return@forEach
                val invokeShape = concreteEventInvokeShape(descriptor, typesByQualifiedName) ?: return@forEach
                if (invokeShape.isSupportedProjectedDelegateShape() && invokeShape.supportsEventSourceCallbackWrapping(plansByType)) {
                    add(eventSourceSubclassType(descriptor, delegatePlan, invokeShape, plansByType))
                }
            }
            eventSourceEntries
            .groupBy { descriptor -> descriptor.ownerTypeName to descriptor.eventTypeName }
            .toSortedMap(compareBy({ it.first }, { it.second }))
            .values
            .forEach { ownerEntries ->
                eventSourceOwnerHelperType(ownerEntries, typesByQualifiedName, plansByType)?.let(::add)
            }
        }
        return helperTypes
            .sortedBy { type -> type.name }
            .chunked(eventProjectionHelperTypesPerFile)
            .mapIndexed { index, chunk ->
                val fileName = "${eventProjectionHelperFilePrefix}_${index.toString().padStart(3, '0')}"
                val fileSpec = supportFileSpec(fileName)
                    .apply { chunk.forEach(::addType) }
                    .build()
                supportFile("$fileName.kt", fileSpec)
            }
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
                    .addJvmFieldAnnotation()
                    .initializer(abiEntriesBuildListCode(chunkedAbiPlans.indices))
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("ENTRIES_BY_TYPE_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                    .addJvmFieldAnnotation()
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
        inventory: WinRTMetadataProjectionInventory,
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
                            .addJvmFieldAnnotation()
                            .initializer(typeShapeEntriesCode(plans.sortedBy { it.type.qualifiedName }, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("TYPES_BY_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
                            .initializer("TYPES.associateBy({ it.typeName })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("BASE_TYPE_MAPPING_TABLE", Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()))
                            .addJvmFieldAnnotation()
                            .initializer("BASE_TYPE_MAPPINGS.toArrowMap()")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("AUTHORING_METADATA_MAPPING_TABLE", Map::class.asClassName().parameterizedBy(stringTypeName(), stringTypeName()))
                            .addJvmFieldAnnotation()
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
        inventory: WinRTMetadataProjectionInventory,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val fileSpec = supportFileSpec("AuthoringMetadataTypeMappingHelper")
            .addImport("io.github.composefluent.winrt.runtime", "ComWrappersSupport")
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
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val mappingsByProjectedName = inventory.authoredMetadataTypeMappings
            .filter { it.projectedTypeName in authoredTypeNames }
            .associateBy { it.projectedTypeName }
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
                    .addJvmFieldAnnotation()
                    .initializer(authoringWrapperEntriesCode(entries, entryClass))
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("WRAPPERS_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                    .addJvmFieldAnnotation()
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

    private fun authoringEntryTypeNames(
        inventory: WinRTMetadataProjectionInventory,
        authoredRuntimeClassNames: Set<String>,
    ): Set<String> {
        val metadataTypeNames = inventory.authoredMetadataTypeMappings.mapTo(mutableSetOf()) { it.projectedTypeName }
        return authoredRuntimeClassNames
            .takeIf(Set<String>::isNotEmpty)
            ?.intersect(metadataTypeNames)
            ?: metadataTypeNames
    }

    private fun renderAuthoringAbiClassPlan(
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && !semanticHelpers.isStatic(it.type) }
            .filter { plan -> plan.type.qualifiedName in authoredTypeNames }
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
                            .addJvmFieldAnnotation()
                            .initializer(stringListCode(AUTHORING_ABI_OPERATIONS))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("CLASSES", List::class.asClassName().parameterizedBy(entryClass))
                            .addJvmFieldAnnotation()
                            .initializer(authoringAbiClassEntriesCode(entries, semanticHelpers, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("CLASSES_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
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
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
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
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec("WinRTAuthoringAbiClasses")
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            fileBuilder.addType(authoringAbiClassObject(plan))
        }
        fileBuilder.addType(
            TypeSpec.objectBuilder("WinRTAuthoringAbiClasses")
                .addModifiers(KModifier.INTERNAL)
                .addFunction(authoringAbiClassFromAbiLookupFunction(entries))
                .build(),
        )
        return supportFile("WinRTAuthoringAbiClasses.kt", fileBuilder.build())
    }

    private fun renderAuthoringCustomQueryInterfacePlan(
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
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
                            .addJvmFieldAnnotation()
                            .initializer(stringListCode(AUTHORING_CUSTOM_QI_NOT_HANDLED_INTERFACES))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .addJvmFieldAnnotation()
                            .initializer(authoringCustomQiEntriesCode(entries, semanticHelpers, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
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
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
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
                            .addJvmFieldAnnotation()
                            .initializer(authoringActivationFactoryEntriesCode(entries, semanticHelpers, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("FACTORIES_BY_PROJECTED_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
                            .initializer("FACTORIES.associateBy({ it.projectedTypeName })")
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("FACTORIES_BY_SERVER_TYPE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
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

    private fun renderNamespaceAdditions(
        inventory: WinRTMetadataProjectionInventory,
        namespaceAdditionsClassName: ClassName,
    ): KotlinProjectionFile? {
        if (inventory.namespaceAdditions.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "NamespaceAdditionEntry")
        val fileSpec = supportFileSpec(namespaceAdditionsClassName.simpleName)
            .addType(
                dataClass(
                    className = "NamespaceAdditionEntry",
                    fields = listOf(
                        "namespace" to stringTypeName(),
                        "kind" to stringTypeName(),
                        "sourceFiles" to stringListTypeName(),
                    ),
                ),
            )
            .addType(
                TypeSpec.objectBuilder(namespaceAdditionsClassName.simpleName)
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .addJvmFieldAnnotation()
                            .initializer(namespaceAdditionEntriesCode(inventory, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_NAMESPACE", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
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
        return supportFile("${namespaceAdditionsClassName.simpleName}.kt", fileSpec)
    }

    private fun renderAuthoringModuleActivationFactoryPlan(
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoringModuleActivationFactoryPlanClassName: ClassName,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val entryClass = ClassName(SUPPORT_PACKAGE, "AuthoringModuleActivationFactoryEntry")
        val fileSpec = supportFileSpec(authoringModuleActivationFactoryPlanClassName.simpleName)
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
                TypeSpec.objectBuilder(authoringModuleActivationFactoryPlanClassName.simpleName)
                    .addModifiers(KModifier.INTERNAL)
                    .addProperty(
                        PropertySpec.builder("ENTRIES", List::class.asClassName().parameterizedBy(entryClass))
                            .addJvmFieldAnnotation()
                            .initializer(authoringModuleActivationFactoryEntriesCode(entries, entryClass))
                            .build(),
                    )
                    .addProperty(
                        PropertySpec.builder("ENTRIES_BY_RUNTIME_CLASS_NAME", Map::class.asClassName().parameterizedBy(stringTypeName(), entryClass))
                            .addJvmFieldAnnotation()
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
        return supportFile("${authoringModuleActivationFactoryPlanClassName.simpleName}.kt", fileSpec)
    }

    private fun renderAuthoringServerActivationFactories(
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoringServerActivationFactoriesClassName: ClassName,
        authoringModuleActivationFactoryPlanClassName: ClassName,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec(authoringServerActivationFactoriesClassName.simpleName)
        val plansByQualifiedName = plans.associateBy { it.type.qualifiedName }
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            fileBuilder.addType(authoringServerActivationFactoryClass(plan, semanticHelpers, plansByQualifiedName))
        }
        fileBuilder.addType(
            TypeSpec.objectBuilder(authoringServerActivationFactoriesClassName.simpleName)
                .addModifiers(KModifier.INTERNAL)
                .addFunction(authoringServerActivationFactoryRegisterFunction(entries, authoringModuleActivationFactoryPlanClassName))
                .build(),
        )
        return supportFile("${authoringServerActivationFactoriesClassName.simpleName}.kt", fileBuilder.build())
    }

    private fun renderAuthoringHostExports(
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoringHostExportsClassName: ClassName,
        authoringServerActivationFactoriesClassName: ClassName,
        authoredRuntimeClassNames: Set<String>,
        emitJvmAddressWrappers: Boolean,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val authoredTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in authoredTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val hostBridgeClass = ClassName("io.github.composefluent.winrt.authoring", "WinRTAuthoringHostBridge")
        val hostExportsInterface = ClassName("io.github.composefluent.winrt.authoring", "WinRTAuthoringHostExports")
        val hostExportRegistryClass = ClassName("io.github.composefluent.winrt.authoring", "WinRTAuthoringHostExportRegistry")
        val hostExportsBuilder = TypeSpec.objectBuilder(authoringHostExportsClassName.simpleName)
            .addModifiers(KModifier.INTERNAL)
            .addSuperinterface(hostExportsInterface)
            .addInitializerBlock(
                CodeBlock.of(
                    "%T.registerHostExports(%S, this)\n",
                    hostExportRegistryClass,
                    authoringHostExportsClassName.canonicalName,
                ),
            )
            .addFunction(
                FunSpec.builder("registerActivationFactories")
                    .addModifiers(KModifier.OVERRIDE)
                    .addStatement("%T.register()", authoringServerActivationFactoriesClassName)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("dllGetActivationFactory")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("activatableClassId", RAW_ADDRESS_CLASS_NAME)
                    .addParameter("factoryOut", RAW_ADDRESS_CLASS_NAME)
                    .returns(Int::class)
                    .addStatement("registerActivationFactories()")
                    .addStatement("return %T.dllGetActivationFactory(activatableClassId, factoryOut)", hostBridgeClass)
                    .build(),
            )
            .addFunction(
                FunSpec.builder("dllCanUnloadNow")
                    .returns(Int::class)
                    .addStatement("return %T.dllCanUnloadNow()", hostBridgeClass)
                    .build(),
            )
        if (emitJvmAddressWrappers) {
            hostExportsBuilder
                .addFunction(
                    FunSpec.builder("dllGetActivationFactoryAddress")
                        .addAnnotation(ClassName("kotlin.jvm", "JvmStatic"))
                        .addParameter("activatableClassId", Long::class)
                        .addParameter("factoryOut", Long::class)
                        .returns(Int::class)
                        .addStatement("return dllGetActivationFactory(%T(activatableClassId), %T(factoryOut))", RAW_ADDRESS_CLASS_NAME, RAW_ADDRESS_CLASS_NAME)
                        .build(),
                )
                .addFunction(
                    FunSpec.builder("dllCanUnloadNowAddress")
                        .addAnnotation(ClassName("kotlin.jvm", "JvmStatic"))
                        .returns(Int::class)
                        .addStatement("return dllCanUnloadNow()")
                        .build(),
                )
        }
        val fileBuilder = supportFileSpec(authoringHostExportsClassName.simpleName)
            .addType(
                hostExportsBuilder.build(),
            )
        if (!emitJvmAddressWrappers) {
            fileBuilder
                .addFunction(
                    FunSpec.builder("dllGetActivationFactoryExport")
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                                .addMember("%T::class", ClassName("kotlin.experimental", "ExperimentalNativeApi"))
                                .build(),
                        )
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("kotlin.native", "CName"))
                                .addMember("%S", "DllGetActivationFactory")
                                .build(),
                        )
                        .addParameter("activatableClassId", RAW_ADDRESS_CLASS_NAME)
                        .addParameter("factoryOut", RAW_ADDRESS_CLASS_NAME)
                        .returns(Int::class)
                        .addStatement("return %T.dllGetActivationFactory(activatableClassId, factoryOut)", authoringHostExportsClassName)
                        .build(),
                )
                .addFunction(
                    FunSpec.builder("dllCanUnloadNowExport")
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
                                .addMember("%T::class", ClassName("kotlin.experimental", "ExperimentalNativeApi"))
                                .build(),
                        )
                        .addAnnotation(
                            AnnotationSpec.builder(ClassName("kotlin.native", "CName"))
                                .addMember("%S", "DllCanUnloadNow")
                                .build(),
                        )
                        .returns(Int::class)
                        .addStatement("return %T.dllCanUnloadNow()", authoringHostExportsClassName)
                        .build(),
                )
        }
        return supportFile("${authoringHostExportsClassName.simpleName}.kt", fileBuilder.build())
    }

    private fun authoringServerActivationFactoryClass(
        plan: KotlinTypeProjectionPlan,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        plansByQualifiedName: Map<String, KotlinTypeProjectionPlan>,
    ): TypeSpec {
        val projectedType = ClassName(plan.packageName, plan.type.name)
        val isActivatable = authoredRuntimeClassHasDefaultActivation(plan, semanticHelpers)
        val factory = plan.factorySurfaceDescriptor ?: semanticHelpers.factorySurfaceDescriptor(plan.type)
        val factoryInterfaces = authoringServerFactoryInterfaces(factory)
        return TypeSpec.classBuilder(authoringServerActivationFactoryClassName(plan))
            .addModifiers(KModifier.INTERNAL)
            .addSuperinterface(WINRT_ACTIVATION_FACTORY_CLASS_NAME)
            .addFunction(authoringServerActivationFactoryInterfacesFunction(plan, factoryInterfaces, plansByQualifiedName))
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
                                ClassName("kotlin", "UnsupportedOperationException"),
                                "Runtime class '${plan.type.qualifiedName}' does not expose default activation.",
                            )
                        }
                    }
                    .build(),
            )
            .build()
    }

    private fun authoringServerActivationFactoryInterfacesFunction(
        plan: KotlinTypeProjectionPlan,
        factoryInterfaces: List<AuthoringServerFactoryInterface>,
        plansByQualifiedName: Map<String, KotlinTypeProjectionPlan>,
    ): FunSpec {
        val code = CodeBlock.builder()
        if (factoryInterfaces.isEmpty()) {
            code.add("return emptyList()\n")
        } else {
            code.add("return listOf(\n")
            code.indent()
            factoryInterfaces.forEach { factoryInterface ->
                val interfaceName = factoryInterface.name
                val rawInterfaceName = interfaceName.substringBefore('<')
                val interfacePlan = plansByQualifiedName[rawInterfaceName]
                    ?: throw IllegalArgumentException(
                        "Support renderer requires authored runtime class ${plan.type.qualifiedName} factory interface $rawInterfaceName to have a projection plan before rendering server activation factory definitions.",
                    )
                code.add("%T(\n", WINRT_INSPECTABLE_INTERFACE_DEFINITION_CLASS_NAME)
                code.indent()
                code.add("interfaceId = %L,\n", authoringInterfaceIdCode(interfaceName, plan))
                code.add("methods = %L,\n", authoringServerFactoryInterfaceMethodsCode(plan, interfacePlan, factoryInterface.kinds))
                code.unindent()
                code.add("),\n")
            }
            code.unindent()
            code.add(")\n")
        }
        return FunSpec.builder("factoryInterfaces")
            .returns(List::class.asClassName().parameterizedBy(WINRT_INSPECTABLE_INTERFACE_DEFINITION_CLASS_NAME))
            .addCode(code.build())
            .build()
    }

    private fun authoringServerFactoryInterfaceMethodsCode(
        runtimeClassPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
        kinds: Set<AuthoringServerFactoryInterfaceKind>,
    ): CodeBlock {
        if (interfacePlan.instanceMemberBindings.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }
        val slotOrder = interfacePlan.abiSlotBindings.associate { it.constantName to it.slot }
        interfacePlan.instanceMemberBindings.forEach { binding ->
            require(slotOrder.containsKey(binding.slotConstantName)) {
                "Support renderer requires authored factory binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to carry ABI slot metadata before rendering server activation factory definitions."
            }
            require(
                interfacePlan.type.methods
                    .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
                    .any { method -> binding.bindingName == method.abiSlotConstantName(interfacePlan.type.methods) },
            ) {
                "Support renderer requires authored factory binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to map to a factory method body before rendering server activation factory definitions."
            }
            authoredCcwBindingUnsupportedReason(typeRenderer, interfacePlan.type, binding)?.let { reason ->
                throw IllegalArgumentException(
                    "Support renderer requires authored factory binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to use supported authored ABI metadata before rendering server activation factory definitions; unsupported $reason.",
                )
            }
        }
        val methods = interfacePlan.instanceMemberBindings.sortedWith(
            compareBy<KotlinProjectionInstanceMemberBinding> { slotOrder.getValue(it.slotConstantName) }
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
            code.add("%L", authoringServerFactoryMethodHandlerCode(runtimeClassPlan, interfacePlan, binding, kinds))
            code.unindent()
            code.add("},\n")
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringServerFactoryMethodHandlerCode(
        runtimeClassPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
        binding: KotlinProjectionInstanceMemberBinding,
        kinds: Set<AuthoringServerFactoryInterfaceKind>,
    ): CodeBlock {
        val method = interfacePlan.type.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .firstOrNull { method -> binding.bindingName == method.abiSlotConstantName(interfacePlan.type.methods) }
            ?: error(
                "Support renderer requires authored factory binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to map to a factory method body before rendering server activation factory definitions.",
            )
        authoredCcwBindingUnsupportedReason(typeRenderer, interfacePlan.type, binding)?.let { reason ->
            error(
                "Support renderer requires authored factory binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to be validated before rendering server activation factory definitions; unsupported $reason.",
            )
        }
        val isComposableFactoryMethod =
            AuthoringServerFactoryInterfaceKind.Composable in kinds &&
                method.returnType.typeName == runtimeClassPlan.type.qualifiedName &&
                method.parameters.size >= 2 &&
                method.parameters.takeLast(2).all { parameter -> isWinRTObjectTypeName(parameter.type.typeName) }
        val kind = when {
            isComposableFactoryMethod -> AuthoringServerFactoryInterfaceKind.Composable
            AuthoringServerFactoryInterfaceKind.Constructor in kinds -> AuthoringServerFactoryInterfaceKind.Constructor
            AuthoringServerFactoryInterfaceKind.Static in kinds -> AuthoringServerFactoryInterfaceKind.Static
            else -> error(
                "Support renderer requires authored factory binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to map to an activatable, static, or composable factory kind before rendering server activation factory definitions.",
            )
        }
        val projectedType = ClassName(runtimeClassPlan.packageName, runtimeClassPlan.type.name)
        val receiveArrayParameterName = method.receiveArrayResultParameter()?.name
        val code = CodeBlock.builder()
        code.add("try {\n")
        code.indent()
        binding.parameterBindings.forEachIndexed { index, parameter ->
            if (parameter.name == receiveArrayParameterName || (kind == AuthoringServerFactoryInterfaceKind.Composable && index >= binding.parameterBindings.size - 2)) {
                return@forEachIndexed
            }
            code.add(
                "val %L = %L\n",
                authoringCcwLocalParameterName(index),
                authoringCcwDecodeArgumentCode(parameter, authoringCcwParameterRawIndex(binding.parameterBindings, index)),
            )
        }
        val invocation = authoringServerFactoryInvocationCode(projectedType, method, binding, receiveArrayParameterName, kind)
        val returnBinding = authoringCcwProjectedReturnBinding(interfacePlan, binding) ?: binding.returnBinding
        if (kind == AuthoringServerFactoryInterfaceKind.Composable) {
            code.add("val __result = %L\n", invocation)
            val innerRawIndex = authoringCcwParameterRawIndex(binding.parameterBindings, binding.parameterBindings.lastIndex)
            val instanceRawIndex = authoringCcwAbiArgumentCount(binding.parameterBindings)
            code.add(
                "%T.writePointer(rawArgs[%L] as %T, %T.nullPointer)\n",
                PLATFORM_ABI_CLASS_NAME,
                innerRawIndex,
                RAW_ADDRESS_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
            code.add("%L\n", authoringCcwWriteReturnCode(returnBinding, "rawArgs[$instanceRawIndex] as RawAddress", "__result"))
            code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
        } else if (returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
            code.add("%L\n", invocation)
            code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
        } else {
            code.add("val __result = %L\n", invocation)
            val returnRawIndex = authoringCcwAbiArgumentCount(binding.parameterBindings)
            code.add("%L\n", authoringCcwWriteReturnCode(returnBinding, "rawArgs[$returnRawIndex] as RawAddress", "__result"))
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

    private fun authoringServerFactoryInvocationCode(
        projectedType: ClassName,
        method: WinRTMethodDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
        receiveArrayParameterName: String?,
        kind: AuthoringServerFactoryInterfaceKind,
    ): CodeBlock {
        val target = when (kind) {
            AuthoringServerFactoryInterfaceKind.Constructor -> CodeBlock.of("%T", projectedType)
            AuthoringServerFactoryInterfaceKind.Static -> CodeBlock.of("%T.%L", projectedType, method.projectedMethodName())
            AuthoringServerFactoryInterfaceKind.Composable -> CodeBlock.of("%T", projectedType)
        }
        val code = CodeBlock.builder()
        code.add("%L(", target)
        binding.parameterBindings
            .withIndex()
            .filterNot { (_, parameter) -> parameter.name == receiveArrayParameterName }
            .let { parameters ->
                if (kind == AuthoringServerFactoryInterfaceKind.Composable) {
                    parameters.dropLast(2)
                } else {
                    parameters
                }
            }
            .forEachIndexed { argumentIndex, (parameterIndex, _) ->
                if (argumentIndex > 0) {
                    code.add(", ")
                }
                code.add("%L", authoringCcwLocalParameterName(parameterIndex))
            }
        code.add(")")
        return code.build()
    }

    private fun authoringServerActivationFactoryRegisterFunction(
        entries: List<KotlinTypeProjectionPlan>,
        authoringModuleActivationFactoryPlanClassName: ClassName,
    ): FunSpec {
        val code = CodeBlock.builder()
        code.add("%T.registerModuleActivationFactories(\n", authoringModuleActivationFactoryPlanClassName)
        code.indent()
        code.add("createFactory = { entry ->\n")
        code.indent()
        code.add("when (entry.runtimeClassName) {\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            code.add(
                "%S -> run {\n",
                plan.type.qualifiedName,
            )
            code.indent()
            code.add("val __factory = %T()\n", ClassName(SUPPORT_PACKAGE, authoringServerActivationFactoryClassName(plan)))
            code.add(
                "%T.createCCWForActivationFactory(__factory, factoryInterfaces = __factory.factoryInterfaces(), interfaceId = %T.IActivationFactory)\n",
                COM_WRAPPERS_SUPPORT_CLASS_NAME,
                IID_CLASS_NAME,
            )
            code.unindent()
            code.add("}\n")
        }
        code.add("else -> error(%S + entry.runtimeClassName)\n", "No authored activation factory for runtime class: ")
        code.unindent()
        code.add("}\n")
        code.unindent()
        code.add("},\n")
        code.unindent()
        code.add(")\n")
        return FunSpec.builder("register")
            .addCode(code.build())
            .build()
    }

    private fun authoringServerActivationFactoryClassName(plan: KotlinTypeProjectionPlan): String =
        "_ServerActivationFactory_" + plan.type.qualifiedName
            .replace('.', '_')
            .replace('`', '_')

    private fun authoringServerFactoryInterfaces(
        factory: WinRTFactorySurfaceDescriptor,
    ): List<AuthoringServerFactoryInterface> =
        (factory.constructorFactories.map {
            AuthoringServerFactoryInterface(it, setOf(AuthoringServerFactoryInterfaceKind.Constructor))
        } + factory.staticMemberTargets.map {
            AuthoringServerFactoryInterface(it, setOf(AuthoringServerFactoryInterfaceKind.Static))
        } + factory.composableFactories.map {
            AuthoringServerFactoryInterface(it, setOf(AuthoringServerFactoryInterfaceKind.Composable))
        })
            .groupBy(AuthoringServerFactoryInterface::name)
            .map { (name, entries) ->
                AuthoringServerFactoryInterface(
                    name = name,
                    kinds = entries.flatMap { it.kinds }.toSet(),
                )
            }
            .sortedBy { it.name }

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
                    .addCode(
                        CodeBlock.of(
                            """
                            return %T.findObject(%T.fromRawComPtr(instance.pointer), %T::class)
                                ?: error(%S)
                            """.trimIndent() + "\n",
                            COM_WRAPPERS_SUPPORT_CLASS_NAME,
                            PLATFORM_ABI_CLASS_NAME,
                            projectedType,
                            "Authored ABI instance for ${plan.type.qualifiedName} is not backed by a registered Kotlin authored object.",
                        ),
                    )
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
            ?.let { authoringInterfaceIdCode(it, plan) }
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
                    .addCode("return marshaler?.pointer?.let(%T::fromRawComPtr) ?: %T.nullPointer\n", PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME)
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
                                %T.fromRawComPtr(marshaler.getRefPointer())
                            } finally {
                                marshaler.close()
                            }
                            """.trimIndent() + "\n",
                            PLATFORM_ABI_CLASS_NAME,
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
            .addFunction(authoringAbiClassArrayMarshalerFunction(plan, projectedType, wrapperType))
            .addFunction(authoringAbiClassCreateMarshalerArrayFunction(projectedType))
            .addFunction(authoringAbiClassGetAbiArrayFunction())
            .addFunction(authoringAbiClassFromAbiArrayFunction(projectedType))
            .addFunction(authoringAbiClassCopyAbiArrayFunction(projectedType))
            .addFunction(authoringAbiClassFromManagedArrayFunction(projectedType))
            .addFunction(authoringAbiClassDisposeMarshalerArrayFunction())
            .addFunction(authoringAbiClassDisposeAbiArrayFunction())
            .build()
    }

    private fun authoringAbiClassArrayMarshalerFunction(
        plan: KotlinTypeProjectionPlan,
        projectedType: ClassName,
        wrapperType: ClassName,
    ): FunSpec =
        FunSpec.builder("arrayMarshaler")
            .addModifiers(KModifier.PRIVATE)
            .returns(MARSHALER_CLASS_NAME.parameterizedBy(projectedType))
            .addCode(
                CodeBlock.of(
                    """
                    return %T.interfaceType(
                        %T(projectedTypeName, defaultInterfaceId),
                        %T::class,
                    ) { value ->
                        when (value) {
                            is %T -> value
                            is %T -> %T.fromAbi(%T.fromRawComPtr(value.nativeObject.pointer))
                                ?: error(%S)
                            else -> error(%S)
                        }
                    }
                    """.trimIndent() + "\n",
                    MARSHALER_CLASS_NAME,
                    WINRT_TYPE_HANDLE_CLASS_NAME,
                    projectedType,
                    projectedType,
                    IWINRT_OBJECT_CLASS_NAME,
                    wrapperType,
                    PLATFORM_ABI_CLASS_NAME,
                    "Could not wrap ABI instance for authored type ${plan.type.qualifiedName}.",
                    "Could not marshal ABI instance for authored type ${plan.type.qualifiedName}.",
                ),
            )
            .build()

    private fun authoringAbiClassCreateMarshalerArrayFunction(projectedType: ClassName): FunSpec =
        FunSpec.builder("CreateMarshalerArray")
            .addParameter("values", Array::class.asClassName().parameterizedBy(projectedType.copy(nullable = true)).copy(nullable = true))
            .returns(WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true))
            .addCode("return arrayMarshaler().createMarshalerArray(values)\n")
            .build()

    private fun authoringAbiClassGetAbiArrayFunction(): FunSpec =
        FunSpec.builder("GetAbiArray")
            .addParameter("marshaler", WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true))
            .returns(WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true))
            .addCode("return marshaler\n")
            .build()

    private fun authoringAbiClassFromAbiArrayFunction(projectedType: ClassName): FunSpec =
        FunSpec.builder("FromAbiArray")
            .addParameter("length", Int::class)
            .addParameter("data", RAW_ADDRESS_CLASS_NAME)
            .returns(List::class.asClassName().parameterizedBy(projectedType.copy(nullable = true)).copy(nullable = true))
            .addCode("return arrayMarshaler().fromAbiArray(length, data)\n")
            .build()

    private fun authoringAbiClassCopyAbiArrayFunction(projectedType: ClassName): FunSpec =
        FunSpec.builder("CopyAbiArray")
            .addParameter("values", Array::class.asClassName().parameterizedBy(projectedType.copy(nullable = true)))
            .addParameter("marshaler", WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true))
            .addCode(
                CodeBlock.of(
                    """
                    arrayMarshaler().fromAbiArray(marshaler?.length ?: 0, marshaler?.data ?: %T.nullPointer)
                        ?.forEachIndexed { index, value ->
                            values[index] = value
                        }
                    """.trimIndent() + "\n",
                    PLATFORM_ABI_CLASS_NAME,
                ),
            )
            .build()

    private fun authoringAbiClassFromManagedArrayFunction(projectedType: ClassName): FunSpec =
        FunSpec.builder("FromManagedArray")
            .addParameter("values", Array::class.asClassName().parameterizedBy(projectedType.copy(nullable = true)).copy(nullable = true))
            .returns(WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true))
            .addCode("return arrayMarshaler().fromManagedArray(values)\n")
            .build()

    private fun authoringAbiClassDisposeMarshalerArrayFunction(): FunSpec =
        FunSpec.builder("DisposeMarshalerArray")
            .addParameter("marshaler", WINRT_ABI_ARRAY_CLASS_NAME.copy(nullable = true))
            .addCode("arrayMarshaler().disposeMarshalerArray(marshaler)\n")
            .build()

    private fun authoringAbiClassDisposeAbiArrayFunction(): FunSpec =
        FunSpec.builder("DisposeAbiArray")
            .addParameter("length", Int::class)
            .addParameter("data", RAW_ADDRESS_CLASS_NAME)
            .addCode("arrayMarshaler().disposeAbiArray(length, data)\n")
            .build()

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
        inventory: WinRTMetadataProjectionInventory,
        plans: List<KotlinTypeProjectionPlan>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): KotlinProjectionFile? {
        if (!inventory.helperOutputs.authoringMetadataTypeMappingHelperRequired) {
            return null
        }
        val ccwEntryTypeNames = authoringEntryTypeNames(inventory, authoredRuntimeClassNames)
        val entries = plans
            .filter { it.type.kind == WinRTTypeKind.RuntimeClass && it.type.qualifiedName in ccwEntryTypeNames }
            .filterNot { semanticHelpers.isStatic(it.type) }
        if (entries.isEmpty()) {
            return null
        }
        val fileBuilder = supportFileSpec("WinRTAuthoringCcwFactories")
            .addImport("io.github.composefluent.winrt.runtime", "abiLayout")
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
            .map { it.interfaceName }
            .distinct()
            .sorted()
            .filterNot(::isRuntimeAppendedAuthoringInterfaceName)
            .forEach { interfaceName ->
                val rawInterfaceName = interfaceName.substringBefore('<')
                val interfacePlan = plansByQualifiedName[rawInterfaceName]
                    ?: throw IllegalArgumentException(
                        "Support renderer requires authored runtime class ${plan.type.qualifiedName} CCW interface $rawInterfaceName to have a projection plan before rendering authoring CCW definitions.",
                    )
                code.add("%T(\n", WINRT_INSPECTABLE_INTERFACE_DEFINITION_CLASS_NAME)
                code.indent()
                code.add("interfaceId = %L,\n", authoringInterfaceIdCode(interfaceName, plan))
                code.add("methods = %L,\n", authoringCcwInterfaceMethodsCode(plan, interfacePlan))
                code.unindent()
                code.add("),\n")
            }
        code.unindent()
        code.add("),\n")
        code.add(
            "defaultInterfaceId = %L,\n",
            defaultInterface
                ?.let { authoringInterfaceIdCode(it, plan) }
                ?: CodeBlock.of("%T.IInspectable", IID_CLASS_NAME),
        )
        code.add("runtimeClassName = %S,\n", plan.type.qualifiedName)
        code.add(
            "queryInterfaceFallback = { obj, requestedInterfaceId -> %L(obj, requestedInterfaceId) },\n",
            authoringCcwQueryInterfaceFunctionName(plan),
        )
        code.unindent()
        code.add(")\n")
        return code.build()
    }

    private fun authoringInterfaceIdCode(
        interfaceName: String,
        ownerPlan: KotlinTypeProjectionPlan,
    ): CodeBlock {
        val rawInterfaceName = interfaceName.substringBefore('<')
        runtimeAppendedAuthoringInterfaceIdCode(rawInterfaceName)?.let { return it }
        if ('<' !in interfaceName) {
            return CodeBlock.of("%T.Metadata.IID", projectionClassNameForQualifiedName(rawInterfaceName))
        }
        val binding = typeRenderer.renderAbiTypeBinding(
            typeName = interfaceName,
            typesByQualifiedName = ownerPlan.typesByQualifiedName,
            currentNamespace = ownerPlan.type.namespace,
        )
        val signature = typeRenderer.abiTypeSignature(binding)
            ?: throw IllegalArgumentException(
                "Support renderer requires authored interface $interfaceName to have a renderable type signature before rendering authoring support interface IDs.",
            )
        return CodeBlock.of("%T.createFromSignature(%L)", PARAMETERIZED_INTERFACE_ID_CLASS_NAME, signature)
    }

    private fun isRuntimeAppendedAuthoringInterfaceName(interfaceName: String): Boolean =
        interfaceName.substringBefore('<') == WINDOWS_FOUNDATION_ISTRINGABLE_TYPE_NAME

    private fun runtimeAppendedAuthoringInterfaceIdCode(rawInterfaceName: String): CodeBlock? =
        when (rawInterfaceName) {
            WINDOWS_FOUNDATION_ISTRINGABLE_TYPE_NAME -> CodeBlock.of("%T.IStringable", IID_CLASS_NAME)
            else -> null
        }

    private fun authoringCcwQueryInterfaceFunction(plan: KotlinTypeProjectionPlan): FunSpec {
        return FunSpec.builder(authoringCcwQueryInterfaceFunctionName(plan))
            .addModifiers(KModifier.PRIVATE)
            .addParameter("value", ANY)
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
                    val winRTObject = value as? %T ?: return null
                    return winRTObject.nativeObject
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

    private fun authoringCcwInterfaceMethodsCode(
        runtimeClassPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
    ): CodeBlock {
        if (interfacePlan.instanceMemberBindings.isEmpty()) {
            return CodeBlock.of("emptyList()")
        }
        val slotOrder = interfacePlan.abiSlotBindings.associate { it.constantName to it.slot }
        interfacePlan.instanceMemberBindings.forEach { binding ->
            require(slotOrder.containsKey(binding.slotConstantName)) {
                "Support renderer requires authored CCW binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to carry ABI slot metadata before rendering authoring CCW definitions."
            }
            require(
                authoredCcwBindingHasMemberBody(interfacePlan.type, binding),
            ) {
                "Support renderer requires authored CCW binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to map to an authored method, property, or event body before rendering authoring CCW definitions."
            }
        }
        val methods = interfacePlan.instanceMemberBindings.sortedWith(
            compareBy<KotlinProjectionInstanceMemberBinding> { slotOrder.getValue(it.slotConstantName) }
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
            code.add("%L", authoringCcwMethodHandlerCode(runtimeClassPlan, interfacePlan, binding))
            code.unindent()
            code.add("},\n")
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoredCcwBindingHasMemberBody(
        interfaceType: WinRTTypeDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): Boolean =
        interfaceType.events.any { event ->
            binding.bindingName == "${event.name.uppercase()}_ADD_SLOT" ||
                binding.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
        } ||
            interfaceType.methods
                .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
                .any { method -> binding.bindingName == method.abiSlotConstantName(interfaceType.methods) } ||
            interfaceType.properties.any { property ->
                binding.bindingName == "${property.name.uppercase()}_GETTER_SLOT" ||
                    binding.bindingName == "${property.name.uppercase()}_SETTER_SLOT"
            }

    private fun authoringCcwMethodSignatureCode(binding: KotlinProjectionInstanceMemberBinding): CodeBlock {
        val explicitKinds = binding.parameterBindings.flatMap(::authoringCcwParameterAbiValueKindCodes) +
            authoringCcwReturnAbiValueKindCodes(binding.returnBinding)
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

    private fun authoringCcwParameterAbiValueKindCodes(parameter: KotlinProjectionAbiParameterBinding): List<CodeBlock> =
        if (parameter.typeBinding.kind == KotlinProjectionAbiValueKind.Array) {
            when (parameter.category) {
                WinRTMetadataParameterCategory.PassArray,
                WinRTMetadataParameterCategory.FillArray -> listOf(
                    CodeBlock.of("%T.Int32", COM_ABI_VALUE_KIND_CLASS_NAME),
                    CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME),
                )
                WinRTMetadataParameterCategory.ReceiveArray -> listOf(
                    CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME),
                    CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME),
                )
                else -> listOf(CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME))
            }
        } else {
            listOf(authoringCcwAbiValueKindCode(parameter.typeBinding))
        }

    private fun authoringCcwReturnAbiValueKindCodes(binding: KotlinProjectionAbiTypeBinding): List<CodeBlock> =
        when (binding.kind) {
            KotlinProjectionAbiValueKind.Unit -> emptyList()
            KotlinProjectionAbiValueKind.Array -> listOf(
                CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME),
                CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME),
            )
            else -> listOf(CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME))
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
        KotlinProjectionAbiValueKind.Enum ->
            binding.enumUnderlyingType?.let(::integralComAbiValueKindCode)
                ?: CodeBlock.of("%T.Int32", COM_ABI_VALUE_KIND_CLASS_NAME)
        KotlinProjectionAbiValueKind.Struct ->
            if (binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken") {
                CodeBlock.of("%T.Int64", COM_ABI_VALUE_KIND_CLASS_NAME)
            } else customStructAbi(binding)
                ?.let(::authoringCcwCustomStructAbiValueKindCode)
                ?: typeRenderer.nativeStructClassName(binding)
                    ?.let { structType -> CodeBlock.of("%T.Struct(%T.Metadata.layout.abiLayout)", COM_ABI_VALUE_KIND_CLASS_NAME, structType) }
                ?: CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME)
        else -> CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME)
    }

    private fun authoringCcwCustomStructAbiValueKindCode(customAbi: KotlinProjectionCustomStructAbi): CodeBlock =
        customAbi.abiArgumentKind
            ?.let(::authoringCcwComArgumentKindCode)
            ?: customAbi.abiLayoutExpression
                ?.let { layout -> CodeBlock.of("%T.Struct(%L)", COM_ABI_VALUE_KIND_CLASS_NAME, layout) }
            ?: CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME)

    private fun authoringCcwComArgumentKindCode(kind: KotlinProjectionComArgumentKind): CodeBlock =
        when (kind) {
            KotlinProjectionComArgumentKind.Pointer -> CodeBlock.of("%T.Pointer", COM_ABI_VALUE_KIND_CLASS_NAME)
            KotlinProjectionComArgumentKind.Int8 -> CodeBlock.of("%T.Int8", COM_ABI_VALUE_KIND_CLASS_NAME)
            KotlinProjectionComArgumentKind.Int16 -> CodeBlock.of("%T.Int16", COM_ABI_VALUE_KIND_CLASS_NAME)
            KotlinProjectionComArgumentKind.Int32 -> CodeBlock.of("%T.Int32", COM_ABI_VALUE_KIND_CLASS_NAME)
            KotlinProjectionComArgumentKind.Int64 -> CodeBlock.of("%T.Int64", COM_ABI_VALUE_KIND_CLASS_NAME)
            KotlinProjectionComArgumentKind.Float -> CodeBlock.of("%T.Float", COM_ABI_VALUE_KIND_CLASS_NAME)
            KotlinProjectionComArgumentKind.Double -> CodeBlock.of("%T.Double", COM_ABI_VALUE_KIND_CLASS_NAME)
        }

    private fun authoringCcwMethodHandlerCode(
        runtimeClassPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        val interfaceType = interfacePlan.type
        val event = interfaceType.events.firstOrNull { event ->
            binding.bindingName == "${event.name.uppercase()}_ADD_SLOT" ||
                binding.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
        }
        return if (event != null) {
            authoringCcwEventHandlerCode(event, binding)
        } else if (authoredCcwBindingIsSupported(typeRenderer, interfacePlan.type, binding)) {
            authoringCcwOrdinaryMemberHandlerCode(runtimeClassPlan, interfacePlan, binding)
        } else {
            val reason = authoredCcwBindingUnsupportedReason(typeRenderer, interfacePlan.type, binding)
                ?: "unsupported authored ABI shape"
            error(
                "Support renderer requires authored CCW binding ${interfacePlan.type.qualifiedName}.${binding.bindingName} to be validated before rendering authoring CCW definitions; unsupported $reason.",
            )
        }
    }
    private fun authoringCcwEventHandlerCode(
        event: WinRTEventDefinition,
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
        runtimeClassPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        val interfaceType = interfacePlan.type
        val receiveArrayParameterName = authoringCcwReceiveArrayReturnParameter(interfaceType, binding)?.name
        val code = CodeBlock.builder()
        code.add("try {\n")
        code.indent()
        binding.parameterBindings.forEachIndexed { index, parameter ->
            if (parameter.name == receiveArrayParameterName) {
                return@forEachIndexed
            }
            code.add(
                "val %L = %L\n",
                authoringCcwLocalParameterName(index),
                authoringCcwDecodeArgumentCode(parameter, authoringCcwParameterRawIndex(binding.parameterBindings, index)),
            )
        }
        val invocation = authoringCcwInvocationCode(runtimeClassPlan, interfaceType, binding)
        val returnBinding = authoringCcwProjectedReturnBinding(interfacePlan, binding) ?: binding.returnBinding
        if (returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
            code.add("%L\n", invocation)
            binding.parameterBindings.forEachIndexed { index, parameter ->
                authoringCcwPostInvocationParameterCode(
                    parameter,
                    authoringCcwParameterRawIndex(binding.parameterBindings, index),
                    authoringCcwLocalParameterName(index),
                )
                    ?.let { postInvocation -> code.add("%L\n", postInvocation) }
            }
            code.add("%T.S_OK.value\n", KNOWN_HRESULTS_CLASS_NAME)
        } else {
            code.add("val __result = %L\n", invocation)
            binding.parameterBindings.forEachIndexed { index, parameter ->
                authoringCcwPostInvocationParameterCode(
                    parameter,
                    authoringCcwParameterRawIndex(binding.parameterBindings, index),
                    authoringCcwLocalParameterName(index),
                )
                    ?.let { postInvocation -> code.add("%L\n", postInvocation) }
            }
            if (returnBinding.kind == KotlinProjectionAbiValueKind.Array) {
                val returnRawIndex = receiveArrayParameterName?.let { name ->
                    val parameterIndex = binding.parameterBindings.indexOfFirst { parameter -> parameter.name == name }
                    if (parameterIndex >= 0) authoringCcwParameterRawIndex(binding.parameterBindings, parameterIndex) else null
                } ?: authoringCcwAbiArgumentCount(binding.parameterBindings)
                code.add(
                    "%L\n",
                    authoringCcwWriteArrayReturnCode(
                        returnBinding,
                        "rawArgs[$returnRawIndex] as RawAddress",
                        "rawArgs[${returnRawIndex + 1}] as RawAddress",
                        "__result",
                    ),
                )
            } else {
                val returnRawIndex = authoringCcwAbiArgumentCount(binding.parameterBindings)
                code.add("%L\n", authoringCcwWriteReturnCode(returnBinding, "rawArgs[$returnRawIndex] as RawAddress", "__result"))
            }
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

    private fun authoringCcwProjectedReturnBinding(
        interfacePlan: KotlinTypeProjectionPlan,
        binding: KotlinProjectionInstanceMemberBinding,
    ): KotlinProjectionAbiTypeBinding? {
        val interfaceType = interfacePlan.type
        interfaceType.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .firstOrNull { method -> binding.bindingName == method.abiSlotConstantName(interfaceType.methods) }
            ?.let { method ->
                method.receiveArrayResultParameter()?.let { receiveArrayParameter ->
                    return planner.classifyAbiTypeBinding(receiveArrayParameter.typeName, interfaceType.namespace, interfacePlan.typesByQualifiedName)
                }
                return planner.classifyAbiTypeBinding(method.returnTypeName, interfaceType.namespace, interfacePlan.typesByQualifiedName)
            }
        interfaceType.properties.firstOrNull { property ->
            binding.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
        }?.let { property ->
            return planner.classifyAbiTypeBinding(property.typeName, interfaceType.namespace, interfacePlan.typesByQualifiedName)
        }
        return null
    }

    private fun authoringCcwInvocationCode(
        runtimeClassPlan: KotlinTypeProjectionPlan,
        interfaceType: WinRTTypeDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): CodeBlock {
        interfaceType.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .firstOrNull { method -> binding.bindingName == method.abiSlotConstantName(interfaceType.methods) }
            ?.let { method ->
                val receiveArrayParameterName = method.receiveArrayResultParameter()?.name
                val targetMethodName = if (runtimeClassPlan.requiresAuthoringInvokeBridge(binding.slotInterfaceQualifiedName)) {
                    authoringInvokeBridgeName(method)
                } else {
                    method.projectedMethodName()
                }
                return CodeBlock.builder()
                    .add("value.%L(", targetMethodName)
                    .apply {
                        binding.parameterBindings
                            .withIndex()
                            .filterNot { (_, parameter) -> parameter.name == receiveArrayParameterName }
                            .forEachIndexed { argumentIndex, (parameterIndex, _) ->
                                if (argumentIndex > 0) {
                                    add(", ")
                                }
                                add("%L", authoringCcwLocalParameterName(parameterIndex))
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
                val parameterIndex = binding.parameterBindings.indexOf(binding.parameterBindings.single())
                CodeBlock.of("value.%L = %L", propertyName, authoringCcwLocalParameterName(parameterIndex))
            }
        }
        return CodeBlock.of("error(%S)", "No authored member body for ${interfaceType.qualifiedName}.${binding.bindingName}")
    }

    private fun KotlinTypeProjectionPlan.requiresAuthoringInvokeBridge(interfaceName: String): Boolean {
        val rawName = interfaceName.substringBefore('<').removeSuffix("?")
        val qualifiedName = if ('.' in rawName) rawName else "${type.namespace}.$rawName"
        fun matches(candidate: String): Boolean {
            val candidateRawName = candidate.substringBefore('<').removeSuffix("?")
            return candidateRawName == rawName || candidateRawName == qualifiedName
        }
        return classMemberMergeDescriptor
            ?.interfaceDescriptors
            ?.any { descriptor ->
                matches(descriptor.interfaceTypeName) &&
                    (descriptor.isOverridableInterface || descriptor.isProtectedInterface)
            } == true ||
            type.implementedInterfaces.any { implementation ->
                matches(implementation.interfaceName) && implementation.isOverridable
            }
    }

    private fun authoringCcwReceiveArrayReturnParameter(
        interfaceType: WinRTTypeDefinition,
        binding: KotlinProjectionInstanceMemberBinding,
    ): io.github.composefluent.winrt.metadata.WinRTParameterDefinition? =
        interfaceType.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .firstOrNull { method -> binding.bindingName == method.abiSlotConstantName(interfaceType.methods) }
            ?.receiveArrayResultParameter()

    private fun authoringCcwAbiArgumentCount(parameters: List<KotlinProjectionAbiParameterBinding>): Int =
        parameters.sumOf(::authoringCcwAbiArgumentCount)

    private fun authoringCcwAbiArgumentCount(parameter: KotlinProjectionAbiParameterBinding): Int =
        if (parameter.typeBinding.kind == KotlinProjectionAbiValueKind.Array) 2 else 1

    private fun authoringCcwParameterRawIndex(
        parameters: List<KotlinProjectionAbiParameterBinding>,
        parameterIndex: Int,
    ): Int =
        parameters.take(parameterIndex).sumOf(::authoringCcwAbiArgumentCount)

    private fun authoringCcwLocalParameterName(parameterIndex: Int): String =
        "__arg$parameterIndex"

    private fun authoringCcwDecodeArgumentCode(
        parameter: KotlinProjectionAbiParameterBinding,
        rawIndex: Int,
    ): CodeBlock =
        if (parameter.typeBinding.kind == KotlinProjectionAbiValueKind.Array) {
            authoringCcwDecodeArrayArgumentCode(parameter, rawIndex)
        } else {
            authoringCcwDecodeArgumentCode(parameter.typeBinding, rawIndex)
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
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(binding)?.let { customAbi ->
                authoringCcwDecodeCustomStructArgumentCode(binding, customAbi, index)
            } ?: if (binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken") {
                CodeBlock.of("%T(rawArgs[%L] as Long)", EVENT_REGISTRATION_TOKEN_CLASS_NAME, index)
            } else {
                val structType = typeRenderer.nativeStructClassName(binding)
                if (structType == null) {
                    CodeBlock.of("error(%S)", "Unsupported authored ABI struct argument ${binding.describeAbiKind()}")
                } else {
                    CodeBlock.of("%T.Metadata.fromAbi(rawArgs[%L] as %T)", structType, index, RAW_ADDRESS_CLASS_NAME)
                }
            }
        KotlinProjectionAbiValueKind.Object -> CodeBlock.of("%T.fromAbi(rawArgs[%L] as %T)", WINRT_OBJECT_MARSHALLER_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        KotlinProjectionAbiValueKind.PropertyValue -> CodeBlock.of("%T.tryFromBorrowedAbi(rawArgs[%L] as %T)", WINRT_PROPERTY_VALUE_PROJECTION_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            customObjectAbi(binding)?.let { authoringCcwDecodeCustomObjectArgumentCode(binding, it, index) }
                ?: CodeBlock.of(
                    "%T.Metadata.wrap(%T(%T.toRawComPtr(rawArgs[%L] as %T)).asInspectable())",
                    typeRenderer.resolveTypeName(binding.resolvedTypeName),
                    IUNKNOWN_REFERENCE_CLASS_NAME,
                    PLATFORM_ABI_CLASS_NAME,
                    index,
                    RAW_ADDRESS_CLASS_NAME,
                )
        KotlinProjectionAbiValueKind.Delegate -> CodeBlock.of(
            "%T.Metadata.fromAbi(rawArgs[%L] as %T) ?: error(%S)",
            typeRenderer.resolveTypeName(binding.resolvedTypeName),
            index,
            RAW_ADDRESS_CLASS_NAME,
            "Authored delegate argument ${binding.resolvedTypeName} was null.",
        )
        KotlinProjectionAbiValueKind.GenericParameter -> CodeBlock.of(
            "%T.fromAbi<%T>(rawArgs[%L] as %T)",
            WINRT_GENERIC_PARAMETER_PROJECTION_CLASS_NAME,
            typeRenderer.resolveTypeName(binding.typeName),
            index,
            RAW_ADDRESS_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.Reference -> authoringCcwDecodeReferenceArgumentCode(
            binding = binding,
            index = index,
            projectionClass = WINRT_REFERENCE_PROJECTION_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.ReferenceArray -> authoringCcwDecodeReferenceArgumentCode(
            binding = binding,
            index = index,
            projectionClass = WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedMapView -> authoringCcwDecodeMappedCollectionArgumentCode(binding, index)
        KotlinProjectionAbiValueKind.MappedKeyValuePair -> authoringCcwDecodeMappedKeyValuePairArgumentCode(binding, index)
        KotlinProjectionAbiValueKind.MappedBindableIterable,
        KotlinProjectionAbiValueKind.MappedBindableVector,
        KotlinProjectionAbiValueKind.MappedBindableVectorView -> authoringCcwDecodeBindableCollectionArgumentCode(binding, index)
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> authoringCcwDecodeAsyncReferenceArgumentCode(binding, index)
        KotlinProjectionAbiValueKind.UnknownReference -> CodeBlock.of("%T(rawArgs[%L] as %T)", IUNKNOWN_REFERENCE_CLASS_NAME, index, RAW_ADDRESS_CLASS_NAME)
        KotlinProjectionAbiValueKind.InspectableReference -> CodeBlock.of(
            "%T(%T.toRawComPtr(rawArgs[%L] as %T)).asInspectable()",
            IUNKNOWN_REFERENCE_CLASS_NAME,
            PLATFORM_ABI_CLASS_NAME,
            index,
            RAW_ADDRESS_CLASS_NAME,
        )
        else -> CodeBlock.of("error(%S)", "Unsupported authored ABI argument ${binding.describeAbiKind()}")
    }

    private fun authoringCcwDecodeReferenceArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
        projectionClass: ClassName,
    ): CodeBlock {
        val interfaceId = typeRenderer.referenceInterfaceIdCode(binding)
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI reference argument ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "%T.fromAbi(rawArgs[%L] as %T, %L) as %T",
            projectionClass,
            index,
            RAW_ADDRESS_CLASS_NAME,
            interfaceId,
            typeRenderer.resolveTypeName(binding.typeName),
        )
    }

    private fun authoringCcwDecodeMappedCollectionArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock {
        val projectionClass = when (binding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable -> WINRT_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedVector -> WINRT_LIST_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedVectorView -> WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedMap -> WINRT_DICTIONARY_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedMapView -> WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME
            else -> return CodeBlock.of("error(%S)", "Unsupported authored ABI collection argument ${binding.describeAbiKind()}")
        }
        val adapterArguments = authoringCcwMappedCollectionAdapterArguments(binding)
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI collection argument ${binding.describeAbiKind()}")
        return CodeBlock.builder()
            .add("%T.fromAbi(rawArgs[%L] as %T", projectionClass, index, RAW_ADDRESS_CLASS_NAME)
            .apply { adapterArguments.forEach { adapter -> add(", %L", adapter) } }
            .add(") as %T", typeRenderer.resolveTypeName(binding.typeName))
            .build()
    }

    private fun authoringCcwDecodeMappedKeyValuePairArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock {
        val keyAdapter = typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.getOrNull(0) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair argument ${binding.describeAbiKind()}"))
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair argument ${binding.describeAbiKind()}")
        val valueAdapter = typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.getOrNull(1) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair argument ${binding.describeAbiKind()}"))
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair argument ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "%M(%L, %L).projectAbi(rawArgs[%L] as %T) as %T",
            WINRT_KEY_VALUE_PAIR_ADAPTER_FUNCTION_NAME,
            keyAdapter,
            valueAdapter,
            index,
            RAW_ADDRESS_CLASS_NAME,
            typeRenderer.resolveTypeName(binding.typeName),
        )
    }

    private fun authoringCcwDecodeBindableCollectionArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock {
        val projectionClass = when (binding.kind) {
            KotlinProjectionAbiValueKind.MappedBindableIterable -> WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVector -> WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVectorView -> WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME
            else -> return CodeBlock.of("error(%S)", "Unsupported authored ABI bindable collection argument ${binding.describeAbiKind()}")
        }
        return CodeBlock.of(
            "%T.fromAbi(rawArgs[%L] as %T) as %T",
            projectionClass,
            index,
            RAW_ADDRESS_CLASS_NAME,
            typeRenderer.resolveTypeName(binding.typeName),
        )
    }

    private fun authoringCcwDecodeAsyncReferenceArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock {
        val expression = typeRenderer.asyncReferenceExpression(
            returnBinding = binding,
            pointerExpression = CodeBlock.of("rawArgs[%L] as %T", index, RAW_ADDRESS_CLASS_NAME),
        ) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI async argument ${binding.describeAbiKind()}")
        return CodeBlock.of("%L", expression)
    }

    private fun authoringCcwDecodeCustomObjectArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        customAbi: KotlinProjectionCustomObjectAbi,
        index: Int,
    ): CodeBlock {
        val pointerExpression = CodeBlock.of("rawArgs[%L] as %T", index, RAW_ADDRESS_CLASS_NAME)
        val projectedType = typeRenderer.resolveTypeName(binding.resolvedTypeName).copy(nullable = false)
        val expression = if (customAbi.fromAbiFunctionName == "objectFromAbi") {
            CodeBlock.of(
                "%T.%L(%L, %T(%S, %T(%S)), %T::class)",
                WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                customAbi.fromAbiFunctionName,
                pointerExpression,
                WINRT_TYPE_HANDLE_CLASS_NAME,
                customAbi.typeHandleName,
                GUID_CLASS_NAME,
                customAbi.interfaceId.toString(),
                projectedType,
            )
        } else {
            CodeBlock.of(
                "%T.%L(%L)",
                WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
                customAbi.fromAbiFunctionName,
                pointerExpression,
            )
        }
        return if (binding.isNullableAbiTypeName) {
            expression
        } else {
            CodeBlock.of("%L ?: error(%S)", expression, "WINRT_E_NULL_AUTHORED_ABI_ARGUMENT")
        }
    }

    private fun authoringCcwDecodeCustomStructArgumentCode(
        binding: KotlinProjectionAbiTypeBinding,
        customAbi: KotlinProjectionCustomStructAbi,
        index: Int,
    ): CodeBlock {
        val carrierFunctionName = customAbi.fromAbiCarrierFunctionName
        val carrierKind = customAbi.abiArgumentKind
        if (carrierFunctionName != null && carrierKind != null) {
            return CodeBlock.of(
                "%T.%L(%L)",
                customAbi.helperTypeName,
                carrierFunctionName,
                authoringCcwDecodeComArgumentCarrierCode(carrierKind, index),
            )
        }
        if (customAbi.abiLayoutExpression != null) {
            return CodeBlock.of(
                "%T.%L(rawArgs[%L] as %T)",
                customAbi.helperTypeName,
                customAbi.fromAbiFunctionName,
                index,
                RAW_ADDRESS_CLASS_NAME,
            )
        }
        return CodeBlock.of("error(%S)", "Unsupported authored ABI custom struct argument ${binding.describeAbiKind()}")
    }

    private fun authoringCcwDecodeComArgumentCarrierCode(
        kind: KotlinProjectionComArgumentKind,
        index: Int,
    ): CodeBlock =
        when (kind) {
            KotlinProjectionComArgumentKind.Pointer -> CodeBlock.of("rawArgs[%L] as %T", index, RAW_ADDRESS_CLASS_NAME)
            KotlinProjectionComArgumentKind.Int8 -> CodeBlock.of("rawArgs[%L] as Byte", index)
            KotlinProjectionComArgumentKind.Int16 -> CodeBlock.of("rawArgs[%L] as Short", index)
            KotlinProjectionComArgumentKind.Int32 -> CodeBlock.of("rawArgs[%L] as Int", index)
            KotlinProjectionComArgumentKind.Int64 -> CodeBlock.of("rawArgs[%L] as Long", index)
            KotlinProjectionComArgumentKind.Float -> CodeBlock.of("rawArgs[%L] as Float", index)
            KotlinProjectionComArgumentKind.Double -> CodeBlock.of("rawArgs[%L] as Double", index)
        }

    private fun authoringCcwDecodeArrayArgumentCode(
        parameter: KotlinProjectionAbiParameterBinding,
        rawIndex: Int,
    ): CodeBlock {
        if (parameter.category == WinRTMetadataParameterCategory.ReceiveArray) {
            return CodeBlock.of("error(%S)", "Authored ReceiveArray parameter ${parameter.name} is not supported yet.")
        }
        val binding = parameter.typeBinding
        val elementBinding = binding.typeArguments.singleOrNull()
            ?: return CodeBlock.of("error(%S)", "Authored array parameter ${parameter.name} is missing an element binding.")
        typeRenderer.nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
            return CodeBlock.of(
                "run {\n·val __arrayLength = rawArgs[%L] as Int\n·val __arrayData = rawArgs[%L] as %T\n·val __arrayMarshaler = %L\n·(__arrayMarshaler.fromAbiArray(__arrayLength, __arrayData)?.toTypedArray() ?: emptyArray()) as %T\n}",
                rawIndex,
                rawIndex + 1,
                RAW_ADDRESS_CLASS_NAME,
                elementMarshaler,
                typeRenderer.resolveTypeName(binding.typeName),
            )
        }
        val elementRead = typeRenderer.nativeArrayElementReadCode(
            elementBinding = elementBinding,
            dataExpression = CodeBlock.of("__arrayData"),
            indexExpression = CodeBlock.of("__index"),
        ) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI array argument ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "run {\n·val __arrayLength = rawArgs[%L] as Int\n·val __arrayData = rawArgs[%L] as %T\n·Array(__arrayLength) { __index -> %L } as %T\n}",
            rawIndex,
            rawIndex + 1,
            RAW_ADDRESS_CLASS_NAME,
            elementRead,
            typeRenderer.resolveTypeName(binding.typeName),
        )
    }

    private fun authoringCcwPostInvocationParameterCode(
        parameter: KotlinProjectionAbiParameterBinding,
        rawIndex: Int,
        valueExpression: String,
    ): CodeBlock? {
        if (parameter.typeBinding.kind != KotlinProjectionAbiValueKind.Array ||
            parameter.category != WinRTMetadataParameterCategory.FillArray
        ) {
            return null
        }
        val elementBinding = parameter.typeBinding.typeArguments.singleOrNull() ?: return null
        typeRenderer.nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
            return CodeBlock.of(
                "run {\n·val __arrayMarshaler = %L\n·__arrayMarshaler.copyManagedArray(%L, rawArgs[%L] as %T)\n}",
                elementMarshaler,
                valueExpression,
                rawIndex + 1,
                RAW_ADDRESS_CLASS_NAME,
            )
        }
        val elementWrite = typeRenderer.nativeArrayElementWriteCode(
            elementBinding = elementBinding,
            dataExpression = CodeBlock.of("rawArgs[%L] as %T", rawIndex + 1, RAW_ADDRESS_CLASS_NAME),
            indexExpression = CodeBlock.of("__index"),
            valueExpression = CodeBlock.of("__element"),
        ) ?: return null
        return CodeBlock.of(
            "%L.forEachIndexed { __index, __element -> %L }",
            valueExpression,
            elementWrite,
        )
    }

    private fun authoringCcwDecodeEnumRawCode(
        binding: KotlinProjectionAbiTypeBinding,
        index: Int,
    ): CodeBlock =
        binding.enumUnderlyingType?.let { integralAbiCarrierExpression(it, CodeBlock.of("rawArgs[%L]", index)) }
            ?: CodeBlock.of("rawArgs[%L] as Int", index)

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
                    kind = binding.enumUnderlyingType
                        ?.let { integralAbiDescriptor(it).abiValueKind }
                        ?: KotlinProjectionAbiValueKind.Int32,
                    typeName = binding.typeName,
                ),
                outExpression,
                CodeBlock.of("%T.Metadata.toAbi(%L)", typeRenderer.resolveTypeName(binding.resolvedTypeName), valueExpression).toString(),
            ),
        )
        KotlinProjectionAbiValueKind.Struct ->
            customStructAbi(binding)?.let { customAbi ->
                CodeBlock.of("%T.%L(%L, %L)", customAbi.helperTypeName, customAbi.copyToFunctionName, valueExpression, outExpression)
            } ?: if (binding.resolvedTypeName == "Windows.Foundation.EventRegistrationToken") {
                CodeBlock.of("%T.Metadata.copyTo(%L, %L)", EVENT_REGISTRATION_TOKEN_CLASS_NAME, valueExpression, outExpression)
            } else {
                val structType = typeRenderer.nativeStructClassName(binding)
                if (structType == null) {
                    CodeBlock.of("error(%S)", "Unsupported authored ABI struct return ${binding.describeAbiKind()}")
                } else {
                    CodeBlock.of("%T.Metadata.copyTo(%L, %L)", structType, valueExpression, outExpression)
                }
            }
        KotlinProjectionAbiValueKind.Object -> CodeBlock.of(
            "%T.createMarshaler(%L).use { __returnMarshaler -> %T.writePointer(%L, __returnMarshaler.abi) }",
            WINRT_OBJECT_MARSHALLER_CLASS_NAME,
            valueExpression,
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
        )
        KotlinProjectionAbiValueKind.PropertyValue -> CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L))",
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            WINRT_PROPERTY_VALUE_PROJECTION_CLASS_NAME,
            valueExpression,
        )
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedMapView -> authoringCcwWriteMappedCollectionReturnCode(binding, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.MappedBindableIterable,
        KotlinProjectionAbiValueKind.MappedBindableVector,
        KotlinProjectionAbiValueKind.MappedBindableVectorView -> authoringCcwWriteBindableCollectionReturnCode(binding, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.MappedKeyValuePair -> authoringCcwWriteMappedKeyValuePairReturnCode(binding, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> authoringCcwWriteAsyncReferenceReturnCode(binding, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
            customObjectAbi(binding)?.let { authoringCcwWriteCustomObjectReturnCode(binding, it, outExpression, valueExpression) }
                ?: authoringCcwWriteObjectReturnCode(binding, outExpression, valueExpression)
        KotlinProjectionAbiValueKind.Reference -> authoringCcwWriteReferenceReturnCode(
            binding = binding,
            outExpression = outExpression,
            valueExpression = valueExpression,
            projectionClass = WINRT_REFERENCE_PROJECTION_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.ReferenceArray -> authoringCcwWriteReferenceReturnCode(
            binding = binding,
            outExpression = outExpression,
            valueExpression = valueExpression,
            projectionClass = WINRT_REFERENCE_ARRAY_PROJECTION_CLASS_NAME,
        )
        KotlinProjectionAbiValueKind.Delegate,
        KotlinProjectionAbiValueKind.GenericParameter,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference -> authoringCcwWriteObjectReturnCode(binding, outExpression, valueExpression)
        else -> CodeBlock.of("error(%S)", "Unsupported authored ABI return ${binding.describeAbiKind()}")
    }

    private fun authoringCcwWriteMappedCollectionReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val projectionClass = when (binding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable -> WINRT_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedVector -> WINRT_LIST_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedVectorView -> WINRT_READ_ONLY_LIST_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedMap -> WINRT_DICTIONARY_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedMapView -> WINRT_READ_ONLY_DICTIONARY_PROJECTION_CLASS_NAME
            else -> return CodeBlock.of("error(%S)", "Unsupported authored ABI collection return ${binding.describeAbiKind()}")
        }
        val adapterArguments = authoringCcwMappedCollectionAdapterArguments(binding)
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI collection return ${binding.describeAbiKind()}")
        return CodeBlock.builder()
            .add("%T.writePointer(%L, %T.fromManaged(%L", PLATFORM_ABI_CLASS_NAME, outExpression, projectionClass, valueExpression)
            .apply { adapterArguments.forEach { adapter -> add(", %L", adapter) } }
            .add("))")
            .build()
    }

    private fun authoringCcwMappedCollectionAdapterArguments(
        binding: KotlinProjectionAbiTypeBinding,
    ): List<CodeBlock>? =
        when (binding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedVectorView ->
                listOf(typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.singleOrNull() ?: return null) ?: return null)
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedMapView ->
                listOf(
                    typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.getOrNull(0) ?: return null) ?: return null,
                    typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.getOrNull(1) ?: return null) ?: return null,
                )
            else -> null
        }

    private fun authoringCcwWriteMappedKeyValuePairReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val keyAdapter = typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.getOrNull(0) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair return ${binding.describeAbiKind()}"))
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair return ${binding.describeAbiKind()}")
        val valueAdapter = typeRenderer.collectionReferenceAdapterCode(binding.typeArguments.getOrNull(1) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair return ${binding.describeAbiKind()}"))
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI key-value pair return ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "%M(%L, %L).createOutputMarshaler(%L).use { __returnMarshaler -> %T.writePointer(%L, __returnMarshaler.abi) }",
            WINRT_KEY_VALUE_PAIR_ADAPTER_FUNCTION_NAME,
            keyAdapter,
            valueAdapter,
            valueExpression,
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
        )
    }

    private fun authoringCcwWriteReferenceReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
        projectionClass: ClassName,
    ): CodeBlock {
        val interfaceId = typeRenderer.referenceInterfaceIdCode(binding)
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI reference return ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L, %L))",
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            projectionClass,
            valueExpression,
            interfaceId,
        )
    }

    private fun authoringCcwWriteAsyncReferenceReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val interfaceId = authoringCcwAsyncReferenceInterfaceIdCode(binding)
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI async return ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "%T.writePointer(%L, %T.detachCCWForObject(%L, %L))",
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            COM_WRAPPERS_SUPPORT_CLASS_NAME,
            valueExpression,
            interfaceId,
        )
    }

    private fun authoringCcwAsyncReferenceInterfaceIdCode(
        binding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? =
        when (binding.kind) {
            KotlinProjectionAbiValueKind.MappedAsyncAction ->
                CodeBlock.of("%T.IAsyncAction", WINRT_ASYNC_INTERFACE_IDS_CLASS_NAME)
            KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> {
                val progressBinding = binding.typeArguments.singleOrNull() ?: return null
                val progressSignature = typeRenderer.asyncOperationResultTypeSignature(progressBinding) ?: return null
                CodeBlock.of("%T.interfaceId(%L)", WINRT_ASYNC_ACTION_WITH_PROGRESS_REFERENCE_CLASS_NAME, progressSignature)
            }
            KotlinProjectionAbiValueKind.MappedAsyncOperation -> {
                val resultBinding = binding.typeArguments.singleOrNull() ?: return null
                val resultSignature = typeRenderer.asyncOperationResultTypeSignature(resultBinding) ?: return null
                CodeBlock.of("%T.interfaceId(%L)", WINRT_ASYNC_OPERATION_REFERENCE_CLASS_NAME, resultSignature)
            }
            KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> {
                val resultBinding = binding.typeArguments.getOrNull(0) ?: return null
                val progressBinding = binding.typeArguments.getOrNull(1) ?: return null
                val resultSignature = typeRenderer.asyncOperationResultTypeSignature(resultBinding) ?: return null
                val progressSignature = typeRenderer.asyncOperationResultTypeSignature(progressBinding) ?: return null
                CodeBlock.of("%T.interfaceId(%L, %L)", WINRT_ASYNC_OPERATION_WITH_PROGRESS_REFERENCE_CLASS_NAME, resultSignature, progressSignature)
            }
            else -> null
        }

    private fun authoringCcwWriteBindableCollectionReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val projectionClass = when (binding.kind) {
            KotlinProjectionAbiValueKind.MappedBindableIterable -> WINRT_BINDABLE_ITERABLE_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVector -> WINRT_BINDABLE_VECTOR_PROJECTION_CLASS_NAME
            KotlinProjectionAbiValueKind.MappedBindableVectorView -> WINRT_BINDABLE_VECTOR_VIEW_PROJECTION_CLASS_NAME
            else -> return CodeBlock.of("error(%S)", "Unsupported authored ABI bindable collection return ${binding.describeAbiKind()}")
        }
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L))",
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            projectionClass,
            valueExpression,
        )
    }

    private fun authoringCcwWriteCustomObjectReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        customAbi: KotlinProjectionCustomObjectAbi,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock =
        CodeBlock.of(
            "%T.%L(%L, %T(%S)).use { __returnReference -> %T.writePointer(%L, %T.fromRawComPtr(__returnReference.getRefPointer())) }",
            WINRT_SYSTEM_PROJECTION_MARSHALERS_CLASS_NAME,
            if (binding.isNullableAbiReturn) "createObjectReferenceOrNull" else customAbi.createReferenceFunctionName,
            valueExpression,
            GUID_CLASS_NAME,
            customAbi.interfaceId.toString(),
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            PLATFORM_ABI_CLASS_NAME,
        )

    private fun authoringCcwWriteArrayReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        lengthOutExpression: String,
        dataOutExpression: String,
        valueExpression: String,
    ): CodeBlock {
        val elementBinding = binding.typeArguments.singleOrNull()
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI array return ${binding.describeAbiKind()}")
        typeRenderer.nonBlittableArrayElementMarshalerExpression(elementBinding)?.let { elementMarshaler ->
            return CodeBlock.of(
                "run {\n·val __returnArrayMarshaler = %L\n·val __returnArray = __returnArrayMarshaler.fromManagedArray(%L)\n·%T.writeInt32(%L, __returnArray?.length ?: 0)\n·%T.writePointer(%L, __returnArray?.data ?: %T.nullPointer)\n}",
                elementMarshaler,
                valueExpression,
                PLATFORM_ABI_CLASS_NAME,
                lengthOutExpression,
                PLATFORM_ABI_CLASS_NAME,
                dataOutExpression,
                PLATFORM_ABI_CLASS_NAME,
            )
        }
        val elementSize = typeRenderer.nativeArrayElementSizeExpression(elementBinding)
            ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI array return ${binding.describeAbiKind()}")
        val elementWrite = typeRenderer.nativeArrayElementWriteCode(
            elementBinding = elementBinding,
            dataExpression = CodeBlock.of("__returnArrayData"),
            indexExpression = CodeBlock.of("__index"),
            valueExpression = CodeBlock.of("__element"),
        ) ?: return CodeBlock.of("error(%S)", "Unsupported authored ABI array return ${binding.describeAbiKind()}")
        return CodeBlock.of(
            "run {\n·val __returnArrayMemory = %T.allocateBytesOwned(%L.size.toLong() * %L, %L)\n·val __returnArrayData = __returnArrayMemory.pointer\n·%L.forEachIndexed { __index, __element -> %L }\n·%T.writeInt32(%L, %L.size)\n·%T.writePointer(%L, __returnArrayData)\n}",
            PLATFORM_ABI_CLASS_NAME,
            valueExpression,
            elementSize,
            elementSize,
            valueExpression,
            elementWrite,
            PLATFORM_ABI_CLASS_NAME,
            lengthOutExpression,
            valueExpression,
            PLATFORM_ABI_CLASS_NAME,
            dataOutExpression,
        )
    }

    private fun authoringCcwWriteObjectReturnCode(
        binding: KotlinProjectionAbiTypeBinding,
        outExpression: String,
        valueExpression: String,
    ): CodeBlock {
        if (binding.kind == KotlinProjectionAbiValueKind.GenericParameter) {
            return CodeBlock.of(
                "%T.createReference(%L).use { __returnReference -> %T.writePointer(%L, __returnReference?.getRefPointer()?.let(%T::fromRawComPtr) ?: %T.nullPointer) }",
                WINRT_GENERIC_PARAMETER_PROJECTION_CLASS_NAME,
                valueExpression,
                PLATFORM_ABI_CLASS_NAME,
                outExpression,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
            )
        }
        val interfaceId = binding.interfaceId?.let { CodeBlock.of("%T(%S)", GUID_CLASS_NAME, it.toString()) }
            ?: CodeBlock.of("%T.IInspectable", IID_CLASS_NAME)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.detachCCWForObject(%L, %L))",
            PLATFORM_ABI_CLASS_NAME,
            outExpression,
            COM_WRAPPERS_SUPPORT_CLASS_NAME,
            valueExpression,
            interfaceId,
        )
    }

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
            relativePath = "io/github/composefluent/winrt/projections/support/$fileName",
            packageName = SUPPORT_PACKAGE,
            contents = contents,
        )

    private fun supportFile(fileName: String, fileSpec: FileSpec): KotlinProjectionFile =
        supportFile(fileName, fileSpec.toString())

    private fun supportFileSpec(fileName: String): FileSpec.Builder =
        FileSpec.builder(SUPPORT_PACKAGE, fileName)
            .addGeneratedProjectionSuppressions()
            .addFileComment("Deterministic generator handoff for reference %L writer parity.", fileName)

    private fun KotlinTypeProjectionPlan.hasGeneratedRuntimeClassMetadataRegistration(): Boolean =
        declarationKind == KotlinProjectionDeclarationKind.Class &&
            KotlinProjectionSpecializationKind.StaticClass !in specializationKinds &&
            KotlinProjectionSpecializationKind.AttributeClass !in specializationKinds

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

    private fun genericTypeInstantiationFunctions(entryClass: ClassName): List<FunSpec> =
        listOf(
            FunSpec.builder("initializeAll")
                .addCode("return\n")
                .build(),
            FunSpec.builder("initializeBySourceType")
                .addParameter("sourceType", String::class)
                .addCode("%T.initializeBySourceType(sourceType)\n", WINRT_GENERIC_TYPE_INSTANTIATION_SUPPORT_INTRINSIC_CLASS_NAME)
                .build(),
            FunSpec.builder("initializeEntry")
                .addParameter("entry", entryClass)
                .addCode("entry.dependencies.forEach(::initializeBySourceType)\n")
                .build(),
        )

    private fun eventSourceSubclassType(
        descriptor: WinRTEventHelperSubclassDescriptor,
        delegatePlan: KotlinTypeProjectionPlan,
        invokeShape: KotlinProjectionDelegateInvokeShape,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): TypeSpec {
        val delegateType = typeRenderer.resolveTypeName(descriptor.projectedEventTypeName)
        return TypeSpec.classBuilder(descriptor.sourceClassName)
            .addModifiers(KModifier.INTERNAL)
            .addTypeVariables(eventSourceTypeVariables(descriptor))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("objectReference", ClassName("io.github.composefluent.winrt.runtime", "ComObjectReference"))
                    .addParameter("vtableIndexForAddHandler", Int::class)
                    .build(),
            )
            .superclass(ClassName("io.github.composefluent.winrt.runtime", "EventSource").parameterizedBy(delegateType))
            .addSuperclassConstructorParameter("objectReference")
            .addSuperclassConstructorParameter("vtableIndexForAddHandler")
            .addFunction(
                FunSpec.builder("createMarshaler")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("handler", delegateType)
                    .returns(ClassName("io.github.composefluent.winrt.runtime", "WinRTDelegateHandle"))
                    .addCode(eventSourceCreateMarshalerCode(descriptor, invokeShape, plansByType))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("createEventSourceState")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("io.github.composefluent.winrt.runtime", "EventSourceState").parameterizedBy(delegateType))
                    .addCode(eventSourceStateCode(delegatePlan, invokeShape, delegateType))
                    .build(),
            )
            .build()
    }

    private fun eventSourceCreateMarshalerCode(
        descriptor: WinRTEventHelperSubclassDescriptor,
        invokeShape: KotlinProjectionDelegateInvokeShape,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): CodeBlock {
        val callbackArguments = CodeBlock.builder()
        invokeShape.parameterBindings.forEachIndexed { index, binding ->
            if (index > 0) {
                callbackArguments.add(", ")
            }
            callbackArguments.add(eventSourceCallbackArgumentCode(index, binding.typeBinding, plansByType))
        }
        val interfaceId = descriptor.interfaceId ?: invokeShape.interfaceId
        return CodeBlock.builder()
            .add("if (handler is io.github.composefluent.winrt.runtime.WinRTProjectedDelegate) {\n")
            .indent()
            .add("return io.github.composefluent.winrt.runtime.WinRTDelegateBridge.createProjectedDelegateHandle(handler)\n")
            .unindent()
            .add("}\n")
            .add("return io.github.composefluent.winrt.runtime.WinRTDelegateBridge.createDelegate(\n")
            .indent()
            .add("iid = io.github.composefluent.winrt.runtime.Guid(%S),\n", interfaceId.toString())
            .add("parameterKinds = %L,\n", eventSourceDelegateValueKindList(invokeShape.parameterBindings.map { it.typeBinding }))
            .add("returnKind = %L,\n", delegateValueKindName(invokeShape.returnBinding))
            .unindent()
            .add(") { __args ->\n")
            .indent()
            .add("handler.invoke(%L)\n", callbackArguments.build())
            .unindent()
            .add("}\n")
            .build()
    }

    private fun eventSourceCallbackArgumentCode(
        index: Int,
        binding: KotlinProjectionAbiTypeBinding,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): CodeBlock =
        if (mappedTypeByAbiName(binding.typeName) != null) {
            CodeBlock.of("__args[%L] as %T", index, typeRenderer.resolveTypeName(binding.typeName))
        } else if (binding.supportsProjectedGenericMetadataWrap(plansByType)) {
            val wrapTypeName = binding.projectedGenericMetadataWrapTypeName(plansByType)
                ?: error("Projected generic metadata wrap support requires a matching projection type for ${binding.describeAbiKind()}")
            CodeBlock.of(
                "%T.Metadata.wrap<%L>(__args[%L] as %T)",
                typeRenderer.resolveTypeName(wrapTypeName),
                eventSourceTypeArgumentCode(binding.typeArguments),
                index,
                when (binding.kind) {
                    KotlinProjectionAbiValueKind.ProjectedInterface -> IUNKNOWN_REFERENCE_CLASS_NAME
                    else -> IINSPECTABLE_REFERENCE_CLASS_NAME
                },
            )
        } else {
            typeRenderer.delegateCallbackArgumentCode(index, binding)
        }

    private fun eventSourceTypeArgumentCode(typeArguments: List<KotlinProjectionAbiTypeBinding>): CodeBlock {
        val code = CodeBlock.builder()
        typeArguments.forEachIndexed { index, argument ->
            if (index > 0) {
                code.add(", ")
            }
            code.add("%T", typeRenderer.resolveTypeName(argument.typeName))
        }
        return code.build()
    }

    private fun eventSourceDelegateValueKindList(bindings: List<KotlinProjectionAbiTypeBinding>): String =
        bindings.joinToString(prefix = "listOf(", postfix = ")") { eventSourceDelegateValueKindName(it) }

    private fun eventSourceDelegateValueKindName(binding: KotlinProjectionAbiTypeBinding): String =
        when (binding.kind) {
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
            KotlinProjectionAbiValueKind.InspectableReference,
            -> "io.github.composefluent.winrt.runtime.WinRTDelegateValueKind.IINSPECTABLE"
            else -> delegateValueKindName(binding)
        }

    private fun eventSourceTypeVariables(descriptor: WinRTEventHelperSubclassDescriptor): List<TypeVariableName> =
        descriptor.genericArgumentTypeNames
            .filter { it.isEventSourceGenericTypeParameterName() }
            .distinct()
            .map { name -> TypeVariableName(name) }

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
            ClassName("io.github.composefluent.winrt.runtime", "EventSourceState"),
            delegateType,
            ClassName("io.github.composefluent.winrt.runtime", "RawAddress"),
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

    private fun eventSourceOwnerHelperType(
        ownerEntries: List<WinRTEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): TypeSpec? {
        val firstEntry = ownerEntries.first()
        val builder = TypeSpec.objectBuilder(
            eventSourceOwnerHelperName(
                ownerType = firstEntry.ownerTypeName,
                eventType = firstEntry.eventTypeName,
            ),
        )
            .addModifiers(KModifier.INTERNAL)
        var hasFunctions = false
        ownerEntries.sortedBy(WinRTEventHelperSubclassDescriptor::eventTypeName).forEach { descriptor ->
            val createEventSource = directEventSourceCreateCode(descriptor, typesByQualifiedName, plansByType) ?: return@forEach
            hasFunctions = true
            builder.addFunction(
                FunSpec.builder(eventSourceCreateFunctionName(descriptor.eventTypeName, descriptor.ownerTypeName))
                    .addParameter("objectReference", ClassName("io.github.composefluent.winrt.runtime", "ComObjectReference"))
                    .addParameter("vtableIndexForAddHandler", Int::class)
                    .returns(ClassName("io.github.composefluent.winrt.runtime", "EventSource").parameterizedBy(STAR).copy(nullable = true))
                    .addCode("return %L\n", createEventSource)
                    .build(),
            )
        }
        return builder.build().takeIf { hasFunctions }
    }

    private fun directEventSourceCreateCode(
        descriptor: WinRTEventHelperSubclassDescriptor,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): CodeBlock? {
        if (descriptor.usesSharedEventHandlerSource) {
            return directSharedEventHandlerSourceCreateCode(descriptor, typesByQualifiedName)
        }
        val invokeShape = concreteEventInvokeShape(descriptor, typesByQualifiedName) ?: return null
        if (!invokeShape.isSupportedProjectedDelegateShape() || !invokeShape.supportsEventSourceCallbackWrapping(plansByType)) {
            return null
        }
        return CodeBlock.of("%L(objectReference, vtableIndexForAddHandler)", eventSourceConstructorCode(descriptor))
    }

    private fun directSharedEventHandlerSourceCreateCode(
        descriptor: WinRTEventHelperSubclassDescriptor,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val typeBinding = planner.classifyAbiTypeBinding(
            typeName = descriptor.eventTypeName,
            currentNamespace = descriptor.ownerTypeName.substringBeforeLast('.', missingDelimiterValue = ""),
            typesByQualifiedName = typesByQualifiedName,
        )
        val invokeShape = typeBinding.delegateInvokeShape ?: return null
        val argumentBinding = typeBinding.typeArguments.singleOrNull() ?: return null
        val interfaceId = typeRenderer.delegateInterfaceIdCode(typeBinding, invokeShape) ?: return null
        val argumentKind = typeRenderer.delegateInvokeValueKindCode(argumentBinding)
        val argumentType = typeRenderer.resolveTypeName(argumentBinding.typeName)
        return CodeBlock.of(
            "%T<%T>(objectReference = objectReference, interfaceId = %L, argsKind = %L, vtableIndexForAddHandler = vtableIndexForAddHandler)",
            ClassName("io.github.composefluent.winrt.runtime", "EventHandlerEventSource"),
            argumentType,
            interfaceId,
            argumentKind,
        )
    }

    internal fun canRenderEventSourceHelper(
        descriptor: WinRTEventHelperSubclassDescriptor,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): Boolean =
        directEventSourceCreateCode(descriptor, typesByQualifiedName, plansByType) != null

    private fun eventSourceConstructorCode(descriptor: WinRTEventHelperSubclassDescriptor): CodeBlock {
        val genericArguments = eventSourceTypeVariables(descriptor)
        return if (genericArguments.isEmpty()) {
            CodeBlock.of("%L", descriptor.sourceClassName)
        } else {
            CodeBlock.of(
                "%L<%L>",
                descriptor.sourceClassName,
                genericArguments.joinToString(", ") { "Any?" },
            )
        }
    }

    private fun concreteEventInvokeShape(
        descriptor: WinRTEventHelperSubclassDescriptor,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): KotlinProjectionDelegateInvokeShape? {
        val typeBinding = planner.classifyAbiTypeBinding(
            typeName = descriptor.eventTypeName,
            currentNamespace = descriptor.ownerTypeName.substringBeforeLast('.', missingDelimiterValue = ""),
            typesByQualifiedName = typesByQualifiedName,
        )
        return typeRenderer.outboundDelegateInvokeShape(typeBinding)
            ?.substituteDelegateTypeArguments(typeBinding.typeArguments)
    }

    private fun KotlinProjectionDelegateInvokeShape.substituteDelegateTypeArguments(
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
    ): KotlinProjectionDelegateInvokeShape {
        if (typeArguments.isEmpty()) {
            return this
        }
        return copy(
            parameterBindings = parameterBindings.map { parameter ->
                parameter.copy(typeBinding = parameter.typeBinding.substituteGenericTypeArguments(typeArguments))
            },
            returnBinding = returnBinding.substituteGenericTypeArguments(typeArguments),
        )
    }

    private fun KotlinProjectionAbiTypeBinding.substituteGenericTypeArguments(
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
    ): KotlinProjectionAbiTypeBinding {
        if (kind == KotlinProjectionAbiValueKind.GenericParameter && typeName.isEventSourceGenericTypeParameterName()) {
            val index = WinRTTypeRef.fromDisplayName(typeName).normalized().genericParameterIndex
            if (index != null && index in typeArguments.indices) {
                return typeArguments[index]
            }
        }
        if (this.typeArguments.isEmpty()) {
            return this
        }
        val substitutedArguments = this.typeArguments.map { it.substituteGenericTypeArguments(typeArguments) }
        return copy(
            typeName = substituteGenericTypeName(typeName, substitutedArguments),
            typeArguments = substitutedArguments,
        )
    }

    private fun substituteGenericTypeName(
        typeName: String,
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
    ): String {
        val genericStart = typeName.indexOf('<')
        if (genericStart < 0 || !typeName.endsWith('>')) {
            return typeName
        }
        return typeName.substring(0, genericStart) + typeArguments.joinToString(prefix = "<", postfix = ">") { it.typeName }
    }

    private fun String.isEventSourceGenericTypeParameterName(): Boolean =
        WinRTTypeRef.fromDisplayName(this).normalized().kind in setOf(
            WinRTTypeRefKind.GenericTypeParameter,
            WinRTTypeRefKind.MethodTypeParameter,
        )

    private fun KotlinProjectionDelegateInvokeShape.supportsEventSourceCallbackWrapping(
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): Boolean =
        parameterBindings.all { it.typeBinding.supportsEventSourceCallbackWrapping(plansByType) }

    private fun KotlinProjectionAbiTypeBinding.supportsEventSourceCallbackWrapping(
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): Boolean {
        if (typeArguments.any { !it.supportsEventSourceCallbackWrapping(plansByType) }) {
            return false
        }
        return when {
            typeArguments.isEmpty() -> true
            kind !in setOf(KotlinProjectionAbiValueKind.ProjectedInterface, KotlinProjectionAbiValueKind.ProjectedRuntimeClass) -> true
            else -> supportsProjectedGenericMetadataWrap(plansByType)
        }
    }

    private fun KotlinProjectionAbiTypeBinding.supportsProjectedGenericMetadataWrap(
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): Boolean = projectedGenericMetadataWrapTypeName(plansByType) != null

    private fun KotlinProjectionAbiTypeBinding.projectedGenericMetadataWrapTypeName(
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): String? =
        projectedGenericMetadataWrapPlan(plansByType)?.type?.qualifiedName
            ?: collectionEventProjectedGenericMetadataWrapTypeName()

    private fun KotlinProjectionAbiTypeBinding.projectedGenericMetadataWrapPlan(
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ): KotlinTypeProjectionPlan? {
        if (typeArguments.isEmpty() ||
            kind !in setOf(KotlinProjectionAbiValueKind.ProjectedInterface, KotlinProjectionAbiValueKind.ProjectedRuntimeClass)
        ) {
            return null
        }
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        val rawResolvedTypeName = resolvedTypeName.substringBefore('<').removeSuffix("?")
        return listOf(rawResolvedTypeName, rawTypeName)
            .distinct()
            .mapNotNull(plansByType::get)
            .firstOrNull { plan -> planSupportsProjectedGenericMetadataWrap(plan) }
    }

    private fun KotlinProjectionAbiTypeBinding.planSupportsProjectedGenericMetadataWrap(
        plan: KotlinTypeProjectionPlan,
    ): Boolean {
        if (plan.type.genericParameterCount != typeArguments.size) {
            return false
        }
        return when (kind) {
            KotlinProjectionAbiValueKind.ProjectedInterface ->
                supportTypeRenderer.canRenderInterfaceWrapper(plan) ||
                    plan.readOnlyCollectionBindings.isNotEmpty() ||
                    plan.mutableCollectionBindings.isNotEmpty()
            KotlinProjectionAbiValueKind.ProjectedRuntimeClass ->
                plan.declarationKind == KotlinProjectionDeclarationKind.Class &&
                    KotlinProjectionSpecializationKind.StaticClass !in plan.specializationKinds &&
                    KotlinProjectionSpecializationKind.AttributeClass !in plan.specializationKinds
            else -> false
        }
    }

    private fun KotlinProjectionAbiTypeBinding.collectionEventProjectedGenericMetadataWrapTypeName(): String? {
        if (kind != KotlinProjectionAbiValueKind.ProjectedInterface || typeArguments.isEmpty()) {
            return null
        }
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        val rawResolvedTypeName = resolvedTypeName.substringBefore('<').removeSuffix("?")
        return listOf(rawResolvedTypeName, rawTypeName)
            .firstOrNull { name ->
                when (name) {
                    "Windows.Foundation.Collections.IObservableVector" -> typeArguments.size == 1
                    "Windows.Foundation.Collections.IObservableMap" -> typeArguments.size == 2
                    "Windows.Foundation.Collections.IMapChangedEventArgs" -> typeArguments.size == 1
                    else -> false
                }
            }
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
        inventory: WinRTMetadataProjectionInventory,
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
            code.add("sourceFiles = %L,\n", stringListCode(addition.sourceFiles))
            code.unindent()
            code.add("),\n")
        }
        code.unindent()
        code.add(")")
        return code.build()
    }

    private fun authoringWrapperEntriesCode(
        entries: List<Pair<KotlinTypeProjectionPlan, io.github.composefluent.winrt.metadata.WinRTAuthoredMetadataTypeMapping>>,
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
        semanticHelpers: WinRTMetadataSemanticHelpers,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            val defaultInterface = plan.type.defaultInterfaceName?.let { semanticHelpers.resolveType(WinRTTypeRef.fromDisplayName(it), plan.type.namespace) }
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
        semanticHelpers: WinRTMetadataSemanticHelpers,
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
        semanticHelpers: WinRTMetadataSemanticHelpers,
        entryClass: ClassName,
    ): CodeBlock {
        val code = CodeBlock.builder()
        code.add("listOf(\n")
        code.indent()
        entries.sortedBy { it.type.qualifiedName }.forEach { plan ->
            val factory = plan.factorySurfaceDescriptor ?: semanticHelpers.factorySurfaceDescriptor(plan.type)
            val isActivatable = authoredRuntimeClassHasDefaultActivation(plan, semanticHelpers)
            val activatableInterfaces = factory.constructorFactories
            val staticInterfaces = factory.staticMemberTargets
            val composableInterfaces = factory.composableFactories
            code.add("%T(\n", entryClass)
            code.indent()
            code.add("projectedTypeName = %S,\n", plan.type.qualifiedName)
            code.add("serverFactoryTypeName = %S,\n", "ABI.${plan.type.qualifiedName}ServerActivationFactory")
            code.add("isActivatable = %L,\n", isActivatable)
            code.add("implementsIActivationFactory = true,\n")
            code.add("factoryInterfaceNames = %L,\n", stringListCode((activatableInterfaces + staticInterfaces + composableInterfaces).distinct()))
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

    private fun authoredRuntimeClassHasDefaultActivation(
        plan: KotlinTypeProjectionPlan,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): Boolean =
        !semanticHelpers.isStatic(plan.type) &&
            plan.type.activation.isActivatable &&
            plan.type.activation.activatableFactoryInterfaceName == null

    private fun authoringFactoryMemberReferences(
        plan: KotlinTypeProjectionPlan,
        interfaceNames: List<String>,
    ): List<String> =
        interfaceNames
            .flatMap { interfaceName ->
                val interfaceType = plan.typesByQualifiedName[interfaceName]
                    ?: plan.typesByQualifiedName[interfaceName.substringBefore('<').removeSuffix("?")]
                    ?: throw IllegalArgumentException(
                        "Support renderer requires authored runtime class ${plan.type.qualifiedName} factory interface $interfaceName to be present before rendering authoring activation factory members.",
                    )
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

    private fun genericAbiRegisterFunctionTypeName(): TypeName =
        Function2::class.asClassName().parameterizedBy(stringListTypeName(), stringTypeName(), UNIT)

    private fun io.github.composefluent.winrt.metadata.WinRTRequiredMappedHelperPlanDescriptor.toSupportPlanString(): String =
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
        const val SUPPORT_PACKAGE = "io.github.composefluent.winrt.projections.support"
        const val PROJECTION_REGISTRAR_CHUNK_SIZE = 64
        const val GENERIC_ABI_REGISTRY_LIST_SEPARATOR = "\u001F"
        const val TYPE_SHAPE_DESCRIPTOR_LIST_SEPARATOR = "\u001F"
        const val WINDOWS_FOUNDATION_ISTRINGABLE_TYPE_NAME = "Windows.Foundation.IStringable"
    }
}

private data class AuthoringServerFactoryInterface(
    val name: String,
    val kinds: Set<AuthoringServerFactoryInterfaceKind>,
)

private enum class AuthoringServerFactoryInterfaceKind {
    Constructor,
    Static,
    Composable,
}
