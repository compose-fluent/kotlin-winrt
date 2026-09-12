package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.Guid
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WinRTMetadataModelCodecTest {
    @Test
    fun round_trips_normalized_model_without_dropping_projection_facts() {
        val model = WinRTMetadataModel(
            namespaces = listOf(
                WinRTNamespace(
                    "Sample.Foundation",
                    listOf(
                        WinRTTypeDefinition(
                            namespace = "Sample.Foundation",
                            name = "Widget",
                            kind = WinRTTypeKind.RuntimeClass,
                            iid = Guid("11111111-2222-3333-4444-555555555555"),
                            defaultInterfaceName = "Sample.Foundation.IWidget",
                            implementedInterfaces = listOf(
                                WinRTInterfaceImplementationDefinition("Sample.Foundation.IWidget", isDefault = true),
                            ),
                            genericParameterCount = 1,
                            genericParameters = listOf(WinRTGenericParameterDefinition("T", 0, constraints = listOf("System.Object"))),
                            activation = WinRTActivationShape(
                                isActivatable = true,
                                activatableFactoryInterfaceName = "Sample.Foundation.IWidgetFactory",
                                staticInterfaceNames = listOf("Sample.Foundation.IWidgetStatics"),
                                factories = listOf(
                                    WinRTAttributedFactoryShape("Sample.Foundation.IWidgetFactory", WinRTAttributedFactoryKind.Activatable, true),
                                ),
                            ),
                            availability = WinRTAvailabilityMetadata(
                                contractVersion = WinRTContractVersionMetadata("Windows.Foundation.UniversalApiContract", 0x00020000),
                                previousContractVersions = listOf(WinRTContractVersionMetadata("Windows.Foundation.UniversalApiContract", 0x00010000)),
                            ),
                            customAttributes = listOf(
                                WinRTCustomAttributeDefinition(
                                    "Sample.Foundation.WidgetAttribute",
                                    fixedArguments = listOf(
                                        WinRTCustomAttributeValue.StringValue("widget"),
                                        WinRTCustomAttributeValue.ArrayValue(
                                            listOf(WinRTCustomAttributeValue.IntegralValue(1), WinRTCustomAttributeValue.IntegralValue(2)),
                                        ),
                                    ),
                                ),
                            ),
                            methods = listOf(
                                WinRTMethodDefinition(
                                    name = "Transform",
                                    returnTypeName = "T0",
                                    returnTypeSignature = WinRTTypeRef.genericTypeParameter(0),
                                    genericParameterCount = 1,
                                    genericParameters = listOf(WinRTGenericParameterDefinition("M", 0)),
                                    parameters = listOf(
                                        WinRTParameterDefinition(
                                            name = "value",
                                            typeName = "Array<Sample.Foundation.IWidget>",
                                            typeSignature = WinRTTypeRef.array(WinRTTypeRef.named("Sample.Foundation.IWidget")),
                                            direction = WinRTParameterDirection.In,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            windowsSdkSelections = listOf(
                WinRTWindowsSdkSelection(
                    "10.0.26100.0",
                    listOf(WinRTWindowsSdkContract("Windows.Foundation.UniversalApiContract", "10.0.26100.0")),
                ),
            ),
        ).normalized()
        val directory = Files.createTempDirectory("winrt-model-codec-")
        val path = directory.resolve("model.json")

        WinRTMetadataModelCodec.writeAtomic(path, model)

        assertEquals(model, WinRTMetadataModelCodec.read(path))
        assertEquals("kotlin-winrt-normalized-model-v1", Files.readString(path).substringAfter("\"header\":\"").substringBefore('"'))
    }

    @Test
    fun rejects_corrupt_or_schema_incompatible_model_cache() {
        val directory = Files.createTempDirectory("winrt-model-codec-corrupt-")
        val path = directory.resolve("model.json")
        Files.writeString(path, "{\"header\":\"kotlin-winrt-normalized-model-v0\",\"schema\":0}")

        assertThrows(IllegalArgumentException::class.java) { WinRTMetadataModelCodec.read(path) }
    }

    @Test
    fun cache_key_changes_when_same_path_metadata_content_changes() {
        val directory = Files.createTempDirectory("winrt-model-codec-key-")
        val input = directory.resolve("sample.winmd")
        Files.writeString(input, "first")
        val first = WinRTMetadataModelCodec.cacheKey(listOf(input))
        Files.writeString(input, "second")

        assertNotEquals(first, WinRTMetadataModelCodec.cacheKey(listOf(input)))
    }
}
