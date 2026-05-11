package io.github.composefluent.winrt.metadata

data class WinRtNullablePropertyProjectionDescriptor(
    val ownerTypeName: String,
    val propertyName: String,
)

fun WinRtPropertyDefinition.projectedPropertyTypeName(ownerTypeName: String): String {
    if (!isNullablePropertyProjection(ownerTypeName, name)) {
        return typeName
    }
    return typeName.trim().let { trimmed ->
        if (trimmed.endsWith("?")) trimmed else "$trimmed?"
    }
}

fun isNullablePropertyProjection(
    ownerTypeName: String,
    propertyName: String,
): Boolean =
    WinRtNullablePropertyProjectionDescriptor(
        ownerTypeName = ownerTypeName.substringBefore('<').removeSuffix("?"),
        propertyName = propertyName,
    ) in XAML_NULLABLE_OBJECT_PROPERTIES

private val XAML_NULLABLE_OBJECT_PROPERTIES = setOf(
    // CsWinRT runtime class/interface FromAbi helpers return null for IntPtr.Zero.
    // Kotlin needs explicit nullable projection types for unsettable XAML object properties.
    WinRtNullablePropertyProjectionDescriptor("Microsoft.UI.Xaml.Controls.ContentControl", "Content"),
    WinRtNullablePropertyProjectionDescriptor("Microsoft.UI.Xaml.Controls.IContentControl", "Content"),
    WinRtNullablePropertyProjectionDescriptor("Microsoft.UI.Xaml.UIElement", "Clip"),
    WinRtNullablePropertyProjectionDescriptor("Microsoft.UI.Xaml.IUIElement", "Clip"),
    WinRtNullablePropertyProjectionDescriptor("Microsoft.UI.Xaml.Window", "SystemBackdrop"),
    WinRtNullablePropertyProjectionDescriptor("Microsoft.UI.Xaml.IWindow", "SystemBackdrop"),
    WinRtNullablePropertyProjectionDescriptor("Windows.UI.Xaml.Controls.ContentControl", "Content"),
    WinRtNullablePropertyProjectionDescriptor("Windows.UI.Xaml.Controls.IContentControl", "Content"),
    WinRtNullablePropertyProjectionDescriptor("Windows.UI.Xaml.UIElement", "Clip"),
    WinRtNullablePropertyProjectionDescriptor("Windows.UI.Xaml.IUIElement", "Clip"),
)
