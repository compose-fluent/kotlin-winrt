package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Native counterpart of Runtime2 TypeMapInfo's per-type metadata provider initialization. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun initializeNativeProjectionMetadata(
    module: IrModuleFragment,
    context: IrPluginContext,
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
) {
    val byMetadata = entries.filter { it.metadataClassName.isNotBlank() }.groupBy { it.metadataClassName }
    if (byMetadata.isEmpty()) return
    val register = requireNotNull(context.findFunctionSymbols(
        CallableId(FqName("io.github.composefluent.winrt.runtime"), Name.identifier("registerGeneratedProjectionTypeIndex")),
        module.files.first(),
    ).singleOrNull { symbol -> symbol.owner.parameters.count { it.kind == IrParameterKind.Regular } == 5 })
    module.acceptChildrenVoid(object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) = element.acceptChildrenVoid(this)
        override fun visitClass(declaration: IrClass) {
            declaration.acceptChildrenVoid(this)
            val registrations = byMetadata[declaration.fqNameWhenAvailable?.asString()] ?: return
            val projectedClass = declaration.parent as IrClass
            declaration.declarations.filterIsInstance<IrConstructor>().forEach { constructor ->
                val body = constructor.body as? IrBlockBody ?: return@forEach
                val builder = DeclarationIrBuilder(context, constructor.symbol)
                val calls = registrations.map { entry ->
                    require(projectedClass.fqNameWhenAvailable?.asString() == entry.kotlinClassName)
                    builder.irCall(register).apply {
                        arguments[0] = IrClassReferenceImpl(0, 0, context.irBuiltIns.kClassClass.owner.defaultType,
                            projectedClass.symbol, projectedClass.defaultType)
                        arguments[1] = builder.irString(entry.projectedTypeName)
                        arguments[2] = builder.irString(entry.kind)
                        arguments[3] = builder.irString(entry.baseTypeName)
                        arguments[4] = builder.irString(entry.interfaceIid)
                    }
                }
                val returnIndex = body.statements.indexOfFirst { it is IrReturn && it.returnTargetSymbol == constructor.symbol }
                body.statements.addAll(if (returnIndex >= 0) returnIndex else body.statements.size, calls)
            }
        }
    })
}

/**
 * CsWinRT 3 keeps SDK types in one implementation assembly (ImplGenerator.EmitTypeForwards),
 * while InteropGenerator tracks concrete type signatures at interop uses. Native has no assembly
 * forwarding or reflection loader: preserve that ownership, but attach registration edges to the
 * using body rather than a module-wide table of every SDK KClass. Unreachable bodies and their
 * registrations can then be eliminated together by the Native linker.
 *
 * Emit the registration expression directly into the using body. Internal helper functions
 * become Native DLL roots, while file-private helpers cannot safely be referenced from other
 * files or public inline bodies in serialized KLIBs. Direct expressions avoid both problems.
 * Collect before editing bodies so the new expressions never become discovery roots.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun lowerNativeProjectionRegistrations(
    module: IrModuleFragment,
    context: IrPluginContext,
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
) {
    if (entries.isEmpty()) return
    val lookupFile = module.files.firstOrNull() ?: return
    val byClass = entries.groupBy { it.kotlinClassName }
    val byMetadata = entries.filter { it.metadataClassName.isNotBlank() }.groupBy { it.metadataClassName }
    val resolved = mutableMapOf<KotlinWinRTProjectionRegistrarEntry, Pair<IrClassSymbol, IrClassSymbol?>>()
    val pending = mutableListOf<() -> Unit>()
    val uses = NativeProjectionUseCollector(entries)
    val register = requireNotNull(context.findFunctionSymbols(
        CallableId(FqName("io.github.composefluent.winrt.runtime"), Name.identifier("registerGeneratedProjectionTypeIndex")),
        lookupFile,
    ).singleOrNull { symbol -> symbol.owner.parameters.count { it.kind == IrParameterKind.Regular } == 5 })

    fun registration(entry: KotlinWinRTProjectionRegistrarEntry, builder: DeclarationIrBuilder): IrExpression {
        val (projectedClass, metadataClass) = resolved.getOrPut(entry) {
            val projectedClass = requireNotNull(context.findClassSymbol(
                ClassId.topLevel(FqName(entry.kotlinClassName)), lookupFile,
            )) { "Cannot resolve Native projection type ${entry.kotlinClassName}" }
            val metadataClass = entry.metadataClassName.takeIf(String::isNotBlank)?.let { name ->
                requireNotNull(projectedClass.owner.declarations.filterIsInstance<IrClass>()
                    .singleOrNull { it.fqNameWhenAvailable?.asString() == name }?.symbol) {
                    "Cannot resolve Native projection metadata $name"
                }
            }
            projectedClass to metadataClass
        }
        // Metadata initialization supplies the synchronized per-type registration. Types
        // without Metadata use the runtime's locked, idempotent index without another flag.
        if (metadataClass != null) return builder.irGetObject(metadataClass)
        return builder.irCall(register).apply {
            arguments[0] = IrClassReferenceImpl(0, 0, context.irBuiltIns.kClassClass.owner.defaultType,
                projectedClass, projectedClass.owner.defaultType)
            arguments[1] = builder.irString(entry.projectedTypeName)
            arguments[2] = builder.irString(entry.kind)
            arguments[3] = builder.irString(entry.baseTypeName)
            arguments[4] = builder.irString(entry.interfaceIid)
        }
    }

    module.transformChildrenVoid(object : IrElementTransformerVoidWithContext() {
        override fun visitBody(body: IrBody): IrBody {
            val result = super.visitBody(body)
            val scope = currentScope?.scope?.scopeOwnerSymbol ?: return result
            // Const fields remain literals in both header and implementation KLIBs. Their
            // values are observed at actual read/use sites, not registered during declaration.
            if ((scope.owner as? IrField)?.correspondingPropertySymbol?.owner?.isConst == true) return result
            // A projection's own constructor/metadata must not recursively initialize its
            // Metadata (notably while enum entries are still being constructed). The owning
            // Metadata constructor performs registration; callers root that existing object.
            val enclosingEntries = mutableSetOf<KotlinWinRTProjectionRegistrarEntry>()
            var enclosing = (scope.owner as? IrDeclaration)?.parent
            while (enclosing is IrDeclaration) {
                if (enclosing is IrClass) {
                    val name = enclosing.fqNameWhenAvailable?.asString()
                    byClass[name]?.let(enclosingEntries::addAll)
                    byMetadata[name]?.let(enclosingEntries::addAll)
                }
                enclosing = enclosing.parent
            }
            val selected = uses.collect(result).filterNot { it in enclosingEntries }
            if (selected.isEmpty()) return result
            val builder = DeclarationIrBuilder(context, scope, result.startOffset, result.endOffset)
            pending += {
                val calls = selected.map { entry -> registration(entry, builder) }
                when (result) {
                    is IrBlockBody -> {
                        // Kotlin constructors must delegate before executing ordinary statements.
                        val delegation = result.statements.indexOfFirst { it is IrDelegatingConstructorCall }
                        result.statements.addAll(if (delegation >= 0) delegation + 1 else 0, calls)
                    }
                    is IrExpressionBody -> {
                        val original = result.expression
                        result.expression = builder.irBlock(resultType = original.type) {
                            calls.forEach { +it }
                            +original
                        }
                    }
                    else -> Unit
                }
            }
            return result
        }
    })
    pending.forEach { it() }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class NativeProjectionUseCollector(entries: List<KotlinWinRTProjectionRegistrarEntry>) {
    private companion object {
        val identityLabelConstructors = setOf(
            "io.github.composefluent.winrt.runtime.WinUIXamlTypeDefinition",
            "io.github.composefluent.winrt.runtime.WinUIXamlMemberDefinition",
            "io.github.composefluent.winrt.runtime.WinUIXamlValueTypeDefinition",
            "io.github.composefluent.winrt.runtime.WinRTProjectionInboundCallSite",
            "io.github.composefluent.winrt.runtime.WinRTProjectionParameter",
        )
    }
    private val byClass = entries.groupBy { it.kotlinClassName }
    private val byMetadata = entries.filter { it.metadataClassName.isNotBlank() }.groupBy { it.metadataClassName }
    private val byName = entries.groupBy { it.projectedTypeName }

    /** These runtime definitions carry type identity beside KClass and typed callbacks.
     * Their literal labels do not perform name-based activation/member lookup. CsWinRT
     * discovery follows those typed references too, rather than treating metadata labels
     * as arbitrary reflection. Still visit every callback and keep ordinary user strings. */
    private fun visitMemberDemandChildren(element: IrElement, visitor: IrVisitorVoid) {
        val call = element as? IrFunctionAccessExpression
        val identityLabels = when (call) {
            is IrConstructorCall -> (call.symbol.owner.parent as? IrClass)?.fqNameWhenAvailable?.asString() in
                identityLabelConstructors
            is IrCall -> call.symbol.owner.fqNameWhenAvailable?.asString() ==
                "io.github.composefluent.winrt.runtime.Projections.registerAuthoredRuntimeClassType"
            else -> false
        }
        if (call != null && identityLabels) {
            call.arguments.forEach { argument ->
                if (argument != null && (argument !is IrConst || argument.value !is String))
                    argument.accept(visitor, null)
            }
        } else element.acceptChildrenVoid(visitor)
    }

    /** Discover user name-based uses before call-site lowering adds ABI identity strings.
     * Like CsWinRT Discover's business-assembly scan, bookkeeping introduced by Emit must
     * not widen member demand. Callbacks and nonliteral arguments still have real uses. */
    fun collectNameBasedTypes(element: IrElement): Set<String> = buildSet {
        element.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (element is IrConst) (element.value as? String)?.let { name ->
                    byName[name]?.forEach { add(it.projectedTypeName) }
                }
                visitMemberDemandChildren(element, this)
            }
        })
    }

    /** CsWinRT discovers executable constructor/factory uses separately from type identity. */
    fun collectConstructionTypes(element: IrElement): Set<String> = buildSet {
        fun factoryOwner(type: IrClass?): IrClass? =
            if (type?.name?.asString() in setOf("ActivationFactory", "ComposableFactory")) type?.parent as? IrClass else null
        fun keep(type: IrClass?) {
            byClass[type?.fqNameWhenAvailable?.asString()]?.forEach { add(it.projectedTypeName) }
        }
        fun factoryType(type: IrType) {
            keep(factoryOwner(type.classOrNull?.owner))
            (type as? IrSimpleType)?.arguments?.forEach { it.typeOrNull?.let(::factoryType) }
        }
        element.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                when (element) {
                    is IrExpression -> factoryType(element.type)
                    is IrValueDeclaration -> factoryType(element.type)
                    is IrFunction -> factoryType(element.returnType)
                }
                if (element is IrDeclarationReference) {
                    val declaration = element.symbol.owner as? IrDeclaration
                    val owner = declaration?.parent as? IrClass
                    val constructed = when {
                        declaration is IrConstructor -> owner
                        factoryOwner(declaration as? IrClass) != null -> factoryOwner(declaration as? IrClass)
                        factoryOwner(owner) != null -> factoryOwner(owner)
                        owner?.isCompanion == true && (declaration as? IrDeclarationWithName)?.name?.asString() == "create" -> owner.parent as? IrClass
                        else -> null
                    }
                    keep(constructed)
                }
                element.acceptChildrenVoid(this)
            }
        })
    }

    /** Outbound member edges and inbound/exposed inheritance are different roots, as in
     * CsWinRT InteropTypeDiscovery. A user implementation must keep its complete ABI. */
    fun collectInstanceMembers(element: IrElement,
        nameBasedTypes: Set<String> = collectNameBasedTypes(element),
    ): Pair<Set<String>, Set<String>> {
        val members = nameBasedTypes.mapTo(linkedSetOf()) { "$it\t*" }
        val exposed = linkedSetOf<String>()
        fun reference(declaration: IrDeclaration?, seen: MutableSet<IrDeclaration> = mutableSetOf()) {
            if (declaration == null || !seen.add(declaration)) return
            val member = when (declaration) {
                is IrSimpleFunction -> declaration.correspondingPropertySymbol?.owner ?: declaration
                is IrField -> declaration.correspondingPropertySymbol?.owner ?: declaration
                else -> declaration
            } as? IrDeclarationWithName ?: return
            val owner = member.parent as? IrClass
            if (owner != null && !owner.isCompanion && member !is IrConstructor) {
                byClass[owner.fqNameWhenAvailable?.asString()]?.forEach {
                    members += "${it.projectedTypeName}\t${member.name.asString()}"
                }
            }
            if (declaration is IrSimpleFunction) declaration.overriddenSymbols.forEach { reference(it.owner, seen) }
        }
        fun expose(type: IrClass, seen: MutableSet<IrClass> = mutableSetOf()) {
            if (!seen.add(type)) return
            byClass[type.fqNameWhenAvailable?.asString()]?.forEach { exposed += it.projectedTypeName }
            type.superTypes.forEach { it.classOrNull?.owner?.let { base -> expose(base, seen) } }
        }
        element.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (element is IrDeclarationReference) reference(element.symbol.owner as? IrDeclaration)
                if (element is IrClass && element.fqNameWhenAvailable?.asString() !in byClass) {
                    element.superTypes.forEach { it.classOrNull?.owner?.let { base -> expose(base) } }
                }
                element.acceptChildrenVoid(this)
            }
        })
        return members to exposed
    }

    /** Member names are semantic Kotlin names, scoped to their canonical projection owner.
     * Accessor pairs and overloads deliberately form one demand unit. */
    fun collectStaticMembers(element: IrElement,
        nameBasedTypes: Set<String> = collectNameBasedTypes(element),
    ): Set<String> = buildSet {
        nameBasedTypes.forEach { add("$it\t*") }
        fun reference(declaration: IrDeclaration?) {
            val member = when (declaration) {
                is IrSimpleFunction -> declaration.correspondingPropertySymbol?.owner ?: declaration
                is IrField -> declaration.correspondingPropertySymbol?.owner ?: declaration
                else -> declaration
            } as? IrDeclarationWithName ?: return
            val companion = member.parent as? IrClass ?: return
            if (!companion.isCompanion) return
            val owner = companion.parent as? IrClass ?: return
            byClass[owner.fqNameWhenAvailable?.asString()]?.forEach {
                add("${it.projectedTypeName}\t${member.name.asString()}")
            }
        }
        element.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                if (element is IrDeclarationReference) reference(element.symbol.owner as? IrDeclaration)
                element.acceptChildrenVoid(this)
            }
        })
    }

    fun collect(element: IrElement, includeDeclarations: Boolean = false): List<KotlinWinRTProjectionRegistrarEntry> {
        val selected = linkedSetOf<KotlinWinRTProjectionRegistrarEntry>()
        val seen = mutableSetOf<String>()
        fun type(type: IrType) {
            (type as? IrSimpleType)?.arguments?.forEach { it.typeOrNull?.let(::type) }
            val klass = type.classOrNull?.owner ?: return
            val name = klass.fqNameWhenAvailable?.asString() ?: return
            if (!seen.add(name)) return
            byClass[name]?.let(selected::addAll)
            // Static calls can mention only Companion/Metadata rather than an instance
            // type. Their declaring projection remains an implementation dependency.
            (klass.parent as? IrClass)?.let { type(it.defaultType) }
            byMetadata[name]?.let {
                selected.addAll(it)
                (klass.parent as? IrClass)?.let { owner -> type(owner.defaultType) }
            }
            // Base/interface identity is required even when no member on that base is called.
            klass.superTypes.forEach(::type)
        }
        element.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                when (element) {
                    is IrExpression -> type(element.type)
                    is IrValueDeclaration -> type(element.type)
                }
                when (element) {
                    is IrClassReference -> type(element.classType)
                    is IrTypeOperatorCall -> type(element.typeOperand)
                    is IrMemberAccessExpression<*> -> element.typeArguments.forEach { it?.let(::type) }
                    is IrConst -> (element.value as? String)?.let { byName[it]?.let(selected::addAll) }
                }
                if (includeDeclarations) {
                    val owner = when (element) {
                        is IrFunctionAccessExpression -> element.symbol.owner.parent
                        is IrFieldAccessExpression -> element.symbol.owner.parent
                        else -> null
                    }
                    (owner as? IrClass)?.let { type(it.defaultType) }
                }
                element.acceptChildrenVoid(this)
            }
            // Nested declarations own their own registration dependencies.
            override fun visitFunction(declaration: IrFunction) {
                if (includeDeclarations) {
                    type(declaration.returnType)
                    declaration.parameters.forEach { type(it.type) }
                    declaration.acceptChildrenVoid(this)
                }
            }
            override fun visitClass(declaration: IrClass) {
                if (includeDeclarations) {
                    declaration.superTypes.forEach(::type)
                    declaration.acceptChildrenVoid(this)
                }
            }
            override fun visitTypeParameter(declaration: IrTypeParameter) {
                if (includeDeclarations) declaration.superTypes.forEach(::type)
            }
        })
        return selected.sortedBy { it.kotlinClassName }
    }


}
