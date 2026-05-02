package io.github.composefluent.winrt.runtime

internal object XamlProjectionConfiguration {
    val useWindowsUiXamlProjections: Boolean
        get() = FeatureSwitches.useWindowsUiXamlProjections

    val supportsWinUiOnlyTypes: Boolean
        get() = !useWindowsUiXamlProjections

    fun <T> select(
        muxValue: T,
        wuxValue: T,
    ): T = if (useWindowsUiXamlProjections) wuxValue else muxValue

    fun winUiOnlyTypeError(typeName: String): WinRtUnsupportedOperationException =
        WinRtUnsupportedOperationException(
            message =
                "The '$typeName' type is only supported for WinUI, and not when using System XAML projections " +
                    "(make sure the '${FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName}' system property is not set to 'true').",
            hResult = KnownHResults.E_NOTSUPPORTED,
        )
}
