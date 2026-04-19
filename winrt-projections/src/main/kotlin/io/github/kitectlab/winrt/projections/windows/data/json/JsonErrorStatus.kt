package io.github.kitectlab.winrt.projections.windows.data.json

enum class JsonErrorStatus(val abiValue: Int) {
    Unknown(0),
    InvalidJsonString(1),
    InvalidJsonNumber(2),
    JsonValueNotFound(3),
    ImplementationLimit(4),
    ;

    companion object {
        fun fromAbiValue(value: Int): JsonErrorStatus =
            entries.firstOrNull { it.abiValue == value }
                ?: error("Unknown Windows.Data.Json.JsonErrorStatus ABI value: $value")
    }
}