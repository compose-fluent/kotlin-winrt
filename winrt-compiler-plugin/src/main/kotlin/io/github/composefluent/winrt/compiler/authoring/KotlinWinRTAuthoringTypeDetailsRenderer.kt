package io.github.composefluent.winrt.compiler.authoring

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTFundamentalType
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTParameterDirection
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.isWinRTGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
import java.nio.file.Path
import kotlin.io.path.createDirectories

object KotlinWinRTAuthoringTypeDetailsRenderer {
    private val authoringTypeDetailsRegistrarPackage = "io.github.composefluent.winrt.projections.support"
    private val authoringTypeDetailsRegistrarName = "WinRTAuthoringTypeDetailsRegistrar"
    private val comAbiValueKindType = ClassName("io.github.composefluent.winrt.runtime", "ComAbiValueKind")
    private val comMethodSignatureType = ClassName("io.github.composefluent.winrt.runtime", "ComMethodSignature")
    private val guidType = ClassName("io.github.composefluent.winrt.runtime", "Guid")
    private val hStringType = ClassName("io.github.composefluent.winrt.runtime", "HString")
    private val iUnknownReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IUnknownReference")
    private val iInspectableReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IInspectableReference")
    private val iWinRTObjectType = ClassName("io.github.composefluent.winrt.runtime", "IWinRTObject")
    private val iidType = ClassName("io.github.composefluent.winrt.runtime", "IID")
    private val exceptionHelpersType = ClassName("io.github.composefluent.winrt.runtime", "ExceptionHelpers")
    private val eventRegistrationTokenType = ClassName("io.github.composefluent.winrt.runtime", "EventRegistrationToken")
    private val knownHResultsType = ClassName("io.github.composefluent.winrt.runtime", "KnownHResults")
    private val marshalDelegateType = ClassName("io.github.composefluent.winrt.runtime", "MarshalDelegate")
    private val parameterizedInterfaceIdType = ClassName("io.github.composefluent.winrt.runtime", "ParameterizedInterfaceId")
    private val platformAbiType = ClassName("io.github.composefluent.winrt.runtime", "PlatformAbi")
    private val projectionsType = ClassName("io.github.composefluent.winrt.runtime", "Projections")
    private val rawAddressType = ClassName("io.github.composefluent.winrt.runtime", "RawAddress")
    private val winRTTypeHandleType = ClassName("io.github.composefluent.winrt.runtime", "WinRTTypeHandle")
    private val comWrappersSupportType = ClassName("io.github.composefluent.winrt.runtime", "ComWrappersSupport")
    private val winRTCcwDefinitionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTCcwDefinition")
    private val winRTInspectableInterfaceDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRTInspectableInterfaceDefinition")
    private val winRTInspectableMethodDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRTInspectableMethodDefinition")
    private val winRTCollectionInterfaceIdsType = ClassName("io.github.composefluent.winrt.runtime", "WinRTCollectionInterfaceIds")
    private val winRTProjectedDelegateType = ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectedDelegate")
    private val winRTAsyncInterfaceIdsType = ClassName("io.github.composefluent.winrt.runtime", "WinRTAsyncInterfaceIds")
    private val winRTAsyncActionReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRTAsyncActionReference")
    private val winRTAsyncActionWithProgressReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRTAsyncActionWithProgressReference")
    private val winRTAsyncOperationReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRTAsyncOperationReference")
    private val winRTAsyncOperationWithProgressReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRTAsyncOperationWithProgressReference")
    private val winRTDictionaryProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTDictionaryProjection")
    private val winRTIterableProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTIterableProjection")
    private val winRTIteratorProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTIteratorProjection")
    private val winRTListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTListProjection")
    private val winRTObjectMarshallerType = ClassName("io.github.composefluent.winrt.runtime", "WinRTObjectMarshaller")
    private val winRTReadOnlyDictionaryProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTReadOnlyDictionaryProjection")
    private val winRTReadOnlyListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTReadOnlyListProjection")
    private val winRTReferenceProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRTReferenceProjection")
    private val winRTReferenceValueAdapterType = ClassName("io.github.composefluent.winrt.runtime", "WinRTReferenceValueAdapter")
    private val winRTReferenceValueAdaptersType = ClassName("io.github.composefluent.winrt.runtime", "WinRTReferenceValueAdapters")
    private val winRTSystemProjectionMarshalersType = ClassName("io.github.composefluent.winrt.runtime", "WinRTSystemProjectionMarshalers")
    private val winRTTypeSignatureType = ClassName("io.github.composefluent.winrt.runtime", "WinRTTypeSignature")
    private val winRTKeyValuePairAdapterMember = MemberName("io.github.composefluent.winrt.runtime", "winRTKeyValuePairAdapter")
    private val instantType = ClassName("kotlin.time", "Instant")
    private val durationType = ClassName("kotlin.time", "Duration")
    private val enumIntegralAbiDescriptors = mapOf(
        WinRTIntegralType.Int8 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Byte::class.asClassName(),
            integralType = WinRTIntegralType.Int8,
            abiKindName = "Int8",
            writeFunctionName = "writeInt8",
        ),
        WinRTIntegralType.UInt8 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Byte::class.asClassName(),
            integralType = WinRTIntegralType.UInt8,
            abiKindName = "Int8",
            rawCarrierConversionSuffix = ".toUByte()",
            writeFunctionName = "writeInt8",
            abiWriteConversionSuffix = ".toByte()",
        ),
        WinRTIntegralType.Int16 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Short::class.asClassName(),
            integralType = WinRTIntegralType.Int16,
            abiKindName = "Int16",
            writeFunctionName = "writeInt16",
        ),
        WinRTIntegralType.UInt16 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Short::class.asClassName(),
            integralType = WinRTIntegralType.UInt16,
            abiKindName = "Int16",
            rawCarrierConversionSuffix = ".toUShort()",
            writeFunctionName = "writeInt16",
            abiWriteConversionSuffix = ".toShort()",
        ),
        WinRTIntegralType.Int32 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Int::class.asClassName(),
            integralType = WinRTIntegralType.Int32,
            abiKindName = "Int32",
            writeFunctionName = "writeInt32",
        ),
        WinRTIntegralType.UInt32 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Int::class.asClassName(),
            integralType = WinRTIntegralType.UInt32,
            abiKindName = "Int32",
            rawCarrierConversionSuffix = ".toUInt()",
            writeFunctionName = "writeInt32",
            abiWriteConversionSuffix = ".toInt()",
        ),
        WinRTIntegralType.Int64 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Long::class.asClassName(),
            integralType = WinRTIntegralType.Int64,
            abiKindName = "Int64",
            writeFunctionName = "writeInt64",
        ),
        WinRTIntegralType.UInt64 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Long::class.asClassName(),
            integralType = WinRTIntegralType.UInt64,
            abiKindName = "Int64",
            rawCarrierConversionSuffix = ".toULong()",
            writeFunctionName = "writeInt64",
            abiWriteConversionSuffix = ".toLong()",
        ),
    )

    fun renderTo(
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
        metadataModel: WinRTMetadataModel,
        outputDirectory: Path,
        assemblyName: String? = null,
    ) {
        val typesByName = metadataModel.namespaces
            .flatMap { namespace -> namespace.types }
            .associateBy { type -> type.qualifiedName }
        val semanticHelpers = WinRTMetadataSemanticHelpers(metadataModel)
        val authoredRuntimeClassNames = candidates.mapTo(mutableSetOf(), KotlinWinRTAuthoredTypeCandidate::sourceTypeName)
        val renderedCandidates = candidates.map { candidate ->
            val interfaces = resolveAuthoringInterfaces(candidate, typesByName, semanticHelpers)
            val packageDirectory = outputDirectory.resolve(candidate.packageName.replace('.', '/'))
            packageDirectory.createDirectories()
            render(candidate, interfaces, typesByName, semanticHelpers, authoredRuntimeClassNames).writeTo(outputDirectory)
            candidate
        }
        renderRegistrar(renderedCandidates, authoringTypeDetailsRegistrarName(assemblyName)).writeTo(outputDirectory)
    }

    private fun resolveAuthoringInterfaces(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): List<AuthoredInterfaceDescriptor> {
        if (candidate.winRTInterfaceNames.isEmpty()) {
            throw IllegalArgumentException(
                "Authored type '${candidate.sourceTypeName}' has no WinRT interfaces for TypeDetails generation.",
            )
        }
        val interfacesByName = linkedMapOf<String, AuthoredInterfaceDescriptor>()
        candidate.winRTInterfaceNames.forEach { interfaceName ->
            val type = typesByName[interfaceName]
                ?: throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references missing WinRT interface '$interfaceName'.",
                )
            if (type.kind != WinRTTypeKind.Interface) {
                throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references non-interface WinRT type '$interfaceName'.",
                )
            }
            if (type.iid == null) {
                throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references WinRT interface '$interfaceName' without metadata IID.",
                )
            }
            collectAuthoredInterfaceClosure(
                candidate,
                WinRTTypeRef.fromDisplayName(interfaceName).normalized(),
                type,
                typesByName,
                interfacesByName,
            )
            semanticHelpers.requiredInterfaceAugmentationDescriptor(type).requiredInterfaceNames.forEach { requiredInterfaceName ->
                val requiredType = instantiateInterfaceDefinition(requiredInterfaceName, typesByName)
                    ?: throw IllegalArgumentException(
                        "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' required interface '$requiredInterfaceName', but metadata for it is missing.",
                    )
                collectAuthoredInterfaceClosure(
                    candidate,
                    WinRTTypeRef.fromDisplayName(requiredInterfaceName).normalized(),
                    requiredType,
                    typesByName,
                    interfacesByName,
                )
            }
        }
        return interfacesByName.values.toList()
    }

    private fun collectAuthoredInterfaceClosure(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        interfaceRef: WinRTTypeRef,
        type: WinRTTypeDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        interfacesByName: MutableMap<String, AuthoredInterfaceDescriptor>,
    ) {
        val normalizedInterfaceRef = interfaceRef.normalized()
        if (interfacesByName.containsKey(normalizedInterfaceRef.typeName)) {
            return
        }
        validateAuthoredInterfaceMemberSupport(candidate, type)
        interfacesByName[normalizedInterfaceRef.typeName] = AuthoredInterfaceDescriptor(normalizedInterfaceRef, type)
        type.implementedInterfaces.forEach { implemented ->
            val implementedType = instantiateInterfaceDefinition(implemented.interfaceName, typesByName)
                ?: throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' inherited interface '${implemented.interfaceName}', but metadata for it is missing.",
                )
            collectAuthoredInterfaceClosure(
                candidate,
                WinRTTypeRef.fromDisplayName(implemented.interfaceName).normalized(),
                implementedType,
                typesByName,
                interfacesByName,
            )
        }
    }

    private fun instantiateInterfaceDefinition(
        interfaceName: String,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): WinRTTypeDefinition? {
        val rawName = interfaceName.substringBefore('<').removeSuffix("?")
        val type = typesByName[interfaceName] ?: typesByName[rawName] ?: return null
        val genericArguments = WinRTTypeRef.fromDisplayName(interfaceName).normalized().typeArguments
        if (genericArguments.isEmpty()) {
            return type
        }
        return type.copy(
            implementedInterfaces = type.implementedInterfaces.map { implemented ->
                implemented.copy(
                    interfaceName = implemented.interfaceType
                        .substituteTypeParameters(genericArguments)
                        .normalized()
                        .typeName,
                )
            },
            methods = type.methods.map { method ->
                val substitutedReturnType = method.returnType.substituteTypeParameters(genericArguments).normalized()
                method.copy(
                    returnTypeName = substitutedReturnType.typeName,
                    returnTypeSignature = substitutedReturnType,
                    parameters = method.parameters.map { parameter ->
                        val substitutedParameterType = parameter.type.substituteTypeParameters(genericArguments).normalized()
                        parameter.copy(
                            typeName = substitutedParameterType.typeName,
                            typeSignature = substitutedParameterType,
                        )
                    },
                )
            },
            properties = type.properties.map { property ->
                val substitutedPropertyType = property.type.substituteTypeParameters(genericArguments).normalized()
                property.copy(
                    typeName = substitutedPropertyType.typeName,
                    typeSignature = substitutedPropertyType,
                )
            },
            events = type.events.map { event ->
                val substitutedDelegateType = event.delegateType.substituteTypeParameters(genericArguments).normalized()
                event.copy(
                    delegateTypeName = substitutedDelegateType.typeName,
                    delegateTypeSignature = substitutedDelegateType,
                )
            },
        )
    }

    private fun validateAuthoredInterfaceMemberSupport(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        type: WinRTTypeDefinition,
    ) {
        type.methods.firstOrNull { method -> method.isStatic }?.let { method ->
            throw IllegalArgumentException(
                "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' static method '${method.name}', but TypeDetails instance CCW generation cannot expose static interface members.",
            )
        }
        type.properties.firstOrNull { property -> property.isStatic }?.let { property ->
            throw IllegalArgumentException(
                "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' static property '${property.name}', but TypeDetails instance CCW generation cannot expose static interface members.",
            )
        }
        type.events.firstOrNull { event -> event.isStatic }?.let { event ->
            throw IllegalArgumentException(
                "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' static event '${event.name}', but TypeDetails instance CCW generation cannot expose static interface members.",
            )
        }
        type.events.firstOrNull { event -> !event.hasValidAccessors }?.let { event ->
            throw IllegalArgumentException(
                "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' event '${event.name}' with unsupported event accessors.",
            )
        }
    }

    private fun render(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        interfaces: List<AuthoredInterfaceDescriptor>,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): FileSpec {
        val typeDetailsName = detailsObjectName(candidate)
        return FileSpec.builder(candidate.packageName, typeDetailsName)
            .addAnnotation(generatedAuthoringTypeDetailsSuppressAnnotation())
            .addImport("io.github.composefluent.winrt.runtime", "abiLayout")
            .addType(
                TypeSpec.objectBuilder(typeDetailsName)
                    .addFunction(renderRegister(candidate))
                    .addFunction(renderCreateCcwDefinition(candidate, interfaces, typesByName, semanticHelpers, authoredRuntimeClassNames))
                    .build(),
            )
            .build()
    }

    private fun renderRegister(candidate: KotlinWinRTAuthoredTypeCandidate): FunSpec =
        FunSpec.builder("register")
            .addStatement(
                "%T.registerAuthoredRuntimeClassType(%T::class, %S)",
                projectionsType,
                sourceClassName(candidate),
                candidate.sourceTypeName,
            )
            .addStatement(
                "%T.registerAuthoringMetadataTypeMappings(mapOf(%S to %S))",
                comWrappersSupportType,
                candidate.sourceTypeName,
                "ABI.${candidate.sourceTypeName}",
            )
            .addStatement(
                "%T.registerAuthoringTypeDetailsFactory(%T::class, ::createCcwDefinition)",
                comWrappersSupportType,
                sourceClassName(candidate),
            )
            .build()

    private fun renderCreateCcwDefinition(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        interfaces: List<AuthoredInterfaceDescriptor>,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): FunSpec {
        val defaultInterface = interfaces.first()
        return FunSpec.builder("createCcwDefinition")
            .addParameter(ParameterSpec.builder("value", ANY).build())
            .returns(winRTCcwDefinitionType)
            .addCode(
                CodeBlock.builder()
                    .add("return %T(\n", winRTCcwDefinitionType)
                    .indent()
                    .add("interfaceDefinitions = listOf(\n")
                    .indent()
                    .apply {
                        interfaces.forEach { descriptor ->
                            add("%L,\n", renderInterface(candidate, descriptor, typesByName, semanticHelpers, authoredRuntimeClassNames))
                        }
                    }
                    .unindent()
                    .add("),\n")
                    .add("defaultInterfaceId = %L,\n", renderInterfaceId(defaultInterface, typesByName))
                    .add("runtimeClassName = %S,\n", candidate.sourceTypeName)
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build()
    }

    private fun renderRegistrar(
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
        registrarName: String,
    ): FileSpec =
        FileSpec.builder(authoringTypeDetailsRegistrarPackage, registrarName)
            .addType(
                TypeSpec.objectBuilder(registrarName)
                    .addFunction(renderRegistrarRegister(candidates))
                    .build(),
            )
            .build()

    private fun renderRegistrarRegister(candidates: List<KotlinWinRTAuthoredTypeCandidate>): FunSpec {
        val code = CodeBlock.builder()
        candidates
            .sortedBy { candidate -> candidate.sourceTypeName }
            .forEach { candidate ->
                code.addStatement("%T.register()", ClassName(candidate.packageName, detailsObjectName(candidate)))
            }
        return FunSpec.builder("register")
            .addCode(code.build())
            .build()
    }

    private fun renderInterface(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        descriptor: AuthoredInterfaceDescriptor,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        val type = descriptor.definition
        val dispatchTarget = authoredDispatchTarget(candidate, type, typesByName, semanticHelpers)
        return CodeBlock.builder()
            .add("%T(\n", winRTInspectableInterfaceDefinitionType)
            .indent()
            .add("interfaceId = %L,\n", renderInterfaceId(descriptor, typesByName))
            .add("methods = listOf(\n")
            .indent()
            .apply {
                type.authoredVtableMethods().forEach { vtableMethod ->
                    add("%L,\n", renderMethod(type, vtableMethod, typesByName, semanticHelpers, dispatchTarget, authoredRuntimeClassNames))
                }
            }
            .unindent()
            .add("),\n")
            .unindent()
            .add(")")
            .build()
    }

    private fun renderInterfaceId(
        descriptor: AuthoredInterfaceDescriptor,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock {
        if (descriptor.type.typeArguments.isNotEmpty()) {
            return CodeBlock.of(
                "%T.createFromSignature(%L)",
                parameterizedInterfaceIdType,
                renderWinRTTypeSignature(descriptor.type, typesByName),
            )
        }
        val iid = descriptor.definition.iid
            ?: throw IllegalArgumentException("Authored WinRT interface '${descriptor.definition.qualifiedName}' has no IID metadata.")
        return CodeBlock.of("%T(%S)", guidType, iid.toString().lowercase())
    }

    private fun renderMethod(
        interfaceType: WinRTTypeDefinition,
        vtableMethod: AuthoredVtableMethod,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        dispatchTarget: AuthoringDispatchTarget,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        val method = vtableMethod.method
        val dispatchMethodName = dispatchTarget.methodName(method)
        val receiveArrayParameter = method.receiveArrayResultParameter()
        validateAuthoredArrayParameterSupport(method, receiveArrayParameter)
        val projectedParameterIndexes = method.parameters.indices.filter { index -> method.parameters[index] != receiveArrayParameter }
        val bridgeArguments = projectedParameterIndexes.joinToString(", ") { index -> "__arg$index" }
        return CodeBlock.builder()
            .add("%T(%L) { rawArgs ->\n", winRTInspectableMethodDefinitionType, renderSignature(method, typesByName, semanticHelpers))
            .indent()
            .add("try {\n")
            .indent()
            .apply {
                var rawIndex = 0
                method.parameters.forEachIndexed { index, parameter ->
                    if (parameter != receiveArrayParameter) {
                        add(
                            "%L",
                            renderParameterProjectionStatement(
                                index,
                                rawIndex,
                                parameter,
                                method,
                                typesByName,
                                semanticHelpers,
                                authoredRuntimeClassNames,
                            ),
                        )
                    }
                    rawIndex += abiArgumentCount(parameter)
                }
            }
            .apply {
                val iterableFirstProjection = renderIterableFirstProjection(
                    interfaceType = interfaceType,
                    method = method,
                    outExpression = CodeBlock.of("rawArgs[%L] as %T", abiArgumentCount(method.parameters), rawAddressType),
                    dispatchTarget = dispatchTarget,
                    typesByName = typesByName,
                    semanticHelpers = semanticHelpers,
                )
                if (iterableFirstProjection != null) {
                    add("%L\n", iterableFirstProjection)
                } else if (isVoidReturn(method) && receiveArrayParameter == null) {
                    addStatement("%L", renderDispatchInvocation(vtableMethod, dispatchTarget, dispatchMethodName, bridgeArguments))
                } else {
                    addStatement(
                        "val __result = %L",
                        renderDispatchInvocation(vtableMethod, dispatchTarget, dispatchMethodName, bridgeArguments),
                    )
                    if (receiveArrayParameter != null) {
                        val receiveArrayRawIndex = rawArgumentIndex(method.parameters, receiveArrayParameter)
                        add(
                            "%L\n",
                            renderArrayReturnProjection(
                                method = method,
                                parameter = receiveArrayParameter,
                                lengthOutExpression = CodeBlock.of("rawArgs[%L] as %T", receiveArrayRawIndex, rawAddressType),
                                dataOutExpression = CodeBlock.of("rawArgs[%L] as %T", receiveArrayRawIndex + 1, rawAddressType),
                                valueExpression = "__result",
                                typesByName = typesByName,
                                semanticHelpers = semanticHelpers,
                            ),
                        )
                    } else {
                        add(
                            "%L\n",
                            renderReturnProjection(
                                method,
                                CodeBlock.of("rawArgs[%L] as %T", abiArgumentCount(method.parameters), rawAddressType),
                                "__result",
                                typesByName,
                                semanticHelpers,
                            ),
                        )
                    }
                }
            }
            .apply {
                method.parameters.forEachIndexed { index, parameter ->
                    if (parameter != receiveArrayParameter && parameter.isFillArrayParameter()) {
                        val rawIndex = rawArgumentIndex(method.parameters, parameter)
                        add(
                            "%L\n",
                            renderArrayFillParameterProjection(
                                method = method,
                                parameter = parameter,
                                valueExpression = "__arg$index",
                                dataExpression = CodeBlock.of("rawArgs[%L] as %T", rawIndex + 1, rawAddressType),
                                typesByName = typesByName,
                                semanticHelpers = semanticHelpers,
                            ),
                        )
                    }
                }
            }
            .addStatement("%T.S_OK.value", knownHResultsType)
            .unindent()
            .add("} catch (__exception: %T) {\n", Throwable::class.asClassName())
            .indent()
            .addStatement("%T.setErrorInfo(__exception)", exceptionHelpersType)
            .addStatement("%T.getHRForException(__exception).value", exceptionHelpersType)
            .unindent()
            .add("}\n")
            .unindent()
            .add("}")
            .build()
    }

    private fun renderDispatchInvocation(
        vtableMethod: AuthoredVtableMethod,
        dispatchTarget: AuthoringDispatchTarget,
        dispatchMethodName: String,
        bridgeArguments: String,
    ): CodeBlock =
        when (vtableMethod.propertyAccessor) {
            PropertyAccessor.Getter -> CodeBlock.of(
                "%L.%L",
                renderDispatchTargetProjection(dispatchTarget),
                projectedPropertyName(vtableMethod.property ?: error("Getter accessor has no property.")),
            )
            PropertyAccessor.Setter -> CodeBlock.of(
                "%L.%L = %L",
                renderDispatchTargetProjection(dispatchTarget),
                projectedPropertyName(vtableMethod.property ?: error("Setter accessor has no property.")),
                bridgeArguments,
            )
            null -> CodeBlock.of(
                "%L.%L(%L)",
                renderDispatchTargetProjection(dispatchTarget),
                dispatchMethodName,
                bridgeArguments,
            )
        }

    private fun renderDispatchTargetProjection(dispatchTarget: AuthoringDispatchTarget): CodeBlock =
        CodeBlock.of("(value as %T)", dispatchTarget.className)

    private fun renderIterableFirstProjection(
        interfaceType: WinRTTypeDefinition,
        method: WinRTMethodDefinition,
        outExpression: CodeBlock,
        dispatchTarget: AuthoringDispatchTarget,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        if (interfaceType.qualifiedName != "Windows.Foundation.Collections.IIterable" || method.name != "First") {
            return null
        }
        val returnType = method.returnType.normalized()
        if (returnType.qualifiedName != "Windows.Foundation.Collections.IIterator") {
            throw IllegalArgumentException(
                "Authored WinRT iterable method ${method.name} returns '${method.returnTypeName}' instead of IIterator<T>.",
            )
        }
        val elementType = returnType.typeArguments.singleOrNull()?.normalized()
            ?: throw IllegalArgumentException(
                "Authored WinRT iterable method ${method.name} returns '${method.returnTypeName}' without exactly one element type.",
            )
        val elementAdapter = renderCollectionElementAdapter(method, elementType, typesByName, semanticHelpers)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.detachReference(%L.iterator(), %L))",
            platformAbiType,
            outExpression,
            winRTIteratorProjectionType,
            renderDispatchTargetProjection(dispatchTarget),
            elementAdapter,
        )
    }

    private fun WinRTTypeDefinition.authoredVtableMethods(): List<AuthoredVtableMethod> {
        val accessors = properties
            .filterNot(WinRTPropertyDefinition::isStatic)
            .flatMap { property ->
                listOfNotNull(
                    property.getterMethodName?.let { accessorName ->
                        AuthoredVtableMethod(
                            method = WinRTMethodDefinition(
                                name = accessorName,
                                returnTypeName = property.typeName,
                                returnTypeSignature = property.type,
                                methodRowId = property.getterMethodRowId,
                            ),
                            property = property,
                            propertyAccessor = PropertyAccessor.Getter,
                        )
                    },
                    property.setterMethodName?.let { accessorName ->
                        AuthoredVtableMethod(
                            method = WinRTMethodDefinition(
                                name = accessorName,
                                returnTypeName = "Void",
                                parameters = listOf(
                                    WinRTParameterDefinition("value", property.typeName, typeSignature = property.type),
                                ),
                                methodRowId = property.setterMethodRowId,
                            ),
                            property = property,
                            propertyAccessor = PropertyAccessor.Setter,
                        )
                    },
                )
            }
        val eventAccessors = events
            .filterNot(WinRTEventDefinition::isStatic)
            .filter(WinRTEventDefinition::hasValidAccessors)
            .flatMap { event ->
                val addMethodName = event.addMethodName ?: "add_${event.name}"
                val removeMethodName = event.removeMethodName ?: "remove_${event.name}"
                listOf(
                    AuthoredVtableMethod(
                        method = methods.firstOrNull { method -> method.name == addMethodName } ?: WinRTMethodDefinition(
                            name = addMethodName,
                            returnTypeName = "Windows.Foundation.EventRegistrationToken",
                            parameters = listOf(
                                WinRTParameterDefinition(
                                    name = "handler",
                                    typeName = event.delegateTypeName,
                                    typeSignature = event.delegateType,
                                ),
                            ),
                            methodRowId = event.addMethodRowId,
                        ),
                    ),
                    AuthoredVtableMethod(
                        method = methods.firstOrNull { method -> method.name == removeMethodName } ?: WinRTMethodDefinition(
                            name = removeMethodName,
                            returnTypeName = "Void",
                            parameters = listOf(
                                WinRTParameterDefinition(
                                    name = "token",
                                    typeName = "Windows.Foundation.EventRegistrationToken",
                                ),
                            ),
                            methodRowId = event.removeMethodRowId,
                        ),
                    ),
                )
            }
        val accessorNames = (accessors + eventAccessors).mapTo(mutableSetOf()) { accessor -> accessor.method.name }
        return (methods.filterNot { method -> method.isStatic || method.name in accessorNames }.map(::AuthoredVtableMethod) + accessors + eventAccessors)
            .sortedWith(compareBy<AuthoredVtableMethod>({ it.method.methodRowId ?: Int.MAX_VALUE }, { it.method.authoringSignatureKey() }))
    }

    private data class AuthoredVtableMethod(
        val method: WinRTMethodDefinition,
        val property: WinRTPropertyDefinition? = null,
        val propertyAccessor: PropertyAccessor? = null,
    )

    private enum class PropertyAccessor {
        Getter,
        Setter,
    }

    private fun WinRTMethodDefinition.authoringSignatureKey(): String =
        buildString {
            append(if (isStatic) 'S' else 'I')
            append('|')
            append(name)
            append('|')
            append(returnType.normalized().typeName)
            append('|')
            append(parameters.joinToString(",") { parameter ->
                "${parameter.name}:${parameter.type.normalized().typeName}:${parameter.direction}:${parameter.isInParameter}:${parameter.isOutParameter}"
            })
        }

    private fun authoredDispatchTarget(
        candidate: KotlinWinRTAuthoredTypeCandidate,
        type: WinRTTypeDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): AuthoringDispatchTarget {
        val exclusiveBaseName = semanticHelpers.getExclusiveToType(type)?.qualifiedName
        val shouldDispatchThroughBaseBridge = exclusiveBaseName != null ||
            type.qualifiedName in candidate.overridableInterfaceNames
        if (!shouldDispatchThroughBaseBridge) {
            return AuthoringDispatchTarget(
                className = sourceClassName(candidate),
                methodName = ::projectedMethodName,
            )
        }
        val dispatchBase = exclusiveBaseName
            ?: candidate.winRTBaseClassName
            ?: error("Authored WinRT override interface ${type.qualifiedName} has no declaring WinRT base class.")
        return AuthoringDispatchTarget(
            className = projectionClassName(dispatchBase, semanticHelpers),
            methodName = ::authoringInvokeBridgeName,
        )
    }

    private fun validateAuthoredArrayParameterSupport(
        method: WinRTMethodDefinition,
        receiveArrayParameter: WinRTParameterDefinition?,
    ) {
        method.parameters.forEach { parameter ->
            if (parameter.type.normalized().kind == WinRTTypeRefKind.Array &&
                (parameter.typeIsByRef || parameter.isOutParameter) &&
                parameter != receiveArrayParameter &&
                !parameter.isFillArrayParameter()
            ) {
                throw IllegalArgumentException(
                    "Authored WinRT override ${method.name} has unsupported array parameter '${parameter.name}' with by-ref/out direction; only trailing receive-array out parameters are supported.",
                )
            }
        }
    }

    private fun WinRTParameterDefinition.isFillArrayParameter(): Boolean =
        type.normalized().kind == WinRTTypeRefKind.Array &&
            (isOutParameter || direction == WinRTParameterDirection.Out) &&
            !typeIsByRef

    private fun WinRTMethodDefinition.receiveArrayResultParameter(): WinRTParameterDefinition? {
        if (returnTypeName != "Unit" && !isWinRTVoidTypeName(returnTypeName)) {
            return null
        }
        val parameter = parameters.singleOrNull { candidate ->
            candidate.type.normalized().kind == WinRTTypeRefKind.Array &&
                candidate.typeIsByRef &&
                candidate.isOutParameter
        } ?: return null
        return parameter
    }

    private fun rawArgumentIndex(
        parameters: List<WinRTParameterDefinition>,
        target: WinRTParameterDefinition,
    ): Int {
        var index = 0
        parameters.forEach { parameter ->
            if (parameter == target) {
                return index
            }
            index += abiArgumentCount(parameter)
        }
        error("Authored WinRT parameter '${target.name}' is not part of the method parameter list.")
    }

    private fun abiArgumentCount(parameters: List<WinRTParameterDefinition>): Int =
        parameters.sumOf(::abiArgumentCount)

    private fun abiArgumentCount(parameter: WinRTParameterDefinition): Int =
        if (parameter.type.normalized().kind == WinRTTypeRefKind.Array) 2 else 1

    private fun renderParameterProjection(
        index: Int,
        parameter: WinRTParameterDefinition,
        method: WinRTMethodDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        val rawArg = CodeBlock.of("rawArgs[%L]", index)
        if (isWinRTObjectTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromAbi(%L as %T)", winRTObjectMarshallerType, rawArg, rawAddressType)
        }
        if (isWinRTStringTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromHandle(%L as %T, owner = false).use { it.toKString() }", hStringType, rawArg, rawAddressType)
        }
        if (isWinRTGuidTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.readGuid(%L as %T)", platformAbiType, rawArg, rawAddressType)
        }
        fundamentalType(parameter.typeName)?.let { type ->
            renderFundamentalParameterProjection(rawArg, type)?.let { projection ->
                return projection
            }
        }
        renderAsyncParameterProjection(rawArg, parameter, typesByName, semanticHelpers)?.let { projection ->
            return projection
        }
        renderCollectionParameterProjection(method, rawArg, parameter, typesByName, semanticHelpers)?.let { projection ->
            return projection
        }
        renderReferenceParameterProjection(method, rawArg, parameter, typesByName, semanticHelpers)?.let { projection ->
            return projection
        }
        renderDelegateParameterProjection(rawArg, parameter, typesByName, semanticHelpers)?.let { projection ->
            return projection
        }
        renderRuntimeOwnedStructParameterProjection(rawArg, parameter)?.let { projection ->
            return projection
        }
        return renderComplexParameterProjection(rawArg, parameter, typesByName, semanticHelpers, authoredRuntimeClassNames)
    }

    private fun renderParameterProjectionStatement(
        index: Int,
        rawIndex: Int,
        parameter: WinRTParameterDefinition,
        method: WinRTMethodDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock =
        if (parameter.type.normalized().kind == WinRTTypeRefKind.Array) {
            CodeBlock.of(
                "val __arg%L = %L\n",
                index,
                renderArrayParameterProjection(parameter, rawIndex, typesByName, semanticHelpers, authoredRuntimeClassNames),
            )
        } else if (isWinRTStringTypeName(parameter.typeName)) {
            CodeBlock.builder()
                .addStatement(
                    "val __hString%L = %T.fromHandle(rawArgs[%L] as %T, owner = false)",
                    index,
                    hStringType,
                    rawIndex,
                    rawAddressType,
                )
                .add("val __arg%L = try {\n", index)
                .indent()
                .addStatement("__hString%L.toKString()", index)
                .unindent()
                .add("} finally {\n")
                .indent()
                .addStatement("__hString%L.close()", index)
                .unindent()
                .add("}\n")
                .build()
        } else {
            CodeBlock.of(
                "val __arg%L = %L\n",
                index,
                renderParameterProjection(rawIndex, parameter, method, typesByName, semanticHelpers, authoredRuntimeClassNames),
            )
        }

    private fun renderArrayParameterProjection(
        parameter: WinRTParameterDefinition,
        rawIndex: Int,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        val arrayType = parameter.type.normalized()
        val elementType = arrayType.elementType?.normalized()
            ?: throw IllegalArgumentException(
                "Authored WinRT parameter '${parameter.name}' array '${parameter.typeName}' has no element type metadata.",
            )
        if (arrayType.arrayRank != 1) {
            throw IllegalArgumentException(
                "Authored WinRT parameter '${parameter.name}' array '${parameter.typeName}' has unsupported rank ${arrayType.arrayRank}.",
            )
        }
        val elementRead = renderArrayElementRead(
            elementType,
            CodeBlock.of("__arrayData"),
            CodeBlock.of("__index"),
            typesByName,
            semanticHelpers,
            authoredRuntimeClassNames,
        )
            ?: throw IllegalArgumentException(
                "Authored WinRT parameter '${parameter.name}' has unsupported array element type '${elementType.typeName}'.",
            )
        return CodeBlock.of(
            "run {\n·val __arrayLength = rawArgs[%L] as %T\n·val __arrayData = rawArgs[%L] as %T\n·%T(__arrayLength) { __index -> %L }\n}",
            rawIndex,
            Int::class.asClassName(),
            rawIndex + 1,
            rawAddressType,
            Array::class.asClassName(),
            elementRead,
        )
    }

    private fun renderComplexParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRTParameterDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        if (parameter.direction == WinRTParameterDirection.Out || parameter.typeIsByRef) {
            return CodeBlock.of("%L as %T", rawArg, rawAddressType)
        }
        val type = typesByName[parameter.typeName]
        return when (type?.kind) {
            WinRTTypeKind.Enum -> CodeBlock.of(
                "%T.Metadata.fromAbi(%L)",
                projectionClassName(parameter.typeName, semanticHelpers),
                renderEnumRawArgument(rawArg, type),
            )
            WinRTTypeKind.Struct -> CodeBlock.of("%T.Metadata.fromAbi(%L as %T)", projectionClassName(parameter.typeName, semanticHelpers), rawArg, rawAddressType)
            WinRTTypeKind.RuntimeClass,
            -> {
                if (parameter.typeName in authoredRuntimeClassNames) {
                    return CodeBlock.of(
                        "%T.fromAbi(%L as %T) as %T",
                        winRTObjectMarshallerType,
                        rawArg,
                        rawAddressType,
                        projectionClassName(parameter.typeName, semanticHelpers),
                    )
                }
                CodeBlock.of(
                    "%T.Metadata.wrap(%T(%T.toRawComPtr(%L as %T), %T.IInspectable, preventReleaseOnDispose = true))",
                    projectionClassName(parameter.typeName, semanticHelpers),
                    iInspectableReferenceType,
                    platformAbiType,
                    rawArg,
                    rawAddressType,
                    iidType,
                )
            }
            WinRTTypeKind.Interface -> CodeBlock.of(
                "%T.Metadata.wrap(%T(%T.toRawComPtr(%L as %T), %T.IUnknown, preventReleaseOnDispose = true))",
                projectionClassName(parameter.typeName, semanticHelpers),
                iUnknownReferenceType,
                platformAbiType,
                rawArg,
                rawAddressType,
                iidType,
            )
            null -> throw IllegalArgumentException(
                "Authored WinRT override parameter '${parameter.name}' of type '${parameter.typeName}' has no metadata.",
            )
            else -> throw IllegalArgumentException(
                "Authored WinRT override parameter '${parameter.name}' has unsupported object type '${parameter.typeName}'.",
            )
        }
    }

    private fun renderEnumRawArgument(rawArg: CodeBlock, type: WinRTTypeDefinition): CodeBlock =
        enumIntegralAbiDescriptor(type).let { descriptor ->
            val carrier = CodeBlock.of("%L as %T", rawArg, descriptor.carrierTypeName)
            if (descriptor.rawCarrierConversionSuffix.isEmpty()) {
                carrier
            } else {
                CodeBlock.of("(%L)%L", carrier, descriptor.rawCarrierConversionSuffix)
            }
        }

    private fun renderFundamentalParameterProjection(
        rawArg: CodeBlock,
        type: WinRTFundamentalType,
    ): CodeBlock? =
        when (type) {
            WinRTFundamentalType.Boolean -> CodeBlock.of("(%L as %T).toInt() != 0", rawArg, Byte::class.asClassName())
            WinRTFundamentalType.Char -> CodeBlock.of("(%L as %T).toInt().toChar()", rawArg, Short::class.asClassName())
            WinRTFundamentalType.Int8 -> CodeBlock.of("%L as %T", rawArg, Byte::class.asClassName())
            WinRTFundamentalType.UInt8 -> CodeBlock.of("(%L as %T).toUByte()", rawArg, Byte::class.asClassName())
            WinRTFundamentalType.Int16 -> CodeBlock.of("%L as %T", rawArg, Short::class.asClassName())
            WinRTFundamentalType.UInt16 -> CodeBlock.of("(%L as %T).toUShort()", rawArg, Short::class.asClassName())
            WinRTFundamentalType.Int32 -> CodeBlock.of("%L as %T", rawArg, Int::class.asClassName())
            WinRTFundamentalType.UInt32 -> CodeBlock.of("(%L as %T).toUInt()", rawArg, Int::class.asClassName())
            WinRTFundamentalType.Int64 -> CodeBlock.of("%L as %T", rawArg, Long::class.asClassName())
            WinRTFundamentalType.UInt64 -> CodeBlock.of("(%L as %T).toULong()", rawArg, Long::class.asClassName())
            WinRTFundamentalType.Float -> CodeBlock.of("%L as %T", rawArg, Float::class.asClassName())
            WinRTFundamentalType.Double -> CodeBlock.of("%L as %T", rawArg, Double::class.asClassName())
            WinRTFundamentalType.String -> null
        }

    private fun renderSignature(
        method: WinRTMethodDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock {
        val kinds = method.parameters.flatMap { parameter -> abiKindNames(parameter, typesByName) } +
            listOfNotNull("Pointer".takeUnless { isVoidReturn(method) || method.receiveArrayResultParameter() != null })
        return if (kinds.isEmpty()) {
            CodeBlock.of("%T()", comMethodSignatureType)
        } else {
            CodeBlock.builder()
                .add("%T.of(", comMethodSignatureType)
                .apply {
                    kinds.forEachIndexed { index, kind ->
                        if (index > 0) {
                            add(", ")
                        }
                        if (kind.startsWith("Struct:")) {
                            add(
                                "%T.Struct(%T.Metadata.layout.abiLayout)",
                                comAbiValueKindType,
                                projectionClassName(kind.removePrefix("Struct:"), semanticHelpers),
                            )
                        } else {
                            add("%T.%L", comAbiValueKindType, kind)
                        }
                    }
                }
                .add(")")
                .build()
        }
    }

    private fun abiKindNames(
        parameter: WinRTParameterDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): List<String> {
        if (parameter.type.normalized().kind == WinRTTypeRefKind.Array) {
            return listOf("Int32", "Pointer")
        }
        fundamentalType(parameter.typeName)?.let { type ->
            return listOf(fundamentalAbiKindName(type))
        }
        val type = typesByName[parameter.typeName]
        return when {
            parameter.typeName.substringBefore('<').removeSuffix("?") == "Windows.Foundation.EventRegistrationToken" &&
                parameter.direction != WinRTParameterDirection.Out &&
                !parameter.typeIsByRef -> listOf("Int64")
            type?.kind == WinRTTypeKind.Enum -> listOf(enumAbiKindName(type))
            type?.kind == WinRTTypeKind.Struct &&
                parameter.direction != WinRTParameterDirection.Out &&
                !parameter.typeIsByRef -> listOf("Struct:${parameter.typeName}")
            else -> listOf("Pointer")
        }
    }

    private fun fundamentalAbiKindName(type: WinRTFundamentalType): String =
        when (type) {
            WinRTFundamentalType.Boolean,
            WinRTFundamentalType.Int8,
            WinRTFundamentalType.UInt8 -> "Int8"
            WinRTFundamentalType.Char,
            WinRTFundamentalType.Int16,
            WinRTFundamentalType.UInt16 -> "Int16"
            WinRTFundamentalType.Int32,
            WinRTFundamentalType.UInt32 -> "Int32"
            WinRTFundamentalType.Int64,
            WinRTFundamentalType.UInt64 -> "Int64"
            WinRTFundamentalType.Float -> "Float"
            WinRTFundamentalType.Double -> "Double"
            WinRTFundamentalType.String -> "Pointer"
        }

    private fun enumAbiKindName(type: WinRTTypeDefinition): String =
        enumIntegralAbiDescriptor(type).abiKindName

    private fun renderReturnProjection(
        method: WinRTMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock {
        if (isWinRTObjectTypeName(method.returnTypeName)) {
            return CodeBlock.of(
                "%T.writePointer(%L, %T.fromManaged(%L))",
                platformAbiType,
                outExpression,
                winRTObjectMarshallerType,
                valueExpression,
            )
        }
        fundamentalType(method.returnTypeName)?.let { type ->
            return renderFundamentalReturnProjection(type, outExpression, valueExpression)
        }
        if (isWinRTGuidTypeName(method.returnTypeName)) {
            return CodeBlock.of("%T.writeGuid(%L, %L as %T)", platformAbiType, outExpression, valueExpression, guidType)
        }
        renderAsyncReturnProjection(method, outExpression, valueExpression, typesByName, semanticHelpers)?.let {
            return it
        }
        renderCollectionReturnProjection(method, outExpression, valueExpression, typesByName, semanticHelpers)?.let {
            return it
        }
        renderReferenceReturnProjection(method, outExpression, valueExpression, typesByName)?.let {
            return it
        }
        renderRuntimeMappedStructReturnProjection(method.returnTypeName, outExpression, valueExpression)?.let {
            return it
        }
        renderRuntimeOwnedStructReturnProjection(method.returnTypeName, outExpression, valueExpression)?.let {
            return it
        }
        val returnType = typesByName[method.returnTypeName]
        renderDelegateReturnProjection(outExpression, valueExpression, returnType)?.let {
            return it
        }
        return when (returnType?.kind) {
            WinRTTypeKind.Enum -> renderEnumReturnProjection(method.returnTypeName, returnType, outExpression, valueExpression, semanticHelpers)
            WinRTTypeKind.Struct -> CodeBlock.of(
                "%T.Metadata.copyTo(%L as %T, %L)",
                projectionClassName(method.returnTypeName, semanticHelpers),
                valueExpression,
                projectionClassName(method.returnTypeName, semanticHelpers),
                outExpression,
            )
            else -> renderObjectReturnProjection(method, returnType, outExpression, valueExpression, typesByName)
        }
    }

    private fun renderFundamentalReturnProjection(
        type: WinRTFundamentalType,
        outExpression: CodeBlock,
        valueExpression: String,
    ): CodeBlock =
        when (type) {
            WinRTFundamentalType.Boolean -> CodeBlock.of(
                "%T.writeInt8(%L, if (%L as %T) 1.toByte() else 0.toByte())",
                platformAbiType,
                outExpression,
                valueExpression,
                Boolean::class.asClassName(),
            )
            WinRTFundamentalType.Char -> CodeBlock.of(
                "%T.writeInt16(%L, (%L as %T).code.toShort())",
                platformAbiType,
                outExpression,
                valueExpression,
                Char::class.asClassName(),
            )
            WinRTFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Byte::class.asClassName())
            WinRTFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, (%L as %T).toByte())", platformAbiType, outExpression, valueExpression, UByte::class.asClassName())
            WinRTFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Short::class.asClassName())
            WinRTFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, (%L as %T).toShort())", platformAbiType, outExpression, valueExpression, UShort::class.asClassName())
            WinRTFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Int::class.asClassName())
            WinRTFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, (%L as %T).toInt())", platformAbiType, outExpression, valueExpression, UInt::class.asClassName())
            WinRTFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Long::class.asClassName())
            WinRTFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, (%L as %T).toLong())", platformAbiType, outExpression, valueExpression, ULong::class.asClassName())
            WinRTFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Float::class.asClassName())
            WinRTFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Double::class.asClassName())
            WinRTFundamentalType.String -> CodeBlock.of("%T.writePointer(%L, %T.create(%L as %T).handle)", platformAbiType, outExpression, hStringType, valueExpression, String::class.asClassName())
        }

    private fun renderRuntimeMappedStructReturnProjection(
        typeName: String,
        outExpression: CodeBlock,
        valueExpression: String,
    ): CodeBlock? =
        when (typeName.substringBefore('<').removeSuffix("?")) {
            "Windows.Foundation.DateTime" -> CodeBlock.of(
                "%T.copyDateTimeTo(%L as %T, %L)",
                winRTSystemProjectionMarshalersType,
                valueExpression,
                instantType,
                outExpression,
            )
            "Windows.Foundation.TimeSpan" -> CodeBlock.of(
                "%T.copyTimeSpanTo(%L as %T, %L)",
                winRTSystemProjectionMarshalersType,
                valueExpression,
                durationType,
                outExpression,
            )
            else -> null
        }

    private fun renderRuntimeOwnedStructParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRTParameterDefinition,
    ): CodeBlock? {
        if (parameter.direction == WinRTParameterDirection.Out || parameter.typeIsByRef) {
            return null
        }
        return when (parameter.typeName.substringBefore('<').removeSuffix("?")) {
            "Windows.Foundation.EventRegistrationToken" -> CodeBlock.of(
                "%T(%L as %T)",
                eventRegistrationTokenType,
                rawArg,
                Long::class.asClassName(),
            )
            else -> null
        }
    }

    private fun renderRuntimeOwnedStructReturnProjection(
        typeName: String,
        outExpression: CodeBlock,
        valueExpression: String,
    ): CodeBlock? =
        when (typeName.substringBefore('<').removeSuffix("?")) {
            "Windows.Foundation.EventRegistrationToken" -> CodeBlock.of(
                "%T.Metadata.copyTo(%L as %T, %L)",
                eventRegistrationTokenType,
                valueExpression,
                eventRegistrationTokenType,
                outExpression,
            )
            else -> null
        }

    private fun renderDelegateParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRTParameterDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        val typeName = parameterType.qualifiedName ?: parameter.typeName
        val definition = typesByName[typeName] ?: return null
        if (definition.kind != WinRTTypeKind.Delegate) {
            return null
        }
        requireAuthoredDelegateMetadata(parameter.name, typeName, definition)
        val delegateType = projectionClassName(typeName, semanticHelpers)
        val projectedTypeArguments = parameterType.typeArguments.map { argument ->
            authoringProjectedTypeName(argument.normalized(), typesByName, semanticHelpers)
        }
        val fromAbi = if (projectedTypeArguments.isEmpty()) {
            CodeBlock.of("%T.Metadata.fromAbi(%L as %T)", delegateType, rawArg, rawAddressType)
        } else {
            CodeBlock.of(
                "%T.Metadata.fromAbi<%L>(%L as %T)",
                delegateType,
                projectedTypeArguments.map { CodeBlock.of("%T", it) }.joinToCodeString(),
                rawArg,
                rawAddressType,
            )
        }
        return CodeBlock.of("%L ?: error(%S)", fromAbi, "WINRT_E_NULL_ABI_DELEGATE_PARAMETER")
    }

    private fun renderDelegateReturnProjection(
        outExpression: CodeBlock,
        valueExpression: String,
        returnType: WinRTTypeDefinition?,
    ): CodeBlock? {
        if (returnType?.kind != WinRTTypeKind.Delegate) {
            return null
        }
        requireAuthoredDelegateMetadata("return", returnType.qualifiedName, returnType)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromProjected(%L as %T))",
            platformAbiType,
            outExpression,
            marshalDelegateType,
            valueExpression,
            winRTProjectedDelegateType,
        )
    }

    private fun requireAuthoredDelegateMetadata(
        usageName: String,
        typeName: String,
        definition: WinRTTypeDefinition,
    ) {
        if (definition.iid == null) {
            throw IllegalArgumentException(
                "Authored WinRT delegate '$typeName' used by '$usageName' has no IID metadata.",
            )
        }
    }

    private fun renderReferenceParameterProjection(
        method: WinRTMethodDefinition,
        rawArg: CodeBlock,
        parameter: WinRTParameterDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        if (parameterType.qualifiedName != "Windows.Foundation.IReference") {
            return null
        }
        val valueType = parameterType.typeArguments.singleOrNull()?.normalized()
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} has nullable parameter '${parameter.name}' without exactly one value type.",
            )
        return CodeBlock.of(
            "%T.fromAbi(%L as %T, %L) as %T",
            winRTReferenceProjectionType,
            rawArg,
            rawAddressType,
            renderReferenceInterfaceId(valueType, typesByName),
            authoringProjectedTypeName(valueType, typesByName, semanticHelpers).copy(nullable = true),
        )
    }

    private fun renderReferenceReturnProjection(
        method: WinRTMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock? {
        val returnType = WinRTTypeRef.fromDisplayName(method.returnTypeName).normalized()
        if (returnType.qualifiedName != "Windows.Foundation.IReference") {
            return null
        }
        val valueType = returnType.typeArguments.singleOrNull()?.normalized()
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns nullable type '${method.returnTypeName}' without exactly one value type.",
            )
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L, %L))",
            platformAbiType,
            outExpression,
            winRTReferenceProjectionType,
            valueExpression,
            renderReferenceInterfaceId(valueType, typesByName),
        )
    }

    private fun renderReferenceInterfaceId(
        valueType: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock =
        CodeBlock.of(
            "%T.createFromParameterizedInterface(%T.IReference, %L)",
            parameterizedInterfaceIdType,
            iidType,
            renderWinRTTypeSignature(valueType, typesByName),
        )

    private fun renderCollectionParameterProjection(
        method: WinRTMethodDefinition,
        rawArg: CodeBlock,
        parameter: WinRTParameterDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        val collectionTypeName = parameterType.qualifiedName ?: parameter.typeName.substringBefore('<')
        val projectionType = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable" -> winRTIterableProjectionType
            "Windows.Foundation.Collections.IVectorView" -> winRTReadOnlyListProjectionType
            "Windows.Foundation.Collections.IVector" -> winRTListProjectionType
            "Windows.Foundation.Collections.IMapView" -> winRTReadOnlyDictionaryProjectionType
            "Windows.Foundation.Collections.IMap" -> winRTDictionaryProjectionType
            else -> return null
        }
        val fallback = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable",
            "Windows.Foundation.Collections.IVectorView",
            -> CodeBlock.of("emptyList()")
            "Windows.Foundation.Collections.IVector" -> CodeBlock.of("mutableListOf()")
            "Windows.Foundation.Collections.IMapView" -> CodeBlock.of("emptyMap()")
            "Windows.Foundation.Collections.IMap" -> CodeBlock.of("linkedMapOf()")
            else -> return null
        }
        val adapterArguments = when (collectionTypeName) {
            "Windows.Foundation.Collections.IMapView",
            "Windows.Foundation.Collections.IMap" -> {
                if (parameterType.typeArguments.size != 2) {
                    throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' collection type '${parameter.typeName}' does not have exactly two argument types.",
                    )
                }
                listOf(
                    renderCollectionElementAdapter(method, parameterType.typeArguments[0].normalized(), typesByName, semanticHelpers),
                    renderCollectionElementAdapter(method, parameterType.typeArguments[1].normalized(), typesByName, semanticHelpers),
                )
            }
            else -> {
                val elementType = parameterType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' collection type '${parameter.typeName}' does not have exactly one element type.",
                    )
                listOf(renderCollectionElementAdapter(method, elementType, typesByName, semanticHelpers))
            }
        }
        return CodeBlock.of(
            "%T.fromAbi(%L as %T%L) ?: %L",
            projectionType,
            rawArg,
            rawAddressType,
            CodeBlock.of("%L", adapterArguments.joinToCodeString(prefix = ", ")),
            fallback,
        )
    }

    private fun renderAsyncParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRTParameterDefinition,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        val parameterTypeName = parameterType.qualifiedName ?: return null
        val projectedType = when (parameterTypeName) {
            "Windows.Foundation.IAsyncAction" -> winRTAsyncActionReferenceType
            "Windows.Foundation.IAsyncActionWithProgress" -> {
                val progressType = parameterType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' async type '${parameter.typeName}' does not have exactly one progress type.",
                    )
                winRTAsyncActionWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(progressType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperation" -> {
                val resultType = parameterType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' async type '${parameter.typeName}' does not have exactly one result type.",
                    )
                winRTAsyncOperationReferenceType.parameterizedBy(
                    authoringProjectedTypeName(resultType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperationWithProgress" -> {
                if (parameterType.typeArguments.size != 2) {
                    throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' async type '${parameter.typeName}' does not have exactly two argument types.",
                    )
                }
                winRTAsyncOperationWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(parameterType.typeArguments[0].normalized(), typesByName, semanticHelpers),
                    authoringProjectedTypeName(parameterType.typeArguments[1].normalized(), typesByName, semanticHelpers),
                )
            }
            else -> return null
        }
        return CodeBlock.of(
            "%T.fromAbi(%L as %T) as %T",
            winRTObjectMarshallerType,
            rawArg,
            rawAddressType,
            projectedType,
        )
    }

    private fun renderArrayReturnProjection(
        method: WinRTMethodDefinition,
        parameter: WinRTParameterDefinition,
        lengthOutExpression: CodeBlock,
        dataOutExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock {
        val arrayType = parameter.type.normalized()
        val elementType = arrayType.elementType?.normalized()
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns array '${parameter.typeName}' without element type metadata.",
            )
        if (arrayType.arrayRank != 1) {
            throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns array '${parameter.typeName}' with unsupported rank ${arrayType.arrayRank}.",
            )
        }
        val (elementSize, elementAlignment) = arrayElementLayout(elementType, typesByName, semanticHelpers)
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns unsupported array element type '${elementType.typeName}'.",
            )
        val elementWrite = renderArrayElementWrite(method, elementType, CodeBlock.of("__returnArrayData"), CodeBlock.of("__index"), CodeBlock.of("__element"), typesByName, semanticHelpers)
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns unsupported array element type '${elementType.typeName}'.",
            )
        return CodeBlock.of(
            "run {\n·val __returnArrayMemory = %T.allocateBytesOwned(%L.size.toLong() * %L, %L)\n·val __returnArrayData = __returnArrayMemory.pointer\n·%L.forEachIndexed { __index, __element -> %L }\n·%T.writeInt32(%L, %L.size)\n·%T.writePointer(%L, __returnArrayData)\n}",
            platformAbiType,
            valueExpression,
            elementSize,
            elementAlignment,
            valueExpression,
            elementWrite,
            platformAbiType,
            lengthOutExpression,
            valueExpression,
            platformAbiType,
            dataOutExpression,
        )
    }

    private fun renderArrayFillParameterProjection(
        method: WinRTMethodDefinition,
        parameter: WinRTParameterDefinition,
        valueExpression: String,
        dataExpression: CodeBlock,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock {
        val arrayType = parameter.type.normalized()
        val elementType = arrayType.elementType?.normalized()
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} fills array '${parameter.typeName}' without element type metadata.",
            )
        if (arrayType.arrayRank != 1) {
            throw IllegalArgumentException(
                "Authored WinRT override ${method.name} fills array '${parameter.typeName}' with unsupported rank ${arrayType.arrayRank}.",
            )
        }
        val elementWrite = renderArrayElementWrite(
            method,
            elementType,
            dataExpression,
            CodeBlock.of("__index"),
            CodeBlock.of("__element"),
            typesByName,
            semanticHelpers,
        )
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} fills unsupported array element type '${elementType.typeName}'.",
            )
        return CodeBlock.of("%L.forEachIndexed { __index, __element -> %L }", valueExpression, elementWrite)
    }

    private fun arrayElementLayout(
        type: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): Pair<CodeBlock, CodeBlock>? =
        fundamentalType(type.typeName)?.let { fundamental ->
            when (fundamental) {
                WinRTFundamentalType.Boolean,
                WinRTFundamentalType.Int8,
                WinRTFundamentalType.UInt8 -> CodeBlock.of("1") to CodeBlock.of("1")
                WinRTFundamentalType.Char,
                WinRTFundamentalType.Int16,
                WinRTFundamentalType.UInt16 -> CodeBlock.of("2") to CodeBlock.of("2")
                WinRTFundamentalType.Int32,
                WinRTFundamentalType.UInt32,
                WinRTFundamentalType.Float -> CodeBlock.of("4") to CodeBlock.of("4")
                WinRTFundamentalType.Int64,
                WinRTFundamentalType.UInt64,
                WinRTFundamentalType.Double,
                WinRTFundamentalType.String -> CodeBlock.of("8") to CodeBlock.of("8")
            }
        } ?: when (typesByName[type.qualifiedName]?.kind) {
            WinRTTypeKind.Interface,
            WinRTTypeKind.RuntimeClass,
            -> CodeBlock.of("8") to CodeBlock.of("8")
            WinRTTypeKind.Struct -> {
                val elementTypeName = type.qualifiedName ?: return null
                val projectionType = projectionClassName(elementTypeName, semanticHelpers)
                CodeBlock.of("%T.Metadata.layout.sizeBytes", projectionType) to
                    CodeBlock.of("%T.Metadata.layout.alignmentBytes", projectionType)
            }
            else -> null
        }

    private fun renderArrayElementWrite(
        method: WinRTMethodDefinition,
        type: WinRTTypeRef,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
        valueExpression: CodeBlock,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? =
        when (fundamentalType(type.typeName)) {
            WinRTFundamentalType.Boolean -> CodeBlock.of(
                "%T.writeInt8(%T.slice(%L, %L.toLong(), 1), if (%L) 1.toByte() else 0.toByte())",
                platformAbiType,
                platformAbiType,
                dataExpression,
                indexExpression,
                valueExpression,
            )
            WinRTFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%T.slice(%L, %L.toLong(), 1), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%T.slice(%L, %L.toLong(), 1), %L.toByte())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.Char -> CodeBlock.of("%T.writeInt16(%T.slice(%L, %L.toLong() * 2, 2), %L.code.toShort())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%T.slice(%L, %L.toLong() * 2, 2), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%T.slice(%L, %L.toLong() * 2, 2), %L.toShort())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%T.slice(%L, %L.toLong() * 4, 4), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%T.slice(%L, %L.toLong() * 4, 4), %L.toInt())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%T.slice(%L, %L.toLong() * 4, 4), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%T.slice(%L, %L.toLong() * 8, 8), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%T.slice(%L, %L.toLong() * 8, 8), %L.toLong())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%T.slice(%L, %L.toLong() * 8, 8), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRTFundamentalType.String -> CodeBlock.of(
                "%T.writePointer(%T.slice(%L, %L.toLong() * 8, 8), %T.create(%L).handle)",
                platformAbiType,
                platformAbiType,
                dataExpression,
                indexExpression,
                hStringType,
                valueExpression,
            )
            null -> {
                val elementTypeName = type.qualifiedName ?: return null
                val elementDefinition = typesByName[elementTypeName] ?: return null
                when (elementDefinition.kind) {
                    WinRTTypeKind.Interface,
                    WinRTTypeKind.RuntimeClass,
                    -> CodeBlock.of(
                        "%T.writePointer(%T.slice(%L, %L.toLong() * 8, 8), %T.detachCCWForObject(%L, %L))",
                        platformAbiType,
                        platformAbiType,
                        dataExpression,
                        indexExpression,
                        comWrappersSupportType,
                        valueExpression,
                        renderObjectInterfaceId(method, elementTypeName, elementDefinition, typesByName),
                    )
                    WinRTTypeKind.Struct -> {
                        val projectionType = projectionClassName(elementTypeName, semanticHelpers)
                        CodeBlock.of(
                            "%T.Metadata.copyTo(%L as %T, %T.slice(%L, %L.toLong() * %T.Metadata.layout.sizeBytes, %T.Metadata.layout.sizeBytes))",
                            projectionType,
                            valueExpression,
                            projectionType,
                            platformAbiType,
                            dataExpression,
                            indexExpression,
                            projectionType,
                            projectionType,
                        )
                    }
                    else -> null
                }
            }
        }

    private fun renderArrayElementRead(
        type: WinRTTypeRef,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock? =
        when (fundamentalType(type.typeName)) {
            WinRTFundamentalType.Boolean -> CodeBlock.of(
                "%T.readInt8(%T.slice(%L, %L.toLong(), 1)).toInt() != 0",
                platformAbiType,
                platformAbiType,
                dataExpression,
                indexExpression,
            )
            WinRTFundamentalType.Int8 -> CodeBlock.of("%T.readInt8(%T.slice(%L, %L.toLong(), 1))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.UInt8 -> CodeBlock.of("%T.readInt8(%T.slice(%L, %L.toLong(), 1)).toUByte()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.Char -> CodeBlock.of("%T.readInt16(%T.slice(%L, %L.toLong() * 2, 2)).toInt().toChar()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.Int16 -> CodeBlock.of("%T.readInt16(%T.slice(%L, %L.toLong() * 2, 2))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.UInt16 -> CodeBlock.of("%T.readInt16(%T.slice(%L, %L.toLong() * 2, 2)).toUShort()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.Int32 -> CodeBlock.of("%T.readInt32(%T.slice(%L, %L.toLong() * 4, 4))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.UInt32 -> CodeBlock.of("%T.readInt32(%T.slice(%L, %L.toLong() * 4, 4)).toUInt()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.Float -> CodeBlock.of("%T.readFloat(%T.slice(%L, %L.toLong() * 4, 4))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.Int64 -> CodeBlock.of("%T.readInt64(%T.slice(%L, %L.toLong() * 8, 8))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.UInt64 -> CodeBlock.of("%T.readInt64(%T.slice(%L, %L.toLong() * 8, 8)).toULong()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.Double -> CodeBlock.of("%T.readDouble(%T.slice(%L, %L.toLong() * 8, 8))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRTFundamentalType.String -> CodeBlock.of(
                "%T.fromHandle(%T.readPointer(%T.slice(%L, %L.toLong() * 8, 8)), owner = false).toKString()",
                hStringType,
                platformAbiType,
                platformAbiType,
                dataExpression,
                indexExpression,
            )
            null -> {
                val elementTypeName = type.qualifiedName ?: return null
                val elementDefinition = typesByName[elementTypeName] ?: return null
                val elementPointer = CodeBlock.of(
                    "%T.readPointer(%T.slice(%L, %L.toLong() * 8, 8))",
                    platformAbiType,
                    platformAbiType,
                    dataExpression,
                    indexExpression,
                )
                when (elementDefinition.kind) {
                    WinRTTypeKind.RuntimeClass,
                    WinRTTypeKind.Interface,
                    -> {
                        if (elementTypeName in authoredRuntimeClassNames) {
                            CodeBlock.of(
                                "%T.fromAbi(%L) as %T",
                                winRTObjectMarshallerType,
                                elementPointer,
                                projectionClassName(elementTypeName, semanticHelpers),
                            )
                        } else {
                            CodeBlock.of(
                                "%T.Metadata.wrap(%T(%T.toRawComPtr(%L), %T.IInspectable, preventReleaseOnDispose = true))",
                                projectionClassName(elementTypeName, semanticHelpers),
                                iInspectableReferenceType,
                                platformAbiType,
                                elementPointer,
                                iidType,
                            )
                        }
                    }
                    WinRTTypeKind.Struct -> {
                        val projectionType = projectionClassName(elementTypeName, semanticHelpers)
                        CodeBlock.of(
                            "%T.Metadata.fromAbi(%T.slice(%L, %L.toLong() * %T.Metadata.layout.sizeBytes, %T.Metadata.layout.sizeBytes))",
                            projectionType,
                            platformAbiType,
                            dataExpression,
                            indexExpression,
                            projectionType,
                            projectionType,
                        )
                    }
                    else -> null
                }
            }
        }

    private fun isWinRTStringTypeName(typeName: String): Boolean =
        fundamentalType(typeName) == WinRTFundamentalType.String

    private fun fundamentalType(typeName: String): WinRTFundamentalType? =
        winRTFundamentalTypeForName(typeName)

    private fun renderCollectionReturnProjection(
        method: WinRTMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val returnTypeName = method.returnTypeName
        val returnType = WinRTTypeRef.fromDisplayName(returnTypeName).normalized()
        val collectionTypeName = returnType.qualifiedName ?: return null
        val projectionType = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable" -> winRTIterableProjectionType
            "Windows.Foundation.Collections.IVectorView" -> winRTReadOnlyListProjectionType
            "Windows.Foundation.Collections.IVector" -> winRTListProjectionType
            "Windows.Foundation.Collections.IMapView" -> winRTReadOnlyDictionaryProjectionType
            "Windows.Foundation.Collections.IMap" -> winRTDictionaryProjectionType
            else -> return null
        }
        val adapterArguments = when (collectionTypeName) {
            "Windows.Foundation.Collections.IMapView",
            "Windows.Foundation.Collections.IMap" -> {
                if (returnType.typeArguments.size != 2) {
                    throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns map collection type '$returnTypeName' without exactly two argument types.",
                    )
                }
                listOf(
                    renderCollectionElementAdapter(method, returnType.typeArguments[0].normalized(), typesByName, semanticHelpers),
                    renderCollectionElementAdapter(method, returnType.typeArguments[1].normalized(), typesByName, semanticHelpers),
                )
            }
            else -> {
                val elementType = returnType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns collection type '$returnTypeName' without exactly one element type.",
                    )
                listOf(renderCollectionElementAdapter(method, elementType, typesByName, semanticHelpers))
            }
        }
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromManaged(%L%L))",
            platformAbiType,
            outExpression,
            projectionType,
            valueExpression,
            CodeBlock.of("%L", adapterArguments.joinToCodeString(prefix = ", ")),
        )
    }

    private fun renderAsyncReturnProjection(
        method: WinRTMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val returnType = WinRTTypeRef.fromDisplayName(method.returnTypeName).normalized()
        val returnTypeName = returnType.qualifiedName ?: return null
        val interfaceId = when (returnTypeName) {
            "Windows.Foundation.IAsyncAction" -> CodeBlock.of("%T.IAsyncAction", winRTAsyncInterfaceIdsType)
            "Windows.Foundation.IAsyncActionWithProgress" -> {
                val progressType = returnType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns async type '${method.returnTypeName}' without exactly one progress type.",
                    )
                CodeBlock.of(
                    "%T.createFromParameterizedInterface(%T.IAsyncActionWithProgressGeneric, %L)",
                    parameterizedInterfaceIdType,
                    winRTAsyncInterfaceIdsType,
                    renderWinRTTypeSignature(progressType, typesByName),
                )
            }
            "Windows.Foundation.IAsyncOperation" -> {
                val resultType = returnType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns async type '${method.returnTypeName}' without exactly one result type.",
                    )
                CodeBlock.of(
                    "%T.createFromParameterizedInterface(%T.IAsyncOperationGeneric, %L)",
                    parameterizedInterfaceIdType,
                    winRTAsyncInterfaceIdsType,
                    renderWinRTTypeSignature(resultType, typesByName),
                )
            }
            "Windows.Foundation.IAsyncOperationWithProgress" -> {
                if (returnType.typeArguments.size != 2) {
                    throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns async type '${method.returnTypeName}' without exactly two argument types.",
                    )
                }
                CodeBlock.of(
                    "%T.createFromParameterizedInterface(%T.IAsyncOperationWithProgressGeneric, %L, %L)",
                    parameterizedInterfaceIdType,
                    winRTAsyncInterfaceIdsType,
                    renderWinRTTypeSignature(returnType.typeArguments[0].normalized(), typesByName),
                    renderWinRTTypeSignature(returnType.typeArguments[1].normalized(), typesByName),
                )
            }
            else -> return null
        }
        return CodeBlock.of(
            "%T.writePointer(%L, %T.detachCCWForObject(%L, %L))",
            platformAbiType,
            outExpression,
            comWrappersSupportType,
            valueExpression,
            interfaceId,
        )
    }

    private fun WinRTTypeRef.displayName(): String =
        qualifiedName ?: toString()

    private fun renderCollectionElementAdapter(
        method: WinRTMethodDefinition,
        elementType: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock {
        val elementTypeName = elementType.qualifiedName
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns collection element '${elementType.displayName()}' without a qualified type name.",
            )
        renderNestedCollectionElementAdapter(method, elementType, typesByName, semanticHelpers)?.let { return it }
        if (isWinRTObjectTypeName(elementTypeName)) {
            return CodeBlock.of("%T.object_", winRTReferenceValueAdaptersType)
        }
        if (isWinRTStringTypeName(elementTypeName)) {
            return CodeBlock.of("%T.string", winRTReferenceValueAdaptersType)
        }
        if (elementTypeName == "Windows.Foundation.Collections.IKeyValuePair") {
            if (elementType.typeArguments.size != 2) {
                throw IllegalArgumentException(
                    "Authored WinRT override ${method.name} returns key-value pair element '${elementType.typeName}' without exactly two argument types.",
                )
            }
            return CodeBlock.of(
                "%M(%L, %L)",
                winRTKeyValuePairAdapterMember,
                renderCollectionElementAdapter(method, elementType.typeArguments[0].normalized(), typesByName, semanticHelpers),
                renderCollectionElementAdapter(method, elementType.typeArguments[1].normalized(), typesByName, semanticHelpers),
            )
        }
        val elementDefinition = typesByName[elementTypeName]
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns collection element type '$elementTypeName' without metadata.",
            )
        return when (elementDefinition.kind) {
            WinRTTypeKind.RuntimeClass -> CodeBlock.of(
                "%T.runtimeClass(%T::class, %S, %T.Metadata.DEFAULT_INTERFACE_IID) { %T.Metadata.wrap(it) }",
                winRTReferenceValueAdaptersType,
                projectionClassName(elementTypeName, semanticHelpers),
                elementTypeName,
                projectionClassName(elementTypeName, semanticHelpers),
                projectionClassName(elementTypeName, semanticHelpers),
            )
            WinRTTypeKind.Interface -> {
                val iid = elementDefinition.iid
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns interface collection element '$elementTypeName' without IID metadata.",
                    )
                CodeBlock.of(
                    "%T<%T>(projectedTypeName = %S, typeSignature = %T.object_(), projector = { reference -> %T.Metadata.wrap(reference!!) }, marshaller = { value -> (value as %T).nativeObject.queryInterface(%T(%S)).getOrThrow() })",
                    winRTReferenceValueAdapterType,
                    projectionClassName(elementTypeName, semanticHelpers),
                    elementTypeName,
                    winRTTypeSignatureType,
                    projectionClassName(elementTypeName, semanticHelpers),
                    iWinRTObjectType,
                    guidType,
                    iid.toString().lowercase(),
                )
            }
            WinRTTypeKind.Struct -> {
                val projectedType = projectionClassName(elementTypeName, semanticHelpers)
                CodeBlock.of(
                    "%T.valueType(%T::class, %S, %L)",
                    winRTReferenceValueAdaptersType,
                    projectedType,
                    elementTypeName,
                    renderWinRTTypeSignature(elementType, typesByName),
                )
            }
            else -> throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns unsupported collection element type '$elementTypeName'.",
            )
        }
    }

    private fun renderNestedCollectionElementAdapter(
        method: WinRTMethodDefinition,
        elementType: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock? {
        val elementTypeName = elementType.qualifiedName ?: return null
        val descriptor = when (elementTypeName) {
            "Windows.Foundation.Collections.IIterable" -> NestedCollectionProjectionDescriptor(
                projectedType = Iterable::class.asClassName(),
                projectionType = winRTIterableProjectionType,
            )
            "Windows.Foundation.Collections.IVectorView" -> NestedCollectionProjectionDescriptor(
                projectedType = List::class.asClassName(),
                projectionType = winRTReadOnlyListProjectionType,
            )
            "Windows.Foundation.Collections.IVector" -> NestedCollectionProjectionDescriptor(
                projectedType = MutableList::class.asClassName(),
                projectionType = winRTListProjectionType,
                typeArgumentCount = 1,
                emptyValueExpression = "mutableListOf()",
            )
            "Windows.Foundation.Collections.IMapView" -> NestedCollectionProjectionDescriptor(
                projectedType = Map::class.asClassName(),
                projectionType = winRTReadOnlyDictionaryProjectionType,
                typeArgumentCount = 2,
                emptyValueExpression = "emptyMap()",
            )
            "Windows.Foundation.Collections.IMap" -> NestedCollectionProjectionDescriptor(
                projectedType = MutableMap::class.asClassName(),
                projectionType = winRTDictionaryProjectionType,
                typeArgumentCount = 2,
                emptyValueExpression = "linkedMapOf()",
            )
            else -> return null
        }
        if (elementType.typeArguments.size != descriptor.typeArgumentCount) {
            throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns collection element type '${elementType.typeName}' without exactly ${descriptor.typeArgumentCount} nested argument type(s).",
            )
        }
        val nestedArgumentTypes = elementType.typeArguments.map { it.normalized() }
        val nestedArgumentAdapters = nestedArgumentTypes.map { nestedType ->
            renderCollectionElementAdapter(method, nestedType, typesByName, semanticHelpers)
        }
        val nestedProjectedTypes = nestedArgumentTypes.map { nestedType ->
            authoringProjectedTypeName(nestedType, typesByName, semanticHelpers)
        }
        val projectedType = descriptor.projectedType.parameterizedBy(nestedProjectedTypes)
        val typeSignature = renderWinRTTypeSignature(elementType, typesByName)
        return CodeBlock.of(
            "%T<%T>(projectedTypeName = %S, typeSignature = %L, projector = { reference -> if (reference == null) %L else %T.fromAbi(%T.fromRawComPtr(reference.pointer)%L) ?: %L }, marshaller = { value -> %T(%T.toRawComPtr(%T.fromManaged(value%L)), %T.createFromSignature(%L)) })",
            winRTReferenceValueAdapterType,
            projectedType,
            elementType.typeName,
            typeSignature,
            descriptor.emptyValueExpression,
            descriptor.projectionType,
            platformAbiType,
            CodeBlock.of("%L", nestedArgumentAdapters.joinToCodeString(prefix = ", ")),
            descriptor.emptyValueExpression,
            iUnknownReferenceType,
            platformAbiType,
            descriptor.projectionType,
            CodeBlock.of("%L", nestedArgumentAdapters.joinToCodeString(prefix = ", ")),
            parameterizedInterfaceIdType,
            typeSignature,
        )
    }

    private fun List<CodeBlock>.joinToCodeString(prefix: String = ""): CodeBlock =
        if (isEmpty()) {
            CodeBlock.of("")
        } else {
            CodeBlock.of(prefix + joinToString(", ") { "%L" }, *toTypedArray())
        }

    private fun authoringProjectedTypeName(
        type: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): TypeName {
        val typeName = type.qualifiedName
            ?: throw IllegalArgumentException("Authored WinRT collection element '${type.displayName()}' has no projected type name.")
        renderAsyncProjectedType(type, typesByName, semanticHelpers)?.let { return it }
        renderNestedCollectionProjectedType(type, typesByName, semanticHelpers)?.let { return it }
        if (isWinRTObjectTypeName(typeName)) {
            return ANY.copy(nullable = true)
        }
        if (isWinRTStringTypeName(typeName)) {
            return String::class.asClassName()
        }
        fundamentalProjectedTypeName(typeName)?.let { return it }
        val definition = typesByName[typeName]
            ?: throw IllegalArgumentException("Authored WinRT collection element type '$typeName' has no metadata.")
        return when (definition.kind) {
            WinRTTypeKind.RuntimeClass -> projectionClassName(typeName, semanticHelpers)
            WinRTTypeKind.Enum -> projectionClassName(typeName, semanticHelpers)
            WinRTTypeKind.Struct -> projectionClassName(typeName, semanticHelpers)
            else -> throw IllegalArgumentException("Authored WinRT collection element type '$typeName' is not projectable.")
        }
    }

    private fun fundamentalProjectedTypeName(typeName: String): ClassName? =
        when (fundamentalType(typeName)) {
            WinRTFundamentalType.Boolean -> Boolean::class.asClassName()
            WinRTFundamentalType.Char -> Char::class.asClassName()
            WinRTFundamentalType.Int8 -> Byte::class.asClassName()
            WinRTFundamentalType.UInt8 -> UByte::class.asClassName()
            WinRTFundamentalType.Int16 -> Short::class.asClassName()
            WinRTFundamentalType.UInt16 -> UShort::class.asClassName()
            WinRTFundamentalType.Int32 -> Int::class.asClassName()
            WinRTFundamentalType.UInt32 -> UInt::class.asClassName()
            WinRTFundamentalType.Int64 -> Long::class.asClassName()
            WinRTFundamentalType.UInt64 -> ULong::class.asClassName()
            WinRTFundamentalType.Float -> Float::class.asClassName()
            WinRTFundamentalType.Double -> Double::class.asClassName()
            WinRTFundamentalType.String,
            null -> null
        }

    private fun renderAsyncProjectedType(
        type: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): TypeName? {
        val typeName = type.qualifiedName ?: return null
        return when (typeName) {
            "Windows.Foundation.IAsyncAction" -> winRTAsyncActionReferenceType
            "Windows.Foundation.IAsyncActionWithProgress" -> {
                val progressType = type.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException("Authored WinRT async type '${type.typeName}' does not have exactly one progress type.")
                winRTAsyncActionWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(progressType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperation" -> {
                val resultType = type.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException("Authored WinRT async type '${type.typeName}' does not have exactly one result type.")
                winRTAsyncOperationReferenceType.parameterizedBy(
                    authoringProjectedTypeName(resultType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperationWithProgress" -> {
                if (type.typeArguments.size != 2) {
                    throw IllegalArgumentException("Authored WinRT async type '${type.typeName}' does not have exactly two argument types.")
                }
                winRTAsyncOperationWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(type.typeArguments[0].normalized(), typesByName, semanticHelpers),
                    authoringProjectedTypeName(type.typeArguments[1].normalized(), typesByName, semanticHelpers),
                )
            }
            else -> null
        }
    }

    private fun renderNestedCollectionProjectedType(
        type: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): TypeName? {
        val projectedType = when (type.qualifiedName) {
            "Windows.Foundation.Collections.IIterable" -> Iterable::class.asClassName()
            "Windows.Foundation.Collections.IVectorView" -> List::class.asClassName()
            "Windows.Foundation.Collections.IVector" -> MutableList::class.asClassName()
            "Windows.Foundation.Collections.IMapView" -> Map::class.asClassName()
            "Windows.Foundation.Collections.IMap" -> MutableMap::class.asClassName()
            else -> return null
        }
        val expectedArgumentCount = when (type.qualifiedName) {
            "Windows.Foundation.Collections.IMapView",
            "Windows.Foundation.Collections.IMap" -> 2
            else -> 1
        }
        if (type.typeArguments.size != expectedArgumentCount) {
            throw IllegalArgumentException("Authored WinRT collection element type '${type.typeName}' does not have $expectedArgumentCount projected type argument(s).")
        }
        return projectedType.parameterizedBy(
            type.typeArguments.map { argument -> authoringProjectedTypeName(argument.normalized(), typesByName, semanticHelpers) },
        )
    }

    private fun renderWinRTTypeSignature(
        type: WinRTTypeRef,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock {
        val typeName = type.qualifiedName
            ?: throw IllegalArgumentException("Authored WinRT collection element '${type.displayName()}' has no type signature name.")
        when (typeName) {
            "Windows.Foundation.Collections.IIterable" -> {
                val elementSignature = renderWinRTTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.iterableSignature(%L)", winRTCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IVectorView" -> {
                val elementSignature = renderWinRTTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.vectorViewSignature(%L)", winRTCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IVector" -> {
                val elementSignature = renderWinRTTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.vectorSignature(%L)", winRTCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IMapView" -> {
                val keySignature = renderWinRTTypeSignature(type.typeArguments.getOrNull(0)?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                val valueSignature = renderWinRTTypeSignature(type.typeArguments.getOrNull(1)?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.mapViewSignature(%L, %L)", winRTCollectionInterfaceIdsType, keySignature, valueSignature)
            }
            "Windows.Foundation.Collections.IMap" -> {
                val keySignature = renderWinRTTypeSignature(type.typeArguments.getOrNull(0)?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                val valueSignature = renderWinRTTypeSignature(type.typeArguments.getOrNull(1)?.normalized() ?: WinRTTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.mapSignature(%L, %L)", winRTCollectionInterfaceIdsType, keySignature, valueSignature)
            }
        }
        if (isWinRTObjectTypeName(typeName)) {
            return CodeBlock.of("%T.object_()", winRTTypeSignatureType)
        }
        if (isWinRTStringTypeName(typeName)) {
            return CodeBlock.of("%T.string()", winRTTypeSignatureType)
        }
        fundamentalType(typeName)?.let { type ->
            return renderFundamentalTypeSignature(type)
        }
        val definition = typesByName[typeName]
            ?: throw IllegalArgumentException("Authored WinRT collection element type '$typeName' has no metadata signature.")
        return when (definition.kind) {
            WinRTTypeKind.Interface -> {
                if (type.typeArguments.isEmpty()) {
                    CodeBlock.of("%T.object_()", winRTTypeSignatureType)
                } else {
                    val iid = definition.iid
                        ?: throw IllegalArgumentException("Authored WinRT collection element interface '$typeName' has no IID metadata.")
                    CodeBlock.of(
                        "%T.parameterizedInterface(%T(%S)%L)",
                        winRTTypeSignatureType,
                        guidType,
                        iid.toString().lowercase(),
                        type.typeArguments.joinToString(separator = "") { argument ->
                            ", ${renderWinRTTypeSignature(argument.normalized(), typesByName)}"
                        },
                    )
                }
            }
            WinRTTypeKind.RuntimeClass -> CodeBlock.of("%T.object_()", winRTTypeSignatureType)
            WinRTTypeKind.Enum -> CodeBlock.of(
                "%T.enum(%S, %L)",
                winRTTypeSignatureType,
                typeName,
                renderFundamentalTypeSignature(integralFundamentalType(enumIntegralAbiDescriptor(definition).integralType)),
            )
            WinRTTypeKind.Struct -> CodeBlock.of(
                "%T.struct(%S%L)",
                winRTTypeSignatureType,
                typeName,
                definition.fields.joinToString(separator = "") { field ->
                    ", ${renderWinRTTypeSignature(WinRTTypeRef.fromDisplayName(field.typeName).normalized(), typesByName)}"
                },
            )
            else -> throw IllegalArgumentException("Authored WinRT collection element type '$typeName' has no supported type signature.")
        }
    }

    private fun renderFundamentalTypeSignature(type: WinRTFundamentalType): CodeBlock =
        when (type) {
            WinRTFundamentalType.Boolean -> CodeBlock.of("%T.boolean()", winRTTypeSignatureType)
            WinRTFundamentalType.Char -> CodeBlock.of("%T.char16()", winRTTypeSignatureType)
            WinRTFundamentalType.Int8 -> CodeBlock.of("%T.int8()", winRTTypeSignatureType)
            WinRTFundamentalType.UInt8 -> CodeBlock.of("%T.uint8()", winRTTypeSignatureType)
            WinRTFundamentalType.Int16 -> CodeBlock.of("%T.int16()", winRTTypeSignatureType)
            WinRTFundamentalType.UInt16 -> CodeBlock.of("%T.uint16()", winRTTypeSignatureType)
            WinRTFundamentalType.Int32 -> CodeBlock.of("%T.int32()", winRTTypeSignatureType)
            WinRTFundamentalType.UInt32 -> CodeBlock.of("%T.uint32()", winRTTypeSignatureType)
            WinRTFundamentalType.Int64 -> CodeBlock.of("%T.int64()", winRTTypeSignatureType)
            WinRTFundamentalType.UInt64 -> CodeBlock.of("%T.uint64()", winRTTypeSignatureType)
            WinRTFundamentalType.Float -> CodeBlock.of("%T.float32()", winRTTypeSignatureType)
            WinRTFundamentalType.Double -> CodeBlock.of("%T.float64()", winRTTypeSignatureType)
            WinRTFundamentalType.String -> CodeBlock.of("%T.string()", winRTTypeSignatureType)
        }

    private fun renderObjectReturnProjection(
        method: WinRTMethodDefinition,
        returnType: WinRTTypeDefinition?,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock {
        val interfaceId = renderObjectInterfaceId(method, method.returnTypeName, returnType, typesByName)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.detachCCWForObject(%L, %L))",
            platformAbiType,
            outExpression,
            comWrappersSupportType,
            valueExpression,
            interfaceId,
        )
    }

    private fun renderObjectInterfaceId(
        method: WinRTMethodDefinition,
        typeName: String,
        type: WinRTTypeDefinition?,
        typesByName: Map<String, WinRTTypeDefinition>,
    ): CodeBlock {
        val normalizedType = WinRTTypeRef.fromDisplayName(typeName).normalized()
        if (normalizedType.typeArguments.isNotEmpty()) {
            return CodeBlock.of(
                "%T.createFromSignature(%L)",
                parameterizedInterfaceIdType,
                renderWinRTTypeSignature(normalizedType, typesByName),
            )
        }
        return when (type?.kind) {
            WinRTTypeKind.RuntimeClass -> {
                val defaultInterfaceName = type.defaultInterfaceName
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns runtime class '$typeName' without default interface metadata.",
                    )
                val defaultInterfaceType = type.defaultInterface?.normalized()
                    ?: WinRTTypeRef.fromDisplayName(defaultInterfaceName).normalized()
                val defaultInterface = typesByName[defaultInterfaceType.qualifiedName]
                    ?: typesByName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns runtime class '$typeName' whose default interface '$defaultInterfaceName' is missing.",
                    )
                if (defaultInterfaceType.typeArguments.isNotEmpty()) {
                    val argumentSignatures = defaultInterfaceType.typeArguments.joinToString(separator = "") { argument ->
                        ", ${renderWinRTTypeSignature(argument.normalized(), typesByName)}"
                    }
                    val iid = defaultInterface.iid
                        ?: throw IllegalArgumentException(
                            "Authored WinRT override ${method.name} returns runtime class '$typeName' whose default interface '$defaultInterfaceName' has no IID.",
                        )
                    CodeBlock.of(
                        "%T.createFromParameterizedInterface(%T(%S)%L)",
                        parameterizedInterfaceIdType,
                        guidType,
                        iid.toString().lowercase(),
                        argumentSignatures,
                    )
                } else {
                    val iid = defaultInterface.iid
                        ?: throw IllegalArgumentException(
                            "Authored WinRT override ${method.name} returns runtime class '$typeName' whose default interface '$defaultInterfaceName' has no IID.",
                        )
                    CodeBlock.of("%T(%S)", guidType, iid.toString().lowercase())
                }
            }
            WinRTTypeKind.Interface -> {
                val iid = type.iid
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns interface '$typeName' without IID metadata.",
                    )
                CodeBlock.of("%T(%S)", guidType, iid.toString().lowercase())
            }
            null -> throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns '$typeName' without metadata.",
            )
            else -> throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns unsupported object type '$typeName'.",
            )
        }
    }

    private fun renderEnumReturnProjection(
        typeName: String,
        type: WinRTTypeDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): CodeBlock {
        val descriptor = enumIntegralAbiDescriptor(type)
        val abiValue = CodeBlock.of("%T.Metadata.toAbi(%L as %T)", projectionClassName(typeName, semanticHelpers), valueExpression, projectionClassName(typeName, semanticHelpers))
        val writeValue = if (descriptor.abiWriteConversionSuffix.isEmpty()) {
            abiValue
        } else {
            CodeBlock.of("%L%L", abiValue, descriptor.abiWriteConversionSuffix)
        }
        return CodeBlock.of("%T.%L(%L, %L)", platformAbiType, descriptor.writeFunctionName, outExpression, writeValue)
    }

    private fun isVoidReturn(method: WinRTMethodDefinition): Boolean =
        isWinRTVoidTypeName(method.returnTypeName)

    private fun authoringInvokeBridgeName(method: WinRTMethodDefinition): String =
        "__winrtAuthoringInvoke${method.name}"

    private fun projectedMethodName(method: WinRTMethodDefinition): String =
        when {
            method.name.startsWith("add_") -> "add${method.name.removePrefix("add_")}"
            method.name.startsWith("remove_") -> "remove${method.name.removePrefix("remove_")}"
            else -> method.name.replaceFirstChar(Char::lowercase)
        }

    private fun projectedPropertyName(property: WinRTPropertyDefinition): String =
        property.name.replaceFirstChar(Char::lowercase)

    private fun detailsObjectName(candidate: KotlinWinRTAuthoredTypeCandidate): String =
        "WinRT_${candidate.className.replace('$', '_')}_TypeDetails"

    private fun generatedAuthoringTypeDetailsSuppressAnnotation(): AnnotationSpec =
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "KOTLIN_WINRT_GENERATED")
            .addMember("%S", "USELESS_CAST")
            .addMember("%S", "UNCHECKED_CAST")
            .build()

    private fun enumIntegralAbiDescriptor(type: WinRTTypeDefinition): AuthoringEnumIntegralAbiDescriptor =
        enumIntegralAbiDescriptors.getValue(type.enumUnderlyingType ?: WinRTIntegralType.Int32)

    private data class AuthoringEnumIntegralAbiDescriptor(
        val carrierTypeName: ClassName,
        val integralType: WinRTIntegralType,
        val abiKindName: String,
        val rawCarrierConversionSuffix: String = "",
        val writeFunctionName: String,
        val abiWriteConversionSuffix: String = "",
    )

    private fun integralFundamentalType(type: WinRTIntegralType): WinRTFundamentalType =
        when (type) {
            WinRTIntegralType.Int8 -> WinRTFundamentalType.Int8
            WinRTIntegralType.UInt8 -> WinRTFundamentalType.UInt8
            WinRTIntegralType.Int16 -> WinRTFundamentalType.Int16
            WinRTIntegralType.UInt16 -> WinRTFundamentalType.UInt16
            WinRTIntegralType.Int32 -> WinRTFundamentalType.Int32
            WinRTIntegralType.UInt32 -> WinRTFundamentalType.UInt32
            WinRTIntegralType.Int64 -> WinRTFundamentalType.Int64
            WinRTIntegralType.UInt64 -> WinRTFundamentalType.UInt64
        }

    private data class NestedCollectionProjectionDescriptor(
        val projectedType: ClassName,
        val projectionType: ClassName,
        val typeArgumentCount: Int = 1,
        val emptyValueExpression: String = "emptyList()",
    )

    private data class AuthoredInterfaceDescriptor(
        val type: WinRTTypeRef,
        val definition: WinRTTypeDefinition,
    )

    private data class AuthoringDispatchTarget(
        val className: ClassName,
        val methodName: (WinRTMethodDefinition) -> String,
    )

    private fun sourceClassName(candidate: KotlinWinRTAuthoredTypeCandidate): ClassName {
        val names = candidate.className.split('$').filter(String::isNotBlank)
        return ClassName(candidate.packageName, names.first(), *names.drop(1).toTypedArray())
    }

    private fun projectionClassName(
        qualifiedName: String,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): ClassName {
        runtimeMappedClassName(qualifiedName, semanticHelpers)?.let { return it }
        return classNameFromWinRTName(qualifiedName)
    }

    private fun runtimeMappedClassName(
        qualifiedName: String,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): ClassName? =
        semanticHelpers.getMappedType(WinRTTypeRef.fromDisplayName(qualifiedName), "")
            ?.mappedQualifiedName
            ?.takeIf { mappedName -> mappedName.startsWith("io.github.composefluent.winrt.runtime.") }
            ?.let(::classNameFromQualifiedName)

    private fun classNameFromWinRTName(qualifiedName: String): ClassName {
        val lastDot = qualifiedName.lastIndexOf('.')
        if (lastDot < 0) {
            return ClassName("", qualifiedName)
        }
        val namespace = qualifiedName.substring(0, lastDot)
        val simpleName = qualifiedName.substring(lastDot + 1)
        val packageName = namespace.split('.')
            .filter(String::isNotBlank)
            .joinToString(".") { it.lowercase() }
        return ClassName(packageName, simpleName)
    }

    private fun classNameFromQualifiedName(qualifiedName: String): ClassName {
        val lastDot = qualifiedName.lastIndexOf('.')
        if (lastDot < 0) {
            return ClassName("", qualifiedName)
        }
        return ClassName(qualifiedName.substring(0, lastDot), qualifiedName.substring(lastDot + 1))
    }
}

fun authoringTypeDetailsRegistrarName(assemblyName: String?): String {
    val suffix = assemblyName
        ?.map { character -> if (character.isLetterOrDigit()) character else '_' }
        ?.joinToString("")
        ?.trim('_')
        ?.takeIf(String::isNotBlank)
        ?: return "WinRTAuthoringTypeDetailsRegistrar"
    val normalizedSuffix = if (suffix.first().isDigit()) "_$suffix" else suffix
    return "WinRTAuthoringTypeDetailsRegistrar_$normalizedSuffix"
}
