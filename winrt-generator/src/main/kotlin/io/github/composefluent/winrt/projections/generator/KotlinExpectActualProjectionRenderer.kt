package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName

internal class KotlinExpectActualProjectionRenderer(
    private val baseRenderer: KotlinProjectionRenderer,
) : KotlinProjectionFileRenderer {
    override fun render(plan: KotlinTypeProjectionPlan): List<KotlinProjectionFile> {
        if (!canRenderExpectActualSlice(plan)) {
            return listOf(prefixFile("commonMain/kotlin", baseRenderer.render(plan)))
        }
        return listOf(
            renderCommonExpectInterface(plan),
            renderJvmActualInterface(plan),
        )
    }

    private fun canRenderExpectActualSlice(plan: KotlinTypeProjectionPlan): Boolean =
        plan.declarationKind == KotlinProjectionDeclarationKind.Interface &&
            plan.type.kind == WinRtTypeKind.Interface &&
            plan.type.genericParameterCount == 0 &&
            plan.type.events.none { !it.isStatic } &&
            plan.mutableCollectionBindings.isEmpty() &&
            plan.readOnlyCollectionBindings.isEmpty()

    private fun renderCommonExpectInterface(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
            .addModifiers(KModifier.EXPECT)
        baseRenderer.applyCommonTypeShape(builder, plan)
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(implemented.interfaceName))
        }
        plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method -> builder.addFunction(baseRenderer.renderInterfaceMethod(method)) }
        plan.type.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .filter { it.getterMethodName != null }
            .forEach { property -> builder.addProperty(baseRenderer.renderInterfaceProperty(property)) }
        return renderSourceSetFile("commonMain/kotlin", plan, builder.build())
    }

    private fun renderJvmActualInterface(plan: KotlinTypeProjectionPlan): KotlinProjectionFile {
        val builder = TypeSpec.interfaceBuilder(plan.type.name)
            .addModifiers(KModifier.ACTUAL)
        baseRenderer.applyCommonTypeShape(builder, plan)
        plan.type.implementedInterfaces.forEach { implemented ->
            builder.addSuperinterface(baseRenderer.resolveTypeName(implemented.interfaceName))
        }
        plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method -> builder.addFunction(baseRenderer.renderInterfaceMethod(method).withModifier(KModifier.ACTUAL)) }
        plan.type.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .filter { it.getterMethodName != null }
            .forEach { property -> builder.addProperty(baseRenderer.renderInterfaceProperty(property).withModifier(KModifier.ACTUAL)) }
        builder.addType(renderJvmInterfaceNativeProjection(plan))
        baseRenderer.appendCompanionShells(builder, plan)
        return renderSourceSetFile("jvmMain/kotlin", plan, builder.build())
    }

    private fun renderJvmInterfaceNativeProjection(plan: KotlinTypeProjectionPlan): TypeSpec {
        val abiShapes = linkedSetOf<List<KotlinProjectionComArgumentKind>>()
        val builder = TypeSpec.classBuilder("NativeProjection")
            .addModifiers(KModifier.PRIVATE)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("nativeObject", IUNKNOWN_REFERENCE_CLASS_NAME)
                    .build(),
            )
            .addSuperinterface(ClassName(plan.packageName, plan.type.name))
            .addSuperinterface(IWINRT_OBJECT_CLASS_NAME)
            .addProperty(
                PropertySpec.builder("nativeObject", COM_OBJECT_REFERENCE_CLASS_NAME)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer("nativeObject")
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("primaryTypeHandle", WINRT_TYPE_HANDLE_CLASS_NAME.copy(nullable = true))
                    .addModifiers(KModifier.OVERRIDE)
                    .getter(
                        FunSpec.getterBuilder()
                            .addCode("return Metadata.TYPE_HANDLE\n")
                            .build(),
                    )
                    .build(),
            )

        plan.type.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
            builder.addFunction(renderJvmInterfaceProxyMethod(plan.type, method, plan.typesByQualifiedName, abiShapes))
        }
        plan.type.properties.filterNot(WinRtPropertyDefinition::isStatic).filter { it.getterMethodName != null }.forEach { property ->
            builder.addProperty(renderJvmInterfaceProxyProperty(plan.type, property, plan.typesByQualifiedName, abiShapes))
        }
        if (abiShapes.isNotEmpty()) {
            builder.addType(renderJvmAbiHelper(abiShapes))
        }
        return builder.build()
    }

    private fun renderJvmInterfaceProxyMethod(
        slotInterfaceType: io.github.composefluent.winrt.metadata.WinRtTypeDefinition,
        method: WinRtMethodDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRtTypeDefinition>,
        abiShapes: MutableSet<List<KotlinProjectionComArgumentKind>>,
    ): FunSpec {
        val returnBinding = baseRenderer.renderAbiTypeBinding(method.returnTypeName, typesByQualifiedName)
        val parameterBindings = method.parameters.map { parameter ->
            KotlinProjectionAbiParameterBinding(
                name = parameter.name,
                typeBinding = baseRenderer.renderAbiTypeBinding(parameter.typeName, typesByQualifiedName),
            )
        }
        val callPlan = baseRenderer.requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            suppressHResultCheck = method.isNoException,
        )
        val invocation = baseRenderer.renderInlineAbiInvocation(
            invokeTargetExpression = "nativeObject",
            slotExpression = baseRenderer.metadataSlotExpression(slotInterfaceType, method.abiSlotConstantName(slotInterfaceType.methods)),
            callPlan = callPlan,
            renderInvocation = { target, slot, arguments -> renderJvmFfmInvocation(target, slot, arguments, abiShapes) },
        ) ?: error("Generator interface proxy parity failed to emit ${method.name}")
        val objectShape = closableMethodShape(slotInterfaceType, method) ?: runtimeObjectMethodShape(method)
        return FunSpec.builder(objectShape?.name ?: method.projectedMethodName())
            .addModifiers(KModifier.OVERRIDE)
            .addMethodGenericParameters(method, objectShape)
            .addParameters(objectShape?.parameters ?: method.parameters.map { ParameterSpec.builder(it.name, baseRenderer.resolveTypeName(it.typeName)).build() })
            .returns(objectShape?.returnType ?: baseRenderer.resolveTypeName(method.returnTypeName))
            .addCode("%L\n", invocation)
            .build()
    }

    private fun renderJvmInterfaceProxyProperty(
        slotInterfaceType: io.github.composefluent.winrt.metadata.WinRtTypeDefinition,
        property: WinRtPropertyDefinition,
        typesByQualifiedName: Map<String, io.github.composefluent.winrt.metadata.WinRtTypeDefinition>,
        abiShapes: MutableSet<List<KotlinProjectionComArgumentKind>>,
    ): PropertySpec {
        val builder = PropertySpec.builder(
            property.name.replaceFirstChar(Char::lowercase),
            baseRenderer.resolveTypeName(property.typeName),
        )
            .mutable(!property.isReadOnly)
            .addModifiers(KModifier.OVERRIDE)
        val getterCallPlan = baseRenderer.requireAbiCallPlan(
            bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.get",
            returnBinding = baseRenderer.renderAbiTypeBinding(property.typeName, typesByQualifiedName),
            parameterBindings = emptyList(),
            suppressHResultCheck = property.isNoException,
        )
        builder.getter(
            FunSpec.getterBuilder()
                .addCode(
                    "%L\n",
                    baseRenderer.renderInlineAbiInvocation(
                        invokeTargetExpression = "nativeObject",
                        slotExpression = CodeBlock.of("%T.Metadata.%L", baseRenderer.resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_GETTER_SLOT"),
                        callPlan = getterCallPlan,
                        renderInvocation = { target, slot, arguments -> renderJvmFfmInvocation(target, slot, arguments, abiShapes) },
                    ) ?: error("Generator interface proxy parity failed to emit getter ${property.name}"),
                )
                .build(),
        )
        if (!property.isReadOnly) {
            val setterCallPlan = baseRenderer.requireAbiCallPlan(
                bindingName = "${slotInterfaceType.qualifiedName}.${property.name}.set",
                returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", baseRenderer.renderAbiTypeBinding(property.typeName, typesByQualifiedName))),
                suppressHResultCheck = property.isNoException,
            )
            builder.setter(
                FunSpec.setterBuilder()
                    .addParameter("value", baseRenderer.resolveTypeName(property.typeName))
                    .addCode(
                        "%L\n",
                        baseRenderer.renderInlineAbiInvocation(
                            invokeTargetExpression = "nativeObject",
                            slotExpression = CodeBlock.of("%T.Metadata.%L", baseRenderer.resolveTypeName(slotInterfaceType.qualifiedName), "${property.name.uppercase()}_SETTER_SLOT"),
                            callPlan = setterCallPlan,
                            renderInvocation = { target, slot, arguments -> renderJvmFfmInvocation(target, slot, arguments, abiShapes) },
                        ) ?: error("Generator interface proxy parity failed to emit setter ${property.name}"),
                    )
                    .build(),
            )
        }
        return builder.build()
    }

    private fun renderJvmFfmInvocation(
        invokeTargetExpression: String,
        slotExpression: CodeBlock,
        abiArguments: List<KotlinProjectionComArgument>,
        abiShapes: MutableSet<List<KotlinProjectionComArgumentKind>>,
    ): CodeBlock {
        val kinds = abiArguments.map { it.kind }
        val shape = kinds.filterNotNull()
        if (shape.size != kinds.size || shape.any { it !in supportedJvmFfmKinds }) {
            return baseRenderer.renderComVtableInvocation(invokeTargetExpression, slotExpression, abiArguments)
        }
        abiShapes += shape
        return CodeBlock.builder()
            .add("JvmAbi.%L(instance = %L.pointer, slot = %L", jvmAbiInvokeFunctionName(shape), invokeTargetExpression, slotExpression)
            .apply {
                abiArguments.forEachIndexed { index, argument ->
                    add(", arg%L = %L", index, argument.expression)
                }
            }
            .add(")")
            .build()
    }

    private fun renderJvmAbiHelper(abiShapes: Set<List<KotlinProjectionComArgumentKind>>): TypeSpec =
        TypeSpec.objectBuilder("JvmAbi")
            .addModifiers(KModifier.PRIVATE)
            .addProperty(
                PropertySpec.builder("linker", ClassName_JAVA_LINKER)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T.nativeLinker()", ClassName_JAVA_LINKER)
                    .build(),
            )
            .addProperty(
                PropertySpec.builder("downcallHandles", ClassName_CONCURRENT_HASH_MAP.parameterizedBy(String::class.asClassName(), ClassName_METHOD_HANDLE))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T()", ClassName_CONCURRENT_HASH_MAP)
                    .build(),
            )
            .apply {
                abiShapes.sortedWith(compareBy<List<KotlinProjectionComArgumentKind>> { it.size }.thenBy { it.joinToString("_") }).forEach { shape ->
                    addProperty(jvmDescriptorProperty(shape))
                    addFunction(jvmInvokeFunction(shape))
                }
                addFunction(jvmHandleFunction())
                addFunction(jvmVtableEntryFunction())
                addFunction(jvmSegmentFunction())
            }
            .build()

    private fun jvmDescriptorProperty(shape: List<KotlinProjectionComArgumentKind>): PropertySpec =
        PropertySpec.builder(jvmAbiDescriptorName(shape), ClassName_FUNCTION_DESCRIPTOR)
            .addModifiers(KModifier.PRIVATE)
            .initializer(jvmDescriptorInitializer(shape))
            .build()

    private fun jvmInvokeFunction(shape: List<KotlinProjectionComArgumentKind>): FunSpec =
        FunSpec.builder(jvmAbiInvokeFunctionName(shape))
            .addParameter("instance", RAW_COM_PTR_CLASS_NAME)
            .addParameter("slot", Int::class)
            .apply {
                shape.forEachIndexed { index, kind ->
                    addParameter("arg$index", jvmKotlinParameterType(kind))
                }
            }
            .returns(Int::class)
            .addCode(
                CodeBlock.builder()
                    .addStatement("val instanceSegment = segment(instance.value)")
                    .add("return handle(instanceSegment, slot, %S, %L).invoke(instanceSegment", shape.jvmShapeSuffix(), jvmAbiDescriptorName(shape))
                    .apply {
                        shape.forEachIndexed { index, kind ->
                            add(", %L", jvmArgumentCarrierExpression(kind, "arg$index"))
                        }
                    }
                    .add(") as Int\n")
                    .build(),
            )
            .build()

    private fun jvmHandleFunction(): FunSpec =
        FunSpec.builder("handle")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("instance", ClassName_MEMORY_SEGMENT)
            .addParameter("slot", Int::class)
            .addParameter("signature", String::class)
            .addParameter("descriptor", ClassName_FUNCTION_DESCRIPTOR)
            .returns(ClassName_METHOD_HANDLE)
            .addCode(
                """
                val function = vtableEntry(instance, slot)
                val key = "${'$'}{function.address()}:${'$'}signature"
                return downcallHandles.computeIfAbsent(key) {
                    linker.downcallHandle(%T.ofAddress(function.address()), descriptor)
                }
                """.trimIndent(),
                ClassName_MEMORY_SEGMENT,
            )
            .build()

    private fun jvmVtableEntryFunction(): FunSpec =
        FunSpec.builder("vtableEntry")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("pointer", ClassName_MEMORY_SEGMENT)
            .addParameter("slot", Int::class)
            .returns(ClassName_MEMORY_SEGMENT)
            .addCode(
                """
                val objectMemory = pointer.reinterpret(%T.ADDRESS.byteSize())
                val vtable = objectMemory.get(%T.ADDRESS, 0)
                val requiredBytes = maxOf(24L, (slot + 1L) * %T.ADDRESS.byteSize())
                return vtable.reinterpret(requiredBytes).getAtIndex(%T.ADDRESS, slot.toLong())
                """.trimIndent(),
                ClassName_VALUE_LAYOUT,
                ClassName_VALUE_LAYOUT,
                ClassName_VALUE_LAYOUT,
                ClassName_VALUE_LAYOUT,
            )
            .build()

    private fun jvmSegmentFunction(): FunSpec =
        FunSpec.builder("segment")
            .addModifiers(KModifier.PRIVATE)
            .addParameter("address", Long::class)
            .returns(ClassName_MEMORY_SEGMENT)
            .addCode(
                """
                return if (address == 0L) {
                    %T.NULL
                } else {
                    %T.ofAddress(address).reinterpret(Long.MAX_VALUE)
                }
                """.trimIndent(),
                ClassName_MEMORY_SEGMENT,
                ClassName_MEMORY_SEGMENT,
            )
            .build()

    private fun jvmDescriptorInitializer(shape: List<KotlinProjectionComArgumentKind>): CodeBlock =
        CodeBlock.builder()
            .add("%T.of(%T.JAVA_INT, %T.ADDRESS", ClassName_FUNCTION_DESCRIPTOR, ClassName_VALUE_LAYOUT, ClassName_VALUE_LAYOUT)
            .apply {
                shape.forEach { kind ->
                    add(", %L", jvmValueLayoutCode(kind))
                }
            }
            .add(")")
            .build()

    private fun renderSourceSetFile(
        sourceSetPrefix: String,
        plan: KotlinTypeProjectionPlan,
        type: TypeSpec,
    ): KotlinProjectionFile {
        val contents = FileSpec.builder(plan.packageName, plan.type.name)
            .addType(type)
            .build()
            .toString()
        return KotlinProjectionFile(
            relativePath = "$sourceSetPrefix/${plan.relativePath}",
            packageName = plan.packageName,
            contents = contents,
        )
    }

    private fun prefixFile(prefix: String, file: KotlinProjectionFile): KotlinProjectionFile =
        KotlinProjectionFile(
            relativePath = "$prefix/${file.relativePath}",
            packageName = file.packageName,
            contents = file.contents,
        )

    private fun FunSpec.withModifier(modifier: KModifier): FunSpec =
        toBuilder().addModifiers(modifier).build()

    private fun PropertySpec.withModifier(modifier: KModifier): PropertySpec =
        toBuilder().addModifiers(modifier).build()
}

private val ClassName_FUNCTION_DESCRIPTOR = ClassName("java.lang.foreign", "FunctionDescriptor")
private val ClassName_JAVA_LINKER = ClassName("java.lang.foreign", "Linker")
private val ClassName_MEMORY_SEGMENT = ClassName("java.lang.foreign", "MemorySegment")
private val ClassName_VALUE_LAYOUT = ClassName("java.lang.foreign", "ValueLayout")
private val ClassName_METHOD_HANDLE = ClassName("java.lang.invoke", "MethodHandle")
private val ClassName_CONCURRENT_HASH_MAP = ClassName("java.util.concurrent", "ConcurrentHashMap")
private val RAW_COM_PTR_CLASS_NAME = ClassName("io.github.composefluent.winrt.runtime", "RawComPtr")

private val supportedJvmFfmKinds = setOf(
    KotlinProjectionComArgumentKind.Pointer,
    KotlinProjectionComArgumentKind.Int8,
    KotlinProjectionComArgumentKind.Int16,
    KotlinProjectionComArgumentKind.Int32,
    KotlinProjectionComArgumentKind.Int64,
    KotlinProjectionComArgumentKind.Float,
    KotlinProjectionComArgumentKind.Double,
)

private fun jvmAbiDescriptorName(shape: List<KotlinProjectionComArgumentKind>): String =
    "descriptor_${shape.jvmShapeSuffix()}"

private fun jvmAbiInvokeFunctionName(shape: List<KotlinProjectionComArgumentKind>): String =
    "invoke_${shape.jvmShapeSuffix()}"

private fun List<KotlinProjectionComArgumentKind>.jvmShapeSuffix(): String =
    if (isEmpty()) {
        "none"
    } else {
        joinToString("_") { kind ->
            when (kind) {
                KotlinProjectionComArgumentKind.Pointer -> "p"
                KotlinProjectionComArgumentKind.Int8 -> "i8"
                KotlinProjectionComArgumentKind.Int16 -> "i16"
                KotlinProjectionComArgumentKind.Int32 -> "i32"
                KotlinProjectionComArgumentKind.Int64 -> "i64"
                KotlinProjectionComArgumentKind.Float -> "f32"
                KotlinProjectionComArgumentKind.Double -> "f64"
            }
        }
    }

private fun jvmKotlinParameterType(kind: KotlinProjectionComArgumentKind): TypeName =
    when (kind) {
        KotlinProjectionComArgumentKind.Pointer -> RAW_ADDRESS_CLASS_NAME
        KotlinProjectionComArgumentKind.Int8 -> Byte::class.asClassName()
        KotlinProjectionComArgumentKind.Int16 -> Short::class.asClassName()
        KotlinProjectionComArgumentKind.Int32 -> Int::class.asClassName()
        KotlinProjectionComArgumentKind.Int64 -> Long::class.asClassName()
        KotlinProjectionComArgumentKind.Float -> Float::class.asClassName()
        KotlinProjectionComArgumentKind.Double -> Double::class.asClassName()
    }

private fun jvmValueLayoutCode(kind: KotlinProjectionComArgumentKind): CodeBlock =
    when (kind) {
        KotlinProjectionComArgumentKind.Pointer -> CodeBlock.of("%T.ADDRESS", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int8 -> CodeBlock.of("%T.JAVA_BYTE", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int16 -> CodeBlock.of("%T.JAVA_SHORT", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int32 -> CodeBlock.of("%T.JAVA_INT", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Int64 -> CodeBlock.of("%T.JAVA_LONG", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Float -> CodeBlock.of("%T.JAVA_FLOAT", ClassName_VALUE_LAYOUT)
        KotlinProjectionComArgumentKind.Double -> CodeBlock.of("%T.JAVA_DOUBLE", ClassName_VALUE_LAYOUT)
    }

private fun jvmArgumentCarrierExpression(
    kind: KotlinProjectionComArgumentKind,
    argumentName: String,
): CodeBlock =
    when (kind) {
        KotlinProjectionComArgumentKind.Pointer -> CodeBlock.of("segment(%L.value)", argumentName)
        KotlinProjectionComArgumentKind.Int8,
        KotlinProjectionComArgumentKind.Int16,
        KotlinProjectionComArgumentKind.Int32,
        KotlinProjectionComArgumentKind.Int64,
        KotlinProjectionComArgumentKind.Float,
        KotlinProjectionComArgumentKind.Double -> CodeBlock.of("%L", argumentName)
    }
