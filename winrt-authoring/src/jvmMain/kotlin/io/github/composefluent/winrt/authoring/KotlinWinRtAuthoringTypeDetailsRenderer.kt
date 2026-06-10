package io.github.composefluent.winrt.authoring

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.WinRtFundamentalType
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtParameterDirection
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.WinRtTypeRefKind
import io.github.composefluent.winrt.metadata.isWinRtObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRtVoidTypeName
import io.github.composefluent.winrt.metadata.winRtFundamentalTypeForName
import java.nio.file.Path
import kotlin.io.path.createDirectories

object KotlinWinRtAuthoringTypeDetailsRenderer {
    private val authoringTypeDetailsRegistrarPackage = "io.github.composefluent.winrt.projections.support"
    private val authoringTypeDetailsRegistrarName = "WinRTAuthoringTypeDetailsRegistrar"
    private val comAbiValueKindType = ClassName("io.github.composefluent.winrt.runtime", "ComAbiValueKind")
    private val comMethodSignatureType = ClassName("io.github.composefluent.winrt.runtime", "ComMethodSignature")
    private val guidType = ClassName("io.github.composefluent.winrt.runtime", "Guid")
    private val hStringType = ClassName("io.github.composefluent.winrt.runtime", "HString")
    private val iUnknownReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IUnknownReference")
    private val iInspectableReferenceType = ClassName("io.github.composefluent.winrt.runtime", "IInspectableReference")
    private val iWinRtObjectType = ClassName("io.github.composefluent.winrt.runtime", "IWinRTObject")
    private val iidType = ClassName("io.github.composefluent.winrt.runtime", "IID")
    private val exceptionHelpersType = ClassName("io.github.composefluent.winrt.runtime", "ExceptionHelpers")
    private val knownHResultsType = ClassName("io.github.composefluent.winrt.runtime", "KnownHResults")
    private val marshalDelegateType = ClassName("io.github.composefluent.winrt.runtime", "MarshalDelegate")
    private val parameterizedInterfaceIdType = ClassName("io.github.composefluent.winrt.runtime", "ParameterizedInterfaceId")
    private val platformAbiType = ClassName("io.github.composefluent.winrt.runtime", "PlatformAbi")
    private val projectionsType = ClassName("io.github.composefluent.winrt.runtime", "Projections")
    private val rawAddressType = ClassName("io.github.composefluent.winrt.runtime", "RawAddress")
    private val comWrappersSupportType = ClassName("io.github.composefluent.winrt.runtime", "ComWrappersSupport")
    private val winRtCcwDefinitionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtCcwDefinition")
    private val winRtInspectableInterfaceDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRtInspectableInterfaceDefinition")
    private val winRtInspectableMethodDefinitionType =
        ClassName("io.github.composefluent.winrt.runtime", "WinRtInspectableMethodDefinition")
    private val winRtCollectionInterfaceIdsType = ClassName("io.github.composefluent.winrt.runtime", "WinRtCollectionInterfaceIds")
    private val winRtProjectedDelegateType = ClassName("io.github.composefluent.winrt.runtime", "WinRtProjectedDelegate")
    private val winRtAsyncInterfaceIdsType = ClassName("io.github.composefluent.winrt.runtime", "WinRtAsyncInterfaceIds")
    private val winRtAsyncActionReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRtAsyncActionReference")
    private val winRtAsyncActionWithProgressReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRtAsyncActionWithProgressReference")
    private val winRtAsyncOperationReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRtAsyncOperationReference")
    private val winRtAsyncOperationWithProgressReferenceType = ClassName("io.github.composefluent.winrt.runtime", "WinRtAsyncOperationWithProgressReference")
    private val winRtDictionaryProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtDictionaryProjection")
    private val winRtIterableProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtIterableProjection")
    private val winRtListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtListProjection")
    private val winRtObjectMarshallerType = ClassName("io.github.composefluent.winrt.runtime", "WinRtObjectMarshaller")
    private val winRtReadOnlyDictionaryProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReadOnlyDictionaryProjection")
    private val winRtReadOnlyListProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReadOnlyListProjection")
    private val winRtReferenceProjectionType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReferenceProjection")
    private val winRtReferenceValueAdapterType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReferenceValueAdapter")
    private val winRtReferenceValueAdaptersType = ClassName("io.github.composefluent.winrt.runtime", "WinRtReferenceValueAdapters")
    private val winRtTypeSignatureType = ClassName("io.github.composefluent.winrt.runtime", "WinRtTypeSignature")
    private val enumIntegralAbiDescriptors = mapOf(
        WinRtIntegralType.Int8 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Byte::class.asClassName(),
            integralType = WinRtIntegralType.Int8,
            abiKindName = "Int8",
            writeFunctionName = "writeInt8",
        ),
        WinRtIntegralType.UInt8 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Byte::class.asClassName(),
            integralType = WinRtIntegralType.UInt8,
            abiKindName = "Int8",
            rawCarrierConversionSuffix = ".toUByte()",
            writeFunctionName = "writeInt8",
            abiWriteConversionSuffix = ".toByte()",
        ),
        WinRtIntegralType.Int16 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Short::class.asClassName(),
            integralType = WinRtIntegralType.Int16,
            abiKindName = "Int16",
            writeFunctionName = "writeInt16",
        ),
        WinRtIntegralType.UInt16 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Short::class.asClassName(),
            integralType = WinRtIntegralType.UInt16,
            abiKindName = "Int16",
            rawCarrierConversionSuffix = ".toUShort()",
            writeFunctionName = "writeInt16",
            abiWriteConversionSuffix = ".toShort()",
        ),
        WinRtIntegralType.Int32 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Int::class.asClassName(),
            integralType = WinRtIntegralType.Int32,
            abiKindName = "Int32",
            writeFunctionName = "writeInt32",
        ),
        WinRtIntegralType.UInt32 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Int::class.asClassName(),
            integralType = WinRtIntegralType.UInt32,
            abiKindName = "Int32",
            rawCarrierConversionSuffix = ".toUInt()",
            writeFunctionName = "writeInt32",
            abiWriteConversionSuffix = ".toInt()",
        ),
        WinRtIntegralType.Int64 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Long::class.asClassName(),
            integralType = WinRtIntegralType.Int64,
            abiKindName = "Int64",
            writeFunctionName = "writeInt64",
        ),
        WinRtIntegralType.UInt64 to AuthoringEnumIntegralAbiDescriptor(
            carrierTypeName = Long::class.asClassName(),
            integralType = WinRtIntegralType.UInt64,
            abiKindName = "Int64",
            rawCarrierConversionSuffix = ".toULong()",
            writeFunctionName = "writeInt64",
            abiWriteConversionSuffix = ".toLong()",
        ),
    )

    fun renderTo(
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        metadataModel: WinRtMetadataModel,
        outputDirectory: Path,
        assemblyName: String? = null,
    ) {
        val typesByName = metadataModel.namespaces
            .flatMap { namespace -> namespace.types }
            .associateBy { type -> type.qualifiedName }
        val semanticHelpers = WinRtMetadataSemanticHelpers(metadataModel)
        val authoredRuntimeClassNames = candidates.mapTo(mutableSetOf(), KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
        val renderedCandidates = candidates.map { candidate ->
            val interfaces = resolveAuthoringInterfaces(candidate, typesByName)
            val packageDirectory = outputDirectory.resolve(candidate.packageName.replace('.', '/'))
            packageDirectory.createDirectories()
            render(candidate, interfaces, typesByName, semanticHelpers, authoredRuntimeClassNames).writeTo(outputDirectory)
            candidate
        }
        renderRegistrar(renderedCandidates, authoringTypeDetailsRegistrarName(assemblyName)).writeTo(outputDirectory)
    }

    private fun resolveAuthoringInterfaces(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): List<WinRtTypeDefinition> {
        if (candidate.winRtInterfaceNames.isEmpty()) {
            throw IllegalArgumentException(
                "Authored type '${candidate.sourceTypeName}' has no WinRT interfaces for TypeDetails generation.",
            )
        }
        return candidate.winRtInterfaceNames.map { interfaceName ->
            val type = typesByName[interfaceName]
                ?: throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references missing WinRT interface '$interfaceName'.",
                )
            if (type.kind != WinRtTypeKind.Interface) {
                throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references non-interface WinRT type '$interfaceName'.",
                )
            }
            if (type.iid == null) {
                throw IllegalArgumentException(
                    "Authored type '${candidate.sourceTypeName}' references WinRT interface '$interfaceName' without metadata IID.",
                )
            }
            validateAuthoredInterfaceMemberSupport(candidate, type)
            type
        }
    }

    private fun validateAuthoredInterfaceMemberSupport(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        type: WinRtTypeDefinition,
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
        val event = type.events.firstOrNull { event ->
            event.addMethodName == null ||
                event.removeMethodName == null ||
                type.methods.none { method -> method.name == event.addMethodName } ||
                type.methods.none { method -> method.name == event.removeMethodName }
        } ?: return
        throw IllegalArgumentException(
            "Authored type '${candidate.sourceTypeName}' references WinRT interface '${type.qualifiedName}' event '${event.name}', but TypeDetails event marshaling is not implemented.",
        )
    }

    private fun render(
        candidate: KotlinWinRtAuthoredTypeCandidate,
        interfaces: List<WinRtTypeDefinition>,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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

    private fun renderRegister(candidate: KotlinWinRtAuthoredTypeCandidate): FunSpec =
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
        candidate: KotlinWinRtAuthoredTypeCandidate,
        interfaces: List<WinRtTypeDefinition>,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): FunSpec {
        val defaultInterface = interfaces.first()
        return FunSpec.builder("createCcwDefinition")
            .addParameter(ParameterSpec.builder("value", ANY).build())
            .returns(winRtCcwDefinitionType)
            .addCode(
                CodeBlock.builder()
                    .add("return %T(\n", winRtCcwDefinitionType)
                    .indent()
                    .add("interfaceDefinitions = listOf(\n")
                    .indent()
                    .apply {
                        interfaces.forEach { type ->
                            add("%L,\n", renderInterface(candidate, type, typesByName, semanticHelpers, authoredRuntimeClassNames))
                        }
                    }
                    .unindent()
                    .add("),\n")
                    .add("defaultInterfaceId = %T(%S),\n", guidType, defaultInterface.iid.toString().lowercase())
                    .add("runtimeClassName = %S,\n", candidate.sourceTypeName)
                    .unindent()
                    .add(")\n")
                    .build(),
            )
            .build()
    }

    private fun renderRegistrar(
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        registrarName: String,
    ): FileSpec =
        FileSpec.builder(authoringTypeDetailsRegistrarPackage, registrarName)
            .addType(
                TypeSpec.objectBuilder(registrarName)
                    .addFunction(renderRegistrarRegister(candidates))
                    .build(),
            )
            .build()

    private fun renderRegistrarRegister(candidates: List<KotlinWinRtAuthoredTypeCandidate>): FunSpec {
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
        candidate: KotlinWinRtAuthoredTypeCandidate,
        type: WinRtTypeDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        val dispatchTarget = authoredDispatchTarget(candidate, type, semanticHelpers)
        return CodeBlock.builder()
            .add("%T(\n", winRtInspectableInterfaceDefinitionType)
            .indent()
            .add("interfaceId = %T(%S),\n", guidType, type.iid.toString().lowercase())
            .add("methods = listOf(\n")
            .indent()
            .apply {
                type.authoredVtableMethods().forEach { vtableMethod ->
                    add("%L,\n", renderMethod(vtableMethod, typesByName, semanticHelpers, dispatchTarget, authoredRuntimeClassNames))
                }
            }
            .unindent()
            .add("),\n")
            .unindent()
            .add(")")
            .build()
    }

    private fun renderMethod(
        vtableMethod: AuthoredVtableMethod,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
            .add("%T(%L) { rawArgs ->\n", winRtInspectableMethodDefinitionType, renderSignature(method, typesByName, semanticHelpers))
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
                if (isVoidReturn(method) && receiveArrayParameter == null) {
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
                "(value as %T).%L",
                dispatchTarget.className,
                projectedPropertyName(vtableMethod.property ?: error("Getter accessor has no property.")),
            )
            PropertyAccessor.Setter -> CodeBlock.of(
                "(value as %T).%L = %L",
                dispatchTarget.className,
                projectedPropertyName(vtableMethod.property ?: error("Setter accessor has no property.")),
                bridgeArguments,
            )
            null -> CodeBlock.of(
                "(value as %T).%L(%L)",
                dispatchTarget.className,
                dispatchMethodName,
                bridgeArguments,
            )
        }

    private fun WinRtTypeDefinition.authoredVtableMethods(): List<AuthoredVtableMethod> {
        val accessors = properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .flatMap { property ->
                listOfNotNull(
                    property.getterMethodName?.let { accessorName ->
                        AuthoredVtableMethod(
                            method = WinRtMethodDefinition(
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
                            method = WinRtMethodDefinition(
                                name = accessorName,
                                returnTypeName = "Void",
                                parameters = listOf(
                                    WinRtParameterDefinition("value", property.typeName, typeSignature = property.type),
                                ),
                                methodRowId = property.setterMethodRowId,
                            ),
                            property = property,
                            propertyAccessor = PropertyAccessor.Setter,
                        )
                    },
                )
            }
        val accessorNames = accessors.mapTo(mutableSetOf()) { accessor -> accessor.method.name }
        return (methods.filterNot { method -> method.isStatic || method.name in accessorNames }.map(::AuthoredVtableMethod) + accessors)
            .sortedWith(compareBy<AuthoredVtableMethod>({ it.method.methodRowId ?: Int.MAX_VALUE }, { it.method.authoringSignatureKey() }))
    }

    private data class AuthoredVtableMethod(
        val method: WinRtMethodDefinition,
        val property: WinRtPropertyDefinition? = null,
        val propertyAccessor: PropertyAccessor? = null,
    )

    private enum class PropertyAccessor {
        Getter,
        Setter,
    }

    private fun WinRtMethodDefinition.authoringSignatureKey(): String =
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
        candidate: KotlinWinRtAuthoredTypeCandidate,
        type: WinRtTypeDefinition,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
            ?: candidate.winRtBaseClassName
            ?: error("Authored WinRT override interface ${type.qualifiedName} has no declaring WinRT base class.")
        return AuthoringDispatchTarget(
            className = projectionClassName(dispatchBase, semanticHelpers),
            methodName = ::authoringInvokeBridgeName,
        )
    }

    private fun validateAuthoredArrayParameterSupport(
        method: WinRtMethodDefinition,
        receiveArrayParameter: WinRtParameterDefinition?,
    ) {
        method.parameters.forEach { parameter ->
            if (parameter.type.normalized().kind == WinRtTypeRefKind.Array &&
                (parameter.typeIsByRef || parameter.isOutParameter) &&
                parameter != receiveArrayParameter
            ) {
                throw IllegalArgumentException(
                    "Authored WinRT override ${method.name} has unsupported array parameter '${parameter.name}' with by-ref/out direction; only trailing receive-array out parameters are supported.",
                )
            }
        }
    }

    private fun WinRtMethodDefinition.receiveArrayResultParameter(): WinRtParameterDefinition? {
        if (returnTypeName != "Unit" && !isWinRtVoidTypeName(returnTypeName)) {
            return null
        }
        val parameter = parameters.singleOrNull { candidate ->
            candidate.type.normalized().kind == WinRtTypeRefKind.Array &&
                candidate.typeIsByRef &&
                candidate.isOutParameter
        } ?: return null
        return parameter
    }

    private fun rawArgumentIndex(
        parameters: List<WinRtParameterDefinition>,
        target: WinRtParameterDefinition,
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

    private fun abiArgumentCount(parameters: List<WinRtParameterDefinition>): Int =
        parameters.sumOf(::abiArgumentCount)

    private fun abiArgumentCount(parameter: WinRtParameterDefinition): Int =
        if (parameter.type.normalized().kind == WinRtTypeRefKind.Array) 2 else 1

    private fun renderParameterProjection(
        index: Int,
        parameter: WinRtParameterDefinition,
        method: WinRtMethodDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        val rawArg = CodeBlock.of("rawArgs[%L]", index)
        if (isWinRtObjectTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromAbi(%L as %T)", winRtObjectMarshallerType, rawArg, rawAddressType)
        }
        if (isWinRtStringTypeName(parameter.typeName)) {
            return CodeBlock.of("%T.fromHandle(%L as %T, owner = false).use { it.toKString() }", hStringType, rawArg, rawAddressType)
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
        return renderComplexParameterProjection(rawArg, parameter, typesByName, semanticHelpers, authoredRuntimeClassNames)
    }

    private fun renderParameterProjectionStatement(
        index: Int,
        rawIndex: Int,
        parameter: WinRtParameterDefinition,
        method: WinRtMethodDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock =
        if (parameter.type.normalized().kind == WinRtTypeRefKind.Array) {
            CodeBlock.of(
                "val __arg%L = %L\n",
                index,
                renderArrayParameterProjection(parameter, rawIndex, typesByName, semanticHelpers, authoredRuntimeClassNames),
            )
        } else if (isWinRtStringTypeName(parameter.typeName)) {
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
        parameter: WinRtParameterDefinition,
        rawIndex: Int,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock {
        if (parameter.direction == WinRtParameterDirection.Out || parameter.typeIsByRef) {
            return CodeBlock.of("%L as %T", rawArg, rawAddressType)
        }
        val type = typesByName[parameter.typeName]
        return when (type?.kind) {
            WinRtTypeKind.Enum -> CodeBlock.of(
                "%T.Metadata.fromAbi(%L)",
                projectionClassName(parameter.typeName, semanticHelpers),
                renderEnumRawArgument(rawArg, type),
            )
            WinRtTypeKind.Struct -> CodeBlock.of("%T.Metadata.fromAbi(%L as %T)", projectionClassName(parameter.typeName, semanticHelpers), rawArg, rawAddressType)
            WinRtTypeKind.RuntimeClass,
            WinRtTypeKind.Interface,
            -> {
                if (parameter.typeName in authoredRuntimeClassNames) {
                    return CodeBlock.of(
                        "%T.fromAbi(%L as %T) as %T",
                        winRtObjectMarshallerType,
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
            null -> throw IllegalArgumentException(
                "Authored WinRT override parameter '${parameter.name}' of type '${parameter.typeName}' has no metadata.",
            )
            else -> throw IllegalArgumentException(
                "Authored WinRT override parameter '${parameter.name}' has unsupported object type '${parameter.typeName}'.",
            )
        }
    }

    private fun renderEnumRawArgument(rawArg: CodeBlock, type: WinRtTypeDefinition): CodeBlock =
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
        type: WinRtFundamentalType,
    ): CodeBlock? =
        when (type) {
            WinRtFundamentalType.Boolean -> CodeBlock.of("(%L as %T).toInt() != 0", rawArg, Byte::class.asClassName())
            WinRtFundamentalType.Char -> CodeBlock.of("(%L as %T).toInt().toChar()", rawArg, Short::class.asClassName())
            WinRtFundamentalType.Int8 -> CodeBlock.of("%L as %T", rawArg, Byte::class.asClassName())
            WinRtFundamentalType.UInt8 -> CodeBlock.of("(%L as %T).toUByte()", rawArg, Byte::class.asClassName())
            WinRtFundamentalType.Int16 -> CodeBlock.of("%L as %T", rawArg, Short::class.asClassName())
            WinRtFundamentalType.UInt16 -> CodeBlock.of("(%L as %T).toUShort()", rawArg, Short::class.asClassName())
            WinRtFundamentalType.Int32 -> CodeBlock.of("%L as %T", rawArg, Int::class.asClassName())
            WinRtFundamentalType.UInt32 -> CodeBlock.of("(%L as %T).toUInt()", rawArg, Int::class.asClassName())
            WinRtFundamentalType.Int64 -> CodeBlock.of("%L as %T", rawArg, Long::class.asClassName())
            WinRtFundamentalType.UInt64 -> CodeBlock.of("(%L as %T).toULong()", rawArg, Long::class.asClassName())
            WinRtFundamentalType.Float -> CodeBlock.of("%L as %T", rawArg, Float::class.asClassName())
            WinRtFundamentalType.Double -> CodeBlock.of("%L as %T", rawArg, Double::class.asClassName())
            WinRtFundamentalType.String -> null
        }

    private fun renderSignature(
        method: WinRtMethodDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): List<String> {
        if (parameter.type.normalized().kind == WinRtTypeRefKind.Array) {
            return listOf("Int32", "Pointer")
        }
        fundamentalType(parameter.typeName)?.let { type ->
            return listOf(fundamentalAbiKindName(type))
        }
        val type = typesByName[parameter.typeName]
        return when {
            type?.kind == WinRtTypeKind.Enum -> listOf(enumAbiKindName(type))
            type?.kind == WinRtTypeKind.Struct &&
                parameter.direction != WinRtParameterDirection.Out &&
                !parameter.typeIsByRef -> listOf("Struct:${parameter.typeName}")
            else -> listOf("Pointer")
        }
    }

    private fun fundamentalAbiKindName(type: WinRtFundamentalType): String =
        when (type) {
            WinRtFundamentalType.Boolean,
            WinRtFundamentalType.Int8,
            WinRtFundamentalType.UInt8 -> "Int8"
            WinRtFundamentalType.Char,
            WinRtFundamentalType.Int16,
            WinRtFundamentalType.UInt16 -> "Int16"
            WinRtFundamentalType.Int32,
            WinRtFundamentalType.UInt32 -> "Int32"
            WinRtFundamentalType.Int64,
            WinRtFundamentalType.UInt64 -> "Int64"
            WinRtFundamentalType.Float -> "Float"
            WinRtFundamentalType.Double -> "Double"
            WinRtFundamentalType.String -> "Pointer"
        }

    private fun enumAbiKindName(type: WinRtTypeDefinition): String =
        enumIntegralAbiDescriptor(type).abiKindName

    private fun renderReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        if (isWinRtObjectTypeName(method.returnTypeName)) {
            return CodeBlock.of(
                "%T.writePointer(%L, %T.fromManaged(%L))",
                platformAbiType,
                outExpression,
                winRtObjectMarshallerType,
                valueExpression,
            )
        }
        fundamentalType(method.returnTypeName)?.let { type ->
            return renderFundamentalReturnProjection(type, outExpression, valueExpression)
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
        val returnType = typesByName[method.returnTypeName]
        renderDelegateReturnProjection(outExpression, valueExpression, returnType)?.let {
            return it
        }
        return when (returnType?.kind) {
            WinRtTypeKind.Enum -> renderEnumReturnProjection(method.returnTypeName, returnType, outExpression, valueExpression, semanticHelpers)
            WinRtTypeKind.Struct -> CodeBlock.of(
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
        type: WinRtFundamentalType,
        outExpression: CodeBlock,
        valueExpression: String,
    ): CodeBlock =
        when (type) {
            WinRtFundamentalType.Boolean -> CodeBlock.of(
                "%T.writeInt8(%L, if (%L as %T) 1.toByte() else 0.toByte())",
                platformAbiType,
                outExpression,
                valueExpression,
                Boolean::class.asClassName(),
            )
            WinRtFundamentalType.Char -> CodeBlock.of(
                "%T.writeInt16(%L, (%L as %T).code.toShort())",
                platformAbiType,
                outExpression,
                valueExpression,
                Char::class.asClassName(),
            )
            WinRtFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Byte::class.asClassName())
            WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%L, (%L as %T).toByte())", platformAbiType, outExpression, valueExpression, UByte::class.asClassName())
            WinRtFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Short::class.asClassName())
            WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%L, (%L as %T).toShort())", platformAbiType, outExpression, valueExpression, UShort::class.asClassName())
            WinRtFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Int::class.asClassName())
            WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%L, (%L as %T).toInt())", platformAbiType, outExpression, valueExpression, UInt::class.asClassName())
            WinRtFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Long::class.asClassName())
            WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%L, (%L as %T).toLong())", platformAbiType, outExpression, valueExpression, ULong::class.asClassName())
            WinRtFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Float::class.asClassName())
            WinRtFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%L, %L as %T)", platformAbiType, outExpression, valueExpression, Double::class.asClassName())
            WinRtFundamentalType.String -> CodeBlock.of("%T.writePointer(%L, %T.create(%L as %T).handle)", platformAbiType, outExpression, hStringType, valueExpression, String::class.asClassName())
        }

    private fun renderDelegateParameterProjection(
        rawArg: CodeBlock,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        val typeName = parameterType.qualifiedName ?: parameter.typeName
        val definition = typesByName[typeName] ?: return null
        if (definition.kind != WinRtTypeKind.Delegate) {
            return null
        }
        requireAuthoredDelegateMetadata(parameter.name, typeName, definition)
        return CodeBlock.of(
            "%T.Metadata.fromAbi(%L as %T)",
            projectionClassName(typeName, semanticHelpers),
            rawArg,
            rawAddressType,
        )
    }

    private fun renderDelegateReturnProjection(
        outExpression: CodeBlock,
        valueExpression: String,
        returnType: WinRtTypeDefinition?,
    ): CodeBlock? {
        if (returnType?.kind != WinRtTypeKind.Delegate) {
            return null
        }
        requireAuthoredDelegateMetadata("return", returnType.qualifiedName, returnType)
        return CodeBlock.of(
            "%T.writePointer(%L, %T.fromProjected(%L as %T))",
            platformAbiType,
            outExpression,
            marshalDelegateType,
            valueExpression,
            winRtProjectedDelegateType,
        )
    }

    private fun requireAuthoredDelegateMetadata(
        usageName: String,
        typeName: String,
        definition: WinRtTypeDefinition,
    ) {
        if (definition.iid == null) {
            throw IllegalArgumentException(
                "Authored WinRT delegate '$typeName' used by '$usageName' has no IID metadata.",
            )
        }
    }

    private fun renderReferenceParameterProjection(
        method: WinRtMethodDefinition,
        rawArg: CodeBlock,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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
            winRtReferenceProjectionType,
            rawArg,
            rawAddressType,
            renderReferenceInterfaceId(valueType, typesByName),
            authoringProjectedTypeName(valueType, typesByName, semanticHelpers).copy(nullable = true),
        )
    }

    private fun renderReferenceReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock? {
        val returnType = WinRtTypeRef.fromDisplayName(method.returnTypeName).normalized()
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
            winRtReferenceProjectionType,
            valueExpression,
            renderReferenceInterfaceId(valueType, typesByName),
        )
    }

    private fun renderReferenceInterfaceId(
        valueType: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock =
        CodeBlock.of(
            "%T.createFromParameterizedInterface(%T.IReference, %L)",
            parameterizedInterfaceIdType,
            iidType,
            renderWinRtTypeSignature(valueType, typesByName),
        )

    private fun renderCollectionParameterProjection(
        method: WinRtMethodDefinition,
        rawArg: CodeBlock,
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        val collectionTypeName = parameterType.qualifiedName ?: parameter.typeName.substringBefore('<')
        val projectionType = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable" -> winRtIterableProjectionType
            "Windows.Foundation.Collections.IVectorView" -> winRtReadOnlyListProjectionType
            "Windows.Foundation.Collections.IVector" -> winRtListProjectionType
            "Windows.Foundation.Collections.IMapView" -> winRtReadOnlyDictionaryProjectionType
            "Windows.Foundation.Collections.IMap" -> winRtDictionaryProjectionType
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
        parameter: WinRtParameterDefinition,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val parameterType = parameter.type.normalized()
        val parameterTypeName = parameterType.qualifiedName ?: return null
        val projectedType = when (parameterTypeName) {
            "Windows.Foundation.IAsyncAction" -> winRtAsyncActionReferenceType
            "Windows.Foundation.IAsyncActionWithProgress" -> {
                val progressType = parameterType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' async type '${parameter.typeName}' does not have exactly one progress type.",
                    )
                winRtAsyncActionWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(progressType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperation" -> {
                val resultType = parameterType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' async type '${parameter.typeName}' does not have exactly one result type.",
                    )
                winRtAsyncOperationReferenceType.parameterizedBy(
                    authoringProjectedTypeName(resultType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperationWithProgress" -> {
                if (parameterType.typeArguments.size != 2) {
                    throw IllegalArgumentException(
                        "Authored WinRT parameter '${parameter.name}' async type '${parameter.typeName}' does not have exactly two argument types.",
                    )
                }
                winRtAsyncOperationWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(parameterType.typeArguments[0].normalized(), typesByName, semanticHelpers),
                    authoringProjectedTypeName(parameterType.typeArguments[1].normalized(), typesByName, semanticHelpers),
                )
            }
            else -> return null
        }
        return CodeBlock.of(
            "%T.fromAbi(%L as %T) as %T",
            winRtObjectMarshallerType,
            rawArg,
            rawAddressType,
            projectedType,
        )
    }

    private fun renderArrayReturnProjection(
        method: WinRtMethodDefinition,
        parameter: WinRtParameterDefinition,
        lengthOutExpression: CodeBlock,
        dataOutExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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

    private fun arrayElementLayout(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): Pair<CodeBlock, CodeBlock>? =
        fundamentalType(type.typeName)?.let { fundamental ->
            when (fundamental) {
                WinRtFundamentalType.Boolean,
                WinRtFundamentalType.Int8,
                WinRtFundamentalType.UInt8 -> CodeBlock.of("1") to CodeBlock.of("1")
                WinRtFundamentalType.Char,
                WinRtFundamentalType.Int16,
                WinRtFundamentalType.UInt16 -> CodeBlock.of("2") to CodeBlock.of("2")
                WinRtFundamentalType.Int32,
                WinRtFundamentalType.UInt32,
                WinRtFundamentalType.Float -> CodeBlock.of("4") to CodeBlock.of("4")
                WinRtFundamentalType.Int64,
                WinRtFundamentalType.UInt64,
                WinRtFundamentalType.Double,
                WinRtFundamentalType.String -> CodeBlock.of("8") to CodeBlock.of("8")
            }
        } ?: when (typesByName[type.qualifiedName]?.kind) {
            WinRtTypeKind.Interface,
            WinRtTypeKind.RuntimeClass,
            -> CodeBlock.of("8") to CodeBlock.of("8")
            WinRtTypeKind.Struct -> {
                val elementTypeName = type.qualifiedName ?: return null
                val projectionType = projectionClassName(elementTypeName, semanticHelpers)
                CodeBlock.of("%T.Metadata.layout.sizeBytes", projectionType) to
                    CodeBlock.of("%T.Metadata.layout.alignmentBytes", projectionType)
            }
            else -> null
        }

    private fun renderArrayElementWrite(
        method: WinRtMethodDefinition,
        type: WinRtTypeRef,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
        valueExpression: CodeBlock,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? =
        when (fundamentalType(type.typeName)) {
            WinRtFundamentalType.Boolean -> CodeBlock.of(
                "%T.writeInt8(%T.slice(%L, %L.toLong(), 1), if (%L) 1.toByte() else 0.toByte())",
                platformAbiType,
                platformAbiType,
                dataExpression,
                indexExpression,
                valueExpression,
            )
            WinRtFundamentalType.Int8 -> CodeBlock.of("%T.writeInt8(%T.slice(%L, %L.toLong(), 1), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.writeInt8(%T.slice(%L, %L.toLong(), 1), %L.toByte())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.Char -> CodeBlock.of("%T.writeInt16(%T.slice(%L, %L.toLong() * 2, 2), %L.code.toShort())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.Int16 -> CodeBlock.of("%T.writeInt16(%T.slice(%L, %L.toLong() * 2, 2), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.writeInt16(%T.slice(%L, %L.toLong() * 2, 2), %L.toShort())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.Int32 -> CodeBlock.of("%T.writeInt32(%T.slice(%L, %L.toLong() * 4, 4), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.writeInt32(%T.slice(%L, %L.toLong() * 4, 4), %L.toInt())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.Float -> CodeBlock.of("%T.writeFloat(%T.slice(%L, %L.toLong() * 4, 4), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.Int64 -> CodeBlock.of("%T.writeInt64(%T.slice(%L, %L.toLong() * 8, 8), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.writeInt64(%T.slice(%L, %L.toLong() * 8, 8), %L.toLong())", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.Double -> CodeBlock.of("%T.writeDouble(%T.slice(%L, %L.toLong() * 8, 8), %L)", platformAbiType, platformAbiType, dataExpression, indexExpression, valueExpression)
            WinRtFundamentalType.String -> CodeBlock.of(
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
                    WinRtTypeKind.Interface,
                    WinRtTypeKind.RuntimeClass,
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
                    WinRtTypeKind.Struct -> {
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
        type: WinRtTypeRef,
        dataExpression: CodeBlock,
        indexExpression: CodeBlock,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
        authoredRuntimeClassNames: Set<String>,
    ): CodeBlock? =
        when (fundamentalType(type.typeName)) {
            WinRtFundamentalType.Boolean -> CodeBlock.of(
                "%T.readInt8(%T.slice(%L, %L.toLong(), 1)).toInt() != 0",
                platformAbiType,
                platformAbiType,
                dataExpression,
                indexExpression,
            )
            WinRtFundamentalType.Int8 -> CodeBlock.of("%T.readInt8(%T.slice(%L, %L.toLong(), 1))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.readInt8(%T.slice(%L, %L.toLong(), 1)).toUByte()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.Char -> CodeBlock.of("%T.readInt16(%T.slice(%L, %L.toLong() * 2, 2)).toInt().toChar()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.Int16 -> CodeBlock.of("%T.readInt16(%T.slice(%L, %L.toLong() * 2, 2))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.readInt16(%T.slice(%L, %L.toLong() * 2, 2)).toUShort()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.Int32 -> CodeBlock.of("%T.readInt32(%T.slice(%L, %L.toLong() * 4, 4))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.readInt32(%T.slice(%L, %L.toLong() * 4, 4)).toUInt()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.Float -> CodeBlock.of("%T.readFloat(%T.slice(%L, %L.toLong() * 4, 4))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.Int64 -> CodeBlock.of("%T.readInt64(%T.slice(%L, %L.toLong() * 8, 8))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.readInt64(%T.slice(%L, %L.toLong() * 8, 8)).toULong()", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.Double -> CodeBlock.of("%T.readDouble(%T.slice(%L, %L.toLong() * 8, 8))", platformAbiType, platformAbiType, dataExpression, indexExpression)
            WinRtFundamentalType.String -> CodeBlock.of(
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
                    WinRtTypeKind.RuntimeClass,
                    WinRtTypeKind.Interface,
                    -> {
                        if (elementTypeName in authoredRuntimeClassNames) {
                            CodeBlock.of(
                                "%T.fromAbi(%L) as %T",
                                winRtObjectMarshallerType,
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
                    WinRtTypeKind.Struct -> {
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

    private fun isWinRtStringTypeName(typeName: String): Boolean =
        fundamentalType(typeName) == WinRtFundamentalType.String

    private fun fundamentalType(typeName: String): WinRtFundamentalType? =
        winRtFundamentalTypeForName(typeName)

    private fun renderCollectionReturnProjection(
        method: WinRtMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val returnTypeName = method.returnTypeName
        val returnType = WinRtTypeRef.fromDisplayName(returnTypeName).normalized()
        val collectionTypeName = returnType.qualifiedName ?: return null
        val projectionType = when (collectionTypeName) {
            "Windows.Foundation.Collections.IIterable" -> winRtIterableProjectionType
            "Windows.Foundation.Collections.IVectorView" -> winRtReadOnlyListProjectionType
            "Windows.Foundation.Collections.IVector" -> winRtListProjectionType
            "Windows.Foundation.Collections.IMapView" -> winRtReadOnlyDictionaryProjectionType
            "Windows.Foundation.Collections.IMap" -> winRtDictionaryProjectionType
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
        method: WinRtMethodDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val returnType = WinRtTypeRef.fromDisplayName(method.returnTypeName).normalized()
        val returnTypeName = returnType.qualifiedName ?: return null
        val interfaceId = when (returnTypeName) {
            "Windows.Foundation.IAsyncAction" -> CodeBlock.of("%T.IAsyncAction", winRtAsyncInterfaceIdsType)
            "Windows.Foundation.IAsyncActionWithProgress" -> {
                val progressType = returnType.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns async type '${method.returnTypeName}' without exactly one progress type.",
                    )
                CodeBlock.of(
                    "%T.createFromParameterizedInterface(%T.IAsyncActionWithProgressGeneric, %L)",
                    parameterizedInterfaceIdType,
                    winRtAsyncInterfaceIdsType,
                    renderWinRtTypeSignature(progressType, typesByName),
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
                    winRtAsyncInterfaceIdsType,
                    renderWinRtTypeSignature(resultType, typesByName),
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
                    winRtAsyncInterfaceIdsType,
                    renderWinRtTypeSignature(returnType.typeArguments[0].normalized(), typesByName),
                    renderWinRtTypeSignature(returnType.typeArguments[1].normalized(), typesByName),
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

    private fun WinRtTypeRef.displayName(): String =
        qualifiedName ?: toString()

    private fun renderCollectionElementAdapter(
        method: WinRtMethodDefinition,
        elementType: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock {
        val elementTypeName = elementType.qualifiedName
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns collection element '${elementType.displayName()}' without a qualified type name.",
            )
        renderNestedCollectionElementAdapter(method, elementType, typesByName, semanticHelpers)?.let { return it }
        if (isWinRtObjectTypeName(elementTypeName)) {
            return CodeBlock.of("%T.object_", winRtReferenceValueAdaptersType)
        }
        if (isWinRtStringTypeName(elementTypeName)) {
            return CodeBlock.of("%T.string", winRtReferenceValueAdaptersType)
        }
        val elementDefinition = typesByName[elementTypeName]
            ?: throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns collection element type '$elementTypeName' without metadata.",
            )
        return when (elementDefinition.kind) {
            WinRtTypeKind.RuntimeClass -> CodeBlock.of(
                "%T.runtimeClass(%T::class, %S, %T.Metadata.DEFAULT_INTERFACE_IID) { %T.Metadata.wrap(it) }",
                winRtReferenceValueAdaptersType,
                projectionClassName(elementTypeName, semanticHelpers),
                elementTypeName,
                projectionClassName(elementTypeName, semanticHelpers),
                projectionClassName(elementTypeName, semanticHelpers),
            )
            WinRtTypeKind.Interface -> {
                val iid = elementDefinition.iid
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns interface collection element '$elementTypeName' without IID metadata.",
                    )
                CodeBlock.of(
                    "%T<%T>(projectedTypeName = %S, typeSignature = %T.object_(), projector = { reference -> %T.Metadata.wrap(reference!!) }, marshaller = { value -> (value as %T).nativeObject.queryInterface(%T(%S)).getOrThrow() })",
                    winRtReferenceValueAdapterType,
                    projectionClassName(elementTypeName, semanticHelpers),
                    elementTypeName,
                    winRtTypeSignatureType,
                    projectionClassName(elementTypeName, semanticHelpers),
                    iWinRtObjectType,
                    guidType,
                    iid.toString().lowercase(),
                )
            }
            WinRtTypeKind.Struct -> {
                val projectedType = projectionClassName(elementTypeName, semanticHelpers)
                CodeBlock.of(
                    "%T.valueType(%T::class, %S, %L)",
                    winRtReferenceValueAdaptersType,
                    projectedType,
                    elementTypeName,
                    renderWinRtTypeSignature(elementType, typesByName),
                )
            }
            else -> throw IllegalArgumentException(
                "Authored WinRT override ${method.name} returns unsupported collection element type '$elementTypeName'.",
            )
        }
    }

    private fun renderNestedCollectionElementAdapter(
        method: WinRtMethodDefinition,
        elementType: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): CodeBlock? {
        val elementTypeName = elementType.qualifiedName ?: return null
        val descriptor = when (elementTypeName) {
            "Windows.Foundation.Collections.IIterable" -> NestedCollectionProjectionDescriptor(
                projectedType = Iterable::class.asClassName(),
                projectionType = winRtIterableProjectionType,
            )
            "Windows.Foundation.Collections.IVectorView" -> NestedCollectionProjectionDescriptor(
                projectedType = List::class.asClassName(),
                projectionType = winRtReadOnlyListProjectionType,
            )
            "Windows.Foundation.Collections.IVector" -> NestedCollectionProjectionDescriptor(
                projectedType = MutableList::class.asClassName(),
                projectionType = winRtListProjectionType,
                typeArgumentCount = 1,
                emptyValueExpression = "mutableListOf()",
            )
            "Windows.Foundation.Collections.IMapView" -> NestedCollectionProjectionDescriptor(
                projectedType = Map::class.asClassName(),
                projectionType = winRtReadOnlyDictionaryProjectionType,
                typeArgumentCount = 2,
                emptyValueExpression = "emptyMap()",
            )
            "Windows.Foundation.Collections.IMap" -> NestedCollectionProjectionDescriptor(
                projectedType = MutableMap::class.asClassName(),
                projectionType = winRtDictionaryProjectionType,
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
        val typeSignature = renderWinRtTypeSignature(elementType, typesByName)
        return CodeBlock.of(
            "%T<%T>(projectedTypeName = %S, typeSignature = %L, projector = { reference -> if (reference == null) %L else %T.fromAbi(%T.fromRawComPtr(reference.pointer)%L) ?: %L }, marshaller = { value -> %T(%T.toRawComPtr(%T.fromManaged(value%L)), %T.createFromSignature(%L)) })",
            winRtReferenceValueAdapterType,
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
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): TypeName {
        val typeName = type.qualifiedName
            ?: throw IllegalArgumentException("Authored WinRT collection element '${type.displayName()}' has no projected type name.")
        renderAsyncProjectedType(type, typesByName, semanticHelpers)?.let { return it }
        renderNestedCollectionProjectedType(type, typesByName, semanticHelpers)?.let { return it }
        if (isWinRtObjectTypeName(typeName)) {
            return ANY.copy(nullable = true)
        }
        if (isWinRtStringTypeName(typeName)) {
            return String::class.asClassName()
        }
        fundamentalProjectedTypeName(typeName)?.let { return it }
        val definition = typesByName[typeName]
            ?: throw IllegalArgumentException("Authored WinRT collection element type '$typeName' has no metadata.")
        return when (definition.kind) {
            WinRtTypeKind.RuntimeClass -> projectionClassName(typeName, semanticHelpers)
            WinRtTypeKind.Enum -> projectionClassName(typeName, semanticHelpers)
            WinRtTypeKind.Struct -> projectionClassName(typeName, semanticHelpers)
            else -> throw IllegalArgumentException("Authored WinRT collection element type '$typeName' is not projectable.")
        }
    }

    private fun fundamentalProjectedTypeName(typeName: String): ClassName? =
        when (fundamentalType(typeName)) {
            WinRtFundamentalType.Boolean -> Boolean::class.asClassName()
            WinRtFundamentalType.Char -> Char::class.asClassName()
            WinRtFundamentalType.Int8 -> Byte::class.asClassName()
            WinRtFundamentalType.UInt8 -> UByte::class.asClassName()
            WinRtFundamentalType.Int16 -> Short::class.asClassName()
            WinRtFundamentalType.UInt16 -> UShort::class.asClassName()
            WinRtFundamentalType.Int32 -> Int::class.asClassName()
            WinRtFundamentalType.UInt32 -> UInt::class.asClassName()
            WinRtFundamentalType.Int64 -> Long::class.asClassName()
            WinRtFundamentalType.UInt64 -> ULong::class.asClassName()
            WinRtFundamentalType.Float -> Float::class.asClassName()
            WinRtFundamentalType.Double -> Double::class.asClassName()
            WinRtFundamentalType.String,
            null -> null
        }

    private fun renderAsyncProjectedType(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): TypeName? {
        val typeName = type.qualifiedName ?: return null
        return when (typeName) {
            "Windows.Foundation.IAsyncAction" -> winRtAsyncActionReferenceType
            "Windows.Foundation.IAsyncActionWithProgress" -> {
                val progressType = type.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException("Authored WinRT async type '${type.typeName}' does not have exactly one progress type.")
                winRtAsyncActionWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(progressType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperation" -> {
                val resultType = type.typeArguments.singleOrNull()?.normalized()
                    ?: throw IllegalArgumentException("Authored WinRT async type '${type.typeName}' does not have exactly one result type.")
                winRtAsyncOperationReferenceType.parameterizedBy(
                    authoringProjectedTypeName(resultType, typesByName, semanticHelpers),
                )
            }
            "Windows.Foundation.IAsyncOperationWithProgress" -> {
                if (type.typeArguments.size != 2) {
                    throw IllegalArgumentException("Authored WinRT async type '${type.typeName}' does not have exactly two argument types.")
                }
                winRtAsyncOperationWithProgressReferenceType.parameterizedBy(
                    authoringProjectedTypeName(type.typeArguments[0].normalized(), typesByName, semanticHelpers),
                    authoringProjectedTypeName(type.typeArguments[1].normalized(), typesByName, semanticHelpers),
                )
            }
            else -> null
        }
    }

    private fun renderNestedCollectionProjectedType(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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

    private fun renderWinRtTypeSignature(
        type: WinRtTypeRef,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock {
        val typeName = type.qualifiedName
            ?: throw IllegalArgumentException("Authored WinRT collection element '${type.displayName()}' has no type signature name.")
        when (typeName) {
            "Windows.Foundation.Collections.IIterable" -> {
                val elementSignature = renderWinRtTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.iterableSignature(%L)", winRtCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IVectorView" -> {
                val elementSignature = renderWinRtTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.vectorViewSignature(%L)", winRtCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IVector" -> {
                val elementSignature = renderWinRtTypeSignature(type.typeArguments.singleOrNull()?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.vectorSignature(%L)", winRtCollectionInterfaceIdsType, elementSignature)
            }
            "Windows.Foundation.Collections.IMapView" -> {
                val keySignature = renderWinRtTypeSignature(type.typeArguments.getOrNull(0)?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                val valueSignature = renderWinRtTypeSignature(type.typeArguments.getOrNull(1)?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.mapViewSignature(%L, %L)", winRtCollectionInterfaceIdsType, keySignature, valueSignature)
            }
            "Windows.Foundation.Collections.IMap" -> {
                val keySignature = renderWinRtTypeSignature(type.typeArguments.getOrNull(0)?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                val valueSignature = renderWinRtTypeSignature(type.typeArguments.getOrNull(1)?.normalized() ?: WinRtTypeRef.unknown(), typesByName)
                return CodeBlock.of("%T.mapSignature(%L, %L)", winRtCollectionInterfaceIdsType, keySignature, valueSignature)
            }
        }
        if (isWinRtObjectTypeName(typeName)) {
            return CodeBlock.of("%T.object_()", winRtTypeSignatureType)
        }
        if (isWinRtStringTypeName(typeName)) {
            return CodeBlock.of("%T.string()", winRtTypeSignatureType)
        }
        fundamentalType(typeName)?.let { type ->
            return renderFundamentalTypeSignature(type)
        }
        val definition = typesByName[typeName]
            ?: throw IllegalArgumentException("Authored WinRT collection element type '$typeName' has no metadata signature.")
        return when (definition.kind) {
            WinRtTypeKind.RuntimeClass -> CodeBlock.of("%T.object_()", winRtTypeSignatureType)
            WinRtTypeKind.Enum -> CodeBlock.of(
                "%T.enum(%S, %L)",
                winRtTypeSignatureType,
                typeName,
                renderFundamentalTypeSignature(integralFundamentalType(enumIntegralAbiDescriptor(definition).integralType)),
            )
            WinRtTypeKind.Struct -> CodeBlock.of(
                "%T.struct(%S%L)",
                winRtTypeSignatureType,
                typeName,
                definition.fields.joinToString(separator = "") { field ->
                    ", ${renderWinRtTypeSignature(WinRtTypeRef.fromDisplayName(field.typeName).normalized(), typesByName)}"
                },
            )
            else -> throw IllegalArgumentException("Authored WinRT collection element type '$typeName' has no supported type signature.")
        }
    }

    private fun renderFundamentalTypeSignature(type: WinRtFundamentalType): CodeBlock =
        when (type) {
            WinRtFundamentalType.Boolean -> CodeBlock.of("%T.boolean()", winRtTypeSignatureType)
            WinRtFundamentalType.Char -> CodeBlock.of("%T.char16()", winRtTypeSignatureType)
            WinRtFundamentalType.Int8 -> CodeBlock.of("%T.int8()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt8 -> CodeBlock.of("%T.uint8()", winRtTypeSignatureType)
            WinRtFundamentalType.Int16 -> CodeBlock.of("%T.int16()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt16 -> CodeBlock.of("%T.uint16()", winRtTypeSignatureType)
            WinRtFundamentalType.Int32 -> CodeBlock.of("%T.int32()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt32 -> CodeBlock.of("%T.uint32()", winRtTypeSignatureType)
            WinRtFundamentalType.Int64 -> CodeBlock.of("%T.int64()", winRtTypeSignatureType)
            WinRtFundamentalType.UInt64 -> CodeBlock.of("%T.uint64()", winRtTypeSignatureType)
            WinRtFundamentalType.Float -> CodeBlock.of("%T.float32()", winRtTypeSignatureType)
            WinRtFundamentalType.Double -> CodeBlock.of("%T.float64()", winRtTypeSignatureType)
            WinRtFundamentalType.String -> CodeBlock.of("%T.string()", winRtTypeSignatureType)
        }

    private fun renderObjectReturnProjection(
        method: WinRtMethodDefinition,
        returnType: WinRtTypeDefinition?,
        outExpression: CodeBlock,
        valueExpression: String,
        typesByName: Map<String, WinRtTypeDefinition>,
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
        method: WinRtMethodDefinition,
        typeName: String,
        type: WinRtTypeDefinition?,
        typesByName: Map<String, WinRtTypeDefinition>,
    ): CodeBlock =
        when (type?.kind) {
            WinRtTypeKind.RuntimeClass -> {
                val defaultInterfaceName = type.defaultInterfaceName
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns runtime class '$typeName' without default interface metadata.",
                    )
                val defaultInterface = typesByName[defaultInterfaceName]
                    ?: typesByName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns runtime class '$typeName' whose default interface '$defaultInterfaceName' is missing.",
                    )
                val iid = defaultInterface.iid
                    ?: throw IllegalArgumentException(
                        "Authored WinRT override ${method.name} returns runtime class '$typeName' whose default interface '$defaultInterfaceName' has no IID.",
                    )
                CodeBlock.of("%T(%S)", guidType, iid.toString().lowercase())
            }
            WinRtTypeKind.Interface -> {
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

    private fun renderEnumReturnProjection(
        typeName: String,
        type: WinRtTypeDefinition,
        outExpression: CodeBlock,
        valueExpression: String,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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

    private fun isVoidReturn(method: WinRtMethodDefinition): Boolean =
        isWinRtVoidTypeName(method.returnTypeName)

    private fun authoringInvokeBridgeName(method: WinRtMethodDefinition): String =
        "__winrtAuthoringInvoke${method.name}"

    private fun projectedMethodName(method: WinRtMethodDefinition): String =
        method.name.replaceFirstChar(Char::lowercase)

    private fun projectedPropertyName(property: WinRtPropertyDefinition): String =
        property.name.replaceFirstChar(Char::lowercase)

    private fun detailsObjectName(candidate: KotlinWinRtAuthoredTypeCandidate): String =
        "WinRT_${candidate.className.replace('$', '_')}_TypeDetails"

    private fun generatedAuthoringTypeDetailsSuppressAnnotation(): AnnotationSpec =
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "KOTLIN_WINRT_GENERATED")
            .addMember("%S", "USELESS_CAST")
            .addMember("%S", "UNCHECKED_CAST")
            .build()

    private fun enumIntegralAbiDescriptor(type: WinRtTypeDefinition): AuthoringEnumIntegralAbiDescriptor =
        enumIntegralAbiDescriptors.getValue(type.enumUnderlyingType ?: WinRtIntegralType.Int32)

    private data class AuthoringEnumIntegralAbiDescriptor(
        val carrierTypeName: ClassName,
        val integralType: WinRtIntegralType,
        val abiKindName: String,
        val rawCarrierConversionSuffix: String = "",
        val writeFunctionName: String,
        val abiWriteConversionSuffix: String = "",
    )

    private fun integralFundamentalType(type: WinRtIntegralType): WinRtFundamentalType =
        when (type) {
            WinRtIntegralType.Int8 -> WinRtFundamentalType.Int8
            WinRtIntegralType.UInt8 -> WinRtFundamentalType.UInt8
            WinRtIntegralType.Int16 -> WinRtFundamentalType.Int16
            WinRtIntegralType.UInt16 -> WinRtFundamentalType.UInt16
            WinRtIntegralType.Int32 -> WinRtFundamentalType.Int32
            WinRtIntegralType.UInt32 -> WinRtFundamentalType.UInt32
            WinRtIntegralType.Int64 -> WinRtFundamentalType.Int64
            WinRtIntegralType.UInt64 -> WinRtFundamentalType.UInt64
        }

    private data class NestedCollectionProjectionDescriptor(
        val projectedType: ClassName,
        val projectionType: ClassName,
        val typeArgumentCount: Int = 1,
        val emptyValueExpression: String = "emptyList()",
    )

    private data class AuthoringDispatchTarget(
        val className: ClassName,
        val methodName: (WinRtMethodDefinition) -> String,
    )

    private fun sourceClassName(candidate: KotlinWinRtAuthoredTypeCandidate): ClassName {
        val names = candidate.className.split('$').filter(String::isNotBlank)
        return ClassName(candidate.packageName, names.first(), *names.drop(1).toTypedArray())
    }

    private fun projectionClassName(
        qualifiedName: String,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): ClassName {
        runtimeMappedClassName(qualifiedName, semanticHelpers)?.let { return it }
        return classNameFromWinRtName(qualifiedName)
    }

    private fun runtimeMappedClassName(
        qualifiedName: String,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): ClassName? =
        semanticHelpers.getMappedType(WinRtTypeRef.fromDisplayName(qualifiedName), "")
            ?.mappedQualifiedName
            ?.takeIf { mappedName -> mappedName.startsWith("io.github.composefluent.winrt.runtime.") }
            ?.let(::classNameFromQualifiedName)

    private fun classNameFromWinRtName(qualifiedName: String): ClassName {
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
