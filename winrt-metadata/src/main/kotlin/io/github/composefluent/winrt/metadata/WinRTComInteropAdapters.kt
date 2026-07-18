package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.Guid

enum class WinRTComInteropVtableBase {
    IUnknown,
    IInspectable,
}

sealed interface WinRTComInteropParameterType {
    data object RawAddress : WinRTComInteropParameterType
    data object StringValue : WinRTComInteropParameterType
    data class ProjectedObject(val typeName: String) : WinRTComInteropParameterType
}

data class WinRTComInteropParameterDescriptor(
    val name: String,
    val type: WinRTComInteropParameterType,
)

sealed interface WinRTComInteropRuntimeClassIidSource {
    data object DefaultInterface : WinRTComInteropRuntimeClassIidSource
    data class Constant(val iid: Guid) : WinRTComInteropRuntimeClassIidSource
}

sealed interface WinRTComInteropResultDescriptor {
    data object UnitResult : WinRTComInteropResultDescriptor
    data class ProjectedRuntimeClass(
        val typeName: String,
        val iidSource: WinRTComInteropRuntimeClassIidSource = WinRTComInteropRuntimeClassIidSource.DefaultInterface,
    ) : WinRTComInteropResultDescriptor

    data object AsyncAction : WinRTComInteropResultDescriptor
    data class AsyncOperation(val resultTypeName: String) : WinRTComInteropResultDescriptor
}

data class WinRTComInteropMethodDescriptor(
    val name: String,
    val slot: Int,
    val parameters: List<WinRTComInteropParameterDescriptor>,
    val result: WinRTComInteropResultDescriptor,
)

data class WinRTComInteropAdapterDescriptor(
    val namespace: String,
    val name: String,
    val minimumUniversalApiContract: Int,
    val activationTypeName: String,
    val queryIid: Guid,
    val vtableBase: WinRTComInteropVtableBase = WinRTComInteropVtableBase.IInspectable,
    val methods: List<WinRTComInteropMethodDescriptor>,
) {
    val projectedPackageName: String
        get() = namespace.split('.').joinToString(".") { segment -> segment.lowercase() }

    val projectedTypeName: String
        get() = "$projectedPackageName.$name"

    val requiredTypeNames: Set<String>
        get() = buildSet {
            add(activationTypeName)
            methods.forEach { method ->
                method.parameters.forEach { parameter ->
                    (parameter.type as? WinRTComInteropParameterType.ProjectedObject)?.let { add(it.typeName) }
                }
                when (val result = method.result) {
                    WinRTComInteropResultDescriptor.UnitResult -> Unit
                    is WinRTComInteropResultDescriptor.ProjectedRuntimeClass -> add(result.typeName)
                    WinRTComInteropResultDescriptor.AsyncAction -> add(WINDOWS_FOUNDATION_IASYNC_ACTION)
                    is WinRTComInteropResultDescriptor.AsyncOperation -> {
                        add(WINDOWS_FOUNDATION_IASYNC_OPERATION)
                        if (result.resultTypeName != "Boolean") {
                            add(result.resultTypeName)
                        }
                    }
                }
            }
        }
}

object WinRTComInteropAdapters {
    val all: List<WinRTComInteropAdapterDescriptor> = listOf(
        adapter(
            namespace = "Windows.ApplicationModel.DataTransfer.DragDrop.Core",
            name = "DragDropManagerInterop",
            minimumUac = 1,
            activationTypeName = "Windows.ApplicationModel.DataTransfer.DragDrop.Core.CoreDragDropManager",
            queryIid = "5AD8CBA7-4C01-4DAC-9074-827894292D63",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.ApplicationModel.DataTransfer.DragDrop.Core.CoreDragDropManager",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.Graphics.Printing",
            name = "PrintManagerInterop",
            minimumUac = 1,
            activationTypeName = "Windows.Graphics.Printing.PrintManager",
            queryIid = "C5435A42-8D43-4E7B-A68A-EF311E392087",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.Graphics.Printing.PrintManager",
                ),
                asyncOperationMethod(
                    name = "showPrintUIForWindowAsync",
                    slot = 7,
                    resultTypeName = "Boolean",
                    parameters = listOf(rawAddressParameter("appWindow")),
                ),
            ),
        ),
        adapter(
            namespace = "Windows.Media",
            name = "SystemMediaTransportControlsInterop",
            minimumUac = 1,
            activationTypeName = "Windows.Media.SystemMediaTransportControls",
            queryIid = "DDB0472D-C911-4A1F-86D9-DC3D71A95F5A",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.Media.SystemMediaTransportControls",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.Media.PlayTo",
            name = "PlayToManagerInterop",
            minimumUac = 1,
            activationTypeName = "Windows.Media.PlayTo.PlayToManager",
            queryIid = "24394699-1F2C-4EB3-8CD7-0EC1DA42A540",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.Media.PlayTo.PlayToManager",
                ),
                unitMethod("showPlayToUIForWindow", 7, listOf(rawAddressParameter("appWindow"))),
            ),
        ),
        adapter(
            namespace = "Windows.Security.Credentials.UI",
            name = "UserConsentVerifierInterop",
            minimumUac = 1,
            activationTypeName = "Windows.Security.Credentials.UI.UserConsentVerifier",
            queryIid = "39E050C3-4E74-441A-8DC0-B81104DF949C",
            methods = listOf(
                asyncOperationMethod(
                    name = "requestVerificationForWindowAsync",
                    slot = 6,
                    resultTypeName = "Windows.Security.Credentials.UI.UserConsentVerificationResult",
                    parameters = listOf(
                        rawAddressParameter("appWindow"),
                        WinRTComInteropParameterDescriptor("message", WinRTComInteropParameterType.StringValue),
                    ),
                ),
            ),
        ),
        adapter(
            namespace = "Windows.Security.Authentication.Web.Core",
            name = "WebAuthenticationCoreManagerInterop",
            minimumUac = 1,
            activationTypeName = "Windows.Security.Authentication.Web.Core.WebAuthenticationCoreManager",
            queryIid = "F4B8E804-811E-4436-B69C-44CB67B72084",
            methods = listOf(
                asyncOperationMethod(
                    name = "requestTokenForWindowAsync",
                    slot = 6,
                    resultTypeName = "Windows.Security.Authentication.Web.Core.WebTokenRequestResult",
                    parameters = listOf(
                        rawAddressParameter("appWindow"),
                        projectedObjectParameter("request", "Windows.Security.Authentication.Web.Core.WebTokenRequest"),
                    ),
                ),
                asyncOperationMethod(
                    name = "requestTokenWithWebAccountForWindowAsync",
                    slot = 7,
                    resultTypeName = "Windows.Security.Authentication.Web.Core.WebTokenRequestResult",
                    parameters = listOf(
                        rawAddressParameter("appWindow"),
                        projectedObjectParameter("request", "Windows.Security.Authentication.Web.Core.WebTokenRequest"),
                        projectedObjectParameter("webAccount", "Windows.Security.Credentials.WebAccount"),
                    ),
                ),
            ),
        ),
        adapter(
            namespace = "Windows.UI.ApplicationSettings",
            name = "AccountsSettingsPaneInterop",
            minimumUac = 1,
            activationTypeName = "Windows.UI.ApplicationSettings.AccountsSettingsPane",
            queryIid = "D3EE12AD-3865-4362-9746-B75A682DF0E6",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.UI.ApplicationSettings.AccountsSettingsPane",
                ),
                asyncActionMethod("showManageAccountsForWindowAsync", 7, listOf(rawAddressParameter("appWindow"))),
                asyncActionMethod("showAddAccountForWindowAsync", 8, listOf(rawAddressParameter("appWindow"))),
            ),
        ),
        adapter(
            namespace = "Windows.UI.ViewManagement",
            name = "InputPaneInterop",
            minimumUac = 1,
            activationTypeName = "Windows.UI.ViewManagement.InputPane",
            queryIid = "75CF2C57-9195-4931-8332-F0B409E916AF",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.UI.ViewManagement.InputPane",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.UI.ViewManagement",
            name = "UIViewSettingsInterop",
            minimumUac = 1,
            activationTypeName = "Windows.UI.ViewManagement.UIViewSettings",
            queryIid = "3694DBF9-8F68-44BE-8FF5-195C98EDE8A6",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "hwnd",
                    resultTypeName = "Windows.UI.ViewManagement.UIViewSettings",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.ApplicationModel.DataTransfer",
            name = "DataTransferManagerInterop",
            minimumUac = 1,
            activationTypeName = "Windows.ApplicationModel.DataTransfer.DataTransferManager",
            queryIid = "3A3DCD6C-3EAB-43DC-BCDE-45671CE800C8",
            vtableBase = WinRTComInteropVtableBase.IUnknown,
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 3,
                    windowParameterName = "appWindow",
                    resultTypeName = "Windows.ApplicationModel.DataTransfer.DataTransferManager",
                    iidSource = WinRTComInteropRuntimeClassIidSource.Constant(
                        Guid("A5CAEE9B-8708-49D1-8D36-67D25A8DA00C"),
                    ),
                ),
                unitMethod("showShareUIForWindow", 4, listOf(rawAddressParameter("appWindow"))),
            ),
        ),
        adapter(
            namespace = "Windows.UI.Input.Spatial",
            name = "SpatialInteractionManagerInterop",
            minimumUac = 2,
            activationTypeName = "Windows.UI.Input.Spatial.SpatialInteractionManager",
            queryIid = "5C4EE536-6A98-4B86-A170-587013D6FD4B",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "window",
                    resultTypeName = "Windows.UI.Input.Spatial.SpatialInteractionManager",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.UI.Input",
            name = "RadialControllerConfigurationInterop",
            minimumUac = 3,
            activationTypeName = "Windows.UI.Input.RadialControllerConfiguration",
            queryIid = "787CDAAC-3186-476D-87E4-B9374A7B9970",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "hwnd",
                    resultTypeName = "Windows.UI.Input.RadialControllerConfiguration",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.UI.Input",
            name = "RadialControllerInterop",
            minimumUac = 3,
            activationTypeName = "Windows.UI.Input.RadialController",
            queryIid = "1B0535C9-57AD-45C1-9D79-AD5C34360513",
            methods = listOf(
                runtimeClassMethod(
                    name = "createForWindow",
                    slot = 6,
                    windowParameterName = "hwnd",
                    resultTypeName = "Windows.UI.Input.RadialController",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.UI.Input.Core",
            name = "RadialControllerIndependentInputSourceInterop",
            minimumUac = 4,
            activationTypeName = "Windows.UI.Input.Core.RadialControllerIndependentInputSource",
            queryIid = "3D577EFF-4CEE-11E6-B535-001BDC06AB3B",
            methods = listOf(
                runtimeClassMethod(
                    name = "createForWindow",
                    slot = 6,
                    windowParameterName = "hwnd",
                    resultTypeName = "Windows.UI.Input.Core.RadialControllerIndependentInputSource",
                ),
            ),
        ),
        adapter(
            namespace = "Windows.Graphics.Display",
            name = "DisplayInformationInterop",
            minimumUac = 15,
            activationTypeName = "Windows.Graphics.Display.DisplayInformation",
            queryIid = "7449121C-382B-4705-8DA7-A795BA482013",
            methods = listOf(
                runtimeClassMethod(
                    name = "getForWindow",
                    slot = 6,
                    windowParameterName = "window",
                    resultTypeName = "Windows.Graphics.Display.DisplayInformation",
                ),
                runtimeClassMethod(
                    name = "getForMonitor",
                    slot = 7,
                    windowParameterName = "monitor",
                    resultTypeName = "Windows.Graphics.Display.DisplayInformation",
                ),
            ),
        ),
    ).sortedBy(WinRTComInteropAdapterDescriptor::projectedTypeName)

    fun forProjection(
        model: WinRTMetadataModel,
        projectedNamespaces: Iterable<String>,
        context: WinRTMetadataProjectionContext,
    ): List<WinRTComInteropAdapterDescriptor> {
        if (context.sources.none { source -> source is WinRTMetadataSource.WindowsSdk }) {
            return emptyList()
        }
        val namespaceSet = projectedNamespaces.toSet()
        val candidateAdapters = all.filter { adapter ->
            adapter.namespace in namespaceSet && context.additionFilter.includes(adapter.activationTypeName)
        }
        if (candidateAdapters.isEmpty()) {
            return emptyList()
        }
        val universalApiContract = model.universalApiContractMajorVersion
            ?: throw IllegalArgumentException(
                "The declared Windows SDK does not contain Windows.Foundation.UniversalApiContract in Platform.xml; " +
                    "Windows COM interop helpers cannot be selected.",
            )
        val typesByQualifiedName = model.namespaces
            .flatMap(WinRTNamespace::types)
            .associateBy(WinRTTypeDefinition::qualifiedName)

        return candidateAdapters.mapNotNull { adapter ->
            if (universalApiContract < adapter.minimumUniversalApiContract) {
                return@mapNotNull null
            }
            val requiredTypes = adapter.requiredTypeNames.associateWith { typeName ->
                resolveTypeReference(
                    type = WinRTTypeRef.fromDisplayName(typeName),
                    currentNamespace = "",
                    typesByQualifiedName = typesByQualifiedName,
                ).definitionType
            }
            val missingTypes = requiredTypes.filterValues { type -> type == null }.keys
            if (missingTypes.isNotEmpty()) {
                if (missingTypes.all { typeName -> context.intentionallyExcludes(typeName) }) {
                    return@mapNotNull null
                }
                throw IllegalArgumentException(
                    "Windows COM interop helper '${adapter.projectedTypeName}' requires missing projected types: " +
                        missingTypes.sorted().joinToString(", "),
                )
            }
            val missingDefaultInterfaces = adapter.methods
                .mapNotNull { method -> method.result as? WinRTComInteropResultDescriptor.ProjectedRuntimeClass }
                .filter { result -> result.iidSource == WinRTComInteropRuntimeClassIidSource.DefaultInterface }
                .map(WinRTComInteropResultDescriptor.ProjectedRuntimeClass::typeName)
                .distinct()
                .filter { typeName -> requiredTypes.getValue(typeName)?.defaultInterfaceName.isNullOrBlank() }
            require(missingDefaultInterfaces.isEmpty()) {
                "Windows COM interop helper '${adapter.projectedTypeName}' requires default interfaces for: " +
                    missingDefaultInterfaces.sorted().joinToString(", ")
            }
            adapter
        }
    }
}

private fun WinRTMetadataProjectionContext.intentionallyExcludes(typeName: String): Boolean =
    excludedTypes.any { excluded -> typeName == excluded || typeName.startsWith("$excluded.") } ||
        exclude.any { excluded -> typeName == excluded || typeName.startsWith("$excluded.") }

private fun adapter(
    namespace: String,
    name: String,
    minimumUac: Int,
    activationTypeName: String,
    queryIid: String,
    vtableBase: WinRTComInteropVtableBase = WinRTComInteropVtableBase.IInspectable,
    methods: List<WinRTComInteropMethodDescriptor>,
): WinRTComInteropAdapterDescriptor = WinRTComInteropAdapterDescriptor(
    namespace = namespace,
    name = name,
    minimumUniversalApiContract = minimumUac,
    activationTypeName = activationTypeName,
    queryIid = Guid(queryIid),
    vtableBase = vtableBase,
    methods = methods,
)

private fun rawAddressParameter(name: String): WinRTComInteropParameterDescriptor =
    WinRTComInteropParameterDescriptor(name, WinRTComInteropParameterType.RawAddress)

private fun projectedObjectParameter(name: String, typeName: String): WinRTComInteropParameterDescriptor =
    WinRTComInteropParameterDescriptor(name, WinRTComInteropParameterType.ProjectedObject(typeName))

private fun runtimeClassMethod(
    name: String,
    slot: Int,
    windowParameterName: String,
    resultTypeName: String,
    iidSource: WinRTComInteropRuntimeClassIidSource = WinRTComInteropRuntimeClassIidSource.DefaultInterface,
): WinRTComInteropMethodDescriptor = WinRTComInteropMethodDescriptor(
    name = name,
    slot = slot,
    parameters = listOf(rawAddressParameter(windowParameterName)),
    result = WinRTComInteropResultDescriptor.ProjectedRuntimeClass(resultTypeName, iidSource),
)

private fun unitMethod(
    name: String,
    slot: Int,
    parameters: List<WinRTComInteropParameterDescriptor>,
): WinRTComInteropMethodDescriptor = WinRTComInteropMethodDescriptor(
    name = name,
    slot = slot,
    parameters = parameters,
    result = WinRTComInteropResultDescriptor.UnitResult,
)

private fun asyncActionMethod(
    name: String,
    slot: Int,
    parameters: List<WinRTComInteropParameterDescriptor>,
): WinRTComInteropMethodDescriptor = WinRTComInteropMethodDescriptor(
    name = name,
    slot = slot,
    parameters = parameters,
    result = WinRTComInteropResultDescriptor.AsyncAction,
)

private fun asyncOperationMethod(
    name: String,
    slot: Int,
    resultTypeName: String,
    parameters: List<WinRTComInteropParameterDescriptor>,
): WinRTComInteropMethodDescriptor = WinRTComInteropMethodDescriptor(
    name = name,
    slot = slot,
    parameters = parameters,
    result = WinRTComInteropResultDescriptor.AsyncOperation(resultTypeName),
)

private const val WINDOWS_FOUNDATION_IASYNC_ACTION = "Windows.Foundation.IAsyncAction"
private const val WINDOWS_FOUNDATION_IASYNC_OPERATION = "Windows.Foundation.IAsyncOperation`1"
