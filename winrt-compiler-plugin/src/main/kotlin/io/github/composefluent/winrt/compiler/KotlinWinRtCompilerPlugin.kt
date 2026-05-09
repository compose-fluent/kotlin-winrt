package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "metadataIndex",
            valueDescription = "<path>",
            description = "Path to the kotlin-winrt metadata index used by authoring analysis.",
            required = false,
        ),
        CliOption(
            optionName = "typeIndexOutput",
            valueDescription = "<path>",
            description = "Path to the compiler-generated kotlin-winrt projection type index resource.",
            required = false,
        ),
        CliOption(
            optionName = "compilerSupportManifest",
            valueDescription = "<path>",
            description = "Path to the generator-emitted kotlin-winrt compiler support manifest.",
            required = false,
        ),
        CliOption(
            optionName = "compilerSupportClassOutputDirectory",
            valueDescription = "<path>",
            description = "Directory for compiler-generated kotlin-winrt support class artifacts.",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        if (option.optionName == "metadataIndex") {
            configuration.put(METADATA_INDEX_KEY, value)
        } else if (option.optionName == "typeIndexOutput") {
            configuration.put(TYPE_INDEX_OUTPUT_KEY, value)
        } else if (option.optionName == "compilerSupportManifest") {
            configuration.put(COMPILER_SUPPORT_MANIFEST_KEY, value)
        } else if (option.optionName == "compilerSupportClassOutputDirectory") {
            configuration.put(COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY, value)
        }
    }

    companion object {
        const val PLUGIN_ID: String = "io.github.composefluent.winrt.compiler"
        val METADATA_INDEX_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt metadata index")
        val TYPE_INDEX_OUTPUT_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt type index output")
        val COMPILER_SUPPORT_MANIFEST_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support manifest")
        val COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support class output directory")
    }
}

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = KotlinWinRtCommandLineProcessor.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(
            KotlinWinRtIrGenerationExtension(
                metadataIndexPath = configuration.get(KotlinWinRtCommandLineProcessor.METADATA_INDEX_KEY),
                typeIndexOutputPath = configuration.get(KotlinWinRtCommandLineProcessor.TYPE_INDEX_OUTPUT_KEY),
                compilerSupportManifestPath = configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
                compilerSupportClassOutputDirectoryPath = configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
            ),
        )
    }
}

class KotlinWinRtIrGenerationExtension(
    private val metadataIndexPath: String?,
    private val typeIndexOutputPath: String?,
    private val compilerSupportManifestPath: String?,
    private val compilerSupportClassOutputDirectoryPath: String?,
) : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val compilerSupportEntries = readCompilerSupportManifest()
        writeCompilerSupportClasses(compilerSupportEntries)
        lowerProjectionIntrinsics(moduleFragment, pluginContext)
        if (metadataIndexPath.isNullOrBlank()) {
            return
        }
        val winRtTypes = readAuthoringMetadataIndex(Path.of(metadataIndexPath))
        if (winRtTypes.isEmpty()) {
            return
        }
        val messageCollector = pluginContext.messageCollector
        val generatedSourceRoot = generatedSourceRootFromMetadataIndex(metadataIndexPath)
        val classContexts = moduleFragment.files
            .asSequence()
            .filterNot { file -> isGeneratedSourceFile(file.fileEntry.name, generatedSourceRoot) }
            .flatMap { file -> file.declarations.asSequence().flatMap { declaration -> classContextsIn(declaration).asSequence() } }
            .toList()
        writeProjectionTypeIndex(classContexts, winRtTypes)
        classContexts.forEach { context ->
            val klass = context.klass
            if (klass.visibility != DescriptorVisibilities.PUBLIC || !context.containingTypesPublic) {
                return@forEach
            }
            val authoredType = authoredTypeFor(klass, winRtTypes) ?: return@forEach
            validateAuthoredType(klass, authoredType, pluginContext.afterK2) { message ->
                messageCollector.report(CompilerMessageSeverity.ERROR, message, null)
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    private fun lowerProjectionIntrinsics(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val intrinsicClassId = ClassId.topLevel(WINRT_PROJECTION_INTRINSIC_FQ_NAME)
        val directLowerings = WinRtProjectionIntrinsicIrLowerings.create(pluginContext)
        val intrinsicFunctions = WINRT_PROJECTION_INTRINSIC_FUNCTIONS.associateWith { functionName ->
            pluginContext.referenceFunctions(CallableId(intrinsicClassId, Name.identifier(functionName)))
                .singleOrNull()
        }
            .filterValues { symbol -> symbol != null }
        if (intrinsicFunctions.isEmpty()) {
            return
        }
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    directLowerings?.lowerComVtableInvoke(
                        call,
                        pluginContext,
                        builderScope = currentScope?.scope?.scopeOwnerSymbol,
                    )
                        ?.let { return it }
                    val intrinsicName = intrinsicFunctions.entries
                        .firstOrNull { (_, symbol) -> symbol == call.symbol }
                        ?.key
                        ?: call.projectionIntrinsicFunctionName()
                        ?: return call
                    directLowerings?.lower(
                        intrinsicName,
                        call,
                        pluginContext,
                        builderScope = currentScope?.scope?.scopeOwnerSymbol,
                    )
                        ?.let { return it }
                    return call
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.projectionIntrinsicFunctionName(): String? {
        val name = symbol.owner.name.asString()
        if (name !in WINRT_PROJECTION_INTRINSIC_FUNCTIONS) {
            return null
        }
        val ownerClass = symbol.owner.parent as? IrClass
        return name.takeIf {
            ownerClass?.fqNameWhenAvailable == WINRT_PROJECTION_INTRINSIC_FQ_NAME ||
                name in WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private class WinRtProjectionIntrinsicIrLowerings private constructor(
        private val hStringCompanion: IrClassSymbol,
        private val hStringCreateReference: IrSimpleFunctionSymbol,
        private val hStringFromHandle: IrSimpleFunctionSymbol,
        private val hStringToKString: IrSimpleFunctionSymbol,
        private val hStringClose: IrSimpleFunctionSymbol,
        private val referencedHStringHandleGetter: IrSimpleFunctionSymbol,
        private val referencedHStringClose: IrSimpleFunctionSymbol,
        private val iWinRTObjectNativeObjectGetter: IrSimpleFunctionSymbol,
        private val comObjectReferencePointerGetter: IrSimpleFunctionSymbol,
        private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
        private val rawAddressValueGetter: IrSimpleFunctionSymbol,
        private val platformAbi: IrClassSymbol,
        private val platformAbiConfinedScope: IrSimpleFunctionSymbol,
        private val platformAbiFromRawComPtr: IrSimpleFunctionSymbol,
        private val platformAbiAllocatePointerSlot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateInt8Slot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateInt32Slot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateInt64Slot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateDoubleSlot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateBytes: IrSimpleFunctionSymbol,
        private val platformAbiReadPointer: IrSimpleFunctionSymbol,
        private val platformAbiReadInt8: IrSimpleFunctionSymbol,
        private val platformAbiReadInt32: IrSimpleFunctionSymbol,
        private val platformAbiReadInt64: IrSimpleFunctionSymbol,
        private val platformAbiReadFloat: IrSimpleFunctionSymbol,
        private val platformAbiReadDouble: IrSimpleFunctionSymbol,
        private val platformAbiIsNullRawAddress: IrSimpleFunctionSymbol,
        private val platformAbiToRawComPtr: IrSimpleFunctionSymbol,
        private val nativeScopeClose: IrSimpleFunctionSymbol,
        private val nativeStructAdapterLayoutGetter: IrSimpleFunctionSymbol,
        private val nativeStructAdapterRead: IrSimpleFunctionSymbol,
        private val nativeStructAdapterWrite: IrSimpleFunctionSymbol,
        private val nativeStructAdapterDisposeAbi: IrSimpleFunctionSymbol,
        private val nativeStructLayoutSizeBytesGetter: IrSimpleFunctionSymbol,
        private val marshalerFromAbiArray: IrSimpleFunctionSymbol,
        private val marshalerDisposeAbiArray: IrSimpleFunctionSymbol,
        private val emptyList: IrSimpleFunctionSymbol,
        private val iUnknownReferenceConstructor: IrConstructorSymbol,
        private val comObjectReferenceAsInspectable: IrSimpleFunctionSymbol,
        private val function1Invoke: IrSimpleFunctionSymbol,
        private val kotlinError: IrSimpleFunctionSymbol,
        private val hResultConstructor: IrConstructorSymbol,
        private val hResultRequireSuccess: IrSimpleFunctionSymbol,
        private val uintConstructor: IrConstructorSymbol?,
        private val ulongConstructor: IrConstructorSymbol?,
        private val jvmFfmSymbols: JvmFfmSymbols?,
    ) {
        fun lower(
            intrinsicName: String,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? =
            when (intrinsicName) {
                "callUnit" -> lowerDescriptorCallUnit(call, pluginContext, builderScope)
                "setString" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.String)
                "setBoolean" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Boolean)
                "setInt32" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Int32)
                "setUInt32" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.UInt32)
                "setInt64" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Int64)
                "setUInt64" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.UInt64)
                "setFloat" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Float)
                "setDouble" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Double)
                "getString" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.String)
                "getBoolean" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Boolean)
                "getInt32" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Int32)
                "getUInt32" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.UInt32)
                "getInt64" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Int64)
                "getUInt64" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.UInt64)
                "getFloat" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Float)
                "getDouble" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Double)
                "getStruct" -> lowerStructGetter(call, pluginContext, builderScope)
                "getArray" -> lowerArrayGetter(call, pluginContext, builderScope)
                "getProjectedRuntimeClass" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                    nullable = false,
                )
                "getNullableProjectedRuntimeClass" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                    nullable = true,
                )
                "getProjectedInterface" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                    nullable = false,
                )
                "getNullableProjectedInterface" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                    nullable = true,
                )
                "staticGetArray" -> lowerStaticArrayGetter(call, pluginContext, builderScope, includeProjectedObjectArgument = false)
                "staticGetArrayWithProjectedObject" ->
                    lowerStaticArrayGetter(call, pluginContext, builderScope, includeProjectedObjectArgument = true)
                "staticCallProjectedRuntimeClassWithString" -> lowerStaticStringProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                )
                "staticCallProjectedInterfaceWithString" -> lowerStaticStringProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                )
                "setStruct" -> lowerStructSetter(call, pluginContext, builderScope)
                else -> null
            }

        private fun lowerStaticStringProjectedObjectCall(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            kind: ProjectedObjectGetterKind,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val value = call.arguments.getOrNull(3) ?: return null
            val wrap = call.arguments.getOrNull(4) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val valueAbi = irTemporary(
                    value = builder.irCall(hStringCreateReference).apply {
                        arguments[0] = builder.irGetObject(hStringCompanion)
                        arguments[1] = value
                    },
                    nameHint = "valueAbi",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val nativeScope = irTemporary(
                            value = builder.irCall(platformAbiConfinedScope).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                            },
                            nameHint = "scope",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irTry(
                            type = call.type,
                            tryResult = builder.irBlock(resultType = call.type) {
                                val resultOut = irTemporary(
                                    value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                        arguments[0] = builder.irGetObject(platformAbi)
                                        arguments[1] = builder.irGet(nativeScope)
                                    },
                                    nameHint = "resultOut",
                                    isMutable = false,
                                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                )
                                +jvmFfmCallUnitBlock(
                                    symbols = symbols,
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    reference = reference,
                                    slot = slot,
                                    argumentKinds = listOf(UnitCallAbiArgumentKind.String, UnitCallAbiArgumentKind.Object),
                                    values = listOf(
                                        builder.irCall(referencedHStringHandleGetter).apply {
                                            arguments[0] = builder.irGet(valueAbi)
                                        },
                                        builder.irGet(resultOut),
                                    ),
                                )
                                val resultPointer = irTemporary(
                                    value = builder.irCall(platformAbiReadPointer).apply {
                                        arguments[0] = builder.irGetObject(platformAbi)
                                        arguments[1] = builder.irGet(resultOut)
                                    },
                                    nameHint = "resultPointer",
                                    isMutable = false,
                                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                )
                                +builder.irIfThenElse(
                                    type = call.type,
                                    condition = builder.irCall(platformAbiIsNullRawAddress).apply {
                                        arguments[0] = builder.irGetObject(platformAbi)
                                        arguments[1] = builder.irGet(resultPointer)
                                    },
                                    thenPart = builder.irCall(kotlinError).apply {
                                        arguments[0] = builder.irString("WINRT_E_NULL_ABI_RETURN")
                                    },
                                    elsePart = wrapProjectedObjectResult(
                                        builder = builder,
                                        callType = call.type,
                                        resultPointer = builder.irGet(resultPointer),
                                        wrap = wrap,
                                        kind = kind,
                                    ),
                                )
                            },
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeScopeClose).apply {
                                    arguments[0] = builder.irGet(nativeScope)
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(referencedHStringClose).apply {
                            arguments[0] = builder.irGet(valueAbi)
                        }
                    },
                )
            }
        }

        private fun lowerStaticArrayGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            includeProjectedObjectArgument: Boolean,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val projectedObject = if (includeProjectedObjectArgument) {
                call.arguments.getOrNull(3) ?: return null
            } else {
                null
            }
            val marshaler = call.arguments.getOrNull(if (includeProjectedObjectArgument) 4 else 3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val lengthOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateInt32Slot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "lengthOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val dataOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "dataOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val argumentKinds =
                            if (projectedObject == null) {
                                listOf(UnitCallAbiArgumentKind.Object, UnitCallAbiArgumentKind.Object)
                            } else {
                                listOf(
                                    UnitCallAbiArgumentKind.Object,
                                    UnitCallAbiArgumentKind.Object,
                                    UnitCallAbiArgumentKind.Object,
                                )
                            }
                        val values =
                            if (projectedObject == null) {
                                listOf(builder.irGet(lengthOut), builder.irGet(dataOut))
                            } else {
                                listOf(
                                    projectedObjectAbi(builder, projectedObject),
                                    builder.irGet(lengthOut),
                                    builder.irGet(dataOut),
                                )
                            }
                        +jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = argumentKinds,
                            values = values,
                        )
                        +decodeAbiArrayFromOutSlots(
                            builder = builder,
                            pluginContext = pluginContext,
                            callType = call.type,
                            marshaler = marshaler,
                            lengthOut = builder.irGet(lengthOut),
                            dataOut = builder.irGet(dataOut),
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerProjectedObjectGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            kind: ProjectedObjectGetterKind,
            nullable: Boolean,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val wrap = call.arguments.getOrNull(3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                            values = listOf(builder.irGet(resultOut)),
                        )
                        val resultPointer = irTemporary(
                            value = builder.irCall(platformAbiReadPointer).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(resultOut)
                            },
                            nameHint = "resultPointer",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irIfThenElse(
                            type = call.type,
                            condition = builder.irCall(platformAbiIsNullRawAddress).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(resultPointer)
                            },
                            thenPart = if (nullable) {
                                builder.irNull(call.type)
                            } else {
                                builder.irCall(kotlinError).apply {
                                    arguments[0] = builder.irString("WINRT_E_NULL_ABI_RETURN")
                                }
                            },
                            elsePart = builder.irBlock(resultType = call.type) {
                                +wrapProjectedObjectResult(
                                    builder = builder,
                                    callType = call.type,
                                    resultPointer = builder.irGet(resultPointer),
                                    wrap = wrap,
                                    kind = kind,
                                )
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun wrapProjectedObjectResult(
            builder: DeclarationIrBuilder,
            callType: org.jetbrains.kotlin.ir.types.IrType,
            resultPointer: IrExpression,
            wrap: IrExpression,
            kind: ProjectedObjectGetterKind,
        ): IrExpression =
            builder.irBlock(resultType = callType) {
                val resultReference = irTemporary(
                    value = builder.irCall(iUnknownReferenceConstructor).apply {
                        arguments[0] = builder.irCall(platformAbiToRawComPtr).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                            arguments[1] = resultPointer
                        }
                    },
                    nameHint = "resultReference",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irAs(
                    builder.irCall(function1Invoke).apply {
                        arguments[0] = wrap
                        arguments[1] = when (kind) {
                            ProjectedObjectGetterKind.RuntimeClass ->
                                builder.irCall(comObjectReferenceAsInspectable).apply {
                                    arguments[0] = builder.irGet(resultReference)
                                }
                            ProjectedObjectGetterKind.Interface -> builder.irGet(resultReference)
                        }
                    },
                    callType,
                )
            }

        private fun lowerArrayGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val marshaler = call.arguments.getOrNull(3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val lengthOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateInt32Slot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "lengthOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val dataOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "dataOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = listOf(UnitCallAbiArgumentKind.Object, UnitCallAbiArgumentKind.Object),
                            values = listOf(builder.irGet(lengthOut), builder.irGet(dataOut)),
                        )
                        val length = irTemporary(
                            value = builder.irCall(platformAbiReadInt32).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(lengthOut)
                            },
                            nameHint = "length",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val data = irTemporary(
                            value = builder.irCall(platformAbiReadPointer).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(dataOut)
                            },
                            nameHint = "data",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +decodeAbiArray(
                            builder = builder,
                            pluginContext = pluginContext,
                            callType = call.type,
                            marshaler = marshaler,
                            length = builder.irGet(length),
                            data = builder.irGet(data),
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun decodeAbiArrayFromOutSlots(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            callType: org.jetbrains.kotlin.ir.types.IrType,
            marshaler: IrExpression,
            lengthOut: IrExpression,
            dataOut: IrExpression,
        ): IrExpression =
            builder.irBlock(resultType = callType) {
                val length = irTemporary(
                    value = builder.irCall(platformAbiReadInt32).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = lengthOut
                    },
                    nameHint = "length",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val data = irTemporary(
                    value = builder.irCall(platformAbiReadPointer).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = dataOut
                    },
                    nameHint = "data",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +decodeAbiArray(
                    builder = builder,
                    pluginContext = pluginContext,
                    callType = callType,
                    marshaler = marshaler,
                    length = builder.irGet(length),
                    data = builder.irGet(data),
                )
            }

        private fun decodeAbiArray(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            callType: org.jetbrains.kotlin.ir.types.IrType,
            marshaler: IrExpression,
            length: IrExpression,
            data: IrExpression,
        ): IrExpression =
            builder.irTry(
                type = callType,
                tryResult = builder.irBlock(resultType = callType) {
                    val decoded = irTemporary(
                        value = builder.irCall(marshalerFromAbiArray).apply {
                            arguments[0] = marshaler
                            arguments[1] = length
                            arguments[2] = data
                        },
                        nameHint = "decoded",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                    +builder.irIfNull(
                        type = callType,
                        subject = builder.irGet(decoded),
                        thenPart = builder.irAs(
                            builder.irCall(emptyList).apply {
                                typeArguments[0] = pluginContext.irBuiltIns.anyNType
                            },
                            callType,
                        ),
                        elsePart = builder.irAs(builder.irGet(decoded), callType),
                    )
                },
                catches = emptyList(),
                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    +builder.irCall(marshalerDisposeAbiArray).apply {
                        arguments[0] = marshaler
                        arguments[1] = length
                        arguments[2] = data
                    }
                },
            )

        private fun lowerStructSetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val value = call.arguments.getOrNull(3) ?: return null
            val adapter = call.arguments.getOrNull(4) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = pluginContext.irBuiltIns.unitType,
                    tryResult = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        val valueAbi = irTemporary(
                            value = builder.irCall(platformAbiAllocateBytes).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                                arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                    arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                        arguments[0] = adapter
                                    }
                                }
                            },
                            nameHint = "valueAbi",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irCall(nativeStructAdapterWrite).apply {
                            arguments[0] = adapter
                            arguments[1] = value
                            arguments[2] = builder.irGet(valueAbi)
                        }
                        +builder.irTry(
                            type = pluginContext.irBuiltIns.unitType,
                            tryResult = jvmFfmCallUnitBlock(
                                symbols = symbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                                values = listOf(builder.irGet(valueAbi)),
                            ),
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                    arguments[0] = adapter
                                    arguments[1] = builder.irGet(valueAbi)
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerStructGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val adapter = call.arguments.getOrNull(3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateBytes).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                                arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                    arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                        arguments[0] = adapter
                                    }
                                }
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irTry(
                            type = call.type,
                            tryResult = builder.irBlock(resultType = call.type) {
                                +jvmFfmCallUnitBlock(
                                    symbols = symbols,
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    reference = reference,
                                    slot = slot,
                                    argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                                    values = listOf(builder.irGet(resultOut)),
                                )
                                +builder.irAs(
                                    builder.irCall(nativeStructAdapterRead).apply {
                                        arguments[0] = adapter
                                        arguments[1] = builder.irGet(resultOut)
                                    },
                                    call.type,
                                )
                            },
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                    arguments[0] = adapter
                                    arguments[1] = builder.irGet(resultOut)
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerNoArgumentGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            returnKind: NoArgumentGetterReturnKind,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            if ((returnKind == NoArgumentGetterReturnKind.UInt32 && uintConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt64 && ulongConstructor == null)
            ) {
                return null
            }
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = allocateGetterResultSlot(builder, returnKind, builder.irGet(nativeScope)),
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                            values = listOf(builder.irGet(resultOut)),
                        )
                        +readGetterResult(builder, pluginContext, returnKind, builder.irGet(resultOut))
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun allocateGetterResultSlot(
            builder: DeclarationIrBuilder,
            returnKind: NoArgumentGetterReturnKind,
            nativeScope: IrExpression,
        ): IrExpression =
            builder.irCall(
                when (returnKind) {
                    NoArgumentGetterReturnKind.String -> platformAbiAllocatePointerSlot
                    NoArgumentGetterReturnKind.Boolean -> platformAbiAllocateInt8Slot
                    NoArgumentGetterReturnKind.Int32,
                    NoArgumentGetterReturnKind.UInt32 -> platformAbiAllocateInt32Slot
                    NoArgumentGetterReturnKind.Int64,
                    NoArgumentGetterReturnKind.UInt64 -> platformAbiAllocateInt64Slot
                    NoArgumentGetterReturnKind.Float -> platformAbiAllocateBytes
                    NoArgumentGetterReturnKind.Double -> platformAbiAllocateDoubleSlot
                },
            ).apply {
                arguments[0] = builder.irGetObject(platformAbi)
                arguments[1] = nativeScope
                if (returnKind == NoArgumentGetterReturnKind.Float) {
                    arguments[2] = builder.irLong(4L)
                }
            }

        private fun readGetterResult(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            returnKind: NoArgumentGetterReturnKind,
            resultOut: IrExpression,
        ): IrExpression =
            when (returnKind) {
                NoArgumentGetterReturnKind.String -> readHStringGetterResult(builder, pluginContext, resultOut)
                NoArgumentGetterReturnKind.Boolean -> builder.irNotEquals(
                    builder.irCall(platformAbiReadInt8).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    },
                    builder.irByte(0),
                )
                NoArgumentGetterReturnKind.Int32 -> builder.irCall(platformAbiReadInt32).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt32 -> builder.irCall(requireNotNull(uintConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt32).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.Int64 -> builder.irCall(platformAbiReadInt64).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt64 -> builder.irCall(requireNotNull(ulongConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt64).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.Float -> builder.irCall(platformAbiReadFloat).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.Double -> builder.irCall(platformAbiReadDouble).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
            }

        private fun readHStringGetterResult(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            resultOut: IrExpression,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.stringType) {
                val value = irTemporary(
                    value = builder.irCall(hStringFromHandle).apply {
                        arguments[0] = builder.irGetObject(hStringCompanion)
                        arguments[1] = builder.irCall(platformAbiReadPointer).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                            arguments[1] = resultOut
                        }
                        arguments[2] = builder.irBoolean(true)
                    },
                    nameHint = "result",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = pluginContext.irBuiltIns.stringType,
                    tryResult = builder.irCall(hStringToKString).apply {
                        arguments[0] = builder.irGet(value)
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(hStringClose).apply {
                            arguments[0] = builder.irGet(value)
                        }
                    },
                )
            }

        private fun lowerOneArgumentUnit(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            argumentKind: UnitCallAbiArgumentKind,
        ): IrExpression? {
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val value = call.arguments.getOrNull(3) ?: return null
            return jvmFfmSymbols?.let { symbols ->
                lowerJvmFfmCallUnitWithArguments(
                    symbols = symbols,
                    call = call,
                    pluginContext = pluginContext,
                    builderScope = builderScope,
                    reference = reference,
                    slot = slot,
                    values = listOf(value),
                    argumentKinds = listOf(argumentKind),
                )
            }
        }

        private fun booleanAbiValue(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            value: IrExpression,
        ): IrExpression =
            builder.irIfThenElse(
                type = pluginContext.irBuiltIns.byteType,
                condition = value,
                thenPart = builder.irByte(1),
                elsePart = builder.irByte(0),
            )

        private fun lowerDescriptorCallUnit(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            val values = call.varargValues(argumentKinds.size) ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            return jvmFfmSymbols?.let { symbols ->
                lowerJvmFfmCallUnitWithArguments(
                    symbols = symbols,
                    call = call,
                    pluginContext = pluginContext,
                    builderScope = builderScope,
                    reference = reference,
                    slot = slot,
                    values = values,
                    argumentKinds = argumentKinds,
                )
            }
        }

        private fun lowerJvmFfmCallUnitWithArguments(
            symbols: JvmFfmSymbols,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            reference: IrExpression,
            slot: IrExpression,
            values: List<IrExpression>,
            argumentKinds: List<UnitCallAbiArgumentKind>,
        ): IrExpression? {
            val scope = builderScope ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                fun abiValueFor(index: Int, kind: UnitCallAbiArgumentKind): IrExpression {
                    val value = values[index]
                    return when (kind) {
                        UnitCallAbiArgumentKind.RawAddress,
                        UnitCallAbiArgumentKind.RawComPtr,
                        UnitCallAbiArgumentKind.Byte,
                        UnitCallAbiArgumentKind.Int32,
                        UnitCallAbiArgumentKind.UInt32,
                        UnitCallAbiArgumentKind.Int64,
                        UnitCallAbiArgumentKind.UInt64,
                        UnitCallAbiArgumentKind.Float -> value
                        UnitCallAbiArgumentKind.Double -> value
                        UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                        UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                        UnitCallAbiArgumentKind.String -> {
                            val stringAbi = irTemporary(
                                value = builder.irCall(hStringCreateReference).apply {
                                    arguments[0] = builder.irGetObject(hStringCompanion)
                                    arguments[1] = value
                                },
                                nameHint = "value${index}Abi",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            stringAbis += stringAbi
                            builder.irCall(referencedHStringHandleGetter).apply {
                                arguments[0] = builder.irGet(stringAbi)
                            }
                        }
                    }
                }

                val abiValues = argumentKinds.mapIndexed(::abiValueFor)
                val callBlock = jvmFfmCallUnitBlock(
                    symbols = symbols,
                    builder = builder,
                    pluginContext = pluginContext,
                    reference = reference,
                    slot = slot,
                    argumentKinds = argumentKinds,
                    values = abiValues,
                )
                if (stringAbis.isEmpty()) {
                    +callBlock
                } else {
                    +builder.irTry(
                        type = pluginContext.irBuiltIns.unitType,
                        tryResult = callBlock,
                        catches = emptyList(),
                        finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            stringAbis.forEach { stringAbi ->
                                +builder.irCall(referencedHStringClose).apply {
                                    arguments[0] = builder.irGet(stringAbi)
                                }
                            }
                        },
                    )
                }
            }
        }

        fun lowerComVtableInvoke(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val callShape = call.comVtableCallShape() ?: return null
            val symbols = jvmFfmSymbols ?: return null
            if (!symbols.canLower(callShape.argumentKinds)) {
                return null
            }
            val scope = builderScope ?: return null
            val instance = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
                val hResultValue = irTemporary(
                    value = jvmFfmCallRaw(
                        symbols = symbols,
                        builder = builder,
                        pluginContext = pluginContext,
                        instancePointer = instance,
                        slot = slot,
                        argumentKinds = callShape.argumentKinds,
                        values = callShape.values,
                    ),
                    nameHint = "hr",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irGet(hResultValue)
            }
        }

        @OptIn(UnsafeDuringIrConstructionAPI::class)
        private fun IrCall.comVtableCallShape(): ComVtableCallShape? {
            val ownerClass = symbol.owner.parent as? IrClass ?: return null
            if (ownerClass.fqNameWhenAvailable != WINRT_COM_VTABLE_INVOKER_FQ_NAME) {
                return null
            }
            val regularParameters = symbol.owner.parameters.filter { parameter ->
                parameter.kind == IrParameterKind.Regular
            }
            return when (symbol.owner.name.asString()) {
                "invoke" -> ComVtableCallShape(emptyList(), emptyList()).takeIf {
                    regularParameters.size == 2
                }
                "invokeArgs" -> {
                    if (regularParameters.size < 3) {
                        return null
                    }
                    val argumentKinds = regularParameters.drop(2).map { parameter ->
                        parameter.type.comVtableAbiArgumentKind() ?: return null
                    }
                    val values = mutableListOf<IrExpression>()
                    for (index in argumentKinds.indices) {
                        values += arguments.getOrNull(index + 3) ?: return null
                    }
                    ComVtableCallShape(argumentKinds, values)
                }
                "invokeGenericArgs" -> {
                    if (regularParameters.size != 3) {
                        return null
                    }
                    val vararg = arguments.getOrNull(3) as? IrVararg ?: return null
                    val values = vararg.elements.map { element ->
                        element as? IrExpression ?: return null
                    }
                    val argumentKinds = values.map { value ->
                        value.type.comVtableAbiArgumentKind() ?: return null
                    }
                    ComVtableCallShape(argumentKinds, values)
                }
                else -> null
            }
        }

        private data class ComVtableCallShape(
            val argumentKinds: List<UnitCallAbiArgumentKind>,
            val values: List<IrExpression>,
        )

        private fun org.jetbrains.kotlin.ir.types.IrType.comVtableAbiArgumentKind(): UnitCallAbiArgumentKind? =
            when (classFqName) {
                WINRT_RAW_ADDRESS_FQ_NAME -> UnitCallAbiArgumentKind.RawAddress
                WINRT_RAW_COM_PTR_FQ_NAME -> UnitCallAbiArgumentKind.RawComPtr
                KOTLIN_BYTE_FQ_NAME -> UnitCallAbiArgumentKind.Byte
                KOTLIN_INT_FQ_NAME -> UnitCallAbiArgumentKind.Int32
                KOTLIN_UINT_FQ_NAME -> UnitCallAbiArgumentKind.UInt32
                KOTLIN_LONG_FQ_NAME -> UnitCallAbiArgumentKind.Int64
                KOTLIN_ULONG_FQ_NAME -> UnitCallAbiArgumentKind.UInt64
                KOTLIN_FLOAT_FQ_NAME -> UnitCallAbiArgumentKind.Float
                KOTLIN_DOUBLE_FQ_NAME -> UnitCallAbiArgumentKind.Double
                else -> null
            }

        private fun jvmFfmCallUnitBlock(
            symbols: JvmFfmSymbols,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            reference: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val hResultValue = irTemporary(
                    value = jvmFfmCallRaw(
                        symbols = symbols,
                        builder = builder,
                        pluginContext = pluginContext,
                        instancePointer = referencePointer(builder, reference),
                        slot = slot,
                        argumentKinds = argumentKinds,
                        values = values,
                    ),
                    nameHint = "hr",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irCall(hResultRequireSuccess).apply {
                    arguments[0] = builder.irCall(hResultConstructor).apply {
                        arguments[0] = builder.irGet(hResultValue)
                    }
                    arguments[1] = builder.irString("WinRT call")
                }
                +builder.irUnit()
            }

        private fun jvmFfmCallRaw(
            symbols: JvmFfmSymbols,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            instancePointer: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
                val instanceSegment = irTemporary(
                    value = symbols.segmentFromRawComPtr(builder, instancePointer),
                    nameHint = "instanceSegment",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val function = irTemporary(
                    value = symbols.vtableEntry(builder, builder.irGet(instanceSegment), slot),
                    nameHint = "function",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val handle = irTemporary(
                    value = symbols.downcallHandle(builder, argumentKinds),
                    nameHint = "handle",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irAs(
                    builder.irCall(symbols.methodHandleInvoke).apply {
                        arguments[0] = builder.irGet(handle)
                        arguments[1] = builder.irVararg(
                            pluginContext.irBuiltIns.anyNType,
                            listOf(builder.irGet(function), builder.irGet(instanceSegment)) +
                                values.mapIndexed { index, value ->
                                    symbols.jvmCarrier(builder, argumentKinds[index], value)
                                },
                        )
                    },
                    pluginContext.irBuiltIns.intType,
                )
            }

        private fun referencePointer(
            builder: DeclarationIrBuilder,
            reference: IrExpression,
        ): IrExpression =
            builder.irCall(comObjectReferencePointerGetter).apply {
                arguments[0] = reference
            }

        private fun IrExpression.stringConstantValue(): String? =
            (this as? IrConst)?.value as? String

        private fun IrCall.varargValues(expectedCount: Int): List<IrExpression>? {
            val vararg = arguments.getOrNull(4) as? IrVararg ?: return null
            val values = vararg.elements.map { element -> element as? IrExpression ?: return null }
            return values.takeIf { it.size == expectedCount }
        }

        private enum class UnitCallAbiArgumentKind {
            RawAddress,
            RawComPtr,
            Byte,
            Int32,
            UInt32,
            Int64,
            UInt64,
            Float,
            Double,
            Boolean,
            String,
            Object,
        }

        private enum class NoArgumentGetterReturnKind {
            String,
            Boolean,
            Int32,
            UInt32,
            Int64,
            UInt64,
            Float,
            Double,
        }

        private enum class ProjectedObjectGetterKind {
            RuntimeClass,
            Interface,
        }

        private object UnitCallAbiShape {
            fun parse(value: String): List<UnitCallAbiArgumentKind>? {
                if (value.isBlank()) {
                    return null
                }
                return value.split(',').map { token ->
                    when (token) {
                        "Int32" -> UnitCallAbiArgumentKind.Int32
                        "UInt32" -> UnitCallAbiArgumentKind.UInt32
                        "Int64" -> UnitCallAbiArgumentKind.Int64
                        "UInt64" -> UnitCallAbiArgumentKind.UInt64
                        "Float" -> UnitCallAbiArgumentKind.Float
                        "Double" -> UnitCallAbiArgumentKind.Double
                        "Boolean" -> UnitCallAbiArgumentKind.Boolean
                        "String" -> UnitCallAbiArgumentKind.String
                        "Object" -> UnitCallAbiArgumentKind.Object
                        else -> return null
                    }
                }
            }
        }

        private fun projectedObjectAbi(
            builder: DeclarationIrBuilder,
            value: IrExpression,
        ): IrExpression =
            builder.irCall(platformAbiFromRawComPtr).apply {
                arguments[0] = builder.irGetObject(platformAbi)
                arguments[1] = builder.irCall(comObjectReferencePointerGetter).apply {
                    arguments[0] = builder.irCall(iWinRTObjectNativeObjectGetter).apply {
                        arguments[0] = value
                    }
                }
            }

        companion object {
            fun create(pluginContext: IrPluginContext): WinRtProjectionIntrinsicIrLowerings? {
                val hString = pluginContext.referenceClass(WINRT_HSTRING_CLASS_ID)
                    ?: return null
                val hStringCompanion = hString.owner.declarations
                    .filterIsInstance<IrClass>()
                    .singleOrNull { it.name.asString() == "Companion" }
                    ?.symbol
                    ?: return null
                val hStringCreateReference = hStringCompanion.functionNamed("createReference") ?: return null
                val hStringFromHandle = hStringCompanion.functionNamed("fromHandle") ?: return null
                val hStringToKString = hString.functionNamed("toKString") ?: return null
                val hStringClose = hString.functionNamed("close") ?: return null
                val referencedHString = pluginContext.referenceClass(WINRT_REFERENCED_HSTRING_CLASS_ID)
                    ?: return null
                val referencedHStringHandleGetter = referencedHString.propertyGetter("handle") ?: return null
                val referencedHStringClose = referencedHString.functionNamed("close") ?: return null
                val iWinRTObject = pluginContext.referenceClass(WINRT_IWINRT_OBJECT_CLASS_ID)
                    ?: return null
                val iWinRTObjectNativeObjectGetter = iWinRTObject.propertyGetter("nativeObject") ?: return null
                val comObjectReference = pluginContext.referenceClass(WINRT_COM_OBJECT_REFERENCE_CLASS_ID)
                    ?: return null
                val comObjectReferencePointerGetter = comObjectReference.propertyGetter("pointer") ?: return null
                val rawComPtr = pluginContext.referenceClass(WINRT_RAW_COM_PTR_CLASS_ID)
                    ?: return null
                val rawComPtrValueGetter = rawComPtr.propertyGetter("value") ?: return null
                val rawAddress = pluginContext.referenceClass(WINRT_RAW_ADDRESS_CLASS_ID)
                    ?: return null
                val rawAddressValueGetter = rawAddress.propertyGetter("value") ?: return null
                val platformAbi = pluginContext.referenceClass(WINRT_PLATFORM_ABI_CLASS_ID)
                    ?: return null
                val nativeScope = pluginContext.referenceClass(WINRT_NATIVE_SCOPE_CLASS_ID)
                    ?: return null
                val platformAbiConfinedScope = platformAbi.functionNamed("confinedScope") ?: return null
                val platformAbiFromRawComPtr = platformAbi.functionNamed("fromRawComPtr") ?: return null
                val platformAbiAllocatePointerSlot = platformAbi.functionNamed("allocatePointerSlot") ?: return null
                val platformAbiAllocateInt8Slot = platformAbi.functionNamed("allocateInt8Slot") ?: return null
                val platformAbiAllocateInt32Slot = platformAbi.functionNamed("allocateInt32Slot") ?: return null
                val platformAbiAllocateInt64Slot = platformAbi.functionNamed("allocateInt64Slot") ?: return null
                val platformAbiAllocateDoubleSlot = platformAbi.functionNamed("allocateDoubleSlot") ?: return null
                val platformAbiAllocateBytes = platformAbi.functionNamedWithRegularParameterCount("allocateBytes", 2) ?: return null
                val platformAbiReadPointer = platformAbi.functionNamed("readPointer") ?: return null
                val platformAbiReadInt8 = platformAbi.functionNamed("readInt8") ?: return null
                val platformAbiReadInt32 = platformAbi.functionNamed("readInt32") ?: return null
                val platformAbiReadInt64 = platformAbi.functionNamed("readInt64") ?: return null
                val platformAbiReadFloat = platformAbi.functionNamed("readFloat") ?: return null
                val platformAbiReadDouble = platformAbi.functionNamed("readDouble") ?: return null
                val platformAbiIsNullRawAddress =
                    platformAbi.functionNamedWithValueParameterTypes("isNull", WINRT_RAW_ADDRESS_FQ_NAME) ?: return null
                val platformAbiToRawComPtr =
                    platformAbi.functionNamedWithValueParameterTypes("toRawComPtr", WINRT_RAW_ADDRESS_FQ_NAME) ?: return null
                val nativeScopeClose = nativeScope.functionNamed("close") ?: return null
                val nativeStructAdapter = pluginContext.referenceClass(WINRT_NATIVE_STRUCT_ADAPTER_CLASS_ID)
                    ?: return null
                val nativeStructLayout = pluginContext.referenceClass(WINRT_NATIVE_STRUCT_LAYOUT_CLASS_ID)
                    ?: return null
                val nativeStructAdapterLayoutGetter = nativeStructAdapter.propertyGetter("layout") ?: return null
                val nativeStructAdapterRead = nativeStructAdapter.functionNamed("read") ?: return null
                val nativeStructAdapterWrite = nativeStructAdapter.functionNamed("write") ?: return null
                val nativeStructAdapterDisposeAbi = nativeStructAdapter.functionNamed("disposeAbi") ?: return null
                val nativeStructLayoutSizeBytesGetter = nativeStructLayout.propertyGetter("sizeBytes") ?: return null
                val marshaler = pluginContext.referenceClass(WINRT_MARSHALER_CLASS_ID)
                    ?: return null
                val marshalerFromAbiArray = marshaler.functionNamed("fromAbiArray") ?: return null
                val marshalerDisposeAbiArray = marshaler.functionNamed("disposeAbiArray") ?: return null
                val emptyList = pluginContext.referenceFunctions(
                    CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyList")),
                ).singleOrNull() ?: return null
                val iUnknownReference = pluginContext.referenceClass(WINRT_IUNKNOWN_REFERENCE_CLASS_ID)
                    ?: return null
                val iUnknownReferenceConstructor =
                    iUnknownReference.constructorWithRegularParameterCount(5) ?: return null
                val comObjectReferenceAsInspectable = comObjectReference.functionNamed("asInspectable") ?: return null
                val function1 = pluginContext.referenceClass(KOTLIN_FUNCTION1_CLASS_ID)
                    ?: return null
                val function1Invoke = function1.functionNamed("invoke") ?: return null
                val kotlinError = pluginContext.referenceFunctions(
                    CallableId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("error")),
                ).singleOrNull() ?: return null
                val hResult = pluginContext.referenceClass(WINRT_HRESULT_CLASS_ID)
                    ?: return null
                val hResultConstructor = hResult.owner.declarations
                    .filterIsInstance<IrConstructor>()
                    .singleOrNull()
                    ?.symbol
                    ?: return null
                val hResultRequireSuccess = hResult.functionNamed("requireSuccess") ?: return null
                val jvmFfmSymbols = JvmFfmSymbols.create(
                    pluginContext = pluginContext,
                    rawComPtrValueGetter = rawComPtrValueGetter,
                    rawAddressValueGetter = rawAddressValueGetter,
                )
                val uintConstructor = pluginContext.referenceClass(KOTLIN_UINT_CLASS_ID)
                    ?.singleValueConstructor()
                val ulongConstructor = pluginContext.referenceClass(KOTLIN_ULONG_CLASS_ID)
                    ?.singleValueConstructor()
                return WinRtProjectionIntrinsicIrLowerings(
                    hStringCompanion = hStringCompanion,
                    hStringCreateReference = hStringCreateReference,
                    hStringFromHandle = hStringFromHandle,
                    hStringToKString = hStringToKString,
                    hStringClose = hStringClose,
                    referencedHStringHandleGetter = referencedHStringHandleGetter,
                    referencedHStringClose = referencedHStringClose,
                    iWinRTObjectNativeObjectGetter = iWinRTObjectNativeObjectGetter,
                    comObjectReferencePointerGetter = comObjectReferencePointerGetter,
                    rawComPtrValueGetter = rawComPtrValueGetter,
                    rawAddressValueGetter = rawAddressValueGetter,
                    platformAbi = platformAbi,
                    platformAbiConfinedScope = platformAbiConfinedScope,
                    platformAbiFromRawComPtr = platformAbiFromRawComPtr,
                    platformAbiAllocatePointerSlot = platformAbiAllocatePointerSlot,
                    platformAbiAllocateInt8Slot = platformAbiAllocateInt8Slot,
                    platformAbiAllocateInt32Slot = platformAbiAllocateInt32Slot,
                    platformAbiAllocateInt64Slot = platformAbiAllocateInt64Slot,
                    platformAbiAllocateDoubleSlot = platformAbiAllocateDoubleSlot,
                    platformAbiAllocateBytes = platformAbiAllocateBytes,
                    platformAbiReadPointer = platformAbiReadPointer,
                    platformAbiReadInt8 = platformAbiReadInt8,
                    platformAbiReadInt32 = platformAbiReadInt32,
                    platformAbiReadInt64 = platformAbiReadInt64,
                    platformAbiReadFloat = platformAbiReadFloat,
                    platformAbiReadDouble = platformAbiReadDouble,
                    platformAbiIsNullRawAddress = platformAbiIsNullRawAddress,
                    platformAbiToRawComPtr = platformAbiToRawComPtr,
                    nativeScopeClose = nativeScopeClose,
                    nativeStructAdapterLayoutGetter = nativeStructAdapterLayoutGetter,
                    nativeStructAdapterRead = nativeStructAdapterRead,
                    nativeStructAdapterWrite = nativeStructAdapterWrite,
                    nativeStructAdapterDisposeAbi = nativeStructAdapterDisposeAbi,
                    nativeStructLayoutSizeBytesGetter = nativeStructLayoutSizeBytesGetter,
                    marshalerFromAbiArray = marshalerFromAbiArray,
                    marshalerDisposeAbiArray = marshalerDisposeAbiArray,
                    emptyList = emptyList,
                    iUnknownReferenceConstructor = iUnknownReferenceConstructor,
                    comObjectReferenceAsInspectable = comObjectReferenceAsInspectable,
                    function1Invoke = function1Invoke,
                    kotlinError = kotlinError,
                    hResultConstructor = hResultConstructor,
                    hResultRequireSuccess = hResultRequireSuccess,
                    uintConstructor = uintConstructor,
                    ulongConstructor = ulongConstructor,
                    jvmFfmSymbols = jvmFfmSymbols,
                )
            }

        }

        private class JvmFfmSymbols private constructor(
            private val memoryLayoutType: org.jetbrains.kotlin.ir.types.IrType,
            private val memorySegmentType: org.jetbrains.kotlin.ir.types.IrType,
            val linkerOptionType: org.jetbrains.kotlin.ir.types.IrType,
            val linkerNativeLinker: IrSimpleFunctionSymbol,
            val linkerDowncallHandle: IrSimpleFunctionSymbol,
            private val functionDescriptorOf: IrSimpleFunctionSymbol,
            private val memorySegmentOfAddress: IrSimpleFunctionSymbol,
            private val memorySegmentReinterpret: IrSimpleFunctionSymbol,
            private val memorySegmentGetAddress: IrSimpleFunctionSymbol,
            private val memorySegmentGetAtIndexAddress: IrSimpleFunctionSymbol,
            val methodHandleInvoke: IrSimpleFunctionSymbol,
            private val valueLayoutAddress: JvmStaticLayoutValue,
            private val valueLayoutJavaInt: JvmStaticLayoutValue,
            private val valueLayoutJavaByte: JvmStaticLayoutValue,
            private val valueLayoutJavaLong: JvmStaticLayoutValue,
            private val valueLayoutJavaFloat: JvmStaticLayoutValue,
            private val valueLayoutJavaDouble: JvmStaticLayoutValue,
            private val intToLong: IrSimpleFunctionSymbol,
            private val uintToInt: IrSimpleFunctionSymbol?,
            private val ulongToLong: IrSimpleFunctionSymbol?,
            private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
            private val rawAddressValueGetter: IrSimpleFunctionSymbol,
        ) {
            fun segmentFromRawComPtr(
                builder: DeclarationIrBuilder,
                pointer: IrExpression,
            ): IrExpression =
                builder.irCall(memorySegmentReinterpret).apply {
                    arguments[0] = builder.irCall(memorySegmentOfAddress).apply {
                        arguments[0] = builder.irCall(rawComPtrValueGetter).apply {
                            arguments[0] = pointer
                        }
                    }
                    arguments[1] = builder.irLong(Long.MAX_VALUE)
                }

            fun segmentFromRawAddress(
                builder: DeclarationIrBuilder,
                address: IrExpression,
            ): IrExpression =
                builder.irCall(memorySegmentReinterpret).apply {
                    arguments[0] = builder.irCall(memorySegmentOfAddress).apply {
                        arguments[0] = builder.irCall(rawAddressValueGetter).apply {
                            arguments[0] = address
                        }
                    }
                    arguments[1] = builder.irLong(Long.MAX_VALUE)
                }

            fun functionDescriptor(
                builder: DeclarationIrBuilder,
                argumentKinds: List<UnitCallAbiArgumentKind>,
            ): IrExpression =
                builder.irCall(functionDescriptorOf).apply {
                    arguments[0] = valueLayoutJavaInt.get(builder)
                    arguments[1] = builder.irVararg(
                        memoryLayoutType,
                        listOf(valueLayoutAddress.get(builder)) +
                            argumentKinds.map { kind -> kind.valueLayoutValue().get(builder) },
                    )
                }

            fun downcallHandle(
                builder: DeclarationIrBuilder,
                argumentKinds: List<UnitCallAbiArgumentKind>,
            ): IrExpression =
                builder.irCall(linkerDowncallHandle).apply {
                    arguments[0] = builder.irCall(linkerNativeLinker)
                    arguments[1] = functionDescriptor(builder, argumentKinds)
                    arguments[2] = builder.irVararg(linkerOptionType, emptyList())
                }

            fun canLower(argumentKinds: List<UnitCallAbiArgumentKind>): Boolean =
                argumentKinds.all { kind ->
                    when (kind) {
                        UnitCallAbiArgumentKind.UInt32 -> uintToInt != null
                        UnitCallAbiArgumentKind.UInt64 -> ulongToLong != null
                        else -> true
                    }
                }

            fun vtableEntry(
                builder: DeclarationIrBuilder,
                instanceSegment: IrExpression,
                slot: IrExpression,
            ): IrExpression {
                val objectMemory = builder.irCall(memorySegmentReinterpret).apply {
                    arguments[0] = instanceSegment
                    arguments[1] = builder.irLong(8L)
                }
                val vtable = builder.irCall(memorySegmentGetAddress).apply {
                    arguments[0] = objectMemory
                    arguments[1] = valueLayoutAddress.get(builder)
                    arguments[2] = builder.irLong(0L)
                }
                return builder.irCall(memorySegmentGetAtIndexAddress).apply {
                    arguments[0] = builder.irCall(memorySegmentReinterpret).apply {
                        arguments[0] = vtable
                        arguments[1] = builder.irLong(Long.MAX_VALUE)
                    }
                    arguments[1] = valueLayoutAddress.get(builder)
                    arguments[2] = builder.irCall(intToLong).apply {
                        arguments[0] = slot
                    }
                }
            }

            fun jvmCarrier(
                builder: DeclarationIrBuilder,
                kind: UnitCallAbiArgumentKind,
                value: IrExpression,
            ): IrExpression =
                when (kind) {
                    UnitCallAbiArgumentKind.Byte,
                    UnitCallAbiArgumentKind.Int32,
                    UnitCallAbiArgumentKind.Int64,
                    UnitCallAbiArgumentKind.Float,
                    UnitCallAbiArgumentKind.Double,
                    UnitCallAbiArgumentKind.Boolean -> value
                    UnitCallAbiArgumentKind.UInt32 -> builder.irCall(requireNotNull(uintToInt)).apply {
                        arguments[0] = value
                    }
                    UnitCallAbiArgumentKind.UInt64 -> builder.irCall(requireNotNull(ulongToLong)).apply {
                        arguments[0] = value
                    }
                    UnitCallAbiArgumentKind.RawComPtr -> segmentFromRawComPtr(builder, value)
                    UnitCallAbiArgumentKind.RawAddress,
                    UnitCallAbiArgumentKind.String,
                    UnitCallAbiArgumentKind.Object -> segmentFromRawAddress(builder, value)
                }

            private fun UnitCallAbiArgumentKind.valueLayoutValue(): JvmStaticLayoutValue =
                when (this) {
                    UnitCallAbiArgumentKind.Byte,
                    UnitCallAbiArgumentKind.Boolean -> valueLayoutJavaByte
                    UnitCallAbiArgumentKind.Int32,
                    UnitCallAbiArgumentKind.UInt32 -> valueLayoutJavaInt
                    UnitCallAbiArgumentKind.Int64,
                    UnitCallAbiArgumentKind.UInt64 -> valueLayoutJavaLong
                    UnitCallAbiArgumentKind.Float -> valueLayoutJavaFloat
                    UnitCallAbiArgumentKind.Double -> valueLayoutJavaDouble
                    UnitCallAbiArgumentKind.RawAddress,
                    UnitCallAbiArgumentKind.RawComPtr,
                    UnitCallAbiArgumentKind.String,
                    UnitCallAbiArgumentKind.Object -> valueLayoutAddress
                }

            private class JvmStaticLayoutValue(
                private val field: IrField?,
                private val getter: IrSimpleFunctionSymbol?,
            ) {
                fun get(builder: DeclarationIrBuilder): IrExpression =
                    field?.let { builder.irGetField(null, it) }
                        ?: builder.irCall(requireNotNull(getter))
            }

            companion object {
                fun create(
                    pluginContext: IrPluginContext,
                    rawComPtrValueGetter: IrSimpleFunctionSymbol,
                    rawAddressValueGetter: IrSimpleFunctionSymbol,
                ): JvmFfmSymbols? {
                    fun missing(): Nothing? = null
                    val memoryLayout = pluginContext.referenceClass(JAVA_MEMORY_LAYOUT_CLASS_ID) ?: return missing()
                    val memorySegment = pluginContext.referenceClass(JAVA_MEMORY_SEGMENT_CLASS_ID) ?: return missing()
                    val linker = pluginContext.referenceClass(JAVA_LINKER_CLASS_ID) ?: return missing()
                    val linkerOption = pluginContext.referenceClass(JAVA_LINKER_OPTION_CLASS_ID) ?: return missing()
                    val functionDescriptor = pluginContext.referenceClass(JAVA_FUNCTION_DESCRIPTOR_CLASS_ID) ?: return missing()
                    val methodHandle = pluginContext.referenceClass(JAVA_METHOD_HANDLE_CLASS_ID) ?: return missing()
                    val valueLayout = pluginContext.referenceClass(JAVA_VALUE_LAYOUT_CLASS_ID) ?: return missing()
                    val addressLayout = pluginContext.referenceClass(JAVA_ADDRESS_LAYOUT_CLASS_ID) ?: return missing()
                    val uint = pluginContext.referenceClass(KOTLIN_UINT_CLASS_ID)
                    val ulong = pluginContext.referenceClass(KOTLIN_ULONG_CLASS_ID)
                    val linkerDowncallHandle = linker.functionNamedWithRegularParameterCount("downcallHandle", 2)
                        ?: return missing()
                    fun staticLayoutValue(classId: ClassId, owner: IrClassSymbol, name: String): JvmStaticLayoutValue? {
                        val property = owner.owner.declarations
                            .filterIsInstance<IrProperty>()
                            .singleOrNull { it.name.asString() == name }
                        val field = owner.fieldNamed(name) ?: property?.backingField
                        val getter = property?.getter?.symbol
                            ?: pluginContext.referenceProperties(CallableId(classId, Name.identifier(name)))
                                .singleOrNull()
                                ?.owner
                                ?.getter
                                ?.symbol
                        return if (field != null || getter != null) {
                            JvmStaticLayoutValue(field, getter)
                        } else {
                            null
                        }
                    }
                    fun valueLayout(name: String): JvmStaticLayoutValue? =
                        staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, name)
                            ?: staticLayoutValue(JAVA_ADDRESS_LAYOUT_CLASS_ID, addressLayout, name)
                    return JvmFfmSymbols(
                        memoryLayoutType = memoryLayout.owner.defaultType,
                        memorySegmentType = memorySegment.owner.defaultType,
                        linkerOptionType = linkerOption.owner.defaultType,
                        linkerNativeLinker = linker.functionNamed("nativeLinker") ?: return missing(),
                        linkerDowncallHandle = linkerDowncallHandle,
                        functionDescriptorOf = functionDescriptor.functionNamed("of") ?: return missing(),
                        memorySegmentOfAddress = memorySegment.functionNamedWithValueParameterTypes("ofAddress", KOTLIN_LONG_FQ_NAME)
                            ?: return missing(),
                        memorySegmentReinterpret = memorySegment.functionNamedWithValueParameterTypes("reinterpret", KOTLIN_LONG_FQ_NAME)
                            ?: return missing(),
                        memorySegmentGetAddress = memorySegment.functionNamedWithValueParameterTypes(
                            "get",
                            JAVA_ADDRESS_LAYOUT_FQ_NAME,
                            KOTLIN_LONG_FQ_NAME,
                        ) ?: return missing(),
                        memorySegmentGetAtIndexAddress = memorySegment.functionNamedWithValueParameterTypes(
                            "getAtIndex",
                            JAVA_ADDRESS_LAYOUT_FQ_NAME,
                            KOTLIN_LONG_FQ_NAME,
                        ) ?: return missing(),
                        methodHandleInvoke = methodHandle.functionNamed("invoke") ?: return missing(),
                        valueLayoutAddress = valueLayout("ADDRESS")
                            ?: return missing(),
                        valueLayoutJavaInt = valueLayout("JAVA_INT") ?: return missing(),
                        valueLayoutJavaByte = valueLayout("JAVA_BYTE") ?: return missing(),
                        valueLayoutJavaLong = valueLayout("JAVA_LONG") ?: return missing(),
                        valueLayoutJavaFloat = valueLayout("JAVA_FLOAT") ?: return missing(),
                        valueLayoutJavaDouble = valueLayout("JAVA_DOUBLE") ?: return missing(),
                        intToLong = pluginContext.irBuiltIns.intClass.owner.declarations
                            .filterIsInstance<IrSimpleFunction>()
                            .singleOrNull { function -> function.name.asString() == "toLong" }
                            ?.symbol
                            ?: return missing(),
                        uintToInt = uint?.functionNamedWithRegularParameterCount("toInt", 0),
                        ulongToLong = ulong?.functionNamedWithRegularParameterCount("toLong", 0),
                        rawComPtrValueGetter = rawComPtrValueGetter,
                        rawAddressValueGetter = rawAddressValueGetter,
                    )
                }
            }
        }
    }

    private fun readCompilerSupportManifest(): List<KotlinWinRtCompilerSupportManifestEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        if (!Files.isRegularFile(manifestPath)) {
            return emptyList()
        }
        return readCompilerSupportManifest(manifestPath)
    }

    private fun writeCompilerSupportClasses(entries: List<KotlinWinRtCompilerSupportManifestEntry>) {
        val outputDirectory = compilerSupportClassOutputDirectoryPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        if (entries.isEmpty()) {
            return
        }
        writeCompilerSupportManifestClass(entries, outputDirectory)
        writeProjectionRegistrarClass(
            entries = readProjectionRegistrarEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeEventProjectionRegistryClass(
            entries = readEventProjectionEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeGenericTypeInstantiationRegistryClass(
            entries = readGenericTypeInstantiationEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeGenericAbiRegistryArtifactClass(
            entries = readGenericAbiRegistryEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeInterfaceNativeProjectionRegistryClass(
            entries = readInterfaceNativeProjectionEntries(entries),
            outputDirectory = outputDirectory,
        )
    }

    private fun readProjectionRegistrarEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtProjectionRegistrarEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "projection-registrar" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readProjectionRegistrarEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readEventProjectionEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtEventProjectionEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "event-source" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readEventProjectionEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readGenericTypeInstantiationEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtGenericTypeInstantiationEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "generic-type-instantiation" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readGenericTypeInstantiationEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readGenericAbiRegistryEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtGenericAbiRegistryEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "generic-abi-registry" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readGenericAbiRegistryEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readInterfaceNativeProjectionEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtInterfaceNativeProjectionEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "interface-native-projection" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readInterfaceNativeProjectionEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun writeProjectionTypeIndex(
        classContexts: List<AuthoredIrClassContext>,
        winRtTypes: Map<String, IndexedWinRtType>,
    ) {
        val outputPath = typeIndexOutputPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        val records = classContexts
            .mapNotNull { context -> context.klass.fqNameWhenAvailable?.asString() }
            .mapNotNull { sourceTypeName -> projectionTypeIndexRecordForSourceType(sourceTypeName, winRtTypes) }
            .distinctBy(KotlinWinRtProjectionTypeIndexRecord::sourceTypeName)
            .sortedBy(KotlinWinRtProjectionTypeIndexRecord::sourceTypeName)
        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(
            outputPath,
            records.joinToString(separator = "\n", postfix = if (records.isEmpty()) "" else "\n") { it.render() },
        )
    }

    private fun authoredTypeFor(
        klass: IrClass,
        winRtTypes: Map<String, IndexedWinRtType>,
    ): KotlinWinRtAuthoredTypeCandidate? {
        val sourceTypeName = klass.fqNameWhenAvailable?.asString() ?: return null
        if (sourceTypeName.startsWith(PROJECTION_PACKAGE_PREFIX) ||
            projectionPackageToMetadataName(sourceTypeName) in winRtTypes
        ) {
            return null
        }
        val resolvedWinRtTypes = klass.superTypes
            .mapNotNull { type -> type.classFqName?.asString() }
            .map(::projectionPackageToMetadataName)
            .mapNotNull(winRtTypes::get)
        if (resolvedWinRtTypes.isEmpty()) {
            return null
        }
        val packageName = sourceTypeName.substringBeforeLast('.', missingDelimiterValue = "")
        val className = sourceTypeName.substringAfterLast('.')
        val winRtBase = resolvedWinRtTypes.firstOrNull { type -> type.kind == "RuntimeClass" }
        val directInterfaces = resolvedWinRtTypes
            .filter { type -> type.kind == "Interface" }
            .map { type -> type.qualifiedName }
        val overridableInterfaces = winRtBase?.overridableInterfaces.orEmpty()
        return KotlinWinRtAuthoredTypeCandidate(
            packageName = packageName,
            className = className,
            sourceTypeName = sourceTypeName,
            winRtBaseClassName = winRtBase?.qualifiedName,
            winRtInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
            overridableInterfaceNames = overridableInterfaces.distinct().sorted(),
        )
    }

    private fun validateAuthoredType(
        klass: IrClass,
        authoredType: KotlinWinRtAuthoredTypeCandidate,
        afterK2: Boolean,
        report: (String) -> Unit,
    ) {
        if (!afterK2) {
            report("kotlin-winrt authoring requires K2 semantic analysis for ${authoredType.sourceTypeName}.")
        }
        if (klass.isInner) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be an inner class.")
        }
        if (klass.typeParameters.isNotEmpty()) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be generic.")
        }
        if (klass.kind == ClassKind.CLASS && klass.modality != Modality.FINAL) {
            report("WinRT authored class ${authoredType.sourceTypeName} must be final.")
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun classContextsIn(
        declaration: IrDeclaration,
        containingTypesPublic: Boolean = true,
    ): List<AuthoredIrClassContext> =
        when (declaration) {
            is IrClass -> {
                val nestedContainingTypesPublic = containingTypesPublic && declaration.visibility == DescriptorVisibilities.PUBLIC
                listOf(AuthoredIrClassContext(declaration, containingTypesPublic)) +
                    declaration.declarations.flatMap { child -> classContextsIn(child, nestedContainingTypesPublic) }
            }
            else -> emptyList()
        }

    private data class AuthoredIrClassContext(
        val klass: IrClass,
        val containingTypesPublic: Boolean,
    )
}

private val WINRT_PROJECTION_INTRINSIC_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRtProjectionIntrinsic")

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamed(name: String): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { it.name.asString() == name }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamedWithValueParameterTypes(
    name: String,
    vararg typeNames: FqName,
): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == name &&
                function.parameters
                    .filter { parameter -> parameter.kind == IrParameterKind.Regular }
                    .map { parameter -> parameter.type.classFqName } == typeNames.toList()
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamedWithRegularParameterCount(
    name: String,
    count: Int,
): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == name &&
                function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == count
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.fieldNamed(name: String): IrField? =
    owner.declarations
        .filterIsInstance<IrField>()
        .singleOrNull { field -> field.name.asString() == name }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.propertyGetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrProperty>()
        .singleOrNull { it.name.asString() == name }
        ?.getter
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.singleValueConstructor(): IrConstructorSymbol? =
    owner.declarations
        .filterIsInstance<IrConstructor>()
        .singleOrNull { constructor ->
            constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 1
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.constructorWithRegularParameterCount(count: Int): IrConstructorSymbol? =
    owner.declarations
        .filterIsInstance<IrConstructor>()
        .singleOrNull { constructor ->
            constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == count
        }
        ?.symbol

private val WINRT_RUNTIME_PACKAGE_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime")

private val KOTLIN_PACKAGE_FQ_NAME =
    FqName("kotlin")

private val KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME =
    FqName("kotlin.collections")

private val KOTLIN_BYTE_FQ_NAME =
    FqName("kotlin.Byte")

private val KOTLIN_INT_FQ_NAME =
    FqName("kotlin.Int")

private val KOTLIN_LONG_FQ_NAME =
    FqName("kotlin.Long")

private val KOTLIN_FLOAT_FQ_NAME =
    FqName("kotlin.Float")

private val KOTLIN_DOUBLE_FQ_NAME =
    FqName("kotlin.Double")

private val KOTLIN_UINT_FQ_NAME =
    FqName("kotlin.UInt")

private val KOTLIN_ULONG_FQ_NAME =
    FqName("kotlin.ULong")

private val WINRT_RAW_COM_PTR_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.RawComPtr")

private val WINRT_RAW_ADDRESS_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.RawAddress")

private val JAVA_ADDRESS_LAYOUT_FQ_NAME =
    FqName("java.lang.foreign.AddressLayout")

private val KOTLIN_UINT_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("UInt"))

private val KOTLIN_ULONG_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("ULong"))

private val KOTLIN_FUNCTION1_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("Function1"))

private val WINRT_COM_VTABLE_INVOKER_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.ComVtableInvoker")

private val WINRT_HSTRING_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("HString"))

private val WINRT_REFERENCED_HSTRING_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("ReferencedHString"))

private val WINRT_IWINRT_OBJECT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("IWinRTObject"))

private val WINRT_COM_OBJECT_REFERENCE_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("ComObjectReference"))

private val WINRT_IUNKNOWN_REFERENCE_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("IUnknownReference"))

private val WINRT_RAW_COM_PTR_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("RawComPtr"))

private val WINRT_RAW_ADDRESS_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("RawAddress"))

private val WINRT_PLATFORM_ABI_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("PlatformAbi"))

private val WINRT_NATIVE_SCOPE_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeScope"))

private val WINRT_NATIVE_STRUCT_ADAPTER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeStructAdapter"))

private val WINRT_NATIVE_STRUCT_LAYOUT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeStructLayout"))

private val WINRT_MARSHALER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("Marshaler"))

private val WINRT_HRESULT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("HResult"))

private val JAVA_FOREIGN_PACKAGE_FQ_NAME =
    FqName("java.lang.foreign")

private val JAVA_MEMORY_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("MemoryLayout"))

private val JAVA_MEMORY_SEGMENT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("MemorySegment"))

private val JAVA_LINKER_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("Linker"))

private val JAVA_LINKER_OPTION_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, FqName("Linker.Option"), false)

private val JAVA_FUNCTION_DESCRIPTOR_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("FunctionDescriptor"))

private val JAVA_VALUE_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("ValueLayout"))

private val JAVA_ADDRESS_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("AddressLayout"))

private val JAVA_METHOD_HANDLE_CLASS_ID =
    ClassId(FqName("java.lang.invoke"), Name.identifier("MethodHandle"))

private val WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS = listOf(
    "callUnit",
    "getString",
    "getBoolean",
    "getInt32",
    "getUInt32",
    "getInt64",
    "getUInt64",
    "getFloat",
    "getDouble",
    "getStruct",
    "getArray",
    "setStruct",
    "setString",
    "setBoolean",
    "setInt32",
    "setUInt32",
    "setInt64",
    "setUInt64",
    "setFloat",
    "setDouble",
    "getProjectedRuntimeClass",
    "getNullableProjectedRuntimeClass",
    "getProjectedInterface",
    "getNullableProjectedInterface",
    "staticGetArray",
    "staticGetArrayWithProjectedObject",
    "staticCallProjectedRuntimeClassWithString",
    "staticCallProjectedInterfaceWithString",
)

private val WINRT_PROJECTION_INTRINSIC_FUNCTIONS = WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS

internal fun generatedSourceRootFromMetadataIndex(metadataIndexPath: String?): String? {
    val indexPath = metadataIndexPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return null
    val parent = indexPath.toAbsolutePath().normalize().parent ?: return null
    if (parent.fileName?.toString() != "kotlin-winrt-authoring") {
        return null
    }
    return parent.parent?.toString()?.normalizedCompilerPathPrefix()
}

internal fun isGeneratedSourceFile(fileName: String, generatedSourceRoot: String?): Boolean {
    val root = generatedSourceRoot ?: return false
    val normalizedFileName = fileName.normalizedCompilerPathPrefix()
    return normalizedFileName == root || normalizedFileName.startsWith("$root/")
}

private fun String.normalizedCompilerPathPrefix(): String =
    replace('\\', '/')
        .trimEnd('/')
        .lowercase()

data class KotlinWinRtCompilerSupportManifestEntry(
    val kind: String,
    val className: String,
    val sourceFile: String,
    val entries: Int,
)

fun readCompilerSupportManifest(path: Path): List<KotlinWinRtCompilerSupportManifestEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseCompilerSupportManifestLine)
        .toList()

private fun parseCompilerSupportManifestLine(line: String): KotlinWinRtCompilerSupportManifestEntry? {
    val parts = line.split('\t', limit = 4)
    if (parts.size < 4) {
        return null
    }
    val entries = parts[3].toIntOrNull() ?: return null
    return KotlinWinRtCompilerSupportManifestEntry(
        kind = parts[0],
        className = parts[1],
        sourceFile = parts[2],
        entries = entries,
    )
}

private const val COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest"

private const val PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTProjectionRegistrar"

private const val EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTEventProjectionRegistry"

private const val GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiationRegistry"

private const val GENERIC_ABI_REGISTRY_ARTIFACT_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTGenericAbiRegistryArtifact"

private const val INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTInterfaceProjectionRegistry"

private const val PROJECTION_REGISTRAR_CHUNK_SIZE: Int = 128

private const val EVENT_PROJECTION_REGISTRY_CHUNK_SIZE: Int = 96

private const val GENERIC_TYPE_INSTANTIATION_REGISTRY_CHUNK_SIZE: Int = 96

private const val INTERFACE_NATIVE_PROJECTION_REGISTRY_CHUNK_SIZE: Int = 96

private const val GENERIC_ABI_REGISTRY_LIST_SEPARATOR: String = "\u001F"

fun writeCompilerSupportManifestClass(
    entries: List<KotlinWinRtCompilerSupportManifestEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(0)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("compiler-support.tsv", null)
    classWriter.addIntConstantField("ENTRY_COUNT", entries.size)
    entries
        .groupBy(KotlinWinRtCompilerSupportManifestEntry::kind)
        .toSortedMap()
        .forEach { (kind, kindEntries) ->
            classWriter.addIntConstantField("${compilerSupportFieldPrefix(kind)}_ENTRIES", kindEntries.sumOf { it.entries })
        }
    classWriter.addDefaultConstructor()
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

data class KotlinWinRtProjectionRegistrarEntry(
    val kotlinClassName: String,
    val projectedTypeName: String,
    val kind: String,
    val baseTypeName: String,
    val metadataClassName: String,
)

fun readProjectionRegistrarEntries(path: Path): List<KotlinWinRtProjectionRegistrarEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseProjectionRegistrarLine)
        .toList()

private fun parseProjectionRegistrarLine(line: String): KotlinWinRtProjectionRegistrarEntry? {
    val parts = line.split('\t', limit = 5)
    if (parts.size < 5) {
        return null
    }
    return KotlinWinRtProjectionRegistrarEntry(
        kotlinClassName = parts[0],
        projectedTypeName = parts[1],
        kind = parts[2],
        baseTypeName = parts[3],
        metadataClassName = parts[4],
    )
}

fun writeProjectionRegistrarClass(
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("projection-registrar.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(PROJECTION_REGISTRAR_CHUNK_SIZE)
    val register = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "register",
        "()V",
        null,
        null,
    )
    register.visitCode()
    chunks.indices.forEach { index ->
        register.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME,
            projectionRegistrarChunkName(index),
            "()V",
            false,
        )
    }
    register.visitInsn(Opcodes.RETURN)
    register.visitMaxs(0, 0)
    register.visitEnd()

    chunks.forEachIndexed { index, chunk ->
        classWriter.addProjectionRegistrarChunk(projectionRegistrarChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun projectionRegistrarChunkName(index: Int): String =
    "registerChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addProjectionRegistrarChunk(
    name: String,
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
) {
    val method = visitMethod(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        name,
        "()V",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        if (entry.metadataClassName.isNotBlank()) {
            val metadataInternalName = entry.metadataClassName.toMetadataInternalName()
            method.visitFieldInsn(
                Opcodes.GETSTATIC,
                metadataInternalName,
                "INSTANCE",
                "L$metadataInternalName;",
            )
            method.visitInsn(Opcodes.POP)
        }
        method.visitLdcInsn(Type.getObjectType(entry.kotlinClassName.toInternalName()))
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "kotlin/jvm/internal/Reflection",
            "getOrCreateKotlinClass",
            "(Ljava/lang/Class;)Lkotlin/reflect/KClass;",
            false,
        )
        method.visitLdcInsn(entry.projectedTypeName)
        method.visitLdcInsn(entry.kind)
        method.visitLdcInsn(entry.baseTypeName)
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/github/composefluent/winrt/runtime/CompilerGeneratedProjectionTypeIndexesKt",
            "registerGeneratedProjectionTypeIndex",
            "(Lkotlin/reflect/KClass;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false,
        )
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun String.toInternalName(): String =
    replace('.', '/')

private fun String.toMetadataInternalName(): String =
    removeSuffix(".Metadata").toInternalName() + "\$Metadata"

data class KotlinWinRtEventProjectionEntry(
    val eventType: String,
    val ownerType: String,
    val sourceClass: String,
    val abiEventType: String,
    val genericArguments: List<String>,
    val usesSharedEventHandlerSource: Boolean,
    val interfaceId: String,
    val parameterKinds: List<String>,
    val returnKind: String,
    val parameterTypeNames: List<String>,
)

fun readEventProjectionEntries(path: Path): List<KotlinWinRtEventProjectionEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseEventProjectionLine)
        .toList()

private fun parseEventProjectionLine(line: String): KotlinWinRtEventProjectionEntry? {
    val parts = line.split('\t', limit = 10)
    if (parts.size < 6) {
        return null
    }
    return KotlinWinRtEventProjectionEntry(
        eventType = parts[0],
        ownerType = parts[1],
        sourceClass = parts[2],
        abiEventType = parts[3],
        genericArguments = parts[4].split(',').filter(String::isNotBlank),
        usesSharedEventHandlerSource = parts[5].toBooleanStrictOrNull() ?: false,
        interfaceId = parts.getOrNull(6).orEmpty(),
        parameterKinds = parts.getOrNull(7).orEmpty().split(',').filter(String::isNotBlank),
        returnKind = parts.getOrNull(8).orEmpty().ifBlank { "UNIT" },
        parameterTypeNames = parts.getOrNull(9).orEmpty().split(',').filter(String::isNotBlank),
    )
}

fun writeEventProjectionRegistryClass(
    entries: List<KotlinWinRtEventProjectionEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("event-sources.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(EVENT_PROJECTION_REGISTRY_CHUNK_SIZE)
    val register = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "register",
        "()V",
        null,
        null,
    )
    register.visitCode()
    chunks.indices.forEach { index ->
        register.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
            eventProjectionRegistryChunkName(index),
            "()V",
            false,
        )
    }
    register.visitInsn(Opcodes.RETURN)
    register.visitMaxs(0, 0)
    register.visitEnd()
    chunks.forEachIndexed { index, chunk ->
        classWriter.addEventProjectionRegistryChunk(eventProjectionRegistryChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun eventProjectionRegistryChunkName(index: Int): String =
    "registerChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addEventProjectionRegistryChunk(
    name: String,
    entries: List<KotlinWinRtEventProjectionEntry>,
) {
    val method = visitMethod(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        name,
        "()V",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "INSTANCE",
            "Lio/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations;",
        )
        method.visitLdcInsn(entry.eventType)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "initializeBySourceType",
            "(Ljava/lang/String;)V",
            false,
        )
        method.addEventSourceDescriptor(entry, withFactory = false)
        method.visitVarInsn(Opcodes.ASTORE, 0)
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime",
            "INSTANCE",
            "Lio/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime;",
        )
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime",
            "createEventSourceFactory",
            "(Lio/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor;)Lkotlin/jvm/functions/Function2;",
            false,
        )
        method.visitVarInsn(Opcodes.ASTORE, 1)
        method.addEventSourceDescriptor(entry, withFactory = true)
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/runtime/WinRtEventSourceRuntime",
            "INSTANCE",
            "Lio/github/composefluent/winrt/runtime/WinRtEventSourceRuntime;",
        )
        method.visitInsn(Opcodes.SWAP)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/runtime/WinRtEventSourceRuntime",
            "registerEventSource",
            "(Lio/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor;)V",
            false,
        )
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addEventSourceDescriptor(
    entry: KotlinWinRtEventProjectionEntry,
    withFactory: Boolean,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(entry.eventType)
    visitLdcInsn(entry.ownerType)
    visitLdcInsn(entry.sourceClass)
    visitLdcInsn(entry.abiEventType)
    addStringList(entry.genericArguments)
    visitInsn(if (entry.usesSharedEventHandlerSource) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    addNullableGuid(entry.interfaceId)
    addWinRtDelegateValueKindList(entry.parameterKinds)
    addWinRtDelegateValueKind(entry.returnKind)
    addStringList(entry.parameterTypeNames)
    if (withFactory) {
        visitVarInsn(Opcodes.ALOAD, 1)
    } else {
        visitInsn(Opcodes.ACONST_NULL)
    }
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor",
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;ZLio/github/composefluent/winrt/runtime/Guid;Ljava/util/List;Lio/github/composefluent/winrt/runtime/WinRtDelegateValueKind;Ljava/util/List;Lkotlin/jvm/functions/Function2;)V",
        false,
    )
}

data class KotlinWinRtGenericTypeInstantiationEntry(
    val className: String,
    val sourceType: String,
    val isDelegate: Boolean,
    val rcwFunctions: List<String>,
    val vtableFunctions: List<String>,
    val propertyAccessors: List<String>,
    val genericReturnOnlyRcwFunctions: List<String>,
    val projectedGenericFallbacks: List<String>,
    val dependencies: List<String>,
)

fun readGenericTypeInstantiationEntries(path: Path): List<KotlinWinRtGenericTypeInstantiationEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseGenericTypeInstantiationLine)
        .toList()

private fun parseGenericTypeInstantiationLine(line: String): KotlinWinRtGenericTypeInstantiationEntry? {
    val parts = line.split('\t', limit = 9)
    if (parts.size < 9) {
        return null
    }
    return KotlinWinRtGenericTypeInstantiationEntry(
        className = parts[0],
        sourceType = parts[1],
        isDelegate = parts[2].toBooleanStrictOrNull() ?: false,
        rcwFunctions = parts[3].splitListField(),
        vtableFunctions = parts[4].splitListField(),
        propertyAccessors = parts[5].splitListField(),
        genericReturnOnlyRcwFunctions = parts[6].splitListField(),
        projectedGenericFallbacks = parts[7].splitListField(),
        dependencies = parts[8].splitListField(),
    )
}

private fun String.splitListField(): List<String> =
    split(',').filter(String::isNotBlank)

fun writeGenericTypeInstantiationRegistryClass(
    entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("generic-instantiations.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(GENERIC_TYPE_INSTANTIATION_REGISTRY_CHUNK_SIZE)
    classWriter.addGenericRegistryDispatcher("initializeAll", "()V", chunks.indices) { method, index ->
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME,
            genericRegistryChunkName(index),
            "()V",
            false,
        )
    }
    classWriter.addGenericRegistryDispatcher("initializeBySourceType", "(Ljava/lang/String;)V", chunks.indices) { method, index ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME,
            genericRegistrySourceChunkName(index),
            "(Ljava/lang/String;)V",
            false,
        )
    }
    chunks.forEachIndexed { index, chunk ->
        classWriter.addGenericRegistryChunk(genericRegistryChunkName(index), chunk)
        classWriter.addGenericRegistrySourceChunk(genericRegistrySourceChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addGenericRegistryDispatcher(
    name: String,
    descriptor: String,
    indices: IntRange,
    callChunk: (org.jetbrains.org.objectweb.asm.MethodVisitor, Int) -> Unit,
) {
    val method = visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, descriptor, null, null)
    method.visitCode()
    indices.forEach { index -> callChunk(method, index) }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun genericRegistryChunkName(index: Int): String =
    "initializeAllChunk${index.toString().padStart(3, '0')}"

private fun genericRegistrySourceChunkName(index: Int): String =
    "initializeBySourceTypeChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addGenericRegistryChunk(
    name: String,
    entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
) {
    val method = visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, name, "()V", null, null)
    method.visitCode()
    entries.forEach { entry ->
        method.addGenericInstantiationEntry(entry)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "initializeEntry",
            "(Lio/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry;)V",
            false,
        )
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericRegistrySourceChunk(
    name: String,
    entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
) {
    val method = visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, name, "(Ljava/lang/String;)V", null, null)
    method.visitCode()
    entries.forEach { entry ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitLdcInsn(entry.sourceType)
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "areEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.addGenericInstantiationEntry(entry)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "initializeEntry",
            "(Lio/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry;)V",
            false,
        )
        method.visitInsn(Opcodes.RETURN)
        method.visitLabel(next)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addGenericInstantiationEntry(entry: KotlinWinRtGenericTypeInstantiationEntry) {
    visitFieldInsn(
        Opcodes.GETSTATIC,
        "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
        "INSTANCE",
        "Lio/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations;",
    )
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(entry.className)
    visitLdcInsn(entry.sourceType)
    visitInsn(if (entry.isDelegate) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    addStringList(entry.rcwFunctions)
    addStringList(entry.vtableFunctions)
    addStringList(entry.propertyAccessors)
    addStringList(entry.genericReturnOnlyRcwFunctions)
    addStringList(entry.projectedGenericFallbacks)
    addStringList(entry.dependencies)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry",
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;ZLjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;)V",
        false,
    )
}

data class KotlinWinRtGenericAbiRegistryEntry(
    val kind: String,
    val name: String,
    val sourceGenericType: String,
    val operation: String,
    val declaration: String,
    val abiParameterTypes: List<String>,
    val typeArrayShape: List<String>,
)

fun readGenericAbiRegistryEntries(path: Path): List<KotlinWinRtGenericAbiRegistryEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseGenericAbiRegistryLine)
        .toList()

private fun parseGenericAbiRegistryLine(line: String): KotlinWinRtGenericAbiRegistryEntry? {
    val parts = line.split('\t', limit = 7)
    if (parts.size < 7) {
        return null
    }
    return KotlinWinRtGenericAbiRegistryEntry(
        kind = parts[0],
        name = parts[1],
        sourceGenericType = parts[2],
        operation = parts[3],
        declaration = parts[4],
        abiParameterTypes = parts[5].splitGenericAbiRegistryListField(),
        typeArrayShape = parts[6].splitGenericAbiRegistryListField(),
    )
}

private fun String.splitGenericAbiRegistryListField(): List<String> =
    split(GENERIC_ABI_REGISTRY_LIST_SEPARATOR).filter(String::isNotBlank)

fun writeGenericAbiRegistryArtifactClass(
    entries: List<KotlinWinRtGenericAbiRegistryEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        GENERIC_ABI_REGISTRY_ARTIFACT_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("generic-abi-registry.tsv", null)
    classWriter.addDefaultConstructor()
    val delegates = entries.filter { it.kind == "delegate" }
    val derivedInterfaces = entries.filter { it.kind == "derived-interface" }.map { it.name }
    classWriter.addGenericAbiDelegateNamedMethod(delegates)
    classWriter.addGenericAbiDelegatesForSourceTypeMethod(delegates)
    classWriter.addGenericAbiIsDerivedGenericInterfaceMethod(derivedInterfaces)
    classWriter.addGenericAbiRegisterDelegatesMethod(delegates)
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$GENERIC_ABI_REGISTRY_ARTIFACT_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addGenericAbiDelegateNamedMethod(entries: List<KotlinWinRtGenericAbiRegistryEntry>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "delegateNamed",
        "(Ljava/lang/String;)Lio/github/composefluent/winrt/projections/support/GenericAbiDelegateEntry;",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        method.visitLdcInsn(entry.name)
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.addGenericAbiDelegateEntry(entry)
        method.visitInsn(Opcodes.ARETURN)
        method.visitLabel(next)
    }
    method.visitInsn(Opcodes.ACONST_NULL)
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericAbiDelegatesForSourceTypeMethod(entries: List<KotlinWinRtGenericAbiRegistryEntry>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "delegatesForSourceType",
        "(Ljava/lang/String;)Ljava/util/List;",
        null,
        null,
    )
    method.visitCode()
    method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
    method.visitInsn(Opcodes.DUP)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
    method.visitVarInsn(Opcodes.ASTORE, 1)
    entries.forEach { entry ->
        method.visitLdcInsn(entry.sourceGenericType)
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.visitVarInsn(Opcodes.ALOAD, 1)
        method.addGenericAbiDelegateEntry(entry)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
        method.visitInsn(Opcodes.POP)
        method.visitLabel(next)
    }
    method.visitVarInsn(Opcodes.ALOAD, 1)
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericAbiIsDerivedGenericInterfaceMethod(typeNames: List<String>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "isDerivedGenericInterface",
        "(Ljava/lang/String;)Z",
        null,
        null,
    )
    method.visitCode()
    typeNames.forEach { typeName ->
        method.visitLdcInsn(typeName)
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.visitInsn(Opcodes.ICONST_1)
        method.visitInsn(Opcodes.IRETURN)
        method.visitLabel(next)
    }
    method.visitInsn(Opcodes.ICONST_0)
    method.visitInsn(Opcodes.IRETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericAbiRegisterDelegatesMethod(entries: List<KotlinWinRtGenericAbiRegistryEntry>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "registerAbiDelegates",
        "(Lkotlin/jvm/functions/Function2;)V",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.addStringList(entry.typeArrayShape)
        method.visitLdcInsn(entry.name)
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            "kotlin/jvm/functions/Function2",
            "invoke",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            true,
        )
        method.visitInsn(Opcodes.POP)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addGenericAbiDelegateEntry(
    entry: KotlinWinRtGenericAbiRegistryEntry,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/projections/support/GenericAbiDelegateEntry")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(entry.name)
    visitLdcInsn(entry.sourceGenericType)
    visitLdcInsn(entry.operation)
    visitLdcInsn(entry.declaration)
    addStringList(entry.abiParameterTypes)
    addStringList(entry.typeArrayShape)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/projections/support/GenericAbiDelegateEntry",
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V",
        false,
    )
}

data class KotlinWinRtInterfaceNativeProjectionEntry(
    val projectedTypeName: String,
    val kotlinInterfaceClassName: String,
    val implementationClassName: String,
    val interfaceId: String,
    val memberCount: Int,
    val members: List<KotlinWinRtInterfaceNativeProjectionMemberEntry> = emptyList(),
)

data class KotlinWinRtInterfaceNativeProjectionMemberEntry(
    val kind: String,
    val jvmName: String,
    val slot: Int,
    val returnKind: String,
    val parameterKinds: List<String>,
    val suppressHResultCheck: Boolean,
    val eventTypeName: String = "",
    val ownerTypeName: String = "",
)

fun readInterfaceNativeProjectionEntries(path: Path): List<KotlinWinRtInterfaceNativeProjectionEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseInterfaceNativeProjectionLine)
        .toList()

private fun parseInterfaceNativeProjectionLine(line: String): KotlinWinRtInterfaceNativeProjectionEntry? {
    val parts = line.split('\t', limit = 6)
    if (parts.size < 5) {
        return null
    }
    val memberCount = parts[4].toIntOrNull() ?: return null
    val members = parts.getOrElse(5) { "" }
        .split(';')
        .filter(String::isNotBlank)
        .mapNotNull(::parseInterfaceNativeProjectionMember)
    return KotlinWinRtInterfaceNativeProjectionEntry(
        projectedTypeName = parts[0],
        kotlinInterfaceClassName = parts[1],
        implementationClassName = parts[2],
        interfaceId = parts[3],
        memberCount = memberCount,
        members = members,
    )
}

private fun parseInterfaceNativeProjectionMember(value: String): KotlinWinRtInterfaceNativeProjectionMemberEntry? {
    val parts = value.split('|', limit = 8)
    if (parts.size < 6) {
        return null
    }
    return KotlinWinRtInterfaceNativeProjectionMemberEntry(
        kind = parts[0],
        jvmName = parts[1],
        slot = parts[2].toIntOrNull() ?: return null,
        returnKind = parts[3],
        parameterKinds = parts[4].split(',').filter(String::isNotBlank),
        suppressHResultCheck = parts[5].toBooleanStrictOrNull() ?: false,
        eventTypeName = parts.getOrElse(6) { "" },
        ownerTypeName = parts.getOrElse(7) { "" },
    )
}

fun writeInterfaceNativeProjectionRegistryClass(
    entries: List<KotlinWinRtInterfaceNativeProjectionEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    entries.forEach { entry ->
        if (entry.memberCount == 0) {
            writeInterfaceNativeProjectionImplementationClass(entry, outputDirectory)
        }
        writeInterfaceNativeProjectionFactoryClass(entry, outputDirectory)
    }

    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("interface-native-projections.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(INTERFACE_NATIVE_PROJECTION_REGISTRY_CHUNK_SIZE)
    val register = classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "register", "()V", null, null)
    register.visitCode()
    chunks.indices.forEach { index ->
        register.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
            interfaceNativeProjectionRegistryChunkName(index),
            "()V",
            false,
        )
    }
    register.visitInsn(Opcodes.RETURN)
    register.visitMaxs(0, 0)
    register.visitEnd()
    chunks.forEachIndexed { index, chunk ->
        classWriter.addInterfaceNativeProjectionRegistryChunk(interfaceNativeProjectionRegistryChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun interfaceNativeProjectionRegistryChunkName(index: Int): String =
    "registerChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addInterfaceNativeProjectionRegistryChunk(
    name: String,
    entries: List<KotlinWinRtInterfaceNativeProjectionEntry>,
) {
    val method = visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, name, "()V", null, null)
    method.visitCode()
    entries.forEach { entry ->
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/runtime/ComWrappersSupport",
            "INSTANCE",
            "Lio/github/composefluent/winrt/runtime/ComWrappersSupport;",
        )
        method.addWinRtTypeHandle(entry.projectedTypeName, entry.interfaceId)
        val factoryInternalName = interfaceNativeProjectionFactoryInternalName(entry)
        method.visitTypeInsn(Opcodes.NEW, factoryInternalName)
        method.visitInsn(Opcodes.DUP)
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, factoryInternalName, "<init>", "()V", false)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/runtime/ComWrappersSupport",
            "registerInterfaceProjectionFactory",
            "(Lio/github/composefluent/winrt/runtime/WinRtTypeHandle;Lkotlin/jvm/functions/Function1;)Z",
            false,
        )
        method.visitInsn(Opcodes.POP)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun writeInterfaceNativeProjectionImplementationClass(
    entry: KotlinWinRtInterfaceNativeProjectionEntry,
    outputDirectory: Path,
) {
    val implementationInternalName = entry.implementationClassName.toInternalName()
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        implementationInternalName,
        null,
        "java/lang/Object",
        arrayOf(entry.kotlinInterfaceClassName.toInternalName(), "io/github/composefluent/winrt/runtime/IWinRTObject"),
    )
    classWriter.visitSource("interface-native-projections.tsv", null)
    classWriter.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
        "nativeObject",
        "Lio/github/composefluent/winrt/runtime/ComObjectReference;",
        null,
        null,
    ).visitEnd()
    classWriter.addInterfaceNativeProjectionConstructor(implementationInternalName)
    classWriter.addInterfaceNativeProjectionNativeObjectGetter(implementationInternalName)
    classWriter.addInterfaceNativeProjectionPrimaryTypeHandleGetter(entry)
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$implementationInternalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addInterfaceNativeProjectionConstructor(ownerInternalName: String) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC,
        "<init>",
        "(Lio/github/composefluent/winrt/runtime/IUnknownReference;)V",
        null,
        null,
    )
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitVarInsn(Opcodes.ALOAD, 1)
    method.visitFieldInsn(
        Opcodes.PUTFIELD,
        ownerInternalName,
        "nativeObject",
        "Lio/github/composefluent/winrt/runtime/ComObjectReference;",
    )
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addInterfaceNativeProjectionNativeObjectGetter(ownerInternalName: String) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC,
        "getNativeObject",
        "()Lio/github/composefluent/winrt/runtime/ComObjectReference;",
        null,
        null,
    )
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitFieldInsn(
        Opcodes.GETFIELD,
        ownerInternalName,
        "nativeObject",
        "Lio/github/composefluent/winrt/runtime/ComObjectReference;",
    )
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addInterfaceNativeProjectionPrimaryTypeHandleGetter(entry: KotlinWinRtInterfaceNativeProjectionEntry) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC,
        "getPrimaryTypeHandle",
        "()Lio/github/composefluent/winrt/runtime/WinRtTypeHandle;",
        null,
        null,
    )
    method.visitCode()
    method.addWinRtTypeHandle(entry.projectedTypeName, entry.interfaceId)
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun writeInterfaceNativeProjectionFactoryClass(
    entry: KotlinWinRtInterfaceNativeProjectionEntry,
    outputDirectory: Path,
) {
    val factoryInternalName = interfaceNativeProjectionFactoryInternalName(entry)
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        factoryInternalName,
        null,
        "java/lang/Object",
        arrayOf("kotlin/jvm/functions/Function1"),
    )
    classWriter.visitSource("interface-native-projections.tsv", null)
    classWriter.addPublicDefaultConstructor()
    classWriter.addInterfaceNativeProjectionFactoryInvoke(factoryInternalName, entry)
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$factoryInternalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addPublicDefaultConstructor() {
    val method = visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addInterfaceNativeProjectionFactoryInvoke(
    ownerInternalName: String,
    entry: KotlinWinRtInterfaceNativeProjectionEntry,
) {
    val implementationInternalName = entry.implementationClassName.toInternalName()
    val typedInvoke = visitMethod(
        Opcodes.ACC_PUBLIC,
        "invoke",
        "(Lio/github/composefluent/winrt/runtime/IUnknownReference;)Ljava/lang/Object;",
        null,
        null,
    )
    typedInvoke.visitCode()
    typedInvoke.visitLdcInsn(Type.getObjectType(entry.kotlinInterfaceClassName.toInternalName()))
    typedInvoke.addWinRtTypeHandle(entry.projectedTypeName, entry.interfaceId)
    typedInvoke.visitVarInsn(Opcodes.ALOAD, 1)
    typedInvoke.addInterfaceNativeProjectionMemberList(entry.members)
    typedInvoke.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "io/github/composefluent/winrt/runtime/WinRtGeneratedInterfaceProjectionRuntime",
        "create",
        "(Ljava/lang/Class;Lio/github/composefluent/winrt/runtime/WinRtTypeHandle;Lio/github/composefluent/winrt/runtime/IUnknownReference;Ljava/util/List;)Ljava/lang/Object;",
        false,
    )
    typedInvoke.visitInsn(Opcodes.ARETURN)
    typedInvoke.visitMaxs(0, 0)
    typedInvoke.visitEnd()

    val bridge = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_BRIDGE or Opcodes.ACC_SYNTHETIC,
        "invoke",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        null,
        null,
    )
    bridge.visitCode()
    bridge.visitVarInsn(Opcodes.ALOAD, 0)
    bridge.visitVarInsn(Opcodes.ALOAD, 1)
    bridge.visitTypeInsn(Opcodes.CHECKCAST, "io/github/composefluent/winrt/runtime/IUnknownReference")
    bridge.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        ownerInternalName,
        "invoke",
        "(Lio/github/composefluent/winrt/runtime/IUnknownReference;)Ljava/lang/Object;",
        false,
    )
    bridge.visitInsn(Opcodes.ARETURN)
    bridge.visitMaxs(0, 0)
    bridge.visitEnd()
}

private fun interfaceNativeProjectionFactoryInternalName(entry: KotlinWinRtInterfaceNativeProjectionEntry): String =
    "${entry.implementationClassName}\$Factory".toInternalName()

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addInterfaceNativeProjectionMemberList(
    members: List<KotlinWinRtInterfaceNativeProjectionMemberEntry>,
) {
    pushInt(members.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    members.forEachIndexed { index, member ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        addInterfaceNativeProjectionMember(member)
        visitInsn(Opcodes.AASTORE)
    }
    visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "kotlin/collections/CollectionsKt",
        "listOf",
        "([Ljava/lang/Object;)Ljava/util/List;",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addInterfaceNativeProjectionMember(
    member: KotlinWinRtInterfaceNativeProjectionMemberEntry,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberDescriptor")
    visitInsn(Opcodes.DUP)
    addEnumValue(
        "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberKind",
        member.kind,
    )
    visitLdcInsn(member.jvmName)
    pushInt(member.slot)
    addEnumValue(
        "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionValueKind",
        member.returnKind,
    )
    addValueKindList(member.parameterKinds)
    visitInsn(if (member.suppressHResultCheck) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    visitLdcInsn(member.eventTypeName)
    visitLdcInsn(member.ownerTypeName)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberDescriptor",
        "<init>",
        "(Lio/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberKind;Ljava/lang/String;ILio/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionValueKind;Ljava/util/List;ZLjava/lang/String;Ljava/lang/String;)V",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addValueKindList(values: List<String>) {
    pushInt(values.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    values.forEachIndexed { index, value ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        addEnumValue("io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionValueKind", value)
        visitInsn(Opcodes.AASTORE)
    }
    visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "kotlin/collections/CollectionsKt",
        "listOf",
        "([Ljava/lang/Object;)Ljava/util/List;",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addEnumValue(
    enumInternalName: String,
    constantName: String,
) {
    visitFieldInsn(
        Opcodes.GETSTATIC,
        enumInternalName,
        constantName,
        "L$enumInternalName;",
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRtTypeHandle(
    projectedTypeName: String,
    interfaceId: String,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/WinRtTypeHandle")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(projectedTypeName)
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/Guid")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(interfaceId)
    visitMethodInsn(Opcodes.INVOKESPECIAL, "io/github/composefluent/winrt/runtime/Guid", "<init>", "(Ljava/lang/String;)V", false)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/WinRtTypeHandle",
        "<init>",
        "(Ljava/lang/String;Lio/github/composefluent/winrt/runtime/Guid;)V",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addNullableGuid(value: String) {
    if (value.isBlank()) {
        visitInsn(Opcodes.ACONST_NULL)
        return
    }
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/Guid")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(value)
    visitMethodInsn(Opcodes.INVOKESPECIAL, "io/github/composefluent/winrt/runtime/Guid", "<init>", "(Ljava/lang/String;)V", false)
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRtDelegateValueKind(value: String) {
    visitFieldInsn(
        Opcodes.GETSTATIC,
        "io/github/composefluent/winrt/runtime/WinRtDelegateValueKind",
        value.ifBlank { "UNIT" },
        "Lio/github/composefluent/winrt/runtime/WinRtDelegateValueKind;",
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRtDelegateValueKindList(values: List<String>) {
    pushInt(values.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    values.forEachIndexed { index, value ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        addWinRtDelegateValueKind(value)
        visitInsn(Opcodes.AASTORE)
    }
    visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "kotlin/collections/CollectionsKt",
        "listOf",
        "([Ljava/lang/Object;)Ljava/util/List;",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addStringList(values: List<String>) {
    pushInt(values.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    values.forEachIndexed { index, value ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        visitLdcInsn(value)
        visitInsn(Opcodes.AASTORE)
    }
    visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "kotlin/collections/CollectionsKt",
        "listOf",
        "([Ljava/lang/Object;)Ljava/util/List;",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.pushInt(value: Int) {
    when (value) {
        0 -> visitInsn(Opcodes.ICONST_0)
        1 -> visitInsn(Opcodes.ICONST_1)
        2 -> visitInsn(Opcodes.ICONST_2)
        3 -> visitInsn(Opcodes.ICONST_3)
        4 -> visitInsn(Opcodes.ICONST_4)
        5 -> visitInsn(Opcodes.ICONST_5)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> visitIntInsn(Opcodes.BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> visitIntInsn(Opcodes.SIPUSH, value)
        else -> visitLdcInsn(value)
    }
}

private fun compilerSupportFieldPrefix(kind: String): String =
    kind.uppercase()
        .map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")

private fun ClassWriter.addIntConstantField(name: String, value: Int) {
    visitField(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
        name,
        "I",
        null,
        value,
    ).visitEnd()
}

private fun ClassWriter.addDefaultConstructor() {
    val method = visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null)
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(1, 1)
    method.visitEnd()
}
