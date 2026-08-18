@file:OptIn(org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI::class)

package io.github.composefluent.winrt.compiler.callsites.lowering

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun lowerWinRTManagedProjectionStateOwners(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
) {
    val candidates = mutableListOf<IrClass>()
    moduleFragment.acceptChildrenVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                if (declaration.isManagedProjectionStateCandidate()) {
                    candidates += declaration
                }
                super.visitClass(declaration)
            }
        },
    )
    if (candidates.isEmpty()) {
        return
    }

    val lookupFile = moduleFragment.files.firstOrNull()
    val ownerClass = pluginContext.findManagedProjectionClass(WINRT_MANAGED_PROJECTION_STATE_OWNER_CLASS_ID, lookupFile)
    val accessClass = pluginContext.findManagedProjectionClass(WINRT_MANAGED_PROJECTION_STATE_ACCESS_CLASS_ID, lookupFile)
    val stateClass = pluginContext.findManagedProjectionClass(WINRT_MANAGED_PROJECTION_STATE_CLASS_ID, lookupFile)
    if (ownerClass == null || accessClass == null || stateClass == null) {
        pluginContext.reportManagedProjectionStateError(
            "cannot resolve managed projection state access, owner, and state classes",
        )
        return
    }
    val accessMethod = accessClass.owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == WINRT_MANAGED_PROJECTION_STATE_METHOD_NAME &&
                function.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
        }
        ?.symbol
    val stateConstructor = stateClass.owner.declarations
        .filterIsInstance<IrConstructor>()
        .singleOrNull { constructor ->
            constructor.visibility == DescriptorVisibilities.PUBLIC &&
                constructor.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
        }
        ?.symbol
    if (accessMethod == null || stateConstructor == null) {
        pluginContext.reportManagedProjectionStateError(
            "requires a zero-argument WinRTManagedProjectionState constructor and owner method",
        )
        return
    }

    val depths = mutableMapOf<IrClassSymbol, Int>()
    candidates
        .sortedBy { candidate -> candidate.inheritanceDepth(depths, mutableSetOf()) }
        .forEach { candidate ->
            if (!candidate.hasSupertype(WINRT_MANAGED_PROJECTION_STATE_OWNER_FQ_NAME)) {
                injectManagedProjectionState(candidate, ownerClass, accessMethod, stateClass, stateConstructor, pluginContext)
            }
        }
}

private fun IrClass.isManagedProjectionStateCandidate(): Boolean {
    if (kind != ClassKind.CLASS && kind != ClassKind.OBJECT) {
        return false
    }
    if (isValue || isExpect || isExternal) {
        return false
    }
    if (hasSupertype(WINRT_IWINRT_OBJECT_FQ_NAME)) {
        return false
    }
    return superTypes.any { type ->
        type.classOrNull?.hasProjectedInterface(mutableSetOf()) == true
    }
}

private fun IrClassSymbol.hasProjectedInterface(visited: MutableSet<IrClassSymbol>): Boolean {
    if (!visited.add(this)) {
        return false
    }
    val declaration = owner
    if (
        declaration.kind == ClassKind.INTERFACE &&
        declaration.annotations.any { annotation ->
            annotation.type.classFqName == WINRT_PROJECTED_INTERFACE_ANNOTATION_FQ_NAME
        }
    ) {
        return true
    }
    return declaration.superTypes.any { type ->
        type.classOrNull?.hasProjectedInterface(visited) == true
    }
}

private fun IrClass.hasSupertype(fqName: FqName): Boolean =
    symbol.hasSupertype(fqName, mutableSetOf())

private fun IrClassSymbol.hasSupertype(
    fqName: FqName,
    visited: MutableSet<IrClassSymbol>,
): Boolean {
    if (!visited.add(this)) {
        return false
    }
    if (owner.fqNameWhenAvailable == fqName) {
        return true
    }
    return owner.superTypes.any { type ->
        type.classOrNull?.hasSupertype(fqName, visited) == true
    }
}

private fun IrClass.inheritanceDepth(
    depths: MutableMap<IrClassSymbol, Int>,
    visiting: MutableSet<IrClassSymbol>,
): Int = depths.getOrPut(symbol) {
    if (!visiting.add(symbol)) {
        return@getOrPut 0
    }
    val depth = 1 + (superTypes.maxOfOrNull { type ->
        type.classOrNull?.owner?.inheritanceDepth(depths, visiting) ?: 0
    } ?: 0)
    visiting.remove(symbol)
    depth
}

private fun injectManagedProjectionState(
    klass: IrClass,
    ownerClass: IrClassSymbol,
    accessMethod: IrSimpleFunctionSymbol,
    stateClass: IrClassSymbol,
    stateConstructor: IrConstructorSymbol,
    pluginContext: IrPluginContext,
) {
    val conflictingMethod = klass.declarations
        .filterIsInstance<IrSimpleFunction>()
        .firstOrNull { function ->
            function.name.asString() == WINRT_MANAGED_PROJECTION_STATE_METHOD_NAME &&
                function.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular } &&
                function.origin != IrDeclarationOrigin.FAKE_OVERRIDE
        }
    if (conflictingMethod != null) {
        if (conflictingMethod.returnType.classFqName == WINRT_MANAGED_PROJECTION_STATE_FQ_NAME) {
            klass.superTypes = klass.superTypes + ownerClass.owner.defaultType
            conflictingMethod.overriddenSymbols = (conflictingMethod.overriddenSymbols + accessMethod).distinct()
        } else {
            pluginContext.reportManagedProjectionStateError(
                "cannot inject state into ${klass.fqNameWhenAvailable}: " +
                    "$WINRT_MANAGED_PROJECTION_STATE_METHOD_NAME already has an incompatible return type",
            )
        }
        return
    }

    klass.declarations.removeAll { declaration ->
        declaration is IrSimpleFunction &&
            declaration.origin == IrDeclarationOrigin.FAKE_OVERRIDE &&
            declaration.name.asString() == WINRT_MANAGED_PROJECTION_STATE_METHOD_NAME &&
            declaration.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
    }

    klass.superTypes = klass.superTypes + ownerClass.owner.defaultType
    val field = klass.addField {
        startOffset = klass.startOffset
        endOffset = klass.endOffset
        origin = IrDeclarationOrigin.DEFINED
        name = Name.identifier(WINRT_MANAGED_PROJECTION_STATE_FIELD_NAME)
        type = stateClass.owner.defaultType
        visibility = DescriptorVisibilities.PRIVATE
        isFinal = true
    }
    val initializerBuilder = DeclarationIrBuilder(
        pluginContext,
        field.symbol,
        klass.startOffset,
        klass.endOffset,
    )
    field.initializer = pluginContext.irFactory.createExpressionBody(
        field.startOffset,
        field.endOffset,
        initializerBuilder.irCall(stateConstructor),
    )

    val function = klass.addFunction(
        name = WINRT_MANAGED_PROJECTION_STATE_METHOD_NAME,
        returnType = stateClass.owner.defaultType.makeNullable(),
        modality = Modality.FINAL,
        visibility = DescriptorVisibilities.PUBLIC,
        origin = IrDeclarationOrigin.DEFINED,
        startOffset = klass.startOffset,
        endOffset = klass.endOffset,
    )
    function.overriddenSymbols = listOf(accessMethod)
    val bodyBuilder = DeclarationIrBuilder(
        pluginContext,
        function.symbol,
        klass.startOffset,
        klass.endOffset,
    )
    function.body = bodyBuilder.irBlockBody {
        +irReturn(
            irGetField(
                irGet(requireNotNull(function.dispatchReceiverParameter)),
                field,
            ),
        )
    }
}

private fun IrPluginContext.findManagedProjectionClass(
    classId: ClassId,
    fromFile: IrFile?,
): IrClassSymbol? =
    fromFile?.let { file -> finderForSource(file).findClass(classId) }
        ?: finderForBuiltins().findClass(classId)

@Suppress("DEPRECATION")
private fun IrPluginContext.reportManagedProjectionStateError(detail: String) {
    messageCollector.report(
        CompilerMessageSeverity.ERROR,
        "kotlin-winrt managed projection state $detail.",
        null,
    )
}

private val WINRT_PROJECTED_INTERFACE_ANNOTATION_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTProjectedInterface")
private val WINRT_MANAGED_PROJECTION_STATE_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTManagedProjectionState")
private val WINRT_MANAGED_PROJECTION_STATE_ACCESS_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTManagedProjectionStateAccess")
private val WINRT_MANAGED_PROJECTION_STATE_OWNER_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTManagedProjectionStateOwner")
private val WINRT_IWINRT_OBJECT_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.IWinRTObject")
private val WINRT_MANAGED_PROJECTION_STATE_CLASS_ID =
    ClassId.topLevel(WINRT_MANAGED_PROJECTION_STATE_FQ_NAME)
private val WINRT_MANAGED_PROJECTION_STATE_ACCESS_CLASS_ID =
    ClassId.topLevel(WINRT_MANAGED_PROJECTION_STATE_ACCESS_FQ_NAME)
private val WINRT_MANAGED_PROJECTION_STATE_OWNER_CLASS_ID =
    ClassId.topLevel(WINRT_MANAGED_PROJECTION_STATE_OWNER_FQ_NAME)
private const val WINRT_MANAGED_PROJECTION_STATE_METHOD_NAME = "winRTManagedProjectionState"
private const val WINRT_MANAGED_PROJECTION_STATE_FIELD_NAME = "__kotlinWinRTManagedProjectionState"
