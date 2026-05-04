package io.github.composefluent.winrt.projections.generator

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
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtMetadataLoader
import io.github.composefluent.winrt.runtime.Guid
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
        assertTrue(common, common.contains("public expect interface IWidget"))
        assertTrue(common, common.contains("public fun rename(`value`: String): String"))
        assertTrue(common, common.contains("public val count: Int"))
        assertFalse(common, common.contains("NativeProjection"))
        assertTrue(jvm, jvm.contains("public actual interface IWidget"))
        assertTrue(jvm, jvm.contains("public actual fun rename(`value`: String): String"))
        assertTrue(jvm, jvm.contains("public actual val count: Int"))
        assertTrue(jvm, jvm.contains("private class NativeProjection"))
        assertTrue(jvm, jvm.contains("private object JvmAbi"))
        assertTrue(jvm, jvm.contains("FunctionDescriptor.of(ValueLayout.JAVA_INT"))
        assertTrue(jvm, jvm.contains("Linker.nativeLinker()"))
        assertTrue(jvm, jvm.contains("JvmAbi.invoke_p_p"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
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
        assertTrue(common, common.contains("public expect interface IWidget : IWidgetBase"))
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
        assertTrue(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("override fun rename(`value`: String): String"))
        assertTrue(jvm, jvm.contains("= _iWidget.rename("))
        assertTrue(jvm, jvm.contains("override val count: Int"))
        assertTrue(jvm, jvm.contains("get() = _iWidget.count"))
        assertFalse(jvm, jvm.contains("ComVtableInvoker.invokeArgs"))
        assertTrue(interfaceJvm, interfaceJvm.contains("JvmAbi.invoke_p_p"))
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
        assertTrue(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("override fun rename(`value`: String): String = _iWidget.rename("))
        assertTrue(jvm, jvm.contains("override val count: Int"))
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
        assertTrue(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("override fun reset()"))
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
        assertTrue(jvm, jvm.contains("private val _iNameReader: INameReader by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("private val _iEnabledReader: IEnabledReader by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("override val name: String"))
        assertTrue(jvm, jvm.contains("override fun isEnabled(): Boolean = _iEnabledReader.isEnabled()"))
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
        assertTrue(jvm, jvm.contains("private val _iBaseWidget: IBaseWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("private val _iWidget: IWidget by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(jvm, jvm.contains("override fun reset()"))
        assertTrue(jvm, jvm.contains("_iBaseWidget.reset()"))
        assertTrue(jvm, jvm.contains("override val name: String"))
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
        assertTrue(paths.any { it.startsWith("commonMain/kotlin/io/github/composefluent/winrt/projections/support/") })
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
        assertTrue(
            Files.walk(outputRoot.resolve("commonMain/kotlin/io/github/composefluent/winrt/projections/support")).use { stream ->
                stream.anyMatch { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
            },
        )
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
        assertTrue(jsonObject, jsonObject.contains("internal val STATIC_PARSE_SLOT: Int = IJsonObjectStatics.Metadata.PARSE_SLOT"))
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
        assertTrue(jsonValue, jsonValue.contains("internal val STATIC_CREATESTRINGVALUE_SLOT: Int ="))
        assertTrue(jsonValue, jsonValue.contains("IJsonValueStatics.Metadata.CREATESTRINGVALUE_SLOT"))
        assertTrue(jsonValue, jsonValue.contains("JsonValueType.Metadata.fromAbi"))
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
        assertTrue(generatedWidget, generatedWidget.contains("internal const val WRITES_WRAPPER_DECLARATION: Boolean = true"))
        assertTrue(generatedWidget, generatedWidget.contains("internal const val FACTORY_CACHE_NAME: String = \"WidgetActivationFactory\""))
        assertTrue(generatedWidget, generatedWidget.contains("internal val OBJECT_REFERENCE_NAMES: List<String> = listOf(\"Sample_Foundation_IWidgetCache\")"))
        assertTrue(generatedWidget, generatedWidget.contains("internal val REQUIRED_INTERFACE_NAMES: List<String> = listOf()"))
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
                            ),
                        ),
                    ),
                ),
            ),
        )

        val contents = KotlinProjectionGenerator().generate(model).single().contents

        assertTrue(contents, contents.contains("fun getObject(input: Any?): Any?"))
        assertTrue(contents, contents.contains("winRtObjectMarshaler(input).use"))
        assertFalse(contents, contents.contains("fun getObject(input: IInspectableReference): IInspectableReference"))
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
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val NAME_GETTER_METHOD_ROW_ID: Int = 5"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_ADD_METHOD_ROW_ID: Int = 9"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 10"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_ADD_SLOT: Int = 7"))
        assertTrue(filesByName.getValue("IInternalContract.kt").contents.contains("internal const val CHANGED_REMOVE_SLOT: Int = 8"))
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
        assertTrue(widgetContents.contains("init {\n        register()\n    }"))
        assertTrue(widgetContents.contains("internal fun acquireInterface(instance: IInspectableReference, iid: Guid): IUnknownReference"))
        assertTrue(widgetContents.contains("ComWrappersSupport.registerRuntimeClassFactory(TYPE_NAME) { instance -> wrap(instance) }"))
        assertTrue(widgetContents.contains("Projections.registerCustomAbiTypeMapping("))
        assertTrue(widgetContents.contains("Widget::class"))
        assertTrue(widgetContents.contains("TYPE_NAME"))
        assertTrue(widgetContents.contains("isRuntimeClass = true"))
        assertTrue(widgetContents.contains("Projections.registerDefaultInterfaceType(Widget::class, IWidget::class)"))
        assertTrue(widgetContents.contains("internal fun wrap(instance: IInspectableReference): Widget = Widget(instance, kotlin.Unit)"))
        assertTrue(widgetContents.contains("public constructor() : this(ComposableFactory.createInstance(), kotlin.Unit)"))
        assertTrue(widgetContents.contains("internal const val CREATE_METHOD_ROW_ID: Int = 20"))
        assertTrue(widgetContents.contains("internal const val NAME_GETTER_METHOD_ROW_ID: Int = 21"))
        assertTrue(widgetContents.contains("internal const val COUNT_GETTER_METHOD_ROW_ID: Int = 22"))
        assertTrue(widgetContents.contains("internal const val CHANGED_ADD_METHOD_ROW_ID: Int = 23"))
        assertTrue(widgetContents.contains("internal const val CHANGED_REMOVE_METHOD_ROW_ID: Int = 24"))
        assertTrue(widgetContents.contains("internal const val LOADED_ADD_METHOD_ROW_ID: Int = 25"))
        assertTrue(widgetContents.contains("internal const val LOADED_REMOVE_METHOD_ROW_ID: Int = 26"))
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
        assertEquals(1, "companion object Metadata".toRegex().findAll(widgetContents).count())

        val widgetFactoryContents = filesByName.getValue("IWidgetFactory.kt").contents
        assertTrue(widgetFactoryContents.contains("internal const val CREATEINSTANCE_METHOD_ROW_ID: Int = 13"))
        assertTrue(widgetFactoryContents.contains("internal const val CREATEINSTANCE_SLOT: Int = 6"))

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

        assertTrue(contents.contains("internal const val CURRENT_GETTER_SLOT: Int = 6"))
        assertTrue(contents.contains("internal const val START_SLOT: Int = 7"))
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
        assertTrue(contents.contains("internal fun wrap(instance: IUnknownReference): ICalculator = NativeProjection(instance)"))
        assertFalse(contents.contains("return object : ICalculator, IWinRTObject"))
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
        assertTrue(contents, contents.contains("internal fun wrap(instance: IUnknownReference): IWidgetFactory = NativeProjection(instance)"))
        assertFalse(contents, contents.contains("Unresolved"))
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
        assertTrue(contents.contains("WinRTEventProjectionHelpers.createEventSource("))
        assertTrue(contents.contains("\"Windows.Foundation.EventHandler<Int>\""))
        assertTrue(contents.contains("\"Sample.Foundation.ICalculator\""))
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
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition(
                                    name = "Count",
                                    typeName = "Int",
                                    getterMethodName = "get_Count",
                                    getterMethodRowId = 11,
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
        assertTrue(widgetContents.contains("internal val STATIC_PARSE_SLOT: Int = IWidgetStatics.Metadata.PARSE_SLOT"))
        assertTrue(widgetContents.contains("var count: Int"))
        assertTrue(widgetContents.contains("internal val STATIC_COUNT_GETTER_SLOT: Int = IWidgetStatics.Metadata.COUNT_GETTER_SLOT"))
        assertTrue(widgetContents.contains("internal val STATIC_COUNT_SETTER_SLOT: Int = IWidgetStatics2.Metadata.COUNT_SETTER_SLOT"))
        assertTrue(widgetContents.contains("val changed: WinRtEvent<EventHandlerCallback<Int>> by"))
        assertTrue(widgetContents.contains("lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertTrue(widgetContents.contains("WinRTEventProjectionHelpers.createEventSource("))
        assertTrue(widgetContents.contains("\"Windows.Foundation.EventHandler<Int>\""))
        assertTrue(widgetContents.contains("\"Sample.Foundation.IWidgetStatics\""))
        assertTrue(widgetContents.contains("StaticInterfaces.iWidgetStatics()"))
        assertTrue(widgetContents.contains("STATIC_CHANGED_ADD_SLOT"))
    }

    @Test
    fun generator_binds_projected_object_parameters_via_iwinrtobject() {
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
        assertTrue(widgetContents, widgetContents.contains("PlatformAbi.fromRawComPtr((value as IWinRTObject).nativeObject.pointer)"))
        assertTrue(widgetContents.contains("fun setNamedValue(name: String, `value`: WidgetValue)"))
        assertTrue(widgetContents.contains("HString.createReference(name).use { __nameAbi ->"))
        assertTrue(widgetInterfaceContents, widgetInterfaceContents.contains("PlatformAbi.fromRawComPtr((value as IWinRTObject).nativeObject.pointer)"))
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
        assertTrue(customIdentityContents.contains("nativeObject.pointer"))
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
        assertTrue(interfaceContents.contains("internal const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(interfaceContents.contains("internal const val NAME_SETTER_SLOT: Int = 7"))
        assertTrue(interfaceContents.contains("internal const val CHANGED_ADD_SLOT: Int = 8"))
        assertTrue(interfaceContents.contains("internal const val CHANGED_REMOVE_SLOT: Int = 9"))
        assertTrue(interfaceContents.contains("internal const val REFRESH_SLOT: Int = 10"))
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
            .getValue("Widget.kt")
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
        val transformerContents = filesByName.getValue("Transformer.kt").contents

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
        assertTrue(contents, contents.contains("PlatformAbi.allocateBytes(__scope, Point.Metadata.layout.sizeBytes)"))
        assertTrue(contents, contents.contains("val __result = Point.Metadata.fromAbi(__resultOut)"))
        assertTrue(contents, contents.contains("Point.Metadata.disposeAbi(__resultOut)"))
        assertTrue(contents, contents.contains("Point.Metadata.copyTo(value, __valueAbi)"))
        assertTrue(contents, contents.contains("Point.Metadata.disposeAbi(__valueAbi)"))
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
        assertTrue(recordSource, recordSource.contains("PlatformAbi.allocateBytes(__scope, NamedValue.Metadata.layout.sizeBytes)"))
        assertTrue(recordSource, recordSource.contains("NamedValue.Metadata.copyTo(value, __valueAbi)"))
        assertTrue(recordSource, recordSource.contains("NamedValue.Metadata.disposeAbi(__valueAbi)"))
        assertTrue(recordSource, recordSource.contains("val __result = NamedValue.Metadata.fromAbi(__resultOut)"))
        assertTrue(recordSource, recordSource.contains("NamedValue.Metadata.disposeAbi(__resultOut)"))
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
            .getValue("Widget.kt")
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

        assertTrue(delegateContents.contains("internal val DESCRIPTOR: WinRtDelegateDescriptor"))
        assertTrue(delegateContents.contains("WinRtDelegateReference.fromAbi(pointer, DESCRIPTOR)"))
        assertTrue(delegateContents.contains("override val nativeObject: ComObjectReference"))
        assertTrue(delegateContents.contains("override fun invoke("))
        assertTrue(delegateContents.contains("): Boolean"))
        assertTrue(delegateContents.contains("__native.invoke(listOf("))
        assertTrue(delegateContents.contains("as Boolean"))
        assertTrue(widgetContents.contains("val __resultPointer = PlatformAbi.readPointer(__resultOut)"))
        assertTrue(widgetContents.contains("val __result = WidgetHandler.Metadata.fromAbi(__resultPointer)"))
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
        assertTrue(contents.contains("TappedRoutedEventArgs.Metadata.wrap(__args[1] as IInspectableReference)"))
        assertFalse(contents.contains("TappedRoutedEventArgs.Metadata.wrap((__args[1] as IUnknownReference).asInspectable())"))
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

        assertTrue(baseContents.contains("internal const val PING_SLOT: Int = 6"))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT: Int = 7"))
        assertTrue(widgetContents.contains("internal const val UPDATED_ADD_SLOT: Int = 8"))
        assertTrue(widgetContents.contains("internal const val UPDATED_REMOVE_SLOT: Int = 9"))
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
                                    ),
                                ),
                            ),
                            fields = listOf(
                                WinRtFieldDefinition("Category", "String"),
                                WinRtFieldDefinition("Enabled", "Boolean"),
                            ),
                            properties = listOf(
                                WinRtPropertyDefinition("SourceType", "System.Type"),
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
        assertTrue(file.contents.contains("val Category: String = \"\""))
        assertTrue(file.contents.contains("val Enabled: Boolean = false"))
        assertTrue(file.contents.contains("val SourceType: KClass<*> = Any::class"))
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
        assertTrue(contents.contains("internal val PROJECTED_ATTRIBUTES: List<String>"))
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
        assertTrue(contents.memberBody("override fun addChanged").contains("requireSuccess()"))
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
                                WinRtEventDefinition(name = "Updated", delegateTypeName = "Sample.Foundation.WidgetHandler"),
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
                                WinRtEventDefinition(name = "Updated", delegateTypeName = "Sample.Foundation.WidgetHandler"),
                                WinRtEventDefinition(name = "Reset", delegateTypeName = "Sample.Foundation.WidgetHandler", isStatic = true),
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
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("internal const val TITLE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("internal val TITLE_GETTER_SLOT: Int = IWidget.Metadata.TITLE_GETTER_SLOT"))
        assertTrue(widgetContents.contains("var title: String"))
        assertTrue(widgetContents.contains("val maxCount: Int"))
        assertTrue(widgetContents.contains("fun addUpdated(handler: WidgetHandler): EventRegistrationToken"))
        assertTrue(widgetContents.contains("fun addReset(handler: WidgetHandler): EventRegistrationToken"))
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

        assertTrue(widgetContents.contains("internal const val NAME_GETTER_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidget\""))
        assertTrue(widgetContents.contains("internal const val NAME_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertTrue(widgetContents.contains("internal val NAME_GETTER_SLOT: Int = IWidgetBase.Metadata.NAME_GETTER_SLOT"))
        assertTrue(widgetContents.contains("internal const val REFRESH_SLOT_OWNER_INTERFACE: String = \"Sample.Foundation.IWidgetExtra\""))
        assertTrue(widgetContents.contains("internal const val REFRESH_SLOT_OWNER_CACHE: String = \"_iWidgetExtra\""))
        assertTrue(widgetContents.contains("internal val REFRESH_SLOT: Int = IWidgetExtra.Metadata.REFRESH_SLOT"))
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

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("WinRtAbiMarshalers"))
        assertTrue(widgetContents.contains("fun refresh()"))
        assertTrue(widgetContents.contains("Metadata.REFRESH_SLOT"))
        assertTrue(widgetContents.contains("fun version(): Int"))
        assertTrue(widgetContents.contains("Metadata.VERSION_SLOT"))
        assertTrue(widgetContents.contains("fun isReady(): Boolean"))
        assertTrue(widgetContents.contains("Metadata.ISREADY_SLOT"))
        assertTrue(widgetContents.contains("fun label(): String"))
        assertTrue(widgetContents.contains("ComVtableInvoker.invoke"))
        assertFalse(widgetContents.contains("invokeAbi("))
        assertTrue(widgetContents.contains("PlatformAbi.confinedScope().use { __scope ->"))
        assertTrue(widgetContents.contains("HString.fromHandle("))
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
        assertTrue(widgetContents.contains("HString.createReference(name).use { __nameAbi ->"))
        assertFalse(widgetContents.contains("-> {"))
        assertTrue(widgetContents.contains("Metadata.GETNAMEDSTRING_SLOT"))
        assertTrue(widgetContents.contains("fun getStringAt(index: UInt): String"))
        assertTrue(widgetContents.contains("index.toInt()"))
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
        assertTrue(widgetContents.contains("if (ready) 1.toByte() else 0.toByte()"))
        assertTrue(widgetContents.contains("Metadata.SETREADY_SLOT"))
        assertTrue(widgetContents.contains("fun createNumberValue("))
        assertTrue(widgetContents.contains("Double): WidgetValue"))
        assertTrue(widgetContents.contains("Metadata.CREATENUMBERVALUE_SLOT"))
        assertTrue(widgetContents.contains("WidgetValue.Metadata.wrap"))
        assertTrue(widgetContents.contains("var ready: Boolean"))
        assertTrue(widgetContents.contains("Metadata.READY_SETTER_SLOT"))
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
    fun generator_hands_custom_mapped_member_call_modes_to_companions_and_support_plan() {
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
        val shapePlanContents = filesByName.getValue("WinRTTypeShapeWriterPlan.kt").contents

        assertTrue(iterableContents, iterableContents.contains("internal val CUSTOM_MAPPED_MEMBER_PLANS: List<String>"))
        assertTrue(iterableContents, iterableContents.contains("internal const val CUSTOM_MAPPED_MEMBER_CALL_MODE: String = \"static-abi\""))
        assertTrue(iterableContents, iterableContents.contains("internal const val CUSTOM_MAPPED_MEMBER_EXPLICIT: Boolean = true"))
        assertTrue(iterableContents, iterableContents.contains("internal const val CUSTOM_MAPPED_MEMBER_PRIVATE: Boolean = false"))
        assertTrue(shapePlanContents, shapePlanContents.contains("mappedCallMode = \"static-abi\""))
        assertTrue(shapePlanContents, shapePlanContents.contains("mappedExplicit = true"))
        assertTrue(shapePlanContents, shapePlanContents.contains("mappedPrivate = false"))
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
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.dateTimeFromAbi(__resultOut)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyTimeSpanTo(delay, __delayAbi)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyHResultTo(error, __errorAbi)"))
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
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.typeNameFromAbi(__resultOut)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.disposeTypeNameAbi(__resultOut)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.copyTypeNameTo(type, __typeAbi)"))
        assertTrue(classContents, classContents.contains("WinRtSystemProjectionMarshalers.disposeTypeNameAbi(__typeAbi)"))
        assertFalse(classContents, classContents.contains("TypeName.Metadata"))
    }

    @Test
    fun generator_covers_short_abi_argument_lists_without_sample_specific_overloads() {
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

        assertTrue(shapeContents, shapeContents.contains("arg0 = if (enabled) 1.toByte() else 0.toByte()"))
        assertTrue(shapeContents, shapeContents.contains("ComVtableInvoker.invokeArgs(instance = _defaultInterface.pointer"))
        assertTrue(shapeContents, shapeContents.contains("Metadata.SETOFFSET_SLOT"))
        assertTrue(shapeContents, shapeContents.contains("Metadata.SETOPACITY_SLOT"))
        assertTrue(shapeContents, shapeContents.contains("Metadata.CONFIGURE_SLOT"))
        assertTrue(shapeContents, shapeContents.contains("arg0 = offset"))
        assertTrue(shapeContents, shapeContents.contains("arg0 = opacity"))
        assertTrue(shapeContents, shapeContents.contains("arg1 = offset"))
        assertFalse(shapeContents, shapeContents.contains("invokeGenericArgs"))
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

        assertTrue(widgetContents, widgetContents.contains("internal val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(widgetContents, widgetContents.contains("internal val FAST_ABI_PROPERTY_SLOTS: List<String>"))
        assertTrue(widgetContents, widgetContents.contains("Name|start=6|get=6|set=7"))
        assertTrue(widgetContents, widgetContents.contains("Mode|start=8|get=8|set=9"))
        assertTrue(widgetContents, widgetContents.contains("internal val OBJECT_REFERENCE_NAMES: List<String> = listOf(\"Sample_FastAbi_IWidgetCache\")"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidget|cache=Sample_FastAbi_IWidgetCache|default=true|skip=|inner=false|defaultObjRef=false|hierarchy=|defaultObjRefSlot=|generic=false"))
        assertTrue(widgetContents, widgetContents.contains("Sample.FastAbi.IWidgetOverrides|cache=Sample_FastAbi_IWidgetOverridesCache|default=false|skip=fast-abi-non-default-exclusive"))
        assertTrue(widgetContents, widgetContents.contains("internal const val MODE_GETTER_SLOT_OWNER_CACHE: String = \"_defaultInterface\""))
        assertFalse(widgetContents, widgetContents.contains("private val _iWidgetOverrides"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("internal val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("internal const val NAME_GETTER_SLOT: Int = 6"))
        assertTrue(defaultInterfaceContents, defaultInterfaceContents.contains("internal const val NAME_SETTER_SLOT: Int = 7"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("internal val FAST_ABI_INTERFACE_SLOTS: List<String>"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("Sample.FastAbi.IWidget|default=true|start=6|count=2|hierarchyOffset=0|next=8"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("Sample.FastAbi.IWidgetOverrides|default=false|start=8|count=2|hierarchyOffset=0|next=10"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("internal const val MODE_GETTER_SLOT: Int = 8"))
        assertTrue(exclusiveInterfaceContents, exclusiveInterfaceContents.contains("internal const val MODE_SETTER_SLOT: Int = 9"))
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
        assertTrue(interfaceContents, interfaceContents.contains("fun tryCommand(): WinRtCommand?"))
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

        val widgetContents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("Widget.kt")
            .contents

        assertFalse(widgetContents.contains("CompletableFuture"))
        assertTrue(widgetContents.contains("fun refreshAsync(): WinRtAsyncActionReference"))
        assertTrue(widgetContents.contains("val __result = WinRtAsyncActionReference(PlatformAbi.readPointer(__resultOut))"))
        assertTrue(widgetContents.contains("fun fetchAsync(): WinRtAsyncOperationReference<String>"))
        assertTrue(widgetContents.contains("interfaceId = WinRtAsyncOperationReference.interfaceId(WinRtTypeSignature.string())"))
        assertTrue(widgetContents.contains("completedHandlerInterfaceId ="))
        assertTrue(widgetContents.contains("WinRtAsyncOperationReference.completedHandlerInterfaceId(WinRtTypeSignature.string())"))
        assertTrue(widgetContents.contains("ComVtableInvoker.invokeArgs(__operation.pointer,"))
        assertTrue(widgetContents.contains("WinRtAsyncOperationVftblSlots.GetResults, __operationResultOut)"))
        assertTrue(widgetContents.contains("val __operationResultString ="))
        assertTrue(widgetContents.contains("fromHandle("))
        assertTrue(widgetContents.contains("PlatformAbi.readPointer(__operationResultOut), owner = true"))
        assertTrue(widgetContents.contains("__operationResultString.use { value -> value.toKString() }"))
        assertTrue(widgetContents.contains("fun fetchStreamAsync(): WinRtAsyncOperationReference<IStream>"))
        assertTrue(widgetContents.contains("WinRtTypeSignature.guid(IStream.Metadata.IID)"))
        assertTrue(widgetContents.contains("IStream.Metadata.wrap(IUnknownReference(PlatformAbi.toRawComPtr(PlatformAbi.readPointer(__operationResultOut))))"))
        assertTrue(widgetContents.contains("fun fetchCommandAsync(): WinRtAsyncOperationReference<WinRtCommand>"))
        assertTrue(widgetContents.contains("WinRtSystemProjectionMarshalers.objectFromAbi(__operationResultPointer,"))
        assertTrue(widgetContents.contains("WinRtTypeHandle(\"io.github.composefluent.winrt.runtime.WinRtCommand\""))
        assertFalse(widgetContents.contains("WinRtCommand.Metadata.wrap"))
        assertTrue(widgetContents.contains("fun refreshWithProgressAsync(): WinRtAsyncActionWithProgressReference<Int>"))
        assertTrue(widgetContents.contains("WinRtAsyncActionWithProgressReference.interfaceId(WinRtTypeSignature.int32())"))
        assertTrue(widgetContents.contains("WinRtAsyncActionWithProgressReference.progressHandlerInterfaceId(WinRtTypeSignature.int32())"))
        assertTrue(widgetContents.contains("WinRtAsyncActionWithProgressReference.completedHandlerInterfaceId(WinRtTypeSignature.int32())"))
        assertTrue(widgetContents.contains("fun fetchWithProgressAsync(): WinRtAsyncOperationWithProgressReference<String, UInt>"))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressReference.interfaceId("))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressReference.progressHandlerInterfaceId("))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressReference.completedHandlerInterfaceId("))
        assertTrue(widgetContents.contains("WinRtTypeSignature.string()"))
        assertTrue(widgetContents.contains("WinRtTypeSignature.uint32()"))
        assertTrue(widgetContents.contains("WinRtAsyncOperationWithProgressVftblSlots.GetResults, __operationResultOut)"))
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
        assertTrue(iterableContents, iterableContents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.CURRENT_GETTER_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.HASCURRENT_GETTER_SLOT"))
        assertTrue(iterableContents, iterableContents.contains("IIterator.Metadata.MOVENEXT_SLOT"))

        assertTrue(listContents, listContents.contains("List<String>,"))
        assertTrue(listContents, listContents.contains("override val size: Int"))
        assertTrue(listContents, listContents.contains("override fun get(index: Int): String"))
        assertTrue(listContents, listContents.contains("AbstractList"))
        assertTrue(listContents, listContents.contains("IVectorView.Metadata.GETAT_SLOT"))
        assertTrue(listContents, listContents.contains("IVectorView.Metadata.SIZE_GETTER_SLOT"))
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
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.contains("Iterable<Map.Entry<T0, T1>>,"))
        assertTrue(mapViewInterfaceContents, mapViewInterfaceContents.contains("WinRtGenericParameterProjection.fromAbi<T1>(__resultPointer)"))
        assertFalse(mapViewInterfaceContents, mapViewInterfaceContents.contains("import T0"))
        assertFalse(mapViewInterfaceContents, mapViewInterfaceContents.contains("import T1"))

        val iterableInterfaceContents = filesByName.getValue("IIterable.kt").contents
        assertTrue(iterableInterfaceContents, iterableInterfaceContents.contains("public interface IIterable<T0>"))
        assertFalse(iterableInterfaceContents, iterableInterfaceContents.contains("import T0"))

        val observableVectorContents = filesByName.getValue("IObservableVector.kt").contents
        assertTrue(observableVectorContents, observableVectorContents.contains("MutableList<T0>,"))
        assertTrue(observableVectorContents, observableVectorContents.contains("private val __iObservableVectorVectorCollection"))
        assertTrue(observableVectorContents, observableVectorContents.contains("override fun set(index: Int, element: T0): T0"))
        assertTrue(observableVectorContents, observableVectorContents.contains(".use { __elementAbiReference ->"))

        val observableMapContents = filesByName.getValue("IObservableMap.kt").contents
        assertTrue(observableMapContents, observableMapContents.contains("MutableMap<T0, T1>,"))
        assertTrue(observableMapContents, observableMapContents.contains("private val __iObservableMapMapCollection"))
        assertTrue(observableMapContents, observableMapContents.contains("override fun put(key: T0, value: T1): T1?"))
        assertTrue(observableMapContents, observableMapContents.contains(".use { __keyAbiReference ->"))
        assertTrue(observableMapContents, observableMapContents.contains(".use { __valueAbiReference ->"))

        val mapContents = filesByName
            .getValue("ObjectMap.kt")
            .contents

        assertTrue(mapContents, mapContents.contains("Map<String, Any?>,"))
        assertTrue(mapContents, mapContents.contains("Map.Entry<String, Any?>"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.KEY_GETTER_SLOT"))
        assertTrue(mapContents, mapContents.contains("IKeyValuePair.Metadata.VALUE_GETTER_SLOT"))
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
                            name = "IObservableMap",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555557"),
                            genericParameterCount = 2,
                        ),
                        WinRtTypeDefinition(
                            namespace = "Windows.Foundation.Collections",
                            name = "IMapChangedEventArgs",
                            kind = WinRtTypeKind.Interface,
                            iid = Guid("11111111-2222-3333-4444-555555555558"),
                            genericParameterCount = 1,
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
            ),
        )

        val filesByName = KotlinProjectionGenerator(
            emitSupportFiles = true,
            projectionContext = WinRtMetadataProjectionContext(sources = emptyList(), component = true),
        )
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }

        assertTrue(filesByName.getValue("WinRTGenericAbiRegistry.kt").contents.contains("_get_Value_Int"))
        assertTrue(filesByName.getValue("WinRTGenericAbiRegistry.kt").contents.contains("Windows.Foundation.IReference<Int>"))
        assertTrue(filesByName.getValue("WinRTGenericAbiRegistry.kt").contents.contains("fun registerAbiDelegates"))
        val genericTypeInstantiations = filesByName.getValue("WinRTGenericTypeInstantiations.kt").contents
        assertTrue(genericTypeInstantiations.contains("Windows_Foundation_IReference_Int"))
        assertTrue(genericTypeInstantiations.contains("fun initializeAll"))
        assertTrue(genericTypeInstantiations.contains("fun initializeBySourceType(sourceType: String)"))
        assertTrue(genericTypeInstantiations.contains("internal data class GenericTypeInstantiationRuntimeBinding"))
        assertTrue(genericTypeInstantiations.contains("fun installRuntimeBinding(binding: GenericTypeInstantiationRuntimeBinding)"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindRcwHelpers"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindVtableFunctions"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindPropertyAccessors"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindDelegateCcwInvoke"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindGenericReturnOnlyRcwHelpers"))
        assertTrue(genericTypeInstantiations.contains("WinRtGenericTypeInstantiationRuntime.bindProjectedGenericFallbacks"))
        assertTrue(genericTypeInstantiations.contains("genericReturnOnlyRcwFunctions = listOf(\"get_Value\")"))
        assertTrue(genericTypeInstantiations.contains("projectedGenericFallbacks = listOf(\"get_Value\")"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initRcwHelpers(entry, entry.rcwFunctions)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initVtableFunctions(entry, entry.vtableFunctions)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initPropertyAccessors(entry, entry.propertyAccessors)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initGenericReturnOnlyRcwHelpers(entry, entry.genericReturnOnlyRcwFunctions)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initProjectedGenericFallbacks(entry, entry.projectedGenericFallbacks)"))
        assertTrue(genericTypeInstantiations.contains("runtimeBinding.initDelegateCcwInvoke(entry)"))
        assertTrue(genericTypeInstantiations.contains("private fun registerGenericInstantiation(entry: GenericTypeInstantiationEntry)"))
        val eventProjectionHelpers = filesByName.getValue("WinRTEventProjectionHelpers.kt").contents
        assertTrue(eventProjectionHelpers.contains("sourceClass = \"EventHandlerEventSource\""))
        assertTrue(eventProjectionHelpers.contains("usesSharedEventHandlerSource = true"))
        assertTrue(eventProjectionHelpers.contains("\"EventHandlerEventSource\" -> eventHandlerEventSourceFactoryFor(entry)"))
        assertTrue(eventProjectionHelpers.contains("fun eventHandlerEventSourceFactoryFor("))
        assertTrue(eventProjectionHelpers.contains("entry: EventSourceEntry"))
        assertTrue(eventProjectionHelpers.contains("WinRtEventSourceFactory?"))
        assertTrue(eventProjectionHelpers.contains("Windows.Foundation.EventHandler<Int>\" -> { obj, index ->"))
        assertTrue(eventProjectionHelpers.contains("EventHandlerEventSource<"))
        assertTrue(eventProjectionHelpers.contains("argsKind = "))
        assertTrue(eventProjectionHelpers.contains("\"_EventSource_Windows_Foundation_TypedEventHandler"))
        assertTrue(eventProjectionHelpers.contains("internal class _EventSource_Windows_Foundation_TypedEventHandler"))
        assertTrue(eventProjectionHelpers.contains("EventSource<TypedEventHandler<Widget, Int>>"))
        assertFalse(eventProjectionHelpers.contains("usesSharedEventHandlerSource = true,\n    genericArgumentTypeNames = listOf(\"Sample.Foundation.Widget\", \"Int\")"))
        assertTrue(eventProjectionHelpers.contains("internal class _EventSource_Sample_Foundation_WidgetHandler"))
        assertTrue(eventProjectionHelpers.contains("EventSource<WidgetHandler>"))
        assertTrue(eventProjectionHelpers.contains("handler.invoke("))
        assertTrue(eventProjectionHelpers.contains("\"_EventSource_Sample_Foundation_WidgetHandler\" ->"))
        assertTrue(eventProjectionHelpers.contains("_EventSource_Sample_Foundation_WidgetHandler(obj, index)"))
        assertFalse(eventProjectionHelpers.contains("internal class _EventSource_Windows_Foundation_Collections_MapChangedEventHandler"))
        assertFalse(eventProjectionHelpers.contains("IMapChangedEventArgs.Metadata.wrap"))
        assertTrue(eventProjectionHelpers.contains("fun installEventSources()"))
        assertTrue(eventProjectionHelpers.contains("WinRTGenericTypeInstantiations.initializeBySourceType(entry.eventType)"))
        assertTrue(eventProjectionHelpers.contains("WinRtEventSourceRuntime.registerEventSource"))
        assertTrue(eventProjectionHelpers.contains("eventSourceFactory = eventSourceFactoryFor(entry)"))
        assertTrue(eventProjectionHelpers.contains("fun createEventSource("))
        assertTrue(eventProjectionHelpers.contains("WinRtEventSourceRuntime.createEventSource("))
        assertFalse(eventProjectionHelpers.contains("No WinRT event source registered"))
        assertTrue(eventProjectionHelpers.contains("ownerType = \"Sample.Foundation.IWidget\""))
        assertTrue(eventProjectionHelpers.contains("fun installEventSources("))
        assertTrue(eventProjectionHelpers.contains("install:"))
        assertTrue(eventProjectionHelpers.contains("EventSourceEntry"))
        assertTrue(filesByName.getValue("WinRTAbiImplementationPlan.kt").contents.contains("Sample.Foundation.IWidget"))
        assertTrue(filesByName.getValue("WinRTAbiImplementationPlan.kt").contents.contains("fun installAbiImplementations"))
        val typeShapeWriterPlan = filesByName.getValue("WinRTTypeShapeWriterPlan.kt").contents
        assertTrue(typeShapeWriterPlan.contains("HELPER_OUTPUTS"))
        assertTrue(typeShapeWriterPlan.contains("WinRTNamespaceAdditions.kt"))
        assertTrue(typeShapeWriterPlan.contains("fun registerBaseTypeMappings"))
        assertTrue(typeShapeWriterPlan.contains("deferredAuthoringFactoryMembers = listOf(\"Sample.Foundation.IWidgetFactory\")"))
        assertTrue(typeShapeWriterPlan.contains("deferredModuleActivationEntries = listOf(\"Sample.Foundation.Widget\")"))
        assertTrue(typeShapeWriterPlan.contains("fun deferredAuthoringFactoryEntries("))
        assertTrue(typeShapeWriterPlan.contains("List<Pair<String, String>>"))
        assertFalse(typeShapeWriterPlan.contains("installModuleActivationFactories"))
        assertFalse(typeShapeWriterPlan.contains("moduleActivationEntries"))
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
        assertTrue(ccwFactories.contains("ComWrappersSupport.createCCWForObject(__result"))
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
        assertTrue(contents, contents.contains("override fun set(index: Int, element: String): String"))
        assertTrue(contents, contents.contains("override fun add(index: Int, element: String)"))
        assertTrue(contents, contents.contains("override fun subList(fromIndex: Int, toIndex: Int): MutableList<String>"))
        assertTrue(contents, contents.contains("AbstractMutableList<String>()"))
        assertFalse(contents, contents.contains("Iterable<String> by __iNameVectorIterableCollection"))
        assertTrue(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
        assertTrue(contents, contents.contains("REQUIRED_MAPPED_HELPER_PLANS"))
        assertTrue(contents, contents.contains("Windows.Foundation.Collections.IVector<String>|IList|idic"))
        assertTrue(contents, contents.contains("removeGeneric=System.Collections.Generic.IEnumerable<String>"))
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
        assertTrue(contents, contents.contains("return object : AbstractMutableList<String>(), MutableList<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("PlatformAbi.readPointer(__resultOut)"))
        assertTrue(contents, contents.contains("IVector.Metadata.GETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.SETAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.INSERTAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.REMOVEAT_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.APPEND_SLOT"))
        assertTrue(contents, contents.contains("IVector.Metadata.CLEAR_SLOT"))
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
            .getValue("NameMap.kt")
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
            .getValue("MapProvider.kt")
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
            .getValue("CollectionProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun names(): Iterable<String>"))
        assertTrue(contents, contents.contains("fun readOnlyNames(): List<String>"))
        assertTrue(contents, contents.contains("val nameMap: Map<String, Int>"))
        assertTrue(contents, contents.contains("PlatformAbi.readPointer(__resultOut)"))
        assertTrue(contents, contents.contains("return object : Iterable<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("return object : AbstractList<String>(), List<String>, IWinRTObject"))
        assertTrue(contents, contents.contains("return object : AbstractMap<String, Int>(), Map<String, Int>, IWinRTObject"))
        assertTrue(contents, contents.contains("IIterable.Metadata.FIRST_SLOT"))
        assertTrue(contents, contents.contains("IVectorView.Metadata.GETAT_SLOT"))
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
            .getValue("WidgetProvider.kt")
            .contents

        assertTrue(contents, contents.contains("fun widgets(): List<IWidget>"))
        assertTrue(contents, contents.contains("return object : AbstractList<IWidget>(), List<IWidget>, IWinRTObject"))
        assertTrue(contents, contents.contains("IWidget.Metadata.wrap"))
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
        assertTrue(contents, contents.contains("WinRtIterableProjection.createMarshaler(widgets"))
        assertTrue(contents, contents.contains("WinRtReferenceValueAdapter<IWidget>"))
        assertTrue(contents, contents.contains("ComVtableInvoker.invokeArgs"))
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

        assertTrue(contents, contents.contains("val __namesMarshaler ="))
        assertTrue(contents, contents.contains("WinRtIterableProjection.createMarshaler(names"))
        assertTrue(contents, contents.contains("WinRtReferenceValueAdapters.string"))
        assertTrue(contents, contents.contains("__namesMarshaler.use { __namesAbi ->"))
        assertFalse(contents, contents.contains("createMarshaler(names, null)"))
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
        assertTrue(contents, contents.contains("WinRtBindableVectorViewProjection.fromAbi"))
        assertTrue(contents, contents.contains("fun setItems(items: MutableList<Any?>)"))
        assertTrue(contents, contents.contains("val __itemsMarshaler = WinRtBindableVectorProjection.createMarshaler(items)"))
        assertTrue(contents, contents.contains("__itemsMarshaler.use { __itemsAbi ->"))
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

        val contents = KotlinProjectionGenerator()
            .generate(model)
            .associateBy { it.relativePath.substringAfterLast('/') }
            .getValue("IOutputStream.kt")
            .contents

        assertTrue(contents, contents.contains("public interface IOutputStream : AutoCloseable"))
        assertTrue(contents, contents.contains("override fun close()"))
        assertTrue(contents, contents.contains("ComVtableInvoker.invoke(instance = nativeObject.pointer, slot = 6)"))
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

    private fun WinRtMetadataModel.filterToWindowsDataJson(): WinRtMetadataModel =
        WinRtMetadataModel(namespaces.filter { it.name == "Windows.Data.Json" }).normalized()

}
