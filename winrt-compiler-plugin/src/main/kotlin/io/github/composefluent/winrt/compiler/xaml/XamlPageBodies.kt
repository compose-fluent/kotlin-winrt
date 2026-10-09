package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.WinRTXamlDeclarationIndex
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*

/** Bodies execute on the original page; no generated superclass or second COM identity. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class XamlPageBodies(private val index: WinRTXamlDeclarationIndex, private val semanticOnly: Boolean,
    private val dependencyRegistrars: List<String> = emptyList()) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val classes = moduleFragment.files.flatMap { it.declarations }.filterIsInstance<IrClass>()
            .associateBy { it.fqNameWhenAvailable?.asString() }
        val pages = index.pages.associateBy { it.className }
        for (page in index.pages) {
            val klass = requireNotNull(classes[page.className]) { "Missing Kotlin XAML class ${page.className}" }
            fun function(name: Name) = klass.declarations.filterIsInstance<IrSimpleFunction>().single { it.name == name }.also {
                check((it.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == XamlDeclarationKey)
            }
            val properties = klass.declarations.filterIsInstance<IrProperty>().filter {
                (it.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == XamlDeclarationKey
            }.associateBy { it.name.asString() }
            val userDeclarations = klass.declarations.filterNot {
                (it.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == XamlDeclarationKey
            }.toList()
            // FIR appends generated declarations. Keep instance state ahead of user initializers;
            // loading itself occurs only at the completed construction call boundary.
            klass.declarations.removeAll(properties.values.toSet())
            klass.declarations.addAll(0, properties.values)
            // C#/C++ generate fields for x:Name. Kotlin exposes typed properties,
            // whose default JVM getters would collide for firstName/FirstName or
            // with inherited SDK getters. Keep their source names and give only
            // these compiler-owned accessors a distinct platform method name.
            pluginContext.referenceClass(ClassId.topLevel(FqName("kotlin.jvm.JvmName")))?.owner?.let { annotation ->
                val constructor = annotation.constructors.single().symbol
                properties.values.mapNotNull { it.getter }.forEach { getter ->
                    val builder = DeclarationIrBuilder(pluginContext, getter.symbol)
                    getter.annotations += builder.irAnnotation(constructor, emptyList()).apply {
                        arguments[0] = builder.irString("getKotlinWinRTXaml_" + getter.correspondingPropertySymbol!!.owner.name.asString())
                    }
                }
            }
            val functions = (listOf(xamlInitializeName, xamlConstructionName, xamlLoadName, xamlConnectName, xamlBindingName) +
                page.properties.flatMap { it.eventFunctions() } +
                if (page.hasCompiledBindings()) listOf(xamlUpdateBindingsName, xamlBindingsChangedName, xamlBindingsLoadingName,
                    xamlBindingsUnloadedName, xamlRefreshBindingsName, xamlPageBindingConnectName) + page.bindBackNames() +
                    (if (page.hasTemplateScopes()) xamlScopeNames else emptySet()) else emptyList()).map(::function)
            if (semanticOnly) {
                val error = pluginContext.referenceFunctions(CallableId(FqName("kotlin"), Name.identifier("error"))).single()
                for (method in functions + properties.values.flatMap { listOfNotNull(it.getter, it.setter) }) {
                    method.body = DeclarationIrBuilder(pluginContext, method.symbol).irBlockBody {
                        +irCall(error).apply { arguments[0] = irString("XAML semantic-only artifact must not be executed") }
                    }
                }
                if (page.hasCompiledBindings()) XamlCompiledBindingBodies(pluginContext, classes, pages).generate(klass, page, properties)
                continue
            }
            fun runtime(name: String) = pluginContext.referenceFunctions(
                CallableId(FqName("io.github.composefluent.winrt.runtime"), Name.identifier(name))).single()
            fun projection(name: String) = requireNotNull(pluginContext.referenceClass(xamlProjectionClassId(name))) {
                "XAML requires generated projection $name"
            }.owner
            val requireElement = runtime("requireXamlNamedElement")
            val propertyBodies = XamlPropertyBodies(pluginContext, classes)
            propertyBodies.generate(klass, page, properties)
            for (connection in page.connections.filter { it.storageName() != null }) {
                val property = properties.getValue(connection.storageName()!!)
                val field = requireNotNull(property.backingField)
                val getter = requireNotNull(property.getter)
                field.type = getter.returnType.makeNullable()
                field.isFinal = false
                field.initializer = DeclarationIrBuilder(pluginContext, field.symbol).run { irExprBody(irNull(field.type)) }
                getter.body = DeclarationIrBuilder(pluginContext, getter.symbol).irBlockBody {
                    +irReturn(irCall(requireElement).apply {
                        type = getter.returnType
                        typeArguments[0] = getter.returnType
                        arguments[0] = irGetField(irGet(requireNotNull(getter.dispatchReceiverParameter)), field)
                        arguments[1] = irString(page.className); arguments[2] = irString(connection.elementName ?: connection.storageName()!!)
                    })
                }
            }
            if (page.hasCompiledBindings()) {
                val bindingProperty = properties.getValue(xamlBindingStateName.asString())
                val bindingField = requireNotNull(bindingProperty.backingField)
                val bindingClass = requireNotNull(pluginContext.referenceClass(xamlBindingStateId)).owner
                bindingField.initializer = DeclarationIrBuilder(pluginContext, bindingField.symbol).run {
                    irExprBody(irCallConstructor(bindingClass.constructors.single().symbol, emptyList()))
                }
                val getter = requireNotNull(bindingProperty.getter)
                getter.body = DeclarationIrBuilder(pluginContext, getter.symbol).irBlockBody {
                    +irReturn(irGetField(irGet(requireNotNull(getter.dispatchReceiverParameter)), bindingField))
                }
                XamlCompiledBindingBodies(pluginContext, classes, pages).generate(klass, page, properties)
            }
            val state = requireNotNull(properties.getValue(xamlStateName.asString()).backingField)
            val stateClass = requireNotNull(pluginContext.referenceClass(xamlStateId)).owner
            state.initializer = DeclarationIrBuilder(pluginContext, state.symbol).run {
                irExprBody(irCallConstructor(stateClass.constructors.single().symbol, emptyList()))
            }
            val stateGetter = requireNotNull(properties.getValue(xamlStateName.asString()).getter)
            stateGetter.body = DeclarationIrBuilder(pluginContext, stateGetter.symbol).irBlockBody {
                +irReturn(irGetField(irGet(requireNotNull(stateGetter.dispatchReceiverParameter)), state))
            }
            val constructionProperty = properties.getValue(xamlConstructionStateName.asString())
            val constructionState = requireNotNull(constructionProperty.backingField)
            constructionState.initializer = DeclarationIrBuilder(pluginContext, constructionState.symbol).run {
                irExprBody(irCallConstructor(stateClass.constructors.single().symbol, emptyList()))
            }
            val constructionGetter = requireNotNull(constructionProperty.getter)
            constructionGetter.body = DeclarationIrBuilder(pluginContext, constructionGetter.symbol).irBlockBody {
                +irReturn(irGetField(irGet(requireNotNull(constructionGetter.dispatchReceiverParameter)), constructionState))
            }
            val load = function(xamlLoadName)
            val localRegistrars = classes.values.filter { it.fqNameWhenAvailable?.parent()?.asString() ==
                "io.github.composefluent.winrt.generated.xaml" && it.name.asString().startsWith("KotlinXamlApplicationDefinitions") }
            val applicationDefinitions = (localRegistrars + dependencyRegistrars.mapNotNull {
                pluginContext.referenceClass(ClassId.topLevel(FqName(it)))?.owner
            }).distinctBy { it.fqNameWhenAvailable }.sortedBy { it.fqNameWhenAvailable?.asString() }
            check(applicationDefinitions.isNotEmpty()) { "XAML application definitions were not generated before Kotlin compilation" }
            val applicationMetadata = requireNotNull(projection("Microsoft.UI.Xaml.Application").companionObject())
            val loadComponent = applicationMetadata.functions.single { it.name.asString() == "loadComponent" &&
                it.parameters.count { p -> p.kind == IrParameterKind.Regular } == 2 }
            val uri = projection("Windows.Foundation.Uri").constructors.single {
                it.parameters.filter { p -> p.kind == IrParameterKind.Regular }.let { p ->
                    p.size == 1 && p[0].type.classFqName?.asString() == "kotlin.String"
                }
            }
            load.body = DeclarationIrBuilder(pluginContext, load.symbol).irBlockBody {
                applicationDefinitions.forEach { registrar ->
                    +irCall(registrar.functions.single { it.name.asString() == "registerAll" }.symbol).apply {
                        dispatchReceiver = irGetObject(registrar.symbol)
                    }
                }
                propertyBodies.defaults(this, page, properties) {
                    irGet(requireNotNull(load.dispatchReceiverParameter))
                }.forEach { +it }
                +irCall(loadComponent.symbol).apply {
                    dispatchReceiver = irGetObject(applicationMetadata.symbol)
                    arguments[1] = irGet(requireNotNull(load.dispatchReceiverParameter))
                    arguments[2] = irCallConstructor(uri.symbol, emptyList()).apply {
                        arguments[0] = irString("ms-appx:///" + page.resourcePath)
                    }
                }
                if (page.hasCompiledBindings()) {
                    val bindings = XamlCompiledBindingBodies(pluginContext, classes, pages)
                    +bindings.lifecycleSubscription(this, klass, page, function(xamlBindingsLoadingName), irGet(requireNotNull(load.dispatchReceiverParameter)), true)
                    +bindings.lifecycleSubscription(this, klass, page, function(xamlBindingsUnloadedName), irGet(requireNotNull(load.dispatchReceiverParameter)), false)
                }
                +irCall(runtime("completeWinRTXamlHotReloadComponent")).apply {
                    arguments[0] = irGet(requireNotNull(load.dispatchReceiverParameter))
                    arguments[1] = irString(page.className)
                    arguments[2] = irString(page.resourcePath)
                    arguments[3] = irString(page.sourceHash)
                }
            }
            val initialize = function(xamlInitializeName)
            val stateLoad = stateClass.functions.single { it.name.asString() == "load" }
            initialize.body = DeclarationIrBuilder(pluginContext, initialize.symbol).irBlockBody {
                +irCall(stateLoad.symbol).apply {
                    dispatchReceiver = irGetField(irGet(requireNotNull(initialize.dispatchReceiverParameter)), state)
                    arguments[1] = boundReference(pluginContext, load, irGet(requireNotNull(initialize.dispatchReceiverParameter)))
                }
            }
            val complete = function(xamlConstructionName)
            val userInitialize = klass.functions.single { it.name.asString() == "initializeComponent" }
            complete.body = DeclarationIrBuilder(pluginContext, complete.symbol).irBlockBody {
                +irCall(stateLoad.symbol).apply {
                    dispatchReceiver = irGetField(irGet(requireNotNull(complete.dispatchReceiverParameter)), constructionState)
                    arguments[1] = boundReference(pluginContext, userInitialize, irGet(requireNotNull(complete.dispatchReceiverParameter)))
                }
            }
            val binding = function(xamlBindingName)
            check(binding.returnType.isNullable()) { "IComponentConnector projection must have nullable GetBindingConnector" }
            binding.body = DeclarationIrBuilder(pluginContext, binding.symbol).irBlockBody {
                +irReturn(irWhen(binding.returnType, mutableListOf()).apply {
                    val connectionId = binding.parameters.first { it.kind == IrParameterKind.Regular }
                    page.connections.filter { root -> root.isScopeRoot && !root.isTemplateChild &&
                        page.connections.any { it.scopeId == root.scopeId && !it.isTemplateChild && it.bindings.isNotEmpty() }
                    }.forEach { root ->
                        val connector = classes.values.single { it.fqNameWhenAvailable?.parent()?.asString() ==
                            "io.github.composefluent.winrt.generated.xaml" && it.name.asString().startsWith("KotlinXamlPageBindingConnector") }
                        branches += irBranch(irEquals(irGet(connectionId), irInt(root.id)),
                            irCallConstructor(connector.constructors.single().symbol, emptyList()).apply {
                                arguments[0] = irGet(requireNotNull(binding.dispatchReceiverParameter))
                            })
                    }
                    if (page.hasTemplateScopes()) {
                        val connector = classes.values.single { it.fqNameWhenAvailable?.parent()?.asString() ==
                            "io.github.composefluent.winrt.generated.xaml" && it.name.asString().startsWith("KotlinXamlBindingScopeConnector") }
                        val target = binding.parameters.last { it.kind == IrParameterKind.Regular }
                        page.connections.filter { it.isScopeRoot && it.isTemplateChild }.forEach { root ->
                            branches += irBranch(irEquals(irGet(connectionId), irInt(root.id)),
                                irCallConstructor(connector.constructors.single().symbol, emptyList()).apply {
                                    arguments[0] = irGet(requireNotNull(binding.dispatchReceiverParameter))
                                    arguments[1] = irInt(root.scopeId); arguments[2] = irGet(target)
                                    arguments[3] = irBoolean(root.isControlTemplateScope())
                                })
                        }
                    }
                    branches += irElseBranch(irNull(binding.returnType))
                })
            }

            // CSharpPagePass2 emits separate Connect bodies on the page and its
            // binding object; ordinary handlers must never run on both paths.
            for ((connect, bindingOnly) in listOf(function(xamlConnectName) to false) +
                if (page.hasCompiledBindings()) listOf(function(xamlPageBindingConnectName) to true) else emptyList()) {
                val parameters = connect.parameters.filter { it.kind == IrParameterKind.Regular }
                val cast = runtime("asWinRT")
                connect.body = DeclarationIrBuilder(pluginContext, connect.symbol).irBlockBody {
                    +irWhen(pluginContext.irBuiltIns.unitType, mutableListOf()).apply {
                        for (connection in page.connections.filter {
                            if (bindingOnly) !it.isTemplateChild && it.bindings.isNotEmpty()
                            else it.storageName() != null || it.events.isNotEmpty()
                        }) {
                            branches += irBranch(irEquals(irGet(parameters[0]), irInt(connection.id)), irBlock {
                                val targetType = classes[connection.typeName]?.defaultType
                                    ?: projection(connection.typeName).defaultType
                                val target = irTemporary(irCall(cast).apply {
                                    type = targetType; typeArguments[0] = targetType; arguments[0] = irGet(parameters[1])
                                })
                                connection.storageName()?.let { fieldName ->
                                    +irSetField(irGet(requireNotNull(connect.dispatchReceiverParameter)),
                                        requireNotNull(properties.getValue(fieldName).backingField), irGet(target))
                                }
                                if (!bindingOnly && !connection.isTemplateChild) {
                                    (connection.elementName ?: connection.fieldName)?.let { name ->
                                        +irCall(runtime("registerWinRTXamlHotReloadElement")).apply {
                                            arguments[0] = irGet(requireNotNull(connect.dispatchReceiverParameter))
                                            arguments[1] = irString(page.className)
                                            arguments[2] = irString(page.resourcePath)
                                            arguments[3] = irString(page.sourceHash)
                                            arguments[4] = irString(name)
                                            arguments[5] = irGet(target)
                                        }
                                    }
                                }
                                for (event in if (bindingOnly) emptyList() else connection.events) {
                                    val handler = xamlIrFunctions(klass, event.handlerName).single()
                                    val add = (classes[event.declaringTypeName] ?: projection(event.declaringTypeName)).functions.single {
                                        it.name.asString() == "add${event.name}" && it.parameters.count { p -> p.kind == IrParameterKind.Regular } == 1
                                    }
                                    val delegateType = add.parameters.single { it.kind == IrParameterKind.Regular }.type
                                    val delegateClass = requireNotNull(delegateType.classOrNull) {
                                        "XAML requires projected delegate ${event.delegateTypeName}"
                                    }.owner
                                    val invoke = delegateClass.functions.single { it.name.asString() == "invoke" }
                                    val handlerParameters = handler.parameters.filter { it.kind == IrParameterKind.Regular }
                                    val delegateParameters = invoke.parameters.filter { it.kind == IrParameterKind.Regular }
                                    val typeArguments = (delegateType as? IrSimpleType)?.arguments.orEmpty()
                                    fun delegateParameterType(type: IrType): IrType {
                                        val parameterIndex = delegateClass.typeParameters.indexOfFirst { parameter ->
                                            parameter.symbol == (type as? IrSimpleType)?.classifier
                                        }
                                        return typeArguments.getOrNull(parameterIndex)?.typeOrNull ?: type
                                    }
                                    require(!handler.isSuspend && handler.typeParameters.isEmpty() &&
                                        handler.returnType.classFqName == invoke.returnType.classFqName &&
                                        handlerParameters.size == delegateParameters.size &&
                                        handlerParameters.zip(delegateParameters).all { (handlerParameter, delegateParameter) ->
                                            val source = delegateParameterType(delegateParameter.type)
                                            val destination = handlerParameter.type
                                            // C# method groups allow a RoutedEventArgs handler for
                                            // a derived event argument. Keep the same conversion in
                                            // the final Kotlin bridge as in XamlCompiler's plan.
                                            (!source.isNullable() || destination.isNullable()) &&
                                                destination.classOrNull?.let { source.isSubtypeOfClass(it) } == true
                                        }) {
                                        "${page.resourcePath}:${event.location.line}:${event.location.column}: " +
                                            "handler ${event.handlerName} no longer matches ${event.delegateTypeName}; rebuild XAML semantic symbols"
                                    }
                                    +irCall(add.symbol).apply {
                                        dispatchReceiver = irGet(target)
                                        arguments[1] = irSamConversion(boundReference(pluginContext, handler,
                                            irGet(requireNotNull(connect.dispatchReceiverParameter))), delegateType)
                                    }
                                }
                                for (event in connection.bindings.filter { bindingOnly && it.isEvent }) {
                                    +XamlCompiledBindingBodies(pluginContext, classes, pages).eventSubscription(this, klass, event,
                                        irGet(requireNotNull(connect.dispatchReceiverParameter)), irGet(target))
                                }
                                if (bindingOnly && connection.canBeInstantiatedLater) {
                                    val receiver = requireNotNull(connect.dispatchReceiverParameter)
                                    val bindingState = requireNotNull(properties.getValue(xamlBindingStateName.asString()).backingField)
                                    val stateType = requireNotNull(pluginContext.referenceClass(xamlBindingStateId)).owner
                                    +irIfThen(pluginContext.irBuiltIns.unitType,
                                        irCall(stateType.functions.single { it.name.asString() == "connected" }).apply {
                                            dispatchReceiver = irGetField(irGet(receiver), bindingState)
                                            arguments[1] = irInt(connection.id)
                                        }, irCall(function(xamlBindingsChangedName).symbol).apply {
                                            dispatchReceiver = irGet(receiver); arguments[1] = irNull(); arguments[2] = irNull()
                                        })
                                }
                                +irUnit()
                            })
                        }
                        // Connect ignores unknown IDs, including pages without any
                        // generated fields or events. Native requires a nonempty when.
                        branches += irBranch(irTrue(), irUnit())
                    }
                }
            }
            // FIR2IR can optimize same-class default-property reads to IrGetField
            // before this extension replaces the getter. Preserve the named-element
            // load guard for user bodies, without changing generated nullable scope
            // probes used by deferred bindings.
            val guardedFields = page.connections.mapNotNull { connection ->
                connection.storageName()?.let(properties::get)?.let { property ->
                    requireNotNull(property.backingField).symbol to requireNotNull(property.getter)
                }
            }.toMap()
            val propertySetters = page.properties.mapNotNull { declaration ->
                properties.getValue(declaration.name).let { property ->
                    property.setter?.let { requireNotNull(property.backingField).symbol to it }
                }
            }.toMap()
            val guardReads = object : IrElementTransformerVoidWithContext() {
                override fun visitGetField(expression: IrGetField): IrExpression {
                    val value = super.visitGetField(expression) as IrGetField
                    val getter = guardedFields[value.symbol] ?: return value
                    val scope = currentScope?.scope?.scopeOwnerSymbol ?: return value
                    return DeclarationIrBuilder(pluginContext, scope, value.startOffset, value.endOffset).irCall(getter.symbol).apply {
                        dispatchReceiver = value.receiver
                    }
                }
                override fun visitSetField(expression: IrSetField): IrExpression {
                    val value = super.visitSetField(expression) as IrSetField
                    val setter = propertySetters[value.symbol] ?: return value
                    val scope = currentScope?.scope?.scopeOwnerSymbol ?: return value
                    return DeclarationIrBuilder(pluginContext, scope, value.startOffset, value.endOffset).irCall(setter.symbol).apply {
                        dispatchReceiver = value.receiver; arguments[1] = value.value
                    }
                }
            }
            userDeclarations.forEach { it.transform(guardReads, null) }
        }
    }

    private fun boundReference(context: IrPluginContext, function: IrSimpleFunction, receiver: IrExpression) =
        xamlFunctionReference(context, function, receiver)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlFunctionReference(context: IrPluginContext, function: IrSimpleFunction, receiver: IrExpression? = null): IrExpression {
    val signature = (if (receiver == null) listOfNotNull(function.dispatchReceiverParameter?.type) else emptyList()) +
        function.parameters.filter { it.kind == IrParameterKind.Regular }.map { it.type } + function.returnType
    return IrFunctionReferenceImpl(function.startOffset, function.endOffset,
        context.irBuiltIns.functionN(signature.size - 1).symbol.typeWith(signature), function.symbol, function.typeParameters.size).apply {
        dispatchReceiver = receiver
    }
}
