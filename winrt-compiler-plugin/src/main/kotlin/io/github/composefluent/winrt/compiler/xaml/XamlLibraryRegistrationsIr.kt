package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTTypeByProjectedName
import io.github.composefluent.winrt.compiler.authoring.requiresComponentAuthoring
import io.github.composefluent.winrt.compiler.authoring.WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION
import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.runtime.XamlFramework
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.name.*

/** CSharpTypeInfoPass2's typed accessors, emitted into the owning library's existing IR.
 * Declaration/schema selection stays in XamlLibrarySchema. Closed-type decisions are
 * shared with the source emitter, so neither backend needs another Kotlin frontend pass.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class XamlLibraryRegistrationsIr(
    private val pluginContext: IrPluginContext,
    private val classes: List<IrClass>,
    private val types: Map<String, IndexedWinRTType>,
    private val names: Set<String>,
    private val members: Map<String, WinUIXamlApplicationTypeMembers>,
    private val framework: XamlFramework = XamlFramework.WinUI,
) {
    private val knownTypes = mutableMapOf<String, IrType>()
    private val runtimePackage = FqName("io.github.composefluent.winrt.runtime")

    private fun remember(type: IrType) {
        knownTypes.putIfAbsent(type.xamlSourceType(), type)
        knownTypes.putIfAbsent(type.makeNotNull().xamlSourceType(), type.makeNotNull())
        (type as? IrSimpleType)?.arguments.orEmpty().mapNotNull { it.typeOrNull }.forEach(::remember)
        (pluginContext.irBuiltIns.primitiveArrayElementTypes[type.classOrNull]
            ?: pluginContext.irBuiltIns.unsignedArraysElementTypes[type.classOrNull])?.let(::remember)
    }

    private fun resolveType(source: String): IrType = knownTypes[source] ?: run {
        val name = source.removeSuffix("?").substringBefore('<')
        val klass = classes.singleOrNull { it.fqNameWhenAvailable?.asString() == name }
            ?: requireNotNull(pluginContext.referenceClass(ClassId.topLevel(FqName(name)))) {
                "Cannot resolve XAML registration Kotlin type: $source"
            }.owner
        val args = xamlKotlinTypeArguments(source).map(::resolveType)
        (if (args.isEmpty()) klass.defaultType else klass.symbol.typeWith(args))
            .let { if (source.endsWith('?')) it.makeNullable() else it }
    }

    private fun runtimeClass(name: String) = requireNotNull(pluginContext.referenceClass(ClassId(runtimePackage, Name.identifier(name)))).owner
    private fun runtime(name: String) = pluginContext.referenceFunctions(CallableId(runtimePackage, Name.identifier(name))).single()
    private fun IrBuilderWithScope.classLiteral(type: IrType): IrExpression = IrClassReferenceImpl(
        startOffset, endOffset, pluginContext.irBuiltIns.kClassClass.starProjectedType, requireNotNull(type.classOrNull), type.makeNotNull())

    private fun IrBuilderWithScope.construct(name: String, values: Map<String, IrExpression>): IrExpression {
        val constructor = runtimeClass(name).constructors.single()
        return irCallConstructor(constructor.symbol, emptyList()).apply {
            values.forEach { (name, expression) ->
                val parameter = constructor.parameters.single { it.name.asString() == name }
                arguments[parameter.indexInParameters] = expression
            }
        }
    }

    private fun IrBuilderWithScope.list(name: String, values: List<IrExpression>): IrExpression {
        val type = runtimeClass(name).defaultType
        val function = pluginContext.referenceFunctions(CallableId(FqName("kotlin.collections"), Name.identifier("listOf")))
            .single { it.owner.parameters.any { p -> p.varargElementType != null } }
        return irCall(function).apply {
            typeArguments[0] = type
            arguments[0] = irVararg(type, values)
        }
    }

    private fun IrBuilderWithScope.lambda(parameters: List<IrType>, result: IrType,
        body: IrBuilderWithScope.(List<IrValueParameter>) -> IrExpression): IrExpression {
        val function = pluginContext.irFactory.buildFun {
            name = SpecialNames.ANONYMOUS
            visibility = DescriptorVisibilities.LOCAL
            returnType = result
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply { parent = scope.scopeOwnerSymbol.owner as IrDeclarationParent }
        val parameters = parameters.mapIndexed { index, type -> function.addValueParameter("p$index", type) }
        function.body = DeclarationIrBuilder(pluginContext, function.symbol).irBlockBody {
            +irReturn(irImplicitCast(body(parameters), result))
        }
        return IrFunctionExpressionImpl(startOffset, endOffset,
            pluginContext.irBuiltIns.functionN(parameters.size).symbol.typeWith(parameters.map { it.type } + result),
            function, IrStatementOrigin.LAMBDA)
    }

    private fun IrBuilderWithScope.staticCall(function: IrSimpleFunction, values: List<IrExpression>): IrExpression =
        irCall(function.symbol).apply {
            function.dispatchReceiverParameter?.let {
                dispatchReceiver = irGetObject(requireNotNull(function.parentClassOrNull).symbol)
            }
            function.parameters.filter { it.kind == IrParameterKind.Regular }.zip(values).forEach { (parameter, value) ->
                arguments[parameter.indexInParameters] = value
            }
        }

    private fun publicFunctions(klass: IrClass) =
        (klass.declarations + klass.companionObject()?.takeIf(::xamlLibraryDeclarationAccessible)?.declarations.orEmpty())
            .filterIsInstance<IrSimpleFunction>().filter { it.visibility == DescriptorVisibilities.PUBLIC && xamlLibraryDeclarationAccessible(it) }

    private fun findFunction(klass: IrClass, method: WinUIXamlApplicationMethod): IrSimpleFunction =
        publicFunctions(klass).single { function -> function.name.asString() == method.name &&
            (function.parentClassOrNull?.kind == ClassKind.OBJECT) == method.isStatic &&
            runCatching { xamlApplicationTypeReference(function.returnType, types, names, framework) == method.returnType &&
                function.parameters.filter { it.kind == IrParameterKind.Regular }
                    .map { xamlApplicationTypeReference(it.type, types, names, framework) } == method.parameterTypes }.getOrDefault(false) }

    private fun IrBuilderWithScope.valueDefinitions(type: WinRTTypeRef, kotlinType: IrType): List<IrExpression> {
        remember(kotlinType)
        val plan = xamlValueTypeRegistrationPlan(type, kotlinType.xamlSourceType())
        val children = plan.children.map { (metadata, source) -> metadata to resolveType(source) }
        val nested = children.flatMap { (metadata, child) -> valueDefinitions(metadata, child) }
        if (!plan.hasDefinition) return nested
        val collection = plan.descriptor as? WinRTCollectionTypeDescriptor
        val boxed = plan.descriptor as? WinRTReferenceTypeDescriptor
        val args = linkedMapOf("name" to irString(type.xamlStandardTypeName()), "type" to classLiteral(kotlinType))
        fun element(prefix: String, index: Int) {
            val (metadata, child) = children[index]
            args["${prefix}TypeName"] = irString(metadata.xamlStandardTypeName())
            args["${prefix}Type"] = classLiteral(child)
        }
        when {
            type.kind == WinRTTypeRefKind.Array -> { args["isArray"] = irTrue(); element("item", 0) }
            boxed?.kind == WinRTReferenceInterfaceKind.Reference -> element("boxed", 0)
            collection?.keyType != null -> { element("key", 0); element("item", 1) }
            else -> element("item", 0)
        }
        if (collection?.kind == WinRTCollectionInterfaceKind.Vector || collection?.kind == WinRTCollectionInterfaceKind.Map) {
            val map = collection.kind == WinRTCollectionInterfaceKind.Map
            val name = if (map) "put" else "add"
            val owner = (if (map) pluginContext.irBuiltIns.mutableMapClass else pluginContext.irBuiltIns.mutableListClass).owner
            val function = owner.functions.single { it.name.asString() == name &&
                it.parameters.count { p -> p.kind == IrParameterKind.Regular } == children.size }
            args[if (map) "addToMap" else "addToVector"] = lambda(
                listOf(pluginContext.irBuiltIns.anyType) + children.map { pluginContext.irBuiltIns.anyNType }, pluginContext.irBuiltIns.unitType) { p ->
                irBlock {
                    +irCall(function.symbol).apply {
                        dispatchReceiver = irAs(irGet(p[0]), owner.symbol.typeWith(children.map { it.second }))
                        function.parameters.filter { it.kind == IrParameterKind.Regular }.forEachIndexed { i, parameter ->
                            arguments[parameter.indexInParameters] = irAs(irGet(p[i + 1]), children[i].second)
                        }
                    }
                    +irUnit()
                }
            }
        }
        return nested + construct("WinUIXamlValueTypeDefinition", args)
    }

    private fun IrBuilderWithScope.propertyDefinition(klass: IrClass, member: WinUIXamlApplicationProperty): IrExpression {
        val property = klass.declarations.filterIsInstance<IrProperty>().single { it.name.asString() == member.name }
        val getter = requireNotNull(property.getter)
        val args = linkedMapOf("name" to irString(member.name), "typeName" to irString(member.type.xamlStandardTypeName()),
            "type" to classLiteral(getter.returnType), "isDependencyProperty" to irBoolean(member.isDependencyProperty),
            "get" to lambda(listOf(pluginContext.irBuiltIns.anyType), pluginContext.irBuiltIns.anyNType) { p ->
                irCall(getter.symbol).apply { dispatchReceiver = irAs(irGet(p[0]), klass.defaultType) }
            }, "valueTypes" to list("WinUIXamlValueTypeDefinition", valueDefinitions(member.type, getter.returnType)))
        if (!member.isReadOnly) {
            val setter = requireNotNull(property.setter)
            args["set"] = lambda(listOf(pluginContext.irBuiltIns.anyType, pluginContext.irBuiltIns.anyNType), pluginContext.irBuiltIns.unitType) { p ->
                irCall(setter.symbol).apply {
                    dispatchReceiver = irAs(irGet(p[0]), klass.defaultType)
                    arguments[setter.parameters.single { it.kind == IrParameterKind.Regular }.indexInParameters] = irAs(irGet(p[1]), getter.returnType)
                }
            }
        }
        return construct("WinUIXamlMemberDefinition", args)
    }

    private fun IrBuilderWithScope.attachedDefinitions(klass: IrClass, schema: WinUIXamlApplicationTypeMembers): List<IrExpression> {
        val methods = schema.methods.filter { it.isStatic && it.isPublic }
        return methods.filter { it.name.startsWith("Get") && it.name.length > 3 &&
            it.parameterTypes.size == 1 && !isWinRTVoidTypeName(it.returnType.typeName) }.map { getter ->
            val name = getter.name.removePrefix("Get")
            val get = findFunction(klass, getter)
            val targetType = get.parameters.single { it.kind == IrParameterKind.Regular }.type
            val setter = methods.singleOrNull { it.name == "Set$name" &&
                it.parameterTypes == getter.parameterTypes + getter.returnType && isWinRTVoidTypeName(it.returnType.typeName) }
            val args = linkedMapOf("name" to irString(name), "typeName" to irString(getter.returnType.xamlStandardTypeName()),
                "type" to classLiteral(get.returnType), "isAttachable" to irTrue(),
                "isDependencyProperty" to irBoolean(schema.properties.any { it.isStatic && it.name == "${name}Property" &&
                    isXamlTypeName(it.type.qualifiedName, "DependencyProperty") }),
                "targetTypeName" to irString(getter.parameterTypes.single().xamlStandardTypeName()), "targetType" to classLiteral(targetType),
                "get" to lambda(listOf(pluginContext.irBuiltIns.anyType), pluginContext.irBuiltIns.anyNType) { p ->
                    staticCall(get, listOf(irAs(irGet(p[0]), targetType)))
                }, "valueTypes" to list("WinUIXamlValueTypeDefinition", valueDefinitions(getter.returnType, get.returnType)))
            if (setter != null) {
                val set = findFunction(klass, setter)
                val parameters = set.parameters.filter { it.kind == IrParameterKind.Regular }
                args["set"] = lambda(listOf(pluginContext.irBuiltIns.anyType, pluginContext.irBuiltIns.anyNType), pluginContext.irBuiltIns.unitType) { p ->
                    staticCall(set, parameters.mapIndexed { i, parameter -> irAs(irGet(p[i]), parameter.type) })
                }
            }
            construct("WinUIXamlMemberDefinition", args)
        }
    }

    fun generate(registrar: IrClass, descriptors: List<WinUIXamlApplicationTypeDescriptor>,
        interfaces: Map<IrClass, List<Pair<WinRTTypeRef, String>>>) {
        val initialize = registrar.functions.single { it.name.asString() == "registerTypes" }
        initialize.body = DeclarationIrBuilder(pluginContext, initialize.symbol).irBlockBody {
            classes.zip(descriptors).forEach { (klass, descriptor) ->
                val name = descriptor.runtimeClassName
                val schema = members.getValue(name)
                klass.superTypes.forEach(::remember)
                if (klass.kind == ClassKind.ENUM_CLASS) {
                    val entries = klass.declarations.filterIsInstance<IrEnumEntry>().filter(::xamlLibraryDeclarationAccessible)
                    +irCall(runtime("registerWinUIXamlEnumType")).apply {
                        typeArguments[0] = klass.defaultType
                        arguments[0] = classLiteral(klass.defaultType)
                        arguments[1] = irString(name)
                        arguments[2] = irVararg(klass.defaultType, entries.map {
                            IrGetEnumValueImpl(startOffset, endOffset, klass.defaultType, it.symbol)
                        })
                    }
                } else {
                    val base = klass.superTypes.firstOrNull { type -> type.classFqName?.asString() == descriptor.baseRuntimeClassName ||
                        resolveIndexedWinRTTypeByProjectedName(type.classFqName?.asString().orEmpty(), types, framework)?.qualifiedName == descriptor.baseRuntimeClassName }
                    fun componentTypes(type: IrClass, seen: MutableSet<IrClass> = mutableSetOf()): List<IndexedWinRTType> =
                        if (!seen.add(type)) emptyList() else type.superTypes.flatMap { superType ->
                            listOfNotNull(resolveIndexedWinRTTypeByProjectedName(superType.classFqName?.asString().orEmpty(), types, framework)) +
                                (superType.classOrNull?.owner?.let { componentTypes(it, seen) } ?: emptyList())
                        }
                    val args = linkedMapOf("type" to classLiteral(klass.defaultType), "name" to irString(name),
                        "baseName" to irString(descriptor.baseRuntimeClassName ?: "System.Object"),
                        "baseType" to classLiteral(base ?: pluginContext.irBuiltIns.anyType),
                        "isWinRTComponent" to irBoolean(requiresComponentAuthoring(componentTypes(klass),
                            klass.hasAnnotation(FqName(WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION)))))
                    if (descriptor.isActivatable) {
                        val constructor = klass.constructors.first { it.visibility == DescriptorVisibilities.PUBLIC &&
                            xamlLibraryDeclarationAccessible(it) && it.parameters.filter { p -> p.kind == IrParameterKind.Regular }.all { p -> p.defaultValue != null } }
                        args["activate"] = lambda(emptyList(), pluginContext.irBuiltIns.anyType) { irCallConstructor(constructor.symbol, emptyList()) }
                    }
                    val singleton = if (klass.kind == ClassKind.OBJECT) klass else klass.companionObject()
                        ?.takeIf { it.visibility == DescriptorVisibilities.PUBLIC && xamlLibraryDeclarationAccessible(it) }
                    singleton?.let { initializer -> args["initializer"] = lambda(emptyList(), pluginContext.irBuiltIns.unitType) { irBlock { +irGetObject(initializer.symbol); +irUnit() } } }
                    schema.contentProperty?.let { args["contentProperty"] = irString(it) }
                    xamlCreateFromStringMethodSource(name, members)?.let { factory ->
                        val ownerName = factory.substringBeforeLast('.')
                        val owner = classes.singleOrNull { it.fqNameWhenAvailable?.asString() == ownerName }
                            ?: requireNotNull(pluginContext.referenceClass(ClassId.topLevel(FqName(ownerName)))).owner
                        val function = publicFunctions(owner).single { it.name.asString() == factory.substringAfterLast('.').removeSurrounding("`") &&
                            it.parameters.filter { p -> p.kind == IrParameterKind.Regular }.let { p -> p.size == 1 && p[0].type.isString() } }
                        args["createFromString"] = lambda(listOf(pluginContext.irBuiltIns.stringType), pluginContext.irBuiltIns.anyType) { p -> staticCall(function, listOf(irGet(p[0]))) }
                    }
                    xamlCollectionRegistrationType(interfaces.getValue(klass))?.let { (type, source) ->
                        // Build the shape twice as distinct IR expressions; IR nodes cannot have two parents.
                        args["shape"] = valueDefinitions(type, resolveType(source)).last()
                        args["valueTypes"] = list("WinUIXamlValueTypeDefinition", valueDefinitions(type, resolveType(source)))
                    }
                    args["members"] = list("WinUIXamlMemberDefinition", schema.properties.filterNot { it.isStatic }
                        .map { propertyDefinition(klass, it) } + attachedDefinitions(klass, schema))
                    +irCall(runtime("registerWinUIXamlTypeDefinition")).apply { arguments[0] = construct("WinUIXamlTypeDefinition", args) }
                }
            }
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun IrType.xamlSourceType(): String {
    val name = requireNotNull(classFqName?.asString())
    val args = (this as? IrSimpleType)?.arguments.orEmpty().map { requireNotNull(it.typeOrNull).xamlSourceType() }
    return name + (if (args.isEmpty()) "" else args.joinToString(", ", "<", ">")) + if (isNullable()) "?" else ""
}
