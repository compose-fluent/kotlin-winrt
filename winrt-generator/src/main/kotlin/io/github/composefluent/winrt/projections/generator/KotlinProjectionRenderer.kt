package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTFundamentalType
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
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
import io.github.composefluent.winrt.metadata.WinRTObjectReferencePlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.guidSignatureFragment
import io.github.composefluent.winrt.metadata.metadataParameterCategoryFor
import io.github.composefluent.winrt.metadata.projectedPropertyTypeName
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.isWinRTGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRTTypeTypeName
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
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
import com.squareup.kotlinpoet.BOOLEAN
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
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

class KotlinProjectionRenderer(
    internal val useInterfaceProjectionArtifacts: Boolean = false,
    internal val useProjectionIntrinsics: Boolean = false,
    internal val suppressProjectedMemberSlotConstants: Boolean = false,
    internal val projectedSlotLiterals: Map<KotlinProjectionSlotLiteralKey, Int> = emptyMap(),
    internal val useWinAppSdkTypeRedirects: Boolean = false,
    internal val useKotlinDurationAlias: Boolean = false,
    internal val genericTypeInstantiationsClassName: ClassName = WINRT_GENERIC_TYPE_INSTANTIATIONS_CLASS_NAME,
    internal val modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport? = null,
    internal val supportOwnerIdentity: String? = null,
) {
    fun render(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val contents = FileSpec.builder(plan.packageName, plan.type.name)
            .addGeneratedProjectionSuppressions()
            .apply {
                if (useKotlinDurationAlias) {
                    addAliasedImport(KOTLIN_DURATION_CLASS_NAME, KOTLIN_DURATION_ALIAS_CLASS_NAME.simpleName)
                }
                if (useWinAppSdkTypeRedirects && plan.requiresApplicationModelCoreAliasImports()) {
                    addAliasedImport(WINDOWS_APPLICATION_MODEL_CORE_FRAMEWORK_VIEW_CLASS_NAME, "WindowsApplicationModelCoreIFrameworkView")
                    addAliasedImport(
                        WINDOWS_APPLICATION_MODEL_CORE_FRAMEWORK_VIEW_SOURCE_CLASS_NAME,
                        "WindowsApplicationModelCoreIFrameworkViewSource",
                    )
                }
            }
            .apply { addType(renderType(plan)) }
            .build()
            .toString()
        return KotlinProjectionFile(
            relativePath = plan.relativePath,
            packageName = plan.packageName,
            contents = contents,
        )
    }

    internal fun renderType(plan: KotlinTypeProjectionPlan): TypeSpec = when (plan.declarationKind) {
        KotlinProjectionDeclarationKind.Interface -> renderInterfaceShell(plan)
        KotlinProjectionDeclarationKind.Class -> renderClassShell(plan)
        KotlinProjectionDeclarationKind.Enum -> renderEnumShell(plan)
        KotlinProjectionDeclarationKind.Struct -> renderStruct(plan)
        KotlinProjectionDeclarationKind.Delegate -> renderDelegate(plan)
    }

    internal fun renderInterfaceShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        winAppSdkCoveredApplicationModelCoreInterface(plan.type.qualifiedName)?.let { coveredInterface ->
            builder.addSuperinterface(resolveTypeName(coveredInterface))
        }
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(resolveTypeName(implemented.interfaceName))
        }
        plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).forEach { builder.addFunction(renderInterfaceMethod(it)) }
        plan.type.properties.filterNot { it.isStatic }.filter { it.hasNativeProjectionPropertyAccessor() }.forEach { property ->
            val getterResolution = property
                .takeIf { !it.hasNativeProjectionGetterAccessor() && it.hasNativeProjectionSetterAccessor() }
                ?.let { findNativeProjectionGetterInterface(plan.type, it, plan.typesByQualifiedName) }
            builder.addProperty(
                renderInterfaceProperty(
                    plan.type.qualifiedName,
                    property,
                    plan.typesByQualifiedName,
                    override = getterResolution?.fromBaseInterface == true,
                ),
            )
        }
        plan.type.events.filterNot { it.isStatic }.forEach { event ->
            builder.addProperty(renderEventProperty(event, eventInvokeDescriptor = null, abstract = true))
            renderEventFunctions(event, abstract = true).forEach(builder::addFunction)
        }
        if (canRenderInterfaceProxy(plan)) {
            builder.addType(renderInterfaceNativeProjection(plan))
        }
        appendCompanionShells(builder, plan)
        return builder.build()
    }

    internal fun renderInterfaceNativeProjection(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.classBuilder("NativeProjection")
            .addModifiers(KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("nativeObject", IUNKNOWN_REFERENCE_CLASS_NAME)
                    .build(),
            )
            .addSuperinterface(plan.projectedSelfTypeName())
            .addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
            .addProperty(
                PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("nativeObject")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("primaryTypeHandle", WINRT_TYPE_HANDLE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .apply {
                                if (plan.interfaceIid == null) {
                                    addCode("return null\n")
                                } else {
                                    addCode("return Metadata.TYPE_HANDLE\n")
                                }
                            }
                            .build(),
                    )
                    .build(),
            )
        repeat(plan.type.genericParameterCount) { index ->
            builder.addTypeVariable(TypeVariableName("T$index"))
        }
        addInterfaceNativeProjectionCollectionCaches(builder, plan)
        addInterfaceNativeProjectionMemberCaches(builder, plan)
        plan.mutableCollectionBindings.forEach { binding ->
            val nativeBinding = interfaceNativeProjectionCollectionBinding(plan, binding)
            builder.addProperty(renderMutableCollectionDelegateProperty(nativeBinding))
            if (nativeBinding.kind == KotlinProjectionMutableCollectionKind.Map) {
                builder.addSuperinterface(mapIterableType(nativeBinding))
            }
            addMutableCollectionForwardMembers(builder, nativeBinding)
        }
        plan.readOnlyCollectionBindings
            .filterNot { readOnlyBinding ->
                plan.mutableCollectionBindings.any { mutableBinding -> mutableBinding.covers(readOnlyBinding) }
            }
            .forEach { binding ->
                val nativeBinding = interfaceNativeProjectionCollectionBinding(plan, binding)
                builder.addProperty(renderReadOnlyCollectionDelegateProperty(nativeBinding))
                addReadOnlyCollectionForwardMembers(builder, nativeBinding)
            }
        if (plan.usesMappedDisposableAugmentation || plan.hasDirectMappedDisposableSuperinterface) {
            builder.addFunction(
                FunSpec.builder("close")
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("%L", renderNativeProjectionCloseInvocation())
                    .build(),
            )
        }
        collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                builder.addFunction(renderInterfaceProxyMethod(interfaceType, method, plan.typesByQualifiedName))
            }
            interfaceType.properties.filterNot(WinRTPropertyDefinition::isStatic).filter { it.hasNativeProjectionPropertyAccessor() }.forEach { property ->
                builder.addProperty(renderInterfaceProxyProperty(interfaceType, property, plan.typesByQualifiedName))
            }
            interfaceType.events.filterNot(WinRTEventDefinition::isStatic).forEach { event ->
                builder.addProperty(
                    renderEventProperty(
                        event = event,
                        eventInvokeDescriptor = null,
                        abstract = false,
                        override = true,
                        eventSourceOwnerTypeName = interfaceType.qualifiedName,
                        eventSourceEventTypeName = plan.typesByQualifiedName[interfaceType.qualifiedName]
                            ?.events
                            ?.firstOrNull { rawEvent -> rawEvent.name == event.name }
                            ?.delegateTypeName,
                        eventSourceObjectReference = interfaceNativeProjectionEventSourceObjectReference(plan, interfaceType),
                        eventSourceAddSlot = metadataSlotExpression(interfaceType, "${event.name.uppercase()}_ADD_SLOT"),
                        fallbackToAddRemove = false,
                    ),
                )
                renderInterfaceProxyEventFunctions(interfaceType, event).forEach(builder::addFunction)
            }
        }
        return builder.build()
    }

    private fun addInterfaceNativeProjectionCollectionCaches(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        interfaceNativeProjectionCollectionCacheBindings(plan).forEach { binding ->
            builder.addProperty(
                PropertySpec.builder(binding.ownerCachePropertyName, IUNKNOWN_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.of(
                            "lazy(%T.PUBLICATION) { %M(nativeObject, %L) }",
                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                            ACQUIRE_INTERFACE_REFERENCE_FUNCTION_NAME,
                            runtimeClassInterfaceIdCode(binding.slotInterfaceInstanceName, plan),
                        ),
                    )
                    .build(),
            )
        }
    }

    private fun interfaceNativeProjectionCollectionCacheBindings(
        plan: KotlinTypeProjectionPlan,
    ): List<KotlinProjectionCollectionSlotBinding> =
        interfaceNativeProjectionCollectionSlotBindings(plan)
            .filter { binding -> binding.requiresInterfaceNativeProjectionCache(plan) }
            .distinctBy { binding -> binding.ownerCachePropertyName }

    internal fun interfaceNativeProjectionEventSourceObjectReference(
        plan: KotlinTypeProjectionPlan,
        interfaceType: WinRTTypeDefinition,
    ): CodeBlock =
        interfaceNativeProjectionOwnerBindings(plan)
            .firstOrNull { binding -> binding.rawInterfaceQualifiedName == interfaceType.qualifiedName }
            ?.let { binding -> CodeBlock.of("%L", binding.ownerCachePropertyName) }
            ?: CodeBlock.of("nativeObject")

    private fun addInterfaceNativeProjectionMemberCaches(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        val existingCacheNames = interfaceNativeProjectionCollectionCacheBindings(plan)
            .mapTo(mutableSetOf()) { binding -> binding.ownerCachePropertyName }
        (interfaceNativeProjectionOwnerBindings(plan).asSequence() +
            plan.instanceMemberBindings
                .asSequence()
                .filterNot { binding -> binding.ownerCachePropertyName == "nativeObject" }
                .map { binding ->
                    InterfaceNativeProjectionOwnerBinding(
                        ownerCachePropertyName = binding.ownerCachePropertyName,
                        ownerInterfaceInstanceName = binding.ownerInterfaceQualifiedName,
                        rawInterfaceQualifiedName = binding.ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?"),
                    )
                })
            .asSequence()
            .filterNot { binding -> binding.ownerCachePropertyName in existingCacheNames }
            .distinctBy { binding -> binding.ownerCachePropertyName }
            .forEach { binding ->
                builder.addProperty(
                    PropertySpec.builder(binding.ownerCachePropertyName, IUNKNOWN_REFERENCE_CLASS_NAME)
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy(%T.PUBLICATION) { %M(nativeObject, %L) }",
                                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                ACQUIRE_INTERFACE_REFERENCE_FUNCTION_NAME,
                                runtimeClassInterfaceIdCode(binding.ownerInterfaceInstanceName, plan),
                            ),
                        )
                        .build(),
                )
            }
    }

    private fun interfaceNativeProjectionOwnerBindings(
        plan: KotlinTypeProjectionPlan,
    ): List<InterfaceNativeProjectionOwnerBinding> =
        interfaceNativeProjectionOwnerBindings(
            interfaceType = plan.type,
            plan = plan,
            genericArguments = emptyList(),
            visited = linkedSetOf(),
        )

    private fun interfaceNativeProjectionOwnerBindings(
        interfaceType: WinRTTypeDefinition,
        plan: KotlinTypeProjectionPlan,
        genericArguments: List<WinRTTypeRef>,
        visited: MutableSet<String>,
    ): List<InterfaceNativeProjectionOwnerBinding> =
        buildList {
            interfaceType.implementedInterfaces.forEach { implemented ->
                val substitutedInterfaceName = implemented.interfaceType
                    .substituteTypeParameters(genericArguments)
                    .normalized()
                    .typeName
                val implementedRawName = substitutedInterfaceName.substringBefore('<').removeSuffix("?")
                val resolvedImplementedRawName = resolveImplementedInterfaceRawName(
                    implementedRawName,
                    interfaceType.namespace,
                    plan.typesByQualifiedName,
                )
                val mappedType = mappedTypeByAbiName(resolvedImplementedRawName) ?: mappedTypeByAbiName(implementedRawName)
                if (
                    isRuntimeOwnedMappedTypeName(resolvedImplementedRawName) ||
                    isMappedCollectionInterfaceName(resolvedImplementedRawName) ||
                    mappedType?.descriptionName == "Iterator"
                ) {
                    return@forEach
                }
                val ownerInterfaceInstanceName = resolvedInterfaceInstanceName(
                    substitutedInterfaceName = substitutedInterfaceName,
                    resolvedRawName = resolvedImplementedRawName,
                )
                if (resolvedImplementedRawName != plan.type.qualifiedName && visited.add(ownerInterfaceInstanceName)) {
                    add(
                        InterfaceNativeProjectionOwnerBinding(
                            ownerCachePropertyName = "_${resolvedImplementedRawName.substringAfterLast('.').replaceFirstChar(Char::lowercase)}",
                            ownerInterfaceInstanceName = ownerInterfaceInstanceName,
                            rawInterfaceQualifiedName = resolvedImplementedRawName,
                        ),
                    )
                }
                plan.typesByQualifiedName[resolvedImplementedRawName]?.let { baseType ->
                    addAll(
                        interfaceNativeProjectionOwnerBindings(
                            interfaceType = baseType,
                            plan = plan,
                            genericArguments = genericArgumentTypeRefs(substitutedInterfaceName),
                            visited = visited,
                        ),
                    )
                }
            }
        }

    private data class InterfaceNativeProjectionOwnerBinding(
        val ownerCachePropertyName: String,
        val ownerInterfaceInstanceName: String,
        val rawInterfaceQualifiedName: String,
    )

    private fun resolvedInterfaceInstanceName(
        substitutedInterfaceName: String,
        resolvedRawName: String,
    ): String =
        if ('<' in substitutedInterfaceName && substitutedInterfaceName.endsWith('>')) {
            "$resolvedRawName<${substitutedInterfaceName.substringAfter('<').substringBeforeLast('>')}>"
        } else {
            resolvedRawName
        }

    private fun interfaceNativeProjectionCollectionSlotBindings(
        plan: KotlinTypeProjectionPlan,
    ): List<KotlinProjectionCollectionSlotBinding> =
        plan.mutableCollectionBindings.map(::KotlinProjectionCollectionSlotBinding) +
            plan.readOnlyCollectionBindings
                .filterNot { readOnlyBinding ->
                    plan.mutableCollectionBindings.any { mutableBinding -> mutableBinding.covers(readOnlyBinding) }
                }
                .map(::KotlinProjectionCollectionSlotBinding)

    private fun interfaceNativeProjectionCollectionBinding(
        plan: KotlinTypeProjectionPlan,
        binding: KotlinProjectionMutableCollectionBinding,
    ): KotlinProjectionMutableCollectionBinding =
        if (KotlinProjectionCollectionSlotBinding(binding).requiresInterfaceNativeProjectionCache(plan)) {
            binding
        } else {
            binding.copy(ownerCachePropertyName = "nativeObject")
        }

    private fun interfaceNativeProjectionCollectionBinding(
        plan: KotlinTypeProjectionPlan,
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ): KotlinProjectionReadOnlyCollectionBinding =
        if (KotlinProjectionCollectionSlotBinding(binding).requiresInterfaceNativeProjectionCache(plan)) {
            binding
        } else {
            binding.copy(ownerCachePropertyName = "nativeObject")
        }

    private data class KotlinProjectionCollectionSlotBinding(
        val ownerCachePropertyName: String,
        val slotInterfaceQualifiedName: String,
        val slotInterfaceInstanceName: String,
    ) {
        constructor(binding: KotlinProjectionMutableCollectionBinding) : this(
            ownerCachePropertyName = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotInterfaceInstanceName = collectionSlotInterfaceInstanceName(
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                elementBinding = binding.elementBinding,
                keyBinding = binding.keyBinding,
                valueBinding = binding.valueBinding,
            ),
        )

        constructor(binding: KotlinProjectionReadOnlyCollectionBinding) : this(
            ownerCachePropertyName = binding.ownerCachePropertyName,
            slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
            slotInterfaceInstanceName = collectionSlotInterfaceInstanceName(
                slotInterfaceQualifiedName = binding.slotInterfaceQualifiedName,
                elementBinding = binding.elementBinding,
                keyBinding = binding.keyBinding,
                valueBinding = binding.valueBinding,
            ),
        )
    }

    private companion object {
        private fun collectionSlotInterfaceInstanceName(
            slotInterfaceQualifiedName: String,
            elementBinding: KotlinProjectionAbiTypeBinding?,
            keyBinding: KotlinProjectionAbiTypeBinding?,
            valueBinding: KotlinProjectionAbiTypeBinding?,
        ): String =
            when {
                elementBinding != null -> "$slotInterfaceQualifiedName<${elementBinding.typeName}>"
                keyBinding != null && valueBinding != null ->
                    "$slotInterfaceQualifiedName<${keyBinding.typeName}, ${valueBinding.typeName}>"
                else -> slotInterfaceQualifiedName
            }
    }

    private fun KotlinProjectionCollectionSlotBinding.requiresInterfaceNativeProjectionCache(
        plan: KotlinTypeProjectionPlan,
    ): Boolean =
        slotInterfaceQualifiedName.substringBefore('<').removeSuffix("?") !=
            plan.type.qualifiedName.substringBefore('<').removeSuffix("?")

    internal fun KotlinTypeProjectionPlan.projectedSelfTypeName(): TypeName {
        val className = ClassName(packageName, type.name)
        return if (type.genericParameterCount == 0) {
            className
        } else {
            className.parameterizedBy((0 until type.genericParameterCount).map { index -> TypeVariableName("T$index") })
        }
    }

    internal fun renderInterfaceProxyMethod(
        slotInterfaceType: WinRTTypeDefinition,
        method: WinRTMethodDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): FunSpec {
        val returnBinding = renderAbiTypeBinding(method.projectedKotlinReturnTypeName(), typesByQualifiedName, slotInterfaceType.namespace)
        val parameterBindings = method.projectedKotlinParameters().map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = renderAbiTypeBinding(parameter.typeName, typesByQualifiedName, slotInterfaceType.namespace),
                category = metadataParameterCategoryFor(parameter),
            )
        }
        val callPlan = requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        )
        val slotExpression = metadataSlotExpression(slotInterfaceType, method.abiSlotConstantName(slotInterfaceType.methods))
        val invocation = interfaceProxyNoArgIntrinsicInvocation(
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: interfaceProxyStructResultIntrinsicInvocation(
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceArrayResultIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceEnumResultIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: interfaceProxyOneArgUnitIntrinsicInvocation(
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceDescriptorUnitIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceDescriptorBooleanIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceDescriptorScalarIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceDescriptorProjectedObjectIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceDescriptorAsyncIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInstanceEnumOneArgUnitIntrinsicInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        ) ?: renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = slotExpression,
            callPlan = callPlan,
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        val objectShape = closableMethodShape(slotInterfaceType, method) ?: runtimeObjectMethodShape(method)
        return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
            .addModifiers(KModifier.OVERRIDE)
            .addMethodGenericParameters(method, objectShape)
            .addParameters(objectShape?.parameters ?: method.projectedKotlinParameters().map { ParameterSpec.builder(it.name, resolveTypeName(it.typeName)).build() })
            .returns(objectShape?.returnType ?: resolveTypeName(method.projectedKotlinReturnTypeName()))
            .addCode("%L\n", invocation)
            .build()
    }

    internal fun renderInterfaceProxyProperty(
        slotInterfaceType: WinRTTypeDefinition,
        property: WinRTPropertyDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): PropertySpec {
        val propertyTypeName = property.projectedPropertyTypeName(slotInterfaceType.qualifiedName, typesByQualifiedName)
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            resolveTypeName(propertyTypeName),
        )
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
        if (property.hasNativeProjectionGetterAccessor()) {
            val getterReturnBinding = renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace)
            val getterCallPlan = requireAbiCallPlan(
                bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.get",
                returnBinding = getterReturnBinding,
                parameterBindings = emptyList(),
                suppressHResultCheck = property.isNoException,
            )
            val scalarGetterInvocation = interfaceProxyScalarGetterInvocation(
                slotInterfaceType = slotInterfaceType,
                property = property,
                returnBinding = getterReturnBinding,
            )
            val projectedObjectGetterInvocation = interfaceProxyProjectedObjectGetterInvocation(
                slotInterfaceType = slotInterfaceType,
                property = property,
                returnBinding = getterReturnBinding,
            )
            val getterSlotExpression = metadataSlotExpression(slotInterfaceType, "${property.name.uppercase()}_GETTER_SLOT")
            val noArgIntrinsicInvocation = interfaceProxyNoArgIntrinsicInvocation(
                slotExpression = getterSlotExpression,
                returnBinding = getterReturnBinding,
                parameterBindings = emptyList(),
                suppressHResultCheck = property.isNoException,
            )
            builder.getter(
                FunSpec.getterBuilder()
                    .addCode(
                        "%L\n",
                        projectedObjectGetterInvocation ?: scalarGetterInvocation ?: noArgIntrinsicInvocation ?: interfaceProxyStructResultIntrinsicInvocation(
                            slotExpression = getterSlotExpression,
                            returnBinding = getterReturnBinding,
                            parameterBindings = emptyList(),
                            suppressHResultCheck = property.isNoException,
                        ) ?: renderInstanceArrayResultIntrinsicInvocation(
                            referenceExpression = "nativeObject",
                            slotExpression = getterSlotExpression,
                            returnBinding = getterReturnBinding,
                            parameterBindings = emptyList(),
                            suppressHResultCheck = property.isNoException,
                        ) ?: renderInstanceEnumResultIntrinsicInvocation(
                            referenceExpression = "nativeObject",
                            slotExpression = getterSlotExpression,
                            returnBinding = getterReturnBinding,
                            parameterBindings = emptyList(),
                            suppressHResultCheck = property.isNoException,
                        ) ?: renderInlineAbiInvocation(
                                invokeTargetExpression = "nativeObject",
                                slotExpression = getterSlotExpression,
                                callPlan = getterCallPlan,
                            ) ?: error("Generator interface proxy parity failed to emit getter ${property.name}"),
                    )
                    .build(),
            )
        } else {
            val getterInterfaceType = findNativeProjectionGetterInterface(slotInterfaceType, property, typesByQualifiedName)?.interfaceType
                ?: error("Could not find property getter interface for ${slotInterfaceType.qualifiedName}.${property.name}")
            val getterInterfaceClassName = resolveTypeName(getterInterfaceType.qualifiedName)
            builder.getter(
                FunSpec.getterBuilder()
                    .addCode(
                        "return this.%M<%T>().%L\n",
                        WINRT_AS_FUNCTION_NAME,
                        getterInterfaceClassName,
                        property.name.replaceFirstChar(Char::lowercase),
                    )
                    .build(),
            )
        }
        if (!property.isReadOnly) {
            val setterCallPlan = requireAbiCallPlan(
                bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.set",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace))),
                suppressHResultCheck = property.isNoException,
            )
            val setterSlotExpression = metadataSlotExpression(slotInterfaceType, "${property.name.uppercase()}_SETTER_SLOT")
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", resolveTypeName(propertyTypeName))
                    .addCode(
                        "%L\n",
                        interfaceProxyOneArgUnitIntrinsicInvocation(
                            slotExpression = setterSlotExpression,
                            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace))),
                            suppressHResultCheck = property.isNoException,
                        ) ?: renderInstanceStructOneArgUnitIntrinsicInvocation(
                            referenceExpression = "nativeObject",
                            slotExpression = setterSlotExpression,
                            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace))),
                            suppressHResultCheck = property.isNoException,
                            argumentExpression = "value",
                        ) ?: renderInstanceEnumOneArgUnitIntrinsicInvocation(
                            referenceExpression = "nativeObject",
                            slotExpression = setterSlotExpression,
                            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                            parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(propertyTypeName, typesByQualifiedName, slotInterfaceType.namespace))),
                            suppressHResultCheck = property.isNoException,
                            argumentExpression = "value",
                        ) ?: renderInlineAbiInvocation(
                            invokeTargetExpression = "nativeObject",
                            slotExpression = setterSlotExpression,
                            callPlan = setterCallPlan,
                        ) ?: error("Generator interface proxy parity failed to emit setter ${property.name}"),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun interfaceProxyStructResultIntrinsicInvocation(
        slotExpression: CodeBlock,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        suppressHResultCheck: Boolean,
    ): CodeBlock? {
        if (
            !useProjectionIntrinsics ||
            returnBinding.kind != KotlinProjectionAbiValueKind.Struct ||
            suppressHResultCheck
        ) {
            return null
        }
        if (customStructAbi(returnBinding) != null) {
            return null
        }
        val structType = nativeStructClassName(returnBinding) ?: return null
        if (parameterBindings.isNotEmpty()) {
        val arguments = parameterBindings.map { parameter ->
            if (parameter.category != WinRTMetadataParameterCategory.In) {
                return null
            }
            descriptorIntrinsicArgument(parameter, includeStruct = true) ?: return null
        }
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return %T.callStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
            .indent()
            .add("nativeObject,\n")
            .add("%L,\n", slotExpression)
            .add("%S,\n", arguments.joinToString(",") { it.shape })
            .add("%T.Metadata,\n", structType)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
        }
        return CodeBlock.builder()
            .add("return %T.getStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
            .indent()
            .add("nativeObject,\n")
            .add("%L,\n", slotExpression)
            .add("%T.Metadata,\n", structType)
            .unindent()
            .add(")\n")
            .build()
    }

    private fun interfaceProxyNoArgIntrinsicInvocation(
        slotExpression: CodeBlock,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        suppressHResultCheck: Boolean,
    ): CodeBlock? {
        if (!useProjectionIntrinsics || parameterBindings.isNotEmpty()) {
            return null
        }
        val helperFunction = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.Unit -> return null
            KotlinProjectionAbiValueKind.String -> "getString"
            KotlinProjectionAbiValueKind.Boolean ->
                if (suppressHResultCheck) "getNoExceptionBoolean" else "getBoolean"
            KotlinProjectionAbiValueKind.Int32 -> "getInt32"
            KotlinProjectionAbiValueKind.UInt32 -> "getUInt32"
            KotlinProjectionAbiValueKind.Int64 -> "getInt64"
            KotlinProjectionAbiValueKind.UInt64 -> "getUInt64"
            KotlinProjectionAbiValueKind.Float -> "getFloat"
            KotlinProjectionAbiValueKind.Double -> "getDouble"
            else -> return null
        }
        return renderInstanceScalarGetterInvocation(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            helperFunction = helperFunction,
            intrinsic = true,
            modulePlatformAbiCalls = modulePlatformAbiCalls,
        )
    }

    private fun interfaceProxyOneArgUnitIntrinsicInvocation(
        slotExpression: CodeBlock,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        suppressHResultCheck: Boolean,
    ): CodeBlock? {
        if (
            !useProjectionIntrinsics ||
            returnBinding.kind != KotlinProjectionAbiValueKind.Unit ||
            parameterBindings.size != 1 ||
            suppressHResultCheck
        ) {
            return null
        }
        val parameter = parameterBindings.single()
        val helperFunction = when (parameter.typeBinding.kind) {
            KotlinProjectionAbiValueKind.String -> {
                if (parameter.typeBinding.typeName.endsWith("?")) return null
                "setString"
            }
            KotlinProjectionAbiValueKind.Boolean -> "setBoolean"
            KotlinProjectionAbiValueKind.Int32 -> "setInt32"
            KotlinProjectionAbiValueKind.UInt32 -> "setUInt32"
            KotlinProjectionAbiValueKind.Int64 -> "setInt64"
            KotlinProjectionAbiValueKind.UInt64 -> "setUInt64"
            KotlinProjectionAbiValueKind.Float -> "setFloat"
            KotlinProjectionAbiValueKind.Double -> "setDouble"
            else -> return null
        }
        modulePlatformAbiCalls?.scalarSetter(
            referenceExpression = "nativeObject",
            slotExpression = slotExpression,
            helperFunction = helperFunction,
            argumentExpression = CodeBlock.of("%L", parameter.name),
        )?.let { return it }
        return CodeBlock.builder()
            .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
            .indent()
            .add("nativeObject,\n")
            .add("%L,\n", slotExpression)
            .add("%L,\n", parameter.name)
            .unindent()
            .add(")\n")
            .build()
    }

    private fun interfaceProxyProjectedObjectGetterInvocation(
        slotInterfaceType: WinRTTypeDefinition,
        property: WinRTPropertyDefinition,
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        if (property.isNoException) {
            return null
        }
        if (customObjectAbi(returnBinding) != null) {
            return null
        }
        return renderInstanceProjectedObjectGetterInvocation(
            referenceExpression = "nativeObject",
            slotExpression = metadataSlotExpression(slotInterfaceType, "${property.name.uppercase()}_GETTER_SLOT"),
            returnBinding = returnBinding,
        )
    }

    private fun interfaceProxyScalarGetterInvocation(
        slotInterfaceType: WinRTTypeDefinition,
        property: WinRTPropertyDefinition,
        returnBinding: KotlinProjectionAbiTypeBinding,
    ): CodeBlock? {
        if (property.isNoException) {
            return null
        }
        val helperFunction = when (returnBinding.kind) {
            KotlinProjectionAbiValueKind.Boolean -> "getBoolean"
            KotlinProjectionAbiValueKind.Int32 -> "getInt32"
            KotlinProjectionAbiValueKind.UInt32 -> "getUInt32"
            KotlinProjectionAbiValueKind.Int64 -> "getInt64"
            KotlinProjectionAbiValueKind.UInt64 -> "getUInt64"
            KotlinProjectionAbiValueKind.Float -> "getFloat"
            KotlinProjectionAbiValueKind.Double -> "getDouble"
            else -> return null
        }
        return renderInstanceScalarGetterInvocation(
            referenceExpression = "nativeObject",
            slotExpression = metadataSlotExpression(slotInterfaceType, "${property.name.uppercase()}_GETTER_SLOT"),
            helperFunction = helperFunction,
            intrinsic = useProjectionIntrinsics,
            modulePlatformAbiCalls = modulePlatformAbiCalls,
        )
    }

    internal fun renderInterfaceProxyEventFunctions(
        slotInterfaceType: WinRTTypeDefinition,
        event: WinRTEventDefinition,
    ): List<FunSpec> {
        val typeName = resolveTypeName(event.delegateTypeName)
        val propertyName = event.name.replaceFirstChar(Char::lowercase)
        return listOf(
            FunSpec.builder("add${event.name}")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", typeName)
                .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("return %L.add(handler)\n", propertyName)
                .build(),
            FunSpec.builder("remove${event.name}")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("%L.remove(token)\n", propertyName)
                .build(),
        )
    }

    internal fun canRenderInterfaceProxy(plan: KotlinTypeProjectionPlan): Boolean =
        !isRuntimeOwnedMappedTypeName(plan.type.qualifiedName) &&
            !(suppressProjectedMemberSlotConstants && plan.type.isExclusiveTo) &&
            mappedTypeByAbiName(plan.type.qualifiedName)?.abiValueKind != KotlinProjectionAbiValueKind.MappedKeyValuePair &&
            (!isMappedCollectionInterfaceName(plan.type.qualifiedName) || plan.readOnlyCollectionBindings.isNotEmpty() || plan.mutableCollectionBindings.isNotEmpty()) &&
            collectInterfaceProxyTypes(plan).all { interfaceType ->
            interfaceType.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).all { method ->
                runCatching {
                    buildAbiCallPlan(
                        returnBinding = renderAbiTypeBinding(method.projectedKotlinReturnTypeName(), plan.typesByQualifiedName, interfaceType.namespace),
                        parameterBindings = method.projectedKotlinParameters().map { parameter ->
                            KotlinProjectionAbiParameterBinding(
                                name = parameter.name,
                                typeBinding = renderAbiTypeBinding(parameter.typeName, plan.typesByQualifiedName, interfaceType.namespace),
                                category = metadataParameterCategoryFor(parameter),
                            )
                        },
                    ) != null
                }.getOrDefault(false)
            } &&
                interfaceType.properties.filterNot(WinRTPropertyDefinition::isStatic).all { property ->
                    if (!property.hasNativeProjectionPropertyAccessor()) {
                        return@all false
                    }
                    val propertyTypeName = property.projectedPropertyTypeName(interfaceType.qualifiedName, plan.typesByQualifiedName)
                    val getterAvailable = if (property.hasNativeProjectionGetterAccessor()) {
                        runCatching {
                            buildAbiCallPlan(
                                returnBinding = renderAbiTypeBinding(propertyTypeName, plan.typesByQualifiedName, interfaceType.namespace),
                                parameterBindings = emptyList(),
                            ) != null
                        }.getOrDefault(false)
                    } else {
                        findNativeProjectionGetterInterface(interfaceType, property, plan.typesByQualifiedName) != null
                    }
                    getterAvailable &&
                        (
                            property.isReadOnly ||
                                property.hasNativeProjectionSetterAccessor() &&
                                runCatching {
                                    buildAbiCallPlan(
                                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                                        parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", renderAbiTypeBinding(propertyTypeName, plan.typesByQualifiedName, interfaceType.namespace))),
                                    ) != null
                                }.getOrDefault(false)
                            )
                } &&
                interfaceType.events.filterNot(WinRTEventDefinition::isStatic).all { event ->
                    event.hasNativeProjectionAccessorPair()
                }
        }

    internal fun collectInterfaceProxyTypes(plan: KotlinTypeProjectionPlan): List<WinRTTypeDefinition> =
        collectInterfaceProxyTypes(plan.type, plan, linkedSetOf(), emptyList())

    private fun collectInterfaceProxyTypes(
        interfaceType: WinRTTypeDefinition,
        plan: KotlinTypeProjectionPlan,
        visited: MutableSet<String>,
        genericArguments: List<WinRTTypeRef>,
    ): List<WinRTTypeDefinition> {
        if (!visited.add(interfaceType.qualifiedName)) {
            return emptyList()
        }
        return buildList {
            interfaceType.implementedInterfaces.forEach { implemented ->
                val substitutedInterfaceName = implemented.interfaceType
                    .substituteTypeParameters(genericArguments)
                    .normalized()
                    .typeName
                val implementedRawName = substitutedInterfaceName.substringBefore('<').removeSuffix("?")
                val resolvedImplementedRawName = resolveImplementedInterfaceRawName(
                    implementedRawName,
                    interfaceType.namespace,
                    plan.typesByQualifiedName,
                )
                val mappedType = mappedTypeByAbiName(resolvedImplementedRawName) ?: mappedTypeByAbiName(implementedRawName)
                if (
                    isRuntimeOwnedMappedTypeName(resolvedImplementedRawName) ||
                    isMappedCollectionInterfaceName(resolvedImplementedRawName) ||
                    mappedType?.descriptionName == "Iterator"
                ) {
                    return@forEach
                }
                plan.typesByQualifiedName[resolvedImplementedRawName]?.let { baseType ->
                    addAll(collectInterfaceProxyTypes(baseType, plan, visited, genericArgumentTypeRefs(substitutedInterfaceName)))
                }
            }
            add(interfaceType.substituteInterfaceProxyMembers(genericArguments))
        }
    }

    private fun resolveImplementedInterfaceRawName(
        rawName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String {
        if (rawName in typesByQualifiedName || '.' in rawName) {
            return rawName
        }
        val qualifiedName = "$currentNamespace.$rawName"
        return if (qualifiedName in typesByQualifiedName) qualifiedName else rawName
    }

    private fun WinRTTypeDefinition.substituteInterfaceProxyMembers(
        genericArguments: List<WinRTTypeRef>,
    ): WinRTTypeDefinition =
        if (genericArguments.isEmpty()) {
            this
        } else {
            copy(
                methods = methods.map { method ->
                    val substitutedReturnType = method.returnType.substituteTypeParameters(genericArguments).normalized()
                    method.copy(
                        returnTypeName = substitutedReturnType.typeName,
                        returnTypeSignature = substitutedReturnType,
                        parameters = method.parameters.map { parameter ->
                            val substitutedType = parameter.type.substituteTypeParameters(genericArguments).normalized()
                            parameter.copy(
                                typeName = substitutedType.typeName,
                                typeSignature = substitutedType,
                                typeIsByRef = substitutedType.isByRef,
                            )
                        },
                    )
                },
                properties = properties.map { property ->
                    val substitutedType = property.type.substituteTypeParameters(genericArguments).normalized()
                    property.copy(typeName = substitutedType.typeName, typeSignature = substitutedType)
                },
                events = events.map { event ->
                    val substitutedType = event.delegateType.substituteTypeParameters(genericArguments).normalized()
                    event.copy(delegateTypeName = substitutedType.typeName, delegateTypeSignature = substitutedType)
                },
            )
        }

    private fun genericArgumentTypeRefs(typeName: String): List<WinRTTypeRef> {
        val trimmed = typeName.trim()
        if ('<' !in trimmed || !trimmed.endsWith('>')) {
            return emptyList()
        }
        return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
            .map(WinRTTypeRef::fromDisplayName)
    }

    internal fun renderAbiTypeBinding(
        typeName: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
        currentNamespace: String? = null,
    ): KotlinProjectionAbiTypeBinding =
        KotlinProjectionPlanner(useWinAppSdkTypeRedirects = useWinAppSdkTypeRedirects).classifyAbiTypeBinding(
            typeName = redirectedAbiTypeExpression(typeName),
            currentNamespace = currentNamespace.orEmpty(),
            typesByQualifiedName = typesByQualifiedName,
        )

    internal fun renderClassShell(plan: KotlinTypeProjectionPlan): TypeSpec = when {
        KotlinProjectionSpecializationKind.AttributeClass in plan.specializationKinds -> renderAttributeClassShell(plan)
        KotlinProjectionSpecializationKind.StaticClass in plan.specializationKinds -> renderStaticClassShell(plan)
        else -> renderRuntimeClassShell(plan)
    }

    internal fun renderRuntimeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val builder = TypeSpec.classBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan, emitKotlinSealed = false)
        if (plan.requiresOpenRuntimeClassShell()) {
            builder.addModifiers(KModifier.OPEN)
        }
        if (KotlinProjectionModifier.Sealed in plan.modifiers) {
            builder.addKdoc(
                "WinRT sealed runtime class shell emitted as a regular Kotlin class because Kotlin sealed constructors would block RCW wrapping and activation.\n",
            )
        }
        val constructorBuilder = FunSpec.constructorBuilder()
            .addModifiers(plan.runtimeClassWrapperConstructorVisibility())
            .addParameter("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
            .addParameter("__winrtWrapper", UNIT)
        val supportsDerivedComposableConstruction = plan.supportsDerivedComposableConstruction()
        if (supportsDerivedComposableConstruction) {
            builder.addSuperinterface(WINRT_COMPOSABLE_OBJECT_CLASS_NAME)
        }
        plan.runtimeClassBaseTypeName?.let { baseTypeName ->
            builder.superclass(resolveTypeName(baseTypeName))
            if (!supportsDerivedComposableConstruction) {
                builder.addSuperclassConstructorParameter("_inner")
                builder.addSuperclassConstructorParameter("kotlin.Unit")
            }
        }
        if (supportsDerivedComposableConstruction) {
            builder.addProperty(
                PropertySpec.builder("_innerStorage", IINSPECTABLE_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("_composableReference", WINRT_COMPOSABLE_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.PRIVATE)
                    .mutable(true)
                    .initializer("null")
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("winRTComposableObjectReference", WINRT_COMPOSABLE_OBJECT_REFERENCE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return _composableReference")
                            .build(),
                    )
                    .build(),
            )
            builder.addProperty(
                PropertySpec.builder("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("_innerStorage?.let { return it }")
                            .addStatement("return nativeObject.asInspectable()")
                            .build(),
                    )
                    .build(),
            )
            builder.addFunction(
                constructorBuilder
                    .apply {
                        plan.runtimeClassBaseTypeName?.let {
                            callSuperConstructor("_inner", "kotlin.Unit")
                        }
                    }
                    .addStatement("this._innerStorage = _inner")
                    .build(),
            )
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.PROTECTED)
                    .addParameter("__derivedComposed", DERIVED_COMPOSED_CLASS_NAME)
                    .apply {
                        plan.runtimeClassBaseTypeName?.let {
                            callSuperConstructor("__derivedComposed")
                        }
                    }
                    .build(),
            )
        } else {
            builder.primaryConstructor(constructorBuilder.build())
            builder.addProperty(
                PropertySpec.builder("_inner", IINSPECTABLE_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("_inner")
                    .build(),
            )
        }
        builder.addProperty(
                PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                .addModifiers(KModifier.OVERRIDE)
                .apply {
                    if (plan.requiresOpenRuntimeClassShell()) {
                        addModifiers(KModifier.OPEN)
                    }
                }
                .getter(
                    FunSpec.getterBuilder()
                        .addCode("return _inner\n")
                        .build(),
                )
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("hasUnwrappableNativeObject", BOOLEAN)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .apply {
                            if (KotlinProjectionModifier.Sealed in plan.modifiers) {
                                addStatement("return true")
                            } else {
                                addStatement("return this::class == %T::class", resolveTypeName(plan.type.qualifiedName))
                            }
                        }
                        .build(),
                )
                .build(),
        )
        if (KotlinProjectionCompanionKind.ComposableFactory in plan.companionKinds && !supportsDerivedComposableConstruction) {
            builder.addInitializerBlock(
                CodeBlock.of("%T.registerComposableWrapper(this, _inner)\n", COM_WRAPPERS_SUPPORT_CLASS_NAME),
            )
        }
        addRuntimeClassIdentityMembers(builder, plan)
        val objectReferencePlansByInterface = plan.objectReferenceSurfaceDescriptor
            ?.objectReferencePlans
            .orEmpty()
            .associateBy { it.interfaceName.substringBefore('<') }
        val defaultObjectReferencePlan = plan.defaultInterfaceName
            ?.substringBefore('<')
            ?.let(objectReferencePlansByInterface::get)
        val defaultInterfaceIsRuntimeOwnedMapped = plan.defaultInterfaceName
            ?.let(::isRuntimeOwnedMappedTypeName) == true
        if (!defaultInterfaceIsRuntimeOwnedMapped &&
            (plan.defaultInterfaceIid != null ||
                (defaultObjectReferencePlan != null && defaultObjectReferencePlan.skippedReason == null) ||
                isMappedCollectionInterfaceName(plan.defaultInterfaceName.orEmpty()))
        ) {
            val defaultCacheType = if (
                plan.runtimeClassBaseTypeName != null ||
                plan.requiresOpenRuntimeClassShell() ||
                defaultObjectReferencePlan?.usesInner == true ||
                defaultObjectReferencePlan?.usesDefaultInterfaceObjRef == true
            ) {
                COM_OBJECT_REFERENCE_CLASS_NAME
            } else {
                IUNKNOWN_REFERENCE_CLASS_NAME
            }
            builder.addProperty(
                PropertySpec.builder("_defaultInterface", defaultCacheType)
                    .addModifiers(KModifier.PRIVATE)
                    .apply {
                        if (defaultObjectReferencePlan != null) {
                            delegate(
                                runtimeClassObjectReferenceCacheInitializer(
                                    defaultObjectReferencePlan,
                                    plan.typesByQualifiedName,
                                    "Metadata.acquireInterface(_inner, %T.Metadata.IID)",
                                    projectionClassName(defaultObjectReferencePlan.interfaceName.substringBefore('<')),
                                ),
                            )
                        } else {
                            delegate(
                                runtimeClassObjectReferenceCacheInitializer(defaultObjectReferencePlan, plan.typesByQualifiedName, "Metadata.acquireDefaultInterface(_inner)"),
                            )
                        }
                    }
                    .build(),
            )
        }
        plan.implementedInterfaceBindings
            .filter { it.iid != null }
            .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
            .filter { binding ->
                objectReferencePlansByInterface[binding.qualifiedName.substringBefore('<')]?.skippedReason == null
            }
            .forEach { binding ->
                val objectReferencePlan = objectReferencePlansByInterface[binding.qualifiedName.substringBefore('<')]
                val acquireExpression =
                    if (plan.composableFactoryBindings.isNotEmpty() && plan.isOverridableRuntimeClassInterface(binding.qualifiedName)) {
                        "Metadata.acquireInterface(winRTComposableObjectReference?.inner ?: _inner, %T.Metadata.IID)"
                    } else {
                        "Metadata.acquireInterface(_inner, %T.Metadata.IID)"
                    }
                builder.addProperty(
                    PropertySpec.builder(
                        "_${binding.qualifiedName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                    )
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            runtimeClassObjectReferenceCacheInitializer(
                                objectReferencePlan,
                                plan.typesByQualifiedName,
                                acquireExpression,
                                projectionClassName(binding.qualifiedName.substringBefore('<')),
                            ),
                        )
                        .build(),
                )
        }
        requiredInterfaceCacheBindings(plan)
            .filter { it.iid != null }
            .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
            .forEach { binding ->
                builder.addProperty(
                    PropertySpec.builder(
                        "_${binding.qualifiedName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}",
                        IUNKNOWN_REFERENCE_CLASS_NAME,
                    )
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy(%T.PUBLICATION) { Metadata.acquireInterface(_inner, %L) }",
                                LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                                runtimeClassInterfaceIdCode(binding.qualifiedName, plan),
                            ),
                        )
                        .build(),
                )
            }
        addRuntimeClassCollectionInterfaceCaches(builder, plan)
        val delegatedInterfaceTargets = runtimeClassDelegatedInterfaceTargets(plan)
        plan.defaultInterfaceName
            ?.takeUnless(::isMappedCollectionInterfaceName)
            ?.takeUnless(::isRuntimeOwnedMappedTypeName)
            ?.takeUnless(String::isMappedRuntimeHelperInterfaceName)
            ?.takeIf { interfaceName -> plan.isPublicRuntimeClassInterface(interfaceName) }
            ?.let { defaultInterfaceName ->
            builder.addRuntimeClassSuperinterface(
                defaultInterfaceName,
                delegatedInterfaceTargets[defaultInterfaceName.substringBefore('<').removeSuffix("?")],
            )
        }
        plan.type.implementedInterfaces
            .filterNot { it.isDefault }
            .filter { implemented -> plan.isPublicRuntimeClassInterface(implemented.interfaceName) }
            .filterNot { implemented ->
                isMappedCollectionInterfaceName(implemented.interfaceName)
            }
            .filterNot { implemented ->
                isRuntimeOwnedMappedTypeName(implemented.interfaceName)
            }
            .filterNot { implemented ->
                implemented.interfaceName.isMappedRuntimeHelperInterfaceName()
            }
            .filterNot { implemented ->
                plan.defaultInterfaceName?.let { defaultInterfaceName ->
                    winAppSdkCoveredApplicationModelCoreInterface(defaultInterfaceName) ==
                        implemented.interfaceName.substringBefore('<').removeSuffix("?")
                } == true
            }
            .forEach { implemented ->
                builder.addRuntimeClassSuperinterface(
                    implemented.interfaceName,
                    delegatedInterfaceTargets[implemented.interfaceName.substringBefore('<').removeSuffix("?")],
                )
            }
        plan.mutableCollectionBindings.forEach { binding ->
            builder.addProperty(renderMutableCollectionDelegateProperty(binding))
            builder.addSuperinterface(mutableCollectionProjectedType(binding))
            if (binding.kind == KotlinProjectionMutableCollectionKind.Map) {
                builder.addSuperinterface(mapIterableType(binding))
            }
            addMutableCollectionForwardMembers(builder, binding)
        }
        val readOnlyCollectionBindings = plan.readOnlyCollectionBindings.filterNot { readOnlyBinding ->
            plan.mutableCollectionBindings.any { mutableBinding -> mutableBinding.covers(readOnlyBinding) }
        }
        readOnlyCollectionBindings.forEach { binding ->
            builder.addProperty(renderReadOnlyCollectionDelegateProperty(binding))
            builder.addSuperinterface(readOnlyCollectionProjectedType(binding))
            addReadOnlyCollectionForwardMembers(builder, binding)
        }
        if (plan.usesMappedDataErrorInfoAugmentation) {
            builder.addSuperinterface(plan.dataErrorInfoClassName)
        }
        if (plan.usesMappedDataErrorInfoAugmentation) {
            addMappedDataErrorInfoForwardMembers(builder, plan)
        }
        if (plan.usesMappedPropertyChangedAugmentation) {
            builder.addSuperinterface(plan.propertyChangedNotifierClassName)
            addMappedPropertyChangedForwardMembers(builder, plan)
        }
        if (plan.usesMappedDisposableAugmentation) {
            builder.addSuperinterface(AUTO_CLOSEABLE_CLASS_NAME)
        }
        val requiredIteratorBinding = requiredIteratorBinding(plan)
        requiredIteratorBinding?.let { iteratorBinding ->
            addRequiredIteratorForwardMembers(builder, iteratorBinding)
        }
        addRuntimeClassInterfaceProjectionCaches(builder, plan)
        val requiredForwardSuppressedMemberNames = if (requiredIteratorBinding != null) {
            mappedCollectionMemberNames(plan) + requiredIteratorMemberNames
        } else {
            mappedCollectionMemberNames(plan)
        }
        renderRequiredInterfaceForwardMembers(plan, requiredForwardSuppressedMemberNames).forEach { member ->
            when (member) {
                is TypeSpec -> error("Nested required-interface members are not supported.")
                is FunSpec -> builder.addFunction(member)
                is PropertySpec -> builder.addProperty(member)
                else -> Unit
            }
        }
        builder.addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
        if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
            builder.addFunction(
                FunSpec.constructorBuilder()
                    .callThisConstructor(CodeBlock.of("%T.activateInstance(Metadata.TYPE_NAME), kotlin.Unit", ACTIVATION_FACTORY_CLASS_NAME))
                    .build(),
            )
            renderFactoryConstructors(plan).forEach(builder::addFunction)
        }
        renderComposableConstructors(plan).forEach(builder::addFunction)
        val mappedCollectionMemberNames = mappedCollectionMemberNames(plan)
        plan.type.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .filterNot { it.isMappedCollectionRuntimeMethod(plan, mappedCollectionMemberNames) }
            .filterNot { method -> isRuntimeClassDelegatedMember(plan, method.abiSlotConstantName(plan.type.methods)) }
            .filterNot { method ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) }
                    ?.isRuntimeOwnedMappedBinding == true
            }
            .filterNot { method ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) }
                    ?.isMappedRuntimeHelperBinding == true
            }
            .filterNot { plan.usesMappedDisposableAugmentation && it.name == "Close" && it.parameters.isEmpty() }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "GetErrors" }
            .forEach { method ->
                builder.addFunction(renderRuntimeClassInterfaceForwardMethod(plan, method) ?: renderRuntimeMethod(plan, method))
            }
        addRuntimeClassAuthoringInvokeBridges(builder, plan, mappedCollectionMemberNames)
        plan.type.properties
            .filterNot { it.isStatic }
            .filter { it.hasNativeProjectionGetterAccessor() }
            .filterNot { it.isMappedCollectionRuntimeProperty(plan, mappedCollectionMemberNames) }
            .filterNot { property -> isRuntimeClassDelegatedMember(plan, "${property.name.uppercase()}_GETTER_SLOT") }
            .filterNot { property ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == "${property.name.uppercase()}_GETTER_SLOT" }
                    ?.isRuntimeOwnedMappedBinding == true
            }
            .filterNot { property ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == "${property.name.uppercase()}_GETTER_SLOT" }
                    ?.isMappedRuntimeHelperBinding == true
            }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "HasErrors" }
            .forEach { property ->
                builder.addProperty(renderRuntimeClassInterfaceForwardProperty(plan, property) ?: renderRuntimeProperty(plan, property))
            }
        plan.type.events
            .filterNot { it.isStatic }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "ErrorsChanged" }
            .filterNot { plan.usesMappedPropertyChangedAugmentation && it.name == "PropertyChanged" }
            .filterNot { event -> isRuntimeClassDelegatedMember(plan, "${event.name.uppercase()}_ADD_SLOT") }
            .filterNot { event ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == "${event.name.uppercase()}_ADD_SLOT" }
                    ?.isRuntimeOwnedMappedBinding == true
            }
            .filterNot { event ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == "${event.name.uppercase()}_ADD_SLOT" }
                    ?.isMappedRuntimeHelperBinding == true
            }
            .forEach { event ->
            val forwardEvent = renderRuntimeClassInterfaceForwardEvent(plan, event)
            if (forwardEvent != null) {
                builder.addProperty(forwardEvent.property)
                forwardEvent.functions.forEach(builder::addFunction)
                return@forEach
            }
            val addBinding = plan.instanceMemberBindings.firstOrNull {
                it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
            }
            val eventModifiers = addBinding?.let { runtimeClassMemberModifiers(plan, it) } ?: listOf(KModifier.OVERRIDE)
            builder.addProperty(
                renderEventProperty(
                    event = event,
                    eventInvokeDescriptor = plan.eventInvokeDescriptors.firstOrNull { it.eventName == event.name && !it.isStatic },
                    abstract = false,
                    override = true,
                    modifiers = eventModifiers,
                    eventSourceOwnerTypeName = addBinding?.slotInterfaceQualifiedName,
                    eventSourceEventTypeName = addBinding?.let { binding ->
                        plan.typesByQualifiedName[binding.slotInterfaceQualifiedName]
                            ?.events
                            ?.firstOrNull { rawEvent -> rawEvent.name == event.name }
                            ?.delegateTypeName
                    },
                    eventSourceObjectReference = addBinding?.let { CodeBlock.of(it.ownerCachePropertyName) },
                    eventSourceAddSlot = addBinding?.let {
                        metadataSlotExpression(it.slotInterfaceQualifiedName, it.slotConstantName)
                    },
                    fallbackToAddRemove = addBinding == null,
                ),
            )
            (renderBoundEventFunctions(plan, event, override = true) ?: renderEventFunctions(event, abstract = false, override = true))
                .forEach(builder::addFunction)
        }
        if (plan.usesMappedDisposableAugmentation) {
            builder.addFunction(
                FunSpec.builder("close")
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode("%T(_inner).close()\n", WINRT_CLOSABLE_OBJECT_CLASS_NAME)
                    .build(),
            )
        }
        val staticMethods = plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedStaticMethod)
        val staticProperties = plan.type.properties.filter { it.isStatic }
        val staticEvents = plan.type.events.filter { it.isStatic }
        if (staticMethods.isNotEmpty() || staticProperties.isNotEmpty() || staticEvents.isNotEmpty() ||
            KotlinProjectionCompanionKind.Metadata in plan.companionKinds) {
            builder.addType(buildMetadataCompanionShell(plan, staticMethods, staticProperties, staticEvents))
        }
        appendCompanionShells(builder, plan, excludeKinds = setOf(KotlinProjectionCompanionKind.Metadata))
        return builder.build()
    }

    private fun addRuntimeClassAuthoringInvokeBridges(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
        mappedCollectionMemberNames: Set<String>,
    ) {
        plan.type.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .filterNot { it.isMappedCollectionRuntimeMethod(plan, mappedCollectionMemberNames) }
            .filterNot { method -> isRuntimeClassDelegatedMember(plan, method.abiSlotConstantName(plan.type.methods)) }
            .filterNot { method ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) }
                    ?.isRuntimeOwnedMappedBinding == true
            }
            .filterNot { method ->
                plan.instanceMemberBindings
                    .firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) }
                    ?.isMappedRuntimeHelperBinding == true
            }
            .filterNot { plan.usesMappedDisposableAugmentation && it.name == "Close" && it.parameters.isEmpty() }
            .filterNot { plan.usesMappedDataErrorInfoAugmentation && it.name == "GetErrors" }
            .mapNotNull { method ->
                val binding = plan.instanceMemberBindings.firstOrNull {
                    it.bindingName == method.abiSlotConstantName(plan.type.methods)
                } ?: return@mapNotNull null
                val modifiers = runtimeClassMemberModifiers(plan, binding)
                if (KModifier.PROTECTED !in modifiers) {
                    return@mapNotNull null
                }
                renderRuntimeClassAuthoringInvokeBridge(method, method.projectedRuntimeClassMethodName(plan, modifiers))
            }
            .forEach(builder::addFunction)
    }

    private fun renderRuntimeClassAuthoringInvokeBridge(
        method: WinRTMethodDefinition,
        methodName: String,
    ): FunSpec {
        val parameterSpecs = method.projectedKotlinParameters().map { parameter ->
            ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build()
        }
        val arguments = parameterSpecs.joinToString(", ") { parameter -> parameter.name.escapeAsKotlinIdentifierIfNeeded() }
        val returns = if (isWinRTVoidTypeName(method.projectedKotlinReturnTypeName())) {
            UNIT
        } else {
            resolveTypeName(method.projectedKotlinReturnTypeName())
        }
        return FunSpec.builder(authoringInvokeBridgeName(method))
            .addParameters(parameterSpecs)
            .returns(returns)
            .apply {
                if (returns == UNIT) {
                    addCode("%L(%L)\n", methodName, arguments)
                } else {
                    addCode("return %L(%L)\n", methodName, arguments)
                }
            }
            .build()
    }

    private fun addRuntimeClassInterfaceProjectionCaches(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        if (suppressProjectedMemberSlotConstants) {
            return
        }
        val delegatedTargets = runtimeClassDelegatedInterfaceTargets(plan).keys
        runtimeClassInterfaceProjectionForwardTargets(plan).values
            .filterNot { it.interfaceName in delegatedTargets }
            .distinctBy { it.projectionPropertyName }
            .sortedBy { it.projectionPropertyName }
            .forEach { target ->
                val initializer = if (target.ownerCachePropertyName == "_defaultInterface") {
                    val rawInterfaceType = projectionClassName(target.rawInterfaceName)
                    CodeBlock.of(
                        "lazy(%T.PUBLICATION) { %T.Metadata.wrap(Metadata.acquireInterface(_inner, %T.Metadata.IID)) }",
                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        rawInterfaceType,
                        rawInterfaceType,
                    )
                } else {
                    val rawInterfaceType = projectionClassName(target.rawInterfaceName)
                    CodeBlock.of(
                        "lazy(%T.PUBLICATION) { %T.Metadata.wrap(%L) }",
                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        rawInterfaceType,
                        target.ownerCachePropertyName,
                    )
                }
                builder.addProperty(
                    PropertySpec.builder(target.projectionPropertyName, resolveTypeName(target.interfaceName))
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(initializer)
                        .build(),
                )
            }
    }

    private fun runtimeClassInterfaceProjectionDelegate(
        target: RuntimeClassInterfaceProjectionForwardTarget,
    ): CodeBlock =
        CodeBlock.of(
            "%T.Metadata.wrap(%L)",
            resolveTypeName(target.rawInterfaceName),
            target.ownerCachePropertyName,
        )

    private fun TypeSpec.Builder.addRuntimeClassSuperinterface(
        interfaceName: String,
        target: RuntimeClassInterfaceProjectionForwardTarget?,
    ) {
        val typeName = resolveTypeName(interfaceName)
        if (target == null) {
            addSuperinterface(typeName)
        } else {
            addSuperinterface(typeName, runtimeClassInterfaceProjectionDelegate(target))
        }
    }

    private fun renderRuntimeClassInterfaceForwardMethod(
        plan: KotlinTypeProjectionPlan,
        method: WinRTMethodDefinition,
    ): FunSpec? {
        if (suppressProjectedMemberSlotConstants) {
            return null
        }
        val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == method.abiSlotConstantName(plan.type.methods) }
            ?: return null
        val target = runtimeClassInterfaceProjectionForwardTargets(plan)[binding.ownerInterfaceQualifiedName.substringBefore('<')]
            ?: return null
        val objectShape = runtimeObjectMethodShape(method)
        val modifiers = objectShape?.let { listOf(KModifier.OVERRIDE) } ?: runtimeClassMemberModifiers(plan, binding)
        val functionName = objectShape?.name ?: method.projectedRuntimeClassMethodName(plan, modifiers)
        val targetFunctionName = objectShape?.name ?: method.projectedMethodName()
        val parameterSpecs = objectShape?.parameters ?: method.projectedKotlinParameters().map { parameter ->
            ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build()
        }
        val arguments = parameterSpecs.joinToString(", ") { parameter -> parameter.name.escapeAsKotlinIdentifierIfNeeded() }
        val returns = objectShape?.returnType ?: resolveTypeName(method.projectedKotlinReturnTypeName())
        return FunSpec.builder(functionName)
            .addProjectedAttributeAnnotations(binding.projectedAttributes)
            .addMethodGenericParameters(method, objectShape)
            .addModifiers(modifiers)
            .addParameters(parameterSpecs)
            .returns(returns)
            .apply {
                if (objectShape?.kind == RuntimeObjectMethodKind.Equals) {
                    addCode("if (other !is %T) return false\n", IWINRT_OBJECT_CLASS_NAME)
                }
                if (returns == UNIT) {
                    addCode("%L.%L(%L)\n", target.projectionPropertyName, targetFunctionName, arguments)
                } else {
                    addCode("return %L.%L(%L)\n", target.projectionPropertyName, targetFunctionName, arguments)
                }
            }
            .build()
    }

    private fun renderRuntimeClassInterfaceForwardProperty(
        plan: KotlinTypeProjectionPlan,
        property: WinRTPropertyDefinition,
    ): PropertySpec? {
        if (suppressProjectedMemberSlotConstants) {
            return null
        }
        val getterBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${property.name.uppercase()}_GETTER_SLOT"
        } ?: return null
        val target = runtimeClassInterfaceProjectionForwardTargets(plan)[getterBinding.ownerInterfaceQualifiedName.substringBefore('<')]
            ?: return null
        val propertyName = property.name.replaceFirstChar(Char::lowercase)
        val propertyTypeName = property.projectedPropertyTypeName(getterBinding.ownerInterfaceQualifiedName, plan.typesByQualifiedName)
        val builder = PropertySpec.builder(propertyName, resolveTypeName(propertyTypeName))
            .mutable(!property.isReadOnly)
            .addProjectedAttributeAnnotations(getterBinding.projectedAttributes)
            .addModifiers(runtimeClassMemberModifiers(plan, getterBinding))
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return %L.%N\n", target.projectionPropertyName, propertyName)
                    .build(),
            )
        if (!property.isReadOnly) {
            val setterBinding = plan.instanceMemberBindings.firstOrNull {
                it.bindingName == "${property.name.uppercase()}_SETTER_SLOT"
            } ?: return null
            if (setterBinding.ownerInterfaceQualifiedName.substringBefore('<') != getterBinding.ownerInterfaceQualifiedName.substringBefore('<')) {
                return null
            }
            val setterTarget = runtimeClassInterfaceProjectionForwardTargets(plan)[setterBinding.ownerInterfaceQualifiedName.substringBefore('<')]
                ?: return null
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", resolveTypeName(propertyTypeName))
                    .addCode("%L.%N=value\n", setterTarget.projectionPropertyName, propertyName)
                    .build(),
            )
        }
        return builder.build()
    }

    private data class RuntimeClassForwardEvent(
        val property: PropertySpec,
        val functions: List<FunSpec>,
    )

    private fun renderRuntimeClassInterfaceForwardEvent(
        plan: KotlinTypeProjectionPlan,
        event: WinRTEventDefinition,
    ): RuntimeClassForwardEvent? {
        if (suppressProjectedMemberSlotConstants) {
            return null
        }
        val addBinding = plan.instanceMemberBindings.firstOrNull {
            it.bindingName == "${event.name.uppercase()}_ADD_SLOT"
        } ?: return null
        val target = runtimeClassInterfaceProjectionForwardTargets(plan)[addBinding.ownerInterfaceQualifiedName.substringBefore('<')]
            ?: return null
        val eventName = event.name.replaceFirstChar(Char::lowercase)
        val delegateTypeName = resolveTypeName(event.delegateTypeName)
        val property = PropertySpec.builder(eventName, WINRT_EVENT_CLASS_NAME.parameterizedBy(delegateTypeName))
            .addModifiers(runtimeClassMemberModifiers(plan, addBinding))
            .getter(
                FunSpec.getterBuilder()
                    .addCode("return %L.%L\n", target.projectionPropertyName, eventName)
                    .build(),
            )
            .build()
        val functions = listOf(
            FunSpec.builder("add${event.name}")
                .addModifiers(runtimeClassMemberModifiers(plan, addBinding))
                .addParameter("handler", delegateTypeName)
                .returns(EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("return %L.add%L(handler)\n", target.projectionPropertyName, event.name)
                .build(),
            FunSpec.builder("remove${event.name}")
                .addModifiers(runtimeClassMemberModifiers(plan, addBinding))
                .addParameter("token", EVENT_REGISTRATION_TOKEN_CLASS_NAME)
                .addCode("%L.remove%L(token)\n", target.projectionPropertyName, event.name)
                .build(),
        )
        return RuntimeClassForwardEvent(property, functions)
    }

    private data class RuntimeClassInterfaceProjectionForwardTarget(
        val rawInterfaceName: String,
        val interfaceName: String,
        val ownerCachePropertyName: String,
        val projectionPropertyName: String,
    )

    private fun runtimeClassDelegatedInterfaceTargets(
        plan: KotlinTypeProjectionPlan,
    ): Map<String, RuntimeClassInterfaceProjectionForwardTarget> =
        if (plan.canUseRuntimeClassInterfaceDelegation()) {
            runtimeClassInterfaceProjectionForwardTargets(plan)
                .filterKeys { interfaceName -> plan.isPublicRuntimeClassInterface(interfaceName) }
        } else {
            emptyMap()
        }

    private fun KotlinTypeProjectionPlan.canUseRuntimeClassInterfaceDelegation(): Boolean = false

    internal fun isRuntimeClassDelegatedInterface(
        plan: KotlinTypeProjectionPlan,
        interfaceName: String,
    ): Boolean =
        interfaceName.substringBefore('<').removeSuffix("?") in runtimeClassDelegatedInterfaceTargets(plan)

    private fun isRuntimeClassDelegatedMember(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
    ): Boolean {
        val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == bindingName } ?: return false
        return isRuntimeClassDelegatedInterface(plan, binding.ownerInterfaceQualifiedName)
    }

    private fun runtimeClassInterfaceProjectionForwardTargets(
        plan: KotlinTypeProjectionPlan,
    ): Map<String, RuntimeClassInterfaceProjectionForwardTarget> {
        val ownerInterfaceBindings = plan.instanceMemberBindings
            .distinctBy { binding -> binding.ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?") }
        return ownerInterfaceBindings.mapNotNull { binding ->
            val rawInterfaceName = binding.ownerInterfaceQualifiedName.substringBefore('<').removeSuffix("?")
            if (isMappedCollectionInterfaceName(rawInterfaceName) || isRuntimeOwnedMappedTypeName(rawInterfaceName)) {
                return@mapNotNull null
            }
            if (rawInterfaceName.isMappedRuntimeHelperInterfaceName()) {
                return@mapNotNull null
            }
            val interfaceType = plan.typesByQualifiedName[rawInterfaceName] ?: return@mapNotNull null
            if (interfaceType.kind != WinRTTypeKind.Interface) {
                return@mapNotNull null
            }
            val interfacePlan = plan.copy(
                type = interfaceType,
                declarationKind = KotlinProjectionDeclarationKind.Interface,
                defaultInterfaceName = null,
                defaultInterfaceIid = null,
                staticInterfaceNames = emptyList(),
                staticInterfaceBindings = emptyList(),
                implementedInterfaceBindings = emptyList(),
                instanceMemberBindings = emptyList(),
                staticMemberBindings = emptyList(),
                classMemberMergeDescriptor = null,
            )
            if (!canRenderInterfaceProxy(interfacePlan)) {
                return@mapNotNull null
            }
            rawInterfaceName to RuntimeClassInterfaceProjectionForwardTarget(
                rawInterfaceName = rawInterfaceName,
                interfaceName = binding.ownerInterfaceQualifiedName,
                ownerCachePropertyName = binding.ownerCachePropertyName,
                projectionPropertyName = "_${interfaceType.name.replaceFirstChar(Char::lowercase)}Projection",
            )
        }.toMap()
    }

    private val KotlinTypeProjectionPlan.runtimeClassBaseTypeName: String?
        get() = type.baseTypeName
            ?.takeUnless(::isWinRTObjectTypeName)

    private fun KotlinTypeProjectionPlan.isPublicRuntimeClassInterface(interfaceName: String): Boolean {
        val rawName = interfaceName.substringBefore('<').removeSuffix("?")
        val descriptor = classMemberMergeDescriptor
            ?.interfaceDescriptors
            ?.firstOrNull { it.interfaceTypeName == rawName }
        return descriptor?.let { !it.isOverridableInterface && !it.isProtectedInterface } ?: true
    }

    private fun KotlinTypeProjectionPlan.isOverridableRuntimeClassInterface(interfaceName: String): Boolean {
        val rawName = interfaceName.substringBefore('<').removeSuffix("?")
        return type.implementedInterfaces.any { implementation ->
            implementation.interfaceName == rawName && implementation.isOverridable
        } ||
            classMemberMergeDescriptor
            ?.interfaceDescriptors
            ?.any { descriptor -> descriptor.interfaceTypeName == rawName && descriptor.isOverridableInterface } == true
    }

    private fun addRuntimeClassIdentityMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        if (plan.type.methods.none { it.isObjectEquals }) {
            builder.addFunction(
                FunSpec.builder("equals")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("other", ANY.copy(nullable = true))
                    .returns(Boolean::class)
                    .addCode(
                        "if (other !is %T) {\nreturn false\n}\nreturn nativeObject.pointer == other.nativeObject.pointer\n",
                        projectionClassName(plan.type.qualifiedName),
                    )
                    .build(),
            )
        }
        if (plan.type.methods.none { it.isObjectGetHashCode }) {
            builder.addFunction(
                FunSpec.builder("hashCode")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(Int::class)
                    .addCode("return nativeObject.pointer.hashCode()\n")
                    .build(),
            )
        }
    }

    private fun addMutableCollectionForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        when (binding.kind) {
            KotlinProjectionMutableCollectionKind.Vector -> addMutableListForwardMembers(builder, binding)
            KotlinProjectionMutableCollectionKind.Map -> addMutableMapForwardMembers(builder, binding)
        }
    }

    private fun KotlinProjectionMutableCollectionBinding.covers(
        readOnlyBinding: KotlinProjectionReadOnlyCollectionBinding,
    ): Boolean = when (kind) {
        KotlinProjectionMutableCollectionKind.Vector ->
            readOnlyBinding.kind in setOf(KotlinProjectionReadOnlyCollectionKind.Iterable, KotlinProjectionReadOnlyCollectionKind.VectorView)
        KotlinProjectionMutableCollectionKind.Map ->
            readOnlyBinding.kind == KotlinProjectionReadOnlyCollectionKind.MapView
    }

    private fun addReadOnlyCollectionForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionReadOnlyCollectionBinding,
    ) {
        when (binding.kind) {
            KotlinProjectionReadOnlyCollectionKind.Iterable -> {
                val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
                builder.addFunction(
                    FunSpec.builder("iterator")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(Iterator::class.asClassName().parameterizedBy(elementType))
                        .addCode("return %L.iterator()\n", binding.delegatePropertyName)
                        .build(),
                )
            }
            KotlinProjectionReadOnlyCollectionKind.VectorView -> {
                val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
                builder.addProperty(
                    PropertySpec.builder("size", Int::class)
                        .addModifiers(KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build())
                        .build(),
                )
                builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("contains").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Boolean::class).addCode("return %L.contains(element)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("containsAll").addModifiers(KModifier.OVERRIDE).addParameter("elements", Collection::class.asClassName().parameterizedBy(elementType)).returns(Boolean::class).addCode("return %L.containsAll(elements)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("iterator").addModifiers(KModifier.OVERRIDE).returns(Iterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.iterator()\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).returns(ListIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.listIterator()\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(ListIterator::class.asClassName().parameterizedBy(elementType)).addCode("return %L.listIterator(index)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("subList").addModifiers(KModifier.OVERRIDE).addParameter("fromIndex", Int::class).addParameter("toIndex", Int::class).returns(List::class.asClassName().parameterizedBy(elementType)).addCode("return %L.subList(fromIndex, toIndex)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(elementType).addCode("return %L[index]\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("indexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.indexOf(element)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("lastIndexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.lastIndexOf(element)\n", binding.delegatePropertyName).build())
            }
            KotlinProjectionReadOnlyCollectionKind.MapView -> {
                val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
                val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
                val entryType = Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)
                builder.addProperty(PropertySpec.builder("size", Int::class).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build()).build())
                builder.addProperty(PropertySpec.builder("entries", Set::class.asClassName().parameterizedBy(entryType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.entries\n", binding.delegatePropertyName).build()).build())
                builder.addProperty(PropertySpec.builder("keys", Set::class.asClassName().parameterizedBy(keyType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.keys\n", binding.delegatePropertyName).build()).build())
                builder.addProperty(PropertySpec.builder("values", Collection::class.asClassName().parameterizedBy(valueType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.values\n", binding.delegatePropertyName).build()).build())
                builder.addFunction(FunSpec.builder("containsKey").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(Boolean::class).addCode("return %L.containsKey(key)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("containsValue").addModifiers(KModifier.OVERRIDE).addParameter("value", valueType).returns(Boolean::class).addCode("return %L.containsValue(value)\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L[key]\n", binding.delegatePropertyName).build())
                builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
            }
        }
    }

    private fun addMutableListForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        val elementType = resolveTypeName(requireNotNull(binding.elementBinding).typeName)
        val collectionType = Collection::class.asClassName().parameterizedBy(elementType)
        val mutableListType = MUTABLE_LIST_CLASS_NAME.parameterizedBy(elementType)
        builder.addProperty(
            PropertySpec.builder("size", Int::class)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build())
                .build(),
        )
        fun forwardBoolean(name: String, parameterName: String, parameterType: TypeName) {
            builder.addFunction(
                FunSpec.builder(name)
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(parameterName, parameterType)
                    .returns(Boolean::class)
                    .addCode("return %L.%L(%L)\n", binding.delegatePropertyName, name, parameterName)
                    .build(),
            )
        }
        forwardBoolean("contains", "element", elementType)
        forwardBoolean("containsAll", "elements", collectionType)
        forwardBoolean("add", "element", elementType)
        forwardBoolean("addAll", "elements", collectionType)
        builder.addFunction(
            FunSpec.builder("addAll")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("index", Int::class)
                .addParameter("elements", collectionType)
                .returns(Boolean::class)
                .addCode("return %L.addAll(index, elements)\n", binding.delegatePropertyName)
                .build(),
        )
        forwardBoolean("remove", "element", elementType)
        forwardBoolean("removeAll", "elements", collectionType)
        forwardBoolean("retainAll", "elements", collectionType)
        builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("iterator").addModifiers(KModifier.OVERRIDE).returns(MUTABLE_ITERATOR_CLASS_NAME.parameterizedBy(elementType)).addCode("return %L.iterator()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).returns(MUTABLE_LIST_ITERATOR_CLASS_NAME.parameterizedBy(elementType)).addCode("return %L.listIterator()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("listIterator").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(MUTABLE_LIST_ITERATOR_CLASS_NAME.parameterizedBy(elementType)).addCode("return %L.listIterator(index)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("subList").addModifiers(KModifier.OVERRIDE).addParameter("fromIndex", Int::class).addParameter("toIndex", Int::class).returns(mutableListType).addCode("return %L.subList(fromIndex, toIndex)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(elementType).addCode("return %L[index]\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("indexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.indexOf(element)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("lastIndexOf").addModifiers(KModifier.OVERRIDE).addParameter("element", elementType).returns(Int::class).addCode("return %L.lastIndexOf(element)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("add").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).addParameter("element", elementType).addCode("%L.add(index, element)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("clear").addModifiers(KModifier.OVERRIDE).addCode("%L.clear()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("removeAt").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).returns(elementType).addCode("return %L.removeAt(index)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("set").addModifiers(KModifier.OVERRIDE).addParameter("index", Int::class).addParameter("element", elementType).returns(elementType).addCode("return %L.set(index, element)\n", binding.delegatePropertyName).build())
    }

    private fun addMutableMapForwardMembers(
        builder: TypeSpec.Builder,
        binding: KotlinProjectionMutableCollectionBinding,
    ) {
        val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
        val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
        val entryType = MUTABLE_MAP_CLASS_NAME.nestedClass("MutableEntry").parameterizedBy(keyType, valueType)
        builder.addProperty(PropertySpec.builder("size", Int::class).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.size\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("entries", MUTABLE_SET_CLASS_NAME.parameterizedBy(entryType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.entries\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("keys", MUTABLE_SET_CLASS_NAME.parameterizedBy(keyType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.keys\n", binding.delegatePropertyName).build()).build())
        builder.addProperty(PropertySpec.builder("values", MUTABLE_COLLECTION_CLASS_NAME.parameterizedBy(valueType)).addModifiers(KModifier.OVERRIDE).getter(FunSpec.getterBuilder().addCode("return %L.values\n", binding.delegatePropertyName).build()).build())
        builder.addFunction(FunSpec.builder("containsKey").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(Boolean::class).addCode("return %L.containsKey(key)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("containsValue").addModifiers(KModifier.OVERRIDE).addParameter("value", valueType).returns(Boolean::class).addCode("return %L.containsValue(value)\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("get").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L[key]\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("isEmpty").addModifiers(KModifier.OVERRIDE).returns(Boolean::class).addCode("return %L.isEmpty()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("clear").addModifiers(KModifier.OVERRIDE).addCode("%L.clear()\n", binding.delegatePropertyName).build())
        builder.addFunction(FunSpec.builder("put").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).addParameter("value", valueType).returns(valueType.copy(nullable = true)).addCode("return %L.put(key, value)\n", binding.delegatePropertyName).build())
        builder.addFunction(
            FunSpec.builder("putAll")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("from", Map::class.asClassName().parameterizedBy(WildcardTypeName.producerOf(keyType), valueType))
                .addCode("%L.putAll(from)\n", binding.delegatePropertyName)
                .build(),
        )
        builder.addFunction(FunSpec.builder("remove").addModifiers(KModifier.OVERRIDE).addParameter("key", keyType).returns(valueType.copy(nullable = true)).addCode("return %L.remove(key)\n", binding.delegatePropertyName).build())
        builder.addFunction(
            FunSpec.builder("iterator")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Iterator::class.asClassName().parameterizedBy(Map.Entry::class.asClassName().parameterizedBy(keyType, valueType)))
                .addCode("return %L.entries.iterator()\n", binding.delegatePropertyName)
                .build(),
        )
    }

    private fun mapIterableType(binding: KotlinProjectionMutableCollectionBinding): TypeName {
        val keyType = resolveTypeName(requireNotNull(binding.keyBinding).typeName)
        val valueType = resolveTypeName(requireNotNull(binding.valueBinding).typeName)
        return Iterable::class.asClassName().parameterizedBy(Map.Entry::class.asClassName().parameterizedBy(keyType, valueType))
    }

    private val KotlinTypeProjectionPlan.usesMappedDisposableAugmentation: Boolean
        get() = requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("IDisposable")

    private val KotlinTypeProjectionPlan.hasDirectMappedDisposableSuperinterface: Boolean
        get() = sequenceOf(defaultInterfaceName)
            .plus(type.implementedInterfaces.map { it.interfaceName })
            .filterNotNull()
            .map { it.substringBefore('<').removeSuffix("?") }
            .mapNotNull(::mappedTypeByAbiName)
            .any { it.descriptionName == "IClosable" }

    private fun renderNativeProjectionCloseInvocation(): CodeBlock =
        if (useProjectionIntrinsics) {
            CodeBlock.builder()
                .add("%T.callUnit(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
                .indent()
                .add("nativeObject,\n")
                .add("6,\n")
                .add("%S,\n", "")
                .unindent()
                .add(")\n")
                .build()
        } else {
            CodeBlock.of(
                "val __hr = %T.invoke(instance = nativeObject.pointer, slot = 6)\n%T(__hr).requireSuccess()\n",
                COM_VTABLE_INVOKER_CLASS_NAME,
                HRESULT_CLASS_NAME,
            )
        }

    private val KotlinTypeProjectionPlan.usesMappedDataErrorInfoAugmentation: Boolean
        get() = requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("INotifyDataErrorInfo")

    private val KotlinTypeProjectionPlan.usesMappedPropertyChangedAugmentation: Boolean
        get() = requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().contains("INotifyPropertyChanged")

    private val KotlinTypeProjectionPlan.usesWindowsUiXamlNamespace: Boolean
        get() = type.qualifiedName.startsWith("Windows.UI.Xaml.")

    private val KotlinTypeProjectionPlan.dataErrorInfoClassName: ClassName
        get() = if (usesWindowsUiXamlNamespace) WUX_DATA_ERROR_INFO_CLASS_NAME else MUX_DATA_ERROR_INFO_CLASS_NAME

    private val KotlinTypeProjectionPlan.dataErrorsChangedHandlerClassName: ClassName
        get() = if (usesWindowsUiXamlNamespace) WUX_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME else MUX_DATA_ERRORS_CHANGED_HANDLER_CLASS_NAME

    private val KotlinTypeProjectionPlan.propertyChangedNotifierClassName: ClassName
        get() = if (usesWindowsUiXamlNamespace) WUX_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME else MUX_PROPERTY_CHANGED_NOTIFIER_CLASS_NAME

    private val KotlinTypeProjectionPlan.propertyChangedHandlerClassName: ClassName
        get() = if (usesWindowsUiXamlNamespace) WUX_PROPERTY_CHANGED_HANDLER_CLASS_NAME else MUX_PROPERTY_CHANGED_HANDLER_CLASS_NAME

    private fun addMappedDataErrorInfoForwardMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        builder.addProperty(
            PropertySpec.builder("__dataErrorInfo", plan.dataErrorInfoClassName)
                .addModifiers(KModifier.PRIVATE)
                .delegate(
                    CodeBlock.of(
                        "lazy(%T.PUBLICATION) { %T.fromAbi(%L) }",
                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        WINRT_DATA_ERROR_INFO_PROJECTION_CLASS_NAME,
                        "_inner",
                    ),
                )
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("hasErrors", Boolean::class)
                .addModifiers(KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addCode("return __dataErrorInfo.hasErrors\n").build())
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("getErrors")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("propertyName", String::class.asClassName().copy(nullable = true))
                .returns(Iterable::class.asClassName().parameterizedBy(ANY.copy(nullable = true)).copy(nullable = true))
                .addCode("return __dataErrorInfo.getErrors(propertyName)\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("addErrorsChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", plan.dataErrorsChangedHandlerClassName)
                .addCode("__dataErrorInfo.addErrorsChanged(handler)\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("removeErrorsChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", plan.dataErrorsChangedHandlerClassName)
                .addCode("__dataErrorInfo.removeErrorsChanged(handler)\n")
                .build(),
        )
    }

    private fun addMappedPropertyChangedForwardMembers(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        builder.addProperty(
            PropertySpec.builder("__propertyChangedNotifier", plan.propertyChangedNotifierClassName)
                .addModifiers(KModifier.PRIVATE)
                .delegate(
                    CodeBlock.of(
                        "lazy(%T.PUBLICATION) { %T.fromAbi(%L) }",
                        LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                        WINRT_PROPERTY_CHANGED_NOTIFIER_PROJECTION_CLASS_NAME,
                        "_inner",
                    ),
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("addPropertyChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", plan.propertyChangedHandlerClassName)
                .addCode("__propertyChangedNotifier.addPropertyChanged(handler)\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("removePropertyChanged")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("handler", plan.propertyChangedHandlerClassName)
                .addCode("__propertyChangedNotifier.removePropertyChanged(handler)\n")
                .build(),
        )
    }

    private fun WinRTPropertyDefinition.isMappedCollectionRuntimeProperty(
        plan: KotlinTypeProjectionPlan,
        mappedCollectionMemberNames: Set<String>,
    ): Boolean {
        if (name !in mappedCollectionMemberNames) {
            return false
        }
        val getterIsMapped = !hasNativeProjectionGetterAccessor() ||
            plan.instanceMemberBindings
                .firstOrNull { it.bindingName == "${name.uppercase()}_GETTER_SLOT" }
                ?.isMappedCollectionOrIteratorBinding == true
        val setterIsMapped = !hasNativeProjectionSetterAccessor() ||
            plan.instanceMemberBindings
                .firstOrNull { it.bindingName == "${name.uppercase()}_SETTER_SLOT" }
                ?.isMappedCollectionOrIteratorBinding == true
        if (getterIsMapped && setterIsMapped) {
            return name in mappedCollectionRuntimeMethodNames(plan)
        }
        return name in mappedCollectionRuntimeMethodNames(plan) &&
            (plan.mutableCollectionBindings.isNotEmpty() || plan.readOnlyCollectionBindings.isNotEmpty())
    }

    private data class RequiredIteratorBinding(
        val elementBinding: KotlinProjectionAbiTypeBinding,
        val ownerCachePropertyName: String,
    )

    private val requiredIteratorMemberNames = setOf("Current", "HasCurrent", "MoveNext")

    private fun requiredIteratorBinding(plan: KotlinTypeProjectionPlan): RequiredIteratorBinding? {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return null
        }
        return plan.type.implementedInterfaces
            .flatMap { implemented -> collectRequiredForwardInterfaceTypes(implemented.interfaceName, plan, mutableSetOf()) }
            .firstOrNull { required ->
                mappedTypeByAbiName(required.interfaceName.substringBefore('<').removeSuffix("?"))?.descriptionName == "Iterator" &&
                    required.genericArguments.size == 1
            }
            ?.let { required ->
                    required.genericArguments.single()
                    .normalized()
                    .typeName
                    .let { typeName -> renderAbiTypeBinding(typeName, plan.typesByQualifiedName, required.type.namespace) }
                    .takeIf(KotlinProjectionAbiTypeBinding::isSupportedReadOnlyCollectionElementBinding)
                    ?.let { elementBinding ->
                        RequiredIteratorBinding(
                            elementBinding = elementBinding,
                            ownerCachePropertyName = requiredForwardOwnerCache(required.interfaceName, plan.defaultInterfaceName),
                        )
                    }
            }
    }

    private fun addRequiredIteratorForwardMembers(
        builder: TypeSpec.Builder,
        iteratorBinding: RequiredIteratorBinding,
    ) {
        val elementBinding = iteratorBinding.elementBinding
        val elementType = resolveTypeName(elementBinding.typeName)
        builder.addSuperinterface(Iterator::class.asClassName().parameterizedBy(elementType))
        builder.addProperty(
            PropertySpec.builder("__iteratorInitialized", Boolean::class)
                .addModifiers(KModifier.PRIVATE)
                .mutable(true)
                .initializer("false")
                .build(),
        )
        builder.addProperty(
            PropertySpec.builder("__iteratorHasCurrent", Boolean::class)
                .addModifiers(KModifier.PRIVATE)
                .mutable(true)
                .initializer("false")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("hasNext")
                .addModifiers(KModifier.OVERRIDE)
                .returns(Boolean::class)
                .addCode("__ensureIteratorInitialized()\nreturn __iteratorHasCurrent\n")
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("next")
                .addModifiers(KModifier.OVERRIDE)
                .returns(elementType)
                .addCode(
                    """
                    __ensureIteratorInitialized()
                    if (!__iteratorHasCurrent) {
                        throw %T()
                    }
                    val __current = __iteratorCurrent()
                    __iteratorHasCurrent = __iteratorMoveNext()
                    return __current
                    """.trimIndent() + "\n",
                    NO_SUCH_ELEMENT_EXCEPTION_CLASS_NAME,
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__ensureIteratorInitialized")
                .addModifiers(KModifier.PRIVATE)
                .addCode(
                    """
                    if (__iteratorInitialized) {
                        return
                    }
                    __iteratorInitialized = true
                    __iteratorHasCurrent = __readIteratorHasCurrent()
                    """.trimIndent() + "\n",
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__iteratorCurrent")
                .addModifiers(KModifier.PRIVATE)
                .returns(elementType)
                .addCode(
                    "%L\n",
                    renderCollectionInvocation(
                        invokeTargetExpression = iteratorBinding.ownerCachePropertyName,
                        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                        slotConstantName = "CURRENT_GETTER_SLOT",
                        returnBinding = elementBinding,
                    ),
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__readIteratorHasCurrent")
                .addModifiers(KModifier.PRIVATE)
                .returns(Boolean::class)
                .addCode(
                    "%L\n",
                    renderCollectionInvocation(
                        invokeTargetExpression = iteratorBinding.ownerCachePropertyName,
                        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                        slotConstantName = "HASCURRENT_GETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                    ),
                )
                .build(),
        )
        builder.addFunction(
            FunSpec.builder("__iteratorMoveNext")
                .addModifiers(KModifier.PRIVATE)
                .returns(Boolean::class)
                .addCode(
                    "%L\n",
                    renderCollectionInvocation(
                        invokeTargetExpression = iteratorBinding.ownerCachePropertyName,
                        slotInterfaceQualifiedName = "Windows.Foundation.Collections.IIterator",
                        slotConstantName = "MOVENEXT_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Boolean, "Boolean"),
                    ),
                )
                .build(),
        )
    }

    private fun requiredInterfaceCacheBindings(plan: KotlinTypeProjectionPlan): List<KotlinProjectionInterfaceBinding> {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return emptyList()
        }
        val existingCacheNames = buildSet {
            plan.defaultInterfaceName?.let(::add)
            plan.implementedInterfaceBindings.mapTo(this) { it.qualifiedName }
        }
        return plan.type.implementedInterfaces
            .flatMap { implemented ->
                collectRequiredForwardInterfaceTypes(implemented.interfaceName, plan, mutableSetOf())
            }
            .filterNot { required ->
                val mappedType = mappedTypeByAbiName(required.interfaceName.substringBefore('<').removeSuffix("?"))
                mappedType?.descriptionName == "INotifyDataErrorInfo" ||
                    mappedType?.descriptionName == "INotifyPropertyChanged" ||
                    mappedType?.descriptionName == "IClosable" ||
                    mappedType?.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
                    mappedType?.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVectorView ||
                    mappedType?.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVector
            }
            .filterNot { required -> required.interfaceName in existingCacheNames }
            .distinctBy { required -> required.interfaceName.substringBefore('<') }
            .map { required ->
                KotlinProjectionInterfaceBinding(
                    qualifiedName = required.interfaceName,
                    iid = required.type.iid,
                )
            }
    }

    private fun addRuntimeClassCollectionInterfaceCaches(
        builder: TypeSpec.Builder,
        plan: KotlinTypeProjectionPlan,
    ) {
        val objectReferencePlansByInterface = plan.objectReferenceSurfaceDescriptor
            ?.objectReferencePlans
            .orEmpty()
            .associateBy { it.interfaceName.substringBefore('<') }
        val defaultObjectReferencePlan = plan.defaultInterfaceName
            ?.substringBefore('<')
            ?.let(objectReferencePlansByInterface::get)
        val existingCacheNames = buildSet {
            val defaultInterfaceIsRuntimeOwnedMapped = plan.defaultInterfaceName
                ?.let(::isRuntimeOwnedMappedTypeName) == true
            if (!defaultInterfaceIsRuntimeOwnedMapped &&
                (plan.defaultInterfaceIid != null ||
                    (defaultObjectReferencePlan != null && defaultObjectReferencePlan.skippedReason == null) ||
                    isMappedCollectionInterfaceName(plan.defaultInterfaceName.orEmpty()))
            ) {
                plan.defaultInterfaceName?.let { add(requiredForwardOwnerCache(it, plan.defaultInterfaceName)) }
            }
            plan.implementedInterfaceBindings
                .filter { it.iid != null }
                .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
                .filter { binding ->
                    objectReferencePlansByInterface[binding.qualifiedName.substringBefore('<')]?.skippedReason == null
                }
                .mapTo(this) { requiredForwardOwnerCache(it.qualifiedName, plan.defaultInterfaceName) }
            requiredInterfaceCacheBindings(plan)
                .filter { it.iid != null }
                .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
                .mapTo(this) { requiredForwardOwnerCache(it.qualifiedName, plan.defaultInterfaceName) }
        }
        val collectionCacheBindings =
            (plan.mutableCollectionBindings.map(::KotlinProjectionCollectionSlotBinding) +
                plan.readOnlyCollectionBindings
                    .filterNot { readOnlyBinding ->
                        plan.mutableCollectionBindings.any { mutableBinding -> mutableBinding.covers(readOnlyBinding) }
                    }
                    .map(::KotlinProjectionCollectionSlotBinding))
                .filterNot { it.ownerCachePropertyName in existingCacheNames }
                .distinctBy { it.ownerCachePropertyName }

        val instanceMemberOwnerCacheBindings = plan.instanceMemberBindings
            .asSequence()
            .map { binding ->
                InstanceMemberOwnerCacheBinding(
                    ownerCachePropertyName = binding.ownerCachePropertyName,
                    ownerInterfaceName = binding.ownerInterfaceQualifiedName,
                    slotInterfaceName = binding.slotInterfaceQualifiedName,
                )
            }
            .filterNot { binding -> binding.ownerCachePropertyName == "nativeObject" }
            .filterNot { binding -> binding.ownerCachePropertyName in existingCacheNames }
            .filterNot { ownerBinding ->
                collectionCacheBindings.any { collectionBinding ->
                    collectionBinding.ownerCachePropertyName == ownerBinding.ownerCachePropertyName
                }
            }
            .distinctBy { binding -> binding.ownerCachePropertyName }
            .toList()

        collectionCacheBindings.forEach { binding ->
            builder.addProperty(
                PropertySpec.builder(binding.ownerCachePropertyName, IUNKNOWN_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.of(
                            "lazy(%T.PUBLICATION) { Metadata.acquireInterface(_inner, %L) }",
                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                            runtimeClassInterfaceIdCode(binding.slotInterfaceInstanceName, plan),
                        ),
                    )
                    .build(),
            )
        }
        instanceMemberOwnerCacheBindings.forEach { binding ->
            val acquisitionTarget =
                if (plan.composableFactoryBindings.isNotEmpty() && plan.isOverridableRuntimeClassInterface(binding.slotInterfaceName)) {
                    CodeBlock.of("winRTComposableObjectReference?.inner ?: _inner")
                } else {
                    CodeBlock.of("_inner")
                }
            builder.addProperty(
                PropertySpec.builder(binding.ownerCachePropertyName, IUNKNOWN_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        CodeBlock.of(
                            "lazy(%T.PUBLICATION) { Metadata.acquireInterface(%L, %L) }",
                            LAZY_THREAD_SAFETY_MODE_CLASS_NAME,
                            acquisitionTarget,
                            runtimeClassInterfaceIdCode(binding.ownerInterfaceName, plan),
                        ),
                    )
                    .build(),
            )
        }
    }

    private data class InstanceMemberOwnerCacheBinding(
        val ownerCachePropertyName: String,
        val ownerInterfaceName: String,
        val slotInterfaceName: String,
    )

    internal fun renderAttributeClassShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.annotationBuilder(plan.type.name)
            .apply {
                addModifiers(renderVisibility(plan.visibility))
                val constructorParameters = attributeConstructorParameters(plan)
                if (constructorParameters.isNotEmpty()) {
                    primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(constructorParameters)
                            .build(),
                    )
                    constructorParameters.forEach { parameter ->
                        addProperty(
                            PropertySpec.builder(parameter.name, parameter.type)
                                .initializer(parameter.name)
                                .build(),
                        )
                    }
                }
                addKdoc("attribute WinRT class shell\n")
            }
            .build()

    private fun attributeConstructorParameters(plan: KotlinTypeProjectionPlan): List<ParameterSpec> {
        val primaryCtorParameters = plan.type.methods
            .filter { method -> method.name == ".ctor" && method.isRuntimeSpecialName }
            .maxWithOrNull(compareBy<WinRTMethodDefinition>({ it.parameters.size }, { -(it.methodRowId ?: Int.MAX_VALUE) }))
            ?.parameters
            .orEmpty()
            .mapNotNull { parameter ->
                attributeParameterSpec(parameter.name, parameter.typeName, hasDefault = false)
            }
        val requiredNames = primaryCtorParameters.mapTo(linkedSetOf()) { it.name }
        val optionalFieldParameters = plan.type.fields
            .filterNot { field -> field.isStatic || field.isLiteral || field.name in requiredNames }
            .mapNotNull { field -> attributeParameterSpec(field.name, field.typeName, hasDefault = true) }
        val optionalPropertyParameters = plan.type.properties
            .filterNot { property -> property.isStatic || property.name in requiredNames }
            .mapNotNull { property -> attributeParameterSpec(property.name, property.typeName, hasDefault = true) }
        return primaryCtorParameters + optionalFieldParameters + optionalPropertyParameters
    }

    private fun attributeParameterSpec(
        name: String,
        typeName: String,
        hasDefault: Boolean,
    ): ParameterSpec? {
        val annotationTypeName = attributeParameterTypeName(typeName) ?: return null
        return ParameterSpec.builder(name, annotationTypeName)
            .apply {
                if (hasDefault) {
                    defaultValue(attributeParameterDefaultValue(typeName) ?: return null)
                }
            }
            .build()
    }

    private fun attributeParameterTypeName(typeName: String): TypeName? {
        val trimmed = typeName.trim()
        if (trimmed.startsWith("Array<") && trimmed.endsWith(">")) {
            val elementType = attributeParameterTypeName(trimmed.substringAfter('<').substringBeforeLast('>')) ?: return null
            return Array::class.asClassName().parameterizedBy(elementType)
        }
        winRTFundamentalTypeForName(trimmed)?.let { fundamentalType ->
            return fundamentalType.toAttributeParameterTypeName()
        }
        return if (isWinRTTypeTypeName(trimmed)) KCLASS_STAR_TYPE_NAME else Long::class.asClassName()
    }

    private fun attributeParameterDefaultValue(typeName: String): CodeBlock? {
        val trimmed = typeName.trim()
        if (trimmed.startsWith("Array<") && trimmed.endsWith(">")) {
            return CodeBlock.of("[]")
        }
        winRTFundamentalTypeForName(trimmed)?.let { fundamentalType ->
            return fundamentalType.toAttributeParameterDefaultValue()
        }
        return if (isWinRTTypeTypeName(trimmed)) CodeBlock.of("Any::class") else CodeBlock.of("0L")
    }

    internal fun renderStaticClassShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .apply {
                applyCommonTypeShape(this, plan)
                addAnnotation(
                    AnnotationSpec.builder(Suppress::class)
                        .addMember("%S", "ClassName")
                        .build(),
                )
                addKdoc("static WinRT class shell\n")
                val staticMethods = plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedStaticMethod)
                val staticProperties = plan.type.properties.filter { it.isStatic }
                val staticEvents = plan.type.events.filter { it.isStatic }
                if (staticMethods.isNotEmpty() || staticProperties.isNotEmpty() || staticEvents.isNotEmpty() ||
                    KotlinProjectionCompanionKind.Metadata in plan.companionKinds) {
                    addType(buildMetadataCompanionShell(plan, staticMethods, staticProperties, staticEvents))
                }
                appendCompanionShells(this, plan, excludeKinds = setOf(KotlinProjectionCompanionKind.Metadata))
            }
            .build()

    internal fun renderEnumShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        if (plan.type.customAttributes.any { it.typeName == "System.FlagsAttribute" }) {
            renderFlagsEnumShell(plan)
        } else {
            renderOpenEnumShell(plan)
        }

    private fun renderOpenEnumShell(plan: KotlinTypeProjectionPlan): TypeSpec {
        val underlyingType = plan.type.enumUnderlyingType
        if (plan.type.kind != WinRTTypeKind.Enum || underlyingType == null) {
            return TypeSpec.classBuilder(plan.type.name)
                .apply {
                    applyCommonTypeShape(this, plan)
                    if (KotlinProjectionSpecializationKind.ApiContract in plan.specializationKinds) {
                        addKdoc("api contract WinRT declaration shell\n")
                    }
                }
                .build()
        }
        val enumTypeName = resolveTypeName(plan.type.qualifiedName)
        val abiTypeName = resolveIntegralTypeName(underlyingType)
        return TypeSpec.classBuilder(plan.type.name)
            .addAnnotation(JVM_INLINE_CLASS_NAME)
            .addModifiers(KModifier.VALUE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("abiValue", abiTypeName)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("abiValue", abiTypeName)
                    .initializer("abiValue")
                    .build(),
            )
            .apply {
                applyCommonTypeShape(this, plan)
                if (KotlinProjectionSpecializationKind.ApiContract in plan.specializationKinds) {
                    addKdoc("api contract WinRT declaration shell\n")
                }
                addType(
                    TypeSpec.companionObjectBuilder("Metadata")
                        .apply {
                            plan.type.enumMembers.forEach { member ->
                                addProperty(
                                    PropertySpec.builder(enumConstantName(member.name), enumTypeName)
                                        .initializer("%T(%L)", enumTypeName, integralLiteral(member.valueBits, underlyingType))
                                        .build(),
                                )
                            }
                        }
                        .addFunction(
                            FunSpec.builder("fromAbi")
                                .addParameter("abiValue", abiTypeName)
                                .returns(enumTypeName)
                                .addCode("return %T(abiValue)\n", enumTypeName)
                                .build(),
                        )
                        .addFunction(
                            FunSpec.builder("toAbi")
                                .addParameter("value", enumTypeName)
                                .returns(abiTypeName)
                                .addCode("return value.abiValue\n")
                                .build(),
                        )
                        .build(),
                )
            }
            .build()
    }

    private fun renderFlagsEnumShell(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .addAnnotation(JVM_INLINE_CLASS_NAME)
            .addModifiers(KModifier.VALUE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("abiValue", KOTLIN_UINT_CLASS_NAME)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("abiValue", KOTLIN_UINT_CLASS_NAME)
                    .initializer("abiValue")
                    .build(),
            )
            .apply {
                applyCommonTypeShape(this, plan)
                addFunction(
                    FunSpec.builder("contains")
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("flag", resolveTypeName(plan.type.qualifiedName))
                        .returns(Boolean::class)
                        .addStatement("val masked = abiValue and flag.abiValue")
                        .addStatement("return masked == flag.abiValue")
                        .build(),
                )
                addFunction(
                    FunSpec.builder("hasFlag")
                        .addParameter("flag", resolveTypeName(plan.type.qualifiedName))
                        .returns(Boolean::class)
                        .addCode("return (flag in this)\n")
                        .build(),
                )
                addFunction(
                    FunSpec.builder("or")
                        .addModifiers(KModifier.INFIX)
                        .addParameter("other", resolveTypeName(plan.type.qualifiedName))
                        .returns(resolveTypeName(plan.type.qualifiedName))
                        .addCode("return %T(abiValue or other.abiValue)\n", resolveTypeName(plan.type.qualifiedName))
                        .build(),
                )
                addFunction(
                    FunSpec.builder("and")
                        .addModifiers(KModifier.INFIX)
                        .addParameter("other", resolveTypeName(plan.type.qualifiedName))
                        .returns(resolveTypeName(plan.type.qualifiedName))
                        .addCode("return %T(abiValue and other.abiValue)\n", resolveTypeName(plan.type.qualifiedName))
                        .build(),
                )
                addType(
                    TypeSpec.companionObjectBuilder("Metadata")
                        .apply {
                            plan.type.enumMembers.forEach { member ->
                                addProperty(
                                    PropertySpec.builder(enumConstantName(member.name), resolveTypeName(plan.type.qualifiedName))
                                        .initializer("%T(%L)", resolveTypeName(plan.type.qualifiedName), integralLiteral(member.valueBits, WinRTIntegralType.UInt32))
                                        .build(),
                                )
                            }
                        }
                        .addFunction(
                            FunSpec.builder("fromAbi")
                                .addParameter("abiValue", KOTLIN_UINT_CLASS_NAME)
                                .returns(resolveTypeName(plan.type.qualifiedName))
                                .addCode("return %T(abiValue)\n", resolveTypeName(plan.type.qualifiedName))
                                .build(),
                        )
                        .addFunction(
                            FunSpec.builder("toAbi")
                                .addParameter("flags", resolveTypeName(plan.type.qualifiedName))
                                .returns(KOTLIN_UINT_CLASS_NAME)
                                .addCode("return flags.abiValue\n")
                                .build(),
                        )
                        .build(),
                )
            }
            .build()

    internal fun renderStruct(plan: KotlinTypeProjectionPlan): TypeSpec =
        TypeSpec.classBuilder(plan.type.name)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(
                        plan.type.fields
                            .filterNot { it.isStatic || it.isLiteral }
                            .map { field ->
                                ParameterSpec.builder(field.name.replaceFirstChar(Char::lowercase), resolveStructFieldTypeName(plan, field.typeName)).build()
                            },
                    )
                    .build(),
            )
            .apply { applyCommonTypeShape(this, plan, addModifiers = false) }
            .apply {
                plan.type.fields
                    .filterNot { it.isStatic || it.isLiteral }
                    .forEach { field ->
                        addProperty(
                            PropertySpec.builder(field.name.replaceFirstChar(Char::lowercase), resolveStructFieldTypeName(plan, field.typeName))
                                .initializer(field.name.replaceFirstChar(Char::lowercase))
                                .build(),
                        )
                    }
                renderStructMetadataCompanion(plan)?.let { companion ->
                    addInitializerBlock(CodeBlock.of("Metadata.register()\n"))
                    addType(companion)
                }
            }
            .build()

    internal fun renderStructMetadataCompanion(plan: KotlinTypeProjectionPlan): TypeSpec? {
        val fields = plan.type.fields.filterNot { it.isStatic || it.isLiteral }
        val fieldSpecs = fields.map { field ->
            nativeStructFieldSpec(field, plan.type.namespace, plan.typesByQualifiedName) ?: return null
        }
        val structTypeName = resolveTypeName(plan.type.qualifiedName)
        return TypeSpec.companionObjectBuilder("Metadata")
            .addSuperinterface(NATIVE_STRUCT_ADAPTER_CLASS_NAME.parameterizedBy(structTypeName))
            .addProperty(
                PropertySpec.builder("layout", NATIVE_STRUCT_LAYOUT_CLASS_NAME)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(
                        CodeBlock.builder()
                            .add("%T.sequential(", NATIVE_STRUCT_LAYOUT_CLASS_NAME)
                            .apply {
                                fieldSpecs.forEachIndexed { index, spec ->
                                    if (index > 0) {
                                        add(", ")
                                    }
                                    add("%L", spec)
                                }
                            }
                            .add(")")
                            .build(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("register")
                    .addModifiers(KModifier.INTERNAL)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement(
                                "%T.registerStruct(%T::class, %S, %S, this, emptyArray<%T>()::class)",
                                WINRT_VALUE_BOXING_REGISTRATION_CLASS_NAME,
                                resolveTypeName(plan.type.qualifiedName),
                                plan.type.qualifiedName,
                                nativeStructGuidSignature(plan) ?: error("Struct ${plan.type.qualifiedName} is missing a WinRT GUID signature."),
                                resolveTypeName(plan.type.qualifiedName),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addFunction(
                FunSpec.builder("read")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .returns(structTypeName)
                    .addCode(nativeStructReadCode(plan, fields))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("write")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", structTypeName)
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode(nativeStructWriteCode(plan, fields))
                    .build(),
            )
            .addFunction(
                FunSpec.builder("fromAbi")
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .returns(structTypeName)
                    .addCode("return read(source)\n")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("copyTo")
                    .addParameter("value", structTypeName)
                    .addParameter("destination", RAW_ADDRESS_CLASS_NAME)
                    .addCode("write(value, destination)\n")
                    .build(),
            )
            .addFunction(
                FunSpec.builder("disposeAbi")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("source", RAW_ADDRESS_CLASS_NAME)
                    .addCode(nativeStructDisposeAbiCode(plan, fields))
                    .build(),
            )
            .build()
    }

    private fun nativeStructReadCode(plan: KotlinTypeProjectionPlan, fields: List<WinRTFieldDefinition>): CodeBlock =
        CodeBlock.builder()
            .add("return %T(\n", resolveTypeName(plan.type.qualifiedName))
            .indent()
            .apply {
                fields.forEach { field ->
                    add("%L = %L,\n", field.name.replaceFirstChar(Char::lowercase), nativeStructFieldReadCode(field, "source", plan.type.namespace, plan.typesByQualifiedName))
                }
            }
            .unindent()
            .add(")\n")
            .build()

    private fun nativeStructWriteCode(plan: KotlinTypeProjectionPlan, fields: List<WinRTFieldDefinition>): CodeBlock =
        CodeBlock.builder()
            .apply {
                fields.forEach { field ->
                    add("%L\n", nativeStructFieldWriteCode(field, "value", "destination", plan.type.namespace, plan.typesByQualifiedName))
                }
            }
            .build()

    private fun nativeStructDisposeAbiCode(plan: KotlinTypeProjectionPlan, fields: List<WinRTFieldDefinition>): CodeBlock =
        CodeBlock.builder()
            .apply {
                fields.forEach { field ->
                    nativeStructFieldDisposeAbiCode(field, "source", plan.type.namespace, plan.typesByQualifiedName)?.let { add("%L\n", it) }
                }
            }
            .build()

    private fun enumConstantName(name: String): String =
        if (name == "Metadata") "MetadataValue" else name

    internal fun nativeStructGuidSignature(plan: KotlinTypeProjectionPlan): String? =
        nativeStructGuidSignature(
            typeName = plan.type.qualifiedName,
            currentNamespace = plan.type.namespace,
            typesByQualifiedName = plan.typesByQualifiedName,
            visiting = emptySet(),
        )

    private fun nativeStructGuidSignature(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        visiting: Set<String>,
    ): String? {
        val qualifiedName = when {
            typesByQualifiedName.containsKey(typeName) -> typeName
            currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$typeName") -> "$currentNamespace.$typeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$typeName") }
        } ?: return mappedTypeByAbiName(typeName)?.abiQualifiedName?.let { mapped ->
            when (mapped) {
                "Windows.Foundation.DateTime" -> "struct(Windows.Foundation.DateTime;i8)"
                "Windows.Foundation.EventRegistrationToken" -> "struct(Windows.Foundation.EventRegistrationToken;i8)"
                "Windows.Foundation.HResult" -> "struct(Windows.Foundation.HResult;i4)"
                "Windows.Foundation.TimeSpan" -> "struct(Windows.Foundation.TimeSpan;i8)"
                else -> null
            }
        }
        if (qualifiedName in visiting) {
            return null
        }
        val resolvedType = typesByQualifiedName[qualifiedName] ?: return null
        if (resolvedType.kind == WinRTTypeKind.Enum) {
            val underlyingSignature = resolvedType.enumUnderlyingType?.guidSignatureFragment() ?: return null
            return "enum($qualifiedName;$underlyingSignature)"
        }
        val type = resolvedType.takeIf { it.kind == WinRTTypeKind.Struct } ?: return null
        val fieldSignatures = type.fields
            .filterNot { it.isStatic || it.isLiteral }
            .map { field ->
                nativeStructFieldGuidSignature(field.typeName, type.namespace, typesByQualifiedName, visiting + qualifiedName) ?: return null
            }
        return "struct($qualifiedName;${fieldSignatures.joinToString(";")})"
    }

    private fun nativeStructFieldGuidSignature(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        visiting: Set<String>,
    ): String? {
        if (isWinRTObjectTypeName(typeName)) {
            return "cinterface(IInspectable)"
        }
        if (isWinRTGuidTypeName(typeName)) {
            return "g16"
        }
        winRTFundamentalTypeForName(typeName)?.let { fundamentalType ->
            return fundamentalType.guidSignatureFragment()
        }
        return when (typeName) {
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IInspectableReference" -> "cinterface(IInspectable)"
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IUnknownReference" -> "cinterface(IUnknown)"
            else -> nativeStructGuidSignature(typeName, currentNamespace, typesByQualifiedName, visiting)
                ?: nativeStructReferenceFieldGuidSignature(typeName, currentNamespace, typesByQualifiedName)
        }
    }

    internal fun nativeStructFieldSpec(
        field: WinRTFieldDefinition,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val scalarKind = nativeStructScalarKind(field.typeName)
        if (scalarKind != null) {
            return CodeBlock.of("%T(%S, %T.%L)", NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME, scalarKind)
        }
        if (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) != null) {
            return CodeBlock.of("%T(%S, %T.ADDRESS)", NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME)
        }
        nativeStructCustomAbiFieldScalarKind(field.typeName)?.let { scalarKind ->
            return CodeBlock.of("%T(%S, %T.%L)", NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME, scalarKind)
        }
        val enumQualifiedName = field.typeName.substringBefore('<').removeSuffix("?").let { rawTypeName ->
            when {
                typesByQualifiedName[rawTypeName]?.kind == WinRTTypeKind.Enum -> rawTypeName
                currentNamespace.isNotBlank() && typesByQualifiedName["$currentNamespace.$rawTypeName"]?.kind == WinRTTypeKind.Enum -> "$currentNamespace.$rawTypeName"
                else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") && typesByQualifiedName[it]?.kind == WinRTTypeKind.Enum }
            }
        }
        if (enumQualifiedName != null) {
            val underlyingType = typesByQualifiedName[enumQualifiedName]?.enumUnderlyingType ?: return null
            return CodeBlock.of(
                "%T(%S, %T.%L)",
                NATIVE_SCALAR_FIELD_SPEC_CLASS_NAME,
                field.name.replaceFirstChar(Char::lowercase),
                NATIVE_STRUCT_SCALAR_KIND_CLASS_NAME,
                underlyingType.toNativeStructScalarKindName(),
            )
        }
        val fieldQualifiedName = nativeNestedStructFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        val fieldType = runCatching { resolveTypeName(fieldQualifiedName) as? ClassName }.getOrNull() ?: return null
        return CodeBlock.of("%T(%S, %T.Metadata.layout)", NATIVE_NESTED_STRUCT_FIELD_SPEC_CLASS_NAME, field.name.replaceFirstChar(Char::lowercase), fieldType)
    }

    private fun nativeStructReferenceFieldGuidSignature(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        val qualifiedName = when {
            typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
            currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
        } ?: return null
        return when (typesByQualifiedName[qualifiedName]?.kind) {
            WinRTTypeKind.Interface -> "cinterface($qualifiedName)"
            WinRTTypeKind.RuntimeClass -> "rc($qualifiedName;default)"
            else -> null
        }
    }

    private fun nativeStructReferenceFieldKind(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): NativeStructReferenceFieldKind? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        if (isWinRTObjectTypeName(rawTypeName)) {
            return NativeStructReferenceFieldKind.InspectableReference
        }
        if (winRTFundamentalTypeForName(rawTypeName) == WinRTFundamentalType.String) {
            return NativeStructReferenceFieldKind.String
        }
        return when (rawTypeName) {
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IInspectableReference" -> NativeStructReferenceFieldKind.InspectableReference
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName,
            "io.github.composefluent.winrt.runtime.IUnknownReference" -> NativeStructReferenceFieldKind.UnknownReference
            else -> {
                val qualifiedName = when {
                    typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
                    currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
                    else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
                } ?: return null
                when (typesByQualifiedName[qualifiedName]?.kind) {
                    WinRTTypeKind.Interface -> NativeStructReferenceFieldKind.ProjectedInterface
                    WinRTTypeKind.RuntimeClass -> NativeStructReferenceFieldKind.ProjectedRuntimeClass
                    else -> null
                }
            }
        }
    }

    internal fun nativeStructScalarKind(typeName: String): String? {
        if (isWinRTGuidTypeName(typeName)) {
            return "GUID"
        }
        return winRTFundamentalTypeForName(typeName)?.toNativeStructScalarKindName()
    }

    private fun WinRTIntegralType.toNativeStructScalarKindName(): String =
        when (this) {
            WinRTIntegralType.Int8,
            WinRTIntegralType.UInt8 -> "INT8"
            WinRTIntegralType.Int16,
            WinRTIntegralType.UInt16 -> "INT16"
            WinRTIntegralType.Int32,
            WinRTIntegralType.UInt32 -> "INT32"
            WinRTIntegralType.Int64,
            WinRTIntegralType.UInt64 -> "INT64"
        }

    private fun WinRTIntegralType.guidSignatureFragment(): String =
        when (this) {
            WinRTIntegralType.Int8 -> "i1"
            WinRTIntegralType.UInt8 -> "u1"
            WinRTIntegralType.Int16 -> "i2"
            WinRTIntegralType.UInt16 -> "u2"
            WinRTIntegralType.Int32 -> "i4"
            WinRTIntegralType.UInt32 -> "u4"
            WinRTIntegralType.Int64 -> "i8"
            WinRTIntegralType.UInt64 -> "u8"
        }

    internal fun nativeNestedStructFieldTypeName(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        val mappedType = mappedTypeByAbiName(rawTypeName)
        if (mappedType != null && mappedType.abiValueKind != KotlinProjectionAbiValueKind.Struct) {
            return null
        }
        val qualifiedName = when {
            typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
            currentNamespace.isNotBlank() && typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
        } ?: return mappedType?.abiQualifiedName
        return qualifiedName.takeIf { typesByQualifiedName[it]?.kind == WinRTTypeKind.Struct }
    }

    internal fun nativeStructFieldReadCode(
        field: WinRTFieldDefinition,
        sourceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        if (isWinRTGuidTypeName(field.typeName)) {
            return CodeBlock.of("%T.readGuid(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        }
        winRTFundamentalTypeForName(field.typeName)?.toNativeStructFieldReadCode(slice)?.let { readCode ->
            return readCode
        }
        customStructAbiForNativeField(field.typeName)?.let { customAbi ->
            return CodeBlock.of("%T.%L(%L)", customAbi.helperTypeName, customAbi.fromAbiFunctionName, slice)
        }
        renderAbiTypeBinding(field.typeName, typesByQualifiedName, currentNamespace)
            .takeIf { it.kind == KotlinProjectionAbiValueKind.Reference }
            ?.let { binding ->
                referenceReadbackExpression(binding, WINRT_REFERENCE_PROJECTION_CLASS_NAME, CodeBlock.of("%L", slice).toString())?.let { return it }
            }
        return nativeStructReferenceFieldReadCode(field, sourceName, currentNamespace, typesByQualifiedName)
            ?: nativeStructEnumFieldReadCode(field, slice, currentNamespace, typesByQualifiedName)
            ?: CodeBlock.of("%T.Metadata.fromAbi(%L)", resolveTypeName(field.typeName), slice)
    }

    internal fun nativeStructFieldWriteCode(
        field: WinRTFieldDefinition,
        valueName: String,
        destinationName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        if (isWinRTGuidTypeName(field.typeName)) {
            return CodeBlock.of("%T.writeGuid(%L, %L)", PLATFORM_ABI_CLASS_NAME, slice, value)
        }
        winRTFundamentalTypeForName(field.typeName)?.toNativeStructFieldWriteCode(slice, value)?.let { writeCode ->
            return writeCode
        }
        customStructAbiForNativeField(field.typeName)?.let { customAbi ->
            return CodeBlock.of("%T.%L(%L, %L)", customAbi.helperTypeName, customAbi.copyToFunctionName, value, slice)
        }
        renderAbiTypeBinding(field.typeName, typesByQualifiedName, currentNamespace)
            .takeIf { it.kind == KotlinProjectionAbiValueKind.Reference }
            ?.let { binding ->
                referenceInterfaceIdCode(binding)?.let { interfaceId ->
                    return CodeBlock.of(
                        "%T.writePointer(%L, %T.fromManaged(%L, %L))",
                        PLATFORM_ABI_CLASS_NAME,
                        slice,
                        WINRT_REFERENCE_PROJECTION_CLASS_NAME,
                        value,
                        interfaceId,
                    )
                }
            }
        return nativeStructReferenceFieldWriteCode(field, valueName, destinationName, currentNamespace, typesByQualifiedName)
            ?: nativeStructEnumFieldWriteCode(field, value, slice, currentNamespace, typesByQualifiedName)
            ?: CodeBlock.of("%T.Metadata.copyTo(%L, %L)", resolveTypeName(field.typeName), value, slice)
    }

    private fun customStructAbiForNativeField(typeName: String): KotlinProjectionCustomStructAbi? =
        mappedTypeByAbiName(typeName.substringBefore('<').removeSuffix("?"))
            ?.customStructAbi

    private fun nativeStructCustomAbiFieldScalarKind(typeName: String): String? {
        val customAbi = customStructAbiForNativeField(typeName) ?: return null
        return when (customAbi.abiArgumentKind) {
            KotlinProjectionComArgumentKind.Int32 -> "INT32"
            KotlinProjectionComArgumentKind.Int64 -> "INT64"
            else -> when (customAbi.sizeBytes) {
                4L -> "INT32"
                8L -> "INT64"
                else -> null
            }
        }
    }

    private fun nativeStructEnumFieldReadCode(
        field: WinRTFieldDefinition,
        slice: CodeBlock,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val enumType = nativeStructEnumFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        val underlyingType = typesByQualifiedName[enumType]?.enumUnderlyingType ?: return null
        return CodeBlock.of("%T.Metadata.fromAbi(%L)", resolveTypeName(enumType), integralPlatformReadExpression(underlyingType, slice))
    }

    private fun nativeStructEnumFieldWriteCode(
        field: WinRTFieldDefinition,
        value: CodeBlock,
        slice: CodeBlock,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val enumType = nativeStructEnumFieldTypeName(field.typeName, currentNamespace, typesByQualifiedName) ?: return null
        val underlyingType = typesByQualifiedName[enumType]?.enumUnderlyingType ?: return null
        return integralPlatformWriteCode(
            underlyingType,
            slice,
            CodeBlock.of("%T.Metadata.toAbi(%L)", resolveTypeName(enumType), value),
        )
    }

    private fun nativeStructEnumFieldTypeName(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String? {
        val rawTypeName = typeName.substringBefore('<').removeSuffix("?")
        return when {
            typesByQualifiedName[rawTypeName]?.kind == WinRTTypeKind.Enum -> rawTypeName
            currentNamespace.isNotBlank() && typesByQualifiedName["$currentNamespace.$rawTypeName"]?.kind == WinRTTypeKind.Enum -> "$currentNamespace.$rawTypeName"
            else -> typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") && typesByQualifiedName[it]?.kind == WinRTTypeKind.Enum }
        }
    }

    private fun nativeStructReferenceFieldReadCode(
        field: WinRTFieldDefinition,
        sourceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        return when (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) ?: return null) {
            NativeStructReferenceFieldKind.String ->
                CodeBlock.of("%T.fromHandle(%T.readPointer(%L), owner = false).use { it.toKString() }", HSTRING_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, slice)
            NativeStructReferenceFieldKind.UnknownReference ->
                CodeBlock.of("%T(%T.toRawComPtr(%T.readPointer(%L)), preventReleaseOnDispose = true).use { %T(it.getRefPointer()) }", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, slice, IUNKNOWN_REFERENCE_CLASS_NAME)
            NativeStructReferenceFieldKind.InspectableReference ->
                CodeBlock.of("%T(%T.toRawComPtr(%T.readPointer(%L)), %T.IInspectable, preventReleaseOnDispose = true).use { %T(it.getRefPointer(), %T.IInspectable) }", IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, slice, IID_CLASS_NAME, IINSPECTABLE_REFERENCE_CLASS_NAME, IID_CLASS_NAME)
            NativeStructReferenceFieldKind.ProjectedInterface,
            NativeStructReferenceFieldKind.ProjectedRuntimeClass -> CodeBlock.of(
                "run {\nval __fieldRef = %T(%T.toRawComPtr(%T.readPointer(%L)), preventReleaseOnDispose = true)\n%T.Metadata.wrap(%T(__fieldRef.getRefPointer()))\n}",
                IUNKNOWN_REFERENCE_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                PLATFORM_ABI_CLASS_NAME,
                slice,
                resolveTypeName(field.typeName),
                IUNKNOWN_REFERENCE_CLASS_NAME,
            )
        }
    }

    private fun nativeStructReferenceFieldWriteCode(
        field: WinRTFieldDefinition,
        valueName: String,
        destinationName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val value = CodeBlock.of("%L.%L", valueName, fieldName)
        val slice = CodeBlock.of("layout.slice(%L, %S)", destinationName, fieldName)
        return when (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName) ?: return null) {
            NativeStructReferenceFieldKind.String ->
                CodeBlock.of("%T.writePointer(%L, %T.create(%L).handle)", PLATFORM_ABI_CLASS_NAME, slice, HSTRING_CLASS_NAME, value)
            NativeStructReferenceFieldKind.UnknownReference,
            NativeStructReferenceFieldKind.InspectableReference ->
                CodeBlock.of("%T.writePointer(%L, %T.fromRawComPtr(%L.getRefPointer()))", PLATFORM_ABI_CLASS_NAME, slice, PLATFORM_ABI_CLASS_NAME, value)
            NativeStructReferenceFieldKind.ProjectedInterface,
            NativeStructReferenceFieldKind.ProjectedRuntimeClass ->
                CodeBlock.of(
                    "%T.writePointer(%L, %T.fromRawComPtr((%L as %T).nativeObject.getRefPointer()))",
                    PLATFORM_ABI_CLASS_NAME,
                    slice,
                    PLATFORM_ABI_CLASS_NAME,
                    value,
                    IWINRT_OBJECT_CLASS_NAME,
                )
        }
    }

    private fun nativeStructFieldDisposeAbiCode(
        field: WinRTFieldDefinition,
        sourceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val fieldName = field.name.replaceFirstChar(Char::lowercase)
        val slice = CodeBlock.of("layout.slice(%L, %S)", sourceName, fieldName)
        val pointer = CodeBlock.of("%T.readPointer(%L)", PLATFORM_ABI_CLASS_NAME, slice)
        return when (nativeStructReferenceFieldKind(field.typeName, currentNamespace, typesByQualifiedName)) {
            NativeStructReferenceFieldKind.String ->
                CodeBlock.of("%T.fromHandle(%L, owner = true).close()", HSTRING_CLASS_NAME, pointer)
            NativeStructReferenceFieldKind.UnknownReference,
            NativeStructReferenceFieldKind.InspectableReference,
            NativeStructReferenceFieldKind.ProjectedInterface,
            NativeStructReferenceFieldKind.ProjectedRuntimeClass ->
                CodeBlock.of("if (%L != %T.nullPointer) %T(%T.toRawComPtr(%L)).close()", pointer, PLATFORM_ABI_CLASS_NAME, IUNKNOWN_REFERENCE_CLASS_NAME, PLATFORM_ABI_CLASS_NAME, pointer)
            null -> null
        }
    }

    private enum class NativeStructReferenceFieldKind {
        String,
        UnknownReference,
        InspectableReference,
        ProjectedInterface,
        ProjectedRuntimeClass,
    }

    private fun runtimeClassObjectReferenceCacheInitializer(
        objectReferencePlan: WinRTObjectReferencePlanDescriptor?,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        acquireExpression: String,
        vararg acquireArgs: Any,
    ): CodeBlock {
        val body = CodeBlock.builder()
        if (objectReferencePlan?.requiresGenericInstantiation == true) {
            body.addStatement(
                "%T.initializeBySourceType(%S)",
                genericTypeInstantiationsClassName,
                objectReferencePlan.interfaceName,
            )
        }
        if (objectReferencePlan?.usesDefaultInterfaceObjRef == true && objectReferencePlan.defaultInterfaceObjRefVtableSlot != null) {
            body.addStatement("_inner.getDefaultInterfaceObjectReference(%L)", objectReferencePlan.defaultInterfaceObjRefVtableSlot)
        } else if (objectReferencePlan?.requiresGenericInstantiation == true) {
            val signature = abiTypeSignature(renderAbiTypeBinding(objectReferencePlan.interfaceName, typesByQualifiedName, objectReferencePlan.interfaceName.substringBeforeLast('.', "")))
                ?: throw IllegalArgumentException(
                    "Generator requires runtime class object-reference cache for ${objectReferencePlan.interfaceName} to have a renderable type signature before interface cache rendering.",
                )
            body.addStatement(
                "Metadata.acquireInterface(_inner, %T.createFromSignature(%L))",
                PARAMETERIZED_INTERFACE_ID_CLASS_NAME,
                signature,
            )
        } else {
            body.add(acquireExpression, *acquireArgs)
            body.add("\n")
        }
        return CodeBlock.builder()
            .add("lazy(%T.PUBLICATION) {\n", LAZY_THREAD_SAFETY_MODE_CLASS_NAME)
            .indent()
            .add(body.build())
            .unindent()
            .add("}")
            .build()
    }

    private fun runtimeClassInterfaceIdCode(
        interfaceName: String,
        plan: KotlinTypeProjectionPlan,
    ): CodeBlock {
        val rawInterfaceName = redirectedAbiTypeName(interfaceName.substringBefore('<').removeSuffix("?"))
        if ('<' !in interfaceName) {
            runtimeOwnedPublicInterfaceIdCode(rawInterfaceName)?.let { return it }
            mappedTypeByAbiName(rawInterfaceName)?.customObjectAbi?.let { customObjectAbi ->
                return CodeBlock.of("%T(%S)", GUID_CLASS_NAME, customObjectAbi.interfaceId.toString())
            }
            return CodeBlock.of("%T.Metadata.IID", projectionClassName(rawInterfaceName))
        }
        runtimeClassMappedIteratorInterfaceIdCode(interfaceName, plan)?.let { return it }
        if (mappedTypeByAbiName(rawInterfaceName)?.isBindableCollectionMapping() == true) {
            return CodeBlock.of("%T.Metadata.IID", projectionClassName(rawInterfaceName))
        }
        val signature = abiTypeSignature(
            renderAbiTypeBinding(interfaceName, plan.typesByQualifiedName, plan.type.namespace),
        ) ?: throw IllegalArgumentException(
            "Generator requires runtime class ${plan.type.qualifiedName} interface $interfaceName to have a renderable type signature before interface cache rendering.",
        )
        return CodeBlock.of("%T.createFromSignature(%L)", PARAMETERIZED_INTERFACE_ID_CLASS_NAME, signature)
    }

    private fun runtimeOwnedPublicInterfaceIdCode(rawInterfaceName: String): CodeBlock? =
        when (rawInterfaceName) {
            "Windows.Foundation.IStringable" -> CodeBlock.of("%T.IStringable", IID_CLASS_NAME)
            else -> null
        }

    private fun runtimeClassMappedIteratorInterfaceIdCode(
        interfaceName: String,
        plan: KotlinTypeProjectionPlan,
    ): CodeBlock? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        if (mappedTypeByAbiName(rawInterfaceName)?.descriptionName != "Iterator") {
            return null
        }
        val elementTypeName = splitGenericArguments(interfaceName.substringAfter('<').substringBeforeLast('>'))
            .singleOrNull()
            ?: throw IllegalArgumentException(
                "Generator requires runtime class ${plan.type.qualifiedName} iterator interface $interfaceName to have exactly one generic argument before interface cache rendering.",
            )
        val elementSignature = abiTypeSignature(
            renderAbiTypeBinding(elementTypeName, plan.typesByQualifiedName, rawInterfaceName.substringBeforeLast('.', "")),
        ) ?: throw IllegalArgumentException(
            "Generator requires runtime class ${plan.type.qualifiedName} iterator interface $interfaceName to have a renderable element type signature before interface cache rendering.",
        )
        return CodeBlock.of(
            "%T.createFromSignature(%T.iteratorSignature(%L))",
            PARAMETERIZED_INTERFACE_ID_CLASS_NAME,
            WINRT_COLLECTION_INTERFACE_IDS_CLASS_NAME,
            elementSignature,
        )
    }

    private fun KotlinProjectionMappedType.isBindableCollectionMapping(): Boolean =
        abiValueKind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
            abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVector ||
            abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVectorView

    internal fun renderDelegate(plan: KotlinTypeProjectionPlan): TypeSpec {
        val invokeMethod = requireDelegateInvokeMethod(plan.type)
        val builder = TypeSpec.funInterfaceBuilder(plan.type.name)
        applyCommonTypeShape(builder, plan)
        val invokeShape = plan.delegateInvokeShape
        if (invokeShape != null && supportsProjectedDelegateObjectMarshaller(plan, invokeShape)) {
            builder.addSuperinterface(WINRT_PROJECTED_DELEGATE_CLASS_NAME)
            builder.addFunction(
                FunSpec.builder("createWinRTDelegateHandle")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(ClassName("io.github.composefluent.winrt.runtime", "WinRTDelegateHandle"))
                    .addCode(
                        CodeBlock.of(
                            """
                            return %T.createDelegate(
                                iid = Metadata.DESCRIPTOR.interfaceId,
                                parameterKinds = Metadata.DESCRIPTOR.parameterKinds,
                                returnKind = Metadata.DESCRIPTOR.returnKind,
                                parameterStructAdapters = Metadata.DESCRIPTOR.parameterStructAdapters,
                                returnStructAdapter = Metadata.DESCRIPTOR.returnStructAdapter,
                            ) { __args ->
                                this(%L)
                            }
                            """.trimIndent() + "\n",
                            WINRT_DELEGATE_BRIDGE_CLASS_NAME,
                            delegateCallbackArgumentCodeList(invokeShape.parameterBindings),
                        ),
                    )
                    .build(),
            )
        }
        builder.addFunction(
            FunSpec.builder("invoke")
                .addModifiers(KModifier.ABSTRACT, KModifier.OPERATOR)
                .addParameters(
                    invokeMethod.parameters.map { parameter ->
                        ParameterSpec.builder(parameter.name, resolveTypeName(parameter.typeName)).build()
                    },
                )
                .returns(resolveTypeName(invokeMethod.returnTypeName))
                .build(),
        )
        if (invokeShape != null && invokeShape.isSupportedProjectedDelegateShape()) {
            val projectedType = plan.projectedSelfTypeName()
            builder.addType(
                TypeSpec.companionObjectBuilder("Metadata")
                    .addProperty(
                        PropertySpec.builder("DESCRIPTOR", WINRT_DELEGATE_DESCRIPTOR_CLASS_NAME)
                            .addModifiers(KModifier.INTERNAL)
                            .initializer("%L", delegateDescriptorCode(invokeShape, plan.type.qualifiedName))
                            .build(),
                    )
                    .addFunction(
                        FunSpec.builder("fromAbi")
                            .addModifiers(KModifier.INTERNAL)
                            .apply {
                                repeat(plan.type.genericParameterCount) { index ->
                                    addTypeVariable(TypeVariableName("T$index"))
                                }
                            }
                            .addParameter("pointer", RAW_ADDRESS_CLASS_NAME)
                            .returns(projectedType.copy(nullable = true))
                            .addCode(
                                CodeBlock.of(
                                    """
                                    val __native = %T.fromAbi(pointer, DESCRIPTOR) ?: return null
                                    return object : %T, %T {
                                        override val nativeObject: %T
                                            get() = __native

                                        override fun invoke(%L): %T {
                                            %L
                                        }
                                    }
                                    """.trimIndent() + "\n",
                                    WINRT_DELEGATE_REFERENCE_CLASS_NAME,
                                    projectedType,
                                    IWINRT_OBJECT_CLASS_NAME,
                                    COM_OBJECT_REFERENCE_CLASS_NAME,
                                    invokeMethod.parameters.joinToString(", ") { "${it.name}: ${resolveTypeName(it.typeName)}" },
                                    resolveTypeName(invokeMethod.returnTypeName),
                                    delegateInvokeBodyCode(invokeShape),
                                ),
                            )
                            .build(),
                    )
                    .build(),
            )
        }
        return builder.build()
    }
}

private fun winAppSdkCoveredApplicationModelCoreInterface(interfaceName: String): String? =
    when (interfaceName.substringBefore('<').removeSuffix("?")) {
        "Microsoft.UI.Xaml.IFrameworkView" -> "Windows.ApplicationModel.Core.IFrameworkView"
        "Microsoft.UI.Xaml.IFrameworkViewSource" -> "Windows.ApplicationModel.Core.IFrameworkViewSource"
        else -> null
    }

private val WINDOWS_APPLICATION_MODEL_CORE_FRAMEWORK_VIEW_CLASS_NAME =
    ClassName("windows.applicationmodel.core", "IFrameworkView")

private val WINDOWS_APPLICATION_MODEL_CORE_FRAMEWORK_VIEW_SOURCE_CLASS_NAME =
    ClassName("windows.applicationmodel.core", "IFrameworkViewSource")

private fun KotlinTypeProjectionPlan.requiresApplicationModelCoreAliasImports(): Boolean =
    winAppSdkCoveredApplicationModelCoreInterface(type.qualifiedName) != null

internal fun KotlinTypeProjectionPlan.supportsDerivedComposableConstruction(): Boolean =
    type.kind == WinRTTypeKind.RuntimeClass &&
        KotlinProjectionCompanionKind.ComposableFactory in companionKinds &&
        KotlinProjectionModifier.Sealed !in modifiers

private fun KotlinTypeProjectionPlan.requiresOpenRuntimeClassShell(): Boolean =
    supportsDerivedComposableConstruction() ||
        (
            KotlinProjectionModifier.Sealed !in modifiers &&
                (
                    classMemberMergeDescriptor?.interfaceDescriptors?.any { descriptor ->
                        descriptor.isOverridableInterface
                    } == true ||
                        typesByQualifiedName.values.any { candidate ->
                            candidate.kind == WinRTTypeKind.RuntimeClass &&
                                candidate.baseTypeName?.let { baseName ->
                                    baseName == type.qualifiedName || baseName == type.name
                                } == true
                        }
                    )
            )

internal fun KotlinTypeProjectionPlan.runtimeClassWrapperConstructorVisibility(): KModifier =
    if (type.isSealedType) KModifier.INTERNAL else KModifier.PROTECTED

private fun KotlinProjectionRenderer.supportsProjectedDelegateObjectMarshaller(
    plan: KotlinTypeProjectionPlan,
    invokeShape: KotlinProjectionDelegateInvokeShape,
): Boolean =
    plan.type.genericParameterCount == 0 &&
        invokeShape.returnBinding.kind == KotlinProjectionAbiValueKind.Unit &&
        invokeShape.isSupportedOutboundDelegateShape() &&
        invokeShape.parameterBindings.all { binding -> supportsProjectedDelegateObjectMarshallerArgument(binding.typeBinding) }

private fun KotlinProjectionRenderer.supportsProjectedDelegateObjectMarshallerArgument(
    typeBinding: KotlinProjectionAbiTypeBinding,
): Boolean =
    when (typeBinding.kind) {
        KotlinProjectionAbiValueKind.String,
        KotlinProjectionAbiValueKind.Boolean,
        KotlinProjectionAbiValueKind.Int8,
        KotlinProjectionAbiValueKind.UInt8,
        KotlinProjectionAbiValueKind.Int16,
        KotlinProjectionAbiValueKind.UInt16,
        KotlinProjectionAbiValueKind.Char16,
        KotlinProjectionAbiValueKind.Int32,
        KotlinProjectionAbiValueKind.UInt32,
        KotlinProjectionAbiValueKind.Int64,
        KotlinProjectionAbiValueKind.UInt64,
        KotlinProjectionAbiValueKind.Float,
        KotlinProjectionAbiValueKind.Double,
        KotlinProjectionAbiValueKind.GuidValue,
        KotlinProjectionAbiValueKind.Struct,
        KotlinProjectionAbiValueKind.Enum,
        KotlinProjectionAbiValueKind.Object,
        KotlinProjectionAbiValueKind.UnknownReference,
        KotlinProjectionAbiValueKind.InspectableReference,
        KotlinProjectionAbiValueKind.MappedAsyncAction,
        KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
        KotlinProjectionAbiValueKind.MappedAsyncOperation,
        KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
        KotlinProjectionAbiValueKind.MappedIterable,
        KotlinProjectionAbiValueKind.MappedVector,
        KotlinProjectionAbiValueKind.MappedMap,
        KotlinProjectionAbiValueKind.MappedVectorView,
        KotlinProjectionAbiValueKind.MappedMapView -> true
        KotlinProjectionAbiValueKind.GenericParameter -> true
        KotlinProjectionAbiValueKind.Array -> typeBinding.typeArguments.singleOrNull()?.kind == KotlinProjectionAbiValueKind.UInt8
        KotlinProjectionAbiValueKind.ProjectedInterface,
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> true
        else -> false
    }
