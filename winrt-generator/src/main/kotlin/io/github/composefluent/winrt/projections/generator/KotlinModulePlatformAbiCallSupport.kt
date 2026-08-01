package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.compiler.callsites.WinRTProjectionCallSiteResultStrategy

class KotlinModulePlatformAbiCallSupport(
    private val className: ClassName,
    private val enabledCalls: Set<ModulePlatformAbiCall>? = null,
) {
    private val calls = linkedSetOf<ModulePlatformAbiCall>()
    private val observedCallCounts = linkedMapOf<ModulePlatformAbiCall, Int>()

    internal fun scalarGetter(
        referenceExpression: String,
        slotExpression: CodeBlock,
        helperFunction: String,
    ): CodeBlock? {
        val call = ModulePlatformAbiCall.Simple(helperFunction)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .build()
    }

    internal fun scalarSetter(
        referenceExpression: String,
        slotExpression: CodeBlock,
        helperFunction: String,
        argumentExpression: CodeBlock,
    ): CodeBlock? {
        val call = ModulePlatformAbiCall.Simple(helperFunction)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .add("%L,\n", argumentExpression)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .build()
    }

    internal fun descriptorUnit(
        referenceExpression: String,
        slotExpression: CodeBlock,
        arguments: List<DescriptorIntrinsicArgument>,
        includeReturn: Boolean,
    ): CodeBlock? {
        val shapes = arguments.fixedSignatureShapesOrNull() ?: return null
        val call = ModulePlatformAbiCall.DescriptorUnit(shapes)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .apply {
                if (includeReturn) {
                    add("return (\n")
                    indent()
                }
            }
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .apply {
                if (includeReturn) {
                    unindent()
                    add(")\n")
                }
            }
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }

    internal fun descriptorBoolean(
        referenceExpression: String,
        slotExpression: CodeBlock,
        arguments: List<DescriptorIntrinsicArgument>,
    ): CodeBlock? {
        val shapes = arguments.fixedSignatureShapesOrNull() ?: return null
        val call = ModulePlatformAbiCall.DescriptorBoolean(shapes)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }

    internal fun descriptorScalar(
        referenceExpression: String,
        slotExpression: CodeBlock,
        returnShape: String,
        arguments: List<DescriptorIntrinsicArgument>,
    ): CodeBlock? {
        val shapes = arguments.fixedSignatureShapesOrNull() ?: return null
        val call = ModulePlatformAbiCall.DescriptorScalar(returnShape, shapes)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }

    internal fun structGetter(
        referenceExpression: String,
        slotExpression: CodeBlock,
        struct: ModulePlatformAbiStruct,
        adapterExpression: CodeBlock,
    ): CodeBlock? {
        val call = ModulePlatformAbiCall.StructGetter(struct)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .add("%L,\n", adapterExpression)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .build()
    }

    internal fun structSetter(
        referenceExpression: String,
        slotExpression: CodeBlock,
        struct: ModulePlatformAbiStruct,
        valueExpression: CodeBlock,
        adapterExpression: CodeBlock,
    ): CodeBlock? {
        val call = ModulePlatformAbiCall.StructSetter(struct)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .add("%L,\n", valueExpression)
            .add("%L,\n", adapterExpression)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .build()
    }

    internal fun descriptorStruct(
        referenceExpression: String,
        slotExpression: CodeBlock,
        resultStruct: ModulePlatformAbiStruct,
        adapterExpression: CodeBlock,
        arguments: List<DescriptorIntrinsicArgument>,
    ): CodeBlock? {
        val shapes = arguments.fixedSignatureShapesOrNull() ?: return null
        val call = ModulePlatformAbiCall.DescriptorStruct(resultStruct, shapes)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return (\n")
            .indent()
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .add("%L,\n", adapterExpression)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }

    internal fun projectedObjectGetter(
        referenceExpression: String,
        slotExpression: CodeBlock,
        helperFunction: String,
        @Suppress("UNUSED_PARAMETER") returnType: TypeName,
        wrapType: ClassName,
    ): CodeBlock? {
        val call = ModulePlatformAbiCall.ProjectedReferenceGetter(helperFunction)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .add("return (\n")
            .indent()
            .addProjectedReferenceWrapStart(helperFunction, wrapType)
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .unindent()
            .add(")\n")
            .addProjectedReferenceWrapEnd(helperFunction, wrapType)
            .unindent()
            .add(")\n")
            .build()
    }

    internal fun descriptorProjectedObject(
        referenceExpression: String,
        slotExpression: CodeBlock,
        helperFunction: String,
        @Suppress("UNUSED_PARAMETER") returnType: TypeName,
        wrapType: ClassName,
        arguments: List<DescriptorIntrinsicArgument>,
    ): CodeBlock? {
        val shapes = arguments.fixedSignatureShapesOrNull() ?: return null
        val call = ModulePlatformAbiCall.DescriptorProjectedReference(helperFunction, shapes)
        val target = record(call) ?: return null
        return CodeBlock.builder()
            .openDescriptorIntrinsicArgumentScopes(arguments)
            .add("return (\n")
            .indent()
            .addProjectedReferenceWrapStart(helperFunction, wrapType)
            .add("%T.%L(\n", target.className, target.functionName)
            .indent()
            .add("%L,\n", referenceExpression)
            .add("%L,\n", slotExpression)
            .addDescriptorIntrinsicArgumentExpressions(arguments)
            .unindent()
            .add(")\n")
            .addProjectedReferenceWrapEnd(helperFunction, wrapType)
            .unindent()
            .add(")\n")
            .closeDescriptorIntrinsicArgumentScopes(arguments)
            .build()
    }

    internal fun renderFiles(layout: KotlinProjectionGenerationLayout): List<KotlinProjectionFile> {
        if (calls.isEmpty()) {
            return emptyList()
        }
        return when (layout) {
            KotlinProjectionGenerationLayout.SingleSourceSet -> listOf(renderFile(kind = ModulePlatformAbiCallFileKind.Plain))
            KotlinProjectionGenerationLayout.ExpectActualJvm -> listOf(
                renderFile(kind = ModulePlatformAbiCallFileKind.Expect),
                renderFile(kind = ModulePlatformAbiCallFileKind.ActualJvm),
            )
        }
    }

    private fun renderFile(kind: ModulePlatformAbiCallFileKind): KotlinProjectionFile {
        val fileName = when (kind) {
            ModulePlatformAbiCallFileKind.Plain -> className.simpleName
            ModulePlatformAbiCallFileKind.Expect -> className.simpleName
            ModulePlatformAbiCallFileKind.ActualJvm -> "${className.simpleName}.jvm"
        }
        val type = TypeSpec.objectBuilder(className.simpleName)
            .addModifiers(KModifier.INTERNAL)
            .apply {
                when (kind) {
                    ModulePlatformAbiCallFileKind.Expect -> addModifiers(KModifier.EXPECT)
                    ModulePlatformAbiCallFileKind.ActualJvm -> addModifiers(KModifier.ACTUAL)
                    ModulePlatformAbiCallFileKind.Plain -> Unit
                }
                calls.sortedWith(compareBy<ModulePlatformAbiCall> { it.functionName }.thenBy { it.arguments.joinToString("_") })
                    .forEach { call -> addFunction(renderFunction(call, kind)) }
            }
            .build()
        val contents = FileSpec.builder(className.packageName, fileName)
            .addGeneratedProjectionSuppressions()
            .addType(type)
            .build()
            .toString()
        val packagePath = className.packageName.replace('.', '/')
        val prefix = when (kind) {
            ModulePlatformAbiCallFileKind.Plain -> ""
            ModulePlatformAbiCallFileKind.Expect -> "commonMain/kotlin/"
            ModulePlatformAbiCallFileKind.ActualJvm -> "jvmMain/kotlin/"
        }
        return KotlinProjectionFile(
            relativePath = "$prefix$packagePath/$fileName.kt",
            packageName = className.packageName,
            contents = contents,
        )
    }

    internal fun plannedCalls(minDescriptorCallOccurrences: Int = 2): Set<ModulePlatformAbiCall> =
        observedCallCounts
            .filter { (call, count) ->
                KotlinRuntimeOwnedProjectionCallSites.declarationFor(call) == null &&
                    (!call.requiresFrequencyThreshold || count >= minDescriptorCallOccurrences)
            }
            .keys
            .toSet()

    private fun record(call: ModulePlatformAbiCall): ModulePlatformAbiCallTarget? {
        observedCallCounts[call] = (observedCallCounts[call] ?: 0) + 1
        KotlinRuntimeOwnedProjectionCallSites.declarationFor(call)?.let { declaration ->
            return ModulePlatformAbiCallTarget(
                className = ClassName.bestGuess(declaration.ownerFqName),
                functionName = declaration.functionName,
            )
        }
        if (enabledCalls != null && call !in enabledCalls) {
            return null
        }
        calls += call
        return ModulePlatformAbiCallTarget(
            className = className,
            functionName = call.functionName,
        )
    }

    private fun renderFunction(
        call: ModulePlatformAbiCall,
        kind: ModulePlatformAbiCallFileKind,
    ): FunSpec {
        val callSiteDescriptor = call.moduleLocalCallSiteDescriptorOrNull()
        val builder = FunSpec.builder(call.functionName)
            .addModifiers(KModifier.INTERNAL)
            .apply {
                when (kind) {
                    ModulePlatformAbiCallFileKind.Expect -> addModifiers(KModifier.EXPECT)
                    ModulePlatformAbiCallFileKind.ActualJvm -> addModifiers(KModifier.ACTUAL)
                    ModulePlatformAbiCallFileKind.Plain -> Unit
                }
            }
            .addParameter("instance", COM_OBJECT_REFERENCE_CLASS_NAME)
            .addParameter("slot", Int::class)
            .apply {
                call.extraLeadingParameters().forEach { parameter ->
                    addParameter(parameter.name, parameter.type)
                }
                call.arguments.forEachIndexed { index, shape ->
                    addParameter("arg$index", shape.typeName())
                }
            }
            .returns(call.returnTypeName())
        if (kind != ModulePlatformAbiCallFileKind.Expect) {
            if (callSiteDescriptor != null) {
                builder.addAnnotation(
                    AnnotationSpec.builder(WINRT_PROJECTION_CALL_SITE_CLASS_NAME)
                        .addMember("%S", callSiteDescriptor.encode())
                        .build(),
                )
                builder.addStatement("return TODO(%S)", MODULE_CALL_SITE_PLACEHOLDER)
            } else {
                builder.addCode("%L\n", call.body())
            }
        }
        return builder.build()
    }

    private fun ModulePlatformAbiCall.moduleLocalCallSiteDescriptorOrNull() =
        canonicalCallSiteDescriptorOrNull()?.takeIf { descriptor ->
            descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.UNIT ||
                descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.SCALAR_OUT ||
                descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.STRING_OUT ||
                descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.REFERENCE_OUT ||
                descriptor.resultStrategy == WinRTProjectionCallSiteResultStrategy.STRUCT_OUT
        }

    private fun ModulePlatformAbiCall.body(): CodeBlock =
        when (this) {
            is ModulePlatformAbiCall.Simple -> CodeBlock.builder()
                .add("return %T.%L(instance, slot", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
                .apply {
                    arguments.indices.forEach { index -> add(", arg%L", index) }
                }
                .add(")")
                .build()
            is ModulePlatformAbiCall.DescriptorUnit -> descriptorBody("callUnit", null)
            is ModulePlatformAbiCall.DescriptorBoolean -> descriptorBody("callBoolean", null)
            is ModulePlatformAbiCall.DescriptorScalar -> descriptorBody("callScalar", returnShape)
            is ModulePlatformAbiCall.StructGetter -> CodeBlock.builder()
                .add("return %T.getStruct(instance, slot, adapter)", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
                .build()
            is ModulePlatformAbiCall.StructSetter -> CodeBlock.builder()
                .add("return %T.setStruct(instance, slot, value, adapter)", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
                .build()
            is ModulePlatformAbiCall.DescriptorStruct -> CodeBlock.builder()
                .add("return %T.callStruct(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME)
                .indent()
                .add("instance,\n")
                .add("slot,\n")
                .add("%S,\n", arguments.joinToString(","))
                .add("adapter")
                .apply {
                    arguments.indices.forEach { index -> add(",\narg%L", index) }
                }
                .add(",\n")
                .unindent()
                .add(")")
                .build()
            is ModulePlatformAbiCall.ProjectedReferenceGetter -> CodeBlock.builder()
                .add("return %T.%L(instance, slot) { __result -> __result }", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
                .build()
            is ModulePlatformAbiCall.DescriptorProjectedReference -> CodeBlock.builder()
                .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, helperFunction)
                .indent()
                .add("instance,\n")
                .add("slot,\n")
                .add("%S,\n", arguments.joinToString(","))
                .add("{ __result -> __result }")
                .apply {
                    arguments.indices.forEach { index -> add(",\narg%L", index) }
                }
                .add(",\n")
                .unindent()
                .add(")")
                .build()
        }

    private fun ModulePlatformAbiCall.descriptorBody(
        intrinsicName: String,
        returnShape: String?,
    ): CodeBlock =
        CodeBlock.builder()
            .add("return %T.%L(\n", WINRT_PROJECTION_INTRINSIC_CLASS_NAME, intrinsicName)
            .indent()
            .add("instance,\n")
            .add("slot,\n")
            .apply {
                if (returnShape != null) {
                    add("%S,\n", returnShape)
                }
            }
            .add("%S", arguments.joinToString(","))
            .apply {
                arguments.indices.forEach { index -> add(",\narg%L", index) }
            }
            .add(",\n")
            .unindent()
            .add(")")
            .build()

    private fun CodeBlock.Builder.addProjectedReferenceWrapStart(
        helperFunction: String,
        wrapType: ClassName,
    ): CodeBlock.Builder =
        apply {
            if (!helperFunction.isNullableProjectedReference()) {
                add("%T.Metadata.wrap(\n", wrapType)
                indent()
            }
        }

    private fun CodeBlock.Builder.addProjectedReferenceWrapEnd(
        helperFunction: String,
        wrapType: ClassName,
    ): CodeBlock.Builder =
        apply {
            if (helperFunction.isNullableProjectedReference()) {
                add("?.let { %T.Metadata.wrap(it) }\n", wrapType)
            } else {
                unindent()
                add(")\n")
            }
        }

    private fun ModulePlatformAbiCall.returnTypeName(): TypeName =
        when (this) {
            is ModulePlatformAbiCall.Simple -> simpleIntrinsicReturnType(helperFunction)
            is ModulePlatformAbiCall.DescriptorUnit -> Unit::class.asClassName()
            is ModulePlatformAbiCall.DescriptorBoolean -> Boolean::class.asClassName()
            is ModulePlatformAbiCall.DescriptorScalar -> returnShape.typeName()
            is ModulePlatformAbiCall.StructGetter -> struct.typeName
            is ModulePlatformAbiCall.StructSetter -> Unit::class.asClassName()
            is ModulePlatformAbiCall.DescriptorStruct -> resultStruct.typeName
            is ModulePlatformAbiCall.ProjectedReferenceGetter -> helperFunction.projectedReferenceReturnType()
            is ModulePlatformAbiCall.DescriptorProjectedReference -> helperFunction.projectedReferenceReturnType()
        }

    private val ModulePlatformAbiCall.requiresFrequencyThreshold: Boolean
        get() = when (this) {
            is ModulePlatformAbiCall.DescriptorUnit,
            is ModulePlatformAbiCall.DescriptorBoolean,
            is ModulePlatformAbiCall.DescriptorScalar,
            is ModulePlatformAbiCall.DescriptorStruct,
            is ModulePlatformAbiCall.DescriptorProjectedReference -> true
            is ModulePlatformAbiCall.Simple,
            is ModulePlatformAbiCall.StructGetter,
            is ModulePlatformAbiCall.StructSetter,
            is ModulePlatformAbiCall.ProjectedReferenceGetter -> false
        }

    private fun ModulePlatformAbiCall.extraLeadingParameters(): List<ModulePlatformAbiParameter> =
        when (this) {
            is ModulePlatformAbiCall.StructGetter -> listOf(
                ModulePlatformAbiParameter("adapter", NATIVE_STRUCT_ADAPTER_CLASS_NAME.parameterizedBy(struct.typeName)),
            )
            is ModulePlatformAbiCall.DescriptorStruct -> listOf(
                ModulePlatformAbiParameter("adapter", NATIVE_STRUCT_ADAPTER_CLASS_NAME.parameterizedBy(resultStruct.typeName)),
            )
            is ModulePlatformAbiCall.StructSetter -> listOf(
                ModulePlatformAbiParameter("value", struct.typeName),
                ModulePlatformAbiParameter("adapter", NATIVE_STRUCT_ADAPTER_CLASS_NAME.parameterizedBy(struct.typeName)),
            )
            is ModulePlatformAbiCall.Simple,
            is ModulePlatformAbiCall.DescriptorUnit,
            is ModulePlatformAbiCall.DescriptorBoolean,
            is ModulePlatformAbiCall.DescriptorScalar,
            is ModulePlatformAbiCall.ProjectedReferenceGetter,
            is ModulePlatformAbiCall.DescriptorProjectedReference -> emptyList()
        }

    private fun List<DescriptorIntrinsicArgument>.fixedSignatureShapesOrNull(): List<String>? {
        if (any { it.expressions.size != 1 }) {
            return null
        }
        val shapes = map(DescriptorIntrinsicArgument::shape)
        return shapes.takeIf { it.all(::isFixedSignatureShape) }
    }

    private fun isFixedSignatureShape(shape: String): Boolean =
        shape in fixedShapeTypes

    private fun String.typeName(): TypeName =
        when (this) {
            "String" -> String::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Int8" -> Byte::class.asClassName()
            "UInt8" -> UByte::class.asClassName()
            "Int16" -> Short::class.asClassName()
            "UInt16" -> UShort::class.asClassName()
            "Int32" -> Int::class.asClassName()
            "UInt32" -> UInt::class.asClassName()
            "Int64" -> Long::class.asClassName()
            "UInt64" -> ULong::class.asClassName()
            "Float" -> Float::class.asClassName()
            "Double" -> Double::class.asClassName()
            "RawAddress" -> RAW_ADDRESS_CLASS_NAME
            "Object" -> IWINRT_OBJECT_CLASS_NAME
            else -> error("Unsupported module platform ABI shape $this")
        }

    private fun simpleIntrinsicReturnType(helperFunction: String): TypeName =
        when (helperFunction) {
            "getString" -> String::class.asClassName()
            "getBoolean", "getNoExceptionBoolean" -> Boolean::class.asClassName()
            "getInt32" -> Int::class.asClassName()
            "getUInt32" -> UInt::class.asClassName()
            "getInt64" -> Long::class.asClassName()
            "getUInt64" -> ULong::class.asClassName()
            "getFloat" -> Float::class.asClassName()
            "getDouble" -> Double::class.asClassName()
            "setString",
            "setBoolean",
            "setInt32",
            "setUInt32",
            "setInt64",
            "setUInt64",
            "setFloat",
            "setDouble" -> Unit::class.asClassName()
            else -> error("Unsupported simple module platform ABI call $helperFunction")
        }

    sealed class ModulePlatformAbiCall {
        abstract val functionName: String
        abstract val arguments: List<String>

        data class Simple(val helperFunction: String) : ModulePlatformAbiCall() {
            override val arguments: List<String> = simpleIntrinsicArguments(helperFunction)
            override val functionName: String = helperFunction
        }

        data class DescriptorUnit(override val arguments: List<String>) : ModulePlatformAbiCall() {
            override val functionName: String = "callUnit_${arguments.shapeSuffix()}"
        }

        data class DescriptorBoolean(override val arguments: List<String>) : ModulePlatformAbiCall() {
            override val functionName: String = "callBoolean_${arguments.shapeSuffix()}"
        }

        data class DescriptorScalar(val returnShape: String, override val arguments: List<String>) : ModulePlatformAbiCall() {
            override val functionName: String = "callScalar_${returnShape}_${arguments.shapeSuffix()}"
        }

        data class StructGetter(val struct: ModulePlatformAbiStruct) : ModulePlatformAbiCall() {
            override val arguments: List<String> = emptyList()
            override val functionName: String = "getStruct_${struct.functionSuffix}"
        }

        data class StructSetter(val struct: ModulePlatformAbiStruct) : ModulePlatformAbiCall() {
            override val arguments: List<String> = emptyList()
            override val functionName: String = "setStruct_${struct.functionSuffix}"
        }

        data class DescriptorStruct(
            val resultStruct: ModulePlatformAbiStruct,
            override val arguments: List<String>,
        ) : ModulePlatformAbiCall() {
            override val functionName: String =
                "callStruct_${resultStruct.functionSuffix}_${arguments.shapeSuffix()}"
        }

        data class ProjectedReferenceGetter(val helperFunction: String) : ModulePlatformAbiCall() {
            override val arguments: List<String> = emptyList()
            override val functionName: String = helperFunction.projectedReferenceFunctionName()
        }

        data class DescriptorProjectedReference(
            val helperFunction: String,
            override val arguments: List<String>,
        ) : ModulePlatformAbiCall() {
            override val functionName: String = "${helperFunction.projectedReferenceFunctionName()}_${arguments.shapeSuffix()}"
        }
    }

    private data class ModulePlatformAbiParameter(
        val name: String,
        val type: TypeName,
    )

    data class ModulePlatformAbiStruct(
        val typeName: ClassName,
        val sizeBytes: Int,
        val alignmentBytes: Int,
    ) {
        init {
            require(sizeBytes > 0) { "A module WinRT struct call requires a positive ABI size." }
            require(alignmentBytes > 0) { "A module WinRT struct call requires a positive ABI alignment." }
        }

        internal val functionSuffix: String
            get() = "${generatedLocalIdentifier("", typeName.canonicalName)}_${sizeBytes}_${alignmentBytes}"
    }

    private data class ModulePlatformAbiCallTarget(
        val className: ClassName,
        val functionName: String,
    )

    private enum class ModulePlatformAbiCallFileKind {
        Plain,
        Expect,
        ActualJvm,
    }

    private companion object {
        const val MODULE_CALL_SITE_PLACEHOLDER = "Lowered while compiling the generated WinRT module"

        val WINRT_PROJECTION_CALL_SITE_CLASS_NAME =
            ClassName("io.github.composefluent.winrt.runtime", "WinRTProjectionCallSite")

        val fixedShapeTypes = setOf(
            "String",
            "Boolean",
            "Int8",
            "UInt8",
            "Int16",
            "UInt16",
            "Int32",
            "UInt32",
            "Int64",
            "UInt64",
            "Float",
            "Double",
            "RawAddress",
            "Object",
        )

        fun List<String>.shapeSuffix(): String =
            joinToString("_").ifEmpty { "Void" }

        fun String.isNullableProjectedReference(): Boolean =
            startsWith("getNullableProjected")

        fun String.projectedReferenceFunctionName(): String =
            when (this) {
                "getProjectedRuntimeClass" -> "getInspectable"
                "getNullableProjectedRuntimeClass" -> "getNullableInspectable"
                "getProjectedInterface" -> "getUnknown"
                "getNullableProjectedInterface" -> "getNullableUnknown"
                "callProjectedRuntimeClass" -> "callInspectable"
                "callProjectedInterface" -> "callUnknown"
                else -> error("Unsupported projected reference ABI call $this")
            }

        fun String.projectedReferenceReturnType(): TypeName {
            val baseType = when (this) {
                "getProjectedRuntimeClass",
                "getNullableProjectedRuntimeClass",
                "callProjectedRuntimeClass" -> IINSPECTABLE_REFERENCE_CLASS_NAME
                "getProjectedInterface",
                "getNullableProjectedInterface",
                "callProjectedInterface" -> IUNKNOWN_REFERENCE_CLASS_NAME
                else -> error("Unsupported projected reference ABI call $this")
            }
            return if (isNullableProjectedReference()) baseType.copy(nullable = true) else baseType
        }

        fun simpleIntrinsicArguments(helperFunction: String): List<String> =
            when (helperFunction) {
                "setString" -> listOf("String")
                "setBoolean" -> listOf("Boolean")
                "setInt32" -> listOf("Int32")
                "setUInt32" -> listOf("UInt32")
                "setInt64" -> listOf("Int64")
                "setUInt64" -> listOf("UInt64")
                "setFloat" -> listOf("Float")
                "setDouble" -> listOf("Double")
                else -> emptyList()
            }
    }
}
