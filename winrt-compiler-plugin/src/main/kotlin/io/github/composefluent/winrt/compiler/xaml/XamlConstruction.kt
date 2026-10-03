package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrRichFunctionReferenceImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.*

/** Mirrors C++/WinRT create_and_initialize: never load markup from a constructor body. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class XamlConstruction : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val lifecycle = pluginContext.referenceClass(xamlComponentId) ?: return
        val initialize = pluginContext.referenceFunctions(CallableId(
            xamlComponentId.packageFqName, Name.identifier("initializeWinRTXamlComponent"))).single()
        fun IrClass.isComponent(visited: MutableSet<IrClass> = mutableSetOf()): Boolean =
            this == lifecycle.owner || (visited.add(this) && superTypes.any {
                it.classOrNull?.owner?.isComponent(visited) == true
            })
        moduleFragment.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
            override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
                val reference = super.visitFunctionReference(expression) as IrFunctionReference
                val constructor = reference.symbol.owner as? IrConstructor ?: return reference
                if (!(constructor.parent as IrClass).isComponent()) return reference
                // Unadapted references are still legacy nodes at the plugin boundary in 2.4.
                // Preserve reflection identity while exposing the invocation body, as Kotlin's
                // UpgradeCallableReferences does, so ordinary and adapted references share lowering.
                reference.initializeTargetShapeFromSymbol()
                val captured = constructor.parameters.indices.filter { reference.arguments[it] != null }
                val unbound = constructor.parameters.indices.filter { reference.arguments[it] == null }
                val signature = (reference.type as IrSimpleType).arguments.map { requireNotNull(it.typeOrNull) }
                check(unbound.size == signature.size - 1)
                val invoke = pluginContext.irFactory.buildFun {
                    name = SpecialNames.ANONYMOUS
                    visibility = DescriptorVisibilities.LOCAL
                    returnType = signature.last()
                }.apply { parent = requireNotNull(currentDeclarationParent) }
                val parameters = mutableMapOf<Int, IrValueParameter>()
                captured.forEach { index ->
                    parameters[index] = invoke.addValueParameter("captured$index", requireNotNull(reference.arguments[index]).type)
                }
                unbound.forEachIndexed { position, index ->
                    parameters[index] = invoke.addValueParameter("p$position", signature[position])
                }
                invoke.body = DeclarationIrBuilder(pluginContext, invoke.symbol).irBlockBody {
                    val created = irCallConstructor(constructor.symbol, reference.typeArguments.map { requireNotNull(it) }).apply {
                        type = invoke.returnType
                        constructor.parameters.indices.forEach { arguments[it] = irGet(parameters.getValue(it)) }
                    }
                    +irReturn(irCall(initialize).apply {
                        type = created.type
                        typeArguments[0] = created.type
                        arguments[0] = created
                    })
                }
                return IrRichFunctionReferenceImpl(reference.startOffset, reference.endOffset, reference.type,
                    reflectionTargetSymbol = reference.reflectionTarget ?: reference.symbol,
                    overriddenFunctionSymbol = reference.type.classOrNull!!.owner.selectSAMOverriddenFunction().symbol,
                    invokeFunction = invoke, origin = reference.origin).apply {
                    boundValues += captured.map { requireNotNull(reference.arguments[it]) }
                }
            }

            override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                val call = super.visitConstructorCall(expression) as IrConstructorCall
                val owner = call.symbol.owner.parent as? IrClass ?: return call
                if (!owner.isComponent()) return call
                val scope = requireNotNull(currentScope?.scope?.scopeOwnerSymbol) {
                    "XAML construction requires an enclosing declaration"
                }
                return DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset).irCall(initialize).apply {
                    type = call.type
                    typeArguments[0] = call.type
                    arguments[0] = call
                }
            }
        })
    }
}
