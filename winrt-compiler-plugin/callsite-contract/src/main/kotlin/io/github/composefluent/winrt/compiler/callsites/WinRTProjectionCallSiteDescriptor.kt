package io.github.composefluent.winrt.compiler.callsites

const val WINRT_PROJECTION_CALL_SITE_ANNOTATION_FQ_NAME: String =
    "io.github.composefluent.winrt.runtime.WinRTProjectionCallSite"

private const val CURRENT_SCHEMA_VERSION = "v1"
private const val EMPTY_PARAMETERS_TOKEN = "-"

enum class WinRTProjectionCallSiteReceiver {
    COM_OBJECT_REFERENCE,
    IUNKNOWN_REFERENCE,
}

enum class WinRTProjectionCallSiteSlotPolicy {
    PARAMETER,
    CONSTANT,
}

enum class WinRTProjectionCallSiteHResultPolicy {
    CHECK,
    IGNORE,
}

enum class WinRTProjectionCallSiteResultStrategy {
    UNIT,
    SCALAR_OUT,
    STRING_OUT,
    STRUCT_OUT,
    REFERENCE_OUT,
    ARRAY_OUT,
    OBJECT_OUT,
}

enum class WinRTProjectionCallSiteValueKind {
    VOID,
    BOOLEAN,
    INT8,
    UINT8,
    INT16,
    UINT16,
    INT32,
    UINT32,
    INT64,
    UINT64,
    FLOAT,
    DOUBLE,
    STRING,
    RAW_ADDRESS,
    RAW_COM_PTR,
    OBJECT,
    STRUCT,
    INSPECTABLE_REFERENCE,
    UNKNOWN_REFERENCE,
    ARRAY,
}

enum class WinRTProjectionCallSiteOwnership {
    NONE,
    BORROWED,
    OWNED,
}

enum class WinRTProjectionCallSiteParameterRole {
    ABI_ARGUMENT,
    STRUCT_VALUE,
    STRUCT_ADAPTER,
    ARRAY_MARSHALER,
}

data class WinRTProjectionCallSiteValue(
    val kind: WinRTProjectionCallSiteValueKind,
    val ownership: WinRTProjectionCallSiteOwnership = WinRTProjectionCallSiteOwnership.NONE,
    val nullable: Boolean = false,
    val sizeBytes: Int = 0,
    val alignmentBytes: Int = 0,
) {
    init {
        require(sizeBytes >= 0) { "WinRT call-site value size must not be negative." }
        require(alignmentBytes >= 0) { "WinRT call-site value alignment must not be negative." }
        if (kind == WinRTProjectionCallSiteValueKind.STRUCT) {
            require(sizeBytes > 0) { "WinRT struct call-site values require a positive size." }
            require(alignmentBytes > 0) { "WinRT struct call-site values require a positive alignment." }
        } else {
            require(sizeBytes == 0 && alignmentBytes == 0) {
                "Only WinRT struct call-site values may declare size or alignment."
            }
        }
        if (kind == WinRTProjectionCallSiteValueKind.VOID) {
            require(ownership == WinRTProjectionCallSiteOwnership.NONE && !nullable) {
                "A WinRT void call-site value cannot declare ownership or nullability."
            }
        }
    }

    internal fun encode(): String =
        listOf(
            kind.name,
            ownership.name,
            if (nullable) "1" else "0",
            sizeBytes.toString(),
            alignmentBytes.toString(),
        ).joinToString("~")

    companion object {
        val VOID: WinRTProjectionCallSiteValue = WinRTProjectionCallSiteValue(WinRTProjectionCallSiteValueKind.VOID)

        internal fun parse(encoded: String): WinRTProjectionCallSiteValue {
            val fields = encoded.split('~')
            require(fields.size == 5) { "Malformed WinRT call-site value '$encoded'." }
            return WinRTProjectionCallSiteValue(
                kind = fields[0].enumValue("value kind"),
                ownership = fields[1].enumValue("value ownership"),
                nullable = when (fields[2]) {
                    "0" -> false
                    "1" -> true
                    else -> throw IllegalArgumentException(
                        "Malformed WinRT call-site value nullability '${fields[2]}'.",
                    )
                },
                sizeBytes = fields[3].nonNegativeInt("value size"),
                alignmentBytes = fields[4].nonNegativeInt("value alignment"),
            )
        }
    }
}

data class WinRTProjectionCallSiteParameter(
    val role: WinRTProjectionCallSiteParameterRole,
    val value: WinRTProjectionCallSiteValue,
) {
    init {
        when (role) {
            WinRTProjectionCallSiteParameterRole.ABI_ARGUMENT,
            WinRTProjectionCallSiteParameterRole.STRUCT_VALUE ->
                require(value.kind != WinRTProjectionCallSiteValueKind.VOID) {
                    "A WinRT call-site value parameter cannot be void."
                }
            WinRTProjectionCallSiteParameterRole.STRUCT_ADAPTER ->
                require(value.kind == WinRTProjectionCallSiteValueKind.STRUCT) {
                    "A WinRT struct-adapter parameter must describe its struct layout."
                }
            WinRTProjectionCallSiteParameterRole.ARRAY_MARSHALER ->
                require(value.kind == WinRTProjectionCallSiteValueKind.ARRAY) {
                    "A WinRT array-marshaler parameter must describe an array value."
                }
        }
    }

    internal fun encode(): String = "${role.name}~${value.encode()}"

    companion object {
        internal fun parse(encoded: String): WinRTProjectionCallSiteParameter {
            val separator = encoded.indexOf('~')
            require(separator > 0) { "Malformed WinRT call-site parameter '$encoded'." }
            return WinRTProjectionCallSiteParameter(
                role = encoded.substring(0, separator).enumValue("parameter role"),
                value = WinRTProjectionCallSiteValue.parse(encoded.substring(separator + 1)),
            )
        }
    }
}

data class WinRTProjectionCallSiteDescriptor(
    val receiver: WinRTProjectionCallSiteReceiver,
    val slotPolicy: WinRTProjectionCallSiteSlotPolicy = WinRTProjectionCallSiteSlotPolicy.PARAMETER,
    val constantSlot: Int = -1,
    val hResultPolicy: WinRTProjectionCallSiteHResultPolicy = WinRTProjectionCallSiteHResultPolicy.CHECK,
    val resultStrategy: WinRTProjectionCallSiteResultStrategy,
    val result: WinRTProjectionCallSiteValue,
    val parameters: List<WinRTProjectionCallSiteParameter> = emptyList(),
) {
    init {
        when (slotPolicy) {
            WinRTProjectionCallSiteSlotPolicy.PARAMETER ->
                require(constantSlot == -1) { "A parameterized WinRT call site cannot declare a constant slot." }
            WinRTProjectionCallSiteSlotPolicy.CONSTANT ->
                require(constantSlot >= 0) { "A constant WinRT call site requires a non-negative vtable slot." }
        }
        when (resultStrategy) {
            WinRTProjectionCallSiteResultStrategy.UNIT ->
                require(result.kind == WinRTProjectionCallSiteValueKind.VOID) {
                    "A unit WinRT call site must use a void result."
                }
            WinRTProjectionCallSiteResultStrategy.SCALAR_OUT ->
                require(result.kind in scalarResultKinds) {
                    "A scalar WinRT call site requires a scalar result, not ${result.kind}."
                }
            WinRTProjectionCallSiteResultStrategy.STRING_OUT -> {
                require(result.kind == WinRTProjectionCallSiteValueKind.STRING) {
                    "A string WinRT call site must use a string result."
                }
                require(result.ownership == WinRTProjectionCallSiteOwnership.OWNED) {
                    "A returned WinRT HSTRING must declare owned result semantics."
                }
            }
            WinRTProjectionCallSiteResultStrategy.STRUCT_OUT ->
                require(result.kind == WinRTProjectionCallSiteValueKind.STRUCT) {
                    "A struct WinRT call site must use a struct result."
                }
            WinRTProjectionCallSiteResultStrategy.REFERENCE_OUT ->
                require(
                    result.kind == WinRTProjectionCallSiteValueKind.INSPECTABLE_REFERENCE ||
                        result.kind == WinRTProjectionCallSiteValueKind.UNKNOWN_REFERENCE,
                ) { "A reference WinRT call site must use an inspectable or unknown reference result." }
            WinRTProjectionCallSiteResultStrategy.ARRAY_OUT ->
                require(result.kind == WinRTProjectionCallSiteValueKind.ARRAY) {
                    "An array WinRT call site must use an array result."
                }
            WinRTProjectionCallSiteResultStrategy.OBJECT_OUT ->
                require(result.kind == WinRTProjectionCallSiteValueKind.OBJECT) {
                    "An object WinRT call site must use an object result."
                }
        }
    }

    fun encode(): String =
        listOf(
            CURRENT_SCHEMA_VERSION,
            receiver.name,
            slotPolicy.name,
            constantSlot.toString(),
            hResultPolicy.name,
            resultStrategy.name,
            result.encode(),
            parameters.joinToString(",", transform = WinRTProjectionCallSiteParameter::encode)
                .ifEmpty { EMPTY_PARAMETERS_TOKEN },
        ).joinToString("|")

    companion object {
        fun parse(encoded: String): WinRTProjectionCallSiteDescriptor {
            val fields = encoded.split('|')
            require(fields.size == 8) { "Malformed WinRT projection call-site descriptor '$encoded'." }
            require(fields[0] == CURRENT_SCHEMA_VERSION) {
                "Unsupported WinRT projection call-site schema '${fields[0]}'."
            }
            val parameters = when (fields[7]) {
                EMPTY_PARAMETERS_TOKEN -> emptyList()
                else -> fields[7].split(',').map(WinRTProjectionCallSiteParameter::parse)
            }
            return WinRTProjectionCallSiteDescriptor(
                receiver = fields[1].enumValue("receiver"),
                slotPolicy = fields[2].enumValue("slot policy"),
                constantSlot = fields[3].intValue("constant slot"),
                hResultPolicy = fields[4].enumValue("HRESULT policy"),
                resultStrategy = fields[5].enumValue("result strategy"),
                result = WinRTProjectionCallSiteValue.parse(fields[6]),
                parameters = parameters,
            )
        }

        private val scalarResultKinds =
            setOf(
                WinRTProjectionCallSiteValueKind.BOOLEAN,
                WinRTProjectionCallSiteValueKind.INT8,
                WinRTProjectionCallSiteValueKind.UINT8,
                WinRTProjectionCallSiteValueKind.INT16,
                WinRTProjectionCallSiteValueKind.UINT16,
                WinRTProjectionCallSiteValueKind.INT32,
                WinRTProjectionCallSiteValueKind.UINT32,
                WinRTProjectionCallSiteValueKind.INT64,
                WinRTProjectionCallSiteValueKind.UINT64,
                WinRTProjectionCallSiteValueKind.FLOAT,
                WinRTProjectionCallSiteValueKind.DOUBLE,
                WinRTProjectionCallSiteValueKind.RAW_ADDRESS,
                WinRTProjectionCallSiteValueKind.RAW_COM_PTR,
            )
    }
}

private inline fun <reified T : Enum<T>> String.enumValue(description: String): T =
    enumValues<T>().singleOrNull { value -> value.name == this }
        ?: throw IllegalArgumentException("Unknown WinRT call-site $description '$this'.")

private fun String.intValue(description: String): Int =
    toIntOrNull() ?: throw IllegalArgumentException("Malformed WinRT call-site $description '$this'.")

private fun String.nonNegativeInt(description: String): Int =
    intValue(description).also { value ->
        require(value >= 0) { "WinRT call-site $description must not be negative." }
    }
