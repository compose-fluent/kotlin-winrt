package io.github.composefluent.winrt.compiler.xaml

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.io.File

/** G1 toolchain probe only; no WinRT contract or production plugin registration. */
@OptIn(ExperimentalCompilerApi::class)
class XamlProbeRegistrar : CompilerPluginRegistrar() {
    override val supportsK2 = true
    override val pluginId = "winrt.xaml.capability.probe"
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val targets = File(requireNotNull(System.getProperty("winrt.test.xamlProbeIndex")))
            .readLines().filter(String::isNotBlank).map { ClassId.topLevel(FqName(it)) }.toSet()
        FirExtensionRegistrarAdapter.registerExtension(object : FirExtensionRegistrar() {
            override fun ExtensionRegistrarContext.configurePlugin() {
                +FirDeclarationGenerationExtension.Factory { ProbeDeclarations(it, targets) }
                +FirSupertypeGenerationExtension.Factory { ProbeSupertypes(it, targets) }
            }
        })
        IrGenerationExtension.registerExtension(ProbeBodies(targets))
    }
}

private object ProbeKey : GeneratedDeclarationKey()
private val elementName = Name.identifier("namedElement")
private val bridgeName = Name.identifier("connect")
private val connectorId = ClassId.topLevel(FqName("probe.Connector"))

private class ProbeDeclarations(session: FirSession, private val targets: Set<ClassId>) :
    FirDeclarationGenerationExtension(session) {
    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext) =
        if (classSymbol.classId in targets) setOf(elementName, bridgeName) else emptySet()

    override fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> {
        if (context == null || context.owner.classId !in targets || callableId.callableName != elementName) return emptyList()
        return listOf(createMemberProperty(context.owner, ProbeKey, elementName, session.builtinTypes.stringType.coneType).symbol)
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        if (context == null || context.owner.classId !in targets || callableId.callableName != bridgeName) return emptyList()
        return listOf(createMemberFunction(context.owner, ProbeKey, bridgeName, session.builtinTypes.stringType.coneType) {
            status { isOverride = true }
        }.symbol)
    }
}

private class ProbeSupertypes(session: FirSession, private val targets: Set<ClassId>) :
    FirSupertypeGenerationExtension(session) {
    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration) = declaration.symbol.classId in targets
    override fun computeAdditionalSupertypes(classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>, typeResolver: TypeResolveService): List<ConeKotlinType> =
        if (resolvedSupertypes.any { it.coneType.classId == connectorId }) emptyList()
        else listOf(connectorId.createConeType(session))
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class ProbeBodies(private val targets: Set<ClassId>) : IrGenerationExtension {
    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        for (page in moduleFragment.files.flatMap { it.declarations }.filterIsInstance<IrClass>()) {
            if (page.fqNameWhenAvailable?.let(ClassId::topLevel) !in targets) continue
            val property = page.declarations.filterIsInstance<IrProperty>().single { it.name == elementName }
            check((property.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == ProbeKey)
            val field = requireNotNull(property.backingField)
            val getter = requireNotNull(property.getter)
            field.initializer = DeclarationIrBuilder(pluginContext, field.symbol).run { irExprBody(irString("element")) }
            getter.body = DeclarationIrBuilder(pluginContext, getter.symbol).irBlockBody {
                +irReturn(irGetField(irGet(requireNotNull(getter.dispatchReceiverParameter)), field))
            }
            val bridge = page.declarations.filterIsInstance<IrSimpleFunction>().single { it.name == bridgeName }
            check((bridge.origin as? IrDeclarationOrigin.GeneratedByPlugin)?.pluginKey == ProbeKey)
            val handler = page.declarations.filterIsInstance<IrSimpleFunction>().single { it.name.asString() == "onClick" }
            bridge.body = DeclarationIrBuilder(pluginContext, bridge.symbol).irBlockBody {
                +irReturn(irCall(handler.symbol).apply { dispatchReceiver = irGet(requireNotNull(bridge.dispatchReceiverParameter)) })
            }
        }
    }
}
