package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTTypeByProjectedName
import io.github.composefluent.winrt.metadata.*
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.expressions.IrConst

/** One metadata mapping for property signatures, attached accessors and collection bases. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlApplicationTypeReference(type: IrType, types: Map<String, IndexedWinRTType>, applicationTypes: Set<String>): WinRTTypeRef {
    val name = requireNotNull(type.classFqName?.asString()) { "XAML property requires a concrete type: $type" }
    if (isWinRTVoidTypeName(name.removePrefix("kotlin."))) return WinRTTypeRef.named("System.Void")
    val arguments = (type as? IrSimpleType)?.arguments.orEmpty().map {
        xamlApplicationTypeReference(requireNotNull(it.typeOrNull) { "XAML property cannot use a star-projected type: $type" }, types, applicationTypes)
    }
    if (name == "kotlin.Array") return WinRTTypeRef.array(arguments.single())
    winRTArrayElementForKotlinType(name)?.let { return WinRTTypeRef.array(WinRTTypeRef.named(it.toKotlinProjectionTypeName())) }
    val primitive = winRTFundamentalTypeForName(name.removePrefix("kotlin."))
    val indexed = resolveIndexedWinRTTypeByProjectedName(name, types)
    val collection = winRTCollectionAbiNameForKotlinType(name)
    val metadataName = when {
        primitive != null -> primitive.toKotlinProjectionTypeName()
        name == "kotlin.Any" -> "System.Object"
        name in applicationTypes -> name
        collection != null -> "$collection`${arguments.size}"
        indexed != null -> indexed.qualifiedName.substringBefore('`') + if (arguments.isEmpty()) "" else "`${arguments.size}"
        else -> error("XAML property type $name has no WinRT metadata projection")
    }
    val result = WinRTTypeRef.named(metadataName, arguments)
    val isValueType = primitive?.isWinRTValueType == true ||
        indexed?.kind in setOf(WinRTTypeKind.Enum.name, WinRTTypeKind.Struct.name) ||
        type.classOrNull?.owner?.kind == org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS
    return if (type.isNullable() && isValueType) WinRTTypeRef.named("Windows.Foundation.IReference`1", listOf(result)) else result
}

/** Public property visibility follows CsWinRT WinRTTypeWriter.AddPropertyDeclaration.
 * The temporary schema also describes representable private x:Bind inputs; generated
 * runtime registration filters them out. Component ABI export remains separate.
 */
@OptIn(UnsafeDuringIrConstructionAPI::class)
internal fun xamlApplicationProperties(
    klass: IrClass,
    types: Map<String, IndexedWinRTType>,
    applicationTypes: Set<String>,
    strictPublicProperties: Boolean = true,
    includeInternal: Boolean = true,
): WinRTXamlApplicationTypeMembers {
    if (klass.kind == org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS) return WinRTXamlApplicationTypeMembers()
    fun visible(function: IrSimpleFunction?) = function != null &&
        (function.visibility == DescriptorVisibilities.PUBLIC || (includeInternal && function.visibility == DescriptorVisibilities.INTERNAL))

    fun resolve(type: IrType): WinRTTypeRef = xamlApplicationTypeReference(type, types, applicationTypes)

    val owners = listOf(klass) + listOfNotNull(klass.companionObject())
    fun visibleOwner(owner: IrClass) = owner === klass || owner.visibility == DescriptorVisibilities.PUBLIC ||
        (includeInternal && owner.visibility == DescriptorVisibilities.INTERNAL)
    val dependencyPropertyNames = owners.filter { it.kind == org.jetbrains.kotlin.descriptors.ClassKind.OBJECT }
        .flatMap { it.declarations.filterIsInstance<IrProperty>() }
        .filter { it.getter?.returnType?.classFqName?.asString() == "microsoft.ui.xaml.DependencyProperty" }
        .mapTo(mutableSetOf()) { it.name.asString() }
    val properties = owners.flatMap { owner -> owner.declarations.filterIsInstance<IrProperty>().map { owner to it } }
        .filter { (_, property) -> property.origin == IrDeclarationOrigin.DEFINED && property.getter != null &&
            property.getter?.dispatchReceiverParameter != null &&
            property.getter!!.parameters.none { parameter ->
                parameter.kind == IrParameterKind.ExtensionReceiver || parameter.kind == IrParameterKind.Context
            } }
        .sortedBy { it.second.name.asString() }
        .mapNotNull { (owner, property) ->
            val public = visible(property.getter) && visibleOwner(owner)
            val static = owner.kind == org.jetbrains.kotlin.descriptors.ClassKind.OBJECT
            val type = if (strictPublicProperties && public && !static) resolve(property.getter!!.returnType) else
                runCatching { resolve(property.getter!!.returnType) }.getOrNull() ?: return@mapNotNull null
            if (isWinRTVoidTypeName(type.typeName)) return@mapNotNull null
            WinRTXamlApplicationProperty(property.name.asString(), type,
                isReadOnly = property.setter == null || (public && !visible(property.setter)), isPublic = public,
                isStatic = static,
                isDependencyProperty = owner === klass && "${property.name.asString()}Property" in dependencyPropertyNames)
        }
    val events = klass.declarations.filterIsInstance<IrSimpleFunction>()
        .filter { visible(it) && it.overriddenSymbols.isEmpty() && it.name.asString().startsWith("add") &&
            it.name.asString().length > 3 && it.name.asString()[3].isUpperCase() }
        .mapNotNull { add ->
            val name = add.name.asString().removePrefix("add")
            val parameter = add.parameters.singleOrNull { it.kind == IrParameterKind.Regular } ?: return@mapNotNull null
            val remove = klass.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull {
                visible(it) && it.name.asString() == "remove$name" &&
                    it.parameters.singleOrNull { p -> p.kind == IrParameterKind.Regular }?.type == parameter.type
            } ?: return@mapNotNull null
            val handlerType = runCatching { resolve(parameter.type) }.getOrNull() ?: return@mapNotNull null
            if (types[handlerType.qualifiedName?.substringBefore('`')]?.kind != WinRTTypeKind.Delegate.name) return@mapNotNull null
            WinRTXamlApplicationEvent(name, handlerType)
        }
    val methods = owners.flatMap { owner ->
        owner.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { it.origin == IrDeclarationOrigin.DEFINED && it.overriddenSymbols.isEmpty() &&
                !it.isSuspend && it.typeParameters.isEmpty() &&
                it.parameters.none { parameter -> parameter.kind == IrParameterKind.ExtensionReceiver ||
                    parameter.kind == IrParameterKind.Context || parameter.varargElementType != null } &&
                events.none { event -> it.name.asString() in listOf("add${event.name}", "remove${event.name}") } }
            .mapNotNull { function -> runCatching {
                WinRTXamlApplicationMethod(function.name.asString(), resolve(function.returnType),
                    function.parameters.filter { it.kind == IrParameterKind.Regular }.map { resolve(it.type) },
                    isStatic = owner.kind == org.jetbrains.kotlin.descriptors.ClassKind.OBJECT,
                    isPublic = visible(function) && visibleOwner(owner))
            }.getOrNull() }
    }
    fun stringAnnotation(name: String) = klass.annotations.firstOrNull { call ->
        call.symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() == "io.github.composefluent.winrt.runtime.$name"
    }?.arguments?.firstOrNull()?.let { (it as? IrConst)?.value as? String }
    val parser = stringAnnotation("WinRTXamlCreateFromString")?.let { value ->
        if ('.' !in value) value else {
            val owner = value.substringBeforeLast('.')
            val local = "${klass.fqNameWhenAvailable!!.asString().substringBeforeLast('.')}.$owner"
            (if (owner !in applicationTypes && local in applicationTypes) local else owner) + "." + value.substringAfterLast('.')
        }
    }
    return WinRTXamlApplicationTypeMembers(properties = properties, events = events, methods = methods,
        contentProperty = stringAnnotation("WinRTXamlContentProperty"),
        createFromStringMethod = parser)
}
