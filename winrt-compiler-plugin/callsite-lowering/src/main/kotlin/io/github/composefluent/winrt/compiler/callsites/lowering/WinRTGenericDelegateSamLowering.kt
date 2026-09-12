@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irCallWithSubstitutedType
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrEnumEntrySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.IrTypeSubstitutor
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Lowers generic WinRT delegate SAM values before Kotlin's ordinary SAM
 * lowering. The declaration annotation carries only the generic WinMD identity
 * and the owning runtime adapter; all closed shape facts come from IR here.
 */
internal fun lowerWinRTGenericDelegateSamReferences(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    guidSignaturesByKotlinClass: Map<String, String>,
) {
    val descriptorFields = mutableMapOf<Pair<IrFile, WinRTDelegateDescriptorShape>, IrField>()
    moduleFragment.transformChildrenVoid(
        object : IrElementTransformerVoidWithContext() {
            override fun visitRichFunctionReference(expression: IrRichFunctionReference): IrExpression {
                val transformed = super.visitRichFunctionReference(expression) as IrRichFunctionReference
                return lowerGenericDelegateSamValue(
                    delegateType = transformed.type as? IrSimpleType ?: return transformed,
                    callback = transformed,
                    pluginContext = pluginContext,
                    guidSignaturesByKotlinClass = guidSignaturesByKotlinClass,
                    descriptorFields = descriptorFields,
                    scopeOwner = currentScope?.scope?.scopeOwnerSymbol ?: return transformed,
                ) ?: transformed
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
                val transformed = super.visitTypeOperator(expression) as IrTypeOperatorCall
                if (transformed.operator != IrTypeOperator.SAM_CONVERSION) return transformed
                val delegateType = transformed.type as? IrSimpleType ?: return transformed
                return lowerGenericDelegateSamValue(
                    delegateType = delegateType,
                    callback = transformed.argument,
                    pluginContext = pluginContext,
                    guidSignaturesByKotlinClass = guidSignaturesByKotlinClass,
                    descriptorFields = descriptorFields,
                    scopeOwner = currentScope?.scope?.scopeOwnerSymbol ?: return transformed,
                ) ?: transformed
            }
        },
    )
}

private fun lowerGenericDelegateSamValue(
    delegateType: IrSimpleType,
    callback: IrExpression,
    pluginContext: IrPluginContext,
    guidSignaturesByKotlinClass: Map<String, String>,
    descriptorFields: MutableMap<Pair<IrFile, WinRTDelegateDescriptorShape>, IrField>,
    scopeOwner: org.jetbrains.kotlin.ir.symbols.IrSymbol,
): IrExpression? {
    val delegateClass = delegateType.classOrNull?.owner ?: return null
    val annotation = delegateClass.annotations
        .filterIsInstance<IrConstructorCall>()
        .singleOrNull { it.type.classFqName?.asString() == WINRT_DELEGATE_TYPE_ANNOTATION_FQ_NAME }
        ?: return null
    val genericInterfaceIid = annotation.stringArgument("genericInterfaceIid")
        .takeIf(String::isNotBlank)
        ?: return null
    val adapterFunctionName = annotation.stringArgument("adapterFunction")
        .takeIf(String::isNotBlank)
        ?: return null
    val typeArguments = delegateType.arguments.map { argument -> argument.typeOrNull ?: return null }
    if (typeArguments.size != delegateClass.typeParameters.size || typeArguments.any { it is IrSimpleType && it.arguments.any { nested -> nested.typeOrNull == null } }) {
        return null
    }
    val invoke = delegateClass.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == "invoke" &&
                function.parameters.any { parameter -> parameter.kind == IrParameterKind.Regular }
        }
        ?: return null
    val substitutions = delegateClass.typeParameters
        .map(IrTypeParameter::symbol)
        .zip(typeArguments)
        .toMap()
    val substitutor = IrTypeSubstitutor(substitutions, allowEmptySubstitution = true)
    val invokeParameters = invoke.parameters
        .filter { parameter -> parameter.kind == IrParameterKind.Regular }
        .map { parameter -> substitutor.substitute(parameter.type) }
    val invokeReturnType = substitutor.substitute(invoke.returnType)
    val parameterKinds = invokeParameters.map { type -> valueKindForType(type, guidSignaturesByKotlinClass) ?: return null }
    val returnKind = valueKindForType(invokeReturnType, guidSignaturesByKotlinClass) ?: return null
    val argumentSignatures = typeArguments.map { type -> typeSignatureForType(type, guidSignaturesByKotlinClass) ?: return null }
    val closedInterfaceId = guidForSignature(
        buildString {
            append("pinterface({")
            append(genericInterfaceIid.lowercase())
            append("};")
            argumentSignatures.joinTo(this, separator = ";")
            append(')')
        },
    )
    val fromFile = (scopeOwner.owner as? IrDeclaration)?.containingFile()
        ?: return null
    val adapter = resolveAdapterFunction(pluginContext, adapterFunctionName, fromFile)
        ?: return null
    val builder = DeclarationIrBuilder(
        pluginContext,
        scopeOwner,
        callback.startOffset,
        callback.endOffset,
    )
    val descriptor = descriptorFieldExpression(
        builder = builder,
        pluginContext = pluginContext,
        file = fromFile,
        interfaceId = closedInterfaceId,
        parameterKinds = parameterKinds,
        returnKind = returnKind,
        descriptorFields = descriptorFields,
        startOffset = callback.startOffset,
        endOffset = callback.endOffset,
    ) ?: return null
    val call = builder.irCallWithSubstitutedType(
        callee = adapter,
        typeArguments = typeArguments,
    )
    val regularParameters = adapter.owner.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
    if (regularParameters.size != 2) return null
    val callbackIndex = adapter.owner.parameters.indexOf(regularParameters[0])
    val descriptorIndex = adapter.owner.parameters.indexOf(regularParameters[1])
    call.arguments[callbackIndex] = callback
    call.arguments[descriptorIndex] = descriptor
    call.type = delegateType
    return call
}

private fun createDescriptorExpression(
    builder: DeclarationIrBuilder,
    pluginContext: IrPluginContext,
    fromFile: IrFile?,
    helper: IrSimpleFunctionSymbol,
    interfaceId: String,
    parameterKinds: List<DelegateValueKind>,
    returnKind: DelegateValueKind,
): IrExpression? {
    val guidClass = pluginContext.findClassSymbol(ClassId.topLevel(FqName(WINRT_GUID_FQ_NAME)), fromFile) ?: return null
    val guidConstructor = guidClass.owner.constructors.singleOrNull { constructor ->
        constructor.parameters.count { it.kind == IrParameterKind.Regular } == 1 &&
            constructor.parameters.single { it.kind == IrParameterKind.Regular }.type.classFqName?.asString() == KOTLIN_STRING_FQ_NAME
    } ?: return null
    val enumClass = pluginContext.findClassSymbol(ClassId.topLevel(FqName(WINRT_DELEGATE_VALUE_KIND_FQ_NAME)), fromFile) ?: return null
    val enumType = enumClass.owner.defaultType
    val guid = builder.irCall(guidConstructor.symbol).apply {
        arguments[guidConstructor.parameters.indexOfFirst { it.kind == IrParameterKind.Regular }] = builder.irString(interfaceId)
    }
    val parameterElements = parameterKinds.map { kind -> enumValue(builder, enumClass, enumType, kind.name) ?: return null }
    val returnElement = enumValue(builder, enumClass, enumType, returnKind.name) ?: return null
    val call = builder.irCall(helper)
    val regularParameters = helper.owner.parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }
    if (regularParameters.size != 3) return null
    val interfaceIndex = helper.owner.parameters.indexOf(regularParameters[0])
    val returnIndex = helper.owner.parameters.indexOf(regularParameters[1])
    val varargIndex = helper.owner.parameters.indexOf(regularParameters[2])
    call.arguments[interfaceIndex] = guid
    call.arguments[returnIndex] = returnElement
    // `irVararg` takes the element type. Passing Array<WinRTDelegateValueKind>
    // here would make the backend allocate WinRTDelegateValueKind[][] while
    // the runtime helper expects WinRTDelegateValueKind[].
    call.arguments[varargIndex] = builder.irVararg(enumType, parameterElements)
    return call
}

/**
 * Hoists a closed delegate descriptor into one private static field per source
 * file and exact closed shape.  The field is compiler-generated from the IR
 * facts, so every runtime-owned and intrinsic/generated use shares the same
 * descriptor construction without adding a runtime type family or CallSite
 * implementation payload.
 */
private fun descriptorFieldExpression(
    builder: DeclarationIrBuilder,
    pluginContext: IrPluginContext,
    file: IrFile,
    interfaceId: String,
    parameterKinds: List<DelegateValueKind>,
    returnKind: DelegateValueKind,
    descriptorFields: MutableMap<Pair<IrFile, WinRTDelegateDescriptorShape>, IrField>,
    startOffset: Int,
    endOffset: Int,
): IrExpression? {
    val shape = WinRTDelegateDescriptorShape(interfaceId, parameterKinds, returnKind)
    val key = file to shape
    descriptorFields[key]?.let { field -> return builder.irGetField(null, field) }

    val helper = resolveRuntimeFunction(pluginContext, WINRT_CREATE_DESCRIPTOR_FQ_NAME, file)
        ?: return null
    // Kotlin/Native gives top-level fields in the same package one shared IR
    // signature across source files.  Keep the per-file cache, but include a
    // stable source-file discriminator so equal closed shapes from different
    // files do not collide during Native lowering.
    val fieldName = Name.identifier(
        "kotlinWinRTDelegateDescriptor_${descriptorFieldFileSuffix(file)}_${descriptorFieldSuffix(shape)}",
    )
    val field = file.declarations
        .filterIsInstance<IrField>()
        .singleOrNull { candidate -> candidate.name == fieldName }
        ?: pluginContext.irFactory.buildField {
            this.startOffset = startOffset
            this.endOffset = endOffset
            origin = IrDeclarationOrigin.DEFINED
            name = fieldName
            visibility = DescriptorVisibilities.PRIVATE
            type = helper.owner.returnType
            isFinal = true
            isStatic = true
        }.also { created ->
            created.parent = file
            val initializerBuilder = DeclarationIrBuilder(
                pluginContext,
                created.symbol,
                startOffset,
                endOffset,
            )
            val initializer = createDescriptorExpression(
                builder = initializerBuilder,
                pluginContext = pluginContext,
                fromFile = file,
                helper = helper,
                interfaceId = interfaceId,
                parameterKinds = parameterKinds,
                returnKind = returnKind,
            ) ?: return null
            created.initializer = pluginContext.irFactory.createExpressionBody(
                startOffset,
                endOffset,
                initializer,
            )
            file.declarations += created
        }
    descriptorFields[key] = field
    return builder.irGetField(null, field)
}

private data class WinRTDelegateDescriptorShape(
    val interfaceId: String,
    val parameterKinds: List<DelegateValueKind>,
    val returnKind: DelegateValueKind,
)

private fun descriptorFieldSuffix(shape: WinRTDelegateDescriptorShape): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(
            buildString {
                append(shape.interfaceId)
                append('|')
                append(shape.returnKind.name)
                append('|')
                shape.parameterKinds.joinTo(this, separator = ",", transform = DelegateValueKind::name)
            }.toByteArray(StandardCharsets.UTF_8),
        )
    return digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
}

private fun descriptorFieldFileSuffix(file: IrFile): String {
    val sourceName = file.fileEntry.name
        .replace('\\', '/')
        .substringAfterLast('/')
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(sourceName.toByteArray(StandardCharsets.UTF_8))
    return digest.take(4).joinToString("") { byte -> "%02x".format(byte) }
}

private fun enumValue(
    builder: DeclarationIrBuilder,
    enumClass: IrClassSymbol,
    enumType: IrType,
    name: String,
): IrGetEnumValue? {
    val entry = enumClass.owner.declarations
        .filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrEnumEntry>()
        .singleOrNull { candidate -> candidate.name.asString() == name }
        ?: return null
    return IrGetEnumValueImpl(
        constructorIndicator = null,
        startOffset = builder.startOffset,
        endOffset = builder.endOffset,
        type = enumType,
        symbol = entry.symbol,
    )
}

private fun resolveAdapterFunction(
    pluginContext: IrPluginContext,
    fqName: String,
    fromFile: IrFile?,
): IrSimpleFunctionSymbol? {
    val name = runCatching { FqName(fqName) }.getOrNull() ?: return null
    val callableId = CallableId(name.parent(), name.shortName())
    return pluginContext.findFunctions(callableId, fromFile)
        .singleOrNull { function -> function.owner.typeParameters.size == 1 || function.owner.typeParameters.size == 2 }
}

private fun resolveRuntimeFunction(
    pluginContext: IrPluginContext,
    fqName: String,
    fromFile: IrFile?,
): IrSimpleFunctionSymbol? {
    val name = FqName(fqName)
    val callableId = CallableId(name.parent(), name.shortName())
    return pluginContext.findFunctions(callableId, fromFile)
        .singleOrNull { function -> function.owner.typeParameters.isEmpty() }
}

private fun IrPluginContext.findClassSymbol(classId: ClassId, fromFile: IrFile?): IrClassSymbol? =
    fromFile?.let { finderForSource(it).findClass(classId) } ?: finderForBuiltins().findClass(classId)

private fun IrPluginContext.findFunctions(
    callableId: CallableId,
    fromFile: IrFile?,
): Collection<IrSimpleFunctionSymbol> {
    val source = fromFile?.let { finderForSource(it).findFunctions(callableId) }.orEmpty()
    return source.ifEmpty { finderForBuiltins().findFunctions(callableId) }
}

private fun IrDeclaration.containingFile(): IrFile? {
    var parent = parent
    while (parent !is IrFile) {
        parent = (parent as? IrDeclaration)?.parent ?: return null
    }
    return parent
}

private fun typeSignatureForType(
    type: IrType,
    guidSignaturesByKotlinClass: Map<String, String>,
): String? {
    val simple = type as? IrSimpleType ?: return null
    val fqName = simple.classFqName?.asString() ?: return null
    primitiveSignature(fqName)?.let { return it }
    if (fqName == KOTLIN_ANY_FQ_NAME) return "cinterface(IInspectable)"
    val base = guidSignaturesByKotlinClass[fqName]
    if (simple.arguments.isEmpty()) return base
    val genericBase = base?.let(::genericDefinitionGuid)
        ?: base?.takeIf { it.startsWith("{") }
        ?: return null
    val arguments = simple.arguments.map { argument -> argument.typeOrNull?.let { typeSignatureForType(it, guidSignaturesByKotlinClass) } ?: return null }
    return buildString {
        append("pinterface(")
        append(genericBase)
        arguments.joinTo(this, separator = ";", prefix = ";")
        append(')')
    }
}

private fun valueKindForType(
    type: IrType,
    guidSignaturesByKotlinClass: Map<String, String>,
): DelegateValueKind? {
    val fqName = type.classFqName?.asString() ?: return null
    return when (fqName) {
        KOTLIN_UNIT_FQ_NAME -> DelegateValueKind.UNIT
        KOTLIN_BOOLEAN_FQ_NAME -> DelegateValueKind.BOOLEAN
        KOTLIN_BYTE_FQ_NAME -> DelegateValueKind.INT8
        KOTLIN_UBYTE_FQ_NAME -> DelegateValueKind.UINT8
        KOTLIN_SHORT_FQ_NAME -> DelegateValueKind.INT16
        KOTLIN_USHORT_FQ_NAME -> DelegateValueKind.UINT16
        KOTLIN_INT_FQ_NAME -> DelegateValueKind.INT32
        KOTLIN_UINT_FQ_NAME -> DelegateValueKind.UINT32
        KOTLIN_LONG_FQ_NAME -> DelegateValueKind.INT64
        KOTLIN_ULONG_FQ_NAME -> DelegateValueKind.UINT64
        KOTLIN_FLOAT_FQ_NAME -> DelegateValueKind.FLOAT
        KOTLIN_DOUBLE_FQ_NAME -> DelegateValueKind.DOUBLE
        KOTLIN_CHAR_FQ_NAME -> DelegateValueKind.CHAR16
        KOTLIN_STRING_FQ_NAME -> DelegateValueKind.HSTRING
        KOTLIN_ANY_FQ_NAME -> DelegateValueKind.OBJECT
        WINRT_GUID_FQ_NAME -> DelegateValueKind.GUID
        else -> {
            val signature = typeSignatureForType(type, guidSignaturesByKotlinClass) ?: return null
            when {
                signature.startsWith("enum(") -> enumUnderlyingKind(signature)
                signature.startsWith("struct(") -> DelegateValueKind.STRUCT
                signature.startsWith("rc(") -> DelegateValueKind.IINSPECTABLE
                signature.startsWith("cinterface(") || signature.startsWith("pinterface(") -> DelegateValueKind.IUNKNOWN
                signature.startsWith("delegate(") || signature.startsWith("{") -> DelegateValueKind.IUNKNOWN
                else -> null
            }
        }
    }
}

private fun enumUnderlyingKind(signature: String): DelegateValueKind? =
    signature.substringAfter(';', missingDelimiterValue = "")
        .removeSuffix(")")
        .let { primitiveSignature ->
            when (primitiveSignature) {
                "i1" -> DelegateValueKind.INT8
                "u1" -> DelegateValueKind.UINT8
                "i2" -> DelegateValueKind.INT16
                "u2" -> DelegateValueKind.UINT16
                "i4" -> DelegateValueKind.INT32
                "u4" -> DelegateValueKind.UINT32
                "i8" -> DelegateValueKind.INT64
                "u8" -> DelegateValueKind.UINT64
                else -> null
            }
        }

private fun primitiveSignature(fqName: String): String? = when (fqName) {
    KOTLIN_BOOLEAN_FQ_NAME -> "b1"
    KOTLIN_BYTE_FQ_NAME -> "i1"
    KOTLIN_UBYTE_FQ_NAME -> "u1"
    KOTLIN_SHORT_FQ_NAME -> "i2"
    KOTLIN_USHORT_FQ_NAME -> "u2"
    KOTLIN_INT_FQ_NAME -> "i4"
    KOTLIN_UINT_FQ_NAME -> "u4"
    KOTLIN_LONG_FQ_NAME -> "i8"
    KOTLIN_ULONG_FQ_NAME -> "u8"
    KOTLIN_FLOAT_FQ_NAME -> "f4"
    KOTLIN_DOUBLE_FQ_NAME -> "f8"
    KOTLIN_CHAR_FQ_NAME -> "c2"
    KOTLIN_STRING_FQ_NAME -> "string"
    WINRT_GUID_FQ_NAME -> "g16"
    else -> null
}

private fun genericDefinitionGuid(signature: String): String? =
    signature.takeIf { it.startsWith("delegate({") && it.endsWith("})") }
        ?.removePrefix("delegate(")
        ?.removeSuffix(")")
        ?: signature.takeIf { it.startsWith("pinterface(") }
            ?.substringAfter("pinterface(")
            ?.substringBefore(';')

private fun IrConstructorCall.stringArgument(name: String): String {
    val index = symbol.owner.parameters.indexOfFirst { parameter -> parameter.name.asString() == name }
    if (index < 0) return ""
    return (arguments.getOrNull(index) as? org.jetbrains.kotlin.ir.expressions.IrConst)?.value as? String ?: ""
}

private enum class DelegateValueKind {
    UNIT,
    BOOLEAN,
    INT8,
    UINT8,
    INT16,
    UINT16,
    INT32,
    UINT32,
    INT64,
    UINT64,
    FLOAT,
    DOUBLE,
    CHAR16,
    GUID,
    STRUCT,
    HSTRING,
    OBJECT,
    IUNKNOWN,
    IINSPECTABLE,
}

private const val WINRT_DELEGATE_TYPE_ANNOTATION_FQ_NAME = "io.github.composefluent.winrt.runtime.WinRTDelegateType"
private const val WINRT_CREATE_DESCRIPTOR_FQ_NAME = "io.github.composefluent.winrt.runtime.createWinRTDelegateDescriptor"
private const val WINRT_DELEGATE_VALUE_KIND_FQ_NAME = "io.github.composefluent.winrt.runtime.WinRTDelegateValueKind"
private const val WINRT_GUID_FQ_NAME = "io.github.composefluent.winrt.runtime.Guid"
private const val KOTLIN_UNIT_FQ_NAME = "kotlin.Unit"
private const val KOTLIN_BOOLEAN_FQ_NAME = "kotlin.Boolean"
private const val KOTLIN_BYTE_FQ_NAME = "kotlin.Byte"
private const val KOTLIN_UBYTE_FQ_NAME = "kotlin.UByte"
private const val KOTLIN_SHORT_FQ_NAME = "kotlin.Short"
private const val KOTLIN_USHORT_FQ_NAME = "kotlin.UShort"
private const val KOTLIN_INT_FQ_NAME = "kotlin.Int"
private const val KOTLIN_UINT_FQ_NAME = "kotlin.UInt"
private const val KOTLIN_LONG_FQ_NAME = "kotlin.Long"
private const val KOTLIN_ULONG_FQ_NAME = "kotlin.ULong"
private const val KOTLIN_FLOAT_FQ_NAME = "kotlin.Float"
private const val KOTLIN_DOUBLE_FQ_NAME = "kotlin.Double"
private const val KOTLIN_CHAR_FQ_NAME = "kotlin.Char"
private const val KOTLIN_STRING_FQ_NAME = "kotlin.String"
private const val KOTLIN_ANY_FQ_NAME = "kotlin.Any"
