package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.WinUiXamlPageDeclaration
import io.github.composefluent.winrt.metadata.WinUiXamlPropertyDeclaration
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetEnumValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.isNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.*

/** Ordinary x:Property fields and per-property events mirror CSharpPagePass1/2.tt. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class XamlPropertyBodies(private val pluginContext: IrPluginContext, private val classes: Map<String?, IrClass>) {
    fun generate(klass: IrClass, page: WinUiXamlPageDeclaration, properties: Map<String, IrProperty>) {
        val eventClass = requireNotNull(pluginContext.referenceClass(xamlPropertyEventId)).owner
        for (declaration in page.properties) {
            val property = properties.getValue(declaration.name)
            val field = requireNotNull(property.backingField)
            field.isFinal = false // Read-only fields can still receive InitializeXProperties defaults.
            field.initializer = DeclarationIrBuilder(pluginContext, field.symbol).run { irExprBody(zeroValue(this, field.type)) }
            val getter = requireNotNull(property.getter)
            getter.origin = IrDeclarationOrigin.GeneratedByPlugin(XamlDeclarationKey)
            getter.body = DeclarationIrBuilder(pluginContext, getter.symbol).irBlockBody {
                +irReturn(irGetField(irGet(requireNotNull(getter.dispatchReceiverParameter)), field))
            }
            if (declaration.isReadOnly) continue
            val event = properties.getValue(declaration.eventStorageName().asString())
            val eventField = requireNotNull(event.backingField)
            eventField.initializer = DeclarationIrBuilder(pluginContext, eventField.symbol).run {
                irExprBody(irCallConstructor(eventClass.constructors.single().symbol, emptyList()))
            }
            val eventGetter = requireNotNull(event.getter)
            eventGetter.body = DeclarationIrBuilder(pluginContext, eventGetter.symbol).irBlockBody {
                +irReturn(irGetField(irGet(requireNotNull(eventGetter.dispatchReceiverParameter)), eventField))
            }
            val setter = requireNotNull(property.setter)
            // JVM's property lowering can replace DEFAULT_PROPERTY_ACCESSOR
            // calls in the owning class with a field write even after its body
            // changes. This setter owns an event and is no longer a default accessor.
            setter.origin = IrDeclarationOrigin.GeneratedByPlugin(XamlDeclarationKey)
            setter.body = DeclarationIrBuilder(pluginContext, setter.symbol).irBlockBody {
                val receiver = requireNotNull(setter.dispatchReceiverParameter)
                val value = setter.parameters.single { it.kind == IrParameterKind.Regular }
                val old = irGetField(irGet(receiver), field)
                // C# != uses value comparison for strings and value types, and
                // identity for other reference properties.
                val differs = if (declaration.isValueType || field.type.makeNotNull().isString())
                    irNotEquals(old, irGet(value)) else irEquals(irEqeqeq(old, irGet(value)), irFalse())
                +irIfThen(pluginContext.irBuiltIns.unitType, differs, irBlock {
                    +irSetField(irGet(receiver), field, irGet(value))
                    +irCall(eventClass.functions.single { it.name.asString() == "raise" }).apply {
                        dispatchReceiver = irGetField(irGet(receiver), eventField)
                        arguments[1] = irGet(receiver); arguments[2] = irString(declaration.name)
                    }
                })
            }
            for (name in declaration.eventFunctions()) {
                val method = klass.functions.single { it.name == name }
                method.body = DeclarationIrBuilder(pluginContext, method.symbol).irBlockBody {
                    +irCall(eventClass.functions.single {
                        it.name.asString() == if (name.asString().startsWith("add")) "add" else "remove"
                    }).apply {
                        dispatchReceiver = irGetField(irGet(requireNotNull(method.dispatchReceiverParameter)), eventField)
                        arguments[1] = irGet(method.parameters.single { it.kind == IrParameterKind.Regular })
                    }
                }
            }
        }
    }

    /** Called inside the guarded LoadComponent operation, after type registration. */
    fun defaults(builder: IrBuilderWithScope, page: WinUiXamlPageDeclaration, properties: Map<String, IrProperty>,
        receiver: () -> IrExpression): List<IrExpression> = with(builder) {
        page.properties.mapNotNull { declaration ->
            val field = requireNotNull(properties.getValue(declaration.name).backingField)
            val value = when {
                declaration.defaultValueMarkup != null -> {
                    // Upstream InitializeXProperties uses XamlReader.Load only
                    // for the explicit complex default's source fragment.
                    val metadata = requireNotNull(pluginContext.referenceClass(xamlProjectionClassId("Microsoft.UI.Xaml.Markup.XamlReader")))
                        .owner.companionObject()!!
                    val load = metadata.functions.single { it.name.asString() == "load" &&
                        it.parameters.count { p -> p.kind == IrParameterKind.Regular } == 1 }
                    irAs(irCall(load.symbol).apply {
                        dispatchReceiver = irGetObject(metadata.symbol)
                        arguments[1] = irString(requireNotNull(declaration.defaultValueMarkup))
                    }, field.type)
                }
                declaration.defaultValue != null -> {
                    // Reuse the scanner-generated literal conversion bridge,
                    // including mapped scalar and SDK CreateFromString handling.
                    val convert = pluginContext.referenceFunctions(CallableId(FqName("io.github.composefluent.winrt.generated.xaml"),
                        Name.identifier("kotlinWinUiXamlMemberValue"))).single()
                    irCall(convert).apply {
                        type = field.type; typeArguments[0] = field.type
                        arguments[0] = irString(requireNotNull(declaration.defaultValue))
                    }
                }
                else -> return@mapNotNull null
            }
            irSetField(receiver(), field, value)
        }
    }

    private fun zeroValue(builder: IrBuilderWithScope, type: IrType): IrExpression = with(builder) {
        val constant = IrConstImpl.defaultValueForType(startOffset, endOffset, type)
        if (type.isNullable() || constant.kind != IrConstKind.Null) return@with constant
        val klass = requireNotNull(type.classOrNull).owner
        if (klass.kind == org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS &&
            classes[klass.fqNameWhenAvailable?.asString()] === klass) {
            // registerWinUiXamlEnumType maps application enum ordinals to Int32.
            // Its ordinal zero is available before the page's load-time registrars.
            return@with IrGetEnumValueImpl(startOffset, endOffset, type,
                klass.declarations.filterIsInstance<IrEnumEntry>().first().symbol)
        }
        val default = pluginContext.referenceFunctions(CallableId(FqName("io.github.composefluent.winrt.runtime"),
            Name.identifier("defaultWinUiXamlValue"))).single()
        irBlock(resultType = type) {
            // Projected structs/enums register their ABI adapter in Metadata.
            klass.companionObject()?.let { +irGetObject(it.symbol) }
            +irAs(irCall(default).apply {
                arguments[0] = IrClassReferenceImpl(startOffset, endOffset,
                    pluginContext.irBuiltIns.kClassClass.starProjectedType, requireNotNull(type.classOrNull), type)
            }, type)
        }
    }
}
