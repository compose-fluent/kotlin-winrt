package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.metadata.*
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.plugin.*
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.*

internal object XamlDeclarationKey : GeneratedDeclarationKey()
internal val xamlConnectorId = ClassId.topLevel(FqName("microsoft.ui.xaml.markup.IComponentConnector"))
internal val xamlStateId = ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinRTXamlLoadState"))
internal val xamlComponentId = ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinRTXamlComponent"))
internal val xamlStateName = Name.identifier("_kotlinXamlState")
internal val xamlConstructionStateName = Name.identifier("_kotlinXamlConstructionState")
internal val xamlConstructionName = Name.identifier("_kotlinXamlCompleteConstruction")
internal val xamlLoadName = Name.identifier("_kotlinXamlLoad")
internal val xamlInitializeName = Name.identifier("_kotlinXamlInitialize")
internal val xamlConnectName = Name.identifier("connect")
internal val xamlBindingName = Name.identifier("getBindingConnector")
internal val xamlBindingStateId = ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinRTXamlBindingState"))
internal val xamlBindingStateName = Name.identifier("_kotlinXamlBindings")
internal val xamlUpdateBindingsName = Name.identifier("_kotlinXamlUpdateBindings")
internal val xamlBindingsChangedName = Name.identifier("_kotlinXamlBindingsChanged")
internal val xamlBindingsLoadingName = Name.identifier("_kotlinXamlBindingsLoading")
internal val xamlBindingsUnloadedName = Name.identifier("_kotlinXamlBindingsUnloaded")
internal val xamlRefreshBindingsName = Name.identifier("updateBindings")
internal val xamlBindingScopeId = ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinRTXamlBindingScope"))
internal val xamlBindingScopeOwnerId = ClassId.topLevel(FqName("io.github.composefluent.winrt.runtime.WinRTXamlBindingScopeOwner"))
internal val xamlScopeUpdateName = Name.identifier("_kotlinXamlUpdateScope")
internal val xamlScopeConnectName = Name.identifier("_kotlinXamlConnectScope")
internal val xamlScopeWriteBackName = Name.identifier("_kotlinXamlWriteBackScope")
internal val xamlScopeCreateName = Name.identifier("_kotlinXamlCreateScopeConnector")
internal val xamlScopeNames = setOf(xamlScopeUpdateName, xamlScopeConnectName, xamlScopeWriteBackName, xamlScopeCreateName)
internal fun WinRTXamlPageDeclaration.hasTemplateScopes() = connections.any { it.isTemplateChild && it.isScopeRoot }

internal fun WinRTXamlPageDeclaration.hasCompiledBindings() = connections.any { it.bindings.isNotEmpty() }
internal fun WinRTXamlPageDeclaration.bindBackNames(): Set<Name> = connections.flatMap { connection ->
    connection.bindings.filter { !connection.isTemplateChild && it.mode == "TwoWay" }.map { binding -> binding.bindBackName(connection) }
}.toSet()
internal fun WinRTXamlBindingDeclaration.bindBackName(connection: WinRTXamlConnectionDeclaration): Name =
    Name.identifier("_kotlinXamlBindBack${connection.id}_$name")
internal fun WinRTXamlConnectionDeclaration.storageName(): String? =
    if (isTemplateChild) null else fieldName ?: if (bindings.isNotEmpty()) "_kotlinXamlConnection$id" else null

// Same namespace casing contract as KotlinProjectionTypeResolver.projectionClassNameForQualifiedName.
internal fun xamlProjectionClassId(name: String): ClassId = ClassId(
    FqName(name.substringBeforeLast('.', "").lowercase()), Name.identifier(name.substringAfterLast('.')),
)

internal class XamlFirRegistrar(index: WinRTXamlDeclarationIndex) : FirExtensionRegistrar() {
    private val pages = index.pages.associateBy { ClassId.topLevel(FqName(it.className)) }
    override fun ExtensionRegistrarContext.configurePlugin() {
        +FirDeclarationGenerationExtension.Factory { XamlDeclarations(it, pages) }
        +FirSupertypeGenerationExtension.Factory { XamlSupertypes(it, pages) }
        +org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension.Factory { XamlNameCheckers(it, pages) }
    }
}

private class XamlDeclarations(session: FirSession, private val pages: Map<ClassId, WinRTXamlPageDeclaration>) :
    FirDeclarationGenerationExtension(session) {
    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>, context: MemberGenerationContext): Set<Name> {
        val page = pages[classSymbol.classId] ?: return emptySet()
        return page.connections.mapNotNull { it.storageName()?.let(Name::identifier) }.toSet() +
            setOf(xamlStateName, xamlConstructionStateName, xamlConstructionName, xamlLoadName, xamlInitializeName, xamlConnectName, xamlBindingName) +
            if (page.hasCompiledBindings()) setOf(xamlBindingStateName, xamlUpdateBindingsName, xamlBindingsChangedName,
                xamlBindingsLoadingName, xamlBindingsUnloadedName, xamlRefreshBindingsName) + page.bindBackNames() +
                (if (page.hasTemplateScopes()) xamlScopeNames else emptySet()) else emptySet()
    }

    override fun generateProperties(callableId: CallableId, context: MemberGenerationContext?): List<FirPropertySymbol> {
        val owner = context?.owner ?: return emptyList()
        val page = pages[owner.classId] ?: return emptyList()
        val name = callableId.callableName
        val element = page.connections.singleOrNull { it.storageName() == name.asString() }
        val state = name == xamlStateName || name == xamlConstructionStateName
        val type = if (state) xamlStateId else if (name == xamlBindingStateName) xamlBindingStateId
            else element?.typeName?.let(::xamlProjectionClassId) ?: return emptyList()
        return listOf(createMemberProperty(owner, XamlDeclarationKey, name, type.createConeType(session)) {
            if (state || name == xamlBindingStateName || element?.fieldName == null) visibility = Visibilities.Private
        }.symbol)
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        val page = pages[owner.classId] ?: return emptyList()
        val name = callableId.callableName
        if (name in xamlScopeNames && page.hasTemplateScopes()) {
            return listOf(createMemberFunction(owner, XamlDeclarationKey, name,
                if (name == xamlScopeCreateName) session.builtinTypes.nullableAnyType.coneType else session.builtinTypes.unitType.coneType) {
                status { isOverride = true }
                if (name != xamlScopeCreateName) valueParameter(Name.identifier("scope"), xamlBindingScopeId.createConeType(session))
                if (name == xamlScopeUpdateName) valueParameter(Name.identifier("initial"), session.builtinTypes.booleanType.coneType)
                else valueParameter(Name.identifier(if (name == xamlScopeWriteBackName) "bindingId" else "connectionId"), session.builtinTypes.intType.coneType)
                if (name == xamlScopeCreateName || name == xamlScopeConnectName) valueParameter(Name.identifier("target"), session.builtinTypes.nullableAnyType.coneType)
            }.symbol)
        }
        val bindingMethod = page.hasCompiledBindings() && name in setOf(xamlUpdateBindingsName, xamlBindingsChangedName,
            xamlBindingsLoadingName, xamlBindingsUnloadedName, xamlRefreshBindingsName) + page.bindBackNames()
        if (!bindingMethod && name !in setOf(xamlLoadName, xamlInitializeName, xamlConstructionName, xamlConnectName, xamlBindingName)) return emptyList()
        val result = if (name == xamlBindingName) xamlConnectorId.createConeType(session, nullable = true)
            else session.builtinTypes.unitType.coneType
        return listOf(createMemberFunction(owner, XamlDeclarationKey, name, result) {
            if (name == xamlLoadName || (bindingMethod && name != xamlRefreshBindingsName)) visibility = Visibilities.Private
            if (name == xamlUpdateBindingsName) valueParameter(Name.identifier("initial"), session.builtinTypes.booleanType.coneType)
            if (name in setOf(xamlBindingsChangedName, xamlBindingsLoadingName, xamlBindingsUnloadedName) + page.bindBackNames()) {
                valueParameter(Name.identifier("sender"), session.builtinTypes.nullableAnyType.coneType)
                valueParameter(Name.identifier("args"), session.builtinTypes.nullableAnyType.coneType)
            }
            if (name == xamlInitializeName || name == xamlConstructionName) status { isOverride = true }
            if (name == xamlConnectName || name == xamlBindingName) {
                status { isOverride = true }
                valueParameter(Name.identifier("connectionId"), session.builtinTypes.intType.coneType)
                valueParameter(Name.identifier("target"), session.builtinTypes.nullableAnyType.coneType)
            }
        }.symbol)
    }
}

private class XamlSupertypes(session: FirSession, private val pages: Map<ClassId, WinRTXamlPageDeclaration>) : FirSupertypeGenerationExtension(session) {
    override fun needTransformSupertypes(declaration: FirClassLikeDeclaration) = declaration.symbol.classId in pages
    override fun computeAdditionalSupertypes(classLikeDeclaration: FirClassLikeDeclaration,
        resolvedSupertypes: List<FirResolvedTypeRef>, typeResolver: TypeResolveService): List<ConeKotlinType> =
        (listOf(xamlConnectorId, xamlComponentId) +
            if (pages[classLikeDeclaration.symbol.classId]?.hasTemplateScopes() == true) listOf(xamlBindingScopeOwnerId) else emptyList())
            .filterNot { id -> resolvedSupertypes.any { it.coneType.classId == id } }
            .map { it.createConeType(session) }
}
