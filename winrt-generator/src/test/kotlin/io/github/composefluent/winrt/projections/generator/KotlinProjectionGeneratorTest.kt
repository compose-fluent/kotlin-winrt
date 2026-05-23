package io.github.composefluent.winrt.projections.generator

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import io.github.composefluent.winrt.metadata.WinRtActivationShape
import io.github.composefluent.winrt.metadata.WinRtAvailabilityMetadata
import io.github.composefluent.winrt.metadata.WinRtContractVersionMetadata
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeDefinition
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeNamedArgument
import io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRtEnumMemberDefinition
import io.github.composefluent.winrt.metadata.WinRtFieldDefinition
import io.github.composefluent.winrt.metadata.WinRtGenericParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataSource
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeLayout
import io.github.composefluent.winrt.metadata.WinRtTypeLayoutKind
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.isRegularFile

class KotlinProjectionGeneratorTest {
    @Test
    fun generator_requires_support_files_for_projection_context_callers() {
        val error = runCatching {
            KotlinProjectionGenerator(
                emitSupportFiles = false,
                projectionContext = WinRtMetadataProjectionContext(
                    sources = listOf(WinRtMetadataSource.windowsSdk()),
                ),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message.orEmpty().contains("emitSupportFiles=true"))
    }

    @Test
    fun runtime_owned_mapped_type_decision_is_declared_on_mapped_type_entries() {
        val runtimeOwned = listOf(
            "System.Object?",
            "Windows.Foundation.EventRegistrationToken",
            "Windows.Foundation.HResult",
            "Windows.Foundation.IClosable",
            "Windows.Foundation.Uri",
            "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedAction",
            "Windows.UI.Xaml.Interop.NotifyCollectionChangedAction",
        )
        val notRuntimeOwned = listOf(
            "WinRT.Interop.HWND",
            "Windows.Foundation.IReference<Int32>",
        )

        assertEquals(emptyList<String>(), runtimeOwned.filterNot(::isRuntimeOwnedMappedTypeName))
        assertEquals(emptyList<String>(), notRuntimeOwned.filter(::isRuntimeOwnedMappedTypeName))
    }

    @Test
    fun mapped_type_simple_name_lookup_is_declared_on_mapped_type_entries() {
        val simpleLookup = listOf(
            "Object",
            "HWND",
            "DateTime",
            "TimeSpan",
            "Uri",
            "EventHandler",
            "EventRegistrationToken",
            "HResult",
            "TypeName",
            "IClosable",
            "IReference",
            "IReferenceArray",
        )
        val notSimpleLookup = listOf(
            "IBindableVector",
            "IXamlServiceProvider",
            "NotifyCollectionChangedAction",
            "ICommand",
        )

        assertEquals(emptyList<String>(), simpleLookup.filter { mappedTypeByAbiName(it) == null })
        assertEquals(emptyList<String>(), notSimpleLookup.filter { mappedTypeByAbiName(it) != null })
    }

    @Test
    fun generator_emits_projection_registrar_compiler_input() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Foundation.WidgetBase",
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val registrar = filesByName.getValue("projection-registrar.tsv").contents
        val manifest = filesByName.getValue("compiler-support.tsv").contents
        val widget = filesByName.getValue("Widget.kt").contents

        assertFalse(filesByName.containsKey("WinRTProjectionRegistrar.kt"))
        assertTrue(registrar, registrar.contains("kotlinClassName\tprojectedTypeName\tkind\tbaseTypeName\tmetadataClassName"))
        assertTrue(registrar, registrar.contains("sample.foundation.IWidget\tSample.Foundation.IWidget\tInterface\t\t"))
        assertTrue(registrar, registrar.contains("sample.foundation.Widget\tSample.Foundation.Widget\tRuntimeClass\tSample.Foundation.WidgetBase\tsample.foundation.Widget.Metadata"))
        assertTrue(manifest, manifest.contains("projection-registrar\tio.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic\tprojection-registrar.tsv\t2"))
        assertTrue(widget, widget.contains("WinRtProjectionSupportIntrinsic.ensureInitialized()"))
    }

    @Test
    fun generator_omits_suppressed_authored_types_from_projection_registrar_compiler_input() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "AuthoredWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Foundation.WidgetBase",
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ProjectedWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Foundation.WidgetBase",
                        ),
                    ),
                ),
            ),
        )

        val registrar = KotlinProjectionGenerator(
            emitSupportFiles = true,
            suppressedProjectionTypeNames = setOf("Sample.Foundation.AuthoredWidget"),
        )
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("projection-registrar.tsv")
            .contents

        assertFalse(registrar, registrar.contains("AuthoredWidget"))
        assertTrue(registrar, registrar.contains("sample.foundation.ProjectedWidget\tSample.Foundation.ProjectedWidget\tRuntimeClass\tSample.Foundation.WidgetBase\tsample.foundation.ProjectedWidget.Metadata"))
    }

    @Test
    fun generator_records_large_projection_registrar_as_compiler_input() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = (0 until 130).map { index ->
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget$index",
                            kind = WinRtTypeKind.RuntimeClass,
                        )
                    },
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val registrar = filesByName.getValue("projection-registrar.tsv").contents
        val manifest = filesByName.getValue("compiler-support.tsv").contents

        assertFalse(filesByName.containsKey("WinRTProjectionRegistrar.kt"))
        assertEquals(131, registrar.lineSequence().count { it.isNotBlank() })
        assertTrue(registrar, registrar.contains("sample.foundation.Widget0\tSample.Foundation.Widget0\tRuntimeClass\t\tsample.foundation.Widget0.Metadata"))
        assertTrue(registrar, registrar.contains("sample.foundation.Widget129\tSample.Foundation.Widget129\tRuntimeClass\t\tsample.foundation.Widget129.Metadata"))
        assertTrue(manifest, manifest.contains("projection-registrar\tio.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic\tprojection-registrar.tsv\t130"))
    }

    @Test
    fun generator_can_emit_expect_common_and_jvm_actual_interface_slice() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/IWidget.kt").contents
        assertGeneratedProjectionSuppressions(common)
        assertGeneratedProjectionSuppressions(jvm)
        assertTrue(common, common.contains("public interface IWidget"))
        assertFalse(common, common.contains("public expect interface IWidget"))
        assertTrue(common, common.contains("public fun rename(`value`: String): String"))
        assertTrue(common, common.contains("public val count: Int"))
        assertFalse(common, common.contains("NativeProjection"))
        assertTrue(jvm, jvm.contains("internal object IWidgetJvmProjection"))
        assertTrue(jvm, jvm.contains("fun wrap(instance: IUnknownReference): IWidget"))
        assertFalse(jvm, jvm.contains("public actual interface IWidget"))
        assertTrue(jvm, jvm.contains("private class NativeProjection"))
        assertTrue(jvm, jvm.contains("private object JvmAbi"))
        assertTrue(jvm, jvm.contains("FunctionDescriptor.of(ValueLayout.JAVA_INT"))
        assertTrue(jvm, jvm.contains("Linker.nativeLinker()"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_p_p"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun expect_actual_interface_slice_emits_jvm_event_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetChangedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("99999999-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition("add_Changed", "Windows.Foundation.EventRegistrationToken", parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetChangedHandler")), isSpecialName = true, methodRowId = 6),
                                WinRtMethodDefinition("remove_Changed", "Unit", parameters = listOf(WinRtParameterDefinition("token", "Windows.Foundation.EventRegistrationToken")), isSpecialName = true, methodRowId = 7),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                    addMethodRowId = 6,
                                    removeMethodRowId = 7,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/IWidget.kt").contents
        assertTrue(common, common.contains("public val changed: WinRtEvent<WidgetChangedHandler>"))
        assertTrue(common, common.contains("public fun addChanged(handler: WidgetChangedHandler): EventRegistrationToken"))
        assertTrue(common, common.contains("public fun removeChanged(token: EventRegistrationToken)"))
        assertTrue(common, common.contains("const val CHANGED_ADD_SLOT: Int = 6"))
        assertTrue(common, common.contains("const val CHANGED_REMOVE_SLOT: Int = 7"))
        assertTrue(jvm, jvm.contains("override val changed: WinRtEvent<WidgetChangedHandler> by lazy"))
        assertTrue(jvm, jvm.contains("WinRTEventProjectionHelper_"))
        assertTrue(jvm, jvm.contains(".createEventSource_"))
        assertTrue(jvm, jvm.contains("IWidget.Metadata.CHANGED_ADD_SLOT"))
        assertTrue(jvm, jvm.contains("override fun addChanged(handler: WidgetChangedHandler): EventRegistrationToken"))
        assertTrue(jvm, jvm.contains("changed.add(handler)"))
        assertTrue(jvm, jvm.contains("override fun removeChanged(token: EventRegistrationToken)"))
        assertTrue(jvm, jvm.contains("changed.remove(token)"))
    }

    @Test
    fun expect_actual_interface_slice_emits_jvm_projected_interface_abi_calls() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IChild",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetChild",
                                    returnTypeName = "Sample.Foundation.IChild",
                                ),
                                WinRtMethodDefinition(
                                    name = "SetChild",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("child", "Sample.Foundation.IChild")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Child",
                                    typeName = "Sample.Foundation.IChild",
                                    getterMethodName = "get_Child",
                                    setterMethodName = "put_Child",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/IWidget.kt").contents
        assertTrue(common, common.contains("public fun getChild(): IChild"))
        assertTrue(common, common.contains("public fun setChild(child: IChild)"))
        assertTrue(common, common.contains("public var child: IChild"))
        assertTrue(jvm, jvm.contains("IChildJvmProjection.wrap(__resultRef)"))
        assertTrue(jvm, jvm.contains("PlatformAbi.fromRawComPtr("))
        assertTrue(jvm, jvm.contains("nativeObject.pointer"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_p"))
        assertFalse(jvm, jvm.contains("IChild.Metadata.wrap"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun runtime_class_members_forward_to_native_interface_projection_in_single_source_layout() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                    methodRowId = 6,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    setterMethodName = "put_Title",
                                    getterMethodRowId = 7,
                                    setterMethodRowId = 8,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                    methodRowId = 6,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    setterMethodName = "put_Title",
                                    getterMethodRowId = 7,
                                    setterMethodRowId = 8,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator().generate(model).associateBy(KotlinProjectionFile::relativePath)
        val widget = filesByPath.getValue("sample/foundation/Widget.kt").contents

        assertFalse(widget, widget.contains("IWidget by IWidget.Metadata.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertTrue(widget, widget.contains("IWidget.Metadata.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertTrue(widget, widget.contains("override fun rename(`value`: String)"))
        assertTrue(widget, widget.contains("override var title: String"))
        assertFalse(widget, widget.contains("ComVtableInvoker"))
        assertFalse(widget, widget.contains("PlatformAbi.confinedScope"))
    }

    @Test
    fun expect_actual_interface_native_projection_implements_inherited_interface_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/IWidget.kt").contents
        assertTrue(common, common.contains("public interface IWidget : IWidgetBase"))
        assertFalse(common, common.contains("public expect interface IWidget"))
        assertTrue(jvm, jvm.contains("override fun reset()"))
        assertTrue(jvm, jvm.contains("IWidgetBase.Metadata.RESET_SLOT"))
        assertTrue(jvm, jvm.contains("override val count: Int"))
        assertTrue(jvm, jvm.contains("IWidgetBase.Metadata.COUNT_GETTER_SLOT"))
        assertTrue(jvm, jvm.contains("override fun rename(`value`: String): String"))
        assertTrue(jvm, jvm.contains("IWidget.Metadata.RENAME_SLOT"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_none"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_p"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_p_p"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun expect_actual_interface_native_projection_deduplicates_redeclared_inherited_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val jvm = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model)
            .single { it.relativePath == "jvmMain/kotlin/sample/foundation/IWidget.kt" }
            .contents

        assertEquals(1, "override fun reset\\(\\)".toRegex().findAll(jvm).count())
        assertEquals(1, "override val count: Int".toRegex().findAll(jvm).count())
        assertTrue(jvm, jvm.contains("IWidgetBase.Metadata.RESET_SLOT"))
        assertTrue(jvm, jvm.contains("IWidgetBase.Metadata.COUNT_GETTER_SLOT"))
        assertFalse(jvm, jvm.contains("slot =\n          IWidget.Metadata.RESET_SLOT"))
        assertFalse(jvm, jvm.contains("slot =\n          IWidget.Metadata.COUNT_GETTER_SLOT"))
    }

    @Test
    fun expect_actual_interface_slice_falls_back_for_conflicting_inherited_noexception_shape() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IRetryingWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryRefresh",
                                    returnTypeName = "Boolean",
                                    isNoException = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Status",
                                    typeName = "Int",
                                    getterMethodName = "get_Status",
                                    isNoException = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IThrowingWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryRefresh",
                                    returnTypeName = "Boolean",
                                    isNoException = false,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Status",
                                    typeName = "Int",
                                    getterMethodName = "get_Status",
                                    isNoException = false,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IRetryingWidget"),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IThrowingWidget"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IRetryingWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IRetryingWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents.contains("public interface IWidget"))
    }

    @Test
    fun expect_actual_interface_slice_falls_back_when_abi_call_plan_is_unavailable() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Accept",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("value", "Sample.Foundation.MissingType"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents
        assertTrue(common, common.contains("public interface IWidget"))
        assertFalse(common, common.contains("public expect interface IWidget"))
    }

    @Test
    fun expect_actual_interface_slice_falls_back_for_async_and_collection_shapes() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "RefreshAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncAction",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetNames",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents
        assertTrue(common, common.contains("public interface IWidget"))
        assertFalse(common, common.contains("public expect interface IWidget"))
    }

    @Test
    fun expect_actual_interface_slice_falls_back_for_static_events() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetChangedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("99999999-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents.contains("public interface IWidget"))
    }

    @Test
    fun expect_actual_interface_slice_falls_back_for_static_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Create",
                                    returnTypeName = "String",
                                    isStatic = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Version",
                                    typeName = "Int",
                                    isStatic = true,
                                    getterMethodName = "get_Version",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents.contains("public interface IWidget"))
    }

    @Test
    fun expect_actual_interface_slice_falls_back_for_setter_only_properties() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    setterMethodName = "put_Name",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/IWidget.kt").contents.contains("public interface IWidget"))
    }

    @Test
    fun generator_can_emit_expect_common_and_jvm_actual_runtime_class_slice() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/Widget.kt").contents
        val interfaceJvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/IWidget.kt").contents
        assertTrue(common, common.contains("public expect class Widget internal constructor("))
        assertTrue(common, common.contains(") : IWidget,\n    IWinRTObject"))
        assertFalse(common, common.contains("ComVtableInvoker"))
        assertTrue(jvm, jvm.contains("public actual class Widget internal actual constructor("))
        assertTrue(jvm, jvm.contains("IWidget by IWidgetJvmProjection.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertFalse(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertFalse(jvm, jvm.contains("override fun rename(`value`: String): String"))
        assertFalse(jvm, jvm.contains("override val count: Int"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
        assertTrue(interfaceJvm, interfaceJvm.contains("JvmAbi.invoke_p_p"))
    }

    @Test
    fun generator_escapes_keyword_property_access_in_single_source_runtime_class_forwarders() {
        val model = keywordPackagePropertyModel(propertyTypeName = "Int")

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .first { it.relativePath.endsWith("AppInfo.kt") }
            .contents

        assertTrue(contents, contents.contains("override val `package`: Int"))
        assertTrue(contents, contents.contains("_iAppInfoProjection.`package`"))
        assertFalse(contents, contents.contains("_iAppInfoProjection.package"))
    }

    @Test
    fun expect_actual_runtime_class_delegates_keyword_projected_object_properties() {
        val model = keywordPackagePropertyModel(
            propertyTypeName = "Windows.ApplicationModel.Package",
            includePackageRuntimeClass = true,
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)
        val commonInterface = filesByPath.getValue("commonMain/kotlin/windows/applicationmodel/IAppInfo.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/windows/applicationmodel/AppInfo.kt").contents

        assertTrue(commonInterface, commonInterface.contains("public val `package`: Package"))
        assertTrue(jvm, jvm.contains("IAppInfo by IAppInfoJvmProjection.wrap(Metadata.acquireInterface(_inner,"))
        assertFalse(jvm, jvm.contains("_iAppInfo.package"))
        assertFalse(jvm, jvm.contains("override val `package`: Package"))
    }

    @Test
    fun static_runtime_class_overload_binding_uses_declaring_static_interface_row_id() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ILauncherStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "LaunchAsync",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("uri", "String")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "LaunchAsync",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("uri", "String"),
                                        WinRtParameterDefinition("options", "String"),
                                    ),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Launcher",
                            kind = WinRtTypeKind.RuntimeClass,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.ILauncherStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "LaunchAsync",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("uri", "String")),
                                    isStatic = true,
                                    methodRowId = 100,
                                ),
                                WinRtMethodDefinition(
                                    name = "LaunchAsync",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("uri", "String"),
                                        WinRtParameterDefinition("options", "String"),
                                    ),
                                    isStatic = true,
                                    methodRowId = 101,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val launcher = KotlinProjectionGenerator()
            .generate(model)
            .first { it.relativePath.endsWith("Launcher.kt") }
            .contents

        assertTrue(launcher, launcher.contains("STATIC_LAUNCHASYNC_10_SLOT"))
        assertTrue(launcher, launcher.contains("STATIC_LAUNCHASYNC_11_SLOT"))
        assertFalse(launcher, launcher.contains("STATIC_LAUNCHASYNC_100_SLOT"))
        assertFalse(launcher, launcher.contains("STATIC_LAUNCHASYNC_101_SLOT"))
        assertFalse(launcher, launcher.contains("ABI binding is unavailable for method LaunchAsync"))
    }

    private fun keywordPackagePropertyModel(
        propertyTypeName: String,
        includePackageRuntimeClass: Boolean = false,
    ): WinRtMetadataModel {
        val property = WinRtPropertyDefinition(
            name = "Package",
            typeName = propertyTypeName,
            getterMethodName = "get_Package",
            getterMethodRowId = 1,
        )
        return WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.ApplicationModel",
                    types = buildList {
                        add(
                            WinRtTypeDefinition(
                                namespace = "Windows.ApplicationModel",
                                name = "IAppInfo",
                                kind = WinRtTypeKind.Interface,
                                iid = Guid("11111111-2222-3333-4444-555555555555"),
                                properties = listOf(property),
                            ),
                        )
                        add(
                            WinRtTypeDefinition(
                                namespace = "Windows.ApplicationModel",
                                name = "AppInfo",
                                kind = WinRtTypeKind.RuntimeClass,
                                defaultInterfaceName = "Windows.ApplicationModel.IAppInfo",
                                implementedInterfaces = listOf(
                                    WinRtInterfaceImplementationDefinition("Windows.ApplicationModel.IAppInfo", isDefault = true),
                                ),
                                properties = listOf(property.copy(getterMethodRowId = null)),
                            ),
                        )
                        if (includePackageRuntimeClass) {
                            add(
                                WinRtTypeDefinition(
                                    namespace = "Windows.ApplicationModel",
                                    name = "Package",
                                    kind = WinRtTypeKind.RuntimeClass,
                                ),
                            )
                        }
                    },
                ),
            ),
        )
    }

    @Test
    fun expect_actual_runtime_class_slice_forwards_event_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetChangedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("99999999-2222-3333-4444-555555555555"),
                            methods = listOf(WinRtMethodDefinition("Invoke", "Unit")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition("add_Changed", "Windows.Foundation.EventRegistrationToken", parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetChangedHandler")), isSpecialName = true, methodRowId = 6),
                                WinRtMethodDefinition("remove_Changed", "Unit", parameters = listOf(WinRtParameterDefinition("token", "Windows.Foundation.EventRegistrationToken")), isSpecialName = true, methodRowId = 7),
                            ),
                            events = listOf(
                                WinRtEventDefinition("Changed", "Sample.Foundation.WidgetChangedHandler", addMethodName = "add_Changed", removeMethodName = "remove_Changed", addMethodRowId = 6, removeMethodRowId = 7),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            events = listOf(
                                WinRtEventDefinition("Changed", "Sample.Foundation.WidgetChangedHandler", addMethodName = "add_Changed", removeMethodName = "remove_Changed", addMethodRowId = 6, removeMethodRowId = 7),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/Widget.kt").contents
        assertTrue(common, common.contains("public expect class Widget internal constructor("))
        assertTrue(jvm, jvm.contains("public actual class Widget internal actual constructor("))
        assertTrue(jvm, jvm.contains("IWidget by IWidgetJvmProjection.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertFalse(jvm, jvm.contains("override val changed: WinRtEvent<WidgetChangedHandler>"))
        assertFalse(jvm, jvm.contains("override fun addChanged(handler: WidgetChangedHandler): EventRegistrationToken"))
        assertFalse(jvm, jvm.contains("override fun removeChanged(token: EventRegistrationToken)"))
    }

    @Test
    fun expect_actual_runtime_class_slice_allows_members_covered_by_default_interface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/Widget.kt").contents
        assertTrue(common, common.contains("public expect class Widget internal constructor("))
        assertTrue(common, common.contains(") : IWidget,\n    IWinRTObject"))
        assertFalse(filesByPath.containsKey("sample/foundation/Widget.kt"))
        assertTrue(jvm, jvm.contains("public actual class Widget internal actual constructor("))
        assertTrue(jvm, jvm.contains("IWidget by IWidgetJvmProjection.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertFalse(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertFalse(jvm, jvm.contains("override fun rename(`value`: String): String"))
        assertFalse(jvm, jvm.contains("override val count: Int"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun expect_actual_runtime_class_slice_normalizes_nullable_public_interface_names() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget?", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/Widget.kt").contents
        assertTrue(common, common.contains("public expect class Widget internal constructor("))
        assertTrue(common, common.contains("IWidget"))
        assertTrue(jvm, jvm.contains("IWidget by IWidgetJvmProjection.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertFalse(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertFalse(jvm, jvm.contains("override fun reset()"))
        assertFalse(filesByPath.containsKey("sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_allows_multiple_non_conflicting_public_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IEnabledReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "IsEnabled",
                                    returnTypeName = "Boolean",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameReader",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameReader", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IEnabledReader"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/Widget.kt").contents
        assertTrue(common, common.contains("INameReader"))
        assertTrue(common, common.contains("IEnabledReader"))
        assertTrue(common, common.contains("IWinRTObject"))
        assertTrue(jvm, jvm.contains("INameReader by INameReaderJvmProjection.wrap(Metadata.acquireInterface(_inner,"))
        assertTrue(jvm, jvm.contains("IEnabledReader by IEnabledReaderJvmProjection.wrap(Metadata.acquireInterface(_inner,"))
        assertFalse(jvm, jvm.contains("override val name: String"))
        assertFalse(jvm, jvm.contains("override fun isEnabled(): Boolean"))
        assertFalse(filesByPath.containsKey("sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_forwards_inherited_public_interface_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBaseWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBaseWidget"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        val common = filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents
        val jvm = filesByPath.getValue("jvmMain/kotlin/sample/foundation/Widget.kt").contents
        assertTrue(common, common.contains("public expect class Widget internal constructor("))
        assertTrue(common, common.contains("IWidget"))
        assertTrue(common, common.contains("IWinRTObject"))
        assertTrue(jvm, jvm.contains("IWidget by IWidgetJvmProjection.wrap(Metadata.acquireInterface(_inner, IWidget.Metadata.IID))"))
        assertFalse(jvm, jvm.contains("private val _iBaseWidget: IBaseWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertFalse(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertFalse(jvm, jvm.contains("override fun reset()"))
        assertFalse(jvm, jvm.contains("override val name: String"))
        assertFalse(filesByPath.containsKey("sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_conflicting_public_interface_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValue",
                                    returnTypeName = "String",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICountReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValue",
                                    returnTypeName = "Int",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameReader",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameReader", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ICountReader"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/INameReader.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/INameReader.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents.contains("public class Widget"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_conflicting_public_interface_parameter_names() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameWriter",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ILabelWriter",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("label", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameWriter",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameWriter", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ILabelWriter"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/INameWriter.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/INameWriter.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents.contains("public class Widget"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_conflicting_public_interface_noexception_shape() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IRetryingWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryRefresh",
                                    returnTypeName = "Boolean",
                                    isNoException = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IThrowingWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryRefresh",
                                    returnTypeName = "Boolean",
                                    isNoException = false,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IRetryingWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IRetryingWidget", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IThrowingWidget"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IRetryingWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IRetryingWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents.contains("public class Widget"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_conflicting_public_interface_property_accessors() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IDisplayNameReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_DisplayName",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameReader",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameReader", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IDisplayNameReader"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/INameReader.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/INameReader.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
        assertTrue(filesByPath.getValue("commonMain/kotlin/sample/foundation/Widget.kt").contents.contains("public class Widget"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_class_member_type_mismatch() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValue",
                                    returnTypeName = "String",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetValue",
                                    returnTypeName = "Int",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_class_member_parameter_name_mismatch() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Rename",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("text", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_class_member_noexception_mismatch() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryRefresh",
                                    returnTypeName = "Boolean",
                                    isNoException = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Status",
                                    typeName = "Int",
                                    getterMethodName = "get_Status",
                                    isNoException = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryRefresh",
                                    returnTypeName = "Boolean",
                                    isNoException = false,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Status",
                                    typeName = "Int",
                                    getterMethodName = "get_Status",
                                    isNoException = false,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_property_setter_mismatch() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    setterMethodName = "put_Name",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_rejects_property_accessor_name_mismatch() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    setterMethodName = "put_Name",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_DisplayName",
                                    setterMethodName = "put_DisplayName",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_falls_back_for_static_events() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_falls_back_when_public_interface_uses_async_or_collections() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "RefreshAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncAction",
                                ),
                                WinRtMethodDefinition(
                                    name = "GetNames",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_falls_back_for_static_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Create",
                                    returnTypeName = "String",
                                    isStatic = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Version",
                                    typeName = "Int",
                                    isStatic = true,
                                    getterMethodName = "get_Version",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun expect_actual_runtime_class_slice_falls_back_for_setter_only_properties() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    setterMethodName = "put_Name",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByPath = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model).associateBy(KotlinProjectionFile::relativePath)

        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(filesByPath.containsKey("commonMain/kotlin/sample/foundation/Widget.kt"))
        assertFalse(filesByPath.containsKey("jvmMain/kotlin/sample/foundation/Widget.kt"))
    }

    @Test
    fun generator_emits_jvm_ffm_for_scalar_shape_without_comvtable_overload() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "TryUpdate",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("enabled", "Boolean"),
                                        WinRtParameterDefinition("index", "UInt"),
                                        WinRtParameterDefinition("opacity", "Float"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val jvm = KotlinProjectionGenerator(
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model)
            .single { it.relativePath == "jvmMain/kotlin/sample/foundation/IWidget.kt" }
            .contents

        assertTrue(jvm, jvm.contains("private val descriptor_i8_i32_f32_p: FunctionDescriptor"))
        assertTrue(jvm, jvm.contains("FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_BYTE,"))
        assertTrue(jvm, jvm.contains("ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS)"))
        assertTrue(jvm, jvm.contains("private fun invoke_i8_i32_f32_p("))
        assertTrue(jvm, jvm.contains("arg0: Byte"))
        assertTrue(jvm, jvm.contains("arg1: Int"))
        assertTrue(jvm, jvm.contains("arg2: Float"))
        assertTrue(jvm, jvm.contains("arg3: RawAddress"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_i8_i32_f32_p"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun expect_actual_layout_places_support_files_in_common_source_set() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                    ),
                ),
            ),
        )

        val files = KotlinProjectionGenerator(
            emitSupportFiles = true,
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generate(model)

        val paths = files.map(KotlinProjectionFile::relativePath)
        assertTrue(paths.contains("commonMain/kotlin/sample/foundation/IWidget.kt"))
        assertTrue(paths.contains("jvmMain/kotlin/sample/foundation/IWidget.kt"))
        assertFalse(paths.any { it.startsWith("io/github/composefluent/winrt/projections/support/") })
        assertFalse(paths.any { it.startsWith("jvmMain/kotlin/io/github/composefluent/winrt/projections/support/") })
    }

    @Test
    fun expect_actual_generate_to_removes_stale_unprefixed_support_files() {
        val outputRoot = Files.createTempDirectory("kotlin-winrt-expect-actual-support-")
        val staleSupportFile = outputRoot.resolve("io/github/composefluent/winrt/projections/support/StaleSupport.kt")
        Files.createDirectories(staleSupportFile.parent)
        Files.writeString(staleSupportFile, "package io.github.composefluent.winrt.projections.support\n")
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                    ),
                ),
            ),
        )

        val summary = KotlinProjectionGenerator(
            emitSupportFiles = true,
            generationLayout = KotlinProjectionGenerationLayout.ExpectActualJvm,
        ).generateTo(model, outputRoot)

        assertTrue(summary.deletedStaleFiles >= 1)
        assertFalse(Files.exists(staleSupportFile))
        assertTrue(outputRoot.resolve("commonMain/kotlin/sample/foundation/IWidget.kt").isRegularFile())
        assertTrue(outputRoot.resolve("jvmMain/kotlin/sample/foundation/IWidget.kt").isRegularFile())
    }

    @Test
    fun generator_projects_method_generic_parameters_like_cswinrt_method_generic_signature_branch() {
        // Mirrors .cswinrt/src/cswinrt/code_writers.h write_abi_signature MethodDef.GenericParam() handling.
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Transform",
                                    returnTypeName = "M0",
                                    genericParameterCount = 1,
                                    genericParameters = listOf(WinRtGenericParameterDefinition("M0", 0)),
                                    parameters = listOf(WinRtParameterDefinition("value", "M0")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val contents = filesByName.getValue("IWidget.kt").contents
        assertTrue(contents, contents.contains("fun <M0> transform(`value`: M0): M0"))
        assertTrue(contents, contents.contains("WinRtGenericParameterProjection.createReference(value)"))
        assertTrue(contents, contents.contains("WinRtGenericParameterProjection.fromAbi<M0>(__resultPointer)"))
        assertFalse(contents, contents.contains("import M0"))
    }

    @Test
    fun generate_to_skips_unchanged_files_and_removes_stale_outputs() {
        val outputRoot = Files.createTempDirectory("kotlin-winrt-generator-incremental-")
        val fullModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IFirst",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ISecond",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                    ),
                ),
            ),
        )
        val narrowedModel = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IFirst",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                    ),
                ),
            ),
        )
        val generator = KotlinProjectionGenerator()

        val first = generator.generateTo(fullModel, outputRoot)
        val second = generator.generateTo(fullModel, outputRoot)
        val narrowed = generator.generateTo(narrowedModel, outputRoot)

        assertEquals(2, first.renderedFiles)
        assertEquals(2, first.writtenFiles)
        assertEquals(0, first.unchangedFiles)
        assertEquals(0, first.deletedStaleFiles)
        assertEquals(2, second.renderedFiles)
        assertEquals(0, second.writtenFiles)
        assertEquals(2, second.unchangedFiles)
        assertEquals(0, second.deletedStaleFiles)
        assertEquals(1, narrowed.renderedFiles)
        assertEquals(0, narrowed.writtenFiles)
        assertEquals(1, narrowed.unchangedFiles)
        assertEquals(1, narrowed.deletedStaleFiles)
        assertTrue(outputRoot.resolve("sample/foundation/IFirst.kt").isRegularFile())
        assertFalse(outputRoot.resolve("sample/foundation/ISecond.kt").isRegularFile())
    }

    @Test
    fun generator_reproduces_cswinrt_json_value_function_calls_surface_from_real_winmd() {
        // Mirrors .cswinrt/src/Tests/FunctionalTests/JsonValueFunctionCalls/Program.cs.
        val filesByName = generateWindowsDataJsonFromInstalledWinmd()

        val jsonValue = filesByName.getValue("JsonValue.kt").contents
        val iJsonValue = filesByName.getValue("IJsonValue.kt").contents

        assertTrue(jsonValue, jsonValue.contains("IStringable"))
        assertTrue(jsonValue, jsonValue.contains("fun createNumberValue(input: Double): JsonValue"))
        assertTrue(jsonValue, jsonValue.contains("fun getNumber(): Double"))
        assertTrue(jsonValue, jsonValue.contains("override fun toString(): String"))
        assertTrue(iJsonValue, iJsonValue.contains("fun getNumber(): Double"))
    }

    @Test
    fun generator_reproduces_cswinrt_json_api_compat_surface_from_real_winmd() {
        // Mirrors the Windows.Data.Json block in .cswinrt/src/Tests/UnitTest/ApiCompatTests.cs.
        val filesByName = generateWindowsDataJsonFromInstalledWinmd()

        val jsonObject = filesByName.getValue("JsonObject.kt").contents
        val iJsonObject = filesByName.getValue("IJsonObject.kt").contents
        val jsonArray = filesByName.getValue("JsonArray.kt").contents
        val iJsonValue = filesByName.getValue("IJsonValue.kt").contents

        assertTrue(jsonObject, jsonObject.contains("fun parse(input: String): JsonObject"))
        assertTrue(jsonObject, jsonObject.contains("fun getNamedString(name: String, defaultValue: String): String"))
        assertTrue(jsonObject, jsonObject.contains("fun getNamedValue(name: String): JsonValue"))
        assertTrue(jsonObject, jsonObject.contains("fun getNamedBoolean(name: String, defaultValue: Boolean): Boolean"))
        assertTrue(jsonObject, jsonObject.contains("fun getNamedArray(name: String, defaultValue: JsonArray): JsonArray"))
        assertTrue(iJsonObject, iJsonObject.contains("fun getNamedArray(name: String): JsonArray"))
        assertTrue(jsonArray, jsonArray.contains("public constructor()"))
        assertTrue(iJsonValue, iJsonValue.contains("val valueType: JsonValueType"))
        assertTrue(iJsonValue, iJsonValue.contains("fun getObject(): JsonObject"))
    }

    @Test
    fun generator_reproduces_windows_data_json_smoke_slice_declaration_set() {
        val filesByName = KotlinProjectionGenerator()
            .generate(windowsDataJsonProjectionModel())
            .associateBy { it.relativePath.substringAfterLast('/') }

        assertEquals(
            listOf(
                "IJsonArray.kt",
                "IJsonArrayStatics.kt",
                "IJsonErrorStatics.kt",
                "IJsonObject.kt",
                "IJsonObjectStatics.kt",
                "IJsonValue.kt",
                "IJsonValueStatics.kt",
                "JsonArray.kt",
                "JsonError.kt",
                "JsonErrorStatus.kt",
                "JsonObject.kt",
                "JsonValue.kt",
                "JsonValueType.kt",
            ),
            filesByName.keys.sorted(),
        )

        val jsonObject = filesByName.getValue("JsonObject.kt").contents
        val jsonArray = filesByName.getValue("JsonArray.kt").contents
        val jsonValue = filesByName.getValue("JsonValue.kt").contents
        val jsonError = filesByName.getValue("JsonError.kt").contents
        val iJsonObject = filesByName.getValue("IJsonObject.kt").contents
        val iJsonValueStatics = filesByName.getValue("IJsonValueStatics.kt").contents

        assertGeneratedProjectionSuppressions(jsonObject)
        assertGeneratedProjectionSuppressions(iJsonObject)
        assertTrue(jsonObject, jsonObject.contains("public class JsonObject internal constructor("))
        assertTrue(jsonObject, jsonObject.contains("IJsonObject"))
        assertTrue(jsonObject, jsonObject.contains("IWinRTObject"))
        assertTrue(jsonObject, jsonObject.contains("private val _inner: IInspectableReference"))
        assertTrue(jsonObject, jsonObject.contains("override val nativeObject: ComObjectReference"))
        assertTrue(jsonObject, jsonObject.contains("fun getNamedString(name: String): String"))
        assertTrue(jsonObject, jsonObject.contains("fun setNamedValue(name: String, `value`: JsonValue)"))
        assertTrue(jsonObject, jsonObject.contains("nativeObject.pointer"))
        assertTrue(jsonObject, jsonObject.contains("fun parse(json: String): JsonObject"))
        assertFalse(jsonObject, jsonObject.contains("fun parse(json: String): JsonObject = error(\"WinRT ABI binding is unavailable\")"))
        assertTrue(jsonObject, jsonObject.contains("HString.createReference(json).use { __jsonAbi ->"))
        assertTrue(jsonObject, jsonObject.contains("val STATIC_PARSE_SLOT: Int = IJsonObjectStatics.Metadata.PARSE_SLOT"))
        assertTrue(jsonObject, jsonObject.contains("public object StaticInterfaces"))
        assertTrue(jsonObject, jsonObject.contains("public const val IJSONOBJECTSTATICS: String = \"Windows.Data.Json.IJsonObjectStatics\""))
        assertTrue(jsonObject, jsonObject.contains("private val _iJsonObjectStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jsonObject, jsonObject.contains("private val _defaultInterface: ComObjectReference"))
        assertTrue(jsonObject, jsonObject.contains("Metadata.acquireInterface(_inner, IJsonObject.Metadata.IID)"))

        assertTrue(jsonArray, jsonArray.contains("public class JsonArray internal constructor("))
        assertTrue(jsonArray, jsonArray.contains("fun getStringAt(index: UInt): String"))
        assertTrue(jsonArray, jsonArray.contains("fun create(): JsonArray"))
        assertTrue(jsonArray, jsonArray.contains("Metadata.wrap("))
        assertTrue(jsonArray, jsonArray.contains("ActivationFactory.activate()"))

        assertTrue(jsonValue, jsonValue.contains("public class JsonValue internal constructor("))
        assertTrue(jsonValue, jsonValue.contains("fun stringify(): String"))
        assertTrue(jsonValue, jsonValue.contains("fun createStringValue(`value`: String): JsonValue"))
        assertTrue(jsonValue, jsonValue.contains("val STATIC_CREATESTRINGVALUE_SLOT: Int ="))
        assertTrue(jsonValue, jsonValue.contains("IJsonValueStatics.Metadata.CREATESTRINGVALUE_SLOT"))
        assertTrue(
            jsonValue,
            jsonValue.contains("JsonValueType.Metadata.fromAbi") ||
                jsonValue.contains("_iJsonValueProjection.valueType"),
        )
        assertFalse(jsonValue, jsonValue.contains("JsonValueType.Metadata.wrap"))

        assertTrue(jsonError, jsonError.contains("public class JsonError internal constructor("))
        assertTrue(jsonError, jsonError.contains("fun getJsonStatus(hResult: Int): JsonErrorStatus"))
        assertTrue(jsonError, jsonError.contains("JsonErrorStatus.Metadata.fromAbi"))

        assertTrue(iJsonObject, iJsonObject.contains("fun getNamedArray(name: String): JsonArray"))
        assertTrue(iJsonObject, iJsonObject.contains("fun setNamedValue(name: String, `value`: JsonValue)"))

        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("fun createBooleanValue(`value`: Boolean): JsonValue"))
        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("fun createNumberValue(`value`: Double): JsonValue"))
        assertTrue(iJsonValueStatics, iJsonValueStatics.contains("fun createStringValue(`value`: String): JsonValue"))
    }

    @Test
    fun plans_deterministic_projection_paths_from_metadata_model() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValue", kind = WinRtTypeKind.RuntimeClass),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "IJsonValue", kind = WinRtTypeKind.Interface),
                        WinRtTypeDefinition(namespace = "Windows.Data.Json", name = "JsonValueType", kind = WinRtTypeKind.Enum),
                    ),
                ),
            ),
        )

        val plans = KotlinProjectionPlanner().plan(model)

        assertEquals(
            listOf(
                "windows/data/json/IJsonValue.kt",
                "windows/data/json/JsonValue.kt",
                "windows/data/json/JsonValueType.kt",
            ),
            plans.map { it.relativePath },
        )
        assertEquals(
            listOf(
                KotlinProjectionDeclarationKind.Interface,
                KotlinProjectionDeclarationKind.Class,
                KotlinProjectionDeclarationKind.Enum,
            ),
            plans.map { it.declarationKind },
        )
    }

    @Test
    fun renders_runtime_class_and_static_methods_from_metadata_shape() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonObjectStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getNamedString",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                                WinRtMethodDefinition(
                                    name = "parse",
                                    returnTypeName = "JsonObject",
                                    parameters = listOf(WinRtParameterDefinition("json", "String")),
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()

        assertEquals("windows/data/json/JsonObject.kt", file.relativePath)
        assertTrue(file.contents.contains("package windows.`data`.json"))
        assertTrue(file.contents.contains("public class JsonObject internal constructor("))
        assertTrue(file.contents.contains("private val _inner: IInspectableReference"))
        assertTrue(file.contents.contains("WinRT ABI binding is unavailable for method getNamedString"))
        assertTrue(file.contents.contains("companion object"))
        assertTrue(file.contents.contains("WinRT ABI binding is unavailable for method parse"))
        assertFalse(file.contents.contains("Not yet bound to winrt-runtime"))
        assertFalse(file.contents.contains("JsonValueType"))
    }

    @Test
    fun planner_carries_metadata_contract_fields_for_later_generator_passes() {
        val interfaceIid = Guid("11111111-2222-3333-4444-555555555555")
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = interfaceIid,
                            methods = listOf(WinRtMethodDefinition(name = "ping", returnTypeName = "Unit")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("66666666-7777-8888-9999-000000000000"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IComposableWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-7777-8888-9999-000000000000"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IComposableWidgetFactory",
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "create", returnTypeName = "Widget", isStatic = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plans = KotlinProjectionPlanner().plan(model)
        val interfacePlan = plans.first { it.type.name == "IWidget" }
        val classPlan = plans.first { it.type.name == "Widget" }

        assertEquals(interfaceIid, interfacePlan.interfaceIid)
        assertEquals("Sample.Foundation.IWidget", classPlan.defaultInterfaceName)
        assertEquals(interfaceIid, classPlan.defaultInterfaceIid)
        assertEquals(listOf("Sample.Foundation.IWidgetStatics"), classPlan.staticInterfaceNames)
        assertEquals(
            listOf(
                KotlinProjectionInterfaceBinding(
                    qualifiedName = "Sample.Foundation.IWidgetStatics",
                    iid = Guid("66666666-7777-8888-9999-000000000000"),
                ),
            ),
            classPlan.staticInterfaceBindings,
        )
        assertTrue(classPlan.implementedInterfaceBindings.isEmpty())
        assertEquals("Sample.Foundation.IComposableWidgetFactory", classPlan.composableFactoryInterfaceName)
        assertEquals(Guid("aaaaaaaa-7777-8888-9999-000000000000"), classPlan.composableFactoryInterfaceIid)
        assertEquals(
            listOf(KotlinProjectionCompanionKind.Metadata),
            interfacePlan.companionKinds,
        )
        assertEquals(
            listOf(
                KotlinProjectionCompanionKind.Metadata,
                KotlinProjectionCompanionKind.ActivationFactory,
                KotlinProjectionCompanionKind.StaticInterfaces,
                KotlinProjectionCompanionKind.ComposableFactory,
            ),
            classPlan.companionKinds,
        )
    }

    @Test
    fun planner_consumes_metadata_handoff_descriptors_for_abi_emission() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetChangedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("99999999-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("message", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getTitle",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("class", "String")),
                                    methodRowId = 9,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    addMethodRowId = 10,
                                    removeMethodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getTitle",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("class", "String")),
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetChangedHandler",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plansByName = KotlinProjectionPlanner().plan(model).associateBy { it.type.name }
        val interfacePlan = plansByName.getValue("IWidget")
        val classPlan = plansByName.getValue("Widget")
        val getTitleSlot = interfacePlan.abiSlotBindings.single { it.constantName == "GETTITLE_SLOT" }
        val getTitleBinding = classPlan.instanceMemberBindings.single { it.bindingName == "GETTITLE_SLOT" }
        val eventDescriptor = classPlan.eventInvokeDescriptors.single { it.eventName == "Changed" }

        assertEquals(6, getTitleSlot.slot)
        assertEquals("getTitle", getTitleSlot.descriptor?.methodName)
        assertEquals("String", getTitleBinding.signatureDescriptor?.projectionReturnTypeName)
        assertEquals("IntPtr", getTitleBinding.signatureDescriptor?.abiReturnTypeName)
        assertEquals("`class`", getTitleBinding.signatureDescriptor?.parameters?.single()?.escapedName)
        assertTrue(getTitleBinding.marshalerPlanDescriptor?.requiresDispose == true)
        assertEquals("Sample.Foundation.WidgetChangedHandler", eventDescriptor.delegateTypeName)
        assertEquals("Invoke", eventDescriptor.invokeMethodName)
        assertTrue(classPlan.typeDeclarationDescriptor.writesWrapperDeclaration)
        assertEquals("WidgetActivationFactory", classPlan.factorySurfaceDescriptor?.activationFactoryCacheName)
        assertEquals(listOf("Sample_Foundation_IWidgetCache"), classPlan.objectReferenceSurfaceDescriptor?.objectReferenceNames)
        assertEquals(emptyList<String>(), classPlan.requiredInterfaceAugmentationDescriptor?.requiredInterfaceNames)
        assertEquals(emptyList<String>(), classPlan.moduleActivationAndAuthoringDescriptor?.moduleActivationFactoryEntries)

        val generatedWidget = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("/Widget.kt") }
            .contents
        assertTrue(generatedWidget, generatedWidget.contains("const val WRITES_WRAPPER_DECLARATION: Boolean = true"))
        assertTrue(generatedWidget, generatedWidget.contains("const val FACTORY_CACHE_NAME: String = \"WidgetActivationFactory\""))
        assertTrue(generatedWidget, generatedWidget.contains("val OBJECT_REFERENCE_NAMES: List<String> = listOf(\"Sample_Foundation_IWidgetCache\")"))
        assertTrue(generatedWidget, generatedWidget.contains("val REQUIRED_INTERFACE_NAMES: List<String> = listOf()"))
    }

    @Test
    fun planner_maps_all_metadata_type_kinds_into_declaration_plans() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Status", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("title", "String"),
                                        WinRtParameterDefinition("count", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val plans = KotlinProjectionPlanner().plan(model)

        assertEquals(
            listOf(
                KotlinProjectionDeclarationKind.Struct,
                KotlinProjectionDeclarationKind.Enum,
                KotlinProjectionDeclarationKind.Delegate,
            ),
            plans.map { it.declarationKind },
        )
        assertEquals(
            listOf(
                "Point",
                "Status",
                "WidgetHandler",
            ),
            plans.map { it.type.name },
        )
        assertTrue(plans.all { it.companionKinds.isEmpty() })
    }

    @Test
    fun generator_projects_system_object_as_kotlin_object_with_winrt_object_marshaling() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IObjectSource",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetObject",
                                    returnTypeName = "System.Object",
                                    parameters = listOf(WinRtParameterDefinition("input", "System.Object")),
                                ),
                                WinRtMethodDefinition(
                                    name = "EchoObjectAlias",
                                    returnTypeName = "Object",
                                    parameters = listOf(WinRtParameterDefinition("input", "Object")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single().contents

        assertTrue(contents, contents.contains("fun getObject(input: Any?): Any?"))
        assertTrue(contents, contents.contains("fun echoObjectAlias(input: Any?): Any?"))
        assertTrue(contents, contents.contains("winRtObjectMarshaler(input).use"))
        assertFalse(contents, contents.contains("fun getObject(input: IInspectableReference): IInspectableReference"))
        assertFalse(contents, contents.contains("fun echoObjectAlias(input: IInspectableReference): IInspectableReference"))
    }

    @Test
    fun planner_tracks_cswinrt_declaration_ownership_traits() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetAttribute",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "System.Attribute",
                            isAttributeType = true,
                            isSealedType = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetOverrides",
                            kind = WinRtTypeKind.Interface,
                            isExclusiveTo = true,
                            iid = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "InternalContract",
                            kind = WinRtTypeKind.Interface,
                            isProjectionInternal = true,
                            iid = Guid("11111111-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetContract",
                            kind = WinRtTypeKind.Struct,
                            isApiContract = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetStatics",
                            kind = WinRtTypeKind.RuntimeClass,
                            isStaticType = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                        ),
                    ),
                ),
            ),
        )

        val plansByName = KotlinProjectionPlanner().plan(model).associateBy { it.type.name }

        assertEquals(
            listOf(KotlinProjectionSpecializationKind.AttributeClass),
            plansByName.getValue("WidgetAttribute").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionModifier.Sealed),
            plansByName.getValue("WidgetAttribute").modifiers,
        )
        assertTrue(plansByName.getValue("WidgetAttribute").companionKinds.isEmpty())

        assertEquals(KotlinProjectionVisibility.Internal, plansByName.getValue("IWidgetOverrides").visibility)
        assertEquals(
            listOf(KotlinProjectionSpecializationKind.ExclusiveInterface),
            plansByName.getValue("IWidgetOverrides").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionCompanionKind.Metadata),
            plansByName.getValue("IWidgetOverrides").companionKinds,
        )

        assertEquals(KotlinProjectionVisibility.Internal, plansByName.getValue("InternalContract").visibility)
        assertEquals(
            listOf(
                KotlinProjectionSpecializationKind.ProjectionInternal,
            ),
            plansByName.getValue("InternalContract").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionCompanionKind.Metadata),
            plansByName.getValue("InternalContract").companionKinds,
        )

        assertEquals(
            listOf(KotlinProjectionSpecializationKind.ApiContract),
            plansByName.getValue("WidgetContract").specializationKinds,
        )
        assertEquals(KotlinProjectionDeclarationKind.Enum, plansByName.getValue("WidgetContract").declarationKind)
        assertTrue(plansByName.getValue("WidgetContract").companionKinds.isEmpty())

        assertEquals(
            listOf(KotlinProjectionSpecializationKind.StaticClass),
            plansByName.getValue("WidgetStatics").specializationKinds,
        )
        assertEquals(
            listOf(KotlinProjectionModifier.Static),
            plansByName.getValue("WidgetStatics").modifiers,
        )
        assertTrue(plansByName.getValue("WidgetStatics").companionKinds.isEmpty())

        assertEquals(
            listOf(KotlinProjectionModifier.Sealed),
            plansByName.getValue("Widget").modifiers,
        )
    }

    @Test
    fun generator_renders_shells_for_non_class_declarations() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Status", kind = WinRtTypeKind.Enum),
                        WinRtTypeDefinition(namespace = "Sample.Foundation", name = "Point", kind = WinRtTypeKind.Struct),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("title", "String"),
                                        WinRtParameterDefinition("count", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("Status.kt").contents.contains("enum class Status"))
        assertTrue(filesByName.getValue("Point.kt").contents.contains("class Point"))
        assertTrue(filesByName.getValue("WidgetHandler.kt").contents.contains("fun interface WidgetHandler"))
        assertTrue(filesByName.getValue("WidgetHandler.kt").contents.contains("public operator fun invoke(title: String, count: Int): Boolean"))
    }

    @Test
    fun generator_projects_flags_enums_as_bitmask_value_types() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.System",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.System",
                            name = "VirtualKeyModifiers",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.UInt32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("None", 0u),
                                WinRtEnumMemberDefinition("Control", 1u),
                                WinRtEnumMemberDefinition("Shift", 2u),
                            ),
                            customAttributes = listOf(WinRtCustomAttributeDefinition("System.FlagsAttribute")),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("VirtualKeyModifiers.kt") }
            .contents

        assertTrue(contents, contents.contains("@JvmInline"))
        assertTrue(contents, contents.contains("public value class VirtualKeyModifiers("))
        assertTrue(contents, contents.contains("public val abiValue: UInt"))
        assertTrue(contents, contents.contains("public val Control: VirtualKeyModifiers = VirtualKeyModifiers(1.toUInt())"))
        assertTrue(contents, contents.contains("public fun fromAbi(abiValue: UInt): VirtualKeyModifiers"))
        assertTrue(contents, contents.contains("VirtualKeyModifiers(abiValue)"))
        assertTrue(contents, contents.contains("public operator fun contains(flag: VirtualKeyModifiers): Boolean {"))
        assertTrue(contents, contents.contains("val masked = abiValue and flag.abiValue"))
        assertTrue(contents, contents.contains("return masked == flag.abiValue"))
        assertTrue(contents, contents.contains("public fun hasFlag(flag: VirtualKeyModifiers): Boolean"))
        assertTrue(contents, contents.contains("public infix fun or(other: VirtualKeyModifiers): VirtualKeyModifiers"))
        assertFalse(contents, contents.contains("enum class VirtualKeyModifiers"))
        assertFalse(contents, contents.contains("Unknown Windows.System.VirtualKeyModifiers ABI value"))
    }

    @Test
    fun renderer_uses_visibility_modifiers_and_metadata_companion_shells() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IInternalContract",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            isProjectionInternal = true,
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 5),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler", addMethodRowId = 9, removeMethodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-2222-3333-4444-555555555555"),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Loaded",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodRowId = 11,
                                    removeMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("baseInterface", "System.Object"),
                                        WinRtParameterDefinition("innerInterface", "System.Object"),
                                    ),
                                    methodRowId = 13,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "create",
                                    returnTypeName = "Widget",
                                    isStatic = true,
                                    methodRowId = 20,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 21),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", isStatic = true, getterMethodName = "get_Count", getterMethodRowId = 22),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Changed", delegateTypeName = "Sample.Foundation.WidgetHandler", addMethodRowId = 23, removeMethodRowId = 24),
                                WinRtEventDefinition(name = "Loaded", delegateTypeName = "Sample.Foundation.WidgetHandler", isStatic = true, addMethodRowId = 25, removeMethodRowId = 26),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetStatics",
                            kind = WinRtTypeKind.RuntimeClass,
                            isStaticType = true,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("55555555-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetContract",
                            kind = WinRtTypeKind.Struct,
                            isApiContract = true,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal interface IInternalContract"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("companion object Metadata"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("IID: Guid = Guid(\"11111111-2222-3333-4444-555555555555\")"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("const val NAME_GETTER_METHOD_ROW_ID: Int = 5"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("const val CHANGED_ADD_METHOD_ROW_ID: Int = 9"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 10"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("const val CHANGED_ADD_SLOT: Int = 7"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("const val CHANGED_REMOVE_SLOT: Int = 8"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("val name: String"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("val changed: WinRtEvent<WidgetHandler>"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("fun addChanged(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("fun removeChanged(token: EventRegistrationToken)"))

        val widgetContents = filesByName.getValue("Widget.kt").contents
        assertTrue(widgetContents.contains("public class Widget internal constructor("))
        assertTrue(widgetContents.contains("private val _inner: IInspectableReference"))
        assertTrue(widgetContents.contains("private val _defaultInterface: ComObjectReference"))
        assertTrue(widgetContents.contains("ActivationFactory.activateInstance(Metadata.TYPE_NAME)"))
        assertTrue(widgetContents.contains("val name: String"))
        assertTrue(widgetContents.contains("companion object Metadata"))
        assertFalse(widgetContents.contains("init {\n        register()\n    }"))
        assertTrue(widgetContents.contains("internal fun acquireInterface(instance: IInspectableReference, iid: Guid): IUnknownReference"))
        assertTrue(widgetContents.contains("ComWrappersSupport.registerRuntimeClassFactory(TYPE_NAME) { instance -> wrap(instance) }"))
        assertTrue(widgetContents.contains("Projections.registerCustomAbiTypeMapping("))
        assertTrue(widgetContents.contains("Widget::class"))
        assertTrue(widgetContents.contains("TYPE_NAME"))
        assertTrue(widgetContents.contains("isRuntimeClass = true"))
        assertTrue(widgetContents.contains("Projections.registerDefaultInterfaceType(Widget::class, IWidget::class)"))
        assertTrue(widgetContents.contains("val __managed = ComWrappersSupport.findObject(PlatformAbi.fromRawComPtr(instance.pointer),"))
        assertTrue(widgetContents.contains("Widget::class)"))
        assertTrue(widgetContents.contains("if (__managed != null)"))
        assertTrue(widgetContents.contains("return Widget(instance, kotlin.Unit)"))
        assertTrue(widgetContents.contains("public constructor() : this(ComposableFactory.createInstance(), kotlin.Unit)"))
        assertTrue(widgetContents.contains("const val CREATE_METHOD_ROW_ID: Int = 20"))
        assertTrue(widgetContents.contains("const val NAME_GETTER_METHOD_ROW_ID: Int = 21"))
        assertTrue(widgetContents.contains("const val COUNT_GETTER_METHOD_ROW_ID: Int = 22"))
        assertTrue(widgetContents.contains("const val CHANGED_ADD_METHOD_ROW_ID: Int = 23"))
        assertTrue(widgetContents.contains("const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 24"))
        assertTrue(widgetContents.contains("const val LOADED_ADD_METHOD_ROW_ID: Int = 25"))
        assertTrue(widgetContents.contains("const val LOADED_REMOVE_METHOD_ROW_ID: Int = 26"))
        assertTrue(widgetContents.contains("fun acquireDefaultInterface(instance: IInspectableReference): IUnknownReference"))
        assertTrue(widgetContents.contains("acquireInterface(instance, DEFAULT_INTERFACE_IID)"))
        assertTrue(widgetContents.contains("fun create(): Widget"))
        assertTrue(widgetContents.contains("Metadata.wrap("))
        assertTrue(widgetContents.contains("ActivationFactory.activate()"))
        assertTrue(widgetContents.contains("val count: Int"))
        assertTrue(widgetContents.contains("val changed: WinRtEvent<WidgetHandler> by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("WinRtEvent(::addChanged, ::removeChanged)"))
        assertTrue(widgetContents.contains("fun addChanged(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("fun removeChanged(token: EventRegistrationToken)"))
        assertTrue(widgetContents.contains("fun addLoaded(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("val loaded: WinRtEvent<WidgetHandler> by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("\"Sample.Foundation.IWidgetStatics\""))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics()"))
        assertTrue(widgetContents.contains("fun addLoaded(handler: WidgetHandler): EventRegistrationToken ="))
        assertTrue(widgetContents.contains("loaded.add(handler)"))
        assertTrue(widgetContents.contains("public object ActivationFactory"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("val FACTORY_INTERFACE_IID: Guid = Guid(\"44444444-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("fun acquire(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.composefluent.winrt.runtime.ActivationFactory.get(RUNTIME_CLASS,"))
        assertTrue(widgetContents.contains("fun activate(): IInspectableReference"))
        assertTrue(widgetContents.contains("public object StaticInterfaces"))
        assertTrue(widgetContents.contains("val IWIDGETSTATICS_IID: Guid = Guid(\"33333333-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("fun iWidgetStatics(): IUnknownReference"))
        assertTrue(widgetContents.contains("io.github.composefluent.winrt.runtime.ActivationFactory.get(Metadata.TYPE_NAME,"))
        assertTrue(widgetContents.contains("public object ComposableFactory"))
        assertTrue(widgetContents.contains("public const val DEFAULT_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("val DEFAULT_INTERFACE_IID: Guid = Guid(\"22222222-2222-3333-4444-555555555555\")"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("fun acquire(): IUnknownReference"))
        assertTrue(widgetContents.contains("fun createInstance(): IInspectableReference"))
        assertTrue(widgetContents.contains("IWidgetFactory.Metadata.CREATEINSTANCE_SLOT"))
        assertTrue(widgetContents.contains("initializeComposableReference(it,"))
        assertTrue(widgetContents.contains("DEFAULT_INTERFACE_IID"))
        assertTrue(widgetContents.contains("ComWrappersSupport.registerComposableWrapper(this, _inner)"))
        assertEquals(1, "companion object Metadata".toRegex().findAll(widgetContents).count())

        val widgetFactoryContents = filesByName.getValue("IWidgetFactory.kt").contents
        assertTrue(widgetFactoryContents.contains("const val CREATEINSTANCE_METHOD_ROW_ID: Int = 13"))
        assertTrue(widgetFactoryContents.contains("const val CREATEINSTANCE_SLOT: Int = 6"))

        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("public class WidgetStatics"))
        assertTrue(filesByName.getValue("WidgetStatics.kt").contents.contains("static WinRT class shell"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("public enum class WidgetContract"))
        assertTrue(filesByName.getValue("WidgetContract.kt").contents.contains("api contract WinRT declaration shell"))
    }

    @Test
    fun generator_orders_interface_slots_across_properties_events_and_methods() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IMixedStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Start",
                                    returnTypeName = "Unit",
                                    methodRowId = 101,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Current",
                                    typeName = "String",
                                    getterMethodName = "get_Current",
                                    getterMethodRowId = 100,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .single { it.relativePath.endsWith("IMixedStatics.kt") }
            .contents

        assertTrue(contents.contains("const val CURRENT_GETTER_SLOT: Int = 6"))
        assertTrue(contents.contains("const val START_SLOT: Int = 7"))
    }

    @Test
    fun generator_emits_single_native_projection_wrapper_for_method_only_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICalculator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Add",
                                    returnTypeName = "Int",
                                    parameters = listOf(
                                        WinRtParameterDefinition("left", "Int"),
                                        WinRtParameterDefinition("right", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single().contents

        assertTrue(contents.contains("TYPE_HANDLE: WinRtTypeHandle"))
        assertTrue(contents.contains("WinRtTypeHandle("))
        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("fun wrap(instance: IUnknownReference): ICalculator = NativeProjection(instance)"))
        assertFalse(contents.contains("return object : ICalculator, IWinRTObject"))
    }

    @Test
    fun generator_marshals_nullable_delegate_setters_without_runtime_proxy_fallback() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IAsyncAction",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555585"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IAsyncInfo"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetResults",
                                    returnTypeName = "Unit",
                                    methodRowId = 8,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Completed",
                                    typeName = "Sample.Foundation.AsyncActionCompletedHandler?",
                                    getterMethodName = "get_Completed",
                                    setterMethodName = "put_Completed",
                                    getterMethodRowId = 6,
                                    setterMethodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IAsyncInfo",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555587"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Id",
                                    typeName = "UInt",
                                    getterMethodName = "get_Id",
                                    getterMethodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "AsyncActionCompletedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555586"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("asyncInfo", "Sample.Foundation.IAsyncAction"),
                                        WinRtParameterDefinition("asyncStatus", "Int"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true).generate(model)
            .single { it.relativePath.endsWith("IAsyncAction.kt") }
            .contents

        assertTrue(contents, contents.contains("private class NativeProjection("))
        assertTrue(contents, contents.contains("WinRtDelegateBridge.createDelegateArgument("))
        assertTrue(contents, contents.contains("callback = __valueCallback"))
        assertTrue(contents, contents.contains("value?.let { value ->"))
        assertFalse(contents, contents.contains("IAsyncAction::class.java"))
    }

    @Test
    fun generator_emits_metadata_for_empty_exclusive_interfaces_with_native_projection() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            isExclusiveTo = true,
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single().contents

        assertTrue(contents, contents.contains("private class NativeProjection("))
        assertTrue(contents, contents.contains("public companion object Metadata"))
        assertTrue(contents, contents.contains("TYPE_HANDLE: WinRtTypeHandle"))
        assertTrue(contents, contents.contains("fun wrap(instance: IUnknownReference): IWidgetFactory = NativeProjection(instance)"))
        assertFalse(contents, contents.contains("Unresolved"))
    }

    @Test
    fun generator_keeps_empty_interface_native_projection_in_generated_source_when_support_files_are_enabled() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            isExclusiveTo = true,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IWidgetFactory.kt").contents
        val manifest = filesByName.getValue("compiler-support.tsv").contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("fun wrap(instance: IUnknownReference): IWidgetFactory = NativeProjection(instance)"))
        assertFalse(manifest, manifest.contains("interface-native"))
    }

    @Test
    fun generator_keeps_supported_method_property_interface_native_projection_in_generated_source() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Add",
                                    returnTypeName = "Int",
                                    parameters = listOf(
                                        WinRtParameterDefinition("left", "Int"),
                                        WinRtParameterDefinition("right", "Int"),
                                    ),
                                    methodRowId = 10,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    setterMethodName = "put_Title",
                                    getterMethodRowId = 11,
                                    setterMethodRowId = 12,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IWidget.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("override fun add(left: Int, right: Int): Int"))
        assertTrue(interfaceContents, interfaceContents.contains("override var title: String"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.getString("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.setString("))
    }

    @Test
    fun generator_keeps_projected_object_interface_native_projection_members_on_kotlin_fallback() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "IElement",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "Element",
                            kind = WinRtTypeKind.RuntimeClass,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                            defaultInterfaceName = "Sample.Xaml.IElement",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Xaml.IElement", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "IPanel",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555563"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Content",
                                    typeName = "Sample.Xaml.Element",
                                    getterMethodName = "get_Content",
                                    setterMethodName = "put_Content",
                                    getterMethodRowId = 10,
                                    setterMethodRowId = 11,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IPanel.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.getNullableProjectedRuntimeClass("))
        assertTrue(interfaceContents, interfaceContents.contains("winRtProjectionMarshaler(value, \"Sample.Xaml.Element?\""))
    }

    @Test
    fun generator_keeps_floating_point_interface_native_projection_on_ir_lowered_kotlin_fallback() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IRange",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Minimum",
                                    typeName = "Double",
                                    getterMethodName = "get_Minimum",
                                    setterMethodName = "put_Minimum",
                                    getterMethodRowId = 10,
                                    setterMethodRowId = 11,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IRange.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.getDouble"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.setDouble"))
        assertFalse(interfaceContents, interfaceContents.contains("wrapGeneratedInterfaceProjection(TYPE_HANDLE, instance) as IRange"))
    }

    @Test
    fun generator_routes_float_string_unit_interface_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IKeyFrameAnimation",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "InsertExpressionKeyFrame",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("normalizedProgressKey", "Float"),
                                        WinRtParameterDefinition("value", "String"),
                                    ),
                                    methodRowId = 13,
                                ),
                                WinRtMethodDefinition(
                                    name = "InsertExpressionKeyFrame",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("normalizedProgressKey", "Float"),
                                        WinRtParameterDefinition("value", "String"),
                                        WinRtParameterDefinition("easingFunction", "Sample.Foundation.CompositionEasingFunction"),
                                    ),
                                    methodRowId = 14,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICompositionEasingFunction",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "CompositionEasingFunction",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ICompositionEasingFunction",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ICompositionEasingFunction", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val interfaceContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IKeyFrameAnimation.kt")
            .contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(interfaceContents, interfaceContents.contains("\"Float,String\","))
        assertTrue(interfaceContents, interfaceContents.contains("\"Float,String,RawAddress\","))
        assertFalse(interfaceContents, interfaceContents.contains("callUnitWith"))
        assertTrue(interfaceContents, interfaceContents.contains("normalizedProgressKey,"))
        assertTrue(interfaceContents, interfaceContents.contains("value,"))
        assertTrue(interfaceContents, interfaceContents.contains("winRtProjectionMarshaler(easingFunction, \"Sample.Foundation.CompositionEasingFunction\""))
        assertTrue(interfaceContents, interfaceContents.contains("Guid(\"11111111-2222-3333-4444-555555555562\")).use {"))
        assertTrue(interfaceContents, interfaceContents.contains("__easingFunctionProjectionMarshaler ->"))
        assertTrue(interfaceContents, interfaceContents.contains("__easingFunctionProjectionMarshaler.abi,"))
        assertFalse(interfaceContents, interfaceContents.contains("easingFunction as IWinRTObject,"))
        assertFalse(interfaceContents, interfaceContents.contains("HString.createReference(value)"))
        assertFalse(interfaceContents, interfaceContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_string_float_unit_interface_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICompositionPropertySet",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "InsertScalar",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("propertyName", "String"),
                                        WinRtParameterDefinition("value", "Float"),
                                    ),
                                    methodRowId = 15,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val interfaceContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ICompositionPropertySet.kt")
            .contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(interfaceContents, interfaceContents.contains("\"String,Float\","))
        assertFalse(interfaceContents, interfaceContents.contains("callUnitWith"))
        assertTrue(interfaceContents, interfaceContents.contains("propertyName,"))
        assertTrue(interfaceContents, interfaceContents.contains("value,"))
        assertFalse(interfaceContents, interfaceContents.contains("HString.createReference(propertyName)"))
        assertFalse(interfaceContents, interfaceContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_double_double_unit_interface_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IRangeBaseOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "OnValueChanged",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("oldValue", "Double"),
                                        WinRtParameterDefinition("newValue", "Double"),
                                    ),
                                    methodRowId = 17,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val interfaceContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IRangeBaseOverrides.kt")
            .contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(interfaceContents, interfaceContents.contains("\"Double,Double\","))
        assertTrue(interfaceContents, interfaceContents.contains("oldValue,"))
        assertTrue(interfaceContents, interfaceContents.contains("newValue,"))
        assertFalse(interfaceContents, interfaceContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_string_projected_object_unit_interface_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICompositionAnimation",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555560"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "SetReferenceParameter",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("key", "String"),
                                        WinRtParameterDefinition("compositionObject", "Sample.Foundation.CompositionObject"),
                                    ),
                                    methodRowId = 16,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICompositionObject",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555563"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "CompositionObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ICompositionObject",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ICompositionObject", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val interfaceContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ICompositionAnimation.kt")
            .contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(interfaceContents, interfaceContents.contains("\"String,RawAddress\","))
        assertFalse(interfaceContents, interfaceContents.contains("callUnitWith"))
        assertTrue(interfaceContents, interfaceContents.contains("key,"))
        assertTrue(interfaceContents, interfaceContents.contains("winRtProjectionMarshaler(compositionObject, \"Sample.Foundation.CompositionObject\""))
        assertTrue(interfaceContents, interfaceContents.contains("Guid(\"11111111-2222-3333-4444-555555555563\")).use {"))
        assertTrue(interfaceContents, interfaceContents.contains("__compositionObjectProjectionMarshaler ->"))
        assertTrue(interfaceContents, interfaceContents.contains("__compositionObjectProjectionMarshaler.abi,"))
        assertFalse(interfaceContents, interfaceContents.contains("compositionObject as IWinRTObject,"))
        assertFalse(interfaceContents, interfaceContents.contains("HString.createReference(key)"))
        assertFalse(interfaceContents, interfaceContents.contains("ComVtableInvoker.invokeArgs"))
        assertFalse(interfaceContents, interfaceContents.contains("PlatformAbi.fromRawComPtr((compositionObject as IWinRTObject).nativeObject.pointer)"))
    }

    @Test
    fun generator_keeps_unsupported_interface_native_projection_on_kotlin_fallback() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555566"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                    methodRowId = 11,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Owner",
                                    typeName = "Sample.Foundation.Widget",
                                    getterMethodName = "get_Owner",
                                    getterMethodRowId = 10,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IWidget.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("fun wrap(instance: IUnknownReference): IWidget = NativeProjection(instance)"))
        assertTrue(interfaceContents, interfaceContents.contains("override fun reset()"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(interfaceContents, interfaceContents.contains("\"\","))
        assertTrue(interfaceContents, interfaceContents.contains("IWidget.Metadata.RESET_SLOT"))
        assertFalse(interfaceContents, interfaceContents.contains("ComVtableInvoker.invoke(instance = nativeObject.pointer"))
        assertFalse(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.invokeUnit"))
        assertFalse(interfaceContents, interfaceContents.contains("wrapGeneratedInterfaceProjection(TYPE_HANDLE, instance) as IWidget"))
    }

    @Test
    fun generator_keeps_projected_object_and_collection_interface_native_projection_on_kotlin_fallback() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IChild",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Child",
                                    typeName = "Sample.Foundation.IChild",
                                    getterMethodName = "get_Child",
                                    getterMethodRowId = 10,
                                ),
                                WinRtPropertyDefinition(
                                    name = "Items",
                                    typeName = "Windows.Foundation.Collections.IVector<String>",
                                    getterMethodName = "get_Items",
                                    getterMethodRowId = 11,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IWidget.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("val child: IChild?"))
        assertTrue(interfaceContents, interfaceContents.contains("val items: MutableList<String>"))
        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertFalse(interfaceContents, interfaceContents.contains("wrapGeneratedInterfaceProjection(TYPE_HANDLE, instance) as IWidget"))
    }

    @Test
    fun generator_routes_fallback_closable_close_through_descriptor_marker() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IClosable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "Close", returnTypeName = "Unit", methodRowId = 6),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IChild",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.IClosable"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Child",
                                    typeName = "Sample.Foundation.IChild",
                                    getterMethodName = "get_Child",
                                    getterMethodRowId = 10,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val interfaceContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IWidget.kt")
            .contents

        assertTrue(interfaceContents, interfaceContents.contains("public interface IWidget : AutoCloseable"))
        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("override fun close()"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(interfaceContents, interfaceContents.contains("nativeObject,"))
        assertTrue(interfaceContents, interfaceContents.contains("6,"))
        assertTrue(interfaceContents, interfaceContents.contains("\"\","))
        assertFalse(interfaceContents, interfaceContents.contains("ComVtableInvoker.invoke(instance = nativeObject.pointer"))
    }

    @Test
    fun generator_keeps_inherited_interface_native_projection_in_generated_source() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBaseWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                    methodRowId = 10,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    getterMethodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IChildWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBaseWidget"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Activate",
                                    returnTypeName = "Boolean",
                                    methodRowId = 20,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                    getterMethodRowId = 21,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IChildWidget.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("private class NativeProjection("))
        assertTrue(interfaceContents, interfaceContents.contains("override fun reset()"))
        assertTrue(interfaceContents, interfaceContents.contains("override val title: String"))
        assertTrue(interfaceContents, interfaceContents.contains("override fun activate(): Boolean"))
        assertTrue(interfaceContents, interfaceContents.contains("override val count: Int"))
    }

    @Test
    fun generator_routes_native_projection_events_through_event_source_table() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-2222-3333-4444-555555555555"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "T0"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICalculator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Reset",
                                    returnTypeName = "Unit",
                                    methodRowId = 6,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                    addMethodRowId = 7,
                                    removeMethodRowId = 8,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single { it.relativePath.endsWith("ICalculator.kt") }.contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("WinRTEventProjectionHelper_"))
        assertTrue(contents.contains(".createEventSource_"))
        assertTrue(contents.contains("ICalculator.Metadata.CHANGED_ADD_SLOT"))
        assertTrue(contents.contains(".add(handler)"))
        assertTrue(contents.contains(".remove(token)"))
    }

    @Test
    fun generator_merges_required_interface_property_accessors_across_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameReader",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    getterMethodRowId = 1,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameWriter",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    setterMethodName = "put_Name",
                                    setterMethodRowId = 1,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NamedObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameReader",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameReader", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INameWriter"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("NamedObject.kt") }
            .contents

        assertEquals(1, "override var name: String".toRegex().findAll(contents).count())
        assertTrue(contents.contains("INameReader.Metadata.NAME_GETTER_SLOT"))
        assertTrue(contents.contains("INameWriter.Metadata.NAME_SETTER_SLOT"))
    }

    @Test
    fun generator_binds_static_methods_and_properties_through_static_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "parse",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "getOwner",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("element", "Sample.Foundation.Widget")),
                                    methodRowId = 16,
                                ),
                                WinRtMethodDefinition(
                                    name = "isVisible",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("element", "Sample.Foundation.Widget")),
                                    methodRowId = 17,
                                ),
                                WinRtMethodDefinition(
                                    name = "show",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("element", "Sample.Foundation.Widget")),
                                    methodRowId = 18,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                    getterMethodRowId = 11,
                                ),
                                WinRtPropertyDefinition(
                                    name = "StaticToken",
                                    typeName = "Sample.Foundation.DependencyProperty",
                                    getterMethodName = "get_StaticToken",
                                    getterMethodRowId = 15,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                    addMethodRowId = 13,
                                    removeMethodRowId = 14,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics2",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    setterMethodName = "put_Count",
                                    setterMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf(
                                    "Sample.Foundation.IWidgetStatics",
                                    "Sample.Foundation.IWidgetStatics2",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "DependencyProperty",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("private val _iWidgetStatics: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("fun iWidgetStatics(): IUnknownReference"))
        assertTrue(widgetContents.contains("fun parse(`value`: String): Widget"))
        assertTrue(widgetContents.contains("ComVtableInvoker.invokeArgs"))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics().pointer"))
        assertTrue(widgetContents.contains("fun getOwner(element: Widget): Widget"))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop"))
        assertTrue(widgetContents.contains("STATIC_GETOWNER_SLOT"))
        assertTrue(widgetContents.contains("fun isVisible(element: Widget): Boolean"))
        assertTrue(widgetContents.contains("fun show(element: Widget)"))
        assertTrue(widgetContents.contains("val STATIC_PARSE_SLOT: Int = IWidgetStatics.Metadata.PARSE_SLOT"))
        assertTrue(widgetContents.contains("var count: Int"))
        assertTrue(widgetContents.contains("val STATIC_COUNT_GETTER_SLOT: Int = IWidgetStatics.Metadata.COUNT_GETTER_SLOT"))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callInt32("))
        assertTrue(widgetContents.contains("STATIC_COUNT_GETTER_SLOT"))
        assertTrue(widgetContents.contains("val STATIC_COUNT_SETTER_SLOT: Int = IWidgetStatics2.Metadata.COUNT_SETTER_SLOT"))
        assertTrue(widgetContents.contains("slot = STATIC_COUNT_SETTER_SLOT"))
        assertTrue(widgetContents.contains("val staticToken: DependencyProperty"))
        assertTrue(widgetContents.contains("STATIC_STATICTOKEN_GETTER_SLOT"))
        assertTrue(widgetContents.contains("staticToken"))
        assertTrue(widgetContents.contains("val changed: WinRtEvent<EventHandlerCallback<Int>> by"))
        assertTrue(widgetContents.contains("lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("WinRTEventProjectionHelper_"))
        assertTrue(widgetContents.contains(".createEventSource_"))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics()"))
        assertTrue(widgetContents.contains("STATIC_CHANGED_ADD_SLOT"))
    }

    @Test
    fun generator_emits_static_runtime_class_forwarding_members_from_static_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.ApplicationModel.DataTransfer",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "IClipboardStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetContent",
                                    returnTypeName = "Windows.ApplicationModel.DataTransfer.DataPackageView",
                                    methodRowId = 1,
                                ),
                                WinRtMethodDefinition(
                                    name = "SetContent",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            name = "content",
                                            typeName = "Windows.ApplicationModel.DataTransfer.DataPackage",
                                        ),
                                    ),
                                    methodRowId = 2,
                                ),
                                WinRtMethodDefinition(
                                    name = "Clear",
                                    returnTypeName = "Unit",
                                    methodRowId = 3,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "IStandardDataFormatsStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Text",
                                    typeName = "String",
                                    getterMethodName = "get_Text",
                                    getterMethodRowId = 4,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "Clipboard",
                            kind = WinRtTypeKind.RuntimeClass,
                            isStaticType = true,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.ApplicationModel.DataTransfer.IClipboardStatics"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "StandardDataFormats",
                            kind = WinRtTypeKind.RuntimeClass,
                            isStaticType = true,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.ApplicationModel.DataTransfer.IStandardDataFormatsStatics"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "DataPackageView",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.ApplicationModel.DataTransfer",
                            name = "DataPackage",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val clipboard = filesByName.getValue("Clipboard.kt").contents
        assertTrue(clipboard.contains("static WinRT class shell"))
        assertTrue(clipboard.contains("companion object Metadata"))
        assertTrue(clipboard.contains("fun getContent(): DataPackageView"))
        assertTrue(clipboard.contains("fun setContent(content: DataPackage)"))
        assertTrue(clipboard.contains("fun clear()"))
        assertTrue(clipboard.contains("StaticInterfaces.iClipboardStatics()"))
        assertTrue(clipboard.contains("DataPackageView.Metadata.wrap"))

        val standardDataFormats = filesByName.getValue("StandardDataFormats.kt").contents
        assertTrue(standardDataFormats.contains("val text: String"))
        assertTrue(standardDataFormats.contains("StaticInterfaces.iStandardDataFormatsStatics()"))
        assertTrue(standardDataFormats.contains("STATIC_TEXT_GETTER_SLOT"))
    }

    @Test
    fun generator_routes_static_unit_methods_through_projection_intrinsic_when_support_files_are_enabled() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "show",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("element", "Sample.Foundation.Widget"),
                                    ),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "setVisible",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("element", "Sample.Foundation.Widget"),
                                        WinRtParameterDefinition("visible", "Boolean"),
                                    ),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun show(element: Widget)"))
        assertTrue(widgetContents.contains("fun setVisible(element: Widget, visible: Boolean)"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(widgetContents.contains("\"RawAddress\""))
        assertTrue(widgetContents.contains("\"RawAddress,Boolean\""))
        assertTrue(widgetContents.contains("winRtProjectionMarshaler(element, \"Sample.Foundation.Widget\""))
        assertTrue(widgetContents.contains("Guid(\"11111111-1111-1111-1111-111111111111\")).use {"))
        assertTrue(widgetContents.contains("__elementProjectionMarshaler ->"))
        assertTrue(widgetContents.contains("__elementProjectionMarshaler.abi,"))
        assertFalse(widgetContents.contains("element as IWinRTObject"))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callUnit("))
    }

    @Test
    fun generator_routes_static_boolean_methods_through_projection_intrinsic_when_support_files_are_enabled() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "isCustomizationSupported",
                                    returnTypeName = "Boolean",
                                    methodRowId = 9,
                                ),
                                WinRtMethodDefinition(
                                    name = "isVisible",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("element", "Sample.Foundation.Widget"),
                                    ),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "listenerExists",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("status", "Sample.Foundation.Status"),
                                    ),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Status",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Ready", 0u),
                                WinRtEnumMemberDefinition("Busy", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun isVisible(element: Widget): Boolean"))
        assertTrue(widgetContents.contains("fun isCustomizationSupported(): Boolean"))
        assertTrue(widgetContents.contains("fun listenerExists(status: Status): Boolean"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.getBoolean("))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.callBoolean("))
        assertTrue(widgetContents.contains("\"RawAddress\""))
        assertTrue(widgetContents.contains("\"Int32\""))
        assertTrue(widgetContents.contains("winRtProjectionMarshaler(element, \"Sample.Foundation.Widget\""))
        assertTrue(widgetContents.contains("Guid(\"11111111-1111-1111-1111-111111111111\")).use {"))
        assertTrue(widgetContents.contains("__elementProjectionMarshaler ->"))
        assertTrue(widgetContents.contains("__elementProjectionMarshaler.abi,"))
        assertFalse(widgetContents.contains("element as IWinRTObject"))
        assertTrue(widgetContents.contains("status.abiValue"))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callBoolean("))
    }

    @Test
    fun generator_routes_static_descriptor_scalar_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "indexOf",
                                    returnTypeName = "Int",
                                    parameters = listOf(WinRtParameterDefinition("element", "Sample.Foundation.Widget")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "rankOf",
                                    returnTypeName = "UInt",
                                    parameters = listOf(WinRtParameterDefinition("status", "Sample.Foundation.Status")),
                                    methodRowId = 11,
                                ),
                                WinRtMethodDefinition(
                                    name = "scoreOf",
                                    returnTypeName = "Double",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    methodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Status",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Ready", 0u),
                                WinRtEnumMemberDefinition("Busy", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun indexOf(element: Widget): Int"))
        assertTrue(widgetContents.contains("fun rankOf(status: Status): UInt"))
        assertTrue(widgetContents.contains("fun scoreOf(name: String): Double"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.callScalar("))
        assertTrue(widgetContents.contains("\"UInt32\""))
        assertTrue(widgetContents.contains("\"Double\""))
        assertTrue(widgetContents.contains("\"RawAddress\""))
        assertTrue(widgetContents.contains("\"Int32\""))
        assertTrue(widgetContents.contains("\"String\""))
        assertTrue(widgetContents.contains("winRtProjectionMarshaler(element, \"Sample.Foundation.Widget\""))
        assertTrue(widgetContents.contains("Guid(\"11111111-1111-1111-1111-111111111111\")).use {"))
        assertTrue(widgetContents.contains("__elementProjectionMarshaler ->"))
        assertTrue(widgetContents.contains("__elementProjectionMarshaler.abi,"))
        assertFalse(widgetContents.contains("element as IWinRTObject"))
        assertTrue(widgetContents.contains("status.abiValue"))
        assertFalse(widgetContents.contains("WinRtProjectionIntrinsic.callInt32("))
        assertFalse(widgetContents.contains("WinRtProjectionIntrinsic.callUInt32("))
        assertFalse(widgetContents.contains("WinRtProjectionIntrinsic.callDouble("))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callInt32("))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callUInt32("))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callDouble("))
    }

    @Test
    fun generator_routes_instance_descriptor_scalar_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICalculator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "subtract",
                                    returnTypeName = "Double",
                                    parameters = listOf(
                                        WinRtParameterDefinition("left", "Double"),
                                        WinRtParameterDefinition("right", "Double"),
                                    ),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Calculator",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ICalculator",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ICalculator", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val calculatorContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Calculator.kt")
            .contents

        assertTrue(calculatorContents.contains("fun subtract(left: Double, right: Double): Double"))
        assertTrue(calculatorContents.contains("WinRtProjectionIntrinsic.callScalar("))
        assertTrue(calculatorContents.contains("\"Double\""))
        assertTrue(calculatorContents.contains("\"Double,Double\""))
        assertTrue(calculatorContents.contains("left,"))
        assertTrue(calculatorContents.contains("right,"))
        assertFalse(calculatorContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_instance_descriptor_boolean_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IPropertySet",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "hasNamedValue",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("defaultValue", "Boolean"),
                                        WinRtParameterDefinition("threshold", "Float"),
                                    ),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val propertySetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IPropertySet.kt")
            .contents

        assertTrue(propertySetContents.contains("fun hasNamedValue("))
        assertTrue(propertySetContents.contains("name: String"))
        assertTrue(propertySetContents.contains("defaultValue: Boolean"))
        assertTrue(propertySetContents.contains("threshold: Float"))
        assertTrue(propertySetContents.contains("WinRtProjectionIntrinsic.callBoolean("))
        assertTrue(propertySetContents.contains("\"String,Boolean,Float\""))
        assertTrue(propertySetContents.contains("name,"))
        assertTrue(propertySetContents.contains("defaultValue,"))
        assertTrue(propertySetContents.contains("threshold,"))
        assertFalse(propertySetContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_instance_descriptor_enum_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IPropertySet",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "tryGetBoolean",
                                    returnTypeName = "Sample.Foundation.GetValueStatus",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("value", "Boolean"),
                                    ),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "GetValueStatus",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Succeeded", 0u),
                                WinRtEnumMemberDefinition("NotFound", 1u),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val propertySetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IPropertySet.kt")
            .contents

        assertTrue(propertySetContents.contains("fun tryGetBoolean(name: String, `value`: Boolean): GetValueStatus"))
        assertTrue(propertySetContents.contains("private class NativeProjection("))
        assertTrue(propertySetContents.contains("GetValueStatus.Metadata.fromAbi("))
        assertTrue(propertySetContents.contains("WinRtProjectionIntrinsic.callScalar("))
        assertTrue(propertySetContents.contains("\"Int32\""))
        assertTrue(propertySetContents.contains("\"String,Boolean\""))
        assertTrue(propertySetContents.contains("name,"))
        assertTrue(propertySetContents.contains("value,"))
        assertFalse(propertySetContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_instance_descriptor_projected_object_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IEasingStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "createBackEasingFunction",
                                    returnTypeName = "Sample.Foundation.BackEasingFunction",
                                    parameters = listOf(
                                        WinRtParameterDefinition("owner", "Sample.Foundation.Compositor"),
                                        WinRtParameterDefinition("mode", "Sample.Foundation.CompositionEasingFunctionMode"),
                                        WinRtParameterDefinition("amplitude", "Float"),
                                    ),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "CompositionEasingFunctionMode",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("In", 0u),
                                WinRtEnumMemberDefinition("Out", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICompositor",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Compositor",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ICompositor",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ICompositor", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBackEasingFunction",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "BackEasingFunction",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IBackEasingFunction",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Sample.Foundation.IBackEasingFunction",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val easingContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IEasingStatics.kt")
            .contents

        assertTrue(easingContents.contains("fun createBackEasingFunction("))
        assertTrue(easingContents.contains("WinRtProjectionIntrinsic.callProjectedRuntimeClass("))
        assertTrue(easingContents.contains("\"RawAddress,Int32,Float\""))
        assertTrue(easingContents.contains("BackEasingFunction.Metadata::wrap"))
        assertTrue(easingContents.contains("winRtProjectionMarshaler(owner, \"Sample.Foundation.Compositor\""))
        assertTrue(easingContents.contains("Guid(\"33333333-3333-3333-3333-333333333333\")).use {"))
        assertTrue(easingContents.contains("__ownerProjectionMarshaler ->"))
        assertTrue(easingContents.contains("__ownerProjectionMarshaler.abi,"))
        assertFalse(easingContents.contains("owner as IWinRTObject"))
        assertTrue(easingContents.contains("mode.abiValue"))
        assertTrue(easingContents.contains("amplitude,"))
        assertFalse(easingContents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_static_properties_through_projection_intrinsics_when_support_files_are_enabled() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                    setterMethodName = "put_Count",
                                    getterMethodRowId = 11,
                                    setterMethodRowId = 12,
                                ),
                                WinRtPropertyDefinition(
                                    name = "StaticToken",
                                    typeName = "Sample.Foundation.DependencyProperty",
                                    getterMethodName = "get_StaticToken",
                                    getterMethodRowId = 13,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "DependencyProperty",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("var count: Int"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.getInt32("))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.setInt32("))
        assertTrue(widgetContents, widgetContents.contains("DependencyProperty.Metadata::wrap"))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callInt32("))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callUnit("))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.getProjectedRuntimeClass("))
    }

    @Test
    fun generator_routes_static_string_projected_object_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "parse",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun parse(`value`: String): Widget"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.staticCallProjectedRuntimeClassWithString("))
        assertTrue(widgetContents.contains("Widget.Metadata::wrap"))
        assertFalse(widgetContents.contains("HString.createReference(`value`)"))
        assertFalse(widgetContents.contains("STATIC_PARSE_SLOT, arg0"))
    }

    @Test
    fun generator_routes_static_descriptor_projected_object_methods_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "copy",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("source", "Sample.Foundation.Widget")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "fromPointerId",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("pointerId", "UInt")),
                                    methodRowId = 11,
                                ),
                                WinRtMethodDefinition(
                                    name = "fromNameAndSource",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("source", "Sample.Foundation.Widget"),
                                    ),
                                    methodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun copy(source: Widget): Widget"))
        assertTrue(widgetContents.contains("fun fromPointerId(pointerId: UInt): Widget"))
        assertTrue(widgetContents.contains("fun fromNameAndSource(name: String, source: Widget): Widget"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.callProjectedRuntimeClass("))
        assertTrue(widgetContents.contains("\"RawAddress\""))
        assertTrue(widgetContents.contains("\"UInt32\""))
        assertTrue(widgetContents.contains("\"String,RawAddress\""))
        assertTrue(widgetContents.contains("Widget.Metadata::wrap"))
        assertTrue(widgetContents.contains("winRtProjectionMarshaler(source, \"Sample.Foundation.Widget\""))
        assertTrue(widgetContents.contains("Guid(\"11111111-1111-1111-1111-111111111111\")).use {"))
        assertTrue(widgetContents.contains("__sourceProjectionMarshaler ->"))
        assertTrue(widgetContents.contains("__sourceProjectionMarshaler.abi,"))
        assertFalse(widgetContents.contains("source as IWinRTObject"))
        assertFalse(widgetContents.contains("WinRtStaticProjectionInterop.callProjectedRuntimeClass("))
    }

    @Test
    fun generator_routes_activation_factory_creates_through_projected_object_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetKind",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Default", 0u),
                                WinRtEnumMemberDefinition("Advanced", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateWithId",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("kind", "Sample.Foundation.WidgetKind"),
                                        WinRtParameterDefinition("width", "Int"),
                                        WinRtParameterDefinition("id", "UInt"),
                                    ),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("internal fun createWithId("))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.callProjectedInterface("))
        assertTrue(widgetContents.contains("acquire(),"))
        assertTrue(widgetContents.contains("\"Int32,Int32,UInt32\""))
        assertTrue(widgetContents.contains("{ __result -> __result.use { it.asInspectable() } },"))
        assertTrue(widgetContents.contains("kind.abiValue"))
        assertFalse(widgetContents.contains("ComVtableInvoker.invokeGenericArgs(instance = acquire().pointer"))
    }

    @Test
    fun generator_routes_static_array_returns_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "findAll",
                                    returnTypeName = "Array<Sample.Foundation.Widget>",
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "findAllForOwner",
                                    returnTypeName = "Array<Sample.Foundation.Widget>",
                                    parameters = listOf(WinRtParameterDefinition("owner", "Sample.Foundation.Widget")),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("fun findAll(): Array<Widget>"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.staticGetArray("))
        assertTrue(widgetContents.contains("fun findAllForOwner(owner: Widget): Array<Widget>"))
        assertTrue(widgetContents.contains("WinRtProjectionIntrinsic.staticGetArrayWithProjectedObject("))
        assertTrue(widgetContents.contains("owner as IWinRTObject"))
        assertTrue(widgetContents.contains("Marshaler.inspectable(Widget::class)"))
        assertFalse(widgetContents.contains("__resultLengthOut"))
        assertFalse(widgetContents.contains("STATIC_FINDALLFOROWNER_SLOT, arg0"))
    }

    @Test
    fun generator_binds_projected_runtime_class_parameters_via_projection_marshaler() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetValue",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetValue",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetValue", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "setNamedValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue"),
                                    ),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue")),
                                ),
                                WinRtMethodDefinition(
                                    name = "setNamedValue",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("value", "Sample.Foundation.WidgetValue"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val generated = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = generated.getValue("Widget.kt").contents
        val widgetInterfaceContents = generated.getValue("IWidget.kt").contents

        assertTrue(widgetContents.contains("IWidget"))
        assertTrue(widgetContents.contains("IWinRTObject"))
        assertTrue(widgetContents.contains("override val nativeObject: ComObjectReference"))
        assertTrue(widgetContents.contains("fun setValue(`value`: WidgetValue)"))
        assertFalse(widgetContents, widgetContents.contains("PlatformAbi.fromRawComPtr((value as IWinRTObject).nativeObject.pointer)"))
        assertTrue(widgetContents.contains("fun setNamedValue(name: String, `value`: WidgetValue)"))
        assertTrue(widgetInterfaceContents.contains("HString.createReference(name).use { __nameAbi ->"))
        assertTrue(widgetInterfaceContents, widgetInterfaceContents.contains("winRtProjectionMarshaler(value, \"Sample.Foundation.WidgetValue\""))
        assertTrue(widgetInterfaceContents, widgetInterfaceContents.contains("Guid(\"22222222-2222-2222-2222-222222222222\")).use {"))
        assertTrue(widgetInterfaceContents, widgetInterfaceContents.contains("__valueProjectionMarshaler ->"))
        assertTrue(widgetInterfaceContents, widgetInterfaceContents.contains("__valueProjectionMarshaler.abi"))
        assertFalse(widgetInterfaceContents, widgetInterfaceContents.contains("PlatformAbi.fromRawComPtr((value as IWinRTObject).nativeObject.pointer)"))
    }

    @Test
    fun generator_emits_runtime_class_pointer_identity_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "ToString",
                                    returnTypeName = "String",
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "Equals",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("obj", "System.Object")),
                                    isObjectEquals = true,
                                    methodRowId = 11,
                                ),
                                WinRtMethodDefinition(
                                    name = "GetHashCode",
                                    returnTypeName = "Int",
                                    isObjectGetHashCode = true,
                                    methodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true)),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "CustomIdentityWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true)),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "ToString",
                                    returnTypeName = "String",
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "Equals",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("obj", "System.Object")),
                                    isObjectEquals = true,
                                    methodRowId = 11,
                                ),
                                WinRtMethodDefinition(
                                    name = "GetHashCode",
                                    returnTypeName = "Int",
                                    isObjectGetHashCode = true,
                                    methodRowId = 12,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val generated = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = generated.getValue("Widget.kt").contents
        val customIdentityContents = generated.getValue("CustomIdentityWidget.kt").contents

        assertTrue(widgetContents.contains("override fun equals(other: Any?): Boolean"))
        assertTrue(widgetContents.contains("if (other !is Widget) return false"))
        assertTrue(widgetContents.contains("nativeObject.pointer =="))
        assertTrue(widgetContents.contains("other.nativeObject.pointer"))
        assertTrue(widgetContents.contains("override fun hashCode(): Int"))
        assertTrue(widgetContents.contains("nativeObject.pointer.hashCode()"))
        assertTrue(customIdentityContents.contains("override fun toString(): String"))
        assertTrue(customIdentityContents.contains("override fun equals(other: Any?): Boolean"))
        assertTrue(customIdentityContents, customIdentityContents.contains("return false"))
        assertTrue(customIdentityContents.contains("override fun hashCode(): Int"))
        assertFalse(customIdentityContents.contains("fun ToString()"))
        assertFalse(customIdentityContents.contains("fun Equals("))
        assertFalse(customIdentityContents.contains("fun GetHashCode("))
    }

    @Test
    fun generator_emits_runtime_class_base_inheritance() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetBase",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetBase",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase", isDefault = true)),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Foundation.WidgetBase",
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true)),
                            isSealedType = true,
                        ),
                    ),
                ),
            ),
        )

        val generated = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val baseContents = generated.getValue("WidgetBase.kt").contents
        val widgetContents = generated.getValue("Widget.kt").contents

        assertTrue(baseContents.contains("public open class WidgetBase internal constructor("))
        assertTrue(baseContents.contains("open override val nativeObject: ComObjectReference"))
        assertTrue(widgetContents.contains("public class Widget internal constructor("))
        assertTrue(widgetContents.contains(") : WidgetBase(_inner, kotlin.Unit),"))
        assertTrue(widgetContents.contains("IWidget"))
        assertTrue(widgetContents.contains("override val nativeObject: ComObjectReference"))
    }

    @Test
    fun generator_emits_protected_overridable_runtime_class_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "Refresh", returnTypeName = "Unit", methodRowId = 10),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Title", "String", getterMethodName = "get_Title"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Sample.Foundation.IWidget",
                                    isDefault = true,
                                    isOverridable = true,
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "Refresh", returnTypeName = "Unit", methodRowId = 10),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Title", "String", getterMethodName = "get_Title"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val generated = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = generated.getValue("Widget.kt").contents

        assertFalse(widgetContents.contains("IWidget,\n"))
        assertTrue(widgetContents.contains("protected open fun refresh()"))
        assertFalse(widgetContents.contains("override fun refresh()"))
        assertTrue(widgetContents.contains("protected open val title: String"))
        assertFalse(widgetContents.contains("override val title: String"))
    }

    @Test
    fun generator_emits_protected_runtime_class_property_setters_for_protected_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IInputCursor",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "InputCursor",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IInputCursor",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Sample.Foundation.IInputCursor",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IElement",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IElementProtected",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            isProjectionInternal = true,
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    "ProtectedCursor",
                                    "Sample.Foundation.InputCursor",
                                    getterMethodName = "get_ProtectedCursor",
                                    setterMethodName = "put_ProtectedCursor",
                                    getterMethodRowId = 20,
                                    setterMethodRowId = 21,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Element",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IElement",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Sample.Foundation.IElement",
                                    isDefault = true,
                                ),
                                WinRtInterfaceImplementationDefinition(
                                    "Sample.Foundation.IElementProtected",
                                    isProtected = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    "ProtectedCursor",
                                    "Sample.Foundation.InputCursor",
                                    getterMethodName = "get_ProtectedCursor",
                                    setterMethodName = "put_ProtectedCursor",
                                    getterMethodRowId = 20,
                                    setterMethodRowId = 21,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val generated = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val elementContents = generated.getValue("Element.kt").contents
        val protectedInterfaceContents = generated.getValue("IElementProtected.kt").contents

        assertTrue(protectedInterfaceContents.contains("internal interface IElementProtected"))
        assertTrue(protectedInterfaceContents.contains("public var protectedCursor: InputCursor"))
        assertTrue(elementContents.contains("protected var protectedCursor: InputCursor"))
        assertTrue(elementContents.contains("get() = _iElementProtectedProjection.protectedCursor"))
        assertTrue(elementContents.contains("_iElementProtectedProjection.protectedCursor = value"))
        assertFalse(elementContents.contains("override var protectedCursor"))
    }

    @Test
    fun generator_leaves_winui_application_resource_initialization_to_xaml_pipeline() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplication",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Microsoft.UI.Xaml.Application",
                                    parameters = listOf(
                                        WinRtParameterDefinition("baseInterface", "System.Object"),
                                        WinRtParameterDefinition("innerInterface", "System.Object"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IApplicationStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-4444-4444-4444-444444444444"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Start",
                                    returnTypeName = "Unit",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Application",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.IApplication",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Microsoft.UI.Xaml.IApplication",
                                    isDefault = true,
                                ),
                                WinRtInterfaceImplementationDefinition(
                                    "Microsoft.UI.Xaml.IApplicationOverrides",
                                    isOverridable = true,
                                ),
                            ),
                            activation = WinRtActivationShape(
                                composableFactoryInterfaceName = "Microsoft.UI.Xaml.IApplicationFactory",
                                staticInterfaceNames = listOf("Microsoft.UI.Xaml.IApplicationStatics"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val application = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.substringAfterLast('/') == "Application.kt" }
            .contents

        assertFalse(application, application.contains("WinRtWinUiResourceManagerBootstrap"))
        assertFalse(application, application.contains("_winUiResourceManagerRegistration"))
        assertTrue(application, application.contains("internal constructor(_inner: IInspectableReference, __winrtWrapper: Unit)"))
        assertTrue(application, application.contains("public constructor()"))
        val constructor = application.substringAfter("public constructor()").substringBefore("override fun equals")
        assertTrue(constructor, constructor.contains("WinRtAuthoringSupportIntrinsic.ensureInitialized()"))
        val start = application.substringAfter("public fun start()").substringBefore("}")
        assertFalse(start, start.contains("WinRtAuthoringSupportIntrinsic.ensureInitialized()"))
    }

    @Test
    fun generator_skips_special_name_accessors_as_ordinary_methods() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetChangedHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(WinRtMethodDefinition("Invoke", "Unit")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition("get_Name", "String", isSpecialName = true, methodRowId = 6),
                                WinRtMethodDefinition("put_Name", "Unit", parameters = listOf(WinRtParameterDefinition("value", "String")), isSpecialName = true, methodRowId = 7),
                                WinRtMethodDefinition("add_Changed", "Windows.Foundation.EventRegistrationToken", parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetChangedHandler")), isSpecialName = true, methodRowId = 8),
                                WinRtMethodDefinition("remove_Changed", "Unit", parameters = listOf(WinRtParameterDefinition("token", "Windows.Foundation.EventRegistrationToken")), isSpecialName = true, methodRowId = 9),
                                WinRtMethodDefinition("Refresh", "Unit", methodRowId = 10),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Name", "String", getterMethodName = "get_Name", setterMethodName = "put_Name", getterMethodRowId = 6, setterMethodRowId = 7),
                            ),
                            events = listOf(
                                WinRtEventDefinition("Changed", "Sample.Foundation.WidgetChangedHandler", addMethodName = "add_Changed", removeMethodName = "remove_Changed", addMethodRowId = 8, removeMethodRowId = 9),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true)),
                            methods = listOf(
                                WinRtMethodDefinition("get_Name", "String", isSpecialName = true, methodRowId = 6),
                                WinRtMethodDefinition("put_Name", "Unit", parameters = listOf(WinRtParameterDefinition("value", "String")), isSpecialName = true, methodRowId = 7),
                                WinRtMethodDefinition("add_Changed", "Windows.Foundation.EventRegistrationToken", parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetChangedHandler")), isSpecialName = true, methodRowId = 8),
                                WinRtMethodDefinition("remove_Changed", "Unit", parameters = listOf(WinRtParameterDefinition("token", "Windows.Foundation.EventRegistrationToken")), isSpecialName = true, methodRowId = 9),
                                WinRtMethodDefinition("Refresh", "Unit", methodRowId = 10),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Name", "String", getterMethodName = "get_Name", setterMethodName = "put_Name", getterMethodRowId = 6, setterMethodRowId = 7),
                            ),
                            events = listOf(
                                WinRtEventDefinition("Changed", "Sample.Foundation.WidgetChangedHandler", addMethodName = "add_Changed", removeMethodName = "remove_Changed", addMethodRowId = 8, removeMethodRowId = 9),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val generated = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = generated.getValue("IWidget.kt").contents
        val widgetContents = generated.getValue("Widget.kt").contents

        assertTrue(interfaceContents.contains("fun refresh()"))
        assertTrue(widgetContents.contains("override fun refresh()"))
        assertTrue(interfaceContents.contains("var name: String"))
        assertTrue(widgetContents.contains("override var name: String"))
        assertTrue(interfaceContents.contains("val changed: WinRtEvent<WidgetChangedHandler>"))
        assertTrue(widgetContents.contains("override val changed: WinRtEvent<WidgetChangedHandler>"))
        listOf("get_Name", "put_Name", "add_Changed", "remove_Changed").forEach { accessorName ->
            assertFalse(interfaceContents.contains("fun $accessorName"))
            assertFalse(widgetContents.contains("fun $accessorName"))
        }
        assertTrue(interfaceContents.contains("const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(interfaceContents.contains("const val NAME_SETTER_SLOT: Int = 7"))
        assertTrue(interfaceContents.contains("const val CHANGED_ADD_SLOT: Int = 8"))
        assertTrue(interfaceContents.contains("const val CHANGED_REMOVE_SLOT: Int = 9"))
        assertTrue(interfaceContents.contains("const val REFRESH_SLOT: Int = 10"))
    }

    @Test
    fun generator_binds_struct_and_non_unit_delegate_member_marshaling() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "location", returnTypeName = "Sample.Foundation.Point", methodRowId = 10),
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "location", returnTypeName = "Sample.Foundation.Point"),
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IWidget.kt")
            .contents

        assertTrue(widgetContents.contains("fun location(): Point"))
        assertTrue(widgetContents.contains("Point.Metadata.fromAbi"))
        assertTrue(widgetContents.contains("fun setHandler(handler: WidgetHandler)"))
        assertTrue(widgetContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(widgetContents.contains("WinRtDelegateValueKind.BOOLEAN"))
        assertTrue(widgetContents.contains("handler(__args[0] as String)"))
        assertFalse(widgetContents.contains("fun location(): Point = error(\"WinRT ABI binding is unavailable\")"))
        assertFalse(widgetContents.contains("fun setHandler(handler: WidgetHandler) = error(\"WinRT ABI binding is unavailable\")"))
    }

    @Test
    fun generator_binds_guid_and_struct_delegate_abi_shapes() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Single"),
                                WinRtFieldDefinition("Y", "Single"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "TransformHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Sample.Foundation.Point",
                                    parameters = listOf(
                                        WinRtParameterDefinition("id", "System.Guid"),
                                        WinRtParameterDefinition("point", "Sample.Foundation.Point"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ITransformer",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setTransform",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.TransformHandler")),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Transformer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ITransformer",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ITransformer", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setTransform",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.TransformHandler")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val delegateContents = filesByName.getValue("TransformHandler.kt").contents
        val transformerContents = filesByName.getValue("ITransformer.kt").contents

        assertTrue(delegateContents.contains("WinRtDelegateValueKind.GUID"))
        assertTrue(delegateContents.contains("WinRtDelegateValueKind.STRUCT"))
        assertTrue(delegateContents.contains("parameterStructAdapters = listOf(null, Point.Metadata)"))
        assertTrue(delegateContents.contains("returnStructAdapter = Point.Metadata"))
        assertTrue(delegateContents.contains("operator fun invoke(id: Guid, point: Point): Point"))
        assertTrue(delegateContents.contains("__native.invoke(listOf(id, point)) as Point"))
        assertTrue(delegateContents.contains("return __native.invoke(listOf(id, point)) as Point"))
        assertTrue(transformerContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(transformerContents.contains("parameterStructAdapters = listOf(null,"))
        assertTrue(transformerContents.contains("Point.Metadata), returnStructAdapter = Point.Metadata"))
        assertTrue(transformerContents.contains("handler(__args[0] as Guid, __args[1] as Point)"))
        assertFalse(transformerContents.contains("fun setTransform(handler: TransformHandler) = error(\"WinRT ABI binding is unavailable\")"))
    }

    @Test
    fun generator_binds_generated_windows_foundation_struct_getters_and_setters() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Single"),
                                WinRtFieldDefinition("Y", "Single"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Graphics",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Graphics",
                            name = "IAdvancedColorInfo",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "RedPrimary",
                                    typeName = "Windows.Foundation.Point",
                                    getterMethodName = "get_RedPrimary",
                                    getterMethodRowId = 10,
                                    setterMethodName = "put_RedPrimary",
                                    setterMethodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Graphics",
                            name = "AdvancedColorInfo",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Graphics.IAdvancedColorInfo",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    "Sample.Graphics.IAdvancedColorInfo",
                                    isDefault = true,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "RedPrimary",
                                    typeName = "Windows.Foundation.Point",
                                    getterMethodName = "get_RedPrimary",
                                    setterMethodName = "put_RedPrimary",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val contents = filesByName.getValue("AdvancedColorInfo.kt").contents
        val pointContents = filesByName.getValue("Point.kt").contents

        assertTrue(contents, contents.contains("override var redPrimary: Point"))
        if (contents.contains("_iAdvancedColorInfoProjection")) {
            assertTrue(contents, contents.contains("_iAdvancedColorInfoProjection.redPrimary"))
        } else {
            assertTrue(contents, contents.contains("PlatformAbi.allocateBytes(__scope, Point.Metadata.layout.sizeBytes)"))
            assertTrue(contents, contents.contains("val __result = Point.Metadata.fromAbi(__resultOut)"))
            assertTrue(contents, contents.contains("Point.Metadata.disposeAbi(__resultOut)"))
            assertTrue(contents, contents.contains("Point.Metadata.copyTo(value, __valueAbi)"))
            assertTrue(contents, contents.contains("Point.Metadata.disposeAbi(__valueAbi)"))
        }
        assertTrue(pointContents, pointContents.contains("Metadata.register()"))
        assertTrue(pointContents, pointContents.contains("WinRtValueBoxingRegistration.registerStruct("))
        assertTrue(pointContents, pointContents.contains("Point::class"))
        assertTrue(pointContents, pointContents.contains("\"struct(Windows.Foundation.Point;f4;f4)\""))
        assertTrue(pointContents, pointContents.contains("emptyArray<Point>()::class"))
    }

    @Test
    fun generator_binds_non_blittable_struct_abi_helpers() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NamedValue",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("Id", "Int32"),
                                WinRtFieldDefinition("Name", "String"),
                                WinRtFieldDefinition("Value", "System.Object"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IRecordSource",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "current",
                                    returnTypeName = "Sample.Foundation.NamedValue",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCurrent",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "Sample.Foundation.NamedValue")),
                                    methodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "RecordSource",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IRecordSource",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IRecordSource", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "current",
                                    returnTypeName = "Sample.Foundation.NamedValue",
                                ),
                                WinRtMethodDefinition(
                                    name = "setCurrent",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "Sample.Foundation.NamedValue")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val namedValue = filesByName.getValue("NamedValue.kt").contents
        val recordSource = filesByName.getValue("RecordSource.kt").contents

        assertTrue(namedValue, namedValue.contains("NativeScalarFieldSpec(\"name\", NativeStructScalarKind.ADDRESS)"))
        assertTrue(namedValue, namedValue.contains("NativeScalarFieldSpec(\"value\", NativeStructScalarKind.ADDRESS)"))
        assertTrue(namedValue, namedValue.contains("\"struct(Sample.Foundation.NamedValue;i4;string;cinterface(IInspectable))\""))
        assertTrue(namedValue, namedValue.contains("HString.fromHandle("))
        assertTrue(namedValue, namedValue.contains("PlatformAbi.readPointer(layout.slice(source, \"name\"))"))
        assertTrue(namedValue, namedValue.contains("false"))
        assertTrue(namedValue, namedValue.contains(".use { it.toKString() }"))
        assertTrue(namedValue, namedValue.contains("PlatformAbi.writePointer("))
        assertTrue(namedValue, namedValue.contains("layout.slice(destination, \"name\")"))
        assertTrue(namedValue, namedValue.contains("HString.create(value.name).handle"))
        assertTrue(namedValue, namedValue.contains("PlatformAbi.toRawComPtr("))
        assertTrue(namedValue, namedValue.contains("layout.slice(source, \"value\")"))
        assertTrue(namedValue, namedValue.contains("IID.IInspectable"))
        assertTrue(namedValue, namedValue.contains("preventReleaseOnDispose = true"))
        assertTrue(namedValue, namedValue.contains("IInspectableReference(it.getRefPointer(), IID.IInspectable)"))
        assertTrue(namedValue, namedValue.contains("true"))
        assertTrue(namedValue, namedValue.contains(").close()"))
        if (recordSource.contains("_iRecordSourceProjection")) {
            assertTrue(recordSource, recordSource.contains("_iRecordSourceProjection.current()"))
            assertTrue(recordSource, recordSource.contains("_iRecordSourceProjection.setCurrent(value)"))
        } else {
            assertTrue(recordSource, recordSource.contains("PlatformAbi.allocateBytes(__scope, NamedValue.Metadata.layout.sizeBytes)"))
            assertTrue(recordSource, recordSource.contains("NamedValue.Metadata.copyTo(value, __valueAbi)"))
            assertTrue(recordSource, recordSource.contains("NamedValue.Metadata.disposeAbi(__valueAbi)"))
            assertTrue(recordSource, recordSource.contains("val __result = NamedValue.Metadata.fromAbi(__resultOut)"))
            assertTrue(recordSource, recordSource.contains("NamedValue.Metadata.disposeAbi(__resultOut)"))
        }
    }

    @Test
    fun generator_binds_unit_delegate_parameters_for_methods_and_events() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                    methodRowId = 10,
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Updated",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodRowId = 11,
                                    removeMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler")),
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Updated",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodRowId = 11,
                                    removeMethodRowId = 12,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IWidget.kt")
            .contents

        assertTrue(widgetContents.contains("fun setHandler(handler: WidgetHandler)"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(widgetContents.contains("parameterKinds = listOf(WinRtDelegateValueKind.HSTRING)"))
        assertTrue(widgetContents.contains("WinRtDelegateValueKind.UNIT"))
        assertTrue(widgetContents.contains("handler(__args[0] as String)"))
        assertTrue(widgetContents.contains("__handlerHandle.createReference().use { __handlerAbi ->"))
        assertFalse(widgetContents.contains("fun setHandler(handler: WidgetHandler) = error(\"WinRT ABI binding is unavailable\")"))
        assertFalse(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken = error(\"WinRT ABI binding is unavailable\")"))
    }

    @Test
    fun generator_projects_delegate_from_abi_wrappers_and_delegate_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getHandler",
                                    returnTypeName = "Sample.Foundation.WidgetHandler",
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getHandler",
                                    returnTypeName = "Sample.Foundation.WidgetHandler",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Boolean",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents
        val delegateContents = filesByName.getValue("WidgetHandler.kt").contents

        assertTrue(delegateContents.contains("val DESCRIPTOR: WinRtDelegateDescriptor"))
        assertTrue(delegateContents.contains("WinRtDelegateReference.fromAbi(pointer, DESCRIPTOR)"))
        assertTrue(delegateContents.contains("override val nativeObject: ComObjectReference"))
        assertTrue(delegateContents.contains("override fun invoke("))
        assertTrue(delegateContents.contains("): Boolean"))
        assertTrue(delegateContents.contains("__native.invoke(listOf("))
        assertTrue(delegateContents.contains("as Boolean"))
        if (widgetContents.contains("_iWidgetProjection")) {
            assertTrue(widgetContents.contains("_iWidgetProjection.getHandler()"))
        } else {
            assertTrue(widgetContents.contains("val __resultPointer = PlatformAbi.readPointer(__resultOut)"))
            assertTrue(widgetContents.contains("val __result = WidgetHandler.Metadata.fromAbi(__resultPointer)"))
        }
        assertFalse(widgetContents.contains("fun getHandler(): WidgetHandler = error(\"WinRT ABI binding is unavailable\")"))
    }

    @Test
    fun generator_decodes_delegate_runtime_class_callback_parameters_as_inspectable_references() {
        // Mirrors .cswinrt/src/cswinrt/code_writers.h write_abi_delegate -> write_managed_method_call:
        // ABI delegate parameters are marshaled according to their signature before invoking the projected delegate.
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "TappedEventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "Sample.Foundation.TappedRoutedEventArgs"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "TappedRoutedEventArgs",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ITappedRoutedEventArgs",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ITappedRoutedEventArgs", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ITappedRoutedEventArgs",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.substringAfterLast('/') == "TappedEventHandler.kt" }
            .contents

        assertTrue(contents.contains("listOf(WinRtDelegateValueKind.OBJECT, WinRtDelegateValueKind.IINSPECTABLE)"))
        assertTrue(contents.contains("this(__args[0], TappedRoutedEventArgs.Metadata.wrap(__args[1] as IInspectableReference))"))
        assertTrue(contents.contains("TappedRoutedEventArgs.Metadata.wrap(__args[1] as IInspectableReference)"))
        assertFalse(contents.contains("(__args[0] as IUnknownReference).asInspectable()"))
        assertFalse(contents.contains("TappedRoutedEventArgs.Metadata.wrap((__args[1] as IUnknownReference).asInspectable())"))
    }

    @Test
    fun generator_binds_delegate_mapped_collection_and_uint8_array_parameters() {
        // Mirrors .cswinrt/src/cswinrt/code_writers.h delegate ABI marshaling: WinRT collection
        // parameters stay interface pointers, while arrays expand to their ABI length/data pair.
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "TokenizingHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("tokens", "Windows.Foundation.Collections.IIterable<String>"),
                                        WinRtParameterDefinition("payload", "Array<UByte>"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ITokenizer",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.TokenizingHandler")),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Tokenizer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ITokenizer",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.ITokenizer", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.TokenizingHandler")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val delegateContents = filesByName.getValue("TokenizingHandler.kt").contents
        val tokenizerContents = filesByName.getValue("ITokenizer.kt").contents

        assertTrue(delegateContents, delegateContents.contains("operator fun invoke(tokens: Iterable<String>, payload: Array<UByte>)"))
        assertTrue(delegateContents, delegateContents.contains("WinRtDelegateValueKind.IUNKNOWN"))
        assertTrue(delegateContents, delegateContents.contains("WinRtDelegateValueKind.UINT8_ARRAY"))
        assertTrue(delegateContents, delegateContents.contains("WinRtIterableProjection.createMarshaler(tokens"))
        assertTrue(delegateContents, delegateContents.contains("__tokensMarshaler?.abi ?: PlatformAbi.nullPointer"))
        assertTrue(delegateContents, delegateContents.contains("__native.invoke(listOf(__tokensMarshaler?.abi ?: PlatformAbi.nullPointer, payload))"))
        assertTrue(tokenizerContents, tokenizerContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(tokenizerContents, tokenizerContents.contains("WinRtIterableProjection.fromAbi(PlatformAbi.fromRawComPtr(__collectionRef.pointer)"))
        assertTrue(tokenizerContents, tokenizerContents.contains("handler(run {"))
        assertTrue(tokenizerContents, tokenizerContents.contains("__args[1] as Array<UByte>"))
        assertFalse(tokenizerContents, tokenizerContents.contains("fun setHandler(handler: TokenizingHandler) = error(\"WinRT ABI binding is unavailable\")"))
    }

    @Test
    fun generator_binds_delegate_generic_parameter_arguments_and_returns() {
        // Mirrors .cswinrt generic parameter ABI behavior: a bare T parameter is marshaled as
        // inspectable object ABI, not rejected just because it is not wrapped in a collection.
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "GenericHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "T0",
                                    parameters = listOf(WinRtParameterDefinition("value", "T0")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IGenericHandlerSource",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.GenericHandler<String>")),
                                    methodRowId = 10,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "GenericHandlerSource",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IGenericHandlerSource",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IGenericHandlerSource", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("handler", "Sample.Foundation.GenericHandler<String>")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val delegateContents = filesByName.getValue("GenericHandler.kt").contents
        val sourceContents = filesByName.getValue("IGenericHandlerSource.kt").contents

        assertTrue(delegateContents, delegateContents.contains("public fun interface GenericHandler<T0>"))
        assertTrue(delegateContents, delegateContents.contains("public operator fun invoke(`value`: T0): T0"))
        assertTrue(delegateContents, delegateContents.contains("listOf(WinRtDelegateValueKind.OBJECT)"))
        assertTrue(delegateContents, delegateContents.contains("returnKind = WinRtDelegateValueKind.OBJECT"))
        assertTrue(delegateContents, delegateContents.contains("return __native.invoke(listOf(value)) as T0"))
        assertTrue(sourceContents, sourceContents.contains("WinRtDelegateBridge.createDelegate"))
        assertTrue(sourceContents, sourceContents.contains("WinRtDelegateValueKind.HSTRING"))
        assertTrue(sourceContents, sourceContents.contains("returnKind = WinRtDelegateValueKind.HSTRING"))
        assertTrue(sourceContents, sourceContents.contains("handler(__args[0] as String)"))
        assertFalse(sourceContents, sourceContents.contains("fun setHandler(handler: GenericHandler<String>) = error(\"WinRT ABI binding is unavailable\")"))
    }

    @Test
    fun generator_rejects_delegates_without_a_single_invoke_method() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                        ),
                    ),
                ),
            ),
        )

        val error = runCatching { KotlinProjectionGenerator().generate(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("must expose exactly one Invoke method"))
    }

    @Test
    fun generator_emits_interface_abi_slots_after_inherited_interface_segments() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "ping", returnTypeName = "Unit", methodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Title", typeName = "String", getterMethodName = "get_Title", getterMethodRowId = 20),
                            ),
                            events = listOf(
                                WinRtEventDefinition(name = "Updated", delegateTypeName = "Sample.Foundation.WidgetHandler", addMethodRowId = 21, removeMethodRowId = 22),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val baseContents = filesByName.getValue("IWidgetBase.kt").contents
        val widgetContents = filesByName.getValue("IWidget.kt").contents

        assertTrue(baseContents.contains("const val PING_SLOT: Int = 6"))
        assertTrue(widgetContents.contains("const val TITLE_GETTER_SLOT: Int = 7"))
        assertTrue(widgetContents.contains("const val UPDATED_ADD_SLOT: Int = 8"))
        assertTrue(widgetContents.contains("const val UPDATED_REMOVE_SLOT: Int = 9"))
    }

    @Test
    fun renderer_resolves_cross_namespace_projection_supertypes() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidgetView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetBase",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetView",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetBase",
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        val interfaceContents = filesByName.getValue("IWidgetView.kt").contents
        val classContents = filesByName.getValue("WidgetView.kt").contents

        assertTrue(interfaceContents.contains("import sample.foundation.IWidgetBase"))
        assertTrue(interfaceContents.contains("interface IWidgetView : IWidgetBase"))
        assertTrue(classContents.contains("import sample.foundation.IWidgetBase"))
        assertTrue(classContents.contains("class WidgetView internal constructor("))
        assertTrue(classContents.contains(") : IWidgetBase"))
    }

    @Test
    fun renderer_uses_specialized_attribute_shell_builder() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetAttribute",
                            kind = WinRtTypeKind.RuntimeClass,
                            isAttributeType = true,
                            isSealedType = true,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = ".ctor",
                                    returnTypeName = "Unit",
                                    isRuntimeSpecialName = true,
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("version", "UInt32"),
                                        WinRtParameterDefinition("aliasCode", "Char16"),
                                    ),
                                ),
                            ),
                            fields = listOf(
                                WinRtFieldDefinition("Category", "String"),
                                WinRtFieldDefinition("Enabled", "Boolean"),
                                WinRtFieldDefinition("Priority", "System.Int32"),
                                WinRtFieldDefinition("Ratio", "System.Single"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("SourceType", "System.Type"),
                                WinRtPropertyDefinition("AliasType", "Type"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()

        assertTrue(file.contents.contains("public annotation class WidgetAttribute"))
        assertTrue(file.contents.contains("val name: String"))
        assertTrue(file.contents.contains("val version: Long"))
        assertTrue(file.contents.contains("val aliasCode: Char"))
        assertTrue(file.contents.contains("val Category: String = \"\""))
        assertTrue(file.contents.contains("val Enabled: Boolean = false"))
        assertTrue(file.contents.contains("val Priority: Long = 0L"))
        assertTrue(file.contents.contains("val Ratio: Float = 0.0f"))
        assertTrue(file.contents.contains("val SourceType: KClass<*> = Any::class"))
        assertTrue(file.contents.contains("val AliasType: KClass<*> = Any::class"))
        assertTrue(file.contents.contains("attribute WinRT class shell"))
    }

    @Test
    fun generator_emits_custom_winrt_attribute_annotations_with_constructor_and_named_values() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetAttribute",
                            kind = WinRtTypeKind.RuntimeClass,
                            isAttributeType = true,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = ".ctor",
                                    returnTypeName = "Unit",
                                    isRuntimeSpecialName = true,
                                    parameters = listOf(
                                        WinRtParameterDefinition("name", "String"),
                                        WinRtParameterDefinition("version", "UInt32"),
                                    ),
                                ),
                            ),
                            fields = listOf(WinRtFieldDefinition("Category", "String")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            customAttributes = listOf(
                                WinRtCustomAttributeDefinition(
                                    typeName = "Sample.Foundation.WidgetAttribute",
                                    fixedArguments = listOf(
                                        WinRtCustomAttributeValue.StringValue("alpha"),
                                        WinRtCustomAttributeValue.IntegralValue(7),
                                    ),
                                    namedArguments = listOf(
                                        WinRtCustomAttributeNamedArgument(
                                            name = "Category",
                                            value = WinRtCustomAttributeValue.StringValue("ui"),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val files = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val attributeContents = files.getValue("WidgetAttribute.kt").contents
        val interfaceContents = files.getValue("IWidget.kt").contents

        assertTrue(attributeContents.contains("public annotation class WidgetAttribute("))
        assertTrue(attributeContents.contains("val name: String"))
        assertTrue(attributeContents.contains("val version: Long"))
        assertTrue(attributeContents.contains("val Category: String = \"\""))
        assertTrue(interfaceContents.contains("@WidgetAttribute("))
        assertTrue(interfaceContents.contains("\"alpha\""))
        assertTrue(interfaceContents.contains("7L"))
        assertTrue(interfaceContents.contains("Category = \"ui\""))
    }

    @Test
    fun planner_treats_split_metadata_winrt_interface_references_as_projected_interfaces() {
        val binding = KotlinProjectionPlanner().classifyAbiTypeBinding(
            typeName = "Windows.Graphics.IGeometrySource2D",
            currentNamespace = "Microsoft.UI.Composition",
            typesByQualifiedName = emptyMap(),
        )

        assertEquals(KotlinProjectionAbiValueKind.ProjectedInterface, binding.kind)
        assertEquals("Windows.Graphics.IGeometrySource2D", binding.resolvedTypeName)
    }

    @Test
    fun planner_and_renderer_classify_void_type_aliases_as_unit_abi() {
        val planner = KotlinProjectionPlanner()
        val renderer = KotlinProjectionRenderer()

        listOf("Unit", "Void", "System.Void").forEach { voidTypeName ->
            val plannedBinding = planner.classifyAbiTypeBinding(
                typeName = voidTypeName,
                currentNamespace = "Sample.Foundation",
                typesByQualifiedName = emptyMap(),
            )
            val renderedBinding = renderer.renderAbiTypeBinding(voidTypeName)

            assertEquals(KotlinProjectionAbiValueKind.Unit, plannedBinding.kind)
            assertEquals(KotlinProjectionAbiValueKind.Unit, renderedBinding.kind)
        }
    }

    @Test
    fun planner_renderer_and_native_struct_helpers_classify_guid_aliases_as_guid_abi() {
        val planner = KotlinProjectionPlanner()
        val renderer = KotlinProjectionRenderer()

        listOf("Guid", "System.Guid").forEach { guidTypeName ->
            val plannedBinding = planner.classifyAbiTypeBinding(
                typeName = guidTypeName,
                currentNamespace = "Sample.Foundation",
                typesByQualifiedName = emptyMap(),
            )
            val renderedBinding = renderer.renderAbiTypeBinding(guidTypeName)

            assertEquals(KotlinProjectionAbiValueKind.GuidValue, plannedBinding.kind)
            assertEquals(KotlinProjectionAbiValueKind.GuidValue, renderedBinding.kind)
            assertEquals(GUID_CLASS_NAME, renderer.resolveTypeName(guidTypeName))
            assertEquals("GUID", renderer.nativeStructScalarKind(guidTypeName))
        }
    }

    @Test
    fun planner_renderer_and_type_resolver_use_metadata_fundamental_aliases() {
        val planner = KotlinProjectionPlanner()
        val renderer = KotlinProjectionRenderer()

        listOf(
            Triple("System.Int32", KotlinProjectionAbiValueKind.Int32, Int::class.asClassName()),
            Triple("Int8", KotlinProjectionAbiValueKind.Int8, Byte::class.asClassName()),
            Triple("Single", KotlinProjectionAbiValueKind.Float, Float::class.asClassName()),
            Triple("System.Byte", KotlinProjectionAbiValueKind.UInt8, KOTLIN_UBYTE_CLASS_NAME),
        ).forEach { (typeName, expectedKind, expectedTypeName) ->
            val plannedBinding = planner.classifyAbiTypeBinding(
                typeName = typeName,
                currentNamespace = "Sample.Foundation",
                typesByQualifiedName = emptyMap(),
            )
            val renderedBinding = renderer.renderAbiTypeBinding(typeName)

            assertEquals(expectedKind, plannedBinding.kind)
            assertEquals(expectedKind, renderedBinding.kind)
            assertEquals(expectedTypeName, renderer.resolveTypeName(typeName))
        }
    }

    @Test
    fun native_struct_helpers_use_metadata_fundamental_aliases() {
        val renderer = KotlinProjectionRenderer()

        data class NativeStructAliasCase(
            val typeName: String,
            val scalarKind: String?,
            val readSnippet: String,
            val writeSnippet: String,
        )

        listOf(
            NativeStructAliasCase("System.Int32", "INT32", "readInt32(layout.slice(source, \"value\"))", "writeInt32(layout.slice(destination, \"value\"), value.value)"),
            NativeStructAliasCase("System.Byte", "INT8", "readInt8(layout.slice(source, \"value\")).toUByte()", "writeInt8(layout.slice(destination, \"value\"), value.value.toByte())"),
            NativeStructAliasCase("Char16", "CHAR16", "readChar16(layout.slice(source, \"value\"))", "writeChar16(layout.slice(destination, \"value\"), value.value)"),
            NativeStructAliasCase("System.Single", "FLOAT32", "readFloat(layout.slice(source, \"value\"))", "writeFloat(layout.slice(destination, \"value\"), value.value)"),
            NativeStructAliasCase("System.String", null, "fromHandle", "HString.create(value.value).handle"),
        ).forEach { (typeName, scalarKind, readSnippet, writeSnippet) ->
            val field = WinRtFieldDefinition("Value", typeName)
            val fieldSpec = renderer.nativeStructFieldSpec(field, "Sample.Foundation", emptyMap()).toString()
            val readCode = renderer.nativeStructFieldReadCode(field, "source", "Sample.Foundation", emptyMap()).toString()
            val writeCode = renderer.nativeStructFieldWriteCode(field, "value", "destination", "Sample.Foundation", emptyMap()).toString()

            assertEquals(scalarKind, renderer.nativeStructScalarKind(typeName))
            if (scalarKind == null) {
                assertTrue(fieldSpec, fieldSpec.contains("NativeStructScalarKind.ADDRESS"))
            } else {
                assertTrue(fieldSpec, fieldSpec.contains("NativeStructScalarKind.$scalarKind"))
            }
            assertTrue(readCode, readCode.contains(readSnippet))
            assertTrue(writeCode, writeCode.contains(writeSnippet))
        }

        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "FundamentalAliases",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("Id", "System.Int32"),
                                WinRtFieldDefinition("Flags", "System.Byte"),
                                WinRtFieldDefinition("Code", "Char16"),
                                WinRtFieldDefinition("Weight", "System.Single"),
                                WinRtFieldDefinition("Name", "System.String"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .single { it.relativePath.endsWith("FundamentalAliases.kt") }
            .contents

        assertTrue(contents, contents.contains("\"struct(Sample.Foundation.FundamentalAliases;c2;u1;i4;string;f4)\""))
        assertTrue(contents, contents.contains("val flags: UByte"))
        assertTrue(contents, contents.contains("NativeScalarFieldSpec(\"name\","))
        assertTrue(contents, contents.contains("NativeStructScalarKind.ADDRESS)"))
        assertTrue(contents, contents.contains("HString.fromHandle("))
        assertTrue(contents, contents.contains("HString.create(value.name).handle"))
        assertTrue(contents, contents.containsIgnoringWhitespace("HString.fromHandle(PlatformAbi.readPointer(layout.slice(source, \"name\")), owner = true).close()"))
        assertFalse(contents, contents.containsIgnoringWhitespace("IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(layout.slice(source, \"name\")))).close()"))
    }

    @Test
    fun generator_classifies_activation_factory_delegate_parameters_from_projection_model() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ChangedCallback",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    isSpecialName = true,
                                    parameters = listOf(WinRtParameterDefinition("value", "Int")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Create",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(WinRtParameterDefinition("callback", "Sample.Foundation.ChangedCallback")),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.substringAfterLast('/') == "Widget.kt" }
            .contents

        assertTrue(contents.contains("callback: ChangedCallback"))
        assertTrue(contents.contains("WinRtDelegateBridge.createDelegate"))
    }

    @Test
    fun generator_emits_projected_winrt_annotations_from_metadata_attributes() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            availability = WinRtAvailabilityMetadata(
                                contractVersion = WinRtContractVersionMetadata(
                                    contractName = "Windows.Foundation.UniversalApiContract",
                                    version = 0x00030000,
                                    platformVersion = "10.0.14393.0",
                                ),
                            ),
                            customAttributes = listOf(
                                WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ContractVersionAttribute",
                                    fixedArguments = listOf(
                                        WinRtCustomAttributeValue.StringValue("Windows.Foundation.UniversalApiContract"),
                                        WinRtCustomAttributeValue.IntegralValue(0x00030000),
                                    ),
                                ),
                                WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExperimentalAttribute",
                                ),
                                WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.AttributeUsageAttribute",
                                    fixedArguments = listOf(
                                        WinRtCustomAttributeValue.EnumValue("Windows.Foundation.Metadata.AttributeTargets", 0x10),
                                    ),
                                    namedArguments = listOf(
                                        WinRtCustomAttributeNamedArgument(
                                            name = "AllowMultiple",
                                            value = WinRtCustomAttributeValue.BooleanValue(true),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single { it.relativePath.endsWith("IWidget.kt") }.contents

        assertTrue(contents.contains("@WinRtAttributeUsage("))
        assertTrue(contents.contains("targets = 16L"))
        assertTrue(contents.contains("allowMultiple = true"))
        assertTrue(contents.contains("@WinRtSupportedOSPlatform(\"Windows10.0.14393.0\")"))
        assertTrue(contents.contains("@WinRtContractVersion("))
        assertTrue(contents.contains("contract = \"Windows.Foundation.UniversalApiContract\""))
        assertTrue(contents.contains("version = 196_608L"))
        assertTrue(contents.contains("@WinRtExperimental"))
        assertTrue(contents.contains("val PROJECTED_ATTRIBUTES: List<String>"))
        assertTrue(contents.contains("listOf("))
        assertTrue(contents.contains("System.Runtime.Versioning.SupportedOSPlatform"))
        assertTrue(contents.contains("Windows.Foundation.Metadata.Experimental"))
    }

    @Test
    fun generator_propagates_declaring_interface_platform_attributes_to_runtime_members() {
        val interfaceAvailability = WinRtAvailabilityMetadata(
            contractVersion = WinRtContractVersionMetadata(
                contractName = "Windows.Foundation.UniversalApiContract",
                version = 0x000a0000,
                platformVersion = "10.0.22621.0",
            ),
        )
        val interfaceAttributes = listOf(
            WinRtCustomAttributeDefinition(
                typeName = "Windows.Foundation.Metadata.ContractVersionAttribute",
                fixedArguments = listOf(
                    WinRtCustomAttributeValue.StringValue("Windows.Foundation.UniversalApiContract"),
                    WinRtCustomAttributeValue.IntegralValue(0x000a0000),
                ),
            ),
        )
        val getName = WinRtMethodDefinition(name = "getName", returnTypeName = "String")
        val count = WinRtPropertyDefinition(name = "Count", typeName = "Int", getterMethodName = "get_Count")
        val create = WinRtMethodDefinition(name = "createWidget", returnTypeName = "Widget", isStatic = true)
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            availability = interfaceAvailability,
                            customAttributes = interfaceAttributes,
                            methods = listOf(getName),
                            properties = listOf(count),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-3333-4444-5555-666666666666"),
                            availability = interfaceAvailability,
                            customAttributes = interfaceAttributes,
                            methods = listOf(create),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidget",
                                    isDefault = true,
                                ),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                            ),
                            methods = listOf(getName, create),
                            properties = listOf(count),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath == "sample/foundation/Widget.kt" }
            .contents

        assertTrue(contents.contains("@WinRtSupportedOSPlatform(\"Windows10.0.22621.0\")\n  override fun getName(): String"))
        assertTrue(contents.contains("@WinRtSupportedOSPlatform(\"Windows10.0.22621.0\")\n  override val count: Int"))
        assertTrue(contents.contains("@WinRtSupportedOSPlatform(\"Windows10.0.22621.0\")\n    public fun createWidget(): Widget"))
    }

    @Test
    fun generator_suppresses_hresult_throw_for_declaring_interface_noexception_members() {
        val noThrowMethod = WinRtMethodDefinition(
            name = "tryRefresh",
            returnTypeName = "Boolean",
            isNoException = true,
        )
        val noThrowProperty = WinRtPropertyDefinition(
            name = "Status",
            typeName = "Int",
            getterMethodName = "get_Status",
            setterMethodName = "put_Status",
            isNoException = true,
        )
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(noThrowMethod),
                            properties = listOf(noThrowProperty),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("22222222-3333-4444-5555-666666666666"),
                            methods = listOf(WinRtMethodDefinition(name = "Invoke", returnTypeName = "Unit")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidget",
                                    isDefault = true,
                                ),
                            ),
                            methods = listOf(noThrowMethod.copy(isNoException = false)),
                            properties = listOf(noThrowProperty.copy(isNoException = false)),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath == "sample/foundation/Widget.kt" }
            .contents

        assertFalse(contents.memberBody("override fun tryRefresh").contains("requireSuccess()"))
        assertFalse(contents.memberBody("override var status").contains("requireSuccess()"))
        assertTrue(
            contents.memberBody("override fun addChanged").contains("requireSuccess()") ||
                contents.memberBody("override fun addChanged").contains("changed.add(handler)") ||
                contents.memberBody("override fun addChanged").contains("_iWidgetProjection.addChanged(handler)"),
        )
        assertFalse(contents.memberBody("override fun removeChanged").contains("requireSuccess()"))
    }

    @Test
    fun generator_emits_deterministic_shell_files_for_equivalent_metadata() {
        val left = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetView",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                staticInterfaceNames = listOf("Sample.UI.IWidgetViewStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "create", returnTypeName = "WidgetView", isStatic = true),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                    ),
                ),
            ),
        )
        val right = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetView",
                            kind = WinRtTypeKind.RuntimeClass,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Sample.UI.IWidgetViewStatics"),
                                isActivatable = true,
                            ),
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            methods = listOf(
                                WinRtMethodDefinition(name = "create", returnTypeName = "WidgetView", isStatic = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val leftFiles = KotlinProjectionGenerator().generate(left).associateBy(KotlinProjectionFile::relativePath)
        val rightFiles = KotlinProjectionGenerator().generate(right).associateBy(KotlinProjectionFile::relativePath)

        assertEquals(leftFiles.keys, rightFiles.keys)
        assertEquals(leftFiles.mapValues { it.value.contents }, rightFiles.mapValues { it.value.contents })
    }

    @Test
    fun generator_emits_member_surfaces_for_runtime_class_and_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Title", typeName = "String", getterMethodName = "get_Title", setterMethodName = "set_Title"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Updated",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Updated",
                                    removeMethodName = "remove_Updated",
                                    addMethodRowId = 20,
                                    removeMethodRowId = 21,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetExtra",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("cccccccc-bbbb-cccc-dddd-eeeeeeeeeeee"),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "MaxCount", typeName = "Int", getterMethodName = "get_MaxCount", getterMethodRowId = 30),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Reset",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Reset",
                                    removeMethodName = "remove_Reset",
                                    addMethodRowId = 31,
                                    removeMethodRowId = 32,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetExtra"),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                composableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Title", typeName = "String", getterMethodName = "get_Title", setterMethodName = "set_Title"),
                                WinRtPropertyDefinition(name = "MaxCount", typeName = "Int", isStatic = true, getterMethodName = "get_MaxCount"),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Updated",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Updated",
                                    removeMethodName = "remove_Updated",
                                ),
                                WinRtEventDefinition(
                                    name = "Reset",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    isStatic = true,
                                    addMethodName = "add_Reset",
                                    removeMethodName = "remove_Reset",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("value", "String")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents

        assertTrue(widgetContents.contains("class Widget internal constructor("))
        assertTrue(widgetContents.contains(": IWidget"))
        assertTrue(widgetContents.contains("IWidgetExtra"))
        assertTrue(widgetContents.contains("private val _inner: IInspectableReference"))
        assertTrue(widgetContents.contains("private val _iWidgetExtra: IUnknownReference by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("const val TITLE_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("const val TITLE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("val TITLE_GETTER_SLOT: Int = IWidget.Metadata.TITLE_GETTER_SLOT"))
        assertTrue(widgetContents.contains("var title: String"))
        assertTrue(widgetContents.contains("val maxCount: Int"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents, widgetContents.contains("addUpdated(handler)") || widgetContents.contains("WinRtEvent(::addUpdated, ::removeUpdated)"))
        assertTrue(widgetContents.contains("updated.remove(token)") || widgetContents.contains("removeUpdated(token)"))
        assertTrue(widgetContents.contains("fun addReset(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("public val reset: WinRtEvent<WidgetHandler>"))
        assertTrue(widgetContents.contains("public fun removeReset(token: EventRegistrationToken)"))
        assertFalse(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken {\n        return __handlerHandle.createReference().use"))
        assertTrue(widgetContents.contains("public const val FACTORY_INTERFACE: String = \"Sample.Foundation.IWidgetFactory\""))
        assertTrue(widgetContents.contains("Projections.registerDefaultInterfaceType(Widget::class, IWidget::class)"))
        assertTrue(widgetContents.contains("Projections.registerDefaultInterfaceTypeName(TYPE_NAME, DEFAULT_INTERFACE"))
    }

    @Test
    fun generator_registers_generic_default_interface_by_name_and_signature_without_kclass_literal() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBox",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                            genericParameterCount = 1,
                            genericParameters = listOf(WinRtGenericParameterDefinition("T", 0)),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringBox",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IBox<String>",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBox<String>", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .first { it.relativePath.endsWith("StringBox.kt") }
            .contents

        assertTrue(contents.contains("public const val DEFAULT_INTERFACE: String = \"Sample.Foundation.IBox<String>\""))
        assertTrue(contents.contains("Projections.registerDefaultInterfaceTypeName(TYPE_NAME, DEFAULT_INTERFACE"))
        assertTrue(contents.contains("WinRtTypeSignature.parameterizedInterface("))
        assertTrue(contents.contains("IBox.Metadata.IID"))
        assertTrue(contents.contains("WinRtTypeSignature.string()"))
        assertFalse(contents.contains("IBox<String>::class"))
    }

    @Test
    fun generator_emits_runtime_member_binding_descriptors_from_default_and_extra_interfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetBase",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetBase"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetExtra",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit", methodRowId = 20),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetExtra"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(widgetContents.contains("const val NAME_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("const val NAME_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("val NAME_GETTER_SLOT: Int = IWidgetBase.Metadata.NAME_GETTER_SLOT"))
        assertTrue(widgetContents.contains("const val REFRESH_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidgetExtra\""))
        assertTrue(widgetContents.contains("const val REFRESH_SLOT_OWNER_CACHE: String = \"_iWidgetExtra\""))
        assertTrue(widgetContents.contains("val REFRESH_SLOT: Int = IWidgetExtra.Metadata.REFRESH_SLOT"))
    }

    @Test
    fun generator_binds_simple_runtime_getters_and_no_arg_methods() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit", methodRowId = 10),
                                WinRtMethodDefinition(name = "version", returnTypeName = "Int", methodRowId = 11),
                                WinRtMethodDefinition(name = "isReady", returnTypeName = "Boolean", methodRowId = 12),
                                WinRtMethodDefinition(name = "label", returnTypeName = "String", methodRowId = 13),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name", getterMethodRowId = 14),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", getterMethodName = "get_Count", getterMethodRowId = 15),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refresh", returnTypeName = "Unit"),
                                WinRtMethodDefinition(name = "version", returnTypeName = "Int"),
                                WinRtMethodDefinition(name = "isReady", returnTypeName = "Boolean"),
                                WinRtMethodDefinition(name = "label", returnTypeName = "String"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "Name", typeName = "String", getterMethodName = "get_Name"),
                                WinRtPropertyDefinition(name = "Count", typeName = "Int", getterMethodName = "get_Count"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents
        val interfaceContents = filesByName.getValue("IWidget.kt").contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun refresh()"))
        assertTrue(widgetContents.contains("Metadata.REFRESH_SLOT"))
        assertTrue(widgetContents.contains("_iWidgetProjection.refresh()"))
        assertTrue(widgetContents.contains("fun version(): Int"))
        assertTrue(widgetContents.contains("Metadata.VERSION_SLOT"))
        assertTrue(widgetContents.contains("_iWidgetProjection.version()"))
        assertTrue(widgetContents.contains("fun isReady(): Boolean"))
        assertTrue(widgetContents.contains("Metadata.ISREADY_SLOT"))
        assertTrue(widgetContents.contains("_iWidgetProjection.isReady()"))
        assertTrue(widgetContents.contains("fun label(): String"))
        assertFalse(widgetContents.contains("invokeAbi("))
        assertTrue(widgetContents.contains("_iWidgetProjection.label()"))
        assertTrue(interfaceContents, interfaceContents.contains("HString.fromHandle("))
        assertTrue(widgetContents.contains("Metadata.LABEL_SLOT"))
        assertTrue(widgetContents.contains("Metadata.NAME_GETTER_SLOT"))
        assertTrue(widgetContents.contains("Metadata.COUNT_GETTER_SLOT"))
    }

    @Test
    fun generator_binds_single_parameter_string_and_uint32_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getNamedString",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "getStringAt",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                    methodRowId = 11,
                                ),
                                WinRtMethodDefinition(
                                    name = "rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    methodRowId = 12,
                                ),
                                WinRtMethodDefinition(
                                    name = "setSelectedIndex",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                    methodRowId = 13,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    getterMethodRowId = 14,
                                    setterMethodName = "put_Title",
                                    setterMethodRowId = 15,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "getNamedString",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                                WinRtMethodDefinition(
                                    name = "getStringAt",
                                    returnTypeName = "String",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                ),
                                WinRtMethodDefinition(
                                    name = "rename",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                ),
                                WinRtMethodDefinition(
                                    name = "setSelectedIndex",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("index", "UInt")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Title",
                                    typeName = "String",
                                    getterMethodName = "get_Title",
                                    setterMethodName = "put_Title",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun getNamedString(name: String): String"))
        if (widgetContents.contains("Projection")) {
            assertTrue(widgetContents.contains(".getNamedString(name)"))
        } else {
            assertTrue(widgetContents.contains("HString.createReference(name).use { __nameAbi ->"))
            assertFalse(widgetContents.contains("-> {"))
        }
        assertTrue(widgetContents.contains("Metadata.GETNAMEDSTRING_SLOT"))
        assertTrue(widgetContents.contains("fun getStringAt(index: UInt): String"))
        assertTrue(widgetContents, widgetContents.contains("index.toInt()") || widgetContents.contains("_iWidgetProjection.getStringAt(index)"))
        assertTrue(widgetContents.contains("Metadata.GETSTRINGAT_SLOT"))
        assertTrue(widgetContents.contains("fun rename(name: String)"))
        assertTrue(widgetContents.contains("Metadata.RENAME_SLOT"))
        assertTrue(widgetContents.contains("fun setSelectedIndex(index: UInt)"))
        assertTrue(widgetContents.contains("Metadata.SETSELECTEDINDEX_SLOT"))
        assertTrue(widgetContents.contains("var title: String"))
        assertTrue(widgetContents.contains("Metadata.TITLE_SETTER_SLOT"))
    }

    @Test
    fun generator_binds_single_parameter_boolean_and_double_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setReady",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("ready", "Boolean")),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "createNumberValue",
                                    returnTypeName = "Sample.Foundation.WidgetValue",
                                    parameters = listOf(WinRtParameterDefinition("value", "Double")),
                                    methodRowId = 11,
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Ready",
                                    typeName = "Boolean",
                                    getterMethodName = "get_Ready",
                                    getterMethodRowId = 12,
                                    setterMethodName = "put_Ready",
                                    setterMethodRowId = 13,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetValue",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetValue",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetValue", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setReady",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("ready", "Boolean")),
                                ),
                                WinRtMethodDefinition(
                                    name = "createNumberValue",
                                    returnTypeName = "Sample.Foundation.WidgetValue",
                                    parameters = listOf(WinRtParameterDefinition("value", "Double")),
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Ready",
                                    typeName = "Boolean",
                                    getterMethodName = "get_Ready",
                                    setterMethodName = "put_Ready",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun setReady(ready: Boolean)"))
        assertTrue(widgetContents.contains("Metadata.SETREADY_SLOT"))
        assertTrue(widgetContents.contains("fun createNumberValue("))
        assertTrue(widgetContents.contains("Double): WidgetValue"))
        assertTrue(widgetContents.contains("Metadata.CREATENUMBERVALUE_SLOT"))
        assertTrue(widgetContents.contains("_iWidgetProjection.createNumberValue(value)"))
        assertTrue(widgetContents.contains("var ready: Boolean"))
        assertTrue(widgetContents.contains("Metadata.READY_SETTER_SLOT"))
    }

    @Test
    fun generator_routes_nullable_value_properties_through_runtime_reference_projection_interop() {
        val referenceIntBinding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.Reference,
            typeName = "Windows.Foundation.IReference<Int>",
            interfaceId = Guid("61C17706-2D65-11E0-9AE8-D48564015472"),
            typeArguments = listOf(KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int")),
        )
        val property = KotlinProjectionRenderer().renderBoundProperty(
            plan = KotlinTypeProjectionPlan(
                type = WinRtTypeDefinition(
                    namespace = "Sample.Foundation",
                    name = "Widget",
                    kind = WinRtTypeKind.RuntimeClass,
                    isSealedType = true,
                ),
                packageName = "sample.foundation",
                relativePath = "sample/foundation/Widget.kt",
                declarationKind = KotlinProjectionDeclarationKind.Class,
                typeDeclarationDescriptor = WinRtTypeDeclarationDescriptor(
                    typeName = "Sample.Foundation.Widget",
                    declarationKind = WinRtTypeKind.RuntimeClass,
                    writesProjectedDeclaration = true,
                    writesAbiDeclaration = true,
                    writesWrapperDeclaration = true,
                    writesImplementationClass = false,
                    writesHelperClass = true,
                    netStandardBranch = false,
                ),
                instanceMemberBindings = listOf(
                    KotlinProjectionInstanceMemberBinding(
                        bindingName = "SELECTION_GETTER_SLOT",
                        ownerInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        ownerCachePropertyName = "_defaultInterface",
                        slotInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        slotConstantName = "SELECTION_GETTER_SLOT",
                        returnBinding = referenceIntBinding,
                    ),
                    KotlinProjectionInstanceMemberBinding(
                        bindingName = "SELECTION_SETTER_SLOT",
                        ownerInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        ownerCachePropertyName = "_defaultInterface",
                        slotInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        slotConstantName = "SELECTION_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(KotlinProjectionAbiParameterBinding("value", referenceIntBinding)),
                    ),
                ),
            ),
            property = WinRtPropertyDefinition(
                name = "Selection",
                typeName = "Windows.Foundation.IReference<Int>",
                getterMethodName = "get_Selection",
                setterMethodName = "put_Selection",
            ),
        ).toString()

        assertTrue(property, property.contains("var selection: kotlin.Int?"))
        assertTrue(property, property.contains("WinRtReferenceProjectionInterop.getReferenceValue("))
        assertTrue(property.contains("WinRtReferenceProjectionInterop.setReferenceValue("))
        assertFalse(property.contains("WinRtReferenceProjection.fromAbi(PlatformAbi.readPointer(__resultOut)"))
        assertFalse(property.contains("WinRtReferenceProjection.createMarshaler(value"))
    }

    @Test
    fun generator_routes_scalar_property_getters_through_projection_intrinsics() {
        val property = KotlinProjectionRenderer(useProjectionIntrinsics = true).renderBoundProperty(
            plan = KotlinTypeProjectionPlan(
                type = WinRtTypeDefinition(
                    namespace = "Sample.Foundation",
                    name = "Widget",
                    kind = WinRtTypeKind.RuntimeClass,
                    isSealedType = true,
                ),
                packageName = "sample.foundation",
                relativePath = "sample/foundation/Widget.kt",
                declarationKind = KotlinProjectionDeclarationKind.Class,
                typeDeclarationDescriptor = WinRtTypeDeclarationDescriptor(
                    typeName = "Sample.Foundation.Widget",
                    declarationKind = WinRtTypeKind.RuntimeClass,
                    writesProjectedDeclaration = true,
                    writesAbiDeclaration = true,
                    writesWrapperDeclaration = true,
                    writesImplementationClass = false,
                    writesHelperClass = true,
                    netStandardBranch = false,
                ),
                instanceMemberBindings = listOf(
                    KotlinProjectionInstanceMemberBinding(
                        bindingName = "COUNT_GETTER_SLOT",
                        ownerInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        ownerCachePropertyName = "_defaultInterface",
                        slotInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        slotConstantName = "COUNT_GETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Int32, "Int"),
                    ),
                ),
            ),
            property = WinRtPropertyDefinition(
                name = "Count",
                typeName = "Int",
                getterMethodName = "get_Count",
            ),
        ).toString()

        assertTrue(property, property.contains("val count: kotlin.Int"))
        assertTrue(property, property.contains("WinRtProjectionIntrinsic.getInt32("))
        assertFalse(property.contains("WinRtInstanceProjectionInterop.getInt32"))
        assertFalse(property.contains("PlatformAbi.confinedScope()"))
        assertFalse(property.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun generator_routes_interface_proxy_scalar_property_getters_through_projection_intrinsics() {
        val interfaceType = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555570"),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Ready",
                    typeName = "Boolean",
                    getterMethodName = "get_Ready",
                    getterMethodRowId = 6,
                ),
            ),
        )

        val property = KotlinProjectionRenderer(useProjectionIntrinsics = true).renderInterfaceProxyProperty(
            slotInterfaceType = interfaceType,
            property = interfaceType.properties.single(),
            typesByQualifiedName = mapOf(interfaceType.qualifiedName to interfaceType),
        ).toString()

        assertTrue(property, property.contains("override val ready: kotlin.Boolean"))
        assertTrue(property, property.contains("WinRtProjectionIntrinsic.getBoolean("))
        assertFalse(property.contains("WinRtInstanceProjectionInterop.getBoolean"))
        assertFalse(property.contains("PlatformAbi.confinedScope()"))
        assertFalse(property.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun generator_routes_struct_properties_through_projection_intrinsics() {
        val property = KotlinProjectionRenderer(useProjectionIntrinsics = true).renderBoundProperty(
            plan = KotlinTypeProjectionPlan(
                type = WinRtTypeDefinition(
                    namespace = "Sample.Foundation",
                    name = "Widget",
                    kind = WinRtTypeKind.RuntimeClass,
                    isSealedType = true,
                ),
                packageName = "sample.foundation",
                relativePath = "sample/foundation/Widget.kt",
                declarationKind = KotlinProjectionDeclarationKind.Class,
                typeDeclarationDescriptor = WinRtTypeDeclarationDescriptor(
                    typeName = "Sample.Foundation.Widget",
                    declarationKind = WinRtTypeKind.RuntimeClass,
                    writesProjectedDeclaration = true,
                    writesAbiDeclaration = true,
                    writesWrapperDeclaration = true,
                    writesImplementationClass = false,
                    writesHelperClass = true,
                    netStandardBranch = false,
                ),
                instanceMemberBindings = listOf(
                    KotlinProjectionInstanceMemberBinding(
                        bindingName = "CENTER_GETTER_SLOT",
                        ownerInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        ownerCachePropertyName = "_defaultInterface",
                        slotInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        slotConstantName = "CENTER_GETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(
                            kind = KotlinProjectionAbiValueKind.Struct,
                            typeName = "Sample.Foundation.Point",
                            sourceTypeKind = WinRtTypeKind.Struct,
                        ),
                    ),
                    KotlinProjectionInstanceMemberBinding(
                        bindingName = "CENTER_SETTER_SLOT",
                        ownerInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        ownerCachePropertyName = "_defaultInterface",
                        slotInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        slotConstantName = "CENTER_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = KotlinProjectionAbiTypeBinding(
                                    kind = KotlinProjectionAbiValueKind.Struct,
                                    typeName = "Sample.Foundation.Point",
                                    sourceTypeKind = WinRtTypeKind.Struct,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            property = WinRtPropertyDefinition(
                name = "Center",
                typeName = "Sample.Foundation.Point",
                getterMethodName = "get_Center",
                setterMethodName = "put_Center",
            ),
        ).toString()

        assertTrue(property, property.contains("WinRtProjectionIntrinsic.getStruct("))
        assertTrue(property, property.contains("WinRtProjectionIntrinsic.setStruct("))
        assertFalse(property.contains("WinRtInstanceProjectionInterop"))
        assertFalse(property.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_projected_object_property_getters_through_projection_intrinsics() {
        val property = KotlinProjectionRenderer(useProjectionIntrinsics = true).renderBoundProperty(
            plan = KotlinTypeProjectionPlan(
                type = WinRtTypeDefinition(
                    namespace = "Sample.Foundation",
                    name = "Widget",
                    kind = WinRtTypeKind.RuntimeClass,
                    isSealedType = true,
                ),
                packageName = "sample.foundation",
                relativePath = "sample/foundation/Widget.kt",
                declarationKind = KotlinProjectionDeclarationKind.Class,
                typeDeclarationDescriptor = WinRtTypeDeclarationDescriptor(
                    typeName = "Sample.Foundation.Widget",
                    declarationKind = WinRtTypeKind.RuntimeClass,
                    writesProjectedDeclaration = true,
                    writesAbiDeclaration = true,
                    writesWrapperDeclaration = true,
                    writesImplementationClass = false,
                    writesHelperClass = true,
                    netStandardBranch = false,
                ),
                instanceMemberBindings = listOf(
                    KotlinProjectionInstanceMemberBinding(
                        bindingName = "OWNER_GETTER_SLOT",
                        ownerInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        ownerCachePropertyName = "_defaultInterface",
                        slotInterfaceQualifiedName = "Sample.Foundation.IWidget",
                        slotConstantName = "OWNER_GETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(
                            kind = KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
                            typeName = "Sample.Foundation.Widget",
                            sourceTypeKind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
            property = WinRtPropertyDefinition(
                name = "Owner",
                typeName = "Sample.Foundation.Widget",
                getterMethodName = "get_Owner",
            ),
        ).toString()

        assertTrue(property, property.contains("val owner: sample.foundation.Widget"))
        assertTrue(
            property,
            property.contains("WinRtProjectionIntrinsic.getProjectedRuntimeClass(") ||
                property.contains("getNullableProjectedRuntimeClass("),
        )
        assertTrue(property, property.contains("Widget.Metadata::wrap"))
        assertFalse(property.contains("WinRtInstanceProjectionInterop.getProjectedRuntimeClass"))
        assertFalse(property.contains("PlatformAbi.confinedScope()"))
        assertFalse(property.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun generator_routes_interface_proxy_projected_object_property_getters_through_projection_intrinsics() {
        val widgetType = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "Widget",
            kind = WinRtTypeKind.RuntimeClass,
        )
        val interfaceType = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555571"),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Owner",
                    typeName = "Sample.Foundation.Widget",
                    getterMethodName = "get_Owner",
                    getterMethodRowId = 6,
                ),
            ),
        )

        val property = KotlinProjectionRenderer(useProjectionIntrinsics = true).renderInterfaceProxyProperty(
            slotInterfaceType = interfaceType,
            property = interfaceType.properties.single(),
            typesByQualifiedName = mapOf(
                interfaceType.qualifiedName to interfaceType,
                widgetType.qualifiedName to widgetType,
            ),
        ).toString()

        assertTrue(property, property.contains("override val owner: sample.foundation.Widget"))
        assertTrue(
            property,
            property.contains("WinRtProjectionIntrinsic.getProjectedRuntimeClass(") ||
                property.contains("getNullableProjectedRuntimeClass("),
        )
        assertTrue(property, property.contains("Widget.Metadata::wrap"))
        assertFalse(property.contains("WinRtInstanceProjectionInterop.getProjectedRuntimeClass"))
        assertFalse(property.contains("PlatformAbi.confinedScope()"))
        assertFalse(property.contains("ComVtableInvoker.invokeArgs"))
    }

    @Test
    fun generator_projects_known_nullable_xaml_object_properties_as_nullable() {
        val systemBackdropProperty = WinRtPropertyDefinition(
            name = "SystemBackdrop",
            typeName = "Microsoft.UI.Xaml.Media.SystemBackdrop",
            getterMethodName = "get_SystemBackdrop",
            setterMethodName = "put_SystemBackdrop",
        )
        val clipProperty = WinRtPropertyDefinition(
            name = "Clip",
            typeName = "Microsoft.UI.Xaml.Media.RectangleGeometry",
            getterMethodName = "get_Clip",
            setterMethodName = "put_Clip",
        )
        val contentProperty = WinRtPropertyDefinition(
            name = "Content",
            typeName = "System.Object",
            getterMethodName = "get_Content",
            setterMethodName = "put_Content",
        )
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Media",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "ISystemBackdrop",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555580"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "SystemBackdrop",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Media.ISystemBackdrop",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Media.ISystemBackdrop", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "IRectangleGeometry",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555581"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "RectangleGeometry",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Media.IRectangleGeometry",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Media.IRectangleGeometry", isDefault = true),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IWindow",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555582"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IWindow2",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555585"),
                            properties = listOf(systemBackdropProperty),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Window",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.IWindow",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.IWindow", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.IWindow2"),
                            ),
                            properties = listOf(systemBackdropProperty),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IUIElement",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555583"),
                            properties = listOf(clipProperty),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "UIElement",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.IUIElement",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.IUIElement", isDefault = true),
                            ),
                            properties = listOf(clipProperty),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Controls",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "IContentControl",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555584"),
                            properties = listOf(contentProperty),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls",
                            name = "ContentControl",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Controls.IContentControl",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Controls.IContentControl", isDefault = true),
                            ),
                            properties = listOf(contentProperty),
                        ),
                    ),
                ),
            ),
        )

        val plansByName = KotlinProjectionPlanner().plan(model).associateBy { it.type.qualifiedName }
        val renderer = KotlinProjectionRenderer()
        val windowPlan = plansByName.getValue("Microsoft.UI.Xaml.Window")
        val window2Plan = plansByName.getValue("Microsoft.UI.Xaml.IWindow2")
        val uiElementPlan = plansByName.getValue("Microsoft.UI.Xaml.UIElement")
        val contentControlPlan = plansByName.getValue("Microsoft.UI.Xaml.Controls.ContentControl")

        assertEquals(
            "Microsoft.UI.Xaml.Media.SystemBackdrop?",
            windowPlan.instanceMemberBindings.first { it.bindingName == "SYSTEMBACKDROP_GETTER_SLOT" }.returnBinding.typeName,
        )
        assertEquals(
            "Microsoft.UI.Xaml.Media.SystemBackdrop?",
            window2Plan.instanceMemberBindings.first { it.bindingName == "SYSTEMBACKDROP_GETTER_SLOT" }.returnBinding.typeName,
        )
        assertEquals(
            "Microsoft.UI.Xaml.Media.RectangleGeometry?",
            uiElementPlan.instanceMemberBindings.first { it.bindingName == "CLIP_SETTER_SLOT" }.parameterBindings.single().typeBinding.typeName,
        )
        assertEquals(
            "System.Object?",
            contentControlPlan.instanceMemberBindings.first { it.bindingName == "CONTENT_GETTER_SLOT" }.returnBinding.typeName,
        )

        val windowProperty = renderer.renderBoundProperty(windowPlan, systemBackdropProperty).toString()
        val clipPropertySource = renderer.renderBoundProperty(uiElementPlan, clipProperty).toString()
        val contentPropertySource = renderer.renderBoundProperty(contentControlPlan, contentProperty).toString()

        assertTrue(windowProperty, windowProperty.contains("var systemBackdrop: microsoft.ui.xaml.media.SystemBackdrop?"))
        assertTrue(windowProperty, windowProperty.contains("getNullableProjectedRuntimeClass("))
        assertTrue(
            windowProperty,
            windowProperty.contains("winRtProjectionMarshaler(value, \"Microsoft.UI.Xaml.Media.SystemBackdrop") ||
                windowProperty.contains("_iWindow2Projection.systemBackdrop = value"),
        )
        assertTrue(windowProperty, windowProperty.contains("Guid(\"11111111-2222-3333-4444-555555555580\")"))
        assertTrue(windowProperty, windowProperty.contains("__valueProjectionMarshaler.abi"))
        assertFalse(windowProperty.contains("WINRT_E_NULL_ABI_RETURN"))

        assertTrue(clipPropertySource, clipPropertySource.contains("var clip: microsoft.ui.xaml.media.RectangleGeometry?"))
        assertTrue(clipPropertySource, clipPropertySource.contains("getNullableProjectedRuntimeClass("))
        assertTrue(clipPropertySource, clipPropertySource.contains("winRtProjectionMarshaler(value, \"Microsoft.UI.Xaml.Media.RectangleGeometry"))
        assertTrue(clipPropertySource, clipPropertySource.contains("Guid(\"11111111-2222-3333-4444-555555555581\")"))
        assertTrue(clipPropertySource, clipPropertySource.contains("__valueProjectionMarshaler.abi"))
        assertFalse(clipPropertySource.contains("WINRT_E_NULL_ABI_RETURN"))

        assertTrue(contentPropertySource, contentPropertySource.contains("var content: kotlin.Any?"))
        assertTrue(contentPropertySource, contentPropertySource.contains("isNull(__resultPointer)) return null"))
        assertFalse(contentPropertySource.contains("WINRT_E_NULL_ABI_RETURN"))
    }

    @Test
    fun generator_keeps_interface_proxy_custom_struct_getters_on_inline_abi_readback() {
        val interfaceType = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555572"),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Created",
                    typeName = "Windows.Foundation.DateTime",
                    getterMethodName = "get_Created",
                    getterMethodRowId = 6,
                ),
            ),
        )

        val property = KotlinProjectionRenderer().renderInterfaceProxyProperty(
            slotInterfaceType = interfaceType,
            property = interfaceType.properties.single(),
            typesByQualifiedName = mapOf(interfaceType.qualifiedName to interfaceType),
        ).toString()

        assertTrue(property, property.contains("WinRtSystemProjectionMarshalers.dateTimeFromAbi"))
        assertFalse(property, property.contains("WinRtProjectionIntrinsic.getStruct"))
        assertFalse(property, property.contains("Instant.Metadata"))
    }

    @Test
    fun generator_keeps_interface_proxy_custom_object_getters_on_inline_abi_readback() {
        val interfaceType = WinRtTypeDefinition(
            namespace = "Sample.Foundation",
            name = "IWidget",
            kind = WinRtTypeKind.Interface,
            iid = Guid("11111111-2222-3333-4444-555555555573"),
            properties = listOf(
                WinRtPropertyDefinition(
                    name = "Command",
                    typeName = "Microsoft.UI.Xaml.Input.ICommand",
                    getterMethodName = "get_Command",
                    getterMethodRowId = 6,
                ),
            ),
        )

        val property = KotlinProjectionRenderer().renderInterfaceProxyProperty(
            slotInterfaceType = interfaceType,
            property = interfaceType.properties.single(),
            typesByQualifiedName = mapOf(interfaceType.qualifiedName to interfaceType),
        ).toString()

        assertTrue(property, property.contains("WinRtSystemProjectionMarshalers.objectFromAbi"))
        assertFalse(property, property.contains("WinRtInstanceProjectionInterop.getProjectedInterface"))
        assertFalse(property, property.contains("WinRtCommand.Metadata"))
    }

    @Test
    fun generator_applies_cswinrt_collection_async_and_custom_type_mappings() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetCollection",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("12345678-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "asReadOnly",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "asMap",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                                WinRtMethodDefinition(
                                    name = "refreshAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncAction",
                                ),
                                WinRtMethodDefinition(
                                    name = "fetchAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncOperation<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "SourceUri",
                                    typeName = "Windows.Foundation.Uri",
                                    getterMethodName = "get_SourceUri",
                                ),
                                WinRtPropertyDefinition(
                                    name = "Selection",
                                    typeName = "Windows.Foundation.IReference<Int>",
                                    getterMethodName = "get_Selection",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()

        assertFalse(file.contents, file.contents.contains("import java.net.URI"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtUri"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtAsyncActionReference"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtAsyncOperationReference"))
        assertTrue(file.contents, file.contents.contains("public interface IWidgetCollection : Iterable<String>"))
        assertTrue(file.contents, file.contents.contains("fun asReadOnly(): List<String>"))
        assertTrue(file.contents, file.contents.contains("fun asMap(): MutableMap<String, Int>"))
        assertTrue(file.contents, file.contents.contains("fun refreshAsync(): WinRtAsyncActionReference"))
        assertTrue(file.contents, file.contents.contains("fun fetchAsync(): WinRtAsyncOperationReference<String>"))
        assertTrue(file.contents, file.contents.contains("val sourceUri: WinRtUri"))
        assertTrue(file.contents, file.contents.contains("val selection: Int?"))
    }

    @Test
    fun generator_hands_custom_mapped_member_call_modes_to_companions_without_source_plan_table() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val iterableContents = filesByName.getValue("IIterable.kt").contents

        if (iterableContents.contains("CUSTOM_MAPPED_MEMBER_PLANS")) {
            assertTrue(iterableContents, iterableContents.contains("val CUSTOM_MAPPED_MEMBER_PLANS: List<String>"))
            assertTrue(iterableContents, iterableContents.contains("const val CUSTOM_MAPPED_MEMBER_CALL_MODE: String = \"static-abi\""))
            assertTrue(iterableContents, iterableContents.contains("const val CUSTOM_MAPPED_MEMBER_EXPLICIT: Boolean = true"))
            assertTrue(iterableContents, iterableContents.contains("const val CUSTOM_MAPPED_MEMBER_PRIVATE: Boolean = false"))
        }
        assertFalse(filesByName.containsKey("WinRTTypeShapeWriterPlan.kt"))
    }

    @Test
    fun generator_emits_kmp_metadata_structs_instead_of_dotnet_value_type_aliases() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Numerics",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Numerics",
                            name = "Vector3",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                                WinRtFieldDefinition("Z", "Float"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.UI",
                            name = "Color",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("A", "Byte"),
                                WinRtFieldDefinition("R", "Byte"),
                                WinRtFieldDefinition("G", "Byte"),
                                WinRtFieldDefinition("B", "Byte"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "CornerRadius",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("TopLeft", "Double")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Duration",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("TimeSpan", "Windows.Foundation.TimeSpan")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "GridLength",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("Value", "Double")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "Thickness",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("Left", "Double")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "CornerRadiusHelper",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml",
                            name = "IXamlServiceProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Controls.Primitives",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls.Primitives",
                            name = "GeneratorPosition",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("Index", "Int")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Controls.Primitives",
                            name = "GeneratorPositionHelper",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Media",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "Matrix",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("M11", "Double"),
                                WinRtFieldDefinition("M12", "Double"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media",
                            name = "MatrixHelper",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Media.Animation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media.Animation",
                            name = "KeyTime",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("TimeSpan", "Windows.Foundation.TimeSpan")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media.Animation",
                            name = "RepeatBehavior",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("Count", "Double")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media.Animation",
                            name = "KeyTimeHelper",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Media.Media3D",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media.Media3D",
                            name = "Matrix3D",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("M11", "Double")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Media.Media3D",
                            name = "Matrix3DHelper",
                            kind = WinRtTypeKind.RuntimeClass,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("Point.kt").contents.contains("public class Point("))
        assertTrue(filesByName.getValue("Vector3.kt").contents.contains("public class Vector3("))
        assertTrue(filesByName.getValue("Color.kt").contents.contains("public class Color("))
        assertTrue(filesByName.getValue("CornerRadius.kt").contents.contains("public class CornerRadius("))
        assertTrue(filesByName.getValue("Duration.kt").contents.contains("public class Duration("))
        assertTrue(filesByName.getValue("GridLength.kt").contents.contains("public class GridLength("))
        assertTrue(filesByName.getValue("Thickness.kt").contents.contains("public class Thickness("))
        assertTrue(filesByName.getValue("GeneratorPosition.kt").contents.contains("public class GeneratorPosition("))
        assertTrue(filesByName.getValue("Matrix.kt").contents.contains("public class Matrix("))
        assertTrue(filesByName.getValue("KeyTime.kt").contents.contains("public class KeyTime("))
        assertTrue(filesByName.getValue("RepeatBehavior.kt").contents.contains("public class RepeatBehavior("))
        assertTrue(filesByName.getValue("Matrix3D.kt").contents.contains("public class Matrix3D("))
        assertFalse(filesByName.containsKey("CornerRadiusHelper.kt"))
        assertFalse(filesByName.containsKey("GeneratorPositionHelper.kt"))
        assertFalse(filesByName.containsKey("MatrixHelper.kt"))
        assertFalse(filesByName.containsKey("KeyTimeHelper.kt"))
        assertFalse(filesByName.containsKey("Matrix3DHelper.kt"))
        assertFalse(filesByName.containsKey("IXamlServiceProvider.kt"))
        assertEquals(null, mappedTypeByAbiName("Windows.Foundation.Point"))
        assertEquals(null, mappedTypeByAbiName("Windows.Foundation.Numerics.Vector3"))
        assertEquals(null, mappedTypeByAbiName("Windows.UI.Color"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.CornerRadius"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Duration"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.GridLength"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Thickness"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Controls.Primitives.GeneratorPosition"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Media.Matrix"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Media.Animation.KeyTime"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Media.Animation.RepeatBehavior"))
        assertEquals(null, mappedTypeByAbiName("Microsoft.UI.Xaml.Media.Media3D.Matrix3D"))
        assertEquals("IXamlServiceProvider", mappedTypeByAbiName("Microsoft.UI.Xaml.IXamlServiceProvider")?.descriptionName)
    }

    @Test
    fun generator_skips_cswinrt_mapped_declarations_without_kotlin_support_surfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IReference",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            genericParameterCount = 1,
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Value",
                                    typeName = "T0",
                                    getterMethodName = "get_Value",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IPropertyValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            genericParameterCount = 1,
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }

        assertFalse(filesByName.containsKey("IReference.kt"))
        assertFalse(filesByName.containsKey("IPropertyValue.kt"))
        assertTrue(filesByName.getValue("IVector.kt").contents.contains("public interface IVector<T0>"))
    }

    @Test
    fun generator_uses_runtime_backed_cswinrt_system_mapped_type_names() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "T0"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "HResult",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("value", "Int")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "ISystemMappedSurface",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "lastFailure",
                                    returnTypeName = "Windows.Foundation.HResult",
                                ),
                                WinRtMethodDefinition(
                                    name = "command",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "PropertyChanged",
                                    typeName = "Microsoft.UI.Xaml.Data.INotifyPropertyChanged",
                                    getterMethodName = "get_PropertyChanged",
                                ),
                                WinRtPropertyDefinition(
                                    name = "CollectionChanged",
                                    typeName = "Windows.UI.Xaml.Interop.INotifyCollectionChanged",
                                    getterMethodName = "get_CollectionChanged",
                                ),
                                WinRtPropertyDefinition(
                                    name = "CollectionChangedArgs",
                                    typeName = "Microsoft.UI.Xaml.Interop.NotifyCollectionChangedEventArgs",
                                    getterMethodName = "get_CollectionChangedArgs",
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("ISystemMappedSurface.kt") }

        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.EventHandlerCallback"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtCommand"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtCollectionChangedNotifier"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtNotifyCollectionChangedEventArgs"))
        assertTrue(file.contents, file.contents.contains("import io.github.composefluent.winrt.runtime.WinRtPropertyChangedNotifier"))
        assertFalse(file.contents, file.contents.contains("import java.time"))
        assertTrue(file.contents, file.contents.contains("fun lastFailure(): Exception"))
        assertTrue(file.contents, file.contents.contains("fun command(): WinRtCommand"))
        assertTrue(file.contents, file.contents.contains("val propertyChanged: WinRtPropertyChangedNotifier"))
        assertTrue(file.contents, file.contents.contains("val collectionChanged: WinRtCollectionChangedNotifier"))
        assertTrue(file.contents, file.contents.contains("val collectionChangedArgs: WinRtNotifyCollectionChangedEventArgs"))
        assertTrue(file.contents, file.contents.contains("fun addChanged(handler: EventHandlerCallback<Int>): EventRegistrationToken"))
    }

    @Test
    fun generator_binds_custom_system_struct_abi_through_runtime_marshaler_facade() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "DateTime",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("universalTime", "Long")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "TimeSpan",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("duration", "Long")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "HResult",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(WinRtFieldDefinition("value", "Int")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IClock",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "now",
                                    returnTypeName = "Windows.Foundation.DateTime",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setDelay",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("delay", "Windows.Foundation.TimeSpan")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setFailure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("error", "Windows.Foundation.HResult")),
                                    methodRowId = 8,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Clock",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IClock",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "now",
                                    returnTypeName = "Windows.Foundation.DateTime",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setDelay",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("delay", "Windows.Foundation.TimeSpan")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setFailure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("error", "Windows.Foundation.HResult")),
                                    methodRowId = 8,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IClock",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IClock.kt").contents
        val classContents = filesByName.getValue("Clock.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("import kotlin.time.Duration"))
        assertTrue(interfaceContents, interfaceContents.contains("import kotlin.time.Instant"))
        assertTrue(interfaceContents, interfaceContents.contains("fun now(): Instant"))
        assertTrue(interfaceContents, interfaceContents.contains("fun setDelay(delay: Duration)"))
        assertTrue(interfaceContents, interfaceContents.contains("fun setFailure(error: Exception)"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtSystemProjectionMarshalers.dateTimeFromAbi(__resultOut)"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtSystemProjectionMarshalers.timeSpanToAbi(delay)"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtSystemProjectionMarshalers.hResultToAbi(error)"))
        assertFalse(classContents, classContents.contains(".Metadata.fromAbi(__resultOut)"))
        assertFalse(classContents, classContents.contains(".Metadata.copyTo(delay"))
        assertFalse(classContents, classContents.contains(".Metadata.copyTo(error"))
    }

    @Test
    fun generator_binds_xaml_type_name_to_kclass_through_runtime_marshaler_facade() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Xaml",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "ITypeHost",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "currentType",
                                    returnTypeName = "Windows.UI.Xaml.Interop.TypeName",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCurrentType",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("type", "Windows.UI.Xaml.Interop.TypeName")),
                                    methodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Xaml",
                            name = "TypeHost",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Xaml.ITypeHost",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "currentType",
                                    returnTypeName = "Windows.UI.Xaml.Interop.TypeName",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCurrentType",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("type", "Windows.UI.Xaml.Interop.TypeName")),
                                    methodRowId = 7,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Xaml.ITypeHost",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("ITypeHost.kt").contents
        val classContents = filesByName.getValue("TypeHost.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("import kotlin.reflect.KClass"))
        assertTrue(interfaceContents, interfaceContents.contains("fun currentType(): KClass<*>?"))
        assertTrue(interfaceContents, interfaceContents.contains("fun setCurrentType(type: KClass<*>?)"))
        if (classContents.contains("_iTypeHostProjection")) {
            assertTrue(classContents, classContents.contains("_iTypeHostProjection.currentType()"))
            assertTrue(classContents, classContents.contains("_iTypeHostProjection.setCurrentType(type)"))
        } else {
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.typeNameFromAbi(__resultOut)"))
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.disposeTypeNameAbi(__resultOut)"))
            assertTrue(classContents, classContents.contains("PlatformAbi.allocateBytes(__typeStructScope, 16L)"))
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyTypeNameTo(type, __typeAbi)"))
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.disposeTypeNameAbi(__typeAbi)"))
        }
        assertFalse(classContents, classContents.contains("TypeName.Metadata"))
    }

    @Test
    fun generator_keeps_legacy_short_abi_argument_lists_on_generic_fallback_after_overload_shrink() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.FastAbi",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IShape",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setEnabled",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("enabled", "Boolean")),
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOffset",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("offset", "Short")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOpacity",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("opacity", "Float")),
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "configure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("enabled", "Boolean"),
                                        WinRtParameterDefinition("offset", "Short"),
                                        WinRtParameterDefinition("opacity", "Float"),
                                    ),
                                    methodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "Shape",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.FastAbi.IShape",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setEnabled",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("enabled", "Boolean")),
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOffset",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("offset", "Short")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "setOpacity",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("opacity", "Float")),
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "configure",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("enabled", "Boolean"),
                                        WinRtParameterDefinition("offset", "Short"),
                                        WinRtParameterDefinition("opacity", "Float"),
                                    ),
                                    methodRowId = 9,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.FastAbi.IShape",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val shapeContents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("/Shape.kt") }
            .contents

        if (shapeContents.contains("_iShapeProjection")) {
            assertTrue(shapeContents, shapeContents.contains("_iShapeProjection.setOffset(offset)"))
            assertTrue(shapeContents, shapeContents.contains("_iShapeProjection.setOpacity(opacity)"))
            assertTrue(shapeContents, shapeContents.contains("_iShapeProjection.configure(enabled, offset, opacity)"))
        } else {
            assertTrue(shapeContents, shapeContents.contains("invokeGenericArgs(instance = _defaultInterface.pointer"))
            assertTrue(shapeContents, shapeContents.contains("if (enabled) 1.toByte() else"))
            assertTrue(shapeContents, shapeContents.contains("0.toByte()"))
            assertTrue(shapeContents, shapeContents.contains("Metadata.SETOFFSET_SLOT"))
            assertTrue(shapeContents, shapeContents.contains("Metadata.SETOPACITY_SLOT"))
            assertTrue(shapeContents, shapeContents.contains("Metadata.CONFIGURE_SLOT"))
            assertTrue(shapeContents, shapeContents.contains("Metadata.SETOFFSET_SLOT, offset"))
            assertTrue(shapeContents, shapeContents.contains("Metadata.SETOPACITY_SLOT, opacity"))
            assertTrue(shapeContents, shapeContents.contains("offset, opacity"))
            assertFalse(shapeContents, shapeContents.contains("ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer"))
        }
    }

    @Test
    fun generator_hands_fast_abi_class_slots_to_metadata_companion() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.FastAbi",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                            methods = listOf(
                                WinRtMethodDefinition("get_Name", "String", isSpecialName = true, methodRowId = 6),
                                WinRtMethodDefinition("set_Name", "Unit", parameters = listOf(WinRtParameterDefinition("value", "String")), isSpecialName = true, methodRowId = 7),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Name",
                                    typeName = "String",
                                    getterMethodName = "get_Name",
                                    setterMethodName = "set_Name",
                                    getterMethodRowId = 6,
                                    setterMethodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IWidgetOverrides",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                io.github.composefluent.winrt.metadata.WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(
                                        io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue.TypeValue("Sample.FastAbi.Widget"),
                                    ),
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("get_Mode", "Int", isSpecialName = true, methodRowId = 8),
                                WinRtMethodDefinition("set_Mode", "Unit", parameters = listOf(WinRtParameterDefinition("value", "Int")), isSpecialName = true, methodRowId = 9),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Mode",
                                    typeName = "Int",
                                    getterMethodName = "get_Mode",
                                    setterMethodName = "set_Mode",
                                    getterMethodRowId = 8,
                                    setterMethodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isFastAbi = true,
                            defaultInterfaceName = "Sample.FastAbi.IWidget",
                            methods = listOf(
                                WinRtMethodDefinition("get_Mode", "Int", isSpecialName = true, methodRowId = 8),
                                WinRtMethodDefinition("set_Mode", "Unit", parameters = listOf(WinRtParameterDefinition("value", "Int")), isSpecialName = true, methodRowId = 9),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Mode",
                                    typeName = "Int",
                                    getterMethodName = "get_Mode",
                                    setterMethodName = "set_Mode",
                                    getterMethodRowId = 8,
                                    setterMethodRowId = 9,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IWidget", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IWidgetOverrides"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val widgetContents = filesByName.getValue("Widget.kt").contents
        val defaultInterfaceContents = filesByName.getValue("IWidget.kt").contents
        val exclusiveInterfaceContents = filesByName.getValue("IWidgetOverrides.kt").contents

        assertTrue(widgetContents, widgetContents.contains("val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(widgetContents, widgetContents.contains("val FAST_ABI_PROPERTY_SLOTS: List<String>"))
        assertTrue(widgetContents, widgetContents.contains("Name|start=6|get=6|set=7"))
        assertTrue(widgetContents, widgetContents.contains("Mode|start=8|get=8|set=9"))
        assertTrue(widgetContents, widgetContents.contains("val OBJECT_REFERENCE_NAMES: List<String> = listOf(\"Sample_FastAbi_IWidgetCache\")"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidget|cache=Sample_FastAbi_IWidgetCache|default=true|skip=|inner=false|defaultObjRef=false|hierarchy=|defaultObjRefSlot=|generic=false"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidgetOverrides|cache=Sample_FastAbi_IWidgetOverridesCache|default=false|skip=fast-abi-non-default-exclusive"))
        assertTrue(widgetContents, widgetContents.contains("const val MODE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents, widgetContents.contains("_iWidgetOverridesProjection"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("const val NAME_SETTER_SLOT: Int = 7"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("const val MODE_GETTER_SLOT: Int = 8"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("const val MODE_SETTER_SLOT: Int = 9"))
    }

    @Test
    fun generator_consumes_cswinrt_object_reference_cache_plans() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.FastAbi",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IDefaultExclusive",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555571"),
                            isExclusiveTo = true,
                            customAttributes = listOf(
                                io.github.composefluent.winrt.metadata.WinRtCustomAttributeDefinition(
                                    typeName = "Windows.Foundation.Metadata.ExclusiveToAttribute",
                                    fixedArguments = listOf(
                                        io.github.composefluent.winrt.metadata.WinRtCustomAttributeValue.TypeValue("Sample.FastAbi.FastDefaultExclusiveWidget"),
                                    ),
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Start", "Unit", methodRowId = 10),
                                WinRtMethodDefinition("Stop", "Unit", methodRowId = 11),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "IGeneric",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555572"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.FastAbi",
                            name = "FastDefaultExclusiveWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isFastAbi = true,
                            isSealedType = false,
                            defaultInterfaceName = "Sample.FastAbi.IDefaultExclusive",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IDefaultExclusive", isDefault = true),
                                WinRtInterfaceImplementationDefinition("Sample.FastAbi.IGeneric<String>"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val widgetContents = KotlinProjectionGenerator().generate(model)
            .single { it.relativePath.endsWith("/FastDefaultExclusiveWidget.kt") }
            .contents

        assertTrue(widgetContents, widgetContents.contains("private val _defaultInterface: ComObjectReference"))
        assertTrue(widgetContents, widgetContents.contains("getDefaultInterfaceObjectReference(8)"))
        assertTrue(widgetContents, widgetContents.contains("initializeBySourceType("))
        assertFalse(widgetContents, widgetContents.contains("initializeDependencies(entry) { }"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IDefaultExclusive|cache=Sample_FastAbi_IDefaultExclusiveCache|default=true|skip=|inner=false|defaultObjRef=true|hierarchy=0|defaultObjRefSlot=8|generic=false"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IGeneric<String>|cache=Sample_FastAbi_IGeneric_String_Cache|default=false|skip=|inner=false|defaultObjRef=false|hierarchy=|defaultObjRefSlot=|generic=true"))
    }

    @Test
    fun generator_binds_custom_object_mapped_abi_through_runtime_marshaler_facade() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IUriCommandHost",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "sourceUri",
                                    returnTypeName = "Windows.Foundation.Uri",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setSourceUri",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("sourceUri", "Windows.Foundation.Uri")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "command",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand",
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "tryCommand",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand?",
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCommand",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("command", "Microsoft.UI.Xaml.Input.ICommand")),
                                    methodRowId = 9,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "UriCommandHost",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IUriCommandHost",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "sourceUri",
                                    returnTypeName = "Windows.Foundation.Uri",
                                    methodRowId = 6,
                                ),
                                WinRtMethodDefinition(
                                    name = "setSourceUri",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("sourceUri", "Windows.Foundation.Uri")),
                                    methodRowId = 7,
                                ),
                                WinRtMethodDefinition(
                                    name = "command",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand",
                                    methodRowId = 8,
                                ),
                                WinRtMethodDefinition(
                                    name = "tryCommand",
                                    returnTypeName = "Microsoft.UI.Xaml.Input.ICommand?",
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "setCommand",
                                    returnTypeName = "Unit",
                                    parameters = listOf(WinRtParameterDefinition("command", "Microsoft.UI.Xaml.Input.ICommand")),
                                    methodRowId = 9,
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IUriCommandHost",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val interfaceContents = filesByName.getValue("IUriCommandHost.kt").contents
        val classContents = filesByName.getValue("UriCommandHost.kt").contents

        assertTrue(interfaceContents, interfaceContents.contains("fun sourceUri(): WinRtUri"))
        assertTrue(interfaceContents, interfaceContents.contains("fun command(): WinRtCommand"))
        assertTrue(interfaceContents, interfaceContents.contains("fun tryCommand(): WinRtCommand"))
        if (classContents.contains("_iUriCommandHostProjection")) {
            assertTrue(classContents, classContents.contains("_iUriCommandHostProjection.sourceUri()"))
            assertTrue(classContents, classContents.contains("_iUriCommandHostProjection.tryCommand()"))
        } else {
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.uriFromAbi(__resultPointer)"))
            assertTrue(classContents, classContents.contains("val __resultPointer = PlatformAbi.readPointer(__resultOut)"))
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.objectFromAbi(__resultPointer,"))
            assertTrue(classContents, classContents.contains("if (PlatformAbi.isNull(__resultPointer)) return null"))
            assertTrue(classContents, classContents.contains("WinRtTypeHandle(\"io.github.composefluent.winrt.runtime.WinRtCommand\""))
            assertTrue(classContents, classContents.contains("Guid(\"E5AF3542-CA67-4081-995B-709DD13792DF\")), WinRtCommand::class)"))
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.createObjectReference(sourceUri,"))
            assertTrue(classContents, classContents.contains("Guid(\"9E365E57-48B2-4160-956F-C7385120BBFC\")).use { __sourceUriAbi ->"))
            assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.createObjectReference(command,"))
            assertTrue(classContents, classContents.contains("Guid(\"E5AF3542-CA67-4081-995B-709DD13792DF\")).use { __commandAbi ->"))
        }
        assertFalse(classContents, classContents.contains("sourceUri as IWinRTObject"))
        assertFalse(classContents, classContents.contains("command as IWinRTObject"))
    }

    @Test
    fun generator_emits_runtime_backed_async_abi_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStream",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("12345678-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refreshAsync", returnTypeName = "Windows.Foundation.IAsyncAction", methodRowId = 6),
                                WinRtMethodDefinition(name = "fetchAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<String>", methodRowId = 7),
                                WinRtMethodDefinition(name = "fetchStreamAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<Sample.Foundation.IStream>", methodRowId = 8),
                                WinRtMethodDefinition(name = "fetchCommandAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<Microsoft.UI.Xaml.Input.ICommand>", methodRowId = 11),
                                WinRtMethodDefinition(name = "refreshWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncActionWithProgress<Int>", methodRowId = 9),
                                WinRtMethodDefinition(name = "fetchWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<String, UInt>", methodRowId = 10),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "refreshAsync", returnTypeName = "Windows.Foundation.IAsyncAction"),
                                WinRtMethodDefinition(name = "fetchAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<String>"),
                                WinRtMethodDefinition(name = "fetchStreamAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<Sample.Foundation.IStream>"),
                                WinRtMethodDefinition(name = "fetchCommandAsync", returnTypeName = "Windows.Foundation.IAsyncOperation<Microsoft.UI.Xaml.Input.ICommand>"),
                                WinRtMethodDefinition(name = "refreshWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncActionWithProgress<Int>"),
                                WinRtMethodDefinition(name = "fetchWithProgressAsync", returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<String, UInt>"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val interfaceContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath }
            .getValue("sample/foundation/IWidget.kt")
            .contents

        assertFalse(interfaceContents.contains("CompletableFuture"))
        assertTrue(interfaceContents.contains("fun refreshAsync(): WinRtAsyncActionReference"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtAsyncActionReference(PlatformAbi.readPointer(__resultOut))"))
        assertTrue(interfaceContents.contains("fun fetchAsync(): WinRtAsyncOperationReference<String>"))
        assertTrue(interfaceContents.contains("WinRtAsyncProjectionInterop.operation<"))
        assertTrue(interfaceContents.contains("resultSignature = WinRtTypeSignature.string()"))
        assertTrue(interfaceContents.contains("resultOut = { __operationScope -> PlatformAbi.allocatePointerSlot(__operationScope) }"))
        assertFalse(interfaceContents.contains("ComVtableInvoker.invokeArgs(__operation.pointer,"))
        assertFalse(interfaceContents.contains("WinRtAsyncOperationVftblSlots.GetResults, __operationResultOut)"))
        assertTrue(interfaceContents.contains("val __operationResultString ="))
        assertTrue(interfaceContents.contains("fromHandle("))
        assertTrue(interfaceContents.contains("PlatformAbi.readPointer(__operationResultOut), owner = true"))
        assertTrue(interfaceContents.contains("__operationResultString.use { value -> value.toKString() }"))
        assertTrue(interfaceContents.contains("fun fetchStreamAsync(): WinRtAsyncOperationReference<IStream>"))
        assertTrue(interfaceContents.contains("WinRtTypeSignature.guid(IStream.Metadata.IID)"))
        assertTrue(interfaceContents.contains("IStream.Metadata.wrap(IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__operationResultOut))))"))
        assertTrue(interfaceContents.contains("fun fetchCommandAsync(): WinRtAsyncOperationReference<WinRtCommand>"))
        assertTrue(interfaceContents.contains("WinRtSystemProjectionMarshalers.objectFromAbi(__operationResultPointer,"))
        assertTrue(interfaceContents.contains("WinRtTypeHandle(\"io.github.composefluent.winrt.runtime.WinRtCommand\""))
        assertFalse(interfaceContents.contains("WinRtCommand.Metadata.wrap"))
        assertTrue(interfaceContents.contains("fun refreshWithProgressAsync(): WinRtAsyncActionWithProgressReference<Int>"))
        assertTrue(interfaceContents.contains("WinRtAsyncProjectionInterop.actionWithProgress<"))
        assertTrue(interfaceContents.contains("progressSignature = WinRtTypeSignature.int32()"))
        assertTrue(interfaceContents.contains("fun fetchWithProgressAsync(): WinRtAsyncOperationWithProgressReference<String, UInt>"))
        assertTrue(interfaceContents.contains("WinRtAsyncProjectionInterop.operationWithProgress<"))
        assertTrue(interfaceContents.contains("WinRtTypeSignature.string()"))
        assertTrue(interfaceContents.contains("WinRtTypeSignature.uint32()"))
        assertFalse(interfaceContents.contains("WinRtAsyncOperationWithProgressVftblSlots.GetResults, __operationResultOut)"))
    }

    @Test
    fun generator_emits_guid_async_operation_result_readback() {
        val renderer = KotlinProjectionRenderer()
        val returnBinding = KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.MappedAsyncOperation,
            typeName = "Windows.Foundation.IAsyncOperation<System.Guid>",
            typeArguments = listOf(
                KotlinProjectionAbiTypeBinding(
                    kind = KotlinProjectionAbiValueKind.GuidValue,
                    typeName = "System.Guid",
                ),
            ),
        )

        val expression = renderer.asyncReferenceExpression(returnBinding, CodeBlock.of("__pointer")).toString()

        assertTrue(expression, expression.contains("WinRtAsyncProjectionInterop.operation<io.github.composefluent.winrt.runtime.Guid>"))
        assertTrue(expression, expression.contains("resultSignature = io.github.composefluent.winrt.runtime.WinRtTypeSignature.guidValue()"))
        assertTrue(expression, expression.contains("io.github.composefluent.winrt.runtime.PlatformAbi.allocateBytes(__operationScope, io.github.composefluent.winrt.runtime.Guid.BYTE_SIZE.toLong())"))
        assertTrue(expression, expression.contains("io.github.composefluent.winrt.runtime.PlatformAbi.readGuid(__operationResultOut)"))
    }

    @Test
    fun generator_routes_async_operation_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "InputStreamOptions",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBuffer",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555571"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IInputStream",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555572"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "ReadAsync",
                                    returnTypeName = "Windows.Foundation.IAsyncOperationWithProgress<Sample.Foundation.IBuffer, UInt>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("buffer", "Sample.Foundation.IBuffer"),
                                        WinRtParameterDefinition("count", "UInt"),
                                        WinRtParameterDefinition("options", "Sample.Foundation.InputStreamOptions"),
                                    ),
                                    methodRowId = 17,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IInputStream.kt")
            .contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callProjectedInterface("))
        assertTrue(contents.contains("\"Object,UInt32,Int32\""))
        assertTrue(contents.contains("WinRtAsyncProjectionInterop.operationWithProgress<IBuffer, UInt>("))
        assertTrue(contents.contains("pointer = PlatformAbi.fromRawComPtr(__asyncReference.pointer)"))
        assertTrue(contents.contains("buffer as IWinRTObject"))
        assertTrue(contents.contains("options.abiValue"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_raw_abi_unit_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IRoutedEvent",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555573"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "RoutedEvent",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IRoutedEvent",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IRoutedEvent",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IElement",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555574"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "AddHandler",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("routedEvent", "Sample.UI.RoutedEvent"),
                                        WinRtParameterDefinition("handler", "System.Object"),
                                        WinRtParameterDefinition("handledEventsToo", "Boolean"),
                                    ),
                                    methodRowId = 19,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IElement.kt")
            .contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(contents.contains("\"RawAddress,RawAddress,Byte\""))
        assertTrue(contents.contains("winRtProjectionMarshaler(routedEvent, \"Sample.UI.RoutedEvent\""))
        assertTrue(contents.contains("Guid(\"11111111-2222-3333-4444-555555555573\")).use {"))
        assertTrue(contents.contains("__routedEventProjectionMarshaler ->"))
        assertTrue(contents.contains("__routedEventProjectionMarshaler.abi"))
        assertFalse(contents.contains("PlatformAbi.fromRawComPtr((routedEvent as IWinRTObject).nativeObject.pointer)"))
        assertTrue(contents.contains("__handlerMarshaler.abi"))
        assertTrue(contents.contains("if (handledEventsToo) 1.toByte() else 0.toByte()"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_raw_abi_projected_object_returns_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555577"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IWidget",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555578"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateWidget",
                                    returnTypeName = "Sample.UI.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("input", "System.Object"),
                                        WinRtParameterDefinition("count", "UInt"),
                                    ),
                                    methodRowId = 23,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IWidgetFactory.kt")
            .contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("winRtObjectMarshaler(input).use { __inputMarshaler ->"))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callProjectedRuntimeClass("))
        assertTrue(contents.contains("\"RawAddress,Int32\""))
        assertTrue(contents.contains("Widget.Metadata::wrap"))
        assertTrue(contents.contains("__inputMarshaler.abi"))
        assertTrue(contents.contains("count.toInt()"))
        assertFalse(contents.contains("PlatformAbi.allocatePointerSlot(__scope)"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_preserves_by_value_struct_layout_tokens_for_projected_object_intrinsics() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float", rowId = 1, offset = 0, abiSize = 4, abiAlignment = 4, isBlittable = true),
                                WinRtFieldDefinition("Y", "Float", rowId = 2, offset = 4, abiSize = 4, abiAlignment = 4, isBlittable = true),
                            ),
                            layout = WinRtTypeLayout(WinRtTypeLayoutKind.Sequential, packingSize = 4, classSize = 8),
                            isBlittable = true,
                            abiSize = 8,
                            abiAlignment = 4,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IPeer",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-55555555557a"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetPeerFromPoint",
                                    returnTypeName = "Sample.UI.Peer",
                                    parameters = listOf(WinRtParameterDefinition("point", "Windows.Foundation.Point")),
                                    methodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Peer",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IPeer",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.UI.IPeer", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IPeer.kt")
            .contents

        assertTrue(contents.contains("WinRtProjectionIntrinsic.callProjectedRuntimeClass("))
        assertTrue(contents.contains("\"Struct8_4\""))
        assertFalse(contents.contains("\"Struct\""))
        assertTrue(contents.contains("Point.Metadata"))
        assertTrue(contents.contains("Peer.Metadata::wrap"))
    }

    @Test
    fun generator_routes_raw_abi_object_returns_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IValueConverter",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555579"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Convert",
                                    returnTypeName = "System.Object",
                                    parameters = listOf(
                                        WinRtParameterDefinition("value", "System.Object"),
                                        WinRtParameterDefinition("targetType", "Windows.UI.Xaml.Interop.TypeName"),
                                        WinRtParameterDefinition("parameter", "System.Object"),
                                        WinRtParameterDefinition("language", "String"),
                                    ),
                                    methodRowId = 24,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IValueConverter.kt")
            .contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents, contents.contains("winRtObjectMarshaler(value).use { __valueMarshaler ->"))
        assertTrue(contents.contains("PlatformAbi.allocateBytes(__targetTypeStructScope, 16L)"))
        assertTrue(contents.contains("WinRtSystemProjectionMarshalers.copyTypeNameTo(targetType, __targetTypeAbi)"))
        assertTrue(contents.contains("winRtObjectMarshaler(parameter).use { __parameterMarshaler ->"))
        assertTrue(contents.contains("HString.createReference(language).use { __languageAbi ->"))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callObject("))
        assertTrue(contents.contains("\"RawAddress,RawAddress,RawAddress,RawAddress\""))
        assertTrue(contents.contains("__valueMarshaler.abi"))
        assertTrue(contents.contains("__targetTypeAbi"))
        assertTrue(contents.contains("__parameterMarshaler.abi"))
        assertTrue(contents.contains("__languageAbi.handle"))
        assertFalse(contents.contains("PlatformAbi.allocatePointerSlot(__scope)"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_raw_abi_inspectable_factory_returns_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555580"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Color",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("A", "Byte"),
                                WinRtFieldDefinition("R", "Byte"),
                                WinRtFieldDefinition("G", "Byte"),
                                WinRtFieldDefinition("B", "Byte"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555581"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = emptyList(),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555582"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateWithHandler",
                                    returnTypeName = "Sample.UI.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("title", "String"),
                                        WinRtParameterDefinition("value", "System.Object"),
                                        WinRtParameterDefinition("color", "Sample.UI.Color"),
                                        WinRtParameterDefinition("handler", "Sample.UI.WidgetHandler"),
                                    ),
                                    methodRowId = 25,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.UI.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.UI.IWidgetFactory",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertTrue(contents.contains("HString.createReference(title).use { __titleAbi ->"))
        assertTrue(contents.contains("winRtObjectMarshaler(value).use { __valueMarshaler ->"))
        assertTrue(contents.contains("Color.Metadata.copyTo(color, __colorAbi)"))
        assertTrue(contents.contains("WinRtDelegateBridge.createDelegate("))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callProjectedInterface("))
        assertTrue(contents.contains("\"RawAddress,RawAddress,RawAddress,RawAddress\""))
        assertTrue(contents.contains("{ __result -> __result.use { it.asInspectable() } },"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs(instance = acquire().pointer"))
    }

    @Test
    fun generator_routes_composable_factory_returns_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555583"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555584"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Sample.UI.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("firstIndex", "Int"),
                                        WinRtParameterDefinition("length", "UInt"),
                                        WinRtParameterDefinition("baseInterface", "System.Object"),
                                        WinRtParameterDefinition("innerInterface", "System.Object"),
                                    ),
                                    methodRowId = 26,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Sample.UI.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.UI.IWidget", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                composableFactoryInterfaceName = "Sample.UI.IWidgetFactory",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents
        val createInstance = contents.substringAfter("internal fun createInstance").substringBefore("public fun acquire")

        assertTrue(createInstance.contains("val __innerOut = PlatformAbi.allocatePointerSlot(__scope)"))
        assertTrue(createInstance.contains("WinRtProjectionIntrinsic.callProjectedInterface("))
        assertTrue(createInstance.contains("\"Int32,Int32,RawAddress,RawAddress\""))
        assertTrue(createInstance.contains("{ __result -> __result.use {"))
        assertTrue(createInstance.contains("ComWrappersSupport.initializeComposableReference(it,"))
        assertTrue(createInstance.contains("DEFAULT_INTERFACE_IID"))
        assertTrue(createInstance.contains("PlatformAbi.nullPointer"))
        assertTrue(createInstance.contains("__innerOut"))
        assertTrue(createInstance.contains("return __result"))
        assertFalse(createInstance.contains("__resultOut"))
        assertFalse(createInstance.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_derived_composable_factory_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555585"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555586"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Sample.UI.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("baseInterface", "System.Object"),
                                        WinRtParameterDefinition("innerInterface", "System.Object"),
                                    ),
                                    methodRowId = 27,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IWidget",
                                    isDefault = true,
                                    isOverridable = true,
                                ),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                composableFactoryInterfaceName = "Sample.UI.IWidgetFactory",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents
        val publicConstructor = contents.substringAfter("public constructor()")
            .substringBefore("override fun equals")
        val createInstanceForSubclass = contents.substringAfter("internal fun createInstanceForSubclass")
            .substringBefore("public fun acquire")

        assertTrue(publicConstructor.contains("WinRtAuthoringSupportIntrinsic.ensureInitialized()"))
        assertTrue(publicConstructor.indexOf("WinRtAuthoringSupportIntrinsic.ensureInitialized()") < publicConstructor.indexOf("ComposableFactory.createInstanceForSubclass"))
        assertTrue(publicConstructor.contains("ComposableFactory.createInstanceForSubclass"))
        assertTrue(publicConstructor.contains("Metadata.DEFAULT_INTERFACE_IID"))
        assertTrue(createInstanceForSubclass.contains("createComposableCCWForObject"))
        assertTrue(createInstanceForSubclass.contains("outerInterfaceId"))
        assertTrue(createInstanceForSubclass.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(createInstanceForSubclass.contains("\"RawAddress,RawAddress,RawAddress\""))
        assertTrue(createInstanceForSubclass.contains("__baseInterface"))
        assertTrue(createInstanceForSubclass.contains("__innerOut"))
        assertTrue(createInstanceForSubclass.contains("__resultOut"))
        assertTrue(createInstanceForSubclass.contains("KnownHResults.S_OK.value"))
        assertFalse(createInstanceForSubclass.contains("ComVtableInvoker.invokeArgs"))
        assertFalse(createInstanceForSubclass.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_mapped_collection_returns_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "Orientation",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Horizontal", 0u),
                                WinRtEnumMemberDefinition("Vertical", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "SnapPointsAlignment",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Near", 0u),
                                WinRtEnumMemberDefinition("Far", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IScrollSnapPointsInfo",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555585"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetIrregularSnapPoints",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Float>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("orientation", "Sample.UI.Orientation"),
                                        WinRtParameterDefinition("alignment", "Sample.UI.SnapPointsAlignment"),
                                    ),
                                    methodRowId = 27,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IScrollSnapPointsInfo.kt")
            .contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callProjectedInterface("))
        assertTrue(contents.contains("\"Int32,Int32\""))
        assertTrue(contents.contains("{ __collectionRef ->"))
        assertTrue(contents.contains("object : AbstractList<Float>(), List<Float>, IWinRTObject"))
        assertTrue(contents.contains("orientation.abiValue"))
        assertTrue(contents.contains("alignment.abiValue"))
        assertFalse(contents.contains("PlatformAbi.allocatePointerSlot(__scope)"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_struct_array_results_through_descriptor_call_unit() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "PointInt32",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Int32"),
                                WinRtFieldDefinition("Y", "Int32"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "RoundingMode",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("None", 0u),
                                WinRtEnumMemberDefinition("Nearest", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "ICoordinateConverter",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555586"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "ConvertLocalToScreen",
                                    returnTypeName = "Array<Sample.Foundation.PointInt32>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("localPoints", "Array<Sample.Foundation.Point>"),
                                    ),
                                    methodRowId = 31,
                                ),
                                WinRtMethodDefinition(
                                    name = "ConvertLocalToScreen",
                                    returnTypeName = "Array<Sample.Foundation.PointInt32>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("localPoints", "Array<Sample.Foundation.Point>"),
                                        WinRtParameterDefinition("roundingMode", "Sample.UI.RoundingMode"),
                                    ),
                                    methodRowId = 32,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "CoordinateConverter",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.ICoordinateConverter",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Sample.UI.ICoordinateConverter",
                                    isDefault = true,
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "ConvertLocalToScreen",
                                    returnTypeName = "Array<Sample.Foundation.PointInt32>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("localPoints", "Array<Sample.Foundation.Point>"),
                                    ),
                                    methodRowId = 31,
                                ),
                                WinRtMethodDefinition(
                                    name = "ConvertLocalToScreen",
                                    returnTypeName = "Array<Sample.Foundation.PointInt32>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("localPoints", "Array<Sample.Foundation.Point>"),
                                        WinRtParameterDefinition("roundingMode", "Sample.UI.RoundingMode"),
                                    ),
                                    methodRowId = 32,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("CoordinateConverter.kt")
            .contents

        if (contents.contains("_iCoordinateConverterProjection")) {
            assertTrue(contents.contains("_iCoordinateConverterProjection.convert"))
        } else {
            assertTrue(contents.contains("WinRtProjectionIntrinsic.callUnit("))
            assertTrue(contents.contains("\"Int32,RawAddress,RawAddress,RawAddress\""))
            assertTrue(contents.contains("\"Int32,RawAddress,Int32,RawAddress,RawAddress\""))
            assertTrue(contents.contains("localPoints.size"))
            assertTrue(contents.contains("__localPointsArrayData"))
            assertTrue(contents.contains("roundingMode.abiValue"))
            assertTrue(contents.contains("Array(__arrayLength) { __index ->"))
        }
        assertTrue(
            contents.contains("PointInt32.Metadata.fromAbi(") ||
                contents.contains("_iCoordinateConverterProjection"),
        )
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_applies_winui_bindable_collection_mappings() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IBindableItemsView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("87654321-2222-3333-4444-555555555555"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Microsoft.UI.Xaml.Interop.IBindableIterable",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "snapshot",
                                    returnTypeName = "Microsoft.UI.Xaml.Interop.IBindableVector",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Items",
                                    typeName = "Microsoft.UI.Xaml.Interop.IBindableVector",
                                    getterMethodName = "get_Items",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val file = KotlinProjectionGenerator().generate(model).single()

        assertTrue(file.contents, file.contents.contains("public interface IBindableItemsView : Iterable<Any?>"))
        assertTrue(file.contents, file.contents.contains("fun snapshot(): MutableList<Any?>"))
        assertTrue(file.contents, file.contents.contains("val items: MutableList<Any?>"))
    }

    @Test
    fun generator_projects_read_only_iterable_and_vector_view_runtime_surfaces() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555551"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IVectorView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-3333-4444-555555555552"),
                            genericParameterCount = 1,
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameList",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameIterable",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameIterable",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameIterable",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameList",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameList",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameList",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val files = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val iterableContents = files.getValue("NameIterable.kt").contents
        val listContents = files.getValue("NameList.kt").contents

        assertTrue(iterableContents, iterableContents.contains("Iterable<String>,"))
        assertTrue(iterableContents, iterableContents.contains("override fun iterator(): Iterator<String>"))
        if (iterableContents.contains("_iNameIterableProjection")) {
            assertTrue(iterableContents, iterableContents.contains("_iNameIterableProjection.iterator()"))
        } else {
            assertTrue(
                iterableContents,
                iterableContents.contains("IIterable.Metadata.FIRST_SLOT") ||
                    iterableContents.contains("WinRtIterableProjection.fromAbi"),
            )
            if (!iterableContents.contains("WinRtIterableProjection.fromAbi")) {
                assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
                assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.HASCURRENT_GETTER_SLOT"))
                assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.MOVENEXT_SLOT"))
            }
        }

        assertTrue(listContents, listContents.contains("List<String>,"))
        assertTrue(listContents, listContents.contains("override val size: Int"))
        assertTrue(listContents, listContents.contains("override fun `get`(index: Int): String"))
        assertTrue(listContents, listContents.contains("private val _iVectorView: IUnknownReference"))
        assertTrue(listContents, listContents.contains("WinRtReadOnlyListProjection.fromAbi(PlatformAbi.fromRawComPtr(_iVectorView.pointer)"))
    }

    @Test
    fun generator_projects_read_only_map_view_runtime_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMapView<String, Int>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameMap",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameMap",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val mapContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("NameMap.kt")
            .contents

        assertTrue(mapContents, mapContents.contains("Map<String, Int>,"))
        assertTrue(mapContents, mapContents.contains("AbstractMap"))
        assertTrue(mapContents, mapContents.contains("IMapView.Metadata.LOOKUP_SLOT"))
        assertTrue(mapContents, mapContents.contains("IMapView.Metadata.HASKEY_SLOT"))
        assertTrue(mapContents, mapContents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(mapContents, mapContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
    }

    @Test
    fun generator_substitutes_generic_collection_closure_arguments() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IKeyValuePair",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            genericParameterCount = 2,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("55555555-5555-5555-5555-555555555555"),
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<T0>",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("GetAt", "T0", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition("SetAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"), WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("InsertAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"), WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("RemoveAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition("Append", "Unit", parameters = listOf(WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("66666666-6666-6666-6666-666666666666"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Lookup", "T1", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("HasKey", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Insert", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"), WinRtParameterDefinition("value", "T1"))),
                                WinRtMethodDefinition("Remove", "Unit", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMapView",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IObservableVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("77777777-7777-7777-7777-777777777777"),
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVector<T0>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IObservableMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("88888888-8888-8888-8888-888888888888"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMap<T0, T1>",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IObjectMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-4444-4444-4444-444444444444"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMapView<String, System.Object>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ObjectMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IObjectMap",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IObjectMap",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val mapViewInterfaceContents = filesByName.getValue("IMapView.kt").contents
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.contains("public interface IMapView<T0, T1>"))
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.contains("private class NativeProjection<T0, T1>("))
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.containsIgnoringWhitespace("Iterable<Map.Entry<T0, T1>>"))
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.contains("WinRtReferenceValueAdapters.genericParameter<T1>(\"T1\")"))
        assertFalse(mapViewInterfaceContents, mapViewInterfaceContents.contains("import T0"))
        assertFalse(mapViewInterfaceContents, mapViewInterfaceContents.contains("import T1"))

        val iterableInterfaceContents = filesByName.getValue("IIterable.kt").contents
        assertTrue(iterableInterfaceContents, iterableInterfaceContents.contains("public interface IIterable<T0>"))
        assertFalse(iterableInterfaceContents, iterableInterfaceContents.contains("import T0"))

        val observableVectorContents = filesByName.getValue("IObservableVector.kt").contents
        assertTrue(observableVectorContents, observableVectorContents.contains("MutableList<T0>"))
        assertTrue(observableVectorContents, observableVectorContents.contains("private val _iVector: IUnknownReference"))
        assertTrue(observableVectorContents, observableVectorContents.contains("nativeObject.queryInterface(IVector.Metadata.IID)"))
        assertTrue(observableVectorContents, observableVectorContents.contains("private val __iObservableVectorVectorCollection"))
        assertTrue(observableVectorContents, observableVectorContents.contains("WinRtListProjection.fromAbi(PlatformAbi.fromRawComPtr(_iVector.pointer)"))
        assertFalse(observableVectorContents, observableVectorContents.contains("WinRtListProjection.fromAbi(PlatformAbi.fromRawComPtr(nativeObject.pointer)"))
        assertTrue(observableVectorContents, observableVectorContents.contains("override fun `set`(index: Int, element: T0): T0"))
        assertTrue(observableVectorContents, observableVectorContents.contains("__iObservableVectorVectorCollection.set(index"))

        val observableMapContents = filesByName.getValue("IObservableMap.kt").contents
        assertTrue(observableMapContents, observableMapContents.contains("MutableMap<T0, T1>"))
        assertTrue(observableMapContents, observableMapContents.contains("private val _iMap: IUnknownReference"))
        assertTrue(observableMapContents, observableMapContents.contains("nativeObject.queryInterface(IMap.Metadata.IID)"))
        assertTrue(observableMapContents, observableMapContents.contains("private val __iObservableMapMapCollection"))
        assertTrue(observableMapContents, observableMapContents.contains("WinRtDictionaryProjection.fromAbi(PlatformAbi.fromRawComPtr(_iMap.pointer)"))
        assertFalse(observableMapContents, observableMapContents.contains("WinRtDictionaryProjection.fromAbi(PlatformAbi.fromRawComPtr(nativeObject.pointer)"))
        assertTrue(observableMapContents, observableMapContents.contains("override fun put(key: T0,"))
        assertTrue(observableMapContents, observableMapContents.contains("__iObservableMapMapCollection.put(key, value)"))

        val mapContents = filesByName
            .getValue("ObjectMap.kt")
            .contents

        assertTrue(mapContents, mapContents.contains("Map<String, Any?>,"))
        assertTrue(mapContents, mapContents.contains("Map.Entry<String, Any?>"))
        assertTrue(mapContents, mapContents.contains("winRtKeyValuePairAdapter(WinRtReferenceValueAdapters.string"))
        assertFalse(mapContents, mapContents.contains("T0"))
        assertFalse(mapContents, mapContents.contains("T1"))
        assertFalse(mapContents, mapContents.contains("Unsupported"))
    }

    @Test
    fun generator_suppresses_redundant_collection_surfaces_for_runtime_class_mappings() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IKeyValuePair",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            genericParameterCount = 2,
                            properties = listOf(
                                WinRtPropertyDefinition("Key", "T0", getterMethodName = "get_Key"),
                                WinRtPropertyDefinition("Value", "T1", getterMethodName = "get_Value"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                            methods = listOf(WinRtMethodDefinition("First", "Windows.Foundation.Collections.IIterator<T0>")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterable<T0>")),
                            methods = listOf(
                                WinRtMethodDefinition("GetAt", "T0", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition("SetAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"), WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("InsertAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"), WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("RemoveAt", "Unit", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition("Append", "Unit", parameters = listOf(WinRtParameterDefinition("value", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("44444444-4444-4444-4444-444444444444"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Lookup", "T1", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("HasKey", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Insert", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"), WinRtParameterDefinition("value", "T1"))),
                                WinRtMethodDefinition("Remove", "Unit", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("55555555-5555-5555-5555-555555555555"),
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IVector<String>")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringVector",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringVector",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringVector", isDefault = true)),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringIntMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("66666666-6666-6666-6666-666666666666"),
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IMap<String, Int>")),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringIntMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringIntMap",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringIntMap", isDefault = true)),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator().generate(model).associateBy { it.relativePath.substringAfterLast('/') }
        val vectorContents = filesByName.getValue("StringVector.kt").contents
        val mapContents = filesByName.getValue("StringIntMap.kt").contents

        assertTrue(vectorContents, vectorContents.contains("MutableList<String>,"))
        assertTrue(vectorContents, vectorContents.contains("__iStringVectorVectorCollection"))
        assertFalse(vectorContents, vectorContents.contains("__iStringVectorIterableCollection"))
        assertFalse(vectorContents, vectorContents.contains("Iterable<String>,"))
        assertFalse(vectorContents, vectorContents.contains("fun first("))
        assertFalse(vectorContents, vectorContents.contains("fun getAt("))
        assertFalse(vectorContents, vectorContents.contains("public fun clear("))
        assertFalse(vectorContents, vectorContents.contains("public val size"))

        assertTrue(mapContents, mapContents.contains("MutableMap<String, Int>,"))
        assertTrue(mapContents, mapContents.contains("__iStringIntMapMapCollection"))
        assertFalse(mapContents, mapContents.contains("__iStringIntMapIterableCollection"))
        assertTrue(mapContents, mapContents.contains("Iterable<Map.Entry<String, Int>>,"))
        assertFalse(mapContents, mapContents.contains("fun first("))
        assertFalse(mapContents, mapContents.contains("fun lookup("))
        assertFalse(mapContents, mapContents.contains("fun hasKey("))
        assertFalse(mapContents, mapContents.contains("public fun remove(key: String)"))
        assertFalse(mapContents, mapContents.contains("public val size"))
    }

    @Test
    fun generator_keeps_custom_members_named_like_collection_abi_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555601"),
                            genericParameterCount = 1,
                            methods = listOf(WinRtMethodDefinition("First", "Windows.Foundation.Collections.IIterator<T0>")),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IRemovableGroup",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555602"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterable<String>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Remove", "Unit", parameters = listOf(WinRtParameterDefinition("value", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "RemovableGroup",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IRemovableGroup",
                            implementedInterfaces = listOf(WinRtInterfaceImplementationDefinition("Sample.Foundation.IRemovableGroup", isDefault = true)),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("RemovableGroup.kt")
            .contents

        assertTrue(contents, contents.contains("Iterable<String>,"))
        assertTrue(contents, contents.contains("override fun remove(`value`: String)"))
        assertFalse(contents, contents.contains("override fun first("))
    }

    @Test
    fun generator_emits_cswinrt_writer_support_handoffs_when_enabled() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "EventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555551"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "System.Object"),
                                        WinRtParameterDefinition("args", "T0"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "TypedEventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            genericParameterCount = 2,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("sender", "T0"),
                                        WinRtParameterDefinition("args", "T1"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IReference",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555552"),
                            genericParameterCount = 1,
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Value",
                                    typeName = "T0",
                                    getterMethodName = "get_Value",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555553"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "EchoObject",
                                    returnTypeName = "System.Object",
                                    parameters = listOf(
                                        WinRtParameterDefinition("input", "System.Object"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "EchoWidget",
                                    returnTypeName = "Sample.Foundation.Widget",
                                    parameters = listOf(
                                        WinRtParameterDefinition("input", "Sample.Foundation.Widget"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "RoundTripPoint",
                                    returnTypeName = "Sample.Foundation.WidgetPoint",
                                    parameters = listOf(
                                        WinRtParameterDefinition("point", "Sample.Foundation.WidgetPoint"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "RoundTripHandler",
                                    returnTypeName = "Sample.Foundation.WidgetHandler",
                                    parameters = listOf(
                                        WinRtParameterDefinition("handler", "Sample.Foundation.WidgetHandler"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "SumValues",
                                    returnTypeName = "Int",
                                    parameters = listOf(
                                        WinRtParameterDefinition("values", "Array<Int>", isInParameter = true),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "RoundTripNames",
                                    returnTypeName = "Array<String>",
                                    parameters = listOf(
                                        WinRtParameterDefinition("names", "Array<String>", isInParameter = true),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "RoundTripGeneric",
                                    returnTypeName = "M0",
                                    genericParameterCount = 1,
                                    genericParameters = listOf(WinRtGenericParameterDefinition("M0", 0)),
                                    parameters = listOf(
                                        WinRtParameterDefinition("value", "M0"),
                                    ),
                                ),
                                WinRtMethodDefinition(
                                    name = "RoundTripGenericArray",
                                    returnTypeName = "Array<M0>",
                                    genericParameterCount = 1,
                                    genericParameters = listOf(WinRtGenericParameterDefinition("M0", 0)),
                                    parameters = listOf(
                                        WinRtParameterDefinition("values", "Array<M0>", isInParameter = true),
                                    ),
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Changed",
                                    delegateTypeName = "Windows.Foundation.EventHandler<Int>",
                                    addMethodName = "add_Changed",
                                    removeMethodName = "remove_Changed",
                                ),
                                WinRtEventDefinition(
                                    name = "Ticked",
                                    delegateTypeName = "Sample.Foundation.WidgetHandler",
                                    addMethodName = "add_Ticked",
                                    removeMethodName = "remove_Ticked",
                                ),
                                WinRtEventDefinition(
                                    name = "Typed",
                                    delegateTypeName = "Windows.Foundation.TypedEventHandler<Sample.Foundation.Widget,Int>",
                                    addMethodName = "add_Typed",
                                    removeMethodName = "remove_Typed",
                                ),
                                WinRtEventDefinition(
                                    name = "MapChanged",
                                    delegateTypeName = "Windows.Foundation.Collections.MapChangedEventHandler<String,Int>",
                                    addMethodName = "add_MapChanged",
                                    removeMethodName = "remove_MapChanged",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetPoint",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition(name = "X", typeName = "Float"),
                                WinRtFieldDefinition(name = "Y", typeName = "Float"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("value", "Int"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidget",
                                    isDefault = true,
                                    isOverridable = true,
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "boxed",
                                    returnTypeName = "Windows.Foundation.IReference<Int>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateInstance",
                                    returnTypeName = "Sample.Foundation.Widget",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IKeyValuePair",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555550"),
                            genericParameterCount = 2,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-55555555554f"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-55555555554e"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<Windows.Foundation.Collections.IKeyValuePair<T0, T1>>",
                                ),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition("Lookup", "T1", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("HasKey", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Insert", "Boolean", parameters = listOf(WinRtParameterDefinition("key", "T0"), WinRtParameterDefinition("value", "T1"))),
                                WinRtMethodDefinition("Remove", "Unit", parameters = listOf(WinRtParameterDefinition("key", "T0"))),
                                WinRtMethodDefinition("Clear", "Unit"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("Size", "UInt", getterMethodName = "get_Size"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IObservableMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            genericParameterCount = 2,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMap<T0, T1>",
                                ),
                            ),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "MapChanged",
                                    delegateTypeName = "Windows.Foundation.Collections.MapChangedEventHandler<T0,T1>",
                                    addMethodName = "add_MapChanged",
                                    removeMethodName = "remove_MapChanged",
                                    addMethodRowId = 10,
                                    removeMethodRowId = 11,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IPropertySet",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-55555555555a"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IObservableMap<String, System.Object>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMapChangedEventArgs",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                            genericParameterCount = 1,
                            properties = listOf(
                                WinRtPropertyDefinition("CollectionChange", "Windows.Foundation.Collections.CollectionChange", getterMethodName = "get_CollectionChange"),
                                WinRtPropertyDefinition("Key", "T0", getterMethodName = "get_Key"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "CollectionChange",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Reset", 0u),
                                WinRtEnumMemberDefinition("ItemInserted", 1u),
                                WinRtEnumMemberDefinition("ItemRemoved", 2u),
                                WinRtEnumMemberDefinition("ItemChanged", 3u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "MapChangedEventHandler",
                            kind = WinRtTypeKind.Delegate,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            genericParameterCount = 2,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "Invoke",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "sender",
                                            "Windows.Foundation.Collections.IObservableMap<T0,T1>",
                                        ),
                                        WinRtParameterDefinition(
                                            "event",
                                            "Windows.Foundation.Collections.IMapChangedEventArgs<T0>",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Windows.UI.Core",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.UI.Core",
                            name = "ICoreDispatcher",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555560"),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "AcceleratorKeyActivated",
                                    delegateTypeName = "Windows.Foundation.TypedEventHandler<Windows.UI.Core.ICoreDispatcher,Windows.UI.Core.AcceleratorKeyEventArgs>",
                                    addMethodName = "add_AcceleratorKeyActivated",
                                    removeMethodName = "remove_AcceleratorKeyActivated",
                                    addMethodRowId = 12,
                                    removeMethodRowId = 13,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.UI.Core",
                            name = "AcceleratorKeyEventArgs",
                            kind = WinRtTypeKind.RuntimeClass,
                            iid = Guid("11111111-2222-3333-4444-555555555564"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Microsoft.UI.Dispatching",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Dispatching",
                            name = "IDispatcherQueueTimer",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555565"),
                            events = listOf(
                                WinRtEventDefinition(
                                    name = "Tick",
                                    delegateTypeName = "Windows.Foundation.TypedEventHandler<Microsoft.UI.Dispatching.DispatcherQueueTimer,System.Object>",
                                    addMethodName = "add_Tick",
                                    removeMethodName = "remove_Tick",
                                    addMethodRowId = 14,
                                    removeMethodRowId = 15,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Dispatching",
                            name = "DispatcherQueueTimer",
                            kind = WinRtTypeKind.RuntimeClass,
                            iid = Guid("11111111-2222-3333-4444-555555555566"),
                            defaultInterfaceName = "Microsoft.UI.Dispatching.IDispatcherQueueTimer",
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(
            emitSupportFiles = true,
            projectionContext = WinRtMetadataProjectionContext(sources = emptyList(), component = true),
        )
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val compilerSupportManifest = filesByName.getValue("compiler-support.tsv").contents
        assertTrue(compilerSupportManifest.contains("kind\tclassName\tsourceFile\tentries"))
        assertTrue(compilerSupportManifest.contains("projection-registrar\tio.github.composefluent.winrt.runtime.WinRtProjectionSupportIntrinsic\tprojection-registrar.tsv"))
        assertFalse(compilerSupportManifest.contains("event-source\t"))
        assertTrue(compilerSupportManifest.contains("generic-type-instantiation\tio.github.composefluent.winrt.projections.support.WinRTGenericTypeInstantiations\tgeneric-instantiations.tsv"))
        assertTrue(compilerSupportManifest.contains("generic-abi-registry\tio.github.composefluent.winrt.runtime.WinRtGenericAbiSupportIntrinsic\tgeneric-abi-registry.tsv"))

        val typeShapeDescriptors = filesByName.getValue("type-shape-descriptors.tsv").contents
        assertTrue(typeShapeDescriptors.contains("projectedTypeName\tkey\tvalue"))
        assertTrue(typeShapeDescriptors.contains("\tWRITES_ABI_DECLARATION\t"))
        assertFalse(
            filesByName.values
                .filter { file -> file.relativePath.endsWith(".kt") }
                .any { file -> file.contents.contains("WRITES_ABI_DECLARATION") },
        )

        val genericAbiRegistry = filesByName.getValue("generic-abi-registry.tsv").contents
        val genericAbiRegistrySource = filesByName.getValue("WinRTGenericAbiRegistry.kt").contents
        assertTrue(genericAbiRegistry.contains("_get_Value_Int"))
        assertTrue(genericAbiRegistry.contains("Windows.Foundation.IReference<Int>"))
        assertTrue(genericAbiRegistrySource.contains("fun registerAbiDelegates"))
        assertTrue(genericAbiRegistrySource.contains("WinRtGenericAbiSupportIntrinsic"))
        assertFalse(genericAbiRegistrySource.contains("Class.forName"))
        assertFalse(genericAbiRegistrySource.contains("_get_Value_Int"))
        assertFalse(genericAbiRegistrySource.contains("GENERIC_ABI_DELEGATES"))
        val genericTypeInstantiations = filesByName.getValue("WinRTGenericTypeInstantiations.kt").contents
        val genericInstantiations = filesByName.getValue("generic-instantiations.tsv").contents
        assertTrue(genericInstantiations.contains("className\tsourceType\tisDelegate\trcwFunctions\tvtableFunctions\tpropertyAccessors\tgenericReturnOnlyRcwFunctions\tprojectedGenericFallbacks\tdependencies"))
        assertTrue(genericInstantiations.contains("Windows_Foundation_IReference_Int"))
        assertTrue(genericInstantiations.contains("\tfalse\t"))
        assertFalse(genericTypeInstantiations.contains("Windows_Foundation_IReference_Int"))
        assertTrue(genericTypeInstantiations.contains("fun initializeAll"))
        assertTrue(genericTypeInstantiations.contains("fun initializeBySourceType(sourceType: String)"))
        assertTrue(genericTypeInstantiations.contains("fun initializeEntry(entry: GenericTypeInstantiationEntry)"))
        assertTrue(genericTypeInstantiations.contains("entry.dependencies.forEach(::initializeBySourceType)"))
        assertFalse(genericTypeInstantiations.contains("GenericTypeInstantiationRuntimeBinding"))
        assertFalse(genericTypeInstantiations.contains("installRuntimeBinding"))
        assertFalse(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime"))
        assertFalse(genericTypeInstantiations.contains("genericReturnOnlyRcwFunctions = listOf(\"get_Value\")"))
        assertFalse(genericTypeInstantiations.contains("projectedGenericFallbacks = listOf(\"get_Value\")"))
        assertFalse(genericTypeInstantiations.contains("runtimeBinding"))
        assertFalse(genericTypeInstantiations.contains("registerGenericInstantiation"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationSupportIntrinsic.initializeAll()"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationSupportIntrinsic.initializeBySourceType(sourceType)"))
        assertFalse(genericTypeInstantiations.contains("WinRTGenericTypeInstantiationRegistry"))
        assertFalse(genericTypeInstantiations.contains("Class.forName"))
        val eventProjectionHelpers = filesByName.getValue("WinRTEventProjectionHelpers.kt").contents
        assertFalse(filesByName.containsKey("event-sources.tsv"))
        assertTrue(eventProjectionHelpers.contains("@file:Suppress("))
        assertTrue(eventProjectionHelpers.contains("\"USELESS_IS_CHECK\""))
        assertTrue(eventProjectionHelpers.contains("\"USELESS_CAST\""))
        assertFalse(eventProjectionHelpers.contains("EVENT_SOURCES: List<EventSourceEntry>"))
        assertFalse(eventProjectionHelpers.contains("eventSourceEntriesChunk000"))
        assertFalse(eventProjectionHelpers.contains("internal object WinRTEventProjectionRegistry"))
        assertFalse(eventProjectionHelpers.contains("fun register()"))
        assertFalse(eventProjectionHelpers.contains("eventSourceFactoryFor"))
        assertFalse(eventProjectionHelpers.contains("eventHandlerEventSourceFactoryFor"))
        assertFalse(eventProjectionHelpers.contains("WinRtEventSourceFactory?"))
        assertTrue(eventProjectionHelpers.contains("EventHandlerEventSource<"))
        assertTrue(eventProjectionHelpers.contains("argsKind = "))
        assertFalse(eventProjectionHelpers.contains("\"_EventSource_Windows_Foundation_TypedEventHandler"))
        assertTrue(eventProjectionHelpers.contains("internal class _EventSource_Windows_Foundation_TypedEventHandler"))
        assertTrue(eventProjectionHelpers.contains("EventSource<TypedEventHandler<Widget, Int>>"))
        assertFalse(eventProjectionHelpers.contains("usesSharedEventHandlerSource = true,\n    genericArgumentTypeNames = listOf(\"Sample.Foundation.Widget\", \"Int\")"))
        assertTrue(eventProjectionHelpers.contains("internal class _EventSource_Sample_Foundation_WidgetHandler"))
        assertTrue(eventProjectionHelpers.contains("EventSource<WidgetHandler>"))
        assertTrue(eventProjectionHelpers.contains("handler.invoke("))
        assertFalse(eventProjectionHelpers.contains("\"_EventSource_Sample_Foundation_WidgetHandler\" ->"))
        assertTrue(eventProjectionHelpers.contains("_EventSource_Sample_Foundation_WidgetHandler(objectReference, vtableIndexForAddHandler)"))
        assertTrue(eventProjectionHelpers.contains("internal class _EventSource_Windows_Foundation_Collections_MapChangedEventHandler"))
        assertTrue(
            eventProjectionHelpers.contains(
                "internal object ${
                    eventSourceOwnerHelperName(
                        ownerType = "Windows.Foundation.Collections.IObservableMap",
                        eventType = "Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>",
                    )
                }",
            ),
        )
        assertTrue(eventProjectionHelpers.contains("fun ${eventSourceCreateFunctionName("Windows.Foundation.Collections.MapChangedEventHandler<T0, T1>", "Windows.Foundation.Collections.IObservableMap")}"))
        assertFalse(eventProjectionHelpers.contains("fun ${eventSourceCreateFunctionName("Windows.Foundation.Collections.MapChangedEventHandler<String, System.Object>", "Windows.Foundation.Collections.IObservableMap")}"))
        assertTrue(eventProjectionHelpers.contains("IObservableMap.Metadata.wrap"))
        assertTrue(eventProjectionHelpers.contains("IMapChangedEventArgs.Metadata.wrap"))
        if (eventProjectionHelpers.contains("Windows.UI.Core.ICoreDispatcher")) {
            assertTrue(
                eventProjectionHelpers.contains(
                    "internal object ${
                        eventSourceOwnerHelperName(
                            ownerType = "Windows.UI.Core.ICoreDispatcher",
                            eventType = "Windows.Foundation.TypedEventHandler<Windows.UI.Core.ICoreDispatcher,Windows.UI.Core.AcceleratorKeyEventArgs>",
                        )
                    }",
                ),
            )
            assertTrue(eventProjectionHelpers.contains("fun ${eventSourceCreateFunctionName("Windows.Foundation.TypedEventHandler<Windows.UI.Core.ICoreDispatcher, Windows.UI.Core.AcceleratorKeyEventArgs>", "Windows.UI.Core.ICoreDispatcher")}"))
        }
        assertFalse(eventProjectionHelpers.contains("fun installEventSources()"))
        assertFalse(eventProjectionHelpers.contains("WinRTEventProjectionRegistry"))
        assertFalse(eventProjectionHelpers.contains("fun createEventSource("))
        assertFalse(eventProjectionHelpers.contains("WinRtEventSourceRuntime.createEventSource("))
        assertTrue(eventProjectionHelpers.contains("internal object WinRTEventProjectionHelper_"))
        assertTrue(eventProjectionHelpers.contains("fun createEventSource_"))
        assertFalse(eventProjectionHelpers.contains("No WinRT event source registered"))
        assertFalse(eventProjectionHelpers.contains("ownerType = \"Sample.Foundation.IWidget\""))
        assertFalse(eventProjectionHelpers.contains("install: Function1"))
        assertFalse(eventProjectionHelpers.contains("install:"))
        assertFalse(eventProjectionHelpers.contains("EventSourceEntry"))
        assertFalse(filesByName.containsKey("WinRTAbiImplementationPlan.kt"))
        assertFalse(filesByName.containsKey("WinRTTypeShapeWriterPlan.kt"))
        val authoringMetadataMappingHelper = filesByName.getValue("AuthoringMetadataTypeMappingHelper.kt").contents
        assertTrue(authoringMetadataMappingHelper.contains("object AuthoringMetadataTypeMappingHelper"))
        assertTrue(authoringMetadataMappingHelper.contains("Sample.Foundation.Widget->ABI.Sample.Foundation.Widget"))
        assertTrue(authoringMetadataMappingHelper.contains("fun initialize()"))
        assertTrue(authoringMetadataMappingHelper.contains("ComWrappersSupport.registerAuthoringMetadataTypeMappings"))
        assertTrue(authoringMetadataMappingHelper.contains("fun getMetadataTypeMapping("))
        assertTrue(authoringMetadataMappingHelper.contains("projectedTypeName: String"))
        val authoringWrapperPlan = filesByName.getValue("WinRTAuthoringWrapperPlan.kt").contents
        assertTrue(authoringWrapperPlan.contains("data class AuthoringWrapperEntry"))
        assertTrue(authoringWrapperPlan.contains("projectedTypeName = \"Sample.Foundation.Widget\""))
        assertTrue(authoringWrapperPlan.contains("metadataTypeName = \"ABI.Sample.Foundation.Widget\""))
        assertTrue(authoringWrapperPlan.contains("defaultInterfaceName = \"Sample.Foundation.IWidget\""))
        assertTrue(authoringWrapperPlan.contains("implementedInterfaceNames = listOf(\"Sample.Foundation.IWidget\")"))
        assertTrue(authoringWrapperPlan.contains("factoryMemberNames = listOf(\"Sample.Foundation.IWidgetFactory\")"))
        assertTrue(authoringWrapperPlan.contains("fun wrapperForProjectedType("))
        assertTrue(authoringWrapperPlan.contains("projectedTypeName: String"))
        assertTrue(authoringWrapperPlan.contains("AuthoringWrapperEntry?"))
        val authoringAbiClassPlan = filesByName.getValue("WinRTAuthoringAbiClassPlan.kt").contents
        assertTrue(authoringAbiClassPlan.contains("data class AuthoringAbiClassEntry"))
        assertTrue(authoringAbiClassPlan.contains("projectedTypeName = \"Sample.Foundation.Widget\""))
        assertTrue(authoringAbiClassPlan.contains("abiTypeName = \"ABI.Sample.Foundation.Widget\""))
        assertTrue(authoringAbiClassPlan.contains("defaultInterfaceName = \"Sample.Foundation.IWidget\""))
        assertTrue(authoringAbiClassPlan.contains("marshalerFamily = \"MarshalInterface\""))
        assertTrue(authoringAbiClassPlan.contains("ABI_OPERATIONS"))
        assertTrue(authoringAbiClassPlan.contains("\"CreateMarshaler2\""))
        assertTrue(authoringAbiClassPlan.contains("\"FromManagedArray\""))
        assertTrue(authoringAbiClassPlan.contains("fun abiClassForProjectedType("))
        assertTrue(authoringAbiClassPlan.contains("projectedTypeName: String"))
        assertTrue(authoringAbiClassPlan.contains("AuthoringAbiClassEntry?"))
        val authoringWrappers = filesByName.getValue("WinRTAuthoringWrappers.kt").contents
        assertTrue(authoringWrappers.contains("object _AuthoringWrapper_Sample_Foundation_Widget"))
        assertTrue(authoringWrappers.contains("projectedTypeName: String = \"Sample.Foundation.Widget\""))
        assertTrue(authoringWrappers.contains("metadataTypeName: String = \"ABI.Sample.Foundation.Widget\""))
        assertTrue(authoringWrappers.contains("defaultInterfaceName: String? = \"Sample.Foundation.IWidget\""))
        assertTrue(authoringWrappers.contains("fun wrap(instance: IInspectableReference): Widget"))
        assertTrue(authoringWrappers.contains("fun fromAbi(pointer: RawAddress): Widget?"))
        assertTrue(authoringWrappers.contains("return wrap(IInspectableReference(PlatformAbi.toRawComPtr(pointer)))"))
        assertTrue(authoringWrappers.contains("object WinRTAuthoringWrappers"))
        assertTrue(authoringWrappers.contains("fun fromAbi(runtimeClassName: String, pointer: RawAddress): Any?"))
        val authoringAbiClasses = filesByName.getValue("WinRTAuthoringAbiClasses.kt").contents
        assertTrue(authoringAbiClasses.contains("object _AuthoringAbiClass_Sample_Foundation_Widget"))
        assertTrue(authoringAbiClasses.contains("defaultInterfaceId: Guid = IWidget.Metadata.IID"))
        assertTrue(authoringAbiClasses.contains("fun CreateMarshaler("))
        assertTrue(authoringAbiClasses.contains("fun CreateMarshaler2("))
        assertTrue(authoringAbiClasses.contains("ComWrappersSupport.tryUnwrapObject(it) ?:"))
        assertTrue(authoringAbiClasses.contains("ComWrappersSupport.createCCWForObject"))
        assertTrue(authoringAbiClasses.contains("interfaceId"))
        assertTrue(authoringAbiClasses.contains("fun GetAbi("))
        assertTrue(authoringAbiClasses.contains("fun FromAbi("))
        assertTrue(authoringAbiClasses.contains("_AuthoringWrapper_Sample_Foundation_Widget.fromAbi(pointer)"))
        assertTrue(authoringAbiClasses.contains("fun DisposeAbi(pointer: RawAddress)"))
        assertTrue(authoringAbiClasses.contains("private fun arrayMarshaler(): Marshaler<Widget>"))
        assertTrue(authoringAbiClasses.contains("Marshaler.interfaceType("))
        assertTrue(authoringAbiClasses.contains("WinRtTypeHandle(projectedTypeName, defaultInterfaceId)"))
        assertTrue(authoringAbiClasses.contains("_AuthoringWrapper_Sample_Foundation_Widget.fromAbi(PlatformAbi.fromRawComPtr(value.nativeObject.pointer))"))
        assertTrue(authoringAbiClasses.contains("fun CreateMarshalerArray("))
        assertTrue(authoringAbiClasses.contains("createMarshalerArray(values)"))
        assertTrue(authoringAbiClasses.contains("fun GetAbiArray(marshaler: WinRtAbiArray?)"))
        assertTrue(authoringAbiClasses.contains("fun FromAbiArray("))
        assertTrue(authoringAbiClasses.contains("fromAbiArray(length, data)"))
        assertTrue(authoringAbiClasses.contains("fun CopyAbiArray("))
        assertTrue(authoringAbiClasses.contains("values[index] = value"))
        assertTrue(authoringAbiClasses.contains("fun FromManagedArray("))
        assertTrue(authoringAbiClasses.contains("fromManagedArray(values)"))
        assertTrue(authoringAbiClasses.contains("fun DisposeMarshalerArray("))
        assertTrue(authoringAbiClasses.contains("fun DisposeAbiArray("))
        assertFalse(authoringAbiClasses.contains("unsupportedAuthoringAbiArrayOperation"))
        assertTrue(authoringAbiClasses.contains("object WinRTAuthoringAbiClasses"))
        val customQiPlan = filesByName.getValue("WinRTAuthoringCustomQueryInterfacePlan.kt").contents
        assertTrue(customQiPlan.contains("data class AuthoringCustomQueryInterfaceEntry"))
        assertTrue(customQiPlan.contains("projectedTypeName = \"Sample.Foundation.Widget\""))
        assertTrue(customQiPlan.contains("overridableInterfaceNames = listOf(\"Sample.Foundation.IWidget\")"))
        assertTrue(customQiPlan.contains("notHandledInterfaceNames = NOT_HANDLED_INTERFACE_NAMES"))
        assertTrue(customQiPlan.contains("IInspectable"))
        assertTrue(customQiPlan.contains("IWeakReferenceSource"))
        assertTrue(customQiPlan.contains("forwardTarget = \"NativeObject.TryAs\""))
        assertTrue(customQiPlan.contains("fun customQueryInterfaceForProjectedType("))
        assertTrue(customQiPlan.contains("AuthoringCustomQueryInterfaceEntry?"))
        val activationFactoryPlan = filesByName.getValue("WinRTAuthoringActivationFactoryPlan.kt").contents
        assertTrue(activationFactoryPlan.contains("data class AuthoringActivationFactoryEntry"))
        assertTrue(activationFactoryPlan.contains("projectedTypeName = \"Sample.Foundation.Widget\""))
        assertTrue(activationFactoryPlan.contains("serverFactoryTypeName = \"ABI.Sample.Foundation.WidgetServerActivationFactory\""))
        assertTrue(activationFactoryPlan.contains("isActivatable = false"))
        assertTrue(activationFactoryPlan.contains("implementsIActivationFactory = true"))
        assertTrue(activationFactoryPlan.contains("factoryInterfaceNames = listOf(\"Sample.Foundation.IWidgetFactory\")"))
        assertTrue(activationFactoryPlan.contains("activatableFactoryInterfaceNames = listOf(\"Sample.Foundation.IWidgetFactory\")"))
        assertTrue(activationFactoryPlan.contains("staticFactoryInterfaceNames = emptyList()"))
        assertTrue(activationFactoryPlan.contains("activatableFactoryMemberNames = listOf(\"Sample.Foundation.IWidgetFactory.CreateInstance\")"))
        assertTrue(activationFactoryPlan.contains("staticFactoryMemberNames = emptyList()"))
        assertTrue(activationFactoryPlan.contains("composableFactoryMemberNames = emptyList()"))
        assertTrue(activationFactoryPlan.contains("makeMethod = \"MarshalInspectable.CreateMarshaler2(IID.IActivationFactory).Detach\""))
        assertTrue(activationFactoryPlan.contains("activateInstanceBehavior = \"notImplemented\""))
        assertTrue(activationFactoryPlan.contains("runClassConstructorTypeName = \"Sample.Foundation.Widget\""))
        assertTrue(activationFactoryPlan.contains("fun factoryForProjectedType("))
        assertTrue(activationFactoryPlan.contains("fun installActivationFactories("))
        val moduleActivationFactoryPlan = filesByName.getValue("WinRTAuthoringModuleActivationFactoryPlan.kt").contents
        assertTrue(moduleActivationFactoryPlan.contains("data class AuthoringModuleActivationFactoryEntry"))
        assertTrue(moduleActivationFactoryPlan.contains("runtimeClassName = \"Sample.Foundation.Widget\""))
        assertTrue(moduleActivationFactoryPlan.contains("serverFactoryTypeName = \"ABI.Sample.Foundation.WidgetServerActivationFactory\""))
        assertTrue(moduleActivationFactoryPlan.contains("fun entryForRuntimeClassName("))
        assertTrue(moduleActivationFactoryPlan.contains("fun getActivationFactory("))
        assertTrue(moduleActivationFactoryPlan.contains("factory(entry, interfaceId)"))
        assertTrue(moduleActivationFactoryPlan.contains("fallback(runtimeClassName, interfaceId)"))
        assertTrue(moduleActivationFactoryPlan.contains("fun installModuleActivationFactories("))
        assertTrue(moduleActivationFactoryPlan.contains("fun registerModuleActivationFactories("))
        assertTrue(moduleActivationFactoryPlan.contains("ComWrappersSupport.registerAuthoringActivationFactory"))
        assertTrue(moduleActivationFactoryPlan.contains("createFactory(entry)"))
        val serverActivationFactories = filesByName.getValue("WinRTAuthoringServerActivationFactories.kt").contents
        assertTrue(serverActivationFactories.contains("internal class _ServerActivationFactory_Sample_Foundation_Widget"))
        assertTrue(serverActivationFactories.contains("WinRtActivationFactory"))
        assertTrue(serverActivationFactories.contains("override fun activateInstance(): ComObjectReference"))
        assertTrue(serverActivationFactories.contains("does not expose default activation"))
        assertTrue(serverActivationFactories.contains("object WinRTAuthoringServerActivationFactories"))
        assertTrue(serverActivationFactories.contains("WinRTAuthoringModuleActivationFactoryPlan.registerModuleActivationFactories"))
        assertTrue(serverActivationFactories.contains("ComWrappersSupport.createCCWForObject"))
        assertTrue(serverActivationFactories.contains("_ServerActivationFactory_Sample_Foundation_Widget()"))
        assertTrue(serverActivationFactories.contains("IID.IActivationFactory"))
        val hostExports = filesByName.getValue("WinRTAuthoringHostExports.kt").contents
        assertTrue(hostExports.contains("object WinRTAuthoringHostExports"))
        assertTrue(hostExports.contains("WinRTAuthoringServerActivationFactories.register()"))
        assertTrue(hostExports.contains("fun dllGetActivationFactory("))
        assertTrue(hostExports.contains("registerActivationFactories()"))
        assertTrue(hostExports.contains("WinRtAuthoringHostBridge.dllGetActivationFactory(activatableClassId, factoryOut)"))
        assertTrue(hostExports.contains("@JvmStatic") || hostExports.contains("@kotlin.jvm.JvmStatic"))
        assertTrue(hostExports.contains("fun dllGetActivationFactoryAddress(activatableClassId: Long, factoryOut: Long): Int"))
        assertTrue(hostExports.contains("RawAddress(activatableClassId)"))
        assertTrue(hostExports.contains("fun dllCanUnloadNow(): Int"))
        assertTrue(hostExports.contains("WinRtAuthoringHostBridge.dllCanUnloadNow()"))
        assertTrue(hostExports.contains("fun dllCanUnloadNowAddress(): Int"))
        val ccwFactories = filesByName.getValue("WinRTAuthoringCcwFactories.kt").contents
        assertTrue(ccwFactories.contains("object WinRTAuthoringCcwFactories"))
        assertTrue(ccwFactories.contains("ComWrappersSupport.registerCcwFactory(Widget::class)"))
        assertTrue(ccwFactories.contains("createCcwDefinitionForSample_Foundation_Widget(value as Widget)"))
        assertTrue(ccwFactories.contains("queryInterfaceFallback = { obj, requestedInterfaceId ->"))
        assertTrue(ccwFactories.contains("queryInterfaceForSample_Foundation_Widget(obj as Widget, requestedInterfaceId)"))
        assertTrue(ccwFactories.contains("requestedInterfaceId == IID.IInspectable"))
        assertTrue(ccwFactories.contains("val winRtObject = value as? IWinRTObject ?: return null"))
        assertTrue(ccwFactories.contains("winRtObject.nativeObject"))
        assertTrue(ccwFactories.contains("tryQueryInterface(requestedInterfaceId)"))
        assertTrue(ccwFactories.contains("WinRtCcwDefinition"))
        assertTrue(ccwFactories.contains("WinRtInspectableInterfaceDefinition"))
        assertTrue(ccwFactories.contains("interfaceId = IWidget.Metadata.IID"))
        assertTrue(ccwFactories.contains("methods = listOf("))
        assertTrue(ccwFactories.contains("WinRtInspectableMethodDefinition"))
        assertTrue(ccwFactories.contains("signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer)"))
        assertTrue(ccwFactories.contains("val input = WinRtObjectMarshaller.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(ccwFactories.contains("value.echoObject(input)"))
        assertTrue(ccwFactories.contains("WinRtObjectMarshaller.createMarshaler(__result).use"))
        assertTrue(ccwFactories.contains("ComAbiValueKind.Struct(WidgetPoint.Metadata.layout.abiLayout)"))
        assertTrue(ccwFactories.contains("WidgetPoint.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(ccwFactories.contains("value.roundTripPoint(point)"))
        assertTrue(ccwFactories.contains("WidgetPoint.Metadata.copyTo(__result"))
        assertTrue(ccwFactories.contains("WidgetHandler.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(ccwFactories.contains("Authored delegate argument Sample.Foundation.WidgetHandler was null."))
        assertTrue(ccwFactories.contains("value.roundTripHandler(handler)"))
        assertTrue(ccwFactories.contains("ComWrappersSupport.detachCCWForObject(__result"))
        assertTrue(ccwFactories.contains("Guid(\"11111111-2222-3333-4444-555555555555\")"))
        assertTrue(ccwFactories.contains("ComAbiValueKind.Int32"))
        assertTrue(ccwFactories.contains("val values = run {"))
        assertTrue(ccwFactories.contains("val __arrayLength = rawArgs[0] as Int"))
        assertTrue(ccwFactories.contains("Array(__arrayLength) { __index ->"))
        assertTrue(ccwFactories.contains("value.sumValues(values)"))
        assertTrue(ccwFactories.contains("val names = run {"))
        assertTrue(ccwFactories.contains("val __arrayMarshaler = Marshaler.string()"))
        assertTrue(ccwFactories.contains("__arrayMarshaler.fromAbiArray(__arrayLength, __arrayData)"))
        assertTrue(ccwFactories.contains("value.roundTripNames(names)"))
        assertTrue(ccwFactories.contains("val __returnArrayMarshaler = Marshaler.string()"))
        assertTrue(ccwFactories.contains("val __returnArray = __returnArrayMarshaler.createMarshalerArray(__result)"))
        assertTrue(ccwFactories.contains("PlatformAbi.writeInt32(rawArgs[2] as RawAddress, __returnArray?.length ?: 0)"))
        assertTrue(ccwFactories.contains("PlatformAbi.writePointer(rawArgs[3] as RawAddress"))
        assertTrue(ccwFactories.contains("WinRtGenericParameterProjection.fromAbi<M0>(rawArgs[0] as RawAddress)"))
        assertTrue(ccwFactories.contains("value.roundTripGeneric(value)"))
        assertTrue(ccwFactories.contains("WinRtGenericParameterProjection.createReference(__result).use"))
        assertTrue(ccwFactories.contains("__returnReference?.getRefPointer()"))
        assertTrue(ccwFactories.contains("PlatformAbi.nullPointer"))
        assertTrue(ccwFactories.contains("val __arrayMarshaler = Marshaler.genericParameter<M0>()"))
        assertTrue(ccwFactories.contains("value.roundTripGenericArray(values)"))
        assertTrue(ccwFactories.contains("val __returnArrayMarshaler = Marshaler.genericParameter<M0>()"))
        assertTrue(ccwFactories.contains("EventHandler.Metadata.fromAbi(rawArgs[0] as RawAddress)"))
        assertTrue(ccwFactories.contains("value.addChanged(handler)"))
        assertTrue(ccwFactories.contains("EventRegistrationToken.Metadata.copyTo(token, rawArgs[1] as RawAddress)"))
        assertTrue(ccwFactories.contains("value.removeChanged(EventRegistrationToken(rawArgs[0] as Long))"))
        assertTrue(ccwFactories.contains("ExceptionHelpers.hResultFromException(error).value"))
        assertTrue(ccwFactories.contains("defaultInterfaceId = IWidget.Metadata.IID"))
        assertTrue(ccwFactories.contains("runtimeClassName = \"Sample.Foundation.Widget\""))
        assertTrue(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("Windows.Foundation"))
        assertTrue(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("SourceAddition"))
        assertTrue(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("fun installNamespaceAdditions"))
        assertFalse(filesByName.getValue("WinRTNamespaceAdditions.kt").contents.contains("sourceFiles"))
    }

    @Test
    fun generator_projects_mutable_vector_runtime_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVector<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameVector",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameVector",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameVector",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("NameVector.kt")
            .contents

        assertTrue(contents, contents.contains("MutableList<String>,"))
        if (contents.contains("_iNameVectorProjection")) {
            assertTrue(contents, contents.contains("_iNameVectorProjection.set(index, element)"))
        } else {
            assertTrue(
                contents,
                contents.contains("override fun set(index: Int, element: String): String") ||
                    contents.contains("WinRtListProjection.fromAbi"),
            )
            if (!contents.contains("WinRtListProjection.fromAbi")) {
                assertTrue(contents, contents.contains("override fun add(index: Int, element: String)"))
                assertTrue(contents, contents.contains("override fun subList(fromIndex: Int, toIndex: Int): MutableList<String>"))
                assertTrue(contents, contents.contains("AbstractMutableList<String>()"))
            }
        }
        assertFalse(contents, contents.contains("Iterable<String> by __iNameVectorIterableCollection"))
        if (!contents.contains("WinRtListProjection.fromAbi")) {
            assertTrue(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
            assertTrue(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
            assertTrue(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
            assertTrue(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
            assertTrue(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
            assertTrue(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
        }
        assertTrue(contents, contents.contains("REQUIRED_MAPPED_HELPER_PLANS"))
        assertTrue(contents, contents.contains("Windows.Foundation.Collections.IVector<String>|IList|idic"))
        assertTrue(contents, contents.contains("removeGeneric=System.Collections.Generic.IEnumerable<String>"))
    }

    @Test
    fun generator_routes_mutable_vector_unit_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IFloatVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVector<Float>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "FloatVector",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IFloatVector",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IFloatVector",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("FloatVector.kt")
            .contents

        assertTrue(contents.contains("MutableList<Float>,"))
        assertTrue(contents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(contents.contains("\"UInt32,Float\""))
        assertTrue(contents.contains("\"Float\""))
        assertTrue(contents.contains("IVector.Metadata.SETAT_SLOT"))
        assertTrue(contents.contains("IVector.Metadata.INSERTAT_SLOT"))
        assertTrue(contents.contains("IVector.Metadata.APPEND_SLOT"))
        assertFalse(contents.contains("return WinRtProjectionIntrinsic.callUnit("))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_struct_argument_unit_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IEasingFunction",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "EasingFunction",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IEasingFunction",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IEasingFunction",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IKeyFrameAnimation",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "InsertKeyFrame",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("progress", "Float"),
                                        WinRtParameterDefinition("value", "Sample.Foundation.Point"),
                                    ),
                                    methodRowId = 10,
                                ),
                                WinRtMethodDefinition(
                                    name = "InsertKeyFrame",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("progress", "Float"),
                                        WinRtParameterDefinition("value", "Sample.Foundation.Point"),
                                        WinRtParameterDefinition("easingFunction", "Sample.Foundation.EasingFunction"),
                                    ),
                                    methodRowId = 11,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IKeyFrameAnimation.kt")
            .contents

        assertTrue(contents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(contents.contains("\"Float,Struct\""))
        assertTrue(contents.contains("\"Float,Struct,RawAddress\""))
        assertTrue(contents.contains("Point.Metadata"))
        assertTrue(contents.contains("winRtProjectionMarshaler(easingFunction, \"Sample.Foundation.EasingFunction\""))
        assertTrue(contents.contains("Guid(\"11111111-2222-3333-4444-555555555558\")).use {"))
        assertTrue(contents.contains("__easingFunctionProjectionMarshaler ->"))
        assertTrue(contents.contains("__easingFunctionProjectionMarshaler.abi"))
        assertFalse(contents.contains("easingFunction as IWinRTObject"))
        assertFalse(contents.contains("PlatformAbi.allocateBytes"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_projected_object_struct_argument_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Point",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBrush",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555560"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Brush",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IBrush",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IBrush",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBrushFactory",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "CreateBrush",
                                    returnTypeName = "Sample.Foundation.Brush",
                                    parameters = listOf(
                                        WinRtParameterDefinition("offset", "Float"),
                                        WinRtParameterDefinition("origin", "Sample.Foundation.Point"),
                                    ),
                                    methodRowId = 12,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IBrushFactory.kt")
            .contents

        assertTrue(contents.contains("WinRtProjectionIntrinsic.callProjectedRuntimeClass("))
        assertTrue(contents.contains("\"Float,Struct\""))
        assertTrue(contents.contains("Brush.Metadata::wrap"))
        assertTrue(contents.contains("Point.Metadata"))
        assertFalse(contents.contains("PlatformAbi.allocateBytes"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_struct_result_descriptor_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Rect",
                            kind = WinRtTypeKind.Struct,
                            fields = listOf(
                                WinRtFieldDefinition("X", "Float"),
                                WinRtFieldDefinition("Y", "Float"),
                                WinRtFieldDefinition("Width", "Float"),
                                WinRtFieldDefinition("Height", "Float"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ITextBox",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555576"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "GetRectFromCharacterIndex",
                                    returnTypeName = "Sample.Foundation.Rect",
                                    parameters = listOf(
                                        WinRtParameterDefinition("charIndex", "Int"),
                                        WinRtParameterDefinition("trailingEdge", "Boolean"),
                                    ),
                                    methodRowId = 20,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ITextBox.kt")
            .contents

        assertTrue(contents.contains("private class NativeProjection("))
        assertTrue(contents, contents.contains("WinRtProjectionIntrinsic.callStruct("))
        assertTrue(contents.contains("\"Int32,Boolean\""))
        assertTrue(contents.contains("Rect.Metadata"))
        assertTrue(contents.contains("charIndex,"))
        assertTrue(contents.contains("trailingEdge,"))
        assertFalse(contents.contains("PlatformAbi.allocateBytes"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_routes_multi_projected_object_unit_calls_through_projection_intrinsic() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IPath",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Path",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IPath",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IPath",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IEasingFunction",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555563"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "EasingFunction",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IEasingFunction",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IEasingFunction",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IPathKeyFrameAnimation",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555564"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "InsertKeyFrame",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("progress", "Float"),
                                        WinRtParameterDefinition("path", "Sample.Foundation.Path"),
                                        WinRtParameterDefinition("easingFunction", "Sample.Foundation.EasingFunction"),
                                    ),
                                    methodRowId = 13,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IPathKeyFrameAnimation.kt")
            .contents

        assertTrue(contents.contains("WinRtProjectionIntrinsic.callUnit("))
        assertTrue(contents.contains("\"Float,RawAddress,RawAddress\""))
        assertTrue(contents.contains("winRtProjectionMarshaler(path, \"Sample.Foundation.Path\""))
        assertTrue(contents.contains("Guid(\"11111111-2222-3333-4444-555555555562\")).use {"))
        assertTrue(contents.contains("__pathProjectionMarshaler ->"))
        assertTrue(contents.contains("winRtProjectionMarshaler(easingFunction, \"Sample.Foundation.EasingFunction\""))
        assertTrue(contents.contains("Guid(\"11111111-2222-3333-4444-555555555563\")).use {"))
        assertTrue(contents.contains("__easingFunctionProjectionMarshaler ->"))
        assertTrue(contents.contains("__pathProjectionMarshaler.abi"))
        assertTrue(contents.contains("__easingFunctionProjectionMarshaler.abi"))
        assertFalse(contents.contains("path as IWinRTObject"))
        assertFalse(contents.contains("easingFunction as IWinRTObject"))
        assertFalse(contents.contains("ComVtableInvoker.invokeGenericArgs"))
    }

    @Test
    fun generator_uses_vector_interface_cache_for_observable_vector_runtime_collection_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-1111-1111-1111-111111111111"),
                            genericParameterCount = 1,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("22222222-2222-2222-2222-222222222222"),
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IIterable<T0>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IObservableVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("33333333-3333-3333-3333-333333333333"),
                            genericParameterCount = 1,
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IVector<T0>",
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ObjectItems",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Windows.Foundation.Collections.IObservableVector<System.Object>",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IObservableVector<System.Object>",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ObjectItems.kt")
            .contents

        assertTrue(contents, contents.contains("private val _iVector: IUnknownReference"))
        assertTrue(contents, contents.contains("WinRtListProjection.fromAbi(PlatformAbi.fromRawComPtr(_iVector.pointer)"))
        assertFalse(contents, contents.contains("WinRtListProjection.fromAbi(PlatformAbi.fromRawComPtr(_defaultInterface.pointer)"))
    }

    @Test
    fun generator_projects_mapped_vector_method_and_property_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IVectorProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IVector<String>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "VectorProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IVectorProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IVector<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IVector<String>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IVectorProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("VectorProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun items(): MutableList<String>"))
        assertTrue(contents, contents.contains("val mutableItems: MutableList<String>"))
        assertTrue(
            contents,
            contents.contains("WinRtListProjection.fromAbi(__collectionPointer, WinRtReferenceValueAdapters.string)") ||
                contents.contains("_iVectorProviderProjection.items()"),
        )
        assertFalse(contents, contents.contains("return object : AbstractMutableList<String>(), MutableList<String>, IWinRTObject"))
        if (!contents.contains("_iVectorProviderProjection")) {
            assertTrue(contents, contents.contains("PlatformAbi.readPointer(__resultOut)"))
            assertFalse(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
            assertFalse(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
            assertFalse(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
            assertFalse(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
            assertFalse(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
            assertFalse(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
        }
    }

    @Test
    fun generator_projects_mutable_map_runtime_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INameMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555559"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NameMap",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INameMap",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.INameMap",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("INameMap.kt")
            .contents

        assertTrue(contents, contents.contains("MutableMap<String, Int>,"))
        assertTrue(contents, contents.contains("override fun put(key: String, value: Int): Int?"))
        assertTrue(contents, contents.contains("override fun remove(key: String): Int?"))
        assertTrue(contents, contents.contains("AbstractMutableMap<String, Int>()"))
        assertTrue(contents, contents.contains("Iterable<Map.Entry<String, Int>>"))
        assertTrue(contents, contents.contains("IMap.Metadata.HASKEY_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.LOOKUP_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.INSERT_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.REMOVE_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.CLEAR_SLOT"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_mapped_map_method_and_property_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IMapProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555560"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "MapProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IMapProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "items",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "MutableItems",
                                    typeName = "Windows.Foundation.Collections.IMap<String, Int>",
                                    getterMethodName = "get_MutableItems",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IMapProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IMapProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun items(): MutableMap<String, Int>"))
        assertTrue(contents, contents.contains("val mutableItems: MutableMap<String, Int>"))
        assertTrue(contents, contents.contains("return object : AbstractMutableMap<String, Int>(), MutableMap<String, Int>, IWinRTObject"))
        assertTrue(contents, contents.contains("AbstractMutableSet<MutableMap.MutableEntry<String, Int>>()"))
        assertTrue(contents, contents.contains("IMap.Metadata.HASKEY_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.LOOKUP_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.INSERT_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.REMOVE_SLOT"))
        assertTrue(contents, contents.contains("IMap.Metadata.CLEAR_SLOT"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_mapped_collection_method_and_property_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ICollectionProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555554"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "names",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "readOnlyNames",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "NameMap",
                                    typeName = "Windows.Foundation.Collections.IMapView<String, Int>",
                                    getterMethodName = "get_NameMap",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "CollectionProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.ICollectionProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "names",
                                    returnTypeName = "Windows.Foundation.Collections.IIterable<String>",
                                ),
                                WinRtMethodDefinition(
                                    name = "readOnlyNames",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<String>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "NameMap",
                                    typeName = "Windows.Foundation.Collections.IMapView<String, Int>",
                                    getterMethodName = "get_NameMap",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.ICollectionProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ICollectionProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun names(): Iterable<String>"))
        assertTrue(contents, contents.contains("fun readOnlyNames(): List<String>"))
        assertTrue(contents, contents.contains("val nameMap: Map<String, Int>"))
        assertTrue(contents, contents.contains("PlatformAbi.readPointer(__resultOut)"))
        assertTrue(contents, contents.contains("WinRtIterableProjection.fromAbi(__collectionPointer"))
        assertFalse(contents, contents.contains("return object : Iterable<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("WinRtReadOnlyListProjection.fromAbi(__collectionPointer"))
        assertFalse(contents, contents.contains("return object : AbstractList<String>(), List<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("return object : AbstractMap<String, Int>(), Map<String, Int>, IWinRTObject"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertFalse(contents, contents.contains("IVectorView.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IMapView.Metadata.LOOKUP_SLOT"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
    }

    @Test
    fun generator_projects_interface_mapped_collection_return_element_binding() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "widgets",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Sample.Foundation.IWidget>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "widgets",
                                    returnTypeName = "Windows.Foundation.Collections.IVectorView<Sample.Foundation.IWidget>",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetProvider",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IWidgetProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun widgets(): List<IWidget>"))
        assertTrue(contents, contents.contains("WinRtReadOnlyListProjection.fromAbi(__collectionPointer"))
        assertFalse(contents, contents.contains("return object : AbstractList<IWidget>(), List<IWidget>, IWinRTObject"))
        assertTrue(contents, contents.contains("IWidget.Metadata.wrap"))
    }

    @Test
    fun generator_uses_runtime_dictionary_projection_for_projected_value_map_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetMapProvider",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "widgets",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Sample.Foundation.Widget>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "WidgetMap",
                                    typeName = "Windows.Foundation.Collections.IMapView<String, Sample.Foundation.Widget>",
                                    getterMethodName = "get_WidgetMap",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetMapProvider",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetMapProvider",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "widgets",
                                    returnTypeName = "Windows.Foundation.Collections.IMap<String, Sample.Foundation.Widget>",
                                ),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "WidgetMap",
                                    typeName = "Windows.Foundation.Collections.IMapView<String, Sample.Foundation.Widget>",
                                    getterMethodName = "get_WidgetMap",
                                ),
                            ),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IWidgetMapProvider", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IWidgetMapProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun widgets(): MutableMap<String, Widget>"))
        assertTrue(contents, contents.contains("val widgetMap: Map<String, Widget>"))
        assertTrue(contents, contents.contains("WinRtDictionaryProjection.fromAbi(__collectionPointer"))
        assertTrue(contents, contents.contains("WinRtReadOnlyDictionaryProjection.fromAbi(__collectionPointer"))
        assertFalse(contents, contents.contains("return object : AbstractMutableMap<String, Widget>(), MutableMap<String, Widget>, IWinRTObject"))
        assertFalse(contents, contents.contains("return object : AbstractMap<String, Widget>(), Map<String, Widget>, IWinRTObject"))
    }

    @Test
    fun generator_marshals_projected_interface_collection_parameters() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetSink",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setWidgets",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "widgets",
                                            "Windows.Foundation.Collections.IIterable<Sample.Foundation.IWidget>",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetSink",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetSink",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setWidgets",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "widgets",
                                            "Windows.Foundation.Collections.IIterable<Sample.Foundation.IWidget>",
                                        ),
                                    ),
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetSink",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("WidgetSink.kt")
            .contents

        assertTrue(contents, contents.contains("fun setWidgets(widgets: Iterable<IWidget>)"))
        if (contents.contains("_iWidgetSinkProjection")) {
            assertTrue(contents, contents.contains("_iWidgetSinkProjection.setWidgets(widgets)"))
        } else {
            assertTrue(contents, contents.contains("WinRtIterableProjection.createMarshaler(widgets"))
            assertTrue(contents, contents.contains("WinRtReferenceValueAdapter<IWidget>"))
            assertTrue(contents, contents.contains("ComVtableInvoker.invokeArgs"))
        }
    }

    @Test
    fun generator_uses_runtime_class_collection_adapter_for_concrete_rcw_recovery() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidgetSink",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555556"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setWidgets",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "widgets",
                                            "Windows.Foundation.Collections.IIterable<Sample.Foundation.Widget>",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidget",
                                    isDefault = true,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "WidgetSink",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IWidgetSink",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setWidgets",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition(
                                            "widgets",
                                            "Windows.Foundation.Collections.IIterable<Sample.Foundation.Widget>",
                                        ),
                                    ),
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IWidgetSink",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        val runtimeClassContents = filesByName
            .getValue("WidgetSink.kt")
            .contents
        val interfaceContents = filesByName
            .getValue("IWidgetSink.kt")
            .contents

        assertTrue(runtimeClassContents, runtimeClassContents.contains("fun setWidgets(widgets: Iterable<Widget>)"))
        assertTrue(interfaceContents, interfaceContents.contains("WinRtReferenceValueAdapters.runtimeClass("))
        assertTrue(interfaceContents, interfaceContents.contains("Widget::class"))
        assertTrue(interfaceContents, interfaceContents.contains("\"Sample.Foundation.Widget\""))
        assertTrue(interfaceContents, interfaceContents.contains("Widget.Metadata.DEFAULT_INTERFACE_IID"))
        assertTrue(interfaceContents, interfaceContents.contains("Widget.Metadata.wrap(it)"))
    }

    @Test
    fun generator_marshals_string_collection_parameters_with_runtime_value_adapter() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringSink",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setNames",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("names", "Windows.Foundation.Collections.IIterable<String>"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringSink",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringSink",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "setNames",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("names", "Windows.Foundation.Collections.IIterable<String>"),
                                    ),
                                ),
                            ),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringSink", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("StringSink.kt")
            .contents

        if (contents.contains("_iStringSinkProjection")) {
            assertTrue(contents, contents.contains("_iStringSinkProjection.setNames(names)"))
        } else {
            assertTrue(contents, contents.contains("val __namesMarshaler ="))
            assertTrue(contents, contents.contains("WinRtIterableProjection.createMarshaler(names"))
            assertTrue(contents, contents.contains("WinRtReferenceValueAdapters.string"))
            assertTrue(contents, contents.contains("__namesMarshaler.use { __namesAbi ->"))
            assertFalse(contents, contents.contains("createMarshaler(names, null)"))
        }
    }

    @Test
    fun generator_marshals_winui_bindable_collection_parameters_and_returns() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.UI",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "IBindableHost",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("87654321-2222-3333-4444-555555555551"),
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "snapshot",
                                    returnTypeName = "Windows.UI.Xaml.Interop.IBindableVectorView",
                                ),
                                WinRtMethodDefinition(
                                    name = "setItems",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("items", "Microsoft.UI.Xaml.Interop.IBindableVector"),
                                    ),
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.UI",
                            name = "BindableHost",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.UI.IBindableHost",
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "snapshot",
                                    returnTypeName = "Windows.UI.Xaml.Interop.IBindableVectorView",
                                ),
                                WinRtMethodDefinition(
                                    name = "setItems",
                                    returnTypeName = "Unit",
                                    parameters = listOf(
                                        WinRtParameterDefinition("items", "Microsoft.UI.Xaml.Interop.IBindableVector"),
                                    ),
                                ),
                            ),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.UI.IBindableHost",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("BindableHost.kt")
            .contents

        assertTrue(contents, contents.contains("fun snapshot(): List<Any?>"))
        assertTrue(contents, contents.contains("fun setItems(items: MutableList<Any?>)"))
        if (contents.contains("_iBindableHostProjection")) {
            assertTrue(contents, contents.contains("_iBindableHostProjection.setItems(items)"))
            assertTrue(contents, contents.contains("_iBindableHostProjection.snapshot()"))
        } else {
            assertTrue(contents, contents.contains("WinRtBindableVectorViewProjection.fromAbi"))
            assertTrue(contents, contents.contains("val __itemsMarshaler = WinRtBindableVectorProjection.createMarshaler(items)"))
            assertTrue(contents, contents.contains("__itemsMarshaler.use { __itemsAbi ->"))
        }
    }

    @Test
    fun generator_projects_required_notify_data_error_info_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Data",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Data",
                            name = "INotifyDataErrorInfo",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("0ee6c2cc-273e-567d-bc0a-1dd87ee51eba"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IValidatedObject",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555563"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ValidatedObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IValidatedObject",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IValidatedObject", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "DirectValidatedObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Microsoft.UI.Xaml.Data.INotifyDataErrorInfo",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyDataErrorInfo", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val files = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val contents = files.getValue("ValidatedObject.kt").contents

        assertTrue(contents, contents.contains("WinRtDataErrorInfo,"))
        assertTrue(contents, contents.contains("WinRtDataErrorInfoProjection.fromAbi(_inner)"))
        assertTrue(contents, contents.contains("override val hasErrors: Boolean"))
        assertTrue(contents, contents.contains("override fun getErrors(propertyName: String?): Iterable<Any?>?"))
        assertTrue(contents, contents.contains("override fun addErrorsChanged(handler: WinRtDataErrorsChangedHandler)"))
        assertTrue(contents, contents.contains("override fun removeErrorsChanged(handler: WinRtDataErrorsChangedHandler)"))
        assertFalse(contents, contents.contains("INotifyDataErrorInfo.Metadata.IID"))

        val directContents = files.getValue("DirectValidatedObject.kt").contents
        assertTrue(directContents, directContents.contains("WinRtDataErrorInfo,"))
        assertTrue(directContents, directContents.contains("override val hasErrors: Boolean"))
        assertFalse(directContents, directContents.contains("INotifyDataErrorInfo.Metadata.IID"))
    }

    @Test
    fun generator_projects_required_notify_property_changed_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Data",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Data",
                            name = "INotifyPropertyChanged",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("90b17601-b065-586e-83d9-9adc3a695284"),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INotifyWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555564"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Data.INotifyPropertyChanged"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NotifyWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INotifyWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INotifyWidget", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("NotifyWidget.kt")
            .contents

        assertTrue(contents, contents.contains("WinRtPropertyChangedNotifier,"))
        assertTrue(contents, contents.contains("WinRtPropertyChangedNotifierProjection.fromAbi(_inner)"))
        assertTrue(contents, contents.contains("override fun addPropertyChanged(handler: WinRtPropertyChangedHandler)"))
        assertTrue(contents, contents.contains("override fun removePropertyChanged(handler: WinRtPropertyChangedHandler)"))
        assertFalse(contents, contents.contains("INotifyPropertyChanged.Metadata.IID"))
    }

    @Test
    fun generator_projects_required_closable_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IClosable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "Close", returnTypeName = "System.Void", methodRowId = 6),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IClosableOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555564"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.IClosable"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ClosableOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IClosableOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IClosableOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ClosableOwner.kt")
            .contents

        assertTrue(contents, contents.contains("AutoCloseable,"))
        assertTrue(contents, contents.contains("override fun close()"))
        assertTrue(contents, contents.contains("WinRtClosableObject(_inner).close()"))
        assertFalse(contents, contents.contains("IClosable.Metadata.IID"))
        assertFalse(contents, contents.contains("private val _iClosable"))
        assertFalse(contents, contents.contains("import windows.foundation.IClosable"))
    }

    @Test
    fun generator_maps_projected_closable_interface_close_to_kotlin_close() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation",
                            name = "IClosable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("30d5a829-7fa4-4026-83bb-d75bae4ea99e"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "Close", returnTypeName = "Unit", methodRowId = 6),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IOutputStream",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555565"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.IClosable"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val filesByName = KotlinProjectionGenerator(emitSupportFiles = true)
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
        val contents = filesByName.getValue("IOutputStream.kt").contents

        assertTrue(contents, contents.contains("public interface IOutputStream : AutoCloseable"))
        assertTrue(contents, contents.contains("private class NativeProjection("))
        assertTrue(contents, contents.contains("fun wrap(instance: IUnknownReference): IOutputStream = NativeProjection(instance)"))
        assertFalse(contents, contents.contains("override fun Close()"))
        assertFalse(contents, contents.contains("AutoCloseable.Metadata"))
    }

    @Test
    fun generator_marks_default_interface_cache_open_and_override_across_runtime_class_hierarchy() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBaseWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555566"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IDerivedWidget",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555567"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "BaseWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IBaseWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBaseWidget", isDefault = true),
                            ),
                            isSealedType = false,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "RootWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Object",
                            defaultInterfaceName = "Sample.Foundation.IBaseWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBaseWidget", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "DerivedWidget",
                            kind = WinRtTypeKind.RuntimeClass,
                            baseTypeName = "Sample.Foundation.BaseWidget",
                            defaultInterfaceName = "Sample.Foundation.IDerivedWidget",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IDerivedWidget", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val files = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(files.getValue("BaseWidget.kt").contents.contains("private val _defaultInterface"))
        assertTrue(files.getValue("DerivedWidget.kt").contents.contains("private val _defaultInterface"))
        assertFalse(files.getValue("RootWidget.kt").contents.contains(": Object("))
    }

    @Test
    fun generator_projects_required_bindable_vector_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Microsoft.UI.Xaml.Interop",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Interop",
                            name = "IBindableIterable",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555571"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Microsoft.UI.Xaml.Interop",
                            name = "IBindableVector",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555572"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Interop.IBindableIterable"),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBindableOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555573"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Microsoft.UI.Xaml.Interop.IBindableVector"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "BindableOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IBindableOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IBindableOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("BindableOwner.kt")
            .contents

        assertTrue(contents, contents.contains("MutableList<Any?>,"))
        assertTrue(contents, contents.contains("override fun `set`(index: Int, element: Any?): Any?"))
        assertTrue(contents, contents.contains("override fun add(index: Int, element: Any?)"))
        assertTrue(contents, contents.contains("WinRtBindableVectorProjection"))
        assertFalse(contents, contents.contains("Iterable<Any?>,"))
    }

    @Test
    fun generator_projects_required_iterator_helper_surface() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555581"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(name = "MoveNext", returnTypeName = "Boolean", methodRowId = 8),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Current",
                                    typeName = "T0",
                                    getterMethodName = "get_Current",
                                    getterMethodRowId = 6,
                                ),
                                WinRtPropertyDefinition(
                                    name = "HasCurrent",
                                    typeName = "Boolean",
                                    getterMethodName = "get_HasCurrent",
                                    getterMethodRowId = 7,
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringIteratorOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555582"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IIterator<String>"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringIteratorOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringIteratorOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IStringIteratorOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("StringIteratorOwner.kt")
            .contents

        assertTrue(contents, contents.contains("Iterator<String>,"))
        assertTrue(contents, contents.contains("override fun hasNext(): Boolean"))
        assertTrue(contents, contents.contains("override fun next(): String"))
        assertTrue(contents, contents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(contents, contents.contains("IIterator.Metadata.HASCURRENT_GETTER_SLOT"))
        assertTrue(contents, contents.contains("IIterator.Metadata.MOVENEXT_SLOT"))
        assertTrue(contents, contents.contains("Metadata.acquireInterface(_inner, IIterator.Metadata.IID)"))
        assertFalse(contents, contents.contains("override val current: String"))
        assertFalse(contents, contents.contains("override val hasCurrent: Boolean"))
        assertFalse(contents, contents.contains("override fun MoveNext"))
    }

    @Test
    fun generator_resolves_required_iterator_projected_runtime_class_elements() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Foundation.Collections",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IKeyValuePair",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555583"),
                            genericParameterCount = 2,
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Key",
                                    typeName = "T0",
                                    getterMethodName = "get_Key",
                                    getterMethodRowId = 6,
                                ),
                                WinRtPropertyDefinition(
                                    name = "Value",
                                    typeName = "T1",
                                    getterMethodName = "get_Value",
                                    getterMethodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IIterator",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555584"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(name = "MoveNext", returnTypeName = "Boolean", methodRowId = 8),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Current",
                                    typeName = "T0",
                                    getterMethodName = "get_Current",
                                    getterMethodRowId = 6,
                                ),
                                WinRtPropertyDefinition(
                                    name = "HasCurrent",
                                    typeName = "Boolean",
                                    getterMethodName = "get_HasCurrent",
                                    getterMethodRowId = 7,
                                ),
                            ),
                        ),
                    ),
                ),
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "INamedResource",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555585"),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "NamedResource",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.INamedResource",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.INamedResource", isDefault = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IResourceIteratorOwner",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555586"),
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition(
                                    "Windows.Foundation.Collections.IIterator<Windows.Foundation.Collections.IKeyValuePair<String, Sample.Foundation.NamedResource>>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "ResourceIteratorOwner",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IResourceIteratorOwner",
                            implementedInterfaces = listOf(
                                WinRtInterfaceImplementationDefinition("Sample.Foundation.IResourceIteratorOwner", isDefault = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("ResourceIteratorOwner.kt")
            .contents

        assertTrue(contents, contents.contains("Iterator<Map.Entry<String, NamedResource>>,"))
        assertTrue(contents, contents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
        assertTrue(contents, contents.contains("NamedResource.Metadata.wrap"))
        assertFalse(contents, contents.contains("Unsupported"))
    }

    @Test
    fun generator_substitutes_required_interface_generic_closure_members() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IBox",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555561"),
                            genericParameterCount = 1,
                            methods = listOf(
                                WinRtMethodDefinition(name = "current", returnTypeName = "T0", methodRowId = 6),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "BoxValue",
                                    typeName = "T0",
                                    getterMethodName = "get_BoxValue",
                                    getterMethodRowId = 7,
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IStringBox",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555562"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IBox<String>",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "StringBox",
                            kind = WinRtTypeKind.RuntimeClass,
                            defaultInterfaceName = "Sample.Foundation.IStringBox",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition(
                                    interfaceName = "Sample.Foundation.IStringBox",
                                    isDefault = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("StringBox.kt")
            .contents

        assertTrue(contents, contents.contains("override fun current(): String"))
        assertTrue(contents, contents.contains("override val boxValue: String"))
        assertTrue(contents, contents.contains("Metadata.acquireInterface(_inner, IBox.Metadata.IID)"))
        assertTrue(contents, contents.contains("_iBox"))
        assertFalse(contents, contents.contains("T0"))
    }

    @Test
    fun planner_rejects_interface_surface_without_iid() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "IWidget",
                            kind = WinRtTypeKind.Interface,
                            methods = listOf(WinRtMethodDefinition(name = "ping", returnTypeName = "Unit")),
                        ),
                    ),
                ),
            ),
        )

        val error = runCatching { KotlinProjectionPlanner().plan(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("requires interface Sample.Foundation.IWidget to carry metadata IID"))
    }

    @Test
    fun generator_rejects_static_runtime_surface_without_static_interface_metadata() {
        val model = WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Sample.Foundation",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRtTypeKind.RuntimeClass,
                            methods = listOf(
                                WinRtMethodDefinition(
                                    name = "create",
                                    returnTypeName = "Widget",
                                    parameters = listOf(WinRtParameterDefinition("name", "String")),
                                    isStatic = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val error = runCatching { KotlinProjectionGenerator().generate(model) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message.orEmpty().contains("requires runtime class Sample.Foundation.Widget to carry static interface metadata"))
    }

    private fun windowsDataJsonProjectionModel(): WinRtMetadataModel =
        WinRtMetadataModel(
            namespaces = listOf(
                WinRtNamespace(
                    name = "Windows.Data.Json",
                    types = listOf(
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonValue",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("A3219FD4-B4C3-42C6-9EE6-8DCD1C3FBE9A"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "stringify", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getString", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getNumber", returnTypeName = "Double"),
                                WinRtMethodDefinition(name = "getBoolean", returnTypeName = "Boolean"),
                                WinRtMethodDefinition(name = "getArray", returnTypeName = "Windows.Data.Json.JsonArray"),
                                WinRtMethodDefinition(name = "getObject", returnTypeName = "Windows.Data.Json.JsonObject"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "ValueType",
                                    typeName = "Windows.Data.Json.JsonValueType",
                                    getterMethodName = "get_ValueType",
                                ),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonValueStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("5F6B544A-2F53-48E1-91A3-F78B50A6345C"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "createBooleanValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Boolean"))),
                                WinRtMethodDefinition(name = "createNumberValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Double"))),
                                WinRtMethodDefinition(name = "createStringValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonValue",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Windows.Data.Json.IJsonValue",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Data.Json.IJsonValue", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonValueStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "stringify", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getString", returnTypeName = "String"),
                                WinRtMethodDefinition(name = "getNumber", returnTypeName = "Double"),
                                WinRtMethodDefinition(name = "getBoolean", returnTypeName = "Boolean"),
                                WinRtMethodDefinition(name = "getArray", returnTypeName = "Windows.Data.Json.JsonArray"),
                                WinRtMethodDefinition(name = "getObject", returnTypeName = "Windows.Data.Json.JsonObject"),
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "createBooleanValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Boolean")), isStatic = true),
                                WinRtMethodDefinition(name = "createNumberValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "Double")), isStatic = true),
                                WinRtMethodDefinition(name = "createStringValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("value", "String")), isStatic = true),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(name = "ValueType", typeName = "Windows.Data.Json.JsonValueType", getterMethodName = "get_ValueType"),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonObject",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("064E24DD-29C2-4F83-9AC1-9EE11578BEB3"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IMap<String, Windows.Data.Json.IJsonValue>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "setNamedValue", returnTypeName = "Unit", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("value", "Windows.Data.Json.JsonValue"))),
                                WinRtMethodDefinition(name = "getNamedObject", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedArray", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "String"))),
                                WinRtMethodDefinition(name = "getNamedNumber", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "Boolean"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonObjectStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonObject",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Windows.Data.Json.IJsonObject",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Data.Json.IJsonObject", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonObjectStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "setNamedValue", returnTypeName = "Unit", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("value", "Windows.Data.Json.JsonValue"))),
                                WinRtMethodDefinition(name = "getNamedObject", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedArray", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedValue", returnTypeName = "Windows.Data.Json.JsonValue", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedString", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "String"))),
                                WinRtMethodDefinition(name = "getNamedNumber", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"))),
                                WinRtMethodDefinition(name = "getNamedBoolean", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("name", "String"), WinRtParameterDefinition("defaultValue", "Boolean"))),
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonArray",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("08C1D4F8-B5C6-46F7-9D5A-0CC4C8FA1BBA"),
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Foundation.Collections.IVector<Windows.Data.Json.IJsonValue>"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getObjectAt", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getArrayAt", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getStringAt", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getNumberAt", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getBooleanAt", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonArrayStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("DB1434A9-E164-499F-93E2-8A8F49BB90BA"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonArray",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            defaultInterfaceName = "Windows.Data.Json.IJsonArray",
                            implementedInterfaces = listOf(
                                io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition("Windows.Data.Json.IJsonArray", isDefault = true),
                            ),
                            activation = WinRtActivationShape(
                                isActivatable = true,
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonArrayStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getObjectAt", returnTypeName = "Windows.Data.Json.JsonObject", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getArrayAt", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getStringAt", returnTypeName = "String", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getNumberAt", returnTypeName = "Double", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "getBooleanAt", returnTypeName = "Boolean", parameters = listOf(WinRtParameterDefinition("index", "UInt"))),
                                WinRtMethodDefinition(name = "create", returnTypeName = "Windows.Data.Json.JsonArray", isStatic = true),
                                WinRtMethodDefinition(name = "parse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                                WinRtMethodDefinition(name = "tryParse", returnTypeName = "Windows.Data.Json.JsonArray", parameters = listOf(WinRtParameterDefinition("json", "String")), isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "IJsonErrorStatics",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("FE616766-BF27-4064-87B7-6563BB11CE2E"),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getJsonStatus", returnTypeName = "Windows.Data.Json.JsonErrorStatus", parameters = listOf(WinRtParameterDefinition("hResult", "Int"))),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonError",
                            kind = WinRtTypeKind.RuntimeClass,
                            isSealedType = true,
                            activation = WinRtActivationShape(
                                staticInterfaceNames = listOf("Windows.Data.Json.IJsonErrorStatics"),
                            ),
                            methods = listOf(
                                WinRtMethodDefinition(name = "getJsonStatus", returnTypeName = "Windows.Data.Json.JsonErrorStatus", parameters = listOf(WinRtParameterDefinition("hResult", "Int")), isStatic = true),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonErrorStatus",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Unknown", 0u),
                                WinRtEnumMemberDefinition("InvalidJsonString", 1u),
                            ),
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Data.Json",
                            name = "JsonValueType",
                            kind = WinRtTypeKind.Enum,
                            enumUnderlyingType = WinRtIntegralType.Int32,
                            enumMembers = listOf(
                                WinRtEnumMemberDefinition("Null", 0u),
                                WinRtEnumMemberDefinition("Boolean", 1u),
                                WinRtEnumMemberDefinition("Number", 2u),
                                WinRtEnumMemberDefinition("String", 3u),
                                WinRtEnumMemberDefinition("Array", 4u),
                                WinRtEnumMemberDefinition("Object", 5u),
                            ),
                        ),
                    ),
                ),
            ),
        )

    private fun generateWindowsDataJsonFromInstalledWinmd(): Map<String, KotlinProjectionFile> {
        val windowsWinmd = runCatching { KotlinProjectionGeneratorOptions.locateWindowsWinmd() }.getOrNull()
        assumeTrue("Windows SDK Windows.winmd is required for CsWinRT parity fixture.", windowsWinmd?.isRegularFile() == true)
        val model = WinRtMetadataLoader
            .load(windowsWinmd!!)
            .filterToWindowsDataJson()
        return KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
    }

    private fun String.memberBody(marker: String): String {
        val start = indexOf(marker)
        require(start >= 0) { "Missing generated member marker: $marker" }
        val nextMember = indexOf("\n  override ", start + marker.length).takeIf { it >= 0 }
            ?: indexOf("\n    public fun ", start + marker.length).takeIf { it >= 0 }
            ?: length
        return substring(start, nextMember)
    }

    private fun String.containsIgnoringWhitespace(snippet: String): Boolean =
        normalizedGeneratedWhitespace().contains(snippet.normalizedGeneratedWhitespace())

    private fun String.normalizedGeneratedWhitespace(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun WinRtMetadataModel.filterToWindowsDataJson(): WinRtMetadataModel =
        WinRtMetadataModel(namespaces.filter { it.name == "Windows.Data.Json" }).normalized()

    private fun assertGeneratedProjectionSuppressions(contents: String) {
        assertTrue(contents, contents.contains("@file:Suppress("))
        assertTrue(contents, contents.contains("\"USELESS_IS_CHECK\""))
        assertTrue(contents, contents.contains("\"USELESS_CAST\""))
        assertTrue(contents, contents.contains("\"UNCHECKED_CAST\""))
        assertTrue(contents, contents.contains("\"REDUNDANT_CALL_OF_CONVERSION_METHOD\""))
        assertTrue(contents, contents.contains("\"REDUNDANT_NULLABLE\""))
    }

}
