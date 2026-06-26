package io.github.composefluent.winrt.metadata

enum class WinRTMetadataDiagnosticSeverity {
    Error,
    Warning,
}

enum class WinRTMetadataDiagnosticCode {
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

data class WinRTMetadataDiagnostic(
    val code: WinRTMetadataDiagnosticCode,
    val severity: WinRTMetadataDiagnosticSeverity,
    val message: String,
    val typeName: String? = null,
    val memberName: String? = null,
    val sourceName: String? = null,
    val rowId: Int? = null,
) {
    val isError: Boolean
        get() = severity == WinRTMetadataDiagnosticSeverity.Error
}

data class WinRTMetadataDiagnosticReport(
    val diagnostics: List<WinRTMetadataDiagnostic>,
) {
    val errors: List<WinRTMetadataDiagnostic>
        get() = diagnostics.filter(WinRTMetadataDiagnostic::isError)

    val warnings: List<WinRTMetadataDiagnostic>
        get() = diagnostics.filterNot(WinRTMetadataDiagnostic::isError)

    val hasErrors: Boolean
        get() = errors.isNotEmpty()

    fun throwIfErrors() {
        if (hasErrors) {
            throw WinRTMetadataDiagnosticException(this)
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

class WinRTMetadataDiagnosticException(
    val report: WinRTMetadataDiagnosticReport,
) : IllegalArgumentException(report.format())

data class WinRTMetadataValidationOptions(
    val validateTypeReferences: Boolean = true,
    val validateActivationFactoryReferences: Boolean = true,
    val validateCustomAttributeTypeReferences: Boolean = false,
    val validateRuntimeClassDefaultInterface: Boolean = true,
    val kotlinSpecificGaps: List<String> = emptyList(),
)

class WinRTMetadataValidator private constructor(
    private val model: WinRTMetadataModel,
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    private val typeClassifier: WinRTMetadataTypeClassifier,
    private val typeSemanticsResolver: WinRTTypeSemanticsResolver,
    private val options: WinRTMetadataValidationOptions,
) {
    fun validateForProjection(): WinRTMetadataDiagnosticReport {
        val diagnostics = buildList {
            model.namespaces
                .flatMap(WinRTNamespace::types)
                .forEach { type -> validateType(type, this) }
            options.kotlinSpecificGaps.map(String::trim).filter(String::isNotEmpty).forEach { gap ->
                add(
                    WinRTMetadataDiagnostic(
                        code = WinRTMetadataDiagnosticCode.IntentionalKotlinGap,
                        severity = WinRTMetadataDiagnosticSeverity.Warning,
                        message = gap,
                    ),
                )
            }
        }
        return WinRTMetadataDiagnosticReport(diagnostics)
    }

    private fun validateType(
        type: WinRTTypeDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        val typeName = type.qualifiedName
        if (type.kind == WinRTTypeKind.Unknown && type.hasProjectedSurface) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.UnsupportedTypeKind,
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
            WinRTTypeKind.Interface -> validateInterface(type, diagnostics)
            WinRTTypeKind.RuntimeClass -> validateRuntimeClass(type, diagnostics)
            WinRTTypeKind.Delegate -> validateDelegate(type, diagnostics)
            WinRTTypeKind.Struct -> validateStruct(type, diagnostics)
            WinRTTypeKind.Enum,
            WinRTTypeKind.Unknown,
            -> Unit
        }

        type.methods.forEach { method -> validateMethod(type, method, diagnostics) }
        type.properties.forEach { property -> validateProperty(type, property, diagnostics) }
        type.events.forEach { event -> validateEvent(type, event, diagnostics) }
    }

    private fun validateInterface(
        type: WinRTTypeDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        if (type.hasProjectedSurface && type.iid == null) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.MissingInterfaceIid,
                typeName = type.qualifiedName,
                message = "Generator requires interface ${type.qualifiedName} to carry metadata IID before projection planning.",
            )
        }
    }

    private fun validateRuntimeClass(
        type: WinRTTypeDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        val instanceSurface = type.methods.any { !it.isStatic } ||
            type.properties.any { !it.isStatic } ||
            type.events.any { !it.isStatic }
        if (options.validateRuntimeClassDefaultInterface && instanceSurface && type.defaultInterfaceName == null) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.MissingRuntimeClassDefaultInterface,
                typeName = type.qualifiedName,
                message = "Runtime class has instance members but no default interface metadata.",
            )
        }

        val staticSurface = type.methods.any(WinRTMethodDefinition::isStatic) ||
            type.properties.any(WinRTPropertyDefinition::isStatic) ||
            type.events.any(WinRTEventDefinition::isStatic)
        if (staticSurface && type.activation.staticInterfaceNames.isEmpty()) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.MissingRuntimeClassStaticInterface,
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
                code = WinRTMetadataDiagnosticCode.MissingActivationFactoryMetadata,
                typeName = type.qualifiedName,
                message = "Runtime class references an activatable factory that is not present in the metadata model.",
            )
        }
    }

    private fun validateDelegate(
        type: WinRTTypeDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        type.methods.forEach { method ->
            method.parameters.forEach { parameter ->
                validateSignatureType(parameter.type, type.namespace, type.qualifiedName, method.name, "delegate parameter ${parameter.name}", diagnostics, method.methodRowId)
            }
        }
    }

    private fun validateStruct(
        type: WinRTTypeDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        type.fields
            .filterNot { it.isStatic || it.isLiteral }
            .forEach { field ->
                validateSignatureType(field.type, type.namespace, type.qualifiedName, field.name, "struct field ${field.name}", diagnostics, field.rowId)
            }
    }

    private fun validateMethod(
        type: WinRTTypeDefinition,
        method: WinRTMethodDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
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
        type: WinRTTypeDefinition,
        property: WinRTPropertyDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        if (!property.hasValidAccessors || !property.hasGetterOrSetterAccessor()) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.InvalidPropertyAccessors,
                typeName = type.qualifiedName,
                memberName = property.name,
                rowId = property.rowId(),
                message = "Property semantics do not resolve to a valid getter/setter accessor pair.",
            )
        }
        validateSignatureType(property.type, type.namespace, type.qualifiedName, property.name, "property type", diagnostics, property.rowId())
    }

    private fun WinRTPropertyDefinition.hasGetterOrSetterAccessor(): Boolean =
        getterMethodName != null ||
            setterMethodName != null ||
            getterMethodRowId != null ||
            setterMethodRowId != null

    private fun validateEvent(
        type: WinRTTypeDefinition,
        event: WinRTEventDefinition,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        if (!event.hasValidAccessors || !event.hasAddRemoveAccessorPair()) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.InvalidEventAccessors,
                typeName = type.qualifiedName,
                memberName = event.name,
                rowId = event.rowId(),
                message = "Event semantics do not resolve to a valid add/remove accessor pair.",
            )
        }
        validateSignatureType(event.delegateType, type.namespace, type.qualifiedName, event.name, "event delegate type", diagnostics, event.rowId())
    }

    private fun WinRTEventDefinition.hasAddRemoveAccessorPair(): Boolean =
        (addMethodName != null || addMethodRowId != null) &&
            (removeMethodName != null || removeMethodRowId != null)

    private fun validateSignatureType(
        type: WinRTTypeRef,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        role: String,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
        rowId: Int? = null,
    ) {
        val normalizedType = type.normalized()
        when (normalizedType.kind) {
            WinRTTypeRefKind.MethodTypeParameter -> Unit

            WinRTTypeRefKind.Unknown -> {
                diagnostics += error(
                    code = WinRTMetadataDiagnosticCode.UnsupportedSignatureType,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    rowId = rowId,
                    message = "$role has an unknown metadata signature type.",
                )
                diagnostics += error(
                    code = WinRTMetadataDiagnosticCode.UnsupportedSemanticShape,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    rowId = rowId,
                    message = "$role cannot be lowered through the reference projection type semantics kernel: unknown type.",
                )
            }

            WinRTTypeRefKind.Array -> {
                if (normalizedType.arrayRank != 1) {
                    diagnostics += error(
                        code = WinRTMetadataDiagnosticCode.UnsupportedSignatureType,
                        typeName = ownerTypeName,
                        memberName = memberName,
                        rowId = rowId,
                        message = "$role uses array rank ${normalizedType.arrayRank}; only SZARRAY-style rank-1 arrays are supported.",
                    )
                }
                validateSignatureType(
                    normalizedType.elementType ?: WinRTTypeRef.unknown(),
                    currentNamespace,
                    ownerTypeName,
                    memberName,
                    "$role element",
                    diagnostics,
                    rowId,
                )
            }

            WinRTTypeRefKind.Named -> {
                validateTypeReference(normalizedType, currentNamespace, ownerTypeName, role, diagnostics, rowId)
                validateNamedSemanticShape(normalizedType, currentNamespace, ownerTypeName, memberName, role, diagnostics, rowId)
                normalizedType.typeArguments.forEachIndexed { index, argument ->
                    validateSignatureType(argument, currentNamespace, ownerTypeName, memberName, "$role generic argument $index", diagnostics, rowId)
                }
            }

            WinRTTypeRefKind.GenericTypeParameter -> Unit
        }
    }

    private fun validateNamedSemanticShape(
        type: WinRTTypeRef,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        role: String,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
        rowId: Int? = null,
    ) {
        val classification = typeClassifier.classify(type, currentNamespace)
        if (classification.projectionCategory == WinRTProjectionCategory.Unit ||
            classification.isMappedType ||
            classification.specialType != null ||
            classification.projectionCategory == WinRTProjectionCategory.Unknown
        ) {
            return
        }
        runCatching { typeSemanticsResolver.resolve(type, currentNamespace) }
            .onFailure { failure ->
                diagnostics += error(
                    code = WinRTMetadataDiagnosticCode.UnsupportedSemanticShape,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    rowId = rowId,
                    message = "$role cannot be lowered through the reference projection type semantics kernel: ${failure.message}",
                )
            }
    }

    private fun validateTypeReference(
        type: WinRTTypeRef?,
        currentNamespace: String,
        ownerTypeName: String,
        role: String,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
        rowId: Int? = null,
    ) {
        val normalizedType = type?.normalized() ?: return
        if (normalizedType.kind != WinRTTypeRefKind.Named) {
            return
        }
        if (!options.validateTypeReferences) {
            return
        }
        val classification = typeClassifier.classify(normalizedType, currentNamespace)
        val isResolved = classification.definitionType != null ||
            classification.projectionCategory != WinRTProjectionCategory.Unknown ||
            classification.isMappedType ||
            classification.specialType != null
        if (!isResolved) {
            diagnostics += error(
                code = WinRTMetadataDiagnosticCode.UnresolvedTypeReference,
                typeName = ownerTypeName,
                rowId = rowId,
                message = "$role references unresolved type ${classification.typeName}.",
            )
        }
    }

    private fun validateCustomAttribute(
        attribute: WinRTCustomAttributeDefinition,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        if (options.validateCustomAttributeTypeReferences) {
            validateTypeReference(WinRTTypeRef.fromDisplayName(attribute.typeName), currentNamespace, ownerTypeName, "custom attribute", diagnostics)
        }
        attribute.fixedArguments.forEach { value ->
            validateCustomAttributeValue(value, currentNamespace, ownerTypeName, memberName, diagnostics)
        }
        attribute.namedArguments.forEach { argument ->
            validateCustomAttributeValue(argument.value, currentNamespace, ownerTypeName, memberName, diagnostics)
        }
    }

    private fun validateCustomAttributeValue(
        value: WinRTCustomAttributeValue,
        currentNamespace: String,
        ownerTypeName: String,
        memberName: String?,
        diagnostics: MutableList<WinRTMetadataDiagnostic>,
    ) {
        when (value) {
            is WinRTCustomAttributeValue.TypeValue ->
                if (options.validateCustomAttributeTypeReferences) {
                    value.typeName
                        ?.let(WinRTTypeRef::fromDisplayName)
                        ?.let { validateTypeReference(it, currentNamespace, ownerTypeName, "custom attribute System.Type argument", diagnostics) }
                }

            is WinRTCustomAttributeValue.EnumValue ->
                if (options.validateCustomAttributeTypeReferences) {
                    validateTypeReference(WinRTTypeRef.fromDisplayName(value.enumTypeName), currentNamespace, ownerTypeName, "custom attribute enum argument", diagnostics)
                }

            is WinRTCustomAttributeValue.ArrayValue ->
                value.values.forEach { nested ->
                    validateCustomAttributeValue(nested, currentNamespace, ownerTypeName, memberName, diagnostics)
                }

            WinRTCustomAttributeValue.NullValue ->
                diagnostics += error(
                    code = WinRTMetadataDiagnosticCode.UnknownCustomAttributeBlob,
                    typeName = ownerTypeName,
                    memberName = memberName,
                    message = "Custom attribute blob contains a null value that cannot be used as generator input.",
                )

            is WinRTCustomAttributeValue.BooleanValue,
            is WinRTCustomAttributeValue.FloatingPointValue,
            is WinRTCustomAttributeValue.IntegralValue,
            is WinRTCustomAttributeValue.StringValue,
            -> Unit
        }
    }

    private fun resolveDefinition(type: WinRTTypeRef, currentNamespace: String): WinRTTypeDefinition? {
        val normalizedType = type.normalized()
        val qualifiedName = normalizedType.qualifiedName ?: return null
        return qualifyTypeName(qualifiedName, currentNamespace, typesByQualifiedName)?.let(typesByQualifiedName::get)
    }

    private fun error(
        code: WinRTMetadataDiagnosticCode,
        typeName: String,
        message: String,
        memberName: String? = null,
        rowId: Int? = null,
    ): WinRTMetadataDiagnostic =
        WinRTMetadataDiagnostic(
            code = code,
            severity = WinRTMetadataDiagnosticSeverity.Error,
            message = message,
            typeName = typeName,
            memberName = memberName,
            sourceName = "metadata",
            rowId = rowId,
        )

    companion object {
        fun create(
            model: WinRTMetadataModel,
            options: WinRTMetadataValidationOptions = WinRTMetadataValidationOptions(),
        ): WinRTMetadataValidator {
            val normalized = model.normalized()
            return WinRTMetadataValidator(
                model = normalized,
                typesByQualifiedName = buildTypesByQualifiedName(normalized),
                typeClassifier = normalized.typeClassifier(),
                typeSemanticsResolver = normalized.typeSemanticsResolver(),
                options = options,
            )
        }
    }
}

fun WinRTMetadataModel.validateForProjection(
    options: WinRTMetadataValidationOptions = WinRTMetadataValidationOptions(),
): WinRTMetadataDiagnosticReport =
    WinRTMetadataValidator.create(this, options).validateForProjection()

fun WinRTMetadataModel.requireValidForProjection(
    options: WinRTMetadataValidationOptions = WinRTMetadataValidationOptions(),
): WinRTMetadataModel {
    val normalized = normalized()
    normalized.validateForProjection(options).throwIfErrors()
    return normalized
}

fun WinRTMetadataProjectionContext.validateForProjectionInputs(
    options: WinRTMetadataValidationOptions = WinRTMetadataValidationOptions(),
): WinRTMetadataDiagnosticReport =
    try {
        load().validateForProjection(options)
    } catch (error: IllegalArgumentException) {
        WinRTMetadataDiagnosticReport(
            listOf(
                WinRTMetadataDiagnostic(
                    code = diagnosticCodeForInputFailure(error.message.orEmpty()),
                    severity = WinRTMetadataDiagnosticSeverity.Error,
                    message = error.message ?: "Invalid metadata projection input.",
                ),
            ),
        )
    }

private fun diagnosticCodeForInputFailure(message: String): WinRTMetadataDiagnosticCode =
    when {
        "missing WinMD" in message || "referenced metadata" in message ->
            WinRTMetadataDiagnosticCode.MissingReferencedMetadata
        "Response file" in message || "ApiContract" in message ->
            WinRTMetadataDiagnosticCode.InvalidCommandSpecification
        else -> WinRTMetadataDiagnosticCode.InvalidMetadataSource
    }

private val WinRTTypeDefinition.hasProjectedSurface: Boolean
    get() = methods.isNotEmpty() || properties.isNotEmpty() || events.isNotEmpty()

private fun WinRTPropertyDefinition.rowId(): Int? =
    getterMethodRowId ?: setterMethodRowId

private fun WinRTEventDefinition.rowId(): Int? =
    addMethodRowId ?: removeMethodRowId
