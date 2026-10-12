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
    val mapped = winRTMappedTypeForKotlinName(name)
    val metadataName = when {
        primitive != null -> primitive.toKotlinProjectionTypeName()
        name == "kotlin.Any" -> "System.Object"
        isWinRTGuidTypeName(name.substringAfterLast('.')) && name == "io.github.composefluent.winrt.runtime.Guid" -> "System.Guid"
        mapped != null -> mapped.abiQualifiedName
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
    accessible: (IrDeclaration) -> Boolean = { true },
    xamlProperties: List<WinUiXamlPropertyDeclaration> = emptyList(),
): WinUiXamlApplicationTypeMembers {
    if (klass.kind == org.jetbrains.kotlin.descriptors.ClassKind.ENUM_CLASS) return WinUiXamlApplicationTypeMembers()
    fun visible(function: IrSimpleFunction?) = function != null && accessible(function) &&
        (function.visibility == DescriptorVisibilities.PUBLIC || (includeInternal && function.visibility == DescriptorVisibilities.INTERNAL))

    fun resolve(type: IrType): WinRTTypeRef {
        fun available(type: IrType): Boolean =
            (type.classOrNull?.owner?.let(accessible) != false) &&
                (type as? IrSimpleType)?.arguments.orEmpty().all { it.typeOrNull?.let(::available) != false }
        require(available(type)) { "XAML signature refers to an unavailable Kotlin type: $type" }
        return xamlApplicationTypeReference(type, types, applicationTypes)
    }

    val owners = (listOf(klass) + listOfNotNull(klass.companionObject())).filter(accessible)
    fun visibleOwner(owner: IrClass) = owner === klass || owner.visibility == DescriptorVisibilities.PUBLIC ||
        (includeInternal && owner.visibility == DescriptorVisibilities.INTERNAL)
    val dependencyPropertyNames = owners.filter { it.kind == org.jetbrains.kotlin.descriptors.ClassKind.OBJECT }
        .flatMap { it.declarations.filterIsInstance<IrProperty>() }
        .filter { accessible(it) && it.getter?.let(accessible) == true &&
            it.getter?.returnType?.classFqName?.asString() == "microsoft.ui.xaml.DependencyProperty" }
        .mapTo(mutableSetOf()) { it.name.asString() }
    val properties = owners.flatMap { owner -> owner.declarations.filterIsInstance<IrProperty>().map { owner to it } }
        .filter { (_, property) -> accessible(property) && property.getter?.let(accessible) == true &&
            (property.origin == IrDeclarationOrigin.DEFINED ||
                xamlProperties.any { it.name == property.name.asString() }) && property.getter != null &&
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
            WinUiXamlApplicationProperty(property.name.asString(), type,
                isReadOnly = property.setter == null || (public && !visible(property.setter)), isPublic = public,
                isStatic = static,
                isDependencyProperty = owner === klass && "${property.name.asString()}Property" in dependencyPropertyNames)
        }
    // CSharpPagePass1.tt emits these as PropertyChangedEventHandler events.
    // Kotlin expands that alias to EventHandler<PropertyChangedEventArgs?>;
    // infer only source events here, preserving the XAML declaration's named
    // delegate instead of exporting the generated pair a second time.
    val declaredEvents = xamlProperties.mapNotNull { property -> property.changedHandler?.let {
        WinUiXamlApplicationEvent(it, WinRTTypeRef.named("Microsoft.UI.Xaml.Data.PropertyChangedEventHandler"))
    } }
    val declaredEventNames = declaredEvents.mapTo(mutableSetOf()) { it.name }
    val events = klass.declarations.filterIsInstance<IrSimpleFunction>()
        .filter { visible(it) && it.overriddenSymbols.isEmpty() && it.name.asString().startsWith("add") &&
            it.name.asString().length > 3 && it.name.asString()[3].isUpperCase() &&
            it.name.asString().removePrefix("add") !in declaredEventNames }
        .mapNotNull { add ->
            val name = add.name.asString().removePrefix("add")
            val parameter = add.parameters.singleOrNull { it.kind == IrParameterKind.Regular } ?: return@mapNotNull null
            val remove = klass.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull {
                visible(it) && it.name.asString() == "remove$name" &&
                    it.parameters.singleOrNull { p -> p.kind == IrParameterKind.Regular }?.type == parameter.type
            } ?: return@mapNotNull null
            val handlerType = runCatching { resolve(parameter.type) }.getOrNull() ?: return@mapNotNull null
            if (types[handlerType.qualifiedName?.substringBefore('`')]?.kind != WinRTTypeKind.Delegate.name) return@mapNotNull null
            WinUiXamlApplicationEvent(name, handlerType)
        } + declaredEvents
    val methods = owners.flatMap { owner ->
        owner.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { accessible(it) && it.origin == IrDeclarationOrigin.DEFINED && it.overriddenSymbols.isEmpty() &&
                !it.isSuspend && it.typeParameters.isEmpty() &&
                it.parameters.none { parameter -> parameter.kind == IrParameterKind.ExtensionReceiver ||
                    parameter.kind == IrParameterKind.Context || parameter.varargElementType != null } &&
                events.none { event -> it.name.asString() in listOf("add${event.name}", "remove${event.name}") } }
            .mapNotNull { function -> runCatching {
                WinUiXamlApplicationMethod(function.name.asString(), resolve(function.returnType),
                    function.parameters.filter { it.kind == IrParameterKind.Regular }.map { resolve(it.type) },
                    isStatic = owner.kind == org.jetbrains.kotlin.descriptors.ClassKind.OBJECT,
                    isPublic = visible(function) && visibleOwner(owner))
            }.getOrNull() }
    }
    fun stringAnnotation(name: String) = klass.annotations.firstOrNull { call ->
        call.symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() == "io.github.composefluent.winrt.runtime.$name"
    }?.arguments?.firstOrNull()?.let { (it as? IrConst)?.value as? String }
    val parser = stringAnnotation("WinUiXamlCreateFromString")?.let { value ->
        if ('.' !in value) value else {
            val owner = value.substringBeforeLast('.')
            val local = "${klass.fqNameWhenAvailable!!.asString().substringBeforeLast('.')}.$owner"
            (if (owner !in applicationTypes && local in applicationTypes) local else owner) + "." + value.substringAfterLast('.')
        }
    }
    return WinUiXamlApplicationTypeMembers(properties = properties, events = events, methods = methods,
        contentProperty = stringAnnotation("WinUiXamlContentProperty"),
        createFromStringMethod = parser)
}
