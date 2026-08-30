@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.createExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/** Mirrors CsWinRT's IIDOptimizer for calls whose complete WinMD shape is known in IR. */
internal fun lowerWinRTGuidGeneratorCalls(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    guidSignaturesByKotlinClass: Map<String, String>,
) {
    val constantFields = mutableMapOf<Pair<IrFile, String>, IrField>()
    moduleFragment.transformChildrenVoid(
        object : IrElementTransformerVoidWithContext() {
            override fun visitCall(expression: IrCall): IrExpression {
                val call = super.visitCall(expression) as IrCall
                val currentScopeSymbol = currentScope?.scope?.scopeOwnerSymbol
                if (currentScopeSymbol != null) {
                    lowerClosedBorrowedInterfaceReference(call, pluginContext, currentScopeSymbol)?.let { return it }
                }
                val owner = call.symbol.owner
                if (owner.parentClassOrNull?.fqNameWhenAvailable?.asString() != GUID_GENERATOR_FQ_NAME) {
                    return call
                }
                val arguments = owner.parameters.mapIndexedNotNull { index, parameter ->
                    call.arguments.getOrNull(index)?.takeIf { parameter.kind == IrParameterKind.Regular }
                }
                val guid = when (owner.name.asString()) {
                    "createIID" -> resolveCreateIid(arguments, guidSignaturesByKotlinClass)
                    "getIID", "getGuid" -> resolveGetIid(arguments, guidSignaturesByKotlinClass)
                    else -> null
                } ?: return call
                val scope = currentScopeSymbol ?: return call
                val file = (scope.owner as? IrDeclaration)?.parent?.containingFile() ?: return call
                val constructor = call.type.classOrNull?.owner?.constructors
                    ?.singleOrNull { candidate ->
                        candidate.parameters.count { it.kind == IrParameterKind.Regular } == 1 &&
                            candidate.parameters.single { it.kind == IrParameterKind.Regular }.type.classFqName?.asString() == "kotlin.String"
                    }
                    ?: return call
                val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
                val field = constantFields.getOrPut(file to guid) {
                    val fieldName = Name.identifier("kotlinWinRTGuid_${guid.filterNot { it == '-' }.lowercase()}")
                    file.declarations.filterIsInstance<IrField>().singleOrNull { candidate ->
                        candidate.name == fieldName
                    } ?: pluginContext.irFactory.buildField {
                        startOffset = call.startOffset
                        endOffset = call.endOffset
                        origin = IrDeclarationOrigin.DEFINED
                        name = fieldName
                        visibility = DescriptorVisibilities.PRIVATE
                        type = call.type
                        isFinal = true
                        isStatic = true
                    }.also { created ->
                        created.parent = file
                        val initializerBuilder = DeclarationIrBuilder(
                            pluginContext,
                            created.symbol,
                            call.startOffset,
                            call.endOffset,
                        )
                        created.initializer = pluginContext.irFactory.createExpressionBody(
                            initializerBuilder.irCall(constructor.symbol).apply {
                                val parameterIndex = constructor.parameters.indexOfFirst {
                                    it.kind == IrParameterKind.Regular
                                }
                                this.arguments[parameterIndex] = initializerBuilder.irString(guid)
                            },
                        )
                        file.declarations += created
                    }
                }
                return builder.irGetField(null, field)
            }
        },
    )
}

private fun lowerClosedBorrowedInterfaceReference(
    call: IrCall,
    pluginContext: IrPluginContext,
    scope: org.jetbrains.kotlin.ir.symbols.IrSymbol,
): IrExpression? {
    val owner = call.symbol.owner
    if (owner.fqNameWhenAvailable?.asString() != ACQUIRE_BORROWED_INTERFACE_REFERENCE_FQ_NAME) return null
    if (owner.regularParameterTypeNames() != listOf(RAW_ADDRESS_FQ_NAME, GUID_FQ_NAME)) return null
    val callArguments = call.regularArguments()
    if (callArguments.size != 2) return null

    val interfaceIdCall = callArguments[1] as? IrCall ?: return null
    val interfaceIdOwner = interfaceIdCall.symbol.owner
    if (interfaceIdOwner.parentClassOrNull?.fqNameWhenAvailable?.asString() != PARAMETERIZED_INTERFACE_ID_FQ_NAME ||
        interfaceIdOwner.name.asString() != "createFromSignature" ||
        interfaceIdOwner.regularParameterTypeNames() != listOf(KOTLIN_STRING_FQ_NAME)
    ) {
        return null
    }
    val signature = interfaceIdCall.regularArguments().singleOrNull()?.stringConstant() ?: return null
    val (interfaceIdLowBits, interfaceIdHighBits) = guidAbiWords(guidForSignature(signature))

    val file = (scope.owner as? IrDeclaration)?.parent?.containingFile() ?: return null
    val scalarOverload = pluginContext.finderForSource(file)
        .findFunctions(ACQUIRE_BORROWED_INTERFACE_REFERENCE_CALLABLE_ID)
        .singleOrNull { function ->
            function.owner.regularParameterTypeNames() ==
                listOf(RAW_ADDRESS_FQ_NAME, KOTLIN_LONG_FQ_NAME, KOTLIN_LONG_FQ_NAME)
        }
        ?: return null
    val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
    val scalarParameters = scalarOverload.owner.parameters.withIndex()
        .filter { (_, parameter) -> parameter.kind == IrParameterKind.Regular }
    return builder.irCall(scalarOverload).apply {
        arguments[scalarParameters[0].index] = callArguments[0]
        arguments[scalarParameters[1].index] = builder.irLong(interfaceIdLowBits)
        arguments[scalarParameters[2].index] = builder.irLong(interfaceIdHighBits)
    }
}

private fun org.jetbrains.kotlin.ir.declarations.IrSimpleFunction.regularParameterTypeNames(): List<String?> =
    parameters
        .filter { parameter -> parameter.kind == IrParameterKind.Regular }
        .map { parameter -> parameter.type.classFqName?.asString() }

private fun IrCall.regularArguments(): List<IrExpression> =
    symbol.owner.parameters.mapIndexedNotNull { index, parameter ->
        arguments.getOrNull(index)?.takeIf { parameter.kind == IrParameterKind.Regular }
    }

private fun guidAbiWords(guid: String): Pair<Long, Long> {
    val uuid = UUID.fromString(guid)
    val mostSignificantBits = uuid.mostSignificantBits
    val data1 = (mostSignificantBits ushr 32) and 0xffff_ffffL
    val data2 = (mostSignificantBits ushr 16) and 0xffffL
    val data3 = mostSignificantBits and 0xffffL
    return (data1 or (data2 shl 32) or (data3 shl 48)) to
        java.lang.Long.reverseBytes(uuid.leastSignificantBits)
}

private fun resolveCreateIid(
    arguments: List<IrExpression>,
    guidSignaturesByKotlinClass: Map<String, String>,
): String? = when (arguments.size) {
    1 -> arguments.single().classGuidSignature(guidSignaturesByKotlinClass)?.let(::guidForSignature)
    2 -> {
        val genericInterface = arguments[0].constantGuid() ?: return null
        val typeArguments = arguments[1] as? IrVararg ?: return null
        val signatures = typeArguments.elements.map { element ->
            (element as? IrExpression)?.classGuidSignature(guidSignaturesByKotlinClass) ?: return null
        }
        guidForSignature(
            signatures.joinToString(
                separator = ";",
                prefix = "pinterface({${genericInterface.lowercase()}};",
                postfix = ")",
            ),
        )
    }
    else -> null
}

private fun resolveGetIid(
    arguments: List<IrExpression>,
    guidSignaturesByKotlinClass: Map<String, String>,
): String? {
    if (arguments.size != 1) return null
    val signature = arguments.single().classGuidSignature(guidSignaturesByKotlinClass) ?: return null
    return when {
        signature.isGuidSignature() -> signature.guidText()
        signature.startsWith("delegate(") && signature.endsWith(')') ->
            guidForSignature(signature.removePrefix("delegate(").dropLast(1))
        signature.startsWith("rc(") && signature.endsWith(')') ->
            signature.substringAfter(';', missingDelimiterValue = "")
                .dropLast(1)
                .takeIf(String::isNotBlank)
                ?.let(::guidForSignature)
        else -> null
    }
}

private fun IrExpression.classGuidSignature(
    guidSignaturesByKotlinClass: Map<String, String>,
): String? = (this as? IrClassReference)
    ?.symbol
    ?.let { it as? IrClassSymbol }
    ?.owner
    ?.fqNameWhenAvailable
    ?.asString()
    ?.let(guidSignaturesByKotlinClass::get)
    ?.takeIf(String::isNotBlank)

private fun IrExpression.constantGuid(): String? = when (this) {
    is IrConstructorCall -> {
        if (symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() != GUID_FQ_NAME) return null
        regularArguments().singleOrNull()?.stringConstant()?.normalizedGuid()
    }
    is IrCall -> symbol.owner.winRTGuidAnnotationValue()
    is IrGetField -> symbol.owner.annotations.firstNotNullOfOrNull { it.winRTGuidAnnotationValue() }
    else -> null
}

private fun IrConstructorCall.regularArguments(): List<IrExpression> =
    symbol.owner.parameters.mapIndexedNotNull { index, parameter ->
        arguments.getOrNull(index)?.takeIf { parameter.kind == IrParameterKind.Regular }
    }

private fun org.jetbrains.kotlin.ir.declarations.IrSimpleFunction.winRTGuidAnnotationValue(): String? =
    annotations.firstNotNullOfOrNull { it.winRTGuidAnnotationValue() }

private fun IrConstructorCall.winRTGuidAnnotationValue(): String? {
    if (symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() != WINRT_GUID_ANNOTATION_FQ_NAME) return null
    return arguments.firstNotNullOfOrNull { it?.stringConstant() }?.normalizedGuid()
}

private fun IrExpression.stringConstant(): String? = (this as? IrConst)?.value as? String

private fun String.normalizedGuid(): String? =
    runCatching { UUID.fromString(this).toString().uppercase() }.getOrNull()

internal fun guidForSignature(signature: String): String =
    if (signature.isGuidSignature()) {
        signature.guidText()
    } else {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(PINTERFACE_NAMESPACE_BYTES)
        val hash = digest.digest(signature.toByteArray(StandardCharsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 0x0F) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3F) or 0x80).toByte()
        formatNetworkGuid(hash)
    }

private fun String.isGuidSignature(): Boolean =
    length == 38 && first() == '{' && last() == '}' && guidText().normalizedGuid() != null

private fun String.guidText(): String = substring(1, lastIndex).uppercase()

private fun formatNetworkGuid(bytes: ByteArray): String = buildString(36) {
    bytes.take(16).forEachIndexed { index, byte ->
        if (index == 4 || index == 6 || index == 8 || index == 10) append('-')
        val value = byte.toInt() and 0xFF
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0F])
    }
}

private tailrec fun IrDeclarationParent.containingFile(): IrFile? = when (this) {
    is IrFile -> this
    is IrDeclaration -> parent.containingFile()
    else -> null
}

private const val GUID_GENERATOR_FQ_NAME = "io.github.composefluent.winrt.runtime.GuidGenerator"
private const val GUID_FQ_NAME = "io.github.composefluent.winrt.runtime.Guid"
private const val RAW_ADDRESS_FQ_NAME = "io.github.composefluent.winrt.runtime.RawAddress"
private const val PARAMETERIZED_INTERFACE_ID_FQ_NAME = "io.github.composefluent.winrt.runtime.ParameterizedInterfaceId"
private const val ACQUIRE_BORROWED_INTERFACE_REFERENCE_FQ_NAME =
    "io.github.composefluent.winrt.runtime.acquireBorrowedInterfaceReference"
private const val KOTLIN_STRING_FQ_NAME = "kotlin.String"
private const val KOTLIN_LONG_FQ_NAME = "kotlin.Long"
private const val WINRT_GUID_ANNOTATION_FQ_NAME = "io.github.composefluent.winrt.runtime.WinRTGuid"
private const val HEX_DIGITS = "0123456789ABCDEF"

private val PINTERFACE_NAMESPACE_BYTES = byteArrayOf(
    0x11, 0xF4.toByte(), 0x7A, 0xD5.toByte(),
    0x7B, 0x73, 0x42, 0xC0.toByte(),
    0xAB.toByte(), 0xAE.toByte(), 0x87.toByte(), 0x8B.toByte(),
    0x1E, 0x16, 0xAD.toByte(), 0xEE.toByte(),
)

private val ACQUIRE_BORROWED_INTERFACE_REFERENCE_CALLABLE_ID = CallableId(
    FqName("io.github.composefluent.winrt.runtime"),
    Name.identifier("acquireBorrowedInterfaceReference"),
)
