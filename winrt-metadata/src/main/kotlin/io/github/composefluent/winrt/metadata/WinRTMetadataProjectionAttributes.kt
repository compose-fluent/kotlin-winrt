package io.github.composefluent.winrt.metadata

data class WinRTProjectedAttributeDescriptor(
    val metadataTypeName: String,
    val projectedTypeName: String,
    val arguments: List<WinRTCustomAttributeValue>,
    val namedArguments: List<WinRTCustomAttributeNamedArgument>,
    val isPlatformAttribute: Boolean = false,
) {
    val renderedArguments: List<String>
        get() = arguments.map(::renderAttributeValue) +
            namedArguments.map { argument -> "${argument.name} = ${renderAttributeValue(argument.value)}" }
}

class WinRTMetadataProjectionAttributeResolver {
    fun resolve(
        attributes: List<WinRTCustomAttributeDefinition>,
        enablePlatformAttributes: Boolean = true,
        contractPlatformVersion: String? = null,
    ): List<WinRTProjectedAttributeDescriptor> {
        var allowMultiple = false
        val projected = linkedMapOf<String, WinRTProjectedAttributeDescriptor>()
        attributes.forEach { attribute ->
            val shortName = attribute.shortAttributeName()
            if (shortName in SKIPPED_ATTRIBUTE_NAMES) {
                return@forEach
            }
            if (attribute.typeName.startsWith("Windows.Foundation.Metadata.")) {
                if (shortName == "AllowMultiple") {
                    allowMultiple = true
                    return@forEach
                }
                if (shortName !in PROJECTED_METADATA_ATTRIBUTE_NAMES) {
                    return@forEach
                }
            }
            if (enablePlatformAttributes && shortName == "ContractVersion" && attribute.fixedArguments.size >= 2) {
                platformAttribute(attribute, contractPlatformVersion)?.let { platform ->
                    projected[platform.projectedTypeName] = platform
                }
            }
            val projectedName = if (shortName == "AttributeUsage") {
                "System.AttributeUsage"
            } else {
                attribute.typeName.removeSuffix("Attribute")
            }
            projected[projectedName] = WinRTProjectedAttributeDescriptor(
                metadataTypeName = attribute.typeName,
                projectedTypeName = projectedName,
                arguments = attribute.fixedArguments,
                namedArguments = attribute.namedArguments,
            )
        }
        if (allowMultiple) {
            val usage = projected["System.AttributeUsage"]
            if (usage != null && usage.namedArguments.none { it.name == "AllowMultiple" }) {
                projected["System.AttributeUsage"] = usage.copy(
                    namedArguments = usage.namedArguments + WinRTCustomAttributeNamedArgument(
                        name = "AllowMultiple",
                        value = WinRTCustomAttributeValue.BooleanValue(true),
                    ),
                )
            }
        }
        val usage = projected["System.AttributeUsage"]
        if (usage != null && usage.namedArguments.none { it.name == "AllowMultiple" }) {
            projected["System.AttributeUsage"] = usage.copy(
                namedArguments = usage.namedArguments + WinRTCustomAttributeNamedArgument(
                    name = "AllowMultiple",
                    value = WinRTCustomAttributeValue.BooleanValue(false),
                ),
            )
        }
        return projected.values.sortedBy(WinRTProjectedAttributeDescriptor::projectedTypeName)
    }

    private fun platformAttribute(
        attribute: WinRTCustomAttributeDefinition,
        contractPlatformVersion: String?,
    ): WinRTProjectedAttributeDescriptor? {
        val contractName = attribute.fixedArguments.getOrNull(0)?.stringValue ?: return null
        val version = (attribute.fixedArguments.getOrNull(1) as? WinRTCustomAttributeValue.IntegralValue)?.value ?: return null
        val majorVersion = version ushr 16
        val platform = contractName.substringAfter("Windows.Foundation.", missingDelimiterValue = "")
            .substringBefore("ApiContract")
            .ifBlank { "Windows" }
        val platformName = contractPlatformVersion?.let { "Windows$it" } ?: "$platform$majorVersion"
        return WinRTProjectedAttributeDescriptor(
            metadataTypeName = attribute.typeName,
            projectedTypeName = "System.Runtime.Versioning.SupportedOSPlatform",
            arguments = listOf(WinRTCustomAttributeValue.StringValue(platformName)),
            namedArguments = emptyList(),
            isPlatformAttribute = true,
        )
    }

    private fun WinRTCustomAttributeDefinition.shortAttributeName(): String =
        typeName.substringAfterLast('.').removeSuffix("Attribute")

    private companion object {
        private val SKIPPED_ATTRIBUTE_NAMES = setOf("GCPressure", "Guid", "Flags", "ProjectionInternal")
        private val PROJECTED_METADATA_ATTRIBUTE_NAMES = setOf(
            "DefaultOverload",
            "Overload",
            "AttributeUsage",
            "ContractVersion",
            "Experimental",
        )
    }
}

private fun renderAttributeValue(value: WinRTCustomAttributeValue): String =
    when (value) {
        is WinRTCustomAttributeValue.StringValue -> value.value?.let { "\"${it.escapeAttributeString()}\"" } ?: "null"
        is WinRTCustomAttributeValue.TypeValue -> value.typeName?.let { "typeof($it)" } ?: "null"
        is WinRTCustomAttributeValue.BooleanValue -> value.value.toString()
        is WinRTCustomAttributeValue.IntegralValue -> value.value.toString()
        is WinRTCustomAttributeValue.FloatingPointValue -> value.value.toString()
        is WinRTCustomAttributeValue.EnumValue -> renderEnumAttributeValue(value)
        is WinRTCustomAttributeValue.ArrayValue -> value.values.joinToString(prefix = "new[] { ", postfix = " }", transform = ::renderAttributeValue)
        WinRTCustomAttributeValue.NullValue -> "null"
    }

private fun renderEnumAttributeValue(value: WinRTCustomAttributeValue.EnumValue): String {
    if (value.enumTypeName == "Windows.Foundation.Metadata.AttributeTargets") {
        val flags = listOf(
            0x1L to "Delegate",
            0x2L to "Enum",
            0x4L to "Event",
            0x8L to "Field",
            0x10L to "Interface",
            0x20L to "Method",
            0x40L to "Parameter",
            0x80L to "Property",
            0x100L to "RuntimeClass",
            0x200L to "Struct",
            0x400L to "InterfaceImpl",
            0x800L to "ApiContract",
        ).filter { (mask, _) -> value.value and mask != 0L }
        if (flags.isNotEmpty()) {
            return flags.joinToString(" | ") { (_, name) -> "System.AttributeTargets.$name" }
        }
    }
    return "${value.enumTypeName}.${value.value}"
}

private fun String.escapeAttributeString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

fun WinRTTypeDefinition.projectedAttributes(
    enablePlatformAttributes: Boolean = true,
): List<WinRTProjectedAttributeDescriptor> =
    WinRTMetadataProjectionAttributeResolver().resolve(
        customAttributes,
        enablePlatformAttributes,
        availability.contractVersion?.platformVersion,
    )
