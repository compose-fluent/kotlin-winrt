package io.github.composefluent.winrt.metadata

internal fun WinRTEventDefinition.withNullableEventContract(ownerTypeName: String): WinRTEventDefinition {
    // WinUI Expander.cpp raises Expanding/Collapsed with nullptr event arguments.
    // CsWinRT Marshaler<T>.FromAbi preserves null. Keep the WinMD signature intact
    // while exposing this native contract in the Kotlin delegate type.
    if (ownerTypeName !in setOf("Microsoft.UI.Xaml.Controls.Expander", "Microsoft.UI.Xaml.Controls.IExpander") ||
        name !in setOf("Expanding", "Collapsed")) return this
    val arguments = delegateType.typeArguments
    if (arguments.size != 2) return this
    return copy(delegateTypeName = "${delegateType.qualifiedName}<${arguments[0].typeName}, ${arguments[1].typeName}?>")
}

internal fun WinRTMethodDefinition.withNullableReturnContract(ownerTypeName: String): WinRTMethodDefinition {
    // WinUI ConnectedAnimationService_Partial.cpp returns S_OK with a null
    // animation when the requested key is absent. Preserve it like CsWinRT's
    // MarshalInterface<T>.FromAbi rather than constructing a zero-pointer RCW.
    if (ownerTypeName.substringBeforeLast('.') in setOf("Microsoft.UI.Xaml.Media.Animation", "Windows.UI.Xaml.Media.Animation") &&
        ownerTypeName.substringAfterLast('.') in setOf("ConnectedAnimationService", "IConnectedAnimationService") &&
        name == "GetAnimation") {
        return copy(returnTypeName = returnTypeName.removeSuffix("?") + "?")
    }
    // Virtualized ItemsControl containers do not exist until they are realized.
    // CsWinRT MarshalInterface<T>.FromAbi preserves the SDK's null pointer, also
    // used explicitly by WinUI Gallery's ItemsPageBase container lookup.
    if (ownerTypeName.substringBeforeLast('.') in setOf("Microsoft.UI.Xaml.Controls", "Windows.UI.Xaml.Controls") &&
        ownerTypeName.substringAfterLast('.') in setOf("ItemsControl", "IItemContainerMapping") &&
        name in setOf("ContainerFromItem", "ContainerFromIndex")) {
        return copy(returnTypeName = returnTypeName.removeSuffix("?") + "?")
    }
    // Gallery's StringOrIntTemplateSelector returns null for unmatched items.
    // CsWinRT MarshalInterface<T>.FromAbi (WinRT.Runtime/Marshalers.cs) preserves
    // a zero ABI pointer. Normalize before planning so base calls and authored
    // overrides expose the same nullable contract on JVM and Native.
    if (ownerTypeName.substringBeforeLast('.') in setOf("Microsoft.UI.Xaml.Controls", "Windows.UI.Xaml.Controls") &&
        ownerTypeName.substringAfterLast('.') in setOf("DataTemplateSelector", "IDataTemplateSelector", "IDataTemplateSelectorOverrides", "IDataTemplateSelectorOverrides2") &&
        name in setOf("SelectTemplate", "SelectTemplateCore")) {
        return copy(returnTypeName = returnTypeName.removeSuffix("?") + "?")
    }
    // XamlCompiler CSharpPagePass2.tt returns null when there is no binding scope.
    // CsWinRT MarshalInterface<T>.FromAbi preserves that null across the ABI.
    if (ownerTypeName == "Microsoft.UI.Xaml.Markup.IComponentConnector" && name == "GetBindingConnector") {
        return copy(returnTypeName = returnTypeName.removeSuffix("?") + "?")
    }
    // Picker cancellation completes successfully with a null result. CsWinRT's
    // MarshalInterface<T>.FromAbi preserves null; the operation itself is nonnull.
    val pickerNamespace = ownerTypeName.substringBeforeLast('.')
    val pickerOwner = ownerTypeName.substringAfterLast('.')
    val nullablePickerResult = pickerNamespace in setOf("Windows.Storage.Pickers", "Microsoft.Windows.Storage.Pickers") &&
        when (name) {
            "PickSingleFileAsync" -> pickerOwner in setOf("FileOpenPicker", "IFileOpenPicker", "IFileOpenPickerWithOperationId")
            "PickSaveFileAsync" -> pickerOwner in setOf("FileSavePicker", "IFileSavePicker")
            "PickSingleFolderAsync" -> pickerOwner in setOf("FolderPicker", "IFolderPicker")
            else -> false
        }
    if (nullablePickerResult && returnType.qualifiedName == "Windows.Foundation.IAsyncOperation") {
        val result = returnType.typeArguments.single()
        return copy(returnTypeName = "Windows.Foundation.IAsyncOperation<${result.typeName.removeSuffix("?")}?>")
    }
    // WinUI controls/dev/Repeater/Layout.h returns nullptr from this virtual's
    // default implementation. CsWinRT MarshalInterface<T>.FromAbi preserves it.
    // Apply the contract before planning so outgoing calls and authored overrides
    // share the same Kotlin return type, including inherited base-call bridges.
    if (ownerTypeName !in setOf(
            "Microsoft.UI.Xaml.Controls.Layout",
            "Microsoft.UI.Xaml.Controls.ILayoutOverrides",
        ) || name != "CreateDefaultItemTransitionProvider") return this
    // Nullability is a projection contract, not part of a WinMD type signature.
    return copy(returnTypeName = returnTypeName.removeSuffix("?") + "?")
}

internal fun WinRTMethodDefinition.withNullableParameterContract(ownerTypeName: String): WinRTMethodDefinition {
    // CsWinRT MarshalInterface<T>.FromManaged (Marshalers.cs) sends null as a
    // zero pointer. These documented XAML operations use it for the tree root,
    // removing a composition child, and the default navigation transition.
    // Normalize once so calls and authored overrides share this contract.
    val ownerNamespace = ownerTypeName.substringBeforeLast('.')
    val owner = ownerTypeName.substringAfterLast('.')
    val nullableType = when {
        ownerNamespace in setOf("Microsoft.UI.Xaml", "Windows.UI.Xaml") &&
            owner in setOf("UIElement", "IUIElement") && name == "TransformToVisual" -> "$ownerNamespace.UIElement"
        ownerNamespace in setOf("Microsoft.UI.Xaml.Hosting", "Windows.UI.Xaml.Hosting") &&
            owner in setOf("ElementCompositionPreview", "IElementCompositionPreviewStatics") &&
            name == "SetElementChildVisual" -> if (ownerNamespace.startsWith("Microsoft")) "Microsoft.UI.Composition.Visual" else "Windows.UI.Composition.Visual"
        ownerNamespace in setOf("Microsoft.UI.Xaml.Controls", "Windows.UI.Xaml.Controls") &&
            owner in setOf("Frame", "IFrame", "IFrame2") && name == "Navigate" -> ownerNamespace.removeSuffix(".Controls") + ".Media.Animation.NavigationTransitionInfo"
        else -> return this
    }
    return copy(parameters = parameters.map { parameter ->
        if (parameter.typeName.removeSuffix("?") == nullableType) parameter.copy(typeName = "$nullableType?") else parameter
    })
}

fun WinRTPropertyDefinition.projectedPropertyTypeName(
    ownerTypeName: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
): String {
    if (!isNullablePropertyProjection(ownerTypeName, typesByQualifiedName)) {
        return typeName
    }
    return typeName.trim().let { trimmed ->
        if (trimmed.endsWith("?")) trimmed else "$trimmed?"
    }
}

fun WinRTPropertyDefinition.isNullablePropertyProjection(
    ownerTypeName: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
): Boolean {
    val normalizedOwnerTypeName = ownerTypeName
        .substringBefore('<')
        .removeSuffix("?")
    val currentNamespace = normalizedOwnerTypeName.substringBeforeLast('.', "")
    return type.isNullableWinRTPropertyReference(currentNamespace, typesByQualifiedName)
}

private fun WinRTTypeRef.isNullableWinRTPropertyReference(
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): Boolean {
    val normalized = normalized()
    if (normalized.kind != WinRTTypeRefKind.Named || normalized.typeArguments.isNotEmpty()) {
        return false
    }
    val rawTypeName = normalized.qualifiedName ?: normalized.typeName
    if (rawTypeName.isNonNullableXamlPropertyRuntimeClassTypeName()) {
        return false
    }
    if (isWinRTObjectTypeName(rawTypeName)) {
        return true
    }
    val resolvedType = resolveTypeReference(normalized, currentNamespace, typesByQualifiedName).definitionType
    return resolvedType?.kind in setOf(
        WinRTTypeKind.Interface,
        WinRTTypeKind.Delegate,
        WinRTTypeKind.RuntimeClass,
    )
}

private fun String.isXamlDependencyPropertyTypeName(): Boolean =
    this == "Microsoft.UI.Xaml.DependencyProperty" ||
        this == "Windows.UI.Xaml.DependencyProperty"

private fun String.isNonNullableXamlPropertyRuntimeClassTypeName(): Boolean =
    isXamlDependencyPropertyTypeName() || isXamlCollectionRuntimeClassTypeName()

private fun String.isXamlCollectionRuntimeClassTypeName(): Boolean {
    if (!startsWith("Microsoft.UI.Xaml.") && !startsWith("Windows.UI.Xaml.")) {
        return false
    }
    val simpleName = substringAfterLast('.')
    return simpleName.endsWith("Collection") || simpleName == "ResourceDictionary"
}
