package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
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
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
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
        val projectionRegistrarEntries = readProjectionRegistrarEntries(compilerSupportEntries)
        val genericTypeInstantiationEntries = readGenericTypeInstantiationEntries(compilerSupportEntries)
        val genericAbiRegistryEntries = readGenericAbiRegistryEntries(compilerSupportEntries)
        writeCompilerSupportClasses(compilerSupportEntries, projectionRegistrarEntries)
        val projectionSupportInitialize = addProjectionSupportInitializerFunction(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            entries = projectionRegistrarEntries,
        )
        val genericTypeInstantiationSupport = addGenericTypeInstantiationSupportFunctions(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            entries = genericTypeInstantiationEntries,
        )
        val genericAbiSupport = addGenericAbiSupportFunctions(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            entries = genericAbiRegistryEntries,
        )
        lowerProjectionSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            initialize = projectionSupportInitialize,
        )
        lowerGenericTypeInstantiationSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            support = genericTypeInstantiationSupport,
        )
        lowerGenericAbiSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            support = genericAbiSupport,
        )
        lowerAuthoringSupportIntrinsicCalls(moduleFragment, pluginContext)
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
        val authoredTypeNames = classContexts
            .mapNotNull { context -> authoredTypeFor(context.klass, winRtTypes)?.sourceTypeName }
            .toSet()
        lowerAuthoredTypeConstructors(moduleFragment, pluginContext, authoredTypeNames)
        lowerAuthoredTypeConstructorCalls(moduleFragment, pluginContext, authoredTypeNames)
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
        var reportedMissingDirectLowering = false
        var reportedUnloweredIntrinsic = false
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
                    if (directLowerings == null) {
                        if (!reportedMissingDirectLowering) {
                            reportedMissingDirectLowering = true
                            pluginContext.messageCollector.report(
                                CompilerMessageSeverity.ERROR,
                                "kotlin-winrt projection intrinsic lowering requires compiling JVM projections with a JDK that exposes java.lang.foreign. Use JDK 25 for Kotlin/JVM compilation; otherwise generated WinRT projection calls would remain as runtime fallback intrinsics.",
                                null,
                            )
                        }
                        return call
                    }
                    if (!directLowerings.hasJvmFfmSymbols) {
                        if (!reportedMissingDirectLowering) {
                            reportedMissingDirectLowering = true
                            pluginContext.messageCollector.report(
                                CompilerMessageSeverity.ERROR,
                                "kotlin-winrt projection intrinsic lowering requires compiling JVM projections with JVM target 25 and a JDK that exposes java.lang.foreign. The compiler plugin loaded, but JVM FFM symbols were not visible to IR lowering; remove lower -Xjdk-release values such as -Xjdk-release=17 for WinRT JVM compilation.",
                                null,
                            )
                        }
                        return call
                    }
                    directLowerings.lower(
                        intrinsicName,
                        call,
                        pluginContext,
                        builderScope = currentScope?.scope?.scopeOwnerSymbol,
                    )
                        ?.let { return it }
                    if (!reportedUnloweredIntrinsic) {
                        reportedUnloweredIntrinsic = true
                        pluginContext.messageCollector.report(
                            CompilerMessageSeverity.ERROR,
                            "kotlin-winrt projection intrinsic $intrinsicName was recognized but could not be lowered. This would leave a runtime fallback call in generated projection bytecode.",
                            null,
                        )
                    }
                    return call
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.projectionIntrinsicFunctionName(): String? {
        val name = symbol.owner.name.asString()
        if (!isProjectionIntrinsicFunction(name, (symbol.owner.parent as? IrClass)?.fqNameWhenAvailable?.asString())) {
            return null
        }
        return name
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
        private val platformAbiReadInt16: IrSimpleFunctionSymbol,
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
        private val winRtObjectMarshaller: IrClassSymbol,
        private val winRtObjectMarshallerFromAbi: IrSimpleFunctionSymbol,
        private val iUnknownReferenceConstructor: IrConstructorSymbol,
        private val comObjectReferenceAsInspectable: IrSimpleFunctionSymbol,
        private val comObjectReferenceClose: IrSimpleFunctionSymbol,
        private val function1Invoke: IrSimpleFunctionSymbol,
        private val kotlinError: IrSimpleFunctionSymbol,
        private val hResultConstructor: IrConstructorSymbol,
        private val hResultRequireSuccess: IrSimpleFunctionSymbol,
        private val ubyteConstructor: IrConstructorSymbol?,
        private val ushortConstructor: IrConstructorSymbol?,
        private val uintConstructor: IrConstructorSymbol?,
        private val ulongConstructor: IrConstructorSymbol?,
        private val jvmFfmSymbols: JvmFfmSymbols?,
    ) {
        val hasJvmFfmSymbols: Boolean
            get() = jvmFfmSymbols != null

        fun lower(
            intrinsicName: String,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? =
            when (intrinsicName) {
                "callUnit" -> lowerDescriptorCallUnit(call, pluginContext, builderScope)
                "callBoolean" -> lowerDescriptorCallBoolean(call, pluginContext, builderScope)
                "callScalar" -> lowerDescriptorCallScalar(call, pluginContext, builderScope)
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
                "callStruct" -> lowerDescriptorCallStruct(call, pluginContext, builderScope)
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
                "callProjectedRuntimeClass" -> lowerDescriptorProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                )
                "callProjectedInterface" -> lowerDescriptorProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                )
                "callObject" -> lowerDescriptorCallObject(call, pluginContext, builderScope)
                "setStruct" -> lowerStructSetter(call, pluginContext, builderScope)
                else -> null
            }

        private fun lowerDescriptorProjectedObjectCall(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            kind: ProjectedObjectGetterKind,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val wrap = call.arguments.getOrNull(4) ?: return null
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = 5) ?: return null
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
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(argumentKind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (argumentKind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
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
                        val abiValues = argumentKinds.map(::abiValueFor)
                        val readResultBlock = builder.irBlock(resultType = call.type) {
                            +jvmFfmCallUnitBlock(
                                symbols = symbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                                values = abiValues + builder.irGet(resultOut),
                                abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
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
                                    pluginContext = pluginContext,
                                    callType = call.type,
                                    resultPointer = builder.irGet(resultPointer),
                                    wrap = wrap,
                                    kind = kind,
                                ),
                            )
                        }
                        if (structAbis.isEmpty() && stringAbis.isEmpty()) {
                            +readResultBlock
                        } else {
                            +builder.irTry(
                                type = call.type,
                                tryResult = readResultBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
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

        private fun lowerDescriptorCallObject(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = 4) ?: return null
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
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(argumentKind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (argumentKind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
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
                        val abiValues = argumentKinds.map(::abiValueFor)
                        val readResultBlock = builder.irBlock(resultType = call.type) {
                            +jvmFfmCallUnitBlock(
                                symbols = symbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                                values = abiValues + builder.irGet(resultOut),
                                abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
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
                            +builder.irAs(
                                builder.irCall(winRtObjectMarshallerFromAbi).apply {
                                    arguments[0] = builder.irGetObject(winRtObjectMarshaller)
                                    arguments[1] = builder.irGet(resultPointer)
                                },
                                call.type,
                            )
                        }
                        if (structAbis.isEmpty() && stringAbis.isEmpty()) {
                            +readResultBlock
                        } else {
                            +builder.irTry(
                                type = call.type,
                                tryResult = readResultBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
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
                                        pluginContext = pluginContext,
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
                                    pluginContext = pluginContext,
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
            pluginContext: IrPluginContext,
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
                    when (kind) {
                        ProjectedObjectGetterKind.RuntimeClass -> {
                            val resultInspectable = irTemporary(
                                value = builder.irCall(comObjectReferenceAsInspectable).apply {
                                    arguments[0] = builder.irGet(resultReference)
                                },
                                nameHint = "resultInspectable",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            builder.irTry(
                                type = callType,
                                tryResult = builder.irCall(function1Invoke).apply {
                                    arguments[0] = wrap
                                    arguments[1] = builder.irGet(resultInspectable)
                                },
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    +builder.irCall(comObjectReferenceClose).apply {
                                        arguments[0] = builder.irGet(resultReference)
                                    }
                                },
                            )
                        }
                        ProjectedObjectGetterKind.Interface ->
                            builder.irCall(function1Invoke).apply {
                                arguments[0] = wrap
                                arguments[1] = builder.irGet(resultReference)
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

        private fun lowerDescriptorCallStruct(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val adapter = call.arguments.getOrNull(4) ?: return null
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = 5) ?: return null
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
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(argumentKind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (argumentKind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct -> {
                                    val structAdapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(structAdapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(structAdapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += structAdapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
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
                        val abiValues = argumentKinds.map(::abiValueFor)
                        +builder.irTry(
                            type = call.type,
                            tryResult = builder.irBlock(resultType = call.type) {
                                +jvmFfmCallUnitBlock(
                                    symbols = symbols,
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    reference = reference,
                                    slot = slot,
                                    argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                                    values = abiValues + builder.irGet(resultOut),
                                    abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
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
                                structAbis.forEach { (structAdapter, valueAbi) ->
                                    +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                        arguments[0] = builder.irGet(structAdapter)
                                        arguments[1] = builder.irGet(valueAbi)
                                    }
                                }
                                stringAbis.forEach { stringAbi ->
                                    +builder.irCall(referencedHStringClose).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
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
            if ((returnKind == NoArgumentGetterReturnKind.UInt8 && ubyteConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt16 && ushortConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt32 && uintConstructor == null) ||
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
                    NoArgumentGetterReturnKind.Int8,
                    NoArgumentGetterReturnKind.UInt8 -> platformAbiAllocateInt8Slot
                    NoArgumentGetterReturnKind.Int16 -> platformAbiAllocateBytes
                    NoArgumentGetterReturnKind.UInt16 -> platformAbiAllocateBytes
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
                if (
                    returnKind == NoArgumentGetterReturnKind.Int16 ||
                    returnKind == NoArgumentGetterReturnKind.UInt16 ||
                    returnKind == NoArgumentGetterReturnKind.Float
                ) {
                    arguments[2] = builder.irLong(if (returnKind == NoArgumentGetterReturnKind.Float) 4L else 2L)
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
                NoArgumentGetterReturnKind.Int8 -> builder.irCall(platformAbiReadInt8).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt8 -> builder.irCall(requireNotNull(ubyteConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt8).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.Int16 -> builder.irCall(platformAbiReadInt16).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt16 -> builder.irCall(requireNotNull(ushortConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt16).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
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
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds)) ?: return null
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
                    abiShape = shape,
                )
            }
        }

        private fun lowerDescriptorCallBoolean(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            return lowerDescriptorCallScalar(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Boolean)
        }

        private fun lowerDescriptorCallScalar(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val returnShape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val returnKind = when (returnShape) {
                "Int8" -> NoArgumentGetterReturnKind.Int8
                "UInt8" -> NoArgumentGetterReturnKind.UInt8
                "Int16" -> NoArgumentGetterReturnKind.Int16
                "UInt16" -> NoArgumentGetterReturnKind.UInt16
                "Int32" -> NoArgumentGetterReturnKind.Int32
                "UInt32" -> NoArgumentGetterReturnKind.UInt32
                "Int64" -> NoArgumentGetterReturnKind.Int64
                "UInt64" -> NoArgumentGetterReturnKind.UInt64
                "Float" -> NoArgumentGetterReturnKind.Float
                "Double" -> NoArgumentGetterReturnKind.Double
                else -> return null
            }
            return lowerDescriptorCallScalar(
                call = call,
                pluginContext = pluginContext,
                builderScope = builderScope,
                returnKind = returnKind,
                abiShapeIndex = 4,
                varargIndex = 5,
            )
        }

        private fun lowerDescriptorCallScalar(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            returnKind: NoArgumentGetterReturnKind,
            abiShapeIndex: Int = 3,
            varargIndex: Int = 4,
        ): IrExpression? {
            val symbols = jvmFfmSymbols ?: return null
            val scope = builderScope ?: return null
            if ((returnKind == NoArgumentGetterReturnKind.UInt8 && ubyteConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt16 && ushortConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt32 && uintConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt64 && ulongConstructor == null) ||
                returnKind == NoArgumentGetterReturnKind.String
            ) {
                return null
            }
            val shape = call.arguments.getOrNull(abiShapeIndex)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = varargIndex) ?: return null
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
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(kind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (kind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
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

                        val abiValues = argumentKinds.map(::abiValueFor)
                        val callBlock = jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                            values = abiValues + builder.irGet(resultOut),
                            abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
                        )
                        val readResult = readGetterResult(builder, pluginContext, returnKind, builder.irGet(resultOut))
                        if (stringAbis.isEmpty() && structAbis.isEmpty()) {
                            +callBlock
                            +readResult
                        } else {
                            +builder.irTry(
                                type = call.type,
                                tryResult = builder.irBlock(resultType = call.type) {
                                    +callBlock
                                    +readResult
                                },
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
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

        private fun lowerJvmFfmCallUnitWithArguments(
            symbols: JvmFfmSymbols,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            reference: IrExpression,
            slot: IrExpression,
            values: List<IrExpression>,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            abiShape: String? = null,
        ): IrExpression? {
            val scope = builderScope ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val hasStructArguments = argumentKinds.any { it == UnitCallAbiArgumentKind.Struct }
                val nativeScope = if (hasStructArguments) {
                    irTemporary(
                        value = builder.irCall(platformAbiConfinedScope).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                        },
                        nameHint = "scope",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                } else {
                    null
                }
                fun callWithPreparedArguments(): IrExpression =
                    builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(kind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (kind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float -> value
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(requireNotNull(nativeScope))
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
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

                        val abiValues = argumentKinds.map(::abiValueFor)
                        val callBlock = jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = argumentKinds,
                            values = abiValues,
                            abiShape = abiShape,
                        )
                        if (stringAbis.isEmpty() && structAbis.isEmpty()) {
                            +callBlock
                        } else {
                            +builder.irTry(
                                type = pluginContext.irBuiltIns.unitType,
                                tryResult = callBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
                    }
                if (nativeScope == null) {
                    +callWithPreparedArguments()
                } else {
                    +builder.irTry(
                        type = pluginContext.irBuiltIns.unitType,
                        tryResult = callWithPreparedArguments(),
                        catches = emptyList(),
                        finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            +builder.irCall(nativeScopeClose).apply {
                                arguments[0] = builder.irGet(nativeScope)
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
                KOTLIN_SHORT_FQ_NAME -> UnitCallAbiArgumentKind.Int16
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
            abiShape: String? = null,
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
                        abiShape = abiShape,
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
            abiShape: String? = null,
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
                    value = if (abiShape == null) {
                        symbols.downcallHandle(builder, argumentKinds)
                    } else {
                        symbols.downcallHandle(builder, abiShape)
                    },
                    nameHint = "handle",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irAs(
                    symbols.invokeHResultDowncall(
                        builder = builder,
                        pluginContext = pluginContext,
                        handle = builder.irGet(handle),
                        arguments = listOf(builder.irGet(function), builder.irGet(instanceSegment)) +
                            values.mapIndexed { index, value ->
                                symbols.jvmCarrier(builder, argumentKinds[index], value)
                            },
                    ),
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

        private fun IrCall.varargValues(expectedCount: Int, varargIndex: Int = 4): List<IrExpression>? {
            val varargArgument = arguments.getOrNull(varargIndex)
            if (expectedCount == 0 && varargArgument == null) {
                return emptyList()
            }
            val vararg = varargArgument as? IrVararg ?: return null
            val values = vararg.elements.map { element -> element as? IrExpression ?: return null }
            return values.takeIf { it.size == expectedCount }
        }

        private enum class UnitCallAbiArgumentKind {
            RawAddress,
            RawComPtr,
            Byte,
            Int16,
            Int32,
            UInt32,
            Int64,
            UInt64,
            Float,
            Double,
            Boolean,
            String,
            Struct,
            Object,
        }

        private enum class NoArgumentGetterReturnKind {
            String,
            Boolean,
            Int8,
            UInt8,
            Int16,
            UInt16,
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
                    return emptyList()
                }
                return value.split(',').map { token ->
                    when (token) {
                        "RawAddress" -> UnitCallAbiArgumentKind.RawAddress
                        "RawComPtr" -> UnitCallAbiArgumentKind.RawComPtr
                        "Byte" -> UnitCallAbiArgumentKind.Byte
                        "Int16" -> UnitCallAbiArgumentKind.Int16
                        "Int32" -> UnitCallAbiArgumentKind.Int32
                        "UInt32" -> UnitCallAbiArgumentKind.UInt32
                        "Int64" -> UnitCallAbiArgumentKind.Int64
                        "UInt64" -> UnitCallAbiArgumentKind.UInt64
                        "Float" -> UnitCallAbiArgumentKind.Float
                        "Double" -> UnitCallAbiArgumentKind.Double
                        "Boolean" -> UnitCallAbiArgumentKind.Boolean
                        "String" -> UnitCallAbiArgumentKind.String
                        "Struct" -> UnitCallAbiArgumentKind.Struct
                        "Object" -> UnitCallAbiArgumentKind.Object
                        else -> if (STRUCT_LAYOUT_TOKEN.matches(token)) UnitCallAbiArgumentKind.Struct else return null
                    }
                }
            }

            fun varargValueCount(argumentKinds: List<UnitCallAbiArgumentKind>): Int =
                argumentKinds.sumOf { kind ->
                    when (kind) {
                        UnitCallAbiArgumentKind.Struct -> 2
                        else -> 1
                    }
                }

            fun appendToken(shape: String, token: String): String =
                if (shape.isBlank()) token else "$shape,$token"

            private val STRUCT_LAYOUT_TOKEN = Regex("""Struct\d+_\d+""")
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
                val platformAbiReadInt16 = platformAbi.functionNamed("readInt16") ?: return null
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
                val winRtObjectMarshaller = pluginContext.referenceClass(WINRT_OBJECT_MARSHALLER_CLASS_ID)
                    ?: return null
                val winRtObjectMarshallerFromAbi = winRtObjectMarshaller.functionNamed("fromAbi") ?: return null
                val emptyList = pluginContext.referenceFunctions(
                    CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyList")),
                ).singleOrNull() ?: return null
                val iUnknownReference = pluginContext.referenceClass(WINRT_IUNKNOWN_REFERENCE_CLASS_ID)
                    ?: return null
                val iUnknownReferenceConstructor =
                    iUnknownReference.constructorWithRegularParameterCount(5) ?: return null
                val comObjectReferenceAsInspectable = comObjectReference.functionNamed("asInspectable") ?: return null
                val comObjectReferenceClose = comObjectReference.functionNamed("close") ?: return null
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
                val ubyteConstructor = pluginContext.referenceClass(KOTLIN_UBYTE_CLASS_ID)
                    ?.singleValueConstructor()
                val ushortConstructor = pluginContext.referenceClass(KOTLIN_USHORT_CLASS_ID)
                    ?.singleValueConstructor()
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
                    platformAbiReadInt16 = platformAbiReadInt16,
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
                    winRtObjectMarshaller = winRtObjectMarshaller,
                    winRtObjectMarshallerFromAbi = winRtObjectMarshallerFromAbi,
                    iUnknownReferenceConstructor = iUnknownReferenceConstructor,
                    comObjectReferenceAsInspectable = comObjectReferenceAsInspectable,
                    comObjectReferenceClose = comObjectReferenceClose,
                    function1Invoke = function1Invoke,
                    kotlinError = kotlinError,
                    hResultConstructor = hResultConstructor,
                    hResultRequireSuccess = hResultRequireSuccess,
                    ubyteConstructor = ubyteConstructor,
                    ushortConstructor = ushortConstructor,
                    uintConstructor = uintConstructor,
                    ulongConstructor = ulongConstructor,
                    jvmFfmSymbols = jvmFfmSymbols,
                )
            }

        }

        private class JvmFfmSymbols private constructor(
            private val memorySegmentType: org.jetbrains.kotlin.ir.types.IrType,
            private val memorySegmentOfAddress: IrSimpleFunctionSymbol,
            private val memorySegmentReinterpret: IrSimpleFunctionSymbol,
            private val memorySegmentGetAddress: IrSimpleFunctionSymbol,
            private val memorySegmentGetAtIndexAddress: IrSimpleFunctionSymbol,
            val methodHandleInvoke: IrSimpleFunctionSymbol,
            private val valueLayoutAddress: JvmStaticLayoutValue,
            private val intToLong: IrSimpleFunctionSymbol,
            private val uintToInt: IrSimpleFunctionSymbol?,
            private val ulongToLong: IrSimpleFunctionSymbol?,
            private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
            private val rawAddressValueGetter: IrSimpleFunctionSymbol,
            private val winRtJvmFfmDowncallHandles: IrClassSymbol,
            private val winRtJvmFfmDowncallHandlesHResult: IrSimpleFunctionSymbol,
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

            private fun List<UnitCallAbiArgumentKind>.jvmFfmAbiShape(): String =
                joinToString(",") { kind -> kind.jvmFfmAbiToken() }

            private fun UnitCallAbiArgumentKind.jvmFfmAbiToken(): String =
                when (this) {
                    UnitCallAbiArgumentKind.RawAddress -> "RawAddress"
                    UnitCallAbiArgumentKind.RawComPtr -> "RawComPtr"
                    UnitCallAbiArgumentKind.Byte -> "Byte"
                    UnitCallAbiArgumentKind.Int16 -> "Int16"
                    UnitCallAbiArgumentKind.Int32 -> "Int32"
                    UnitCallAbiArgumentKind.UInt32 -> "UInt32"
                    UnitCallAbiArgumentKind.Int64 -> "Int64"
                    UnitCallAbiArgumentKind.UInt64 -> "UInt64"
                    UnitCallAbiArgumentKind.Float -> "Float"
                    UnitCallAbiArgumentKind.Double -> "Double"
                    UnitCallAbiArgumentKind.Boolean -> "Boolean"
                    UnitCallAbiArgumentKind.String -> "String"
                    UnitCallAbiArgumentKind.Struct -> "Struct"
                    UnitCallAbiArgumentKind.Object -> "Object"
                }

            fun downcallHandle(
                builder: DeclarationIrBuilder,
                argumentKinds: List<UnitCallAbiArgumentKind>,
            ): IrExpression =
                downcallHandle(builder, argumentKinds.jvmFfmAbiShape())

            fun downcallHandle(
                builder: DeclarationIrBuilder,
                abiShape: String,
            ): IrExpression =
                builder.irCall(winRtJvmFfmDowncallHandlesHResult).apply {
                    arguments[0] = builder.irGetObject(winRtJvmFfmDowncallHandles)
                    arguments[1] = builder.irString(abiShape)
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
                    UnitCallAbiArgumentKind.Int16,
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
                    UnitCallAbiArgumentKind.Struct,
                    UnitCallAbiArgumentKind.String,
                    UnitCallAbiArgumentKind.Object -> segmentFromRawAddress(builder, value)
                }

            fun invokeHResultDowncall(
                builder: DeclarationIrBuilder,
                pluginContext: IrPluginContext,
                handle: IrExpression,
                arguments: List<IrExpression>,
            ): IrExpression {
                val original = methodHandleInvoke.owner
                val receiverParameters = original.parameters.filter { parameter ->
                    parameter.kind != IrParameterKind.Regular
                }
                val instantiated = pluginContext.irFactory.buildFun {
                    updateFrom(original)
                    name = original.name
                    origin = JvmLoweredDeclarationOrigin.POLYMORPHIC_SIGNATURE_INSTANTIATION
                    returnType = pluginContext.irBuiltIns.intType
                }.apply {
                    parent = original.parent
                    parameters = receiverParameters + arguments.mapIndexed { index, argument ->
                        pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                            name = Name.identifier("\$$index")
                            type = argument.type
                            origin = JvmLoweredDeclarationOrigin.POLYMORPHIC_SIGNATURE_INSTANTIATION
                            kind = IrParameterKind.Regular
                        }, this)
                    }
                }
                return builder.irCall(instantiated.symbol, pluginContext.irBuiltIns.intType).apply {
                    this.arguments[0] = handle
                    arguments.forEachIndexed { index, argument ->
                        this.arguments[index + 1] = argument
                    }
                }
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
                    val memorySegment = pluginContext.referenceClass(JAVA_MEMORY_SEGMENT_CLASS_ID) ?: return missing()
                    val methodHandle = pluginContext.referenceClass(JAVA_METHOD_HANDLE_CLASS_ID) ?: return missing()
                    val valueLayout = pluginContext.referenceClass(JAVA_VALUE_LAYOUT_CLASS_ID) ?: return missing()
                    val addressLayout = pluginContext.referenceClass(JAVA_ADDRESS_LAYOUT_CLASS_ID) ?: return missing()
                    val winRtJvmFfmDowncallHandles =
                        pluginContext.referenceClass(WINRT_JVM_FFM_DOWNCALL_HANDLES_CLASS_ID) ?: return missing()
                    val winRtJvmFfmDowncallHandlesHResult =
                        winRtJvmFfmDowncallHandles.functionNamed("hResult") ?: return missing()
                    val uint = pluginContext.referenceClass(KOTLIN_UINT_CLASS_ID)
                    val ulong = pluginContext.referenceClass(KOTLIN_ULONG_CLASS_ID)
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
                        memorySegmentType = memorySegment.owner.defaultType,
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
                        intToLong = pluginContext.irBuiltIns.intClass.owner.declarations
                            .filterIsInstance<IrSimpleFunction>()
                            .singleOrNull { function -> function.name.asString() == "toLong" }
                            ?.symbol
                            ?: return missing(),
                        uintToInt = uint?.functionNamedWithRegularParameterCount("toInt", 0),
                        ulongToLong = ulong?.functionNamedWithRegularParameterCount("toLong", 0),
                        rawComPtrValueGetter = rawComPtrValueGetter,
                        rawAddressValueGetter = rawAddressValueGetter,
                        winRtJvmFfmDowncallHandles = winRtJvmFfmDowncallHandles,
                        winRtJvmFfmDowncallHandlesHResult = winRtJvmFfmDowncallHandlesHResult,
                    )
                }
            }
        }
    }

    private fun readCompilerSupportManifest(): List<KotlinWinRtCompilerSupportManifestEntry> {
        return readCompilerSupportManifestIfConfigured(compilerSupportManifestPath)
    }

    private fun writeCompilerSupportClasses(
        entries: List<KotlinWinRtCompilerSupportManifestEntry>,
        projectionRegistrarEntries: List<KotlinWinRtProjectionRegistrarEntry>,
    ) {
        val outputDirectory = compilerSupportClassOutputDirectoryPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        Files.deleteIfExists(outputDirectory.resolve(STALE_EVENT_PROJECTION_REGISTRY_CLASS_PATH))
        writeCompilerSupportManifestClass(entries, outputDirectory)
        writeProjectionSupportInitializerClass(
            entries = projectionRegistrarEntries,
            outputDirectory = outputDirectory,
        )
    }

    private fun readProjectionRegistrarEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtProjectionRegistrarEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "projection-registrar",
            description = "projection registrar input",
            read = ::readProjectionRegistrarEntries,
        )
    }

    private fun readGenericTypeInstantiationEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtGenericTypeInstantiationEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "generic-type-instantiation",
            description = "generic type instantiation input",
            read = ::readGenericTypeInstantiationEntries,
        )
    }

    private fun readGenericAbiRegistryEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtGenericAbiRegistryEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "generic-abi-registry",
            description = "generic ABI registry input",
            read = ::readGenericAbiRegistryEntries,
        )
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
        val overridableInterfaces = inheritedOverridableInterfaceNames(winRtBase, winRtTypes)
        return KotlinWinRtAuthoredTypeCandidate(
            packageName = packageName,
            className = className,
            sourceTypeName = sourceTypeName,
            winRtBaseClassName = winRtBase?.qualifiedName,
            winRtInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
            overridableInterfaceNames = overridableInterfaces,
            isPublic = true,
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

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addProjectionSupportInitializerFunction(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        entries: List<KotlinWinRtProjectionRegistrarEntry>,
    ): IrSimpleFunctionSymbol? {
        if (entries.isEmpty()) {
            return null
        }
        val file = moduleFragment.files.firstOrNull() ?: return null
        val registerGeneratedProjectionTypeIndex = pluginContext.referenceFunctions(
            CallableId(
                FqName("io.github.composefluent.winrt.runtime"),
                Name.identifier("registerGeneratedProjectionTypeIndex"),
            ),
        )
            .map { symbol -> symbol.owner }
            .singleOrNull { function ->
                function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 4
            }
            ?.symbol
            ?: return null
        val function = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtProjectionSupportInitialize_${projectionSupportInitializerHash(entries)}")
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
        }
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val resolvedEntries = resolveProjectionRegistrarClasses(entries) { className ->
            pluginContext.referenceClass(ClassId.topLevel(FqName(className)))
        }
        function.body = builder.irBlockBody {
            resolvedEntries.forEach { (entry, projectedClass) ->
                +builder.irCall(registerGeneratedProjectionTypeIndex).apply {
                    arguments[0] = IrClassReferenceImpl(
                        startOffset = 0,
                        endOffset = 0,
                        type = pluginContext.irBuiltIns.kClassClass.owner.defaultType,
                        symbol = projectedClass,
                        classType = projectedClass.owner.defaultType,
                    )
                    arguments[1] = builder.irString(entry.projectedTypeName)
                    arguments[2] = builder.irString(entry.kind)
                    arguments[3] = builder.irString(entry.baseTypeName)
                }
            }
        }
        file.declarations += function
        return function.symbol
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerProjectionSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        initialize: IrSimpleFunctionSymbol?,
    ) {
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    if (!call.isProjectionSupportEnsureInitializedCall()) {
                        return call
                    }
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol ?: return call
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return initialize?.let { symbol -> builder.irCall(symbol) } ?: builder.irUnit()
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.isProjectionSupportEnsureInitializedCall(): Boolean {
        val function = symbol.owner
        if (function.name.asString() != "ensureInitialized") {
            return false
        }
        val ownerClass = function.parent as? IrClass ?: return false
        return ownerClass.fqNameWhenAvailable?.asString() ==
            "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic"
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addGenericTypeInstantiationSupportFunctions(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
    ): GenericTypeInstantiationSupportFunctions? {
        if (entries.isEmpty()) {
            return null
        }
        val file = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "module file",
            value = moduleFragment.files.firstOrNull(),
        )
        val supportClass = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "class io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations",
            value = pluginContext.referenceClass(
                ClassId.topLevel(FqName("io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations")),
            ),
        )
        val entryClass = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "class io.github.composefluent.winrt.projections.support.GenericTypeInstantiationEntry",
            value = pluginContext.referenceClass(
                ClassId.topLevel(FqName("io.github.composefluent.winrt.projections.support.GenericTypeInstantiationEntry")),
            ),
        )
        val entryConstructor = entryClass.owner.declarations
            .filterIsInstance<IrConstructor>()
            .singleOrNull { constructor ->
                constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 9
            }
            ?.symbol
            .let { symbol ->
                requireCompilerSupportPrerequisite(
                    description = "generic type instantiation",
                    prerequisite = "GenericTypeInstantiationEntry constructor with 9 regular parameters",
                    value = symbol,
                )
            }
        val initializeEntry = supportClass.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { function ->
                function.name.asString() == "initializeEntry" &&
                    function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 1
            }
            ?.symbol
            .let { symbol ->
                requireCompilerSupportPrerequisite(
                    description = "generic type instantiation",
                    prerequisite = "WinRTGenericTypeInstantiations.initializeEntry with 1 regular parameter",
                    value = symbol,
                )
            }
        val listOf = pluginContext.referenceFunctions(
            CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("listOf")),
        ).singleOrNull { function ->
            function.owner.parameters
                .singleOrNull { parameter -> parameter.kind == IrParameterKind.Regular }
                ?.varargElementType != null
        }.let { symbol ->
            requireCompilerSupportPrerequisite(
                description = "generic type instantiation",
                prerequisite = "kotlin.collections.listOf vararg function",
                value = symbol,
            )
        }

        val sortedEntries = entries.sortedWith(
            compareBy(KotlinWinRtGenericTypeInstantiationEntry::sourceType, KotlinWinRtGenericTypeInstantiationEntry::className),
        )
        val initializeAll = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtGenericTypeInstantiationInitializeAll")
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
        }
        val initializeAllBuilder = DeclarationIrBuilder(pluginContext, initializeAll.symbol)
        initializeAll.body = initializeAllBuilder.irBlockBody {
            sortedEntries.forEach { entry ->
                +initializeAllBuilder.genericTypeInstantiationInitializeEntryCall(
                    pluginContext = pluginContext,
                    supportClass = supportClass,
                    initializeEntry = initializeEntry,
                    entryConstructor = entryConstructor,
                    listOf = listOf,
                    entry = entry,
                )
            }
        }

        val initializeBySourceType = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtGenericTypeInstantiationInitializeBySourceType")
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
            parameters = listOf(
                pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                    name = Name.identifier("sourceType")
                    type = pluginContext.irBuiltIns.stringType
                    kind = IrParameterKind.Regular
                }, this),
            )
        }
        val initializeBySourceTypeBuilder = DeclarationIrBuilder(pluginContext, initializeBySourceType.symbol)
        val sourceTypeParameter = initializeBySourceType.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        initializeBySourceType.body = initializeBySourceTypeBuilder.irBlockBody {
            sortedEntries.forEach { entry ->
                +initializeBySourceTypeBuilder.irIfThen(
                    type = pluginContext.irBuiltIns.unitType,
                    condition = initializeBySourceTypeBuilder.irEquals(
                        initializeBySourceTypeBuilder.irGet(sourceTypeParameter),
                        initializeBySourceTypeBuilder.irString(entry.sourceType),
                    ),
                    thenPart = initializeBySourceTypeBuilder.genericTypeInstantiationInitializeEntryCall(
                        pluginContext = pluginContext,
                        supportClass = supportClass,
                        initializeEntry = initializeEntry,
                        entryConstructor = entryConstructor,
                        listOf = listOf,
                        entry = entry,
                    ),
                )
            }
        }
        file.declarations += initializeAll
        file.declarations += initializeBySourceType
        return GenericTypeInstantiationSupportFunctions(
            initializeAll = initializeAll.symbol,
            initializeBySourceType = initializeBySourceType.symbol,
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun DeclarationIrBuilder.genericTypeInstantiationInitializeEntryCall(
        pluginContext: IrPluginContext,
        supportClass: IrClassSymbol,
        initializeEntry: IrSimpleFunctionSymbol,
        entryConstructor: IrConstructorSymbol,
        listOf: IrSimpleFunctionSymbol,
        entry: KotlinWinRtGenericTypeInstantiationEntry,
    ): IrExpression =
        irCall(initializeEntry).apply {
            dispatchReceiver = irGetObject(supportClass)
            arguments[1] = irCall(entryConstructor).apply {
                arguments[0] = irString(entry.className)
                arguments[1] = irString(entry.sourceType)
                arguments[2] = irBoolean(entry.isDelegate)
                arguments[3] = irStringList(pluginContext, listOf, entry.rcwFunctions)
                arguments[4] = irStringList(pluginContext, listOf, entry.vtableFunctions)
                arguments[5] = irStringList(pluginContext, listOf, entry.propertyAccessors)
                arguments[6] = irStringList(pluginContext, listOf, entry.genericReturnOnlyRcwFunctions)
                arguments[7] = irStringList(pluginContext, listOf, entry.projectedGenericFallbacks)
                arguments[8] = irStringList(pluginContext, listOf, entry.dependencies)
            }
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun DeclarationIrBuilder.irStringList(
        pluginContext: IrPluginContext,
        listOf: IrSimpleFunctionSymbol,
        values: List<String>,
    ): IrExpression {
        val parameter = listOf.owner.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        return irCall(listOf).apply {
            arguments[0] = IrVarargImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = parameter.type,
                varargElementType = pluginContext.irBuiltIns.stringType,
                elements = values.map { value -> irString(value) },
            )
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerGenericTypeInstantiationSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        support: GenericTypeInstantiationSupportFunctions?,
    ) {
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    val genericCall = call.genericTypeInstantiationSupportIntrinsicCallName() ?: return call
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol ?: return call
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return when (genericCall) {
                        "initializeAll" -> support?.let { builder.irCall(it.initializeAll) } ?: builder.irUnit()
                        "initializeBySourceType" -> support?.let {
                            builder.irCall(it.initializeBySourceType).apply {
                                arguments[0] = call.arguments[1]
                            }
                        } ?: builder.irUnit()
                        else -> call
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.genericTypeInstantiationSupportIntrinsicCallName(): String? {
        val function = symbol.owner
        val name = function.name.asString()
        if (name != "initializeAll" && name != "initializeBySourceType") {
            return null
        }
        val ownerClass = function.parent as? IrClass ?: return null
        return name.takeIf {
            ownerClass.fqNameWhenAvailable?.asString() ==
                "io.github.composefluent.winrt.runtime.WinRtGenericTypeInstantiationSupportIntrinsic"
        }
    }

    private data class GenericTypeInstantiationSupportFunctions(
        val initializeAll: IrSimpleFunctionSymbol,
        val initializeBySourceType: IrSimpleFunctionSymbol,
    )

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addGenericAbiSupportFunctions(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        entries: List<KotlinWinRtGenericAbiRegistryEntry>,
    ): GenericAbiSupportFunctions? {
        if (entries.isEmpty()) {
            return null
        }
        val file = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "module file",
            value = moduleFragment.files.firstOrNull(),
        )
        val entryClass = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "class io.github.composefluent.winrt.projections.support.GenericAbiDelegateEntry",
            value = pluginContext.referenceClass(
                ClassId.topLevel(FqName("io.github.composefluent.winrt.projections.support.GenericAbiDelegateEntry")),
            ),
        )
        val entryConstructor = entryClass.owner.declarations
            .filterIsInstance<IrConstructor>()
            .singleOrNull { constructor ->
                constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 6
            }
            ?.symbol
            .let { symbol ->
                requireCompilerSupportPrerequisite(
                    description = "generic ABI registry",
                    prerequisite = "GenericAbiDelegateEntry constructor with 6 regular parameters",
                    value = symbol,
                )
            }
        val intrinsicClass = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "class io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic",
            value = pluginContext.referenceClass(
                ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic")),
            ),
        )
        val delegateNamedIntrinsic = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "WinRtGenericAbiSupportIntrinsic.delegateNamed with 1 regular parameter",
            value = intrinsicClass.functionNamedWithRegularParameterCount("delegateNamed", 1),
        )
        val delegatesForSourceTypeIntrinsic =
            requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "WinRtGenericAbiSupportIntrinsic.delegatesForSourceType with 1 regular parameter",
                value = intrinsicClass.functionNamedWithRegularParameterCount("delegatesForSourceType", 1),
            )
        val isDerivedGenericInterfaceIntrinsic =
            requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "WinRtGenericAbiSupportIntrinsic.isDerivedGenericInterface with 1 regular parameter",
                value = intrinsicClass.functionNamedWithRegularParameterCount("isDerivedGenericInterface", 1),
            )
        val registerAbiDelegatesIntrinsic =
            requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "WinRtGenericAbiSupportIntrinsic.registerAbiDelegates with 1 regular parameter",
                value = intrinsicClass.functionNamedWithRegularParameterCount("registerAbiDelegates", 1),
            )
        val listOf = pluginContext.referenceFunctions(
            CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("listOf")),
        ).singleOrNull { function ->
            function.owner.parameters
                .singleOrNull { parameter -> parameter.kind == IrParameterKind.Regular }
                ?.varargElementType != null
        }.let { symbol ->
            requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "kotlin.collections.listOf vararg function",
                value = symbol,
            )
        }
        val emptyList = pluginContext.referenceFunctions(
            CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyList")),
        ).singleOrNull().let { symbol ->
            requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "kotlin.collections.emptyList function",
                value = symbol,
            )
        }
        val function2 = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "class kotlin.Function2",
            value = pluginContext.referenceClass(KOTLIN_FUNCTION2_CLASS_ID),
        )
        val function2Invoke = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "kotlin.Function2.invoke",
            value = function2.functionNamed("invoke"),
        )

        val delegates = entries
            .filter { entry -> entry.kind == "delegate" }
            .sortedWith(compareBy(KotlinWinRtGenericAbiRegistryEntry::name, KotlinWinRtGenericAbiRegistryEntry::sourceGenericType))
        val derivedInterfaces = entries
            .filter { entry -> entry.kind == "derived-interface" }
            .map { entry -> entry.name }
            .distinct()
            .sorted()

        val delegateNamed = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtGenericAbiDelegateNamed")
            returnType = delegateNamedIntrinsic.owner.returnType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
            parameters = listOf(
                pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                    name = Name.identifier("name")
                    type = pluginContext.irBuiltIns.stringType
                    kind = IrParameterKind.Regular
                }, this),
            )
        }
        val delegateNamedBuilder = DeclarationIrBuilder(pluginContext, delegateNamed.symbol)
        val nameParameter = delegateNamed.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        delegateNamed.body = delegateNamedBuilder.irBlockBody {
            +delegates.asReversed().fold(delegateNamedBuilder.irNull() as IrExpression) { elsePart, entry ->
                delegateNamedBuilder.irIfThenElse(
                    type = delegateNamed.returnType,
                    condition = delegateNamedBuilder.irEquals(
                        delegateNamedBuilder.irGet(nameParameter),
                        delegateNamedBuilder.irString(entry.name),
                    ),
                    thenPart = delegateNamedBuilder.genericAbiDelegateEntryCall(
                        pluginContext = pluginContext,
                        entryConstructor = entryConstructor,
                        listOf = listOf,
                        entry = entry,
                    ),
                    elsePart = elsePart,
                )
            }
        }

        val delegatesForSourceType = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtGenericAbiDelegatesForSourceType")
            returnType = delegatesForSourceTypeIntrinsic.owner.returnType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
            parameters = listOf(
                pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                    name = Name.identifier("sourceGenericType")
                    type = pluginContext.irBuiltIns.stringType
                    kind = IrParameterKind.Regular
                }, this),
            )
        }
        val delegatesForSourceTypeBuilder = DeclarationIrBuilder(pluginContext, delegatesForSourceType.symbol)
        val sourceGenericTypeParameter =
            delegatesForSourceType.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        val delegatesBySourceType = delegates.groupBy { entry -> entry.sourceGenericType }.toSortedMap()
        delegatesForSourceType.body = delegatesForSourceTypeBuilder.irBlockBody {
            +delegatesBySourceType.entries.toList().asReversed().fold(
                delegatesForSourceTypeBuilder.irCall(emptyList) as IrExpression,
            ) { elsePart, (sourceGenericType, sourceEntries) ->
                delegatesForSourceTypeBuilder.irIfThenElse(
                    type = delegatesForSourceType.returnType,
                    condition = delegatesForSourceTypeBuilder.irEquals(
                        delegatesForSourceTypeBuilder.irGet(sourceGenericTypeParameter),
                        delegatesForSourceTypeBuilder.irString(sourceGenericType),
                    ),
                    thenPart = delegatesForSourceTypeBuilder.genericAbiDelegateEntryList(
                        pluginContext = pluginContext,
                        entryConstructor = entryConstructor,
                        listOf = listOf,
                        entries = sourceEntries,
                    ),
                    elsePart = elsePart,
                )
            }
        }

        val isDerivedGenericInterface = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtGenericAbiIsDerivedGenericInterface")
            returnType = isDerivedGenericInterfaceIntrinsic.owner.returnType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
            parameters = listOf(
                pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                    name = Name.identifier("typeName")
                    type = pluginContext.irBuiltIns.stringType
                    kind = IrParameterKind.Regular
                }, this),
            )
        }
        val isDerivedGenericInterfaceBuilder = DeclarationIrBuilder(pluginContext, isDerivedGenericInterface.symbol)
        val typeNameParameter = isDerivedGenericInterface.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        isDerivedGenericInterface.body = isDerivedGenericInterfaceBuilder.irBlockBody {
            +derivedInterfaces.asReversed().fold(isDerivedGenericInterfaceBuilder.irBoolean(false) as IrExpression) { elsePart, typeName ->
                isDerivedGenericInterfaceBuilder.irIfThenElse(
                    type = isDerivedGenericInterface.returnType,
                    condition = isDerivedGenericInterfaceBuilder.irEquals(
                        isDerivedGenericInterfaceBuilder.irGet(typeNameParameter),
                        isDerivedGenericInterfaceBuilder.irString(typeName),
                    ),
                    thenPart = isDerivedGenericInterfaceBuilder.irBoolean(true),
                    elsePart = elsePart,
                )
            }
        }

        val registerAbiDelegates = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRtGenericAbiRegisterAbiDelegates")
            returnType = registerAbiDelegatesIntrinsic.owner.returnType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
            parameters = listOf(
                pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                    name = Name.identifier("register")
                    type = registerAbiDelegatesIntrinsic.owner.parameters
                        .single { parameter -> parameter.kind == IrParameterKind.Regular }
                        .type
                    kind = IrParameterKind.Regular
                }, this),
            )
        }
        val registerAbiDelegatesBuilder = DeclarationIrBuilder(pluginContext, registerAbiDelegates.symbol)
        val registerParameter = registerAbiDelegates.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        registerAbiDelegates.body = registerAbiDelegatesBuilder.irBlockBody {
            delegates.forEach { entry ->
                +registerAbiDelegatesBuilder.irCall(function2Invoke).apply {
                    arguments[0] = registerAbiDelegatesBuilder.irGet(registerParameter)
                    arguments[1] = registerAbiDelegatesBuilder.irStringList(pluginContext, listOf, entry.typeArrayShape)
                    arguments[2] = registerAbiDelegatesBuilder.irString(entry.name)
                }
            }
        }

        file.declarations += delegateNamed
        file.declarations += delegatesForSourceType
        file.declarations += isDerivedGenericInterface
        file.declarations += registerAbiDelegates
        return GenericAbiSupportFunctions(
            delegateNamed = delegateNamed.symbol,
            delegatesForSourceType = delegatesForSourceType.symbol,
            isDerivedGenericInterface = isDerivedGenericInterface.symbol,
            registerAbiDelegates = registerAbiDelegates.symbol,
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun DeclarationIrBuilder.genericAbiDelegateEntryList(
        pluginContext: IrPluginContext,
        entryConstructor: IrConstructorSymbol,
        listOf: IrSimpleFunctionSymbol,
        entries: List<KotlinWinRtGenericAbiRegistryEntry>,
    ): IrExpression {
        val parameter = listOf.owner.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        return irCall(listOf).apply {
            arguments[0] = IrVarargImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = parameter.type,
                varargElementType = pluginContext.irBuiltIns.anyNType,
                elements = entries.map { entry ->
                    genericAbiDelegateEntryCall(
                        pluginContext = pluginContext,
                        entryConstructor = entryConstructor,
                        listOf = listOf,
                        entry = entry,
                    )
                },
            )
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun DeclarationIrBuilder.genericAbiDelegateEntryCall(
        pluginContext: IrPluginContext,
        entryConstructor: IrConstructorSymbol,
        listOf: IrSimpleFunctionSymbol,
        entry: KotlinWinRtGenericAbiRegistryEntry,
    ): IrExpression =
        irCall(entryConstructor).apply {
            arguments[0] = irString(entry.name)
            arguments[1] = irString(entry.sourceGenericType)
            arguments[2] = irString(entry.operation)
            arguments[3] = irString(entry.declaration)
            arguments[4] = irStringList(pluginContext, listOf, entry.abiParameterTypes)
            arguments[5] = irStringList(pluginContext, listOf, entry.typeArrayShape)
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerGenericAbiSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        support: GenericAbiSupportFunctions?,
    ) {
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    val genericAbiCall = call.genericAbiSupportIntrinsicCallName() ?: return call
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol ?: return call
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return when (genericAbiCall) {
                        "delegateNamed" -> support?.let {
                            builder.irCall(it.delegateNamed).apply {
                                arguments[0] = call.arguments[1]
                            }
                        } ?: builder.irNull()
                        "delegatesForSourceType" -> support?.let {
                            builder.irCall(it.delegatesForSourceType).apply {
                                arguments[0] = call.arguments[1]
                            }
                        } ?: builder.irCall(
                            pluginContext.referenceFunctions(
                                CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyList")),
                            ).single(),
                        )
                        "isDerivedGenericInterface" -> support?.let {
                            builder.irCall(it.isDerivedGenericInterface).apply {
                                arguments[0] = call.arguments[1]
                            }
                        } ?: builder.irBoolean(false)
                        "registerAbiDelegates" -> support?.let {
                            builder.irCall(it.registerAbiDelegates).apply {
                                arguments[0] = call.arguments[1]
                            }
                        } ?: builder.irUnit()
                        else -> call
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.genericAbiSupportIntrinsicCallName(): String? {
        val function = symbol.owner
        val name = function.name.asString()
        if (name !in GENERIC_ABI_SUPPORT_INTRINSIC_FUNCTIONS) {
            return null
        }
        val ownerClass = function.parent as? IrClass ?: return null
        return name.takeIf {
            ownerClass.fqNameWhenAvailable?.asString() ==
                "io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic"
        }
    }

    private data class GenericAbiSupportFunctions(
        val delegateNamed: IrSimpleFunctionSymbol,
        val delegatesForSourceType: IrSimpleFunctionSymbol,
        val isDerivedGenericInterface: IrSimpleFunctionSymbol,
        val registerAbiDelegates: IrSimpleFunctionSymbol,
    )

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerAuthoringSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val registrar = authoringTypeDetailsRegistrarRegister(pluginContext)
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    if (!call.isAuthoringSupportEnsureInitializedCall()) {
                        return call
                    }
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol ?: return call
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    if (registrar == null) {
                        return builder.irUnit()
                    }
                    return builder.irCall(registrar.register).apply {
                        dispatchReceiver = builder.irGetObject(registrar.registrarClass)
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerAuthoredTypeConstructors(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        authoredTypeNames: Set<String>,
    ) {
        if (authoredTypeNames.isEmpty()) {
            return
        }
        val registrar = authoringTypeDetailsRegistrarRegister(pluginContext) ?: return
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitConstructor(declaration: IrConstructor): IrStatement {
                    val constructor = super.visitConstructor(declaration) as IrConstructor
                    val ownerClass = constructor.parent as? IrClass ?: return constructor
                    val ownerTypeName = ownerClass.fqNameWhenAvailable?.asString() ?: return constructor
                    if (ownerTypeName !in authoredTypeNames) {
                        return constructor
                    }
                    val body = constructor.body as? IrBlockBody ?: return constructor
                    val builder = DeclarationIrBuilder(pluginContext, constructor.symbol, constructor.startOffset, constructor.endOffset)
                    body.statements.add(
                        0,
                        builder.irCall(registrar.register).apply {
                            dispatchReceiver = builder.irGetObject(registrar.registrarClass)
                        },
                    )
                    return constructor
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerAuthoredTypeConstructorCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        authoredTypeNames: Set<String>,
    ) {
        if (authoredTypeNames.isEmpty()) {
            return
        }
        val registrar = authoringTypeDetailsRegistrarRegister(pluginContext) ?: return
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                    val call = super.visitConstructorCall(expression) as IrConstructorCall
                    val constructedClass = call.symbol.owner.parent as? IrClass ?: return call
                    val constructedTypeName = constructedClass.fqNameWhenAvailable?.asString() ?: return call
                    if (constructedTypeName !in authoredTypeNames) {
                        return call
                    }
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol ?: return call
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return builder.irBlock(resultType = call.type) {
                        +builder.irCall(registrar.register).apply {
                            dispatchReceiver = builder.irGetObject(registrar.registrarClass)
                        }
                        +call
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun authoringTypeDetailsRegistrarRegister(
        pluginContext: IrPluginContext,
    ): AuthoringTypeDetailsRegistrar? {
        val registrarClass = pluginContext.referenceClass(
            ClassId.topLevel(FqName("io.github.composefluent.winrt.projections.support.WinRTAuthoringTypeDetailsRegistrar")),
        ) ?: return null
        val register = registrarClass
            .owner
            .declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { function ->
                function.name.asString() == "register" &&
                    function.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
            }
            ?.symbol
            ?: return null
        return AuthoringTypeDetailsRegistrar(registrarClass, register)
    }

    private data class AuthoringTypeDetailsRegistrar(
        val registrarClass: IrClassSymbol,
        val register: IrSimpleFunctionSymbol,
    )

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.isAuthoringSupportEnsureInitializedCall(): Boolean {
        val function = symbol.owner
        if (function.name.asString() != "ensureInitialized") {
            return false
        }
        val ownerClass = function.parent as? IrClass ?: return false
        return ownerClass.fqNameWhenAvailable?.asString() ==
            "io.github.composefluent.winrt.runtime.WinRtAuthoringSupportIntrinsic"
    }
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

private val KOTLIN_SHORT_FQ_NAME =
    FqName("kotlin.Short")

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

private val KOTLIN_UBYTE_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("UByte"))

private val KOTLIN_USHORT_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("UShort"))

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

private val KOTLIN_FUNCTION2_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("Function2"))

private val GENERIC_ABI_SUPPORT_INTRINSIC_FUNCTIONS =
    setOf("delegateNamed", "delegatesForSourceType", "isDerivedGenericInterface", "registerAbiDelegates")

private val WINRT_COM_VTABLE_INVOKER_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.ComVtableInvoker")

private val WINRT_HSTRING_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("HString"))

private val WINRT_REFERENCED_HSTRING_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("ReferencedHString"))

private val WINRT_IWINRT_OBJECT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("IWinRTObject"))

private val WINRT_OBJECT_MARSHALLER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("WinRtObjectMarshaller"))

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

private val WINRT_JVM_FFM_DOWNCALL_HANDLES_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("WinRtJvmFfmDowncallHandles"))

private val JAVA_FOREIGN_PACKAGE_FQ_NAME =
    FqName("java.lang.foreign")

private val JAVA_MEMORY_SEGMENT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("MemorySegment"))

private val JAVA_VALUE_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("ValueLayout"))

private val JAVA_ADDRESS_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("AddressLayout"))

private val JAVA_METHOD_HANDLE_CLASS_ID =
    ClassId(FqName("java.lang.invoke"), Name.identifier("MethodHandle"))

private val WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS = listOf(
    "callUnit",
    "callBoolean",
    "callScalar",
    "getString",
    "getBoolean",
    "getInt32",
    "getUInt32",
    "getInt64",
    "getUInt64",
    "getFloat",
    "getDouble",
    "getStruct",
    "callStruct",
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
    "callProjectedRuntimeClass",
    "callProjectedInterface",
    "callObject",
)

private val WINRT_PROJECTION_INTRINSIC_FUNCTIONS = WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS

internal fun isProjectionIntrinsicFunction(
    name: String,
    ownerFqName: String?,
): Boolean =
    name in WINRT_PROJECTION_INTRINSIC_FUNCTIONS &&
        ownerFqName == WINRT_PROJECTION_INTRINSIC_FQ_NAME.asString()

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

fun readCompilerSupportManifest(path: Path): List<KotlinWinRtCompilerSupportManifestEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "compiler support manifest",
        expectedHeader = COMPILER_SUPPORT_MANIFEST_HEADER,
        parse = ::parseCompilerSupportManifestLine,
    )
    val duplicate = entries
        .groupBy { entry -> Triple(entry.kind, entry.className, entry.sourceFile) }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate compiler support manifest entry for kind ${duplicate!!.first}, class ${duplicate.second}, and source file ${duplicate.third} in $path."
    }
    return entries
}

fun readCompilerSupportManifestIfConfigured(path: String?): List<KotlinWinRtCompilerSupportManifestEntry> {
    val manifestPath = path?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
    require(Files.isRegularFile(manifestPath)) {
        "kotlin-winrt compiler plugin requires compiler support manifest $manifestPath to exist when compilerSupportManifest is configured."
    }
    return readCompilerSupportManifest(manifestPath)
}

private fun parseCompilerSupportManifestLine(line: String): KotlinWinRtCompilerSupportManifestEntry? {
    val parts = line.split('\t', limit = 4)
    if (parts.size < 4) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
        return null
    }
    if (parts[0] !in COMPILER_SUPPORT_MANIFEST_KINDS) {
        return null
    }
    val expected = COMPILER_SUPPORT_MANIFEST_ENTRY_BY_KIND[parts[0]] ?: return null
    if (parts[1] != expected.className || parts[2] != expected.sourceFile) {
        return null
    }
    val entries = parts[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return KotlinWinRtCompilerSupportManifestEntry(
        kind = parts[0],
        className = parts[1],
        sourceFile = parts[2],
        entries = entries,
    )
}

private val COMPILER_SUPPORT_MANIFEST_KINDS: Set<String> =
    setOf("projection-registrar", "generic-type-instantiation", "generic-abi-registry")

private const val COMPILER_SUPPORT_MANIFEST_HEADER: String =
    "kind\tclassName\tsourceFile\tentries"

private val COMPILER_SUPPORT_MANIFEST_ENTRY_BY_KIND: Map<String, CompilerSupportManifestExpectedEntry> =
    mapOf(
        "projection-registrar" to CompilerSupportManifestExpectedEntry(
            className = "io.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic",
            sourceFile = "projection-registrar.tsv",
        ),
        "generic-type-instantiation" to CompilerSupportManifestExpectedEntry(
            className = "io.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations",
            sourceFile = "generic-instantiations.tsv",
        ),
        "generic-abi-registry" to CompilerSupportManifestExpectedEntry(
            className = "io.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic",
            sourceFile = "generic-abi-registry.tsv",
        ),
    )

private data class CompilerSupportManifestExpectedEntry(
    val className: String,
    val sourceFile: String,
)

private const val COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest"

private const val PROJECTION_SUPPORT_INITIALIZER_INTERNAL_NAME_PREFIX: String =
    "io/github/composefluent/winrt/projections/support/WinRTProjectionSupport_"

private const val PROJECTION_SUPPORT_INITIALIZER_CLASS_NAME_PREFIX: String =
    "WinRTProjectionSupport_"

private const val STALE_EVENT_PROJECTION_REGISTRY_CLASS_PATH: String =
    "io/github/composefluent/winrt/projections/support/WinRTEventProjectionRegistry.class"

private const val PROJECTION_REGISTRAR_CHUNK_SIZE: Int = 128

private const val GENERIC_ABI_REGISTRY_LIST_SEPARATOR: String = "\u001F"

fun writeCompilerSupportManifestClass(
    entries: List<KotlinWinRtCompilerSupportManifestEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        Files.deleteIfExists(outputDirectory.resolve("$COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME.class"))
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

fun <T : Any> resolveProjectionRegistrarClasses(
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
    resolve: (String) -> T?,
): List<Pair<KotlinWinRtProjectionRegistrarEntry, T>> =
    entries
        .sortedWith(compareBy(KotlinWinRtProjectionRegistrarEntry::kotlinClassName, KotlinWinRtProjectionRegistrarEntry::projectedTypeName))
        .map { entry ->
            val projectedClass = resolve(entry.kotlinClassName)
            require(projectedClass != null) {
                "kotlin-winrt compiler plugin requires projection registrar input for ${entry.projectedTypeName} " +
                    "to reference resolvable Kotlin class ${entry.kotlinClassName}."
            }
            entry to projectedClass
        }

fun <T : Any> requireCompilerSupportPrerequisite(
    description: String,
    prerequisite: String,
    value: T?,
): T {
    require(value != null) {
        "kotlin-winrt compiler plugin requires $description support input to resolve $prerequisite."
    }
    return value
}

fun readProjectionRegistrarEntries(path: Path): List<KotlinWinRtProjectionRegistrarEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "projection registrar input",
        expectedHeader = PROJECTION_REGISTRAR_HEADER,
        parse = ::parseProjectionRegistrarLine,
    )
    val duplicate = entries
        .groupBy { entry -> entry.kotlinClassName to entry.projectedTypeName }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate projection registrar input for Kotlin class ${duplicate!!.first} and projected type ${duplicate.second} in $path."
    }
    return entries
}

private const val PROJECTION_REGISTRAR_HEADER: String =
    "kotlinClassName\tprojectedTypeName\tkind\tbaseTypeName\tmetadataClassName"

private fun parseProjectionRegistrarLine(line: String): KotlinWinRtProjectionRegistrarEntry? {
    val parts = line.split('\t', limit = 5)
    if (parts.size < 5) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
        return null
    }
    if (parts[2] !in PROJECTION_REGISTRAR_KINDS) {
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

private val PROJECTION_REGISTRAR_KINDS: Set<String> =
    setOf("Interface", "RuntimeClass", "Enum", "Struct", "Delegate")

fun writeProjectionSupportInitializerClass(
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
    outputDirectory: Path,
): String? {
    if (entries.isEmpty()) {
        deleteStaleProjectionSupportInitializerClasses(outputDirectory, currentInternalName = null)
        return null
    }
    val internalName = projectionSupportInitializerInternalName(entries)
    deleteStaleProjectionSupportInitializerClasses(outputDirectory, internalName)
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("compiler-support.tsv", null)
    classWriter.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "initialized",
        "Z",
        null,
        null,
    ).visitEnd()
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(PROJECTION_REGISTRAR_CHUNK_SIZE)
    val initialize = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "initialize",
        "()V",
        null,
        null,
    )
    initialize.visitCode()
    val alreadyInitialized = org.jetbrains.org.objectweb.asm.Label()
    initialize.visitFieldInsn(Opcodes.GETSTATIC, internalName, "initialized", "Z")
    initialize.visitJumpInsn(Opcodes.IFNE, alreadyInitialized)
    initialize.visitInsn(Opcodes.ICONST_1)
    initialize.visitFieldInsn(Opcodes.PUTSTATIC, internalName, "initialized", "Z")
    chunks.indices.forEach { index ->
        initialize.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            internalName,
            projectionRegistrarChunkName(index),
            "()V",
            false,
        )
    }
    initialize.visitLabel(alreadyInitialized)
    initialize.visitInsn(Opcodes.RETURN)
    initialize.visitMaxs(0, 0)
    initialize.visitEnd()

    chunks.forEachIndexed { index, chunk ->
        classWriter.addProjectionRegistrarChunk(projectionRegistrarChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$internalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
    return internalName
}

fun deleteStaleProjectionSupportInitializerClasses(
    outputDirectory: Path,
    currentInternalName: String?,
) {
    val supportDirectory = outputDirectory.resolve(PROJECTION_SUPPORT_INITIALIZER_INTERNAL_NAME_PREFIX)
        .parent
        ?: return
    if (!Files.isDirectory(supportDirectory)) {
        return
    }
    val currentRelativePath = currentInternalName?.let { "$it.class" }
    Files.list(supportDirectory).use { stream ->
        stream
            .filter(Files::isRegularFile)
            .filter { path -> path.fileName.toString().startsWith(PROJECTION_SUPPORT_INITIALIZER_CLASS_NAME_PREFIX) }
            .filter { path -> path.fileName.toString().endsWith(".class") }
            .filter { path ->
                currentRelativePath == null ||
                    outputDirectory.relativize(path).toString().replace('\\', '/') != currentRelativePath
            }
            .forEach(Files::deleteIfExists)
    }
}

fun projectionSupportInitializerInternalName(
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
): String {
    val digest = projectionSupportInitializerHash(entries)
    return "$PROJECTION_SUPPORT_INITIALIZER_INTERNAL_NAME_PREFIX$digest"
}

fun projectionSupportInitializerHash(
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
): String =
    MessageDigest.getInstance("SHA-256")
        .digest(
            entries
                .sortedWith(compareBy(KotlinWinRtProjectionRegistrarEntry::kotlinClassName, KotlinWinRtProjectionRegistrarEntry::projectedTypeName))
                .joinToString(separator = "\n") { entry ->
                    listOf(
                        entry.kotlinClassName,
                        entry.projectedTypeName,
                        entry.kind,
                        entry.baseTypeName,
                        entry.metadataClassName,
                    ).joinToString("\t")
                }
                .toByteArray(StandardCharsets.UTF_8),
        )
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(16)

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

fun readGenericTypeInstantiationEntries(path: Path): List<KotlinWinRtGenericTypeInstantiationEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "generic type instantiation input",
        expectedHeader = GENERIC_TYPE_INSTANTIATION_HEADER,
        parse = ::parseGenericTypeInstantiationLine,
    )
    val duplicate = entries
        .groupBy { entry -> entry.sourceType to entry.className }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate generic type instantiation input for source type ${duplicate!!.first} and class ${duplicate.second} in $path."
    }
    return entries
}

private const val GENERIC_TYPE_INSTANTIATION_HEADER: String =
    "className\tsourceType\tisDelegate\trcwFunctions\tvtableFunctions\tpropertyAccessors\tgenericReturnOnlyRcwFunctions\tprojectedGenericFallbacks\tdependencies"

private fun parseGenericTypeInstantiationLine(line: String): KotlinWinRtGenericTypeInstantiationEntry? {
    val parts = line.split('\t', limit = 9)
    if (parts.size < 9) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank()) {
        return null
    }
    val rcwFunctions = parts[3].splitListFieldOrNull() ?: return null
    val vtableFunctions = parts[4].splitListFieldOrNull() ?: return null
    val propertyAccessors = parts[5].splitListFieldOrNull() ?: return null
    val genericReturnOnlyRcwFunctions = parts[6].splitListFieldOrNull() ?: return null
    val projectedGenericFallbacks = parts[7].splitListFieldOrNull() ?: return null
    val dependencies = parts[8].splitListFieldOrNull() ?: return null
    return KotlinWinRtGenericTypeInstantiationEntry(
        className = parts[0],
        sourceType = parts[1],
        isDelegate = parts[2].toBooleanStrictOrNull() ?: return null,
        rcwFunctions = rcwFunctions,
        vtableFunctions = vtableFunctions,
        propertyAccessors = propertyAccessors,
        genericReturnOnlyRcwFunctions = genericReturnOnlyRcwFunctions,
        projectedGenericFallbacks = projectedGenericFallbacks,
        dependencies = dependencies,
    )
}

private fun String.splitListFieldOrNull(): List<String>? =
    splitSupportListFieldOrNull(',')

data class KotlinWinRtGenericAbiRegistryEntry(
    val kind: String,
    val name: String,
    val sourceGenericType: String,
    val operation: String,
    val declaration: String,
    val abiParameterTypes: List<String>,
    val typeArrayShape: List<String>,
)

fun readGenericAbiRegistryEntries(path: Path): List<KotlinWinRtGenericAbiRegistryEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "generic ABI registry input",
        expectedHeader = GENERIC_ABI_REGISTRY_HEADER,
        parse = ::parseGenericAbiRegistryLine,
    )
    val duplicate = entries
        .groupBy { entry -> entry.duplicateKey() }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate generic ABI registry input for ${duplicate!!} in $path."
    }
    return entries
}

private const val GENERIC_ABI_REGISTRY_HEADER: String =
    "kind\tname\tsourceGenericType\toperation\tdeclaration\tabiParameterTypes\ttypeArrayShape"

private fun KotlinWinRtGenericAbiRegistryEntry.duplicateKey(): String =
    when (kind) {
        "derived-interface" -> "$kind:$name"
        "delegate" -> "$kind:$name:$sourceGenericType"
        else -> "$kind:$name"
    }

fun <T> readCompilerSupportInputEntries(
    manifestPath: Path,
    manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    kind: String,
    description: String,
    read: (Path) -> List<T>,
): List<T> {
    val manifestDirectory = manifestPath.parent ?: Path.of("")
    return manifestEntries
        .asSequence()
        .filter { it.kind == kind }
        .flatMap { entry ->
            val sourcePath = manifestDirectory.resolve(entry.sourceFile)
            require(Files.isRegularFile(sourcePath)) {
                "kotlin-winrt compiler plugin requires $description file $sourcePath declared by $manifestPath to exist."
            }
            val entries = read(sourcePath)
            require(entries.size == entry.entries) {
                "kotlin-winrt compiler plugin expected ${entry.entries} $description entries in $sourcePath declared by $manifestPath, but found ${entries.size}."
            }
            entries.asSequence()
        }
        .toList()
}

private fun <T> readRequiredTsvRows(
    path: Path,
    description: String,
    expectedHeader: String,
    parse: (String) -> T?,
): List<T> {
    val lines = Files.readAllLines(path)
    require(lines.firstOrNull() == expectedHeader) {
        "kotlin-winrt compiler plugin expected $description header '$expectedHeader' in $path."
    }
    return lines
        .asSequence()
        .drop(1)
        .mapIndexedNotNull { index, line ->
            if (line.isBlank()) {
                null
            } else {
                parse(line)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt compiler plugin could not parse $description row ${index + 2} in $path.",
                    )
            }
        }
        .toList()
}

private fun parseGenericAbiRegistryLine(line: String): KotlinWinRtGenericAbiRegistryEntry? {
    val parts = line.split('\t', limit = 7)
    if (parts.size < 7) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank()) {
        return null
    }
    val abiParameterTypes = parts[5].splitGenericAbiRegistryListFieldOrNull() ?: return null
    val typeArrayShape = parts[6].splitGenericAbiRegistryListFieldOrNull() ?: return null
    when (parts[0]) {
        "derived-interface" -> Unit
        "delegate" -> {
            if (parts[2].isBlank() || parts[3].isBlank() || parts[4].isBlank()) {
                return null
            }
            if (abiParameterTypes.isEmpty() || typeArrayShape.isEmpty()) {
                return null
            }
        }
        else -> return null
    }
    return KotlinWinRtGenericAbiRegistryEntry(
        kind = parts[0],
        name = parts[1],
        sourceGenericType = parts[2],
        operation = parts[3],
        declaration = parts[4],
        abiParameterTypes = abiParameterTypes,
        typeArrayShape = typeArrayShape,
    )
}

private fun String.splitGenericAbiRegistryListFieldOrNull(): List<String>? =
    splitSupportListFieldOrNull(GENERIC_ABI_REGISTRY_LIST_SEPARATOR)

private fun String.splitSupportListFieldOrNull(separator: String): List<String>? {
    if (isEmpty()) {
        return emptyList()
    }
    val parts = split(separator)
    if (parts.any(String::isBlank)) {
        return null
    }
    return parts
}

private fun String.splitSupportListFieldOrNull(separator: Char): List<String>? =
    splitSupportListFieldOrNull(separator.toString())

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
