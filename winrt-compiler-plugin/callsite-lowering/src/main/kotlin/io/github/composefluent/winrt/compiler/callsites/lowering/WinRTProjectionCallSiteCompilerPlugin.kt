package io.github.composefluent.winrt.compiler.callsites.lowering

import io.github.composefluent.winrt.compiler.callsites.WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteDescriptor
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteHResultPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteParameterRole
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteReceiver
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultStrategy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteSlotPolicy
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteValue
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteValueKind
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.Collections
import java.util.WeakHashMap

@OptIn(ExperimentalCompilerApi::class)
class WinRTProjectionCallSiteCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.composefluent.winrt.compiler.callsites"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(WinRTProjectionCallSiteIrGenerationExtension())
    }
}

class WinRTProjectionCallSiteIrGenerationExtension : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) = lowerWinRTProjectionCallSites(moduleFragment, pluginContext)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun lowerWinRTProjectionCallSites(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
) {
        val annotatedFunctions = mutableListOf<Pair<IrSimpleFunction, IrFunctionAccessExpression>>()
        moduleFragment.acceptChildrenVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                    declaration.annotations
                        .singleOrNull(::isProjectionCallSiteAnnotation)
                        ?.let { annotation -> annotatedFunctions += declaration to annotation }
                    super.visitSimpleFunction(declaration)
                }
            },
        )
        if (annotatedFunctions.isEmpty()) return

        val symbols = RuntimeCallSiteSymbols.create(pluginContext, moduleFragment)
        for ((function, annotation) in annotatedFunctions) {
            if (function in loweredCallSiteFunctions) {
                continue
            }
            val descriptorText = (annotation.arguments.firstOrNull() as? IrConst)?.value as? String
            if (descriptorText == null) {
                pluginContext.reportError(function, "has no constant descriptor argument")
                continue
            }
            val descriptor = runCatching { WinRTProjectionCallSiteDescriptor.parse(descriptorText) }
                .getOrElse { failure ->
                    pluginContext.reportError(function, "has an invalid descriptor: ${failure.message}")
                    continue
                }
            val validationError = validateCallSiteFunction(function, descriptor)
            if (validationError != null) {
                pluginContext.reportError(function, validationError)
                continue
            }
            if (!function.hasTodoPlaceholder()) {
                pluginContext.reportError(function, "must contain a TODO() placeholder body before lowering")
                continue
            }
            if (symbols == null) {
                pluginContext.reportError(function, "cannot resolve the runtime ABI symbols required for lowering")
                continue
            }
            val lowered = symbols.lower(function, descriptor, pluginContext)
            if (!lowered) {
                pluginContext.reportError(
                    function,
                    "uses a valid but not-yet-supported call-site shape '${descriptor.encode()}'",
                )
            } else {
                loweredCallSiteFunctions += function
            }
        }
}

private fun isProjectionCallSiteAnnotation(annotation: IrFunctionAccessExpression): Boolean =
    annotation.type.classFqName?.asString() ==
        WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME

private val loweredCallSiteFunctions = Collections.synchronizedSet(
    Collections.newSetFromMap(WeakHashMap<IrSimpleFunction, Boolean>()),
)

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun validateCallSiteFunction(
    function: IrSimpleFunction,
    descriptor: WinRTProjectionCallSiteDescriptor,
): String? {
    if (function.typeParameters.isNotEmpty()) return "must not declare type parameters"
    if (descriptor.receiver != WinRTProjectionCallSiteReceiver.COM_OBJECT_REFERENCE) {
        return "uses unsupported receiver ${descriptor.receiver}"
    }
    if (descriptor.slotPolicy != WinRTProjectionCallSiteSlotPolicy.PARAMETER) {
        return "uses unsupported slot policy ${descriptor.slotPolicy}"
    }
    if (descriptor.parameters.any { parameter -> parameter.role == WinRTProjectionCallSiteParameterRole.ARRAY_MARSHALER }) {
        return "uses array-marshaler parameters that are not implemented by the bootstrap lowering"
    }
    val regularParameters = function.regularParameters()
    if (regularParameters.size != descriptor.parameters.size + 2) {
        return "must declare receiver, slot, and exactly ${descriptor.parameters.size} ABI parameters"
    }
    if (regularParameters[0].type.classFqName != WINRT_COM_OBJECT_REFERENCE_FQ_NAME) {
        return "must use ComObjectReference as its first parameter"
    }
    if (regularParameters[1].type.classFqName != KOTLIN_INT_FQ_NAME) {
        return "must use Int as its vtable-slot parameter"
    }
    descriptor.parameters.forEachIndexed { index, parameter ->
        val actualType = regularParameters[index + 2].type.classFqName
        when (parameter.role) {
            WinRTProjectionCallSiteParameterRole.ABI_ARGUMENT -> {
                val expectedType = parameter.value.kind.projectedKotlinTypeFqName()
                    ?: return "uses unsupported ABI parameter kind ${parameter.value.kind}"
                if (actualType != expectedType) {
                    return "ABI parameter $index must use $expectedType for ${parameter.value.kind}"
                }
            }
            WinRTProjectionCallSiteParameterRole.STRUCT_VALUE -> {
                if (parameter.value.kind != WinRTProjectionCallSiteValueKind.STRUCT || actualType == null) {
                    return "struct-value parameter $index must use a concrete projected struct type"
                }
            }
            WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER -> {
                if (actualType != WINRT_NATIVE_STRUCT_ADAPTER_FQ_NAME) {
                    return "struct-adapter parameter $index must use $WINRT_NATIVE_STRUCT_ADAPTER_FQ_NAME"
                }
            }
            WinRTProjectionCallSiteParameterRole.ARRAY_MARSHALER ->
                return "uses an unsupported array-marshaler parameter"
        }
    }
    validateStructCallSiteSignature(function, descriptor)?.let { return it }
    if (descriptor.result.kind == WinRTProjectionCallSiteValueKind.STRUCT) {
        if (function.returnType.classFqName == null) {
            return "must return a concrete projected struct type for ${descriptor.result.kind}"
        }
    } else {
        val expectedReturnType = descriptor.result.kind.projectedKotlinTypeFqName()
            ?: return "uses unsupported result kind ${descriptor.result.kind}"
        if (function.returnType.classFqName != expectedReturnType) {
            return "must return $expectedReturnType for ${descriptor.result.kind}"
        }
    }
    return when (descriptor.resultStrategy) {
        WinRTProjectionCallSiteResultStrategy.UNIT,
        WinRTProjectionCallSiteResultStrategy.SCALAR_OUT,
        WinRTProjectionCallSiteResultStrategy.STRING_OUT,
        WinRTProjectionCallSiteResultStrategy.REFERENCE_OUT,
        WinRTProjectionCallSiteResultStrategy.STRUCT_OUT -> null
        else -> "uses result strategy ${descriptor.resultStrategy}, which is not implemented by the bootstrap lowering"
    }
}

private class RuntimeCallSiteSymbols private constructor(
    private val comVtableInvoker: IrClassSymbol,
    private val projectionIntrinsic: IrClassSymbol,
    private val projectionIntrinsicCallUnit: IrSimpleFunctionSymbol,
    private val projectionIntrinsicCallBoolean: IrSimpleFunctionSymbol,
    private val projectionIntrinsicCallScalar: IrSimpleFunctionSymbol,
    private val projectionIntrinsicGetProjectedRuntimeClass: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicGetNullableProjectedRuntimeClass: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicGetProjectedInterface: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicGetNullableProjectedInterface: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicCallProjectedRuntimeClass: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicCallProjectedInterface: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicGetStruct: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicSetStruct: IrSimpleFunctionSymbol?,
    private val projectionIntrinsicCallStruct: IrSimpleFunctionSymbol?,
    private val comObjectReferencePointerGetter: IrSimpleFunctionSymbol,
    private val acquireScalarScratchFrame: IrSimpleFunctionSymbol,
    private val scalarScratchFrameConsumeOwnedHString: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt8: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt32: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadInt64: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadFloat: IrSimpleFunctionSymbol,
    private val scalarScratchFrameReadDouble: IrSimpleFunctionSymbol,
    private val scalarScratchFrameClose: IrSimpleFunctionSymbol,
    private val acquireHStringReferenceFrame: IrSimpleFunctionSymbol,
    private val hStringReferenceFrameHandleGetter: IrSimpleFunctionSymbol,
    private val hStringReferenceFrameClose: IrSimpleFunctionSymbol,
    private val invokeWithScalarFrame: IrSimpleFunctionSymbol,
    private val invokeWithRawAddress: IrSimpleFunctionSymbol,
    private val invokeWithInt32: IrSimpleFunctionSymbol,
    private val invokeWithUInt32: IrSimpleFunctionSymbol,
    private val invokeWithInt64: IrSimpleFunctionSymbol,
    private val hResultConstructor: IrConstructorSymbol,
    private val hResultRequireSuccess: IrSimpleFunctionSymbol,
    private val uintConstructor: IrConstructorSymbol,
    private val ulongConstructor: IrConstructorSymbol,
    private val ulongToLong: IrSimpleFunctionSymbol,
) {
    fun lower(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        pluginContext: IrPluginContext,
    ): Boolean =
        when (descriptor.resultStrategy) {
            WinRTProjectionCallSiteResultStrategy.SCALAR_OUT,
            WinRTProjectionCallSiteResultStrategy.STRING_OUT -> if (
                descriptor.parameters.isEmpty() &&
                (descriptor.result.kind == WinRTProjectionCallSiteValueKind.STRING ||
                    descriptor.result.kind in bootstrapGetterKinds)
            ) {
                lowerGetter(function, descriptor, pluginContext)
                true
            } else {
                lowerThroughProjectionIntrinsic(function, descriptor, pluginContext)
            }
            WinRTProjectionCallSiteResultStrategy.UNIT -> when {
                descriptor.isStructSetter() ->
                    lowerStructThroughProjectionIntrinsic(function, descriptor, pluginContext)
                descriptor.parameters.size == 1 &&
                    descriptor.parameters.single().value.kind in bootstrapSetterKinds ->
                    lowerSetter(function, descriptor, pluginContext)
                else -> lowerThroughProjectionIntrinsic(function, descriptor, pluginContext)
            }
            WinRTProjectionCallSiteResultStrategy.REFERENCE_OUT ->
                lowerReferenceThroughProjectionIntrinsic(function, descriptor, pluginContext)
            WinRTProjectionCallSiteResultStrategy.STRUCT_OUT ->
                lowerStructThroughProjectionIntrinsic(function, descriptor, pluginContext)
            else -> false
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerReferenceThroughProjectionIntrinsic(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        pluginContext: IrPluginContext,
    ): Boolean {
        if (descriptor.hResultPolicy != WinRTProjectionCallSiteHResultPolicy.CHECK) {
            return false
        }
        val isInspectable = descriptor.result.kind == WinRTProjectionCallSiteValueKind.INSPECTABLE_REFERENCE
        val intrinsic = (if (descriptor.parameters.isEmpty()) {
            when {
                isInspectable && descriptor.result.nullable -> projectionIntrinsicGetNullableProjectedRuntimeClass
                isInspectable -> projectionIntrinsicGetProjectedRuntimeClass
                descriptor.result.nullable -> projectionIntrinsicGetNullableProjectedInterface
                else -> projectionIntrinsicGetProjectedInterface
            }
        } else {
            if (descriptor.result.nullable) return false
            if (isInspectable) {
                projectionIntrinsicCallProjectedRuntimeClass
            } else {
                projectionIntrinsicCallProjectedInterface
            }
        }) ?: return false
        val parameters = function.regularParameters()
        val argumentShapes = descriptor.parameters.map { parameter ->
            parameter.value.kind.projectionIntrinsicShapeOrNull() ?: return false
        }
        val referenceType = if (isInspectable) {
            pluginContext.findClassSymbol(ClassId.topLevel(WINRT_INSPECTABLE_REFERENCE_FQ_NAME))?.owner?.defaultType
        } else {
            pluginContext.findClassSymbol(ClassId.topLevel(WINRT_IUNKNOWN_REFERENCE_FQ_NAME))?.owner?.defaultType
        } ?: return false
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
        val identityFunction = pluginContext.irFactory.buildFun {
            startOffset = function.startOffset
            endOffset = function.endOffset
            name = Name.special("<anonymous>")
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            returnType = referenceType
        }.apply {
            parent = function
        }
        val identityValue = identityFunction.addValueParameter("result", referenceType)
        val identityBuilder = DeclarationIrBuilder(
            pluginContext,
            identityFunction.symbol,
            function.startOffset,
            function.endOffset,
        )
        identityFunction.body = identityBuilder.irBlockBody {
            +identityBuilder.irReturn(identityBuilder.irGet(identityValue))
        }
        val identity = IrFunctionExpressionImpl(
            startOffset = function.startOffset,
            endOffset = function.endOffset,
            type = pluginContext.irBuiltIns.functionN(1).typeWith(referenceType, referenceType),
            function = identityFunction,
            origin = IrStatementOrigin.LAMBDA,
        )
        val call = builder.irCall(intrinsic, function.returnType).apply {
            typeArguments[0] = referenceType
            arguments[0] = builder.irGetObject(projectionIntrinsic)
            arguments[1] = builder.irGet(parameters[0])
            arguments[2] = builder.irGet(parameters[1])
            if (descriptor.parameters.isEmpty()) {
                arguments[3] = identity
            } else {
                arguments[3] = builder.irString(argumentShapes.joinToString(","))
                arguments[4] = identity
                val varargParameter = intrinsic.owner.regularParameters().last()
                arguments[5] = IrVarargImpl(
                    startOffset = function.startOffset,
                    endOffset = function.endOffset,
                    type = varargParameter.type,
                    varargElementType = pluginContext.irBuiltIns.anyNType,
                    elements = parameters.drop(2).map(builder::irGet),
                )
            }
        }
        function.body = builder.irBlockBody {
            +builder.irReturn(call)
        }
        return true
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerStructThroughProjectionIntrinsic(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        pluginContext: IrPluginContext,
    ): Boolean {
        if (descriptor.hResultPolicy != WinRTProjectionCallSiteHResultPolicy.CHECK) {
            return false
        }
        val parameters = function.regularParameters()
        val isSetter = descriptor.isStructSetter()
        val intrinsic: IrSimpleFunctionSymbol
        val structType = if (isSetter) {
            intrinsic = projectionIntrinsicSetStruct ?: return false
            parameters[2].type
        } else {
            if (
                descriptor.resultStrategy != WinRTProjectionCallSiteResultStrategy.STRUCT_OUT ||
                descriptor.parameters.firstOrNull()?.role != WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER ||
                descriptor.parameters.drop(1).any { parameter ->
                    parameter.role != WinRTProjectionCallSiteParameterRole.ABI_ARGUMENT
                }
            ) {
                return false
            }
            intrinsic = if (descriptor.parameters.size == 1) {
                projectionIntrinsicGetStruct
            } else {
                projectionIntrinsicCallStruct
            } ?: return false
            function.returnType
        }
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
        val call = builder.irCall(intrinsic, function.returnType).apply {
            typeArguments[0] = structType
            arguments[0] = builder.irGetObject(projectionIntrinsic)
            arguments[1] = builder.irGet(parameters[0])
            arguments[2] = builder.irGet(parameters[1])
            when {
                isSetter -> {
                    arguments[3] = builder.irGet(parameters[2])
                    arguments[4] = builder.irGet(parameters[3])
                }
                descriptor.parameters.size == 1 -> {
                    arguments[3] = builder.irGet(parameters[2])
                }
                else -> {
                    val argumentShapes = descriptor.parameters.drop(1).map { parameter ->
                        parameter.value.kind.projectionIntrinsicShapeOrNull() ?: return false
                    }
                    arguments[3] = builder.irString(argumentShapes.joinToString(","))
                    arguments[4] = builder.irGet(parameters[2])
                    val varargParameter = intrinsic.owner.regularParameters().last()
                    arguments[5] = IrVarargImpl(
                        startOffset = function.startOffset,
                        endOffset = function.endOffset,
                        type = varargParameter.type,
                        varargElementType = pluginContext.irBuiltIns.anyNType,
                        elements = parameters.drop(3).map(builder::irGet),
                    )
                }
            }
        }
        function.body = builder.irBlockBody {
            +builder.irReturn(call)
        }
        return true
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerThroughProjectionIntrinsic(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        pluginContext: IrPluginContext,
    ): Boolean {
        if (descriptor.hResultPolicy != WinRTProjectionCallSiteHResultPolicy.CHECK) {
            return false
        }
        val intrinsic = when {
            descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.UNIT -> projectionIntrinsicCallUnit
            descriptor.result.kind == WinRTProjectionCallSiteValueKind.BOOLEAN -> projectionIntrinsicCallBoolean
            descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.SCALAR_OUT ||
                descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.STRING_OUT ->
                projectionIntrinsicCallScalar
            else -> return false
        }
        val parameters = function.regularParameters()
        val argumentShapes = descriptor.parameters.map { parameter ->
            parameter.value.kind.projectionIntrinsicShapeOrNull() ?: return false
        }
        val resultShape = descriptor.result.kind.projectionIntrinsicShapeOrNull()
        val argumentShape = argumentShapes.joinToString(",")
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
        val call = builder.irCall(intrinsic, function.returnType).apply {
            arguments[0] = builder.irGetObject(projectionIntrinsic)
            arguments[1] = builder.irGet(parameters[0])
            arguments[2] = builder.irGet(parameters[1])
            var nextArgumentIndex = 3
            if (intrinsic == projectionIntrinsicCallScalar) {
                typeArguments[0] = function.returnType
                arguments[nextArgumentIndex++] = builder.irString(resultShape ?: return false)
            }
            arguments[nextArgumentIndex++] = builder.irString(argumentShape)
            val varargParameter = intrinsic.owner.regularParameters().last()
            arguments[nextArgumentIndex] = IrVarargImpl(
                startOffset = function.startOffset,
                endOffset = function.endOffset,
                type = varargParameter.type,
                varargElementType = pluginContext.irBuiltIns.anyNType,
                elements = parameters.drop(2).map(builder::irGet),
            )
        }
        function.body = builder.irBlockBody {
            +builder.irReturn(call)
        }
        return true
    }

    private fun lowerGetter(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        pluginContext: IrPluginContext,
    ) {
        val parameters = function.regularParameters()
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
        val result = builder.irBlock(resultType = function.returnType) {
            val frame = irTemporary(
                value = builder.irCall(acquireScalarScratchFrame).apply {
                    arguments[0] = builder.irBoolean(descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.IGNORE)
                },
                nameHint = "resultFrame",
                isMutable = false,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            )
            +builder.irTry(
                type = function.returnType,
                tryResult = builder.irBlock(resultType = function.returnType) {
                    val hResult = irTemporary(
                        value = invoke(
                            builder = builder,
                            symbol = invokeWithScalarFrame,
                            reference = builder.irGet(parameters[0]),
                            slot = builder.irGet(parameters[1]),
                            argument = builder.irGet(frame),
                        ),
                        nameHint = "hr",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                    if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                        +requireSuccess(builder, builder.irGet(hResult))
                    }
                    +readGetterResult(builder, descriptor.result.kind, builder.irGet(frame))
                },
                catches = emptyList(),
                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    +builder.irCall(scalarScratchFrameClose).apply {
                        arguments[0] = builder.irGet(frame)
                    }
                },
            )
        }
        function.body = builder.irBlockBody {
            +builder.irReturn(result)
        }
    }

    private fun lowerSetter(
        function: IrSimpleFunction,
        descriptor: WinRTProjectionCallSiteDescriptor,
        pluginContext: IrPluginContext,
    ): Boolean {
        val kind = descriptor.parameters.single().value.kind
        val parameters = function.regularParameters()
        val builder = DeclarationIrBuilder(pluginContext, function.symbol, function.startOffset, function.endOffset)
        function.body = builder.irBlockBody {
            if (kind == WinRTProjectionCallSiteValueKind.STRING) {
                val frame = irTemporary(
                    value = builder.irCall(acquireHStringReferenceFrame).apply {
                        arguments[0] = builder.irGet(parameters[2])
                    },
                    nameHint = "valueFrame",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = pluginContext.irBuiltIns.unitType,
                    tryResult = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        val handle = builder.irCall(hStringReferenceFrameHandleGetter).apply {
                            arguments[0] = builder.irGet(frame)
                        }
                        val hResult = invoke(
                            builder = builder,
                            symbol = invokeWithRawAddress,
                            reference = builder.irGet(parameters[0]),
                            slot = builder.irGet(parameters[1]),
                            argument = handle,
                        )
                        if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                            +requireSuccess(builder, hResult)
                        } else {
                            +hResult
                        }
                        +builder.irUnit()
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(hStringReferenceFrameClose).apply {
                            arguments[0] = builder.irGet(frame)
                        }
                    },
                )
                return@irBlockBody
            }
            val invokeSymbol = when (kind) {
                WinRTProjectionCallSiteValueKind.INT32 -> invokeWithInt32
                WinRTProjectionCallSiteValueKind.UINT32 -> invokeWithUInt32
                WinRTProjectionCallSiteValueKind.INT64,
                WinRTProjectionCallSiteValueKind.UINT64 -> invokeWithInt64
                else -> return false
            }
            val argument = if (kind == WinRTProjectionCallSiteValueKind.UINT64) {
                builder.irCall(ulongToLong).apply {
                    arguments[0] = builder.irGet(parameters[2])
                }
            } else {
                builder.irGet(parameters[2])
            }
            val hResult = invoke(
                builder = builder,
                symbol = invokeSymbol,
                reference = builder.irGet(parameters[0]),
                slot = builder.irGet(parameters[1]),
                argument = argument,
            )
            if (descriptor.hResultPolicy == WinRTProjectionCallSiteHResultPolicy.CHECK) {
                +requireSuccess(builder, hResult)
            } else {
                +hResult
            }
            +builder.irUnit()
        }
        return true
    }

    private fun invoke(
        builder: DeclarationIrBuilder,
        symbol: IrSimpleFunctionSymbol,
        reference: IrExpression,
        slot: IrExpression,
        argument: IrExpression,
    ): IrExpression =
        builder.irCall(symbol).apply {
            arguments[0] = builder.irGetObject(comVtableInvoker)
            arguments[1] = builder.irCall(comObjectReferencePointerGetter).apply {
                arguments[0] = reference
            }
            arguments[2] = slot
            arguments[3] = argument
        }

    private fun requireSuccess(
        builder: DeclarationIrBuilder,
        hResult: IrExpression,
    ): IrExpression =
        builder.irCall(hResultRequireSuccess).apply {
            arguments[0] = builder.irCall(hResultConstructor).apply {
                arguments[0] = hResult
            }
            arguments[1] = builder.irString("WinRT call")
        }

    private fun readGetterResult(
        builder: DeclarationIrBuilder,
        kind: WinRTProjectionCallSiteValueKind,
        frame: IrExpression,
    ): IrExpression =
        when (kind) {
            WinRTProjectionCallSiteValueKind.STRING ->
                builder.irCall(scalarScratchFrameConsumeOwnedHString).apply { arguments[0] = frame }
            WinRTProjectionCallSiteValueKind.BOOLEAN ->
                builder.irNotEquals(
                    builder.irCall(scalarScratchFrameReadInt8).apply { arguments[0] = frame },
                    builder.irByte(0),
                )
            WinRTProjectionCallSiteValueKind.INT32 ->
                builder.irCall(scalarScratchFrameReadInt32).apply { arguments[0] = frame }
            WinRTProjectionCallSiteValueKind.UINT32 ->
                builder.irCall(uintConstructor).apply {
                    arguments[0] = builder.irCall(scalarScratchFrameReadInt32).apply { arguments[0] = frame }
                }
            WinRTProjectionCallSiteValueKind.INT64 ->
                builder.irCall(scalarScratchFrameReadInt64).apply { arguments[0] = frame }
            WinRTProjectionCallSiteValueKind.UINT64 ->
                builder.irCall(ulongConstructor).apply {
                    arguments[0] = builder.irCall(scalarScratchFrameReadInt64).apply { arguments[0] = frame }
                }
            WinRTProjectionCallSiteValueKind.FLOAT ->
                builder.irCall(scalarScratchFrameReadFloat).apply { arguments[0] = frame }
            WinRTProjectionCallSiteValueKind.DOUBLE ->
                builder.irCall(scalarScratchFrameReadDouble).apply { arguments[0] = frame }
            else -> error("Unsupported bootstrap getter result $kind")
        }

    companion object {
        @OptIn(UnsafeDuringIrConstructionAPI::class)
        fun create(
            pluginContext: IrPluginContext,
            moduleFragment: IrModuleFragment,
        ): RuntimeCallSiteSymbols? {
            val fromFile = moduleFragment.files.firstOrNull()
            val sourceClasses = mutableMapOf<FqName, IrClassSymbol>()
            val sourceTopLevelFunctions = mutableMapOf<CallableId, MutableList<IrSimpleFunctionSymbol>>()
            moduleFragment.acceptChildrenVoid(
                object : IrVisitorVoid() {
                    override fun visitElement(element: IrElement) {
                        element.acceptChildrenVoid(this)
                    }

                    override fun visitClass(declaration: IrClass) {
                        declaration.fqNameWhenAvailable?.let { fqName ->
                            sourceClasses[fqName] = declaration.symbol
                        }
                        super.visitClass(declaration)
                    }

                    override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                        if (declaration.parent is IrFile) {
                            declaration.fqNameWhenAvailable?.let { fqName ->
                                val callableId = CallableId(
                                    fqName.parent(),
                                    fqName.shortName(),
                                )
                                sourceTopLevelFunctions.getOrPut(callableId, ::mutableListOf) += declaration.symbol
                            }
                        }
                        super.visitSimpleFunction(declaration)
                    }
                },
            )

            fun classSymbol(fqName: FqName): IrClassSymbol? =
                sourceClasses[fqName]
                    ?: pluginContext.findClassSymbol(ClassId.topLevel(fqName), fromFile)
            fun topLevelFunction(name: String): IrSimpleFunctionSymbol? {
                val callableId = CallableId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier(name))
                return sourceTopLevelFunctions[callableId]?.singleOrNull()
                    ?: pluginContext.findFunctionSymbols(callableId, fromFile).singleOrNull()
            }

            val comVtableInvoker = classSymbol(WINRT_COM_VTABLE_INVOKER_FQ_NAME) ?: return null
            val projectionIntrinsic = classSymbol(WINRT_PROJECTION_INTRINSIC_FQ_NAME) ?: return null
            val comObjectReference = classSymbol(WINRT_COM_OBJECT_REFERENCE_FQ_NAME) ?: return null
            val scalarFrame = classSymbol(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME) ?: return null
            val hStringFrame = classSymbol(WINRT_NATIVE_HSTRING_REFERENCE_FRAME_FQ_NAME) ?: return null
            val hResult = classSymbol(WINRT_HRESULT_FQ_NAME) ?: return null
            val uint = classSymbol(KOTLIN_UINT_FQ_NAME) ?: return null
            val ulong = classSymbol(KOTLIN_ULONG_FQ_NAME) ?: return null

            fun invokeWith(type: FqName): IrSimpleFunctionSymbol? =
                comVtableInvoker.functionNamedWithRegularParameterTypes(
                    name = "invokeArgs",
                    WINRT_RAW_COM_PTR_FQ_NAME,
                    KOTLIN_INT_FQ_NAME,
                    type,
                )

            return RuntimeCallSiteSymbols(
                comVtableInvoker = comVtableInvoker,
                projectionIntrinsic = projectionIntrinsic,
                projectionIntrinsicCallUnit = projectionIntrinsic.functionNamed("callUnit") ?: return null,
                projectionIntrinsicCallBoolean = projectionIntrinsic.functionNamed("callBoolean") ?: return null,
                projectionIntrinsicCallScalar = projectionIntrinsic.functionNamed("callScalar") ?: return null,
                projectionIntrinsicGetProjectedRuntimeClass =
                    projectionIntrinsic.functionNamed("getProjectedRuntimeClass"),
                projectionIntrinsicGetNullableProjectedRuntimeClass =
                    projectionIntrinsic.functionNamed("getNullableProjectedRuntimeClass"),
                projectionIntrinsicGetProjectedInterface =
                    projectionIntrinsic.functionNamed("getProjectedInterface"),
                projectionIntrinsicGetNullableProjectedInterface =
                    projectionIntrinsic.functionNamed("getNullableProjectedInterface"),
                projectionIntrinsicCallProjectedRuntimeClass =
                    projectionIntrinsic.functionNamed("callProjectedRuntimeClass"),
                projectionIntrinsicCallProjectedInterface =
                    projectionIntrinsic.functionNamed("callProjectedInterface"),
                projectionIntrinsicGetStruct = projectionIntrinsic.functionNamed("getStruct"),
                projectionIntrinsicSetStruct = projectionIntrinsic.functionNamed("setStruct"),
                projectionIntrinsicCallStruct = projectionIntrinsic.functionNamed("callStruct"),
                comObjectReferencePointerGetter = comObjectReference.propertyGetter("pointer") ?: return null,
                acquireScalarScratchFrame = topLevelFunction("acquireNativeScalarScratchFrame") ?: return null,
                scalarScratchFrameConsumeOwnedHString = scalarFrame.functionNamed("consumeOwnedHString") ?: return null,
                scalarScratchFrameReadInt8 = scalarFrame.functionNamed("readInt8") ?: return null,
                scalarScratchFrameReadInt32 = scalarFrame.functionNamed("readInt32") ?: return null,
                scalarScratchFrameReadInt64 = scalarFrame.functionNamed("readInt64") ?: return null,
                scalarScratchFrameReadFloat = scalarFrame.functionNamed("readFloat") ?: return null,
                scalarScratchFrameReadDouble = scalarFrame.functionNamed("readDouble") ?: return null,
                scalarScratchFrameClose = scalarFrame.functionNamed("close") ?: return null,
                acquireHStringReferenceFrame = topLevelFunction("acquireInitializedNativeHStringReferenceFrame") ?: return null,
                hStringReferenceFrameHandleGetter = hStringFrame.propertyGetter("handle") ?: return null,
                hStringReferenceFrameClose = hStringFrame.functionNamed("close") ?: return null,
                invokeWithScalarFrame = invokeWith(WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME) ?: return null,
                invokeWithRawAddress = invokeWith(WINRT_RAW_ADDRESS_FQ_NAME) ?: return null,
                invokeWithInt32 = invokeWith(KOTLIN_INT_FQ_NAME) ?: return null,
                invokeWithUInt32 = invokeWith(KOTLIN_UINT_FQ_NAME) ?: return null,
                invokeWithInt64 = invokeWith(KOTLIN_LONG_FQ_NAME) ?: return null,
                hResultConstructor = hResult.singleValueConstructor() ?: return null,
                hResultRequireSuccess = hResult.functionNamed("requireSuccess") ?: return null,
                uintConstructor = uint.singleValueConstructor() ?: return null,
                ulongConstructor = ulong.singleValueConstructor() ?: return null,
                ulongToLong = ulong.functionNamedWithRegularParameterCount("toLong", 0) ?: return null,
            )
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrSimpleFunction.hasTodoPlaceholder(): Boolean {
    var found = false
    body?.acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (found) return
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                if (expression.symbol.owner.fqNameWhenAvailable?.asString() == "kotlin.TODO") {
                    found = true
                } else {
                    super.visitCall(expression)
                }
            }
        },
    )
    return found
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrFunction.regularParameters() =
    parameters.filter { parameter -> parameter.kind == IrParameterKind.Regular }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun validateStructCallSiteSignature(
    function: IrSimpleFunction,
    descriptor: WinRTProjectionCallSiteDescriptor,
): String? {
    val hasStructResult = descriptor.result.kind == WinRTProjectionCallSiteValueKind.STRUCT
    val isSetter = descriptor.isStructSetter()
    if (!hasStructResult && descriptor.parameters.none { parameter ->
            parameter.role == WinRTProjectionCallSiteParameterRole.STRUCT_VALUE ||
                parameter.role == WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER
        }
    ) {
        return null
    }
    if (!hasStructResult && !isSetter) {
        return "uses unsupported struct parameter roles"
    }
    if (hasStructResult) {
        if (
            descriptor.parameters.firstOrNull()?.role != WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER ||
            descriptor.parameters.drop(1).any { parameter ->
                parameter.role != WinRTProjectionCallSiteParameterRole.ABI_ARGUMENT
            }
        ) {
            return "a struct result must be followed by one struct adapter and ordinary ABI arguments"
        }
    }

    val regularParameters = function.regularParameters()
    val concreteStructType = if (hasStructResult) function.returnType else regularParameters[2].type
    val expectedLayout = if (hasStructResult) descriptor.result else descriptor.parameters[0].value
    descriptor.parameters.forEachIndexed { index, parameter ->
        if (
            parameter.role == WinRTProjectionCallSiteParameterRole.STRUCT_VALUE ||
            parameter.role == WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER
        ) {
            if (!parameter.value.hasSameStructLayout(expectedLayout)) {
                return "struct parameter $index does not match the call-site struct layout"
            }
        }
        if (parameter.role == WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER) {
            val adapterTypeArgument = (regularParameters[index + 2].type as? IrSimpleType)
                ?.arguments
                ?.singleOrNull()
                ?.typeOrNull
            if (adapterTypeArgument?.classFqName != concreteStructType.classFqName) {
                return "struct-adapter parameter $index must target the concrete projected struct type"
            }
        }
    }
    return null
}

private fun WinRTProjectionCallSiteDescriptor.isStructSetter(): Boolean =
    resultStrategy == WinRTProjectionCallSiteResultStrategy.UNIT &&
        parameters.size == 2 &&
        parameters[0].role == WinRTProjectionCallSiteParameterRole.STRUCT_VALUE &&
        parameters[1].role == WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER

private fun WinRTProjectionCallSiteValue.hasSameStructLayout(
    other: WinRTProjectionCallSiteValue,
): Boolean =
    kind == WinRTProjectionCallSiteValueKind.STRUCT &&
        other.kind == WinRTProjectionCallSiteValueKind.STRUCT &&
        sizeBytes == other.sizeBytes &&
        alignmentBytes == other.alignmentBytes

private fun WinRTProjectionCallSiteValueKind.projectedKotlinTypeFqName(): FqName? =
    when (this) {
        WinRTProjectionCallSiteValueKind.VOID -> KOTLIN_UNIT_FQ_NAME
        WinRTProjectionCallSiteValueKind.BOOLEAN -> KOTLIN_BOOLEAN_FQ_NAME
        WinRTProjectionCallSiteValueKind.INT8 -> KOTLIN_BYTE_FQ_NAME
        WinRTProjectionCallSiteValueKind.UINT8 -> KOTLIN_UBYTE_FQ_NAME
        WinRTProjectionCallSiteValueKind.INT16 -> KOTLIN_SHORT_FQ_NAME
        WinRTProjectionCallSiteValueKind.UINT16 -> KOTLIN_USHORT_FQ_NAME
        WinRTProjectionCallSiteValueKind.INT32 -> KOTLIN_INT_FQ_NAME
        WinRTProjectionCallSiteValueKind.UINT32 -> KOTLIN_UINT_FQ_NAME
        WinRTProjectionCallSiteValueKind.INT64 -> KOTLIN_LONG_FQ_NAME
        WinRTProjectionCallSiteValueKind.UINT64 -> KOTLIN_ULONG_FQ_NAME
        WinRTProjectionCallSiteValueKind.FLOAT -> KOTLIN_FLOAT_FQ_NAME
        WinRTProjectionCallSiteValueKind.DOUBLE -> KOTLIN_DOUBLE_FQ_NAME
        WinRTProjectionCallSiteValueKind.STRING -> KOTLIN_STRING_FQ_NAME
        WinRTProjectionCallSiteValueKind.RAW_ADDRESS -> WINRT_RAW_ADDRESS_FQ_NAME
        WinRTProjectionCallSiteValueKind.RAW_COM_PTR -> WINRT_RAW_COM_PTR_FQ_NAME
        WinRTProjectionCallSiteValueKind.OBJECT -> WINRT_IWINRT_OBJECT_FQ_NAME
        WinRTProjectionCallSiteValueKind.INSPECTABLE_REFERENCE -> WINRT_INSPECTABLE_REFERENCE_FQ_NAME
        WinRTProjectionCallSiteValueKind.UNKNOWN_REFERENCE -> WINRT_IUNKNOWN_REFERENCE_FQ_NAME
        WinRTProjectionCallSiteValueKind.STRUCT,
        WinRTProjectionCallSiteValueKind.ARRAY -> null
    }

private fun WinRTProjectionCallSiteValueKind.projectionIntrinsicShapeOrNull(): String? =
    when (this) {
        WinRTProjectionCallSiteValueKind.BOOLEAN -> "Boolean"
        WinRTProjectionCallSiteValueKind.INT8 -> "Int8"
        WinRTProjectionCallSiteValueKind.UINT8 -> "UInt8"
        WinRTProjectionCallSiteValueKind.INT16 -> "Int16"
        WinRTProjectionCallSiteValueKind.UINT16 -> "UInt16"
        WinRTProjectionCallSiteValueKind.INT32 -> "Int32"
        WinRTProjectionCallSiteValueKind.UINT32 -> "UInt32"
        WinRTProjectionCallSiteValueKind.INT64 -> "Int64"
        WinRTProjectionCallSiteValueKind.UINT64 -> "UInt64"
        WinRTProjectionCallSiteValueKind.FLOAT -> "Float"
        WinRTProjectionCallSiteValueKind.DOUBLE -> "Double"
        WinRTProjectionCallSiteValueKind.STRING -> "String"
        WinRTProjectionCallSiteValueKind.RAW_ADDRESS -> "RawAddress"
        WinRTProjectionCallSiteValueKind.RAW_COM_PTR -> "RawComPtr"
        WinRTProjectionCallSiteValueKind.OBJECT -> "Object"
        else -> null
    }

@Suppress("DEPRECATION")
private fun IrPluginContext.reportError(function: IrSimpleFunction, detail: String) {
    messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "kotlin-winrt call site ${function.fqNameWhenAvailable ?: function.name} $detail.",
        null,
    )
}

private fun IrPluginContext.findClassSymbol(classId: ClassId, fromFile: IrFile? = null): IrClassSymbol? =
    fromFile?.let { file -> finderForSource(file).findClass(classId) }
        ?: finderForBuiltins().findClass(classId)

private fun IrPluginContext.findFunctionSymbols(
    callableId: CallableId,
    fromFile: IrFile? = null,
): Collection<IrSimpleFunctionSymbol> {
    val sourceSymbols = fromFile?.let { file -> finderForSource(file).findFunctions(callableId) }.orEmpty()
    return sourceSymbols.ifEmpty { finderForBuiltins().findFunctions(callableId) }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamed(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function -> function.name.asString() == name }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamedWithRegularParameterCount(
    name: String,
    count: Int,
): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == name && function.regularParameters().size == count
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamedWithRegularParameterTypes(
    name: String,
    vararg types: FqName,
): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == name &&
                function.regularParameters().map { parameter -> parameter.type.classFqName } == types.toList()
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.propertyGetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations.filterIsInstance<IrProperty>()
        .singleOrNull { property -> property.name.asString() == name }
        ?.getter
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.singleValueConstructor(): IrConstructorSymbol? =
    owner.declarations.filterIsInstance<IrConstructor>()
        .singleOrNull { constructor -> constructor.regularParameters().size == 1 }
        ?.symbol

private val bootstrapGetterKinds =
    setOf(
        WinRTProjectionCallSiteValueKind.BOOLEAN,
        WinRTProjectionCallSiteValueKind.INT32,
        WinRTProjectionCallSiteValueKind.UINT32,
        WinRTProjectionCallSiteValueKind.INT64,
        WinRTProjectionCallSiteValueKind.UINT64,
        WinRTProjectionCallSiteValueKind.FLOAT,
        WinRTProjectionCallSiteValueKind.DOUBLE,
    )

private val bootstrapSetterKinds =
    setOf(
        WinRTProjectionCallSiteValueKind.STRING,
        WinRTProjectionCallSiteValueKind.INT32,
        WinRTProjectionCallSiteValueKind.UINT32,
        WinRTProjectionCallSiteValueKind.INT64,
        WinRTProjectionCallSiteValueKind.UINT64,
    )

private val WINRT_RUNTIME_PACKAGE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime")
private val WINRT_COM_VTABLE_INVOKER_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.ComVtableInvoker")
private val WINRT_PROJECTION_INTRINSIC_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic")
private val WINRT_COM_OBJECT_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.ComObjectReference")
private val WINRT_NATIVE_SCALAR_SCRATCH_FRAME_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.NativeScalarScratchFrame")
private val WINRT_NATIVE_HSTRING_REFERENCE_FRAME_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.NativeHStringReferenceFrame")
private val WINRT_RAW_ADDRESS_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawAddress")
private val WINRT_RAW_COM_PTR_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.RawComPtr")
private val WINRT_HRESULT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.HResult")
private val WINRT_IWINRT_OBJECT_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.IWinRTObject")
// IInspectableReference is a source-level typealias and has already expanded in IR.
private val WINRT_INSPECTABLE_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.InspectableReference")
private val WINRT_IUNKNOWN_REFERENCE_FQ_NAME = FqName("io.github.composefluent.winrt.runtime.IUnknownReference")
private val WINRT_NATIVE_STRUCT_ADAPTER_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.NativeStructAdapter")
private val KOTLIN_UNIT_FQ_NAME = FqName("kotlin.Unit")
private val KOTLIN_BOOLEAN_FQ_NAME = FqName("kotlin.Boolean")
private val KOTLIN_BYTE_FQ_NAME = FqName("kotlin.Byte")
private val KOTLIN_UBYTE_FQ_NAME = FqName("kotlin.UByte")
private val KOTLIN_SHORT_FQ_NAME = FqName("kotlin.Short")
private val KOTLIN_USHORT_FQ_NAME = FqName("kotlin.UShort")
private val KOTLIN_INT_FQ_NAME = FqName("kotlin.Int")
private val KOTLIN_UINT_FQ_NAME = FqName("kotlin.UInt")
private val KOTLIN_LONG_FQ_NAME = FqName("kotlin.Long")
private val KOTLIN_ULONG_FQ_NAME = FqName("kotlin.ULong")
private val KOTLIN_FLOAT_FQ_NAME = FqName("kotlin.Float")
private val KOTLIN_DOUBLE_FQ_NAME = FqName("kotlin.Double")
private val KOTLIN_STRING_FQ_NAME = FqName("kotlin.String")
