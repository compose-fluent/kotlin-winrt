package io.github.composefluent.winrt.runtime

/** The XAML ABI used by an application. UWP uses the Windows SDK implementation. */
enum class XamlFramework {
    WinUi,
    Uwp,
}

/**
 * Selects the process-wide XAML projection before generated types or application code run.
 *
 * CsWinRT's UseWindowsUIXamlProjections switch selects the same mapping boundary. A Kotlin
 * host can select it directly; hosts that do not select it keep the existing feature-switch
 * default. Selection becomes immutable on initialization or first XAML projection use.
 */
object XamlRuntime {
    val framework: XamlFramework
        get() = XamlProjectionConfiguration.framework

    fun initialize(framework: XamlFramework) {
        XamlProjectionConfiguration.initialize(framework)
    }
}

/** ABI identities for the selected XAML implementation; WinUiXamlInterfaceIds stays WinUI-only. */
object XamlInterfaceIds {
    val IXamlMetadataProvider: Guid
        get() = XamlProjectionConfiguration.abi.metadataProvider
    val IXamlType: Guid
        get() = XamlProjectionConfiguration.abi.type
    val IXamlMember: Guid
        get() = XamlProjectionConfiguration.abi.member
    val IApplicationOverrides: Guid
        get() = XamlProjectionConfiguration.abi.applicationOverrides
    /** UWP exposes BoxedType on this separate interface; WinUI includes it in IXamlType. */
    val IXamlType2: Guid?
        get() = XamlProjectionConfiguration.abi.boxedTypeInterface
}

internal data class XamlAbiContract(
    val metadataProvider: Guid,
    val type: Guid,
    val member: Guid,
    val applicationOverrides: Guid,
    val defaultMetadataProviderRuntimeClassName: String?,
    val boxedTypeInterface: Guid? = null,
)

/** Shared metadata method bodies are stored in WinUI order, then adapted once at the ABI edge. */
internal object XamlTypeAbi {
    private const val boxedTypeMethodIndex = 11

    fun interfaces(methods: List<WinRTInspectableMethodDefinition>): List<WinRTInspectableInterfaceDefinition> {
        val abi = XamlProjectionConfiguration.abi
        val boxedInterface = abi.boxedTypeInterface
            ?: return listOf(WinRTInspectableInterfaceDefinition(abi.type, methods))
        // Windows SDK IXamlType2 owns BoxedType on UWP (UniversalApiContract v7).
        return listOf(
            WinRTInspectableInterfaceDefinition(abi.type, methods.filterIndexed { index, _ -> index != boxedTypeMethodIndex }),
            WinRTInspectableInterfaceDefinition(boxedInterface, listOf(methods[boxedTypeMethodIndex])),
        )
    }

    private fun slot(winUiSlot: Int): Int =
        if (XamlProjectionConfiguration.abi.boxedTypeInterface == null) winUiSlot else winUiSlot - 1

    val underlyingType: Int get() = slot(18)
    val activateInstance: Int get() = slot(19)
    val createFromString: Int get() = slot(20)
    val getMember: Int get() = slot(21)
    val addToVector: Int get() = slot(22)
    val addToMap: Int get() = slot(23)
    val runInitializer: Int get() = slot(24)
}
