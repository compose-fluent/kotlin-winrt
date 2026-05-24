package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRtEnumMemberDefinition
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.readText

class KotlinWinRtAuthoringSourceScannerTest {
    @Test
    fun merges_scanned_authored_runtime_classes_into_projection_metadata_model() {
        val augmented = KotlinWinRtAuthoringMetadataModel.mergeAuthoredRuntimeClasses(
            model = model(),
            candidates = listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
        )

        val authoredType = augmented.namespaces.single { namespace -> namespace.name == "sample" }.types.single()
        assertEquals(WinRtTypeKind.RuntimeClass, authoredType.kind)
        assertEquals("sample.App", authoredType.qualifiedName)
        assertEquals("Microsoft.UI.Xaml.Application", authoredType.baseTypeName)
        assertEquals("Microsoft.UI.Xaml.IApplicationOverrides", authoredType.defaultInterfaceName)
        assertTrue(authoredType.activation.isActivatable)
        assertTrue(authoredType.implementedInterfaces.single().isDefault)
        assertTrue(authoredType.implementedInterfaces.single().isOverridable)
    }

    @Test
    fun writes_authored_metadata_descriptor_for_scanned_candidates() {
        val output = Files.createTempFile("kotlin-winrt-authored-metadata-", ".tsv")

        KotlinWinRtAuthoringMetadataModel.writeDescriptor(
            candidates = listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
            outputFile = output,
        )

        assertEquals(
            "sample.App\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides\tMicrosoft.UI.Xaml.IApplicationOverrides\ttrue\ttrue\n",
            output.readText(),
        )
    }

    @Test
    fun renders_generated_type_details_for_scanned_authored_type() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "App",
            sourceTypeName = "sample.App",
            winRtBaseClassName = "Microsoft.UI.Xaml.Application",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = model(),
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_App_TypeDetails.kt").readText()
        assertTrue(generated.contains("object WinRT_App_TypeDetails"))
        assertTrue(generated, generated.contains("fun register()"))
        assertTrue(generated, generated.contains("ComWrappersSupport.registerAuthoringTypeDetailsFactory(App::class, ::createCcwDefinition)"))
        assertTrue(generated, generated.contains("createCcwDefinition"))
        assertTrue(generated.contains("interfaceId = Guid(\"aaaaaaaa-1111-2222-3333-444444444444\")"))
        assertTrue(generated.contains("WinRtInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer))"))
        assertTrue(generated.contains("rawArgs"))
        assertTrue(generated.contains("LaunchActivatedEventArgs.Metadata.wrap"))
        assertTrue(generated.contains("(value as Application).__winrtAuthoringInvokeOnLaunched(__arg0)"))
        assertTrue(generated, !generated.contains("findDeclaredMethod"))
        assertTrue(generated, !generated.contains("getDeclaredMethod"))
        assertTrue(generated, !generated.contains("method.invoke"))
        assertTrue(generated, !generated.contains("InvocationTargetException"))
        assertTrue(generated, !generated.contains("E_NOTIMPL"))
        val registrar = output.resolve("io/github/composefluent/winrt/projections/support/WinRTAuthoringTypeDetailsRegistrar.kt").readText()
        assertTrue(registrar.contains("object WinRTAuthoringTypeDetailsRegistrar"))
        assertTrue(registrar.contains("WinRT_App_TypeDetails.register()"))
    }

    @Test
    fun renders_object_alias_override_parameters_through_object_marshaller() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-object-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalContentControl",
            sourceTypeName = "sample.LocalContentControl",
            winRtBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.Controls.IContentControlOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Controls.IContentControlOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Controls",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "IContentControlOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("2504174a-017e-5a2d-9c28-d97c66ae9937"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnContentChanged",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRtParameterDefinition("oldContent", "System.Object"),
                                        WinRtParameterDefinition("newContent", "Any"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalContentControl_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRtObjectMarshaller.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("WinRtObjectMarshaller.fromAbi(rawArgs[1] as RawAddress)"))
        assertTrue(generated.contains("(value as ContentControl).__winrtAuthoringInvokeOnContentChanged(__arg0, __arg1)"))
        assertTrue(generated, !generated.contains("ContentControl::class.java"))
        assertTrue(generated, !generated.contains("Any::class.java, Any::class.java"))
        assertTrue(generated, !generated.contains("Object.Metadata.wrap"))
    }

    @Test
    fun renders_runtime_mapped_struct_overrides_through_runtime_projection_types() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-mapped-struct-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalShape",
            sourceTypeName = "sample.LocalShape",
            winRtBaseClassName = "Sample.Shape",
            winRtInterfaceNames = listOf("Sample.IShapeOverrides"),
            overridableInterfaceNames = listOf("Sample.IShapeOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Rect",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Size",
                            kind = WinRtTypeKind.Struct,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "Shape",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IShapeOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("2504174a-017e-5a2d-9c28-d97c66ae9940"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "MeasureOverride",
                                    returnTypeName = "Windows.Foundation.Size",
                                    parameters = listOf(
                                        WinRtParameterDefinition("availableSize", "Windows.Foundation.Size"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetPeerFromPointCore",
                                    returnTypeName = "Windows.Foundation.Rect",
                                    parameters = listOf(
                                        WinRtParameterDefinition("point", "Windows.Foundation.Point"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalShape_TypeDetails.kt").readText()
        assertTrue(generated.contains("import io.github.composefluent.winrt.runtime.Point"))
        assertTrue(generated.contains("import io.github.composefluent.winrt.runtime.Rect"))
        assertTrue(generated.contains("import io.github.composefluent.winrt.runtime.Size"))
        assertTrue(generated.contains("ComAbiValueKind.Struct(Size.Metadata.layout.abiLayout)"))
        assertTrue(generated.contains("Size.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("Point.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(generated.contains("Rect.Metadata.copyTo(__result as Rect"))
        assertTrue(generated, !generated.contains("import windows.foundation.Point"))
        assertTrue(generated, !generated.contains("import windows.foundation.Rect"))
        assertTrue(generated, !generated.contains("import windows.foundation.Size"))
    }

    @Test
    fun renders_system_string_override_parameters_and_returns_through_hstring() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-string-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalStringable",
            sourceTypeName = "sample.LocalStringable",
            winRtBaseClassName = "Sample.IStringableBase",
            winRtInterfaceNames = listOf("Sample.IStringableOverrides"),
            overridableInterfaceNames = listOf("Sample.IStringableOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IStringableBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IStringableOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IStringableOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Transform",
                                    returnTypeName = "System.String",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalStringable_TypeDetails.kt").readText()
        assertTrue(generated.contains("HString.fromHandle(rawArgs[0] as RawAddress, owner = false)"))
        assertTrue(generated.contains("(value as IStringableBase).__winrtAuthoringInvokeTransform(__arg0)"))
        assertTrue(generated.contains("PlatformAbi.writePointer("))
        assertTrue(generated.contains("rawArgs[1] as RawAddress"))
        assertTrue(generated.contains("HString.create("))
        assertTrue(generated.contains("__result"))
        assertTrue(generated.contains(".handle"))
    }

    @Test
    fun renders_system_fundamental_override_parameters_and_returns_through_scalar_abi_shapes() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-fundamental-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalFundamentals",
            sourceTypeName = "sample.LocalFundamentals",
            winRtBaseClassName = "Sample.FundamentalBase",
            winRtInterfaceNames = listOf("Sample.IFundamentalOverrides"),
            overridableInterfaceNames = listOf("Sample.IFundamentalOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "FundamentalBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IFundamentalOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IFundamentalOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("99999999-1111-2222-3333-444444444444"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetEnabled",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Boolean")),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetSymbol",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Char")),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetAmount",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Byte")),
                                ),
                                WinRtMethodDefinition(
                                    name = "SetRatio",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("value", "System.Single")),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetEnabled",
                                    returnTypeName = "System.Boolean",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetSymbol",
                                    returnTypeName = "System.Char",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetAmount",
                                    returnTypeName = "System.UInt32",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetRatio",
                                    returnTypeName = "System.Single",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalFundamentals_TypeDetails.kt").readText()
        assertTrue(generated.contains("ComMethodSignature.of(ComAbiValueKind.Int8)"))
        assertTrue(generated.contains("ComMethodSignature.of(ComAbiValueKind.Int16)"))
        assertTrue(generated.contains("ComMethodSignature.of(ComAbiValueKind.Float)"))
        assertTrue(generated.contains("val __arg0 = (rawArgs[0] as Byte).toInt() != 0"))
        assertTrue(generated.contains("val __arg0 = (rawArgs[0] as Short).toInt().toChar()"))
        assertTrue(generated.contains("val __arg0 = (rawArgs[0] as Byte).toUByte()"))
        assertTrue(generated.contains("val __arg0 = rawArgs[0] as Float"))
        assertTrue(generated.contains("PlatformAbi.writeInt8("))
        assertTrue(generated.contains("if (__result as Boolean)"))
        assertTrue(generated.contains("PlatformAbi.writeInt16("))
        assertTrue(generated.contains("Char).code.toShort()"))
        assertTrue(generated.contains("(__result as UInt).toInt()"))
        assertTrue(generated.contains("PlatformAbi.writeFloat("))
    }

    @Test
    fun renders_uint32_flags_enum_override_parameters_and_returns_through_unsigned_abi_carrier() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-enum-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalFlags",
            sourceTypeName = "sample.LocalFlags",
            winRtBaseClassName = "Sample.FlagsBase",
            winRtInterfaceNames = listOf("Sample.IFlagsOverrides"),
            overridableInterfaceNames = listOf("Sample.IFlagsOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "WidgetFlags",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.UInt32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("None", 0u),
                                WinRtEnumMemberDefinition("Enabled", 1u),
                            ),
                            customAttributes = listOf(WinRtCustomAttributeDefinition("System.FlagsAttribute")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "FlagsBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.IFlagsOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample",
                            name = "IFlagsOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("99999999-1111-2222-3333-444444444445"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetFlags",
                                    returnTypeName = "Void",
                                    parameters = listOf(WinRtParameterDefinition("flags", "Sample.WidgetFlags")),
                                ),
                                WinRtMethodDefinition(
                                    name = "GetFlags",
                                    returnTypeName = "Sample.WidgetFlags",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalFlags_TypeDetails.kt").readText()
        assertTrue(generated, generated.contains("ComMethodSignature.of(ComAbiValueKind.Int32)"))
        assertTrue(generated, generated.contains("val __arg0 = WidgetFlags.Metadata.fromAbi((rawArgs[0] as Int).toUInt())"))
        assertTrue(generated, generated.contains("PlatformAbi.writeInt32(rawArgs[0] as RawAddress,"))
        assertTrue(generated, generated.contains("WidgetFlags.Metadata.toAbi(__result"))
        assertTrue(generated, generated.contains("as WidgetFlags).toInt()"))
        assertTrue(generated, !generated.contains("WidgetFlags.Metadata.fromAbi(rawArgs[0] as Int)"))
    }

    @Test
    fun renders_inherited_override_dispatch_against_declaring_winrt_base_class() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-inherited-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalContentControl",
            sourceTypeName = "sample.LocalContentControl",
            winRtBaseClassName = "Microsoft.UI.Xaml.Controls.ContentControl",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IUIElementOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IUIElementOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "UIElement",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IUIElementOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IUIElementOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("bbbbbbbb-1111-2222-3333-444444444444"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(WinRtCustomAttributeValue.TypeValue("Microsoft.UI.Xaml.UIElement")),
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnDisconnectVisualChildren",
                                    returnTypeName = "Void",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalContentControl_TypeDetails.kt").readText()
        assertTrue(generated.contains("(value as UIElement).__winrtAuthoringInvokeOnDisconnectVisualChildren()"))
        assertTrue(generated, !generated.contains("ContentControl::__class.java"))
        assertTrue(generated, !generated.contains("findDeclaredMethod"))
    }

    @Test
    fun renders_authored_collection_returns_through_generic_collection_projection_helpers() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-collection-details-")
        val candidate = KotlinWinRtAuthoredTypeCandidate(
            packageName = "sample",
            className = "LocalAutomationPeer",
            sourceTypeName = "sample.LocalAutomationPeer",
            winRtBaseClassName = "Microsoft.UI.Xaml.Automation.Peers.AutomationPeer",
            winRtInterfaceNames = listOf("Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides"),
            overridableInterfaceNames = listOf("Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides"),
            isPublic = false,
        )
        val metadataModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Automation.Peers",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "AutomationPeer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Automation.Peers.IAutomationPeer",
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "IAutomationPeer",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Automation.Peers",
                            name = "IAutomationPeerOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetControlledPeersCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetDescribedByCore",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<Microsoft.UI.Xaml.Automation.Peers.AutomationPeer>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetRawChildrenCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<Object>",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetNamesCore",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<System.String>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        KotlinWinRtAuthoringTypeDetailsRenderer.renderTo(
            candidates = listOf(candidate),
            metadataModel = metadataModel,
            outputDirectory = output,
        )

        val generated = output.resolve("sample/WinRT_LocalAutomationPeer_TypeDetails.kt").readText()
        assertTrue(generated.contains("WinRtListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReadOnlyListProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtIterableProjection.fromManaged(__result"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.runtimeClass(AutomationPeer::class"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.object_"))
        assertTrue(generated.contains("WinRtReferenceValueAdapters.string"))
        assertTrue(generated, !generated.contains("createCCWForObject(__result, IID.IInspectable)"))
    }

    @Test
    fun writes_authoring_host_manifest_for_scanned_authored_types() {
        val output = Files.createTempDirectory("kotlin-winrt-authoring-host-")
        val manifest = output.resolve("SampleComponent.host.json")
        KotlinWinRtAuthoringMetadataModel.writeHostManifest(
            assemblyName = "SampleComponent",
            candidates = listOf(
                KotlinWinRtAuthoredTypeCandidate(
                    packageName = "sample",
                    className = "App",
                    sourceTypeName = "sample.App",
                    winRtBaseClassName = "Microsoft.UI.Xaml.Application",
                    winRtInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                    overridableInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationOverrides"),
                ),
            ),
            outputFile = manifest,
        )

        val json = manifest.readText()
        assertTrue(json.contains("\"model\": \"jvm-authoring-host\""))
        assertTrue(json.contains("\"assemblyName\": \"SampleComponent\""))
        assertTrue(json.contains("\"hostExportsClass\": \"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports\""))
        assertTrue(json.contains("\"targetArtifact\": \"SampleComponent.jar\""))
        assertTrue(json.contains("\"activatableClasses\": [\"sample.App\"]"))
        assertTrue(json.contains("\"activatableClassTargets\": {\"sample.App\": \"SampleComponent.jar\"}"))
    }

    private fun model(): WinRtMetadataModel =
        WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinRtTypeKind.RuntimeClass,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.IApplicationOverrides",
                                    isOverridable = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = io.github.composefluent.winrt.runtime.Guid("aaaaaaaa-1111-2222-3333-444444444444"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnLaunched",
                                    returnTypeName = "Void",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "args",
                                            typeName = "Microsoft.UI.Xaml.LaunchActivatedEventArgs",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IStringable",
                            kind = WinRtTypeKind.Interface,
                        ),
                    ),
                ),
            ),
        )
}
