package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal object XamlProjectionConfiguration {
    // Zero means unselected. Explicit selection and first projection use share one atomic
    // publication so a late host cannot change already-registered names or interface identities.
    private val selectedFramework = AtomicInt(0)

    val framework: XamlFramework
        get() {
            val current = selectedFramework.load()
            if (current != 0) return decode(current)
            val candidate = if (FeatureSwitches.useWindowsUiXamlProjections) 2 else 1
            selectedFramework.compareAndSet(0, candidate)
            return decode(selectedFramework.load())
        }

    fun initialize(framework: XamlFramework) {
        val requested = if (framework == XamlFramework.Uwp) 2 else 1
        selectedFramework.compareAndSet(0, requested)
        check(selectedFramework.load() == requested) {
            "XAML is already initialized for ${this.framework}; select $framework before using projections."
        }
    }

    internal fun clearForTests() {
        selectedFramework.store(0)
    }

    private fun decode(value: Int): XamlFramework = if (value == 2) XamlFramework.Uwp else XamlFramework.WinUi

    val useWindowsUiXamlProjections: Boolean
        get() = framework == XamlFramework.Uwp

    val supportsWinUiOnlyTypes: Boolean
        get() = !useWindowsUiXamlProjections

    fun <T> select(
        muxValue: T,
        wuxValue: T,
    ): T = if (useWindowsUiXamlProjections) wuxValue else muxValue

    val abi: XamlAbiContract
        get() = select(winUiAbi, uwpAbi)

    private val winUiAbi by lazy {
        XamlAbiContract(
            WinUiXamlInterfaceIds.IXamlMetadataProvider,
            WinUiXamlInterfaceIds.IXamlType,
            WinUiXamlInterfaceIds.IXamlMember,
            WinUiXamlInterfaceIds.IApplicationOverrides,
            WinUiXamlMetadataProviderInfo.runtimeClassName,
        )
    }

    // Windows SDK windows.ui.xaml.markup.h and windows.ui.xaml.h. These interfaces have
    // different identities from WinUI. XamlTypeAbi owns the IXamlType2 shape difference.
    // UWP system types do not require the Windows App SDK's metadata-provider activation hook.
    private val uwpAbi by lazy {
        XamlAbiContract(
            Guid("B3765D69-68A5-4B32-8861-FDB90C1F5836"),
            Guid("7920EAB1-A2E5-479A-BD50-6CEF3C0B4970"),
            Guid("C541F58C-43A9-4216-B718-E0B11B14E93E"),
            Guid("25F99FF7-9347-459A-9FAC-B2D0E11C1A0F"),
            null,
            Guid("9F0C6E3B-433B-56AD-8F69-78A4DD3E64F9"),
        )
    }

    fun winUiOnlyTypeError(typeName: String): WinRTUnsupportedOperationException =
        WinRTUnsupportedOperationException(
            message =
                "The '$typeName' type is only supported for WinUI, and not when using System XAML projections " +
                    "(make sure the '${FeatureSwitches.UseWindowsUIXamlProjectionsPropertyName}' system property is not set to 'true').",
            hResult = KnownHResults.E_NOTSUPPORTED,
        )
}
