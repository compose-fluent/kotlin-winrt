package io.github.composefluent.winrt.metadata

enum class WinRtMetadataDiagnosticSeverity {
    Error,
    Warning,
}

enum class WinRtMetadataDiagnosticCode {
    InvalidCommandSpecification,
    InvalidMetadataSource,
    MissingReferencedMetadata,
    UnresolvedTypeReference,
    UnsupportedSignatureType,
    UnsupportedSemanticShape,
    UnsupportedGenericMethodShape,
    MissingInterfaceIid,
    MissingRuntimeClassDefaultInterface,
    MissingRuntimeClassStaticInterface,
    MissingActivationFactoryMetadata,
    InvalidPropertyAccessors,
    InvalidEventAccessors,
    UnknownCustomAttributeBlob,
    UnsupportedTypeKind,
    IntentionalKotlinGap,
}

data class WinRtMetadataDiagnostic(
    val code: WinRtMetadataDiagnosticCode,
    val severity: WinRtMetadataDiagnosticSeverity,
    val message: String,
    val typeName: String? = null,
    val memberName: String? = null,
    val sourceName: String? = null,
    val rowId: Int? = null,
) {
    val isError: Boolean
        get() = severity == WinRtMetadataDiagnosticSeverity.Error
}

data class WinRtMetadataDiagnosticReport(
    val diagnostics: List<WinRtMetadataDiagnostic>,
) {
    val errors: List<WinRtMetadataDiagnostic>
        get() = diagnostics.filter(WinRtMetadataDiagnostic::isError)

    val warnings: List<WinRtMetadataDiagnostic>
        get() = diagnostics.filterNot(WinRtMetadataDiagnostic::isError)

    val hasErrors: Boolean
        get() = errors.isNotEmpty()

    fun throwIfErrors() {
        if (hasErrors) {
            throw WinRtMetadataDiagnosticException(this)
        }
    }

    fun format(): String =
        diagnostics.joinToString(separator = System.lineSeparator()) { diagnostic ->
            val location = listOfNotNull(diagnostic.typeName, diagnostic.memberName).joinToString(".")
            val source = buildList {
                diagnostic.sourceName?.takeIf(String::isNotBlank)?.let { add(it) }
                diagnostic.rowId?.let { add("row $it") }
            }.joinToString(" ")
            val fullLocation = listOf(location.takeIf(String::isNotBlank), source.takeIf(String::isNotBlank))
                .filterNotNull()
                .joinToString(" ")
            val prefix = if (fullLocation.isBlank()) "" else "$fullLocation: "
            "${diagnostic.severity} ${diagnostic.code}: $prefix${diagnostic.message}"
        }
}

class WinRtMetadataDiagnosticException(
    val report: WinRtMetadataDiagnosticReport,
) : IllegalArgumentException(report.format())

data class WinRtMetadataValidationOptions(
    val validateTypeReferences: Boolean = true,
    val validateActivationFactoryReferences: Boolean = true,
    val validateCustomAttributeTypeReferences: Boolean = false,
    val validateRuntimeClassDefaultInterface: Boolean = true,
    val kotlinSpecificGaps: List<String> = emptyList(),
)

class WinRtMetadataValidator private constructor(
    private val model: WinRtMetadataModel,
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    private val typeClassifier: WinRtMetadataTypeClassifier,
    private val typeSemanticsResolver: WinRtTypeSemanticsResolver,
    private val options: WinRtMetadataValidationOptions,
) {
    fun validateForProjection(): WinRtMetadataDiagnosticReport {
        val diagnostics = buildList {
            model.namespaces
                .flatMap(WinRtNamespace::types)
                .forEach { type -> validateType(type, this) }
            options.kotlinSpecificGaps.map(String::trim).filter(String::isNotEmpty).forEach { gap ->
                add(
                    WinRtMetadataDiagnostic(
                        code = WinRtMetadataDiagnosticCode.IntentionalKotlinGap,
                        severity = WinRtMetadataDiagnosticSeverity.Warning,
                        message = gap,
                    ),
                )
            }
        }
        return WinRtMetadataDiagnosticReport(diagnostics)
    }

    private fun validateType(
        type: WinRtTypeDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        val typeName = type.qualifiedName
        if (type.kind == WinRtTypeKind.Unknown && type.hasProjectedSurface) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.UnsupportedTypeKind,
                typeName = typeName,
                message = "Type has projected members but no resolved WinRT type category.",
            )
        }

        validateTypeReference(type.baseType, type.namespace, typeName, "base type", diagnostics)
        validateTypeReference(type.defaultInterface, type.namespace, typeName, "default interface", diagnostics)
        type.implementedInterfaces.forEach { implementation ->
            validateTypeReference(implementation.interfaceType, type.namespace, typeName, "implemented interface ${implementation.interfaceName}", diagnostics)
        }
        type.genericParameters.forEach { parameter ->
            parameter.constraintTypes.forEach { constraint ->
                validateTypeReference(constraint, type.namespace, typeName, "generic parameter ${parameter.name} constraint", diagnostics)
            }
        }
        type.fields.forEach { field ->
            validateSignatureType(field.type, type.namespace, typeName, field.name, "field type", diagnostics, field.rowId)
        }
        type.customAttributes.forEach { attribute ->
            validateCustomAttribute(attribute, type.namespace, typeName, null, diagnostics)
        }

        when (type.kind) {
            WinRtTypeKind.Interface -> validateInterface(type, diagnostics)
            WinRtTypeKind.RuntimeClass -> validateRuntimeClass(type, diagnostics)
            WinRtTypeKind.Delegate -> validateDelegate(type, diagnostics)
            WinRtTypeKind.Struct -> validateStruct(type, diagnostics)
            WinRtTypeKind.Enum,
            WinRtTypeKind.Unknown,
            -> Unit
        }

        type.methods.forEach { method -> validateMethod(type, method, diagnostics) }
        type.properties.forEach { property -> validateProperty(type, property, diagnostics) }
        type.events.forEach { event -> validateEvent(type, event, diagnostics) }
    }

    private fun validateInterface(
        type: WinRtTypeDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        if (type.hasProjectedSurface && type.iid == null) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.MissingInterfaceIid,
                typeName = type.qualifiedName,
                message = "Generator requires interface ${type.qualifiedName} to carry metadata IID before projection planning.",
            )
        }
    }

    private fun validateRuntimeClass(
        type: WinRtTypeDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        val instanceSurface = type.methods.any { !it.isStatic } ||
            type.properties.any { !it.isStatic } ||
            type.events.any { !it.isStatic }
        if (options.validateRuntimeClassDefaultInterface && instanceSurface && type.defaultInterfaceName == null) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.MissingRuntimeClassDefaultInterface,
                typeName = type.qualifiedName,
                message = "Runtime class has instance members but no default interface metadata.",
            )
        }

        val staticSurface = type.methods.any(WinRtMethodDefinition::isStatic) ||
            type.properties.any(WinRtPropertyDefinition::isStatic) ||
            type.events.any(WinRtEventDefinition::isStatic)
        if (staticSurface && type.activation.staticInterfaceNames.isEmpty()) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.MissingRuntimeClassStaticInterface,
                typeName = type.qualifiedName,
                message = "Generator requires runtime class ${type.qualifiedName} to carry static interface metadata before projection planning.",
            )
        }
        if (options.validateActivationFactoryReferences) {
            type.activation.activatableFactoryInterface?.let { factoryType ->
                validateTypeReference(factoryType, type.namespace, type.qualifiedName, "activatable factory", diagnostics)
            }
            type.activation.composableFactoryInterface?.let { factoryType ->
                validateTypeReference(factoryType, type.namespace, type.qualifiedName, "composable factory", diagnostics)
            }
            type.activation.staticInterfaces.forEach { staticType ->
                validateTypeReference(staticType, type.namespace, type.qualifiedName, "static interface", diagnostics)
            }
        }
        if (options.validateActivationFactoryReferences &&
            type.activation.isActivatable &&
            type.activation.activatableFactoryInterfaceName?.contains("Factory") == true &&
            type.activation.activatableFactoryInterface?.let { resolveDefinition(it, type.namespace) } == null
        ) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.MissingActivationFactoryMetadata,
                typeName = type.qualifiedName,
                message = "Runtime class references an activatable factory that is not present in the metadata model.",
            )
        }
    }

    private fun validateDelegate(
        type: WinRtTypeDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        type.methods.forEach { method ->
            method.parameters.forEach { parameter ->
                validateSignatureType(parameter.type, type.namespace, type.qualifiedName, method.name, "delegate parameter ${parameter.name}", diagnostics, method.methodRowId)
            }
        }
    }

    private fun validateStruct(
        type: WinRtTypeDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        type.fields
            .filterNot { it.isStatic || it.isLiteral }
            .forEach { field ->
                validateSignatureType(field.type, type.namespace, type.qualifiedName, field.name, "struct field ${field.name}", diagnostics, field.rowId)
            }
    }

    private fun validateMethod(
        type: WinRtTypeDefinition,
        method: WinRtMethodDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        validateSignatureType(method.returnType, type.namespace, type.qualifiedName, method.name, "method return type", diagnostics, method.methodRowId)
        method.parameters.forEach { parameter ->
            validateSignatureType(parameter.type, type.namespace, type.qualifiedName, method.name, "parameter ${parameter.name}", diagnostics, method.methodRowId)
        }
        method.returnParameterAttributes.forEach { attribute ->
            validateCustomAttribute(attribute, type.namespace, type.qualifiedName, method.name, diagnostics)
        }
    }

    private fun validateProperty(
        type: WinRtTypeDefinition,
        property: WinRtPropertyDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        if (!property.hasValidAccessors) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.InvalidPropertyAccessors,
                typeName = type.qualifiedName,
                memberName = property.name,
                rowId = property.rowId(),
                message = "Property semantics do not resolve to a valid getter/setter accessor pair.",
            )
        }
        validateSignatureType(property.type, type.namespace, type.qualifiedName, property.name, "property type", diagnostics, property.rowId())
    }

    private fun validateEvent(
        type: WinRtTypeDefinition,
        event: WinRtEventDefinition,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        if (!event.hasValidAccessors) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.InvalidEventAccessors,
                typeName = type.qualifiedName,
                memberName = event.name,
                rowId = event.rowId(),
                message = "Event semantics do not resolve to a valid add/remove accessor pair.",
            )
        }
        validateSignatureType(event.delegateType, type.namespace, type.qualifiedName, event.name, "event delegate type", diagnostics, event.rowId())
    }

    private fun validateSignatureType(
        type: WinRtTypeRef,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        role: String,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
        rowId: Int? = null,
    ) {
        val normalizedType = type.normalized()
        when (normalizedType.kind) {
            WinRtTypeRefKind.MethodTypeParameter -> Unit

            WinRtTypeRefKind.Unknown -> {
                diagnostics += error(
                    code = WinRtMetadataDiagnosticCode.UnsupportedSignatureType,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    rowId = rowId,
                    message = "$role has an unknown metadata signature type.",
                )
                diagnostics += error(
                    code = WinRtMetadataDiagnosticCode.UnsupportedSemanticShape,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    rowId = rowId,
                    message = "$role cannot be lowered through the reference projection type semantics kernel: unknown type.",
                )
            }

            WinRtTypeRefKind.Array -> {
                if (normalizedType.arrayRank != 1) {
                    diagnostics += error(
                        code = WinRtMetadataDiagnosticCode.UnsupportedSignatureType,
                        typeName = ownerTypeName,
                        memberName = memberName,
                        rowId = rowId,
                        message = "$role uses array rank ${normalizedType.arrayRank}; only SZARRAY-style rank-1 arrays are supported.",
                    )
                }
                validateSignatureType(
                    normalizedType.elementType ?: WinRtTypeRef.unknown(),
                    currentNamespace,
                    ownerTypeName,
                    memberName,
                    "$role element",
                    diagnostics,
                    rowId,
                )
            }

            WinRtTypeRefKind.Named -> {
                validateTypeReference(normalizedType, currentNamespace, ownerTypeName, role, diagnostics, rowId)
                validateNamedSemanticShape(normalizedType, currentNamespace, ownerTypeName, memberName, role, diagnostics, rowId)
                normalizedType.typeArguments.forEachIndexed { index, argument ->
                    validateSignatureType(argument, currentNamespace, ownerTypeName, memberName, "$role generic argument $index", diagnostics, rowId)
                }
            }

            WinRtTypeRefKind.GenericTypeParameter -> Unit
        }
    }

    private fun validateNamedSemanticShape(
        type: WinRtTypeRef,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        role: String,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
        rowId: Int? = null,
    ) {
        val classification = typeClassifier.classify(type, currentNamespace)
        if (classification.projectionCategory == WinRtProjectionCategory.Unit ||
            classification.isMappedType ||
            classification.specialType != null ||
            classification.projectionCategory == WinRtProjectionCategory.Unknown
        ) {
            return
        }
        runCatching { typeSemanticsResolver.resolve(type, currentNamespace) }
            .onFailure { failure ->
                diagnostics += error(
                    code = WinRtMetadataDiagnosticCode.UnsupportedSemanticShape,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    rowId = rowId,
                    message = "$role cannot be lowered through the reference projection type semantics kernel: ${failure.message}",
                )
            }
    }

    private fun validateTypeReference(
        type: WinRtTypeRef?,
        currentNamespace: String,
        ownerTypeName: String,
        role: String,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
        rowId: Int? = null,
    ) {
        val normalizedType = type?.normalized() ?: return
        if (normalizedType.kind != WinRtTypeRefKind.Named) {
            return
        }
        if (!options.validateTypeReferences) {
            return
        }
        val classification = typeClassifier.classify(normalizedType, currentNamespace)
        val isResolved = classification.definitionType != null ||
            classification.projectionCategory != WinRtProjectionCategory.Unknown ||
            classification.isMappedType ||
            classification.specialType != null
        if (!isResolved) {
            diagnostics += error(
                code = WinRtMetadataDiagnosticCode.UnresolvedTypeReference,
                typeName = ownerTypeName,
                rowId = rowId,
                message = "$role references unresolved type ${classification.typeName}.",
            )
        }
    }

    private fun validateCustomAttribute(
        attribute: WinRtCustomAttributeDefinition,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        if (options.validateCustomAttributeTypeReferences) {
            validateTypeReference(WinRtTypeRef.fromDisplayName(attribute.typeName), currentNamespace, ownerTypeName, "custom attribute", diagnostics)
        }
        attribute.fixedArguments.forEach { value ->
            validateCustomAttributeValue(value, currentNamespace, ownerTypeName, memberName, diagnostics)
        }
        attribute.namedArguments.forEach { argument ->
            validateCustomAttributeValue(argument.value, currentNamespace, ownerTypeName, memberName, diagnostics)
        }
    }

    private fun validateCustomAttributeValue(
        value: WinRtCustomAttributeValue,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        diagnostics: MutableList<WinRtMetadataDiagnostic>,
    ) {
        when (value) {
            is WinRtCustomAttributeValue.TypeValue ->
                if (options.validateCustomAttributeTypeReferences) {
                    value.typeName
                        ?.let(WinRtTypeRef::fromDisplayName)
                        ?.let { validateTypeReference(it, currentNamespace, ownerTypeName, "custom attribute System.Type argument", diagnostics) }
                }

            is WinRtCustomAttributeValue.EnumValue ->
                if (options.validateCustomAttributeTypeReferences) {
                    validateTypeReference(WinRtTypeRef.fromDisplayName(value.enumTypeName), currentNamespace, ownerTypeName, "custom attribute enum argument", diagnostics)
                }

            is WinRtCustomAttributeValue.ArrayValue ->
                value.values.forEach { nested ->
                    validateCustomAttributeValue(nested, currentNamespace, ownerTypeName, memberName, diagnostics)
                }

            WinRtCustomAttributeValue.NullValue ->
                diagnostics += error(
                    code = WinRtMetadataDiagnosticCode.UnknownCustomAttributeBlob,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    message = "Custom attribute blob contains a null value that cannot be used as generator input.",
                )

            is WinRtCustomAttributeValue.BooleanValue,
            is WinRtCustomAttributeValue.FloatingPointValue,
            is WinRtCustomAttributeValue.IntegralValue,
            is WinRtCustomAttributeValue.StringValue,
            -> Unit
        }
    }

    private fun resolveDefinition(type: WinRtTypeRef, currentNamespace: String): WinRtTypeDefinition? {
        val normalizedType = type.normalized()
        val qualifiedName = normalizedType.qualifiedName ?: return null
        return qualifyTypeName(qualifiedName, currentNamespace, typesByQualifiedName)?.let(typesByQualifiedName::get)
    }

    private fun error(
        code: WinRtMetadataDiagnosticCode,
        typeName: String,
        message: String,
        memberName: String? = null,
        rowId: Int? = null,
    ): WinRtMetadataDiagnostic =
        WinRtMetadataDiagnostic(
            code = code,
            severity = WinRtMetadataDiagnosticSeverity.Error,
            message = message,
            typeName = typeName,
            memberName = memberName,
            sourceName = "metadata",
            rowId = rowId,
        )

    companion object {
        fun create(
            model: WinRtMetadataModel,
            options: WinRtMetadataValidationOptions = WinRtMetadataValidationOptions(),
        ): WinRtMetadataValidator {
            val normalized = model.normalized()
            return WinRtMetadataValidator(
                model = normalized,
                typesByQualifiedName = buildTypesByQualifiedName(normalized),
                typeClassifier = normalized.typeClassifier(),
                typeSemanticsResolver = normalized.typeSemanticsResolver(),
                options = options,
            )
        }
    }
}

fun WinRtMetadataModel.validateForProjection(
    options: WinRtMetadataValidationOptions = WinRtMetadataValidationOptions(),
): WinRtMetadataDiagnosticReport =
    WinRtMetadataValidator.create(this, options).validateForProjection()

fun WinRtMetadataModel.requireValidForProjection(
    options: WinRtMetadataValidationOptions = WinRtMetadataValidationOptions(),
): WinRtMetadataModel {
    val normalized = normalized()
    normalized.validateForProjection(options).throwIfErrors()
    return normalized
}

fun WinRtMetadataProjectionContext.validateForProjectionInputs(
    options: WinRtMetadataValidationOptions = WinRtMetadataValidationOptions(),
): WinRtMetadataDiagnosticReport =
    try {
        load().validateForProjection(options)
    } catch (error: IllegalArgumentException) {
        WinRtMetadataDiagnosticReport(
            listOf(
                WinRtMetadataDiagnostic(
                    code = diagnosticCodeForInputFailure(error.message.orEmpty()),
                    severity = WinRtMetadataDiagnosticSeverity.Error,
                    message = error.message ?: "Invalid metadata projection input.",
                ),
            ),
        )
    }

private fun diagnosticCodeForInputFailure(message: String): WinRtMetadataDiagnosticCode =
    when {
        "missing WinMD" in message || "referenced metadata" in message ->
            WinRtMetadataDiagnosticCode.MissingReferencedMetadata
        "Response file" in message || "ApiContract" in message ->
            WinRtMetadataDiagnosticCode.InvalidCommandSpecification
        else -> WinRtMetadataDiagnosticCode.InvalidMetadataSource
    }

private val WinRtTypeDefinition.hasProjectedSurface: Boolean
    get() = methods.isNotEmpty() || properties.isNotEmpty() || events.isNotEmpty()

private fun WinRtPropertyDefinition.rowId(): Int? =
    getterMethodRowId ?: setterMethodRowId

private fun WinRtEventDefinition.rowId(): Int? =
    addMethodRowId ?: removeMethodRowId
